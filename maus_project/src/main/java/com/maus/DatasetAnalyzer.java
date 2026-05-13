package com.maus;

import com.opencsv.CSVReader;

import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;

public class DatasetAnalyzer {

    public static void main(String[] args) {

        String datasetPath = "final_dataset.csv";

        Map<String, Integer> labelCounts = new HashMap<>();

        int totalRows = 0;

        try {

            CSVReader reader = new CSVReader(
                    new FileReader(datasetPath)
            );

            String[] line;

            // Skip header
            reader.readNext();

            while ((line = reader.readNext()) != null) {

                totalRows++;

                String label = line[6];

                if (label == null || label.trim().isEmpty()) {
                    label = "MISSING";
                }

                labelCounts.put(
                        label,
                        labelCounts.getOrDefault(label, 0) + 1
                );
            }

            reader.close();

            System.out.println("=================================");
            System.out.println("DATASET ANALYSIS");
            System.out.println("=================================");

            System.out.println("Total Rows: " + totalRows);

            System.out.println("\nClass Distribution:");

            for (Map.Entry<String, Integer> entry : labelCounts.entrySet()) {

                System.out.println(
                        "Label "
                                + entry.getKey()
                                + " -> "
                                + entry.getValue()
                        );
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}