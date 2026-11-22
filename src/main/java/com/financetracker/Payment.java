package com.financetracker;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A payment made toward a debt. {@code interest} is the estimated interest that had
 * built up since the previous payment; the rest of the amount went to {@code principal}.
 */
public record Payment(int id, int debtId, LocalDate date, BigDecimal amount,
                      BigDecimal interest, BigDecimal principal, BigDecimal balanceAfter, String note) {
}
