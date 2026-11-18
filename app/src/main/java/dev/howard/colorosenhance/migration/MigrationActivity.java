package dev.howard.colorosenhance.migration;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.OnBackPressedCallback;
import dev.howard.colorosenhance.EnhanceApplication;
import dev.howard.colorosenhance.MainActivity;

/** The migration surface is private to the new app; uninstall always uses system confirmation. */
public final class MigrationActivity extends AppCompatActivity {
    private EnhanceApplication app;private LinearLayout content;
    private final Runnable redraw=this::render;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);app=(EnhanceApplication)getApplication();
        var scroll=new android.widget.ScrollView(this);content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);
        int pad=Math.round(24*getResources().getDisplayMetrics().density);content.setPadding(pad,pad*2,pad,pad*2);
        scroll.setFillViewport(true);scroll.addView(content);setContentView(scroll);setTitle("旧版设置迁移");
        getOnBackPressedDispatcher().addCallback(this,new OnBackPressedCallback(true){
            @Override public void handleOnBackPressed(){
                if(app.migration().blocksEditing())finishAffinity();else finish();
            }
        });
        render();
    }
    @Override public void onStart(){super.onStart();app.observe(redraw);app.migration().foreground(this);render();}
    @Override public void onStop(){app.migration().background(this);app.removeObserver(redraw);super.onStop();}
    private void text(String value,int size){var view=new TextView(this);view.setText(value);view.setTextSize(size);view.setPadding(0,12,0,20);content.addView(view);}
    private void button(String title,Runnable action){var view=new Button(this);view.setText(title);view.setOnClickListener(v->action.run());content.addView(view);}
    private void render(){
        if(content==null)return;content.removeAllViews();var migration=app.migration();
        text("ColorOS 增强 · 自动迁移",24);text(migration.message(),16);
        text("旧包："+MigrationProtocol.LEGACY+"\n新包："+MigrationProtocol.CURRENT,12);
        if(migration.phase()==MigrationController.Phase.READING){content.addView(new ProgressBar(this));return;}
        if(migration.phase()==MigrationController.Phase.COMPLETE){
            button("进入新版设置",()->{startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));finish();});
            if(migration.imported() && migration.legacyInstalled())button("卸载旧版本",()->{
                try{startActivity(new Intent(Intent.ACTION_DELETE,Uri.parse("package:"+MigrationProtocol.LEGACY)));}
                catch(RuntimeException missing){text("请到系统应用管理中卸载旧包。",14);}
            });
        }else{
            button(migration.phase()==MigrationController.Phase.CONFLICT?"继续迁移，使用旧版设置":"重新检测／重试迁移",migration::retry);
            text("在 LSPosed 中取消旧版启用，勾选新版与所需作用域。返回本页会自动复查；旧包只在迁移完成后再卸载。若 root 未获授权或框架尚未连接，请先处理授权与连接。",14);
            button("查看旧包应用信息",()->{try{startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+MigrationProtocol.LEGACY)));}catch(RuntimeException ignored){}});
        }
    }
}
