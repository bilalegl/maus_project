package com.maus;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) {

        String basePath = "maus_project/data/IBI_sequence";

        File mainFolder = new File(basePath);

        if (!mainFolder.exists() || !mainFolder.isDirectory()) {
            System.out.println("IBI_sequence folder not found.");
            return;
        }

        try {

            // Create output dataset
            CSVWriter writer = new CSVWriter(
                    new FileWriter("final_dataset.csv")
            );

            // Header
            String[] header = {
        "Subject",
        "Trial",
        "MeanIBI",
        "MeanHR",
        "SDNN",
        "RMSSD",
        "SDSD",
        "NN50",
        "pNN50",
        "MinIBI",
        "MaxIBI",
        "Label"
};

            writer.writeNext(header);

            File[] participantFolders = mainFolder.listFiles(File::isDirectory);

            if (participantFolders == null) {
                System.out.println("No participant folders found.");
                return;
            }

            // Loop through subjects
            for (File participant : participantFolders) {

                String subjectID = participant.getName();

                File[] files = participant.listFiles();

                if (files == null) {
                    continue;
                }

                // Loop through files
                for (File file : files) {

                    String fileName = file.getName();

                    // Process only valid trial files
                    if (fileName.startsWith("trial_")
                            && fileName.endsWith(".csv")
                            && !fileName.contains("peak")) {

                        List<Double> ppiValues = new ArrayList<>();

                        try {

                            CSVReader reader = new CSVReader(
                                    new FileReader(file)
                            );

                            String[] line;

                            // Skip header
                            reader.readNext();

                            while ((line = reader.readNext()) != null) {

                                try {

                                    // PPI_pix column index = 2
                                    String value = line[2];

                                    if (value == null || value.isEmpty()) {
                                        continue;
                                    }

                                    double ppi = Double.parseDouble(value);

                                    // Cleaning rules
                                    if (ppi >= 300 && ppi <= 2000) {
                                        ppiValues.add(ppi);
                                    }

                                } catch (Exception e) {
                                    // Ignore bad rows
                                }
                            }

                            reader.close();

                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        // Skip empty files
                        if (ppiValues.isEmpty()) {
                            continue;
                        }

                        // HRV Features
double meanIBI = calculateMean(ppiValues);

double meanHR = 60000.0 / meanIBI;

double sdnn = calculateSDNN(ppiValues, meanIBI);

double rmssd = calculateRMSSD(ppiValues);

double sdsd = calculateSDSD(ppiValues);

int nn50 = calculateNN50(ppiValues);

double pnn50 = calculatePNN50(ppiValues, nn50);

double minIBI = calculateMin(ppiValues);

double maxIBI = calculateMax(ppiValues);

                        // Get clean label from trial number
                        String label = getLabelFromTrial(fileName);

                        // Dataset row
String[] row = {
        subjectID,
        fileName,
        String.format("%.2f", meanIBI),
        String.format("%.2f", meanHR),
        String.format("%.2f", sdnn),
        String.format("%.2f", rmssd),
        String.format("%.2f", sdsd),
        String.valueOf(nn50),
        String.format("%.2f", pnn50),
        String.format("%.2f", minIBI),
        String.format("%.2f", maxIBI),
        label
};

                        writer.writeNext(row);

                        System.out.println(
                                "Processed: "
                                        + subjectID
                                        + " - "
                                        + fileName
                                        + " | Label: "
                                        + label
                        );
                    }
                }
            }

            writer.close();

            System.out.println("\n=================================");
            System.out.println("final_dataset.csv created successfully.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Assign label using MAUS task sequence
    public static String getLabelFromTrial(String fileName) {

        // 0-back
        if (fileName.startsWith("trial_1")
                || fileName.startsWith("trial_6")) {
            return "0";
        }

        // 2-back
        if (fileName.startsWith("trial_2")
                || fileName.startsWith("trial_4")) {
            return "2";
        }

        // 3-back
        if (fileName.startsWith("trial_3")
                || fileName.startsWith("trial_5")) {
            return "3";
        }

        return "UNKNOWN";
    }

    // Mean IBI
    public static double calculateMean(List<Double> values) {

        double sum = 0;

        for (double v : values) {
            sum += v;
        }

        return sum / values.size();
    }

    // SDNN
    public static double calculateSDNN(List<Double> values, double mean) {

        double sum = 0;

        for (double v : values) {
            sum += Math.pow(v - mean, 2);
        }

        return Math.sqrt(sum / (values.size() - 1));
    }

    // RMSSD
    public static double calculateRMSSD(List<Double> values) {

        double sum = 0;

        for (int i = 0; i < values.size() - 1; i++) {

            double diff = values.get(i + 1) - values.get(i);

            sum += diff * diff;
        }

        return Math.sqrt(sum / (values.size() - 1));
    }

    public static double calculateSDSD(List<Double> values) {

    List<Double> diffs = new ArrayList<>();

    for (int i = 0; i < values.size() - 1; i++) {

        double diff = values.get(i + 1) - values.get(i);

        diffs.add(diff);
    }

    double mean = calculateMean(diffs);

    double sum = 0;

    for (double d : diffs) {
        sum += Math.pow(d - mean, 2);
    }

    return Math.sqrt(sum / (diffs.size() - 1));
}

public static int calculateNN50(List<Double> values) {

    int count = 0;

    for (int i = 0; i < values.size() - 1; i++) {

        double diff = Math.abs(
                values.get(i + 1) - values.get(i)
        );

        if (diff > 50) {
            count++;
        }
    }

    return count;
}

public static double calculatePNN50(List<Double> values, int nn50) {

    return ((double) nn50 / (values.size() - 1)) * 100.0;
}

public static double calculateMin(List<Double> values) {

    double min = values.get(0);

    for (double v : values) {

        if (v < min) {
            min = v;
        }
    }

    return min;
}

public static double calculateMax(List<Double> values) {

    double max = values.get(0);

    for (double v : values) {

        if (v > max) {
            max = v;
        }
    }

    return max;
}
}