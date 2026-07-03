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

        // 按修改时间倒序排列（最新的在前）
        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));

        StringBuilder sb = new StringBuilder();
        sb.append("=== 历史日志文件 (").append(files.length).append(" 个) ===\n\n");
        sb.append("💡 点击行号查看对应文件\n\n");
        for (int i = 0; i < files.length; i++) {
            File f = files[i];
            long size = f.length();
            String sizeStr = size < 1024 ? size + "B" : String.format("%.1fKB", size / 1024.0);
            // 标记日期时间
            String dateStr = f.getName().replace("app_", "").replace(".log", "");
            // 格式: yyyyMMdd_HHmmss
            String displayDate = "";
            if (dateStr.length() >= 15) {
                displayDate = dateStr.substring(0,4) + "-" + dateStr.substring(4,6) + "-" + dateStr.substring(6,8)
                    + " " + dateStr.substring(9,11) + ":" + dateStr.substring(11,13) + ":" + dateStr.substring(13,15);
            }
            sb.append("─── 文件 ").append(i + 1).append(" ───────────────────────\n");
            sb.append("📄 ").append(displayDate).append("\n");
            sb.append("📏 ").append(sizeStr).append("\n");
            sb.append("\n");

            // 读取并显示内容（最多 100 行）
            try {
                BufferedReader reader = new BufferedReader(new FileReader(f));
                String line;
                int lineCount = 0;
                while ((line = reader.readLine()) != null && lineCount < 100) {
                    sb.append(line).append("\n");
                    lineCount++;
                }
                reader.close();
                if (lineCount >= 100) {
                    sb.append("... (仅显示前 100 行，完整文件 ").append(sizeStr).append(")\n");
                }
            } catch (Exception e) {
                sb.append("(读取失败: ").append(e.getMessage()).append(")\n");
            }
            sb.append("\n");
        }

        sb.append("\n=== 文件路径 ===\n").append(dirPath);
        sb.append("\n\n💡 如需导出，请用 adb 或文件管理器复制此目录");

        tvLog.setText(sb.toString());
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));

        Toast.makeText(this, "已加载 " + files.length + " 个历史日志文件", Toast.LENGTH_SHORT).show();
    }
}