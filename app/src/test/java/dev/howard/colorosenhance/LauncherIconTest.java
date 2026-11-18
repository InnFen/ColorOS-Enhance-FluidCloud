package dev.howard.colorosenhance;

import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.ColorDrawable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=36) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class LauncherIconTest {
    @Test public void lightAndNightUseTheSameGradientMarkWithExactBackgrounds() throws Exception {
        for(boolean dark:new boolean[]{false,true}){
            var app=RuntimeEnvironment.getApplication();var config=new Configuration(app.getResources().getConfiguration());
            config.uiMode=(config.uiMode&~Configuration.UI_MODE_NIGHT_MASK)|(dark?Configuration.UI_MODE_NIGHT_YES:Configuration.UI_MODE_NIGHT_NO);
            var context=app.createConfigurationContext(config);
            var icon=context.getDrawable(R.mipmap.ic_launcher);
            assertTrue(icon instanceof AdaptiveIconDrawable);
            var adaptive=(AdaptiveIconDrawable)icon;
            assertEquals(dark?Color.rgb(20,27,43):Color.WHITE,((ColorDrawable)adaptive.getBackground()).getColor());
            assertNotNull(adaptive.getMonochrome());
            Bitmap bitmap=Bitmap.createBitmap(432,432,Bitmap.Config.ARGB_8888);adaptive.setBounds(0,0,432,432);adaptive.draw(new Canvas(bitmap));
            var directory=new java.io.File("build/icon-preview");if(!directory.isDirectory()&&!directory.mkdirs())throw new java.io.IOException();
            try(var output=new java.io.FileOutputStream(new java.io.File(directory,(dark?"dark":"light")+".png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,output));}
            assertEquals(0xff000000,bitmap.getPixel(216,216)&0xff000000);
        }
    }
}
