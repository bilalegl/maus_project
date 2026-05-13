package com.maus;

import weka.classifiers.Evaluation;
import weka.classifiers.functions.MultilayerPerceptron;
import weka.classifiers.trees.RandomForest;
import weka.core.Instances;
import weka.core.converters.CSVLoader;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.NumericToNominal;
import weka.attributeSelection.AttributeSelection;
import weka.attributeSelection.InfoGainAttributeEval;
import weka.attributeSelection.Ranker;
import weka.classifiers.meta.AdaBoostM1;
import weka.classifiers.trees.DecisionStump;

import java.io.File;
import java.util.Random;

public class ModelEvaluation {

    public static void main(String[] args) {
        try {
            Instances data = loadData("final_dataset.csv");

            System.out.println("Total instances: " + data.numInstances());
            System.out.println("Total attributes: " + data.numAttributes());
            System.out.println("Class attribute: " + data.classAttribute().name());
            performFeatureSelection(data);

            System.out.println("\n===== RANDOM FOREST =====");
            evaluateRandomForest(data);

            System.out.println("\n===== MLP =====");
            evaluateMLP(data);

            System.out.println("\n===== GRADIENT BOOSTING =====");
evaluateBoosting(data);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

private static Instances loadData(String csvPath) throws Exception {

    CSVLoader loader = new CSVLoader();

    loader.setSource(new File(csvPath));

    Instances data = loader.getDataSet();

    // Remove Subject column
    data.deleteAttributeAt(0);

    // Remove Trial column
    data.deleteAttributeAt(0);

    // Set class attribute
    data.setClassIndex(data.numAttributes() - 1);

    // Convert class from numeric to nominal
    NumericToNominal convert =
            new NumericToNominal();

    convert.setAttributeIndices(
            String.valueOf(data.classIndex() + 1)
    );

    convert.setInputFormat(data);

    data = Filter.useFilter(data, convert);

    // Reset class index
    data.setClassIndex(data.numAttributes() - 1);

    return data;
}

    private static void evaluateRandomForest(Instances data) throws Exception {
        RandomForest rf = new RandomForest();
        rf.setNumIterations(100);
        rf.setSeed(1);

        Evaluation eval = new Evaluation(data);
        eval.crossValidateModel(rf, data, 10, new Random(1));

        System.out.println(eval.toSummaryString());
        System.out.println(eval.toClassDetailsString());
        System.out.println(eval.toMatrixString());
    }

    private static void evaluateMLP(Instances data) throws Exception {
        MultilayerPerceptron mlp = new MultilayerPerceptron();
        mlp.setSeed(1);
        mlp.setTrainingTime(200); // epochs

        Evaluation eval = new Evaluation(data);
        eval.crossValidateModel(mlp, data, 10, new Random(1));

        System.out.println(eval.toSummaryString());
        System.out.println(eval.toClassDetailsString());
        System.out.println(eval.toMatrixString());
    }
    
private static void evaluateBoosting(Instances data)
        throws Exception {

    AdaBoostM1 boost = new AdaBoostM1();

    boost.setClassifier(new DecisionStump());

    boost.setNumIterations(50);

    Evaluation eval = new Evaluation(data);

    eval.crossValidateModel(
            boost,
            data,
            10,
            new Random(1)
    );

    System.out.println(eval.toSummaryString());

    System.out.println(eval.toClassDetailsString());

    System.out.println(eval.toMatrixString());
}

    public static void performFeatureSelection(Instances data)
        throws Exception {

    AttributeSelection selector = new AttributeSelection();

    InfoGainAttributeEval evaluator =
            new InfoGainAttributeEval();

    Ranker ranker = new Ranker();

    selector.setEvaluator(evaluator);

    selector.setSearch(ranker);

    selector.SelectAttributes(data);

    System.out.println("\n===== FEATURE SELECTION =====");

    int[] indices = selector.selectedAttributes();

    double[][] ranked =
            selector.rankedAttributes();

    for (int i = 0; i < ranked.length; i++) {

        int index = (int) ranked[i][0];

        double score = ranked[i][1];

        System.out.println(
                data.attribute(index).name()
                        + " -> "
                        + score
        );
    }
}
}