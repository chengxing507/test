package com.train12306;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Calendar;

/**
 * 主页面 — 12306 智能助手
 * <p>
 * 功能：
 * - 输入出发/到达站、选择日期
 * - 车次类型筛选 (G/D/Z/T/K)
 * - 直接调用 12306 API 查询余票
 * - 无需 MCP 服务器
 */
public class MainActivity extends Activity {

    private EditText etFrom, etTo;
    private Button btnDate;
    private ProgressBar progressBar;
    private TextView tvStatus;

    private String selectedDate = "";
    private String stationFromName = "", stationToName = "";
    private String filterFlags = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        initListeners();

        // 异步预加载站点数据
        preloadStationData();
    }

    private void initViews() {
        etFrom = findViewById(R.id.et_from);
        etTo = findViewById(R.id.et_to);
        btnDate = findViewById(R.id.btn_date);
        progressBar = findViewById(R.id.progress_bar);
        tvStatus = findViewById(R.id.tv_status);

        Button btnSwap = findViewById(R.id.btn_swap);
        Button btnQuery = findViewById(R.id.btn_query);
        Button btnMultiLeg = findViewById(R.id.btn_multi_leg);
        Button btnSettings = findViewById(R.id.btn_settings);
        Button btnFilter = findViewById(R.id.btn_filter);
        Button btnLog = findViewById(R.id.btn_log);

        // 设置默认日期为今天
        Calendar cal = Calendar.getInstance();
        selectedDate = String.format("%04d-%02d-%02d",
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH));
        btnDate.setText(selectedDate);

        btnDate.setOnClickListener(v -> showDatePicker());
        btnSwap.setOnClickListener(v -> swapStations());

        ButtonGuard.guard(btnSettings, () ->
                startActivity(new Intent(MainActivity.this, SettingsActivity.class)));

        ButtonGuard.guard(btnMultiLeg, () ->
                startActivity(new Intent(MainActivity.this, MultiLegActivity.class)));

        ButtonGuard.guard(btnLog, () ->
                startActivity(new Intent(MainActivity.this, LogActivity.class)));

        btnFilter.setOnClickListener(v -> showFilterDialog());

        ButtonGuard.guard(btnQuery, () -> {
            stationFromName = etFrom.getText().toString().trim();
            stationToName = etTo.getText().toString().trim();
            if (stationFromName.isEmpty() || stationToName.isEmpty()) {
                Toast.makeText(MainActivity.this, "请输入出发站和到达站", Toast.LENGTH_SHORT).show();
                return;
            }
            doQuery();
        });
    }

    private void initListeners() {
        // 预留：后续添加 AutoCompleteTextView 站名自动补全
    }

    // ======================== 预加载站点数据 ========================

    /**
     * 进入页面时异步预加载站点数据，减少查询等待时间
     */
    private void preloadStationData() {
        if (StationDataManager.isLoaded()) {
            tvStatus.setText("✅ 站点数据已就绪");
            return;
        }

        tvStatus.setText("⏳ 正在加载站点数据...");
        progressBar.setVisibility(View.VISIBLE);

        new Thread(() -> {
            try {
                // 触发一次查询，会下载并缓存 station_name.js
                StationDataManager.getStationCode("北京");
                runOnUiThread(() -> {
                    tvStatus.setText("✅ 站点数据已就绪");
                    progressBar.setVisibility(View.GONE);
                    AppLogger.log("MAIN", "站点数据预加载成功");
                });
            } catch (Exception e) {
                AppLogger.warn("MAIN", "站点数据预加载失败: " + e.getMessage());
                runOnUiThread(() -> {
                    tvStatus.setText("⚠️ 站点数据加载失败，查询时自动重试");
                    progressBar.setVisibility(View.GONE);
                });
            }
        }).start();
    }

    // ======================== 日期选择 ========================

    private void showDatePicker() {
        Calendar cal = Calendar.getInstance();
        DatePickerDialog dpd = new DatePickerDialog(this,
                (view, year, month, dayOfMonth) -> {
                    selectedDate = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth);
                    btnDate.setText(selectedDate);
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH));
        dpd.getDatePicker().setMinDate(System.currentTimeMillis() - 86400000);
        dpd.show();
    }

    // ======================== 站点交换 ========================

    private void swapStations() {
        String tmp = etFrom.getText().toString();
        etFrom.setText(etTo.getText().toString());
        etTo.setText(tmp);
        String tmpName = stationFromName;
        stationFromName = stationToName;
        stationToName = tmpName;
    }

    // ======================== 车次筛选 ========================

    private void showFilterDialog() {
        final String[] items = {"高铁 G", "动车 D", "直达 Z", "特快 T", "快速 K"};
        final boolean[] checked = {false, false, false, false, false};
        final String[] codes = {"G", "D", "Z", "T", "K"};

        new AlertDialog.Builder(this)
                .setTitle("车次筛选")
                .setMultiChoiceItems(items, checked, null)
                .setPositiveButton("确定", (dialog, which) -> {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < checked.length; i++) {
                        if (checked[i]) sb.append(codes[i]);
                    }
                    filterFlags = sb.toString();
                    String msg = filterFlags.isEmpty() ? "显示全部车次" : "已筛选: " + filterFlags;
                    Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                    tvStatus.setText(msg);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ======================== 查询核心 ========================

    private void doQuery() {
        final String from = stationFromName;
        final String to = stationToName;
        final String date = selectedDate;

        AppLogger.log("QUERY", "开始查询: " + from + " -> " + to + " | " + date);

        // 更新 UI 状态
        tvStatus.setText("⏳ 查询中...");
        progressBar.setVisibility(View.VISIBLE);

        Toast.makeText(this, "正在查询车票...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                // 步骤1: 查询出发站代码
                AppLogger.log("QUERY", "查询站点代码: " + from);
                updateStatus("正在查询 " + from + " 的站点代码...");
                String fromCode = StationDataManager.getStationCode(from);

                // 步骤2: 查询到达站代码
                AppLogger.log("QUERY", "查询站点代码: " + to);
                updateStatus("正在查询 " + to + " 的站点代码...");
                String toCode = StationDataManager.getStationCode(to);

                AppLogger.log("QUERY", "站点码: " + from + "=" + fromCode + ", " + to + "=" + toCode);

                if (fromCode == null || fromCode.isEmpty()) {
                    showError("未找到站点「" + from + "」的代码，请检查站名是否正确");
                    return;
                }
                if (toCode == null || toCode.isEmpty()) {
                    showError("未找到站点「" + to + "」的代码，请检查站名是否正确");
                    return;
                }

                // 步骤3: 查询车次
                updateStatus("正在查询 " + date + " 的车次...");
                final String jsonResponse = TicketQueryManager.queryTickets(date, fromCode, toCode, filterFlags);

                // 解析并传递到车次列表页
                final String parsedData = parseAndFilterTickets(jsonResponse, filterFlags);

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Intent intent = new Intent(MainActivity.this, TicketListActivity.class);
                    intent.putExtra("ticket_data", parsedData);
                    intent.putExtra("query_date", date);
                    intent.putExtra("from_station", from);
                    intent.putExtra("to_station", to);
                    startActivity(intent);
                    tvStatus.setText("✅ 查询完成：" + from + " → " + to);
                });

            } catch (final Exception e) {
                AppLogger.error("QUERY", "查询失败: " + e.getMessage());
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    tvStatus.setText("❌ 查询失败: " + e.getMessage());
                    Toast.makeText(MainActivity.this,
                            "查询失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    /**
     * 解析 12306 API 返回的 JSON，提取车次文本
     * 格式：车次 出发站 到达站 出发时间 到达时间 历时
     */
    private String parseAndFilterTickets(String json, String filterFlags) {
        StringBuilder result = new StringBuilder();

        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (!root.get("status").getAsBoolean()) {
                return "查询失败: " + root.toString();
            }

            JsonArray results = root.getAsJsonObject("data")
                    .getAsJsonArray("result");

            for (int i = 0; i < results.size(); i++) {
                String line = results.get(i).getAsString();
                String[] parts = line.split("\\|");

                // 字段索引: 3=车次, 6=出发站码, 7=到达站码, 8=出发时间, 9=到达时间, 10=历时
                String trainCode = parts.length > 3 ? parts[3] : "";
                String startTime = parts.length > 8 ? parts[8] : "";
                String arriveTime = parts.length > 9 ? parts[9] : "";
                String duration = parts.length > 10 ? parts[10] : "";

                // 车次筛选
                if (!filterFlags.isEmpty()) {
                    boolean matched = false;
                    for (char c : filterFlags.toCharArray()) {
                        if (trainCode.startsWith(String.valueOf(c))) {
                            matched = true;
                            break;
                        }
                    }
                    if (!matched) continue;
                }

                result.append(trainCode).append(" ")
                        .append(stationFromName).append(" ")
                        .append(stationToName).append(" ")
                        .append(startTime).append(" ")
                        .append(arriveTime).append(" ")
                        .append(duration).append("\n");
            }
        } catch (Exception e) {
            AppLogger.error("QUERY", "解析车次失败: " + e.getMessage());
            return "解析失败: " + e.getMessage();
        }

        AppLogger.log("QUERY", "解析到 " + result.toString().split("\n").length + " 个车次");
        return result.toString().trim();
    }

    /**
     * 在 UI 线程显示错误信息
     */
    private void showError(final String msg) {
        runOnUiThread(() -> {
            tvStatus.setText("❌ " + msg);
            progressBar.setVisibility(View.GONE);
            Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
        });
    }

    /**
     * 在 UI 线程更新状态文字
     */
    private void updateStatus(final String msg) {
        runOnUiThread(() -> tvStatus.setText(msg));
    }
}