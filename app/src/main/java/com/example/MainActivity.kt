package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import com.example.data.database.FinancialLogEntity
import com.example.data.database.UserEntity
import com.example.data.repository.EconomicIndicators
import com.example.data.worker.EconomicSyncWorker
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.*
import java.text.DecimalFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
/**
 * 전체 입력창의 텍스트 색상을 선명한 검정색(Color.Black)으로 일괄 보장하는 표준 컬러 세팅
 */
@Composable
fun sunnyTextFieldColors(
    containerColor: Color = Color(0xFFFBFBFB),
    focusedBorderColor: Color = Color(0xFF1E88E5),
    unfocusedBorderColor: Color = Color(0xFFE0E0E0),
    focusedLabelColor: Color = Color(0xFF1E88E5),
    unfocusedLabelColor: Color = Color.Gray
) = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.Black,
    unfocusedTextColor = Color.Black,
    cursorColor = Color.Black,
    focusedContainerColor = containerColor,
    unfocusedContainerColor = containerColor,
    focusedBorderColor = focusedBorderColor,
    unfocusedBorderColor = unfocusedBorderColor,
    focusedLabelColor = focusedLabelColor,
    unfocusedLabelColor = unfocusedLabelColor,
    focusedLeadingIconColor = focusedBorderColor,
    unfocusedLeadingIconColor = Color.Gray
)

class MainActivity : ComponentActivity() {
    private val viewModel: RetirementViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Background task management & battery optimization: schedule periodic economic indicators sync
        EconomicSyncWorker.schedulePeriodic(applicationContext)

        setContent {
            MyApplicationTheme(darkTheme = false) {
                // Android 15 & Galaxy S24 Edge-to-Edge: Root scaffold doesn't double-pad systemBars
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets(0, 0, 0, 0)
                ) { innerPadding ->
                    MainNavigationContent(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun MainNavigationContent(
    viewModel: RetirementViewModel,
    modifier: Modifier = Modifier
) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
        },
        label = "screen_transition",
        modifier = modifier.fillMaxSize()
    ) { screen ->
        when (screen) {
            is Screen.Login -> LoginScreen(viewModel = viewModel)
            is Screen.OnboardingLifestyle -> OnboardingLifestyleScreen(viewModel = viewModel)
            is Screen.OnboardingAsset -> OnboardingAssetScreen(viewModel = viewModel)
            is Screen.OnboardingCharacter -> OnboardingCharacterScreen(viewModel = viewModel)
            is Screen.Dashboard -> DashboardScreen(viewModel = viewModel)
        }
    }
}

// ==================== SCREEN 1: LOGIN & REGISTRATION ====================
@Composable
fun LoginScreen(viewModel: RetirementViewModel) {
    var isRegisterMode by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }

    val authError by viewModel.authError.collectAsStateWithLifecycle()
    val authSuccessMessage by viewModel.authSuccessMessage.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(authError) {
        authError?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(authSuccessMessage) {
        authSuccessMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            if (isRegisterMode) {
                isRegisterMode = false // Switch to login upon successful registration
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFFE3F2FD), Color(0xFFFFFFFF))
                )
            )
            .imePadding()
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .testTag("auth_card"),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(28.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // App Logo Title
                Text(
                    text = "인생맑음 🌤️",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF1E88E5),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "복잡함은 줄이고, 안도감은 가득히",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))
                // Firebase connection status badge
                Surface(
                    color = if (viewModel.isFirebaseAvailable) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    if (viewModel.isFirebaseAvailable) Color(0xFF4CAF50) else Color(0xFFFF9800),
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (viewModel.isFirebaseAvailable) "🔒 Firebase 보안 클라우드 연동 완료" else "📡 오프라인 로컬 저장소 모드 작동 중",
                            fontSize = 11.sp,
                            color = if (viewModel.isFirebaseAvailable) Color(0xFF2E7D32) else Color(0xFFE65100),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = if (isRegisterMode) "새로운 여정 시작하기 (가입)" else "내 은퇴 방으로 들어가기 (로그인)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.DarkGray,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Username Input
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("아이디") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("username_input"),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    colors = sunnyTextFieldColors()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Password Input
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("비밀번호") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("password_input"),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    colors = sunnyTextFieldColors()
                )

                if (isRegisterMode) {
                    Spacer(modifier = Modifier.height(12.dp))
                    // Nickname Input
                    OutlinedTextField(
                        value = nickname,
                        onValueChange = { nickname = it },
                        label = { Text("부르고 싶은 내 이름 (닉네임)") },
                        leadingIcon = { Icon(Icons.Default.Face, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("nickname_input"),
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        colors = sunnyTextFieldColors()
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Primary Action Button
                Button(
                    onClick = {
                        if (isRegisterMode) {
                            viewModel.register(username, password, nickname)
                        } else {
                            viewModel.login(username, password)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("auth_action_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = if (isRegisterMode) "가입 및 시작하기" else "보안 입장하기",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Toggle Mode Text
                Text(
                    text = if (isRegisterMode) "이미 계정이 있으신가요? 로그인하기" else "처음 오셨나요? 간편 회원가입하기",
                    color = Color(0xFF1976D2),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable {
                            isRegisterMode = !isRegisterMode
                            viewModel.login("", "") // clears errors
                        }
                        .padding(8.dp)
                        .testTag("auth_toggle")
                )
            }
        }
    }
}


// ==================== SCREEN 2: ONBOARDING LIFESTYLE ====================
@Composable
fun OnboardingLifestyleScreen(viewModel: RetirementViewModel) {
    val loggedInUser by viewModel.loggedInUser.collectAsStateWithLifecycle()
    var selectedLifestyle by remember { mutableStateOf<RetirementLifestyle?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
            .systemBarsPadding()
            .padding(24.dp)
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "안녕하세요, ${loggedInUser?.nickname ?: "회원"}님!",
            fontSize = 16.sp,
            color = Color(0xFF1E88E5),
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "1. 은퇴 후에 어떤 여유를 누릴까요?",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF212121),
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Text(
            text = "머릿속으로 상상하는 은퇴 라이프스타일을 하나만 선택해 주세요. 복잡한 계산식은 어울리는 생활 규모에 맞게 AI가 뒤에서 알아서 맞춰드려요.",
            fontSize = 14.sp,
            color = Color.Gray,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(28.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(RetirementLifestyle.values()) { life ->
                val isSelected = selectedLifestyle == life
                Card(
                    onClick = { selectedLifestyle = life },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("lifestyle_${life.id}"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) Color(0xFFE3F2FD) else Color.White
                    ),
                    border = CardDefaults.outlinedCardBorder(enabled = isSelected).copy(
                        width = if (isSelected) 2.dp else 1.dp,
                        brush = Brush.linearGradient(
                            listOf(
                                if (isSelected) Color(0xFF1E88E5) else Color(0xFFEEEEEE),
                                if (isSelected) Color(0xFF1E88E5) else Color(0xFFEEEEEE)
                            )
                        )
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .padding(20.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EmojiIcon(
                            emoji = life.emoji,
                            backgroundColor = if (isSelected) Color(0xFFBBDEFB) else Color(0xFFF1F5F9),
                            borderColor = if (isSelected) Color(0xFF1E88E5) else Color.Transparent,
                            size = 52.dp,
                            emojiSize = 26.sp,
                            modifier = Modifier.padding(end = 16.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = life.title,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF212121)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = life.desc,
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "선택됨",
                                tint = Color(0xFF1E88E5)
                            )
                        }
                    }
                }
            }
        }

        Button(
            onClick = { selectedLifestyle?.let { viewModel.completeLifestyle(it.id) } },
            enabled = selectedLifestyle != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("lifestyle_next_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF1E88E5),
                disabledContainerColor = Color(0xFFE0E0E0)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = "다음 단계로 갈게요",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}


// ==================== SCREEN 3: ONBOARDING ASSET ====================
@Composable
fun OnboardingAssetScreen(viewModel: RetirementViewModel) {
    var assetIndex by remember { mutableStateOf(2) } // default RANGE_2
    val activeAsset = AssetRange.fromIndex(assetIndex)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
            .systemBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "안심을 더하는 자산 설정",
            fontSize = 16.sp,
            color = Color(0xFF1E88E5),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Start)
        )
        Text(
            text = "2. 나의 대략적인 자산 범위",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF212121),
            modifier = Modifier
                .padding(vertical = 8.dp)
                .align(Alignment.Start)
        )
        Text(
            text = "정확하게 적지 않으셔도 괜찮아요. 아래 슬라이더를 부드럽게 넘겨 은퇴 설계를 해나갈 첫 출발점을 지정해 주시면 됩니다.",
            fontSize = 14.sp,
            color = Color.Gray,
            lineHeight = 20.sp,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.weight(0.5f))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(32.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "현재 예상 자산 구간",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = activeAsset.title,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF1E88E5)
                )

                Spacer(modifier = Modifier.height(36.dp))

                Slider(
                    value = assetIndex.toFloat(),
                    onValueChange = { assetIndex = it.toInt() },
                    valueRange = 0f..5f,
                    steps = 4,
                    colors = SliderDefaults.colors(
                        activeTrackColor = Color(0xFF1E88E5),
                        thumbColor = Color(0xFF1E88E5)
                    ),
                    modifier = Modifier.testTag("asset_slider")
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("시작 (5천만 미만)", fontSize = 11.sp, color = Color.Gray)
                    Text("안정 (10억 이상)", fontSize = 11.sp, color = Color.Gray)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = Color(0xFF2E7D32),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "은퇴 후 물가상승률(2.6% 내외) 및 자산의 미래 가치를 보수적이고 보수적으로 역계산해 드리니 걱정 마세요.",
                    fontSize = 12.sp,
                    color = Color(0xFF1B5E20),
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { viewModel.completeAsset(assetIndex) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("asset_next_button"),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = "이 자산 수준으로 계속할게요",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}


// ==================== SCREEN 4: ONBOARDING CHARACTER ====================
@Composable
fun OnboardingCharacterScreen(viewModel: RetirementViewModel) {
    var selectedChar by remember { mutableStateOf<CompanionCharacter?>(null) }
    var companionName by remember { mutableStateOf("") }
    var customPersona by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
            .imePadding()
            .systemBarsPadding()
            .padding(24.dp)
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "나를 지켜줄 동반자",
            fontSize = 16.sp,
            color = Color(0xFF1E88E5),
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "3. 나를 든든히 지켜줄 조언가",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF212121),
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Text(
            text = "어려운 금융 소식과 매달의 계획을 감성적으로 꼼꼼하게 들려줄 AI 파트너를 한 명 골라보세요. 마음에 안정을 안겨줄 거예요.",
            fontSize = 14.sp,
            color = Color.Gray,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(CompanionCharacter.values()) { char ->
                val isSelected = selectedChar == char
                Card(
                    onClick = {
                        selectedChar = char
                        companionName = char.displayName
                        customPersona = char.type
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("character_${char.id}"),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) Color(0xFFFFF8E1) else Color.White
                    ),
                    border = CardDefaults.outlinedCardBorder(enabled = isSelected).copy(
                        width = if (isSelected) 2.dp else 1.dp,
                        brush = Brush.linearGradient(
                            listOf(
                                if (isSelected) Color(0xFFFFB300) else Color(0xFFEEEEEE),
                                if (isSelected) Color(0xFFFFB300) else Color(0xFFEEEEEE)
                            )
                        )
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            EmojiIcon(
                                emoji = char.emoji,
                                backgroundColor = if (isSelected) Color(0xFFFFECB3) else Color(0xFFF1F5F9),
                                borderColor = if (isSelected) Color(0xFFFFB300) else Color.Transparent,
                                size = 56.dp,
                                emojiSize = 28.sp,
                                elevation = if (isSelected) 2.dp else 0.dp,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = char.displayName,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF212121)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = char.type,
                                        fontSize = 11.sp,
                                        color = Color(0xFF795548),
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .background(Color(0xFFEFEBE9), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                    Text(
                                        text = char.tags,
                                        fontSize = 11.sp,
                                        color = Color(0xFFE65100),
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .background(Color(0xFFFFF3E0), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "선택됨",
                                    tint = Color(0xFFFFB300),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = char.desc,
                            fontSize = 12.sp,
                            color = Color.DarkGray,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            if (selectedChar != null) {
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = "✨ AI 동반자 프로필 커스텀",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E88E5)
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            // Companion Custom Name Field
                            Text(
                                text = "동반자 전용 이름",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = companionName,
                                onValueChange = { companionName = it },
                                placeholder = { Text("동반자의 예쁜 이름을 지어주세요", color = Color.Gray) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("companion_name_input"),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                colors = sunnyTextFieldColors()
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // Companion Custom Persona Field
                            Text(
                                text = "동반자 성격/페르소나",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = customPersona,
                                onValueChange = { customPersona = it },
                                placeholder = { Text("예: 차분하고 따뜻한 위로가, 뼈 때리는 팩폭 분석가", color = Color.Gray) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("companion_persona_input"),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                colors = sunnyTextFieldColors()
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Persona Preset Quick Select Chips
                            Text(
                                text = "빠른 추천 페르소나 설정",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val presets = listOf("따뜻하고 친절한 조언가", "뼈 때리는 현실주의 분석가", "파이팅 넘치는 긍정 격려자")
                                presets.forEach { preset ->
                                    val isPresetSelected = customPersona == preset
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = if (isPresetSelected) Color(0xFFE3F2FD) else Color(0xFFF5F5F5),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable { customPersona = preset }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = preset,
                                            fontSize = 10.sp,
                                            color = if (isPresetSelected) Color(0xFF1E88E5) else Color.DarkGray,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { selectedChar?.let { viewModel.completeCharacter(it.id, companionName, customPersona) } },
            enabled = selectedChar != null && companionName.isNotBlank() && customPersona.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("character_next_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF1E88E5),
                disabledContainerColor = Color(0xFFE0E0E0)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = "내 은퇴 방 입장하기",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}


// ==================== SCREEN 5: CORE DASHBOARD ====================
@Composable
fun DashboardScreen(viewModel: RetirementViewModel) {
    val user by viewModel.loggedInUser.collectAsStateWithLifecycle()
    val economicIndicators by viewModel.economicIndicators.collectAsStateWithLifecycle()
    val aiAdvice by viewModel.aiAdvice.collectAsStateWithLifecycle()
    val isAiLoading by viewModel.isAiLoading.collectAsStateWithLifecycle()
    val logs by viewModel.financialLogs.collectAsStateWithLifecycle()
    val totalSolvedSavings by viewModel.totalSolvedSavings.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf(DashboardTab.HOME) }

    val context = LocalContext.current
    var showPermissionRationale by remember { mutableStateOf(false) }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "실시간 은퇴 케어 알림이 활성화되었습니다.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "알림이 꺼져 있어 중요 지출 경고를 놓칠 수 있습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionCheck = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            )
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                showPermissionRationale = true
            }
        }
    }

    // State for creating a new financial leak log
    var showAddLeakDialog by remember { mutableStateOf(false) }

    // State for editing retirement numbers
    var showEditProgressDialog by remember { mutableStateOf(false) }

    // State for interactive companion chat window
    var showCompanionChatDialog by remember { mutableStateOf(false) }

    val userNonNull = user ?: return

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 768.dp

        if (isWideScreen) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF1F5F9))
            ) {
                NavigationRail(
                    containerColor = Color.White,
                    header = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 16.dp)
                        ) {
                            Text(
                                "인생맑음",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E88E5)
                            )
                            Text("🌤️", fontSize = 24.sp)
                        }
                    },
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Spacer(modifier = Modifier.height(24.dp))
                    NavigationRailItem(
                        selected = activeTab == DashboardTab.HOME,
                        onClick = { activeTab = DashboardTab.HOME },
                        icon = { Icon(Icons.Default.Home, contentDescription = "홈") },
                        label = { Text("인생날씨") },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = Color(0xFF1E88E5),
                            selectedTextColor = Color(0xFF1E88E5),
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray,
                            indicatorColor = Color(0xFFE3F2FD)
                        ),
                        modifier = Modifier.testTag("nav_rail_home")
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    NavigationRailItem(
                        selected = activeTab == DashboardTab.LEAKS,
                        onClick = { activeTab = DashboardTab.LEAKS },
                        icon = { Icon(Icons.Default.Warning, contentDescription = "지출막기") },
                        label = { Text("지출막기") },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = Color(0xFF1E88E5),
                            selectedTextColor = Color(0xFF1E88E5),
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray,
                            indicatorColor = Color(0xFFE3F2FD)
                        ),
                        modifier = Modifier.testTag("nav_rail_leaks")
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    NavigationRailItem(
                        selected = activeTab == DashboardTab.ASSETS,
                        onClick = { activeTab = DashboardTab.ASSETS },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "자금설정") },
                        label = { Text("자산세팅") },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = Color(0xFF1E88E5),
                            selectedTextColor = Color(0xFF1E88E5),
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray,
                            indicatorColor = Color(0xFFE3F2FD)
                        ),
                        modifier = Modifier.testTag("nav_rail_assets")
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    NavigationRailItem(
                        selected = activeTab == DashboardTab.SETTINGS,
                        onClick = { activeTab = DashboardTab.SETTINGS },
                        icon = { Icon(Icons.Default.Person, contentDescription = "설정") },
                        label = { Text("설정") },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = Color(0xFF1E88E5),
                            selectedTextColor = Color(0xFF1E88E5),
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray,
                            indicatorColor = Color(0xFFE3F2FD)
                        ),
                        modifier = Modifier.testTag("nav_rail_settings")
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    DashboardContent(
                        activeTab = activeTab,
                        userNonNull = userNonNull,
                        economicIndicators = economicIndicators,
                        aiAdvice = aiAdvice,
                        isAiLoading = isAiLoading,
                        logs = logs,
                        totalSolvedSavings = totalSolvedSavings,
                        viewModel = viewModel,
                        onEditRequest = { showEditProgressDialog = true },
                        onAddLeakRequest = { showAddLeakDialog = true },
                        onOpenChatRequest = { showCompanionChatDialog = true }
                    )
                }
            }
        } else {
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    NavigationBar(
                        containerColor = Color.White,
                        tonalElevation = 8.dp,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        NavigationBarItem(
                            selected = activeTab == DashboardTab.HOME,
                            onClick = { activeTab = DashboardTab.HOME },
                            icon = { Icon(Icons.Default.Home, contentDescription = "홈") },
                            label = { Text("인생날씨") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF1E88E5),
                                selectedTextColor = Color(0xFF1E88E5),
                                unselectedIconColor = Color.Gray,
                                unselectedTextColor = Color.Gray,
                                indicatorColor = Color(0xFFE3F2FD)
                            ),
                            modifier = Modifier.testTag("nav_tab_home")
                        )
                        NavigationBarItem(
                            selected = activeTab == DashboardTab.LEAKS,
                            onClick = { activeTab = DashboardTab.LEAKS },
                            icon = { Icon(Icons.Default.Warning, contentDescription = "지출막기") },
                            label = { Text("지출막기") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF1E88E5),
                                selectedTextColor = Color(0xFF1E88E5),
                                unselectedIconColor = Color.Gray,
                                unselectedTextColor = Color.Gray,
                                indicatorColor = Color(0xFFE3F2FD)
                            ),
                            modifier = Modifier.testTag("nav_tab_leaks")
                        )
                        NavigationBarItem(
                            selected = activeTab == DashboardTab.ASSETS,
                            onClick = { activeTab = DashboardTab.ASSETS },
                            icon = { Icon(Icons.Default.Settings, contentDescription = "자금설정") },
                            label = { Text("자산세팅") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF1E88E5),
                                selectedTextColor = Color(0xFF1E88E5),
                                unselectedIconColor = Color.Gray,
                                unselectedTextColor = Color.Gray,
                                indicatorColor = Color(0xFFE3F2FD)
                            ),
                            modifier = Modifier.testTag("nav_tab_assets")
                        )
                        NavigationBarItem(
                            selected = activeTab == DashboardTab.SETTINGS,
                            onClick = { activeTab = DashboardTab.SETTINGS },
                            icon = { Icon(Icons.Default.Person, contentDescription = "설정") },
                            label = { Text("설정") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF1E88E5),
                                selectedTextColor = Color(0xFF1E88E5),
                                unselectedIconColor = Color.Gray,
                                unselectedTextColor = Color.Gray,
                                indicatorColor = Color(0xFFE3F2FD)
                            ),
                            modifier = Modifier.testTag("nav_tab_settings")
                        )
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFF1F5F9))
                        .padding(innerPadding)
                ) {
                    DashboardContent(
                        activeTab = activeTab,
                        userNonNull = userNonNull,
                        economicIndicators = economicIndicators,
                        aiAdvice = aiAdvice,
                        isAiLoading = isAiLoading,
                        logs = logs,
                        totalSolvedSavings = totalSolvedSavings,
                        viewModel = viewModel,
                        onEditRequest = { showEditProgressDialog = true },
                        onAddLeakRequest = { showAddLeakDialog = true },
                        onOpenChatRequest = { showCompanionChatDialog = true }
                    )
                }
            }
        }
    }

    // Modal: Add Leak Log
    if (showAddLeakDialog) {
        AddLeakDialog(
            onDismiss = { showAddLeakDialog = false },
            onConfirm = { title, amount, category ->
                viewModel.addFinancialLeak(title, amount, category)
                showAddLeakDialog = false
            }
        )
    }

    // Modal: Edit Progress Numbers
    if (showEditProgressDialog) {
        EditProgressDialog(
            user = userNonNull,
            onDismiss = { showEditProgressDialog = false },
            onConfirm = { target, current, fund, isa ->
                viewModel.updateSavingProgress(target, current, fund, isa)
                showEditProgressDialog = false
            }
        )
    }

    // Modal: Companion Chat Window
    if (showCompanionChatDialog) {
        CompanionChatDialog(
            viewModel = viewModel,
            onDismissRequest = { showCompanionChatDialog = false }
        )
    }

    // Modal: Runtime Notification Permission Rationale
    if (showPermissionRationale) {
        PermissionRationaleDialog(
            onDismiss = { showPermissionRationale = false },
            onConfirm = {
                showPermissionRationale = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        )
    }
}

