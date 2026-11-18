package com.howard.simpinautounlock.hook;

import android.os.Handler;
import android.os.Message;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

public final class PinResultHookInstaller {
    private static final String UICC_PROFILE =
            "com.android.internal.telephony.uicc.UiccProfile";
    private static final Set<Method> INSTALLED_METHODS = new HashSet<>();

    private PinResultHookInstaller() {}

    @android.annotation.SuppressLint("BlockedPrivateApi")
    public static synchronized void install(XposedModule module, ClassLoader classLoader) {
        Class<?> profileClass;
        Field phoneIdField;
        int supplyPinDoneEvent;
        try {
            profileClass = Class.forName(UICC_PROFILE, false, classLoader);
            OptionalInt resolvedEvent = PinResultEventResolver.resolve(profileClass);
            if (resolvedEvent.isEmpty()) {
                throw new NoSuchFieldException("supply PIN result event unavailable");
            }
            supplyPinDoneEvent = resolvedEvent.getAsInt();
            phoneIdField = profileClass.getDeclaredField("mPhoneId");
            phoneIdField.setAccessible(true);
        } catch (Throwable error) {
            SafeLog.failure("PIN_RESULT_METADATA_MISSING");
            return;
        }

        List<Candidate> candidates = new ArrayList<>();
        for (int index = 1; index <= 16; index++) {
            Class<?> candidateClass;
            try {
                candidateClass = Class.forName(UICC_PROFILE + "$" + index, false, classLoader);
            } catch (Throwable error) {
                continue;
            }
            if (!Handler.class.isAssignableFrom(candidateClass)) {
                continue;
            }

            Method handleMessage;
            try {
                handleMessage = candidateClass.getDeclaredMethod("handleMessage", Message.class);
                handleMessage.setAccessible(true);
            } catch (Throwable error) {
                continue;
            }

            Field outerField = null;
            for (Field field : candidateClass.getDeclaredFields()) {
                if (field.isSynthetic() && field.getType() == profileClass) {
                    outerField = field;
                    break;
                }
            }
            if (outerField == null) {
                continue;
            }
            try {
                outerField.setAccessible(true);
            } catch (Throwable error) {
                continue;
            }
            candidates.add(new Candidate(
                    index,
                    handleMessage,
                    outerField,
                    phoneIdField,
                    supplyPinDoneEvent));
        }
        if (candidates.isEmpty()) {
            SafeLog.failure("PIN_RESULT_HANDLER_MISSING");
            return;
        }

        for (Candidate candidate : candidates) {
            if (INSTALLED_METHODS.contains(candidate.handleMessage())) {
                continue;
            }
            try {
                module.hook(candidate.handleMessage())
                        .setId("dev.howard.colorosenhance.sim:pin-result-" + candidate.index())
                        .intercept(chain -> {
                            Object original = chain.proceed();
                            try {
                                Message message = (Message) chain.getArg(0);
                                if (message == null || message.what != candidate.event()) {
                                    return original;
                                }
                                Object profile = candidate.outerField().get(chain.getThisObject());
                                int slotId = ((Number) candidate.phoneIdField().get(profile)).intValue();
                                AsyncResultDecoder.Decoded decoded =
                                        new AsyncResultDecoder().decode(message.obj);
                                if (!decoded.readable()) {
                                    PinStateHookInstaller.onSupplyResult(slotId, false);
                                    SafeLog.failure("PIN_RESULT_DECODE_FAILED");
                                    return original;
                                }
                                PinStateHookInstaller.onSupplyResult(slotId, decoded.success());
                                PhoneRuntime runtime = PhoneRuntime.current();
                                if (runtime == null) {
                                    SafeLog.failure("PHONE_RUNTIME_UNAVAILABLE");
                                    return original;
                                }
                                runtime.coordinator().onSupplyResult(
                                        slotId, decoded.success(), decoded.remainingAttempts());
                            } catch (Throwable error) {
                                SafeLog.failure("PIN_RESULT_INTERCEPT_FAILED");
                            }
                            return original;
                        });
                INSTALLED_METHODS.add(candidate.handleMessage());
                SafeLog.hookInstalled("pin-result-" + candidate.index());
            } catch (Throwable error) {
                SafeLog.failure("PIN_RESULT_HOOK_FAILED");
            }
        }
    }

    private record Candidate(
            int index,
            Method handleMessage,
            Field outerField,
            Field phoneIdField,
            int event) {}
}
