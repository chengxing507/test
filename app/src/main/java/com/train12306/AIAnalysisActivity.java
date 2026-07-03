package com.train12306;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * AI 分析页面 — 支持车次对比与路线分析
 * <p>
 * 特性：
 * - 从 TicketList 或 RouteDetail 传入数据上下文
 * - 自定义 Prompt 提问
 * - 三态 UI：空闲 / 加载中 / 结果展示
 * - 60 秒超时保护
 */
public class AIAnalysisActivity extends Activity {

    private EditText etPrompt;
    private TextView tvResult, tvHint;
    private ProgressBar progressBar;
    private Button btnAnalyze;

    private String ticketSummary = "";
    private String routeDetail = "";
    private String trainCode = "", fromStation = "", toStation = "";
    private String startTime = "", arriveTime = "", duration = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_analysis);

        etPrompt = findViewById(R.id.et_prompt);
        tvResult = findViewById(R.id.tv_result);
        tvHint = findViewById(R.id.tv_hint);
        progressBar = findViewById(R.id.progress_bar);
        btnAnalyze = findViewById(R.id.btn_analyze);
        Button btnBack = findViewById(R.id.btn_back);

        // 获取传入数据
        ticketSummary = getIntent().getStringExtra("ticket_summary");
        routeDetail = getIntent().getStringExtra("route_detail");
        trainCode = getIntent().getStringExtra("train_code");
        fromStation = getIntent().getStringExtra("from_station");
        toStation = getIntent().getStringExtra("to_station");
        startTime = getIntent().getStringExtra("start_time");
        arriveTime = getIntent().getStringExtra("arrive_time");
        duration = getIntent().getStringExtra("duration");

        // 根据传入数据设置不同的提示
        if (trainCode != null && !trainCode.isEmpty()) {
            tvHint.setText("当前分析车次: " + trainCode + " " + fromStation + "→" + toStation);
            etPrompt.setHint("例如：分析这趟车的时间安排是否合理");
        } else if (ticketSummary != null && !ticketSummary.isEmpty()) {
            tvHint.setText("共 " + ticketSummary.split("\\n").length + " 个车次可对比分析");
            etPrompt.setHint("例如：推荐性价比最高的车次并说明原因");
        } else {
            tvHint.setText("没有车次数据，您可以输入问题咨询");
            etPrompt.setHint("输入您的问题...");
        }

        ButtonGuard.guard(btnAnalyze, () -> doAnalysis());
        btnBack.setOnClickListener(v -> finish());
    }

    /**
     * 执行 AI 分析
     */
    private void doAnalysis() {
        SharedPreferences prefs = getSharedPreferences("ai_config", MODE_PRIVATE);
        final String baseUrl = prefs.getString("base_url", "");
        final String apiKey = prefs.getString("api_key", "");
        final String modelName = prefs.getString("model_name", "deepseek-v4-flash");

        if (baseUrl.isEmpty() || apiKey.isEmpty()) {
            Toast.makeText(this, "请先在设置中配置 AI API", Toast.LENGTH_LONG).show();
            return;
        }

        // 获取用户输入，如果为空则使用默认提示
        String userInput = etPrompt.getText().toString().trim();
        if (userInput.isEmpty()) {
            if (trainCode != null && !trainCode.isEmpty()) {
                userInput = "请分析这个车次的优缺点，给出出行建议";
            } else {
                userInput = "请根据以下车次信息，对比分析并推荐最佳车次";
            }
        }

        final String userPrompt = userInput;

        // 构建上下文
        StringBuilder context = new StringBuilder();
        context.append("===== 车次信息 =====\n");
        if (trainCode != null && !trainCode.isEmpty()) {
            context.append("车次：").append(trainCode).append("\n");
            context.append("路线：").append(fromStation).append(" → ").append(toStation).append("\n");
            context.append("时间：").append(startTime).append(" - ").append(arriveTime).append("\n");
            context.append("历时：").append(duration).append("\n\n");
        }
        if (routeDetail != null && !routeDetail.isEmpty()) {
            context.append("===== 经停站详情 =====\n").append(routeDetail).append("\n\n");
        }
        if (ticketSummary != null && !ticketSummary.isEmpty()) {
            context.append("===== 所有可选车次 =====\n").append(ticketSummary).append("\n\n");
        }
        context.append("===== 用户问题 =====\n").append(userPrompt);

        final String message = context.toString();

        // 切换到加载状态
        tvResult.setText("");
        progressBar.setVisibility(View.VISIBLE);
        btnAnalyze.setEnabled(false);
        tvHint.setText("⏳ AI 分析中，请稍候...");

        new Thread(() -> {
            try {
                AIAnalysisClient ai = new AIAnalysisClient(baseUrl, apiKey, modelName);
                final String result = ai.analyzeRoute(
                        "你是一个专业的火车出行助手。" +
                        "你精通中国铁路系统，熟悉各条线路和车次特点。" +
                        "请根据用户提供的车次信息，给出详细、实用的出行建议和分析。" +
                        "回答要简洁明了，重点突出，用中文回答。",
                        message);

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    tvResult.setText(result);
                    btnAnalyze.setEnabled(true);
                    tvHint.setText("✅ 分析完成");
                });

            } catch (final Exception e) {
                AppLogger.error("AI", "分析失败: " + e.getMessage());
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    tvResult.setText("分析失败: " + e.getMessage());
                    btnAnalyze.setEnabled(true);
                    tvHint.setText("❌ 分析失败，请重试");
                    Toast.makeText(AIAnalysisActivity.this,
                            "AI 分析失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
}