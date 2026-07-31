package com.jobapply.util;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * Intelligent screening question handler that detects question types,
 * generates contextual answers based on resume and config, and auto-fills them.
 */
public class ScreeningQuestionHandler {

    private final ResumeParser resumeParser;
    private final String noticePeriod;
    private final String currentCTC;
    private final String expectedCTC;
    private final String experience;

    public ScreeningQuestionHandler(ResumeParser resumeParser) {
        this.resumeParser = resumeParser;
        this.noticePeriod = Config.get("answers.noticePeriodDays");
        this.currentCTC = Config.get("answers.currentCtcLakhs");
        this.expectedCTC = Config.get("answers.expectedCtcLakhs");
        this.experience = Config.get("answers.totalExperienceYears");
    }

    /**
     * Handle screening questions by analyzing the question and auto-filling answers
     */
    public void handleQuestions(Page page) {
        try {
            // Wait briefly for the modal to settle
            page.waitForTimeout(1500);

            // Try to find question text in common locations
            String questionText = extractQuestionText(page);
            if (questionText.isEmpty()) {
                System.out.println("  Could not extract question text — skipping auto-answer.");
                return;
            }

            String answer = generateAnswer(questionText);
            if (answer.isEmpty()) {
                System.out.println("  Could not generate answer for: " + questionText);
                return;
            }

            // Try to find and fill the input field
            if (fillAnswer(page, answer)) {
                System.out.println("  Auto-answered: " + questionText.substring(0, Math.min(50, questionText.length())) + "...");
            }
        } catch (Exception e) {
            System.out.println("  Error handling screening questions: " + e.getMessage());
        }
    }

    /**
     * Extract the question text from the modal
     */
    private String extractQuestionText(Page page) {
        try {
            // Try common selectors for Naukri's chatbot modal
            String[] selectors = {
                    "div.chatbot_DrawerContentWrapper div.chatbot_Question",
                    "div.chatbot_DrawerContentWrapper p",
                    "div.chatbot_DrawerContentWrapper span",
                    "div[class*='question']",
                    "div[class*='Question']"
            };

            for (String selector : selectors) {
                try {
                    Locator locator = page.locator(selector);
                    if (locator.count() > 0) {
                        String text = locator.first().innerText();
                        if (!text.isEmpty() && text.length() > 5) {
                            return text.trim();
                        }
                    }
                } catch (Exception e) {
                    // Continue to next selector
                }
            }
        } catch (Exception e) {
            // Fall back to empty question
        }
        return "";
    }

    /**
     * Generate an intelligent answer based on the question type
     */
    private String generateAnswer(String question) {
        String lowerQ = question.toLowerCase();

        // Notice period questions
        if (lowerQ.contains("notice") || lowerQ.contains("notice period") ||
                lowerQ.contains("lwd") || lowerQ.contains("last working day")) {
            return noticePeriod + " days";
        }

        // Current CTC questions
        if (lowerQ.contains("current ctc") || lowerQ.contains("current salary")) {
            return currentCTC + " LPA";
        }

        // Expected CTC questions
        if (lowerQ.contains("expected ctc") || lowerQ.contains("expected salary")) {
            return expectedCTC + " LPA";
        }

        // Experience questions
        if (lowerQ.contains("experience") || lowerQ.contains("years of")) {
            return experience + " years";
        }

        // Why do you want to join?
        if (lowerQ.contains("why") && (lowerQ.contains("join") || lowerQ.contains("interested"))) {
            return generateWhyJoinAnswer(question);
        }

        // Relevant skills/experience
        if (lowerQ.contains("relevant") && (lowerQ.contains("experience") || lowerQ.contains("skills"))) {
            return generateRelevantExperienceAnswer();
        }

        // Are you available/can you start?
        if (lowerQ.contains("available") || lowerQ.contains("start") || lowerQ.contains("join us")) {
            return "Yes, I am available to join immediately or as per the notice period.";
        }

        return "";
    }

    /**
     * Generate a contextual "Why do you want to join?" answer
     */
    private String generateWhyJoinAnswer(String question) {
        StringBuilder answer = new StringBuilder();
        answer.append("I am interested in this opportunity because of my ");
        answer.append(resumeParser.getExperience()).append(" years of experience in QA automation and SDET roles. ");
        answer.append("I have strong expertise in ").append(resumeParser.getSkills()).append(". ");
        answer.append("I am excited to contribute my skills to this team and grow with the organization.");
        return answer.toString();
    }

    /**
     * Generate a contextual answer about relevant experience
     */
    private String generateRelevantExperienceAnswer() {
        StringBuilder answer = new StringBuilder();
        answer.append("Yes, I have ").append(resumeParser.getExperience()).append(" years of relevant experience in ");
        String domain = resumeParser.getDomain();
        if (!domain.isEmpty()) {
            answer.append("the ").append(domain).append(" domain. ");
        }
        answer.append("My key skills include: ").append(resumeParser.getSkills()).append(". ");
        if (!resumeParser.getCertifications().isEmpty()) {
            answer.append("I also hold certifications in ").append(resumeParser.getCertifications()).append(".");
        }
        return answer.toString();
    }

    /**
     * Attempt to fill the answer into an input field and submit
     */
    private boolean fillAnswer(Page page, String answer) {
        try {
            // Try common input selectors
            String[] inputSelectors = {
                    "textarea",
                    "input[type='text']",
                    "div[contenteditable='true']",
                    "input.chatbot_Input",
                    "input[class*='input']",
                    "textarea[class*='input']"
            };

            for (String selector : inputSelectors) {
                try {
                    Locator input = page.locator(selector);
                    if (input.count() > 0) {
                        input.first().fill(answer);
                        page.waitForTimeout(500);

                        // Try to find and click submit button
                        String[] submitSelectors = {
                                "button[type='submit']",
                                "button.chatbot_SendButton",
                                "button:has-text('Send')",
                                "button:has-text('Submit')",
                                "button:has-text('Next')"
                        };

                        for (String submitSelector : submitSelectors) {
                            try {
                                Locator submitBtn = page.locator(submitSelector);
                                if (submitBtn.count() > 0) {
                                    submitBtn.first().click();
                                    page.waitForTimeout(1500);
                                    return true;
                                }
                            } catch (Exception e) {
                                // Continue to next submit selector
                            }
                        }
                        return true; // Input was filled even if submit not found
                    }
                } catch (Exception e) {
                    // Continue to next input selector
                }
            }
        } catch (Exception e) {
            System.out.println("  Error filling answer: " + e.getMessage());
        }
        return false;
    }
}
