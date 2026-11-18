package dev.lsposed.fluidcloud;

/** OEM capsule phases, sampled by our one clock; no vendor animator owns a View. */
final class CapsuleMotion {
    // Panel ExpandEvent selects AnimatorDuration(150, 50), not the generic
    // DisappearAnimatorScene's 300 ms main phase. Keep its linear trajectory.
    private static final double HIDE_BODY_MS=150, HIDE_DOT_MS=50, HIDE_CONTENT_MS=100;
    private final java.util.function.DoubleUnaryOperator expansionCurve;
    CapsuleMotion(){this(CapsuleMotion::curve);}
    CapsuleMotion(java.util.function.DoubleUnaryOperator expansionCurve){this.expansionCurve=java.util.Objects.requireNonNull(expansionCurve);}
    private double ease(double value){return clamp(expansionCurve.applyAsDouble(clamp(value)));}
    record Pose(double body, double dot, double extra, double scale, double alpha,
                double blur, double moon) {
        double visibility() { return Math.max(0, Math.min(1, dot * (.1 + .9 * body))); }
    }
    static final Pose FULL = new Pose(1,1,0,1,1,0,1);
    static final Pose HIDDEN = new Pose(0,0,0,.1,0,1,0);
    private static final Pose DOT = new Pose(0,1,0,.1,0,1,0);
    private static final Pose EXPANDED = new Pose(1,1,1,1.1,1,0,1);
    private Pose current=FULL, start=FULL, growStart=FULL;
    private boolean showing, running;
    private double elapsed, dotDuration, bodyDuration;

    Pose current(){return current;}
    boolean moving(){return running;}
    void snap(boolean visible){current=visible?FULL:HIDDEN;start=current;running=false;elapsed=0;}
    void target(boolean show){
        start=current;showing=show;elapsed=0;
        if(show && current.equals(FULL) || !show && current.equals(HIDDEN)){running=false;return;}
        running=true;
        if(show){
            dotDuration=start.body()<.00001 && start.dot()<1 ? 50*(1-start.dot()):0;
            growStart=dotDuration>0 ? DOT:start;
            bodyDuration=300;
        }else{
            bodyDuration=start.body()>.00001 || start.extra()>.00001 ? HIDE_BODY_MS:0;
            dotDuration=HIDE_DOT_MS;
        }
    }
    void advance(double milliseconds){
        if(!running)return;elapsed+=Math.max(0,milliseconds);
        if(showing){
            if(elapsed<dotDuration){current=mix(start,DOT,ease(elapsed/dotDuration));return;}
            double time=elapsed-dotDuration;
            if(time<bodyDuration){
                double t=ease(time/bodyDuration),delay=growStart.alpha()==0?50:0;
                double fade=ease((time-delay)/(300-delay));
                current=new Pose(lerp(growStart.body(),1,t),1,lerp(growStart.extra(),1,t),
                        lerp(growStart.scale(),1.1,t),lerp(growStart.alpha(),1,fade),
                        lerp(growStart.blur(),0,fade),lerp(growStart.moon(),1,t));return;
            }
            time-=bodyDuration;
            if(time<500){current=mix(EXPANDED,FULL,ease(time/500));return;}
            current=FULL;
        }else{
            if(elapsed<bodyDuration){
                double t=elapsed/bodyDuration,fade=clamp(elapsed/HIDE_CONTENT_MS);
                current=new Pose(lerp(start.body(),0,t),start.dot(),lerp(start.extra(),0,t),
                        lerp(start.scale(),.1,t),lerp(start.alpha(),0,fade),
                        lerp(start.blur(),1,fade),lerp(start.moon(),0,t));return;
            }
            Pose from=bodyDuration>0?DOT:start;
            double t=(elapsed-bodyDuration)/dotDuration;
            if(t<1){current=mix(from,HIDDEN,t);return;}
            current=HIDDEN;
        }
        running=false;
    }
    private static Pose mix(Pose a,Pose b,double t){return new Pose(lerp(a.body(),b.body(),t),
            lerp(a.dot(),b.dot(),t),lerp(a.extra(),b.extra(),t),lerp(a.scale(),b.scale(),t),
            lerp(a.alpha(),b.alpha(),t),lerp(a.blur(),b.blur(),t),lerp(a.moon(),b.moon(),t));}
    private static double lerp(double a,double b,double t){return a+(b-a)*clamp(t);}
    private static double clamp(double value){return Math.max(0,Math.min(1,value));}
    // Native expansion PathInterpolator(0.3, 0, 0.1, 1). Solve x, then evaluate y.
    static double curve(double fraction){
        double x=clamp(fraction),low=0,high=1;
        if(x==0 || x==1)return x;
        for(int i=0;i<24;i++){double t=(low+high)/2,one=1-t;
            double value=3*one*one*t*.3+3*one*t*t*.1+t*t*t;
            if(value<x)low=t;else high=t;
        }
        double t=(low+high)/2;return 3*(1-t)*t*t+t*t*t;
    }
}
