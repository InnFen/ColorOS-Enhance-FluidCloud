package dev.lsposed.fluidcloud;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.FrameLayout;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=36)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class CameraMaterialSurfaceTest {
    @Test public void nativeContourFollowsTheCurrentMorphInsteadOfWaitingOutsideTheClip() {
        var context=Robolectric.buildActivity(Activity.class).setup().get();
        var owner=new FrameLayout(context);
        var nativeStroke=new GradientDrawable();nativeStroke.setColor(Color.TRANSPARENT);
        nativeStroke.setStroke(4,Color.RED);nativeStroke.setCornerRadius(30);
        var proxy=new CardHeaderDrawable(nativeStroke,0,new NativeCardMaterial(type->null));
        var surface=new CameraMaterialSurface(context,owner);
        surface.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(432,View.MeasureSpec.EXACTLY));
        surface.layout(0,0,1000,432);surface.update(List.of(new CameraMaterialSurface.Paint(proxy,1)));
        var image=Bitmap.createBitmap(1000,432,Bitmap.Config.ARGB_8888);
        var canvas=new Canvas(image);
        surface.updateOutline("opening",new Rect(300,90,700,330));surface.draw(canvas);
        assertEquals("The native stroke must exist at the opening shape's edge",Color.RED,image.getPixel(302,210));
        assertEquals(Color.TRANSPARENT,image.getPixel(2,210));
        assertEquals(new Rect(0,0,400,240),nativeStroke.getBounds());
        image.eraseColor(Color.TRANSPARENT);
        surface.updateOutline("expanded",new Rect(0,0,1000,432));surface.draw(canvas);
        assertEquals(Color.RED,image.getPixel(2,210));assertEquals(Color.TRANSPARENT,image.getPixel(302,210));
        assertEquals(new Rect(0,0,1000,432),nativeStroke.getBounds());
        image.eraseColor(Color.TRANSPARENT);
        surface.updateOutline("closing",new Rect(300,90,700,330));surface.draw(canvas);
        assertEquals(Color.RED,image.getPixel(302,210));
        surface.release();proxy.release();
    }
}
