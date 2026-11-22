package com.financetracker;

import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/** Compares avalanche vs snowball for a monthly budget, with a what-if slider and a payoff chart. */
public class PayoffPlanView extends ScrollPane {

    static final String BUDGET_KEY = "payoff.budget";
    static final String STRATEGY_KEY = "payoff.strategy";
    private static final DateTimeFormatter MONTH_YEAR = DateTimeFormatter.ofPattern("MMM yyyy");

    private final DebtManager debts;
    private final VBox content = new VBox(20);
    private final TextField budgetField = new TextField();
    private final Slider slider = new Slider();
    private final VBox results = new VBox(20);
    private final PauseTransition saveLater = new PauseTransition(Duration.millis(400));
    private boolean syncing;

    public PayoffPlanView(DebtManager debts) {
        this.debts = debts;
        content.setPadding(new Insets(24, 40, 40, 40));
        content.getStyleClass().add("main-content");
        setContent(content);
        setFitToWidth(true);
        getStyleClass().add("debt-scroll");

        budgetField.setPrefWidth(120);
        budgetField.setOnAction(e -> applyBudgetText());
        budgetField.focusedProperty().addListener((o, was, is) -> {
            if (!is) {
                applyBudgetText();
            }
        });
        slider.setBlockIncrement(25);
        slider.setMajorTickUnit(25);
        slider.setSnapToTicks(true);
        slider.valueProperty().addListener((o, a, b) -> {
            if (!syncing) {
                setBudget(BigDecimal.valueOf(b.doubleValue()).setScale(0, RoundingMode.HALF_UP));
            }
        });
        saveLater.setOnFinished(e -> debts.setSetting(BUDGET_KEY, budgetField.getText().replace(",", "")));

        debts.addListener(this::refresh);
        refresh();
    }

    // ---------------------------------------------------------------- shared helpers (also used by DebtsView)

    static List<PayoffPlanner.DebtInput> inputs(List<Debt> debts) {
        return debts.stream().filter(d -> !d.isPaidOff())
                .map(d -> new PayoffPlanner.DebtInput(d.getId(), d.getName(), d.getBalance(), d.getApr(), d.getMinPayment()))
                .toList();
    }

    /** Saved monthly budget, never below the current minimums. */
    static BigDecimal savedBudget(DebtManager dm, List<Debt> active) {
        BigDecimal minimums = PayoffPlanner.totalMinimums(inputs(active));
        BigDecimal saved = DebtDialogs.parse(dm.getSetting(BUDGET_KEY, ""));
        return saved == null || saved.compareTo(minimums) < 0 ? minimums : saved;
    }

    static PayoffPlanner.Strategy savedStrategy(DebtManager dm) {
        try {
            return PayoffPlanner.Strategy.valueOf(dm.getSetting(STRATEGY_KEY, "AVALANCHE"));
        } catch (IllegalArgumentException e) {
            return PayoffPlanner.Strategy.AVALANCHE;
        }
    }

    // ---------------------------------------------------------------- layout

