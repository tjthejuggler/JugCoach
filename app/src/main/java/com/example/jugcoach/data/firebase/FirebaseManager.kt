package com.example.jugcoach.data.firebase

import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import android.content.Context
import com.example.jugcoach.data.entity.Pattern
import com.example.jugcoach.data.entity.Run
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FirebaseManager"

/**
 * Manager class for Firebase operations
 */
@Singleton
class FirebaseManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val firebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firebaseDatabase: FirebaseDatabase by lazy { FirebaseDatabase.getInstance() }
    private val initialized = AtomicBoolean(false)

    init {
        initialize()
    }

    private fun initialize() {
        try {
            if (!initialized.getAndSet(true)) {
                Log.d("RecordSync", "Initializing Firebase...")
                FirebaseApp.initializeApp(context)
                Log.d("RecordSync", "Firebase app initialized")
                
                firebaseDatabase.setPersistenceEnabled(true)
                Log.d("RecordSync", "Firebase persistence enabled")
                
                // Log initial auth state
                val currentUser = firebaseAuth.currentUser
                Log.d("RecordSync", "Initial auth state - user logged in: ${currentUser != null}")
                if (currentUser != null) {
                    Log.d("RecordSync", "Initial user email: ${currentUser.email}")
                    Log.d("RecordSync", "Initial user ID: ${currentUser.uid}")
                }
                
                Log.d(TAG, "Firebase initialized successfully")
                
                // Test database connection once on startup
                testDatabaseConnection()
            } else {
                Log.d("RecordSync", "Firebase already initialized, skipping initialization")
            }
        } catch (e: Exception) {
            Log.e("RecordSync", "Error initializing Firebase", e)
            Log.e("RecordSync", "Exception message: ${e.message}")
            Log.e("RecordSync", "Stack trace: ${e.stackTraceToString()}")
            initialized.set(false)
        }
    }
    
    /**
     * Test the database connection by trying to access a public node
     */
    private fun testDatabaseConnection() {
        firebaseDatabase.getReference(".info/connected")
            .addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    val connected = snapshot.getValue(Boolean::class.java) ?: false
                    Log.d(TAG, "Firebase connection status: $connected")
                    if (connected) {
                        // Once connected, check basic database structure
                        checkDatabaseStructure()
                    }
                }
                
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                    Log.e(TAG, "Firebase connection check failed", error.toException())
                }
            })
    }
    
    /**
     * Check if essential database nodes exist to validate access
     */
    private fun checkDatabaseStructure() {
        firebaseDatabase.getReference("myTricks").limitToFirst(1)
            .get()
            .addOnSuccessListener { snapshot ->
                Log.d(TAG, "myTricks node exists: ${snapshot.exists()}, childCount: ${snapshot.childrenCount}")
                if (snapshot.exists()) {
                    Log.d(TAG, "Sample myTricks keys: ${snapshot.children.map { it.key }.take(3)}")
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to access myTricks node", e)
            }
            
        firebaseDatabase.getReference("users").limitToFirst(1)
            .get()
            .addOnSuccessListener { snapshot ->
                Log.d(TAG, "users node exists: ${snapshot.exists()}, childCount: ${snapshot.childrenCount}")
                if (snapshot.exists()) {
                    Log.d(TAG, "Sample users keys: ${snapshot.children.map { it.key }.take(3)}")
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to access users node", e)
            }
    }

    /**
     * Get the current Firebase Auth instance
     */
    fun getAuth(): FirebaseAuth = firebaseAuth

    /**
     * Get the Firebase Database instance
     */
    fun getDatabase(): FirebaseDatabase = firebaseDatabase

    /**
     * Check if a user is currently logged in
     */
    fun isUserLoggedIn(): Boolean {
        val isLoggedIn = firebaseAuth.currentUser != null
        Log.d("RecordSync", "isUserLoggedIn check: $isLoggedIn")
        if (isLoggedIn) {
            Log.d("RecordSync", "Current user email: ${firebaseAuth.currentUser?.email}")
            Log.d("RecordSync", "Current user ID: ${firebaseAuth.currentUser?.uid}")
            Log.d("RecordSync", "Current user is email verified: ${firebaseAuth.currentUser?.isEmailVerified}")
        }
        return isLoggedIn
    }

    /**
     * Get the current logged-in user's ID
     */
    fun getCurrentUserId(): String? {
        val uid = firebaseAuth.currentUser?.uid
        Log.d("RecordSync", "getCurrentUserId: $uid")
        return uid
    }

    /**
     * Get the current logged-in user's email
     */
    fun getCurrentUserEmail(): String? {
        val email = firebaseAuth.currentUser?.email
        Log.d("RecordSync", "getCurrentUserEmail: $email")
        return email
    }

    /**
     * Sign out the current user
     */
    fun signOut() {
        Log.d("RecordSync", "Signing out user: ${getCurrentUserEmail()}")
        firebaseAuth.signOut()
        Log.d("RecordSync", "After signOut, isLoggedIn: ${isUserLoggedIn()}")
    }
    
    /**
     * Test database connection and check record structure
     * This can be called to diagnose any issues with database access
     */
    fun testDatabaseAccessAndStructure() {
        Log.d("RecordSync", "Testing database access and structure")
        try {
            // Check if we can access the myTricks node
            firebaseDatabase.getReference("myTricks").limitToFirst(5)
                .get()
                .addOnSuccessListener { snapshot ->
                    Log.d("RecordSync", "Database test succeeded, found ${snapshot.childrenCount} records")
                    if (snapshot.exists() && snapshot.childrenCount > 0) {
                        // Log a sample record structure
                        val sampleKey = snapshot.children.first().key
                        Log.d("RecordSync", "Sample record key: $sampleKey")
                        
                        val username = snapshot.children.first().child("username").getValue(String::class.java)
                        val email = snapshot.children.first().child("email").getValue(String::class.java)
                        val hasMyTricks = snapshot.children.first().hasChild("myTricks")
                        
                        Log.d("RecordSync", "Sample record structure - username: $username, email: $email, hasMyTricks: $hasMyTricks")
                        
                        if (hasMyTricks) {
                            val tricks = snapshot.children.first().child("myTricks").children.map { it.key }
                            Log.d("RecordSync", "Sample tricks: ${tricks.take(3)}")
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("RecordSync", "Database test failed", e)
                }
                
            // Check if we can access the leaderboard node
            firebaseDatabase.getReference("leaderboard").limitToFirst(3)
                .get()
                .addOnSuccessListener { snapshot ->
                    Log.d("RecordSync", "Leaderboard test succeeded, found ${snapshot.childrenCount} records")
                    if (snapshot.exists() && snapshot.childrenCount > 0) {
                        val tricks = snapshot.children.map { it.key }
                        Log.d("RecordSync", "Sample leaderboard tricks: $tricks")
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("RecordSync", "Leaderboard test failed", e)
                }
        } catch (e: Exception) {
            Log.e("RecordSync", "Error testing database access", e)
        }
    }
}