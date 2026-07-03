package com.train12306;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 12306 站点数据管理器
 * <p>
 * 从 12306 官网下载 station_name.js，解析为 站名→代码 的映射表。
 * 支持缓存到内存，避免频繁下载。
 */
public class StationDataManager {

    private static final String STATION_URL =
            "https://kyfw.12306.cn/otn/resources/js/framework/station_name.js?station_version=1.9369";

    /** 站名 → 站点代码 */
    private static Map<String, String> nameToCode = null;
    private static long lastFetchTime = 0;
    private static final long CACHE_TTL = 24 * 60 * 60 * 1000L; // 24 小时

    /**
     * 获取站点代码
     *
     * @param stationName 中文站名，如 "北京南"、"常熟"
     * @return 站点代码，如 "VNP"、"CAU"；未找到返回 null
     */
    public static synchronized String getStationCode(String stationName) {
        if (stationName == null || stationName.trim().isEmpty()) return null;
        stationName = stationName.trim();

        // 如果缓存过期或为空，重新加载
        if (nameToCode == null || System.currentTimeMillis() - lastFetchTime > CACHE_TTL) {
            try {
                fetchStationData();
            } catch (Exception e) {
                AppLogger.error("STATION", "加载站点数据失败: " + e.getMessage());
                return null;
            }
        }

        // 精确查找
        String code = nameToCode.get(stationName);
        if (code != null) return code;

        // 去掉"站"再查（用户可能输入"常熟站"）
        if (stationName.endsWith("站")) {
            code = nameToCode.get(stationName.substring(0, stationName.length() - 1));
        }
        return code;
    }

    /**
     * 从 12306 下载并解析站点数据
     * 格式：var station_names ='@bjb|北京北|VAP|...';
     * 每个字段：@简称|站名|代码|拼音|缩写|索引|城市码|城市
     */
    private static void fetchStationData() throws Exception {
        AppLogger.log("STATION", "正在下载站点数据...");

        HttpURLConnection conn = (HttpURLConnection)
                new URL(STATION_URL).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();

        String data = sb.toString();
        AppLogger.log("STATION", "站点数据大小: " + data.length() + " bytes");

        // 解析：提取 @xxx|站名|代码
        nameToCode = new HashMap<>();
        Pattern pattern = Pattern.compile("@([^|]+)\\|([^|]+)\\|([^|]+)");
        Matcher matcher = pattern.matcher(data);

        int count = 0;
        while (matcher.find()) {
            String stationName = matcher.group(2);  // 中文站名
            String stationCode = matcher.group(3);  // 站点代码
            nameToCode.put(stationName, stationCode);
            count++;
        }

        lastFetchTime = System.currentTimeMillis();
        AppLogger.log("STATION", "站点解析完成: " + count + " 个车站");
    }

    /**
     * 清空缓存，下次查询会重新下载
     */
    public static synchronized void clearCache() {
        nameToCode = null;
        lastFetchTime = 0;
    }

    /**
     * 检查数据是否已加载
     */
    public static synchronized boolean isLoaded() {
        return nameToCode != null && !nameToCode.isEmpty();
    }
}
