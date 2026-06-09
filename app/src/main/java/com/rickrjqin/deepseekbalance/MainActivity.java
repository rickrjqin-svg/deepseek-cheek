package com.rickrjqin.deepseekbalance;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.ViewGroup;
import android.view.Window;
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
        float density = getResources().getDisplayMetrics().density;
        int pad = Math.round(24 * density);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(pad, 0, pad, 0);

        EditText input = new EditText(this);
        input.setHint("sk-...");
        input.setText(SecureStore.get(this));
        input.setSelectAllOnFocus(true);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setPadding(0, pad / 2, 0, pad / 2);
        content.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView intervalLabel = new TextView(this);
        intervalLabel.setText("自动刷新");
        intervalLabel.setTextSize(14);
        intervalLabel.setPadding(0, pad / 2, 0, Math.round(6 * density));
        content.addView(intervalLabel);

        RadioGroup intervals = new RadioGroup(this);
        long currentInterval = getPreferences(MODE_PRIVATE).getLong("refresh_interval", 0);
        long[] intervalValues = {0, 30 * 60_000L, 60 * 60_000L, 2 * 60 * 60_000L};
        String[] intervalLabels = {"关闭", "每 30 分钟", "每 1 小时", "每 2 小时"};
        for (int i = 0; i < intervalLabels.length; i++) {
            RadioButton option = new RadioButton(this);
            option.setId(1000 + i);
            option.setText(intervalLabels[i]);
            option.setTag(intervalValues[i]);
            intervals.addView(option);
            if (intervalValues[i] == currentInterval) option.setChecked(true);
        }
        content.addView(intervals);

        new AlertDialog.Builder(this)
                .setTitle("连接 DeepSeek")
                .setMessage("API Key 仅在本机加密保存。自动刷新仅在应用处于前台时运行。")
                .setView(content)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存并刷新", (dialog, which) -> {
                    String key = input.getText().toString().trim();
                    if (key.isEmpty()) return;
                    try {
                        SecureStore.put(this, key);
                        RadioButton selected = intervals.findViewById(
                                intervals.getCheckedRadioButtonId());
                        long interval = selected == null ? 0 : (long) selected.getTag();
                        getPreferences(MODE_PRIVATE).edit()
                                .putLong("refresh_interval", interval).apply();
                        scheduleNextRefresh();
                        refresh();
                    } catch (Exception e) {
                        Toast.makeText(this, "无法安全保存 Key", Toast.LENGTH_LONG).show();
                    }
                }).show();
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
