package com.jobapply;

/**
 * Represents one scraped job listing from Naukri search results.
 */
public class JobListing {
    public String title;
    public String company;
    public String location;
    public String jobUrl;
    public String applyType;   // "native" or "company_site" or "unknown"
    public String postedDate;  // Date string from Naukri (e.g., "3 days ago", "Posted on 01-Aug-2026")
    public long postedTimestamp; // Unix timestamp for comparison
    public boolean applied;
    public String appliedAt;   // ISO timestamp, set when applied

    public JobListing() {}

    public JobListing(String title, String company, String location, String jobUrl, String applyType) {
        this.title = title;
        this.company = company;
        this.location = location;
        this.jobUrl = jobUrl;
        this.applyType = applyType;
        this.postedDate = "";
        this.postedTimestamp = 0;
        this.applied = false;
        this.appliedAt = null;
    }

    @Override
    public String toString() {
        return title + " @ " + company + " (" + location + ") [" + applyType + "]";
    }
}

