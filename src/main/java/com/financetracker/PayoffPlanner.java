package com.financetracker;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Month-by-month debt payoff simulation.
 *
 * Each month: interest is added to every balance (APR / 12), every debt gets its minimum payment,
 * and whatever is left of the monthly budget goes to the target debt chosen by the strategy.
 * When a debt is paid off its minimum rolls into the extra pool (the "snowball" effect), because
 * the budget stays the same.
 */
public final class PayoffPlanner {

    public enum Strategy {
        AVALANCHE("Avalanche", "Highest interest rate first - pays the least interest"),
        SNOWBALL("Snowball", "Smallest balance first - quickest wins");

        public final String label;
        public final String description;

        Strategy(String label, String description) {
            this.label = label;
            this.description = description;
        }
    }

    public record DebtInput(int id, String name, BigDecimal balance, BigDecimal apr, BigDecimal minPayment) {
    }

    /**
     * @param balanceByMonth  total balance owed at the start (index 0) and after each month
     * @param payoffMonthById month number (1-based) each debt reaches $0, in payoff order
     */
    public record Result(Strategy strategy, boolean feasible, String problem, int months,
                         BigDecimal totalInterest, BigDecimal totalPaid,
                         List<BigDecimal> balanceByMonth, Map<Integer, Integer> payoffMonthById) {
    }

    public static final int MAX_MONTHS = 600;   // 50 years - beyond this we call it "never"

    private PayoffPlanner() {
    }

    public static BigDecimal totalMinimums(List<DebtInput> debts) {
        return debts.stream()
                .filter(d -> d.balance().signum() > 0)
                .map(d -> d.minPayment().min(d.balance()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static Result plan(List<DebtInput> debts, BigDecimal monthlyBudget, Strategy strategy) {
        List<Account> accounts = new ArrayList<>();
        for (DebtInput d : debts) {
            if (d.balance().signum() > 0) {
                accounts.add(new Account(d));
            }
        }
        List<BigDecimal> history = new ArrayList<>();
        Map<Integer, Integer> payoffMonth = new LinkedHashMap<>();
        history.add(sum(accounts));

        if (accounts.isEmpty()) {
            return new Result(strategy, true, null, 0, BigDecimal.ZERO, BigDecimal.ZERO, history, payoffMonth);
        }
        BigDecimal minimums = totalMinimums(debts);
        if (monthlyBudget.compareTo(minimums) < 0) {
            return new Result(strategy, false,
                    String.format("Your monthly budget is below the total of your minimum payments ($%,.2f).", minimums),
                    0, BigDecimal.ZERO, BigDecimal.ZERO, history, payoffMonth);
        }

        BigDecimal totalInterest = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;
        int month = 0;
        while (accounts.stream().anyMatch(Account::open)) {
            month++;
            if (month > MAX_MONTHS) {
                return new Result(strategy, false,
                        "At this budget the interest outpaces your payments, so the debt never gets paid off.",
                        MAX_MONTHS, totalInterest, totalPaid, history, payoffMonth);
            }

            // 1. interest
            for (Account a : accounts) {
                if (a.open()) {
                    BigDecimal i = a.balance.multiply(a.input.apr())
                            .divide(BigDecimal.valueOf(1200), 2, RoundingMode.HALF_UP);
                    a.balance = a.balance.add(i);
                    totalInterest = totalInterest.add(i);
                }
            }

            // 2. minimums
            BigDecimal available = monthlyBudget;
            for (Account a : accounts) {
                if (a.open()) {
                    BigDecimal pay = a.input.minPayment().min(a.balance).min(available);
                    a.balance = a.balance.subtract(pay);
                    available = available.subtract(pay);
                    totalPaid = totalPaid.add(pay);
                }
            }

            // 3. extra, in strategy order
            List<Account> targets = accounts.stream().filter(Account::open)
                    .sorted(order(strategy)).toList();
            for (Account a : targets) {
                if (available.signum() <= 0) {
                    break;
                }
                BigDecimal pay = available.min(a.balance);
                a.balance = a.balance.subtract(pay);
                available = available.subtract(pay);
                totalPaid = totalPaid.add(pay);
            }

            // 4. record
            for (Account a : accounts) {
                if (!a.open() && !payoffMonth.containsKey(a.input.id())) {
                    payoffMonth.put(a.input.id(), month);
                }
            }
            history.add(sum(accounts));
        }
        return new Result(strategy, true, null, month, totalInterest, totalPaid, history, payoffMonth);
    }

    private static Comparator<Account> order(Strategy strategy) {
        Comparator<Account> byApr = Comparator.comparing((Account a) -> a.input.apr()).reversed();
        Comparator<Account> byBalance = Comparator.comparing((Account a) -> a.balance);
        return strategy == Strategy.AVALANCHE
                ? byApr.thenComparing(byBalance)
                : byBalance.thenComparing(byApr);
    }

    private static BigDecimal sum(List<Account> accounts) {
        return accounts.stream().map(a -> a.balance).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static final class Account {
        final DebtInput input;
        BigDecimal balance;

        Account(DebtInput input) {
            this.input = input;
            this.balance = input.balance().setScale(2, RoundingMode.HALF_UP);
        }

        boolean open() {
            return balance.signum() > 0;
        }
    }
}
