package com.howard.simpinautounlock.hook;

import android.os.Handler;
import com.howard.simpinautounlock.core.TimeoutScheduler;

public final class HandlerTimeoutScheduler implements TimeoutScheduler {
    private final Handler handler;

    public HandlerTimeoutScheduler(Handler handler) {
        this.handler = handler;
    }

    @Override
    public boolean schedule(Runnable task, long delayMillis) {
        return handler.postDelayed(task, delayMillis);
    }
}
