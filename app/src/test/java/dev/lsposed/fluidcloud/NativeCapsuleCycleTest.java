package dev.lsposed.fluidcloud;

import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class NativeCapsuleCycleTest {
    @Test public void bothDirectionsWrapOnlyAcrossRealEntriesIncludingOneAndNoEntries(){
        assertEquals(0,NativeCapsuleCycle.target(3,2,0));assertEquals(2,NativeCapsuleCycle.target(3,0,1));
        assertEquals(1,NativeCapsuleCycle.target(3,0,0));assertEquals(1,NativeCapsuleCycle.target(3,2,1));
        assertEquals(0,NativeCapsuleCycle.target(1,0,0));assertEquals(0,NativeCapsuleCycle.target(1,0,1));
        assertEquals(-1,NativeCapsuleCycle.target(0,0,0));assertEquals(-1,NativeCapsuleCycle.target(2,2,0));
        assertEquals(-1,NativeCapsuleCycle.target(2,0,7));
    }
    @Test public void onlyTheEmptySwipeTargetUsesNativeSlideAndOriginalArgumentsArePreserved()throws Exception{
        for(boolean legacy:new boolean[]{false,true}){
            var cycle=contract(legacy);var helper=new Helper();
            assertFalse(cycle.skip(helper,1,2,7,0,false));assertNull(helper.call);
            assertFalse(cycle.skip(helper,1,1,7,0,true));assertNull(helper.call);
            assertTrue(cycle.skip(helper,1,2,7,0,true));assertArrayEquals(new int[]{1,0,7,0},helper.call);
            assertTrue(cycle.skip(helper,0,2,8,1,true));assertArrayEquals(new int[]{0,1,8,1},helper.call);
            helper.a.l=helper.a.m=List.of();assertFalse(cycle.skip(helper,0,0,8,1,true));
        }
    }
    private NativeCapsuleCycle contract(boolean old)throws Exception{
        Map<String,Class<?>> types=Map.of(old?"a5.h":"com.oplus.systemui.plugins.seedling.capsule.ui.viewmodel.l",Helper.class,
                old?"a5.q":"com.oplus.systemui.plugins.seedling.capsule.ui.viewmodel.u",Model.class);
        return new NativeCapsuleCycle(name->{Class<?> found=types.get(name);if(found==null)throw new ClassNotFoundException(name);return found;},old);
    }
    public static class Model{public List<?> m=List.of("a","b"),l=m;}
    public static class Helper{
        public Model a=new Model();int[] call;
        public void a(int current,int next,int direction,int gesture){call=new int[]{current,next,direction,gesture};}
        public void b(int current,int next,int direction,int gesture){}
        public void c(int current,int next,int direction,int gesture){throw new AssertionError("native hide must be left to the hook caller");}
    }
}
