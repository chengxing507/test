package com.train12306;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * 路线详情页面 — 显示指定车次的经停站列表
 * <p>
 * 直接调 12306 API 查询经停信息
 */
public class RouteDetailActivity extends Activity {

    private static final String QUERY_ROUTE_URL =
            "https://kyfw.12306.cn/otn/queryTrainInfo/query";

    private ListView listView;
    private TextView tvHeader;
    private TextView tvEmpty;
    private View tvLoading;  // 布局中是一个 LinearLayout
    private View progressBar;

    private final List<String> routeStations = new ArrayList<>();
    private String trainCode, queryDate;
    private String fromStation, toStation, startTime, arriveTime, duration;
    private String trainNo, fromCode, toCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_route_detail);

        listView = findViewById(R.id.list_route);
        tvHeader = findViewById(R.id.tv_route_header);
        tvEmpty = findViewById(R.id.tv_empty);
        tvLoading = findViewById(R.id.tv_loading);
        progressBar = findViewById(R.id.progress_bar);

        Button btnAnalyze = findViewById(R.id.btn_analyze);
        Button btnBack = findViewById(R.id.btn_back);

        // 获取传入数据
        trainCode = getIntent().getStringExtra("train_code");
        trainNo = getIntent().getStringExtra("train_no");
        queryDate = getIntent().getStringExtra("query_date");
        fromStation = getIntent().getStringExtra("from_station");
        toStation = getIntent().getStringExtra("to_station");
        fromCode = getIntent().getStringExtra("from_code");
        toCode = getIntent().getStringExtra("to_code");
        startTime = getIntent().getStringExtra("start_time");
        arriveTime = getIntent().getStringExtra("arrive_time");
        duration = getIntent().getStringExtra("duration");

        // 更新头部信息
        tvHeader.setText(trainCode + " | " + fromStation + " → " + toStation
                + " | " + startTime + " - " + arriveTime + " | " + duration);

        // 显示加载中
        showLoading();

        // 加载路线
        loadRoute();

        ButtonGuard.guard(btnAnalyze, () -> openAIAnalysis());
        btnBack.setOnClickListener(v -> finish());
    }

    private void showLoading() {
        tvLoading.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);
        listView.setVisibility(View.GONE);
    }

    /**
     * 异步加载经停站列表
     */
    private void loadRoute() {
        Toast.makeText(this, "加载路线中...", Toast.LENGTH_SHORT).show();
        AppLogger.log("ROUTE", "加载路线: " + trainCode + " | " + queryDate);

        new Thread(() -> {
            try {
                // 使用 train_no（内部编号）查询路线，配合站点代码
                String result = queryTrainRoute(trainNo, queryDate, fromCode, toCode);
                parseRoute(result);

                safeRunOnUiThread(() -> {
                    if (routeStations.isEmpty()) {
                        tvLoading.setVisibility(View.GONE);
                        progressBar.setVisibility(View.GONE);
                        tvEmpty.setVisibility(View.VISIBLE);
                    } else {
                        tvLoading.setVisibility(View.GONE);
                        progressBar.setVisibility(View.GONE);
                        tvEmpty.setVisibility(View.GONE);
                        listView.setVisibility(View.VISIBLE);
                        listView.setAdapter(new ArrayAdapter<>(
                                RouteDetailActivity.this,
                                android.R.layout.simple_list_item_1,
                                routeStations));
                    }
                });

            } catch (final Throwable t) {
                AppLogger.error("ROUTE", "路线加载失败: " + t.getMessage());
                safeRunOnUiThread(() -> {
                    tvLoading.setVisibility(View.GONE);
                    progressBar.setVisibility(View.GONE);
                    tvEmpty.setVisibility(View.VISIBLE);
                    Toast.makeText(RouteDetailActivity.this,
                            "路线加载失败: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    /**
     * 安全地在 UI 线程执行
     */
    private void safeRunOnUiThread(final Runnable action) {
        runOnUiThread(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                AppLogger.error("ROUTE_UI", "UI 更新异常: " + t.getMessage());
            }
        });
    }

    /**
     * 直接调 12306 API 查询经停站
     * 参数：trainNo（内部编号），date（日期）
     */
    private String queryTrainRoute(String trainNo, String date, String fromCode, String toCode) throws Exception {
        // 优先使用 train_no，如果为空则降级用车次代码
        String trainId = (trainNo != null && !trainNo.isEmpty()) ? trainNo : trainCode;

        // 12306 API 参数名是 trainNo（驼峰）和 date
        String urlStr = QUERY_ROUTE_URL
                + "?trainNo=" + URLEncoder.encode(trainId, "UTF-8")
                + "&date=" + date;

        AppLogger.log("ROUTE", "请求 URL: " + urlStr);

        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        conn.setRequestProperty("Referer", "https://kyfw.12306.cn/otn/leftTicket/init");
        conn.setRequestProperty("Accept", "application/json, text/plain, */*");

        int httpCode = conn.getResponseCode();
        AppLogger.log("ROUTE", "HTTP " + httpCode);

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();

        String response = sb.toString();
        AppLogger.log("ROUTE", "响应大小: " + response.length() + " bytes");
        return response;
    }

    /**
     * 解析 12306 API 响应中的经停站数据
     * 12306 返回格式: {"data": [{"station_name":"...", "arrive_time":"...", "start_time":"...", "stopover_time":"..."}, ...]}
     */
    private void parseRoute(String data) {
        try {
            // 打印原始响应到日志帮助调试
            AppLogger.log("ROUTE", "原始响应: " + (data.length() > 300 ? data.substring(0, 300) + "..." : data));

            // 使用 lenient 模式解析，兼容 12306 返回的非标准 JSON
            JsonObject json = new GsonBuilder().setLenient().create()
                    .fromJson(data, JsonObject.class);

            if (json == null) {
                routeStations.add("响应为空，可能参数不正确");
                return;
            }

            // 尝试从 data 字段获取车站数组
            if (json.has("data")) {
                if (json.get("data").isJsonArray()) {
                    // 格式: {"data": [{"station_name":"...", ...}, ...]}
                    JsonArray stations = json.getAsJsonArray("data");
                    for (int i = 0; i < stations.size(); i++) {
                        JsonObject station = stations.get(i).getAsJsonObject();
                        String stationName = getJsonStr(station, "station_name");
                        String arriveTime = getJsonStr(station, "arrive_time");
                        String departTime = getJsonStr(station, "start_time");
                        String stopTime = getJsonStr(station, "stopover_time");
                        routeStations.add((i + 1) + ". " + stationName
                                + "  " + arriveTime + "/" + departTime
                                + "  停" + stopTime);
                    }
                } else if (json.get("data").isJsonObject()) {
                    // 格式: {"data": {"data": [{"stations": [...]}, ...]}}
                    JsonObject dataObj = json.getAsJsonObject("data");
                    if (dataObj.has("data") && dataObj.get("data").isJsonArray()) {
                        JsonArray dataArray = dataObj.getAsJsonArray("data");
                        if (dataArray.size() > 0) {
                            JsonObject train = dataArray.get(0).getAsJsonObject();
                            if (train.has("stations")) {
                                JsonArray stations = train.getAsJsonArray("stations");
                                for (int i = 0; i < stations.size(); i++) {
                                    JsonObject station = stations.get(i).getAsJsonObject();
                                    String stationName = getJsonStr(station, "station_name");
                                    String arriveTime = getJsonStr(station, "arrive_time");
                                    String departTime = getJsonStr(station, "start_time");
                                    String stopTime = getJsonStr(station, "stopover_time");
                                    routeStations.add((i + 1) + ". " + stationName
                                            + "  " + arriveTime + "/" + departTime
                                            + "  停" + stopTime);
                                }
                            }
                        }
                    }
                }
            }

            if (routeStations.isEmpty()) {
                routeStations.add("该车次暂无经停站数据");
            }
            AppLogger.log("ROUTE", "解析到 " + routeStations.size() + " 个经停站");
        } catch (Throwable t) {
            AppLogger.error("ROUTE", "路线解析异常: " + t.getMessage());
            // 显示原始响应前 500 字符帮助调试
            String preview = data != null && data.length() > 500
                    ? data.substring(0, 500) + "..."
                    : (data != null ? data : "null");
            routeStations.add("⚠️ 解析失败: " + t.getMessage());
            routeStations.add("原始响应(前500字符): " + preview);
        }
    }

    private String getJsonStr(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return "-";
    }

    /**
     * 打开 AI 分析页面，携带路线详情
     */
    private void openAIAnalysis() {
        if (routeStations.isEmpty()) {
            Toast.makeText(this, "没有路线数据可供分析", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(RouteDetailActivity.this, AIAnalysisActivity.class);
        intent.putExtra("train_code", trainCode);

        StringBuilder sb = new StringBuilder();
        for (String s : routeStations) sb.append(s).append("\n");
        intent.putExtra("route_detail", sb.toString());

        intent.putExtra("from_station", fromStation);
        intent.putExtra("to_station", toStation);
        intent.putExtra("start_time", startTime);
        intent.putExtra("arrive_time", arriveTime);
        intent.putExtra("duration", duration);
        startActivity(intent);
    }
}