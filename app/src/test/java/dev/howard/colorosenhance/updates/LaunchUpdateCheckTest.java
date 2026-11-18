package dev.howard.colorosenhance.updates;

import static org.junit.Assert.*;
import java.util.ArrayDeque;
import java.util.concurrent.Executor;
import org.junit.Test;

public class LaunchUpdateCheckTest {
    @Test public void checksOncePerVisitAndNotOnEachSettingsPage() {
        Fixture f=new Fixture();
        f.checks.check();assertEquals(0,f.worker.size()); // Services/background launch cannot start a check.
        f.checks.foreground();f.checks.check();
        for(int i=0;i<5;i++){f.checks.foreground();f.checks.check();}
        assertEquals(1,f.worker.size());f.complete();
        assertEquals(f.release,f.checks.pending());
        f.checks.background();assertNull(f.checks.pending());
        f.checks.foreground();f.checks.check();assertEquals(1,f.worker.size());
        f.complete();assertEquals(2,f.requests);
    }
    @Test public void failureEqualAndOlderVersionsNeverPromptOrRetryInSameVisit() {
        for(GitHubUpdateClient.Result result:new GitHubUpdateClient.Result[]{null,
                new GitHubUpdateClient.Result(null,"offline"),
                new GitHubUpdateClient.Result(release(69),null),new GitHubUpdateClient.Result(release(68),null)}) {
            Fixture f=new Fixture();f.result=result;
            f.checks.foreground();f.checks.check();f.complete();
            assertNull(f.checks.pending());assertEquals(0,f.notifications);
            f.checks.check();assertEquals(0,f.worker.size());
        }
    }
    @Test public void dismissingDoesNotRedisplayUntilNextVisit() {
        Fixture f=new Fixture();f.checks.foreground();f.checks.check();f.complete();
        f.checks.dismiss();f.checks.check();f.checks.foreground();f.checks.check();
        assertNull(f.checks.pending());assertTrue(f.worker.isEmpty());
        f.checks.background();f.checks.foreground();f.checks.check();f.complete();
        assertEquals(f.release,f.checks.pending());
    }
    @Test public void lateBackgroundResultDoesNotOpenAnOverlayOrPersistAPendingDialog() {
        Fixture f=new Fixture();f.checks.foreground();f.checks.check();
        f.worker.run();assertNull(f.checks.pending()); // Network completed; UI callback still queued.
        f.checks.background();int before=f.notifications;f.main.run();
        assertEquals(before,f.notifications);assertNull(f.checks.pending());
        f.checks.foreground();assertNull(f.checks.pending());
        f.checks.check();assertEquals(1,f.worker.size());
    }
    @Test public void quickReopeningReusesTheRunningRequestWithoutParallelNetworkWork() {
        Fixture f=new Fixture();f.checks.foreground();f.checks.check();
        f.checks.background();f.checks.foreground();f.checks.check();
        assertEquals(1,f.worker.size());f.complete();assertEquals(1,f.requests);
        assertEquals(f.release,f.checks.pending());
    }
    @Test public void unexpectedFailureAndRejectedWorkerStaySilent() {
        Queue worker=new Queue(),main=new Queue();
        LaunchUpdateCheck check=new LaunchUpdateCheck(worker,main,()->{throw new IllegalStateException();},69,()->fail("No error popup"));
        check.foreground();check.check();worker.run();main.run();assertNull(check.pending());
        check=new LaunchUpdateCheck(task->{throw new java.util.concurrent.RejectedExecutionException();},main,()->null,69,()->fail("No error popup"));
        check.foreground();check.check();assertNull(check.pending());
    }
    static ReleaseInfo release(long code) { return new ReleaseInfo(code,"1.31.0","Notes",ReleaseInfo.RELEASES_URL,""); }
    static class Queue extends ArrayDeque<Runnable> implements Executor {
        public void execute(Runnable task){add(task);}
        void run(){remove().run();}
    }
    static class Fixture {
        final Queue worker=new Queue(),main=new Queue();
        final ReleaseInfo release=release(70);
        GitHubUpdateClient.Result result=new GitHubUpdateClient.Result(release,null);
        int requests,notifications;
        final LaunchUpdateCheck checks=new LaunchUpdateCheck(worker,main,()->{requests++;return result;},69,()->notifications++);
        void complete(){worker.run();main.run();}
    }
}
