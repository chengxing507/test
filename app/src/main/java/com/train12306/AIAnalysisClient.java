package com.train12306;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * AI 分析客户端 — 基于 OkHttp，兼容 OpenAI API 格式
 * <p>
 * 支持：
 * - OpenAI / DeepSeek / 通义千问等兼容接口
 * - 手动重定向（307/308）
 * - 60 秒超时
 * - 多种响应格式解析
 */
public class AIAnalysisClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final int TIMEOUT = 60;
    private static final int MAX_REDIRECTS = 5;

    private final String baseUrl;
    private final String apiKey;
    private final String modelName;
    private final OkHttpClient httpClient;
    private final Gson gson = new Gson();

    public AIAnalysisClient(String baseUrl, String apiKey, String modelName) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT, TimeUnit.SECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(true)
                .build();
    }

    /**
     * 测试连接 — 发送简单请求验证 API 可用性
     */
    public String testConnection() throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("model", modelName);

        JsonArray messages = new JsonArray();
        JsonObject msg = new JsonObject();
        msg.addProperty("role", "user");
        msg.addProperty("content", "Hello");
        messages.add(msg);

        body.add("messages", messages);
        body.addProperty("max_tokens", 5);

        callAPI(gson.toJson(body));
        return "连接成功！API 可用";
    }

    /**
     * AI 分析路线/车次
     *
     * @param systemPrompt 系统提示词
     * @param userMessage  用户消息 + 上下文
     * @return AI 回复文本
     */
    public String analyzeRoute(String systemPrompt, String userMessage) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("model", modelName);

        JsonArray messages = new JsonArray();

        JsonObject system = new JsonObject();
        system.addProperty("role", "system");
        system.addProperty("content", systemPrompt);
        messages.add(system);

        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", userMessage);
        messages.add(user);

        body.add("messages", messages);
        body.addProperty("temperature", 0.7);
        body.addProperty("max_tokens", 2000);

        return callAPI(gson.toJson(body));
    }

    /**
     * 核心 API 调用（支持手动重定向）
     */
    private String callAPI(String jsonBody) throws Exception {
        String targetUrl = baseUrl;
        int redirects = MAX_REDIRECTS;

        while (redirects-- > 0) {
            Request request = new Request.Builder()
                    .url(targetUrl)
                    .post(RequestBody.create(jsonBody, JSON))
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                int code = response.code();

                // 手动处理重定向，保持 POST 方法
                if (code == 301 || code == 302 || code == 307 || code == 308) {
                    String location = response.header("Location");
                    if (location == null || location.isEmpty()) {
                        throw new Exception("API 返回 " + code + " 但无 Location 头");
                    }
                    targetUrl = location;
                    AppLogger.log("AI", "API 重定向到: " + location);
                    continue;
                }

                String respBody = response.body() != null ? response.body().string() : "";

                if (code >= 200 && code < 300) {
                    return parseAIResponse(respBody);
                }

                throw new Exception("API 返回 " + code + ": " + respBody);
            }
        }
        throw new Exception("重定向次数过多 (超过 " + MAX_REDIRECTS + " 次)");
    }

    /**
     * 解析 AI API 响应，兼容多种格式
     */
    private String parseAIResponse(String json) throws Exception {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

            // OpenAI 格式: choices[0].message.content
            if (obj.has("choices")) {
                JsonArray choices = obj.getAsJsonArray("choices");
                if (choices.size() > 0) {
                    JsonObject first = choices.get(0).getAsJsonObject();

                    // Chat 格式: message.content
                    if (first.has("message")) {
                        JsonObject msg = first.getAsJsonObject("message");
                        if (msg.has("content")) {
                            return msg.get("content").getAsString();
                        }
                    }

                    // Completion 格式: text
                    if (first.has("text")) {
                        return first.get("text").getAsString();
                    }
                }
            }

            // 兼容其他格式
            if (obj.has("response")) return obj.get("response").getAsString();
            if (obj.has("output")) return obj.get("output").getAsString();

            // 无法解析，返回原始 JSON
            AppLogger.log("AI", "无法识别的 AI 响应格式，返回原始 JSON");
            return json;

        } catch (Exception e) {
            AppLogger.log("AI", "AI 响应解析失败: " + e.getMessage());
            throw new Exception("AI 响应解析失败: " + e.getMessage());
        }
    }
}