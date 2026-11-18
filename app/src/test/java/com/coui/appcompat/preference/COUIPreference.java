package com.coui.appcompat.preference;

/** Host-owned card preference in the test Settings classloader. */
public class COUIPreference extends android.preference.Preference {
    private boolean visible=true;
    public void setVisible(boolean value){visible=value;}
    public boolean isVisible(){return visible;}
    public COUIPreference(android.content.Context context) { super(context); }
}
