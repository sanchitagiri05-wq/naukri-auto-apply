package com.jobapply;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.jobapply.util.Config;
import com.jobapply.util.Pacer;
import com.jobapply.util.ResumeParser;
import com.jobapply.util.ScreeningQuestionHandler;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

/**
 * STEP 3: Run after Login + JobScraper.
 *
 * Opens each job from data/jobs.json that hasn't been applied to yet and
 * clicks the native "Apply" button where present. Jobs that redirect to an
 * external company site are SKIPPED by default (apply.nativeOnly=true in
 * config) and logged so you can review/apply to them manually — those forms
 * vary too much per-company to safely automate blindly.
 *
 * Screening questions (notice period, CTC, etc.), when a popup appears, are
 * filled from config/config.properties — NOT guessed. If a popup shows a
 * question this script doesn't recognize, it stops and asks you to handle
 * that one job manually rather than guessing.
 *
 * SELECTORS BELOW MAY NEED ADJUSTING if Naukri changes its markup — same
 * caveat as JobScraper.
 */
public class ApplyBot {

    private static final String APPLY_BUTTON_SELECTOR = "#apply-button";
    private static final String CHATBOT_MODAL_SELECTOR = "div.chatbot_DrawerContentWrapper";

    public static void run() {
        Config.load();
        Path storageStatePath = Path.of("session", "storage_state.json");
        if (!Files.exists(storageStatePath)) {
            System.out.println("No saved session found. Run Login first (option: login).");
            return;
        }

        Path jobsPath = Path.of("data", "jobs.json");
        if (!Files.exists(jobsPath)) {
            System.out.println("No scraped jobs found. Run JobScraper first (option: scrape).");
            return;
        }

        // Load and parse resume profile (JSON) for intelligent answering
        Path resumeProfilePath = Path.of("config", "resume_profile.json");
        ResumeParser resumeParser = new ResumeParser(resumeProfilePath);

        List<JobListing> jobs = loadJobs(jobsPath);
        boolean nativeOnly = Config.getBool("apply.nativeOnly");
        int maxApplications = Config.getInt("pacing.maxApplicationsPerRun");
        int applied = 0;

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions()
                            .setHeadless(!Config.getBool("browser.headed"))
                            .setChannel("chrome")
            );
            BrowserContext context = browser.newContext(
                    new Browser.NewContextOptions().setStorageStatePath(storageStatePath)
            );
            Page page = context.newPage();

            for (JobListing job : jobs) {
                if (applied >= maxApplications) {
                    System.out.println("Reached pacing.maxApplicationsPerRun (" + maxApplications + "). Stopping for this run.");
                    break;
                }
                if (job.applied) continue;

                // Filter: Only apply to jobs posted within 48 hours (2 days)
                if (!isPostedWithin24Hours(job)) {
                    continue;
                }

                System.out.println("Opening: " + job);
                try {
                    page.navigate(job.jobUrl);
                    page.waitForTimeout(2500);

                    Locator applyBtn = page.locator(APPLY_BUTTON_SELECTOR).first();
                    if (applyBtn.count() == 0) {
                        System.out.println("  No apply button found — skipping.");
                        continue;
                    }

                    String buttonText = applyBtn.innerText().toLowerCase();
                    boolean looksExternal = buttonText.contains("company site");

                    if (looksExternal) {
                        job.applyType = "company_site";
                        if (nativeOnly) {
                            System.out.println("  External application (company site) — skipped (apply.nativeOnly=true). Review manually.");
                            continue;
                        }
                    } else {
                        job.applyType = "native";
                    }

                    applyBtn.click();
                    page.waitForTimeout(2000);

                    handleScreeningQuestionsIfPresent(page, resumeParser);

                    job.applied = true;
                    job.appliedAt = Instant.now().toString();
                    applied++;
                    System.out.println("  Applied.");

                } catch (Exception e) {
                    System.out.println("  Error on this job, skipping: " + e.getMessage());
                }

                saveJobs(jobs, jobsPath);
                Pacer.waitRandom();
            }

            browser.close();
        }

        System.out.println("Done. Applied to " + applied + " job(s) this run.");
        System.out.println("Full log in data/jobs.json — check 'applyType: company_site' entries for manual follow-up.");
    }

    /**
     * Intelligent handling of Naukri's screening-question chat widget using resume content
     * and smart question type detection to generate and auto-fill contextual answers.
     */
    private static void handleScreeningQuestionsIfPresent(Page page, ResumeParser resumeParser) {
        Locator modal = page.locator(CHATBOT_MODAL_SELECTOR);
        if (modal.count() == 0) return; // no screening questions for this job

        System.out.println("  Screening questions detected — auto-answering from resume...");

        ScreeningQuestionHandler handler = new ScreeningQuestionHandler(resumeParser);
        
        // Handle up to 5 questions per job (Naukri typically has 1-3 screening questions)
        for (int i = 0; i < 5; i++) {
            // Check if modal is still visible
            if (page.locator(CHATBOT_MODAL_SELECTOR).count() == 0) {
                break;
            }
            
            try {
                handler.handleQuestions(page);
                page.waitForTimeout(1000);
            } catch (Exception e) {
                System.out.println("  Could not auto-answer question " + (i + 1) + ": " + e.getMessage());
                break;
            }
        }
    }

    private static List<JobListing> loadJobs(Path path) {
        try {
            Gson gson = new Gson();
            String json = Files.readString(path);
            Type listType = new TypeToken<List<JobListing>>() {}.getType();
            return gson.fromJson(json, listType);
        } catch (Exception e) {
            throw new RuntimeException("Could not read data/jobs.json", e);
        }
    }

    private static void saveJobs(List<JobListing> jobs, Path path) {
        try {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(path, gson.toJson(jobs));
        } catch (Exception e) {
            System.out.println("  Warning: could not save progress to jobs.json: " + e.getMessage());
        }
    }

    /**
     * Check if a job was posted within the last 48 hours (2 days)
     */
    private static boolean isPostedWithin24Hours(JobListing job) {
        // If no posted timestamp, assume it's old and skip
        if (job.postedTimestamp == 0) {
            return false;
        }

        long currentTimeMillis = System.currentTimeMillis();
        long postedTimeMillis = job.postedTimestamp * 1000; // Convert seconds to milliseconds
        long ageInMillis = currentTimeMillis - postedTimeMillis;
        long fortyEightHoursInMillis = 48 * 60 * 60 * 1000; // 48 hours in milliseconds

        return ageInMillis <= fortyEightHoursInMillis;
    }

    public static void main(String[] args) {
        run();
    }
}
