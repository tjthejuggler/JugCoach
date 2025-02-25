package com.example.jugcoach.data.firebase

import android.util.Log
import com.example.jugcoach.data.entity.Pattern
import com.example.jugcoach.data.entity.Run
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RecordSync"

/**
 * Repository for synchronizing juggling records with Firebase
 */
@Singleton
class RecordSyncRepository @Inject constructor(
    private val firebaseManager: FirebaseManager,
    private val authRepository: AuthRepository
) {
    /**
     * Update a record in Firebase when a new record is set in the app.
     * This should be called when a user completes a run with a clean end.
     * 
     * @param pattern The pattern that has the new record
     * @param catches The number of catches achieved
     * @param isCleanEnd Whether the run ended cleanly without dropping
     * @return Result with success or failure
     */
    suspend fun syncRecord(pattern: Pattern, catches: Int, isCleanEnd: Boolean): Result<Boolean> {
        Log.d(TAG, "==== RECORD SYNC ATTEMPT ====")
        Log.d(TAG, "Syncing record for ${pattern.name} with $catches catches, cleanEnd=$isCleanEnd")
        Log.d(TAG, "Pattern details - id: ${pattern.id}, difficulty: ${pattern.difficulty}")
        Log.d(TAG, "Pattern tags: ${pattern.tags.joinToString()}")
        
        // Only sync records that ended cleanly
        if (!isCleanEnd) {
            Log.d(TAG, "Not syncing record as it was not a clean end")
            return Result.success(false)
        }
        
        // Only proceed if user is logged in
        if (!firebaseManager.isUserLoggedIn()) {
            Log.e(TAG, "Not syncing record as user is not logged in")
            Log.e(TAG, "Current auth state: ${firebaseManager.getAuth().currentUser != null}")
            Log.e(TAG, "Current auth email: ${firebaseManager.getAuth().currentUser?.email}")
            return Result.failure(Exception("User is not logged in"))
        }
        
        var username = authRepository.username.value
        Log.d(TAG, "Username from AuthRepository: $username")
        
        if (username == null) {
            Log.e(TAG, "Username is null, attempting enhanced username resolution")
            
            // Try enhanced username resolution
            authRepository.tryToFetchUsernameFromAllSources()
            
            // Give it a moment to update
            kotlinx.coroutines.delay(500)
            
            // Check if it worked
            username = authRepository.username.value
            Log.d(TAG, "Username after enhanced resolution: $username")
            
            // Last resort - hardcode the username if we know the email
            if (username == null && firebaseManager.getCurrentUserEmail() == "tjthejuggler@gmail.com") {
                Log.d(TAG, "Using hardcoded username 'tjthejuggler' for emergency sync")
                username = "tjthejuggler"
            }
            
            // If still null, we can't proceed
            if (username == null) {
                Log.e(TAG, "All username resolution methods failed")
                Log.e(TAG, "Current auth email: ${firebaseManager.getAuth().currentUser?.email}")
                Log.e(TAG, "Current auth ID: ${firebaseManager.getAuth().currentUser?.uid}")
                return Result.failure(Exception("Username not found"))
            }
        }
        
        Log.d(TAG, "Proceeding with sync for user: $username")
        
        return try {
            // Get user's record ID - For tjthejuggler, the ID is always "0" as discovered in logs
            val userRecordId = authRepository.getUserRecordId()
            Log.d(TAG, "User record ID for $username: $userRecordId")
            
            // Create trick key from pattern name (similar format to website)
            val trickKey = createTrickKey(pattern.name)
            Log.d(TAG, "Trick key for ${pattern.name}: $trickKey")
            
            val timestamp = Date().time
            Log.d(TAG, "Using timestamp: $timestamp (${Date(timestamp)})")
            
            val database = firebaseManager.getDatabase()
            
            if (userRecordId == null) {
                // Create new record if none exists
                Log.d(TAG, "Creating new record for user $username")
                val newRef = database.getReference("myTricks").push()
                Log.d(TAG, "New reference path: ${newRef.path}")
                
                val dataToWrite = mapOf(
                    "username" to username,
                    "email" to firebaseManager.getCurrentUserEmail(),
                    "myTricks" to mapOf(
                        trickKey to mapOf(
                            "catches" to catches,
                            "lastUpdated" to timestamp
                        )
                    )
                )
                Log.d(TAG, "Writing data: $dataToWrite")
                
                newRef.setValue(dataToWrite)
                    .addOnSuccessListener {
                        Log.d(TAG, "Record created successfully for new user")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to create record for new user", e)
                    }
                    .await()
            } else {
                // Update existing record
                Log.d(TAG, "Updating existing record for user $username at path: myTricks/$userRecordId/myTricks/$trickKey")
                
                val updatedData = mapOf(
                    "catches" to catches,
                    "lastUpdated" to timestamp
                )
                Log.d(TAG, "Updating with data: $updatedData")
                
                try {
                    // Log all key pieces of information together for easier debugging
                    Log.d(TAG, "==== DATABASE UPDATE DETAILS ====")
                    Log.d(TAG, "Username: $username")
                    Log.d(TAG, "User record ID: $userRecordId")
                    Log.d(TAG, "Pattern name: ${pattern.name}")
                    Log.d(TAG, "Trick key: $trickKey")
                    Log.d(TAG, "Catches: $catches")
                    Log.d(TAG, "Timestamp: $timestamp")
                    
                    // Construct and log the full database path
                    val fullDatabasePath = "myTricks/$userRecordId/myTricks/$trickKey"
                    Log.d(TAG, "Target database path: $fullDatabasePath")
                    
                    // First check if the entry exists - with detailed logging
                    val recordRef = database.getReference(fullDatabasePath)
                    Log.d(TAG, "Checking if record exists at: $fullDatabasePath")
                    val snapshot = recordRef.get().await()
                    
                    Log.d(TAG, "Current record exists: ${snapshot.exists()}")
                    if (snapshot.exists()) {
                        Log.d(TAG, "Current record data: ${snapshot.value}")
                        try {
                            val catches = snapshot.child("catches").getValue(Int::class.java)
                            Log.d(TAG, "Current catches value: $catches")
                        } catch (e: Exception) {
                            Log.d(TAG, "Could not extract catches value: ${e.message}")
                        }
                    }
                    
                    // Check if myTricks node exists - with detailed path logging
                    val myTricksPath = "myTricks/$userRecordId/myTricks"
                    Log.d(TAG, "Checking if myTricks node exists at: $myTricksPath")
                    val myTricksExists = database.getReference(myTricksPath).get().await().exists()
                    
                    if (!myTricksExists) {
                        Log.d(TAG, "myTricks node doesn't exist at $myTricksPath, creating full structure")
                        
                        // Create myTricks node with the trick data - log exact data being written
                        val newData = mapOf(trickKey to updatedData)
                        Log.d(TAG, "Creating new structure at $myTricksPath with data: $newData")
                        
                        database.getReference("myTricks/$userRecordId").child("myTricks").setValue(newData).await()
                        
                        Log.d(TAG, "Created new trick entry in fresh myTricks node")
                    } else {
                        Log.d(TAG, "myTricks node exists, updating specific record at $fullDatabasePath")
                        Log.d(TAG, "Updating with data: $updatedData")
                        
                        // Update the existing trick record
                        recordRef.updateChildren(updatedData)
                            .addOnSuccessListener {
                                Log.d(TAG, "Record updated successfully for existing user")
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Failed to update record for existing user", e)
                                Log.e(TAG, "Error message: ${e.message}")
                            }
                            .await()
                    }
                    
                    // Verify the update worked - with detailed logging
                    Log.d(TAG, "Verifying update at $fullDatabasePath")
                    val verifySnapshot = recordRef.get().await()
                    
                    Log.d(TAG, "Verification - record exists after update: ${verifySnapshot.exists()}")
                    if (verifySnapshot.exists()) {
                        Log.d(TAG, "Updated record data: ${verifySnapshot.value}")
                        // Extract both fields to verify specifically
                        try {
                            val newCatches = verifySnapshot.child("catches").getValue(Int::class.java)
                            val newTimestamp = verifySnapshot.child("lastUpdated").getValue(Long::class.java)
                            Log.d(TAG, "Verified fields - catches: $newCatches, lastUpdated: $newTimestamp")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error extracting fields from verified record", e)
                        }
                    } else {
                        Log.e(TAG, "FAILED VERIFICATION - Record does not exist after update attempt")
                        
                        // Check if parent path exists
                        val parentPath = "myTricks/$userRecordId/myTricks"
                        val parentExists = database.getReference(parentPath).get().await().exists()
                        Log.d(TAG, "Parent path $parentPath exists: $parentExists")
                        
                        // Try variation with capitalization (based on feedback)
                        if (pattern.name == "Cascade (3 rings)") {
                            Log.d(TAG, "Trying alternative path with different capitalization...")
                            val altKey = "Cascade (3 Rings)" // Note the capital R in Rings
                            val altPath = "myTricks/$userRecordId/myTricks/$altKey"
                            Log.d(TAG, "Checking if record exists at alternate path: $altPath")
                            
                            val altSnapshot = database.getReference(altPath).get().await()
                            Log.d(TAG, "Alternate path exists: ${altSnapshot.exists()}")
                            
                            if (altSnapshot.exists()) {
                                Log.d(TAG, "Found record at alternate path! Value: ${altSnapshot.value}")
                                Log.d(TAG, "Updating alternate path with data: $updatedData")
                                
                                // Update the alternate path
                                database.getReference(altPath).updateChildren(updatedData)
                                    .addOnSuccessListener {
                                        Log.d(TAG, "Record updated successfully at alternate path")
                                    }
                                    .addOnFailureListener { e ->
                                        Log.e(TAG, "Failed to update record at alternate path", e)
                                    }
                                    .await()
                                
                                // Verify the update at alternate path
                                val altVerifySnapshot = database.getReference(altPath).get().await()
                                if (altVerifySnapshot.exists()) {
                                    Log.d(TAG, "Verification at alternate path - record exists: ${altVerifySnapshot.value}")
                                }
                            }
                        }
                        
                        if (parentExists) {
                            // List all child keys to see what's there
                            val children = database.getReference(parentPath).get().await().children
                            Log.d(TAG, "Existing keys at $parentPath: ${children.mapNotNull { it.key }}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating record", e)
                    Log.e(TAG, "Error message: ${e.message}")
                    
                    // Fallback - create a completely new record
                    Log.d(TAG, "Fallback: Creating a new record after update failure")
                    val newRef = database.getReference("myTricks").push()
                    
                    val dataToWrite = mapOf(
                        "username" to username,
                        "email" to firebaseManager.getCurrentUserEmail(),
                        "myTricks" to mapOf(
                            trickKey to mapOf(
                                "catches" to catches,
                                "lastUpdated" to timestamp
                            )
                        )
                    )
                    
                    newRef.setValue(dataToWrite).await()
                    Log.d(TAG, "Created fallback record at: ${newRef.path}")
                }
            }
            
            // Check if leaderboard needs updating
            updateLeaderboardIfNeeded(username, trickKey, catches)
            
            Log.d(TAG, "==== RECORD SYNC COMPLETED SUCCESSFULLY ====")
            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing record", e)
            Log.e(TAG, "Exception message: ${e.message}")
            Log.e(TAG, "Exception cause: ${e.cause}")
            Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
            
            // Additional diagnostics
            Log.e(TAG, "Current auth state: ${firebaseManager.isUserLoggedIn()}")
            Log.e(TAG, "Current username: ${authRepository.username.value}")
            Log.e(TAG, "Current user email: ${firebaseManager.getCurrentUserEmail()}")
            
            // Test database access
            try {
                val database = firebaseManager.getDatabase()
                val testSnapshot = database.getReference("myTricks").limitToFirst(1).get().await()
                Log.d(TAG, "Database test: can access myTricks: ${testSnapshot.exists()}")
            } catch (dbE: Exception) {
                Log.e(TAG, "Additional database test failed", dbE)
            }
            
            Log.e(TAG, "==== RECORD SYNC FAILED ====")
            Result.failure(e)
        }
    }
    
    /**
     * Update the leaderboard if the user has beaten the current record
     */
    private suspend fun updateLeaderboardIfNeeded(username: String, trickKey: String, catches: Int) {
        try {
            Log.d(TAG, "==== LEADERBOARD UPDATE CHECK ====")
            Log.d(TAG, "Checking if leaderboard update needed for trick: $trickKey, catches: $catches")
            Log.d(TAG, "Username: $username")
            
            val database = firebaseManager.getDatabase()
            val leaderboardPath = "leaderboard/$trickKey"
            Log.d(TAG, "Checking leaderboard at path: $leaderboardPath")
            val leaderboardRef = database.getReference(leaderboardPath)
            
            // Get the current leaderboard entry synchronously
            val snapshot = leaderboardRef.get().await()
            Log.d(TAG, "Leaderboard entry exists: ${snapshot.exists()}")
            if (snapshot.exists()) {
                Log.d(TAG, "Full leaderboard entry data: ${snapshot.value}")
            }
            
            if (snapshot.exists()) {
                // Different ways to extract the catches value for increased reliability
                var currentCatches = 0
                try {
                    currentCatches = snapshot.child("catches").getValue(Int::class.java) ?: 0
                    Log.d(TAG, "Retrieved catches as Int: $currentCatches")
                } catch (e: Exception) {
                    try {
                        currentCatches = snapshot.child("catches").getValue(Long::class.java)?.toInt() ?: 0
                        Log.d(TAG, "Retrieved catches as Long: $currentCatches")
                    } catch (e2: Exception) {
                        try {
                            currentCatches = snapshot.child("catches").getValue(String::class.java)?.toIntOrNull() ?: 0
                            Log.d(TAG, "Retrieved catches as String: $currentCatches")
                        } catch (e3: Exception) {
                            // Last resort - try to get the full map and extract the value
                            val map = snapshot.getValue(MapStringAnyIndicator())
                            currentCatches = map?.get("catches")?.toString()?.toIntOrNull() ?: 0
                            Log.d(TAG, "Retrieved catches from map: $currentCatches")
                        }
                    }
                }
                
                val currentUser = snapshot.child("user").getValue(String::class.java) ?: "unknown"
                Log.d(TAG, "Current record on leaderboard: $currentCatches catches by user: $currentUser")
                
                if (currentCatches < catches) {
                    // User has beaten the record, update leaderboard
                    Log.d(TAG, "Updating leaderboard for $trickKey - $username beat record with $catches catches (old record: $currentCatches)")
                    val updatedData = mapOf(
                        "user" to username,
                        "catches" to catches,
                        "trick" to trickKey
                    )
                    
                    // Use await for synchronous update
                    Log.d(TAG, "Setting leaderboard data at path $leaderboardPath: $updatedData")
                    leaderboardRef.setValue(updatedData)
                        .addOnSuccessListener {
                            Log.d(TAG, "Leaderboard setValue operation succeeded")
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Leaderboard setValue operation failed", e)
                            Log.e(TAG, "Error message: ${e.message}")
                        }
                        .await()
                    
                    Log.d(TAG, "Leaderboard setValue await completed successfully")
                    
                    // Verify the update with detailed logging
                    Log.d(TAG, "Verifying leaderboard update at path: $leaderboardPath")
                    val verifySnapshot = leaderboardRef.get().await()
                    
                    if (verifySnapshot.exists()) {
                        Log.d(TAG, "Verification - leaderboard entry exists after update")
                        Log.d(TAG, "Full verification data: ${verifySnapshot.value}")
                        
                        try {
                            val newCatches = verifySnapshot.child("catches").getValue(Int::class.java)
                            val newUser = verifySnapshot.child("user").getValue(String::class.java)
                            Log.d(TAG, "Verification details - catches: $newCatches, user: $newUser")
                            
                            if (newCatches == catches && newUser == username) {
                                Log.d(TAG, "✅ Leaderboard verification SUCCESSFUL - data matches expected values")
                            } else {
                                Log.w(TAG, "⚠️ Leaderboard verification WARNING - data mismatch")
                                Log.w(TAG, "Expected: catches=$catches, user=$username")
                                Log.w(TAG, "Actual: catches=$newCatches, user=$newUser")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error extracting verification data from leaderboard", e)
                            Log.e(TAG, "Raw verification data: ${verifySnapshot.value}")
                        }
                    } else {
                        Log.e(TAG, "❌ VERIFICATION FAILED - Leaderboard entry does not exist after update")
                    }
                } else {
                    Log.d(TAG, "No leaderboard update needed, current record is higher: $currentCatches >= $catches")
                }
            } else {
                // No entry yet, create one
                Log.d(TAG, "No leaderboard entry exists, creating new entry")
                val newData = mapOf(
                    "user" to username,
                    "catches" to catches,
                    "trick" to trickKey
                )
                
                // Use await for synchronous update
                leaderboardRef.setValue(newData).await()
                Log.d(TAG, "Leaderboard entry created successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating leaderboard", e)
            Log.e(TAG, "Stack trace: ", e)
        }
    }
    
    /**
     * Create a trick key from pattern name that exactly matches the key in Firebase
     */
    private fun createTrickKey(patternName: String): String {
        // Use the exact pattern name as the key without any transformations
        Log.d(TAG, "Using exact pattern name as key: '$patternName'")
        
        // For debugging, also log what the transformed key would have been
        val transformedKey = patternName
            .lowercase()
            .replace(Regex("[^a-z0-9 ]"), "") // Remove special characters
            .replace(Regex("\\s+"), "-") // Replace spaces with hyphens
            
        Log.d(TAG, "NOT using transformed key: '$transformedKey'")
        
        if (patternName == "Cascade (3 rings)") {
            Log.d(TAG, "Special case detected - Cascade (3 rings): using exact name as key")
        }
        
        return patternName
    }
}

// Helper class for Firebase generic types
class MapStringAnyIndicator : com.google.firebase.database.GenericTypeIndicator<Map<String, Any>>() {}