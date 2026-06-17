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
import java.text.SimpleDateFormat;
import java.util.Date;
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

        TextView cookieLabel = new TextView(this);
        cookieLabel.setText("控制台 Cookie（用于读取 Pro/Flash 用量，可选）");
        cookieLabel.setTextColor(muted);
        cookieLabel.setTextSize(12);
        cookieLabel.setPadding(0, dp(14), 0, dp(6));
        content.addView(cookieLabel);

        EditText cookieInput = new EditText(this);
        cookieInput.setHint("platform.deepseek.com 登录后的 Cookie");
        cookieInput.setHintTextColor(Color.rgb(112, 145, 157));
        cookieInput.setTextColor(white);
        cookieInput.setTextSize(12);
        cookieInput.setText(SecureStore.get(this, "platform_cookie"));
        cookieInput.setSingleLine(false);
        cookieInput.setMinLines(2);
        cookieInput.setMaxLines(3);
        cookieInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        cookieInput.setPadding(dp(14), dp(8), dp(14), dp(8));
        cookieInput.setBackground(roundBackground(Color.rgb(8, 37, 48),
                Color.rgb(54, 91, 104), dp(13), 1));
        content.addView(cookieInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(74)));

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
                SecureStore.put(this, "platform_cookie", cookieInput.getText().toString().trim());
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
            String cookie = SecureStore.get(this, "platform_cookie");
            if (!cookie.isEmpty()) requestConsoleUsage(cookie);
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

    private void requestConsoleUsage(String cookie) {
        try {
            UsageAccumulator usage = new UsageAccumulator();
            String start = new SimpleDateFormat("yyyy-MM-01", Locale.US).format(new Date());
            String end = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            String month = new SimpleDateFormat("M", Locale.US).format(new Date());
            String year = new SimpleDateFormat("yyyy", Locale.US).format(new Date());
            String[] endpoints = {
                    "https://platform.deepseek.com/api/v0/usage/amount?month=" + month + "&year=" + year,
                    "https://platform.deepseek.com/api/v0/usage/cost?month=" + month + "&year=" + year,
                    "https://platform.deepseek.com/api/v0/usage/current",
                    "https://platform.deepseek.com/api/v0/users/settings",
                    "https://platform.deepseek.com/api/v0/users/current",
                    "https://platform.deepseek.com/api/v0/users/get_user_summary",
                    "https://platform.deepseek.com/api/usage/amount?month=" + month + "&year=" + year,
                    "https://platform.deepseek.com/api/usage/cost?month=" + month + "&year=" + year,
                    "https://platform.deepseek.com/api/usage/current",
                    "https://platform.deepseek.com/api/users/settings",
                    "https://platform.deepseek.com/api/users/current",
                    "https://platform.deepseek.com/api/users/get_user_summary",
                    "https://platform.deepseek.com/usage/amount?month=" + month + "&year=" + year,
                    "https://platform.deepseek.com/usage/cost?month=" + month + "&year=" + year,
                    "https://platform.deepseek.com/usage/current",
                    "https://platform.deepseek.com/api/v0/usage?start_date=" + start + "&end_date=" + end,
                    "https://platform.deepseek.com/api/v0/billing/usage?start_date=" + start + "&end_date=" + end,
                    "https://platform.deepseek.com/api/v0/dashboard/usage?start_date=" + start + "&end_date=" + end,
                    "https://platform.deepseek.com/api/v0/usage/daily?start_date=" + start + "&end_date=" + end,
                    "https://platform.deepseek.com/api/usage?start_date=" + start + "&end_date=" + end,
                    "https://platform.deepseek.com/api/billing/usage?start_date=" + start + "&end_date=" + end
            };
            for (String endpoint : endpoints) {
                try {
                    String body = requestConsoleJson(endpoint, cookie);
                    if (!body.isEmpty() && body.trim().startsWith("{")) {
                        parseUsageObject(new JSONObject(body), usage, "");
                    } else if (!body.isEmpty() && body.trim().startsWith("[")) {
                        parseUsageArray(new JSONArray(body), usage, "");
                    }
                    if (usage.hasData()) break;
                } catch (Exception ignored) {}
            }
            if (usage.hasData()) {
                getSharedPreferences("usage", MODE_PRIVATE).edit()
                        .putFloat("pro_tokens", (float) usage.proTokens)
                        .putFloat("pro_cost", (float) usage.proCost)
                        .putFloat("flash_tokens", (float) usage.flashTokens)
                        .putFloat("flash_cost", (float) usage.flashCost)
                        .apply();
                runOnUiThread(() -> view.invalidate());
            }
        } catch (Exception ignored) {}
    }

    private String requestConsoleJson(String endpoint, String cookie) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        try {
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("Accept", "application/json, text/plain, */*");
            connection.setRequestProperty("Cookie", cookie);
            connection.setRequestProperty("Referer", "https://platform.deepseek.com/usage");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 DeepSeekBalance/1.2");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) return "";
            return read(connection.getInputStream());
        } finally {
            connection.disconnect();
        }
    }

    private void parseUsageArray(JSONArray array, UsageAccumulator usage, String inheritedModel) throws Exception {
        for (int i = 0; i < array.length(); i++) {
            Object item = array.get(i);
            if (item instanceof JSONObject) parseUsageObject((JSONObject) item, usage, inheritedModel);
            else if (item instanceof JSONArray) parseUsageArray((JSONArray) item, usage, inheritedModel);
        }
    }

    private void parseUsageObject(JSONObject object, UsageAccumulator usage, String inheritedModel) throws Exception {
        String model = inheritedModel;
        JSONArray names = object.names();
        if (names == null) return;
        for (int i = 0; i < names.length(); i++) {
            String name = names.getString(i);
            Object value = object.get(name);
            String lowerName = name.toLowerCase(Locale.US);
            if (value instanceof String) {
                String text = ((String) value).toLowerCase(Locale.US);
                if (isProValue(text)) model = "pro";
                if (isFlashValue(text)) model = "flash";
            }
            if ((lowerName.contains("model") || lowerName.contains("product")) && value != null) {
                String text = String.valueOf(value).toLowerCase(Locale.US);
                if (isProValue(text)) model = "pro";
                if (isFlashValue(text)) model = "flash";
            }
            if (isProKey(lowerName)) model = "pro";
            if (isFlashKey(lowerName)) model = "flash";
            if ((value instanceof JSONObject || value instanceof JSONArray)
                    && (isProKey(lowerName) || isFlashKey(lowerName))) {
                String nestedModel = isProKey(lowerName) ? "pro" : "flash";
                if (value instanceof JSONObject) parseUsageObject((JSONObject) value, usage, nestedModel);
                else parseUsageArray((JSONArray) value, usage, nestedModel);
            }
        }
        double tokens = numericAny(object, "total_tokens", "tokens", "token_count", "usage_tokens",
                "prompt_tokens", "completion_tokens", "input_tokens", "output_tokens",
                "total_amount", "amount", "usage_amount", "token_num", "total_token_num");
        double cost = numericAny(object, "cost", "amount_cost", "expense", "fee", "total_fee",
                "total_cost", "money", "total_money", "usage_cost");
        if (!model.isEmpty() && (tokens > 0 || cost > 0)) usage.add(model, tokens, cost);
        for (int i = 0; i < names.length(); i++) {
            Object value = object.get(names.getString(i));
            if (value instanceof JSONObject) parseUsageObject((JSONObject) value, usage, model);
            else if (value instanceof JSONArray) parseUsageArray((JSONArray) value, usage, model);
        }
    }

    private boolean isProValue(String text) {
        return text.contains("v4-pro") || text.contains("deepseek-v4-pro")
                || text.contains("v4_pro") || text.contains("pro");
    }

    private boolean isFlashValue(String text) {
        return text.contains("v4-flash") || text.contains("deepseek-v4-flash")
                || text.contains("v4_flash") || text.contains("flash");
    }

    private boolean isProKey(String text) {
        return text.equals("pro") || text.equals("v4_pro") || text.equals("v4-pro")
                || text.equals("deepseek_v4_pro") || text.equals("deepseek-v4-pro");
    }

    private boolean isFlashKey(String text) {
        return text.equals("flash") || text.equals("v4_flash") || text.equals("v4-flash")
                || text.equals("deepseek_v4_flash") || text.equals("deepseek-v4-flash");
    }

    private double numericAny(JSONObject object, String... keys) {
        double total = 0;
        for (String key : keys) {
            if (!object.has(key)) continue;
            Object value = object.opt(key);
            if (value instanceof Number) total += ((Number) value).doubleValue();
            else if (value instanceof String) {
                try { total += Double.parseDouble((String) value); } catch (Exception ignored) {}
            }
        }
        return total;
    }

    private static final class UsageAccumulator {
        double proTokens;
        double proCost;
        double flashTokens;
        double flashCost;

        void add(String model, double tokens, double cost) {
            if ("pro".equals(model)) {
                proTokens += tokens;
                proCost += cost;
            } else if ("flash".equals(model)) {
                flashTokens += tokens;
                flashCost += cost;
            }
        }

        boolean hasData() {
            return proTokens > 0 || flashTokens > 0 || proCost > 0 || flashCost > 0;
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
