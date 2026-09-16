package com.example.data.util

import java.text.DecimalFormat

/**
 * 은퇴 자금 연산 및 한국어 화폐 표기 전문 계산기 (SRP 원칙 준수 순수 유틸리티)
 */
object FinancialCalculator {

    private val numberFormat = DecimalFormat("#,###")

    /**
     * 원 단위 금액을 가독성 높은 한국어 화폐 문자열(억/만원/원)로 변환합니다.
     * 1만원 미만 소액(예: 4,500원 커피 지출)도 왜곡 없이 원 단위로 정확하게 표기합니다.
     */
    fun formatKoreanMoney(amount: Long): String {
        if (amount <= 0L) return "0원"
        if (amount < 10_000L) {
            return "${numberFormat.format(amount)}원"
        }
        val eok = amount / 100_000_000L
        val man = (amount % 100_000_000L) / 10_000L
        return when {
            eok > 0L && man > 0L -> "${eok}억 ${numberFormat.format(man)}만원"
            eok > 0L -> "${eok}억원"
            else -> "${numberFormat.format(man)}만원"
        }
    }

    /**
     * 그래프 및 뱃지용 억/만 단위 축약 포맷터
     */
    fun formatKoreanEok(amount: Long): String {
        if (amount <= 0L) return "0"
        val eok = amount / 100_000_000L
        return if (eok > 0L) "${eok}억" else "${amount / 10_000L}만"
    }

    /**
     * 목표 은퇴 자금과 연 복리 수익률을 고려하여 매월 적립해야 하는 필요 저축액(PMT)을 산출합니다.
     *
     * @param totalFund 은퇴 시점까지 필요한 총 목표 자금 (원)
     * @param years 은퇴 준비 가능 기간 (년)
     * @param annualRate 기대 연간 투자 수익률 (예: 0.05 = 5%)
     * @return 월별 필요 저축액 (원)
     */
    fun calculateMonthlySavingWithInterest(totalFund: Long, years: Int, annualRate: Double): Long {
        if (totalFund <= 0L) return 0L
        val months = years * 12
        if (months <= 0) return totalFund
        if (annualRate <= 0.0) return (totalFund / months).coerceAtLeast(0L)

        val monthlyRate = annualRate / 12.0
        val denominator = Math.pow(1.0 + monthlyRate, months.toDouble()) - 1.0
        if (denominator <= 0.0) return (totalFund / months).coerceAtLeast(0L)

        val result = totalFund * (monthlyRate / denominator)
        return result.toLong().coerceAtLeast(0L)
    }

    /**
     * 매월 일정 금액을 복리로 적립했을 때 특정 개월 후의 미래 자산 가치(FV)를 계산합니다.
     *
     * @param monthlyDeposit 매월 적립금 (원)
     * @param months 적립 개월 수
     * @param annualRate 기대 연간 투자 수익률 (예: 0.05 = 5%)
     * @return 미래 시점의 총 적립 자산 가치 (원)
     */
    fun calculateFutureValue(monthlyDeposit: Long, months: Int, annualRate: Double): Long {
        if (monthlyDeposit <= 0L || months <= 0) return 0L
        if (annualRate <= 0.0) return monthlyDeposit * months

        val monthlyRate = annualRate / 12.0
        val numerator = Math.pow(1.0 + monthlyRate, months.toDouble()) - 1.0
        if (numerator <= 0.0) return monthlyDeposit * months

        val result = monthlyDeposit * (numerator / monthlyRate)
        return result.toLong().coerceAtLeast(0L)
    }

    /**
     * 연 4% 인출 규칙(Trinity Study 4% Rule)에 따른 필요 자산 총액을 산출합니다.
     * @param annualExpenses 연간 총 예상 생활비 (원)
     * @return 원금을 보존하며 배당/이자로 영구 지속 가능한 추천 자산 총액
     */
    fun calculate4PercentRuleTarget(annualExpenses: Long): Long {
        return (annualExpenses * 25L).coerceAtLeast(0L)
    }
}
