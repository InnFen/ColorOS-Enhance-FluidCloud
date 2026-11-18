package dev.howard.colorosenhance.icons;

import java.util.List;
import java.util.ArrayList;

/** Presentation offsets over an unchanged native layout, driven by the native gesture fraction. */
final class IconDrawPlan {
    record Cell(float left, float width, boolean selected, boolean alwaysHidden) {
        Cell(float left,float width,boolean selected){this(left,width,selected,false);}
    }
    record Transform(float dx, float alpha) {
        static final Transform NONE = new Transform(0, 1);
    }
    record Reveal(float left,float right) {}
    static Reveal reveal(float left,float width,boolean toRight,float progress) {
        float p=Float.isFinite(progress)?Math.max(0,Math.min(1,progress)):0;
        float w=Math.max(0,width);
        return toRight?new Reveal(left+w*(1-p),left+w):new Reveal(left,left+w*p);
    }
    static List<Transform> at(List<Cell> cells, boolean compactToRight, float fraction) {
        float progress = Float.isFinite(fraction) ? Math.max(0, Math.min(1, fraction)) : 0;
        List<Transform> result = new ArrayList<>(cells.size());
        for (Cell cell : cells) {
            float removed = 0;
            for (Cell other : cells) if (other.selected && (compactToRight ? other.left > cell.left : other.left < cell.left))
                removed += Math.max(0, other.width) * (other.alwaysHidden ? 1 : 1-progress);
            result.add(new Transform(removed==0 ? 0 : (compactToRight ? 1 : -1) * removed,
                    cell.alwaysHidden ? 0 : cell.selected ? progress : 1));
        }
        return result;
    }
}
