package com.jobapply;

/**
 * Entry point. Run steps in order the first time:
 *   1. login   -> opens browser, you log in + OTP, session gets saved
 *   2. scrape  -> searches Naukri per config, saves matching jobs to data/jobs.json
 *   3. apply   -> opens each unapplied job and applies (native listings only, by default)
 *
 * Usage (after `mvn compile`):
 *   mvn exec:java -Dexec.args="login"
 *   mvn exec:java -Dexec.args="scrape"
 *   mvn exec:java -Dexec.args="apply"
 *
 * Or after `mvn package`, run the fat jar directly:
 *   java -jar target/naukri-auto-apply.jar login
 *   java -jar target/naukri-auto-apply.jar scrape
 *   java -jar target/naukri-auto-apply.jar apply
 */
public class Main {
    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            return;
        }

        switch (args[0].toLowerCase()) {
            case "login" -> Login.run();
            case "scrape" -> JobScraper.run();
            case "apply" -> ApplyBot.run();
            default -> printUsage();
        }
    }

    private static void printUsage() {
        System.out.println("Usage: java -jar naukri-auto-apply.jar <login|scrape|apply>");
        System.out.println();
        System.out.println("  login   - open browser, log in + OTP manually, save session");
        System.out.println("  scrape  - search Naukri and save matching jobs to data/jobs.json");
        System.out.println("  apply   - apply to scraped jobs (native listings only, by default)");
    }
}
