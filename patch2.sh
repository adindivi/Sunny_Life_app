#!/bin/bash
cat << 'INNEREOF' > patch_milestone.txt
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
INNEREOF
