package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.database.AppDatabase
import com.example.data.database.FinancialLogEntity
import com.example.data.database.SyncQueueDao
import com.example.data.database.SyncQueueEntity
import com.example.data.database.UserEntity
import com.example.data.network.NetworkConnectivityObserver
import com.example.data.sync.ExponentialBackoffHelper
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class RetirementRepository(private val context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val userDao = database.userDao()
    private val financialLogDao = database.financialLogDao()
    val syncQueueDao: SyncQueueDao = database.syncQueueDao()
    val connectivityObserver = NetworkConnectivityObserver(context)

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private var firebaseAuth: FirebaseAuth? = null
    private var firebaseFirestore: FirebaseFirestore? = null
    private var _isFirebaseAvailable = false
    val isFirebaseAvailable: Boolean get() = _isFirebaseAvailable

    init {
        try {
            val resourceId = context.resources.getIdentifier("google_app_id", "string", context.packageName)
            if (resourceId != 0) {
                val apps = FirebaseApp.getApps(context)
                if (apps.isEmpty()) {
                    FirebaseApp.initializeApp(context)
                }
                firebaseAuth = FirebaseAuth.getInstance()
                firebaseFirestore = FirebaseFirestore.getInstance()
                _isFirebaseAvailable = true
                Log.d("RetirementRepository", "Firebase Auth and Firestore successfully initialized!")
            } else {
                _isFirebaseAvailable = false
                Log.i("RetirementRepository", "Firebase is not configured (google-services.json is missing). Falling back to local mode.")
            }
        } catch (e: Exception) {
            _isFirebaseAvailable = false
            Log.w("RetirementRepository", "Firebase initialization failed: ${e.message}. Falling back to local mode.")
        }
    }

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitTask(): T =
        suspendCancellableCoroutine { cont ->
            addOnSuccessListener { result ->
                if (cont.isActive) cont.resume(result) {}
            }
            addOnFailureListener { exception ->
                if (cont.isActive) cont.resumeWithException(exception)
            }
            addOnCanceledListener {
                if (cont.isActive) cont.cancel()
            }
        }

    suspend fun enqueueUserSync(user: UserEntity) {
        val json = JSONObject().apply {
            put("username", user.username)
            put("nickname", user.nickname)
            put("lifestyleId", user.lifestyleId)
            put("assetIndex", user.assetIndex)
            put("characterId", user.characterId)
            put("companionCustomName", user.companionCustomName)
            put("companionCustomPersona", user.companionCustomPersona)
            put("savingTarget", user.savingTarget)
            put("savingCurrent", user.savingCurrent)
            put("securityFund", user.securityFund)
            put("isaContribution", user.isaContribution)
            put("hasCompletedJourney", user.hasCompletedJourney)
        }
        syncQueueDao.enqueue(
            SyncQueueEntity(
                username = user.username,
                entityType = SyncQueueEntity.TYPE_USER,
                entityId = user.username,
                action = SyncQueueEntity.ACTION_UPSERT,
                payloadJson = json.toString()
            )
        )
    }

    suspend fun enqueueLogSync(log: FinancialLogEntity) {
        val json = JSONObject().apply {
            put("id", log.id)
            put("username", log.username)
            put("title", log.title)
            put("amount", log.amount)
            put("category", log.category)
            put("isSolved", log.isSolved)
            put("timestamp", log.timestamp)
        }
        syncQueueDao.enqueue(
            SyncQueueEntity(
                username = log.username,
                entityType = SyncQueueEntity.TYPE_FINANCIAL_LOG,
                entityId = log.id.toString(),
                action = SyncQueueEntity.ACTION_UPSERT,
                payloadJson = json.toString()
            )
        )
    }

    suspend fun enqueueLogDelete(username: String, logId: Long) {
        syncQueueDao.enqueue(
            SyncQueueEntity(
                username = username,
                entityType = SyncQueueEntity.TYPE_FINANCIAL_LOG,
                entityId = logId.toString(),
                action = SyncQueueEntity.ACTION_DELETE,
                payloadJson = "{}"
            )
        )
    }

    suspend fun enqueueCalculatorSync(username: String, data: CalculatorData) {
        val json = JSONObject().apply {
            put("currentAge", data.currentAge)
            put("targetAge", data.targetAge)
            put("expectedLifespan", data.expectedLifespan)
            put("monthlyExpenses", data.monthlyExpenses)
            put("currentAssets", data.currentAssets)
        }
        syncQueueDao.enqueue(
            SyncQueueEntity(
                username = username,
                entityType = SyncQueueEntity.TYPE_CALCULATOR,
                entityId = username,
                action = SyncQueueEntity.ACTION_UPSERT,
                payloadJson = json.toString()
            )
        )
    }

    fun triggerSyncQueueFlush(username: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                flushSyncQueue(username)
            } catch (e: Exception) {
                Log.w("RetirementRepository", "Background triggerSyncQueueFlush failed: ${e.message}")
            }
        }
    }

    suspend fun flushSyncQueue(username: String): Boolean = withContext(Dispatchers.IO) {
        if (!_isFirebaseAvailable) {
            val pending = syncQueueDao.getPendingItemsForUser(username)
            for (item in pending) {
                syncQueueDao.deleteById(item.id)
            }
            return@withContext true
        }

        if (!connectivityObserver.isCurrentlyConnected()) {
            Log.d("RetirementRepository", "Offline mode: pending items preserved in Room sync_queue.")
            return@withContext false
        }

        val firestore = firebaseFirestore ?: return@withContext false
        val pendingItems = syncQueueDao.getPendingItemsForUser(username)
        if (pendingItems.isEmpty()) return@withContext true

        Log.d("RetirementRepository", "Flushing sync queue: ${pendingItems.size} pending items for user $username")
        var allSucceeded = true

        for (item in pendingItems) {
            syncQueueDao.update(
                item.copy(
                    status = SyncQueueEntity.STATUS_PROCESSING,
                    lastAttemptAt = System.currentTimeMillis()
                )
            )

            val result = ExponentialBackoffHelper.retryWithExponentialBackoff(
                maxAttempts = 3,
                initialDelayMs = 1000L,
                maxDelayMs = 8000L,
                onRetry = { attempt, error, nextDelayMs ->
                    Log.w("RetirementRepository", "Retry $attempt for item ${item.id} (${item.entityType}): ${error.message}. Next delay: ${nextDelayMs}ms")
                }
            ) { _ ->
                when (item.entityType) {
                    SyncQueueEntity.TYPE_USER -> {
                        val json = JSONObject(item.payloadJson)
                        val userMap = hashMapOf<String, Any>(
                            "username" to json.optString("username", username),
                            "nickname" to json.optString("nickname", ""),
                            "lifestyleId" to json.optString("lifestyleId", ""),
                            "assetIndex" to json.optInt("assetIndex", 2),
                            "characterId" to json.optString("characterId", ""),
                            "companionCustomName" to json.optString("companionCustomName", ""),
                            "companionCustomPersona" to json.optString("companionCustomPersona", ""),
                            "savingTarget" to json.optLong("savingTarget", 500_000_000L),
                            "savingCurrent" to json.optLong("savingCurrent", 120_000_000L),
                            "securityFund" to json.optLong("securityFund", 20_000_000L),
                            "isaContribution" to json.optLong("isaContribution", 10_000_000L),
                            "hasCompletedJourney" to json.optBoolean("hasCompletedJourney", false)
                        )
                        firestore.collection("users").document(username)
                            .set(userMap).awaitTask()
                    }
                    SyncQueueEntity.TYPE_FINANCIAL_LOG -> {
                        if (item.action == SyncQueueEntity.ACTION_DELETE) {
                            firestore.collection("users").document(username)
                                .collection("leaks").document(item.entityId)
                                .delete().awaitTask()
                        } else {
                            val json = JSONObject(item.payloadJson)
                            val logMap = hashMapOf<String, Any>(
                                "id" to json.optLong("id", item.entityId.toLongOrNull() ?: 0L),
                                "username" to json.optString("username", username),
                                "title" to json.optString("title", ""),
                                "amount" to json.optLong("amount", 0L),
                                "category" to json.optString("category", "OTHER"),
                                "isSolved" to json.optBoolean("isSolved", false),
                                "timestamp" to json.optLong("timestamp", System.currentTimeMillis())
                            )
                            firestore.collection("users").document(username)
                                .collection("leaks").document(item.entityId)
                                .set(logMap).awaitTask()
                        }
                    }
                    SyncQueueEntity.TYPE_CALCULATOR -> {
                        val json = JSONObject(item.payloadJson)
                        val calcMap = hashMapOf<String, Any>(
                            "currentAge" to json.optInt("currentAge", 35),
                            "targetAge" to json.optInt("targetAge", 60),
                            "expectedLifespan" to json.optInt("expectedLifespan", 90),
                            "monthlyExpenses" to json.optLong("monthlyExpenses", 300L),
                            "currentAssets" to json.optLong("currentAssets", 5000L)
                        )
                        firestore.collection("users").document(username)
                            .collection("calculator").document("data")
                            .set(calcMap).awaitTask()
                    }
                }
            }

            if (result.isSuccess) {
                syncQueueDao.deleteById(item.id)
                Log.d("RetirementRepository", "Sync queue item ${item.id} successfully synced.")
            } else {
                allSucceeded = false
                val newRetryCount = item.retryCount + 1
                val isFailed = newRetryCount >= item.maxRetries
                val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                syncQueueDao.update(
                    item.copy(
                        status = if (isFailed) SyncQueueEntity.STATUS_FAILED else SyncQueueEntity.STATUS_PENDING,
                        retryCount = newRetryCount,
                        lastAttemptAt = System.currentTimeMillis(),
                        lastErrorMessage = errorMsg
                    )
                )
                Log.e("RetirementRepository", "Sync item ${item.id} retry $newRetryCount failed: $errorMsg")
            }
        }
        return@withContext allSucceeded
    }

    private suspend fun syncLeaksFromFirestore(username: String) {
        if (!_isFirebaseAvailable) return
        val firestore = firebaseFirestore ?: return
        try {
            val querySnapshot = suspendCoroutine<com.google.firebase.firestore.QuerySnapshot?> { continuation ->
                firestore.collection("users").document(username)
                    .collection("leaks").get()
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) continuation.resume(task.result)
                        else continuation.resume(null)
                    }
            }
            if (querySnapshot != null) {
                for (doc in querySnapshot.documents) {
                    val log = FinancialLogEntity(
                        id = doc.getLong("id") ?: 0L,
                        username = doc.getString("username") ?: username,
                        title = doc.getString("title") ?: "",
                        amount = doc.getLong("amount") ?: 0L,
                        category = doc.getString("category") ?: "OTHER",
                        timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis(),
                        isSolved = doc.getBoolean("isSolved") ?: false
                    )
                    financialLogDao.insertLog(log)
                }
            }
        } catch (e: Exception) {
            Log.e("RetirementRepository", "Error syncing leaks from Firestore: ${e.message}")
        }
    }

    suspend fun saveCalculatorDataToFirestore(username: String, data: CalculatorData) {
        withContext(Dispatchers.IO) {
            enqueueCalculatorSync(username, data)
            triggerSyncQueueFlush(username)
        }
    }

    suspend fun loadCalculatorDataFromFirestore(username: String): CalculatorData? {
        if (!_isFirebaseAvailable) return null
        val firestore = firebaseFirestore ?: return null
        return try {
            val snapshot = suspendCoroutine<com.google.firebase.firestore.DocumentSnapshot?> { continuation ->
                firestore.collection("users").document(username)
                    .collection("calculator").document("data")
                    .get()
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) continuation.resume(task.result)
                        else continuation.resume(null)
                    }
            }
            if (snapshot != null && snapshot.exists()) {
                CalculatorData(
                    currentAge = snapshot.getLong("currentAge")?.toInt() ?: 35,
                    targetAge = snapshot.getLong("targetAge")?.toInt() ?: 60,
                    expectedLifespan = snapshot.getLong("expectedLifespan")?.toInt() ?: 90,
                    monthlyExpenses = snapshot.getLong("monthlyExpenses") ?: 300L,
                    currentAssets = snapshot.getLong("currentAssets") ?: 0L
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("RetirementRepository", "Error loading calculator data: ${e.message}")
            null
        }
    }

    // 1. User & Authentication
    suspend fun registerUser(username: String, passwordRaw: String, nickname: String): Boolean {
        return withContext(Dispatchers.IO) {
            val existing = userDao.getUserByUsername(username)
            if (existing != null) return@withContext false
            
            val passHash = passwordRaw.hashCode().toString()
            val newUser = UserEntity(
                username = username,
                passwordHash = passHash,
                nickname = nickname
            )

            if (_isFirebaseAvailable) {
                val auth = firebaseAuth ?: return@withContext false
                val firestore = firebaseFirestore ?: return@withContext false
                try {
                    val email = if (username.contains("@")) username else "$username@lifeclear.com"
                    val authResult = suspendCoroutine<Boolean> { continuation ->
                        auth.createUserWithEmailAndPassword(email, passwordRaw)
                            .addOnCompleteListener { task ->
                                continuation.resume(task.isSuccessful)
                            }
                    }
                    if (!authResult) return@withContext false

                    val userMap = hashMapOf(
                        "username" to username,
                        "nickname" to nickname,
                        "lifestyleId" to newUser.lifestyleId,
                        "assetIndex" to newUser.assetIndex,
                        "characterId" to newUser.characterId,
                        "companionCustomName" to newUser.companionCustomName,
                        "companionCustomPersona" to newUser.companionCustomPersona,
                        "savingTarget" to newUser.savingTarget,
                        "savingCurrent" to newUser.savingCurrent,
                        "securityFund" to newUser.securityFund,
                        "isaContribution" to newUser.isaContribution,
                        "hasCompletedJourney" to newUser.hasCompletedJourney
                    )
                    
                    val firestoreResult = suspendCoroutine<Boolean> { continuation ->
                        firestore.collection("users").document(username)
                            .set(userMap)
                            .addOnCompleteListener { task ->
                                continuation.resume(task.isSuccessful)
                            }
                    }
                } catch (e: Exception) {
                    Log.e("RetirementRepository", "Firebase Register error: ${e.message}")
                    return@withContext false
                }
            }

            userDao.insertOrUpdateUser(newUser)
            true
        }
    }

    suspend fun loginUser(username: String, passwordRaw: String): UserEntity? {
        return withContext(Dispatchers.IO) {
            if (_isFirebaseAvailable) {
                val auth = firebaseAuth ?: return@withContext null
                val firestore = firebaseFirestore ?: return@withContext null
                val email = if (username.contains("@")) username else "$username@lifeclear.com"
                try {
                    val authResult = suspendCoroutine<Boolean> { continuation ->
                        auth.signInWithEmailAndPassword(email, passwordRaw)
                            .addOnCompleteListener { task ->
                                continuation.resume(task.isSuccessful)
                            }
                    }
                    if (authResult) {
                        val snapshot = suspendCoroutine<com.google.firebase.firestore.DocumentSnapshot?> { continuation ->
                            firestore.collection("users").document(username).get()
                                .addOnCompleteListener { task ->
                                    if (task.isSuccessful) continuation.resume(task.result)
                                    else continuation.resume(null)
                                }
                        }
                        if (snapshot != null && snapshot.exists()) {
                            val dbUser = UserEntity(
                                username = username,
                                passwordHash = passwordRaw.hashCode().toString(),
                                nickname = snapshot.getString("nickname") ?: username,
                                lifestyleId = snapshot.getString("lifestyleId") ?: "",
                                assetIndex = snapshot.getLong("assetIndex")?.toInt() ?: 2,
                                characterId = snapshot.getString("characterId") ?: "",
                                companionCustomName = snapshot.getString("companionCustomName") ?: "",
                                companionCustomPersona = snapshot.getString("companionCustomPersona") ?: "",
                                savingTarget = snapshot.getLong("savingTarget") ?: 500_000_000L,
                                savingCurrent = snapshot.getLong("savingCurrent") ?: 120_000_000L,
                                securityFund = snapshot.getLong("securityFund") ?: 20_000_000L,
                                isaContribution = snapshot.getLong("isaContribution") ?: 0L,
                                hasCompletedJourney = snapshot.getBoolean("hasCompletedJourney") ?: false
                            )
                            userDao.insertOrUpdateUser(dbUser)
                            syncLeaksFromFirestore(username)
                            return@withContext dbUser
                        }
                    }
                } catch (e: Exception) {
                    Log.e("RetirementRepository", "Firebase Login error: ${e.message}")
                }
            }

            val user = userDao.getUserByUsername(username) ?: return@withContext null
            val passHash = passwordRaw.hashCode().toString()
            if (user.passwordHash == passHash) {
                user
            } else {
                null
            }
        }
    }

    fun observeUser(username: String): Flow<UserEntity?> = userDao.observeUserByUsername(username)

    suspend fun saveOnboardingProgress(
        username: String,
        lifestyleId: String,
        assetIndex: Int,
        characterId: String,
        companionCustomName: String,
        companionCustomPersona: String
    ) {
        withContext(Dispatchers.IO) {
            val user = userDao.getUserByUsername(username) ?: return@withContext
            val updated = user.copy(
                lifestyleId = lifestyleId,
                assetIndex = assetIndex,
                characterId = characterId,
                companionCustomName = companionCustomName,
                companionCustomPersona = companionCustomPersona,
                hasCompletedJourney = true
            )
            userDao.updateUser(updated)
            enqueueUserSync(updated)
            triggerSyncQueueFlush(username)
        }
    }

    suspend fun updateFinancialProgress(
        username: String,
        savingTarget: Long,
        savingCurrent: Long,
        securityFund: Long,
        isaContribution: Long
    ) {
        withContext(Dispatchers.IO) {
            val user = userDao.getUserByUsername(username) ?: return@withContext
            val updated = user.copy(
                savingTarget = savingTarget,
                savingCurrent = savingCurrent,
                securityFund = securityFund,
                isaContribution = isaContribution
            )
            userDao.updateUser(updated)
            enqueueUserSync(updated)
            triggerSyncQueueFlush(username)
        }
    }

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
            userDao.updateUser(updated)
            enqueueUserSync(updated)
            triggerSyncQueueFlush(username)
        }
    }

    // 2. Financial Leak Tracking (지출 구멍 막기)
    fun observeLogs(username: String): Flow<List<FinancialLogEntity>> =
        financialLogDao.observeLogsForUser(username)

    fun observeTotalSolvedSaving(username: String): Flow<Long?> =
        financialLogDao.observeTotalSolvedSaving(username)

    suspend fun addLog(username: String, title: String, amount: Long, category: String) {
        withContext(Dispatchers.IO) {
            val newLog = FinancialLogEntity(
                username = username,
                title = title,
                amount = amount,
                category = category
            )
            val insertedId = financialLogDao.insertLog(newLog)
            val insertedLog = newLog.copy(id = insertedId)
            enqueueLogSync(insertedLog)
            triggerSyncQueueFlush(username)
        }
    }

    suspend fun solveLog(log: FinancialLogEntity, isSolved: Boolean) {
        withContext(Dispatchers.IO) {
            val updated = log.copy(isSolved = isSolved)
            financialLogDao.updateLog(updated)
            enqueueLogSync(updated)
            triggerSyncQueueFlush(log.username)
        }
    }

    suspend fun deleteLog(logId: Long) {
        withContext(Dispatchers.IO) {
            val log = financialLogDao.getLogById(logId)
            if (log != null) {
                enqueueLogDelete(log.username, logId)
                triggerSyncQueueFlush(log.username)
            }
            financialLogDao.deleteLogById(logId)
        }
    }

    fun saveCustomApiKey(key: String) {
        val sharedPref = context.getSharedPreferences("retirement_settings", Context.MODE_PRIVATE)
        sharedPref.edit().putString("custom_gemini_api_key", key).apply()
    }

    fun getCustomApiKey(): String {
        val sharedPref = context.getSharedPreferences("retirement_settings", Context.MODE_PRIVATE)
        return sharedPref.getString("custom_gemini_api_key", "") ?: ""
    }

    suspend fun testGeminiApiKey(apiKeyToTest: String): Pair<Boolean, String> {
        return withContext(Dispatchers.IO) {
            if (apiKeyToTest.isBlank()) {
                return@withContext Pair(false, "API 키가 비어 있습니다. 올바른 키를 입력해 주세요.")
            }
            val prompt = "API 연결 테스트 성공 여부 확인용 메시지입니다. '연결 성공!' 이라는 짤막한 답변 하나만 한글로 돌려주세요."
            val jsonMediaType = "application/json; charset=utf-8".toMediaType()
            val requestBodyJson = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        })
                    })
                }
                put("contents", contentsArray)
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.2)
                    put("maxOutputTokens", 50)
                })
            }.toString()

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKeyToTest")
                .post(requestBodyJson.toRequestBody(jsonMediaType))
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string() ?: ""
                        val responseJson = JSONObject(bodyString)
                        val candidates = responseJson.getJSONArray("candidates")
                        if (candidates.length() > 0) {
                            val content = candidates.getJSONObject(0).getJSONObject("content")
                            val parts = content.getJSONArray("parts")
                            if (parts.length() > 0) {
                                val reply = parts.getJSONObject(0).getString("text").trim()
                                return@withContext Pair(true, reply)
                            }
                        }
                        return@withContext Pair(true, "연결에 성공했으나 예상치 못한 응답 형식입니다.")
                    } else {
                        val errorBody = response.body?.string() ?: ""
                        Log.e("RetirementRepository", "Test API key failed: ${response.code} - $errorBody")
                        val errMsg = try {
                            val errJson = JSONObject(errorBody)
                            val errorObj = errJson.getJSONObject("error")
                            errorObj.getString("message")
                        } catch (e: Exception) {
                            "API 응답 코드: ${response.code}"
                        }
                        return@withContext Pair(false, "연결 실패: $errMsg")
                    }
                }
            } catch (e: Exception) {
                Log.e("RetirementRepository", "Test API key call failed", e)
                return@withContext Pair(false, "연결 오류: 인터넷 상태나 API 키가 올바른지 확인해 주세요. (${e.localizedMessage})")
            }
        }
    }

    // 2-B. Chat History Methods
    fun observeChatMessages(username: String): Flow<List<ChatMessage>> = flow {
        if (!_isFirebaseAvailable) {
            emit(emptyList())
            return@flow
        }
        val firestore = firebaseFirestore ?: return@flow
        try {
            val querySnapshot = suspendCoroutine<com.google.firebase.firestore.QuerySnapshot?> { continuation ->
                firestore.collection("users").document(username)
                    .collection("chats")
                    .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.ASCENDING)
                    .get()
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) continuation.resume(task.result)
                        else continuation.resume(null)
                    }
            }
            if (querySnapshot != null) {
                val messages = querySnapshot.documents.mapNotNull { doc ->
                    try {
                        ChatMessage(
                            id = doc.getString("id") ?: "",
                            role = doc.getString("role") ?: "user",
                            content = doc.getString("content") ?: "",
                            timestamp = doc.getLong("timestamp") ?: 0L
                        )
                    } catch (e: Exception) { null }
                }
                emit(messages)
            } else {
                emit(emptyList())
            }
        } catch (e: Exception) {
            Log.e("RetirementRepository", "Error fetching chat history: ${e.message}")
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    suspend fun saveChatMessage(username: String, message: ChatMessage) {
        if (!_isFirebaseAvailable) return
        val firestore = firebaseFirestore ?: return
        try {
            val dataMap = hashMapOf(
                "id" to message.id,
                "role" to message.role,
                "content" to message.content,
                "timestamp" to message.timestamp
            )
            suspendCoroutine<Unit> { continuation ->
                firestore.collection("users").document(username)
                    .collection("chats").document(message.id)
                    .set(dataMap)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) continuation.resume(Unit)
                        else continuation.resume(Unit)
                    }
            }
        } catch (e: Exception) {
            Log.e("RetirementRepository", "Error saving chat message: ${e.message}")
        }
    }

    suspend fun sendChatMessageAndGetReply(
        apiKey: String,
        history: List<ChatMessage>,
        newMessage: String,
        systemInstruction: String
    ): String {
        return withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) return@withContext "API 키가 설정되지 않았습니다. 설정에서 등록해주세요."

            val jsonMediaType = "application/json; charset=utf-8".toMediaType()
            val requestBodyJson = JSONObject().apply {
                val contentsArray = JSONArray()
                
                // Add history
                history.forEach { msg ->
                    contentsArray.put(JSONObject().apply {
                        put("role", msg.role)
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", msg.content)
                            })
                        })
                    })
                }
                
                // Add new message
                contentsArray.put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", newMessage)
                        })
                    })
                })
                
                put("contents", contentsArray)

                // Add system instructions
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", systemInstruction)
                        })
                    })
                })

                // Recognition and Generation Parameters Optimization
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.7)
                    put("topP", 0.95)
                    put("topK", 40)
                    put("maxOutputTokens", 1024)
                })
            }.toString()

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
                .post(requestBodyJson.toRequestBody(jsonMediaType))
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext "인생날씨 분석 중 에러가 발생했습니다. (API 응답 코드: ${response.code})"
                    val bodyString = response.body?.string() ?: return@withContext "응답 내용이 없습니다."
                    val responseJson = JSONObject(bodyString)
                    val candidates = responseJson.getJSONArray("candidates")
                    if (candidates.length() > 0) {
                        val content = candidates.getJSONObject(0).getJSONObject("content")
                        val parts = content.getJSONArray("parts")
                        if (parts.length() > 0) {
                            return@withContext parts.getJSONObject(0).getString("text")
                        }
                    }
                    "새로운 답변을 생성하지 못했습니다."
                }
            } catch (e: Exception) {
                Log.e("RetirementRepository", "Gemini API call failed", e)
                "통신 중 에러가 발생했습니다. 잠시 후 다시 시도해주세요."
            }
        }
    }

    // 3. Gemini API Chat/Advice Service
    suspend fun fetchGeminiAdvice(
        characterName: String,
        characterType: String,
        lifestyleTitle: String,
        assetRange: String,
        savingTarget: Long,
        savingCurrent: Long,
        companionCustomName: String = "",
        companionCustomPersona: String = "",
        customPrompt: String? = null,
        currentAge: Int = 35,
        targetAge: Int = 60,
        expectedLifespan: Int = 90,
        monthlyExpenses: Long = 300_0000L,
        totalSolvedSavings: Long = 0L
    ): String {
        return withContext(Dispatchers.IO) {
            // Fetch API Key from SharedPreferences first, fall back to BuildConfig (automatically injected via Secrets Gradle Plugin from .env)
            var apiKey = getCustomApiKey()
            if (apiKey.isEmpty()) {
                apiKey = try {
                    val clazz = Class.forName("com.example.BuildConfig")
                    val field = clazz.getField("GEMINI_API_KEY")
                    field.get(null) as String
                } catch (e: Exception) {
                    Log.e("RetirementRepository", "Failed to load GEMINI_API_KEY from BuildConfig: ${e.message}")
                    ""
                }
            }

            val finalCharName = if (companionCustomName.isNotBlank()) companionCustomName else characterName
            val finalCharPersona = if (companionCustomPersona.isNotBlank()) companionCustomPersona else characterType

            val totalPrepared = savingCurrent + totalSolvedSavings
            val progressPercent = ((totalPrepared.toDouble() / savingTarget.coerceAtLeast(1L)) * 100).coerceIn(0.0, 100.0)
            val gap = (savingTarget - totalPrepared).coerceAtLeast(0L)
            val yearsRemaining = (targetAge - currentAge).coerceAtLeast(1)
            val monthsRemaining = yearsRemaining * 12
            val monthlyNeeded = if (gap > 0) gap / monthsRemaining else 0L

            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                val progressStr = String.format("%.1f", progressPercent)
                val gapMan = gap / 10_000L
                val monthlyNeededMan = monthlyNeeded / 10_000L
                val totalPreparedMan = totalPrepared / 10_000L
                val savingTargetMan = savingTarget / 10_000L

                val personaAdvice = when {
                    characterName.contains("거북") -> {
                        "🐢 **원금 보존형 안정 설계:**\n" +
                        "1. **예적금 사다리 & 국채 투자:** 남은 ${yearsRemaining}년 동안 매월 ${monthlyNeededMan}만원씩 고금리 예금 및 3년/5년 만기 개인투자용 국채에 분산 예치해 안정적 이자를 수령하세요.\n" +
                        "2. **연금저축 & IRP 한도 채우기:** 연 900만원 세액공제를 최대로 챙겨 매년 연말정산 환급금을 은퇴 금고로 재투자하세요.\n" +
                        "3. **비상금 6개월치 분리:** 갑작스러운 지출로 원금을 깨지 않도록 CMA 파킹통장에 비상금을 단단히 보관하세요."
                    }
                    characterName.contains("다람") -> {
                        "🐿️ **황금 밸런스 배당/성장 설계:**\n" +
                        "1. **월배당 ETF & 우량 고배당주 시스템:** 은퇴 후 필요한 월 ${monthlyExpenses / 10_000L}만원의 현금 흐름을 위해, 4% 배당률의 우량 배당 다우존스 ETF를 적립식 매수하세요.\n" +
                        "2. **지출 구멍 절약 자동 이체:** 지출 구멍을 막아 아낀 돈을 매월 첫날 인덱스 펀드에 자동 이체하여 복리 효과를 극대화하세요.\n" +
                        "3. **ISA 계좌 3년 주기 비과세 활용:** 배당소득세(15.4%)를 아껴 재투자하는 영리한 굴리기를 실천하세요."
                    }
                    else -> {
                        "🦅 **성장 추구 글로벌 자산배분 설계:**\n" +
                        "1. **글로벌 지수(S&P500/나스닥) 적립식 투자:** 남은 ${yearsRemaining}년 동안 인플레이션을 압도하는 미국 대표 지수에 분할 적립하세요.\n" +
                        "2. **생애주기 TDF(Target Date Fund) 자산배분:** ${targetAge}세 은퇴 시점에 맞춰 채권 비중이 자동으로 조절되는 TDF로 리스크를 관리하세요.\n" +
                        "3. **은퇴 시점 현금 버퍼 확보:** 은퇴 직전 2~3년 치 생활비는 안전자산으로 단계적 전환하여 시장 하락장에 대비하세요."
                    }
                }

                val statusSummary = if (gap == 0L) {
                    "🎉 이미 목표 은퇴 자금 ${savingTargetMan}만원을 100% 달성하셨습니다!"
                } else {
                    "📊 현재 목표 달성률은 **${progressStr}%**이며, 목표치까지 **${gapMan}만원**의 격차가 있습니다. ${targetAge}세 은퇴까지 약 **${yearsRemaining}년(${monthsRemaining}개월)** 동안 매월 **약 ${monthlyNeededMan}만원**을 꾸준히 적립하시면 충분히 도달할 수 있습니다!"
                }

                return@withContext "🌤️ **${finalCharName}의 은퇴 자산 & 목표치 정밀 분석 리포트**\n\n" +
                        "$statusSummary\n\n" +
                        "💡 **${lifestyleTitle} 라이프를 위한 은퇴 전략 조언:**\n" +
                        "$personaAdvice\n\n" +
                        "💬 궁금한 점이 있으시다면 무엇이든 물어보세요! 함께 한 걸음씩 차근차근 나아가면 꿈꾸던 은퇴가 현실이 됩니다."
            }

            val systemRole = "당신은 사용자의 은퇴 설계를 전담하는 다정하고 전문적인 금융 동반자 AI 캐릭터인 '${finalCharName}'입니다.\n" +
                    "캐릭터 페르소나 및 성향: '${finalCharPersona}' (유형: ${characterType}).\n" +
                    "사용자의 재무 및 은퇴 목표 데이터:\n" +
                    "- 현재 나이: ${currentAge}세 / 희망 은퇴 연령: ${targetAge}세 / 기대 수명: ${expectedLifespan}세\n" +
                    "- 남은 준비 기간: ${yearsRemaining}년 (${monthsRemaining}개월)\n" +
                    "- 현재 준비된 총 자산: ${totalPrepared / 10_000L}만원 (기본 자산 ${savingCurrent / 10_000L}만원 + 지출 절약액 ${totalSolvedSavings / 10_000L}만원)\n" +
                    "- 희망 목표 자산액: ${savingTarget / 10_000L}만원 (달성률: ${String.format("%.1f", progressPercent)}%)\n" +
                    "- 목표까지의 재무 격차(부족액): ${gap / 10_000L}만원\n" +
                    "- 매월 필요한 저축/투자액: 약 ${monthlyNeeded / 10_000L}만원\n" +
                    "- 은퇴 후 희망 월 생활비: ${monthlyExpenses / 10_000L}만원, 선호 라이프스타일: '${lifestyleTitle}'\n\n" +
                    "답변 지침:\n" +
                    "1. 사용자의 현재 자산과 목표치 간의 격차(부족액/초과액)와 남은 준비 기간을 구체적인 숫자로 짚어주세요.\n" +
                    "2. 대한민국 현실에 맞는 실현 가능한 3단계 은퇴 전략(예: 연금저축/IRP/ISA 활용, 4% 안전인출룰, 월배당/지수투자, 생활비 지출 통제)을 캐릭터 성격에 맞는 부드럽고 든든한 어조(해요체)로 조언해 주세요.\n" +
                    "3. 중학생도 이해하기 쉽게 비유를 들어 설명하고, 비관적인 비난 대신 긍정적인 용기와 실천 팁을 주세요."

            val userText = customPrompt ?: "나의 현재 자산(${totalPrepared / 10_000L}만원)과 목표 자산(${savingTarget / 10_000L}만원), 은퇴 연령(${targetAge}세) 데이터를 종합적으로 분석해서, 남은 기간 동안 실천할 수 있는 가장 현실적이고 든든한 은퇴 전략 조언을 해줘."

            val prompt = "$systemRole\n\n사용자 요청: $userText\n\n답변:"

            val jsonMediaType = "application/json; charset=utf-8".toMediaType()
            val requestBodyJson = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        })
                    })
                }
                put("contents", contentsArray)

                // Recognition and Generation Parameters Optimization
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.7)
                    put("topP", 0.95)
                    put("topK", 40)
                    put("maxOutputTokens", 1024)
                })
            }.toString()

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
                .post(requestBodyJson.toRequestBody(jsonMediaType))
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext "인생날씨 분석 중 잠깐의 안개가 끼었습니다. (API 응답 코드: ${response.code}). 하지만 회원님의 미래는 여전히 맑음입니다! 🐢"
                    }
                    val bodyString = response.body?.string() ?: return@withContext "연결 상태가 잠시 불안정합니다."
                    val responseJson = JSONObject(bodyString)
                    val candidates = responseJson.getJSONArray("candidates")
                    if (candidates.length() > 0) {
                        val content = candidates.getJSONObject(0).getJSONObject("content")
                        val parts = content.getJSONArray("parts")
                        if (parts.length() > 0) {
                            return@withContext parts.getJSONObject(0).getString("text")
                        }
                    }
                    "새로운 내일의 날씨 예보를 불러오고 있습니다."
                }
            } catch (e: Exception) {
                Log.e("RetirementRepository", "Gemini API call failed", e)
                "은퇴 설계 엔진과 통신 중 에러가 발생했습니다. 잠시 후 맑음 날씨로 다시 연락드리겠습니다!"
            }
        }
    }

    companion object {
        @Volatile
        private var cachedIndicators: EconomicIndicators? = null
        @Volatile
        private var lastIndicatorsFetchTime: Long = 0L
        private const val CACHE_TTL_MS = 4 * 60 * 60 * 1000L // 4 hours
    }

    suspend fun refreshEconomicIndicators(): EconomicIndicators = withContext(Dispatchers.IO) {
        val fresh = EconomicIndicators(
            inflationRate = 2.6, // 소비자물가상승률 (한국은행 ECOS)
            baseInterestRate = 3.50, // 한국은행 기준금리
            kospiIndex = 2685.42, // 코스피 지수 (KRX)
            kospiChangePercent = 0.45,
            weatherStatus = "맑음",
            weatherDescription = "안정적인 인플레이션과 균형 잡힌 주식시장 속에서 은퇴 자금을 차곡차곡 쌓아가기 좋은 계절입니다."
        )
        cachedIndicators = fresh
        lastIndicatorsFetchTime = System.currentTimeMillis()
        fresh
    }

    // 4. Simulated Economic Weather Indicators (From ECOS/KRX Concepts with 4-hour Battery Cache)
    fun fetchEconomicIndicators(): Flow<EconomicIndicators> = flow {
        val cached = cachedIndicators
        val now = System.currentTimeMillis()
        if (cached != null && (now - lastIndicatorsFetchTime < CACHE_TTL_MS)) {
            emit(cached)
        } else {
            val fresh = refreshEconomicIndicators()
            emit(fresh)
        }
    }.flowOn(Dispatchers.IO)

    suspend fun syncPendingDataToFirestore(username: String): Boolean = withContext(Dispatchers.IO) {
        flushSyncQueue(username)
    }

    fun observePendingSyncCount(username: String): Flow<Int> =
        syncQueueDao.observePendingCountForUser(username)
}

data class EconomicIndicators(
    val inflationRate: Double,
    val baseInterestRate: Double,
    val kospiIndex: Double,
    val kospiChangePercent: Double,
    val weatherStatus: String,
    val weatherDescription: String
)

data class CalculatorData(
    val currentAge: Int,
    val targetAge: Int,
    val expectedLifespan: Int,
    val monthlyExpenses: Long,
    val currentAssets: Long
)

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: String = "user", // "user" or "model"
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
