package dev.howard.colorosenhance.installer;

import android.content.Intent;
import android.content.pm.ActivityInfo;

/** One ActivityStarter.execute call; no app-supplied bypass marker or persistent allowlist. */
final class InstallerLaunchScope {
    @FunctionalInterface interface Operation {Object run() throws Throwable;}
    private static final class Frame {
        Intent intent,snapshot;
        InstallerRouting.Origin origin;
        int user;
    }
    private final ThreadLocal<Frame> current=new ThreadLocal<>();
    static boolean ordinaryConfirmation(int type) {return type==0 || type==5 || type==6;}
    Object within(Operation operation) throws Throwable {
        Frame previous=current.get();current.set(new Frame());
        try{return operation.run();}
        finally{if(previous==null)current.remove();else current.set(previous);}
    }
    void mark(Intent intent,InstallerRouting.Origin origin,int user) {
        Frame frame=current.get();if(frame==null)return;
        frame.intent=intent;frame.snapshot=new Intent(intent);frame.origin=origin;frame.user=user;
    }
    boolean matches(Intent intent,ActivityInfo activity,int uid,String caller,int user,String target) {
        Frame frame=current.get();
        return frame!=null && frame.intent==intent && frame.origin!=null && !frame.origin.pendingIntent()
                && frame.user==user && frame.origin.uid()==uid && frame.origin.packageName().equals(caller)
                && frame.snapshot.filterEquals(intent) && frame.snapshot.getFlags()==intent.getFlags()
                && intent.getComponent()!=null && target.equals(intent.getComponent().getPackageName())
                && activity!=null && target.equals(activity.packageName)
                && intent.getComponent().getClassName().equals(activity.name)
                && activity.applicationInfo!=null && target.equals(activity.applicationInfo.packageName)
                && activity.applicationInfo.uid>=0 && activity.applicationInfo.uid/100000==user;
    }
}
