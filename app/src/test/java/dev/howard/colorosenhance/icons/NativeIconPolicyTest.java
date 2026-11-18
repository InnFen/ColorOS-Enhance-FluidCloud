package dev.howard.colorosenhance.icons;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class NativeIconPolicyTest {
    @Test public void nativeUserPolicyAndPerContainerExclusionsAlwaysWinAndUpdateInPlace() {
        var manager=new Manager();var container=new Container();var policy=NativeIconPolicy.read(manager,container);
        assertTrue(policy.known());assertTrue(policy.allows("wifi"));
        manager.mController.mIconHideList.add("wifi");assertFalse(policy.allows("wifi"));
        manager.mController.mIconHideList.clear();manager.mController.mIconHidePolicyList.add("wifi");assertFalse(policy.allows("wifi"));
        manager.mController.mIconHidePolicyList.clear();manager.mBlockList.add("wifi");assertFalse(policy.allows("wifi"));
        manager.mBlockList.clear();container.mIgnoredSlots.add("wifi");assertFalse(policy.allows("wifi"));
        container.mIgnoredSlots.clear();assertTrue(policy.allows("wifi"));
    }
    @Test public void unknownNativeCollectionsDoNotGrantModuleOwnership() {
        var policy=NativeIconPolicy.read(new Object(),new Object());assertFalse(policy.known());assertFalse(policy.allows("wifi"));
    }
    @Test public void colorOs16ManagerPolicyContractRetainsUserAndPolicyExclusions() {
        var manager=new LegacyManager();var policy=NativeIconPolicy.read(manager,new Container());
        assertTrue(policy.known());assertTrue(policy.allows("wifi"));
        manager.mPolicyBlockList.add("wifi");assertFalse(policy.allows("wifi"));
        manager.mPolicyBlockList.clear();manager.mController.mIconHideList.add("wifi");assertFalse(policy.allows("wifi"));
    }
    @Test public void userHidingIsDistinguishedFromHostAndPolicyExclusionsWithoutCopyingItsLiveSet() {
        var manager=new Manager();var container=new Container();var policy=NativeIconPolicy.read(manager,container);
        manager.mBlockList.add("wifi");manager.mController.mIconHidePolicyList.add("wifi");container.mIgnoredSlots.add("wifi");
        assertFalse(policy.userHidden("wifi"));manager.mController.mIconHideList.add("wifi");assertTrue(policy.userHidden("wifi"));
        manager.mController.mIconHideList.clear();assertFalse(policy.userHidden("wifi"));
        assertFalse(new NativeIconPolicy(List.of(),false).userHidden("wifi"));
    }
    static class LegacyController {Set<String> mIconHideList=new HashSet<>();}
    static class LegacyManager {
        LegacyController mController=new LegacyController();
        List<String> mBlockList=new ArrayList<>(),mPolicyBlockList=new ArrayList<>();
    }
    static class Controller {Set<String> mIconHideList=new HashSet<>(),mIconHidePolicyList=new HashSet<>();}
    static class Manager {Controller mController=new Controller();List<String> mBlockList=new ArrayList<>();}
    static class Container {Set<String> mIgnoredSlots=new HashSet<>();}
}
