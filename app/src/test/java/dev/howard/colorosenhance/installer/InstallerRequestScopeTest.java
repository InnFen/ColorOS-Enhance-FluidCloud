package dev.howard.colorosenhance.installer;

import android.content.Intent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=36,manifest=Config.NONE)
public class InstallerRequestScopeTest {
    static class Request {
        private Intent intent=new Intent();
        private int userId=0,realCallingUid=10123,callingUid=-1;
        private String callingPackage="example.files";
        private Object originatingPendingIntent;
    }
    private final InstallerRequestScope scope;
    public InstallerRequestScopeTest() throws Exception { scope=new InstallerRequestScope(Request.class); }
    private InstallerRouting.Origin origin(Request request) throws Exception {
        return scope.originFor(request.intent,request.userId,0,request.realCallingUid);
    }
    @Test public void onlyExposesCurrentRequestAndRemovesItAfterReturn() throws Throwable {
        Request request=new Request(); assertNull(origin(request));
        Object result=scope.within(request,()->{assertEquals("example.files",origin(request).packageName());return 42;});
        assertEquals(42,result); assertNull(origin(request));
    }
    @Test public void restoresOuterAfterNestedSuccessAndException() throws Throwable {
        Request outer=new Request(),inner=new Request(); inner.callingPackage="example.inner";
        scope.within(outer,()->{
            scope.within(inner,()->{assertNull(origin(outer));assertEquals("example.inner",origin(inner).packageName());return null;});
            assertEquals("example.files",origin(outer).packageName());
            RuntimeException expected=new RuntimeException();
            try {scope.within(inner,()->{throw expected;});fail();} catch(RuntimeException actual){assertSame(expected,actual);}
            assertEquals("example.files",origin(outer).packageName()); return null;
        });
        assertNull(origin(outer)); assertNull(origin(inner));
    }
    @Test public void keepsNativeExceptionAndClearsContext() throws Throwable {
        Request request=new Request(); SecurityException failure=new SecurityException();
        try {scope.within(request,()->{throw failure;});fail();} catch(SecurityException actual){assertSame(failure,actual);}
        assertNull(origin(request));
    }
    @Test public void ignoresOtherIntentUserRetryFlagsAndDelegatedUids() throws Throwable {
        Request request=new Request();
        scope.within(request,()->{
            assertNull(scope.originFor(new Intent(),0,0,10123));
            assertNull(scope.originFor(request.intent,10,0,10123));
            assertNull(scope.originFor(request.intent,0,1,10123));
            assertNull(scope.originFor(request.intent,0,0,10456));
            request.callingUid=10456; assertNull(origin(request));
            request.callingUid=10123; assertNotNull(origin(request)); return null;
        });
    }
    @Test public void marksPendingIntentAndDoesNotLeakToAnotherThread() throws Throwable {
        Request request=new Request(); request.originatingPendingIntent=new Object();
        scope.within(request,()->{
            assertTrue(origin(request).pendingIntent());
            java.util.concurrent.atomic.AtomicReference<Object> seen=new java.util.concurrent.atomic.AtomicReference<>();
            Thread thread=new Thread(()->{try{seen.set(origin(request));}catch(Exception failure){seen.set(failure);}});
            thread.start(); thread.join(); assertNull(seen.get()); return null;
        });
    }
}
