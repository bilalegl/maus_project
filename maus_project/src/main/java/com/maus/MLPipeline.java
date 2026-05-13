package com.maus;

import weka.classifiers.trees.RandomForest;
import weka.core.Instances;
import weka.core.converters.CSVLoader;

import java.io.File;

public class MLPipeline {

    public static void main(String[] args) {

        try {

            // Load CSV dataset
            CSVLoader loader = new CSVLoader();

            loader.setSource(new File("final_dataset.csv"));

            Instances data = loader.getDataSet();

            // Remove Subject and Trial string columns
            data.deleteAttributeAt(0); // Subject
            data.deleteAttributeAt(0); // Trial

            // Set class label (last column)
            data.setClassIndex(data.numAttributes() - 1);

            System.out.println("Dataset Loaded Successfully");

            System.out.println("Total Instances: " + data.numInstances());

            System.out.println("Total Attributes: " + data.numAttributes());

            // Random Forest Model
            RandomForest rf = new RandomForest();

            rf.buildClassifier(data);

            System.out.println("\nRandom Forest Model Trained Successfully");

            System.out.println(rf);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}