package dev.lsposed.fluidcloud;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class NativePanelDismissalTest {
    @Test public void nativeSeparatedPanelCompletesWithoutAnAnimatedCollapse()throws Exception{
        Controller controller=new Controller();Dependency.sDependency=new Dependency();Dependency.sDependency.value.enabled=true;
        new NativePanelDismissal(controller,NativePanelDismissalTest::lookup).run();
        assertEquals(List.of("false:true:false"),Dependency.sDependency.value.calls);assertEquals(1,controller.centralSurfacesImpl.value.mShadeController.calls);
    }
    @Test public void combinedShadeUsesItsOwnNativeInstantCompletion()throws Exception{
        Controller controller=new Controller();Dependency.sDependency=new Dependency();
        new NativePanelDismissal(controller,NativePanelDismissalTest::lookup).run();
        assertTrue(Dependency.sDependency.value.calls.isEmpty());assertEquals(1,controller.centralSurfacesImpl.value.mShadeController.calls);
    }
    private static Class<?> lookup(String name)throws ClassNotFoundException{return switch(name){
        case "com.android.systemui.DependencyEx"->Dependency.class;
        case "com.android.systemui.shade.OplusSeparateNotificationAndQSEx"->Separate.class;
        default->throw new ClassNotFoundException(name);
    };}
    public static class Controller{public final Lazy centralSurfacesImpl=new Lazy();}
    public static class Lazy{final Central value=new Central();public Object get(){return value;}}
    public static class Central{public final Shade mShadeController=new Shade();}
    public static class Shade{int calls;public void instantCollapseShade(){calls++;}}
    public static class Dependency{public static Dependency sDependency;final Separate value=new Separate();public Object getDependency(Class<?> type){return value;}}
    public static class Separate{boolean enabled;final List<String> calls=new ArrayList<>();public boolean enableSeparateNotificationAndQS(){return enabled;}public void collapseQSPanel(boolean a,boolean b,boolean c){calls.add(a+":"+b+":"+c);}}
}
