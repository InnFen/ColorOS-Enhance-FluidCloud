package dev.lsposed.fluidcloud;

import java.util.Objects;

/** The single display owner. No Android views, native animation fragments or timers. */
final class CloudPresentationCoordinator {
    enum Owner { NATIVE, MODULE, HIDDEN }
    record Input(Object session, int display, Object content, boolean contentAvailable,
                 CloudConfig config, boolean landscape, boolean fullscreen, CloudScene scene,
                 boolean shade, boolean protectedState, boolean nativeVisible) {
        Input { Objects.requireNonNull(session); Objects.requireNonNull(config); }
    }
    record Ticket(long epoch, long revision) { }
    record Frame(Ticket ticket, Object content, Owner owner, double fraction, CapsuleMotion.Pose pose) {
        Frame(Ticket ticket,Object content,Owner owner,double fraction){this(ticket,content,owner,fraction,null);}
    }
    interface Port {
        void prepare(Object content);
        void request(Owner owner, Ticket ticket, Runnable committed);
        void render(Frame frame);
        void cancelTouch();
        void release();
        void failure(Throwable error);
    }
    interface Clock {
        long now();
        default double durationScale(){return 1;}
        void postFrame(Runnable frame);
        void removeFrame(Runnable frame);
    }
    private final Port port;
    private final Clock clock;
    private final Runnable frame = this::onFrame;
    private Input input;
    private Owner committed = Owner.NATIVE, requested = Owner.NATIVE;
    private Ticket ticket = new Ticket(0, 0);
    private final CapsuleMotion motion;
    private double target = 1;
    private long lastTime;
    private boolean queued, waiting, failed, closed, controlled, suspended;

