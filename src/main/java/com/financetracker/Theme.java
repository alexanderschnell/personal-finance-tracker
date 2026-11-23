package com.financetracker;

import javafx.scene.Scene;
import javafx.scene.control.Dialog;

/**
 * The app's "liquid metal" look. Every window and dialog goes through here so
 * they all load the same stylesheets (the palette lives in main_styles.css).
 */
final class Theme {

    private static final String[] SHEETS = {"/main_styles.css", "/debt_styles.css"};

    private Theme() {
    }

    /** Styles a window's scene. Call before adding any window-specific stylesheet. */
    static Scene apply(Scene scene) {
        for (String sheet : SHEETS) {
            scene.getStylesheets().add(Theme.class.getResource(sheet).toExternalForm());
        }
        return scene;
    }

    /** Styles a dialog or alert, which otherwise opens in the default light look. */
    static <D extends Dialog<?>> D apply(D dialog) {
        for (String sheet : SHEETS) {
            dialog.getDialogPane().getStylesheets().add(Theme.class.getResource(sheet).toExternalForm());
        }
        return dialog;
    }
}
