package dev.howard.colorosenhance.updates;

import static org.junit.Assert.*;
import android.app.Application;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.util.Map;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk=36,application=Application.class)
public class GitHubUpdateClientTest {
    static String release() {
        return """
                {"tag_name":"70-1.31.0","name":"A display title is not a version",
                 "draft":false,"prerelease":false,
                 "html_url":"https://github.com/Xposed-Modules-Repo/io.github.howard_tse.colorosenhance/releases/tag/70-1.31.0",
                 "body":"更新说明","assets":[
                 {"name":"coloros-enhance-1.31.0.apk","state":"uploaded","size":123,
                  "browser_download_url":"https://github.com/Xposed-Modules-Repo/io.github.howard_tse.colorosenhance/releases/download/70-1.31.0/coloros-enhance-1.31.0.apk"}]}
                """;
    }
    @Test public void comparesCodeAndRequiresAMatchingDownloadInsteadOfDisplayName() throws Exception {
        ReleaseInfo info=ReleaseInfo.parse(release());
        assertEquals(70,info.versionCode());assertEquals("1.31.0",info.versionName());
        assertTrue(info.newerThan(69));assertFalse(info.newerThan(70));assertFalse(info.newerThan(71));
        assertEquals("更新说明",info.notes());
        assertTrue(info.apkUrl().endsWith("/70-1.31.0/coloros-enhance-1.31.0.apk"));
    }
    @Test public void rejectsDraftPreviewAndMalformedTags() throws Exception {
        for(String field:new String[]{"draft","prerelease"}) {
            JSONObject json=new JSONObject(release()).put(field,true);
            assertThrows(JSONException.class,()->ReleaseInfo.parse(json.toString()));
        }
        for(String tag:new String[]{"1.31.0","v1.31.0","0-1.31.0","2147483648-1.31.0","70-../apk","70-1.31.0/other"}) {
            JSONObject json=new JSONObject(release()).put("tag_name",tag);
            assertThrows(JSONException.class,()->ReleaseInfo.parse(json.toString()));
        }
    }
    @Test public void rejectsMissingOrUnfinishedApkAndUntrustedLinks() throws Exception {
        for(String url:new String[]{"http://github.com/other", "https://github.com.evil.test/apk",
                "https://github.com"+"@"+"evil.test/apk",ReleaseInfo.MODULE_URL+"/releases/tag/69-1.30.0"}) {
            JSONObject json=new JSONObject(release()).put("html_url",url);
            assertThrows(JSONException.class,()->ReleaseInfo.parse(json.toString()));
        }
        for(String name:new String[]{"other.apk","coloros-enhance-1.30.0.apk"}) {
            JSONObject json=new JSONObject(release());json.getJSONArray("assets").getJSONObject(0).put("name",name);
            assertThrows(JSONException.class,()->ReleaseInfo.parse(json.toString()));
        }
        for(String field:new String[]{"state","size","browser_download_url"}) {
            JSONObject json=new JSONObject(release());
            Object bad=switch(field){case "state"->"new";case "size"->0;default->"https://evil.test/file.apk";};
            json.getJSONArray("assets").getJSONObject(0).put(field,bad);
            assertThrows(JSONException.class,()->ReleaseInfo.parse(json.toString()));
        }
    }
    @Test public void checksOnceWithTimeoutsNoRedirectsNoTokenAndClosesConnection() throws Exception {
        Fake connection=new Fake(200,release());
        var result=new GitHubUpdateClient(()->connection).check();
        assertTrue(result.successful());assertNull(result.error());assertTrue(connection.disconnected);assertTrue(connection.closed);
        assertEquals(7000,connection.getConnectTimeout());assertEquals(10000,connection.getReadTimeout());
        assertFalse(connection.getInstanceFollowRedirects());assertFalse(connection.getUseCaches());
        assertNull(connection.getRequestProperty("Authorization"));assertFalse(connection.getDoOutput());
    }
    @Test public void distinguishesUnavailableRateLimitedAndInvalidResponses() throws Exception {
        for(int status:new int[]{404,429,503,302,403}) {
            Fake connection=new Fake(status,"");
            if(status==403)connection.headers=Map.of("X-RateLimit-Remaining","0");
            var result=new GitHubUpdateClient(()->connection).check();
            assertFalse(result.successful());assertTrue(connection.disconnected);
            if(status==429 || status==403)assertTrue(result.error().contains("频繁"));
            if(status==404)assertTrue(result.error().contains("正式版本"));
        }
        Fake invalid=new Fake(200,"not json");
        assertTrue(new GitHubUpdateClient(()->invalid).check().error().contains("格式"));
        assertTrue(invalid.disconnected);assertTrue(invalid.closed);
    }
    @Test public void handlesTimeoutAndBoundsBothDeclaredAndStreamingResponseSize() throws Exception {
        assertTrue(new GitHubUpdateClient(()->{throw new SocketTimeoutException();}).check().error().contains("超时"));
        assertFalse(new GitHubUpdateClient(()->{throw new IOException();}).check().successful());
        for(boolean declared:new boolean[]{false,true}) {
            Fake connection=new Fake(200,"a".repeat(512*1024+1));
            connection.declared=declared?512*1024+1:-1;
            assertTrue(new GitHubUpdateClient(()->connection).check().error().contains("格式"));
            assertTrue(connection.disconnected);if(!declared)assertTrue(connection.closed);
        }
    }
    static final class Fake extends HttpsURLConnection {
        final int status;final byte[] bytes;
        boolean disconnected,closed;long declared=-1;Map<String,String> headers=Map.of();
        Fake(int status,String body)throws Exception {super(new URL(ReleaseInfo.API_URL));this.status=status;bytes=body.getBytes(StandardCharsets.UTF_8);}
        @Override public int getResponseCode(){return status;}
        @Override public String getHeaderField(String name){return headers.get(name);}
        @Override public long getContentLengthLong(){return declared;}
        @Override public InputStream getInputStream(){return new ByteArrayInputStream(bytes){@Override public void close(){closed=true;}};}
        @Override public void disconnect(){disconnected=true;}
        @Override public boolean usingProxy(){return false;}
        @Override public void connect(){}
        @Override public String getCipherSuite(){return "test";}
        @Override public Certificate[] getLocalCertificates(){return null;}
        @Override public Certificate[] getServerCertificates(){return null;}
    }
}
