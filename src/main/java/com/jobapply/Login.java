package com.jobapply;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;

import com.jobapply.util.Config;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

/**
 * STEP 1: Run this first.
 *
 * Opens a real, visible Chrome window pointed at Naukri's login page.
 * You log in and complete the OTP yourself (this script does NOT attempt
 * to bypass OTP — that's intentional). Once you're logged in and can see
 * your Naukri homepage/dashboard, come back to the terminal and press ENTER.
 *
 * Your session is then saved to session/storage_state.json so later steps
 * (JobScraper, ApplyBot) can reuse it without you logging in again, until
 * the session naturally expires.
 */
public class Login {

    public static void run() {
        Config.load();
        Path sessionDir = Path.of("session");
        Path storageStatePath = sessionDir.resolve("storage_state.json");

        try {
            Files.createDirectories(sessionDir);
        } catch (Exception e) {
            throw new RuntimeException("Could not create session/ directory", e);
        }

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions()
                            .setHeadless(false)
                            .setChannel("chrome")
            );
            BrowserContext context = browser.newContext();
            Page page = context.newPage();

            System.out.println("Opening Naukri login page...");
            page.navigate("https://www.naukri.com/nlogin/login");

            System.out.println();
            System.out.println("=================================================================");
            System.out.println(" ACTION NEEDED:");
            System.out.println(" 1. Log in normally in the browser window that just opened.");
            System.out.println(" 2. Complete the OTP step yourself.");
            System.out.println(" 3. Once you can see your Naukri dashboard/homepage, come back");
            System.out.println("    here and press ENTER.");
            System.out.println("=================================================================");
            new Scanner(System.in).nextLine();

            context.storageState(new BrowserContext.StorageStateOptions().setPath(storageStatePath));
            System.out.println("Session saved to " + storageStatePath.toAbsolutePath());
            System.out.println("You can now run: scrape  and later: apply");

            browser.close();
        }
    }

    public static void main(String[] args) {
        run();
    }
}
