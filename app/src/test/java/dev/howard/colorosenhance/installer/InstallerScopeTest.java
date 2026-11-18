package dev.howard.colorosenhance.installer;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import static org.junit.Assert.*;

/** LSPosed 2.2.0's server-side module query uses a synthetic process scope, not the android APK. */
public class InstallerScopeTest {
    @Test public void staticScopeCanBeSelectedByTheSystemServerLoader() throws Exception {
        var scopes=Files.readAllLines(Path.of("src/main/resources/META-INF/xposed/scope.list"));
        assertTrue("Daemon selects enabled user-0 modules where app_pkg_name='system'",scopes.contains("system"));
        assertFalse("android denotes a different scope in this framework",scopes.contains("android"));
    }
}
