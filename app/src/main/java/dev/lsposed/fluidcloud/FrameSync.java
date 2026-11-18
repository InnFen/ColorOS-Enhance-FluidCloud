package dev.lsposed.fluidcloud;

import android.view.View;

/** Submits a change of output owner as one compositor frame. */
interface FrameSync {
    default boolean ready(View host, View mirror) { return true; }
    void transfer(View host, View shade, View mirror, Runnable nativeChange,
            Runnable mirrorChange, Runnable committed);
}