    CloudPresentationCoordinator(Port port, Clock clock) {
        this(port,clock,CapsuleMotion::curve);
    }
    CloudPresentationCoordinator(Port port,Clock clock,java.util.function.DoubleUnaryOperator nativeCurve){
        this.port = Objects.requireNonNull(port); this.clock = Objects.requireNonNull(clock);
        motion=new CapsuleMotion(nativeCurve);
    }
    void update(Input next) {
        if (closed) return;
        if (failed) {
            if (input != null && input.session() == next.session() && input.display() == next.display()
                    && input.landscape() == next.landscape() && Objects.equals(input.content(), next.content())
                    && input.config().equals(next.config())) return;
            failed = false; input = null;
        }
        try {
            Input previous = input;
            boolean rebound = previous == null || previous.session() != next.session()
                    || previous.display() != next.display() || previous.landscape() != next.landscape();
            boolean replaced = previous != null && !Objects.equals(previous.content(), next.content());
            if (rebound) reset();
            else if (replaced) {
                // Rebind content without clearing the valid surface between buffers.
                ticket = new Ticket(ticket.epoch() + 1, 0); port.cancelTouch();
            }
            input = next;
            int policy = policy(next); // -1 native, 0 collapsed, 1 visible
            boolean missing=!next.contentAvailable() || next.content()==null;
            if(missing && !controlled)policy=-1;
            if (policy < 0) {
                controlled = suspended = false;
                stopFrames();
                if (next.protectedState() || !next.config().scenesEnabled() || !next.contentAvailable()) {
                    invalidateTicket(); port.cancelTouch(); port.release();
                    requested = committed = Owner.NATIVE; waiting = false;
                } else request(Owner.NATIVE);
                return;
            }
            if(missing){
                // Native expanded cards temporarily replace the small capsule. Keep
                // the already acquired surface/gate and SDK presentation configuration,
                // but publish no pixels or input. Re-entry must not wait for a new
                // buffer from a status-bar root that the current scene has hidden.
                suspended=true;stopFrames();port.cancelTouch();publish();return;
            }
            boolean wasControlled = controlled;
            boolean wasSuspended=suspended;suspended=false;
            controlled = true;
            port.prepare(next.content());
            long now = clock.now();
            if (rebound || !wasControlled || wasSuspended) {
                // Joining an already hidden scene is not a new hide/show animation.
                target = policy; motion.snap(policy==1); lastTime = now;
            } else if (target != policy) {
                advance(now); target = policy; motion.target(policy==1); lastTime = now;
                invalidateTicket();
            }
            publish();
        } catch (RuntimeException | LinkageError error) { fail(error); }
    }
    private static int policy(Input value) {
        CloudConfig config = value.config();
        if (!config.scenesEnabled() || value.protectedState() || value.display() != 0) return -1;
        if (value.shade()) return config.retainInShade() ? 1 : -1;
        CloudScene scene = value.scene() != null ? value.scene() : value.fullscreen() ? CloudScene.FULLSCREEN : null;
        if (scene != null) return switch (config.mode(scene)) {
            case KEEP -> 1;
            case ANIMATE -> 0;
            case OEM -> -1;
        };
        boolean configured = config.mode(CloudScene.FULLSCREEN) != CloudSceneMode.OEM
                || config.mode(CloudScene.RECENTS) != CloudSceneMode.OEM
                || config.mode(CloudScene.OTHER) != CloudSceneMode.OEM || config.retainInShade()
                || config.capsuleOnTop() || config.keepCapsuleDuringLaunch();
        return configured && value.nativeVisible() ? 1 : -1;
    }
    private void publish() {
        if (!controlled || input == null) return;
        Owner owner = suspended || !moving() && target == 0 ? Owner.HIDDEN : Owner.MODULE;
        CapsuleMotion.Pose pose=suspended?CapsuleMotion.HIDDEN:motion.current();
        if (owner != committed || waiting) {
            port.render(new Frame(ticket, input.content(), owner, pose.visibility(),pose));
            request(owner);
            return;
        }
        port.render(new Frame(ticket, input.content(), owner, pose.visibility(),pose));
        if (moving()) schedule(); else stopFrames();
    }
    private void request(Owner owner) {
        if (!waiting && committed == owner) {
            requested = owner;
            if (controlled && owner != Owner.NATIVE) publish();
            return;
        }
        // The native input host is retained across visual handoffs. Rebinding or
        // protection cancels its stream; moving pixels alone does not create a
        // second input target or interrupt a native shade drag already handed off.
        requested = owner; waiting = true;
        Ticket expected = ticket;
        port.request(owner, expected, () -> {
            if (closed || failed || !expected.equals(ticket) || requested != owner) return;
            try {
                committed = owner; waiting = false; lastTime = clock.now();
                if (controlled && owner != Owner.NATIVE) publish();
            } catch (RuntimeException | LinkageError error) { fail(error); }
        });
    }
    void invalidate() {
        if (!closed && !failed && controlled && committed == Owner.MODULE) schedule();
    }
    private void onFrame() {
        queued = false;
        if (closed || failed || !controlled || waiting || committed != Owner.MODULE) return;
        try {
            port.prepare(input.content()); advance(clock.now()); publish();
        } catch (RuntimeException | LinkageError error) { fail(error); }
    }
    private void advance(long now) {
        if (lastTime == 0 || now <= lastTime) { lastTime = now; return; }
        double dt = (now - lastTime) / 1_000_000d;
        lastTime = now;
        if (!moving()) return;
        double scale=clock.durationScale();
        motion.advance(scale==0?Double.POSITIVE_INFINITY:dt/(Double.isFinite(scale) && scale>0?scale:1));
    }
    private boolean moving() { return !suspended && motion.moving(); }
    private void schedule() { if (!queued) { queued = true; clock.postFrame(frame); } }
    private void stopFrames() { if (queued) { queued = false; clock.removeFrame(frame); } }
    private void invalidateTicket() { ticket = new Ticket(ticket.epoch(), ticket.revision() + 1); }
    private void reset() {
        stopFrames(); ticket = new Ticket(ticket.epoch() + 1, 0);
        waiting = controlled = suspended = false; motion.snap(true);
        port.cancelTouch(); port.release(); requested = committed = Owner.NATIVE;
    }
    boolean retainShade() {
        return !closed && !failed && input != null && input.config().retainInShade()
                && input.display() == 0 && !input.protectedState();
    }
    boolean ownsContent() { return !closed && !failed && controlled; }
    boolean accepts(Ticket value) { return !closed && !failed && value != null && value.equals(ticket); }
    Frame current() {var pose=suspended?CapsuleMotion.HIDDEN:motion.current();return new Frame(ticket, input == null ? null : input.content(), committed, pose.visibility(),pose); }
    private void fail(Throwable error) {
        if (failed || closed) return;
        failed = true;
        try { reset(); } catch (RuntimeException | LinkageError cleanup) { error.addSuppressed(cleanup); }
        try { port.failure(error); } catch (RuntimeException | LinkageError ignored) { }
    }
    void unavailable(Throwable error) { fail(error); }
    void close() {
        if (closed) return;
        try { reset(); }
        finally { closed = true; input = null; }
    }
}
