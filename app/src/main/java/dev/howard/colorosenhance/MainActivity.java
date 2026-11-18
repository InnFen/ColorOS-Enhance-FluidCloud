package dev.howard.colorosenhance;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

/** Activity boundaries retain the platform's predictive back and each page's saved scroll state. */
public final class MainActivity extends AppCompatActivity {
    private EnhanceApplication app;
    private String selected;
    private Runnable redraw=()->{};
    private boolean resumed;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        app=(EnhanceApplication)getApplication();
        selected=state==null?getIntent().getStringExtra("group"):state.getString("group");
        selected=normalizeRoute(selected);
        Runnable render=MiuixSettings.install(this,selected);
        redraw=()->{if(canShowUpdatePrompt())app.launchUpdates().check();render.run();};
    }
    @Override public void onStart(){super.onStart();app.observe(redraw);app.migration().foreground(this);app.settings().refresh();redraw.run();}
    @Override protected void onResume(){super.onResume();resumed=true;redraw.run();}
    @Override protected void onPause(){resumed=false;redraw.run();super.onPause();}
    @Override public void onStop(){app.migration().background(this);app.removeObserver(redraw);super.onStop();}
    public boolean canShowUpdatePrompt(){
        var phase=app.migration().phase();
        return resumed && !isFinishing() && (phase==dev.howard.colorosenhance.migration.MigrationController.Phase.IDLE
                || phase==dev.howard.colorosenhance.migration.MigrationController.Phase.COMPLETE);
    }
    @Override protected void onSaveInstanceState(Bundle state){state.putString("group",selected);super.onSaveInstanceState(state);}
    public static String normalizeRoute(String route){return SettingsLayout.known(route)?route:"home";}
}