    public void refresh() {
        List<Debt> active = debts.getDebts().stream().filter(d -> !d.isPaidOff()).toList();
        content.getChildren().clear();

        Label title = new Label("Payoff plan");
        title.getStyleClass().add("page-title");
        content.getChildren().add(title);

        if (active.isEmpty()) {
            Label none = new Label("Add a debt on the Debts screen to build your payoff plan.");
            none.getStyleClass().add("card-sub");
            content.getChildren().add(none);
            return;
        }

        BigDecimal minimums = PayoffPlanner.totalMinimums(inputs(active));
        BigDecimal budget = savedBudget(debts, active);

        Label q = new Label("How much can you put toward debt each month?");
        q.getStyleClass().add("section-header");
        Label dollar = new Label("$");
        dollar.getStyleClass().add("budget-dollar");
        budgetField.getStyleClass().add("budget-field");
        Label minNote = new Label(String.format("Minimums add up to %s/month. Anything above that is extra that speeds things up.",
                DebtDialogs.fmt(minimums)));
        minNote.getStyleClass().add("card-sub");

        syncing = true;
        slider.setMin(minimums.doubleValue());
        slider.setMax(Math.max(minimums.doubleValue() + 1500, budget.doubleValue() + 500));
        slider.setValue(budget.doubleValue());
        budgetField.setText(budget.setScale(2, RoundingMode.HALF_UP).toPlainString());
        syncing = false;
        HBox.setHgrow(slider, Priority.ALWAYS);
        HBox budgetRow = new HBox(10, dollar, budgetField, slider);
        budgetRow.setAlignment(Pos.CENTER_LEFT);

        VBox budgetCard = new VBox(10, q, budgetRow, minNote);
        budgetCard.setPadding(new Insets(18, 20, 18, 20));
        budgetCard.getStyleClass().add("debt-card");

        content.getChildren().addAll(budgetCard, results);
        renderResults(active, budget);
    }

    private void applyBudgetText() {
        BigDecimal v = DebtDialogs.parse(budgetField.getText());
        if (v != null) {
            setBudget(v);
        }
    }

    private void setBudget(BigDecimal budget) {
        List<Debt> active = debts.getDebts().stream().filter(d -> !d.isPaidOff()).toList();
        if (active.isEmpty()) {
            return;
        }
        BigDecimal minimums = PayoffPlanner.totalMinimums(inputs(active));
        if (budget.compareTo(minimums) < 0) {
            budget = minimums;
        }
        syncing = true;
        budgetField.setText(budget.setScale(2, RoundingMode.HALF_UP).toPlainString());
        if (budget.doubleValue() > slider.getMax()) {
            slider.setMax(budget.doubleValue() + 500);
        }
        slider.setValue(budget.doubleValue());
        syncing = false;
        renderResults(active, budget);
        saveLater.playFromStart();
    }

    private void renderResults(List<Debt> active, BigDecimal budget) {
        List<PayoffPlanner.DebtInput> in = inputs(active);
        PayoffPlanner.Result avalanche = PayoffPlanner.plan(in, budget, PayoffPlanner.Strategy.AVALANCHE);
        PayoffPlanner.Result snowball = PayoffPlanner.plan(in, budget, PayoffPlanner.Strategy.SNOWBALL);
        PayoffPlanner.Strategy chosen = savedStrategy(debts);

        results.getChildren().clear();
        if (!avalanche.feasible()) {
            Label warn = new Label("⚠  " + avalanche.problem());
            warn.getStyleClass().add("warning-box");
            warn.setWrapText(true);
            warn.setMaxWidth(Double.MAX_VALUE);
            results.getChildren().add(warn);
            return;
        }

        HBox cards = new HBox(16, strategyCard(avalanche, snowball, active, chosen), strategyCard(snowball, avalanche, active, chosen));
        cards.getChildren().forEach(n -> HBox.setHgrow(n, Priority.ALWAYS));
        results.getChildren().addAll(cards, whatIf(in, budget, chosen), chart(avalanche, snowball));
    }

