package com.howard.simpinautounlock.ipc;

import java.lang.reflect.Method;
import java.util.Optional;

public final class PhoneFactorySimIdentityProvider implements SimIdentityProvider {
    private static final String PHONE_FACTORY_CLASS =
            "com.android.internal.telephony.PhoneFactory";
    private static final String PHONE_CLASS = "com.android.internal.telephony.Phone";
    private static final String ICC_RECORDS_CLASS =
            "com.android.internal.telephony.uicc.IccRecords";

    private final ClassLoader classLoader;

    public PhoneFactorySimIdentityProvider() {
        this(null);
    }

    public PhoneFactorySimIdentityProvider(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    public Optional<String> currentIccid(int slotId) {
        try {
            Class<?> phoneFactory = loadClass(PHONE_FACTORY_CLASS);
            Method getPhone = phoneFactory.getMethod("getPhone", int.class);
            Object phone = getPhone.invoke(null, slotId);
            if (phone == null) {
                return Optional.empty();
            }

            Method getIccRecords = loadClass(PHONE_CLASS).getMethod("getIccRecords");
            Object records = getIccRecords.invoke(phone);
            if (records == null) {
                return Optional.empty();
            }

            Method getFullIccId = loadClass(ICC_RECORDS_CLASS).getMethod("getFullIccId");
            Object iccid = getFullIccId.invoke(records);
            return iccid instanceof String ? Optional.of((String) iccid) : Optional.empty();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            return Optional.empty();
        }
    }

    private Class<?> loadClass(String name) throws ClassNotFoundException {
        return classLoader == null
                ? Class.forName(name)
                : Class.forName(name, false, classLoader);
    }
}
