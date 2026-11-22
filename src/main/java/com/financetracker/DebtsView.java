package com.financetracker;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Home screen: every debt, progress toward paying it off, and the actions to work on it. */
public class DebtsView extends ScrollPane {

    private static final DateTimeFormatter MONTH_YEAR = DateTimeFormatter.ofPattern("MMMM yyyy");
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("MMM d, yyyy");

    private final DebtManager debts;
    private final VBox content = new VBox(22);

    public DebtsView(DebtManager debts) {
        this.debts = debts;
        content.setPadding(new Insets(24, 40, 40, 40));
        content.getStyleClass().add("main-content");
        setContent(content);
        setFitToWidth(true);
        getStyleClass().add("debt-scroll");
        debts.addListener(this::refresh);
        refresh();
    }

    public void refresh() {
        List<Debt> all = debts.getDebts();
        List<Debt> active = all.stream().filter(d -> !d.isPaidOff()).toList();
        List<Debt> defeated = all.stream().filter(Debt::isPaidOff).toList();

        content.getChildren().clear();

        Button add = new Button("+ Add debt");
        add.getStyleClass().add("action-button");
        add.setOnAction(e -> DebtDialogs.debtDialog(null).ifPresent(f ->
                debts.addDebt(f.name(), f.type(), f.balance(), f.apr(), f.minPayment(), f.dueDay(), LocalDate.now())));
        Label title = new Label("My debts");
        title.getStyleClass().add("page-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        content.getChildren().add(new HBox(title, spacer, add));

        if (all.isEmpty()) {
            content.getChildren().add(emptyState());
            return;
        }

        content.getChildren().add(summary(all, active));

        if (!active.isEmpty()) {
            VBox list = new VBox(14);
            active.forEach(d -> list.getChildren().add(debtCard(d)));
            content.getChildren().add(list);
        }

        if (!defeated.isEmpty()) {
            Label h = new Label("Defeated 🎉");
            h.getStyleClass().add("section-header");
            FlowPane wins = new FlowPane(12, 12);
            defeated.forEach(d -> wins.getChildren().add(defeatedCard(d)));
            content.getChildren().addAll(h, wins);
        }
    }

    // ---------------------------------------------------------------- summary

    private HBox summary(List<Debt> all, List<Debt> active) {
        BigDecimal owing = active.stream().map(Debt::getBalance).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paidOff = all.stream().map(Debt::amountPaidOff).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal start = all.stream().map(Debt::getOriginalBalance).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal minimums = active.stream().map(d -> d.getMinPayment().min(d.getBalance()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int pct = start.signum() == 0 ? 0 : paidOff.multiply(BigDecimal.valueOf(100))
                .divide(start, 0, java.math.RoundingMode.DOWN).intValue();

        String freeLabel;
        String freeSub;
        if (active.isEmpty()) {
            freeLabel = "Debt-free!";
            freeSub = "Nothing left to pay";
        } else {
            BigDecimal budget = PayoffPlanView.savedBudget(debts, active);
            PayoffPlanner.Strategy strategy = PayoffPlanView.savedStrategy(debts);
            PayoffPlanner.Result r = PayoffPlanner.plan(PayoffPlanView.inputs(active), budget, strategy);
            if (r.feasible()) {
                freeLabel = LocalDate.now().plusMonths(r.months()).format(MONTH_YEAR);
                freeSub = String.format("%s at %s/month", strategy.label, DebtDialogs.fmt(budget));
            } else {
                freeLabel = "Not on track";
                freeSub = "Raise your budget in Payoff Plan";
            }
        }

        HBox row = new HBox(16,
                stat("Total owing", DebtDialogs.fmt(owing), active.size() + " active debt" + (active.size() == 1 ? "" : "s"), "stat-danger"),
                stat("Paid off so far", DebtDialogs.fmt(paidOff), pct + "% of " + DebtDialogs.fmt(start), "stat-good"),
                stat("Monthly minimums", DebtDialogs.fmt(minimums), "due every month", null),
                stat("Debt-free by", freeLabel, freeSub, "stat-accent"));
        row.getChildren().forEach(n -> HBox.setHgrow(n, Priority.ALWAYS));
        return row;
    }

    private VBox stat(String label, String value, String sub, String accent) {
        Label l = new Label(label);
        l.getStyleClass().add("card-title");
        Label v = new Label(value);
        v.getStyleClass().add("card-value");
        if (accent != null) {
            v.getStyleClass().add(accent);
        }
        Label s = new Label(sub);
        s.getStyleClass().add("card-sub");
        VBox card = new VBox(6, l, v, s);
        card.setPadding(new Insets(18));
        card.getStyleClass().add("summary-card");
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    // ---------------------------------------------------------------- debt cards

    private VBox debtCard(Debt d) {
        Label name = new Label(d.getName());
        name.getStyleClass().add("debt-name");
        Label type = new Label(d.getType().toString());
        type.getStyleClass().addAll("badge", d.getType() == Debt.Type.CREDIT_CARD ? "badge-card" : "badge-loan");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label balance = new Label(DebtDialogs.fmt(d.getBalance()));
        balance.getStyleClass().add("debt-balance");
        HBox top = new HBox(10, name, type, spacer, balance);
        top.setAlignment(Pos.CENTER_LEFT);

        LocalDate today = LocalDate.now();
        LocalDate due = d.nextDueDate(today);
        long days = ChronoUnit.DAYS.between(today, due);
        String dueText = days == 0 ? "due today" : days == 1 ? "due tomorrow" : "due in " + days + " days";
        Label details = new Label(String.format("%s%% APR   ·   Min %s/month   ·   Next payment %s (%s)",
                d.getApr().stripTrailingZeros().toPlainString(), DebtDialogs.fmt(d.getMinPayment()),
                due.format(DateTimeFormatter.ofPattern("MMM d")), dueText));
        details.getStyleClass().add("debt-details");
        if (days <= 5) {
            details.getStyleClass().add("due-soon");
        }

        ProgressBar bar = new ProgressBar(d.progress());
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.getStyleClass().add("debt-progress");
        Label progress = new Label(String.format("%d%% paid off   ·   %s of %s",
                Math.round(d.progress() * 100), DebtDialogs.fmt(d.amountPaidOff()), DebtDialogs.fmt(d.getOriginalBalance())));
        progress.getStyleClass().add("card-sub");

        Button pay = small("Log payment", true);
        pay.setOnAction(e -> DebtDialogs.paymentDialog(d).ifPresent(f -> {
            Payment p = debts.logPayment(d.getId(), f.date(), f.amount(), f.note());
            if (p.balanceAfter().signum() == 0) {
                DebtDialogs.celebrate(d);
            }
        }));
        Button update = small("Update balance", false);
        update.setOnAction(e -> DebtDialogs.balanceDialog(d).ifPresent(b -> {
            debts.setBalance(d.getId(), b, LocalDate.now());
            if (b.signum() == 0) {
                DebtDialogs.celebrate(d);
            }
        }));
        Button history = small("History", false);
        history.setOnAction(e -> DebtDialogs.historyDialog(d, debts.getPayments(d.getId())));
        Button edit = small("Edit", false);
        edit.setOnAction(e -> DebtDialogs.debtDialog(d).ifPresent(f -> {
            d.setName(f.name());
            d.setType(f.type());
            d.setOriginalBalance(f.balance());
            d.setApr(f.apr());
            d.setMinPayment(f.minPayment());
            d.setDueDay(f.dueDay());
            debts.updateDebt(d);
        }));
        Button delete = small("Delete", false);
        delete.getStyleClass().add("danger-link");
        delete.setOnAction(e -> {
            if (DebtDialogs.confirm("Delete debt", "Delete " + d.getName() + " and its payment history? This can't be undone.")) {
                debts.deleteDebt(d.getId());
            }
        });
        HBox actions = new HBox(8, pay, update, history, edit, delete);

        VBox card = new VBox(10, top, details, bar, progress, actions);
        card.setPadding(new Insets(18, 20, 18, 20));
        card.getStyleClass().add("debt-card");
        return card;
    }

    private VBox defeatedCard(Debt d) {
        Label name = new Label("✔  " + d.getName());
        name.getStyleClass().add("debt-name");
        String when = d.getPaidOffDate() == null ? "" : "Paid off " + d.getPaidOffDate().format(SHORT_DATE);
        Label sub = new Label(DebtDialogs.fmt(d.getOriginalBalance()) + " cleared   ·   " + when);
        sub.getStyleClass().add("card-sub");
        Button history = small("History", false);
        history.setOnAction(e -> DebtDialogs.historyDialog(d, debts.getPayments(d.getId())));
        Button delete = small("Remove", false);
        delete.getStyleClass().add("danger-link");
        delete.setOnAction(e -> {
            if (DebtDialogs.confirm("Remove debt", "Remove " + d.getName() + " from your list?")) {
                debts.deleteDebt(d.getId());
            }
        });
        VBox card = new VBox(8, name, sub, new HBox(8, history, delete));
        card.setPadding(new Insets(14, 18, 14, 18));
        card.getStyleClass().addAll("debt-card", "defeated-card");
        card.setPrefWidth(300);
        return card;
    }

    private VBox emptyState() {
        Label h = new Label("No debts yet");
        h.getStyleClass().add("section-header");
        Label t = new Label("Add your credit cards and student loans to see your total, track payments\n"
                + "and get a plan for when you'll be debt-free.");
        t.getStyleClass().add("card-sub");
        VBox box = new VBox(8, h, t);
        box.setPadding(new Insets(30));
        box.getStyleClass().add("debt-card");
        return box;
    }

    private static Button small(String text, boolean primary) {
        Button b = new Button(text);
        b.getStyleClass().add(primary ? "small-primary" : "small-button");
        return b;
    }
}