@Composable
fun DashboardContent(
    activeTab: DashboardTab,
    userNonNull: UserEntity,
    economicIndicators: EconomicIndicators?,
    aiAdvice: String,
    isAiLoading: Boolean,
    logs: List<FinancialLogEntity>,
    totalSolvedSavings: Long,
    viewModel: RetirementViewModel,
    onEditRequest: () -> Unit,
    onAddLeakRequest: () -> Unit,
    onOpenChatRequest: () -> Unit
) {
    when (activeTab) {
        DashboardTab.HOME -> DashboardHomeView(
            user = userNonNull,
            economicIndicators = economicIndicators,
            aiAdvice = aiAdvice,
            isAiLoading = isAiLoading,
            viewModel = viewModel,
            totalSolvedSavings = totalSolvedSavings,
            logs = logs,
            onEditRequest = onEditRequest,
            onOpenChatRequest = onOpenChatRequest
        )
        DashboardTab.LEAKS -> DashboardLeaksView(
            logs = logs,
            totalSolvedSavings = totalSolvedSavings,
            viewModel = viewModel,
            onAddLeakRequest = onAddLeakRequest
        )
        DashboardTab.ASSETS -> DashboardAssetsView(
            user = userNonNull,
            viewModel = viewModel,
            onEditRequest = onEditRequest
        )
        DashboardTab.SETTINGS -> DashboardSettingsView(
            user = userNonNull,
            viewModel = viewModel
        )
    }
}

enum class DashboardTab {
    HOME, LEAKS, ASSETS, SETTINGS
}

