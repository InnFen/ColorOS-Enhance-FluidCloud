package com.howard.simpinautounlock.storage;

public interface PreferenceStore {
    String getString(String key, String defaultValue);

    int getInt(String key, int defaultValue);

    boolean contains(String key);

    Editor edit();

    interface Editor {
        Editor putString(String key, String value);

        Editor putInt(String key, int value);

        Editor remove(String key);

        Editor clear();

        boolean commit();
    }
}
