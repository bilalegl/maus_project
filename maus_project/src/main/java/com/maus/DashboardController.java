package com.maus;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.*;
import javafx.stage.Stage;

import java.util.List;

/**
 * Full dashboard with:
 *   Tab 1 — Accuracy bar chart + kappa/MAE/RMSE table
 *   Tab 2 — Per-class Precision / Recall / F1 bar chart
 *   Tab 3 — Confusion matrix (TableView) — model selector
 *   Tab 4 — Feature importance bar chart
 *   Tab 5 — Class distribution pie chart
 */
public class DashboardController {

    private final Stage  stage;
    private final ResultsStore store = ResultsStore.get();

    public DashboardController(Stage stage) {
        this.stage = stage;
    }

    public void show() {
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.setStyle("-fx-background-color: #f4f4f4;");

        tabs.getTabs().addAll(
                makeTab("Overview",          buildOverviewTab()),
                makeTab("Per-Class Metrics", buildPerClassTab()),
                makeTab("Confusion Matrix",  buildConfusionTab()),
                makeTab("Feature Importance",buildFeatureTab()),
                makeTab("Class Distribution",buildDistributionTab())
        );

        Scene scene = new Scene(tabs, 940, 660);
        stage.setTitle("MAUS HRV — Results Dashboard");
        stage.setScene(scene);
        stage.centerOnScreen();
    }

    // ── Tab helpers ───────────────────────────────────────────────────────

    private Tab makeTab(String title, javafx.scene.Node content) {
        Tab tab = new Tab(title, content);
        return tab;
    }

    // ── Tab 1: Overview ───────────────────────────────────────────────────

    private VBox buildOverviewTab() {

        List<ResultsStore.ModelResult> results = store.getResults();

        // Accuracy bar chart
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis   yAxis = new NumberAxis(0, 100, 10);
        yAxis.setLabel("Accuracy (%)");

        BarChart<String, Number> accChart =
                new BarChart<>(xAxis, yAxis);
        accChart.setTitle("Model Accuracy (10-fold CV)");
        accChart.setLegendVisible(false);
        accChart.setPrefHeight(280);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (ResultsStore.ModelResult r : results) {
            series.getData().add(
                    new XYChart.Data<>(r.modelName, r.accuracy));
        }
        accChart.getData().add(series);

        // Colour bars: green if >= 70, amber if >= 50, red otherwise
        for (XYChart.Data<String, Number> d : series.getData()) {
            double acc = d.getYValue().doubleValue();
            String colour = acc >= 70 ? "#4caf50"
                          : acc >= 50 ? "#ff9800"
                                      : "#f44336";
            d.getNode().setStyle("-fx-bar-fill: " + colour + ";");
        }

        // Summary table
        TableView<MetricRow> table = buildMetricTable(results);
        table.setPrefHeight(200);

        Label note = new Label(
                "Total instances: " + store.getTotalInstances()
                + "   |   Models trained with 10-fold cross-validation");
        note.setStyle("-fx-font-size: 11px; -fx-text-fill: #888;");

        VBox box = new VBox(12, accChart, table, note);
        box.setPadding(new Insets(16));
        return box;
    }

    private TableView<MetricRow> buildMetricTable(
            List<ResultsStore.ModelResult> results) {

        TableView<MetricRow> table = new TableView<>();

        TableColumn<MetricRow, String> colModel =
                makeCol("Model", "model", 180);
        TableColumn<MetricRow, String> colAcc =
                makeCol("Accuracy (%)", "accuracy", 110);
        TableColumn<MetricRow, String> colKappa =
                makeCol("Kappa", "kappa", 90);
        TableColumn<MetricRow, String> colMAE =
                makeCol("MAE", "mae", 90);
        TableColumn<MetricRow, String> colRMSE =
                makeCol("RMSE", "rmse", 90);

        table.getColumns().addAll(colModel, colAcc, colKappa, colMAE, colRMSE);

        ObservableList<MetricRow> rows =
                FXCollections.observableArrayList();
        for (ResultsStore.ModelResult r : results) {
            rows.add(new MetricRow(
                    r.modelName,
                    String.format("%.2f", r.accuracy),
                    String.format("%.4f", r.kappa),
                    String.format("%.4f", r.mae),
                    String.format("%.4f", r.rmse)
            ));
        }
        table.setItems(rows);
        return table;
    }

