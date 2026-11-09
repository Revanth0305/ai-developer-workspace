package dev.workspace;

import org.springframework.stereotype.Component;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Fixed argv processes only. Output is bounded even when a child keeps writing. */
@Component
public class BoundedProcess {
    public record Result(int exitCode, String output) {}
    public Result run(List<String> command, Path cwd, Duration timeout, BooleanSupplier cancelled, Consumer<String> log) throws Exception {
        return runInternal(command,cwd,timeout,cancelled,log,false);
    }
    /** Only read a known Git setting; no working-tree operation runs with user config enabled. */
    public String gitSetting(Path root,String key) throws Exception {
        if(!Set.of("core.autocrlf","core.eol").contains(key)) throw new IllegalArgumentException("Unsupported Git setting");
        Result result=runInternal(List.of("git","-C",root.toString(),"config","--get",key),root,Duration.ofSeconds(5),()->false,s->{},true);
        return result.exitCode()==0?result.output().strip():"";
    }
    private Result runInternal(List<String> command,Path cwd,Duration timeout,BooleanSupplier cancelled,Consumer<String> log,boolean readGitConfig) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        if (cwd != null) builder.directory(cwd.toFile());
        Map<String,String> env=builder.environment();
        env.keySet().removeIf(k -> k.startsWith("GIT_") || k.startsWith("NODE_") || k.equals("DOCKER_HOST") || k.equals("DOCKER_CONTEXT") || k.equals("DOCKER_CONFIG"));
        if(!readGitConfig) { env.put("GIT_CONFIG_NOSYSTEM","1"); env.put("GIT_CONFIG_SYSTEM",nullDevice()); env.put("GIT_CONFIG_GLOBAL",nullDevice()); }
        env.put("GIT_TERMINAL_PROMPT","0");
        Process process=builder.start();
        StringBuffer captured=new StringBuffer();
        Thread reader=Thread.ofVirtual().start(() -> {
            try(Reader stream=new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8)) {
                char[] buffer=new char[2048]; int count;
                while((count=stream.read(buffer))!=-1) {
                    String piece=new String(buffer,0,count);
                    synchronized(captured) {
                        int available=65536-captured.length();
                        if(available>0) { String bounded=piece.substring(0,Math.min(available,piece.length())); captured.append(bounded); log.accept(bounded); }
                    }
                }
            } catch(IOException ignored) { }
        });
        try {
            long end=System.nanoTime()+timeout.toNanos();
            while(!process.waitFor(100,TimeUnit.MILLISECONDS)) {
                if(cancelled.getAsBoolean()) throw new CancellationException("Operation cancelled");
                if(System.nanoTime()>end) throw new TimeoutException("Command exceeded its time limit");
            }
            reader.join(2000);
            return new Result(process.exitValue(),captured.toString());
        } finally {
            if(process.isAlive()) { process.descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly(); process.waitFor(2,TimeUnit.SECONDS); }
            try { process.getInputStream().close(); } catch(IOException ignored) { }
            reader.join(2000);
        }
    }
    // Git for Windows recognizes /dev/null; a native NUL config path fails in current Git.
    static String nullDevice() { return "/dev/null"; }
}
