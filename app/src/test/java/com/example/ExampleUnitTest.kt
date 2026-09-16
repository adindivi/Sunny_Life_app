package com.example

import com.example.data.util.FinancialCalculator
import com.example.ui.util.DebounceClickHandler
import org.junit.Assert.*
import org.junit.Test

/**
 * 실 서비스 비즈니스 로직(금융 복리 공식, 화폐 포맷팅, 연타 방지) 종합 정밀 검증 단위 테스트
 */
class FinancialCalculatorTest {

    // ==================== 1. 한국어 화폐 표기(formatKoreanMoney) 정밀 검증 ====================

    @Test
    fun formatKoreanMoney_handlesZeroAndNegative() {
        assertEquals("0원", FinancialCalculator.formatKoreanMoney(0L))
        assertEquals("0원", FinancialCalculator.formatKoreanMoney(-1000L))
    }

    @Test
    fun formatKoreanMoney_handlesLessThanTenThousand_fixesZeroManBug() {
        // [버그 수정 검증]: 1만원 미만 지출 구멍(예: 카페 4,500원)이 "0만원"으로 왜곡되지 않고 원 단위로 정확히 표기되는지 검증
        assertEquals("4,500원", FinancialCalculator.formatKoreanMoney(4500L))
        assertEquals("1,000원", FinancialCalculator.formatKoreanMoney(1000L))
        assertEquals("9,990원", FinancialCalculator.formatKoreanMoney(9990L))
    }

    @Test
    fun formatKoreanMoney_handlesManWonRange() {
        assertEquals("1만원", FinancialCalculator.formatKoreanMoney(10_000L))
        assertEquals("5만원", FinancialCalculator.formatKoreanMoney(50_000L))
        assertEquals("300만원", FinancialCalculator.formatKoreanMoney(3_000_000L))
        assertEquals("1,000만원", FinancialCalculator.formatKoreanMoney(10_000_000L))
        assertEquals("9,999만원", FinancialCalculator.formatKoreanMoney(99_990_000L))
    }

    @Test
    fun formatKoreanMoney_handlesEokRange() {
        assertEquals("1억원", FinancialCalculator.formatKoreanMoney(100_000_000L))
        assertEquals("5억원", FinancialCalculator.formatKoreanMoney(500_000_000L))
        assertEquals("1억 5,000만원", FinancialCalculator.formatKoreanMoney(150_000_000L))
        assertEquals("12억원", FinancialCalculator.formatKoreanMoney(1_200_000_000L))
        assertEquals("12억 3,450만원", FinancialCalculator.formatKoreanMoney(1_234_500_000L))
    }

    @Test
    fun formatKoreanEok_abbreviatesCorrectly() {
        assertEquals("0", FinancialCalculator.formatKoreanEok(0L))
        assertEquals("5000만", FinancialCalculator.formatKoreanEok(50_000_000L))
        assertEquals("1억", FinancialCalculator.formatKoreanEok(100_000_000L))
        assertEquals("10억", FinancialCalculator.formatKoreanEok(1_000_000_000L))
    }

    // ==================== 2. 은퇴 자금 복리 연산(PMT 공식) 검증 ====================

    @Test
    fun calculateMonthlySavingWithInterest_zeroInterest_equalsSimpleDivision() {
        // 이자율 0%일 때는 단순 원금 나누기와 일치해야 함: 1200만원 / 12개월 = 100만원
        val total = 12_000_000L
        val years = 1
        val result = FinancialCalculator.calculateMonthlySavingWithInterest(total, years, 0.0)
        assertEquals(1_000_000L, result)
    }

