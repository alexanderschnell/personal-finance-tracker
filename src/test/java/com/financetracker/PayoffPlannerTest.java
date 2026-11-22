package com.financetracker;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static com.financetracker.PayoffPlanner.Strategy.AVALANCHE;
import static com.financetracker.PayoffPlanner.Strategy.SNOWBALL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayoffPlannerTest {

    private static PayoffPlanner.DebtInput debt(int id, String balance, String apr, String min) {
        return new PayoffPlanner.DebtInput(id, "d" + id, new BigDecimal(balance), new BigDecimal(apr), new BigDecimal(min));
    }

    @Test
    void interestFreeDebtIsPaidOffInExactMonths() {
        var r = PayoffPlanner.plan(List.of(debt(1, "1200", "0", "100")), new BigDecimal("100"), AVALANCHE);
        assertTrue(r.feasible());
        assertEquals(12, r.months());
        assertEquals(0, r.totalInterest().signum());
        assertEquals(0, r.balanceByMonth().get(12).signum());
    }

    @Test
    void interestIsChargedMonthly() {
        // $1000 at 12% APR = 1%/month; paying $1010/month clears it in one month with $10 interest
        var r = PayoffPlanner.plan(List.of(debt(1, "1000", "12", "10")), new BigDecimal("1010"), AVALANCHE);
        assertEquals(1, r.months());
        assertEquals(new BigDecimal("10.00"), r.totalInterest());
    }

    @Test
    void avalancheNeverPaysMoreInterestThanSnowball() {
        // small low-rate debt vs big high-rate card: the strategies pick different targets
        var debts = List.of(debt(1, "500", "4", "25"), debt(2, "6000", "22.99", "150"));
        var budget = new BigDecimal("600");
        var avalanche = PayoffPlanner.plan(debts, budget, AVALANCHE);
        var snowball = PayoffPlanner.plan(debts, budget, SNOWBALL);
        assertTrue(avalanche.totalInterest().compareTo(snowball.totalInterest()) < 0);
        assertEquals(List.of(2, 1), List.copyOf(avalanche.payoffMonthById().keySet()));
        assertEquals(List.of(1, 2), List.copyOf(snowball.payoffMonthById().keySet()));
    }

    @Test
    void budgetBelowMinimumsIsRejected() {
        var r = PayoffPlanner.plan(List.of(debt(1, "1000", "10", "50"), debt(2, "1000", "10", "50")),
                new BigDecimal("80"), AVALANCHE);
        assertFalse(r.feasible());
    }

    @Test
    void paymentsThatDontCoverInterestNeverFinish() {
        // 24% on $10,000 is $200/month interest; $150/month never pays it down
        var r = PayoffPlanner.plan(List.of(debt(1, "10000", "24", "150")), new BigDecimal("150"), AVALANCHE);
        assertFalse(r.feasible());
    }

    @Test
    void freedMinimumsRollIntoTheNextDebt() {
        var debts = List.of(debt(1, "100", "0", "50"), debt(2, "1000", "0", "50"));
        var r = PayoffPlanner.plan(debts, new BigDecimal("100"), SNOWBALL);
        // month 1: both mins (100) -> d1 50, d2 950; month 2: d1 paid, d2 900;
        // then the full $100 goes to d2: 900 / 100 = 9 more months
        assertEquals(2, r.payoffMonthById().get(1));
        assertEquals(11, r.months());
    }
}
