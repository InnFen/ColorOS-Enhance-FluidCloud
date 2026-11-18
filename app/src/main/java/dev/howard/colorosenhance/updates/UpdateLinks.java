package dev.howard.colorosenhance.updates;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import dev.howard.colorosenhance.BuildConfig;

/** The Settings host opens the module Activity, so networking never runs in the host process. */
public final class UpdateLinks {
    private UpdateLinks() { }
    public static void check(Context context) {
        launch(context, new Intent().setComponent(new ComponentName(BuildConfig.PUBLIC_APPLICATION_ID,
                "dev.howard.colorosenhance.updates.UpdateActivity")));
    }
    public static void repository(Context context) { browser(context, ReleaseInfo.SOURCE_URL); }
    static void browser(Context context, String url) {
        launch(context, new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE));
    }
    private static void launch(Context context, Intent intent) {
        if (!(context instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { context.startActivity(intent); }
        catch (RuntimeException unavailable) { Toast.makeText(context, "暂时无法打开，请确认模块和浏览器已安装并启用。", Toast.LENGTH_LONG).show(); }
    }
}
