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
import java.util.*;

/**
 * Same logic as ModelEvaluation but writes every result into ResultsStore
 * so the JavaFX dashboard can display them without re-running models.
 *
 * Call ModelEvaluationWithStore.run() from your JavaFX main before
 * launching the dashboard stage.
 */
public class ModelEvaluationWithStore {

    public static void run(String datasetPath) throws Exception {

        ResultsStore store = ResultsStore.get();

        // ── Load data ───────────────────────────────────────────────────
        Instances data = loadData(datasetPath);

        // ── Class distribution ──────────────────────────────────────────
        List<ResultsStore.ClassDistribution> distList = new ArrayList<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (int i = 0; i < data.numInstances(); i++) {
            String lbl = data.instance(i).stringValue(data.classIndex());
            counts.merge(lbl, 1, Integer::sum);
        }
        int total = data.numInstances();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            distList.add(new ResultsStore.ClassDistribution(
                    e.getKey(), e.getValue(), 100.0 * e.getValue() / total));
        }
        store.setClassDistributions(distList, total);

        // ── Feature importance ──────────────────────────────────────────
        AttributeSelection selector = new AttributeSelection();
        selector.setEvaluator(new InfoGainAttributeEval());
        Ranker ranker = new Ranker();
        selector.setSearch(ranker);
        selector.SelectAttributes(data);

        double[][] ranked = selector.rankedAttributes();
        List<ResultsStore.FeatureImportance> fiList = new ArrayList<>();
        for (double[] pair : ranked) {
            int idx = (int) pair[0];
            if (idx == data.classIndex()) continue;
            fiList.add(new ResultsStore.FeatureImportance(
                    data.attribute(idx).name(), pair[1]));
        }
        store.setFeatureImportances(fiList);

        // ── Normalize ───────────────────────────────────────────────────
        Instances dataNorm = normalize(data);

        // ── 10-fold CV for each model ───────────────────────────────────
        String[] names = {"Random Forest", "MLP", "AdaBoost"};
        weka.classifiers.Classifier[] clfs = {
            buildRF(), buildMLP(), buildBoost()
        };

        for (int i = 0; i < clfs.length; i++) {
            Evaluation eval = new Evaluation(dataNorm);
            eval.crossValidateModel(clfs[i], dataNorm, 10, new Random(1));
            store.addModelResult(
                    ResultsStore.fromEvaluation(names[i], eval));
            System.out.println(names[i] + " done.");
        }
    }

    // ── Builders ──────────────────────────────────────────────────────────

    private static RandomForest buildRF() {
        RandomForest rf = new RandomForest();
        rf.setNumIterations(100);
        rf.setSeed(1);
        return rf;
    }

    private static MultilayerPerceptron buildMLP() {
        MultilayerPerceptron mlp = new MultilayerPerceptron();
        mlp.setSeed(1);
        mlp.setTrainingTime(200);
        return mlp;
    }

    private static AdaBoostM1 buildBoost() {
        AdaBoostM1 boost = new AdaBoostM1();
        boost.setClassifier(new DecisionStump());
        boost.setNumIterations(50);
        return boost;
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    static Instances loadData(String csvPath) throws Exception {
        CSVLoader loader = new CSVLoader();
        loader.setSource(new File(csvPath));
        Instances data = loader.getDataSet();
        data.deleteAttributeAt(0); // Subject
        data.deleteAttributeAt(0); // Trial
        data.setClassIndex(data.numAttributes() - 1);

        NumericToNominal convert = new NumericToNominal();
        convert.setAttributeIndices(String.valueOf(data.classIndex() + 1));
        convert.setInputFormat(data);
        data = Filter.useFilter(data, convert);
        data.setClassIndex(data.numAttributes() - 1);
        return data;
    }

    static Instances normalize(Instances data) throws Exception {
        Normalize norm = new Normalize();
        norm.setInputFormat(data);
        Instances out = Filter.useFilter(data, norm);
        out.setClassIndex(data.classIndex());
        return out;
    }
}