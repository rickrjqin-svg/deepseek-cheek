package com.rickrjqin.deepseekbalance;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.EditText;
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
    private BalanceView view;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) controller.setSystemBarsBehavior(
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        } else {
            window.getDecorView().setSystemUiVisibility(
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
        view = new BalanceView(this, this);
        setContentView(view);
        if (SecureStore.get(this).isEmpty()) {
            view.postDelayed(this::showKeyDialog, 550);
        } else {
            view.postDelayed(this::refresh, 420);
        }
    }

    @Override
    public void refresh() {
        String key = SecureStore.get(this);
        if (key.isEmpty()) {
            showKeyDialog();
            return;
        }
        view.setLoading(true);
        executor.execute(() -> requestBalance(key));
    }

    @Override
    public void settings() {
        showKeyDialog();
    }

    private void showKeyDialog() {
        EditText input = new EditText(this);
        input.setHint("sk-...");
        input.setText(SecureStore.get(this));
        input.setSelectAllOnFocus(true);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        int pad = Math.round(24 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad / 2, pad, pad / 2);
        new AlertDialog.Builder(this)
                .setTitle("连接 DeepSeek")
                .setMessage("API Key 仅在本机通过 Android Keystore 加密保存，不会上传到任何第三方服务器。")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存并刷新", (dialog, which) -> {
                    String key = input.getText().toString().trim();
                    if (key.isEmpty()) return;
                    try {
                        SecureStore.put(this, key);
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
            });
        } catch (Exception e) {
            String message = e.getMessage() == null ? "网络连接失败" : e.getMessage();
            runOnUiThread(() -> {
                view.setLoading(false);
                view.setError(message);
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            });
        } finally {
            if (connection != null) connection.disconnect();
        }
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
        executor.shutdownNow();
        super.onDestroy();
    }
}
