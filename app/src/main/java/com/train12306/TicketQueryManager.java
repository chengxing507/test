package com.train12306;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/**
 * 12306 车票查询管理器
 * <p>
 * 直接调用 12306 官方 API（kyfw.12306.cn），无需 MCP 服务器。
 * <p>
 * 流程：<br>
 * 1. 访问首页获取 Cookie<br>
 * 2. 查询车次列表<br>
 * 3. 返回原始 JSON 数据
 */
public class TicketQueryManager {

    private static final String INIT_URL = "https://kyfw.12306.cn/otn/leftTicket/init";
    private static final String QUERY_URL = "https://kyfw.12306.cn/otn/leftTicket/queryG";
    private static final String TRANSFER_URL = "https://kyfw.12306.cn/otn/leftTicket/queryT";

    private static CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);

    /**
     * 查询车票
     *
     * @param date        出发日期，格式 yyyy-MM-dd
     * @param fromCode    出发站代码
     * @param toCode      到达站代码
     * @param filterFlags 车次筛选 (G/D/Z/T/K...)，空字符串表示全部
     * @return 12306 API 返回的原始 JSON 字符串
     */
    public static String queryTickets(String date, String fromCode, String toCode, String filterFlags)
            throws Exception {

        // 1. 获取 Cookie
        ensureCookie();

        // 2. 构造查询 URL
        String urlStr = QUERY_URL + "?leftTicketDTO.train_date=" + date
                + "&leftTicketDTO.from_station=" + fromCode
                + "&leftTicketDTO.to_station=" + toCode
                + "&purpose_codes=ADULT";

        AppLogger.log("QUERY", "请求 URL: " + urlStr);

        // 3. 发起请求
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        conn.setRequestProperty("Referer", INIT_URL);
        conn.setRequestProperty("Accept", "application/json, text/plain, */*");

        // 带上 Cookie
        String cookie = getCookieString();
        if (cookie != null) {
            conn.setRequestProperty("Cookie", cookie);
        }

        int code = conn.getResponseCode();
        AppLogger.log("QUERY", "HTTP " + code);

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();

        String response = sb.toString();
        AppLogger.log("QUERY", "响应大小: " + response.length() + " bytes");

        return response;
    }

    /**
     * 确保已获取 12306 Cookie
     */
    private static void ensureCookie() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(INIT_URL).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

        // 读取响应（获取 Cookie）
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), "UTF-8"));
        while (reader.readLine() != null) { /* consume */ }
        reader.close();

        // 从响应头提取 Cookie
        List<String> cookies = conn.getHeaderFields().get("Set-Cookie");
        if (cookies != null) {
            for (String c : cookies) {
                List<HttpCookie> parsed = HttpCookie.parse(c);
                for (HttpCookie httpCookie : parsed) {
                    cookieManager.getCookieStore().add(null, httpCookie);
                }
            }
        }

        AppLogger.log("QUERY", "Cookie 获取完成");
    }

    /**
     * 获取当前 Cookie 字符串
     */
    private static String getCookieString() {
        List<HttpCookie> cookies = cookieManager.getCookieStore().getCookies();
        if (cookies.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (HttpCookie c : cookies) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(c.getName()).append("=").append(c.getValue());
        }
        return sb.toString();
    }
}
