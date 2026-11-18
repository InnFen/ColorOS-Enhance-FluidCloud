package dev.howard.colorosenhance.migration;

import android.util.Base64;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=36)
public class RootMigrationClientTest {
    private static final String NONCE="a".repeat(32);
    private static String frame(String state) throws Exception {
        var object=new JSONObject().put("nonce",NONCE).put("state",state).put("snapshot",MigrationProtocol.encode(MigrationProtocolTest.sample()));
        return "COLOROS_MIGRATION_V1 "+Base64.encodeToString(object.toString().getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP)+"\n";
    }
    @Test public void rootManagerNoiseIsNotConfusedWithAConfirmedMigrationResult() throws Exception {
        var result=RootMigrationClient.parse("root-manager notice\n"+frame("DISABLED"),NONCE,false);
        assertEquals(RootMigrationReader.LegacyState.DISABLED,result.state());assertEquals(MigrationProtocolTest.sample(),result.snapshot());
        assertThrows(IllegalArgumentException.class,()->RootMigrationClient.parse("permission denied",NONCE,false));
        assertThrows(IllegalArgumentException.class,()->RootMigrationClient.parse(frame("DISABLED"),"b".repeat(32),false));
        assertThrows(IllegalArgumentException.class,()->RootMigrationClient.parse(frame("DISABLED")+frame("DISABLED"),NONCE,false));
        assertThrows(IllegalArgumentException.class,()->RootMigrationClient.parse(frame("UNKNOWN"),NONCE,false));
    }
    @Test public void apkPathIsShellQuotedAsOneLiteralArgument(){
        assertEquals("'/data/app/a b/base.apk'",RootMigrationClient.quote("/data/app/a b/base.apk"));
        assertEquals("'a'\\'';$(ignored)'",RootMigrationClient.quote("a';$(ignored)"));
    }
}
