package com.howard.simpinautounlock.hook;

final class PinStateInterceptorPolicy {
    private PinStateInterceptorPolicy() {}

    static Object intercept(DeferralCheck check, FrameworkCall original) throws Throwable {
        boolean deferred = false;
        try {
            deferred = check.shouldDefer();
        } catch (Throwable ignored) {
            // A hook-side failure must leave the native state publisher available.
        }
        return deferred ? null : original.proceed();
    }

    static boolean refreshIfActive(
            Object profileLock, DeferralCheck isDisposed, FrameworkCall refresh) throws Throwable {
        synchronized (profileLock) {
            if (isDisposed.shouldDefer()) {
                return false;
            }
            refresh.proceed();
            return true;
        }
    }

    @FunctionalInterface interface DeferralCheck {
        boolean shouldDefer() throws Throwable;
    }

    @FunctionalInterface interface FrameworkCall {
        Object proceed() throws Throwable;
    }
}
