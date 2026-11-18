package com.howard.simpinautounlock.ui;

import static org.junit.Assert.*;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Parcelable;
import android.os.SystemClock;
import android.text.InputType;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import dev.howard.colorosenhance.R;
import com.google.android.material.materialswitch.MaterialSwitch;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Uses synthetic credentials only; never contacts the phone bridge or credential repositories. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, application = Application.class)
public class MaterialSettingsViewTest {
    private View page;
    private SlotConfigView slot;
    private final List<String> actions = new ArrayList<>();

    @Before
    public void inflatePage() {
        page = LayoutInflater.from(themed(false)).inflate(R.layout.sim_activity_main, null);
        slot = page.findViewById(R.id.sim_slot_1);
        slot.bind(1, listener());
    }

    @Test
    public void renderingSwitchStateNeverSubmitsAToggle() {
        View control = slot.findViewById(R.id.sim_toggle_button);
        assertTrue("Enable control must expose checked state to accessibility", control instanceof CompoundButton);
        CompoundButton toggle = (CompoundButton) control;
        slot.render(presentation(true, true));
        assertTrue(toggle.isChecked());
        slot.render(presentation(true, false));
        assertFalse(toggle.isChecked());
        slot.showLoading();
        slot.showError("测试错误");
        assertTrue(actions.isEmpty());
        assertFalse(toggle.isEnabled());
    }

    @Test
    public void switchClickRequestsTheCorrectSlotWithoutPretendingTheSaveSucceeded() {
        slot.render(presentation(true, false));
        View control = slot.findViewById(R.id.sim_toggle_button);
        assertTrue("Toggle needs an observable checked state", control instanceof CompoundButton);
        CompoundButton toggle = (CompoundButton) control;
        toggle.performClick();
        assertEquals(List.of("toggle:1:true"), actions);
        assertFalse("Wait for authoritative bridge result", toggle.isChecked());
        slot.render(presentation(true, true));
        assertTrue(toggle.isChecked());
        toggle.performClick();
        assertEquals(List.of("toggle:1:true", "toggle:1:false"), actions);
        assertTrue(toggle.isChecked());
    }

    @Test
    public void draggingSwitchInEitherDirectionRequestsTheCorrectStateExactlyOnce() {
        page.measure(View.MeasureSpec.makeMeasureSpec(822, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1782, View.MeasureSpec.EXACTLY));
        page.layout(0, 0, 822, 1782);
        MaterialSwitch toggle = slot.findViewById(R.id.sim_toggle_button);
        slot.render(presentation(true, false));
        dragThumb(toggle, true);
        assertEquals(List.of("toggle:1:true"), actions);
        assertFalse("Drag also waits for the bridge result", toggle.isChecked());
        slot.render(presentation(true, true));
        dragThumb(toggle, false);
        assertEquals(List.of("toggle:1:true", "toggle:1:false"), actions);
        assertTrue(toggle.isChecked());
    }

    private static void dragThumb(MaterialSwitch toggle, boolean right) {
        toggle.jumpDrawablesToCurrentState();
        Bitmap bitmap = Bitmap.createBitmap(toggle.getWidth(), toggle.getHeight(), Bitmap.Config.ARGB_8888);
        try {
            toggle.draw(new Canvas(bitmap));
            Rect thumb = toggle.getThumbDrawable().getBounds();
            float from = thumb.centerX();
            float y = thumb.centerY();
            float direction = right ? 1f : -1f;
            float slop = ViewConfiguration.get(toggle.getContext()).getScaledTouchSlop();
            long start = SystemClock.uptimeMillis();
            touch(toggle, start, start, MotionEvent.ACTION_DOWN, from, y);
            touch(toggle, start, start + 20, MotionEvent.ACTION_MOVE, from + direction * (slop + 2), y);
            touch(toggle, start, start + 40, MotionEvent.ACTION_MOVE, from + direction * toggle.getWidth(), y);
            touch(toggle, start, start + 60, MotionEvent.ACTION_UP, from + direction * toggle.getWidth(), y);
        } finally {
            bitmap.recycle();
        }
    }

    private static void touch(View view, long down, long now, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(down, now, action, x, y, 0);
        try { view.onTouchEvent(event); } finally { event.recycle(); }
    }

