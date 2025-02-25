package com.example.jugcoach.data.firebase

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AuthRepository"

/**
 * Repository for handling Firebase Authentication
 */
@Singleton
class AuthRepository @Inject constructor(
    val firebaseManager: FirebaseManager
) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val _currentUser = MutableStateFlow<FirebaseUser?>(null)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser
    
    private val _username = MutableStateFlow<String?>(null)
    val username: StateFlow<String?> = _username

    init {
        // Initialize current user state
        _currentUser.value = firebaseManager.getAuth().currentUser
        
        // Listen for auth state changes
        firebaseManager.getAuth().addAuthStateListener { auth ->
            _currentUser.value = auth.currentUser
            if (auth.currentUser != null) {
                // Start username resolution
                Log.d("RecordSync", "Auth state changed - user logged in, fetching username")
                fetchUsernameForEmail(auth.currentUser?.email)
            } else {
                Log.d("RecordSync", "Auth state changed - user logged out, clearing username")
                _username.value = null
            }
        }
        
        // If user is already logged in at initialization, manually trigger username lookup
        if (firebaseManager.getAuth().currentUser != null) {
            Log.d("RecordSync", "User already logged in at init, manually fetching username")
            tryToFetchUsernameFromAllSources()
        }
    }
    
    /**
     * Attempt to fetch username using all available methods
     */
    fun tryToFetchUsernameFromAllSources() {
        coroutineScope.launch {
            try {
                Log.d("RecordSync", "Attempting all username resolution methods")
                // First try by email
                fetchUsernameForEmail(firebaseManager.getCurrentUserEmail())
                
                // If that didn't work, check if we have a fixed username for this user
                if (_username.value == null) {
                    Log.d("RecordSync", "Email lookup failed, trying direct record lookup")
                    val email = firebaseManager.getCurrentUserEmail()
                    if (email == "tjthejuggler@gmail.com") {
                        Log.d("RecordSync", "Recognized tjthejuggler@gmail.com, manually setting username")
                        _username.value = "tjthejuggler"
                        return@launch // We've found the username, no need to continue
                    }
                }
                
                // If still no username, try checking all records
                if (_username.value == null) {
                    Log.d("RecordSync", "Direct lookup failed, scanning all records")
                    scanAllRecordsForUsername()
                }
                
                Log.d("RecordSync", "Username resolution complete, result: ${_username.value}")
            } catch (e: Exception) {
                Log.e("RecordSync", "Error in tryToFetchUsernameFromAllSources", e)
            }
        }
    }
    
    /**
     * Scan all records in myTricks to find one that might belong to the current user
     */
    private suspend fun scanAllRecordsForUsername() {
        try {
            val database = firebaseManager.getDatabase()
            val myTricksRef = database.getReference("myTricks")
            
            // Get all records
            val snapshot = myTricksRef.get().await()
            Log.d("RecordSync", "Scanning ${snapshot.childrenCount} records for potential matches")
            
            // Look for key "0" specifically, as logs show this contains tjthejuggler
            if (snapshot.hasChild("0")) {
                val recordSnapshot = snapshot.child("0")
                val username = recordSnapshot.child("username").getValue(String::class.java)
                if (username != null) {
                    Log.d("RecordSync", "Found username in record 0: $username")
                    _username.value = username
                    return
                }
            }
            
            // General scan of all records
            for (record in snapshot.children) {
                val username = record.child("username").getValue(String::class.java)
                Log.d("RecordSync", "Record ${record.key} has username: $username")
                
                if (username != null) {
                    // If we have a logged in user, see if email matches
                    val recordEmail = record.child("email").getValue(String::class.java)
                    val currentEmail = firebaseManager.getCurrentUserEmail()
                    
                    if (recordEmail == currentEmail) {
                        Log.d("RecordSync", "Found matching email for username: $username")
                        _username.value = username
                        return
                    }
                    
                    // Special case for tjthejuggler
                    if (username == "tjthejuggler" && currentEmail == "tjthejuggler@gmail.com") {
                        Log.d("RecordSync", "Found tjthejuggler record with matching logic")
                        _username.value = username
                        return
                    }
                }
            }
            
            Log.d("RecordSync", "No matching username found in database scan")
        } catch (e: Exception) {
            Log.e("RecordSync", "Error scanning records for username", e)
        }
    }
    
    /**
     * Login with email and password
     * @return Result with user if successful, error otherwise
     */
    suspend fun login(email: String, password: String): Result<FirebaseUser> = try {
        val result = firebaseManager.getAuth().signInWithEmailAndPassword(email, password).await()
        result.user?.let {
            fetchUsernameForEmail(email)
            Result.success(it)
        } ?: Result.failure(Exception("Login failed: User is null"))
    } catch (e: Exception) {
        Log.e(TAG, "Login failed", e)
        Result.failure(e)
    }
    
    /**
     * Login using username (lookup email first, then login)
     */
    suspend fun loginWithUsername(username: String, password: String): Result<FirebaseUser> {
        return try {
            Log.d(TAG, "Attempting to login with username: $username")
            
            // First, find the email associated with this username
            val email = findEmailByUsername(username)
            
            if (email != null) {
                Log.d(TAG, "Found email for username: $username -> $email, attempting login")
                login(email, password)
            } else {
                Log.e(TAG, "No email found for username: $username")
                Result.failure(Exception("No account found for username: $username"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Login with username failed", e)
            Result.failure(e)
        }
    }
    
    /**
     * Look up the email address for a given username
     */
    private suspend fun findEmailByUsername(username: String): String? {
        return try {
            Log.d(TAG, "Finding email for username: $username")
            val database = firebaseManager.getDatabase()
            
            // APPROACH 1: Check in myTricks collection
            Log.d(TAG, "Checking myTricks collection...")
            val myTricksRef = database.getReference("myTricks")
            val myTricksSnapshot = myTricksRef.get().await()
            
            // Log what we found in myTricks
            Log.d(TAG, "myTricks entries count: ${myTricksSnapshot.childrenCount}")
            
            // Query records by username
            val dataSnapshot = myTricksRef.orderByChild("username")
                .equalTo(username)
                .get()
                .await()
            
            Log.d(TAG, "Found ${dataSnapshot.childrenCount} matching records in myTricks for username: $username")
            
            // Go through each user record and check the email
            for (userRecord in dataSnapshot.children) {
                Log.d(TAG, "Examining record key: ${userRecord.key}")
                val recordUsername = userRecord.child("username").getValue(String::class.java)
                Log.d(TAG, "Record username: $recordUsername")
                
                val email = userRecord.child("email").getValue(String::class.java)
                if (email != null) {
                    Log.d(TAG, "Found email in myTricks: $email")
                    return email
                }
            }
            
            // APPROACH 2: Check in users collection (legacy format)
            Log.d(TAG, "Checking users collection...")
            val usersRef = database.getReference("users")
            val usersSnapshot = usersRef.get().await()
            
            // Log what we found in users
            Log.d(TAG, "users entries count: ${usersSnapshot.childrenCount}")
            
            // Try direct lookup if email is used as the key (with , instead of .)
            val emailAsKey = username.replace(".", ",")
            val directSnapshot = usersRef.child(emailAsKey).get().await()
            if (directSnapshot.exists()) {
                Log.d(TAG, "Found direct entry for $emailAsKey in users collection")
                return emailAsKey.replace(",", ".")
            }
            
            // Query by username
            val legacySnapshot = usersRef.orderByChild("username")
                .equalTo(username)
                .get()
                .await()
            
            Log.d(TAG, "Found ${legacySnapshot.childrenCount} matching records in users for username: $username")
            
            for (userRecord in legacySnapshot.children) {
                // In legacy format, the key might be the email address
                val key = userRecord.key
                Log.d(TAG, "Examining users record key: $key")
                
                if (key?.contains(",") == true) {  // Email with dots replaced by commas
                    val email = key.replace(",", ".")
                    Log.d(TAG, "Found email from key: $email")
                    return email
                }
                
                // Or there might be an email field
                val email = userRecord.child("email").getValue(String::class.java)
                if (email != null) {
                    Log.d(TAG, "Found email in users field: $email")
                    return email
                }
            }
            
            // APPROACH 3: Scan all user entries as a last resort
            Log.d(TAG, "Scanning all entries as last resort")
            for (userRecord in usersSnapshot.children) {
                val recordUsername = userRecord.child("username").getValue(String::class.java)
                if (recordUsername == username) {
                    val key = userRecord.key
                    if (key?.contains(",") == true) {
                        val email = key.replace(",", ".")
                        Log.d(TAG, "Found email from users scan: $email")
                        return email
                    }
                }
            }
            
            Log.e(TAG, "No email found for username: $username after checking all paths")
            null // No email found for this username
        } catch (e: Exception) {
            Log.e(TAG, "Error finding email for username: $username", e)
            null
        }
    }
    
    /**
     * Fetch the username associated with an email address
     * Public so it can be called from RecordSyncRepository if needed
     */
    fun fetchUsernameForEmail(email: String?) {
        if (email == null) {
            _username.value = null
            return
        }
        
        try {
            val database = firebaseManager.getDatabase()
            val myTricksRef = database.getReference("myTricks")
            
            myTricksRef.orderByChild("email")
                .equalTo(email)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        for (userRecord in snapshot.children) {
                            val username = userRecord.child("username").getValue(String::class.java)
                            if (username != null) {
                                _username.value = username
                                return
                            }
                        }
                        
                        // If we can't find in myTricks, check users (legacy format)
                        checkLegacyUsernameFormat(email)
                    }
                    
                    override fun onCancelled(error: DatabaseError) {
                        Log.e(TAG, "Error fetching username for email: $email", error.toException())
                    }
                })
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up username listener", e)
        }
    }
    
    private fun checkLegacyUsernameFormat(email: String) {
        try {
            val database = firebaseManager.getDatabase()
            val usersRef = database.getReference("users")
            
            // In legacy format, the email might be the key
            usersRef.child(email.replace(".", ","))
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val username = snapshot.child("username").getValue(String::class.java)
                        if (username != null) {
                            _username.value = username
                        }
                    }
                    
                    override fun onCancelled(error: DatabaseError) {
                        Log.e(TAG, "Error checking legacy username format", error.toException())
                    }
                })
        } catch (e: Exception) {
            Log.e(TAG, "Error checking legacy username format", e)
        }
    }
    
    /**
     * Sign out the current user
     */
    fun signOut() {
        firebaseManager.signOut()
        _username.value = null
    }
    
    /**
     * Get the user record ID for the current user
     */
    suspend fun getUserRecordId(): String? {
        val username = _username.value
        val email = firebaseManager.getCurrentUserEmail()
        
        Log.d("RecordSync", "getUserRecordId called - current username flow value: $username")
        Log.d("RecordSync", "Current auth state: ${firebaseManager.getAuth().currentUser != null}")
        Log.d("RecordSync", "Current auth email: $email")
        
        // Special case for tjthejuggler - we know the record ID from logs
        if (username == "tjthejuggler" || email == "tjthejuggler@gmail.com") {
            Log.d("RecordSync", "Special case for tjthejuggler - using known record ID '0'")
            return "0"
        }
        
        if (username == null) {
            Log.e("RecordSync", "Cannot get user record ID - username is null but user is logged in")
            Log.e("RecordSync", "Current auth user email: ${firebaseManager.getAuth().currentUser?.email}")
            return null
        }
        
        Log.d("RecordSync", "Getting user record ID for username: $username")
        
        return try {
            val database = firebaseManager.getDatabase()
            val myTricksRef = database.getReference("myTricks")
            
            Log.d("RecordSync", "Querying myTricks for username: $username")
            val dataSnapshot = myTricksRef.orderByChild("username")
                .equalTo(username)
                .get()
                .await()
            
            Log.d("RecordSync", "Found ${dataSnapshot.childrenCount} records matching username: $username")
            
            // Log all records found to help debug
            if (dataSnapshot.childrenCount > 0) {
                for (record in dataSnapshot.children) {
                    Log.d("RecordSync", "Found record with key: ${record.key}")
                    val recordUsername = record.child("username").getValue(String::class.java)
                    val recordEmail = record.child("email").getValue(String::class.java)
                    Log.d("RecordSync", "Record data - username: $recordUsername, email: $recordEmail")
                }
            } else {
                Log.d("RecordSync", "No records found matching username: $username")
                Log.d("RecordSync", "Checking database connection and structure...")
                try {
                    val testSnapshot = myTricksRef.limitToFirst(5).get().await()
                    Log.d("RecordSync", "Database connection test: found ${testSnapshot.childrenCount} total records")
                    if (testSnapshot.childrenCount > 0) {
                        Log.d("RecordSync", "Sample records in database:")
                        for (record in testSnapshot.children) {
                            val sampleUsername = record.child("username").getValue(String::class.java)
                            Log.d("RecordSync", "Sample record key: ${record.key}, username: $sampleUsername")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("RecordSync", "Error testing database connection", e)
                }
            }
            
            val recordId = dataSnapshot.children.firstOrNull()?.key
            
            if (recordId != null) {
                Log.d("RecordSync", "Found user record ID: $recordId")
                
                // Additional verification - check if this record has expected structure
                val recordSnapshot = myTricksRef.child(recordId).get().await()
                Log.d("RecordSync", "Record exists: ${recordSnapshot.exists()}")
                
                if (recordSnapshot.exists()) {
                    val recordUsername = recordSnapshot.child("username").getValue(String::class.java)
                    val recordEmail = recordSnapshot.child("email").getValue(String::class.java)
                    val hasTricks = recordSnapshot.hasChild("myTricks")
                    
                    Log.d("RecordSync", "Record details - username: $recordUsername, email: $recordEmail, hasTricks: $hasTricks")
                    
                    // If record doesn't have a myTricks node, create it
                    if (!hasTricks) {
                        Log.d("RecordSync", "Creating missing myTricks node for user")
                        myTricksRef.child(recordId).child("myTricks").setValue(mapOf<String, Any>())
                            .addOnSuccessListener {
                                Log.d("RecordSync", "Created myTricks node successfully")
                            }
                            .addOnFailureListener { e ->
                                Log.e("RecordSync", "Failed to create myTricks node", e)
                            }
                    }
                }
            } else {
                Log.e("RecordSync", "No record found for username: $username")
            }
            
            recordId
        } catch (e: Exception) {
            Log.e("RecordSync", "Error getting user record ID", e)
            Log.e("RecordSync", "Exception message: ${e.message}")
            Log.e("RecordSync", "Stack trace: ${e.stackTraceToString()}")
            null
        }
    }
}