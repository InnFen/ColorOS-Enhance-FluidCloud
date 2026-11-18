package dev.lsposed.fluidcloud;

import android.graphics.Matrix;
import android.graphics.RectF;

/** One window-space path for show, hide and reversals; provider children stay untouched. */
final class CapsulePresentationGeometry {
    record Profile(RectF dot,float extraWidth,float blurRadius) { }
    record Geometry(RectF bounds, Matrix forward, Matrix inverse, Matrix contentForward,
                    float fraction,float contentAlpha,float blurRadius,float moonAlpha,float moonShift) { }
    static Geometry resolve(RectF content, RectF camera, double fraction) {
        if(!valid(content) || !Double.isFinite(fraction))return null;
        if(!valid(camera)){if(fraction<.999)return null;camera=content;}
        float p=(float)Math.max(0,Math.min(1,fraction));
        // DisplayCutout is an avoidance rectangle, often extending to y=0. It is
        // not the visible camera capsule. Keep the native baseline and height;
        // contract the rounded mask horizontally instead of squashing SDK pixels.
        float diameter=Math.min(content.width(),content.height());
        float width=lerp(diameter,content.width(),p),center=lerp(camera.centerX(),content.centerX(),p);
        RectF bounds=new RectF(center-width/2,content.top,center+width/2,content.bottom);
        Matrix matrix=new Matrix();matrix.setTranslate(center-content.centerX(),0);
        Matrix inverse=new Matrix();if(!matrix.invert(inverse))return null;
        return new Geometry(bounds,matrix,inverse,new Matrix(matrix),p,p,0,p,(content.width()-width)/2);
    }
    static Geometry resolve(RectF content,Profile profile,CapsuleMotion.Pose pose){
        if(!valid(content) || profile==null || !valid(profile.dot()) || pose==null)return null;
        RectF dot=profile.dot();float body=(float)pose.body(),open=(float)pose.dot();
        float width=(lerp(dot.width(),content.width(),body)+profile.extraWidth()*(float)pose.extra())*open;
        float height=lerp(dot.height(),content.height(),body)*open;
        if(width<=0 || height<=0)return null;
        float x=lerp(dot.centerX(),content.centerX(),body),y=lerp(dot.centerY(),content.centerY(),body);
        RectF bounds=new RectF(x-width/2,y-height/2,x+width/2,y+height/2);
        Matrix forward=new Matrix();forward.setRectToRect(content,bounds,Matrix.ScaleToFit.FILL);
        Matrix inverse=new Matrix();if(!forward.invert(inverse))return null;
        Matrix pixels=new Matrix();pixels.setScale((float)pose.scale(),(float)pose.scale(),content.centerX(),content.centerY());
        pixels.postTranslate(x-content.centerX(),y-content.centerY());
        return new Geometry(bounds,forward,inverse,pixels,(float)pose.visibility(),(float)pose.alpha(),
                profile.blurRadius()*(float)pose.blur(),(float)pose.moon(),(content.width()-width)/2);
    }
    private static boolean valid(RectF value){return value!=null && !value.isEmpty()
            && Float.isFinite(value.left) && Float.isFinite(value.top) && Float.isFinite(value.right) && Float.isFinite(value.bottom);}
    private static float lerp(float from,float to,float amount){return from+(to-from)*amount;}
}
