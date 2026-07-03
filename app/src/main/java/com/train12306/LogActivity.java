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

/**
 * 运行日志页面 — 终端风格
 * <p>
 * 特性：
 * - 黑底绿字等宽字体（终端风格）
 * - 自动滚动到底部
 * - 一键复制所有日志
 * - 刷新 / 清空功能
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

        refreshLog();

        btnRefresh.setOnClickListener(v -> refreshLog());
        btnClear.setOnClickListener(v -> clearLog());
        btnBack.setOnClickListener(v -> finish());

        btnCopy.setOnClickListener(v -> copyLog());
    }

    /**
     * 刷新日志显示（带自动滚动到底部）
     */
    private void refreshLog() {
        String logText = AppLogger.getInstance().getLogText();
        tvLog.setText(logText.isEmpty() ? "暂无日志" : logText);

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
}