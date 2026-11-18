package dev.howard.colorosenhance.migration;

import android.content.Context;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

/** One bounded root read. The shell contains only our APK path and fixed arguments. */
public final class RootMigrationClient {
    public record Result(RootMigrationReader.LegacyState state,MigrationProtocol.Snapshot snapshot) { }
    static String quote(String text) {return "'"+text.replace("'","'\\''")+"'";}
    public static Result read(Context context,boolean probe) {
        String nonce=java.util.UUID.randomUUID().toString().replace("-","");
        String command="CLASSPATH="+quote(context.getApplicationInfo().sourceDir)
                +" /system/bin/app_process /system/bin dev.howard.colorosenhance.migration.RootMigrationReader "
                +(probe?"probe":"read")+" "+nonce;
        Process process=null;
        try {
            process=new ProcessBuilder("su","-c",command).start();process.getOutputStream().close();
            Process running=process;
            var output=new java.io.ByteArrayOutputStream();
            Thread reader=new Thread(()->{try(var input=running.getInputStream()) {
                byte[] bytes=input.readNBytes(MigrationProtocol.MAX_BYTES*3+1);
                if(bytes.length>MigrationProtocol.MAX_BYTES*3)running.destroyForcibly(); else output.writeBytes(bytes);
            }catch(java.io.IOException ignored){}},"coloros-migration-read");
            Thread errors=new Thread(()->{try(var input=running.getErrorStream()) {
                if(input.readNBytes(65537).length>65536)running.destroyForcibly();
            }catch(java.io.IOException ignored){}},"coloros-migration-errors");
            reader.start();errors.start();
            if(!process.waitFor(45,TimeUnit.SECONDS)) throw new IllegalStateException("Root migration timed out");
            reader.join(2000);errors.join(2000);
            if(process.exitValue()!=0 || reader.isAlive() || errors.isAlive()) throw new IllegalStateException("Root migration unavailable");
            return parse(output.toString(StandardCharsets.UTF_8),nonce,probe);
        } catch(java.io.IOException|InterruptedException error) {
            if(error instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IllegalStateException("Root migration unavailable");
        } finally {if(process!=null) {
            if(process.isAlive())process.destroyForcibly();
            try{process.getInputStream().close();}catch(java.io.IOException ignored){}
            try{process.getErrorStream().close();}catch(java.io.IOException ignored){}
        }}
    }
    static Result parse(String output,String nonce,boolean probe) {
        String[] lines=output.split("\\R");String payload=null;
        for(String line:lines) if(line.startsWith("COLOROS_MIGRATION_V1 ")) {
            if(payload!=null)throw new IllegalArgumentException("Ambiguous migration result");payload=line.substring(21);
        }
        if(payload==null || payload.length()>MigrationProtocol.MAX_BYTES*3)throw new IllegalArgumentException("Missing migration result");
        try {
            var object=new JSONObject(new String(Base64.decode(payload,Base64.NO_WRAP),StandardCharsets.UTF_8));
            if(!nonce.equals(object.getString("nonce")))throw new IllegalArgumentException("Migration nonce mismatch");
            var state=RootMigrationReader.LegacyState.valueOf(object.getString("state"));
            if(state==RootMigrationReader.LegacyState.UNKNOWN)throw new IllegalArgumentException("Unknown legacy activation state");
            MigrationProtocol.Snapshot snapshot=probe?null:MigrationProtocol.decode(object.getString("snapshot"));
            return new Result(state,snapshot);
        }catch(org.json.JSONException error){throw new IllegalArgumentException("Invalid root migration response");}
    }
    private RootMigrationClient() { }
}
