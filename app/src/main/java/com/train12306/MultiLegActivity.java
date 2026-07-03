package com.train12306;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 换乘规划页面
 */
public class MultiLegActivity extends Activity {

    private EditText etFrom, etTo;
    private Spinner spinnerMode, spinnerMaxTrans, spinnerMaxInterval;
    private LinearLayout layoutWaypoints;
    private CheckBox cbAiFilterHubs;
    private Button btnAddWaypoint, btnQuery, btnCancel, btnBack;
    private Button btnSortTime, btnSortPrice, btnAiFilter, btnAiConfig;
    private TextView tvStatus, tvEmpty, tvResultCount;
    private ListView listPaths;

    private final List<EditText> waypointInputs = new ArrayList<>();
    private final List<MultiLegPlanner.Path> allPaths = new ArrayList<>();
    private PathAdapter pathAdapter;

    private MultiLegPlanner planner;

    private boolean sortTimeAsc = true;
    private boolean sortPriceAsc = true;

    // 实时日志
    private TextView tvLiveLog;
    private LinearProgressIndicator linearProgress;
    private TextView tvProgressPercent;
    private StringBuilder liveLogBuilder = new StringBuilder();
    private Handler logHandler = new Handler();

    private static final String[] MODE_NAMES = {"自动换乘", "途经站序列", "弹性途经站"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multi_leg);

        initViews();
        setupSpinners();
        setupWaypoints();
        setupButtons();

