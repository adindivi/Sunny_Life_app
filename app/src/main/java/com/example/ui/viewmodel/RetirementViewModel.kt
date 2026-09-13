package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.FinancialLogEntity
import com.example.data.database.UserEntity
import com.example.data.repository.EconomicIndicators
import com.example.data.repository.RetirementRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class RetirementViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = RetirementRepository(application)

    // Current Screen State
    private val _currentScreen = MutableStateFlow<Screen>(Screen.Login)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    // Auth States
    private val _loggedInUser = MutableStateFlow<UserEntity?>(null)
    val loggedInUser: StateFlow<UserEntity?> = _loggedInUser.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _authSuccessMessage = MutableStateFlow<String?>(null)
    val authSuccessMessage: StateFlow<String?> = _authSuccessMessage.asStateFlow()

    // Economic Indicators
    private val _economicIndicators = MutableStateFlow<EconomicIndicators?>(null)
    val economicIndicators: StateFlow<EconomicIndicators?> = _economicIndicators.asStateFlow()

    // AI Advice State
    private val _aiAdvice = MutableStateFlow<String>("")
    val aiAdvice: StateFlow<String> = _aiAdvice.asStateFlow()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    // Calculator Specific AI Advice States
    private val _calculatorAdvice = MutableStateFlow<String>("")
    val calculatorAdvice: StateFlow<String> = _calculatorAdvice.asStateFlow()

    private val _isCalculatorAiLoading = MutableStateFlow(false)
    val isCalculatorAiLoading: StateFlow<Boolean> = _isCalculatorAiLoading.asStateFlow()

    // API Key States
    private val _customApiKey = MutableStateFlow("")
    val customApiKey: StateFlow<String> = _customApiKey.asStateFlow()

    private val _testResult = MutableStateFlow<Pair<Boolean, String>?>(null)
    val testResult: StateFlow<Pair<Boolean, String>?> = _testResult.asStateFlow()

    private val _isTestingApi = MutableStateFlow(false)
    val isTestingApi: StateFlow<Boolean> = _isTestingApi.asStateFlow()

    val isFirebaseAvailable: Boolean get() = repository.isFirebaseAvailable

    // Network Connectivity & Offline Sync States
    val isOnline: StateFlow<Boolean> = repository.connectivityObserver.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), repository.connectivityObserver.isCurrentlyConnected())

    val pendingSyncCount: StateFlow<Int> = loggedInUser
        .filterNotNull()
        .flatMapLatest { user -> repository.observePendingSyncCount(user.username) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Financial Logs (Leaks) list
    val financialLogs: StateFlow<List<FinancialLogEntity>> = loggedInUser
        .filterNotNull()
        .flatMapLatest { user -> repository.observeLogs(user.username) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalSolvedSavings: StateFlow<Long> = loggedInUser
        .filterNotNull()
        .flatMapLatest { user -> repository.observeTotalSolvedSaving(user.username) }
        .map { it ?: 0L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    private val _calculatorData = MutableStateFlow<com.example.data.repository.CalculatorData?>(null)
    val calculatorData: StateFlow<com.example.data.repository.CalculatorData?> = _calculatorData.asStateFlow()

    fun loadCalculatorData() {
        val user = _loggedInUser.value ?: return
        viewModelScope.launch {
            val data = repository.loadCalculatorDataFromFirestore(user.username)
            if (data != null) {
                _calculatorData.value = data
            }
        }
    }

    fun saveCalculatorData(
        currentAge: Int,
        targetAge: Int,
        expectedLifespan: Int,
        monthlyExpenses: Long,
        currentAssets: Long
    ) {
        val user = _loggedInUser.value ?: return
        viewModelScope.launch {
            val data = com.example.data.repository.CalculatorData(
                currentAge, targetAge, expectedLifespan, monthlyExpenses, currentAssets
            )
            repository.saveCalculatorDataToFirestore(user.username, data)
            _calculatorData.value = data
            com.example.data.worker.UserDataSyncWorker.scheduleOneTime(getApplication(), user.username)
        }
    }

    fun flushPendingSync() {
        val user = _loggedInUser.value ?: return
        viewModelScope.launch {
            repository.flushSyncQueue(user.username)
        }
    }

    init {
        _customApiKey.value = repository.getCustomApiKey()
        viewModelScope.launch {
            repository.fetchEconomicIndicators().collect {
                _economicIndicators.value = it
            }
        }
        // Auto-flush pending offline sync queue whenever device comes online
        viewModelScope.launch {
            repository.connectivityObserver.observe().collect { online ->
                if (online) {
                    val user = _loggedInUser.value
                    if (user != null) {
                        repository.flushSyncQueue(user.username)
                    }
                }
            }
        }
    }

    fun saveCustomApiKey(key: String) {
        repository.saveCustomApiKey(key)
        _customApiKey.value = key
    }

    fun testCustomApiKey(key: String) {
        viewModelScope.launch {
            _isTestingApi.value = true
            _testResult.value = null
            val result = repository.testGeminiApiKey(key)
            _testResult.value = result
            _isTestingApi.value = false
        }
    }

    fun clearTestResult() {
        _testResult.value = null
    }

    // Auth Actions
    fun login(username: String, passwordRaw: String) {
        viewModelScope.launch {
            _authError.value = null
            if (username.isBlank() || passwordRaw.isBlank()) {
                _authError.value = "아이디와 비밀번호를 모두 입력해주세요."
                return@launch
            }
            val user = repository.loginUser(username, passwordRaw)
            if (user != null) {
                _loggedInUser.value = user
                _authSuccessMessage.value = "${user.nickname}님, 환영합니다!"
                if (user.hasCompletedJourney) {
                    _currentScreen.value = Screen.Dashboard
                    loadAiAdvice()
                } else {
                    _currentScreen.value = Screen.OnboardingLifestyle
                }
            } else {
                _authError.value = "아이디 또는 비밀번호가 일치하지 않습니다."
            }
        }
    }

    fun register(username: String, passwordRaw: String, nickname: String) {
        viewModelScope.launch {
            _authError.value = null
            _authSuccessMessage.value = null
            if (username.isBlank() || passwordRaw.isBlank() || nickname.isBlank()) {
                _authError.value = "모든 정보를 올바르게 기입해주세요."
                return@launch
            }
            val success = repository.registerUser(username, passwordRaw, nickname)
            if (success) {
                _authSuccessMessage.value = "회원가입이 완료되었습니다. 로그인해 주세요."
            } else {
                _authError.value = "이미 가입되어 있는 아이디입니다."
            }
        }
    }

    fun logout() {
        _loggedInUser.value = null
        _currentScreen.value = Screen.Login
        _aiAdvice.value = ""
    }

    // Onboarding Actions
    fun completeLifestyle(lifestyleId: String) {
        val currentUser = _loggedInUser.value ?: return
        viewModelScope.launch {
            val updatedUser = currentUser.copy(lifestyleId = lifestyleId)
            _loggedInUser.value = updatedUser
            _currentScreen.value = Screen.OnboardingAsset
        }
    }

    fun completeAsset(assetIndex: Int) {
        val currentUser = _loggedInUser.value ?: return
        viewModelScope.launch {
            val updatedUser = currentUser.copy(assetIndex = assetIndex)
            _loggedInUser.value = updatedUser
            _currentScreen.value = Screen.OnboardingCharacter
        }
    }

    fun completeCharacter(characterId: String, companionName: String, companionPersona: String) {
        val currentUser = _loggedInUser.value ?: return
        viewModelScope.launch {
            repository.saveOnboardingProgress(
                username = currentUser.username,
                lifestyleId = currentUser.lifestyleId,
                assetIndex = currentUser.assetIndex,
                characterId = characterId,
                companionCustomName = companionName,
                companionCustomPersona = companionPersona
            )
            // Refresh local user state from DB
            val freshUser = repository.observeUser(currentUser.username).firstOrNull() ?: currentUser.copy(
                characterId = characterId,
                companionCustomName = companionName,
                companionCustomPersona = companionPersona,
                hasCompletedJourney = true
            )
            _loggedInUser.value = freshUser
            _currentScreen.value = Screen.Dashboard
            loadAiAdvice()
        }
    }

    // AI Character Advice Core
    fun loadAiAdvice() {
        val user = _loggedInUser.value ?: return
        if (!user.hasCompletedJourney) return

        viewModelScope.launch {
            _isAiLoading.value = true
            val charDetails = CompanionCharacter.fromId(user.characterId)
            val lifestyleDetails = RetirementLifestyle.fromId(user.lifestyleId)
            val assetDetails = AssetRange.fromIndex(user.assetIndex)
            val calc = _calculatorData.value
            val solvedSavings = totalSolvedSavings.value

            val advice = repository.fetchGeminiAdvice(
                characterName = charDetails.displayName,
                characterType = charDetails.type,
                lifestyleTitle = lifestyleDetails.title,
                assetRange = assetDetails.title,
                savingTarget = user.savingTarget,
                savingCurrent = user.savingCurrent,
                companionCustomName = user.companionCustomName,
                companionCustomPersona = user.companionCustomPersona,
                currentAge = calc?.currentAge ?: 35,
                targetAge = calc?.targetAge ?: 60,
                expectedLifespan = calc?.expectedLifespan ?: 90,
                monthlyExpenses = (calc?.monthlyExpenses ?: 300L) * 10_000L,
                totalSolvedSavings = solvedSavings
            )
            _aiAdvice.value = advice
            _isAiLoading.value = false
        }
    }

    fun askCompanionCustomQuestion(question: String) {
        val user = _loggedInUser.value ?: return
        viewModelScope.launch {
            _isAiLoading.value = true
            val charDetails = CompanionCharacter.fromId(user.characterId)
            val lifestyleDetails = RetirementLifestyle.fromId(user.lifestyleId)
            val assetDetails = AssetRange.fromIndex(user.assetIndex)
            val calc = _calculatorData.value
            val solvedSavings = totalSolvedSavings.value

            val advice = repository.fetchGeminiAdvice(
                characterName = charDetails.displayName,
                characterType = charDetails.type,
                lifestyleTitle = lifestyleDetails.title,
                assetRange = assetDetails.title,
                savingTarget = user.savingTarget,
                savingCurrent = user.savingCurrent,
                companionCustomName = user.companionCustomName,
                companionCustomPersona = user.companionCustomPersona,
                customPrompt = question,
                currentAge = calc?.currentAge ?: 35,
                targetAge = calc?.targetAge ?: 60,
                expectedLifespan = calc?.expectedLifespan ?: 90,
                monthlyExpenses = (calc?.monthlyExpenses ?: 300L) * 10_000L,
                totalSolvedSavings = solvedSavings
            )
            _aiAdvice.value = advice
            _isAiLoading.value = false
        }
    }

    fun loadCalculatorAdvice(
        currentAge: Int,
        targetAge: Int,
        expectedLifespan: Int,
        monthlyExpensesMan: Long,
        simpleRequiredFund: Long,
        yieldRequiredFund: Long
    ) {
        val user = _loggedInUser.value ?: return
        viewModelScope.launch {
            _isCalculatorAiLoading.value = true
            
            val charDetails = CompanionCharacter.fromId(user.characterId)
            val lifestyleDetails = RetirementLifestyle.fromId(user.lifestyleId)
            val assetDetails = AssetRange.fromIndex(user.assetIndex)
            
            val customPrompt = """
                내가 입력한 은퇴 계산기 입력값은 다음과 같아:
                - 현재 나이: ${currentAge}세
                - 은퇴 희망 나이: ${targetAge}세
                - 기대 수명: ${expectedLifespan}세 (은퇴 후 준비 기간: ${expectedLifespan - targetAge}년)
                - 은퇴 후 월 예상 지출액: ${monthlyExpensesMan}만원
                - 단순 수명 대비 필요한 은퇴 자금: ${simpleRequiredFund / 10000}만원
                - 연 4% 인출 규칙을 적용한 추천 은퇴 자금: ${yieldRequiredFund / 10000}만원
                
                이 결과를 분석하고 내 캐릭터 성향인 '${charDetails.displayName}(${charDetails.type})'으로서 나에게 딱 맞춘 현실적이고 따뜻한 은퇴 준비 꿀팁을 전수해줘.
                특히 월 지출액을 줄이거나 저축률을 효과적으로 높일 수 있는 구체적인 실행 방안(예: 비정기 지출 통제, 연금 저축 계좌 활용, 소액 저축 습관 등)을 2~3가지 제안해줘. 친근하게 이모지도 많이 써서 반말로 혹은 존댓말로 아주 정답게 조언해줘!
            """.trimIndent()

            val advice = repository.fetchGeminiAdvice(
                characterName = charDetails.displayName,
                characterType = charDetails.type,
                lifestyleTitle = lifestyleDetails.title,
                assetRange = assetDetails.title,
                savingTarget = user.savingTarget,
                savingCurrent = user.savingCurrent,
                companionCustomName = user.companionCustomName,
                companionCustomPersona = user.companionCustomPersona,
                customPrompt = customPrompt
            )
            
            if (advice.startsWith("🌤️ 기본 조언:")) {
                val localTip = generateLocalCalculatorTip(
                    charDetails,
                    currentAge,
                    targetAge,
                    expectedLifespan,
                    monthlyExpensesMan,
                    simpleRequiredFund,
                    yieldRequiredFund
                )
                _calculatorAdvice.value = localTip
            } else {
                _calculatorAdvice.value = advice
            }
            _isCalculatorAiLoading.value = false
        }
    }

    private fun generateLocalCalculatorTip(
        character: CompanionCharacter,
        currentAge: Int,
        targetAge: Int,
        expectedLifespan: Int,
        monthlyExpensesMan: Long,
        simpleRequiredFund: Long,
        yieldRequiredFund: Long
    ): String {
        val retirementYears = expectedLifespan - targetAge
        val builder = StringBuilder()
        
        builder.append("${character.emoji} **${character.displayName}**의 맞춤형 은퇴 설계 레시피:\n\n")
        
        // 1. Expense check
        if (monthlyExpensesMan >= 400L) {
            builder.append("💡 **월 지출액이 조금 높은 편이에요!** 은퇴 후 월 ${monthlyExpensesMan}만원은 현재 가치 기준으로 꽤 풍요로운 삶을 의미해요. 하지만 그만큼 모아야 할 목표액이 기하급수적으로 늘어난답니다. 통신비, 구독 서비스, 보험료 등의 고정 지출을 미리 다이어트하면 목표 달성일이 훨씬 앞당겨질 수 있어요!\n\n")
        } else {
            builder.append("💡 **합리적인 월 예상 지출 계획입니다!** 월 ${monthlyExpensesMan}만원 내외의 은퇴 생활비는 매우 현실적이고 영리한 계획이에요. 여기에 정기적인 비정기 지출(경조사비, 세금 등)만 추가로 저축 주머니를 만들어 관리하면 완벽합니다!\n\n")
        }
        
        // 2. Character specific tip
        when (character) {
            CompanionCharacter.TURTLE -> {
                builder.append("🐢 **안전제일 거북이의 꿀팁:**\n")
                builder.append("- **예금·채권 만기 사다리 타기:** 원금 손실을 극도로 꺼리시는 만큼, 매달 일정 금액을 고금리 예금이나 국채 사다리(Laddering) 방식으로 나누어 묶어두세요. 안정성과 현금 흐름을 동시에 잡을 수 있습니다.\n")
                builder.append("- **연금저축 및 IRP 납입 한도 채우기:** 매년 최대 900만 원까지 세액공제 혜택을 챙기고, 연금 수령 시 저율과세(3.3~5.5%) 혜택을 극대화하여 나가는 세금 구멍을 막으세요.\n")
            }
            CompanionCharacter.SQUIRREL -> {
                builder.append("🐿️ **황금밸런스 다람쥐의 꿀팁:**\n")
                builder.append("- **고배당주 & ETF 포트폴리오:** 연 4% 법칙 달성을 위해 매달 배당금이 입금되는 월배당 ETF나 우량 고배당주를 차곡차곡 모으세요. 도토리가 스스로 자라나는 나무를 심는 셈입니다.\n")
                builder.append("- **소액 저축 습관화:** 하루 커피 한 잔 값(5,000원)을 매일 자동으로 투자 통장에 이체하는 잔돈 저축 시스템을 구축해 보세요. 시간이 흐르면 엄청난 복리 효과가 생깁니다.\n")
            }
            CompanionCharacter.EAGLE -> {
                builder.append("🦅 **기회포착 독수리의 꿀팁:**\n")
                builder.append("- **적립식 글로벌 지수 추종 투자:** 시장 변동성을 기회로 삼아, 미국 S&P500이나 나스닥100 등 글로벌 우량 자산에 매월 일정액을 기계적으로 적립식 투자하세요. 장기적으로 인플레이션을 시원하게 이겨낼 수 있습니다.\n")
                builder.append("- **ISA 계좌 적극 활용:** 비과세 및 분리과세 한도를 가득 채워 투자 수익에 대한 세금을 한 푼이라도 아끼는 영리한 공격 투자자가 되세요.\n")
            }
        }
        
        builder.append("\n🎯 **추천 행동 전략:**\n")
        val delayYears = if (currentAge < targetAge) "목표 은퇴 연령을 단 2년만 늦추거나" else ""
        builder.append("은퇴 후 생존 기간인 ${retirementYears}년 동안 원금을 갉아먹지 않으려면, 단순히 현금을 모아두기보다 연 4% 인출 규칙을 실행할 수 있는 배당·이자 자산 시스템을 구축하는 것이 은퇴 안전판을 만들어 줍니다. $delayYears 저축 금액을 월 15만 원만 늘려도 장기 은퇴 목표액에 도달하기 위한 부담이 절반으로 경감됩니다. 함께 천천히 준비해 나가요!")
        
        return builder.toString()
    }

    // Financial Actions
    fun addFinancialLeak(title: String, amount: Long, category: String) {
        val user = _loggedInUser.value ?: return
        viewModelScope.launch {
            repository.addLog(user.username, title, amount, category)
            com.example.data.worker.UserDataSyncWorker.scheduleOneTime(getApplication(), user.username)
        }
    }

    fun solveFinancialLeak(log: FinancialLogEntity, isSolved: Boolean) {
        val user = _loggedInUser.value ?: return
        viewModelScope.launch {
            repository.solveLog(log, isSolved)
            com.example.data.worker.UserDataSyncWorker.scheduleOneTime(getApplication(), user.username)
        }
    }

    fun deleteFinancialLeak(logId: Long) {
        val user = _loggedInUser.value ?: return
        viewModelScope.launch {
            repository.deleteLog(logId)
            com.example.data.worker.UserDataSyncWorker.scheduleOneTime(getApplication(), user.username)
        }
    }

    fun updateSavingProgress(target: Long, current: Long, securityFund: Long, isaContribution: Long) {
        val user = _loggedInUser.value ?: return
        viewModelScope.launch {
            repository.updateFinancialProgress(
                username = user.username,
                savingTarget = target,
                savingCurrent = current,
                securityFund = securityFund,
                isaContribution = isaContribution
            )
            com.example.data.worker.UserDataSyncWorker.scheduleOneTime(getApplication(), user.username)
            val freshUser = repository.observeUser(user.username).firstOrNull()
            if (freshUser != null) {
                _loggedInUser.value = freshUser
            }
        }
    }

    fun updateCompanionSettings(characterId: String, customName: String, customPersona: String) {
        val user = _loggedInUser.value ?: return
        viewModelScope.launch {
            repository.updateCompanionSettings(
                username = user.username,
                characterId = characterId,
                customName = customName,
                customPersona = customPersona
            )
            val freshUser = repository.observeUser(user.username).firstOrNull()
            if (freshUser != null) {
                _loggedInUser.value = freshUser
            }
        }
    }

    // ==================== CHAT WITH COMPANION CHARACTER SYSTEM ====================
    val chatMessages: StateFlow<List<com.example.data.repository.ChatMessage>> = _loggedInUser
        .filterNotNull()
        .flatMapLatest { user -> repository.observeChatMessages(user.username) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading: StateFlow<Boolean> = _isChatLoading.asStateFlow()

    fun initChatIfNeeded() {
        val user = _loggedInUser.value ?: return
        viewModelScope.launch {
            // Give the stream a brief moment, then check if it's empty
            kotlinx.coroutines.delay(500)
            if (chatMessages.value.isEmpty()) {
                val charDetails = CompanionCharacter.fromId(user.characterId)
                val finalCompName = if (user.companionCustomName.isNotBlank()) user.companionCustomName else charDetails.displayName
                val finalCompType = if (user.companionCustomPersona.isNotBlank()) user.companionCustomPersona else charDetails.type
                
                val welcomeMsg = "안녕하세요, ${user.nickname}님! ${charDetails.emoji} 회원님의 든든한 은퇴 설계를 함께할 동반자, **$finalCompName**($finalCompType)입니다!\n\n" +
                        "회원님께서 그리시는 '**${RetirementLifestyle.fromId(user.lifestyleId).title}**'의 꿈을 위해, " +
                        "현재 자산 상황과 은퇴 적립 금액을 바탕으로 가장 현명하고 따뜻한 조언을 해드릴게요.\n\n" +
                        "자산 운용법, 생활비 절약 비법, 또는 은퇴 후의 사소한 고민거리까지 무엇이든 편안하게 물어보세요! 🌤️"
                        
                val welcomeMessage = com.example.data.repository.ChatMessage(
                    id = "welcome_${System.currentTimeMillis()}",
                    role = "model",
                    content = welcomeMsg
                )
                repository.saveChatMessage(user.username, welcomeMessage)
            }
        }
    }

    fun sendChatMessage(text: String) {
        if (text.isBlank()) return
        val user = _loggedInUser.value ?: return
        val userMsg = com.example.data.repository.ChatMessage(
            id = java.util.UUID.randomUUID().toString(),
            role = "user",
            content = text
        )
        
        viewModelScope.launch {
            _isChatLoading.value = true
            
            // 1. Save user message
            repository.saveChatMessage(user.username, userMsg)
            
            // 2. Prepare context and request Gemini
            val apiKey = customApiKey.value
            if (apiKey.isBlank()) {
                repository.saveChatMessage(
                    user.username,
                    com.example.data.repository.ChatMessage(role = "model", content = "API 키가 설정되지 않았습니다. 설정에서 등록해주세요.")
                )
                _isChatLoading.value = false
                return@launch
            }
            
            val charDetails = CompanionCharacter.fromId(user.characterId)
            val finalCompName = if (user.companionCustomName.isNotBlank()) user.companionCustomName else charDetails.displayName
            val finalCompType = if (user.companionCustomPersona.isNotBlank()) user.companionCustomPersona else charDetails.type
            
            val systemInstruction = "당신은 사용자의 은퇴 설계를 돕는 전담 AI 동반자입니다.\n" +
                "이름: $finalCompName\n" +
                "성향/타입: $finalCompType\n" +
                "캐릭터 설명: ${charDetails.desc}\n\n" +
                "사용자 정보:\n" +
                "닉네임: ${user.nickname}\n" +
                "원하는 라이프스타일: ${RetirementLifestyle.fromId(user.lifestyleId).title}\n" +
                "현재 자산 수준: ${AssetRange.fromIndex(user.assetIndex).title}\n" +
                "현재 모은 금액: ${user.savingCurrent}원\n" +
                "목표 금액: ${user.savingTarget}원\n\n" +
                "당신의 캐릭터에 맞게 다정하고 안도감 가득한 목소리 톤을 완벽히 유지하며 아주 구체적이고 현실적인 한국 맞춤형 은퇴 조언(예: 생활비 설계, 연금 꿀팁 등)을 해주세요."
            
            val history = chatMessages.value.takeLast(10) // Send up to last 10 messages for context
            val reply = repository.sendChatMessageAndGetReply(
                apiKey = apiKey,
                history = history,
                newMessage = text,
                systemInstruction = systemInstruction
            )
            
            // 3. Save model reply
            val modelMsg = com.example.data.repository.ChatMessage(
                id = java.util.UUID.randomUUID().toString(),
                role = "model",
                content = reply
            )
            repository.saveChatMessage(user.username, modelMsg)
            
            _isChatLoading.value = false
        }
    }

    fun clearChat() {
        // Clearing chat in Firestore is more complex (requires deleting all documents in subcollection)
        // For now, we simply re-initialize the welcome message if empty
    }
}


sealed class Screen {
    object Login : Screen()
    object OnboardingLifestyle : Screen()
    object OnboardingAsset : Screen()
    object OnboardingCharacter : Screen()
    object Dashboard : Screen()
}

enum class RetirementLifestyle(val id: String, val title: String, val desc: String, val emoji: String) {
    RURAL("rural", "시골에서 여유롭게", "텃밭을 가꾸고 자연과 호흡하는 건강한 은퇴 생활", "🏡"),
    CITY("city", "도심에서 문화생활", "박물관, 음악회, 트렌디한 도심 문화를 누리는 편리함", "☕"),
    TRAVEL("travel", "1년에 두 번 해외여행", "미뤄왔던 드넓은 세계를 직접 탐험하는 모험 가득한 노후", "✈️"),
    HOBBY("hobby", "나만의 취미생활", "배우고 싶던 공예, 골프, 예술에 아낌없이 몰입하는 재미", "🎨");

    companion object {
        fun fromId(id: String) = values().firstOrNull { it.id == id } ?: RURAL
    }
}

enum class AssetRange(val index: Int, val title: String) {
    RANGE_0(0, "5천만 원 미만"),
    RANGE_1(1, "5천만 ~ 2억"),
    RANGE_2(2, "2억 ~ 5억"),
    RANGE_3(3, "5억 ~ 8억"),
    RANGE_4(4, "8억 ~ 10억"),
    RANGE_5(5, "10억 이상");

    companion object {
        fun fromIndex(index: Int) = values().firstOrNull { it.index == index } ?: RANGE_2
    }
}

enum class CompanionCharacter(val id: String, val displayName: String, val emoji: String, val type: String, val tags: String, val desc: String) {
    TURTLE(
        id = "turtle",
        displayName = "단단한 거북이",
        emoji = "🐢",
        type = "안전제일형",
        tags = "#원금사수 #절대안전",
        desc = "위험한 모험보다는 소중한 내 원금을 든든히 지키며, 물가상승률을 이기는 가장 보수적이고 영리한 길을 함께 걸어갑니다."
    ),
    SQUIRREL(
        id = "squirrel",
        displayName = "똑똑한 다람쥐",
        emoji = "🐿️",
        type = "황금밸런스형",
        tags = "#위험분산 #안전배분",
        desc = "안전 자산과 배당 자산에 분산 저장하여 도토리를 채웁니다. 흔들리지 않는 절묘한 무게 중심을 맞춰 드립니다."
    ),
    EAGLE(
        id = "eagle",
        displayName = "과감한 독수리",
        emoji = "🦅",
        type = "기회포착형",
        tags = "#기회포착 #수익극대화",
        desc = "확실한 타이밍에 우량 자산을 낚아챕니다. 변동성을 감내하면서 은퇴 자산의 활기찬 양적 성장을 정조준합니다."
    );

    companion object {
        fun fromId(id: String) = values().firstOrNull { it.id == id } ?: SQUIRREL
    }
}
