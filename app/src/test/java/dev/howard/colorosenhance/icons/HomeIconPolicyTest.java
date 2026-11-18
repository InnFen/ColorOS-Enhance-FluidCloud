package dev.howard.colorosenhance.icons;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class HomeIconPolicyTest {
    @Test public void overrideRemovesOnlyHomeHdDefaultAndDoesNotMutateNativeList() {
        List<String> original=new ArrayList<>(List.of("volte","vowifi","volume"));
        var config=StatusIconConfig.from(Map.of("enabled",true,"force_hd_home",true));
        assertEquals(List.of("vowifi","volume"),HomeIconPolicy.effective(original,"HOME",config));
        assertEquals(List.of("volte","vowifi","volume"),original);
        for(String location:List.of("QS","KEYGUARD","SHADE_CARRIER_GROUP","unknown"))
            assertSame(original,HomeIconPolicy.effective(original,location,config));
    }
    @Test public void defaultOffAndMasterOffRestoreOriginalExclusions() {
        List<String> original=List.of("volte","vowifi");
        for(var prefs:List.of(Map.<String,Object>of(),Map.<String,Object>of("enabled",true),
                Map.<String,Object>of("force_hd_home",true)))
            assertSame(original,HomeIconPolicy.effective(original,"HOME",StatusIconConfig.from(prefs)));
    }
}
