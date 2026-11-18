package com.howard.simpinautounlock.storage;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Objects;

public final class AndroidPreferenceStore implements PreferenceStore {
    private static final String PREFERENCE_NAME = PhoneCredentialStorage.PREFERENCES;

    private final SharedPreferences preferences;

    public AndroidPreferenceStore(Context context) {
        this(context, PREFERENCE_NAME);
    }

    public AndroidPreferenceStore(Context context, String preferenceName) {
        this(context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE));
    }

    public AndroidPreferenceStore(SharedPreferences preferences) {
        this.preferences = Objects.requireNonNull(preferences);
    }

    @Override
    public String getString(String key, String defaultValue) {
        return preferences.getString(key, defaultValue);
    }

    @Override
    public int getInt(String key, int defaultValue) {
        return preferences.getInt(key, defaultValue);
    }

    @Override
    public boolean contains(String key) {
        return preferences.contains(key);
    }

    @Override
    public Editor edit() {
        return new AndroidEditor(preferences.edit());
    }

    private static final class AndroidEditor implements Editor {
        private final SharedPreferences.Editor editor;

        private AndroidEditor(SharedPreferences.Editor editor) {
            this.editor = editor;
        }

        @Override
        public Editor putString(String key, String value) {
            editor.putString(key, value);
            return this;
        }

        @Override
        public Editor putInt(String key, int value) {
            editor.putInt(key, value);
            return this;
        }

        @Override
        public Editor remove(String key) {
            editor.remove(key);
            return this;
        }

        @Override
        public Editor clear() {
            editor.clear();
            return this;
        }

        @Override
        public boolean commit() {
            return editor.commit();
        }
    }
}
