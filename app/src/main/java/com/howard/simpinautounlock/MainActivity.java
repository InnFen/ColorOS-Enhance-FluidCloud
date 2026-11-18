package com.howard.simpinautounlock;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

/** Compatibility entry for old shortcuts; all SIM management now uses the unified settings UI. */
public final class MainActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startActivity(new Intent(this, dev.howard.colorosenhance.MainActivity.class)
                .putExtra("group", "sim_slots"));
        finish();
    }
}
