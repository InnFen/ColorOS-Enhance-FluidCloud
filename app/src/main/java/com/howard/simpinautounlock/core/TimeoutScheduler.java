package com.howard.simpinautounlock.core;

public interface TimeoutScheduler {
    boolean schedule(Runnable task, long delayMillis);
}
