package dev.howard.colorosenhance;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
/** Executes only the packaged, fixed three-scope operation; no IPC-supplied commands. */
public final class ScopeRestarter {
    public enum Code { SUCCESS, ROOT_UNAVAILABLE, ROOT_DENIED, TARGET_UNAVAILABLE, FAILED, TIMEOUT }
    public record Outcome(Code code,String target,List<String> completed) {
        public Outcome { completed=List.copyOf(completed); }
        public String message() {
            if(code==Code.SUCCESS) return "系统界面、电话服务和设置已重启。SIM PIN 开机验证仍需完整重启手机。";
            String prefix=completed.isEmpty()?"":"已完成："+String.join("、",completed.stream().map(ScopeRestarter::title).toList())+"。";
            return prefix+switch(code) {
                case ROOT_UNAVAILABLE -> "未找到 root 服务。";
                case ROOT_DENIED -> "未获得 root 权限，请检查 KernelSU 对 ColorOS 增强的授权。";
                case TARGET_UNAVAILABLE -> title(target)+"进程不可用，操作已停止。";
                case TIMEOUT -> title(target)+"重启结果未确认，操作已停止。请检查状态后再试。";
                default -> title(target)+"重启未完成，请检查设备状态。";
            };
        }
    }
    private static final List<String> TARGETS=List.of("systemui","phone","settings");
    public static String title(String target) {
        return switch(target) { case "systemui" -> "系统界面"; case "phone" -> "电话服务"; case "settings" -> "设置"; default -> "作用域"; };
    }
    public static Outcome restart(File cache,String script,int userId) {
        File output=null; Process process=null;
        try {
            if(userId<0) return failure(Code.FAILED);
            output=File.createTempFile("scope-restart-",".log",cache);
            // An authorization granted after this request expires must never execute a late kill.
            String input="request_deadline="+(System.currentTimeMillis()/1000+45)+"\ntarget_user="+userId+"\n"+script;
            try { process=new ProcessBuilder("su","-c",input).redirectErrorStream(true).redirectOutput(output).start(); }
            catch(IOException unavailable) { return failure(Code.ROOT_UNAVAILABLE); }
            process.getOutputStream().close();
            if(!process.waitFor(60,TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return interrupted(Code.TIMEOUT,readOutput(output));
            }
            return parse(process.exitValue(),readOutput(output));
        } catch(InterruptedException interrupted) {
            if(process!=null && process.isAlive()) process.destroyForcibly();
            Thread.currentThread().interrupt(); return interrupted(Code.FAILED,readOutput(output));
        }
        catch(IOException | RuntimeException failed) { return interrupted(Code.FAILED,readOutput(output)); }
        finally { if(process!=null && process.isAlive()) process.destroyForcibly(); if(output!=null) output.delete(); }
    }
    static Outcome failure(Code code) { return new Outcome(code,"",List.of()); }
    private static String readOutput(File output) {
        if(output==null) return "";
        try(FileInputStream stream=new FileInputStream(output)) { return new String(stream.readNBytes(8192),StandardCharsets.UTF_8); }
        catch(IOException failure) { return ""; }
    }
    static Outcome interrupted(Code code,String output) {
        StringBuilder prefix=new StringBuilder(); List<String> completed=List.of();
        // A killed writer may leave a half record: only newline-terminated records are evidence.
        int end=output.lastIndexOf('\n');
        if(end>=0) for(String line:output.substring(0,end).split("\\R")) {
            if(line.startsWith("COLOROS_SCOPE_ERROR ") || line.equals("COLOROS_SCOPES_DONE")) break;
            prefix.append(line).append('\n');
            Outcome candidate=parse(12,prefix+"COLOROS_SCOPE_ERROR all "+code.name()+"\n");
            if(candidate.code()!=code || candidate.completed().size()<completed.size()) break;
            completed=candidate.completed();
        }
        return new Outcome(code,completed.size()<TARGETS.size()?TARGETS.get(completed.size()):"",completed);
    }
    static Outcome parse(int exit,String output) {
        List<String> completed=new ArrayList<>();
        String[] lines=output.trim().split("\\R");
        boolean marker=false;
        // Parse a strict ordered transcript; earlier root-manager output is not trusted as completion.
        for(int i=0;i<lines.length;i++) {
            String line=lines[i];
            if(line.startsWith("COLOROS_SCOPE_OK ")) {
                marker=true; String[] p=line.split(" ");
                if(p.length!=4 || completed.size()>=3 || !TARGETS.get(completed.size()).equals(p[1])) return failure(Code.FAILED);
                if(!p[2].matches("[0-9]{1,10}") || !p[3].matches("[0-9]{1,10}")) return failure(Code.FAILED);
                long old=Long.parseLong(p[2]),next=Long.parseLong(p[3]);
                if(next<=1 || old==next || (old<=1 && !(old==0 && "settings".equals(p[1])))) return failure(Code.FAILED);
                completed.add(p[1]);
            } else if(line.startsWith("COLOROS_SCOPE_ERROR ")) {
                String[] p=line.split(" ");
                if(i!=lines.length-1 || exit==0 || p.length!=3 || !(TARGETS.contains(p[1])||"all".equals(p[1]))) return failure(Code.FAILED);
                Code code; try { code=Code.valueOf(p[2]); } catch(IllegalArgumentException bad) { return failure(Code.FAILED); }
                if(code==Code.SUCCESS || code==Code.ROOT_UNAVAILABLE) return failure(Code.FAILED);
                return new Outcome(code,p[1],completed);
            } else if("COLOROS_SCOPES_DONE".equals(line)) {
                return i==lines.length-1 && exit==0 && completed.equals(TARGETS) ? new Outcome(Code.SUCCESS,"",completed) : failure(Code.FAILED);
            } else if(marker || line.startsWith("COLOROS_")) return failure(Code.FAILED);
        }
        return new Outcome(exit!=0 && !marker ? Code.ROOT_DENIED:Code.FAILED,"",completed);
    }
}
