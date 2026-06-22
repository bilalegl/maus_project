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

            CSVWriter writer = new CSVWriter(new FileWriter("final_dataset.csv"));

            String[] header = {
                "Subject", "Trial", "MeanIBI", "MeanHR",
                "SDNN", "RMSSD", "SDSD", "NN50", "pNN50",
                "MinIBI", "MaxIBI", "Label"
            };

            writer.writeNext(header);

            File[] participantFolders = mainFolder.listFiles(File::isDirectory);

            if (participantFolders == null) {
                System.out.println("No participant folders found.");
                return;
            }

            int processed = 0;
            int skipped   = 0;

            for (File participant : participantFolders) {

                String subjectID = participant.getName();
                File[] files = participant.listFiles();

                if (files == null) continue;

                for (File file : files) {

                    String fileName = file.getName();

                    if (!fileName.startsWith("trial_")
                            || !fileName.endsWith(".csv")
                            || fileName.contains("peak")) {
                        continue;
                    }

                    List<Double> ppiValues = new ArrayList<>();

                    try {
                        CSVReader reader = new CSVReader(new FileReader(file));
                        reader.readNext(); // skip header

                        String[] line;
                        while ((line = reader.readNext()) != null) {
                            try {
                                if (line.length <= 2) continue;
                                String value = line[2];
                                if (value == null || value.trim().isEmpty()) continue;
                                double ppi = Double.parseDouble(value.trim());
                                // Valid IBI range: 300–2000 ms
                                if (ppi >= 300 && ppi <= 2000) {
                                    ppiValues.add(ppi);
                                }
                            } catch (NumberFormatException ignored) {
                                // skip malformed rows
                            }
                        }
                        reader.close();

                    } catch (Exception e) {
                        System.err.println("Error reading: " + file.getPath());
                        e.printStackTrace();
                    }

                    if (ppiValues.size() < 5) {
                        // Skip files with too few beats for reliable HRV
                        skipped++;
                        continue;
                    }

                    double meanIBI = calculateMean(ppiValues);
                    double meanHR  = 60000.0 / meanIBI;
                    double sdnn    = calculateSDNN(ppiValues, meanIBI);
                    double rmssd   = calculateRMSSD(ppiValues);
                    double sdsd    = calculateSDSD(ppiValues);
                    int    nn50    = calculateNN50(ppiValues);
                    double pnn50   = calculatePNN50(ppiValues, nn50);
                    double minIBI  = calculateMin(ppiValues);
                    double maxIBI  = calculateMax(ppiValues);

                    String label = getLabelFromTrial(fileName);

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
                    processed++;

                    System.out.println("Processed: " + subjectID + " - " + fileName
                            + " | Beats: " + ppiValues.size()
                            + " | Label: " + label);
                }
            }

            writer.close();

            System.out.println("\n=================================");
            System.out.println("final_dataset.csv created.");
            System.out.println("Rows written : " + processed);
            System.out.println("Rows skipped : " + skipped + " (< 5 valid beats)");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Maps trial file name to cognitive load label.
     *
     * MAUS task sequence:
     *   trial_1  -> 0-back  (label 0)
     *   trial_2  -> 2-back  (label 2)
     *   trial_3  -> 3-back  (label 3)
     *   trial_4  -> 2-back  (label 2)
     *   trial_5  -> 3-back  (label 3)
     *   trial_6  -> 0-back  (label 0)
     *
     * BUG FIX: original code used startsWith("trial_1") which matched
     * trial_10, trial_11, trial_12 etc., silently mislabelling them.
     * We now extract the numeric part and compare exactly.
     */
    public static String getLabelFromTrial(String fileName) {

        // Strip "trial_" prefix and ".csv" suffix to get trial number
        String stripped = fileName.replace("trial_", "").replace(".csv", "").trim();

        // Handle cases like "trial_1_something.csv" — take first token
        if (stripped.contains("_")) {
            stripped = stripped.split("_")[0];
        }

        try {
            int trialNum = Integer.parseInt(stripped);
            switch (trialNum) {
                case 1: case 6: return "0";
                case 2: case 4: return "2";
                case 3: case 5: return "3";
                default:        return "UNKNOWN";
            }
        } catch (NumberFormatException e) {
            return "UNKNOWN";
        }
    }

    // ── HRV Feature Calculations ─────────────────────────────────────────

    public static double calculateMean(List<Double> values) {
        double sum = 0;
        for (double v : values) sum += v;
        return sum / values.size();
    }

    public static double calculateSDNN(List<Double> values, double mean) {
        double sum = 0;
        for (double v : values) sum += Math.pow(v - mean, 2);
        return Math.sqrt(sum / (values.size() - 1));
    }

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
            diffs.add(values.get(i + 1) - values.get(i));
        }
        double mean = calculateMean(diffs);
        double sum  = 0;
        for (double d : diffs) sum += Math.pow(d - mean, 2);
        return Math.sqrt(sum / (diffs.size() - 1));
    }

    public static int calculateNN50(List<Double> values) {
        int count = 0;
        for (int i = 0; i < values.size() - 1; i++) {
            if (Math.abs(values.get(i + 1) - values.get(i)) > 50) count++;
        }
        return count;
    }

    public static double calculatePNN50(List<Double> values, int nn50) {
        return ((double) nn50 / (values.size() - 1)) * 100.0;
    }

    public static double calculateMin(List<Double> values) {
        double min = values.get(0);
        for (double v : values) if (v < min) min = v;
        return min;
    }

    public static double calculateMax(List<Double> values) {
        double max = values.get(0);
        for (double v : values) if (v > max) max = v;
        return max;
    }
}