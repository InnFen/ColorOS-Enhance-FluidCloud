package dev.howard.colorosenhance.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;
import dev.lsposed.fluidcloud.CloudCorners;
import dev.lsposed.fluidcloud.MediaWaveBands;
import dev.lsposed.fluidcloud.MediaWaveOptions;
import java.util.Map;

/**
 * Self-drawn ColorOS simulation, not a recording or a system View. No media, preference or service
 * access. Geometry follows the cached camera-card study and the capsule phase timings documented
 * in cloud-presentation-v2-design; material, wallpaper and sample content are deliberately synthetic.
 */
public final class FeatureDemoView extends View {
    private static final int WHITE=0xffeff1f5,SECONDARY=0xffb6bbcc;
    private static final Typeface NORMAL=Typeface.create("sans-serif",Typeface.NORMAL);
    private static final int[] DEMO_TONES_HZ = {80, 220, 520, 1200, 3200, 9000};
    private final String[] bandLabels = new String[MediaWaveBands.EDGE_COUNT - 1];
    private final int[] demoToneBands = new int[DEMO_TONES_HZ.length];
    private final float[] demoLevels = new float[bandLabels.length];
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect=new RectF();
    private final Path outline=new Path(),path=new Path();
    private final PathInterpolator expansion=new PathInterpolator(.3f,0,.1f,1);
    private final String kind;
    private String route="";
    private int scene=1;
    private Map<String,?> values=Map.of();
    private ValueAnimator animator;
    private float progress=1;
    private boolean started;
    private int ink,muted,accent,surface;
    private Shader wallpaper,material;
    private final Runnable replay=this::play;
    public FeatureDemoView(Context context,String kind){
        super(context);this.kind=kind;
        // COUI enables Android force-dark on its host window. This canvas already owns its
        // palette; automatic inversion would turn the simulated black camera/material white.
        setForceDarkAllowed(false);
        boolean dark=(getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        ink=dark?0xffeeeeef:0xff24272c;muted=dark?0xff989da7:0xff7b8190;
        accent=0xff4587ff;surface=dark?0xff25272e:0xfff1f4fa;refreshMaterials();
        setMinimumHeight(Math.round(210*getResources().getDisplayMetrics().density));
        presentation(""); updateBandLabels();
        setOnClickListener(view->{if(kind.equals("scenes"))scene=(scene+1)%3;play();});
        setSaveEnabled(false);
    }
    /** Selects a presentation context without reading or writing preferences. */
    public void presentation(String route){
        this.route=route==null?"":route;
        setContentDescription(kind.equals("scenes")?
                "ColorOS 模拟预览，点按切换全屏、最近任务、下拉状态栏并重播。尺寸与材质以实际系统为准，不会修改设置。":
                "ColorOS 模拟预览，点按重播。尺寸与材质以实际系统为准，不采集声音，不会修改设置。");
        invalidate();
    }
    public void values(Map<String,?> next) {
        values = next == null ? Map.of() : Map.copyOf(next);
        updateBandLabels();
        invalidate();
    }
    private void updateBandLabels() {
        if (!"media".equals(kind)) return;
        MediaWaveBands bands = MediaWaveBands.from(values);
        for (int index = 0; index < bandLabels.length; index++) {
            bandLabels[index] = bands.edgeHz(index) + "–" + bands.edgeHz(index + 1);
        }
        java.util.Arrays.fill(demoToneBands, -1);
        for (int tone = 0; tone < DEMO_TONES_HZ.length; tone++) {
            int hz = DEMO_TONES_HZ[tone];
            for (int band = 0; band < bandLabels.length; band++) {
                if (hz >= bands.edgeHz(band) && hz < bands.edgeHz(band + 1)) {
                    demoToneBands[tone] = band;
                    break;
                }
            }
        }
    }
    /** The standalone UI supplies its theme without changing the Settings host configuration. */
    public void palette(int foreground,int secondary,int highlight,int background){
        if(ink==foreground&&muted==secondary&&accent==highlight&&surface==background)return;
        ink=foreground;muted=secondary;accent=highlight;surface=background;refreshMaterials();invalidate();
    }
    private void refreshMaterials(){
        wallpaper=new LinearGradient(0,8,290,166,new int[]{0xff132439,blend(0xff233956,accent,.20f),0xff142136},null,Shader.TileMode.CLAMP);
        material=new LinearGradient(0,13,0,150,new int[]{0xff151921,0xff2c3546,0xff46536b},new float[]{0,.45f,1},Shader.TileMode.CLAMP);
    }
    public void play(){
        stop();
        if(!isAttachedToWindow()||!isShown()||!ValueAnimator.areAnimatorsEnabled()||kind.equals("battery")){progress=1;invalidate();return;}
        ValueAnimator next=ValueAnimator.ofFloat(0,1);animator=next;next.setDuration(3200);
        // The OEM phases below are elapsed time; a second interpolator would distort their timing.
        next.setInterpolator(new LinearInterpolator());
        next.addUpdateListener(value->{progress=(float)value.getAnimatedValue();invalidate();});
        next.addListener(new AnimatorListenerAdapter(){
            @Override public void onAnimationEnd(Animator ended){
                next.removeAllUpdateListeners();next.removeAllListeners();if(animator==next)animator=null;
            }
        });next.start();
    }
    public void stop(){
        if(replay!=null)removeCallbacks(replay);
        ValueAnimator old=animator;animator=null;
        if(old!=null){old.cancel();old.removeAllUpdateListeners();old.removeAllListeners();}progress=1;
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();if(!started){started=true;post(replay);}}
    @Override protected void onDetachedFromWindow(){removeCallbacks(replay);stop();super.onDetachedFromWindow();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(visibility!=VISIBLE){removeCallbacks(replay);stop();}}
    @Override public void onVisibilityAggregated(boolean visible){super.onVisibilityAggregated(visible);if(!visible){removeCallbacks(replay);stop();}}
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int width=MeasureSpec.getSize(widthSpec);
        setMeasuredDimension(resolveSize(width,widthSpec),resolveSize(Math.round(width*.56f),heightSpec));
    }
    private boolean flag(String key){return Boolean.TRUE.equals(values.get(key));}
    private boolean enabled(){return !Boolean.FALSE.equals(values.get("enabled"));}
    private int number(String key,int fallback){Object value=values.get(key);return value instanceof Number n?n.intValue():fallback;}
    private int bounded(String key,int fallback,int min,int max){return Math.max(min,Math.min(max,number(key,fallback)));}
    private static float clamp(float value){return Math.max(0,Math.min(1,value));}
    private float curve(float value){return expansion.getInterpolation(clamp(value));}
    private float opening(){
        // Open, close, then reopen. The finite clock ends on a useful, visible preview.
        if(progress<.10f)return 0;if(progress<.30f)return curve((progress-.10f)/.20f);
        if(progress<.46f)return 1;if(progress<.64f)return 1-curve((progress-.46f)/.18f);
        if(progress<.74f)return 0;return curve((progress-.74f)/.20f);
    }
    private void box(Canvas c,float left,float top,float right,float bottom,float radius,int color){
        paint.setShader(null);paint.setColor(color);paint.setStyle(Paint.Style.FILL);rect.set(left,top,right,bottom);c.drawRoundRect(rect,radius,radius,paint);
    }
    private void line(Canvas c,float x1,float y1,float x2,float y2,float width,int color){
        paint.setShader(null);paint.setColor(color);paint.setStrokeWidth(width);paint.setStrokeCap(Paint.Cap.ROUND);c.drawLine(x1,y1,x2,y2,paint);
    }
    private void label(Canvas c,String text,float x,float y,float size,int color){
        paint.setShader(null);paint.setColor(color);paint.setTypeface(NORMAL);paint.setTextSize(size);paint.setStyle(Paint.Style.FILL);c.drawText(text,x,y,paint);
    }
    private void circle(Canvas c,float x,float y,float radius,int color){paint.setShader(null);paint.setColor(color);paint.setStyle(Paint.Style.FILL);c.drawCircle(x,y,radius,paint);}
    private void roundedPath(float left,float top,float right,float bottom,float radius){outline.reset();rect.set(left,top,right,bottom);outline.addRoundRect(rect,radius,radius,Path.Direction.CW);}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);int outer=canvas.save();canvas.scale(getWidth()/360f,getHeight()/202f);
        // All moving cards and gradients share a display crop; none may paint across the caption.
        roundedPath(10,8,350,166,22);int screen=canvas.save();canvas.clipPath(outline);
        switch(kind){case "battery"->battery(canvas);case "aod"->aod(canvas);case "icons"->icons(canvas);
            case "media"->media(canvas);case "camera"->camera(canvas);case "scenes"->scenes(canvas);default->capsule(canvas);}
        canvas.restoreToCount(screen);
        label(canvas,kind.equals("scenes")?"模拟预览 · 点按切换场景并重播":"模拟预览 · 点按重播",12,187,10,muted);
        label(canvas,"尺寸、材质与内容以实际系统为准",12,200,8,muted);canvas.restoreToCount(outer);
    }
    private void backdrop(Canvas c){
        paint.setShader(wallpaper);paint.setColor(Color.WHITE);paint.setStyle(Paint.Style.FILL);c.drawRect(10,8,350,166,paint);paint.setShader(null);
        // Synthetic wallpaper: never include a user's screenshot or an application's artwork.
        path.reset();path.moveTo(10,149);path.cubicTo(100,32,167,191,350,75);path.lineTo(350,166);path.lineTo(10,166);path.close();
        paint.setColor(0x284e729c);c.drawPath(path,paint);
    }
    private void phone(Canvas c){backdrop(c);status(c,1,1);cameraHole(c);}
    private void cameraHole(Canvas c){circle(c,180,26,5,Color.BLACK);}
    private void status(Canvas c,float clockAlpha,float rightAlpha){
        label(c,"9:41",25,30,10,alpha(WHITE,clockAlpha));wifi(c,286,23,alpha(WHITE,rightAlpha));
        for(int i=0;i<4;i++)box(c,299+i*3,30-i*2,301+i*3,32,1,alpha(WHITE,rightAlpha));smallBattery(c,316,23,alpha(WHITE,rightAlpha));
    }
    private void smallBattery(Canvas c,float x,float y,int color){
        box(c,x,y,x+18,y+9,2,color);box(c,x+19,y+3,x+20,y+6,1,color);box(c,x+2,y+2,x+5,y+7,1,alpha(0xff27374c,Color.alpha(color)/255f));
    }
    private void wifi(Canvas c,float x,float y,int color){
        paint.setShader(null);paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.4f);paint.setStrokeCap(Paint.Cap.ROUND);
        rect.set(x-5,y-1,x+5,y+9);c.drawArc(rect,220,100,false,paint);rect.set(x-3,y+2,x+3,y+8);c.drawArc(rect,220,100,false,paint);circle(c,x,y+7,1,color);
    }
    private void bluetooth(Canvas c,float x,float y,int color){
        line(c,x,y-7,x,y+7,1.3f,color);line(c,x,y-7,x+4,y-3,1.3f,color);line(c,x+4,y-3,x-4,y+4,1.3f,color);
        line(c,x,y+7,x+4,y+3,1.3f,color);line(c,x+4,y+3,x-4,y-4,1.3f,color);
    }
    private void pill(Canvas c,float width,float height,float content,float opacity,boolean stroke){
        if(enabled()&&flag("capsule_size_enabled")){
            width*=bounded("capsule_width_percent",100,50,200)/100f;
            height=Math.min(48,height*bounded("capsule_height_percent",100,50,200)/100f);
        }
        int layer=c.saveLayerAlpha(30,0,330,52,Math.round(clamp(opacity)*255));
        float radius=height/2;
        boolean custom=enabled()&&flag("capsule_corners_enabled");
        boolean continuous=custom&&flag("capsule_continuous_corners");
        if(custom)radius=Math.min(radius,bounded("capsule_corner_dp",16,0,24));
        rect.set(180-width/2,26-height/2,180+width/2,26+height/2);
        CloudCorners.path(outline,rect,radius,continuous);
        paint.setColor(Color.BLACK);c.drawPath(outline,paint);
        if(stroke&&width>25){paint.setStyle(Paint.Style.STROKE);paint.setColor(0xff69707d);paint.setStrokeWidth(.65f);
            c.drawPath(outline,paint);paint.setStyle(Paint.Style.FILL);}
        if(content>.01f){int inner=c.saveLayerAlpha(112,12,248,42,Math.round(clamp(content)*255));
            c.clipPath(outline);
            album(c,130,18,16);waveBars(c,205,26,2,3,8,false,false);c.restoreToCount(inner);}
        c.restoreToCount(layer);cameraHole(c);
    }
    private void album(Canvas c,float left,float top,float size){
        box(c,left,top,left+size,top+size,size*.25f,blend(accent,0xff4bc6bc,.35f));
        circle(c,left+size*.52f,top+size*.50f,size*.27f,0xffd9e4ec);circle(c,left+size*.52f,top+size*.50f,size*.075f,0xff345669);
    }
    private void homeContent(Canvas c){
        label(c,"11 月 6 日  星期五",28,64,10,SECONDARY);label(c,"晴  26°",28,87,22,WHITE);
        for(int i=0;i<4;i++){box(c,28+i*79,112,75+i*79,150,12,0x245e7fa1);circle(c,51+i*79,131,8,blend(accent,WHITE,i*.17f));}
    }
    private void controls(Canvas c,float fraction){
        int save=c.saveLayerAlpha(10,34,350,166,Math.round(clamp(fraction)*255));float dy=(1-fraction)*-40;c.translate(0,dy);
        box(c,10,38,350,166,0,0xf1202c3e);
        box(c,25,49,174,88,14,0xff3c7aea);wifi(c,43,62,WHITE);label(c,"无线局域网",61,73,10,WHITE);
        box(c,184,49,334,88,14,0xff3a485c);label(c,"蓝牙",222,73,10,WHITE);
        box(c,25,99,174,141,14,0xff46566c);label(c,"媒体控制",44,116,9,WHITE);label(c,"示例音频",44,131,8,SECONDARY);
        box(c,184,99,254,141,14,0xff56677e);box(c,264,99,334,141,14,0xff56677e);
        circle(c,219,119,7,WHITE);label(c,"♪",294,125,17,WHITE);c.restoreToCount(save);
    }
    private void scenes(Canvas c){
        phone(c);float time=progress*3200;
        float p=time<600?0:time<900?curve((time-600)/300):time<1750?1:time<2250?1-curve((time-1750)/500):curve((time-2750)/250);
        boolean landscape=route.equals("cloud_landscape"),inherited=landscape&&flag("landscape_follow_portrait");
        String prefix=landscape&&!inherited?"landscape_":"";
        String keepKey=switch(scene){case 0->"fullscreen";case 1->"recents";default->"shade_keep";};
        String animationKey=scene==0?"fullscreen_animation":"recents_shrink";
        boolean unbound=enabled()&&flag("unbind_status_bar"),keep=unbound&&flag(prefix+keepKey);
        boolean animate=unbound&&scene!=2&&flag(prefix+animationKey)&&!keep;
        homeContent(c);
        if(scene==1){
            int recents=c.saveLayerAlpha(10,40,350,155,Math.round(p*255));
            box(c,28,48,332,154,16,alpha(0xff162236,p));
            for(int i=0;i<2;i++){float left=60+i*135;box(c,left,52,left+117,144,11,blend(0xffd8e1ed,0xff8093ad,p*.25f));
                box(c,left+8,61,left+109,75,4,0xffb3c5db);for(int j=0;j<3;j++)box(c,left+8,84+j*15,left+92-j*13,89+j*15,2,0xff9caec4);}
            c.restoreToCount(recents);
        }else if(scene==2)controls(c,p);
        else{box(c,10,40,350,166,0,blend(0xff213953,0xff15253a,p));label(c,"应用内容",149,98,15,WHITE);line(c,160,143,200,143,2,SECONDARY);}
        // Cached OEM phases: 150 ms body + 50 ms dot; 50 ms dot + 300 ms body + rebound.
        if(keep)pill(c,112,28,1,1,false);
        else if(animate){
            float body,content,dot;
            if(time<600){body=content=dot=1;}
            else if(time<750){body=1-(time-600)/150;content=1-clamp((time-600)/100);dot=1;}
            else if(time<800){body=content=0;dot=1-(time-750)/50;}
            else if(time<1700){body=content=dot=0;}
            else if(time<1750){body=content=0;dot=(time-1700)/50;}
            else if(time<2800){body=curve((time-1750)/300);content=curve((time-1800)/250);dot=1;}
            else if(time<2950){body=1-(time-2800)/150;content=1-clamp((time-2800)/100);dot=1;}
            else{body=content=0;dot=1-clamp((time-2950)/50);}
            float rebound=time>2050&&time<2550?3*(1-curve((time-2050)/500)):0;
            pill(c,10+102*body+rebound,10+18*body,content,dot,false);
        }else pill(c,112,28,1,1-p,false);
        String name=switch(scene){case 0->"全屏";case 1->"最近任务";default->"下拉状态栏";};
        label(c,(landscape?"横屏":"竖屏")+" · "+name+" · "+(keep?"保持显示":animate?"动画补全":"跟随系统"),24,160,9,WHITE);
        if(inherited)label(c,"沿用竖屏配置",257,160,8,SECONDARY);
    }
    private void camera(Canvas c){
        phone(c);homeContent(c);float p=opening();boolean attached=enabled()&&flag("camera_card_expansion");
        if(enabled()&&flag("card_dim_enabled"))box(c,10,8,350,166,0,Color.argb(Math.round(p*bounded("card_dim_percent",30,0,100)*2.55f),0,0,0));
        if(p<.001f){pill(c,112,28,1,1,false);return;}
        // Only the first card gains the header; content retains the original camera-safe top.
        float left=124-104*p,right=236+104*p,top=13+(attached?0:34*p),bottom=41+111*p;
        boolean custom=enabled()&&flag("card_corners_enabled"),continuous=custom&&flag("card_continuous_corners");
        float collapsed=enabled()&&flag("capsule_corners_enabled")?Math.min(14,bounded("capsule_corner_dp",16,0,24)):14;
        float expanded=custom?bounded("card_corner_dp",28,0,64):24;
        float radius=collapsed+(expanded-collapsed)*p;
        rect.set(left,top,right,bottom);
        CloudCorners.path(outline,rect,radius,continuous);
        int clip=c.save();c.clipPath(outline);
        paint.setColor(Color.WHITE);paint.setShader(material);c.drawRect(left,top,right,bottom,paint);paint.setShader(null);
        float reveal=curve((p-.18f)/.82f);int content=c.saveLayerAlpha(20,48,340,155,Math.round(reveal*255));
        album(c,35,55,32);label(c,"示例音频",79,68,13,WHITE);label(c,"本地模拟 · 媒体控制",79,85,9,SECONDARY);
        label(c,"00:42",35,105,8,SECONDARY);line(c,66,102,303,102,2,0xff747d90);line(c,66,102,131,102,2,WHITE);label(c,"03:30",308,105,8,SECONDARY);
        label(c,"♡",43,137,23,WHITE);previous(c,119,129,false);circle(c,180,129,15,0x345f6b80);
        line(c,176,123,176,135,3,WHITE);line(c,184,123,184,135,3,WHITE);previous(c,240,129,true);circle(c,307,129,8,0xff7c879b);
        c.restoreToCount(content);c.restoreToCount(clip);
        paint.setShader(null);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(.7f);paint.setColor(alpha(0xff9aabc4,p*.45f));
        c.drawPath(outline,paint);paint.setStyle(Paint.Style.FILL);cameraHole(c);
    }
    private void previous(Canvas c,float x,float y,boolean next){
        float direction=next?1:-1;path.reset();path.moveTo(x-4*direction,y-6);path.lineTo(x+4*direction,y);path.lineTo(x-4*direction,y+6);path.close();paint.setColor(WHITE);c.drawPath(path,paint);line(c,x+6*direction,y-6,x+6*direction,y+6,2,WHITE);
    }
    private boolean pausedSample(){return progress>.48f&&progress<.69f;}
    private void waveBars(Canvas c,float left,float cy,float barWidth,float gap,float maxHeight,boolean realtime,boolean paused){
        float sample=paused&&flag("wave_hold_paused")?.48f:progress;
        int interval=bounded("wave_interval_ms",MediaWaveOptions.DEFAULTS.intervalMs(),MediaWaveOptions.INTERVAL_MIN_MS,MediaWaveOptions.INTERVAL_MAX_MS);
        sample=(float)(Math.floor(sample*3200/interval)*interval/3200);
        float sensitivity=realtime?bounded("wave_sensitivity",MediaWaveOptions.DEFAULTS.sensitivityPercent(),
                MediaWaveOptions.SENSITIVITY_MIN_PERCENT,MediaWaveOptions.SENSITIVITY_MAX_PERCENT)/100f:1;
        if (realtime) {
            java.util.Arrays.fill(demoLevels, 0);
            for (int tone = 0; tone < demoToneBands.length; tone++) {
                int band = demoToneBands[tone];
                if (band < 0) continue;
                float level = (float) (.25 + .65 * (.5 + .5 * Math.sin(sample * 24 + tone * 1.37))
                        * Math.abs(Math.cos(sample * 7 + tone * .3)));
                demoLevels[band] = Math.max(demoLevels[band], level);
            }
        }
        for(int i=0;i<6;i++){
            float level=realtime?demoLevels[i]:.35f+.35f*(float)Math.sin(progress*30+i*.9);
            if(paused&&(!realtime||!flag("wave_hold_paused")))level=0;
            float h=barWidth+(maxHeight-barWidth)*clamp(level*sensitivity);
            box(c,left+i*(barWidth+gap),cy-h/2,left+i*(barWidth+gap)+barWidth,cy+h/2,barWidth/2,SECONDARY);
        }
    }
    private void media(Canvas c){
        phone(c);boolean realtime=enabled()&&flag("realtime_media_wave"),paused=pausedSample(),visible=!paused||(enabled()&&flag("keep_paused_music"));
        if(visible){pill(c,112,28,0,1,false);album(c,130,18,16);waveBars(c,205,26,2,3,10,realtime,paused);}
        label(c,"小胶囊局部放大",27,63,9,SECONDARY);
        if(visible){box(c,26,72,334,125,26,Color.BLACK);album(c,43,84,29);circle(c,180,98,9,Color.BLACK);waveBars(c,223,99,4,8,28,realtime,paused);}
        else{box(c,26,72,334,125,26,0x223a506b);label(c,"暂停后按系统处理",127,103,11,SECONDARY);}
        if(realtime){for(int i=0;i<bandLabels.length;i++)label(c,bandLabels[i],25+i*53,143,7,SECONDARY);
            label(c,(paused?"暂停："+(flag("wave_hold_paused")?"保留最后柱高":"回落为短柱"):"六频段（Hz） · 合成信号")+"  /  不采集声音",67,160,9,WHITE);
        }else label(c,"原厂装饰律动 · 开启后显示六频段效果",78,151,9,WHITE);
    }
    private void icons(Canvas c){
        phone(c);float p=opening();
        // Restore the wallpaper before drawing the header's moving icon layer.
        paint.setShader(wallpaper);paint.setColor(Color.WHITE);c.drawRect(10,8,350,39,paint);paint.setShader(null);
        controls(c,p);float y=30+12*p;
        boolean on=enabled();float clock=on&&flag("shade_only.clock")?p:1;
        label(c,"9:41",25+3*p,y,10,alpha(WHITE,clock));
        float wifi=on&&flag("shade_only.wifi")?p:1,battery=on&&flag("shade_only.battery")?p:1;
        wifi(c,286,23+12*p,alpha(WHITE,wifi));smallBattery(c,316,23+12*p,alpha(WHITE,battery));
        int mode=on?number("bluetooth_mode",0):0;
        bluetooth(c,270,27+12*p,alpha(WHITE,mode>0?0:on&&flag("shade_only.bluetooth")?p:1));cameraHole(c);
        if(p<.1f)label(c,mode>0?"未连接设备示例 · 蓝牙按所选规则隐藏":"下拉后按系统状态显示所选图标",70,119,10,SECONDARY);
        else label(c,"图标位置与显隐随下拉进度过渡",87,159,9,WHITE);
    }
    private void aod(Canvas c){
        backdrop(c);float dim=enabled()&&flag("dim_enabled")?bounded("dim",0,0,100)/100f*opening():0;
        path.reset();path.moveTo(10,166);path.lineTo(115,64);path.lineTo(191,135);path.lineTo(274,76);path.lineTo(350,166);path.close();paint.setColor(0xff58677e);c.drawPath(path,paint);
        path.reset();path.moveTo(90,90);path.lineTo(115,64);path.lineTo(151,103);path.lineTo(123,94);path.lineTo(111,101);path.close();paint.setColor(0xffc6d1df);c.drawPath(path,paint);
        box(c,10,8,350,166,0,Color.argb(Math.round(dim*255),0,0,0));label(c,"09:41",111,64,42,WHITE);
        if(!(enabled()&&flag("hide_date")))label(c,"11 月 6 日  星期五",126,84,10,WHITE);
        label(c,"仅背景压暗 · 时钟亮度保持不变",91,153,10,SECONDARY);
    }
    private void battery(Canvas c){
        phone(c);homeContent(c);label(c,"状态栏电池局部放大",27,65,10,SECONDARY);
        box(c,104,80,249,130,13,WHITE);box(c,252,96,257,114,2,WHITE);box(c,108,84,222,126,10,0xffd8e4ed);box(c,223,84,245,126,4,0xff536780);
        int weight=enabled()&&flag("weight_enabled")?bounded("weight",570,100,1000):570;
        paint.setColor(0xff233b52);paint.setTextSize(enabled()&&flag("text_size_enabled")?bounded("text_size_tenths_dp",100,60,140)*.35f:35);
        paint.setTypeface(Typeface.create(NORMAL,weight,false));
        float x=155+(enabled()&&flag("horizontal_offset_enabled")?bounded("horizontal_offset_tenths_dp",0,-30,30):0);
        float y=118+(enabled()&&flag("vertical_offset_enabled")?bounded("vertical_offset_tenths_dp",0,-30,30):0);
        int clip=c.save();c.clipRect(108,84,245,126);c.drawText("80",x,y,paint);c.restoreToCount(clip);
        label(c,"字重 · 字号 · 水平与垂直偏移",97,155,9,SECONDARY);
    }
    private void capsule(Canvas c){
        phone(c);homeContent(c);boolean skip=enabled()&&flag("skip_empty_capsule"),empty=!skip&&progress>.36f&&progress<.58f;
        if(!empty){pill(c,112,28,1,1,!(enabled()&&flag("hide_capsule_stroke")));
            if(!(enabled()&&flag("hide_capsule_arrows"))){label(c,"‹",115,31,15,SECONDARY);label(c,"›",240,31,15,SECONDARY);}}
        box(c,26,96,334,149,15,0xed29384e);
        label(c,empty?"空白状态":progress>.58f?"活动二 · 秒表":"活动一 · 媒体",42,117,12,WHITE);
        label(c,skip?"左右滑动仅在有效活动之间循环":"系统切换顺序包含空白状态",42,136,9,SECONDARY);
    }
    private static int alpha(int color,float opacity){return Color.argb(Math.round(Color.alpha(color)*clamp(opacity)),Color.red(color),Color.green(color),Color.blue(color));}
    private static int blend(int a,int b,float fraction){float f=clamp(fraction);return Color.rgb(Math.round(Color.red(a)*(1-f)+Color.red(b)*f),Math.round(Color.green(a)*(1-f)+Color.green(b)*f),Math.round(Color.blue(a)*(1-f)+Color.blue(b)*f));}
}
