package com.train12306;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 应用日志系统 — 内存环形缓冲，全局单例
 * <p>
 * 特性：
 * - 日志级别：DEBUG / INFO / WARN / ERROR
 * - 环形缓冲，最多 500 条
 * - 线程安全
 * - 支持按级别过滤
 */
public class AppLogger {

    private static AppLogger instance;
    private final List<LogEntry> logs = new ArrayList<>();
    private static final int MAX_LOG = 500;

    /** 日志级别 */
    public enum Level {
        DEBUG(0), INFO(1), WARN(2), ERROR(3);

        final int value;
        Level(int value) { this.value = value; }
    }

    private AppLogger() {}

    public static synchronized AppLogger getInstance() {
        if (instance == null) instance = new AppLogger();
        return instance;
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