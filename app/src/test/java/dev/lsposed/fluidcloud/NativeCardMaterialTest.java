package dev.lsposed.fluidcloud;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.drawable.Drawable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=36)
public class NativeCardMaterialTest {
    @Test public void shaderTracksExtendedBoundsWithoutInvalidatingSettledFramesAndRestores() throws Exception {
        var contract=NativeCardMaterial.resolve(Stroke.class,Pair.class);
        var material=new NativeCardMaterial(type->type==Stroke.class?contract:null);var stroke=new Stroke();
        material.resize(stroke,1159,445);assertEquals(new NativeCardMaterial.Size(1159,445),contract.read(stroke));
        for(int i=0;i<30;i++)material.resize(stroke,1159,445);
        assertEquals(1,stroke.updates);
        stroke.d(1159,330,0,0);material.resize(stroke,1159,468); // native refresh after a card content update
        assertEquals(new NativeCardMaterial.Size(1159,468),contract.read(stroke));
        material.restore(stroke);assertEquals(new NativeCardMaterial.Size(1159,330),contract.read(stroke));
    }
    @Test public void nativeUpdatesAndUnknownMaterialsAreNotOverwrittenOnRestore() throws Exception {
        var contract=NativeCardMaterial.resolve(Stroke.class,Pair.class);
        var material=new NativeCardMaterial(type->type==Stroke.class?contract:null);var stroke=new Stroke();
        material.resize(stroke,1159,445);stroke.d(1159,360,0,0);material.restore();
        assertEquals(new NativeCardMaterial.Size(1159,360),contract.read(stroke));
        var ordinary=new android.graphics.drawable.ColorDrawable(0xff112233);ordinary.setBounds(0,0,100,200);
        material.resize(ordinary,100,300);assertEquals(200,ordinary.getBounds().height());
    }
    public record Pair(Object first,Object second) { public Object a(){return first;}public Object b(){return second;} }
    public static class Stroke extends Drawable {
        private Pair c=new Pair(1159f,307f);int updates;
        public void d(float width,float height,float x,float y){c=new Pair(width,height);updates++;}
        @Override public void draw(Canvas canvas) { }
        @Override public void setAlpha(int value) { }
        @Override public void setColorFilter(ColorFilter filter) { }
        @Override public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
    }
}
