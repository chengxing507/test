package com.train12306;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 多段换乘路线规划器 — 纯本地计算
 * <p>
 * 所有路线查询基于 queryG（直达 API），
 * 中转站在本地通过「枢纽站列表」和用户途经站推算，不依赖 12306 中转 API。
 */
public class MultiLegPlanner {

    public enum Mode { AUTO, WAYPOINT, FLEXIBLE }

    // ======================== 数据结构 ========================

    /** 一段车程 */
    public static class Segment {
        public String trainCode;
        public String fromStation;     // 中文站名
        public String toStation;
        public String fromTime;        // HH:mm
        public String toTime;
        public String duration;
        public String fromCode;
        public String toCode;

        public Segment() {}

        public Segment(String tc, String f, String t, String ft, String tt, String d) {
            trainCode = tc; fromStation = f; toStation = t;
            fromTime = ft; toTime = tt; duration = d;
        }
    }

    /** 一条完整路线 */
    public static class Path {
        public List<Segment> segments = new ArrayList<>();
        public int transfers;
        public int totalMinutes;
        public String totalDuration;
        public String firstDeparture;
        public String lastArrival;
        public int totalWaitMinutes;

        public String getSummary() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < segments.size(); i++) {
                Segment s = segments.get(i);
                if (i > 0) sb.append(" ⤵ ");
                sb.append(s.trainCode).append(" ")
                  .append(s.fromStation).append("(").append(s.fromTime).append(")")
                  .append("→").append(s.toStation).append("(").append(s.toTime).append(")");
            }
            sb.append("  共").append(totalDuration);
            return sb.toString();
        }

        public String getDetailed() {
            StringBuilder sb = new StringBuilder();
            sb.append("路线: ");
            for (int i = 0; i < segments.size(); i++) {
                Segment s = segments.get(i);
                if (i > 0) sb.append(" 换乘 ");
                sb.append(s.trainCode).append(" ").append(s.fromStation)
                  .append(" ").append(s.fromTime).append(" → ")
                  .append(s.toStation).append(" ").append(s.toTime)
                  .append(" (").append(s.duration).append(")");
            }
            sb.append("\n总耗时: ").append(totalDuration)
              .append(" | 换乘: ").append(transfers).append("次")
              .append(" | 等待: ").append(totalWaitMinutes).append("分");
            return sb.toString();
        }
    }

    /** 单列车次（从 queryG 解析） */
    static class TrainInfo {
        String trainCode, startTime, arriveTime, duration;
        String fromName, toName, fromCode, toCode;
    }

    // ======================== 枢纽站列表 ========================

    /** 全国主要铁路枢纽站（代码 → 中文名），使用 LinkedHashMap 保证插入顺序 */
    private static final Map<String, String> HUBS = new LinkedHashMap<>();
    static {
        HUBS.put("BJP", "北京"); HUBS.put("VNP", "北京南"); HUBS.put("BJA", "北京西");
        HUBS.put("VOH", "上海虹桥"); HUBS.put("SHH", "上海"); HUBS.put("SNH", "上海南");
        HUBS.put("GZQ", "广州南"); HUBS.put("GGT", "广州东");
        HUBS.put("SZH", "深圳"); HUBS.put("IOQ", "深圳北");
        HUBS.put("HGH", "杭州东"); HUBS.put("HZH", "杭州");
        HUBS.put("NKH", "南京南"); HUBS.put("NJH", "南京");
        HUBS.put("WHN", "武汉"); HUBS.put("WCN", "汉口");
        HUBS.put("CQW", "重庆北"); HUBS.put("CQI", "重庆西");
        HUBS.put("CDW", "成都东"); HUBS.put("CNW", "成都");
        HUBS.put("XAY", "西安北"); HUBS.put("XAC", "西安");
        HUBS.put("TJB", "天津"); HUBS.put("TJI", "天津西");
        HUBS.put("CSQ", "长沙南"); HUBS.put("CSN", "长沙");
        HUBS.put("ZZF", "郑州东"); HUBS.put("ZZV", "郑州");
        HUBS.put("FYG", "福州南"); HUBS.put("FZS", "福州");
        HUBS.put("JGX", "济南西"); HUBS.put("JNK", "济南");
        HUBS.put("HFV", "合肥南"); HUBS.put("HFH", "合肥");
        HUBS.put("SJJ", "石家庄"); HUBS.put("SJP", "石家庄北");
        HUBS.put("NCG", "南昌西"); HUBS.put("NCJ", "南昌");
        HUBS.put("KMK", "昆明"); HUBS.put("KMM", "昆明南");
        HUBS.put("GYV", "贵阳北"); HUBS.put("GIW", "贵阳");
        HUBS.put("LZX", "兰州西"); HUBS.put("LZJ", "兰州");
        HUBS.put("NNZ", "南宁东"); HUBS.put("NNN", "南宁");
        HUBS.put("HKK", "海口"); HUBS.put("VUQ", "海口东");
        HUBS.put("HHE", "呼和浩特"); HUBS.put("INC", "银川");
        HUBS.put("WLX", "乌鲁木齐"); HUBS.put("LSO", "拉萨");
        HUBS.put("HKB", "哈尔滨西"); HUBS.put("HBB", "哈尔滨");
        HUBS.put("CCT", "长春"); HUBS.put("CRT", "长春西");
        HUBS.put("SYT", "沈阳北"); HUBS.put("SBT", "沈阳");
        HUBS.put("DUT", "大连"); HUBS.put("DLT", "大连北");
        HUBS.put("QDK", "青岛"); HUBS.put("QDU", "青岛北");
        HUBS.put("XMN", "厦门"); HUBS.put("XKS", "厦门北");
        HUBS.put("SUH", "苏州"); HUBS.put("OHH", "苏州北");
        HUBS.put("WXH", "无锡"); HUBS.put("WXG", "无锡东");
        HUBS.put("CZH", "常州"); HUBS.put("CZJ", "常州北");
        HUBS.put("NZH", "宁波"); HUBS.put("NGH", "宁波东");
        HUBS.put("WZH", "温州南"); HUBS.put("RZH", "温州");
        HUBS.put("HZH", "湖州");
    }

    // ======================== 字段 ========================

    private String queryDate;
    private int maxTransfers;
    private int maxIntervalMinutes;
    private Mode mode;
    private ProgressCallback callback;
    private boolean cancelled;

    // 进度跟踪
    private int progressTotal = 0;
    private int progressCurrent = 0;

    // AI 预筛选后的活跃枢纽站列表（null=使用全部）
    private List<String> activeHubs;

    public interface ProgressCallback {
        void onProgress(String msg);
        void onError(String msg);
        boolean isCancelled();

        /** 带百分比的进度报告 */
        default void onProgressPercent(int current, int total, String message) {
            onProgress(String.format("[%d/%d] %s", current, total, message));
        }
    }

    public MultiLegPlanner(String date, int maxTrans, int maxIntervalHours) {
        this.queryDate = date;
        this.maxTransfers = maxTrans;
        this.maxIntervalMinutes = maxIntervalHours * 60;
        this.mode = Mode.AUTO;
    }

    public void setMode(Mode m) { this.mode = m; }
    public void setCallback(ProgressCallback cb) { this.callback = cb; }
    public void cancel() { this.cancelled = true; }

    private void log(String msg) {
        AppLogger.log("PLANNER", msg);
        if (callback != null) callback.onProgress(msg);
    }
    private void err(String msg) {
        AppLogger.error("PLANNER", msg);
        if (callback != null) callback.onError(msg);
    }
    private boolean isCancelled() {
        if (cancelled) return true;
        if (callback != null && callback.isCancelled()) return true;
        return false;
    }

    // ======================== 对外入口 ========================

    /**
     * Mode B: 有序途经站规划
     */
    public List<Path> planWithWaypoints(List<String> stations) {
        if (stations == null || stations.size() < 2) {
            err("至少需要2个站点");
            return Collections.emptyList();
        }
        log("途经站模式: " + String.join(" → ", stations));

        List<List<TrainInfo>> segmentTrains = new ArrayList<>();
        for (int i = 0; i < stations.size() - 1; i++) {
            if (isCancelled()) return Collections.emptyList();
            String from = stations.get(i).trim();
            String to = stations.get(i + 1).trim();
            log(String.format("查询第%d段: %s → %s", i + 1, from, to));
            List<TrainInfo> trains = queryDirectTrains(from, to);
            if (trains.isEmpty()) {
                err(String.format("❌ 未找到 %s → %s 的列车", from, to));
                return Collections.emptyList();
            }
            segmentTrains.add(trains);
            log(String.format("  ✓ %s → %s : %d 趟", from, to, trains.size()));
        }

        List<Path> results = combinePaths(segmentTrains, stations);
        log(String.format("组合完成: %d 条路径", results.size()));
        return results;
    }

    /**
     * Mode A: 自动换乘规划
     * @param useHintsAsWaypoints true=途经站必须全部按序经过, false=仅用作建议
     */
    public List<Path> planAutoTransfer(String from, String to,
                                       List<String> hints, boolean useHintsAsWaypoints) {
        if (useHintsAsWaypoints && hints != null && !hints.isEmpty()) {
            List<String> stations = new ArrayList<>();
            stations.add(from);
            stations.addAll(hints);
            stations.add(to);
            return planWithWaypoints(stations);
        }

        log(String.format("自动换乘: %s → %s  最多%d次换乘", from, to, maxTransfers));
        List<Path> allPaths = new ArrayList<>();

        // Level 0: 直达
        List<TrainInfo> direct = queryDirectTrains(from, to);
        for (TrainInfo t : direct) {
            Path p = new Path();
            p.segments.add(toSegment(t, from, to));
            calcStats(p);
            allPaths.add(p);
        }
        log(String.format("直达 %d 条", direct.size()));

        // Level 1+: 通过枢纽站中转
        for (int level = 1; level <= maxTransfers; level++) {
            if (isCancelled()) break;
            progressTotal = 0; // 重置计数器，让 findHubTransferPaths 重新初始化
            progressCurrent = 0;
            log(String.format("查找 %d 次换乘...", level));
            List<Path> levelPaths = findHubTransferPaths(from, to, level, new HashSet<>());
            allPaths.addAll(levelPaths);
            log(String.format("  %d 次换乘: %d 条", level, levelPaths.size()));
        }

        Collections.sort(allPaths, (a, b) -> {
            int c = Integer.compare(a.totalMinutes, b.totalMinutes);
            if (c == 0) c = Integer.compare(a.transfers, b.transfers);
            return c;
        });
        log(String.format("共 %d 条路径", allPaths.size()));
        return allPaths;
    }

    /**
     * Mode C: 弹性途经站 — 尝试途经站的各种子集/排列
     */
    public List<Path> planFlexible(String from, String to, List<String> waypoints) {
        if (waypoints == null || waypoints.isEmpty()) {
            return planAutoTransfer(from, to, null, false);
        }

        // 去重
        Set<String> waypointSet = new HashSet<>();
        for (String w : waypoints) {
            String wt = w.trim();
            if (!wt.isEmpty() && !wt.equals(from) && !wt.equals(to))
                waypointSet.add(wt);
        }
        List<String> uniqueWps = new ArrayList<>(waypointSet);

        log(String.format("弹性途经站模式: %s → %s  途经池: %s",
                from, to, String.join(", ", uniqueWps)));

        List<Path> all = new ArrayList<>();
        // 尝试途经站的各个子集（从全部到空）
        int n = uniqueWps.size();
        for (int mask = (1 << n) - 1; mask >= 0; mask--) {
            if (isCancelled()) break;
            List<String> selected = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                if ((mask & (1 << i)) != 0) selected.add(uniqueWps.get(i));
            }
            // 排列子集中所有可能顺序
            List<List<String>> perms = generatePermutations(selected);
            for (List<String> perm : perms) {
                if (isCancelled()) break;
                List<String> stations = new ArrayList<>();
                stations.add(from);
                stations.addAll(perm);
                stations.add(to);
                List<Path> paths = planWithWaypoints(stations);
                all.addAll(paths);
            }
            // 限制总数
            if (all.size() > 300) break;
        }

        Collections.sort(all, (a, b) -> Integer.compare(a.totalMinutes, b.totalMinutes));
        log(String.format("弹性模式共 %d 条路径", all.size()));
        return all;
    }

    // ======================== 核心算法 ========================

    /**
     * 查询两个站点之间的直达车次
     */
    private List<TrainInfo> queryDirectTrains(String fromName, String toName) {
        List<TrainInfo> list = new ArrayList<>();
        if (isCancelled()) return list;
        try {
            String fromCode = StationDataManager.getStationCode(fromName);
            String toCode = StationDataManager.getStationCode(toName);
            if (fromCode == null || toCode == null) {
                err("无法获取站点代码: " + fromName + " / " + toName);
                return list;
            }

            String json = TicketQueryManager.queryTickets(queryDate, fromCode, toCode, "");
            if (json == null || json.isEmpty()) return list;

            // 尝试解析 JSON，兼容 12306 返回的非标准内容
            JsonObject root;
            try {
                root = JsonParser.parseString(json).getAsJsonObject();
            } catch (Exception e) {
                // 12306 有时返回非 JSON 内容（如错误页），静默跳过
                AppLogger.warn("PLANNER", "非JSON响应 [" + fromName + "→" + toName + "]: "
                        + json.substring(0, Math.min(100, json.length())));
                return list;
            }

            if (!root.has("data")) return list;
            JsonObject data = root.getAsJsonObject("data");
            if (!data.has("result")) return list;
            JsonArray results = data.getAsJsonArray("result");

            for (int i = 0; i < results.size(); i++) {
                String line = results.get(i).getAsString();
                String[] parts = line.split("\\|");
                if (parts.length <= 8) continue;

                TrainInfo ti = new TrainInfo();
                ti.trainCode = parts.length > 3 ? parts[3] : "";
                ti.startTime = parts.length > 8 ? parts[8] : "";
                ti.arriveTime = parts.length > 9 ? parts[9] : "";
                ti.duration = parts.length > 10 ? parts[10] : "";
                ti.fromName = fromName;
                ti.toName = toName;
                ti.fromCode = fromCode;
                ti.toCode = toCode;
                if (!ti.trainCode.isEmpty()) list.add(ti);
            }
        } catch (Exception e) {
            // 仅记录日志，不抛到 UI 层
            AppLogger.warn("PLANNER", "查询直达失败 [" + fromName + "→" + toName + "]: " + e.getMessage());
        }
        return list;
    }

    /**
     * 通过枢纽站查找中转路径（带进度报告）
     */
    private List<Path> findHubTransferPaths(String from, String to, int level, Set<String> visited) {
        return findHubTransferPaths(from, to, level, visited, true);
    }

    /** 内部版本，countProgress=false 时跳过进度计数（用于递归调用） */
    private List<Path> findHubTransferPaths(String from, String to, int level, Set<String> visited, boolean countProgress) {
        List<Path> result = new ArrayList<>();
        if (isCancelled() || level <= 0) return result;

        // 确定要查的枢纽站列表
        List<Map.Entry<String, String>> hubList;
        if (activeHubs != null) {
            hubList = new ArrayList<>();
            for (Map.Entry<String, String> e : HUBS.entrySet()) {
                if (activeHubs.contains(e.getValue())) {
                    hubList.add(e);
                }
            }
        } else {
            hubList = new ArrayList<>(HUBS.entrySet());
        }
        // 按站名排序，保证每次迭代顺序完全一致
        Collections.sort(hubList, (a, b) -> a.getValue().compareTo(b.getValue()));

        // 首次进入时初始化计数器（只在顶层计数）
        if (countProgress && progressTotal == 0) {
            progressTotal = hubList.size();
            progressCurrent = 0;
        }

        for (Map.Entry<String, String> hub : hubList) {
            if (isCancelled()) break;
            String hubCode = hub.getKey();
            String hubName = hub.getValue();

            if (countProgress) {
                progressCurrent++;
                int pct = progressTotal > 0 ? (int)((float)progressCurrent / progressTotal * 100) : 0;
                String progMsg = String.format("🔍 枢纽站 %s (%d/%d) %d%% | %s → %s → %s",
                        hubName, progressCurrent, progressTotal, pct, from, hubName, to);
                log(progMsg);
                if (callback != null) callback.onProgressPercent(progressCurrent, progressTotal,
                        String.format("正在查询 %s → %s 的列车...", from, hubName));
            } else {
                // 递归调用只显示文本，不计数
                log(String.format("  ↪ 递归查询: %s → %s → %s", from, hubName, to));
            }

            // 跳过已经过站
            if (visited.contains(hubName) || visited.contains(hubCode)) continue;
            if (hubName.equals(from) || hubName.equals(to)) continue;

            Set<String> newVisited = new HashSet<>(visited);
            newVisited.add(hubName);

            List<TrainInfo> firstLegs = queryDirectTrains(from, hubName);
            if (firstLegs.isEmpty()) {
                log(String.format("  ⤷ %s→%s 无直达车，跳过", from, hubName));
                continue;
            }

            if (level == 1) {
                log(String.format("  ⤷ 回程 %s→%s...", hubName, to));
                List<TrainInfo> secondLegs = queryDirectTrains(hubName, to);
                if (secondLegs.isEmpty()) {
                    log(String.format("  ⤷ %s→%s 无直达车，跳过", hubName, to));
                    continue;
                }

                for (TrainInfo first : firstLegs) {
                    for (TrainInfo second : secondLegs) {
                        if (isValidTransfer(first.arriveTime, second.startTime, true)) {
                            Path p = new Path();
                            p.segments.add(toSegment(first, from, hubName));
                            p.segments.add(toSegment(second, hubName, to));
                            calcStats(p);
                            result.add(p);
                            if (result.size() > 100) break;
                        }
                    }
                    if (result.size() > 100) break;
                }
            } else {
                // 深层递归（不计数进度）
                List<Path> subPaths = findHubTransferPaths(hubName, to, level - 1, newVisited, false);
                for (Path sub : subPaths) {
                    if (isCancelled()) break;
                    for (TrainInfo first : firstLegs) {
                        if (isValidTransfer(first.arriveTime, sub.segments.get(0).fromTime, true)) {
                            Path full = new Path();
                            full.segments.add(toSegment(first, from, hubName));
                            full.segments.addAll(sub.segments);
                            calcStats(full);
                            result.add(full);
                            if (result.size() > 100) break;
                        }
                    }
                    if (result.size() > 100) break;
                }
            }
        }
        return result;
    }

    /** 合并多段车次 */
    private List<Path> combinePaths(List<List<TrainInfo>> segmentTrains, List<String> stations) {
        List<Path> result = new ArrayList<>();
        combineRecurse(segmentTrains, 0, new Path(), stations, result);
        return result;
    }

    private void combineRecurse(List<List<TrainInfo>> segs, int idx,
                                 Path cur, List<String> stations, List<Path> result) {
        if (isCancelled()) return;
        if (idx >= segs.size()) {
            Path p = new Path();
            p.segments.addAll(cur.segments);
            calcStats(p);
            result.add(p);
            return;
        }
        for (TrainInfo t : segs.get(idx)) {
            if (isCancelled()) return;
            if (idx > 0) {
                Segment prev = cur.segments.get(cur.segments.size() - 1);
                if (!isValidTransfer(prev.toTime, t.startTime, false)) continue;
            }
            cur.segments.add(toSegment(t, stations.get(idx), stations.get(idx + 1)));
            combineRecurse(segs, idx + 1, cur, stations, result);
            cur.segments.remove(cur.segments.size() - 1);
            if (result.size() > 500) return;
        }
    }

    // ======================== 工具方法 ========================

    private Segment toSegment(TrainInfo t, String fromName, String toName) {
        return new Segment(t.trainCode, fromName, toName,
                t.startTime, t.arriveTime, t.duration);
    }

    /** 换乘时间校验 */
    private boolean isValidTransfer(String arrive, String depart, boolean allowCrossDay) {
        try {
            int a = timeToMin(arrive);
            int d = timeToMin(depart);
            if (d < a) {
                if (allowCrossDay) d += 1440;
                else return false;
            }
            int wait = d - a;
            return wait >= 15 && wait <= maxIntervalMinutes;
        } catch (Exception e) { return true; }
    }

    /** 计算路径统计 */
    private void calcStats(Path p) {
        if (p.segments.isEmpty()) return;
        p.transfers = p.segments.size() - 1;
        p.firstDeparture = p.segments.get(0).fromTime;
        p.lastArrival = p.segments.get(p.segments.size() - 1).toTime;

        int totalMin = 0, totalWait = 0;
        for (int i = 0; i < p.segments.size(); i++) {
            Segment s = p.segments.get(i);
            totalMin += parseDurationMin(s.duration);
            if (i > 0) {
                Segment prev = p.segments.get(i - 1);
                int w = calcWait(prev.toTime, s.fromTime);
                if (w > 0) totalWait += w;
            }
        }
        p.totalMinutes = totalMin;
        p.totalWaitMinutes = totalWait;

        int h = totalMin / 60, m = totalMin % 60;
        p.totalDuration = h > 0 ? h + "h" + m + "m" : m + "m";
    }

    private int parseDurationMin(String d) {
        if (d == null || d.isEmpty()) return 0;
        try {
            if (d.contains(":")) {
                String[] p = d.split(":");
                return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
            }
            return Integer.parseInt(d.replaceAll("[^0-9]", ""));
        } catch (Exception e) { return 0; }
    }

    private int calcWait(String arrive, String depart) {
        try {
            int a = timeToMin(arrive);
            int d = timeToMin(depart);
            if (d < a) d += 1440;
            return d - a;
        } catch (Exception e) { return 0; }
    }

    private int timeToMin(String t) {
        String[] p = t.split(":");
        return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
    }

    /** 生成列表的所有排列 */
    private List<List<String>> generatePermutations(List<String> items) {
        List<List<String>> result = new ArrayList<>();
        if (items.isEmpty()) { result.add(new ArrayList<>()); return result; }
        permute(items, 0, result);
        return result;
    }

    private void permute(List<String> items, int start, List<List<String>> result) {
        if (start >= items.size()) {
            result.add(new ArrayList<>(items));
            return;
        }
        for (int i = start; i < items.size(); i++) {
            Collections.swap(items, start, i);
            permute(items, start + 1, result);
            Collections.swap(items, start, i);
            if (result.size() > 120) return;
        }
    }

    // ======================== AI 预筛选枢纽站 ========================

    /**
     * 用 AI 预筛掉不合理的枢纽站，减少查询量
     * @return true=筛选成功, false=失败（继续用全部枢纽站）
     */
    public boolean filterHubsByAI(String from, String to,
                                   String baseUrl, String apiKey, String modelName) {
        try {
            // 构建枢纽站列表文本
            StringBuilder sb = new StringBuilder();
            sb.append("你是铁路出行专家。从").append(from).append("到").append(to)
              .append("，以下是所有可能的中转枢纽站列表。\n")
              .append("请选出地理位置上合理、适合作为中转站的站点。排除明显绕路的站点。\n")
              .append("只返回选中的站点名称，用中文逗号或英文逗号分隔，不要任何额外文字。\n\n")
              .append("可选站点:\n");

            List<String> hubNames = new ArrayList<>(HUBS.values());
            // 去重并按区域排列
            Collections.sort(hubNames);
            for (String h : hubNames) {
                if (!h.equals(from) && !h.equals(to)) {
                    sb.append(h).append("、");
                }
            }
            // 去掉最后一个顿号
            String prompt = sb.toString();
            if (prompt.endsWith("、")) {
                prompt = prompt.substring(0, prompt.length() - 1);
            }

            AppLogger.log("PLANNER", "AI 预筛选枢纽站，发送到 AI...");
            AIAnalysisClient aiClient = new AIAnalysisClient(baseUrl, apiKey, modelName);
            String result = aiClient.analyzeRoute("", prompt);
            // 记录 AI 原始回复到日志
            AppLogger.log("AI_RAW", "AI 预筛选原始回复: " + result);

            // 解析返回的站点名 - 增强解析
            Set<String> selected = new HashSet<>();
            // 1. 先尝试提取所有匹配的中文站名（2-4个汉字）
            java.util.regex.Pattern stationPattern = java.util.regex.Pattern.compile(
                "[" + String.join("", new ArrayList<>(HUBS.values())) + "]{2,}");
            // 2. 用分隔符切割
            String cleanResult = result.replaceAll("[\\*#\\[\\]()【】①②③④⑤⑥⑦⑧⑨⑩\\d+\\.、]", " ");
            String[] parts = cleanResult.split("[，,、\\s]+");
            for (String p : parts) {
                p = p.trim();
                if (p.isEmpty()) continue;
                // 精确匹配
                if (HUBS.containsValue(p)) {
                    selected.add(p);
                } else {
                    // 模糊匹配：检查是否包含某个枢纽站名
                    for (String hubName : HUBS.values()) {
                        if (p.contains(hubName) || hubName.contains(p)) {
                            selected.add(hubName);
                            break;
                        }
                    }
                }
            }

            if (selected.isEmpty()) {
                AppLogger.warn("PLANNER", "AI 未选中任何枢纽站，使用全部");
                return false;
            }

            activeHubs = new ArrayList<>(selected);
            // 按站名排序保证每次迭代顺序一致
            Collections.sort(activeHubs);
            AppLogger.log("PLANNER", "AI 筛选后保留 " + activeHubs.size()
                    + " 个枢纽站: " + String.join(", ", activeHubs));
            return true;

        } catch (Exception e) {
            AppLogger.warn("PLANNER", "AI 预筛选失败: " + e.getMessage());
            return false;
        }
    }

    /** 获取当前活跃的枢纽站列表副本 */
    public List<String> getActiveHubs() {
        if (activeHubs == null) {
            List<String> all = new ArrayList<>(HUBS.values());
            Collections.sort(all);
            return all;
        }
        return new ArrayList<>(activeHubs);
    }

    /** 预设 prompt 列表 — 各预设间应有明显区别 */
    public static String[] getBuiltinPrompts() {
        return new String[]{
            "严格筛选：排除换乘等待<30分钟（赶不上车）的方案，排除总耗时超过直达车1.5倍的绕路方案，按总时长升序推荐最优的2条方案",
            "舒适优先：排除夜间换乘（23:00-06:00期间）的方案，优先推荐换乘次数≤2次、总时长最短且均为白天乘车的方案，高铁/动车组合优先",
            "性价比优先：排除换乘等待>3小时（浪费时间）的方案，优先推荐换乘次数少且总时长短的方案，如多方案总时长接近则选换乘次数更少的",
            "全面分析：不要直接排除，对每条路线逐一评价优缺点（换乘次数、等待时间、是否跨天、是否绕路），按综合评分从高到低排序"
        };
    }

    public static String getDefaultAIPrompt() {
        return getBuiltinPrompts()[0];
    }

    /** AI 分析前缀说明 */
    public static String getAIPromptPrefix() {
        return "你是一个出行规划专家。以下是若干条火车换乘方案，请分析并排除明显不合理的路线" +
               "（如换乘时间过短赶不上、绕路太多、总耗时太长等），并推荐最合理的1-3条方案。";
    }
}