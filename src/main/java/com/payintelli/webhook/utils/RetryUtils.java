package com.payintelli.webhook.utils;

import java.sql.Timestamp;

public class RetryUtils {
    
    // Exponential backoff delays in seconds: 1min, 5min, 15min, 1hr, 6hr
    private static final int[] RETRY_DELAYS = {60, 300, 900, 3600, 21600};
    
    public static int getRetryDelaySeconds(int attemptCount) {
        int delayIndex = Math.min(attemptCount - 1, RETRY_DELAYS.length - 1);
        return RETRY_DELAYS[delayIndex];
    }
    
    public static Timestamp calculateNextRetryTime(int attemptCount) {
        int delaySeconds = getRetryDelaySeconds(attemptCount);
        return new Timestamp(System.currentTimeMillis() + (delaySeconds * 1000L));
    }
}