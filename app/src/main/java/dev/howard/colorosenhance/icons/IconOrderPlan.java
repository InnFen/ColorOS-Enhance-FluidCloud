package dev.howard.colorosenhance.icons;

import java.util.*;

/** Physical left-to-right placement; native holder indices and subscription order never change. */
final class IconOrderPlan {
    record Cell(int id,String slot,float width,boolean shadeOnly){}
    record Bounds(float leftStart,float leftEnd,float rightStart,float rightEnd){}
    record Position(float left,float compactDx,boolean onLeft){}
    static Map<Integer,Position> create(IconOrder saved,List<Cell> cells,Bounds bounds){
        if(!finite(bounds.leftStart,bounds.leftEnd,bounds.rightStart,bounds.rightEnd)
                ||bounds.leftEnd<bounds.leftStart ||bounds.rightEnd<bounds.rightStart)return Map.of();
        IconOrder order=saved.withAvailable(cells.stream().map(Cell::slot).toList());
        Map<Integer,Position> result=new HashMap<>();
        place(order.left(),cells,bounds.leftStart,bounds.leftEnd,true,result);
        place(order.right(),cells,bounds.rightStart,bounds.rightEnd,false,result);
        return Map.copyOf(result);
    }
    private static void place(List<String> slots,List<Cell> cells,float start,float end,boolean left,Map<Integer,Position> out){
        List<Cell> sorted=new ArrayList<>();
        for(String slot:slots)for(Cell cell:cells)if(slot.equals(cell.slot)&&Float.isFinite(cell.width)&&cell.width>0)sorted.add(cell);
        float cursor=left?start:end,occupied=0;
        for(int i=0;i<sorted.size();i++){
            Cell cell=sorted.get(left?i:sorted.size()-1-i);
            float x=left?cursor:cursor-cell.width;
            float reserved=cell.shadeOnly?0:cell.width;
            if(occupied+reserved>end-start+.01f)continue;
            out.put(cell.id,new Position(x,0,left));cursor+=left?cell.width:-cell.width;
            occupied+=reserved;
        }
        float removed=0;
        for(int i=0;i<sorted.size();i++){
            Cell cell=sorted.get(left?i:sorted.size()-1-i);Position position=out.get(cell.id);
            if(position==null)continue;
            out.put(cell.id,new Position(position.left,left?-removed:removed,left));
            if(cell.shadeOnly)removed+=cell.width;
        }
    }
    private static boolean finite(float... values){for(float value:values)if(!Float.isFinite(value))return false;return true;}
}
