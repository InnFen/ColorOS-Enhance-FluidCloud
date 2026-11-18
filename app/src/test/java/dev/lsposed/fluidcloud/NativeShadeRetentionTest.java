package dev.lsposed.fluidcloud;

import android.app.Application;
import android.os.Bundle;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=36,application=Application.class)
public class NativeShadeRetentionTest {
    public static class Handler { Bundle last;public void onPanel(Bundle bundle){last=bundle;} }

    @Test public void keepChangesOnlyCapsuleFractionAndRestoresLatestNativeInputOnce()throws Exception{
        NativeShadeRetention retention=new NativeShadeRetention(Handler.class.getMethod("onPanel",Bundle.class));
        Handler handler=new Handler();Bundle nativeEvent=new Bundle();nativeEvent.putFloat("expandFraction",.7f);nativeEvent.putInt("expandFrom",2);
        Bundle kept=retention.filter(handler,nativeEvent,true);
        assertNotSame(nativeEvent,kept);assertEquals(.7f,nativeEvent.getFloat("expandFraction"),0);
        assertEquals(0,kept.getFloat("expandFraction"),0);assertEquals(2,kept.getInt("expandFrom"));
        nativeEvent.putFloat("expandFraction",1f);retention.filter(handler,nativeEvent,true);
        nativeEvent.putFloat("expandFraction",.2f);retention.restore();
        assertEquals(1f,handler.last.getFloat("expandFraction"),0);handler.last=null;
        retention.restore();assertNull(handler.last);
    }

    @Test public void disabledProtectionPassesNativeObjectAndDropsRetainedReplay()throws Exception{
        NativeShadeRetention retention=new NativeShadeRetention(Handler.class.getMethod("onPanel",Bundle.class));
        Handler handler=new Handler();Bundle event=new Bundle();event.putFloat("expandFraction",.6f);
        retention.filter(handler,event,true);assertSame(event,retention.filter(handler,event,false));
        retention.restore();assertNull(handler.last);
        assertNull(retention.filter(handler,null,true));
    }
}