    private VBox strategyCard(PayoffPlanner.Result r, PayoffPlanner.Result other, List<Debt> active,
                              PayoffPlanner.Strategy chosen) {
        Label name = new Label(r.strategy().label);
        name.getStyleClass().add("debt-name");
        Label desc = new Label(r.strategy().description);
        desc.getStyleClass().add("card-sub");

        LocalDate free = LocalDate.now().plusMonths(r.months());
        Label date = new Label("Debt-free " + free.format(MONTH_YEAR));
        date.getStyleClass().addAll("card-value", "stat-accent");
        Label meta = new Label(String.format("%d months   ·   %s in interest", r.months(), DebtDialogs.fmt(r.totalInterest())));
        meta.getStyleClass().add("card-sub");

        VBox card = new VBox(8, new HBox(10, name), desc, date, meta);

        BigDecimal saves = other.totalInterest().subtract(r.totalInterest());
        if (saves.compareTo(BigDecimal.ONE) >= 0) {
            Label badge = new Label("Saves " + DebtDialogs.fmt(saves));
            badge.getStyleClass().addAll("badge", "badge-good");
            ((HBox) card.getChildren().get(0)).getChildren().add(badge);
        }

        Label orderTitle = new Label("Payoff order");
        orderTitle.getStyleClass().add("card-title");
        VBox order = new VBox(4, orderTitle);
        int n = 1;
        for (Map.Entry<Integer, Integer> e : r.payoffMonthById().entrySet()) {
            String debtName = active.stream().filter(d -> d.getId() == e.getKey()).map(Debt::getName).findFirst().orElse("?");
            Label l = new Label(String.format("%d.  %s  -  %s", n++, debtName,
                    LocalDate.now().plusMonths(e.getValue()).format(MONTH_YEAR)));
            order.getChildren().add(l);
        }
        card.getChildren().add(order);

        if (r.strategy() == chosen) {
            Label using = new Label("✔  Using this plan");
            using.getStyleClass().add("using-label");
            card.getChildren().add(using);
            card.getStyleClass().add("strategy-chosen");
        } else {
            Button use = new Button("Use this plan");
            use.getStyleClass().add("small-button");
            use.setOnAction(e -> {
                debts.setSetting(STRATEGY_KEY, r.strategy().name());
                refresh();
                debtsChanged();
            });
            card.getChildren().add(use);
        }
        card.setPadding(new Insets(18, 20, 18, 20));
        card.getStyleClass().add("debt-card");
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private Label whatIf(List<PayoffPlanner.DebtInput> in, BigDecimal budget, PayoffPlanner.Strategy strategy) {
        PayoffPlanner.Result now = PayoffPlanner.plan(in, budget, strategy);
        PayoffPlanner.Result more = PayoffPlanner.plan(in, budget.add(BigDecimal.valueOf(100)), strategy);
        int sooner = now.months() - more.months();
        BigDecimal saved = now.totalInterest().subtract(more.totalInterest());
        Label l = new Label(sooner > 0 || saved.signum() > 0
                ? String.format("💡  Adding $100/month would make you debt-free %d month%s sooner and save %s in interest.",
                sooner, sooner == 1 ? "" : "s", DebtDialogs.fmt(saved))
                : "💡  You're already paying everything off as fast as the plan allows.");
        l.getStyleClass().add("tip-box");
        l.setWrapText(true);
        l.setMaxWidth(Double.MAX_VALUE);
        return l;
    }

    private LineChart<Number, Number> chart(PayoffPlanner.Result a, PayoffPlanner.Result b) {
        NumberAxis x = new NumberAxis();
        x.setLabel("Months from now");
        NumberAxis y = new NumberAxis();
        y.setLabel("Total owing ($)");
        LineChart<Number, Number> chart = new LineChart<>(x, y);
        chart.setTitle("Total debt over time");
        chart.setCreateSymbols(false);
        chart.setAnimated(false);
        chart.setPrefHeight(340);
        chart.getData().add(series(a));
        chart.getData().add(series(b));
        chart.getStyleClass().add("payoff-chart");
        return chart;
    }

    private XYChart.Series<Number, Number> series(PayoffPlanner.Result r) {
        XYChart.Series<Number, Number> s = new XYChart.Series<>();
        s.setName(r.strategy().label);
        List<BigDecimal> h = r.balanceByMonth();
        for (int m = 0; m < h.size(); m++) {
            s.getData().add(new XYChart.Data<>(m, h.get(m)));
        }
        return s;
    }

    private Runnable onStrategyChanged = () -> { };

    /** Lets Main refresh the Debts screen when the chosen strategy changes. */
    void setOnStrategyChanged(Runnable r) {
        onStrategyChanged = r;
    }

    private void debtsChanged() {
        onStrategyChanged.run();
    }
}
