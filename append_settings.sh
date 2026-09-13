#!/bin/bash
cat << 'INNEREOF' >> app/src/main/java/com/example/MainActivity.kt

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
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = customPersona,
                onValueChange = { customPersona = it },
                label = { Text("추가 성향 및 말투 (선택)") },
                placeholder = { Text("예: 항상 밝고 긍정적인 말투로 응원해줘") },
                modifier = Modifier.fillMaxWidth().height(120.dp),
                shape = RoundedCornerShape(12.dp),
                maxLines = 4
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
INNEREOF
