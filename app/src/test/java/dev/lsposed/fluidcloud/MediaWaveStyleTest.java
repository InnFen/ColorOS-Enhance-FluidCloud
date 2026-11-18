package dev.lsposed.fluidcloud;

import android.graphics.Color;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=36,manifest=Config.NONE)
public class MediaWaveStyleTest {
    private JSONObject asset()throws Exception{
        var layers=new JSONArray();
        for(int i=5;i>=0;i--)layers.put(new JSONObject("""
          {"ks":{"p":{"k":[%s,36,0]},"o":{"k":100}},"shapes":[{"ty":"gr","it":[
           {"ty":"rc","s":{"a":1,"k":[{"s":[6,6]},{"s":[6,43]}]}},
           {"ty":"fl","c":{"k":[1,1,1,1]},"o":{"k":80}}]}]}
          """.formatted(10+i*10)));
        return new JSONObject().put("w",72).put("h",72).put("layers",layers);
    }
    @Test public void nativeInsetsWidthHeightAndSixBarOrderArePreserved()throws Exception{
        var style=MediaWaveStyle.from(asset());assertNotNull(style);assertEquals(6,style.bars().size());
        for(int i=0;i<6;i++){
            var bar=style.bars().get(i);assertEquals(10+i*10,bar.x(),0);assertEquals(6,bar.width(),0);
            assertEquals(6,bar.height(0),0);assertEquals(43,bar.height(1),0);
            assertTrue(bar.max()/style.height()<.6f);
        }
    }
    @Test public void nativeEightyPercentFillSurvivesOpaqueTintAndDrawableAlpha()throws Exception{
        var bar=MediaWaveStyle.from(asset()).bars().getFirst();
        assertEquals(204,bar.alpha(Color.WHITE,255));assertEquals(102,bar.alpha(Color.WHITE,128));
        assertEquals(51,bar.alpha(0x80ffffff,128));
    }
    @Test public void unknownOrMalformedAssetKeepsNativeRendering()throws Exception{
        var asset=asset();asset.getJSONArray("layers").remove(0);assertNull(MediaWaveStyle.from(asset));
        assertNull(MediaWaveStyle.from(new JSONObject()));
        asset=asset();asset.put("w",1);assertNull(MediaWaveStyle.from(asset));
    }
}
