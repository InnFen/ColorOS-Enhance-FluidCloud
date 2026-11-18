package com.coui.appcompat.seekbar;

import android.content.Context;
import android.view.View;

/** Mirrors COS17's custom-View listener contract, deliberately not a platform SeekBar. */
public class COUISeekBar extends View {
    public interface OnSeekBarChangeListener {
        void onProgressChanged(COUISeekBar view,int progress,boolean fromUser);
        void onStartTrackingTouch(COUISeekBar view);
        void onStopTrackingTouch(COUISeekBar view);
    }
    private int progress,max;
    private OnSeekBarChangeListener listener;
    public COUISeekBar(Context context){super(context);}
    public void setMax(int value){max=value;}
    public int getMax(){return max;}
    public int getProgress(){return progress;}
    public void setProgress(int value){progress=value;if(listener!=null)listener.onProgressChanged(this,value,false);}
    public void setOnSeekBarChangeListener(OnSeekBarChangeListener value){listener=value;}
    public void start(){listener.onStartTrackingTouch(this);}
    public void move(int value){progress=value;listener.onProgressChanged(this,value,true);}
    public void stop(){listener.onStopTrackingTouch(this);}
}