        addWaypointInput();
        addWaypointInput();
    }

    private void initViews() {
        etFrom = findViewById(R.id.et_from);
        etTo = findViewById(R.id.et_to);
        spinnerMode = findViewById(R.id.spinner_mode);
        spinnerMaxTrans = findViewById(R.id.spinner_max_trans);
        spinnerMaxInterval = findViewById(R.id.spinner_max_interval);
        layoutWaypoints = findViewById(R.id.layout_waypoints);
        btnAddWaypoint = findViewById(R.id.btn_add_waypoint);
        cbAiFilterHubs = findViewById(R.id.cb_ai_filter_hubs);
        btnQuery = findViewById(R.id.btn_query);
        btnCancel = findViewById(R.id.btn_cancel);
        btnBack = findViewById(R.id.btn_back);
        btnSortTime = findViewById(R.id.btn_sort_time);
        btnSortPrice = findViewById(R.id.btn_sort_price);
        btnAiFilter = findViewById(R.id.btn_ai_filter);
        btnAiConfig = findViewById(R.id.btn_ai_config);
        tvStatus = findViewById(R.id.tv_status);
        tvEmpty = findViewById(R.id.tv_empty);
        tvResultCount = findViewById(R.id.tv_result_count);
        listPaths = findViewById(R.id.list_paths);

        // 实时日志与进度
        tvLiveLog = findViewById(R.id.tv_live_log);
        linearProgress = findViewById(R.id.linear_progress);
        tvProgressPercent = findViewById(R.id.tv_progress_percent);
        if (tvLiveLog != null) {
            tvLiveLog.setMovementMethod(new ScrollingMovementMethod());
        }

        pathAdapter = new PathAdapter();
        listPaths.setAdapter(pathAdapter);
    }

    private void setupSpinners() {
        ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, MODE_NAMES);
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMode.setAdapter(modeAdapter);

        Integer[] transValues = {1, 2, 3, 4, 5, 6};
        ArrayAdapter<Integer> transAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, transValues);
        transAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMaxTrans.setAdapter(transAdapter);
        spinnerMaxTrans.setSelection(2); // 默认 3

        Integer[] intervalValues = new Integer[48];
        for (int i = 0; i < 48; i++) intervalValues[i] = i + 1;
        ArrayAdapter<Integer> intervalAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, intervalValues);
        intervalAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMaxInterval.setAdapter(intervalAdapter);
        spinnerMaxInterval.setSelection(23); // 默认 24
    }

    private void setupWaypoints() {
        layoutWaypoints.removeAllViews();
        waypointInputs.clear();
    }

    private void addWaypointInput() {
        LinearLayout row = new LinearLayout(this);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 3, 0, 3);

        final int index = waypointInputs.size();
        TextView label = new TextView(this);
        label.setText("途经" + (index + 1) + ": ");
        label.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        label.setPadding(0, 8, 6, 0);
        label.setTextSize(13);

        EditText et = new EditText(this);
        et.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        et.setHint("站名");
        et.setPadding(6, 6, 6, 6);
        et.setBackgroundResource(android.R.drawable.editbox_background);
        et.setTextSize(13);

        Button btnDel = new Button(this);
        btnDel.setText("×");
        btnDel.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final EditText savedEt = et;
        btnDel.setOnClickListener(v -> {
            waypointInputs.remove(savedEt);
            layoutWaypoints.removeView(row);
            relabelWaypoints();
        });

        row.addView(label);
        row.addView(et);
        row.addView(btnDel);
        layoutWaypoints.addView(row);
        waypointInputs.add(et);
    }

    private void relabelWaypoints() {
        for (int i = 0; i < layoutWaypoints.getChildCount(); i++) {
            View row = layoutWaypoints.getChildAt(i);
            if (row instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) row;
                if (group.getChildCount() > 0) {
                    View first = group.getChildAt(0);
                    if (first instanceof TextView) {
                        ((TextView) first).setText("途经" + (i + 1) + ": ");
                    }
                }
            }
        }
    }

    private void setupButtons() {
        btnAddWaypoint.setOnClickListener(v -> addWaypointInput());

        btnQuery.setOnClickListener(v -> {
            btnQuery.setEnabled(false);
            startQuery();
        });

        btnCancel.setOnClickListener(v -> {
            if (planner != null) planner.cancel();
            setStatus("已取消");
            btnCancel.setVisibility(View.GONE);
            if (linearProgress != null) linearProgress.setVisibility(View.GONE);
            if (tvProgressPercent != null) tvProgressPercent.setVisibility(View.GONE);
            btnQuery.setEnabled(true);
        });

        btnBack.setOnClickListener(v -> goBackToHome());

        // 排序按钮
        btnSortTime.setOnClickListener(v -> {
            sortTimeAsc = !sortTimeAsc;
            btnSortTime.setText("时间 " + (sortTimeAsc ? "↑" : "↓"));
            sortResults();
        });
        btnSortPrice.setOnClickListener(v -> {
            // 改为显示详细计算过程
            StringBuilder calcLog = new StringBuilder();
            calcLog.append("📊 路径计算过程:\n");
            calcLog.append("=======================\n");
            if (allPaths.isEmpty()) {
                calcLog.append("暂无路径数据\n");
            } else {
                for (int i = 0; i < Math.min(allPaths.size(), 20); i++) {
                    MultiLegPlanner.Path path = allPaths.get(i);
                    calcLog.append("路径 #").append(i + 1).append(":\n");
                    for (int j = 0; j < path.segments.size(); j++) {
                        MultiLegPlanner.Segment seg = path.segments.get(j);
                        calcLog.append("  第").append(j + 1).append("段: ")
                            .append(seg.trainCode).append(" ")
                            .append(seg.fromStation).append(" ").append(seg.fromTime)
                            .append(" → ").append(seg.toStation).append(" ").append(seg.toTime)
                            .append(" (历时 ").append(seg.duration).append(")\n");
                        if (j < path.segments.size() - 1) {
                            MultiLegPlanner.Segment next = path.segments.get(j + 1);
                            int waitMin = path.totalWaitMinutes; // 粗略
                            calcLog.append("    换乘等待: 计算中...\n");
                        }
                    }
                    calcLog.append("  总耗时: ").append(path.totalDuration)
                        .append(" | 换乘: ").append(path.transfers).append("次\n");
                    calcLog.append("----------------------\n");
                }
            }
            String calcStr = calcLog.toString();
            appendLog(calcStr + "\n");
            setStatus("✅ 计算过程已输出到日志");
            Toast.makeText(this, "计算过程已输出到日志区", Toast.LENGTH_SHORT).show();
        });

        btnAiFilter.setOnClickListener(v -> openAIFilter());
        btnAiConfig.setOnClickListener(v -> showPromptConfigDialog());
    }

    @Override
    public void onBackPressed() {
        goBackToHome();
    }

    private void goBackToHome() {
        // 逐级退出：直接回到 MainActivity
        AppLogger.log("MULTI", "返回首页");
        finish();
    }

    private void startQuery() {
        String from = etFrom.getText().toString().trim();
        String to = etTo.getText().toString().trim();
        if (from.isEmpty() || to.isEmpty()) {
            Toast.makeText(this, "请输入起点和终点", Toast.LENGTH_SHORT).show();
            btnQuery.setEnabled(true);
            return;
        }

        int modeIdx = spinnerMode.getSelectedItemPosition();
        int maxTrans = (int) spinnerMaxTrans.getSelectedItem();
        int maxInterval = (int) spinnerMaxInterval.getSelectedItem();

        List<String> waypoints = new ArrayList<>();
        for (EditText et : waypointInputs) {
            String w = et.getText().toString().trim();
            if (!w.isEmpty()) waypoints.add(w);
        }

        setQuerying(true);
        allPaths.clear();
        pathAdapter.notifyDataSetChanged();
        tvEmpty.setVisibility(View.GONE);
        listPaths.setVisibility(View.GONE);
        findViewById(R.id.layout_results_controls).setVisibility(View.GONE);

        planner = new MultiLegPlanner(getQueryDate(), maxTrans, maxInterval);
        planner.setCallback(new MultiLegPlanner.ProgressCallback() {
            @Override
            public void onProgress(String msg) {
                runOnUiThread(() -> {
                    setStatus(msg);
                    appendLog(msg + "\n");
                });
            }
            @Override
            public void onProgressPercent(int current, int total, String message) {
                runOnUiThread(() -> {
                    updateProgress(current, total);
                    appendLog(String.format("[%d/%d] %s\n", current, total, message));
                });
            }
            @Override
            public void onError(String msg) {
                runOnUiThread(() -> {
                    setStatus("❌ " + msg);
                    appendLog("❌ " + msg + "\n");
                });
            }
            @Override
            public boolean isCancelled() { return false; }
        });

        new Thread(() -> {
            // AI 预筛选枢纽站（自动模式且勾选了复选框时）— 在后台线程执行
            if (modeIdx == 0 && cbAiFilterHubs.isChecked()) {
                SharedPreferences prefs = getSharedPreferences("ai_config", MODE_PRIVATE);
                String baseUrl = prefs.getString("base_url", "");
                String apiKey = prefs.getString("api_key", "");
                String modelName = prefs.getString("model_name", "");
                if (!baseUrl.isEmpty() && !apiKey.isEmpty()) {
                    appendLog("🤖 AI 正在预筛选枢纽站，请稍候...\n");
                    updateStatus("🤖 AI 正在预筛选枢纽站...");
                    final boolean filtered = planner.filterHubsByAI(from, to, baseUrl, apiKey, modelName);
                    if (filtered) {
                        int count = planner.getActiveHubs().size();
                        String msg = String.format("🤖 AI 筛选后保留 %d 个枢纽站", count);
                        appendLog(msg + "\n");
                        updateStatus(msg);
                    } else {
                        String msg = "⚠️ AI 筛选失败，使用全部枢纽站";
                        appendLog(msg + "\n");
                        updateStatus(msg);
                    }
                }
            }

            // 正式查询
            List<MultiLegPlanner.Path> results;
            try {
                switch (modeIdx) {
                    case 0:
                        planner.setMode(MultiLegPlanner.Mode.AUTO);
                        results = planner.planAutoTransfer(from, to, waypoints, false);
                        break;
                    case 1:
                        planner.setMode(MultiLegPlanner.Mode.WAYPOINT);
                        List<String> stations = new ArrayList<>();
                        stations.add(from);
                        stations.addAll(waypoints);
                        stations.add(to);
                        results = planner.planWithWaypoints(stations);
                        break;
                    case 2:
                        planner.setMode(MultiLegPlanner.Mode.FLEXIBLE);
                        results = planner.planFlexible(from, to, waypoints);
                        break;
                    default:
                        results = new ArrayList<>();
                }
            } catch (Exception e) {
                AppLogger.error("MULTI", "查询异常: " + e.getMessage());
                results = new ArrayList<>();
            }

            List<MultiLegPlanner.Path> finalResults = results;
            runOnUiThread(() -> {
                setQuerying(false);
                allPaths.clear();
                allPaths.addAll(finalResults);
                sortResults();
                showResults();
            });
        }).start();
    }

    private void sortResults() {
        if (allPaths.isEmpty()) return;
        Collections.sort(allPaths, (a, b) -> {
            int cmp = Integer.compare(a.totalMinutes, b.totalMinutes);
            return sortTimeAsc ? cmp : -cmp;
        });
        pathAdapter.notifyDataSetChanged();
    }

    private void setQuerying(boolean querying) {
        if (linearProgress != null) {
            linearProgress.setVisibility(querying ? View.VISIBLE : View.GONE);
            if (querying) linearProgress.setProgress(0);
        }
        btnQuery.setEnabled(!querying);
        btnCancel.setVisibility(querying ? View.VISIBLE : View.GONE);
        if (tvProgressPercent != null) {
            tvProgressPercent.setVisibility(querying ? View.VISIBLE : View.GONE);
        }
        if (querying) {
            setStatus("正在查询...");
            clearLiveLog();
            appendLog("▶ 开始查询\n");
        }
    }

    private void setStatus(String msg) {
        if (tvStatus != null) {
            tvStatus.setText(msg);
        }
        AppLogger.log("MULTI", msg);
    }

    /** 在后台线程中更新状态（自动 post 到 UI 线程） */
    private void updateStatus(final String msg) {
        runOnUiThread(() -> {
            if (tvStatus != null) tvStatus.setText(msg);
        });
    }

    /** 添加实时日志 */
    private void appendLog(final String logLine) {
        runOnUiThread(() -> {
            if (tvLiveLog != null) {
                liveLogBuilder.append(logLine);
                if (liveLogBuilder.length() > 5000) {
                    liveLogBuilder.delete(0, liveLogBuilder.length() - 4000);
                }
                tvLiveLog.setText(liveLogBuilder.toString());
                // 自动滚动到底部
                final int scrollAmount = tvLiveLog.getLayout() != null ?
                        tvLiveLog.getLayout().getLineCount() * tvLiveLog.getLineHeight() : 0;
                if (scrollAmount > tvLiveLog.getHeight()) {
                    tvLiveLog.scrollTo(0, scrollAmount - tvLiveLog.getHeight());
                }
            }
        });
    }

    /** 清空实时日志 */
    private void clearLiveLog() {
        liveLogBuilder = new StringBuilder();
        if (tvLiveLog != null) tvLiveLog.setText("");
    }

    /** 更新水平进度条（带100%上限保护） */
    private void updateProgress(final int current, final int total) {
        runOnUiThread(() -> {
            if (linearProgress != null) {
                int pct = total > 0 ? Math.min(100, (int)((float)current / total * 100)) : 0;
                linearProgress.setProgress(pct);
                linearProgress.setMax(100);
            }
            if (tvProgressPercent != null) {
                int pct = total > 0 ? Math.min(100, (int)((float)current / total * 100)) : 0;
                tvProgressPercent.setText(String.format("%d%%", Math.min(pct, 100)));
            }
        });
    }

    private void showResults() {
        if (allPaths.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText("未找到符合条件的路线\n请尝试增加最大换乘次数或扩大间隔时间");
            listPaths.setVisibility(View.GONE);
            findViewById(R.id.layout_results_controls).setVisibility(View.GONE);
            appendLog("❌ 未找到符合条件的路线\n");
        } else {
            tvEmpty.setVisibility(View.GONE);
            listPaths.setVisibility(View.VISIBLE);
            findViewById(R.id.layout_results_controls).setVisibility(View.VISIBLE);
            tvResultCount.setText(String.format("共 %d 条路线", allPaths.size()));
            String msg = String.format("✅ 找到 %d 条路线", allPaths.size());
            setStatus(msg);
            appendLog(msg + "\n");
        }
        // 完成查询，进度条填满
        if (linearProgress != null) {
            linearProgress.setProgress(100);
        }
        if (tvProgressPercent != null) {
            tvProgressPercent.setText("100%");
        }
    }

    private String getQueryDate() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
                .format(new java.util.Date());
    }

    // ======================== AI 筛选 ========================

    private void openAIFilter() {
        if (allPaths.isEmpty()) {
            Toast.makeText(this, "没有路线可供分析", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences prefs = getSharedPreferences("ai_config", MODE_PRIVATE);
        String baseUrl = prefs.getString("base_url", "");
        String apiKey = prefs.getString("api_key", "");
        String modelName = prefs.getString("model_name", "");
        String prompt = prefs.getString("multi_leg_prompt", MultiLegPlanner.getDefaultAIPrompt());

        if (baseUrl.isEmpty() || apiKey.isEmpty()) {
            Toast.makeText(this, "请先在设置中配置 AI API", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(MultiLegPlanner.getAIPromptPrefix()).append("\n");
        sb.append("筛选规则: ").append(prompt).append("\n\n");
        sb.append("以下是全部 ").append(allPaths.size()).append(" 条换乘方案:\n\n");

        for (int i = 0; i < allPaths.size(); i++) {
            sb.append("方案").append(i + 1).append(": ")
              .append(allPaths.get(i).getDetailed()).append("\n");
        }
        sb.append("\n请按规则筛选后，输出推荐的方案编号和理由。");

        final String promptText = sb.toString();

        setStatus("🤖 AI 分析中...");
        // 在结果列表上方显示 AI 分析结果（非弹窗）
        final TextView tvAiResult = new TextView(this);
        tvAiResult.setPadding(12, 12, 12, 12);
        tvAiResult.setTextSize(14);
        tvAiResult.setTextColor(0xFF1A1C1E);
        tvAiResult.setLineSpacing(4, 1);
        tvAiResult.setText("⏳ AI 正在分析...\n" + promptText.substring(0, Math.min(100, promptText.length())) + "...");
        
        // 插入到结果列表上方
        final LinearLayout rootLayout = findViewById(R.id.layout_results_controls);
        if (rootLayout != null && rootLayout.getParent() instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) rootLayout.getParent();
            // 移除旧的 AI 结果 View（如果有）
            View oldResult = findViewById(R.id.tv_ai_analysis_result);
            if (oldResult != null) {
                ((ViewGroup) oldResult.getParent()).removeView(oldResult);
            }
            tvAiResult.setId(R.id.tv_ai_analysis_result);
            // 在结果控制栏下方插入
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(8, 8, 8, 8);
            tvAiResult.setLayoutParams(lp);
            // 找到合适的父容器插入
            if (parent instanceof LinearLayout) {
                int insertIdx = Math.max(0, parent.indexOfChild(rootLayout) + 1);
                ((LinearLayout) parent).addView(tvAiResult, insertIdx);
            }
        }

        final AIAnalysisClient aiClient = new AIAnalysisClient(baseUrl, apiKey, modelName);
        new Thread(() -> {
            try {
                String result = aiClient.analyzeRoute("", promptText);
                runOnUiThread(() -> {
                    tvAiResult.setText("🤖 AI 分析结果:\n\n" + result);
                    setStatus("✅ AI 分析完成");
                    appendLog("✅ AI 分析完成\n");
                    AppLogger.log("AI_RAW", "AI 进一步分析原始回复: " + result);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    tvAiResult.setText("❌ AI 分析失败: " + e.getMessage());
                    setStatus("❌ AI 分析失败: " + e.getMessage());
                    appendLog("❌ AI 分析失败: " + e.getMessage() + "\n");
                });
            }
        }).start();
    }

    private void showAIResult(String result) {
        // 已废弃 - 改用非弹窗显示
    }

    /** 显示 Prompt 配置对话框 — 预设 + 自定义 */
    private void showPromptConfigDialog() {
        final SharedPreferences prefs = getSharedPreferences("ai_config", MODE_PRIVATE);

        // 预设列表（带编号标签）
        final String[] presets = MultiLegPlanner.getBuiltinPrompts();
        final String[] displayItems = new String[presets.length + 1];
        for (int i = 0; i < presets.length; i++) {
            displayItems[i] = "【预设 " + (i + 1) + "】" + presets[i].substring(0,
                    Math.min(35, presets[i].length())) + "...";
        }
        displayItems[presets.length] = "✏️ 自定义编辑";

        new AlertDialog.Builder(this)
                .setTitle("选择或编辑 AI Prompt")
                .setItems(displayItems, (dialog, which) -> {
                    if (which < presets.length) {
                        // 选中预设
                        String selected = presets[which];
                        prefs.edit().putString("multi_leg_prompt", selected).apply();
                        Toast.makeText(this, "已应用预设 " + (which + 1), Toast.LENGTH_SHORT).show();
                    } else {
                        // 自定义编辑
                        String current = prefs.getString("multi_leg_prompt", presets[0]);
                        showEditPromptDialog(current);
                    }
                })
                .setNeutralButton("查看当前", (d, w) -> {
                    String current = prefs.getString("multi_leg_prompt", presets[0]);
                    new AlertDialog.Builder(this)
                            .setTitle("当前 Prompt")
                            .setMessage(current)
                            .setPositiveButton("编辑", (d2, w2) -> showEditPromptDialog(current))
                            .setNegativeButton("关闭", null)
                            .show();
                })
                .setPositiveButton("关闭", null)
                .show();
    }

    private void showEditPromptDialog(String initialText) {
        EditText et = new EditText(this);
        et.setText(initialText);
        et.setPadding(16, 16, 16, 16);
        et.setMinHeight(200);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        et.setGravity(android.view.Gravity.TOP);

        new AlertDialog.Builder(this)
                .setTitle("编辑 AI Prompt")
                .setView(et)
                .setPositiveButton("保存", (d, w) -> {
                    String newPrompt = et.getText().toString().trim();
                    getSharedPreferences("ai_config", MODE_PRIVATE)
                            .edit()
                            .putString("multi_leg_prompt", newPrompt)
                            .apply();
                    Toast.makeText(this, "已保存自定义 Prompt", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ======================== 列表适配器 ========================

    private class PathAdapter extends BaseAdapter {
        @Override
        public int getCount() { return allPaths.size(); }

        @Override
        public Object getItem(int i) { return allPaths.get(i); }

        @Override
        public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(
                        R.layout.item_multi_leg_path, parent, false);
            }

            MultiLegPlanner.Path path = allPaths.get(i);

            TextView tvSegments = convertView.findViewById(R.id.tv_segments);
            TextView tvInfo = convertView.findViewById(R.id.tv_info);
            TextView tvDetail = convertView.findViewById(R.id.tv_detail);

            tvSegments.setText(path.getSummary());
            tvInfo.setText(String.format("🔄 %d次换乘 | ⏱ %s | ⏳ 等待%d分 | 🚉 %s→%s",
                    path.transfers, path.totalDuration, path.totalWaitMinutes,
                    path.firstDeparture, path.lastArrival));
            tvDetail.setText(path.getDetailed());

            convertView.setBackgroundColor(i == 0 ? 0xFFE3F2FD : 0xFFF5F5F5);

            return convertView;
        }
    }
}