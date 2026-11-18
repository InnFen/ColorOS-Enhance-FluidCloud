package dev.lsposed.fluidcloud;

import android.graphics.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Geometry and fill opacity from the exact OEM voice-print, parsed once per resource. */
record MediaWaveStyle(float width,float height,List<Bar> bars) {
    record Bar(float x,float y,float width,float min,float max,int color) {
        float height(float level){return min+(max-min)*Math.max(0,Math.min(1,level));}
        int alpha(int tint,int drawableAlpha){return Math.round(Color.alpha(color)*Color.alpha(tint)/255f*drawableAlpha/255f);}
    }
    static MediaWaveStyle from(JSONObject asset){
        try{
            float width=(float)asset.getDouble("w"),height=(float)asset.getDouble("h");
            if(!Float.isFinite(width) || !Float.isFinite(height) || width<=0 || height<=0)return null;
            JSONArray layers=asset.getJSONArray("layers");var bars=new ArrayList<Bar>();
            if(layers.length()!=MediaWaveLevels.COUNT)return null;
            for(int i=0;i<layers.length();i++){
                JSONObject layer=layers.getJSONObject(i),transform=layer.getJSONObject("ks");
                JSONArray position=transform.getJSONObject("p").getJSONArray("k");
                JSONObject rect=find(layer.getJSONArray("shapes"),"rc"),fill=find(layer.getJSONArray("shapes"),"fl");
                if(rect==null || fill==null)return null;
                JSONObject size=rect.getJSONObject("s");JSONArray keys=size.getJSONArray("k");
                float barWidth=-1,min=Float.POSITIVE_INFINITY,max=0;
                for(int k=0;k<(size.optInt("a")==0?1:keys.length());k++){
                    JSONArray vector=size.optInt("a")==0?keys:keys.getJSONObject(k).getJSONArray("s");
                    float w=(float)vector.getDouble(0),h=(float)vector.getDouble(1);
                    if(!Float.isFinite(w) || !Float.isFinite(h) || w<=0 || h<w || h>height || (barWidth>0 && w!=barWidth))return null;
                    barWidth=w;min=Math.min(min,h);max=Math.max(max,h);
                }
                JSONArray color=fill.getJSONObject("c").getJSONArray("k");
                double opacity=fill.getJSONObject("o").getDouble("k")*transform.getJSONObject("o").getDouble("k")/10000d;
                if(color.length()>3)opacity*=color.getDouble(3);
                int argb=Color.argb(channel(opacity),channel(color.getDouble(0)),channel(color.getDouble(1)),channel(color.getDouble(2)));
                float x=(float)position.getDouble(0),y=(float)position.getDouble(1);
                if(!Float.isFinite(x) || !Float.isFinite(y) || x<barWidth/2 || x+barWidth/2>width || y<max/2 || y+max/2>height)return null;
                bars.add(new Bar(x,y,barWidth,min,max,argb));
            }
            bars.sort(Comparator.comparingDouble(Bar::x));
            return new MediaWaveStyle(width,height,List.copyOf(bars));
        }catch(org.json.JSONException|IllegalArgumentException error){return null;}
    }
    private static int channel(double value){return (int)Math.round(Math.max(0,Math.min(1,value))*255);}
    private static JSONObject find(JSONArray shapes,String type){
        for(int i=0;i<shapes.length();i++){
            JSONObject item=shapes.optJSONObject(i);if(item==null)continue;
            if(type.equals(item.optString("ty")))return item;
            JSONArray children=item.optJSONArray("it");JSONObject nested=children==null?null:find(children,type);
            if(nested!=null)return nested;
        }
        return null;
    }
}
