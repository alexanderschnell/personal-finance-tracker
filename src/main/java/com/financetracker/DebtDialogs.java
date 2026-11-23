package com.financetracker;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** Small modal dialogs used by the debt screens. */
final class DebtDialogs {

    private DebtDialogs() {
    }

    record DebtForm(String name, Debt.Type type, BigDecimal balance, BigDecimal apr,
                    BigDecimal minPayment, int dueDay) {
    }

    record PaymentForm(LocalDate date, BigDecimal amount, String note) {
    }

    /** Add a new debt ({@code existing == null}) or edit one. For edits, "balance" is the starting balance. */
    static Optional<DebtForm> debtDialog(Debt existing) {
        boolean editing = existing != null;
        Dialog<DebtForm> dialog = baseDialog(editing ? "Edit " + existing.getName() : "Add a debt",
                editing ? "Update the details of this debt." : "Add a credit card or student loan you're paying off.");

        TextField name = new TextField(editing ? existing.getName() : "");
        name.setPromptText("e.g. Visa, OSAP");
        ComboBox<Debt.Type> type = new ComboBox<>();
        type.getItems().setAll(Debt.Type.values());
        type.setValue(editing ? existing.getType() : Debt.Type.CREDIT_CARD);
        TextField balance = moneyField(editing ? existing.getOriginalBalance() : null, "0.00");
        TextField apr = moneyField(editing ? existing.getApr() : null, "e.g. 19.99");
        TextField min = moneyField(editing ? existing.getMinPayment() : null, "0.00");
        Spinner<Integer> dueDay = new Spinner<>(1, 31, editing ? existing.getDueDay() : 1);
        dueDay.setEditable(true);

        GridPane grid = grid();
        int r = 0;
        grid.addRow(r++, new Label("Name"), name);
        grid.addRow(r++, new Label("Type"), type);
        grid.addRow(r++, new Label(editing ? "Starting balance ($)" : "Current balance ($)"), balance);
        grid.addRow(r++, new Label("Interest rate (APR %)"), apr);
        grid.addRow(r++, new Label("Minimum payment ($/month)"), min);
        grid.addRow(r++, new Label("Payment due day"), dueDay);
        Label hint = hint(editing
                ? "Starting balance is only used for the progress bar. To change what you owe now, use \"Update balance\"."
                : "Interest-free (e.g. the federal part of a student loan)? Enter 0 for the rate.");
        grid.add(hint, 0, r, 2, 1);

        dialog.getDialogPane().setContent(grid);
        BooleanBinding invalid = Bindings.createBooleanBinding(() ->
                        name.getText().isBlank() || parse(balance.getText()) == null
                                || parse(apr.getText()) == null || parse(min.getText()) == null,
                name.textProperty(), balance.textProperty(), apr.textProperty(), min.textProperty());
        okButton(dialog).disableProperty().bind(invalid);
        dialog.setResultConverter(bt -> bt.getButtonData() == ButtonBar.ButtonData.OK_DONE
                ? new DebtForm(name.getText().trim(), type.getValue(), parse(balance.getText()),
                parse(apr.getText()), parse(min.getText()), dueDay.getValue())
                : null);
        name.requestFocus();
        return dialog.showAndWait();
    }

    static Optional<PaymentForm> paymentDialog(Debt debt) {
        Dialog<PaymentForm> dialog = baseDialog("Log a payment",
                String.format("Payment toward %s (owing $%,.2f)", debt.getName(), debt.getBalance()));
        TextField amount = moneyField(debt.getMinPayment().min(debt.getBalance()), "0.00");
        DatePicker date = new DatePicker(LocalDate.now());
        TextField note = new TextField();
        note.setPromptText("optional");

        GridPane grid = grid();
        grid.addRow(0, new Label("Amount ($)"), amount);
        grid.addRow(1, new Label("Date"), date);
        grid.addRow(2, new Label("Note"), note);
        grid.add(hint("Interest since your last payment is estimated and split out automatically."), 0, 3, 2, 1);
        dialog.getDialogPane().setContent(grid);

        okButton(dialog).disableProperty().bind(Bindings.createBooleanBinding(() -> {
            BigDecimal v = parse(amount.getText());
            return v == null || v.signum() <= 0 || date.getValue() == null;
        }, amount.textProperty(), date.valueProperty()));
        dialog.setResultConverter(bt -> bt.getButtonData() == ButtonBar.ButtonData.OK_DONE
                ? new PaymentForm(date.getValue(), parse(amount.getText()), note.getText())
                : null);
        amount.requestFocus();
        amount.selectAll();
        return dialog.showAndWait();
    }

