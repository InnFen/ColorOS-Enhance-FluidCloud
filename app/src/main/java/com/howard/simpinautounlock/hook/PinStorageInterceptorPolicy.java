package com.howard.simpinautounlock.hook;

final class PinStorageInterceptorPolicy {
    private PinStorageInterceptorPolicy() {}

    static Object intercept(
            ThrowingIntSupplier slotSupplier,
            ThrowingIntConsumer entryTrace,
            ThrowingSupplier originalCall,
            AfterProceed postProcessor,
            FailureReporter failureReporter) throws Throwable {
        int slotId;
        try {
            slotId = slotSupplier.getAsInt();
            entryTrace.accept(slotId);
        } catch (Throwable failure) {
            reportBestEffort(failureReporter, failure);
            return originalCall.get();
        }

        Object originalObject = originalCall.get();
        try {
            return postProcessor.apply(slotId, originalObject);
        } catch (Throwable failure) {
            reportBestEffort(failureReporter, failure);
            return originalObject;
        }
    }

    private static void reportBestEffort(
            FailureReporter failureReporter, Throwable failure) {
        try {
            failureReporter.accept(failure);
        } catch (Throwable ignored) {
            // Diagnostics must never affect the intercepted framework method.
        }
    }

    @FunctionalInterface
    interface ThrowingIntSupplier {
        int getAsInt() throws Throwable;
    }

    @FunctionalInterface
    interface ThrowingIntConsumer {
        void accept(int value) throws Throwable;
    }

    @FunctionalInterface
    interface ThrowingSupplier {
        Object get() throws Throwable;
    }

    @FunctionalInterface
    interface AfterProceed {
        Object apply(int slotId, Object originalObject) throws Throwable;
    }

    @FunctionalInterface
    interface FailureReporter {
        void accept(Throwable failure) throws Throwable;
    }
}
