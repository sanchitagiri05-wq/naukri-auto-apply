package com.jobapply;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jobapply.util.Config;
import com.jobapply.util.Pacer;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

/**
 * STEP 2: Run after Login.
 *
 * Reuses the saved session, searches Naukri for each keyword in config,
 * and scrapes basic listing info into data/jobs.json.
 *
 * IMPORTANT: Naukri's HTML structure changes periodically. The CSS selectors
 * below (JOB_CARD_SELECTOR etc.) are current as of this writing but may need
 * adjusting — if a run finds 0 jobs, open Naukri in a normal browser, right-click
 * a job card -> Inspect, and update the selectors accordingly.
 */
public class JobScraper {

    // ---- Selectors: adjust here if Naukri's markup changes ----
    private static final String JOB_CARD_SELECTOR = "div.cust-job-tuple";
    private static final String TITLE_SELECTOR = "a.title";
    private static final String COMPANY_SELECTOR = "a.comp-name";
    private static final String LOCATION_SELECTOR = "span.locWdth";
    private static final String POSTED_DATE_SELECTOR = "span.job-posted-date, span.dt-posted";
    // For fallback: look for "Posted" text in the card
    private static final String POSTED_TEXT_SELECTOR = "div.jd-info-item";
    // -------------------------------------------------------------

    public static void run() {
        Config.load();
        Path storageStatePath = Path.of("session", "storage_state.json");
        if (!Files.exists(storageStatePath)) {
            System.out.println("No saved session found. Run Login first (option: login).");
            return;
        }

        List<JobListing> allJobs = new ArrayList<>();
        List<String> keywords = Config.getList("search.keywords");
        String location = Config.get("search.location", "");
        int maxPerKeyword = Config.getInt("search.maxResultsPerKeyword");

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

            for (String keyword : keywords) {
                System.out.println("Searching: " + keyword + (location.isEmpty() ? "" : " in " + location));
                String url = buildSearchUrl(keyword, location);
                page.navigate(url);
                page.waitForTimeout(3000);

                Locator cards = page.locator(JOB_CARD_SELECTOR);
                int count = Math.min(cards.count(), maxPerKeyword);
                System.out.println("  Found " + cards.count() + " listings, scraping up to " + count);

                for (int i = 0; i < count; i++) {
                    try {
                        Locator card = cards.nth(i);
                        Locator titleEl = card.locator(TITLE_SELECTOR).first();
                        String title = titleEl.innerText().trim();
                        String jobUrl = titleEl.getAttribute("href");
                        String company = safeText(card.locator(COMPANY_SELECTOR).first());
                        String loc = safeText(card.locator(LOCATION_SELECTOR).first());

                        JobListing job = new JobListing(title, company, loc, jobUrl, "unknown");
                        
                        // Extract posted date
                        String postedDate = extractPostedDate(card);
                        job.postedDate = postedDate;
                        job.postedTimestamp = parsePostedDateToTimestamp(postedDate);
                        
                        allJobs.add(job);
                    } catch (Exception e) {
                        System.out.println("  (skipped one listing — couldn't parse it: " + e.getMessage() + ")");
                    }
                }

                Pacer.waitRandom();
            }

            browser.close();
        }

        saveJobs(allJobs);
        System.out.println("Saved " + allJobs.size() + " jobs to data/jobs.json");
    }

    private static String buildSearchUrl(String keyword, String location) {
        String kw = keyword.trim().toLowerCase().replace(" ", "-");
        StringBuilder url = new StringBuilder("https://www.naukri.com/")
                .append(kw).append("-jobs");
        if (!location.isEmpty()) {
            url.append("-in-").append(location.trim().toLowerCase().replace(" ", "-"));
        }
        return url.toString();
    }

    private static String safeText(Locator locator) {
        try {
            return locator.innerText().trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static void saveJobs(List<JobListing> jobs) {
        try {
            Files.createDirectories(Path.of("data"));
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(Path.of("data", "jobs.json"), gson.toJson(jobs));
        } catch (Exception e) {
            throw new RuntimeException("Could not save jobs.json", e);
        }
    }

    /**
     * Extract posted date string from job card (e.g., "3 days ago", "Posted on 01-Aug-2026")
     */
    private static String extractPostedDate(Locator card) {
        try {
            // Try primary selectors
            String[] selectors = {
                POSTED_DATE_SELECTOR,
                "span.dt-posted",
                "span.posted-date",
                "div.posted-date",
                "span[data-test*='posted']",
                "span:text-matches('ago')",
                "span:text-matches('days')",
                "span:text-matches('hours')"
            };

            for (String selector : selectors) {
                try {
                    Locator dateEl = card.locator(selector);
                    if (dateEl.count() > 0) {
                        String text = dateEl.first().innerText().trim();
                        if (!text.isEmpty() && (text.contains("ago") || text.contains("day") || text.contains("hour") || text.contains("Posted"))) {
                            return text;
                        }
                    }
                } catch (Exception e) {
                    // Try next selector
                }
            }

            // Fallback: scan all text in card for date patterns
            try {
                String cardText = card.innerText();
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(".*(\\d+\\s+(?:days?|hours?)\\s+ago).*", java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL);
                java.util.regex.Matcher matcher = pattern.matcher(cardText);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            } catch (Exception e) {
                // Fall through
            }

            return "";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Parse posted date string to Unix timestamp (seconds)
     * Handles formats like "3 days ago", "2 hours ago", "Posted on 01-Aug-2026"
     */
    private static long parsePostedDateToTimestamp(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return 0;
        }

        try {
            String lowerDate = dateStr.toLowerCase();
            long currentTimeSeconds = System.currentTimeMillis() / 1000;

            // Parse "X weeks ago" format (note: check weeks before days)
            java.util.regex.Pattern weeksPattern = java.util.regex.Pattern.compile("(\\d+)\\s+weeks?\\s+ago");
            java.util.regex.Matcher weeksMatcher = weeksPattern.matcher(lowerDate);
            if (weeksMatcher.find()) {
                long weeksAgo = Long.parseLong(weeksMatcher.group(1));
                return currentTimeSeconds - (weeksAgo * 7 * 24 * 60 * 60);
            }

            // Parse "X days ago" format
            java.util.regex.Pattern daysPattern = java.util.regex.Pattern.compile("(\\d+)\\s+days?\\s+ago");
            java.util.regex.Matcher daysMatcher = daysPattern.matcher(lowerDate);
            if (daysMatcher.find()) {
                long daysAgo = Long.parseLong(daysMatcher.group(1));
                return currentTimeSeconds - (daysAgo * 24 * 60 * 60);
            }

            // Parse "X hours ago" format
            java.util.regex.Pattern hoursPattern = java.util.regex.Pattern.compile("(\\d+)\\s+hours?\\s+ago");
            java.util.regex.Matcher hoursMatcher = hoursPattern.matcher(lowerDate);
            if (hoursMatcher.find()) {
                long hoursAgo = Long.parseLong(hoursMatcher.group(1));
                return currentTimeSeconds - (hoursAgo * 60 * 60);
            }

            // If no pattern matches, assume it's too old and return 0
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public static void main(String[] args) {
        run();
    }
}
