package dev.howard.colorosenhance.updates;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONException;

/** One launch/manual check; no token, settings upload, periodic work, or download/installation backend. */
public final class GitHubUpdateClient {
    private static final int MAX_RESPONSE_BYTES = 512 * 1024;
    interface Connections { HttpsURLConnection open() throws IOException; }
    public record Result(ReleaseInfo release, String error) {
        public boolean successful() { return release != null; }
    }
    private final Connections connections;
    public GitHubUpdateClient() {
        this(() -> (HttpsURLConnection) URI.create(ReleaseInfo.API_URL).toURL().openConnection());
    }
    GitHubUpdateClient(Connections connections) { this.connections = connections; }

    public Result check() {
        HttpsURLConnection connection = null;
        try {
            connection = connections.open();
            connection.setConnectTimeout(7000);
            connection.setReadTimeout(10000);
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
            connection.setRequestProperty("User-Agent", "ColorOS-Enhance-UpdateCheck");
            int status = connection.getResponseCode();
            if (status == 404) return failed("暂未找到可用的正式版本，请稍后重试或查看发布页。");
            if (status == 429 || (status == 403 && ("0".equals(connection.getHeaderField("X-RateLimit-Remaining"))
                    || connection.getHeaderField("Retry-After") != null)))
                return failed("GitHub 请求过于频繁，请稍后重试或查看发布页。");
            if (status != 200) return failed("GitHub 暂时无法访问（HTTP " + status + "），请稍后重试。");
            if (connection.getContentLengthLong() > MAX_RESPONSE_BYTES) return invalid();
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) return failed("检查已取消。");
                    if (count > MAX_RESPONSE_BYTES - bytes.size()) return invalid();
                    bytes.write(buffer, 0, count);
                }
                return new Result(ReleaseInfo.parse(bytes.toString(StandardCharsets.UTF_8)), null);
            }
        } catch (SocketTimeoutException timeout) {
            return failed("连接 GitHub 超时，请检查网络后重试。");
        } catch (JSONException | IllegalArgumentException invalid) {
            return invalid();
        } catch (IOException | SecurityException unavailable) {
            return failed("无法连接 GitHub，请检查网络后重试，或在浏览器中查看发布页。");
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
    private static Result failed(String message) { return new Result(null, message); }
    private static Result invalid() { return failed("发布信息不完整或格式不匹配，请在发布页核对版本。"); }
}
