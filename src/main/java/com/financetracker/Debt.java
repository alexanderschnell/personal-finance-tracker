package com.financetracker;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/** A debt being paid down: a credit card or a student loan. Amounts are exact (BigDecimal). */
public class Debt {

    public enum Type {
        CREDIT_CARD("Credit card"),
        STUDENT_LOAN("Student loan");

        private final String label;

        Type(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final int id;
    private String name;
    private Type type;
    private BigDecimal originalBalance;   // starting point for the progress bar
    private BigDecimal balance;           // what is owed right now
    private BigDecimal apr;               // annual rate in percent, e.g. 19.99
    private BigDecimal minPayment;        // monthly minimum
    private int dueDay;                   // day of month the payment is due (1-31)
    private final LocalDate createdDate;
    private LocalDate paidOffDate;

    public Debt(int id, String name, Type type, BigDecimal originalBalance, BigDecimal balance,
                BigDecimal apr, BigDecimal minPayment, int dueDay, LocalDate createdDate, LocalDate paidOffDate) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.originalBalance = originalBalance;
        this.balance = balance;
        this.apr = apr;
        this.minPayment = minPayment;
        this.dueDay = dueDay;
        this.createdDate = createdDate;
        this.paidOffDate = paidOffDate;
    }

    public boolean isPaidOff() {
        return balance.signum() <= 0;
    }

    /** Fraction of the original balance that has been paid, 0..1. */
    public double progress() {
        if (originalBalance.signum() <= 0) {
            return isPaidOff() ? 1 : 0;
        }
        BigDecimal paid = originalBalance.subtract(balance);
        double p = paid.divide(originalBalance, 6, RoundingMode.HALF_UP).doubleValue();
        return Math.max(0, Math.min(1, p));
    }

    public BigDecimal amountPaidOff() {
        BigDecimal paid = originalBalance.subtract(balance);
        return paid.signum() < 0 ? BigDecimal.ZERO : paid;
    }

    /** Next due date on or after {@code today}; clamps the due day to short months. */
    public LocalDate nextDueDate(LocalDate today) {
        LocalDate candidate = today.withDayOfMonth(Math.min(dueDay, today.lengthOfMonth()));
        if (candidate.isBefore(today)) {
            LocalDate next = today.plusMonths(1);
            candidate = next.withDayOfMonth(Math.min(dueDay, next.lengthOfMonth()));
        }
        return candidate;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public Type getType() { return type; }
    public BigDecimal getOriginalBalance() { return originalBalance; }
    public BigDecimal getBalance() { return balance; }
    public BigDecimal getApr() { return apr; }
    public BigDecimal getMinPayment() { return minPayment; }
    public int getDueDay() { return dueDay; }
    public LocalDate getCreatedDate() { return createdDate; }
    public LocalDate getPaidOffDate() { return paidOffDate; }

    public void setName(String name) { this.name = name; }
    public void setType(Type type) { this.type = type; }
    public void setOriginalBalance(BigDecimal originalBalance) { this.originalBalance = originalBalance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
    public void setApr(BigDecimal apr) { this.apr = apr; }
    public void setMinPayment(BigDecimal minPayment) { this.minPayment = minPayment; }
    public void setDueDay(int dueDay) { this.dueDay = dueDay; }
    public void setPaidOffDate(LocalDate paidOffDate) { this.paidOffDate = paidOffDate; }
}
