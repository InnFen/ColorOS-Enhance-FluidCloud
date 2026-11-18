package dev.lsposed.fluidcloud;
import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=36,application=Application.class)
public class ShadeOpenRequestTest {
    @Test public void opensExactlyOnceAfterRealCollapseRatherThanAnArbitraryDelay(){
        boolean[] expanded={true};List<String> events=new ArrayList<>();
        var request=new ShadeOpenRequest(new Handler(Looper.getMainLooper()),()->expanded[0],()->events.add("collapse"),error->fail(error.toString()));
        request.submit(()->events.add("open"),()->true);Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500));
        assertEquals(List.of("collapse"),events);expanded[0]=false;Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));
        assertEquals(List.of("collapse","open"),events);Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));assertEquals(2,events.size());
    }
    @Test public void staleContentOrLockCancelsPendingNativeClick(){
        boolean[] valid={true},expanded={true};int[] opened={0};
        var request=new ShadeOpenRequest(new Handler(Looper.getMainLooper()),()->expanded[0],()->{},error->fail(error.toString()));
        request.submit(()->opened[0]++,()->valid[0]);valid[0]=false;expanded[0]=false;
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));assertEquals(0,opened[0]);
        request.submit(()->opened[0]++,()->true);request.cancel();Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals(0,opened[0]);
    }
    @Test public void timeoutAndCollapseFailureNeverReplayAHiddenOrDuplicateClick(){
        int[] opened={0};List<Throwable> errors=new ArrayList<>();
        var request=new ShadeOpenRequest(new Handler(Looper.getMainLooper()),()->true,()->{},errors::add);
        request.submit(()->opened[0]++,()->true);Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
        assertEquals(0,opened[0]);assertEquals(1,errors.size());
        var broken=new ShadeOpenRequest(new Handler(Looper.getMainLooper()),()->true,()->{throw new IllegalStateException("no service");},errors::add);
        broken.submit(()->opened[0]++,()->true);Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals(0,opened[0]);assertEquals(2,errors.size());
    }
}
