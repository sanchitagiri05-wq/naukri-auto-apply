package com.jobapply.util;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Adds randomized, human-like delays between actions.
 * Deliberately avoids fast/bursty behavior — this is the main thing that
 * keeps automation from looking like a bot to detection systems.
 */
public class Pacer {

    public static void waitRandom() {
        int min = Config.getInt("pacing.minDelaySeconds");
        int max = Config.getInt("pacing.maxDelaySeconds");
        int seconds = ThreadLocalRandom.current().nextInt(min, max + 1);
        System.out.println("  ...pausing " + seconds + "s (human-like pacing)");
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
