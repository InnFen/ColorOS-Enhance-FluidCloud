package dev.lsposed.fluidcloud;

import android.content.Context;
import android.graphics.Color;
import android.view.View;

/** Paint only: native card/root routing still owns every touch and accessibility target. */
@android.annotation.SuppressLint("ViewConstructor")
final class CardBackdropView extends View {
    CardBackdropView(Context context){
        super(context);setBackgroundColor(Color.BLACK);setAlpha(0);
        setClickable(false);setFocusable(false);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    void amount(float alpha){if(Float.compare(getAlpha(),alpha)!=0)setAlpha(alpha);}
}
