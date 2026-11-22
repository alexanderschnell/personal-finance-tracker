package com.financetracker;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebtManagerTest {

    @TempDir
    Path tmp;
    private DebtManager dm;
    private final LocalDate start = LocalDate.of(2026, 1, 1);

    @BeforeEach
    void open() {
        dm = new DebtManager(tmp.resolve("test.db").toString());
    }

    @AfterEach
    void close() {
        dm.close();
    }

    @Test
    void paymentSplitsInterestAndPrincipal() {
        int id = dm.addDebt("Loan", Debt.Type.STUDENT_LOAN, new BigDecimal("1000"), new BigDecimal("12"),
                new BigDecimal("100"), 15, start);
        // 30 days at 12%: 1000 * 0.12 * 30 / 365 = 9.86
        Payment p = dm.logPayment(id, start.plusDays(30), new BigDecimal("100"), null);
        assertEquals(new BigDecimal("9.86"), p.interest());
        assertEquals(new BigDecimal("90.14"), p.principal());
        assertEquals(new BigDecimal("909.86"), dm.getDebt(id).getBalance());
    }

    @Test
    void overpayingClearsTheDebtAndMarksItPaidOff() {
        int id = dm.addDebt("Visa", Debt.Type.CREDIT_CARD, new BigDecimal("200"), BigDecimal.ZERO,
                new BigDecimal("25"), 1, start);
        Payment p = dm.logPayment(id, start.plusDays(5), new BigDecimal("250"), "bonus");
        assertEquals(0, p.balanceAfter().signum());
        Debt d = dm.getDebt(id);
        assertTrue(d.isPaidOff());
        assertEquals(start.plusDays(5), d.getPaidOffDate());
        assertEquals(1.0, d.progress());
    }

    @Test
    void updatingBalanceAboveStartRaisesStartingBalance() {
        int id = dm.addDebt("Visa", Debt.Type.CREDIT_CARD, new BigDecimal("500"), new BigDecimal("19.99"),
                new BigDecimal("25"), 1, start);
        dm.setBalance(id, new BigDecimal("650"), start.plusDays(10));
        Debt d = dm.getDebt(id);
        assertEquals(new BigDecimal("650.00"), d.getBalance());
        assertEquals(new BigDecimal("650.00"), d.getOriginalBalance());
        assertNull(d.getPaidOffDate());
    }

    @Test
    void settingsRoundTrip() {
        assertEquals("x", dm.getSetting("missing", "x"));
        dm.setSetting("payoff.budget", "750");
        dm.setSetting("payoff.budget", "800");
        assertEquals("800", dm.getSetting("payoff.budget", null));
    }

    @Test
    void nextDueDateClampsToShortMonths() {
        Debt d = new Debt(1, "x", Debt.Type.CREDIT_CARD, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO,
                BigDecimal.ONE, 31, start, null);
        assertEquals(LocalDate.of(2026, 2, 28), d.nextDueDate(LocalDate.of(2026, 2, 10)));
        assertEquals(LocalDate.of(2026, 3, 31), d.nextDueDate(LocalDate.of(2026, 3, 1)));
    }
}