    // ── Tab 2: Per-class metrics ──────────────────────────────────────────

    private VBox buildPerClassTab() {

        List<ResultsStore.ModelResult> results = store.getResults();

        // ComboBox to select model
        ComboBox<String> modelPicker = new ComboBox<>();
        for (ResultsStore.ModelResult r : results) {
            modelPicker.getItems().add(r.modelName);
        }
        if (!modelPicker.getItems().isEmpty()) {
            modelPicker.setValue(modelPicker.getItems().get(0));
        }

        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis   yAxis = new NumberAxis(0, 1, 0.1);

        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setTitle("Per-Class Precision / Recall / F1");
        chart.setPrefHeight(380);

        // Rebuild chart when selection changes
        modelPicker.setOnAction(e -> refreshPerClassChart(
                chart, results, modelPicker.getValue()));
        refreshPerClassChart(chart, results,
                modelPicker.getValue());

        Label lbl = new Label("Select model:");
        HBox picker = new HBox(8, lbl, modelPicker);
        picker.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(12, picker, chart);
        box.setPadding(new Insets(16));
        return box;
    }

    private void refreshPerClassChart(
            BarChart<String, Number> chart,
            List<ResultsStore.ModelResult> results,
            String modelName) {

        chart.getData().clear();

        ResultsStore.ModelResult target = results.stream()
                .filter(r -> r.modelName.equals(modelName))
                .findFirst().orElse(null);

        if (target == null) return;

        XYChart.Series<String, Number> precSeries =
                new XYChart.Series<>();
        precSeries.setName("Precision");

        XYChart.Series<String, Number> recSeries =
                new XYChart.Series<>();
        recSeries.setName("Recall");

        XYChart.Series<String, Number> f1Series =
                new XYChart.Series<>();
        f1Series.setName("F1");

        for (java.util.Map.Entry<String, double[]> e
                : target.perClass.entrySet()) {
            String cls = "Class " + e.getKey();
            double[] v = e.getValue();
            precSeries.getData().add(new XYChart.Data<>(cls, v[0]));
            recSeries.getData().add(new XYChart.Data<>(cls,  v[1]));
            f1Series.getData().add(new XYChart.Data<>(cls,   v[2]));
        }

        chart.getData().addAll(precSeries, recSeries, f1Series);
    }

    // ── Tab 3: Confusion matrix ───────────────────────────────────────────

    private VBox buildConfusionTab() {

        List<ResultsStore.ModelResult> results = store.getResults();

        ComboBox<String> modelPicker = new ComboBox<>();
        for (ResultsStore.ModelResult r : results) {
            modelPicker.getItems().add(r.modelName);
        }
        if (!modelPicker.getItems().isEmpty()) {
            modelPicker.setValue(modelPicker.getItems().get(0));
        }

        StackPane tableHolder = new StackPane();
        tableHolder.setPrefHeight(360);

        // Build initial matrix
        refreshConfusionMatrix(tableHolder, results,
                modelPicker.getValue());

        modelPicker.setOnAction(e ->
                refreshConfusionMatrix(tableHolder, results,
                        modelPicker.getValue()));

        Label lbl = new Label("Select model:");
        HBox picker = new HBox(8, lbl, modelPicker);
        picker.setAlignment(Pos.CENTER_LEFT);

        Label hint = new Label(
                "Rows = actual class   |   Columns = predicted class");
        hint.setStyle("-fx-font-size: 11px; -fx-text-fill: #888;");

        VBox box = new VBox(12, picker, tableHolder, hint);
        box.setPadding(new Insets(16));
        return box;
    }

