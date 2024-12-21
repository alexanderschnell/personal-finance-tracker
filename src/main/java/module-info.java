module com.financetracker {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires javafx.graphics;

    exports com.financetracker;
    opens com.financetracker to javafx.fxml;
}