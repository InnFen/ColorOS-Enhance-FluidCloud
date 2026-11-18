package dev.lsposed.fluidcloud;

import android.os.Handler;
import android.os.SystemClock;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** A single native click can wait for a real panel collapse; no stored MotionEvents. */
final class ShadeOpenRequest {
    private final Handler main;
    private final BooleanSupplier expanded;
    private final Runnable collapse;
    private final Consumer<Throwable> failed;
    private Runnable pending;
    ShadeOpenRequest(Handler main,BooleanSupplier expanded,Runnable collapse,Consumer<Throwable> failed){
        this.main=main;this.expanded=expanded;this.collapse=collapse;this.failed=failed;
    }
    void submit(Runnable open,BooleanSupplier valid){
        cancel();long deadline=SystemClock.uptimeMillis()+1500;
        pending=new Runnable(){
            public void run(){
                if(pending!=this)return;
                try{
                    if(!valid.getAsBoolean()){cancel();return;}
                    if(!expanded.getAsBoolean()){pending=null;open.run();return;}
                    if(SystemClock.uptimeMillis()>=deadline){cancel();failed.accept(new IllegalStateException("Panel collapse timed out; native click not replayed"));return;}
                    main.postDelayed(this,16);
                }catch(RuntimeException|LinkageError error){cancel();failed.accept(error);}
            }
        };
        try{collapse.run();main.post(pending);}
        catch(RuntimeException|LinkageError error){cancel();failed.accept(error);}
    }
    void cancel(){if(pending!=null)main.removeCallbacks(pending);pending=null;}
}
