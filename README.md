# Naukri Auto-Apply (Playwright + Java)

A starter project to search and apply to jobs on Naukri.com using Playwright,
with human-like pacing and manual OTP login (this does not attempt to bypass
OTP or any security step).

## ⚠️ Before you use this

- This is **unofficial automation** — Naukri does not provide a public API or
  official auto-apply feature. Automating a site's UI may be against its
  Terms of Service. You're responsible for how you use this.
- Keep volume and speed conservative. The config defaults are deliberately
  cautious (human-like delays, a per-run application cap well under Naukri's
  own daily limit). Don't crank these up — burst applying is what gets
  accounts flagged.
- The screening-question auto-answer logic (`ApplyBot.handleScreeningQuestionsIfPresent`)
  is a **stub**, not a finished feature — it pauses and lets you answer by
  hand in the open browser window. Wiring it up fully needs testing against
  real question text, which varies per job.
- Naukri's HTML changes periodically. If scraping/applying stops working,
  the CSS selectors at the top of `JobScraper.java` and `ApplyBot.java` are
  the first place to check — inspect a real page in Chrome DevTools and
  update them.

## Requirements

- Java 25+
- Maven
- Playwright browsers (installed automatically the first time, see below)

## Setup

```bash
cd naukri-auto-apply
mvn compile

# Install Playwright's bundled browser (one-time)
mvn exec:java -Dexec.mainClass="com.microsoft.playwright.CLI" -Dexec.args="install chromium"
```

1. Put your resume file in `resume/` (see the note in that folder).
2. Open `config/config.properties` and fill in:
   - Search keywords and location
   - Your real notice period / current CTC / expected CTC (used to answer
     screening questions — not guessed by AI)
   - Pacing limits (leave conservative to start)

## Usage

Run these in order:

```bash
# 1. Log in (opens a real browser window — you log in and complete OTP yourself)
mvn exec:java -Dexec.args="login"

# 2. Search Naukri and scrape matching jobs into data/jobs.json
mvn exec:java -Dexec.args="scrape"

# 3. Apply to scraped jobs (native "Apply" listings only, by default)
mvn exec:java -Dexec.args="apply"
```

Or build a runnable jar:

```bash
mvn package
java -jar target/naukri-auto-apply.jar login
java -jar target/naukri-auto-apply.jar scrape
java -jar target/naukri-auto-apply.jar apply
```

## Project structure

```
naukri-auto-apply/
├── resume/                  <- put your resume file here
├── config/
│   └── config.properties    <- your search terms, screening answers, pacing
├── session/
│   └── storage_state.json   <- created after `login`, reused by scrape/apply
├── data/
│   └── jobs.json            <- created by `scrape`, updated by `apply`
└── src/main/java/com/jobapply/
    ├── Main.java             <- CLI entry point
    ├── Login.java            <- Step 1: manual login + OTP, saves session
    ├── JobScraper.java       <- Step 2: searches + scrapes listings
    ├── ApplyBot.java         <- Step 3: applies to native listings
    ├── JobListing.java       <- data model for a scraped job
    └── util/
        ├── Config.java       <- reads config.properties
        └── Pacer.java        <- randomized human-like delays
```

## What this does NOT do (yet)

- Apply to "Apply on company site" (off-platform ATS) listings — these are
  logged in `data/jobs.json` with `applyType: "company_site"` for you to
  review and apply to manually, since every company's form is different.
- Fully auto-answer screening questions — it pauses and waits for you.
- Tailor your resume per job — it uses whatever's on your Naukri profile.
- Guarantee you won't get rate-limited or flagged — pacing reduces risk,
  it doesn't eliminate it.

## Reasonable next steps

- Get `login` → `scrape` working first and just review `data/jobs.json` —
  don't touch `apply` until you trust the matches it's finding.
- Once comfortable, test `apply` with a very small `pacing.maxApplicationsPerRun`
  (e.g. 2–3) and watch the browser window the whole time.
- Add resume-parsing + LLM-based job matching as a filter step between
  `scrape` and `apply`, so it's not applying to everything it finds.
