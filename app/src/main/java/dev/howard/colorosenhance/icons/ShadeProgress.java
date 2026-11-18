package dev.howard.colorosenhance.icons;

/** Separate notification and control centers publish independent native expansion fractions. */
final class ShadeProgress {
    private float notification, controls;
    float notification(float value) { notification=bounded(value,notification);return Math.max(notification,controls); }
    float controls(float value) { controls=bounded(value,controls);return Math.max(notification,controls); }
    private static float bounded(float value,float previous) {
        return Float.isFinite(value) ? Math.max(0,Math.min(1,value)) : previous;
    }
}
