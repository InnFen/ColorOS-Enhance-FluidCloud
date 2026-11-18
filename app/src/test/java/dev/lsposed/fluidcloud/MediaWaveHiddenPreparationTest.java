package dev.lsposed.fluidcloud;

import android.app.Activity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=36,manifest=Config.NONE)
public class MediaWaveHiddenPreparationTest {
    public static class CapsuleView extends FrameLayout {public CapsuleView(Activity context){super(context);}}
    @Test public void hiddenCapsuleKeepsRendererPreparedForItsVeryFirstReturningFrame(){
        var activity=Robolectric.buildActivity(Activity.class).setup().visible();
        try{
            var root=new FrameLayout(activity.get());var capsule=new CapsuleView(activity.get());var image=new ImageView(activity.get());
            root.addView(capsule);capsule.addView(image);activity.get().setContentView(root);
            assertTrue(NativeMediaWave.capsule(image));
            capsule.setVisibility(View.INVISIBLE);capsule.setAlpha(0);
            assertFalse(image.isShown());assertTrue(NativeMediaWave.capsule(image));
            capsule.setVisibility(View.VISIBLE);capsule.setAlpha(1);assertTrue(NativeMediaWave.capsule(image));
            root.removeView(capsule);assertFalse(NativeMediaWave.capsule(image));
        }finally{activity.pause().stop().destroy();}
    }
    @Test public void otherLottieIconsAndCardControlsAreNotInTheCapsuleScope(){
        var activity=Robolectric.buildActivity(Activity.class).setup().visible();
        try{
            var card=new FrameLayout(activity.get());var image=new ImageView(activity.get());card.addView(image);activity.get().setContentView(card);
            assertFalse(NativeMediaWave.capsule(image));
        }finally{activity.pause().stop().destroy();}
    }
}
