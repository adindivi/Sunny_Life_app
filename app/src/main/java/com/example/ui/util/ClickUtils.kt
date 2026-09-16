package com.example.ui.util

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.semantics.Role

/**
 * 일반 JVM/단위 테스트 및 일반 리스너에서 활용 가능한 디바운스 핸들러
 */
class DebounceClickHandler(
    private val intervalMs: Long = 600L,
    private val clockProvider: () -> Long = { SystemClock.uptimeMillis() }
) {
    private var lastClickTime = 0L

    /**
     * 마지막 클릭 후 지정된 인터벌이 경과한 경우에만 [action]을 실행합니다.
     * @return 실행되었으면 true, 광클로 차단되었으면 false
     */
    fun processClick(action: () -> Unit): Boolean {
        val now = clockProvider()
        return if (now - lastClickTime >= intervalMs) {
            lastClickTime = now
            action()
            true
        } else {
            false
        }
    }

    fun reset() {
        lastClickTime = 0L
    }
}

/**
 * Button, IconButton, TextButton 등의 onClick 콜백에 즉시 적용 가능한 다중 클릭(광클) 방지 래퍼
 *
 * 사용 예시:
 * ```
 * Button(
 *     onClick = rememberDebouncedClick(debounceInterval = 600L) {
 *         viewModel.login(id, password)
 *     }
 * ) { ... }
 * ```
 */
@Composable
fun rememberDebouncedClick(
    debounceInterval: Long = 600L,
    onClick: () -> Unit
): () -> Unit {
    var lastClickTime by remember { mutableLongStateOf(0L) }
    return remember(onClick, debounceInterval) {
        {
            val currentTime = SystemClock.uptimeMillis()
            if (currentTime - lastClickTime >= debounceInterval) {
                lastClickTime = currentTime
                onClick()
            }
        }
    }
}

/**
 * Modifier 레벨에서 연속 클릭(광클)을 방지하는 clickable 확장 함수
 */
fun Modifier.debouncedClickable(
    debounceInterval: Long = 600L,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onClick: () -> Unit
): Modifier = composed(
    inspectorInfo = debugInspectorInfo {
        name = "debouncedClickable"
        properties["debounceInterval"] = debounceInterval
        properties["enabled"] = enabled
        properties["onClick"] = onClick
    }
) {
    var lastClickTime by remember { mutableLongStateOf(0L) }
    this.clickable(
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role
    ) {
        val currentTime = SystemClock.uptimeMillis()
        if (currentTime - lastClickTime >= debounceInterval) {
            lastClickTime = currentTime
            onClick()
        }
    }
}
