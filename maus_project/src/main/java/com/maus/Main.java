package com.maus;

import java.io.File;

public class Main {

    public static void main(String[] args) {

        String basePath = "maus_project/data/IBI_sequence";

        File mainFolder = new File(basePath);

        if (!mainFolder.exists()) {
            System.out.println("Dataset folder not found!");
            return;
        }

        File[] subjects = mainFolder.listFiles(File::isDirectory);

        for (File subject : subjects) {

            System.out.println("\nSubject: " + subject.getName());

            File[] files = subject.listFiles();

            for (File file : files) {

                String name = file.getName();

                if (name.startsWith("trial_")
                        && name.endsWith(".csv")
                        && !name.contains("peak")) {

                    System.out.println("Valid Trial File: " + name);
                }
            }
        }
    }
}