    @SuppressWarnings("unchecked")
    private void refreshConfusionMatrix(
            StackPane holder,
            List<ResultsStore.ModelResult> results,
            String modelName) {

        holder.getChildren().clear();

        ResultsStore.ModelResult target = results.stream()
                .filter(r -> r.modelName.equals(modelName))
                .findFirst().orElse(null);

        if (target == null) return;

        double[][] cm         = target.confusionMatrix;
        String[]   classNames = target.classNames;
        int        n          = classNames.length;

        TableView<ObservableList<String>> table = new TableView<>();

        // First column: row header (actual class)
        TableColumn<ObservableList<String>, String> rowHeader =
                new TableColumn<>("Actual \\ Predicted");
        rowHeader.setPrefWidth(150);
        rowHeader.setCellValueFactory(d ->
                new javafx.beans.property.SimpleStringProperty(
                        d.getValue().get(0)));
        table.getColumns().add(rowHeader);

        // One column per predicted class
        for (int col = 0; col < n; col++) {
            final int c = col;
            TableColumn<ObservableList<String>, String> column =
                    new TableColumn<>("Pred " + classNames[c]);
            column.setPrefWidth(90);
            column.setCellValueFactory(d ->
                    new javafx.beans.property.SimpleStringProperty(
                            d.getValue().get(c + 1)));

            // Highlight diagonal (correct predictions) in green
            column.setCellFactory(tc -> {
                TableCell<ObservableList<String>, String> cell =
                        new TableCell<>() {
                    @Override
                    protected void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty || item == null) {
                            setText(null);
                            setStyle("");
                        } else {
                            setText(item);
                            int row = getIndex();
                            boolean diag = (row == c);
                            setStyle(diag
                                ? "-fx-background-color: #c8e6c9; "
                                  + "-fx-font-weight: bold;"
                                : "");
                        }
                    }
                };
                cell.setAlignment(Pos.CENTER);
                return cell;
            });

            table.getColumns().add(column);
        }

        // Rows
        ObservableList<ObservableList<String>> rows =
                FXCollections.observableArrayList();

        for (int row = 0; row < n; row++) {
            ObservableList<String> rowData =
                    FXCollections.observableArrayList();
            rowData.add("Act " + classNames[row]);
            for (int col = 0; col < n; col++) {
                rowData.add(String.valueOf((int) cm[row][col]));
            }
            rows.add(rowData);
        }

        table.setItems(rows);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        holder.getChildren().add(table);
    }

    // ── Tab 4: Feature importance ─────────────────────────────────────────

    private VBox buildFeatureTab() {

        List<ResultsStore.FeatureImportance> fi =
                store.getFeatureImportances();

        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis   yAxis = new NumberAxis();
        yAxis.setLabel("Information Gain");

        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setTitle("Feature Importance (Information Gain)");
        chart.setLegendVisible(false);
        chart.setPrefHeight(400);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (ResultsStore.FeatureImportance f : fi) {
            series.getData().add(
                    new XYChart.Data<>(f.featureName, f.infoGain));
        }
        chart.getData().add(series);

        // Blue bars
        for (XYChart.Data<String, Number> d : series.getData()) {
            d.getNode().setStyle("-fx-bar-fill: #1976d2;");
        }

        VBox box = new VBox(12, chart);
        box.setPadding(new Insets(16));
        return box;
    }

    // ── Tab 5: Class distribution pie chart ───────────────────────────────

    private VBox buildDistributionTab() {

        List<ResultsStore.ClassDistribution> dist =
                store.getClassDistributions();

        PieChart pie = new PieChart();
        pie.setTitle("Class Distribution (n = "
                + store.getTotalInstances() + ")");
        pie.setPrefHeight(400);

        for (ResultsStore.ClassDistribution d : dist) {
            PieChart.Data slice = new PieChart.Data(
                    "Class " + d.label
                    + " (" + String.format("%.1f", d.percentage) + "%)",
                    d.count);
            pie.getData().add(slice);
        }

        VBox box = new VBox(12, pie);
        box.setPadding(new Insets(16));
        return box;
    }

    // ── Generic column builder ────────────────────────────────────────────

    private <T> TableColumn<T, String> makeCol(
            String title, String property, double width) {

        TableColumn<T, String> col = new TableColumn<>(title);
        col.setCellValueFactory(new PropertyValueFactory<>(property));
        col.setPrefWidth(width);
        return col;
    }

    // ── Row model for the overview table ──────────────────────────────────

    public static class MetricRow {
        private final String model;
        private final String accuracy;
        private final String kappa;
        private final String mae;
        private final String rmse;

        public MetricRow(String model, String accuracy,
                         String kappa, String mae, String rmse) {
            this.model    = model;
            this.accuracy = accuracy;
            this.kappa    = kappa;
            this.mae      = mae;
            this.rmse     = rmse;
        }

        public String getModel()    { return model;    }
        public String getAccuracy() { return accuracy; }
        public String getKappa()    { return kappa;    }
        public String getMae()      { return mae;      }
        public String getRmse()     { return rmse;     }
    }
}