package com.howard.simpinautounlock.ui;

import static org.junit.Assert.*;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import dev.howard.colorosenhance.R;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Renders the real resource layouts with synthetic status data, without phone/IPC access. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, qualifiers = "zh-rCN-w411dp-h891dp-xhdpi", application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SettingsPreviewTest {
    @Test public void lightLayoutFitsAndRenders() throws Exception {
        render("light", false, 411, 1f);
    }

    @Test public void darkLayoutFitsAndRenders() throws Exception {
        render("dark", true, 411, 1f);
    }

    @Test public void narrowLayoutWithLargeTextKeepsActionsInsideCards() throws Exception {
        render("large-text", false, 320, 1.5f);
    }

    private void render(String name, boolean night, int widthDp, float fontScale) throws Exception {
        Context application = RuntimeEnvironment.getApplication();
        Configuration config = new Configuration(application.getResources().getConfiguration());
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | (night ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        config.fontScale = fontScale;
        Context context = new ContextThemeWrapper(application.createConfigurationContext(config), R.style.sim_AppTheme);
        View page = LayoutInflater.from(context).inflate(R.layout.sim_activity_main, null);
        for (int i = 0; i < 2; i++) {
            SlotConfigView slot = page.findViewById(i == 0 ? R.id.sim_slot_0 : R.id.sim_slot_1);
            slot.bind(i, new SlotConfigView.Listener() {
                @Override public void onSave(int slotId, String pin, String confirmation) {
                    fail("Preview must not configure a SIM");
                }
                @Override public void onToggle(int slotId, boolean enabled) {
                    fail("Preview must not toggle a SIM");
                }
                @Override public void onClear(int slotId) { fail("Preview must not clear a SIM"); }
            });
            slot.render(new SlotPresentation(
                    "状态：已启用\n已绑定 SIM：•••• " + (i == 0 ? "1234" : "5678")
                            + "\nSIM：匹配\n最近结果：成功",
                    true, true, true, false));
        }
        float density = context.getResources().getDisplayMetrics().density;
        int width = Math.round(widthDp * density);
        int height = Math.round(891 * density);
        page.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        page.layout(0, 0, width, height);
        ViewGroup content = page.findViewById(R.id.sim_settings_content);
        for (int id : new int[] {R.id.sim_slot_0, R.id.sim_slot_1}) {
            SlotConfigView slot = page.findViewById(id);
            for (int action : new int[] {R.id.sim_toggle_button, R.id.sim_save_button, R.id.sim_clear_button,
                    R.id.sim_pin_entry, R.id.sim_pin_confirmation}) {
                View control = slot.findViewById(action);
                Rect bounds = new Rect(0, 0, control.getWidth(), control.getHeight());
                content.offsetDescendantRectToMyCoords(control, bounds);
                assertTrue(name + ": control must have visible width", bounds.width() > 0);
                assertTrue(name + ": control must fit screen start", bounds.left >= content.getPaddingLeft());
                assertTrue(name + ": control must fit screen end",
                        bounds.right <= width - content.getPaddingRight());
            }
            TextView save = slot.findViewById(R.id.sim_save_button);
            for (int line = 0; line < save.getLineCount(); line++) {
                assertEquals("Save label must not be truncated", 0, save.getLayout().getEllipsisCount(line));
            }
            TextView formatHint = slot.findViewById(com.google.android.material.R.id.textinput_helper_text);
            assertNotNull("PIN format must remain visible outside the single-line input hint", formatHint);
            assertEquals(View.VISIBLE, formatHint.getVisibility());
            assertTrue(formatHint.getText().toString().contains("4–8"));
            for (int line = 0; line < formatHint.getLineCount(); line++) {
                assertEquals("PIN format must not be truncated at large text sizes", 0,
                        formatHint.getLayout().getEllipsisCount(line));
            }
        }
        TypedValue background = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.colorBackground, background, true);
        Bitmap bitmap = Bitmap.createBitmap(content.getWidth(), content.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(background.resourceId == 0 ? background.data : context.getColor(background.resourceId));
        content.draw(canvas);
        File directory = new File("build/reports/ui-preview");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        try (FileOutputStream output = new FileOutputStream(new File(directory, name + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        } finally {
            bitmap.recycle();
        }
    }
}
