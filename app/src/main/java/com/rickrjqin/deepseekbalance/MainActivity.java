package com.rickrjqin.deepseekbalance;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity implements BalanceView.Actions {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private BalanceView view;
    private boolean requestInFlight;
    private final Runnable scheduledRefresh = new Runnable() {
        @Override
        public void run() {
            refresh();
            scheduleNextRefresh();
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(10, 42, 58));
        window.setNavigationBarColor(Color.rgb(8, 25, 35));
        view = new BalanceView(this, this);
        setContentView(view);
        if (!SecureStore.get(this).isEmpty()) {
            view.postDelayed(this::refresh, 420);
        }
    }

    @Override
    public void refresh() {
        if (requestInFlight) return;
        String key = SecureStore.get(this);
        if (key.isEmpty()) {
            showKeyDialog();
            return;
        }
        requestInFlight = true;
        view.setLoading(true);
        executor.execute(() -> requestBalance(key));
    }

    @Override
    public void settings() {
        showKeyDialog();
    }

    private void showKeyDialog() {
        int pad = dp(22);
        int cyan = Color.rgb(72, 195, 240);
        int white = Color.rgb(240, 248, 252);
        int muted = Color.rgb(181, 205, 216);

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(pad, dp(25), pad, dp(20));
        content.setBackground(panelBackground());

        TextView icon = new TextView(this);
        icon.setText("✦");
        icon.setTextColor(Color.WHITE);
        icon.setTextSize(20);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(gradientBackground(
                Color.rgb(72, 211, 248), Color.rgb(43, 111, 238), dp(15)));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(52), dp(52));
        iconParams.gravity = Gravity.CENTER_HORIZONTAL;
        content.addView(icon, iconParams);

        TextView title = new TextView(this);
        title.setText("连接 DeepSeek");
        title.setTextColor(white);
        title.setTextSize(25);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setPadding(0, dp(13), 0, 0);
        content.addView(title);

        TextView message = new TextView(this);
        message.setText("API Key 仅通过 Android Keystore 加密保存在设备本地。");
        message.setTextColor(muted);
        message.setTextSize(13);
        message.setGravity(Gravity.CENTER);
        message.setLineSpacing(0, 1.2f);
        message.setPadding(0, dp(12), 0, dp(15));
        content.addView(message);

        EditText input = new EditText(this);
        input.setHint("sk-...");
        input.setHintTextColor(Color.rgb(112, 145, 157));
        input.setTextColor(white);
        input.setTextSize(14);
        input.setText(SecureStore.get(this));
        input.setSelectAllOnFocus(true);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setPadding(dp(14), 0, dp(14), 0);
        input.setBackground(roundBackground(Color.rgb(8, 37, 48),
                Color.rgb(54, 91, 104), dp(13), 1));
        content.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        LinearLayout intervalPanel = new LinearLayout(this);
        intervalPanel.setOrientation(LinearLayout.VERTICAL);
        intervalPanel.setPadding(dp(14), dp(11), dp(14), dp(10));
        intervalPanel.setBackground(roundBackground(Color.rgb(20, 55, 67),
                Color.rgb(55, 92, 104), dp(15), 1));
        LinearLayout.LayoutParams intervalParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        intervalParams.topMargin = dp(16);
        content.addView(intervalPanel, intervalParams);

        TextView intervalLabel = new TextView(this);
        intervalLabel.setText("自动刷新");
        intervalLabel.setTextColor(muted);
        intervalLabel.setTextSize(12);
        intervalLabel.setPadding(dp(4), 0, 0, dp(4));
        intervalPanel.addView(intervalLabel);

        RadioGroup intervals = new RadioGroup(this);
        intervals.setOrientation(RadioGroup.VERTICAL);
        long currentInterval = getPreferences(MODE_PRIVATE).getLong("refresh_interval", 0);
        long[] intervalValues = {0, 30 * 60_000L, 60 * 60_000L, 2 * 60 * 60_000L};
        String[] intervalLabels = {"关闭", "每 30 分钟", "每 1 小时", "每 2 小时"};
        for (int i = 0; i < intervalLabels.length; i++) {
            RadioButton option = new RadioButton(this);
            option.setId(1000 + i);
            option.setText(intervalLabels[i]);
            option.setTextColor(white);
            option.setTextSize(14);
            option.setPadding(0, dp(3), 0, dp(3));
            option.setButtonTintList(new ColorStateList(
                    new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                    new int[]{cyan, Color.rgb(129, 141, 146)}));
            option.setTag(intervalValues[i]);
            intervals.addView(option);
            if (intervalValues[i] == currentInterval) option.setChecked(true);
        }
        intervalPanel.addView(intervals);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams buttonsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        buttonsParams.topMargin = dp(16);
        content.addView(buttons, buttonsParams);

        Button cancel = dialogButton("取消", Color.rgb(34, 70, 83), white);
        Button save = dialogButton("保存并刷新", cyan, Color.WHITE);
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        buttonParams.rightMargin = dp(6);
        buttons.addView(cancel, buttonParams);
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        saveParams.leftMargin = dp(6);
        buttons.addView(save, saveParams);

        cancel.setOnClickListener(v -> dialog.dismiss());
        save.setOnClickListener(v -> {
            String key = input.getText().toString().trim();
            if (key.isEmpty()) {
                input.setError("请输入 API Key");
                return;
            }
            try {
                SecureStore.put(this, key);
                RadioButton selected = intervals.findViewById(
                        intervals.getCheckedRadioButtonId());
                long interval = selected == null ? 0 : (long) selected.getTag();
                getPreferences(MODE_PRIVATE).edit()
                        .putLong("refresh_interval", interval).apply();
                dialog.dismiss();
                scheduleNextRefresh();
                refresh();
            } catch (Exception e) {
                Toast.makeText(this, "无法安全保存 Key", Toast.LENGTH_LONG).show();
            }
        });

        dialog.setContentView(content);
        Window dialogWindow = dialog.getWindow();
        if (dialogWindow != null) {
            dialogWindow.setBackgroundDrawableResource(android.R.color.transparent);
            dialogWindow.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params = dialogWindow.getAttributes();
            params.width = Math.min(getResources().getDisplayMetrics().widthPixels - dp(36), dp(390));
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            params.dimAmount = .72f;
            dialogWindow.setAttributes(params);
        }
        dialog.show();
        if (dialogWindow != null) {
            dialogWindow.setLayout(
                    Math.min(getResources().getDisplayMetrics().widthPixels - dp(36), dp(390)),
                    WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private Button dialogButton(String label, int background, int foreground) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(foreground);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setPadding(0, 0, 0, 0);
        button.setBackground(roundBackground(background, background, dp(13), 0));
        return button;
    }

    private GradientDrawable panelBackground() {
        GradientDrawable drawable = gradientBackground(
                Color.rgb(25, 59, 70), Color.rgb(15, 43, 54), dp(28));
        drawable.setStroke(dp(1), Color.rgb(110, 151, 163));
        return drawable;
    }

    private GradientDrawable gradientBackground(int start, int end, int radius) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{start, end});
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private GradientDrawable roundBackground(int fill, int stroke, int radius, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) drawable.setStroke(dp(strokeWidth), stroke);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void requestBalance(String key) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(
                    "https://api.deepseek.com/user/balance").openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(12000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + key);
            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String body = read(stream);
            if (code != 200) throw new Exception(errorMessage(code, body));

            JSONObject root = new JSONObject(body);
            JSONArray infos = root.getJSONArray("balance_infos");
            JSONObject selected = infos.getJSONObject(0);
            for (int i = 0; i < infos.length(); i++) {
                if ("CNY".equals(infos.getJSONObject(i).optString("currency"))) {
                    selected = infos.getJSONObject(i);
                    break;
                }
            }
            BalanceView.Balance balance = new BalanceView.Balance(
                    root.optBoolean("is_available"),
                    selected.optString("currency", "CNY"),
                    selected.optDouble("total_balance", 0),
                    selected.optDouble("topped_up_balance", 0),
                    selected.optDouble("granted_balance", 0));
            runOnUiThread(() -> {
                view.setBalance(balance);
                view.setLoading(false);
                requestInFlight = false;
            });
        } catch (Exception e) {
            String message = e.getMessage() == null ? "网络连接失败" : e.getMessage();
            runOnUiThread(() -> {
                view.setLoading(false);
                view.setError(message);
                requestInFlight = false;
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            });
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void scheduleNextRefresh() {
        refreshHandler.removeCallbacks(scheduledRefresh);
        long interval = getPreferences(MODE_PRIVATE).getLong("refresh_interval", 0);
        if (interval > 0 && !SecureStore.get(this).isEmpty()) {
            refreshHandler.postDelayed(scheduledRefresh, interval);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        scheduleNextRefresh();
    }

    @Override
    protected void onPause() {
        refreshHandler.removeCallbacks(scheduledRefresh);
        super.onPause();
    }

    private static String read(InputStream stream) throws Exception {
        if (stream == null) return "";
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) result.append(line);
        return result.toString();
    }

    private static String errorMessage(int code, String body) {
        try {
            JSONObject root = new JSONObject(body);
            JSONObject error = root.optJSONObject("error");
            if (error != null) return error.optString("message", "请求失败");
        } catch (Exception ignored) {}
        return String.format(Locale.US, "请求失败（HTTP %d）", code);
    }

    @Override
    protected void onDestroy() {
        refreshHandler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
        super.onDestroy();
    }
}
