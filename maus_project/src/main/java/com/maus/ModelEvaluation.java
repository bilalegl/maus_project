package com.maus;

import weka.classifiers.Evaluation;
import weka.classifiers.functions.MultilayerPerceptron;
import weka.classifiers.trees.RandomForest;
import weka.classifiers.meta.AdaBoostM1;
import weka.classifiers.trees.DecisionStump;
import weka.core.Instances;
import weka.core.converters.CSVLoader;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.Normalize;
import weka.filters.unsupervised.attribute.NumericToNominal;
import weka.attributeSelection.AttributeSelection;
import weka.attributeSelection.InfoGainAttributeEval;
import weka.attributeSelection.Ranker;

import java.io.File;
import java.util.Random;

/**
 * Improvements over original:
 *
 * 1. Normalization added before MLP (MLP is sensitive to feature scale).
 * 2. Stratified held-out 80/20 split in addition to 10-fold CV so results
 *    on unseen data are also reported.
 * 3. Per-class precision / recall / F1 printed for every model.
 * 4. MLPipeline.java is now obsolete — this class handles everything.
 */
public class ModelEvaluation {

    public static void main(String[] args) {
        try {
            // ── Load + preprocess ───────────────────────────────────────
            Instances data = loadData("final_dataset.csv");

            System.out.println("Total instances : " + data.numInstances());
            System.out.println("Total attributes: " + data.numAttributes());
            System.out.println("Class attribute : " + data.classAttribute().name());

            // Feature selection report (uses full data — info only)
            performFeatureSelection(data);

            // Normalize for all models (min-max to [0,1])
            Instances dataNorm = normalize(data);

            // ── 10-fold cross-validation ────────────────────────────────
            System.out.println("\n==============================");
            System.out.println("10-FOLD CROSS-VALIDATION");
            System.out.println("==============================");

            System.out.println("\n--- Random Forest ---");
            evaluateRandomForest(dataNorm, true);

            System.out.println("\n--- MLP ---");
            evaluateMLP(dataNorm, true);

            System.out.println("\n--- AdaBoost ---");
            evaluateBoosting(dataNorm, true);

            // ── Held-out 80/20 split ────────────────────────────────────
            System.out.println("\n==============================");
            System.out.println("HELD-OUT 80/20 TEST SPLIT");
            System.out.println("==============================");

            // Shuffle with fixed seed for reproducibility
            dataNorm.randomize(new Random(42));
            dataNorm.stratify(5);

            int trainSize = (int) Math.round(dataNorm.numInstances() * 0.8);
            int testSize  = dataNorm.numInstances() - trainSize;

            Instances trainSet = new Instances(dataNorm, 0, trainSize);
            Instances testSet  = new Instances(dataNorm, trainSize, testSize);

            System.out.println("Train: " + trainSet.numInstances()
                    + "  Test: " + testSet.numInstances());

            System.out.println("\n--- Random Forest (held-out) ---");
            evaluateOnSplit(new RandomForest(), trainSet, testSet);

            System.out.println("\n--- MLP (held-out) ---");
            MultilayerPerceptron mlp = new MultilayerPerceptron();
            mlp.setSeed(1);
            mlp.setTrainingTime(200);
            evaluateOnSplit(mlp, trainSet, testSet);

            System.out.println("\n--- AdaBoost (held-out) ---");
            AdaBoostM1 boost = new AdaBoostM1();
            boost.setClassifier(new DecisionStump());
            boost.setNumIterations(50);
            evaluateOnSplit(boost, trainSet, testSet);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ── Data loading ──────────────────────────────────────────────────────

    private static Instances loadData(String csvPath) throws Exception {

        CSVLoader loader = new CSVLoader();
        loader.setSource(new File(csvPath));
        Instances data = loader.getDataSet();

        // Remove Subject (index 0) then Trial (now also index 0 after first removal)
        data.deleteAttributeAt(0);
        data.deleteAttributeAt(0);

        // Set class to last column (Label)
        data.setClassIndex(data.numAttributes() - 1);

        // Convert numeric label to nominal so Weka treats it as classification
        NumericToNominal convert = new NumericToNominal();
        convert.setAttributeIndices(String.valueOf(data.classIndex() + 1));
        convert.setInputFormat(data);
        data = Filter.useFilter(data, convert);
        data.setClassIndex(data.numAttributes() - 1);

        return data;
    }

    // ── Normalization ─────────────────────────────────────────────────────

    /**
     * Min-max normalization to [0,1] on all numeric features (not class).
     * Important for MLP convergence; harmless for tree-based models.
     */
    private static Instances normalize(Instances data) throws Exception {
        Normalize norm = new Normalize();
        norm.setInputFormat(data);
        Instances normalized = Filter.useFilter(data, norm);
        normalized.setClassIndex(data.classIndex());
        return normalized;
    }

    // ── Cross-validation evaluations ──────────────────────────────────────

    private static void evaluateRandomForest(Instances data, boolean cv) throws Exception {
        RandomForest rf = new RandomForest();
        rf.setNumIterations(100);
        rf.setSeed(1);
        if (cv) runCrossValidation(rf, data);
    }

    private static void evaluateMLP(Instances data, boolean cv) throws Exception {
        MultilayerPerceptron mlp = new MultilayerPerceptron();
        mlp.setSeed(1);
        mlp.setTrainingTime(200);
        if (cv) runCrossValidation(mlp, data);
    }

    private static void evaluateBoosting(Instances data, boolean cv) throws Exception {
        AdaBoostM1 boost = new AdaBoostM1();
        boost.setClassifier(new DecisionStump());
        boost.setNumIterations(50);
        if (cv) runCrossValidation(boost, data);
    }

    private static void runCrossValidation(
            weka.classifiers.Classifier clf,
            Instances data) throws Exception {

        Evaluation eval = new Evaluation(data);
        eval.crossValidateModel(clf, data, 10, new Random(1));
        printResults(eval);
    }

    // ── Held-out split evaluation ─────────────────────────────────────────

    private static void evaluateOnSplit(
            weka.classifiers.Classifier clf,
            Instances train,
            Instances test) throws Exception {

        clf.buildClassifier(train);
        Evaluation eval = new Evaluation(train);
        eval.evaluateModel(clf, test);
        printResults(eval);
    }

    // ── Result printing ───────────────────────────────────────────────────

    private static void printResults(Evaluation eval) throws Exception {
        System.out.printf("Accuracy     : %.2f%%%n",
                (1 - eval.errorRate()) * 100);
        System.out.printf("Kappa        : %.4f%n", eval.kappa());
        System.out.printf("MAE          : %.4f%n", eval.meanAbsoluteError());
        System.out.printf("RMSE         : %.4f%n", eval.rootMeanSquaredError());
        System.out.println(eval.toClassDetailsString("Per-class metrics:"));
        System.out.println(eval.toMatrixString("Confusion matrix:"));
    }

    // ── Feature selection ─────────────────────────────────────────────────

    public static void performFeatureSelection(Instances data) throws Exception {

        AttributeSelection selector = new AttributeSelection();
        InfoGainAttributeEval evaluator = new InfoGainAttributeEval();
        Ranker ranker = new Ranker();

        selector.setEvaluator(evaluator);
        selector.setSearch(ranker);
        selector.SelectAttributes(data);

        System.out.println("\n===== FEATURE IMPORTANCE (Information Gain) =====");

        double[][] ranked = selector.rankedAttributes();
        for (double[] pair : ranked) {
            int    idx   = (int) pair[0];
            double score = pair[1];
            // Skip the class attribute itself
            if (idx == data.classIndex()) continue;
            System.out.printf("  %-12s -> %.4f%n",
                    data.attribute(idx).name(), score);
        }
    }
}