package com.train12306;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 应用日志系统 — 内存环形缓冲 + 文件持久化，全局单例
 * <p>
 * 特性：
 * - 日志级别：DEBUG / INFO / WARN / ERROR
 * - 环形缓冲，最多 500 条
 * - 自动写入 app 内部存储 logs/ 目录（无需额外权限）
 * - 线程安全
 * - 支持按级别过滤
 */
public class AppLogger {

    private static AppLogger instance;
    private final List<LogEntry> logs = new ArrayList<>();
    private static final int MAX_LOG = 500;
    private PrintWriter fileWriter = null;
    private final Object fileLock = new Object();
    private boolean fileLoggingEnabled = false;
    private String logDirPath = "";

    /** 日志级别 */
    public enum Level {
        DEBUG(0), INFO(1), WARN(2), ERROR(3);

        final int value;
        Level(int value) { this.value = value; }
    }

    private AppLogger() {}

    /**
     * 初始化日志系统（必须在 Application 或 MainActivity 中调用一次）
     */
    public static synchronized void init(Context context) {
        if (instance == null) {
            instance = new AppLogger();
            instance.initFileLogging(context);
        }
    }

    /**
     * 初始化文件日志（写入内部存储 logs/ 目录，无需权限）
     */
    private void initFileLogging(Context context) {
        try {
            File logDir = new File(context.getFilesDir(), "logs");
            if (!logDir.exists()) logDir.mkdirs();
            logDirPath = logDir.getAbsolutePath();
            String date = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(new Date());
            File logFile = new File(logDir, "app_" + date + ".log");
            fileWriter = new PrintWriter(new FileOutputStream(logFile, true));
            fileLoggingEnabled = true;
            // 直接写入文件首行，避免递归 addLog
            fileWriter.println("--- 日志启动: " + date + " ---");
            fileWriter.flush();
        } catch (Exception e) {
            // 无法写入，静默降级为纯内存
            fileLoggingEnabled = false;
        }
    }

    public static synchronized AppLogger getInstance() {
        if (instance == null) {
            // 未初始化时创建一个纯内存实例（降级）
            instance = new AppLogger();
        }
        return instance;
    }

    /**
     * 获取日志文件存储路径
     */
    public static String getLogDirPath() {
        AppLogger inst = getInstance();
        return inst.logDirPath;
    }

    // ======================== 便捷方法 ========================

    public static void log(String tag, String msg) {
        add(Level.INFO, tag, msg);
    }

    public static void debug(String tag, String msg) {
        add(Level.DEBUG, tag, msg);
    }

    public static void warn(String tag, String msg) {
        add(Level.WARN, tag, msg);
    }

    public static void error(String tag, String msg) {
        add(Level.ERROR, tag, msg);
    }

    private static void add(Level level, String tag, String msg) {
        getInstance().addLog(level, tag, msg);
    }

    // ======================== 内部方法 ========================

    private void addLog(Level level, String tag, String msg) {
        String time = new SimpleDateFormat("HH:mm:ss.SSS", Locale.CHINA).format(new Date());
        synchronized (logs) {
            logs.add(new LogEntry(time, level, tag, msg));
            if (logs.size() > MAX_LOG) logs.remove(0);
        }
        // 同步写入文件
        synchronized (fileLock) {
            if (fileWriter != null) {
                fileWriter.printf("%s [%s][%s] %s%n", time, levelStr(level), tag, msg);
                fileWriter.flush();
            }
        }
    }

    private String levelStr(Level level) {
        switch (level) {
            case DEBUG: return "D";
            case WARN:  return "W";
            case ERROR: return "E";
            default:    return "I";
        }
    }

    /**
     * 获取所有日志
     */
    public List<LogEntry> getLogs() {
        synchronized (logs) {
            return new ArrayList<>(logs);
        }
    }

    /**
     * 获取日志文本（带级别颜色标签）
     */
    public String getLogText() {
        StringBuilder sb = new StringBuilder();
        synchronized (logs) {
            for (LogEntry e : logs) {
                String levelTag;
                switch (e.level) {
                    case DEBUG: levelTag = "D"; break;
                    case WARN:  levelTag = "W"; break;
                    case ERROR: levelTag = "E"; break;
                    default:    levelTag = "I"; break;
                }
                sb.append(e.time)
                  .append(" [").append(levelTag).append("][").append(e.tag).append("] ")
                  .append(e.msg).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * 按标签过滤日志
     */
    public String getLogTextByTag(String tag) {
        StringBuilder sb = new StringBuilder();
        synchronized (logs) {
            for (LogEntry e : logs) {
                if (e.tag.equals(tag)) {
                    sb.append(e.time).append(" [").append(e.tag).append("] ").append(e.msg).append("\n");
                }
            }
        }
        return sb.toString();
    }

    /**
     * 清空日志
     */
    public void clear() {
        synchronized (logs) {
            logs.clear();
        }
        synchronized (fileLock) {
            if (fileWriter != null) {
                fileWriter.println("--- 日志已清空 ---");
                fileWriter.flush();
            }
        }
    }

    /**
     * 关闭文件日志
     */
    public static synchronized void close() {
        if (instance != null) {
            synchronized (instance.fileLock) {
                if (instance.fileWriter != null) {
                    instance.fileWriter.println("--- 日志结束 ---");
                    instance.fileWriter.close();
                    instance.fileWriter = null;
                }
            }
            instance = null;
        }
    }

    // ======================== 日志条目 ========================

    public static class LogEntry {
        public final String time;
        public final Level level;
        public final String tag;
        public final String msg;

        LogEntry(String time, Level level, String tag, String msg) {
            this.time = time;
            this.level = level;
            this.tag = tag;
            this.msg = msg;
        }
    }
}