package com.oplus.settings.widget.preference;
public class SettingsSimpleJumpPreference extends android.preference.Preference {
    private boolean visible=true;
    public void setVisible(boolean value){visible=value;}
    public boolean isVisible(){return visible;}
    public SettingsSimpleJumpPreference(android.content.Context context) {
        super(context);
        // Firmware's normal jump row has no android.R.id.summary child.
        setLayoutResource(context.getResources().getIdentifier("preference_category","layout","android"));
    }
}
