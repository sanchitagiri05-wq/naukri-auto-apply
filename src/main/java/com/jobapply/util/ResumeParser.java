package com.jobapply.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * Loads resume profile from a JSON file to build a knowledge base
 * for answering screening questions contextually based on your actual background.
 *
 * Expected JSON structure in resume_profile.json:
 * {
 *   "name": "Your Name",
 *   "currentRole": "QA Automation Engineer",
 *   "experience": "4",
 *   "skills": ["Selenium", "Java", "TestNG", "Playwright", "API Testing"],
 *   "projects": ["Project 1 description", "Project 2 description"],
 *   "certifications": ["ISTQB", "AWS Certified"],
 *   "domain": "E-commerce, FinTech"
 * }
 */
public class ResumeParser {

    private JsonObject resumeProfile;
    private Map<String, String> extractedData;

    public ResumeParser(Path profilePath) {
        this.extractedData = new HashMap<>();
        this.resumeProfile = loadResumeProfile(profilePath);
        parseResumeContent();
    }

    /**
     * Load resume profile from JSON file
     */
    private JsonObject loadResumeProfile(Path profilePath) {
        try {
            if (!Files.exists(profilePath)) {
                System.out.println("Warning: Resume profile file not found at " + profilePath);
                System.out.println("Please create resume_profile.json in the config folder with your details.");
                return new JsonObject();
            }

            String jsonContent = Files.readString(profilePath);
            return new Gson().fromJson(jsonContent, JsonObject.class);
        } catch (Exception e) {
            System.out.println("Warning: Failed to parse resume profile: " + e.getMessage());
            return new JsonObject();
        }
    }

    /**
     * Parse the resume profile to extract structured information
     */
    private void parseResumeContent() {
        try {
            if (resumeProfile.has("name")) {
                extractedData.put("name", resumeProfile.get("name").getAsString());
            }
            if (resumeProfile.has("currentRole")) {
                extractedData.put("currentRole", resumeProfile.get("currentRole").getAsString());
            }
            if (resumeProfile.has("experience")) {
                extractedData.put("experience", resumeProfile.get("experience").getAsString());
            }
            if (resumeProfile.has("skills")) {
                extractedData.put("skills", resumeProfile.get("skills").toString());
            }
            if (resumeProfile.has("projects")) {
                extractedData.put("projects", resumeProfile.get("projects").toString());
            }
            if (resumeProfile.has("certifications")) {
                extractedData.put("certifications", resumeProfile.get("certifications").toString());
            }
            if (resumeProfile.has("domain")) {
                extractedData.put("domain", resumeProfile.get("domain").toString());
            }
        } catch (Exception e) {
            System.out.println("Warning: Error parsing resume profile: " + e.getMessage());
        }
    }

    public String getExtractedData(String key) {
        return extractedData.getOrDefault(key, "");
    }

    public String getSkills() {
        return extractedData.getOrDefault("skills", "");
    }

    public String getExperience() {
        return extractedData.getOrDefault("experience", "");
    }

    public String getCertifications() {
        return extractedData.getOrDefault("certifications", "");
    }

    public String getDomain() {
        return extractedData.getOrDefault("domain", "");
    }

    public String getCurrentRole() {
        return extractedData.getOrDefault("currentRole", "");
    }

    public JsonObject getFullProfile() {
        return resumeProfile;
    }

    public boolean isEmpty() {
        return resumeProfile.size() == 0;
    }
}
