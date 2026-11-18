package dev.lsposed.fluidcloud;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class NativeWindowStateTest {
    enum State { Showing, Hiding, Hidden, Future }
    @Test public void mapsRepositoryEnumsWithoutDependingOnOrdinals() {
        assertEquals(0, NativeWindowState.decode(State.Showing));
        assertEquals(1, NativeWindowState.decode(State.Hiding));
        assertEquals(2, NativeWindowState.decode(State.Hidden));
        assertEquals(-1, NativeWindowState.decode(State.Future));
        assertEquals(-1, NativeWindowState.decode(null));
    }
    @Test public void acceptsOnlyKnownLegacyStates() {
        assertEquals(0, NativeWindowState.decode(0));
        assertEquals(1, NativeWindowState.decode(1));
        assertEquals(2, NativeWindowState.decode(2));
        assertEquals(-1, NativeWindowState.decode(3));
        assertEquals(-1, NativeWindowState.decode("Hidden"));
    }
    @Test public void repositoryReadsInitialStateAndTracksOnlyItsDisplayUntilClosed() throws Exception {
        Repository source = new Repository();
        NativeWindowState state = NativeWindowState.repository(source, Repository.class, Callbacks.class,
                Queue.class, StateFlow.class);
        int[] signals = {0};
        state.start(7, () -> signals[0]++);
        assertEquals(2, state.value());
        source.commandQueue.send(0, 1, 0);
        source.commandQueue.send(7, 2, 0);
        assertEquals(2, state.value());
        source.commandQueue.send(7, 1, 1);
        assertEquals(1, state.value());
        assertEquals(1, signals[0]);
        state.close();
        assertTrue(source.commandQueue.callbacks.isEmpty());
    }
    @Test public void legacyKeepsNativeListenerLifecycle() throws Exception {
        Legacy source = new Legacy();
        NativeWindowState state = NativeWindowState.legacy(source, Legacy.class, Listener.class);
        state.start(7, () -> {});
        source.windowState = 1;
        assertEquals(1, state.value());
        state.close();
        assertTrue(source.listeners.isEmpty());
    }
    @Test public void rejectsWrongDisplayBeforeRegistering() throws Exception {
        Repository source = new Repository();
        NativeWindowState state = NativeWindowState.repository(source, Repository.class, Callbacks.class,
                Queue.class, StateFlow.class);
        assertThrows(IllegalStateException.class, () -> state.start(0, () -> {}));
        assertTrue(source.commandQueue.callbacks.isEmpty());
    }
    public interface Listener { void onStatusBarWindowStateChanged(int state); }
    public static class Legacy {
        int thisDisplayId = 7, windowState = 0;
        List<Listener> listeners = new ArrayList<>();
        public void addListener(Listener value) { listeners.add(value); }
        public void removeListener(Listener value) { listeners.remove(value); }
    }
    public interface Callbacks { void setWindowState(int display, int window, int state); }
    public static class Queue {
        List<Callbacks> callbacks = new ArrayList<>();
        public void addCallback(Callbacks value) { callbacks.add(value); }
        public void removeCallback(Callbacks value) { callbacks.remove(value); }
        void send(int d, int w, int s) { for (Callbacks c : List.copyOf(callbacks)) c.setWindowState(d,w,s); }
    }
    public interface StateFlow { Object getValue(); }
    public static class Repository {
        int thisDisplayId = 7;
        Queue commandQueue = new Queue();
        public StateFlow getWindowState() { return () -> State.Hidden; }
    }
}
