package dev.howard.colorosenhance;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Runs only the packaged SystemUI restart script. Call from a worker thread. */
public final class SystemUiRestarter {
    public enum Result { SUCCESS, ROOT_UNAVAILABLE, ROOT_DENIED, TARGET_UNAVAILABLE, FAILED, TIMEOUT }

    private static final Pattern SUCCESS = Pattern.compile("AOD_RESTART_OK ([0-9]{1,10}) ([0-9]{1,10})");

    public static Result restart(File cacheDirectory, String script) {
        File output = null;
        Process process = null;
        try {
            output = File.createTempFile("systemui-restart-", ".log", cacheDirectory);
            // A late root grant must not restart SystemUI after this request has expired.
            long deadline = System.currentTimeMillis() / 1000 + 30;
            ProcessBuilder command = new ProcessBuilder("su", "-c",
                    "request_deadline=" + deadline + "\n" + script);
            command.redirectErrorStream(true).redirectOutput(output);
            try { process = command.start(); }
            catch (IOException e) { return Result.ROOT_UNAVAILABLE; }
            process.getOutputStream().close();
            if (!process.waitFor(45, TimeUnit.SECONDS)) return Result.TIMEOUT;
            try (FileInputStream input = new FileInputStream(output)) {
                return parseResult(process.exitValue(),
                        new String(input.readNBytes(4096), StandardCharsets.UTF_8));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.FAILED;
        } catch (IOException | RuntimeException e) {
            return Result.FAILED;
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            if (output != null) output.delete();
        }
    }

    static Result parseResult(int exitCode, String output) {
        String[] lines = output.trim().split("\\R");
        String last = lines[lines.length - 1];
        Matcher success = SUCCESS.matcher(last);
        if (exitCode == 0 && success.matches()) {
            long before = Long.parseLong(success.group(1));
            long after = Long.parseLong(success.group(2));
            return before > 1 && after > 1 && before != after ? Result.SUCCESS : Result.FAILED;
        }
        if (exitCode == 10 && last.equals("AOD_RESTART_ROOT_DENIED")) return Result.ROOT_DENIED;
        if (exitCode == 11 && last.equals("AOD_RESTART_TARGET_UNAVAILABLE")) return Result.TARGET_UNAVAILABLE;
        if (exitCode == 13 && last.equals("AOD_RESTART_TIMEOUT")) return Result.TIMEOUT;
        // su can reject the request before the script starts, without our status marker.
        if (exitCode != 0 && !output.contains("AOD_RESTART_")) return Result.ROOT_DENIED;
        return Result.FAILED;
    }
}
