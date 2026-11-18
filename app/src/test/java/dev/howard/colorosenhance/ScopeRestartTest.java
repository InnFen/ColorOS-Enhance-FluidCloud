package dev.howard.colorosenhance;
import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;

public class ScopeRestartTest {
    private static final String SUCCESS="COLOROS_SCOPE_OK systemui 100 201\nCOLOROS_SCOPE_OK phone 101 202\nCOLOROS_SCOPE_OK settings 102 203\nCOLOROS_SCOPES_DONE\n";
    @Test public void successRequiresAllThreeVerifiedReplacements() {
        assertEquals(ScopeRestarter.Code.SUCCESS,ScopeRestarter.parse(0,SUCCESS).code());
        for(String invalid:List.of(SUCCESS.replace("100 201","100 100"),SUCCESS.replace("101 202","1 202"),
                SUCCESS.replace("COLOROS_SCOPE_OK phone 101 202\n",""),SUCCESS.replace("systemui","unknown"),
                SUCCESS.replace("COLOROS_SCOPES_DONE","")))
            assertEquals(ScopeRestarter.Code.FAILED,ScopeRestarter.parse(0,invalid).code());
        assertEquals(ScopeRestarter.Code.FAILED,ScopeRestarter.parse(1,SUCCESS).code());
        assertEquals(ScopeRestarter.Code.SUCCESS,ScopeRestarter.parse(0,SUCCESS.replace("settings 102","settings 0")).code());
    }
    @Test public void errorsRetainPartialCompletionAndDeniedRootCannotClaimSuccess() {
        var result=ScopeRestarter.parse(13,"COLOROS_SCOPE_OK systemui 100 201\nCOLOROS_SCOPE_ERROR phone TIMEOUT\n");
        assertEquals(ScopeRestarter.Code.TIMEOUT,result.code()); assertEquals(List.of("systemui"),result.completed());
        assertEquals("phone",result.target());
        assertEquals(ScopeRestarter.Code.ROOT_DENIED,ScopeRestarter.parse(1,"Permission denied").code());
        assertEquals(ScopeRestarter.Code.FAILED,ScopeRestarter.parse(0,"COLOROS_SCOPE_ERROR phone TIMEOUT").code());
    }
    @Test public void repeatedClicksShareOneJobAndOnlyCompletionPublishesResult() {
        ArrayDeque<Runnable> worker=new ArrayDeque<>(),main=new ArrayDeque<>();
        var controller=new ScopeRestartController(worker::add,main::add,()->ScopeRestarter.parse(0,SUCCESS),()->{});
        assertTrue(controller.start()); assertFalse(controller.start()); assertTrue(controller.running());
        assertNull(controller.result()); assertEquals(1,worker.size());
        worker.remove().run(); assertTrue(controller.running()); assertNull(controller.result());
        main.remove().run(); assertFalse(controller.running()); assertEquals(ScopeRestarter.Code.SUCCESS,controller.result().code());
        assertTrue(controller.start());
    }
    @Test public void workerFailureReleasesBusyStateWithoutReportingSuccess() {
        var controller=new ScopeRestartController(Runnable::run,Runnable::run,()->{throw new IllegalStateException();},()->{});
        assertTrue(controller.start()); assertFalse(controller.running()); assertEquals(ScopeRestarter.Code.FAILED,controller.result().code());
    }
    @Test public void outerTimeoutRetainsOnlyCompleteVerifiedRecordsAndNeverReportsSuccess() {
        var timeout=ScopeRestarter.interrupted(ScopeRestarter.Code.TIMEOUT,"COLOROS_SCOPE_OK systemui 100 201\nCOLOROS_SCOPE_OK phone 101 202\nCOLOROS_SCOPE_OK settings 0 ");
        assertEquals(List.of("systemui","phone"),timeout.completed()); assertEquals("settings",timeout.target());
        assertEquals(ScopeRestarter.Code.TIMEOUT,timeout.code());
        assertEquals(ScopeRestarter.Code.TIMEOUT,ScopeRestarter.interrupted(ScopeRestarter.Code.TIMEOUT,SUCCESS).code());
        assertTrue(ScopeRestarter.interrupted(ScopeRestarter.Code.TIMEOUT,"COLOROS_SCOPE_OK systemui 100 100\n").completed().isEmpty());
    }
}
