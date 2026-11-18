package dev.lsposed.fluidcloud;

import android.view.AttachedSurfaceControl;
import android.view.SurfaceControl;
import android.view.View;
import android.window.SurfaceSyncGroup;
import java.util.ArrayList;
import java.util.List;

/** Uses public Android surface synchronization; no vendor animation timings. */
final class NativeFrameSync implements FrameSync {
    @Override public boolean ready(View host, View mirror) {
        return host.isAttachedToWindow() && mirror.isAttachedToWindow()
                && mirror.getWidth() > 0 && mirror.getHeight() > 0
                && host.getDisplay() != null && mirror.getDisplay() != null
                && host.getDisplay().getDisplayId() == mirror.getDisplay().getDisplayId()
                && host.getRootSurfaceControl() != null && mirror.getRootSurfaceControl() != null;
    }

    @Override public void transfer(View host, View shade, View mirror, Runnable nativeChange,
            Runnable mirrorChange, Runnable committed) {
        List<Participant> participants = new ArrayList<>();
        add(participants, host, nativeChange, mirror);
        // A firmware can omit the fake container. A detached one cannot contribute a buffer.
        if (shade != null && shade.isAttachedToWindow()) add(participants, shade, () -> { }, mirror);
        add(participants, mirror, mirrorChange, mirror);
        SurfaceSyncGroup group = new SurfaceSyncGroup("FluidCloudUnbound-handoff");
        try {
            for (Participant participant : participants) {
                if (!group.add(participant.surface, () -> {
                    for (Runnable change : participant.changes) change.run();
                    participant.root.invalidate();
                })) throw new IllegalStateException("Could not synchronize capsule surfaces");
            }
            // markSyncReady and frame-recording callbacks do not mean the transaction committed.
            try (SurfaceControl.Transaction transaction = new SurfaceControl.Transaction()) {
                transaction.addTransactionCommittedListener(mirror.getContext().getMainExecutor(),
                        committed::run);
                group.addTransaction(transaction);
            }
        } finally {
            // add() can run its mutation even when it returns false. Caller must restore native
            // output on failure, and partially enrolled roots must always be released.
            group.markSyncReady();
        }
    }

    private static void add(List<Participant> participants, View view, Runnable change, View mirror) {
        if (!view.isAttachedToWindow() || view.getDisplay() == null || mirror.getDisplay() == null
                || view.getDisplay().getDisplayId() != mirror.getDisplay().getDisplayId())
            throw new IllegalStateException("Capsule surface detached or on another display");
        AttachedSurfaceControl surface = view.getRootSurfaceControl();
        if (surface == null) throw new IllegalStateException("Capsule root has no surface control");
        for (Participant participant : participants) {
            if (participant.surface == surface) { participant.changes.add(change); return; }
        }
        Participant participant = new Participant(surface, view.getRootView());
        participant.changes.add(change);
        participants.add(participant);
    }

    private static final class Participant {
        final AttachedSurfaceControl surface;
        final View root;
        final List<Runnable> changes = new ArrayList<>();
        Participant(AttachedSurfaceControl surface, View root) { this.surface = surface; this.root = root; }
    }
}
