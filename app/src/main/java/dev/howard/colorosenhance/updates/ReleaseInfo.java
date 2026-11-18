package dev.howard.colorosenhance.updates;

import java.net.URI;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Accept only the public module repository's stable release and its matching APK. */
public record ReleaseInfo(long versionCode, String versionName, String notes, String pageUrl, String apkUrl) {
    public static final String SOURCE_URL = "https://github.com/Howard-Tse/ColorOS-Enhance";
    public static final String MODULE_URL = "https://github.com/Xposed-Modules-Repo/io.github.howard_tse.colorosenhance";
    public static final String RELEASES_URL = MODULE_URL + "/releases";
    public static final String API_URL = "https://api.github.com/repos/Xposed-Modules-Repo/io.github.howard_tse.colorosenhance/releases/latest";
    private static final String PATH = "/Xposed-Modules-Repo/io.github.howard_tse.colorosenhance/releases/";
    private static final Pattern TAG = Pattern.compile("([1-9][0-9]{0,9})-([0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][A-Za-z0-9.-]+)?)");

    public boolean newerThan(long installedCode) { return versionCode > installedCode; }

    public static ReleaseInfo parse(String json) throws JSONException {
        JSONObject release = new JSONObject(json);
        if (release.getBoolean("draft") || release.getBoolean("prerelease"))
            throw new JSONException("Not a stable release");
        String tag = release.getString("tag_name");
        var match = TAG.matcher(tag);
        if (!match.matches() || tag.length() > 100) throw new JSONException("Invalid version tag");
        long code = Long.parseLong(match.group(1));
        if (code > Integer.MAX_VALUE) throw new JSONException("Invalid version code");
        String name = match.group(2);
        String page = release.getString("html_url");
        if (!githubPath(page, PATH + "tag/" + tag)) throw new JSONException("Invalid release link");
        String apkName = "coloros-enhance-" + name + ".apk";
        JSONArray assets = release.getJSONArray("assets");
        String apk = null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            if (!apkName.equals(asset.optString("name")) || !"uploaded".equals(asset.optString("state"))
                    || asset.optLong("size", 0) <= 0) continue;
            String url = asset.getString("browser_download_url");
            if (githubPath(url, PATH + "download/" + tag + "/" + apkName)) apk = url;
        }
        if (apk == null) throw new JSONException("Release APK unavailable");
        String notes = release.optString("body", "");
        if (notes.length() > 20000) notes = notes.substring(0, 20000) + "\n…完整说明请查看发布页。";
        return new ReleaseInfo(code, name, notes, page, apk);
    }

    private static boolean githubPath(String url, String expectedPath) {
        try {
            URI uri = URI.create(url);
            return "https".equals(uri.getScheme()) && "github.com".equals(uri.getHost())
                    && uri.getRawUserInfo() == null && uri.getPort() == -1 && uri.getRawQuery() == null
                    && uri.getRawFragment() == null && expectedPath.equals(uri.getRawPath());
        } catch (IllegalArgumentException invalid) { return false; }
    }
}
