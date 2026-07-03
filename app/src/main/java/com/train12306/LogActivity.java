package com.train12306;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Arrays;
import java.util.Comparator;

/**
 * 运行日志页面 — 终端风格
 * <p>
 * 特性：
 * - 黑底绿字等宽字体（终端风格）
 * - 自动滚动到底部
 * - 一键复制所有日志
 * - 刷新 / 清空功能
 * - 查看历史日志文件（即使闪退后也能找到）
 */
public class LogActivity extends Activity {

    private TextView tvLog;
    private ScrollView scrollView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log);

        tvLog = findViewById(R.id.tv_log);
        scrollView = findViewById(R.id.scroll_log);
        Button btnRefresh = findViewById(R.id.btn_refresh);
        Button btnClear = findViewById(R.id.btn_clear);
        Button btnBack = findViewById(R.id.btn_back);
        TextView btnCopy = findViewById(R.id.btn_copy_log);
        TextView btnHistory = findViewById(R.id.btn_history);

        refreshLog();

        btnRefresh.setOnClickListener(v -> refreshLog());
        btnClear.setOnClickListener(v -> clearLog());
        btnBack.setOnClickListener(v -> finish());

        btnCopy.setOnClickListener(v -> copyLog());
        btnHistory.setOnClickListener(v -> showHistoryFiles());
    }

    /**
     * 刷新日志显示（带自动滚动到底部）
     */
    private void refreshLog() {
        String logText = AppLogger.getInstance().getLogText();
        tvLog.setText(logText.isEmpty() ? "暂无日志" : logText);

        // 底部附加日志路径提示
        String path = AppLogger.getLogDirPath();
        if (!path.isEmpty()) {
            tvLog.append("\n\n--- 日志目录: " + path + " ---");
        }

        // 自动滚动到底部
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    /**
     * 清空所有日志
     */
    private void clearLog() {
        AppLogger.getInstance().clear();
        tvLog.setText("");
        Toast.makeText(this, "日志已清空", Toast.LENGTH_SHORT).show();
    }

    /**
     * 复制日志到剪贴板
     */
    private void copyLog() {
        String text = tvLog.getText().toString();
        if (text.isEmpty() || "暂无日志".equals(text)) {
            Toast.makeText(this, "没有内容可复制", Toast.LENGTH_SHORT).show();
            return;
        }

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("log", text));
            Toast.makeText(this, "✅ 日志已复制（" + text.length() + " 字符）", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 显示历史日志文件列表
     */
    private void showHistoryFiles() {
        String dirPath = AppLogger.getLogDirPath();
        if (dirPath.isEmpty()) {
            Toast.makeText(this, "日志目录不可用", Toast.LENGTH_SHORT).show();
            return;
        }

        File logDir = new File(dirPath);
        File[] files = logDir.listFiles((d, name) -> name.endsWith(".log"));
        if (files == null || files.length == 0) {
            Toast.makeText(this, "没有历史日志文件", Toast.LENGTH_SHORT).show();
            return;
        }

        // 按修改时间倒序排列
        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));

        StringBuilder sb = new StringBuilder();
        sb.append("=== 历史日志文件 (").append(files.length).append(" 个) ===\n\n");
        for (int i = 0; i < Math.min(files.length, 10); i++) {
            File f = files[i];
            long size = f.length();
            String sizeStr = size < 1024 ? size + "B" : String.format("%.1fKB", size / 1024.0);
            sb.append("[").append(i + 1).append("] ")
              .append(f.getName()).append(" (").append(sizeStr).append(")\n");
        }
        sb.append("\n文件路径: ").append(dirPath);

        // 显示最近一个日志文件的内容预览
        if (files.length > 0) {
            try {
                BufferedReader reader = new BufferedReader(new FileReader(files[0]));
                StringBuilder content = new StringBuilder();
                String line;
                int lineCount = 0;
                while ((line = reader.readLine()) != null && lineCount < 50) {
                    content.append(line).append("\n");
                    lineCount++;
                }
                reader.close();

                sb.append("\n\n=== 最新日志预览 (").append(lineCount).append(" 行) ===\n\n");
                sb.append(content);
                if (lineCount >= 50) sb.append("... (更多内容请查看文件)");
            } catch (Exception e) {
                sb.append("\n\n(读取文件失败: ").append(e.getMessage()).append(")");
            }
        }

        tvLog.setText(sb.toString());
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));

        Toast.makeText(this, "已加载历史日志（最近 " + Math.min(files.length, 10) + " 个文件）", Toast.LENGTH_SHORT).show();
    }
}