    @Test
    public void saveStillRoutesBothEntriesToTheRightSlotAndClearsInputs() {
        EditText pin = slot.findViewById(R.id.sim_pin_entry);
        EditText confirmation = slot.findViewById(R.id.sim_pin_confirmation);
        pin.setText("1234");
        confirmation.setText("5678");
        slot.findViewById(R.id.sim_save_button).performClick();
        assertEquals(List.of("save:1:1234:5678"), actions);
        assertEquals("", pin.getText().toString());
        assertEquals("", confirmation.getText().toString());
    }

    @Test
    public void unavailableModuleStillAllowsStagingButCannotToggle() {
        slot.render(presentation(false, false));
        assertTrue(slot.findViewById(R.id.sim_save_button).isEnabled());
        assertFalse(slot.findViewById(R.id.sim_toggle_button).isEnabled());
        assertTrue(slot.findViewById(R.id.sim_clear_button).isEnabled());
        slot.findViewById(R.id.sim_clear_button).performClick();
        assertEquals(List.of("clear:1"), actions);
    }

    @Test
    public void pinInputsRemainMaskedAndDoNotRestoreAcrossViewState() {
        EditText pin = slot.findViewById(R.id.sim_pin_entry);
        assertEquals(InputType.TYPE_NUMBER_VARIATION_PASSWORD,
                pin.getInputType() & InputType.TYPE_MASK_VARIATION);
        pin.setText("1234");
        SparseArray<Parcelable> state = new SparseArray<>();
        page.saveHierarchyState(state);
        View restored = LayoutInflater.from(themed(false)).inflate(R.layout.sim_activity_main, null);
        restored.restoreHierarchyState(state);
        SlotConfigView restoredSlot = restored.findViewById(R.id.sim_slot_1);
        assertEquals("", ((EditText) restoredSlot.findViewById(R.id.sim_pin_entry)).getText().toString());
    }

    @Test
    public void nightThemeHasDarkSurfaceAndReadableText() {
        Context context = themed(true);
        int background = themeColor(context, android.R.attr.colorBackground);
        int foreground = themeColor(context, android.R.attr.textColorPrimary);
        assertTrue("Night background must be dark", luminance(background) < 0.2);
        assertTrue("Night text must contrast with its background", luminance(foreground) > 0.5);
        View nightPage = LayoutInflater.from(context).inflate(R.layout.sim_activity_main, null);
        assertNotNull(nightPage.findViewById(R.id.sim_slot_1));
    }

    @Test
    public void slotStatusAndPendingWarningRemainVisible() {
        slot.render(new SlotPresentation("已绑定 SIM：•••• 1234\n最近结果：成功", true, true, true, true));
        String text = ((TextView) slot.findViewById(R.id.sim_slot_status)).getText().toString();
        assertTrue(text.contains("•••• 1234"));
        assertTrue(text.contains("最近结果：成功"));
        assertTrue(text.contains("首次导入完成前不要换卡或交换卡槽"));
    }

    private SlotConfigView.Listener listener() {
        return new SlotConfigView.Listener() {
            @Override public void onSave(int id, String pin, String confirmation) {
                actions.add("save:" + id + ":" + pin + ":" + confirmation);
            }
            @Override public void onToggle(int id, boolean enabled) {
                actions.add("toggle:" + id + ":" + enabled);
            }
            @Override public void onClear(int id) { actions.add("clear:" + id); }
        };
    }

    private static SlotPresentation presentation(boolean available, boolean enabled) {
        return new SlotPresentation("测试状态", available, enabled, true, false);
    }

    static Context themed(boolean night) {
        Context application = RuntimeEnvironment.getApplication();
        Configuration config = new Configuration(application.getResources().getConfiguration());
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | (night ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        return new ContextThemeWrapper(application.createConfigurationContext(config), R.style.sim_AppTheme);
    }

    private static int themeColor(Context context, int attribute) {
        TypedValue value = new TypedValue();
        assertTrue(context.getTheme().resolveAttribute(attribute, value, true));
        return value.resourceId == 0 ? value.data : context.getColor(value.resourceId);
    }

    private static double luminance(int color) {
        return (0.2126 * Color.red(color) + 0.7152 * Color.green(color) + 0.0722 * Color.blue(color)) / 255.0;
    }
}
