package dev.howard.colorosenhance;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public final class SystemUiRestarterTest {
    @Test public void successRequiresAReplacementProcess() {
        assertEquals(SystemUiRestarter.Result.SUCCESS,
                SystemUiRestarter.parseResult(0, "AOD_RESTART_OK 16472 17500\n"));
        assertEquals(SystemUiRestarter.Result.FAILED,
                SystemUiRestarter.parseResult(0, "AOD_RESTART_OK 16472 16472\n"));
    }

    @Test public void anEmptyOrMalformedResponseIsNotSuccess() {
        for (String output : new String[]{"", "Success", "AOD_RESTART_OK", "AOD_RESTART_OK 1 200",
                "AOD_RESTART_OK 200 -1", "AOD_RESTART_OK 200 abc", "AOD_RESTART_OK 200 0"}) {
            assertEquals(output, SystemUiRestarter.Result.FAILED,
                    SystemUiRestarter.parseResult(0, output));
        }
    }

    @Test public void nonzeroExitCannotReportSuccess() {
        assertEquals(SystemUiRestarter.Result.FAILED,
                SystemUiRestarter.parseResult(1, "AOD_RESTART_OK 100 200"));
    }

    @Test public void rootManagerMayWriteBeforeTheResult() {
        assertEquals(SystemUiRestarter.Result.SUCCESS,
                SystemUiRestarter.parseResult(0, "root manager message\nAOD_RESTART_OK 100 200\n"));
    }

    @Test public void scriptErrorsHaveDistinctResults() {
        assertEquals(SystemUiRestarter.Result.ROOT_DENIED,
                SystemUiRestarter.parseResult(10, "AOD_RESTART_ROOT_DENIED"));
        assertEquals(SystemUiRestarter.Result.TARGET_UNAVAILABLE,
                SystemUiRestarter.parseResult(11, "AOD_RESTART_TARGET_UNAVAILABLE"));
        assertEquals(SystemUiRestarter.Result.FAILED,
                SystemUiRestarter.parseResult(12, "AOD_RESTART_FAILED"));
        assertEquals(SystemUiRestarter.Result.TIMEOUT,
                SystemUiRestarter.parseResult(13, "AOD_RESTART_TIMEOUT"));
    }

    @Test public void mismatchedErrorMarkerIsNotTrusted() {
        assertEquals(SystemUiRestarter.Result.FAILED,
                SystemUiRestarter.parseResult(0, "AOD_RESTART_TIMEOUT"));
        assertEquals(SystemUiRestarter.Result.FAILED,
                SystemUiRestarter.parseResult(10, "AOD_RESTART_OK 100 200"));
    }

    @Test public void suRejectionHasNoScriptMarker() {
        assertEquals(SystemUiRestarter.Result.ROOT_DENIED,
                SystemUiRestarter.parseResult(1, "Permission denied"));
    }
}
