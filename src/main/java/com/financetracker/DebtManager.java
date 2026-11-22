package com.financetracker;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Stores debts, payments and app settings in the same SQLite file as transactions.
 * Money is stored as TEXT so BigDecimal values round-trip exactly.
 */
public class DebtManager implements AutoCloseable {

    private static final BigDecimal DAYS_PER_YEAR = BigDecimal.valueOf(365);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final Connection connection;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public DebtManager() {
        this(TransactionManager.databasePath());
    }

    public DebtManager(String databasePath) {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
            createTables();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to open debt database: " + e.getMessage(), e);
        }
    }

    private void createTables() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS debts (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    type TEXT NOT NULL,
                    original_balance TEXT NOT NULL,
                    balance TEXT NOT NULL,
                    apr TEXT NOT NULL,
                    min_payment TEXT NOT NULL,
                    due_day INTEGER NOT NULL,
                    created_date TEXT NOT NULL,
                    paid_off_date TEXT
                )""");
            st.execute("""
                CREATE TABLE IF NOT EXISTS debt_payments (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    debt_id INTEGER NOT NULL,
                    date TEXT NOT NULL,
                    amount TEXT NOT NULL,
                    interest TEXT NOT NULL,
                    principal TEXT NOT NULL,
                    balance_after TEXT NOT NULL,
                    note TEXT
                )""");
            st.execute("CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
        }
    }

    // ---------------------------------------------------------------- listeners

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    private void changed() {
        listeners.forEach(Runnable::run);
    }

    // ---------------------------------------------------------------- debts

    public List<Debt> getDebts() {
        List<Debt> debts = new ArrayList<>();
        String sql = "SELECT * FROM debts ORDER BY CAST(balance AS REAL) = 0, name COLLATE NOCASE";
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String paidOff = rs.getString("paid_off_date");
                debts.add(new Debt(
                        rs.getInt("id"),
                        rs.getString("name"),
                        Debt.Type.valueOf(rs.getString("type")),
                        new BigDecimal(rs.getString("original_balance")),
                        new BigDecimal(rs.getString("balance")),
                        new BigDecimal(rs.getString("apr")),
                        new BigDecimal(rs.getString("min_payment")),
                        rs.getInt("due_day"),
                        LocalDate.parse(rs.getString("created_date")),
                        paidOff == null ? null : LocalDate.parse(paidOff)));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load debts: " + e.getMessage(), e);
        }
        return debts;
    }

    public Debt getDebt(int id) {
        return getDebts().stream().filter(d -> d.getId() == id).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No debt with id " + id));
    }

    public int addDebt(String name, Debt.Type type, BigDecimal balance, BigDecimal apr,
                       BigDecimal minPayment, int dueDay, LocalDate createdDate) {
        String sql = """
            INSERT INTO debts (name, type, original_balance, balance, apr, min_payment, due_day, created_date)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)""";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, name.trim());
            ps.setString(2, type.name());
            ps.setString(3, money(balance).toPlainString());
            ps.setString(4, money(balance).toPlainString());
            ps.setString(5, apr.toPlainString());
            ps.setString(6, money(minPayment).toPlainString());
            ps.setInt(7, dueDay);
            ps.setString(8, createdDate.toString());
            ps.executeUpdate();
            int id = lastInsertId();
            changed();
            return id;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to add debt: " + e.getMessage(), e);
        }
    }

    /** Saves name, type, rate, minimum, due day and starting balance (not the current balance). */
    public void updateDebt(Debt debt) {
        String sql = "UPDATE debts SET name = ?, type = ?, original_balance = ?, apr = ?, min_payment = ?, due_day = ? WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, debt.getName().trim());
            ps.setString(2, debt.getType().name());
            ps.setString(3, money(debt.getOriginalBalance()).toPlainString());
            ps.setString(4, debt.getApr().toPlainString());
            ps.setString(5, money(debt.getMinPayment()).toPlainString());
            ps.setInt(6, debt.getDueDay());
            ps.setInt(7, debt.getId());
            ps.executeUpdate();
            changed();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update debt: " + e.getMessage(), e);
        }
    }

    public void deleteDebt(int id) {
        try (PreparedStatement p1 = connection.prepareStatement("DELETE FROM debt_payments WHERE debt_id = ?");
             PreparedStatement p2 = connection.prepareStatement("DELETE FROM debts WHERE id = ?")) {
            p1.setInt(1, id);
            p1.executeUpdate();
            p2.setInt(1, id);
            p2.executeUpdate();
            changed();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete debt: " + e.getMessage(), e);
        }
    }

    /**
     * Sets the balance to match a statement (new purchases, real interest charged, etc.).
     * Raising it above the starting balance also raises the starting balance.
     */
    public void setBalance(int debtId, BigDecimal newBalance, LocalDate date) {
        Debt debt = getDebt(debtId);
        BigDecimal bal = money(newBalance.max(BigDecimal.ZERO));
        BigDecimal original = debt.getOriginalBalance().max(bal);
        String paidOff = bal.signum() == 0 ? date.toString() : null;
        String sql = "UPDATE debts SET balance = ?, original_balance = ?, paid_off_date = ? WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, bal.toPlainString());
            ps.setString(2, original.toPlainString());
            ps.setString(3, paidOff);
            ps.setInt(4, debtId);
            ps.executeUpdate();
            changed();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update balance: " + e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------- payments

    /**
     * Records a payment. Interest is estimated as simple daily interest on the balance since the
     * previous payment (or since the debt was added) - the same way Canadian student loans accrue,
     * and close enough for credit cards. Use {@link #setBalance} to match the real statement.
     */
    public Payment logPayment(int debtId, LocalDate date, BigDecimal amount, String note) {
        Debt debt = getDebt(debtId);
        LocalDate since = lastPaymentDate(debtId);
        if (since == null || since.isBefore(debt.getCreatedDate())) {
            since = debt.getCreatedDate();
        }
        long days = Math.max(0, ChronoUnit.DAYS.between(since, date));

        BigDecimal balance = debt.getBalance();
        BigDecimal interest = balance.multiply(debt.getApr())
                .multiply(BigDecimal.valueOf(days))
                .divide(HUNDRED.multiply(DAYS_PER_YEAR), 2, RoundingMode.HALF_UP);
        BigDecimal owed = balance.add(interest);
        BigDecimal paid = money(amount);
        BigDecimal balanceAfter = owed.subtract(paid).max(BigDecimal.ZERO);
        BigDecimal principal = balance.subtract(balanceAfter);   // may be negative if payment < interest

        try {
            connection.setAutoCommit(false);
            int id;
            try (PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO debt_payments (debt_id, date, amount, interest, principal, balance_after, note)
                    VALUES (?, ?, ?, ?, ?, ?, ?)""")) {
                ps.setInt(1, debtId);
                ps.setString(2, date.toString());
                ps.setString(3, paid.toPlainString());
                ps.setString(4, interest.toPlainString());
                ps.setString(5, principal.toPlainString());
                ps.setString(6, balanceAfter.toPlainString());
                ps.setString(7, note == null || note.isBlank() ? null : note.trim());
                ps.executeUpdate();
                id = lastInsertId();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE debts SET balance = ?, paid_off_date = ? WHERE id = ?")) {
                ps.setString(1, balanceAfter.toPlainString());
                ps.setString(2, balanceAfter.signum() == 0 ? date.toString() : null);
                ps.setInt(3, debtId);
                ps.executeUpdate();
            }
            connection.commit();
            changed();
            return new Payment(id, debtId, date, paid, interest, principal, balanceAfter, note);
        } catch (SQLException e) {
            try { connection.rollback(); } catch (SQLException ignored) { }
            throw new RuntimeException("Failed to log payment: " + e.getMessage(), e);
        } finally {
            try { connection.setAutoCommit(true); } catch (SQLException ignored) { }
        }
    }

    public List<Payment> getPayments(int debtId) {
        List<Payment> payments = new ArrayList<>();
        String sql = "SELECT * FROM debt_payments WHERE debt_id = ? ORDER BY date DESC, id DESC";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, debtId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    payments.add(new Payment(rs.getInt("id"), rs.getInt("debt_id"),
                            LocalDate.parse(rs.getString("date")),
                            new BigDecimal(rs.getString("amount")),
                            new BigDecimal(rs.getString("interest")),
                            new BigDecimal(rs.getString("principal")),
                            new BigDecimal(rs.getString("balance_after")),
                            rs.getString("note")));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load payments: " + e.getMessage(), e);
        }
        return payments;
    }

    private LocalDate lastPaymentDate(int debtId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT MAX(date) FROM debt_payments WHERE debt_id = ?")) {
            ps.setInt(1, debtId);
            try (ResultSet rs = ps.executeQuery()) {
                String d = rs.next() ? rs.getString(1) : null;
                return d == null ? null : LocalDate.parse(d);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // ---------------------------------------------------------------- settings

    public String getSetting(String key, String fallback) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT value FROM settings WHERE key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : fallback;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void setSetting(String key, String value) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO settings (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // ---------------------------------------------------------------- helpers

    /** This sqlite-jdbc version doesn't support RETURN_GENERATED_KEYS, so ask SQLite directly. */
    private int lastInsertId() throws SQLException {
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException ignored) {
        }
    }
}
