package dev.howard.colorosenhance.settings;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import dev.howard.colorosenhance.SettingsCatalog;
import dev.howard.colorosenhance.ui.WaveBandAxisView;
import dev.lsposed.fluidcloud.MediaWaveBands;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.*;
final class NativeDialogs {
    private final Context context;
    private final HostReflection host;
    private final Consumer<String> error;
    private final List<EditText> inputs=new ArrayList<>();
    private Dialog active;
    private dev.howard.colorosenhance.ui.FeatureDemoView activeDemo;
    NativeDialogs(Context context,HostReflection host,Consumer<String> error) {
        this.context=context; this.host=host; this.error=error;
    }
    void number(SettingsCatalog.Item item,int current,IntConsumer save) {
        clear();
        try {
            EditText input=input(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            input.setText(format(current,item.divisor())); input.selectAll();
            String range=format(item.min(),item.divisor())+"–"+format(item.max(),item.divisor())+" "+item.unit()
                    +"，步长 "+format(item.step(),item.divisor());
            show(item.title(),range,container(input),()-> {
                Integer value=NativeNumberInput.parse(input.getText().toString(),item.min(),item.max(),item.step(),item.divisor());
                if(value==null) { input.setError("请输入范围内且符合步长的数值"); return; }
                clear(); save.accept(value);
            });
        } catch(ReflectiveOperationException | RuntimeException failure) { failed(); }
    }
    void waveBands(MediaWaveBands bands, Consumer<String> save) {
        clear();
        try {
            LinearLayout fields = container();
            TextView invalid = new TextView(context);
            invalid.setText(SettingsCatalog.WAVE_BANDS_ERROR);
            invalid.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            invalid.setVisibility(View.GONE);
            fields.addView(invalid);
            WaveBandAxisView axis = new WaveBandAxisView(context);
            axis.setBands(bands);
            fields.addView(axis, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            EditText[] boundaries = new EditText[MediaWaveBands.EDGE_COUNT];
            float density = context.getResources().getDisplayMetrics().density;
            for (int i = 0; i < boundaries.length; i++) {
                EditText boundary = input(InputType.TYPE_CLASS_NUMBER);
                boundary.setId(View.generateViewId());
                boundary.setSelectAllOnFocus(true);
                boundary.setText(Integer.toString(bands.edgeHz(i)));
                boundaries[i] = boundary;

                TextView label = new TextView(context);
                label.setText(SettingsCatalog.waveBandEdgeTitle(i));
                label.setLabelFor(boundary.getId());
                LinearLayout row = new LinearLayout(context);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                row.addView(boundary, new LinearLayout.LayoutParams(Math.round(112 * density), ViewGroup.LayoutParams.WRAP_CONTENT));
                fields.addView(row);
            }
            boolean[] updatingFields = {false};
            TextWatcher draftWatcher = new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence text, int start, int before, int count) { }
                @Override public void afterTextChanged(Editable text) {
                    if (updatingFields[0]) return;
                    MediaWaveBands entered = readWaveBands(boundaries);
                    axis.setEnabled(entered != null);
                    if (entered != null) {
                        axis.setBands(entered);
                        invalid.setVisibility(View.GONE);
                    }
                }
            };
            for (EditText boundary : boundaries) boundary.addTextChangedListener(draftWatcher);
            axis.setOnBandsChanged(edited -> {
                // A drag replaces a valid draft. Update all of its fields before revalidating.
                updatingFields[0] = true;
                try {
                    for (int i = 0; i < boundaries.length; i++) {
                        EditText boundary = boundaries[i];
                        String value = Integer.toString(edited.edgeHz(i));
                        if (!value.contentEquals(boundary.getText())) boundary.setText(value);
                    }
                } finally {
                    updatingFields[0] = false;
                }
            });
            ScrollView scroll = new ScrollView(context);
            scroll.addView(fields);
            show(SettingsCatalog.WAVE_BANDS_TITLE, SettingsCatalog.WAVE_BANDS_HELP, scroll, () -> {
                MediaWaveBands entered = readWaveBands(boundaries);
                if (entered == null) {
                    invalid.setVisibility(View.VISIBLE);
                    scroll.smoothScrollTo(0, 0);
                    return;
                }
                clear();
                save.accept(entered.encode());
            }, () -> {
                clear();
                save.accept(MediaWaveBands.DEFAULTS.encode());
            });
        } catch (ReflectiveOperationException | RuntimeException failure) { failed(); }
    }
    private static MediaWaveBands readWaveBands(EditText[] boundaries) {
        StringJoiner encoded = new StringJoiner(",");
        for (EditText boundary : boundaries) encoded.add(boundary.getText());
        return MediaWaveBands.parse(encoded.toString());
    }
    void pin(int slot,BiConsumer<String,String> save) {
        clear();
        try {
            int type=InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD;
            EditText pin=input(type),confirmation=input(type);
            pin.setHint("SIM PIN（4–8 位数字）"); confirmation.setHint("再次输入 PIN");
            pin.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(8)});
            confirmation.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(8)});
            show("卡槽 "+(slot+1)+" · 保存 PIN","请输入此 SIM 卡当前使用的 PIN。",container(pin,confirmation),()-> {
                String first=pin.getText().toString(),second=confirmation.getText().toString();
                if(!first.matches("[0-9]{4,8}")) { pin.setError("请输入 4–8 位数字"); return; }
                if(!first.equals(second)) { confirmation.setError("两次输入不一致"); return; }
                clear(); save.accept(first,second);
            });
        } catch(ReflectiveOperationException | RuntimeException failure) { failed(); }
    }
    void confirm(String title,String message,Runnable action) {
        clear();
        try { show(title,message,null,()-> { clear(); action.run(); }); }
        catch(ReflectiveOperationException | RuntimeException failure) { failed(); }
    }
    void demo(String kind,String route,String title,String summary,Map<String,?> values){
        clear();
        try{
            var illustration=new dev.howard.colorosenhance.ui.FeatureDemoView(context,kind);
            activeDemo=illustration;
            illustration.presentation(route);illustration.values(values);
            Object builder=host.create("com.coui.appcompat.dialog.COUIAlertDialogBuilder",context);
            host.call(builder,"setTitle",title+" · 效果演示");host.call(builder,"setMessage",summary);
            host.call(builder,"setView",illustration);
            host.call(builder,"setPositiveButton","知道了",(DialogInterface.OnClickListener)null);
            Dialog dialog=(Dialog)host.call(builder,"create");active=dialog;
            dialog.setOnDismissListener(ignored->{
                illustration.stop();if(activeDemo==illustration)activeDemo=null;
                if(active==dialog){active=null;clearText();}
            });
            dialog.show();
            Button button=(Button)host.call(dialog,"getButton",DialogInterface.BUTTON_POSITIVE);
            button.setOnClickListener(ignored->clear());
        }catch(ReflectiveOperationException|RuntimeException failure){failed();}
    }
    void choice(String title,CharSequence[] labels,int checked,IntConsumer selected) {
        clear();
        try {
            Object builder=host.create("com.coui.appcompat.dialog.COUIAlertDialogBuilder",context);
            host.call(builder,"setTitle",title);
            Context themed=(Context)host.call(builder,"getContext");
            host.call(builder,"setSingleChoiceItems",choiceAdapter(themed,labels),checked,(DialogInterface.OnClickListener)(dialog,which)->{
                clear();selected.accept(which);
            });
            host.call(builder,"setNegativeButton","取消",(DialogInterface.OnClickListener)(dialog,which)->clear());
            Dialog dialog=(Dialog)host.call(builder,"create");active=dialog;dialog.show();
        } catch(ReflectiveOperationException | RuntimeException failure) {failed();}
    }
    void iconOrder(dev.howard.colorosenhance.icons.IconOrder value,List<String> slots,Consumer<String> save){
        clear();
        try{
            var editor=new dev.howard.colorosenhance.icons.IconOrderEditor(context,value,slots);
            show("状态栏图标排序",null,editor,()->{String encoded=editor.encodedValue();clear();save.accept(encoded);});
        }catch(ReflectiveOperationException|RuntimeException failure){failed();}
    }
    @android.annotation.SuppressLint("DiscouragedApi") // Use the host firmware's COUI palette and list style.
    private ListAdapter choiceAdapter(Context themed,CharSequence[] labels) {
        var resources=themed.getResources();
        int style=resources.getIdentifier("alertDialogStyle","attr","com.android.settings");
        int item=resources.getIdentifier("singleChoiceItemLayout","attr","com.android.settings");
        int layout=android.R.layout.simple_list_item_single_choice;
        if(style!=0 && item!=0) {
            TypedArray attributes=themed.obtainStyledAttributes(null,new int[]{item},style,0);
            try {layout=attributes.getResourceId(0,layout);} finally {attributes.recycle();}
        }
        int color=resources.getIdentifier("coui_bottom_alert_dialog_button_text_color_selector","color","com.android.settings");
        ColorStateList colors;
        if(color!=0) colors=themed.getColorStateList(color);
        else {
            TypedArray attributes=themed.obtainStyledAttributes(new int[]{android.R.attr.textColorPrimary});
            try {colors=attributes.getColorStateList(0);} finally {attributes.recycle();}
        }
        ColorStateList textColors=colors;
        return new ArrayAdapter<CharSequence>(themed,layout,android.R.id.text1,labels) {
            @Override public View getView(int position,View recycled,ViewGroup parent) {
                View row=super.getView(position,recycled,parent);
                TextView text=row.findViewById(android.R.id.text1);
                if(textColors!=null) text.setTextColor(textColors);
                return row;
            }
        };
    }
    private EditText input(int inputType) throws ReflectiveOperationException {
        EditText input=(EditText)host.create("com.coui.appcompat.edittext.COUIEditText",context);
        input.setInputType(inputType); input.setSingleLine(true);
        input.setSaveEnabled(false); input.setSaveFromParentEnabled(false);
        input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        inputs.add(input); return input;
    }
    private LinearLayout container(EditText... fields) {
        LinearLayout box=new LinearLayout(context); box.setOrientation(LinearLayout.VERTICAL);
        int padding=Math.round(24*context.getResources().getDisplayMetrics().density);
        box.setPadding(padding,0,padding,padding/2); box.setSaveEnabled(false);
        box.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        for(EditText input:fields) box.addView(input,new LinearLayout.LayoutParams(-1,-2));
        return box;
    }
    private void show(String title,String message,View view,Runnable positive) throws ReflectiveOperationException {
        show(title, message, view, positive, null);
    }
    private void show(String title,String message,View view,Runnable positive,Runnable reset) throws ReflectiveOperationException {
        Object builder=host.create("com.coui.appcompat.dialog.COUIAlertDialogBuilder",context);
        host.call(builder,"setTitle",title); host.call(builder,"setMessage",message);
        if(view!=null) host.call(builder,"setView",view);
        host.call(builder,"setNegativeButton","取消",(DialogInterface.OnClickListener)(dialog,which)->clear());
        host.call(builder,"setPositiveButton","确定",(DialogInterface.OnClickListener)(dialog,which)->{});
        if(reset!=null)host.call(builder,"setNeutralButton","恢复默认",(DialogInterface.OnClickListener)(dialog,which)->{});
        Dialog dialog=(Dialog)host.call(builder,"create"); active=dialog;
        dialog.setOnDismissListener(ignored->{ if(active==dialog) { active=null; clearText(); } });
        dialog.show();
        Button button=(Button)host.call(dialog,"getButton",DialogInterface.BUTTON_POSITIVE);
        button.setOnClickListener(ignored->positive.run());
        if(reset!=null) {
            Button restore=(Button)host.call(dialog,"getButton",DialogInterface.BUTTON_NEUTRAL);
            restore.setOnClickListener(ignored->reset.run());
        }
    }
    void clear() {
        var illustration=activeDemo;activeDemo=null;
        if(illustration!=null)illustration.stop();
        Dialog old=active; active=null; clearText();
        if(old!=null) old.dismiss();
    }
    private void clearText() { for(EditText input:inputs) input.setText(""); inputs.clear(); }
    private void failed() { clear(); error.accept("无法打开编辑窗口，请使用独立应用。 "); }
    static String format(int value,int divisor) {
        return BigDecimal.valueOf(value).divide(BigDecimal.valueOf(divisor)).stripTrailingZeros().toPlainString();
    }
}
