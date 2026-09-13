#!/bin/bash
cat << 'INNEREOF' > patch.txt
    suspend fun updateCompanionSettings(
        username: String,
        characterId: String,
        customName: String,
        customPersona: String
    ) {
        withContext(Dispatchers.IO) {
            val user = userDao.getUserByUsername(username) ?: return@withContext
            val updated = user.copy(
                characterId = characterId,
                companionCustomName = customName,
                companionCustomPersona = customPersona
            )
            userDao.insertOrUpdateUser(updated)

            if (_isFirebaseAvailable) {
                val firestore = firebaseFirestore ?: return@withContext
                val updates = mapOf(
                    "characterId" to characterId,
                    "companionCustomName" to customName,
                    "companionCustomPersona" to customPersona
                )
                firestore.collection("users").document(username)
                    .update(updates)
                    .addOnFailureListener { e ->
                        Log.e("RetirementRepository", "Firestore settings update failed", e)
                    }
            }
        }
    }
INNEREOF
