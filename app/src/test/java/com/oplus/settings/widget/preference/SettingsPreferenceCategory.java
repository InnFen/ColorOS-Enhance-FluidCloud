package com.oplus.settings.widget.preference;
public class SettingsPreferenceCategory extends android.preference.PreferenceCategory {
    private boolean visible=true;
    public void setVisible(boolean value) { visible=value; }
    public boolean isVisible() { return visible; }
    public SettingsPreferenceCategory(android.content.Context context) { super(context); }
}
