package com.coui.appcompat.preference;
public class COUISwitchPreference extends android.preference.SwitchPreference {
    private boolean visible = true;
    public COUISwitchPreference(android.content.Context context) { super(context); }
    public void setVisible(boolean value) { visible = value; }
    public boolean isVisible() { return visible; }
}
