package dev.lsposed.fluidcloud;

/** A stateless sample of the currently rendered card, including interrupted animations. */
final class CardDimProgress {
    private CardDimProgress(){}
    static float alpha(float visibleWidth,float fullWidth,float cameraWidth,float nativeAlpha,int percent){
        if(!Float.isFinite(visibleWidth)||!Float.isFinite(fullWidth)||!Float.isFinite(cameraWidth)
                ||!Float.isFinite(nativeAlpha)||fullWidth<=0||cameraWidth<0||fullWidth<=cameraWidth)return 0;
        float fraction=Math.max(0,Math.min(1,(visibleWidth-cameraWidth)/(fullWidth-cameraWidth)));
        return fraction*Math.max(0,Math.min(1,nativeAlpha))*Math.max(0,Math.min(100,percent))/100f;
    }
}
