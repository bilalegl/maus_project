package com.maus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Singleton store that ModelEvaluation writes to and the JavaFX
 * Dashboard reads from.  No file I/O needed — same JVM.
 *
 * Usage in ModelEvaluation:
 *   ResultsStore.get().addModelResult(...)
 *
 * Usage in JavaFX controller:
 *   ResultsStore store = ResultsStore.get();
 *   List<ModelResult> results = store.getResults();
 */
public class ResultsStore {

    // ── Singleton ──────────────────────────────────────────────────────

    private static final ResultsStore INSTANCE = new ResultsStore();
    public static ResultsStore get() { return INSTANCE; }
    private ResultsStore() {}

    // ── Data structures ────────────────────────────────────────────────

    public static class ModelResult {

        public final String modelName;
        public final double accuracy;       // 0-100
        public final double kappa;
        public final double mae;
        public final double rmse;
        // Per-class precision/recall/f1  key = class label string
        public final Map<String, double[]> perClass; // [precision, recall, f1]
        public final double[][] confusionMatrix;
        public final String[] classNames;

        public ModelResult(
                String modelName,
                double accuracy,
                double kappa,
                double mae,
                double rmse,
                Map<String, double[]> perClass,
                double[][] confusionMatrix,
                String[] classNames) {

            this.modelName       = modelName;
            this.accuracy        = accuracy;
            this.kappa           = kappa;
            this.mae             = mae;
            this.rmse            = rmse;
            this.perClass        = perClass;
            this.confusionMatrix = confusionMatrix;
            this.classNames      = classNames;
        }
    }

    public static class FeatureImportance {
        public final String featureName;
        public final double infoGain;

        public FeatureImportance(String featureName, double infoGain) {
            this.featureName = featureName;
            this.infoGain    = infoGain;
        }
    }

    public static class ClassDistribution {
        public final String label;
        public final int count;
        public final double percentage;

        public ClassDistribution(String label, int count, double percentage) {
            this.label      = label;
            this.count      = count;
            this.percentage = percentage;
        }
    }

    // ── Storage ────────────────────────────────────────────────────────

    private final List<ModelResult>        results              = new ArrayList<>();
    private final List<FeatureImportance>  featureImportances   = new ArrayList<>();
    private final List<ClassDistribution>  classDistributions   = new ArrayList<>();
    private int totalInstances = 0;

    // ── Write API ──────────────────────────────────────────────────────

    public void addModelResult(ModelResult r) {
        results.add(r);
    }

    public void setFeatureImportances(List<FeatureImportance> list) {
        featureImportances.clear();
        featureImportances.addAll(list);
    }

    public void setClassDistributions(List<ClassDistribution> list, int total) {
        classDistributions.clear();
        classDistributions.addAll(list);
        totalInstances = total;
    }

    // ── Read API ───────────────────────────────────────────────────────

    public List<ModelResult>       getResults()             { return results; }
    public List<FeatureImportance> getFeatureImportances()  { return featureImportances; }
    public List<ClassDistribution> getClassDistributions()  { return classDistributions; }
    public int getTotalInstances()                          { return totalInstances; }

    // ── Helper: build ModelResult from Weka Evaluation ────────────────

    public static ModelResult fromEvaluation(
            String modelName,
            weka.classifiers.Evaluation eval) throws Exception {

        double accuracy = (1 - eval.errorRate()) * 100;
        double kappa    = eval.kappa();
        double mae      = eval.meanAbsoluteError();
        double rmse     = eval.rootMeanSquaredError();

        // Class names
        weka.core.Attribute classAttr = eval.getHeader().classAttribute();
        int numClasses = classAttr.numValues();
        String[] classNames = new String[numClasses];
        for (int i = 0; i < numClasses; i++) {
            classNames[i] = classAttr.value(i);
        }

        // Per-class stats
        Map<String, double[]> perClass = new LinkedHashMap<>();
        for (int i = 0; i < numClasses; i++) {
            double prec = eval.precision(i);
            double rec  = eval.recall(i);
            double f1   = eval.fMeasure(i);
            perClass.put(classNames[i], new double[]{prec, rec, f1});
        }

        // Confusion matrix
        double[][] cm = eval.confusionMatrix();

        return new ModelResult(modelName, accuracy, kappa, mae, rmse,
                perClass, cm, classNames);
    }
}