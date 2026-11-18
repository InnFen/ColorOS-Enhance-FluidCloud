package dev.pixellock.coloros;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import io.github.libxposed.service.XposedService;

/** Plain settings screen. Changes apply live in SystemUI through LSPosed remote preferences. */
public final class MainActivity extends Activity {
    private LinearLayout list;
    private TextView status;
    private final Runnable refresh = () -> runOnUiThread(this::build);

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        scroll.setFitsSystemWindows(true);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        list.setPadding(pad, pad, pad, pad);
        scroll.addView(list);
        setContentView(scroll);
        ((App) getApplication()).observe(refresh);
        build();
    }

    @Override protected void onDestroy() {
        ((App) getApplication()).unobserve(refresh);
        super.onDestroy();
    }

    private void build() {
        list.removeAllViews();
        TextView title = text("Pixel 鎖屏", 26, true);
        list.addView(title);
        TextView version = text("版本 " + versionName(), 13, false);
        version.setAlpha(0.65f);
        list.addView(version);
        status = text("", 14, false);
        list.addView(status);

        XposedService service = ((App) getApplication()).service();
        if (service == null) {
            status.setText("沒連上 LSPosed：請在 LSPosed 裡啟用本模組並勾選「系統介面」，然後重開這個畫面。");
            return;
        }
        SharedPreferences prefs;
        try {
            prefs = service.getRemotePreferences(Config.GROUP);
        } catch (Throwable t) {
            status.setText("讀不到設定：" + t);
            return;
        }
        status.setText("已連上 " + service.getFrameworkName() + " " + service.getFrameworkVersion()
                + "。改動會即時套用；沒反應就重啟系統介面。");

        section("時鐘");
        toggle(prefs, Config.CLOCK, "Pixel 雙行時鐘", "時鐘換成上下兩行大字，顏色跟隨系統取色", true);
        slider(prefs, Config.CLOCK_SCALE, "時鐘大小", "%", Config.SCALE_MIN, Config.SCALE_MAX, Config.SCALE_DEFAULT);
        slider(prefs, Config.CLOCK_OFFSET, "上下微調（0 為系統原位置）", "dp", Config.OFFSET_MIN, Config.OFFSET_MAX, Config.OFFSET_DEFAULT);
        slider(prefs, Config.CLOCK_ROUND, "數字圓潤度", "", Config.ROUND_MIN, Config.ROUND_MAX, Config.ROUND_DEFAULT);
        slider(prefs, Config.CLOCK_OVERLAP, "數字重疊", "%", Config.OVERLAP_MIN, Config.OVERLAP_MAX, Config.OVERLAP_DEFAULT);
        slider(prefs, Config.CLOCK_GAP, "重疊處鏤空間隙", "", Config.GAP_MIN, Config.GAP_MAX, Config.GAP_DEFAULT);
        toggle(prefs, Config.CLOCK_DATE, "日期移到時鐘下方", "關掉則保留原本在時鐘上方的日期", true);
        toggle(prefs, Config.CLOCK_AVOID, "有通知時改成一行", "通知或流體雲卡片出現時，時鐘縮小成一行收到左上角，日期在下面；沒通知時回到兩行", true);
        toggle(prefs, Config.CLOCK_RESERVE, "為大時鐘預留空間（備用模式）", "只在系統時鐘不是一般文字元件時用到", true);
        toggle(prefs, Config.AOD, "全景息屏的時鐘也用 Pixel 樣式", "全景息屏和鎖屏是同一個時鐘，關掉則息屏時維持原版", true);

        section("快捷按鈕");
        toggle(prefs, Config.SHORTCUTS, "取色色塊背景", "左右下角按鈕換成跟隨系統色的圓形色塊，圖示自動深淺", true);
        slider(prefs, Config.SHORTCUT_TONE, "按鈕深淺（0 最淺，9 最深）", "", Config.TONE_MIN, Config.TONE_MAX,
                Config.TONE_DEFAULT);

        section("流體雲");
        toggle(prefs, Config.LIGHT_CARDS, "淺色模式用淺色卡片", "鎖屏和通知欄的流體雲卡片跟隨系統深淺色；狀態列膠囊和息屏時維持黑色", true);

        section("診斷");
        toggle(prefs, Config.DUMP, "輸出鎖屏結構到 LSPosed 日誌",
                "排查用：只記錄介面結構，通知內容只記字數。用完請關掉", false);

        Button restart = new Button(this);
        restart.setText("重啟系統介面（需要 root）");
        restart.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setMessage("鎖屏和狀態列會閃一下重新載入。要繼續嗎？")
                .setPositiveButton("重啟", (d, w) -> restartSystemUi())
                .setNegativeButton("取消", null)
                .show());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(24);
        list.addView(restart, lp);

        TextView credits = text("時鐘字型：Google Sans Flex（SIL Open Font License 1.1）。本模組以 GPL-3.0 發布。", 12, false);
        credits.setPadding(0, dp(16), 0, 0);
        list.addView(credits);
    }

    private void restartSystemUi() {
        new Thread(() -> {
            int code;
            try {
                Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "killall com.android.systemui"});
                code = p.waitFor();
            } catch (Exception e) {
                code = -1;
            }
            int result = code;
            runOnUiThread(() -> Toast.makeText(this, result == 0 ? "已重啟系統介面" : "重啟失敗（沒有 root 授權？）",
                    Toast.LENGTH_SHORT).show());
        }).start();
    }

    private void section(String name) {
        TextView t = text(name, 15, true);
        t.setPadding(0, dp(24), 0, dp(4));
        t.setAlpha(0.7f);
        list.addView(t);
    }

    private void toggle(SharedPreferences prefs, String key, String label, String hint, boolean def) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(label, 17, false));
        TextView h = text(hint, 13, false);
        h.setAlpha(0.65f);
        texts.addView(h);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Switch sw = new Switch(this);
        sw.setChecked(prefs.getBoolean(key, def));
        sw.setOnCheckedChangeListener((b, checked) -> prefs.edit().putBoolean(key, checked).apply());
        row.addView(sw);
        row.setOnClickListener(v -> sw.toggle());
        list.addView(row);
    }

    private void slider(SharedPreferences prefs, String key, String label, String unit, int min, int max, int def) {
        TextView t = text("", 17, false);
        t.setPadding(0, dp(10), 0, 0);
        SeekBar bar = new SeekBar(this);
        bar.setMax(max - min);
        int value = prefs.getInt(key, def);
        bar.setProgress(value - min);
        t.setText(label + "：" + value + unit);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) { t.setText(label + "：" + (p + min) + unit); }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { prefs.edit().putInt(key, s.getProgress() + min).apply(); }
        });
        t.setOnLongClickListener(v -> {
            bar.setProgress(def - min);
            prefs.edit().putInt(key, def).apply();
            Toast.makeText(this, "已恢復預設", Toast.LENGTH_SHORT).show();
            return true;
        });
        list.addView(t);
        list.addView(bar);
    }

    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

}
