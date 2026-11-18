package dev.lsposed.fluidcloud;

import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=36, application=android.app.Application.class)
public class NativeCardGlassTest {
    @Test public void reusedGlassKeepsItsStockProfileAcrossRadiusAndCurveChanges() throws Exception {
        var glass = new NativeCardGlass.Materials();
        var drawable = new ColorDrawable();
        var stock = new NativeCardGlass.Shape(2, 1.1f, 31, 32, 33, 34);
        var surface = new TestSurface(stock);
        var square = NativeCardGlass.Shape.uniform(1, 0, 0);
        var continuous = NativeCardGlass.Shape.uniform(2, 3, 64);
        glass.apply(drawable, surface, square);
        assertEquals(square, surface.shape);
        glass.apply(drawable, surface, square);
        assertEquals(1, surface.writes);
        glass.apply(drawable, surface, continuous);
        assertEquals(continuous, surface.shape);
        glass.restore(drawable);
        assertEquals(stock, surface.shape);
    }

    @Test public void externalProfileReplacementIsNeverOverwrittenByRestore() throws Exception {
        var glass = new NativeCardGlass.Materials();
        var drawable = new ColorDrawable();
        var surface = new TestSurface(NativeCardGlass.Shape.uniform(2, 3, 28));
        var custom = NativeCardGlass.Shape.uniform(1, 0, 12);
        var newStock = NativeCardGlass.Shape.uniform(2, 3, 48);
        glass.apply(drawable, surface, custom);
        surface.shape = newStock;
        glass.restore(drawable);
        assertEquals(newStock, surface.shape);
        assertEquals(1, surface.writes);
    }

    @Test public void cameraExtensionWrapperResolvesTheOriginalDrawableHost() {
        var drawable = new ColorDrawable();
        var nested = new android.graphics.drawable.InsetDrawable(drawable, 0);
        var wrapper = new CardHeaderDrawable(nested, 12, new NativeCardMaterial());
        var host = new View(RuntimeEnvironment.getApplication());
        host.setForeground(wrapper);
        assertSame(host, NativeCardGlass.Materials.drawableHost(drawable));
    }

    private static final class TestSurface implements NativeCardGlass.Surface {
        NativeCardGlass.Shape shape;
        int writes;
        TestSurface(NativeCardGlass.Shape shape) { this.shape = shape; }
        @Override public NativeCardGlass.Shape read(Drawable drawable) { return shape; }
        @Override public void write(Drawable drawable, NativeCardGlass.Shape replacement) {
            shape = replacement;
            writes++;
        }
    }
}
