package com.train12306;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 车次列表页面
 * <p>
 * 特性：
 * - 三态 UI：加载中 / 空数据 / 正常列表
 * - 点击车次进入路线详情
 * - AI 对比分析入口
 */
public class TicketListActivity extends Activity {

    private ListView listView;
    private View tvEmpty;   // 布局中是 LinearLayout (含 🚫 + TextView)
    private View tvLoading; // 布局中是一个 LinearLayout
    private ProgressBar progressBar;

    private final List<TicketItem> ticketList = new ArrayList<>();
    private String queryDate, fromStation, toStation, fromCode, toCode;

    public static class TicketItem {
        String trainCode;
        String trainNo;   // 12306 内部车次编号，用于路线查询
        String fromStation;
        String toStation;
        String startTime;
        String arriveTime;
        String duration;
    }

    

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ticket_list);

        listView = findViewById(R.id.list_tickets);
        tvEmpty = findViewById(R.id.tv_empty);
        tvLoading = findViewById(R.id.tv_loading);
        progressBar = findViewById(R.id.progress_bar);

        queryDate = getIntent().getStringExtra("query_date");
        fromStation = getIntent().getStringExtra("from_station");
        toStation = getIntent().getStringExtra("to_station");
        fromCode = getIntent().getStringExtra("from_code");
        toCode = getIntent().getStringExtra("to_code");
        String ticketData = getIntent().getStringExtra("ticket_data");

        // 先显示加载状态
        showLoading();

        // 解析数据（在线程中）
        new Thread(() -> {
            parseTickets(ticketData);
            runOnUiThread(this::updateUI);
        }).start();

        Button btnCompare = findViewById(R.id.btn_compare);
        Button btnBack = findViewById(R.id.btn_back);

        btnCompare.setOnClickListener(v -> openAIAnalysis());
        btnBack.setOnClickListener(v -> finish());
    }

    /**
     * 显示加载中状态
     */
    private void showLoading() {
        tvLoading.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);
        listView.setVisibility(View.GONE);
    }

    /**
     * 更新 UI（三态切换）
     */
    private void updateUI() {
        if (ticketList.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.GONE);
            tvLoading.setVisibility(View.GONE);
            listView.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            progressBar.setVisibility(View.GONE);
            tvLoading.setVisibility(View.GONE);
            listView.setVisibility(View.VISIBLE);
            listView.setAdapter(new TicketAdapter());
        }
    }

    /**
         * 解析车次文本
         * 格式: "车次 trainNo 出发站 到达站 出发时间 到达时间 历时"
         */
        private void parseTickets(String data) {
            if (data == null || data.isEmpty()) {
                AppLogger.warn("TICKET", "车次数据为空");
                return;
            }

            String[] lines = data.split("\\\\n");
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("查询失败") || line.startsWith("解析失败")) {
                    continue;
                }
                String[] parts = line.split("\\\\s+");
                if (parts.length >= 6) {
                    TicketItem item = new TicketItem();
                    item.trainCode = parts[0];
                    item.trainNo = parts[1];
                    item.fromStation = parts.length > 2 ? parts[2] : fromStation;
                    item.toStation = parts.length > 3 ? parts[3] : toStation;
                    item.startTime = parts.length > 4 ? parts[4] : "";
                    item.arriveTime = parts.length > 5 ? parts[5] : "";
                    item.duration = parts.length > 6 ? parts[6] : "";
                    ticketList.add(item);
                }
            }
            AppLogger.log("TICKET", "解析到 " + ticketList.size() + " 个车次");
        }

    // ======================== 列表适配器 ========================

    private class TicketAdapter extends BaseAdapter {
        @Override
        public int getCount() { return ticketList.size(); }

        @Override
        public Object getItem(int i) { return ticketList.get(i); }

        @Override
        public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_ticket, parent, false);
                holder = new ViewHolder();
                holder.tvTrainCode = convertView.findViewById(R.id.tv_train_code);
                holder.tvFromStation = convertView.findViewById(R.id.tv_from_station);
                holder.tvToStation = convertView.findViewById(R.id.tv_to_station);
                holder.tvStartTime = convertView.findViewById(R.id.tv_start_time);
                holder.tvArriveTime = convertView.findViewById(R.id.tv_arrive_time);
                holder.tvDuration = convertView.findViewById(R.id.tv_duration);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            TicketItem item = ticketList.get(i);
            holder.tvTrainCode.setText(item.trainCode);
            holder.tvFromStation.setText(item.fromStation);
            holder.tvToStation.setText(item.toStation);
            holder.tvStartTime.setText(item.startTime);
            holder.tvArriveTime.setText(item.arriveTime);
            holder.tvDuration.setText(item.duration);

            final int pos = i;
            convertView.setOnClickListener(v -> {
                TicketItem t = ticketList.get(pos);
                Intent intent = new Intent(TicketListActivity.this, RouteDetailActivity.class);
                intent.putExtra("train_code", t.trainCode);
                intent.putExtra("train_no", t.trainNo);
                intent.putExtra("query_date", queryDate);
                intent.putExtra("from_station", t.fromStation);
                intent.putExtra("to_station", t.toStation);
                intent.putExtra("from_code", fromCode);
                intent.putExtra("to_code", toCode);
                intent.putExtra("start_time", t.startTime);
                intent.putExtra("arrive_time", t.arriveTime);
                intent.putExtra("duration", t.duration);
                startActivity(intent);
            });

            return convertView;
        }
    }

    private static class ViewHolder {
        TextView tvTrainCode, tvFromStation, tvToStation, tvStartTime, tvArriveTime, tvDuration;
    }

    // ======================== AI 对比分析 ========================

    private void openAIAnalysis() {
        if (ticketList.isEmpty()) {
            Toast.makeText(this, "没有车次数据可供分析", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (TicketItem t : ticketList) {
            sb.append(t.trainCode).append(" ")
              .append(t.startTime).append("-").append(t.arriveTime).append(" ")
              .append(t.duration).append("\n");
        }

        Intent intent = new Intent(this, AIAnalysisActivity.class);
        intent.putExtra("ticket_summary", sb.toString());
        startActivity(intent);
    }
}