    static Optional<BigDecimal> balanceDialog(Debt debt) {
        Dialog<BigDecimal> dialog = baseDialog("Update balance",
                "Set " + debt.getName() + " to match your latest statement.");
        TextField balance = moneyField(debt.getBalance(), "0.00");
        GridPane grid = grid();
        grid.addRow(0, new Label("Balance owing ($)"), balance);
        grid.add(hint("Use this when new purchases are added, or when the real interest charged differs from the estimate."), 0, 1, 2, 1);
        dialog.getDialogPane().setContent(grid);
        okButton(dialog).disableProperty().bind(Bindings.createBooleanBinding(
                () -> parse(balance.getText()) == null, balance.textProperty()));
        dialog.setResultConverter(bt -> bt.getButtonData() == ButtonBar.ButtonData.OK_DONE ? parse(balance.getText()) : null);
        return dialog.showAndWait();
    }

    static void historyDialog(Debt debt, List<Payment> payments) {
        Dialog<Void> dialog = Theme.apply(new Dialog<Void>());
        dialog.setTitle("Payment history");
        dialog.setHeaderText(debt.getName() + " - " + payments.size() + " payment" + (payments.size() == 1 ? "" : "s"));
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        if (payments.isEmpty()) {
            dialog.getDialogPane().setContent(new Label("No payments logged yet."));
        } else {
            TableView<Payment> table = new TableView<>();
            table.getColumns().add(col("Date", p -> p.date().toString()));
            table.getColumns().add(col("Paid", p -> fmt(p.amount())));
            table.getColumns().add(col("Interest", p -> fmt(p.interest())));
            table.getColumns().add(col("Principal", p -> fmt(p.principal())));
            table.getColumns().add(col("Balance after", p -> fmt(p.balanceAfter())));
            table.getColumns().add(col("Note", p -> p.note() == null ? "" : p.note()));
            table.getItems().setAll(payments);
            table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
            table.setPrefSize(640, 320);

            BigDecimal paid = payments.stream().map(Payment::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal interest = payments.stream().map(Payment::interest).reduce(BigDecimal.ZERO, BigDecimal::add);
            Label totals = new Label(String.format("Total paid $%,.2f  -  of which about $%,.2f was interest", paid, interest));
            VBox box = new VBox(10, table, totals);
            dialog.getDialogPane().setContent(box);
        }
        dialog.showAndWait();
    }

    static boolean confirm(String title, String message) {
        Alert alert = Theme.apply(new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL));
        alert.setTitle(title);
        alert.setHeaderText(null);
        return alert.showAndWait().filter(b -> b == ButtonType.OK).isPresent();
    }

    static void celebrate(Debt debt) {
        Alert alert = Theme.apply(new Alert(Alert.AlertType.INFORMATION));
        alert.setTitle("Debt defeated!");
        alert.setHeaderText("🎉  " + debt.getName() + " is paid off!");
        alert.setContentText(String.format("That's $%,.2f gone for good. Its minimum payment can now go toward your next debt.",
                debt.getOriginalBalance()));
        alert.showAndWait();
    }

    static void error(String message) {
        Alert alert = Theme.apply(new Alert(Alert.AlertType.ERROR, message, ButtonType.OK));
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    // ---------------------------------------------------------------- helpers

    /** Parses "1,234.50", "$1234.5" or "19.99%". Returns null if invalid or negative. */
    static BigDecimal parse(String text) {
        if (text == null) {
            return null;
        }
        String cleaned = text.replace("$", "").replace(",", "").replace("%", "").trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        try {
            BigDecimal v = new BigDecimal(cleaned);
            return v.signum() < 0 ? null : v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String fmt(BigDecimal v) {
        return String.format("$%,.2f", v);
    }

    private static <T> Dialog<T> baseDialog(String title, String header) {
        Dialog<T> dialog = Theme.apply(new Dialog<>());
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.getDialogPane().getButtonTypes().addAll(
                new ButtonType("Save", ButtonBar.ButtonData.OK_DONE), ButtonType.CANCEL);
        return dialog;
    }

    private static Node okButton(Dialog<?> dialog) {
        return dialog.getDialogPane().getButtonTypes().stream()
                .filter(b -> b.getButtonData() == ButtonBar.ButtonData.OK_DONE)
                .map(b -> dialog.getDialogPane().lookupButton(b))
                .findFirst().orElseThrow();
    }

    private static GridPane grid() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(16, 20, 8, 20));
        return grid;
    }

    private static TextField moneyField(BigDecimal value, String prompt) {
        TextField f = new TextField(value == null ? "" : value.toPlainString());
        f.setPromptText(prompt);
        return f;
    }

    private static Label hint(String text) {
        Label l = new Label(text);
        l.setWrapText(true);
        l.setMaxWidth(380);
        l.setStyle("-fx-text-fill: #8b939e; -fx-font-size: 11px;");
        return l;
    }

    private static TableColumn<Payment, String> col(String title, Function<Payment, String> value) {
        TableColumn<Payment, String> c = new TableColumn<>(title);
        c.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(value.apply(cd.getValue())));
        c.setSortable(false);
        return c;
    }
}
