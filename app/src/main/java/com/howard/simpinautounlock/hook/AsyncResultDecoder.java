package com.howard.simpinautounlock.hook;

import java.lang.reflect.Field;

public final class AsyncResultDecoder {
    public record Decoded(boolean readable, boolean success, int remainingAttempts) {}

    public Decoded decode(Object asyncResult) {
        if (asyncResult == null) {
            return new Decoded(false, false, -1);
        }
        try {
            Field exceptionField = asyncResult.getClass().getDeclaredField("exception");
            Field resultField = asyncResult.getClass().getDeclaredField("result");
            exceptionField.setAccessible(true);
            resultField.setAccessible(true);
            boolean success = exceptionField.get(asyncResult) == null;
            Object result = resultField.get(asyncResult);
            int remainingAttempts = -1;
            if (result instanceof int[] values && values.length > 0) {
                remainingAttempts = values[0];
            } else if (result instanceof Integer value) {
                remainingAttempts = value;
            }
            return new Decoded(true, success, remainingAttempts);
        } catch (Throwable error) {
            return new Decoded(false, false, -1);
        }
    }
}
