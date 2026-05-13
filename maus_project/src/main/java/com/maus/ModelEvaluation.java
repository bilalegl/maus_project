package com.maus;

import weka.classifiers.Evaluation;
import weka.classifiers.functions.MultilayerPerceptron;
import weka.classifiers.trees.RandomForest;
import weka.core.Instances;
import weka.core.converters.CSVLoader;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.NumericToNominal;

import java.io.File;
import java.util.Random;

public class ModelEvaluation {

    public static void main(String[] args) {
        try {
            Instances data = loadData("final_dataset.csv");

            System.out.println("Total instances: " + data.numInstances());
            System.out.println("Total attributes: " + data.numAttributes());
            System.out.println("Class attribute: " + data.classAttribute().name());

            System.out.println("\n===== RANDOM FOREST =====");
            evaluateRandomForest(data);

            System.out.println("\n===== MLP =====");
            evaluateMLP(data);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Instances loadData(String csvPath) throws Exception {
        CSVLoader loader = new CSVLoader();
        loader.setSource(new File(csvPath));
        Instances data = loader.getDataSet();

        // Remove Subject and Trial columns
        data.deleteAttributeAt(0); // Subject
        data.deleteAttributeAt(0); // Trial

        // Convert numeric Label to nominal for classification
        NumericToNominal converter = new NumericToNominal();
        converter.setAttributeIndices("last");
        converter.setInputFormat(data);
        data = Filter.useFilter(data, converter);

        // Set class attribute
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
}