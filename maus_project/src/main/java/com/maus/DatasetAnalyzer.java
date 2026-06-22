package com.maus;

import com.opencsv.CSVReader;

import java.io.FileReader;
import java.util.*;

/**
 * Reads final_dataset.csv and prints:
 *   - total row count
 *   - class distribution
 *   - per-feature per-class mean  (useful sanity check before training)
 *
 * BUG FIX: original read line[6] for the label. The CSV has 12 columns
 * (indices 0-11) and Label is the last one at index 11.
 */
public class DatasetAnalyzer {

    // Column indices (must match header in Main.java)
    static final int COL_SUBJECT  = 0;
    static final int COL_TRIAL    = 1;
    static final int COL_MEAN_IBI = 2;
    static final int COL_MEAN_HR  = 3;
    static final int COL_SDNN     = 4;
    static final int COL_RMSSD    = 5;
    static final int COL_SDSD     = 6;
    static final int COL_NN50     = 7;
    static final int COL_PNN50    = 8;
    static final int COL_MIN_IBI  = 9;
    static final int COL_MAX_IBI  = 10;
    static final int COL_LABEL    = 11;   // ← was wrongly 6 before

    static final String[] FEATURE_NAMES = {
        "MeanIBI", "MeanHR", "SDNN", "RMSSD", "SDSD",
        "NN50", "pNN50", "MinIBI", "MaxIBI"
    };
    static final int[] FEATURE_COLS = {
        COL_MEAN_IBI, COL_MEAN_HR, COL_SDNN, COL_RMSSD, COL_SDSD,
        COL_NN50, COL_PNN50, COL_MIN_IBI, COL_MAX_IBI
    };

    public static void main(String[] args) {

        String datasetPath = "final_dataset.csv";

        // label -> count
        Map<String, Integer> labelCounts = new LinkedHashMap<>();

        // label -> list of feature vectors (parallel to FEATURE_COLS)
        Map<String, List<double[]>> labelFeatures = new LinkedHashMap<>();

        int totalRows = 0;

        try {
            CSVReader reader = new CSVReader(new FileReader(datasetPath));
            reader.readNext(); // skip header

            String[] line;
            while ((line = reader.readNext()) != null) {

                if (line.length <= COL_LABEL) {
                    System.err.println("Short row at index " + totalRows + ", skipping.");
                    continue;
                }

                totalRows++;

                String label = line[COL_LABEL].trim();
                if (label.isEmpty()) label = "MISSING";

                labelCounts.put(label, labelCounts.getOrDefault(label, 0) + 1);
                labelFeatures.computeIfAbsent(label, k -> new ArrayList<>());

                double[] feats = new double[FEATURE_COLS.length];
                for (int i = 0; i < FEATURE_COLS.length; i++) {
                    try {
                        feats[i] = Double.parseDouble(line[FEATURE_COLS[i]].trim());
                    } catch (NumberFormatException e) {
                        feats[i] = Double.NaN;
                    }
                }
                labelFeatures.get(label).add(feats);
            }
            reader.close();

        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        // ── Print summary ──────────────────────────────────────────────
        System.out.println("=================================");
        System.out.println("DATASET ANALYSIS");
        System.out.println("=================================");
        System.out.println("Total Rows : " + totalRows);
        System.out.println("Labels     : " + labelCounts.keySet());

        System.out.println("\nClass Distribution:");
        for (Map.Entry<String, Integer> e : labelCounts.entrySet()) {
            double pct = 100.0 * e.getValue() / totalRows;
            System.out.printf("  Label %-8s -> %4d rows  (%.1f%%)%n",
                    e.getKey(), e.getValue(), pct);
        }

        System.out.println("\nPer-class feature means:");
        System.out.printf("  %-10s", "Feature");
        for (String lbl : labelCounts.keySet()) {
            System.out.printf("  Label %-5s", lbl);
        }
        System.out.println();

        for (int fi = 0; fi < FEATURE_NAMES.length; fi++) {
            System.out.printf("  %-10s", FEATURE_NAMES[fi]);
            for (String lbl : labelCounts.keySet()) {
                List<double[]> rows = labelFeatures.getOrDefault(lbl, Collections.emptyList());
                double sum = 0; int cnt = 0;
                for (double[] r : rows) {
                    if (!Double.isNaN(r[fi])) { sum += r[fi]; cnt++; }
                }
                double mean = cnt > 0 ? sum / cnt : Double.NaN;
                System.out.printf("  %11.2f", mean);
            }
            System.out.println();
        }

        System.out.println("\nDone.");
    }
}