    @Test
    fun calculateMonthlySavingWithInterest_positiveInterest_reducesRequiredMonthlySaving() {
        // 연 5% 복리 수익률 적용 시 단순 저축액(100만원)보다 적은 금액으로도 목표 달성이 가능해야 함 (복리 효과)
        val total = 12_000_000L
        val years = 1
        val zeroRateResult = FinancialCalculator.calculateMonthlySavingWithInterest(total, years, 0.0)
        val compoundResult = FinancialCalculator.calculateMonthlySavingWithInterest(total, years, 0.05)

        assertTrue("복리 적용 시 월 필요 적립액($compoundResult)이 단순 적립액($zeroRateResult)보다 작아야 합니다.", compoundResult < zeroRateResult)
        assertTrue("결과값은 0보다 커야 합니다.", compoundResult > 0L)
    }

    @Test
    fun calculateMonthlySavingWithInterest_handlesEdgeCasesSafely() {
        // 목표액 0원 이하인 경우 0원 반환
        assertEquals(0L, FinancialCalculator.calculateMonthlySavingWithInterest(0L, 10, 0.05))
        assertEquals(0L, FinancialCalculator.calculateMonthlySavingWithInterest(-500L, 10, 0.05))

        // 기간이 0년 이하인 경우 총액 그대로 반환 (Zero-Division 방어)
        assertEquals(10_000_000L, FinancialCalculator.calculateMonthlySavingWithInterest(10_000_000L, 0, 0.05))
        assertEquals(10_000_000L, FinancialCalculator.calculateMonthlySavingWithInterest(10_000_000L, -2, 0.05))
    }

    // ==================== 3. 미래 가치(FV 복리 공식) 검증 ====================

    @Test
    fun calculateFutureValue_zeroInterest_equalsTotalDeposits() {
        // 월 10만원씩 12개월 적립 시 이자율 0%면 120만원이어야 함
        val monthly = 100_000L
        val months = 12
        val result = FinancialCalculator.calculateFutureValue(monthly, months, 0.0)
        assertEquals(1_200_000L, result)
    }

    @Test
    fun calculateFutureValue_positiveInterest_exceedsTotalDeposits() {
        // 월 10만원씩 12개월 적립 시 연 5% 복리면 원금(120만원)을 초과해야 함
        val monthly = 100_000L
        val months = 12
        val result = FinancialCalculator.calculateFutureValue(monthly, months, 0.05)
        assertTrue("복리 미래가치($result)는 단순 합계(120만원)보다 커야 합니다.", result > 1_200_000L)
    }

    @Test
    fun calculate4PercentRuleTarget_multipliesByTwentyFive() {
        // 월 300만원(연 3,600만원) 생활비 -> 4% 규칙 필요 자금은 25배인 9억원
        val annualExpenses = 36_000_000L
        val target = FinancialCalculator.calculate4PercentRuleTarget(annualExpenses)
        assertEquals(900_000_000L, target)
    }

    // ==================== 4. 버튼 연속 클릭(광클) 방지 디바운스 검증 ====================

    @Test
    fun debounceClickHandler_preventsRapidClicksWithinInterval() {
        var simulatedTime = 1000L
        val handler = DebounceClickHandler(intervalMs = 600L, clockProvider = { simulatedTime })

        var executedCount = 0
        val action: () -> Unit = { executedCount++ }

        // 첫 번째 클릭 성공
        val click1 = handler.processClick(action)
        assertTrue("첫 클릭은 즉시 실행되어야 합니다.", click1)
        assertEquals(1, executedCount)

        // 100ms 후 광클 (차단되어야 함)
        simulatedTime += 100L
        val click2 = handler.processClick(action)
        assertFalse("600ms 이내의 연타는 차단되어야 합니다.", click2)
        assertEquals(1, executedCount)

        // 300ms 후 추가 광클 (차단되어야 함)
        simulatedTime += 200L
        val click3 = handler.processClick(action)
        assertFalse("600ms 이내의 연타는 차단되어야 합니다.", click3)
        assertEquals(1, executedCount)

        // 650ms 경과 후 클릭 (성공해야 함)
        simulatedTime += 350L // 누적 650ms
        val click4 = handler.processClick(action)
        assertTrue("인터벌(600ms) 경과 후 클릭은 정상 실행되어야 합니다.", click4)
        assertEquals(2, executedCount)
    }
}
