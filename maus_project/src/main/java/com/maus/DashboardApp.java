package com.maus;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * JavaFX entry point.
 *
 * Flow:
 *   1. Show a "loading" splash while models train (background thread).
 *   2. On completion, replace with the full Dashboard.
 */
public class DashboardApp extends Application {

    @Override
    public void start(Stage primaryStage) {

        // ── Splash screen ─────────────────────────────────────────────
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setMaxSize(60, 60);

        Label msg = new Label("Training models, please wait…");
        msg.setStyle("-fx-font-size: 14px; -fx-text-fill: #555;");

        VBox splash = new VBox(20, spinner, msg);
        splash.setAlignment(javafx.geometry.Pos.CENTER);
        splash.setStyle("-fx-background-color: #f8f8f8;");

        Scene splashScene = new Scene(splash, 860, 640);
        primaryStage.setScene(splashScene);
        primaryStage.setTitle("MAUS HRV Dashboard — Loading…");
        primaryStage.show();

        // ── Background training thread ────────────────────────────────
        Thread worker = new Thread(() -> {
            try {
                ModelEvaluationWithStore.run("D:\\maus_project\\final_dataset.csv");

                Platform.runLater(() -> {
                    try {
                        DashboardController controller =
                                new DashboardController(primaryStage);
                        controller.show();
                    } catch (Exception e) {
                        showError(primaryStage, e);
                    }
                });

            } catch (Exception e) {
                Platform.runLater(() -> showError(primaryStage, e));
            }
        });

        worker.setDaemon(true);
        worker.start();
    }

    private void showError(Stage stage, Exception e) {
        e.printStackTrace();
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText("Model evaluation failed");
        alert.setContentText(e.getMessage());
        alert.showAndWait();
        stage.close();
    }

    public static void main(String[] args) {
        launch(args);
    }
}