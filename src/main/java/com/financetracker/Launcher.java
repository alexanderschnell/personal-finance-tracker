package com.financetracker;

/**
 * Entry point for the runnable (shaded) jar.
 * JavaFX refuses to start from a fat jar when the main class extends Application,
 * so this plain class delegates to {@link Main}.
 */
public final class Launcher {
    private Launcher() {
    }

    public static void main(String[] args) {
        Main.main(args);
    }
}
