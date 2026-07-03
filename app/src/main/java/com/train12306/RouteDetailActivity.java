package com.train12306;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

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
        queryDate = getIntent().getStringExtra("query_date");
        fromStation = getIntent().getStringExtra("from_station");
        toStation = getIntent().getStringExtra("to_station");
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
                String result = queryTrainRoute(trainCode, queryDate);
                parseRoute(result);

                runOnUiThread(() -> {
                    if (routeStations.isEmpty()) {
                        tvLoading.setVisibility(View.GONE);
                        progressBar.setVisibility(View.GONE);
                        tvEmpty.setVisibility(View.VISIBLE);
                        tvEmpty.setText("未获取到路线信息");
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

            } catch (final Exception e) {
                AppLogger.error("ROUTE", "路线加载失败: " + e.getMessage());
                runOnUiThread(() -> {
                    tvLoading.setVisibility(View.GONE);
                    progressBar.setVisibility(View.GONE);
                    tvEmpty.setVisibility(View.VISIBLE);
                    tvEmpty.setText("路线加载失败: " + e.getMessage());
                    Toast.makeText(RouteDetailActivity.this,
                            "路线加载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    /**
     * 直接调 12306 API 查询经停站
     */
    private String queryTrainRoute(String trainCode, String date) throws Exception {
        String urlStr = QUERY_ROUTE_URL
                + "?train_no=" + URLEncoder.encode(trainCode, "UTF-8")
                + "&from_station_telecode="
                + "&to_station_telecode="
                + "&depart_date=" + date;

        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        conn.setRequestProperty("Referer", "https://kyfw.12306.cn/otn/leftTicket/init");

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();

        return sb.toString();
    }

    /**
     * 解析 12306 API 响应中的经停站数据
     */
    private void parseRoute(String data) {
        try {
            JsonObject json = JsonParser.parseString(data).getAsJsonObject();
            if (json.has("data")) {
                JsonArray dataArray = json.getAsJsonObject("data").getAsJsonArray("data");
                if (dataArray != null && dataArray.size() > 0) {
                    // 取第一个车次的数据
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
            AppLogger.log("ROUTE", "解析到 " + routeStations.size() + " 个经停站");
        } catch (Exception e) {
            AppLogger.error("ROUTE", "路线解析异常: " + e.getMessage());
            routeStations.add("解析失败: " + e.getMessage());
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