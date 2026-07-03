package com.train12306;

import android.view.View;

/**
 * 按钮防抖守卫 — 防止用户快速重复点击
 * <p>
 * 特性：
 * - 1.5 秒冷却时间
 * - 点击时自动禁用按钮（视觉反馈）
 * - 冷却结束后自动恢复
 */
public class ButtonGuard {

    private static long lastClickTime = 0;

    /**
     * 为按钮添加防抖保护
     *
     * @param v      按钮控件
     * @param action 点击后执行的操作
     */
    public static void guard(View v, final Runnable action) {
        if (v == null || action == null) return;

        v.setOnClickListener(new View.OnClickListener() {
            private boolean busy = false;

            @Override
            public void onClick(View v) {
                long now = System.currentTimeMillis();
                if (busy || now - lastClickTime < 1500) {
                    AppLogger.debug("GUARD", "点击被拦截 (防抖)");
                    return;
                }
                lastClickTime = now;
                busy = true;
                v.setEnabled(false);

                try {
                    action.run();
                } catch (Exception e) {
                    AppLogger.error("GUARD", "按钮操作异常: " + e.getMessage());
                }

                v.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        busy = false;
                        v.setEnabled(true);
                    }
                }, 1500);
            }
        });
    }
}