@Composable
fun DashboardHomeView(
    user: UserEntity,
    economicIndicators: EconomicIndicators?,
    aiAdvice: String,
    isAiLoading: Boolean,
    viewModel: RetirementViewModel,
    totalSolvedSavings: Long,
    logs: List<FinancialLogEntity> = emptyList(),
    onEditRequest: () -> Unit,
    onOpenChatRequest: () -> Unit
) {
    val char = CompanionCharacter.fromId(user.characterId)
    val lifestyle = RetirementLifestyle.fromId(user.lifestyleId)
    val assetRange = AssetRange.fromIndex(user.assetIndex)
    var customUserQuestion by remember { mutableStateOf("") }
    var showToppingCard by remember { mutableStateOf(true) }

    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsStateWithLifecycle()

    val formattedTarget = DecimalFormat("#,###").format(user.savingTarget)
    val totalWithLeakSavings = user.savingCurrent + totalSolvedSavings
    val progressPercent = ((totalWithLeakSavings.toDouble() / user.savingTarget) * 100).coerceAtMost(100.0)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 768.dp

        if (isWide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left Column: Profile, Economic Weather, Saving Progress
                Column(
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Contextual Topping Card
                    if (showToppingCard) {
                        ContextualToppingCard(
                            user = user,
                            logs = logs,
                            totalSolvedSavings = totalSolvedSavings,
                            onAddLeakClick = onOpenChatRequest,
                            onDismiss = { showToppingCard = false }
                        )
                    }

                    // Profile / Companion Header Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E88E5))
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    EmojiIcon(
                                        emoji = char.emoji,
                                        backgroundColor = Color.White.copy(alpha = 0.22f),
                                        borderColor = Color.White.copy(alpha = 0.45f),
                                        size = 60.dp,
                                        emojiSize = 32.sp,
                                        elevation = 1.dp,
                                        modifier = Modifier.padding(end = 12.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "${user.nickname}님의 노후 동반자",
                                            fontSize = 12.sp,
                                            color = Color(0xFFBBDEFB),
                                            fontWeight = FontWeight.Medium
                                        )
                                        val finalCompName = if (user.companionCustomName.isNotBlank()) user.companionCustomName else char.displayName
                                        val finalCompType = if (user.companionCustomPersona.isNotBlank()) user.companionCustomPersona else char.type
                                        Text(
                                            text = "$finalCompName ($finalCompType)",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { viewModel.logout() },
                                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "로그아웃")
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            HorizontalDivider(color = Color.White.copy(alpha = 0.2f))

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val statusDotColor = when {
                                        !isOnline -> Color(0xFFFF7043)
                                        pendingSyncCount > 0 -> Color(0xFFFFCA28)
                                        viewModel.isFirebaseAvailable -> Color(0xFF81C784)
                                        else -> Color(0xFFFFB74D)
                                    }
                                    val syncStatusText = when {
                                        !isOnline -> "오프라인 모드 (대기 ${pendingSyncCount}건 ☁️)"
                                        pendingSyncCount > 0 -> "클라우드 동기화 대기 중 (${pendingSyncCount}건)..."
                                        viewModel.isFirebaseAvailable -> "Firebase 클라우드 동기화 완료 ✨"
                                        else -> "로컬 오프라인 모드 유지 중"
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .background(statusDotColor, shape = CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = syncStatusText,
                                        fontSize = 11.sp,
                                        color = Color(0xFFE3F2FD),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                if (pendingSyncCount > 0 && isOnline) {
                                    Text(
                                        text = "지금 동기화",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier
                                            .background(Color.White.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
                                            .clickable { viewModel.flushPendingSync() }
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("나의 로망 라이프스타일", fontSize = 11.sp, color = Color(0xFFE3F2FD))
                                    Text(
                                        text = "${lifestyle.emoji} ${lifestyle.title}",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("출발 지점 자산", fontSize = 11.sp, color = Color(0xFFE3F2FD))
                                    Text(
                                        text = assetRange.title,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = onOpenChatRequest,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .testTag("open_companion_chat_button_wide"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White.copy(alpha = 0.2f),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "💬 동반자와 1:1 안심 대화창 열기",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Live Economic Weather Indicator Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("오늘의 경제 기상도", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "대한민국 경제 날씨: 맑음 🌤️",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF2E7D32)
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "날씨",
                                    tint = Color(0xFF1E88E5),
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                EconomicWidget(
                                    label = "한국은행 금리",
                                    value = "${economicIndicators?.baseInterestRate ?: 3.50}%",
                                    color = Color(0xFF2E7D32)
                                )
                                EconomicWidget(
                                    label = "인플레이션(물가)",
                                    value = "${economicIndicators?.inflationRate ?: 2.6}%",
                                    color = Color(0xFFC62828)
                                )
                                EconomicWidget(
                                    label = "코스피 시세",
                                    value = "${economicIndicators?.kospiIndex ?: 2685.42}",
                                    color = Color(0xFF1565C0)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = economicIndicators?.weatherDescription ?: "은퇴 대비 자산을 안정적으로 적립해 나가기 쾌적한 상태입니다.",
                                fontSize = 11.sp,
                                color = Color.DarkGray,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    // Saving Progress Dashboard Card (현재 자산과 목표를 비교하고 달성률을 시각화)
                    RetirementProgressDashboardCard(
                        user = user,
                        viewModel = viewModel,
                        totalSolvedSavings = totalSolvedSavings,
                        onEditRequest = onEditRequest
                    )

                    // Personalized Report Summary & Export Card
                    PersonalizedReportSummaryCard(
                        user = user,
                        viewModel = viewModel,
                        totalSolvedSavings = totalSolvedSavings,
                        aiAdvice = aiAdvice,
                        logs = logs
                    )
                }

                // Right Column: AI Retirement Chatbot Module (stays visible right next to statistics)
                Column(
                    modifier = Modifier
                        .weight(0.9f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    RetirementAiChatbotModule(
                        user = user,
                        viewModel = viewModel,
                        aiAdvice = aiAdvice,
                        isAiLoading = isAiLoading,
                        totalSolvedSavings = totalSolvedSavings,
                        onOpenChatRequest = onOpenChatRequest
                    )
                }
            }
        } else {
            // Mobile: Original beautiful vertical layout
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Contextual Topping Card
                if (showToppingCard) {
                    item {
                        ContextualToppingCard(
                            user = user,
                            logs = logs,
                            totalSolvedSavings = totalSolvedSavings,
                            onAddLeakClick = onOpenChatRequest,
                            onDismiss = { showToppingCard = false }
                        )
                    }
                }

                // Upper Greeting Header Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E88E5))
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    EmojiIcon(
                                        emoji = char.emoji,
                                        backgroundColor = Color.White.copy(alpha = 0.22f),
                                        borderColor = Color.White.copy(alpha = 0.45f),
                                        size = 60.dp,
                                        emojiSize = 32.sp,
                                        elevation = 1.dp,
                                        modifier = Modifier.padding(end = 12.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "${user.nickname}님의 노후 동반자",
                                            fontSize = 12.sp,
                                            color = Color(0xFFBBDEFB),
                                            fontWeight = FontWeight.Medium
                                        )
                                        val finalCompName = if (user.companionCustomName.isNotBlank()) user.companionCustomName else char.displayName
                                        val finalCompType = if (user.companionCustomPersona.isNotBlank()) user.companionCustomPersona else char.type
                                        Text(
                                            text = "$finalCompName ($finalCompType)",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { viewModel.logout() },
                                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "로그아웃")
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            HorizontalDivider(color = Color.White.copy(alpha = 0.2f))

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val statusDotColor = when {
                                        !isOnline -> Color(0xFFFF7043)
                                        pendingSyncCount > 0 -> Color(0xFFFFCA28)
                                        viewModel.isFirebaseAvailable -> Color(0xFF81C784)
                                        else -> Color(0xFFFFB74D)
                                    }
                                    val syncStatusText = when {
                                        !isOnline -> "오프라인 모드 (대기 ${pendingSyncCount}건 ☁️)"
                                        pendingSyncCount > 0 -> "클라우드 동기화 대기 (${pendingSyncCount}건)..."
                                        viewModel.isFirebaseAvailable -> "Firebase 클라우드 동기화 완료 ✨"
                                        else -> "로컬 오프라인 모드 유지 중"
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .background(statusDotColor, shape = CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = syncStatusText,
                                        fontSize = 11.sp,
                                        color = Color(0xFFE3F2FD),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                if (pendingSyncCount > 0 && isOnline) {
                                    Text(
                                        text = "지금 동기화",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier
                                            .background(Color.White.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
                                            .clickable { viewModel.flushPendingSync() }
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("나의 로망 라이프스타일", fontSize = 11.sp, color = Color(0xFFE3F2FD))
                                    Text(
                                        text = "${lifestyle.emoji} ${lifestyle.title}",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("출발 지점 자산", fontSize = 11.sp, color = Color(0xFFE3F2FD))
                                    Text(
                                        text = assetRange.title,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = onOpenChatRequest,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .testTag("open_companion_chat_button_mobile"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White.copy(alpha = 0.2f),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "💬 동반자와 1:1 안심 대화창 열기",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Live Economic Weather Indicator Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("오늘의 경제 기상도", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "대한민국 경제 날씨: 맑음 🌤️",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF2E7D32)
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "날씨",
                                    tint = Color(0xFF1E88E5),
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                EconomicWidget(
                                    label = "한국은행 금리",
                                    value = "${economicIndicators?.baseInterestRate ?: 3.50}%",
                                    color = Color(0xFF2E7D32)
                                )
                                EconomicWidget(
                                    label = "인플레이션(물가)",
                                    value = "${economicIndicators?.inflationRate ?: 2.6}%",
                                    color = Color(0xFFC62828)
                                )
                                EconomicWidget(
                                    label = "코스피 시세",
                                    value = "${economicIndicators?.kospiIndex ?: 2685.42}",
                                    color = Color(0xFF1565C0)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = economicIndicators?.weatherDescription ?: "은퇴 대비 자산을 안정적으로 적립해 나가기 쾌적한 상태입니다.",
                                fontSize = 11.sp,
                                color = Color.DarkGray,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                // Saving Progress Dashboard Card (현재 자산과 목표를 비교하고 달성률을 시각화 및 목표 설정 폼)
                item {
                    RetirementProgressDashboardCard(
                        user = user,
                        viewModel = viewModel,
                        totalSolvedSavings = totalSolvedSavings,
                        onEditRequest = onEditRequest
                    )
                }

                // Personalized Report Summary & Export Card
                item {
                    PersonalizedReportSummaryCard(
                        user = user,
                        viewModel = viewModel,
                        totalSolvedSavings = totalSolvedSavings,
                        aiAdvice = aiAdvice,
                        logs = logs
                    )
                }

                // AI Retirement Strategy Chatbot Module
                item {
                    RetirementAiChatbotModule(
                        user = user,
                        viewModel = viewModel,
                        aiAdvice = aiAdvice,
                        isAiLoading = isAiLoading,
                        totalSolvedSavings = totalSolvedSavings,
                        onOpenChatRequest = onOpenChatRequest
                    )
                }
            }
        }
    }
}


@Composable
fun EconomicWidget(label: String, value: String, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(Color(0xFFF8F9FA), RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp, horizontal = 12.dp)
    ) {
        Text(label, fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = color)
    }
}


// ==================== DASHBOARD TAB 2: SPENDING LEAKS ====================
@Composable
fun DashboardLeaksView(
    logs: List<FinancialLogEntity>,
    totalSolvedSavings: Long,
    viewModel: RetirementViewModel,
    onAddLeakRequest: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF1F1))
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "지출 구멍 막아서 은퇴 자금으로!",
                        fontSize = 12.sp,
                        color = Color(0xFFC62828),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "절약 성공 총액: ${DecimalFormat("#,###").format(totalSolvedSavings)}원",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFC62828)
                    )
                }
                Button(
                    onClick = { onAddLeakRequest() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("add_leak_button")
                ) {
                    Text("구멍 추가", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "내가 등록한 지출 새는 곳들",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color.DarkGray,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "새어나가는 지출 구멍이 없습니다.\n안심하고 저축을 계속해주세요! 🌤️",
                    textAlign = TextAlign.Center,
                    color = Color.Gray,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(logs) { log ->
                    LeakItem(log = log, onSolvedToggle = { isSolved ->
                        viewModel.solveFinancialLeak(log, isSolved)
                    }, onDelete = {
                        viewModel.deleteFinancialLeak(log.id)
                    })
                }
            }
        }
    }
}

@Composable
fun LeakItem(
    log: FinancialLogEntity,
    onSolvedToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val categoryEmoji = when (log.category) {
        "FOOD" -> "🍔"
        "TRANSPORT" -> "🚕"
        "CAFE" -> "☕"
        "SHOPPING" -> "🛍️"
        else -> "💸"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (log.isSolved) Color(0xFFE8F5E9) else Color.White
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EmojiIcon(
                emoji = categoryEmoji,
                backgroundColor = if (log.isSolved) Color(0xFFC8E6C9) else Color(0xFFECEFF1),
                borderColor = if (log.isSolved) Color(0xFF81C784) else Color(0xFFCFD8DC),
                size = 48.dp,
                emojiSize = 22.sp,
                elevation = 0.dp,
                modifier = Modifier.padding(end = 12.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = log.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (log.isSolved) Color(0xFF2E7D32) else Color.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${DecimalFormat("#,###").format(log.amount)}원",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (log.isSolved) Color(0xFF2E7D32) else Color.Gray
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { onSolvedToggle(!log.isSolved) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (log.isSolved) Color(0xFF2E7D32) else Color(0xFFEEEEEE),
                        contentColor = if (log.isSolved) Color.White else Color.DarkGray
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("solve_toggle_${log.id}")
                ) {
                    Text(if (log.isSolved) "절약완료! ✓" else "절약하기", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "삭제", tint = Color.LightGray)
                }
            }
        }
    }
}


// ==================== DASHBOARD TAB 3: ASSET DETAILS ====================
@Composable
fun DashboardAssetsView(
    user: UserEntity,
    viewModel: RetirementViewModel,
    onEditRequest: () -> Unit
) {
    val totalWithLeakSavings = user.savingCurrent

    // Calculations
    val target = user.savingTarget
    val emergencyTarget = user.securityFund
    val isa = user.isaContribution

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Upper Title
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("내 손안의 은퇴 금고", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Text("세밀한 자산 설정", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
                }
                Button(
                    onClick = onEditRequest,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("금액 수정", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Section: Retirement Asset Projection Chart (은퇴 시점까지의 자산 흐름 시각화 그래프)
        item {
            RetirementAssetProjectionChart(user = user)
        }

        // Section: Retirement Fund Needed Calculator (은퇴 자금 필요량 계산기)
        item {
            RetirementCalculatorCard(user = user, viewModel = viewModel)
        }

        // Section 1: Emergency Fund (든든한 비상금)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🛡️ 든든한 비상금", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3F51B5))
                        Text("격리 보호 중", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "갑작스러운 지출이 발생해도 은퇴 자산을 훼손하지 않도록 안전하게 모셔둔 독립 비상금입니다.",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("보관 금액", fontSize = 12.sp, color = Color.DarkGray)
                        Text("${DecimalFormat("#,###").format(emergencyTarget)}원", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF3F51B5))
                    }
                }
            }
        }

        // Section 2: Tax Benefits (세금 줄이기)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🐷 세금 줄이기 (절세 계좌)", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                        Text("연간 한도 추적 중", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "나라에서 비과세와 소득공제를 아끼지 않는 ISA, 연금저축, IRP 등 절세 3총사 계좌에 납입된 금액입니다.",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("ISA 등 납입 성공액", fontSize = 12.sp, color = Color.DarkGray)
                        Text("${DecimalFormat("#,###").format(isa)}원", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2E7D32))
                    }
                }
            }
        }

        // Section 3: Asset Balancing (시소 무게중심)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("⚖️ 안전자산 vs 성장자산 무게중심", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "은퇴가 다가올수록 위험자산의 비중을 낮추고, 채권 및 배당자산 비중을 늘려 시소의 무게중심을 잡아주어야 은퇴날씨가 맑게 유지됩니다.",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Safe to Growth balance ratio simulation based on companion selection
                    val characterId = user.characterId
                    val (safePercent, growthPercent) = when (characterId) {
                        "turtle" -> Pair(80, 20)
                        "squirrel" -> Pair(50, 50)
                        "eagle" -> Pair(20, 80)
                        else -> Pair(50, 50)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("안전(채권/예금) $safePercent%", fontSize = 11.sp, color = Color.DarkGray, fontWeight = FontWeight.Bold)
                        Text("성장(주식/ETF) $growthPercent%", fontSize = 11.sp, color = Color.DarkGray, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(16.dp)
                            .clip(CircleShape)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(safePercent.toFloat())
                                .fillMaxHeight()
                                .background(Color(0xFF81C784))
                        )
                        Box(
                            modifier = Modifier
                                .weight(growthPercent.toFloat())
                                .fillMaxHeight()
                                .background(Color(0xFFFFB74D))
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "회원님의 '${if (user.companionCustomName.isNotBlank()) user.companionCustomName else CompanionCharacter.fromId(characterId).displayName}' 성향 권장 비율에 맞게 포트폴리오가 균형 잡혀 있습니다.",
                        fontSize = 10.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Section 4: Google API Key Settings & Connection Test (구글 API 설정 및 연결 테스트)
        item {
            val customApiKey by viewModel.customApiKey.collectAsStateWithLifecycle()
            val testResult by viewModel.testResult.collectAsStateWithLifecycle()
            val isTestingApi by viewModel.isTestingApi.collectAsStateWithLifecycle()

            var apiKeyInput by remember(customApiKey) { mutableStateOf(customApiKey) }
            var isPasswordVisible by remember { mutableStateOf(false) }

            Card(
                modifier = Modifier.fillMaxWidth().testTag("api_key_settings_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "⚙️ 구글 Gemini AI 엔진 설정",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E88E5)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "스마트한 AI 캐릭터 동반자가 실시간으로 회원님의 자산을 분석하고 따뜻한 맞춤형 은퇴 조언을 생성하기 위해 사용됩니다.",
                        fontSize = 11.sp,
                        color = Color.DarkGray,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // 쉬운 설명 아코디언/안내 문구
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "💡 초보자를 위한 3초 API 키 무료 발급 가이드",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "1. 구글 AI 스튜디오 홈페이지에 방문합니다.\n" +
                                       "   👉 Google AI Studio (aistudio.google.com)\n" +
                                       "2. 로그인 후 [Get API key] -> [Create API key] 클릭!\n" +
                                       "3. 생성된 키(AIzaSy...로 시작)를 복사해서 아래에 넣어주세요.",
                                fontSize = 10.sp,
                                color = Color.Gray,
                                lineHeight = 14.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // API Key Input
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        label = { Text("Google Gemini API Key 입력", fontSize = 12.sp) },
                        placeholder = { Text("AIzaSy...", color = Color.LightGray) },
                        modifier = Modifier.fillMaxWidth().testTag("api_key_input_field"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            TextButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Text(
                                    text = if (isPasswordVisible) "숨기기" else "보기",
                                    color = Color(0xFF1E88E5),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        colors = sunnyTextFieldColors(containerColor = Color.White)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 저장 버튼
                        Button(
                            onClick = {
                                viewModel.saveCustomApiKey(apiKeyInput.trim())
                                viewModel.clearTestResult()
                                viewModel.loadAiAdvice() // Reload advice with the new key!
                            },
                            modifier = Modifier.weight(1f).height(44.dp).testTag("api_key_save_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("API 키 저장", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        // 연결 테스트 버튼
                        Button(
                            onClick = {
                                viewModel.testCustomApiKey(apiKeyInput.trim())
                            },
                            enabled = !isTestingApi,
                            modifier = Modifier.weight(1f).height(44.dp).testTag("api_key_test_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isTestingApi) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("연결 테스트 🧪", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // 테스트 결과 표시
                    testResult?.let { (success, message) ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (success) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (success) "✅ 연결 성공!" else "❌ 연결 실패",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (success) Color(0xFF2E7D32) else Color(0xFFC62828)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = message,
                                    fontSize = 11.sp,
                                    color = if (success) Color(0xFF1B5E20) else Color(0xFFB71C1C),
                                    lineHeight = 14.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section 5: Real Expert Matching (진짜 전문가 SOS)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF263238))
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "복잡한 세금 및 증여 문제는?",
                            fontSize = 11.sp,
                            color = Color(0xFF90A4AE),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "진짜 세무 전문가 매칭 SOS 📞",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "복잡한 법률 및 상속 세무 문제를 터치 한 번에 지역 일등 세무사와 1:1로 해결해 드립니다.",
                            fontSize = 11.sp,
                            color = Color(0xFFCFD8DC),
                            lineHeight = 15.sp
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "이동",
                        tint = Color.White,
                        modifier = Modifier
                            .size(28.dp)
                            .background(Color(0xFF37474F), CircleShape)
                            .padding(4.dp)
                    )
                }
            }
        }
    }
}


// ==================== MODAL: ADD LEAK DIALOG ====================
@Composable
fun AddLeakDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Long, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var amountString by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("CAFE") } // default CAFE
    val context = LocalContext.current

    val categories = listOf(
        Triple("CAFE", "☕ 카페", Color(0xFF795548)),
        Triple("FOOD", "🍔 식비", Color(0xFFE53935)),
        Triple("TRANSPORT", "🚕 교통", Color(0xFFFFB300)),
        Triple("SHOPPING", "🛍️ 쇼핑", Color(0xFF00ACC1)),
        Triple("OTHER", "💸 기타", Color(0xFF757575))
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 460.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(0xFFE3F2FD), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when(category) {
                                "CAFE" -> "☕"
                                "FOOD" -> "🍔"
                                "TRANSPORT" -> "🚕"
                                "SHOPPING" -> "🛍️"
                                else -> "💸"
                            },
                            fontSize = 24.sp
                        )
                    }
                    Column {
                        Text(
                            text = "지출 구멍 등록하기",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )
                        Text(
                            text = "소리없이 새어나가는 일상 지출을 기록하세요",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFFF1F5F9))

                // Title Input
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("지출 내역 이름") },
                    placeholder = { Text("예: 매일 마시는 스타벅스 라떼", color = Color.Gray) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_leak_title"),
                    shape = RoundedCornerShape(14.dp),
                    colors = sunnyTextFieldColors()
                )

                // Amount Input
                OutlinedTextField(
                    value = amountString,
                    onValueChange = { amountString = it },
                    label = { Text("지출 금액 (원)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    placeholder = { Text("예: 5200", color = Color.Gray) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_leak_amount"),
                    shape = RoundedCornerShape(14.dp),
                    colors = sunnyTextFieldColors()
                )

                // Category Selection
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "카테고리 선택",
                        fontSize = 12.sp,
                        color = Color(0xFF475569),
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categories.forEach { cat ->
                            val isSelected = category == cat.first
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (isSelected) cat.third.copy(alpha = 0.15f) else Color(0xFFF8FAFC),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .border(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) cat.third else Color(0xFFE2E8F0),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { category = cat.first }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = cat.second,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) cat.third else Color(0xFF64748B)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("취소", color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
                    }

                    Button(
                        onClick = {
                            val amount = amountString.toLongOrNull() ?: 0L
                            if (title.isNotBlank() && amount > 0L) {
                                onConfirm(title, amount, category)
                                Toast.makeText(context, "‘${title}’ 구멍이 등록되었습니다!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .weight(1.4f)
                            .height(50.dp)
                            .testTag("dialog_leak_confirm"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("구멍 막기 등록", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}


// ==================== MODAL: EDIT RETIREMENT PROGRESS DIALOG ====================
@Composable
fun EditProgressDialog(
    user: UserEntity,
    onDismiss: () -> Unit,
    onConfirm: (Long, Long, Long, Long) -> Unit
) {
    var target by remember { mutableStateOf(user.savingTarget.toString()) }
    var current by remember { mutableStateOf(user.savingCurrent.toString()) }
    var fund by remember { mutableStateOf(user.securityFund.toString()) }
    var isa by remember { mutableStateOf(user.isaContribution.toString()) }
    val context = LocalContext.current

    fun formatMoneyKorean(valueStr: String): String {
        val amount = valueStr.toLongOrNull() ?: return "0원"
        val eok = amount / 100_000_000L
        val man = (amount % 100_000_000L) / 10_000L
        return buildString {
            if (eok > 0) append("${eok}억 ")
            if (man > 0) append("${man}만 ")
            if (eok == 0L && man == 0L) append("${amount}원") else append("원")
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 480.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(0xFFE8F5E9), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🏦", fontSize = 24.sp)
                    }
                    Column {
                        Text(
                            text = "은퇴 금고 수치 직접 설정",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )
                        Text(
                            text = "실제 금융 현황에 맞게 은퇴 자산 계획을 튜닝하세요",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFFF1F5F9))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                ) {
                    item {
                        Column {
                            OutlinedTextField(
                                value = target,
                                onValueChange = { target = it },
                                label = { Text("은퇴 최종 목표 금액 (원)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("dialog_edit_target"),
                                shape = RoundedCornerShape(14.dp),
                                colors = sunnyTextFieldColors()
                            )
                            Text(
                                text = "환산: ${formatMoneyKorean(target)}",
                                fontSize = 11.sp,
                                color = Color(0xFF1E88E5),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 6.dp, top = 2.dp)
                            )
                        }
                    }

                    item {
                        Column {
                            OutlinedTextField(
                                value = current,
                                onValueChange = { current = it },
                                label = { Text("현재까지 모은 돈 (원)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("dialog_edit_current"),
                                shape = RoundedCornerShape(14.dp),
                                colors = sunnyTextFieldColors()
                            )
                            Text(
                                text = "환산: ${formatMoneyKorean(current)}",
                                fontSize = 11.sp,
                                color = Color(0xFF2E7D32),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 6.dp, top = 2.dp)
                            )
                        }
                    }

                    item {
                        Column {
                            OutlinedTextField(
                                value = fund,
                                onValueChange = { fund = it },
                                label = { Text("독립 보관 비상금 (원)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("dialog_edit_fund"),
                                shape = RoundedCornerShape(14.dp),
                                colors = sunnyTextFieldColors()
                            )
                            Text(
                                text = "환산: ${formatMoneyKorean(fund)}",
                                fontSize = 11.sp,
                                color = Color(0xFFD97706),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 6.dp, top = 2.dp)
                            )
                        }
                    }

                    item {
                        Column {
                            OutlinedTextField(
                                value = isa,
                                onValueChange = { isa = it },
                                label = { Text("절세 삼총사 계좌 납입금 (원)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("dialog_edit_isa"),
                                shape = RoundedCornerShape(14.dp),
                                colors = sunnyTextFieldColors()
                            )
                            Text(
                                text = "환산: ${formatMoneyKorean(isa)}",
                                fontSize = 11.sp,
                                color = Color(0xFF9333EA),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 6.dp, top = 2.dp)
                            )
                        }
                    }
                }

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("취소", color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
                    }

                    Button(
                        onClick = {
                            val t = target.toLongOrNull() ?: user.savingTarget
                            val c = current.toLongOrNull() ?: user.savingCurrent
                            val f = fund.toLongOrNull() ?: user.securityFund
                            val i = isa.toLongOrNull() ?: user.isaContribution
                            onConfirm(t, c, f, i)
                            Toast.makeText(context, "은퇴 금고 수치가 안전하게 업데이트되었습니다.", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .weight(1.4f)
                            .height(50.dp)
                            .testTag("dialog_edit_confirm"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("금고 안전 저장", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ==================== MODAL: PERMISSION RATIONALE DIALOG ====================
@Composable
fun PermissionRationaleDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 460.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color(0xFFE3F2FD), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🔔", fontSize = 26.sp)
                    }
                    Column {
                        Text(
                            text = "실시간 은퇴 케어 알림 설정",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )
                        Text(
                            text = "인생맑음이 노후 자금과 절약 습관을 지켜드립니다",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFFF1F5F9))

                // Feature List
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PermissionFeatureRow(
                        emoji = "💧",
                        title = "새는 돈 긴급 방어 알림",
                        desc = "나도 모르게 빠져나가는 커피·배달비 지출 구멍을 즉시 감지하고 경고합니다."
                    )
                    PermissionFeatureRow(
                        emoji = "📈",
                        title = "거시경제 날씨 & 복리 리포트",
                        desc = "금리·환율 변동에 맞춰 내 은퇴 자산을 불릴 수 있는 최적의 타이밍을 전달합니다."
                    )
                    PermissionFeatureRow(
                        emoji = "🛡️",
                        title = "오프라인 큐 동기화 & 금고 안전 보고",
                        desc = "네트워크 불안정 시에도 자산 기록을 지키고 동기화 완료 결과를 안전하게 알려드립니다."
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("나중에 하기", color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
                    }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier
                            .weight(1.4f)
                            .height(50.dp)
                            .testTag("permission_rationale_confirm"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("알림 허용하고 케어받기", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionFeatureRow(emoji: String, title: String, desc: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(Color(0xFFF8FAFC), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, fontSize = 18.sp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B)
            )
            Text(
                text = desc,
                fontSize = 11.sp,
                color = Color(0xFF64748B),
                lineHeight = 15.sp
            )
        }
    }
}

// ==================== COMPONENT: CONTEXTUAL TOPPING CARD ====================
@Composable
fun ContextualToppingCard(
    user: UserEntity,
    logs: List<FinancialLogEntity>,
    totalSolvedSavings: Long,
    onAddLeakClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val unsolvedLeaks = logs.filter { !it.isSolved }
    val totalWithLeaks = user.savingCurrent + totalSolvedSavings
    val progressPercent = ((totalWithLeaks.toDouble() / user.savingTarget.coerceAtLeast(1L)) * 100).coerceIn(0.0, 100.0)

    val (badgeText, badgeBg, badgeTextColor, emoji, titleText, descText, ctaText) = when {
        unsolvedLeaks.isNotEmpty() -> {
            val totalLeak = unsolvedLeaks.sumOf { it.amount }
            val formattedLeak = DecimalFormat("#,###").format(totalLeak)
            Septuple(
                "💧 지출 구멍 다이어트 토핑",
                Color(0xFFFEF2F2),
                Color(0xFFDC2626),
                "🚨",
                "현재 발견된 지출 구멍 ${unsolvedLeaks.size}건 (총 ${formattedLeak}원)",
                "매달 새어나가는 소액 지출을 막으면 20년 뒤 은퇴 자산이 수천만 원 더 불어납니다.",
                "지출 구멍 틀어막기"
            )
        }
        progressPercent >= 50.0 -> {
            Septuple(
                "🚀 복리 가속 순항 토핑",
                Color(0xFFECFDF5),
                Color(0xFF059669),
                "⭐",
                "목표의 절반을 돌파한 ${user.nickname}님, 복리 효과가 가속 중입니다!",
                "안정적인 배당 ETF와 채권 사다리 전략으로 은퇴 전 매월 현금 흐름을 설계해보세요.",
                "동반자와 전략 점검"
            )
        }
        else -> {
            Septuple(
                "🌱 은퇴 씨앗 틔우기 토핑",
                Color(0xFFEFF6FF),
                Color(0xFF2563EB),
                "💡",
                "연간 최대 900만원 세액공제 혜택(연금저축/IRP) 챙기셨나요?",
                "지금 시작하는 10만원의 작은 씨앗이 노후 맑은 햇살이 되어 돌아옵니다.",
                "AI 맞춤 조언 확인"
            )
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("contextual_topping_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category pill badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = badgeBg
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeTextColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "닫기",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(emoji, fontSize = 22.sp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = titleText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = descText,
                        fontSize = 12.sp,
                        color = Color(0xFF64748B),
                        lineHeight = 17.sp
                    )
                }
            }

            Button(
                onClick = onAddLeakClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
            ) {
                Text(
                    text = ctaText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1E293B)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color(0xFF1E293B),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

// Helper data holder for topping card
data class Septuple<A, B, C, D, E, F, G>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E,
    val sixth: F,
    val seventh: G
)

// ==================== COMPONENT: RETIREMENT ASSET PROJECTION CHART ====================
@Composable
fun RetirementAssetProjectionChart(user: UserEntity) {
    var interestRate by remember { mutableStateOf(5) } // Default 5%
    var annualSavingsMan by remember { mutableStateOf(1200) } // Default 1,200만원 (1200)

    val rates = listOf(3, 5, 8)
    val rateLabels = listOf("보수적 (3%)", "중립적 (5%)", "적극적 (8%)")

    // Calculations
    val years = 25
    val savingCurrent = user.savingCurrent
    val savingTarget = user.savingTarget

    // Calculate compound interest year by year, sample at 0, 5, 10, 15, 20, 25
    val r = interestRate / 100.0
    val annualSavings = annualSavingsMan * 10_000L

    val projectedPoints = remember(interestRate, annualSavings, savingCurrent) {
        val list = ArrayList<Long>()
        var current = savingCurrent.toDouble()
        list.add(savingCurrent) // Year 0

        for (y in 1..years) {
            current = current * (1 + r) + annualSavings
            if (y % 5 == 0) {
                list.add(current.toLong())
            }
        }
        list
    }

    val maxVal = remember(projectedPoints, savingTarget) {
        val maxProj = projectedPoints.maxOrNull() ?: 100_000_000L
        (maxOf(maxProj, savingTarget) * 1.15).toLong()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("asset_projection_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📈 미래 은퇴 자산 시뮬레이션",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E88E5)
                )
                Text(
                    text = "25년 장기 추정",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "현재 자산과 매년 저축액을 복리로 굴렸을 때의 예상 자산 흐름입니다. 연 수익률과 저축액을 변경해 보세요.",
                fontSize = 11.sp,
                color = Color.Gray,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Controls 1: Interest Rate Options
            Text("연 평균 투자 수익률 선택", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rates.forEachIndexed { index, rate ->
                    val isSelected = interestRate == rate
                    Button(
                        onClick = { interestRate = rate },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) Color(0xFF1E88E5) else Color(0xFFF1F5F9),
                            contentColor = if (isSelected) Color.White else Color.DarkGray
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(rateLabels[index], fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Controls 2: Annual Savings Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("연간 추가 저축액", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                Text(
                    text = "${formatKoreanMoney(annualSavings)} (월 약 ${DecimalFormat("#,###").format(annualSavingsMan / 12)}만원)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E88E5)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Slider(
                value = annualSavingsMan.toFloat(),
                onValueChange = { annualSavingsMan = it.toInt() },
                valueRange = 0f..5000f,
                steps = 49,
                colors = SliderDefaults.colors(
                    activeTrackColor = Color(0xFF1E88E5),
                    thumbColor = Color(0xFF1E88E5),
                    inactiveTrackColor = Color(0xFFE2E8F0)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Recharts-style Chart Legend (범례)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Legend item 1: Expected asset
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFF1E88E5), shape = CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "예상 은퇴 자산 추이",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF475569)
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // Legend item 2: Target asset
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(12.dp)
                            .height(2.dp)
                            .background(Color(0xFFF4511E))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "설정 목표 자산",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF475569)
                    )
                }
            }

            // Canvas Chart with Tap & Drag Interaction
            var selectedIndex by remember { mutableStateOf<Int?>(null) }
            val haptic = LocalHapticFeedback.current

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            ) {
                val labels = listOf("현재", "+5년", "+10년", "+15년", "+20년", "25년뒤")
                
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            val paddingPx = 32.dp.toPx()
                            fun computeIndex(offset: Offset): Int? {
                                if (offset.y < 0 || offset.y > size.height) return null
                                val chartWidth = size.width - 2 * paddingPx
                                if (chartWidth <= 0) return null
                                val stepWidth = chartWidth / 5
                                return ((offset.x - paddingPx + stepWidth / 2) / stepWidth).toInt().coerceIn(0, 5)
                            }

                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val initialIdx = computeIndex(down.position)
                                if (initialIdx != null) {
                                    selectedIndex = initialIdx
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }

                                var dragChange: androidx.compose.ui.input.pointer.PointerInputChange?
                                do {
                                    val event = awaitPointerEvent()
                                    dragChange = event.changes.firstOrNull { it.id == down.id }
                                    if (dragChange != null && dragChange.pressed) {
                                        val idx = computeIndex(dragChange.position)
                                        if (idx != null && idx != selectedIndex) {
                                            selectedIndex = idx
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                        dragChange.consume()
                                    }
                                } while (dragChange != null && dragChange.pressed)

                                selectedIndex = null
                            }
                        }
                ) {
                    val padding = 32.dp.toPx()
                    val chartWidth = size.width - 2 * padding
                    val chartHeight = size.height - 2 * padding
                    val stepWidth = chartWidth / 5
 
                    // 1. Draw Grid Lines (Y-Axis milestones)
                    val gridLinesCount = 4
                    val textPaint = Paint().asFrameworkPaint().apply {
                        color = android.graphics.Color.GRAY
                        textSize = 24f
                        textAlign = android.graphics.Paint.Align.RIGHT
                    }

                    for (i in 0..gridLinesCount) {
                        val fraction = i.toFloat() / gridLinesCount
                        val gridY = size.height - padding - fraction * chartHeight
                        val gridValue = (fraction * maxVal).toLong()

                        // Dashed horizontal grid line
                        drawLine(
                            color = Color(0xFFE2E8F0),
                            start = Offset(padding, gridY),
                            end = Offset(size.width - padding, gridY),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        )

                        // Y Label
                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawText(
                                formatKoreanEok(gridValue),
                                padding - 10f,
                                gridY + 8f,
                                textPaint
                            )
                        }
                    }

                    // 2. Draw Target Line (Orange/Red dashed line)
                    val targetY = size.height - padding - (savingTarget.toFloat() / maxVal) * chartHeight
                    if (targetY in padding..(size.height - padding)) {
                        drawLine(
                            color = Color(0xFFF4511E),
                            start = Offset(padding, targetY),
                            end = Offset(size.width - padding, targetY),
                            strokeWidth = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 10f), 0f)
                        )
                        drawIntoCanvas { canvas ->
                            val targetPaint = Paint().asFrameworkPaint().apply {
                                color = android.graphics.Color.parseColor("#F4511E")
                                textSize = 22f
                                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                                textAlign = android.graphics.Paint.Align.LEFT
                            }
                            canvas.nativeCanvas.drawText(
                                "목표: ${formatKoreanEok(savingTarget)}",
                                padding + 10f,
                                targetY - 10f,
                                targetPaint
                            )
                        }
                    }

                    // 3. Build projection coordinates
                    val coords = List(6) { i ->
                        val x = padding + i * stepWidth
                        val y = size.height - padding - (projectedPoints[i].toFloat() / maxVal) * chartHeight
                        Offset(x, y)
                    }

                    // 4. Draw Area Path (linear gradient fill)
                    val areaPath = Path().apply {
                        moveTo(coords[0].x, size.height - padding)
                        for (coord in coords) {
                            lineTo(coord.x, coord.y)
                        }
                        lineTo(coords.last().x, size.height - padding)
                        close()
                    }
                    drawPath(
                        path = areaPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0x5F1E88E5), Color(0x001E88E5)),
                            startY = coords.minOf { it.y },
                            endY = size.height - padding
                        )
                    )

                    // 5. Draw Curve Line
                    val linePath = Path().apply {
                        moveTo(coords[0].x, coords[0].y)
                        for (i in 1 until coords.size) {
                            lineTo(coords[i].x, coords[i].y)
                        }
                    }
                    drawPath(
                        path = linePath,
                        color = Color(0xFF1E88E5),
                        style = Stroke(width = 3.5.dp.toPx())
                    )

                    // 6. Draw Dots & X Labels
                    val labelPaint = Paint().asFrameworkPaint().apply {
                        color = android.graphics.Color.GRAY
                        textSize = 24f
                        textAlign = android.graphics.Paint.Align.CENTER
                    }

                    coords.forEachIndexed { i, coord ->
                        val isSelected = selectedIndex == i

                        if (isSelected) {
                            // Semi-transparent glowing ring for professional recharts touch feedback
                            drawCircle(
                                color = Color(0x3D1E88E5),
                                radius = 14.dp.toPx(),
                                center = coord
                            )
                        }

                        // Draw Dot Shadow
                        drawCircle(
                            color = Color(0x33000000),
                            radius = if (isSelected) 8.dp.toPx() else 5.dp.toPx(),
                            center = coord + Offset(0f, 2f)
                        )

                        // Draw Dot
                        drawCircle(
                            color = if (isSelected) Color(0xFF1565C0) else Color(0xFF1E88E5),
                            radius = if (isSelected) 7.dp.toPx() else 4.dp.toPx(),
                            center = coord
                        )
                        drawCircle(
                            color = Color.White,
                            radius = if (isSelected) 4.dp.toPx() else 2.dp.toPx(),
                            center = coord
                        )

                        // Draw X Label
                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawText(
                                labels[i],
                                coord.x,
                                size.height - padding + 35f,
                                labelPaint
                            )
                        }
                    }

                    // 7. Draw Interaction Indicator Line & Tooltip Anchor
                    selectedIndex?.let { idx ->
                        val coord = coords[idx]
                        // Vertical tracker line
                        drawLine(
                            color = Color(0xFF1E88E5),
                            start = Offset(coord.x, padding),
                            end = Offset(coord.x, size.height - padding),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
                        )
                    }
                }

                // Standard Android floating UI overlay Tooltip
                selectedIndex?.let { idx ->
                    val value = projectedPoints[idx]
                    val label = labels[idx]
                    val isCrossTarget = value >= savingTarget
                    val diff = value - savingTarget
                    val absDiff = kotlin.math.abs(diff)

                    Box(
                        modifier = Modifier
                            .align(
                                if (idx <= 2) Alignment.TopEnd else Alignment.TopStart
                            )
                            .padding(16.dp)
                            .background(Color(0xF00F172A), RoundedCornerShape(14.dp))
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(14.dp))
                            .padding(12.dp)
                            .widthIn(max = 240.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Header
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "📅 $label 시점 자산 정보",
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            // Divider Line
                            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Color(0xFF334155)))
                            
                            // Projected Asset Info Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(modifier = Modifier.size(6.dp).background(Color(0xFF1E88E5), CircleShape))
                                    Text("예상 자산", fontSize = 11.sp, color = Color(0xFFCBD5E1))
                                }
                                Text(
                                    text = formatKoreanMoney(value),
                                    fontSize = 11.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            // Target Asset Info Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(modifier = Modifier.size(6.dp).background(Color(0xFFF4511E), CircleShape))
                                    Text("목표 자산", fontSize = 11.sp, color = Color(0xFFCBD5E1))
                                }
                                Text(
                                    text = formatKoreanMoney(savingTarget),
                                    fontSize = 11.sp,
                                    color = Color(0xFFFDA4AF),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            // Divider Line
                            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Color(0xFF334155)))
                            
                            // Gap / Difference Analysis Row
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (diff >= 0) "목표 대비 초과" else "목표 대비 부족",
                                        fontSize = 10.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                    Text(
                                        text = if (diff >= 0) "+${formatKoreanMoney(absDiff)}" else "-${formatKoreanMoney(absDiff)}",
                                        fontSize = 11.sp,
                                        color = if (diff >= 0) Color(0xFF4ADE80) else Color(0xFFF87171),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                
                                Text(
                                    text = if (isCrossTarget) "🎉 이미 목표 자산을 넘어섰어요!" else "💪 은퇴 준비를 위해 저축 지속 필요",
                                    fontSize = 9.sp,
                                    color = if (isCrossTarget) Color(0xFF4ADE80) else Color(0xFFFBBF24),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun formatKoreanMoney(amount: Long): String {
    if (amount <= 0) return "0원"
    val eok = amount / 100_000_000L
    val man = (amount % 100_000_000L) / 10_000L
    return when {
        eok > 0 && man > 0 -> "${eok}억 ${DecimalFormat("#,###").format(man)}만원"
        eok > 0 -> "${eok}억원"
        else -> "${DecimalFormat("#,###").format(man)}만원"
    }
}

fun formatKoreanEok(amount: Long): String {
    val eok = amount / 100_000_000L
    return if (eok > 0) "${eok}억" else "${amount / 10_000L}만"
}

fun calculateMonthlySavingWithInterest(totalFund: Long, years: Int, annualRate: Double): Long {
    val months = years * 12
    if (months <= 0) return totalFund
    if (annualRate <= 0.0) return totalFund / months
    
    val r = annualRate / 12.0
    val denominator = java.lang.Math.pow(1.0 + r, months.toDouble()) - 1.0
    if (denominator <= 0.0) return totalFund / months
    return (totalFund * (r / denominator)).toLong()
}

fun calculateFutureValue(monthlyDeposit: Long, months: Int, annualRate: Double): Long {
    if (months <= 0) return 0L
    if (annualRate <= 0.0) return monthlyDeposit * months
    val r = annualRate / 12.0
    val numerator = java.lang.Math.pow(1.0 + r, months.toDouble()) - 1.0
    return (monthlyDeposit * (numerator / r)).toLong()
}

fun drawWrappedText(
    canvas: android.graphics.Canvas,
    text: String,
    x: Float,
    y: Float,
    paint: android.graphics.Paint,
    maxWidth: Float,
    lineHeight: Float
): Float {
    var currentY = y
    val lines = text.split("\n")
    for (line in lines) {
        if (line.isEmpty()) {
            currentY += lineHeight
            continue
        }
        var wordStart = 0
        while (wordStart < line.length) {
            val count = paint.breakText(line, wordStart, line.length, true, maxWidth, null)
            val sub = line.substring(wordStart, wordStart + count)
            canvas.drawText(sub, x, currentY, paint)
            currentY += lineHeight
            wordStart += count
        }
    }
    return currentY
}

fun savePdfToDownloads(context: android.content.Context, pdfFile: java.io.File): android.net.Uri? {
    val filename = "LifeClear_Retirement_Report_${System.currentTimeMillis()}.pdf"
    return try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    pdfFile.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }
            uri
        } else {
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val targetFile = java.io.File(downloadsDir, filename)
            pdfFile.inputStream().use { inputStream ->
                java.io.FileOutputStream(targetFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            android.net.Uri.fromFile(targetFile)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun sharePdfReport(context: android.content.Context, uri: android.net.Uri, nickname: String) {
    try {
        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            putExtra(android.content.Intent.EXTRA_SUBJECT, "[인생맑음] ${nickname}님의 은퇴 준비 종합 진단 리포트")
            putExtra(
                android.content.Intent.EXTRA_TEXT,
                "안녕하세요! 인생맑음에서 발행한 ${nickname}님의 은퇴 준비 현황 및 맞춤 AI 전략 보고서(PDF)를 공유합니다."
            )
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = android.content.Intent.createChooser(shareIntent, "은퇴 리포트 공유하기")
        chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "공유 실패: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
    }
}

fun openPdfReport(context: android.content.Context, uri: android.net.Uri) {
    try {
        val viewIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(viewIntent)
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "PDF 뷰어를 찾을 수 없습니다.", android.widget.Toast.LENGTH_SHORT).show()
    }
}

fun generateRetirementPdfReport(
    context: android.content.Context,
    user: com.example.data.database.UserEntity,
    currentAge: Int,
    targetAge: Int,
    expectedLifespan: Int,
    monthlyExpenses: Long,
    simpleRequiredFund: Long,
    yieldRequiredFund: Long,
    plannerSelectedYieldRule: Boolean,
    plannerAnnualReturnRate: Float,
    monthlySavingRequired: Long,
    milestone5YearFund: Long,
    milestone10YearFund: Long,
    calculatorAdvice: String,
    liveLocalTip: String,
    totalSolvedSavings: Long = 0L,
    logs: List<com.example.data.database.FinancialLogEntity> = emptyList(),
    openShareChooserDirectly: Boolean = true,
    onComplete: ((java.io.File, android.net.Uri?) -> Unit)? = null
) {
    try {
        val plannerTargetFund = if (plannerSelectedYieldRule) yieldRequiredFund else simpleRequiredFund
        val pdfDocument = android.graphics.pdf.PdfDocument()
        
        // PAGE 1: Summary and Planner
        val pageInfo1 = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page1 = pdfDocument.startPage(pageInfo1)
        val canvas1 = page1.canvas
        
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
        }
        
        // Colors
        val primaryColor = android.graphics.Color.rgb(30, 136, 229) // Material Blue #1E88E5
        val darkGray = android.graphics.Color.rgb(51, 65, 85) // Slate Dark
        val lightGray = android.graphics.Color.rgb(100, 116, 139) // Slate Light
        val bgBoxColor = android.graphics.Color.rgb(248, 250, 252) // Light gray box
        val borderBoxColor = android.graphics.Color.rgb(226, 232, 240) // Border gray
        val accentColor = android.graphics.Color.rgb(16, 185, 129) // Emerald Green
        val warningColor = android.graphics.Color.rgb(245, 158, 11) // Amber
        
        // Margins & Dimensions
        val leftMargin = 40f
        val rightMargin = 555f
        val contentWidth = rightMargin - leftMargin
        
        // Title block
        paint.color = primaryColor
        paint.textSize = 22f
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        canvas1.drawText("인생맑음 개인 맞춤 은퇴 리포트", leftMargin, 55f, paint)
        
        paint.color = lightGray
        paint.textSize = 10f
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        val currentDateTime = java.text.SimpleDateFormat("yyyy년 MM월 dd일 HH:mm", java.util.Locale.KOREAN).format(java.util.Date())
        canvas1.drawText("발행일: $currentDateTime  |  고객명: ${user.nickname}님  |  인생맑음 금융진단센터", leftMargin, 75f, paint)
        
        // Top decorative bar
        paint.color = primaryColor
        canvas1.drawRect(leftMargin, 85f, rightMargin, 88f, paint)
        
        // SECTION 1: 회원 프로필 및 현재 자산/목표 진단 현황
        var yPos = 115f
        paint.color = darkGray
        paint.textSize = 13f
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        canvas1.drawText("1. 자산 현황 및 은퇴 목표 종합 진단", leftMargin, yPos, paint)
        yPos += 14f
        
        val summaryBoxRect = android.graphics.RectF(leftMargin, yPos, rightMargin, yPos + 115f)
        paint.color = bgBoxColor
        paint.style = android.graphics.Paint.Style.FILL
        canvas1.drawRoundRect(summaryBoxRect, 8f, 8f, paint)
        paint.color = borderBoxColor
        paint.style = android.graphics.Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas1.drawRoundRect(summaryBoxRect, 8f, 8f, paint)
        paint.style = android.graphics.Paint.Style.FILL
        
        val totalAssetsWithLeaks = user.savingCurrent + totalSolvedSavings
        val achieveRate = ((totalAssetsWithLeaks.toDouble() / user.savingTarget.coerceAtLeast(1L)) * 100.0).coerceIn(0.0, 100.0)
        val gapToTarget = (user.savingTarget - totalAssetsWithLeaks).coerceAtLeast(0L)
        
        paint.color = darkGray
        paint.textSize = 10f
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        
        val col1 = leftMargin + 16f
        val col2 = leftMargin + 270f
        
        canvas1.drawText("• 현재 순저축 자산:", col1, yPos + 25f, paint)
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        canvas1.drawText(formatKoreanMoney(user.savingCurrent), col1 + 105f, yPos + 25f, paint)
        
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        canvas1.drawText("• 은퇴 목표 자산:", col2, yPos + 25f, paint)
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        paint.color = primaryColor
        canvas1.drawText(formatKoreanMoney(user.savingTarget), col2 + 95f, yPos + 25f, paint)
        
        paint.color = darkGray
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        canvas1.drawText("• 지출구멍 절약 누적:", col1, yPos + 50f, paint)
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        paint.color = accentColor
        canvas1.drawText("+ " + formatKoreanMoney(totalSolvedSavings), col1 + 105f, yPos + 50f, paint)
        
        paint.color = darkGray
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        canvas1.drawText("• 은퇴 목표 달성률:", col2, yPos + 50f, paint)
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        paint.color = if (achieveRate >= 70) accentColor else primaryColor
        canvas1.drawText(String.format(java.util.Locale.US, "%.1f%% 달성", achieveRate), col2 + 95f, yPos + 50f, paint)
        
        paint.color = darkGray
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        canvas1.drawText("• 종합 합산 준비 자산:", col1, yPos + 75f, paint)
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        paint.color = darkGray
        canvas1.drawText(formatKoreanMoney(totalAssetsWithLeaks), col1 + 105f, yPos + 75f, paint)
        
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        canvas1.drawText("• 목표 달성 잔여 갭:", col2, yPos + 75f, paint)
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        paint.color = if (gapToTarget == 0L) accentColor else warningColor
        canvas1.drawText(if (gapToTarget == 0L) "목표 완수!" else formatKoreanMoney(gapToTarget), col2 + 95f, yPos + 75f, paint)

        // Progress bar inside box
        val barTop = yPos + 92f
        val barWidth = contentWidth - 32f
        paint.color = android.graphics.Color.rgb(226, 232, 240)
        canvas1.drawRoundRect(android.graphics.RectF(col1, barTop, col1 + barWidth, barTop + 10f), 5f, 5f, paint)
        val fillWidth = (barWidth * (achieveRate / 100.0)).toFloat().coerceIn(4f, barWidth)
        paint.color = primaryColor
        canvas1.drawRoundRect(android.graphics.RectF(col1, barTop, col1 + fillWidth, barTop + 10f), 5f, 5f, paint)

        // SECTION 2: 은퇴 생애주기 시뮬레이션 지표
        yPos += 135f
        paint.color = darkGray
        paint.textSize = 13f
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        canvas1.drawText("2. 은퇴 설계 파라미터 및 필요 노후 자금", leftMargin, yPos, paint)
        yPos += 14f

        val metricsRect = android.graphics.RectF(leftMargin, yPos, rightMargin, yPos + 130f)
        paint.color = bgBoxColor
        paint.style = android.graphics.Paint.Style.FILL
        canvas1.drawRoundRect(metricsRect, 8f, 8f, paint)
        paint.color = borderBoxColor
        paint.style = android.graphics.Paint.Style.STROKE
        canvas1.drawRoundRect(metricsRect, 8f, 8f, paint)
        paint.style = android.graphics.Paint.Style.FILL

        paint.color = darkGray
        paint.textSize = 10f
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)

        val yearsRemaining = (targetAge - currentAge).coerceAtLeast(1)
        val retirementPeriod = (expectedLifespan - targetAge).coerceAtLeast(1)

        canvas1.drawText("• 현재 나이 / 은퇴 목표 나이:", col1, yPos + 25f, paint)
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        canvas1.drawText("${currentAge}세  👉  ${targetAge}세 (잔여 ${yearsRemaining}년)", col1 + 130f, yPos + 25f, paint)

        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        canvas1.drawText("• 기대 수명 / 은퇴 생활 기간:", col2, yPos + 25f, paint)
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        canvas1.drawText("${expectedLifespan}세 (총 ${retirementPeriod}년간)", col2 + 130f, yPos + 25f, paint)

        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        canvas1.drawText("• 은퇴 후 월 예상 생활비:", col1, yPos + 52f, paint)
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        canvas1.drawText("${monthlyExpenses}만 원 / 월", col1 + 130f, yPos + 52f, paint)

        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        canvas1.drawText("• 적용 운용 수익률:", col2, yPos + 52f, paint)
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        canvas1.drawText(String.format(java.util.Locale.US, "연 %.1f%% 복리", plannerAnnualReturnRate), col2 + 130f, yPos + 52f, paint)

        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        canvas1.drawText("• 원금 기준 단순 필요 자금:", col1, yPos + 79f, paint)
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        canvas1.drawText(formatKoreanMoney(simpleRequiredFund), col1 + 130f, yPos + 79f, paint)

        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        canvas1.drawText("• 4% 룰 수익률 반영 필요액:", col2, yPos + 79f, paint)
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        paint.color = primaryColor
        canvas1.drawText(formatKoreanMoney(yieldRequiredFund), col2 + 130f, yPos + 79f, paint)

        paint.color = darkGray
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        canvas1.drawText("• 최종 채택 목표 은퇴 자금:", col1, yPos + 106f, paint)
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        paint.color = accentColor
        canvas1.drawText(formatKoreanMoney(plannerTargetFund), col1 + 130f, yPos + 106f, paint)

        // SECTION 3: 월별 저축 목표액 및 연도별 로드맵
        yPos += 150f
        paint.color = darkGray
        paint.textSize = 13f
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        canvas1.drawText("3. 월 저축 실행 목표 및 구간별 로드맵", leftMargin, yPos, paint)
        yPos += 14f

        val roadmapBoxRect = android.graphics.RectF(leftMargin, yPos, rightMargin, yPos + 195f)
        paint.color = bgBoxColor
        paint.style = android.graphics.Paint.Style.FILL
        canvas1.drawRoundRect(roadmapBoxRect, 8f, 8f, paint)
        paint.color = borderBoxColor
        paint.style = android.graphics.Paint.Style.STROKE
        canvas1.drawRoundRect(roadmapBoxRect, 8f, 8f, paint)
        paint.style = android.graphics.Paint.Style.FILL

        // Highlight card for monthly saving requirement
        val monthlyCardRect = android.graphics.RectF(leftMargin + 14f, yPos + 14f, rightMargin - 14f, yPos + 58f)
        paint.color = android.graphics.Color.rgb(239, 246, 255) // light blue
        canvas1.drawRoundRect(monthlyCardRect, 8f, 8f, paint)
        paint.color = android.graphics.Color.rgb(191, 219, 254)
        paint.style = android.graphics.Paint.Style.STROKE
        canvas1.drawRoundRect(monthlyCardRect, 8f, 8f, paint)
        paint.style = android.graphics.Paint.Style.FILL

        paint.color = android.graphics.Color.rgb(30, 58, 138)
        paint.textSize = 11f
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        canvas1.drawText("🎯 목표 달성을 위한 추천 월 저축액:", leftMargin + 28f, yPos + 40f, paint)
        paint.textSize = 16f
        paint.color = android.graphics.Color.rgb(29, 78, 216)
        canvas1.drawText(formatKoreanMoney(monthlySavingRequired) + " / 월", leftMargin + 225f, yPos + 40f, paint)

        // Roadmap table
        var tableY = yPos + 80f
        paint.color = darkGray
        paint.textSize = 10f
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        canvas1.drawText("구간(타임라인)", leftMargin + 25f, tableY, paint)
        canvas1.drawText("예상 나이", leftMargin + 160f, tableY, paint)
        canvas1.drawText("예상 누적 자산(복리 운용 시)", leftMargin + 280f, tableY, paint)
        canvas1.drawText("상태", leftMargin + 460f, tableY, paint)

        paint.color = borderBoxColor
        canvas1.drawLine(leftMargin + 20f, tableY + 6f, rightMargin - 20f, tableY + 6f, paint)

        // 5-year milestone
        tableY += 24f
        paint.color = darkGray
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        canvas1.drawText("🚀 5년 후 마일스톤", leftMargin + 25f, tableY, paint)
        canvas1.drawText("${currentAge + 5}세", leftMargin + 160f, tableY, paint)
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        canvas1.drawText(formatKoreanMoney(milestone5YearFund), leftMargin + 280f, tableY, paint)
        paint.color = primaryColor
        canvas1.drawText("기반 구축", leftMargin + 460f, tableY, paint)

        // 10-year milestone
        tableY += 24f
        paint.color = darkGray
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        val m10Months = minOf(120, yearsRemaining * 12)
        val calculated10Y = calculateFutureValue(monthlySavingRequired, m10Months, plannerAnnualReturnRate.toDouble() / 100.0)
        canvas1.drawText("🌟 10년 후 마일스톤", leftMargin + 25f, tableY, paint)
        canvas1.drawText("${minOf(currentAge + 10, targetAge)}세", leftMargin + 160f, tableY, paint)
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        canvas1.drawText(formatKoreanMoney(calculated10Y), leftMargin + 280f, tableY, paint)
        paint.color = primaryColor
        canvas1.drawText("가속 성장", leftMargin + 460f, tableY, paint)

        // Retirement target final
        tableY += 24f
        paint.color = darkGray
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        canvas1.drawText("🏆 은퇴 시점 최종 달성", leftMargin + 25f, tableY, paint)
        canvas1.drawText("${targetAge}세", leftMargin + 160f, tableY, paint)
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        paint.color = accentColor
        canvas1.drawText(formatKoreanMoney(plannerTargetFund), leftMargin + 280f, tableY, paint)
        canvas1.drawText("은퇴 개시", leftMargin + 460f, tableY, paint)

        // Footnote page 1
        paint.color = lightGray
        paint.textSize = 9f
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        canvas1.drawText("인생맑음 - 당신의 든든한 노후 준비 동반자  |  1 / 2 페이지", leftMargin, 815f, paint)
        
        pdfDocument.finishPage(page1)
        
        // PAGE 2: AI advice and companion character + Leaks
        val pageInfo2 = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 2).create()
        val page2 = pdfDocument.startPage(pageInfo2)
        val canvas2 = page2.canvas
        
        // Title page 2
        paint.color = primaryColor
        paint.textSize = 18f
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        canvas2.drawText("✨ 은퇴 동반자 AI 맞춤 전략 & 지출 구멍 분석", leftMargin, 55f, paint)
        
        paint.color = lightGray
        paint.textSize = 10f
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        canvas2.drawText("${user.nickname}님의 소비 패턴 및 자산 형성을 위한 전담 동반자의 실시간 조언", leftMargin, 75f, paint)
        
        paint.color = primaryColor
        canvas2.drawRect(leftMargin, 85f, rightMargin, 88f, paint)
        
        // Companion Character Info Box
        val character = CompanionCharacter.fromId(user.characterId)
        val companionName = user.companionCustomName.ifBlank { character.displayName }
        
        yPos = 115f
        val charBoxRect = android.graphics.RectF(leftMargin, yPos, rightMargin, yPos + 55f)
        paint.color = android.graphics.Color.rgb(240, 253, 244) // Mint light bg
        canvas2.drawRoundRect(charBoxRect, 8f, 8f, paint)
        paint.color = android.graphics.Color.rgb(187, 247, 208)
        paint.style = android.graphics.Paint.Style.STROKE
        canvas2.drawRoundRect(charBoxRect, 8f, 8f, paint)
        paint.style = android.graphics.Paint.Style.FILL

        paint.color = darkGray
        paint.textSize = 12f
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        canvas2.drawText("🤖 전담 은퇴 동반자: $companionName (${character.displayName} - ${character.type})", leftMargin + 16f, yPos + 24f, paint)
        paint.color = lightGray
        paint.textSize = 10f
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        canvas2.drawText("동반자 특성: \"${character.desc}\"", leftMargin + 16f, yPos + 42f, paint)

        // AI Advice Speech Box
        yPos += 75f
        paint.color = darkGray
        paint.textSize = 13f
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        canvas2.drawText("💡 ${companionName}의 맞춤형 은퇴 솔루션 브리핑", leftMargin, yPos, paint)
        yPos += 14f

        val adviceBoxHeight = 310f
        val bubbleRect = android.graphics.RectF(leftMargin, yPos, rightMargin, yPos + adviceBoxHeight)
        paint.color = android.graphics.Color.rgb(255, 251, 235) // Warm amber light
        canvas2.drawRoundRect(bubbleRect, 10f, 10f, paint)
        paint.color = android.graphics.Color.rgb(253, 230, 138)
        paint.style = android.graphics.Paint.Style.STROKE
        canvas2.drawRoundRect(bubbleRect, 10f, 10f, paint)
        paint.style = android.graphics.Paint.Style.FILL

        paint.color = android.graphics.Color.rgb(69, 26, 3)
        paint.textSize = 10.5f
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)

        val adviceText = if (calculatorAdvice.isNotBlank()) calculatorAdvice else liveLocalTip
        drawWrappedText(
            canvas = canvas2,
            text = adviceText,
            x = leftMargin + 18f,
            y = yPos + 24f,
            paint = paint,
            maxWidth = contentWidth - 36f,
            lineHeight = 16.5f
        )

        // SECTION: 지출 구멍 차단 내역 요약
        yPos += adviceBoxHeight + 25f
        paint.color = darkGray
        paint.textSize = 13f
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        canvas2.drawText("🛡️ 지출 구멍(재정 누수) 차단 및 절약 실천 현황", leftMargin, yPos, paint)
        yPos += 14f

        val leaksBoxRect = android.graphics.RectF(leftMargin, yPos, rightMargin, yPos + 160f)
        paint.color = bgBoxColor
        canvas2.drawRoundRect(leaksBoxRect, 8f, 8f, paint)
        paint.color = borderBoxColor
        paint.style = android.graphics.Paint.Style.STROKE
        canvas2.drawRoundRect(leaksBoxRect, 8f, 8f, paint)
        paint.style = android.graphics.Paint.Style.FILL

        val solvedLogs = logs.filter { it.isSolved }
        val unsolvedLogs = logs.filter { !it.isSolved }

        paint.color = darkGray
        paint.textSize = 10f
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        canvas2.drawText("• 해결 완료된 구멍: ${solvedLogs.size}건", leftMargin + 16f, yPos + 24f, paint)
        paint.color = accentColor
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        canvas2.drawText("(총 절약 방어액: +${formatKoreanMoney(totalSolvedSavings)})", leftMargin + 160f, yPos + 24f, paint)

        paint.color = darkGray
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        canvas2.drawText("• 주의 필요한 미해결 구멍: ${unsolvedLogs.size}건", leftMargin + 16f, yPos + 46f, paint)
        val unsolvedSum = unsolvedLogs.sumOf { it.amount }
        paint.color = warningColor
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        canvas2.drawText("(예상 누수 방지액: ${formatKoreanMoney(unsolvedSum)})", leftMargin + 160f, yPos + 46f, paint)

        // Top 3 logs display
        var leakListY = yPos + 72f
        paint.color = darkGray
        paint.textSize = 9.5f
        val displayLogs = logs.take(3)
        if (displayLogs.isEmpty()) {
            paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
            canvas2.drawText("등록된 지출 구멍 내역이 없습니다. 일상 속 불필요한 고정 지출을 점검해보세요.", leftMargin + 16f, leakListY, paint)
        } else {
            for (log in displayLogs) {
                paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
                val statusSymbol = if (log.isSolved) "✅ [차단완료]" else "⚠️ [점검필요]"
                canvas2.drawText("$statusSymbol ${log.title}", leftMargin + 16f, leakListY, paint)
                paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                paint.color = if (log.isSolved) accentColor else warningColor
                canvas2.drawText(formatKoreanMoney(log.amount), rightMargin - 100f, leakListY, paint)
                paint.color = darkGray
                leakListY += 20f
            }
        }

        // Footnote page 2
        paint.color = lightGray
        paint.textSize = 9f
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        canvas2.drawText("인생맑음 - 당신의 든든한 노후 준비 동반자  |  2 / 2 페이지", leftMargin, 815f, paint)
        
        pdfDocument.finishPage(page2)
        
        // Save to cache directory
        val reportsDir = java.io.File(context.cacheDir, "reports")
        if (!reportsDir.exists()) reportsDir.mkdirs()
        val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.US).format(java.util.Date())
        val pdfFile = java.io.File(reportsDir, "LifeClear_Report_${user.nickname}_$timeStamp.pdf")
        pdfDocument.writeTo(java.io.FileOutputStream(pdfFile))
        pdfDocument.close()
        
        // Save copy to public Downloads directory
        val savedPublicUri = savePdfToDownloads(context, pdfFile)
        
        // FileProvider share URI
        val authority = "${context.packageName}.fileprovider"
        val shareUri = try {
            androidx.core.content.FileProvider.getUriForFile(context, authority, pdfFile)
        } catch (e: Exception) {
            null
        }
        
        onComplete?.invoke(pdfFile, shareUri)
        
        if (openShareChooserDirectly && shareUri != null) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                sharePdfReport(context, shareUri, user.nickname)
            }
        }

        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(
                context,
                "인생맑음 맞춤 리포트 PDF가 생성되었습니다! (다운로드 폴더 저장 완료) 📄",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }

    } catch (e: Exception) {
        e.printStackTrace()
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(context, "PDF 생성 중 오류 발생: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }
}

// ==================== REPORT PREVIEW & SHARE DIALOGS ====================

@Composable
fun ReportPreviewDialog(
    user: UserEntity,
    calcData: com.example.data.repository.CalculatorData?,
    totalSolvedSavings: Long,
    aiAdvice: String,
    onDismiss: () -> Unit,
    onGenerateAndShare: () -> Unit
) {
    val totalWithLeaks = user.savingCurrent + totalSolvedSavings
    val target = user.savingTarget
    val progress = ((totalWithLeaks.toDouble() / target.coerceAtLeast(1L)) * 100).coerceIn(0.0, 100.0)
    val targetAge = calcData?.targetAge ?: 60
    val currentAge = calcData?.currentAge ?: 35
    val yearsRemaining = (targetAge - currentAge).coerceAtLeast(1)
    val character = CompanionCharacter.fromId(user.characterId)
    val companionName = user.companionCustomName.ifBlank { character.displayName }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("📄", fontSize = 22.sp)
                Column {
                    Text(
                        text = "인생맑음 개인 맞춤 리포트 미리보기",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )
                    Text(
                        text = "${user.nickname}님의 은퇴 준비 종합 진단서 요약",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Key metrics box
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "📊 리포트에 수록될 핵심 지표 (Page 1)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF334155)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("현재 저축 자산", fontSize = 10.sp, color = Color.Gray)
                                Text(formatKoreanMoney(user.savingCurrent), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("목표 은퇴 자산", fontSize = 10.sp, color = Color.Gray)
                                Text(formatKoreanMoney(target), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E88E5))
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("지출 구멍 절약 누적", fontSize = 10.sp, color = Color.Gray)
                                Text("+ " + formatKoreanMoney(totalSolvedSavings), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("목표 달성률", fontSize = 10.sp, color = Color.Gray)
                                Text(String.format(java.util.Locale.US, "%.1f%%", progress), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                            }
                        }

                        LinearProgressIndicator(
                            progress = { (progress / 100.0).toFloat() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = Color(0xFF1E88E5),
                            trackColor = Color(0xFFE2E8F0)
                        )

                        Text(
                            text = "• 은퇴 계획: ${currentAge}세 ➡️ ${targetAge}세 (잔여 ${yearsRemaining}년)\n• 5년/10년/최종 연도별 누적 로드맵 시뮬레이션 수록",
                            fontSize = 11.sp,
                            color = Color(0xFF475569),
                            lineHeight = 16.sp
                        )
                    }
                }

                // AI Companion section box
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7).copy(alpha = 0.4f)),
                    border = BorderStroke(1.dp, Color(0xFFFDE68A))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "💡 AI 동반자 전략 및 처방 (Page 2)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF92400E)
                        )
                        Text(
                            text = "전담 동반자: $companionName (${character.displayName})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF78350F)
                        )
                        Text(
                            text = if (aiAdvice.isNotBlank()) aiAdvice else "현재 자산과 목표 갭을 줄이기 위한 전담 동반자의 실시간 전략이 포함됩니다.",
                            fontSize = 11.sp,
                            color = Color(0xFF451A03),
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 16.sp
                        )
                    }
                }

                Text(
                    text = "💡 '리포트 생성 및 공유'를 누르면 기기 다운로드 폴더에 보관되며, 카카오톡/메일 등으로 즉시 전송할 수 있습니다.",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onGenerateAndShare,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.testTag("confirm_generate_pdf_report")
            ) {
                Text("PDF 리포트 생성 & 공유", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기", color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
            }
        },
        modifier = Modifier.widthIn(max = 480.dp),
        shape = RoundedCornerShape(28.dp),
        containerColor = Color.White
    )
}

@Composable
fun ReportShareSuccessDialog(
    user: UserEntity,
    pdfFile: java.io.File?,
    shareUri: android.net.Uri?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("🎉", fontSize = 22.sp)
                Text(
                    text = "PDF 리포트 생성 완료!",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B)
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "${user.nickname}님의 재무 데이터와 AI 동반자 전략이 담긴 2페이지 PDF 리포트가 기기 다운로드 폴더에 안전하게 보관되었습니다.",
                    fontSize = 12.sp,
                    color = Color(0xFF475569),
                    lineHeight = 17.sp
                )
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9))
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("📁 파일 저장 위치", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
                        Text(
                            text = "다운로드(Downloads) > LifeClear_Report_${user.nickname}.pdf",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (shareUri != null) {
                    OutlinedButton(
                        onClick = { openPdfReport(context, shareUri) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("open_pdf_button")
                    ) {
                        Text("📄 열기", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E88E5))
                    }
                    Button(
                        onClick = { sharePdfReport(context, shareUri, user.nickname) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("share_pdf_button")
                    ) {
                        Text("📤 공유하기", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(onClick = onDismiss, shape = RoundedCornerShape(14.dp)) {
                        Text("확인")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기", color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
            }
        },
        modifier = Modifier.widthIn(max = 480.dp),
        shape = RoundedCornerShape(28.dp),
        containerColor = Color.White
    )
}

@Composable
fun PersonalizedReportSummaryCard(
    user: UserEntity,
    viewModel: RetirementViewModel,
    totalSolvedSavings: Long,
    aiAdvice: String,
    logs: List<FinancialLogEntity>
) {
    val context = LocalContext.current
    val calcData by viewModel.calculatorData.collectAsStateWithLifecycle()
    
    val totalWithLeaks = user.savingCurrent + totalSolvedSavings
    val target = user.savingTarget
    val progress = ((totalWithLeaks.toDouble() / target.coerceAtLeast(1L)) * 100).coerceIn(0.0, 100.0)
    val targetAge = calcData?.targetAge ?: 60
    val currentAge = calcData?.currentAge ?: 35
    val yearsRemaining = (targetAge - currentAge).coerceAtLeast(1)
    val character = CompanionCharacter.fromId(user.characterId)
    val companionName = user.companionCustomName.ifBlank { character.displayName }
    val monthlyExpenses = calcData?.monthlyExpenses ?: 300L

    var showPreviewDialog by remember { mutableStateOf(false) }
    var generatedFile by remember { mutableStateOf<java.io.File?>(null) }
    var generatedUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var isGeneratingPdf by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    fun executeGenerate(openShare: Boolean) {
        if (isGeneratingPdf) return
        isGeneratingPdf = true
        coroutineScope.launch(Dispatchers.Default) {
            try {
                val simpleFund = monthlyExpenses * 12 * ((calcData?.expectedLifespan ?: 90) - targetAge).coerceAtLeast(1) * 10_000L
                val yieldFund = (monthlyExpenses * 12 * 25) * 10_000L
                val gap = (target - totalWithLeaks).coerceAtLeast(0L)
                val monthlySaving = calculateMonthlySavingWithInterest(gap, yearsRemaining, 0.05)
                val m5 = calculateFutureValue(monthlySaving, minOf(60, yearsRemaining * 12), 0.05)
                val m10 = calculateFutureValue(monthlySaving, minOf(120, yearsRemaining * 12), 0.05)

                generateRetirementPdfReport(
                    context = context,
                    user = user,
                    currentAge = currentAge,
                    targetAge = targetAge,
                    expectedLifespan = calcData?.expectedLifespan ?: 90,
                    monthlyExpenses = monthlyExpenses,
                    simpleRequiredFund = simpleFund,
                    yieldRequiredFund = yieldFund,
                    plannerSelectedYieldRule = true,
                    plannerAnnualReturnRate = 5f,
                    monthlySavingRequired = monthlySaving,
                    milestone5YearFund = m5,
                    milestone10YearFund = m10,
                    calculatorAdvice = aiAdvice,
                    liveLocalTip = "목표 달성을 위한 월 저축과 지출 구멍 차단을 실천하세요.",
                    totalSolvedSavings = totalSolvedSavings,
                    logs = logs,
                    openShareChooserDirectly = openShare,
                    onComplete = { file, uri ->
                        generatedFile = file
                        generatedUri = uri
                        showSuccessDialog = true
                    }
                )
            } finally {
                isGeneratingPdf = false
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("personalized_report_summary_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(Color(0xFFEBF5FF), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("📄", fontSize = 22.sp)
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "인생맑음 개인 맞춤 리포트",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B)
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFE0F2FE)
                            ) {
                                Text(
                                    text = "PDF 발행",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0369A1),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "현재 자산 · 목표 갭 · 지출구멍 · AI 동반자 종합 처방전",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }

            // Summary Info Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReportMetricChip(
                    icon = "🎯",
                    label = "목표 달성률",
                    value = String.format(java.util.Locale.US, "%.1f%%", progress),
                    color = Color(0xFF1E88E5)
                )
                ReportMetricChip(
                    icon = "🛡️",
                    label = "구멍 절약액",
                    value = formatKoreanMoney(totalSolvedSavings),
                    color = Color(0xFF10B981)
                )
                ReportMetricChip(
                    icon = "🤖",
                    label = "동반자",
                    value = companionName,
                    color = Color(0xFF8B5CF6)
                )
            }

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { showPreviewDialog = true },
                    modifier = Modifier
                        .weight(0.42f)
                        .height(44.dp)
                        .testTag("preview_personalized_report_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("🔍", fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "미리보기",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF334155)
                        )
                    }
                }

                Button(
                    onClick = { executeGenerate(openShare = true) },
                    modifier = Modifier
                        .weight(0.58f)
                        .height(44.dp)
                        .testTag("generate_personalized_report_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1E88E5),
                        contentColor = Color.White
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (isGeneratingPdf) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "리포트 생성 중...",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text("📄", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "리포트 생성 & 공유",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal: Preview
    if (showPreviewDialog) {
        ReportPreviewDialog(
            user = user,
            calcData = calcData,
            totalSolvedSavings = totalSolvedSavings,
            aiAdvice = aiAdvice,
            onDismiss = { showPreviewDialog = false },
            onGenerateAndShare = {
                showPreviewDialog = false
                executeGenerate(openShare = true)
            }
        )
    }

    // Modal: Success & Share
    if (showSuccessDialog) {
        ReportShareSuccessDialog(
            user = user,
            pdfFile = generatedFile,
            shareUri = generatedUri,
            onDismiss = { showSuccessDialog = false }
        )
    }
}

@Composable
fun ReportMetricChip(icon: String, label: String, value: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(icon, fontSize = 13.sp)
            Column {
                Text(label, fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                Text(value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
            }
        }
    }
}

// ==================== COMPONENT: POLISHED EMOJI ICON ====================
@Composable
fun EmojiIcon(
    emoji: String,
    backgroundColor: Color = Color(0xFFF1F5F9),
    borderColor: Color = Color.Transparent,
    size: androidx.compose.ui.unit.Dp = 48.dp,
    emojiSize: androidx.compose.ui.unit.TextUnit = 24.sp,
    elevation: androidx.compose.ui.unit.Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.size(size),
        shape = CircleShape,
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = if (borderColor != Color.Transparent) BorderStroke(1.5.dp, borderColor) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = emoji,
                fontSize = emojiSize,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ==================== COMPONENT: COMPANION INTERACTIVE CHAT DIALOG ====================
@Composable
fun CompanionChatDialog(
    viewModel: RetirementViewModel,
    onDismissRequest: () -> Unit
) {
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isChatLoading by viewModel.isChatLoading.collectAsStateWithLifecycle()
    val loggedInUser by viewModel.loggedInUser.collectAsStateWithLifecycle()
    val calcData by viewModel.calculatorData.collectAsStateWithLifecycle()
    val totalSolvedSavings by viewModel.totalSolvedSavings.collectAsStateWithLifecycle()
    
    val user = loggedInUser ?: return
    val char = CompanionCharacter.fromId(user.characterId)
    val finalCompName = if (user.companionCustomName.isNotBlank()) user.companionCustomName else char.displayName
    
    var messageInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    
    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }
    
    // Initialize chat session if empty
    LaunchedEffect(Unit) {
        viewModel.initChatIfNeeded()
    }
    
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .widthIn(max = 500.dp)
                .fillMaxHeight(0.88f)
                .imePadding(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header Panel
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color(0xFF1E88E5), Color(0xFF1565C0))
                            )
                        )
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            EmojiIcon(
                                emoji = char.emoji,
                                backgroundColor = Color.White.copy(alpha = 0.2f),
                                borderColor = Color.White.copy(alpha = 0.4f),
                                size = 44.dp,
                                emojiSize = 24.sp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = finalCompName,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "실시간 1:1 안심 은퇴 설계 상담",
                                    fontSize = 11.sp,
                                    color = Color(0xFFE3F2FD)
                                )
                            }
                        }
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Reset chat history button
                            IconButton(
                                onClick = { viewModel.clearChat() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "대화 초기화",
                                    tint = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = onDismissRequest,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "닫기",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }

                // Financial Context Analysis Strip
                val targetAge = calcData?.targetAge ?: 60
                val totalWithLeaks = user.savingCurrent + totalSolvedSavings
                val gap = (user.savingTarget - totalWithLeaks).coerceAtLeast(0L)
                val progress = ((totalWithLeaks.toDouble() / user.savingTarget.coerceAtLeast(1L)) * 100).coerceIn(0.0, 100.0)

                Surface(
                    color = Color(0xFFEFF6FF),
                    border = BorderStroke(1.dp, Color(0xFFDBEAFE)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("📊", fontSize = 12.sp)
                            Text(
                                text = "현재 ${DecimalFormat("#,###").format(totalWithLeaks / 10000L)}만 / 목표 ${DecimalFormat("#,###").format(user.savingTarget / 10000L)}만 (${targetAge}세 은퇴, 갭 ${DecimalFormat("#,###").format(gap / 10000L)}만)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF1E3A8A),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFDBEAFE)) {
                            Text(
                                text = "${String.format("%.1f", progress)}% 달성",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1D4ED8),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                
                // Chat Message List Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(chatMessages, key = { it.id }) { msg ->
                            if (msg.role == "user") {
                                UserChatBubble(msg.content)
                            } else {
                                CompanionChatBubble(msg.content, char.emoji, finalCompName)
                            }
                        }
                        
                        if (isChatLoading) {
                            item {
                                CompanionLoadingBubble(char.emoji, finalCompName)
                            }
                        }
                    }
                }

                // Quick Strategy Prompt Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val promptList = listOf(
                        "🎯 현재 자산과 목표치 갭 정밀 분석",
                        "⏳ ${targetAge}세 은퇴까지 월 필요 저축액",
                        "💡 지출 구멍 줄여 조기 은퇴 1년 앞당기기",
                        "📈 물가상승률 방어 4% 룰 연금 전략"
                    )
                    promptList.forEach { prompt ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF1F5F9),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.clickable {
                                viewModel.sendChatMessage(prompt)
                            }
                        ) {
                            Text(
                                text = prompt,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF334155),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
                
                // Bottom Input Area
                Surface(
                    color = Color.White,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = messageInput,
                            onValueChange = { messageInput = it },
                            placeholder = { Text("은퇴에 대한 고민이나 질문을 보내주세요...", fontSize = 13.sp) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_input_field"),
                            maxLines = 4,
                            shape = RoundedCornerShape(20.dp),
                            colors = sunnyTextFieldColors(
                                containerColor = Color(0xFFF8FAFC),
                                focusedBorderColor = Color(0xFF1E88E5),
                                unfocusedBorderColor = Color(0xFFCBD5E1)
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (messageInput.isNotBlank()) {
                                    viewModel.sendChatMessage(messageInput)
                                    messageInput = ""
                                }
                            },
                            enabled = messageInput.isNotBlank() && !isChatLoading,
                            modifier = Modifier
                                .background(
                                    if (messageInput.isNotBlank() && !isChatLoading) Color(0xFF1E88E5) else Color(0xFFE2E8F0),
                                    shape = CircleShape
                                )
                                .size(44.dp)
                                .testTag("chat_send_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "전송",
                                tint = if (messageInput.isNotBlank() && !isChatLoading) Color.White else Color(0xFF94A3B8),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UserChatBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Card(
            shape = RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E88E5)),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = text,
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun CompanionChatBubble(text: String, emoji: String, name: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        EmojiIcon(
            emoji = emoji,
            backgroundColor = Color(0xFFEFF6FF),
            borderColor = Color(0xFFBFDBFE),
            size = 36.dp,
            emojiSize = 18.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.widthIn(max = 280.dp)) {
            Text(
                text = name,
                fontSize = 11.sp,
                color = Color(0xFF64748B),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
            )
            Card(
                shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Text(
                    text = text,
                    color = Color(0xFF1E293B),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    lineHeight = 19.sp
                )
            }
        }
    }
}

@Composable
fun CompanionLoadingBubble(emoji: String, name: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        EmojiIcon(
            emoji = emoji,
            backgroundColor = Color(0xFFEFF6FF),
            borderColor = Color(0xFFBFDBFE),
            size = 36.dp,
            emojiSize = 18.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = name,
                fontSize = 11.sp,
                color = Color(0xFF64748B),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
            )
            Card(
                shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = Color(0xFF1E88E5)
                    )
                    Text(
                        text = "신중하게 생각하고 있어요...",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }
        }
    }
}

// ==================== COMPONENT: RETIREMENT PROGRESS DASHBOARD CARD ====================
@Composable
fun RetirementProgressDashboardCard(
    user: UserEntity,
    viewModel: RetirementViewModel,
    totalSolvedSavings: Long,
    onEditRequest: () -> Unit
) {
    val context = LocalContext.current
    val lifestyle = RetirementLifestyle.fromId(user.lifestyleId)
    val calcData by viewModel.calculatorData.collectAsStateWithLifecycle()
    
    val currentAge = calcData?.currentAge ?: 35
    val targetAge = calcData?.targetAge ?: 60
    val expectedLifespan = calcData?.expectedLifespan ?: 90
    val monthlyExpenses = calcData?.monthlyExpenses ?: 300L
    val lifestyleTitle = lifestyle.title
    
    // State for target setup form
    var isFormExpanded by remember { mutableStateOf(false) }
    var targetAgeInput by remember(targetAge) { mutableStateOf(targetAge.toString()) }
    var targetAssetsManInput by remember(user.savingTarget) { mutableStateOf((user.savingTarget / 10_000L).toString()) }
    var currentAssetsManInput by remember(user.savingCurrent) { mutableStateOf((user.savingCurrent / 10_000L).toString()) }
    
    val totalWithLeakSavings = user.savingCurrent + totalSolvedSavings
    val progressPercent = ((totalWithLeakSavings.toDouble() / user.savingTarget.coerceAtLeast(1L)) * 100).coerceIn(0.0, 100.0)
    val remainingGap = (user.savingTarget - totalWithLeakSavings).coerceAtLeast(0)
    val excessAmount = (totalWithLeakSavings - user.savingTarget).coerceAtLeast(0)
    
    // Time & Gap calculations
    val yearsRemaining = (targetAge - currentAge).coerceAtLeast(1)
    val monthsRemaining = yearsRemaining * 12
    val monthlySavingsNeeded = if (remainingGap > 0) remainingGap / monthsRemaining else 0L
    
    val formattedCurrent = DecimalFormat("#,###").format(user.savingCurrent)
    val formattedSolved = DecimalFormat("#,###").format(totalSolvedSavings)
    val formattedTotal = DecimalFormat("#,###").format(totalWithLeakSavings)
    val formattedTarget = DecimalFormat("#,###").format(user.savingTarget)
    val formattedRemaining = DecimalFormat("#,###").format(remainingGap)
    val formattedExcess = DecimalFormat("#,###").format(excessAmount)
    val formattedMonthlyNeeded = DecimalFormat("#,###").format(monthlySavingsNeeded)
    
    val progressPercentStr = String.format("%.1f", progressPercent)

    val feedbackText = when {
        progressPercent >= 100.0 -> "🎉 대단합니다! 이미 목표 은퇴 자금을 100% 달성하셨습니다. 꿈꾸던 '${lifestyle.title}' 라이프가 코앞에 다가왔어요!"
        progressPercent >= 80.0 -> "🚀 우와, 거의 다 왔습니다! 은퇴 자금의 80% 이상을 확보하셨네요. 안정적이고 행복한 노후가 머지않았습니다."
        progressPercent >= 50.0 -> "⭐ 기분 좋은 절반의 성공! 목표의 50%를 무사히 돌파했습니다. 은퇴 자금 모으기가 탄탄한 궤도에 올랐어요."
        progressPercent >= 30.0 -> "📈 든든한 발판 마련! 30%를 돌파하며 노후 자금의 튼튼한 뼈대를 완성하셨습니다. 지금 흐름을 계속 유지해보세요!"
        else -> "🌱 은퇴 자금의 씨앗이 싹트고 있습니다! 지출 구멍을 똑똑하게 틀어막아 은퇴 목표에 매일 조금씩 더 다가서고 있어요."
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("retirement_progress_dashboard_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Header Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFFE3F2FD), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFF1E88E5),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "은퇴 자금 목표 달성률",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B)
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFEFF6FF)
                            ) {
                                Text(
                                    text = "${targetAge}세 은퇴 목표",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1D4ED8),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "현재 재무 상황과 목표치 실시간 격차 분석",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
                
                val arrowRotation by animateFloatAsState(
                    targetValue = if (isFormExpanded) 180f else 0f,
                    label = "arrowRotation"
                )

                // Toggle Target Setup Form Button
                FilledTonalButton(
                    onClick = { isFormExpanded = !isFormExpanded },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (isFormExpanded) Color(0xFF1E88E5) else Color(0xFFF1F5F9),
                        contentColor = if (isFormExpanded) Color.White else Color(0xFF334155)
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("toggle_target_form_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "목표 설정",
                        modifier = Modifier
                            .size(16.dp)
                            .rotate(arrowRotation)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isFormExpanded) "닫기" else "목표 설정",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Expandable Target Setting Form
            AnimatedVisibility(
                visible = isFormExpanded,
                enter = expandVertically(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioLowBouncy)
                ) + fadeIn(tween(250)),
                exit = shrinkVertically(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)
                ) + fadeOut(tween(200))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .background(Color(0xFFF8FAFC), RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFF1E88E5), modifier = Modifier.size(18.dp))
                        Text(
                            text = "은퇴 연령 및 목표 자산액 직접 설정",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )
                    }

                    // 1. Target Retirement Age Setting
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("희망 은퇴 연령 (현재 나이: ${currentAge}세)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF475569))
                            Text("${targetAgeInput}세", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E88E5))
                        }
                        // Quick selection chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(50, 55, 60, 65, 70).forEach { age ->
                                val isSelected = targetAgeInput == age.toString()
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) Color(0xFF1E88E5) else Color.White,
                                    border = BorderStroke(1.dp, if (isSelected) Color(0xFF1E88E5) else Color(0xFFCBD5E1)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { targetAgeInput = age.toString() }
                                ) {
                                    Text(
                                        text = "${age}세",
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.White else Color(0xFF475569),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 2. Target Assets Setting (만원 단위)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("은퇴 목표 자산액 (만원 단위)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF475569))
                            val targetVal = targetAssetsManInput.toLongOrNull() ?: 0L
                            Text(
                                if (targetVal >= 10_000) "${targetVal / 10_000}억 ${targetVal % 10_000}만원" else "${DecimalFormat("#,###").format(targetVal)}만원",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E88E5)
                            )
                        }
                        // Quick asset chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(30_000L to "3억", 50_000L to "5억", 70_000L to "7억", 100_000L to "10억", 150_000L to "15억").forEach { (amount, label) ->
                                val isSelected = targetAssetsManInput == amount.toString()
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) Color(0xFF1E88E5) else Color.White,
                                    border = BorderStroke(1.dp, if (isSelected) Color(0xFF1E88E5) else Color(0xFFCBD5E1)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { targetAssetsManInput = amount.toString() }
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.White else Color(0xFF475569),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 6.dp)
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = targetAssetsManInput,
                            onValueChange = { targetAssetsManInput = it.filter { char -> char.isDigit() } },
                            modifier = Modifier.fillMaxWidth().testTag("target_assets_input"),
                            shape = RoundedCornerShape(10.dp),
                            placeholder = { Text("목표 자산액 입력 (예: 50000 = 5억원)") },
                            suffix = { Text("만원", fontSize = 12.sp, color = Color.Gray) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = sunnyTextFieldColors(containerColor = Color.White)
                        )
                    }

                    // 3. Current Assets Setting (만원 단위)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("현재 보유 자산액 (만원 단위)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF475569))
                        OutlinedTextField(
                            value = currentAssetsManInput,
                            onValueChange = { currentAssetsManInput = it.filter { char -> char.isDigit() } },
                            modifier = Modifier.fillMaxWidth().testTag("current_assets_input"),
                            shape = RoundedCornerShape(10.dp),
                            placeholder = { Text("현재 보유 자산 (예: 12000 = 1억 2천만원)") },
                            suffix = { Text("만원", fontSize = 12.sp, color = Color.Gray) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = sunnyTextFieldColors(containerColor = Color.White)
                        )
                    }

                    // Save & Calculate Action Button
                    Button(
                        onClick = {
                            val parsedTargetAge = targetAgeInput.toIntOrNull() ?: targetAge
                            val parsedTargetWon = (targetAssetsManInput.toLongOrNull() ?: (user.savingTarget / 10_000L)) * 10_000L
                            val parsedCurrentWon = (currentAssetsManInput.toLongOrNull() ?: (user.savingCurrent / 10_000L)) * 10_000L
                            
                            viewModel.updateSavingProgress(
                                target = parsedTargetWon,
                                current = parsedCurrentWon,
                                securityFund = user.securityFund,
                                isaContribution = user.isaContribution
                            )
                            viewModel.saveCalculatorData(
                                currentAge = currentAge,
                                targetAge = parsedTargetAge,
                                expectedLifespan = expectedLifespan,
                                monthlyExpenses = monthlyExpenses,
                                currentAssets = parsedCurrentWon / 10_000L
                            )
                            isFormExpanded = false
                            Toast.makeText(context, "은퇴 연령(${parsedTargetAge}세) 및 목표 자산이 성공적으로 저장되었습니다.", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("save_target_form_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("은퇴 목표 저장 및 차이 계산 반영", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Progress Display Layout (Radial gauge concept + detailed list)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left Part: Circle Progress Percentage
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(Color(0xFFF8FAFC), CircleShape)
                        .border(1.dp, Color(0xFFF1F5F9), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    // Circular Progress Track Behind
                    CircularProgressIndicator(
                        progress = 1.0f,
                        modifier = Modifier.size(88.dp),
                        color = Color(0xFFE2E8F0),
                        strokeWidth = 8.dp
                    )
                    // Beautiful Glowing Circular Progress
                    CircularProgressIndicator(
                        progress = (progressPercent / 100.0).toFloat(),
                        modifier = Modifier.size(88.dp),
                        color = if (progressPercent >= 100.0) Color(0xFF10B981) else Color(0xFF1E88E5),
                        strokeWidth = 8.dp
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$progressPercentStr%",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = if (progressPercent >= 100.0) Color(0xFF10B981) else Color(0xFF1E88E5)
                        )
                        Text(
                            text = if (progressPercent >= 100.0) "목표 달성" else "달성 진행 중",
                            fontSize = 9.sp,
                            color = Color(0xFF64748B),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Right Part: Big summary text details
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "현재 준비된 총 자산",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${formattedTotal}원",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF0F172A)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(modifier = Modifier.size(6.dp).background(Color(0xFF1E88E5), CircleShape))
                        Text(
                            text = "목표자산(${targetAge}세): ${formattedTarget}원",
                            fontSize = 11.sp,
                            color = Color(0xFF475569),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Progress Bar Visual Track with Milestones
            RetirementMilestonePath(
                progressPercent = progressPercent,
                formattedTarget = formattedTarget
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ==================== FINANCIAL GAP CALCULATION CARD ====================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (remainingGap > 0) Color(0xFFFFF7ED) else Color(0xFFECFDF5)
                ),
                border = BorderStroke(1.dp, if (remainingGap > 0) Color(0xFFFFEDD5) else Color(0xFFA7F3D0))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                imageVector = if (remainingGap > 0) Icons.Default.Warning else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (remainingGap > 0) Color(0xFFEA580C) else Color(0xFF059669),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "현재 재무 상황 vs 목표치 간 차이",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (remainingGap > 0) Color(0xFF9A3412) else Color(0xFF065F46)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (remainingGap > 0) Color(0xFFFFEDD5) else Color(0xFFD1FAE5)
                        ) {
                            Text(
                                text = if (remainingGap > 0) "부족액: -${formattedRemaining}원" else "목표 100% 달성 (+${formattedExcess}원)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (remainingGap > 0) Color(0xFFC2410C) else Color(0xFF047857),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // Gap details breakdown
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("은퇴까지 남은 기간", fontSize = 11.sp, color = Color(0xFF64748B))
                            Text("${yearsRemaining}년 (${monthsRemaining}개월)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("목표 달성을 위한 월 필요 저축액", fontSize = 11.sp, color = Color(0xFF64748B))
                            Text(
                                text = if (remainingGap > 0) "월 약 ${formattedMonthlyNeeded}원" else "달성 완료 (저축 여유)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (remainingGap > 0) Color(0xFFEA580C) else Color(0xFF059669)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Breakdown Grid (현재 자산, 지출 절약액, 남은 금액 비교)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF8FAFC), RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Row 1: Saving Current
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(modifier = Modifier.size(6.dp).background(Color(0xFF3B82F6), CircleShape))
                        Text("기본 보유 자산", fontSize = 11.sp, color = Color(0xFF475569))
                    }
                    Text(
                        text = "${formattedCurrent}원",
                        fontSize = 11.sp,
                        color = Color(0xFF1E293B),
                        fontWeight = FontWeight.Bold
                    )
                }

                // Row 2: Saved Leakages
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(modifier = Modifier.size(6.dp).background(Color(0xFF10B981), CircleShape))
                        Text("지출 구멍 절약 누적액", fontSize = 11.sp, color = Color(0xFF475569))
                    }
                    Text(
                        text = "+${formattedSolved}원",
                        fontSize = 11.sp,
                        color = Color(0xFF10B981),
                        fontWeight = FontWeight.Bold
                    )
                }

                // Divider
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE2E8F0)))

                // Row 3: Remaining Goal Gap
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(modifier = Modifier.size(6.dp).background(Color(0xFFF59E0B), CircleShape))
                        Text("은퇴 목표 자산액 (${targetAge}세)", fontSize = 11.sp, color = Color(0xFF475569))
                    }
                    Text(
                        text = "${formattedTarget}원",
                        fontSize = 11.sp,
                        color = Color(0xFF1E293B),
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Companion Feedback Bubble
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFEFF6FF), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "💡",
                    fontSize = 16.sp
                )
                Text(
                    text = feedbackText,
                    fontSize = 11.sp,
                    color = Color(0xFF1E40AF),
                    fontWeight = FontWeight.Bold,
                    lineHeight = 15.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ==================== COMPONENT: AI RETIREMENT STRATEGY CHATBOT MODULE ====================
@Composable
fun RetirementAiChatbotModule(
    user: UserEntity,
    viewModel: RetirementViewModel,
    aiAdvice: String,
    isAiLoading: Boolean,
    totalSolvedSavings: Long,
    onOpenChatRequest: () -> Unit
) {
    val char = CompanionCharacter.fromId(user.characterId)
    val lifestyle = RetirementLifestyle.fromId(user.lifestyleId)
    val finalCompName = if (user.companionCustomName.isNotBlank()) user.companionCustomName else char.displayName
    val calcData by viewModel.calculatorData.collectAsStateWithLifecycle()
    
    val targetAge = calcData?.targetAge ?: 60
    val totalWithLeakSavings = user.savingCurrent + totalSolvedSavings
    val remainingGap = (user.savingTarget - totalWithLeakSavings).coerceAtLeast(0)
    val progressPercent = ((totalWithLeakSavings.toDouble() / user.savingTarget.coerceAtLeast(1L)) * 100).coerceIn(0.0, 100.0)
    val progressPercentStr = String.format("%.1f", progressPercent)
    
    var inlineQuestionInput by remember { mutableStateOf("") }
    
    val quickPrompts = listOf(
        "🎯 현재 자산과 목표치 갭 정밀 분석해줘",
        "⏳ ${targetAge}세 은퇴를 위한 월 최적 저축액과 포트폴리오",
        "💡 지출 구멍 줄여 은퇴 1년 앞당기는 법",
        "📈 물가상승률 방어 배당 및 연금 인출 전략"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("retirement_ai_chatbot_module"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
        border = BorderStroke(1.dp, Color(0xFFFDE68A)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row: Companion Persona & Badges
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EmojiIcon(
                        emoji = char.emoji,
                        backgroundColor = Color(0xFFFEF3C7),
                        borderColor = Color(0xFFFDE68A),
                        size = 44.dp,
                        emojiSize = 24.sp,
                        elevation = 2.dp,
                        modifier = Modifier.padding(end = 10.dp)
                    )
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "${finalCompName}의 AI 은퇴 전략 챗봇",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF78350F)
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFFEF3C7)
                            ) {
                                Text(
                                    text = "Gemini 3.5 Flash",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFB45309),
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "현재 자산·목표치 데이터 심층 분석 및 1:1 맞춤 조언",
                            fontSize = 11.sp,
                            color = Color(0xFF92400E)
                        )
                    }
                }
                
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (isAiLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFFD97706)
                        )
                    } else {
                        IconButton(
                            onClick = { viewModel.loadAiAdvice() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "전략 다시 분석",
                                tint = Color(0xFF92400E),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = onOpenChatRequest,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "대화방 열기",
                            tint = Color(0xFF92400E),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Real-time Financial Data Analysis Context Pill
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFFEF3C7),
                border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("📊", fontSize = 12.sp)
                    Text(
                        text = "분석 기준: 현재 총 자산 ${DecimalFormat("#,###").format(totalWithLeakSavings / 10000L)}만원 · 목표 ${DecimalFormat("#,###").format(user.savingTarget / 10000L)}만원 (${targetAge}세 은퇴, 갭 ${DecimalFormat("#,###").format(remainingGap / 10000L)}만원, 달성률 ${progressPercentStr}%)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF92400E),
                        lineHeight = 15.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // AI Advice Text Card Output
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    if (isAiLoading && aiAdvice.isEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFFD97706)
                            )
                            Text(
                                text = "회원님의 현재 자산과 은퇴 목표치 데이터를 정밀 분석하고 있습니다...",
                                fontSize = 12.sp,
                                color = Color(0xFF78350F)
                            )
                        }
                    } else {
                        Text(
                            text = if (aiAdvice.isNotBlank()) aiAdvice else "현재 자산과 은퇴 목표 데이터를 분석할 준비가 되었습니다. 아래 빠른 질문이나 직접 질문을 입력해보세요!",
                            fontSize = 13.sp,
                            color = Color(0xFF451A03),
                            lineHeight = 21.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
            }

            // Quick Strategy Prompt Chips
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "💡 빠른 은퇴 전략 분석 질문 (클릭 시 즉시 조언)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF92400E)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickPrompts.take(2).forEach { prompt ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFEF3C7),
                            border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    viewModel.askCompanionCustomQuestion(prompt)
                                }
                        ) {
                            Text(
                                text = prompt,
                                fontSize = 11.sp,
                                color = Color(0xFF78350F),
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickPrompts.drop(2).forEach { prompt ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFEF3C7),
                            border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    viewModel.askCompanionCustomQuestion(prompt)
                                }
                        ) {
                            Text(
                                text = prompt,
                                fontSize = 11.sp,
                                color = Color(0xFF78350F),
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // Interactive Input Row inside Module
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inlineQuestionInput,
                    onValueChange = { inlineQuestionInput = it },
                    placeholder = { Text("은퇴 전략이나 자산에 대해 무엇이든 질문하세요...", fontSize = 12.sp, color = Color.Gray) },
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .testTag("ai_chatbot_inline_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = sunnyTextFieldColors(
                        containerColor = Color.White,
                        focusedBorderColor = Color(0xFFD97706),
                        unfocusedBorderColor = Color(0xFFFDE68A)
                    ),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (inlineQuestionInput.isNotBlank()) {
                            viewModel.askCompanionCustomQuestion(inlineQuestionInput)
                            inlineQuestionInput = ""
                        }
                    },
                    enabled = inlineQuestionInput.isNotBlank() && !isAiLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(54.dp)
                        .testTag("ai_chatbot_inline_send_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "전송", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("질문", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Button to open Full 1:1 Chat Dialog
            OutlinedButton(
                onClick = onOpenChatRequest,
                modifier = Modifier.fillMaxWidth().testTag("open_full_companion_chat_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFF78350F)
                ),
                border = BorderStroke(1.dp, Color(0xFFD97706))
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("💬 1:1 심층 상담 챗봇 대화방 열기", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ==================== COMPONENT: RETIREMENT FUND NEEDED CALCULATOR ====================
@Composable
fun RetirementCalculatorCard(
    user: UserEntity,
    viewModel: RetirementViewModel
) {
    val context = LocalContext.current

    // String input states for direct numeric entry & validation
    var currentAssetsText by remember { mutableStateOf((user.savingCurrent / 10_000L).toString()) }
    var currentAgeText by remember { mutableStateOf("35") }
    var targetAgeText by remember { mutableStateOf("60") }
    var expectedLifespanText by remember { mutableStateOf("90") }
    var monthlyExpensesText by remember { mutableStateOf("300") } // 만원 단위
    var isPdfDownloading by remember { mutableStateOf(false) }

    LaunchedEffect(user.savingCurrent) {
        currentAssetsText = (user.savingCurrent / 10_000L).toString()
    }

    val calculatorData by viewModel.calculatorData.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadCalculatorData()
    }

    LaunchedEffect(calculatorData) {
        calculatorData?.let { data ->
            currentAssetsText = data.currentAssets.toString()
            currentAgeText = data.currentAge.toString()
            targetAgeText = data.targetAge.toString()
            expectedLifespanText = data.expectedLifespan.toString()
            monthlyExpensesText = data.monthlyExpenses.toString()
        }
    }

    // Direct numeric parsing
    val assetsParsed = currentAssetsText.trim().replace(",", "").toLongOrNull()
    val currentAgeParsed = currentAgeText.trim().toIntOrNull()
    val targetAgeParsed = targetAgeText.trim().toIntOrNull()
    val lifespanParsed = expectedLifespanText.trim().toIntOrNull()
    val monthlyExpensesParsed = monthlyExpensesText.trim().replace(",", "").toLongOrNull()

    // 1. Assets Validation Logic
    val assetsError = when {
        currentAssetsText.isBlank() -> "보유 자산 금액을 입력해 주세요."
        assetsParsed == null -> "유효한 숫자를 입력해 주세요."
        assetsParsed < 0 -> "자산은 0원 이상이어야 합니다."
        assetsParsed > 10_000_000 -> "1,000억원(10,000,000만원) 이하로 입력해 주세요."
        else -> null
    }

    // 2. Current Age Validation Logic
    val currentAgeError = when {
        currentAgeText.isBlank() -> "현재 나이를 입력해 주세요."
        currentAgeParsed == null -> "유효한 정수 나이를 입력해 주세요."
        currentAgeParsed < 18 -> "현재 나이는 최소 18세 이상이어야 합니다."
        currentAgeParsed > 100 -> "현재 나이는 100세 이하여야 합니다."
        else -> null
    }

    // 3. Target Retirement Age Validation Logic
    val targetAgeError = when {
        targetAgeText.isBlank() -> "은퇴 희망 나이를 입력해 주세요."
        targetAgeParsed == null -> "유효한 정수 나이를 입력해 주세요."
        targetAgeParsed < 20 -> "은퇴 희망 나이는 최소 20세 이상이어야 합니다."
        targetAgeParsed > 110 -> "은퇴 희망 나이는 110세 이하여야 합니다."
        currentAgeParsed != null && targetAgeParsed <= currentAgeParsed -> "은퇴 희망 나이는 현재 나이(${currentAgeParsed}세)보다 커야 합니다."
        else -> null
    }

    // 4. Expected Monthly Expenses Validation Logic
    val monthlyExpensesError = when {
        monthlyExpensesText.isBlank() -> "월 예상 지출액을 입력해 주세요."
        monthlyExpensesParsed == null -> "유효한 숫자를 입력해 주세요."
        monthlyExpensesParsed < 10 -> "월 예상 지출액은 최소 10만원 이상이어야 합니다."
        monthlyExpensesParsed > 10_000 -> "월 예상 지출액은 1억원(10,000만원) 이하로 입력해 주세요."
        else -> null
    }

    // 5. Expected Lifespan Validation Logic
    val lifespanError = when {
        expectedLifespanText.isBlank() -> "기대 수명을 입력해 주세요."
        lifespanParsed == null -> "유효한 정수 나이를 입력해 주세요."
        lifespanParsed < 50 -> "기대 수명은 최소 50세 이상이어야 합니다."
        lifespanParsed > 120 -> "기대 수명은 120세 이하여야 합니다."
        targetAgeParsed != null && lifespanParsed <= targetAgeParsed -> "기대 수명은 은퇴 희망 나이(${targetAgeParsed}세)보다 커야 합니다."
        else -> null
    }

    val hasAnyError = assetsError != null || currentAgeError != null || targetAgeError != null || monthlyExpensesError != null || lifespanError != null

    // Safe sanitized values for calculation fallbacks
    val cleanCurrentAge = currentAgeParsed?.coerceIn(18, 100) ?: 35
    val cleanTargetAge = targetAgeParsed?.coerceIn(cleanCurrentAge + 1, 110) ?: 60
    val cleanLifespan = lifespanParsed?.coerceIn(cleanTargetAge + 1, 120) ?: 90
    val cleanMonthlyExpenses = ((monthlyExpensesParsed ?: 300L).coerceIn(10L, 10_000L)) * 10_000L
    val cleanCurrentAssets = ((assetsParsed ?: 0L).coerceAtLeast(0L)) * 10_000L

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Auto-sync logic for Firebase
    LaunchedEffect(cleanCurrentAge, cleanTargetAge, cleanLifespan, cleanMonthlyExpenses, cleanCurrentAssets, hasAnyError) {
        if (hasAnyError) return@LaunchedEffect
        
        val currentDataObj = com.example.data.repository.CalculatorData(
            currentAge = cleanCurrentAge,
            targetAge = cleanTargetAge,
            expectedLifespan = cleanLifespan,
            monthlyExpenses = cleanMonthlyExpenses / 10_000L,
            currentAssets = cleanCurrentAssets / 10_000L
        )
        
        if (calculatorData != null && calculatorData != currentDataObj) {
            delay(1000) // Debounce typing/slider sliding for 1 second
            
            coroutineScope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar("클라우드 동기화 중...", duration = SnackbarDuration.Short)
            }
            
            viewModel.saveCalculatorData(
                currentAge = cleanCurrentAge,
                targetAge = cleanTargetAge,
                expectedLifespan = cleanLifespan,
                monthlyExpenses = cleanMonthlyExpenses / 10_000L,
                currentAssets = cleanCurrentAssets / 10_000L
            )
            
            coroutineScope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar("클라우드와 동기화되었습니다 ☁️", duration = SnackbarDuration.Short)
            }
        } else if (calculatorData == null) {
            // Initial save if no data exists
            viewModel.saveCalculatorData(
                currentAge = cleanCurrentAge,
                targetAge = cleanTargetAge,
                expectedLifespan = cleanLifespan,
                monthlyExpenses = cleanMonthlyExpenses / 10_000L,
                currentAssets = cleanCurrentAssets / 10_000L
            )
        }
    }

    // Financial calculations
    val retirementYears = (cleanLifespan - cleanTargetAge).coerceAtLeast(0)
    val simpleRequiredFund = cleanMonthlyExpenses * 12 * retirementYears
    val yieldRequiredFund = cleanMonthlyExpenses * 12 * 25 // 4% Rule (25 times annual expense)

    // Normalize ratios for visualization bar chart
    val maxVal = maxOf(simpleRequiredFund, yieldRequiredFund, cleanCurrentAssets, user.savingTarget).toFloat().coerceAtLeast(1f)
    val simpleRatio = (simpleRequiredFund.toFloat() / maxVal).coerceIn(0.05f, 1.0f)
    val yieldRatio = (yieldRequiredFund.toFloat() / maxVal).coerceIn(0.05f, 1.0f)
    val currentTargetRatio = (user.savingTarget.toFloat() / maxVal).coerceIn(0.05f, 1.0f)

    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("retirement_calculator_card"),
            shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, if (hasAnyError) Color(0xFFFCA5A5) else Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFFE0F2FE), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🧭", fontSize = 18.sp)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "스마트 은퇴 자금 계산기",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )
                    Text(
                        text = "내 상황과 계획에 꼭 맞는 필요 은퇴 목표액 산출",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }

            // Global Validation Error Warning Banner
            if (hasAnyError) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFEF2F2),
                    border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⚠️", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "입력값에 유효하지 않은 항목이 있습니다",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFDC2626)
                            )
                            val firstError = listOfNotNull(assetsError, currentAgeError, targetAgeError, monthlyExpensesError, lifespanError).firstOrNull() ?: ""
                            Text(
                                text = firstError,
                                fontSize = 10.sp,
                                color = Color(0xFFB91C1C)
                            )
                        }
                        TextButton(
                            onClick = {
                                currentAssetsText = (user.savingCurrent / 10_000L).coerceAtLeast(0L).toString()
                                currentAgeText = "35"
                                targetAgeText = "60"
                                monthlyExpensesText = "300"
                                expectedLifespanText = "90"
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("기본값 복원", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ------------------ PROGRESS BAR SECTION (계산기 상단) ------------------
            val totalSolvedSavings by viewModel.totalSolvedSavings.collectAsStateWithLifecycle()
            val totalWithLeakSavings = cleanCurrentAssets + totalSolvedSavings
            
            val currentTargetProgress = ((totalWithLeakSavings.toDouble() / user.savingTarget.coerceAtLeast(1)) * 100).coerceIn(0.0, 100.0)
            val yieldProgress = ((totalWithLeakSavings.toDouble() / yieldRequiredFund.coerceAtLeast(1)) * 100).coerceIn(0.0, 100.0)
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF8FAFC), RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFFEFF2F6), RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "현재 준비 완료 자산",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF475569)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFE0F2FE), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("적립+지출절약", fontSize = 8.sp, color = Color(0xFF0369A1), fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        text = "${DecimalFormat("#,###").format(totalWithLeakSavings)}원",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF1E88E5)
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Progress Bar 1: Recommended Goal (연 4% 규칙 추천액)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "💡 계산기 추천 목표액 대비 달성률",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF64748B)
                        )
                        Text(
                            text = "${String.format("%.1f", yieldProgress)}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981)
                        )
                    }
                    
                    // Linear Progress Bar for Recommended Target
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFECFDF5))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth((yieldProgress / 100f).toFloat())
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(Color(0xFF34D399), Color(0xFF10B981))
                                    )
                                )
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(10.dp))
                
                // Progress Bar 2: Configured/Current Goal (설정된 목표액)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🎯 설정된 은퇴 목표액 대비 달성률",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF64748B)
                        )
                        Text(
                            text = "${String.format("%.1f", currentTargetProgress)}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E88E5)
                        )
                    }
                    
                    // Linear Progress Bar for Current Target
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEFF6FF))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth((currentTargetProgress / 100f).toFloat())
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(Color(0xFF60A5FA), Color(0xFF1E88E5))
                                    )
                                )
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(18.dp))

            // Inputs Column - KEYWORD-BASED + DIRECT NUMERIC INPUT + ERROR VALIDATION
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // 1. 현재 보유 자산 (Current Assets)
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    border = BorderStroke(1.dp, if (assetsError != null) Color(0xFFEF4444) else Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("💰", fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "현재 보유 자산",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF334155)
                                )
                            }
                            Text(
                                text = if (assetsError == null) formatKoreanMoney(cleanCurrentAssets) else "입력 확인 필요",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (assetsError == null) Color(0xFF1E88E5) else Color(0xFFEF4444)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))

                        // Direct Numeric Input Field
                        OutlinedTextField(
                            value = currentAssetsText,
                            onValueChange = { input ->
                                currentAssetsText = input.filter { it.isDigit() }
                            },
                            label = { Text("자산 직접 입력 (만원 단위)", fontSize = 11.sp) },
                            trailingIcon = {
                                Text("만원", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B), modifier = Modifier.padding(end = 12.dp))
                            },
                            isError = assetsError != null,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("calculator_assets_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.Black,
                                unfocusedTextColor = Color.Black,
                                cursorColor = Color.Black,
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                errorContainerColor = Color(0xFFFEF2F2),
                                focusedBorderColor = Color(0xFF1E88E5),
                                unfocusedBorderColor = Color(0xFFCBD5E1),
                                errorBorderColor = Color(0xFFEF4444)
                            )
                        )

                        if (assetsError != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "⚠️ $assetsError",
                                fontSize = 10.sp,
                                color = Color(0xFFDC2626),
                                fontWeight = FontWeight.Medium
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Quick input buttons (Easy & Keyword-based)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "+1천만" to 1000L,
                                "+5천만" to 5000L,
                                "+1억" to 10000L
                            ).forEach { (label, valueToAdd) ->
                                Button(
                                    onClick = {
                                        val currentVal = assetsParsed ?: 0L
                                        val nextVal = (currentVal + valueToAdd).coerceIn(0L, 200000L)
                                        currentAssetsText = nextVal.toString()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White,
                                        contentColor = Color(0xFF1E88E5)
                                    ),
                                    border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            
                            Button(
                                onClick = { currentAssetsText = "0" },
                                modifier = Modifier
                                    .weight(0.8f)
                                    .height(34.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFEF2F2),
                                    contentColor = Color(0xFFEF4444)
                                ),
                                border = BorderStroke(1.dp, Color(0xFFFEE2E2)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("초기화", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        // Fine-tune Slider
                        val sliderAssetVal = (assetsParsed ?: 0L).toFloat().coerceIn(0f, 200000f)
                        Slider(
                            value = sliderAssetVal,
                            onValueChange = { currentAssetsText = it.toLong().toString() },
                            valueRange = 0f..200000f, // 0 to 20억
                            steps = 199, // 1000만 단위
                            colors = SliderDefaults.colors(
                                activeTrackColor = Color(0xFF1E88E5),
                                thumbColor = Color(0xFF1E88E5),
                                inactiveTrackColor = Color(0xFFE2E8F0)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // 2. 현재 내 나이 (Current Age)
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    border = BorderStroke(1.dp, if (currentAgeError != null) Color(0xFFEF4444) else Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("👤", fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "현재 내 나이",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF334155)
                                )
                            }
                            Text(
                                text = if (currentAgeError == null) "${cleanCurrentAge}세" else "입력 확인 필요",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (currentAgeError == null) Color(0xFF3B82F6) else Color(0xFFEF4444)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = currentAgeText,
                            onValueChange = { input ->
                                currentAgeText = input.filter { it.isDigit() }
                            },
                            label = { Text("현재 나이 입력 (18~100세)", fontSize = 11.sp) },
                            trailingIcon = {
                                Text("세", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B), modifier = Modifier.padding(end = 12.dp))
                            },
                            isError = currentAgeError != null,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("calculator_current_age_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.Black,
                                unfocusedTextColor = Color.Black,
                                cursorColor = Color.Black,
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                errorContainerColor = Color(0xFFFEF2F2),
                                focusedBorderColor = Color(0xFF3B82F6),
                                unfocusedBorderColor = Color(0xFFCBD5E1),
                                errorBorderColor = Color(0xFFEF4444)
                            )
                        )

                        if (currentAgeError != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "⚠️ $currentAgeError",
                                fontSize = 10.sp,
                                color = Color(0xFFDC2626),
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        val sliderCurrentAgeVal = (currentAgeParsed ?: 35).toFloat().coerceIn(18f, 100f)
                        Slider(
                            value = sliderCurrentAgeVal,
                            onValueChange = { currentAgeText = it.toInt().toString() },
                            valueRange = 18f..100f,
                            steps = 81,
                            colors = SliderDefaults.colors(
                                activeTrackColor = Color(0xFF3B82F6),
                                thumbColor = Color(0xFF3B82F6),
                                inactiveTrackColor = Color(0xFFE2E8F0)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // 3. 은퇴 희망 나이 (Retirement Age)
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    border = BorderStroke(1.dp, if (targetAgeError != null) Color(0xFFEF4444) else Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("⏳", fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "은퇴 희망 나이",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF334155)
                                )
                            }
                            Text(
                                text = if (targetAgeError == null) "${cleanTargetAge}세" else "입력 확인 필요",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (targetAgeError == null) Color(0xFF10B981) else Color(0xFFEF4444)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = targetAgeText,
                            onValueChange = { input ->
                                targetAgeText = input.filter { it.isDigit() }
                            },
                            label = { Text("은퇴 희망 나이 직접 입력 (세)", fontSize = 11.sp) },
                            trailingIcon = {
                                Text("세", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B), modifier = Modifier.padding(end = 12.dp))
                            },
                            isError = targetAgeError != null,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("calculator_target_age_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.Black,
                                unfocusedTextColor = Color.Black,
                                cursorColor = Color.Black,
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                errorContainerColor = Color(0xFFFEF2F2),
                                focusedBorderColor = Color(0xFF10B981),
                                unfocusedBorderColor = Color(0xFFCBD5E1),
                                errorBorderColor = Color(0xFFEF4444)
                            )
                        )

                        if (targetAgeError != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "⚠️ $targetAgeError",
                                fontSize = 10.sp,
                                color = Color(0xFFDC2626),
                                fontWeight = FontWeight.Medium
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Keyword options (Easy & Keyword-based)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "조기 은퇴\n(50세)" to 50,
                                "표준 은퇴\n(60세)" to 60,
                                "일반 은퇴\n(65세)" to 65,
                                "느긋한 은퇴\n(70세)" to 70
                            ).forEach { (label, value) ->
                                val isSelected = targetAgeParsed == value
                                Button(
                                    onClick = { targetAgeText = value.toString() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSelected) Color(0xFF10B981) else Color.White,
                                        contentColor = if (isSelected) Color.White else Color(0xFF334155)
                                    ),
                                    border = BorderStroke(1.dp, if (isSelected) Color(0xFF10B981) else Color(0xFFCBD5E1)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(2.dp)
                                ) {
                                    Text(
                                        label, 
                                        fontSize = 9.sp, 
                                        fontWeight = FontWeight.Bold,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        lineHeight = 11.sp
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        // Fine-tune Slider
                        val sliderTargetAgeVal = (targetAgeParsed ?: 60).toFloat().coerceIn(30f, 95f)
                        Slider(
                            value = sliderTargetAgeVal,
                            onValueChange = { targetAgeText = it.toInt().toString() },
                            valueRange = 30f..95f,
                            steps = 64,
                            colors = SliderDefaults.colors(
                                activeTrackColor = Color(0xFF10B981),
                                thumbColor = Color(0xFF10B981),
                                inactiveTrackColor = Color(0xFFE2E8F0)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // 4. 월 예상 지출액 (Expected Monthly Expenses)
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    border = BorderStroke(1.dp, if (monthlyExpensesError != null) Color(0xFFEF4444) else Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("💸", fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "은퇴 후 월 예상 지출액",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF334155)
                                )
                            }
                            Text(
                                text = if (monthlyExpensesError == null) "${DecimalFormat("#,###").format(cleanMonthlyExpenses / 10_000L)}만원" else "입력 확인 필요",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (monthlyExpensesError == null) Color(0xFFF59E0B) else Color(0xFFEF4444)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = monthlyExpensesText,
                            onValueChange = { input ->
                                monthlyExpensesText = input.filter { it.isDigit() }
                            },
                            label = { Text("월 예상 지출액 입력 (만원 단위)", fontSize = 11.sp) },
                            trailingIcon = {
                                Text("만원", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B), modifier = Modifier.padding(end = 12.dp))
                            },
                            isError = monthlyExpensesError != null,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("calculator_monthly_expenses_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.Black,
                                unfocusedTextColor = Color.Black,
                                cursorColor = Color.Black,
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                errorContainerColor = Color(0xFFFEF2F2),
                                focusedBorderColor = Color(0xFFF59E0B),
                                unfocusedBorderColor = Color(0xFFCBD5E1),
                                errorBorderColor = Color(0xFFEF4444)
                            )
                        )

                        if (monthlyExpensesError != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "⚠️ $monthlyExpensesError",
                                fontSize = 10.sp,
                                color = Color(0xFFDC2626),
                                fontWeight = FontWeight.Medium
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Keyword options (Easy & Keyword-based)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "알뜰살뜰\n(150만)" to 150L,
                                "기본생활\n(300만)" to 300L,
                                "여유만만\n(500만)" to 500L,
                                "품격유지\n(800만)" to 800L
                            ).forEach { (label, value) ->
                                val isSelected = monthlyExpensesParsed == value
                                Button(
                                    onClick = { monthlyExpensesText = value.toString() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSelected) Color(0xFFF59E0B) else Color.White,
                                        contentColor = if (isSelected) Color.White else Color(0xFF334155)
                                    ),
                                    border = BorderStroke(1.dp, if (isSelected) Color(0xFFF59E0B) else Color(0xFFCBD5E1)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(2.dp)
                                ) {
                                    Text(
                                        label, 
                                        fontSize = 9.sp, 
                                        fontWeight = FontWeight.Bold,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        lineHeight = 11.sp
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        // Fine-tune Slider
                        val sliderExpenseVal = (monthlyExpensesParsed ?: 300L).toFloat().coerceIn(50f, 1000f)
                        Slider(
                            value = sliderExpenseVal,
                            onValueChange = { monthlyExpensesText = it.toInt().toString() },
                            valueRange = 50f..1000f,
                            steps = 94,
                            colors = SliderDefaults.colors(
                                activeTrackColor = Color(0xFFF59E0B),
                                thumbColor = Color(0xFFF59E0B),
                                inactiveTrackColor = Color(0xFFE2E8F0)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                
                // 5. 기대 수명 (Expected Lifespan) - Clean & Direct Input with Slider
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    border = BorderStroke(1.dp, if (lifespanError != null) Color(0xFFEF4444) else Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "기대 수명 (은퇴 후 준비 기간 계산용)",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                            Text(
                                text = if (lifespanError == null) "${cleanLifespan}세 (은퇴 후 ${retirementYears}년 생존 기준)" else "입력 확인 필요",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (lifespanError == null) Color(0xFF475569) else Color(0xFFEF4444)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = expectedLifespanText,
                            onValueChange = { input ->
                                expectedLifespanText = input.filter { it.isDigit() }
                            },
                            label = { Text("기대 수명 입력 (세)", fontSize = 11.sp) },
                            trailingIcon = {
                                Text("세", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B), modifier = Modifier.padding(end = 12.dp))
                            },
                            isError = lifespanError != null,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("calculator_lifespan_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.Black,
                                unfocusedTextColor = Color.Black,
                                cursorColor = Color.Black,
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                errorContainerColor = Color(0xFFFEF2F2),
                                focusedBorderColor = Color(0xFF64748B),
                                unfocusedBorderColor = Color(0xFFCBD5E1),
                                errorBorderColor = Color(0xFFEF4444)
                            )
                        )

                        if (lifespanError != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "⚠️ $lifespanError",
                                fontSize = 10.sp,
                                color = Color(0xFFDC2626),
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        val sliderLifespanVal = (lifespanParsed ?: 90).toFloat().coerceIn(60f, 120f)
                        Slider(
                            value = sliderLifespanVal,
                            onValueChange = { expectedLifespanText = it.toInt().toString() },
                            valueRange = 60f..120f,
                            steps = 59,
                            colors = SliderDefaults.colors(
                                activeTrackColor = Color(0xFF64748B),
                                thumbColor = Color(0xFF64748B),
                                inactiveTrackColor = Color(0xFFE2E8F0)
                            ),
                            modifier = Modifier.fillMaxWidth().height(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Calculated Results Cards
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF8FAFC), RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "📊 계산된 실시간 필요 은퇴 자금",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )

                // Option 1: Simple Total Sum
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("1. 단순 수명 대비 총합 필요액", fontSize = 11.sp, color = Color(0xFF475569))
                        Text(
                            text = formatKoreanMoney(simpleRequiredFund),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                    }
                    Text(
                        text = "월 지출액 × 12개월 × 은퇴 후 ${retirementYears}년",
                        fontSize = 9.sp,
                        color = Color(0xFF94A3B8)
                    )
                }

                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Color(0xFFE2E8F0)))

                // Option 2: 4% Rule Total Capital
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("2. 연 4% 인출 규칙 자금 (추천)", fontSize = 11.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFFD1FAE5), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text("안전", fontSize = 8.sp, color = Color(0xFF065F46), fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(
                            text = formatKoreanMoney(yieldRequiredFund),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF10B981)
                        )
                    }
                    Text(
                        text = "연간 총 지출액 × 25 (안정적 자산 배당 및 이자 수익으로 원금 보존)",
                        fontSize = 9.sp,
                        color = Color(0xFF059669)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Proportional Bar Comparison Visualizer (시각화 그래프 컴포넌트)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "📈 자산 금액 비교 시각화 그래프",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF475569)
                )

                // Bar 1: My Current Target
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("내 현재 목표 자금", fontSize = 10.sp, color = Color(0xFF475569))
                        Text(formatKoreanMoney(user.savingTarget), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E88E5))
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEFF6FF))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(currentTargetRatio)
                                .background(Color(0xFF1E88E5))
                        )
                    }
                }

                // Bar 2: Simple Total Needed
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("단순 수명 대비 총액", fontSize = 10.sp, color = Color(0xFF475569))
                        Text(formatKoreanMoney(simpleRequiredFund), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF1F5F9))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(simpleRatio)
                                .background(Color(0xFF64748B))
                        )
                    }
                }

                // Bar 3: Yield Recommended Needed
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("연 4% 인출 규칙 필요액 (추천)", fontSize = 10.sp, color = Color(0xFF475569))
                        Text(formatKoreanMoney(yieldRequiredFund), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFECFDF5))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(yieldRatio)
                                .background(Color(0xFF10B981))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 📅 맞춤형 은퇴 저축 설계 플래너 (Structured Savings Planner)
            var plannerSelectedYieldRule by remember { mutableStateOf(true) } // Default to yield recommended
            var plannerAnnualReturnRate by remember { mutableStateOf(5f) } // Default 5%
            
            val yearsToSave = (cleanTargetAge - cleanCurrentAge).coerceAtLeast(1)
            val monthsToSave = yearsToSave * 12
            
            val plannerTargetFund = if (plannerSelectedYieldRule) yieldRequiredFund else simpleRequiredFund
            val plannerTargetName = if (plannerSelectedYieldRule) "연 4% 추천 목표 자금" else "단순 필요 은퇴 자금"
            
            val monthlySavingRequired = calculateMonthlySavingWithInterest(plannerTargetFund, yearsToSave, plannerAnnualReturnRate.toDouble() / 100.0)
            val monthlySavingCash = plannerTargetFund / monthsToSave
            val monthlySavingDifference = (monthlySavingCash - monthlySavingRequired).coerceAtLeast(0L)
            
            // Milestones
            val milestone5YearMonths = minOf(60, monthsToSave)
            val milestone10YearMonths = minOf(120, monthsToSave)
            val milestone5YearFund = calculateFutureValue(monthlySavingRequired, milestone5YearMonths, plannerAnnualReturnRate.toDouble() / 100.0)
            val milestone10YearFund = calculateFutureValue(monthlySavingRequired, milestone10YearMonths, plannerAnnualReturnRate.toDouble() / 100.0)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                    .background(Color(0xFFFBFDFF), RoundedCornerShape(16.dp))
                    .padding(14.dp)
                    .testTag("structured_savings_planner"),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(Color(0xFFEFF6FF), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("📅", fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "체계적 은퇴 저축 플래너",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )
                        Text(
                            text = "목표 달성을 위한 월별 저축액 정밀 설계",
                            fontSize = 10.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                // Remaining Period Info Badges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("준비 기간", fontSize = 9.sp, color = Color(0xFF64748B))
                            Text("${yearsToSave}년 (${monthsToSave}개월)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1.2f)
                            .background(Color(0xFFEFF6FF), RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("기준 목표 자금", fontSize = 9.sp, color = Color(0xFF2563EB))
                            Text(formatKoreanMoney(plannerTargetFund), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D4ED8))
                        }
                    }
                }

                // Tab Selector for Goal Type
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF1F5F9), RoundedCornerShape(10.dp))
                        .padding(3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (plannerSelectedYieldRule) Color.White else Color.Transparent)
                            .clickable { plannerSelectedYieldRule = true }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "연 4% 추천 기준",
                            fontSize = 10.sp,
                            fontWeight = if (plannerSelectedYieldRule) FontWeight.Bold else FontWeight.Medium,
                            color = if (plannerSelectedYieldRule) Color(0xFF10B981) else Color(0xFF64748B)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (!plannerSelectedYieldRule) Color.White else Color.Transparent)
                            .clickable { plannerSelectedYieldRule = false }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "단순 총액 기준",
                            fontSize = 10.sp,
                            fontWeight = if (!plannerSelectedYieldRule) FontWeight.Bold else FontWeight.Medium,
                            color = if (!plannerSelectedYieldRule) Color(0xFF334155) else Color(0xFF64748B)
                        )
                    }
                }

                // Return Rate Slider
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📈 기대 연간 투자 수익률",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF475569)
                        )
                        Text(
                            text = if (plannerAnnualReturnRate == 0f) "0% (단순 예금)" else "${plannerAnnualReturnRate.toInt()}% (복리 투자)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (plannerAnnualReturnRate == 0f) Color(0xFF64748B) else Color(0xFF2563EB)
                        )
                    }
                    Slider(
                        value = plannerAnnualReturnRate,
                        onValueChange = { plannerAnnualReturnRate = it },
                        valueRange = 0f..10f,
                        steps = 9,
                        colors = SliderDefaults.colors(
                            activeTrackColor = Color(0xFF3B82F6),
                            thumbColor = Color(0xFF3B82F6),
                            inactiveTrackColor = Color(0xFFE2E8F0)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Monthly Saving Target Result Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFEFF6FF), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFFDBEAFE), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "🎯 설계된 월별 저축 목표액",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF1E40AF)
                    )
                    Text(
                        text = formatKoreanMoney(monthlySavingRequired),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF1D4ED8)
                    )
                    
                    if (plannerAnnualReturnRate > 0f && monthlySavingDifference > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "복리 효과로 매달 ",
                                fontSize = 9.sp,
                                color = Color(0xFF1E40AF)
                            )
                            Text(
                                text = formatKoreanMoney(monthlySavingDifference),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                            Text(
                                text = " 절약 중! ✨",
                                fontSize = 9.sp,
                                color = Color(0xFF1E40AF)
                            )
                        }
                    } else {
                        Text(
                            text = "투자 수익률이 높아질수록 필요한 월별 저축액이 감소합니다.",
                            fontSize = 9.sp,
                            color = Color(0xFF1E40AF)
                        )
                    }
                }

                // Milestone Progress Table
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "📊 주요 구간별 자산 형성 시뮬레이션",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF475569)
                    )
                    
                    // Milestone Row 1: 5 Years
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⏱️ 5년 후 누적액 (${milestone5YearMonths}개월)",
                            fontSize = 10.sp,
                            color = Color(0xFF64748B)
                        )
                        Text(
                            text = formatKoreanMoney(milestone5YearFund),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF334155)
                        )
                    }
                    
                    // Milestone Row 2: 10 Years (only if monthsToSave > 60)
                    if (monthsToSave > 60) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⏱️ 10년 후 누적액 (${milestone10YearMonths}개월)",
                                fontSize = 10.sp,
                                color = Color(0xFF64748B)
                            )
                            Text(
                                text = formatKoreanMoney(milestone10YearFund),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF334155)
                            )
                        }
                    }

                    // Milestone Row 3: Final Target
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🏁 은퇴 목표 시점 누적액 (${monthsToSave}개월)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF334155)
                        )
                        Text(
                            text = formatKoreanMoney(plannerTargetFund),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (plannerSelectedYieldRule) Color(0xFF10B981) else Color(0xFF1E88E5)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // AI Companion Advice Section
            val calculatorAdvice by viewModel.calculatorAdvice.collectAsStateWithLifecycle()
            val isCalculatorAiLoading by viewModel.isCalculatorAiLoading.collectAsStateWithLifecycle()
            val character = CompanionCharacter.fromId(user.characterId)
            
            // Generate live local advice as default fallback or live tracker
            val liveLocalTip = remember(cleanCurrentAge, cleanTargetAge, cleanLifespan, cleanMonthlyExpenses) {
                // We can construct a lovely instant tip based on the state
                val retirementYears = cleanLifespan - cleanTargetAge
                val expenseInMan = (cleanMonthlyExpenses / 10_000L).toInt()
                val builder = StringBuilder()
                builder.append("현재 입력된 값을 기준으로 ${character.displayName}의 꿀팁을 준비했어요:\n\n")
                if (expenseInMan >= 400) {
                    builder.append("💸 **월 ${expenseInMan}만 원의 은퇴비는 상위권 수준!** 은퇴비가 조금 넉넉한 편이라 은퇴 준비가 버거울 수 있어요. 월 30만 원 정도만 지금 저축으로 전환해 보세요. 그 작은 스노우볼이 목표 달성일을 무려 3년 앞당겨 줍니다!\n\n")
                } else {
                    builder.append("✅ **합리적인 규모의 지출 계획이에요!** 아주 현명하게 소비 구조를 설계하셨어요. 연금 세액공제 한도를 매달 알뜰하게 챙겨 고정 비용 구멍을 더 줄여봅시다!\n\n")
                }
                
                when (character) {
                    CompanionCharacter.TURTLE -> {
                        builder.append("🐢 **거북이 가이드:** 원금 사수가 목표인 당신, 예금 풍차돌리기와 고정식 장기 국채, IRP 안전자산 70% 비중을 채워 편안하고 안전하게 이자를 불리세요!")
                    }
                    CompanionCharacter.SQUIRREL -> {
                        builder.append("🐿️ **다람쥐 가이드:** 자산을 안전하게 배분하는 당신, 연 4% 이상 분기/월배당을 지급하는 고배당 ETF를 도토리처럼 야무지게 모아 현금 흐름을 만드세요!")
                    }
                    CompanionCharacter.EAGLE -> {
                        builder.append("🦅 **독수리 가이드:** 공격적인 투자를 지향하는 당신, 미국 S&P500이나 지수 추종 ETF에 적립식 투자를 세팅해 장기 인플레이션을 완벽히 이겨보세요!")
                    }
                }
                builder.toString()
            }

            val displayedAdvice = if (calculatorAdvice.isNotBlank()) calculatorAdvice else liveLocalTip

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFFF9DB), RoundedCornerShape(20.dp))
                    .border(1.dp, Color(0xFFFEEA85), RoundedCornerShape(20.dp))
                    .padding(16.dp)
                    .testTag("ai_companion_advice_container")
            ) {
                // Character Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.White, CircleShape)
                            .border(1.dp, Color(0xFFFDE047), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(character.emoji, fontSize = 24.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (user.companionCustomName.isNotBlank()) user.companionCustomName else character.displayName,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF78350F)
                        )
                        Text(
                            text = "나의 은퇴 동반자 · ${character.type}",
                            fontSize = 10.sp,
                            color = Color(0xFFB45309)
                        )
                    }
                    
                    // Button to request full analysis using Gemini API
                    Button(
                        onClick = {
                            if (hasAnyError) {
                                val firstError = listOfNotNull(assetsError, currentAgeError, targetAgeError, monthlyExpensesError, lifespanError).firstOrNull() ?: "입력값을 확인해 주세요."
                                Toast.makeText(context, "⚠️ 입력값 오류: $firstError", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            viewModel.loadCalculatorAdvice(
                                currentAge = cleanCurrentAge,
                                targetAge = cleanTargetAge,
                                expectedLifespan = cleanLifespan,
                                monthlyExpensesMan = cleanMonthlyExpenses / 10_000L,
                                simpleRequiredFund = simpleRequiredFund,
                                yieldRequiredFund = yieldRequiredFund
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFEF08A),
                            contentColor = Color(0xFF78350F)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("✨ AI 특급 처방", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Speech bubble content
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    if (isCalculatorAiLoading) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFFD97706),
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "동반자 캐릭터가 내 계산 결과를 분석 중입니다...",
                                fontSize = 11.sp,
                                color = Color(0xFF92400E),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Formatting paragraphs to support markdown-like bullet points cleanly
                            displayedAdvice.split("\n").forEach { line ->
                                if (line.isNotBlank()) {
                                    Text(
                                        text = line,
                                        fontSize = 11.sp,
                                        color = Color(0xFF451A03),
                                        lineHeight = 16.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            FinancialSimulationChart(
                currentAge = cleanCurrentAge,
                targetAge = cleanTargetAge,
                lifespan = cleanLifespan,
                currentAssets = cleanCurrentAssets,
                targetFund = if (plannerSelectedYieldRule) yieldRequiredFund else simpleRequiredFund,
                isYieldRule = plannerSelectedYieldRule
            )
            
            Spacer(modifier = Modifier.height(18.dp))
            
            // Save to Cloud Button
            Button(
                onClick = {
                    if (hasAnyError) {
                        val firstError = listOfNotNull(assetsError, currentAgeError, targetAgeError, monthlyExpensesError, lifespanError).firstOrNull() ?: "입력값을 확인해 주세요."
                        Toast.makeText(context, "⚠️ 입력값 오류: $firstError", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    viewModel.saveCalculatorData(
                        currentAge = cleanCurrentAge,
                        targetAge = cleanTargetAge,
                        expectedLifespan = cleanLifespan,
                        monthlyExpenses = cleanMonthlyExpenses / 10_000L,
                        currentAssets = cleanCurrentAssets / 10_000L
                    )
                    Toast.makeText(context, "클라우드에 안전하게 저장되었습니다 ☁️", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("save_cloud_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (hasAnyError) Color(0xFF94A3B8) else Color(0xFF38BDF8),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("☁️", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "내 데이터 클라우드에 백업하기",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // PDF Download Button
            Button(
                onClick = {
                    if (hasAnyError) {
                        val firstError = listOfNotNull(assetsError, currentAgeError, targetAgeError, monthlyExpensesError, lifespanError).firstOrNull() ?: "입력값을 확인해 주세요."
                        Toast.makeText(context, "⚠️ 입력값 오류: $firstError", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (isPdfDownloading) return@Button
                    isPdfDownloading = true
                    coroutineScope.launch(Dispatchers.Default) {
                        try {
                            generateRetirementPdfReport(
                                context = context,
                                user = user,
                                currentAge = cleanCurrentAge,
                                targetAge = cleanTargetAge,
                                expectedLifespan = cleanLifespan,
                                monthlyExpenses = cleanMonthlyExpenses / 10_000L,
                                simpleRequiredFund = simpleRequiredFund,
                                yieldRequiredFund = yieldRequiredFund,
                                plannerSelectedYieldRule = plannerSelectedYieldRule,
                                plannerAnnualReturnRate = plannerAnnualReturnRate,
                                monthlySavingRequired = monthlySavingRequired,
                                milestone5YearFund = milestone5YearFund,
                                milestone10YearFund = milestone10YearFund,
                                calculatorAdvice = calculatorAdvice,
                                liveLocalTip = liveLocalTip
                            )
                        } finally {
                            isPdfDownloading = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("download_pdf_report_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (hasAnyError) Color(0xFF94A3B8) else Color(0xFF2563EB),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (isPdfDownloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("PDF 생성 중...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Text("📄", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("은퇴 종합 진단 보고서 PDF 다운로드", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons to Apply Calculation
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        if (hasAnyError) {
                            val firstError = listOfNotNull(assetsError, currentAgeError, targetAgeError, monthlyExpensesError, lifespanError).firstOrNull() ?: "입력값을 확인해 주세요."
                            Toast.makeText(context, "⚠️ 입력값 오류: $firstError", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        viewModel.updateSavingProgress(
                            target = simpleRequiredFund,
                            current = cleanCurrentAssets,
                            securityFund = user.securityFund,
                            isaContribution = user.isaContribution
                        )
                        Toast.makeText(context, "단순 총액 목표가 적용되었습니다.", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f).height(40.dp).testTag("apply_simple_target_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (hasAnyError) Color(0xFFF1F5F9) else Color(0xFFE2E8F0),
                        contentColor = if (hasAnyError) Color(0xFF94A3B8) else Color(0xFF334155)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("단순 총액 적용", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        if (hasAnyError) {
                            val firstError = listOfNotNull(assetsError, currentAgeError, targetAgeError, monthlyExpensesError, lifespanError).firstOrNull() ?: "입력값을 확인해 주세요."
                            Toast.makeText(context, "⚠️ 입력값 오류: $firstError", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        viewModel.updateSavingProgress(
                            target = yieldRequiredFund,
                            current = cleanCurrentAssets,
                            securityFund = user.securityFund,
                            isaContribution = user.isaContribution
                        )
                        Toast.makeText(context, "연 4% 추천 목표가 적용되었습니다.", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1.2f).height(40.dp).testTag("apply_yield_target_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (hasAnyError) Color(0xFF94A3B8) else Color(0xFF10B981),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("💡 연 4% 추천 자산 적용", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        } // End of Column
    } // End of Card
        
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 16.dp)
    )
} // End of Box
} // End of RetirementCalculatorCard



@Composable
fun FinancialSimulationChart(
    currentAge: Int,
    targetAge: Int,
    lifespan: Int,
    currentAssets: Long,
    targetFund: Long,
    isYieldRule: Boolean
) {
    var scrubAge by remember { mutableStateOf<Int?>(null) }
    val haptic = LocalHapticFeedback.current

    val dataPoints = remember(currentAge, targetAge, lifespan, currentAssets, targetFund, isYieldRule) {
        val points = mutableListOf<Pair<Int, Float>>()
        val accumulationYears = (targetAge - currentAge).coerceAtLeast(1)
        val depletionYears = (lifespan - targetAge).coerceAtLeast(1)

        for (year in 0..accumulationYears) {
            val age = currentAge + year
            val fraction = year.toFloat() / accumulationYears.toFloat()
            val asset = currentAssets + (targetFund - currentAssets) * (fraction * fraction)
            points.add(age to asset.toFloat())
        }

        for (year in 1..depletionYears) {
            val age = targetAge + year
            val fraction = year.toFloat() / depletionYears.toFloat()
            val asset = if (isYieldRule) {
                targetFund - (targetFund * 0.15f * fraction)
            } else {
                targetFund * (1f - fraction)
            }
            points.add(age to asset.coerceAtLeast(0f))
        }
        points
    }

    val maxAsset = dataPoints.maxOf { it.second }.coerceAtLeast(1f)
    val totalYears = (lifespan - currentAge).coerceAtLeast(1)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF8FAFC), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "📈 노후 자산 변화 시뮬레이션",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF334155)
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                scrubAge?.let { age ->
                    val assetAtAge = dataPoints.firstOrNull { it.first == age }?.second?.toLong() ?: 0L
                    Text(
                        text = "${age}세: ${formatKoreanEok(assetAtAge)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF0284C7),
                        modifier = Modifier
                            .background(Color(0xFFE0F2FE), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = if (isYieldRule) "연 4% 룰 적용" else "단순 소비형",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isYieldRule) Color(0xFF10B981) else Color(0xFF8B5CF6),
                    modifier = Modifier
                        .background(if (isYieldRule) Color(0xFFD1FAE5) else Color(0xFFEDE9FE), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .pointerInput(currentAge, lifespan) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val age = (currentAge + (down.position.x / size.width) * totalYears).toInt().coerceIn(currentAge, lifespan)
                        scrubAge = age
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                        var dragChange: androidx.compose.ui.input.pointer.PointerInputChange?
                        do {
                            val event = awaitPointerEvent()
                            dragChange = event.changes.firstOrNull { it.id == down.id }
                            if (dragChange != null && dragChange.pressed) {
                                val newAge = (currentAge + (dragChange.position.x / size.width) * totalYears).toInt().coerceIn(currentAge, lifespan)
                                if (newAge != scrubAge) {
                                    scrubAge = newAge
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                dragChange.consume()
                            }
                        } while (dragChange != null && dragChange.pressed)

                        scrubAge = null
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val chartHeight = height - 20.dp.toPx()
                
                fun xPos(age: Int): Float = ((age - currentAge).toFloat() / totalYears.toFloat()) * width
                fun yPos(value: Float): Float = chartHeight - (value / maxAsset) * chartHeight

                val path = androidx.compose.ui.graphics.Path()
                val fillPath = androidx.compose.ui.graphics.Path()

                dataPoints.forEachIndexed { index, point ->
                    val x = xPos(point.first)
                    val y = yPos(point.second)
                    if (index == 0) {
                        path.moveTo(x, y)
                        fillPath.moveTo(x, chartHeight)
                        fillPath.lineTo(x, y)
                    } else {
                        path.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                }
                
                val endX = xPos(lifespan)
                fillPath.lineTo(endX, chartHeight)
                fillPath.close()

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF38BDF8).copy(alpha = 0.3f), Color.Transparent),
                        startY = 0f,
                        endY = chartHeight
                    )
                )

                drawPath(
                    path = path,
                    color = Color(0xFF0284C7),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 3.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round
                    )
                )

                val targetX = xPos(targetAge)
                val targetY = yPos(targetFund.toFloat())

                drawLine(
                    color = Color(0xFF94A3B8),
                    start = androidx.compose.ui.geometry.Offset(targetX, 0f),
                    end = androidx.compose.ui.geometry.Offset(targetX, chartHeight),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                )

                drawCircle(
                    color = Color.White,
                    radius = 6.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(targetX, targetY)
                )
                drawCircle(
                    color = Color(0xFF0284C7),
                    radius = 4.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(targetX, targetY)
                )

                // Interactive touch point indicator
                scrubAge?.let { age ->
                    val scrubX = xPos(age)
                    val assetVal = dataPoints.firstOrNull { it.first == age }?.second ?: 0f
                    val scrubY = yPos(assetVal)

                    drawLine(
                        color = Color(0xFF0284C7),
                        start = androidx.compose.ui.geometry.Offset(scrubX, 0f),
                        end = androidx.compose.ui.geometry.Offset(scrubX, chartHeight),
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 7.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(scrubX, scrubY)
                    )
                    drawCircle(
                        color = Color(0xFF0284C7),
                        radius = 5.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(scrubX, scrubY)
                    )
                }
            }
        }
        
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("${currentAge}세", fontSize = 10.sp, color = Color(0xFF64748B))
            Text("은퇴 (${targetAge}세)", fontSize = 10.sp, color = Color(0xFF0284C7), fontWeight = FontWeight.Bold)
            Text("${lifespan}세", fontSize = 10.sp, color = Color(0xFF64748B))
        }
    }
}

@Composable
fun DashboardSettingsView(
    user: UserEntity,
    viewModel: RetirementViewModel
) {
    var selectedCharacterId by remember { mutableStateOf(user.characterId) }
    var customName by remember { mutableStateOf(user.companionCustomName) }
    var customPersona by remember { mutableStateOf(user.companionCustomPersona) }
    var showSaveSuccess by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Header
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("나만의 AI 동반자 설정", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
            Text("은퇴 설계를 함께할 AI 동반자의 성향과 이름을 맞춤 설정해보세요.", fontSize = 14.sp, color = Color(0xFF64748B))
        }

        // Character Selection
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("동반자 유형 선택", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
            
            val characters = CompanionCharacter.values()
            characters.forEach { char ->
                val isSelected = selectedCharacterId == char.id
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedCharacterId = char.id },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) Color(0xFFEFF6FF) else Color.White
                    ),
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) Color(0xFF3B82F6) else Color(0xFFE2E8F0)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EmojiIcon(
                            emoji = char.emoji,
                            backgroundColor = if (isSelected) Color(0xFFDBEAFE) else Color(0xFFF1F5F9),
                            borderColor = if (isSelected) Color(0xFFBFDBFE) else Color(0xFFE2E8F0),
                            size = 48.dp,
                            emojiSize = 24.sp
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(char.displayName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFE0F2FE)) {
                                    Text(char.type, fontSize = 10.sp, color = Color(0xFF0369A1), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(char.desc, fontSize = 12.sp, color = Color(0xFF64748B), lineHeight = 18.sp)
                        }
                    }
                }
            }
        }

        // Customization
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("상세 맞춤 설정", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))

            OutlinedTextField(
                value = customName,
                onValueChange = { customName = it },
                label = { Text("동반자 이름 (선택)") },
                placeholder = { Text("예: 찰리, 은퇴요정") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = sunnyTextFieldColors(containerColor = Color.White)
            )

            OutlinedTextField(
                value = customPersona,
                onValueChange = { customPersona = it },
                label = { Text("추가 성향 및 말투 (선택)") },
                placeholder = { Text("예: 항상 밝고 긍정적인 말투로 응원해줘") },
                modifier = Modifier.fillMaxWidth().height(120.dp),
                shape = RoundedCornerShape(12.dp),
                maxLines = 4,
                colors = sunnyTextFieldColors(containerColor = Color.White)
            )
        }

        // Save Button
        Button(
            onClick = {
                viewModel.updateCompanionSettings(selectedCharacterId, customName, customPersona)
                showSaveSuccess = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("save_settings_button"),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5))
        ) {
            Text("설정 저장하기", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(40.dp))
    }

    if (showSaveSuccess) {
        AlertDialog(
            onDismissRequest = { showSaveSuccess = false },
            title = { Text("저장 완료") },
            text = { Text("동반자 설정이 성공적으로 업데이트되었습니다.") },
            confirmButton = {
                TextButton(onClick = { showSaveSuccess = false }) {
                    Text("확인")
                }
            }
        )
    }
}
@Composable
fun RetirementMilestonePath(
    progressPercent: Double,
    formattedTarget: String
) {
    val milestones = listOf(0, 25, 50, 75, 100)
    
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(24.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            // Background Track
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Color(0xFFE2E8F0), RoundedCornerShape(2.dp))
            )
            
            // Active Track
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = (progressPercent / 100.0).toFloat().coerceIn(0f, 1f))
                    .height(4.dp)
                    .background(Color(0xFF3B82F6), RoundedCornerShape(2.dp))
            )
            
            // Milestone Nodes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                milestones.forEach { milestone ->
                    val isReached = progressPercent >= milestone
                    val isCurrent = progressPercent >= milestone && progressPercent < (milestone + 25)
                    
                    Box(
                        modifier = Modifier
                            .size(if (isCurrent) 20.dp else 16.dp)
                            .background(
                                color = if (isReached) Color(0xFF3B82F6) else Color.White,
                                shape = CircleShape
                            )
                            .border(
                                width = if (isReached) 0.dp else 2.dp,
                                color = if (isReached) Color.Transparent else Color(0xFFCBD5E1),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isReached && milestone > 0) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(10.dp)
                            )
                        } else if (milestone == 100) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = if (isReached) Color.White else Color(0xFF94A3B8),
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                }
            }
        }
        
        // Milestone Labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            milestones.forEach { milestone ->
                val isReached = progressPercent >= milestone
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (milestone == 100) "목표달성" else "${milestone}%",
                        fontSize = 10.sp,
                        fontWeight = if (isReached) FontWeight.Bold else FontWeight.Normal,
                        color = if (isReached) Color(0xFF1E293B) else Color(0xFF94A3B8)
                    )
                    if (milestone == 100) {
                        Text(
                            text = "${formattedTarget}원",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF3B82F6)
                        )
                    }
                }
            }
        }
    }
}
