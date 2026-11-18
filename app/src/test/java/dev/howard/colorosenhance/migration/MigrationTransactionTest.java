package dev.howard.colorosenhance.migration;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=36)
public class MigrationTransactionTest {
    static class Store implements MigrationTransaction.Store {
        final Map<String,Map<String,Object>> groups=new LinkedHashMap<>(MigrationProtocolTest.groups());
        int writes,failAt=-1;boolean permanent,readyBeforeParameters;
        Map<String,Map<String,Object>> expectedTarget;
        public Map<String,Object> read(String group){return Map.copyOf(groups.get(group));}
        public boolean replace(String group,Map<String,Object> values){
            writes++;groups.put(group,Map.copyOf(values));
            if("integration".equals(group) && Boolean.TRUE.equals(values.get(PackageActivation.READY)) && expectedTarget!=null){
                for(String key:MigrationProtocol.GROUPS)if(!"integration".equals(key) && !groups.get(key).equals(expectedTarget.get(key)))readyBeforeParameters=true;
            }
            return !(writes==failAt || permanent && writes>=failAt);
        }
    }
    @Test public void readinessIsPublishedOnlyAfterEveryParameterAndEnableFlagIsAcknowledged(){
        var store=new Store();var before=MigrationTransaction.capture(store);
        var target=MigrationController.activation(MigrationProtocolTest.sample(),true);store.expectedTarget=target;
        assertTrue(MigrationTransaction.apply(store,before,target).success());
        assertEquals(target,MigrationTransaction.capture(store));assertFalse(store.readyBeforeParameters);
    }
    @Test public void failedCommitThatChangedTheCacheIsRolledBackIncludingThatGroup(){
        var store=new Store();var before=MigrationTransaction.capture(store);store.failAt=4;
        var result=MigrationTransaction.apply(store,before,MigrationController.activation(MigrationProtocolTest.sample(),true));
        assertFalse(result.success());assertTrue(result.rollbackConfirmed());assertEquals(before,MigrationTransaction.capture(store));
    }
    @Test public void unconfirmedRollbackLeavesTheActivationGateClosed(){
        var store=new Store();var before=MigrationTransaction.capture(store);store.failAt=4;store.permanent=true;
        var result=MigrationTransaction.apply(store,before,MigrationController.activation(MigrationProtocolTest.sample(),true));
        assertFalse(result.success());assertFalse(result.rollbackConfirmed());
        assertEquals(false,store.groups.get("integration").get(PackageActivation.READY));
    }
    @Test public void concurrentChangesAreNotOverwritten(){
        var store=new Store();var before=MigrationTransaction.capture(store);
        store.groups.put("cloud",Map.of("wave_interval_ms",100));
        var result=MigrationTransaction.apply(store,before,MigrationController.activation(MigrationProtocolTest.sample(),true));
        assertFalse(result.success());assertEquals(0,store.writes);assertEquals(100,store.groups.get("cloud").get("wave_interval_ms"));
    }
}
