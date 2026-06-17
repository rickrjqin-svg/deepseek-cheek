package com.rickrjqin.deepseekbalance;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class BalanceView extends View {
    interface Actions {
        void refresh();
        void settings();
    }

    static final class Balance {
        final boolean available;
        final String currency;
        final double total;
        final double toppedUp;
        final double granted;

        Balance(boolean available, String currency, double total, double toppedUp, double granted) {
            this.available = available;
            this.currency = currency;
            this.total = total;
            this.toppedUp = toppedUp;
            this.granted = granted;
        }
    }

    private static final int WHITE = Color.rgb(244, 250, 255);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Actions actions;
    private final float density;
    private final RectF refreshHit = new RectF();
    private final RectF settingsHit = new RectF();
    private final List<Snapshot> history = new ArrayList<>();
    private Balance balance;
    private long startTime = System.nanoTime();
    private float pressedScale = 1f;
    private float layoutScale = 1f;
    private boolean loading;
    private String error = "";

    BalanceView(Context context, Actions actions) {
        super(context);
        this.actions = actions;
        density = getResources().getDisplayMetrics().density;
        paint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL));
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(1));
        loadHistory();
    }

    void setLoading(boolean value) {
        loading = value;
        invalidate();
        if (value) postInvalidateOnAnimation();
    }

    void setError(String message) {
        error = message;
        invalidate();
    }

    void setBalance(Balance value) {
        balance = value;
        error = "";
        saveSnapshot(value.total);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;
        layoutScale = Math.min(1f, Math.min(
                w / (390f * density),
                h / (844f * density)));
        float t = (System.nanoTime() - startTime) / 1_000_000_000f;

        paint.setShader(new LinearGradient(0, 0, w, h,
                new int[]{Color.rgb(13, 66, 91), Color.rgb(40, 111, 130),
                        Color.rgb(102, 119, 92), Color.rgb(17, 60, 85)},
                new float[]{0, .34f, .7f, 1}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(new RadialGradient(
                w * (.76f + .08f * (float) Math.sin(t * .22f)), h * .17f,
                w * .68f, Color.argb(175, 255, 211, 100), Color.TRANSPARENT,
                Shader.TileMode.CLAMP));
        canvas.drawCircle(w * .8f, h * .18f, w * .72f, paint);
        paint.setShader(new RadialGradient(
                w * (.16f + .06f * (float) Math.cos(t * .18f)), h * .62f,
                w * .65f, Color.argb(130, 32, 178, 237), Color.TRANSPARENT,
                Shader.TileMode.CLAMP));
        canvas.drawCircle(w * .18f, h * .58f, w * .68f, paint);
        paint.setShader(null);

        float margin = dp(24);
        float top = dp(56);
        float bottom = h - dp(34);
        RectF shell = new RectF(margin, top, w - margin, bottom);
        canvas.save();
        canvas.scale(pressedScale, pressedScale, w / 2f, h / 2f);
        glass(canvas, shell, dp(32), 53, true);

        float x = shell.left + dp(24);
        float right = shell.right - dp(24);
        drawLogo(canvas, x + dp(19), shell.top + dp(46), dp(19));
        text(canvas, "DeepSeek Balance", x + dp(47), shell.top + dp(52),
                dp(18), WHITE, true, Paint.Align.LEFT);
        drawRefresh(canvas, right - dp(38), shell.top + dp(46), t);
        drawSettings(canvas, right, shell.top + dp(46));
        refreshHit.set(right - dp(59), shell.top + dp(20), right - dp(18), shell.top + dp(70));
        settingsHit.set(right - dp(18), shell.top + dp(20), right + dp(20), shell.top + dp(70));

        float accountTop = shell.top + dp(92);
        RectF account = new RectF(x, accountTop, right, accountTop + dp(205));
        glass(canvas, account, dp(25), 38, false);
        text(canvas, "▰  账户余额", account.left + dp(22), account.top + dp(38),
                dp(18), WHITE, false, Paint.Align.LEFT);
        String status = balance == null ? (error.isEmpty() ? "待连接" : "异常")
                : (balance.available ? "●  可用" : "●  不可用");
        text(canvas, status, account.right - dp(20), account.top + dp(38),
                dp(15), balance != null && balance.available
                        ? Color.rgb(104, 255, 196) : Color.rgb(255, 189, 120),
                false, Paint.Align.RIGHT);
        String amount = balance == null ? "¥ --" : money(balance.total, balance.currency);
        paint.setShader(new LinearGradient(account.left, 0, account.right, 0,
                Color.rgb(126, 220, 255), Color.rgb(116, 131, 255), Shader.TileMode.CLAMP));
        text(canvas, amount, account.left + dp(22), account.top + dp(105),
                dp(45), Color.WHITE, true, Paint.Align.LEFT);
        paint.setShader(null);

        float gap = dp(12);
        float cardW = (account.width() - dp(44) - gap) / 2f;
        RectF charge = new RectF(account.left + dp(16), account.top + dp(126),
                account.left + dp(16) + cardW, account.bottom - dp(14));
        RectF gift = new RectF(charge.right + gap, charge.top,
                account.right - dp(16), charge.bottom);
        miniCard(canvas, charge, "赠送额度", balance == null ? "--" : money(balance.granted, balance.currency),
                Color.rgb(255, 203, 113));
        miniCard(canvas, gift, "月度用量",
                monthlyUsage() <= 0 && balance == null ? "--"
                        : money(monthlyUsage(), balance == null ? "CNY" : balance.currency),
                Color.rgb(123, 224, 255));

        UsageStats proUsage = usageStats("pro");
        UsageStats flashUsage = usageStats("flash");
        double maxModelTokens = Math.max(1, Math.max(proUsage.tokens, flashUsage.tokens));

        float proTop = account.bottom + dp(12);
        RectF pro = new RectF(x, proTop, right, proTop + dp(68));
        modelCard(canvas, pro, "DeepSeek V4 Pro", money(proUsage.cost, "CNY"),
                tokenText(proUsage.tokens), (float) (proUsage.tokens / maxModelTokens),
                Color.rgb(173, 77, 238), true);

        float flashTop = pro.bottom + dp(10);
        RectF flash = new RectF(x, flashTop, right, flashTop + dp(68));
        modelCard(canvas, flash, "DeepSeek V4 Flash", money(flashUsage.cost, "CNY"),
                tokenText(flashUsage.tokens), (float) (flashUsage.tokens / maxModelTokens),
                Color.rgb(65, 168, 255), false);

        float chartTop = flash.bottom + dp(12);
        RectF chart = new RectF(x, chartTop, right, shell.bottom - dp(24));
        glass(canvas, chart, dp(25), 36, false);
        drawChart(canvas, chart);
        canvas.restore();
        if (loading) postInvalidateOnAnimation();
    }

    private void drawChart(Canvas c, RectF r) {
        if (r.width() <= dp(80) || r.height() <= dp(90)) return;
        text(c, "▥  用量变化", r.left + dp(20), r.top + dp(36),
                dp(18), WHITE, true, Paint.Align.LEFT);
        float top = r.top + dp(66);
        float bottom = r.bottom - dp(30);
        int count = 7;
        float gap = dp(9);
        float axisW = dp(34);
        float leftBase = r.left + dp(20) + axisW;
        float rightBase = r.right - dp(16);
        float usable = rightBase - leftBase;
        float barW = (usable - gap * (count - 1)) / count;
        double max = .01;
        double[] changes = new double[count];
        String[] labels = new String[count];
        UsageStats proUsage = usageStats("pro");
        UsageStats flashUsage = usageStats("flash");
        double consoleTokens = proUsage.tokens + flashUsage.tokens;
        if (consoleTokens > 0) {
            long nowMs = System.currentTimeMillis();
            for (int i = 0; i < count; i++) {
                Date day = new Date(nowMs - (long) (count - 1 - i) * 24L * 60L * 60L * 1000L);
                labels[i] = new SimpleDateFormat("M/d", Locale.CHINA).format(day);
                changes[i] = i == count - 1 ? consoleTokens : 0;
                max = Math.max(max, changes[i]);
            }
        } else {
        int offset = Math.max(0, history.size() - count);
        for (int i = 0; i < count; i++) {
            int index = offset + i;
            if (index < history.size()) {
                Snapshot now = history.get(index);
                Snapshot before = index > 0 ? history.get(index - 1) : now;
                changes[i] = Math.max(0, before.value - now.value) * 1_000_000d;
                labels[i] = now.label;
                max = Math.max(max, changes[i]);
            } else {
                changes[i] = 0;
                labels[i] = "·";
            }
        }
        }
        for (int i = 1; i <= 2; i++) {
            stroke.setColor(Color.argb(35, 255, 255, 255));
            stroke.setStrokeWidth(dp(1));
            float y = top + (bottom - top) * i / 3f;
            c.drawLine(leftBase, y, rightBase, y, stroke);
        }
        text(c, tokenAxisValue(max), r.left + dp(17), top + dp(4), dp(9),
                Color.argb(180, 235, 246, 255), false, Paint.Align.LEFT);
        text(c, tokenAxisValue(max / 2), r.left + dp(17), (top + bottom) / 2 + dp(4), dp(9),
                Color.argb(160, 235, 246, 255), false, Paint.Align.LEFT);
        text(c, "0", r.left + dp(17), bottom + dp(4), dp(9),
                Color.argb(140, 235, 246, 255), false, Paint.Align.LEFT);
        for (int i = 0; i < count; i++) {
            float left = leftBase + i * (barW + gap);
            float barH = changes[i] == 0 ? dp(4)
                    : Math.max(dp(8), (float) (changes[i] / max) * (bottom - top));
            RectF bar = new RectF(left, bottom - barH, left + barW, bottom);
            if (changes[i] > 0) {
                text(c, tokenAxisValue(changes[i]), left + barW / 2, bar.top - dp(5),
                        dp(8), Color.argb(210, 245, 250, 255), false, Paint.Align.CENTER);
            }
            paint.setShader(new LinearGradient(0, bar.top, 0, bar.bottom,
                    Color.rgb(127, 208, 255), Color.rgb(55, 92, 238),
                    Shader.TileMode.CLAMP));
            c.drawRoundRect(bar, dp(7), dp(7), paint);
            paint.setShader(null);
            text(c, labels[i], left + barW / 2, r.bottom - dp(10),
                    dp(10), Color.argb(205, 245, 250, 255), false, Paint.Align.CENTER);
        }
    }

    private void miniCard(Canvas c, RectF r, String label, String value, int color) {
        paint.setColor(Color.argb(22, 255, 255, 255));
        c.drawRoundRect(r, dp(18), dp(18), paint);
        stroke.setColor(Color.argb(42, 255, 255, 255));
        c.drawRoundRect(r, dp(18), dp(18), stroke);
        text(c, label, r.left + dp(16), r.top + dp(27), dp(14),
                Color.argb(210, 245, 250, 255), false, Paint.Align.LEFT);
        text(c, value, r.left + dp(16), r.bottom - dp(15), dp(21),
                color, true, Paint.Align.LEFT);
    }

    private void modelCard(Canvas c, RectF r, String name, String cost, String tokens,
                           float ratio, int color, boolean neural) {
        glass(c, r, dp(20), 32, false);
        drawModelIcon(c, r.left + dp(36), r.centerY(), dp(22), color, neural);
        text(c, name, r.left + dp(68), r.top + dp(27), dp(16),
                WHITE, true, Paint.Align.LEFT);
        text(c, cost, r.right - dp(16), r.top + dp(27), dp(12),
                Color.argb(210, 245, 250, 255), false, Paint.Align.RIGHT);
        text(c, tokens, r.left + dp(68), r.top + dp(48), dp(11),
                Color.argb(190, 235, 246, 255), false, Paint.Align.LEFT);
        drawProgress(c, r.left + dp(68), r.bottom - dp(10), r.right - dp(16), ratio);
    }

    private void glass(Canvas c, RectF r, float radius, int alpha, boolean outer) {
        paint.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom,
                Color.argb(alpha + 8, 255, 255, 255),
                Color.argb(Math.max(10, alpha - 17), 125, 215, 235),
                Shader.TileMode.CLAMP));
        c.drawRoundRect(r, radius, radius, paint);
        paint.setShader(null);
        stroke.setColor(Color.argb(145, 255, 255, 255));
        stroke.setStrokeWidth(outer ? dp(1.4f) : dp(.8f));
        c.drawRoundRect(r, radius, radius, stroke);
    }

    private void drawLogo(Canvas c, float x, float y, float radius) {
        paint.setShader(new LinearGradient(x - radius, y - radius, x + radius, y + radius,
                Color.rgb(61, 192, 255), Color.rgb(47, 91, 235), Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(x - radius, y - radius, x + radius, y + radius),
                dp(13), dp(13), paint);
        paint.setShader(null);

        paint.setColor(Color.WHITE);
        Path whale = new Path();
        whale.moveTo(x - radius * .58f, y + radius * .05f);
        whale.cubicTo(x - radius * .40f, y - radius * .47f,
                x + radius * .22f, y - radius * .42f,
                x + radius * .46f, y - radius * .06f);
        whale.cubicTo(x + radius * .28f, y + radius * .42f,
                x - radius * .30f, y + radius * .48f,
                x - radius * .58f, y + radius * .05f);
        c.drawPath(whale, paint);
        Path tail = new Path();
        tail.moveTo(x + radius * .37f, y - radius * .18f);
        tail.lineTo(x + radius * .70f, y - radius * .42f);
        tail.lineTo(x + radius * .60f, y - radius * .04f);
        tail.lineTo(x + radius * .78f, y + radius * .22f);
        tail.lineTo(x + radius * .39f, y + radius * .08f);
        tail.close();
        c.drawPath(tail, paint);
        paint.setColor(Color.rgb(39, 119, 245));
        c.drawCircle(x - radius * .28f, y - radius * .08f, dp(1.8f), paint);
    }

    private void drawModelIcon(Canvas c, float x, float y, float radius,
                               int color, boolean neural) {
        paint.setShader(new RadialGradient(x - radius * .3f, y - radius * .4f, radius * 1.4f,
                Color.WHITE, color, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, radius, paint);
        paint.setShader(null);
        stroke.setColor(Color.WHITE);
        stroke.setStrokeWidth(dp(1.6f));
        stroke.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(Color.WHITE);
        if (neural) {
            float[][] nodes = {
                    {x - dp(9), y - dp(7)}, {x - dp(9), y + dp(7)},
                    {x, y - dp(11)}, {x, y}, {x, y + dp(11)},
                    {x + dp(9), y - dp(7)}, {x + dp(9), y + dp(7)}
            };
            int[][] links = {{0,2},{0,3},{1,3},{1,4},{2,5},{3,5},{3,6},{4,6}};
            for (int[] link : links) {
                c.drawLine(nodes[link[0]][0], nodes[link[0]][1],
                        nodes[link[1]][0], nodes[link[1]][1], stroke);
            }
            for (float[] node : nodes) c.drawCircle(node[0], node[1], dp(2.4f), paint);
        } else {
            Path bolt = new Path();
            bolt.moveTo(x + dp(3), y - dp(15));
            bolt.lineTo(x - dp(8), y + dp(1));
            bolt.lineTo(x - dp(1), y + dp(1));
            bolt.lineTo(x - dp(4), y + dp(15));
            bolt.lineTo(x + dp(10), y - dp(4));
            bolt.lineTo(x + dp(3), y - dp(4));
            bolt.close();
            c.drawPath(bolt, paint);
        }
    }

    private void drawRefresh(Canvas c, float x, float y, float time) {
        stroke.setColor(WHITE);
        stroke.setStrokeWidth(dp(2.5f));
        stroke.setStrokeCap(Paint.Cap.ROUND);
        RectF r = new RectF(x - dp(12), y - dp(12), x + dp(12), y + dp(12));
        c.save();
        if (loading) c.rotate(time * 190f, x, y);
        c.drawArc(r, -55, 275, false, stroke);
        Path p = new Path();
        p.moveTo(x + dp(6), y - dp(13));
        p.lineTo(x + dp(14), y - dp(12));
        p.lineTo(x + dp(12), y - dp(4));
        c.drawPath(p, stroke);
        c.restore();
    }

    private void drawSettings(Canvas c, float x, float y) {
        text(c, "⚙", x, y + dp(10), dp(29), WHITE, false, Paint.Align.CENTER);
    }

    private void drawProgress(Canvas c, float left, float y, float right, float ratio) {
        paint.setColor(Color.argb(80, 3, 35, 50));
        c.drawRoundRect(new RectF(left, y, right, y + dp(5)), dp(3), dp(3), paint);
        ratio = Math.max(0, Math.min(1, ratio));
        paint.setShader(new LinearGradient(left, 0, right, 0,
                Color.rgb(83, 126, 255), Color.rgb(108, 242, 246), Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(left, y, left + (right - left) * ratio, y + dp(5)),
                dp(3), dp(3), paint);
        paint.setShader(null);
    }

    private void text(Canvas c, String value, float x, float y, float size, int color,
                      boolean bold, Paint.Align align) {
        paint.setTextSize(size);
        paint.setColor(color);
        paint.setTextAlign(align);
        paint.setTypeface(android.graphics.Typeface.create("sans",
                bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL));
        c.drawText(value, x, y, paint);
    }

    private String money(double amount, String currency) {
        String symbol = "CNY".equals(currency) ? "¥" : currency + " ";
        return symbol + String.format(Locale.US, "%,.2f", amount);
    }

    private String tokenText(double tokens) {
        if (tokens >= 1_000_000) return String.format(Locale.US, "%.1fM Tokens", tokens / 1_000_000d);
        if (tokens >= 1_000) return String.format(Locale.US, "%.1fK Tokens", tokens / 1_000d);
        return String.format(Locale.US, "%.0f Tokens", tokens);
    }

    private String chartValue(double value) {
        if (value >= 1000) return String.format(Locale.US, "¥%.1fk", value / 1000d);
        if (value >= 10) return String.format(Locale.US, "¥%.0f", value);
        if (value > 0) return String.format(Locale.US, "¥%.2f", value);
        return "¥0";
    }

    private String tokenAxisValue(double value) {
        if (value >= 1_000_000) return String.format(Locale.US, "%.0fM", value / 1_000_000d);
        if (value >= 1_000) return String.format(Locale.US, "%.0fK", value / 1_000d);
        return String.format(Locale.US, "%.0f", value);
    }

    private UsageStats usageStats(String key) {
        SharedPreferences prefs = getContext().getSharedPreferences("usage", Context.MODE_PRIVATE);
        return new UsageStats(
                prefs.getFloat(key + "_tokens", 0),
                prefs.getFloat(key + "_cost", 0));
    }

    private double monthlyUsage() {
        UsageStats proUsage = usageStats("pro");
        UsageStats flashUsage = usageStats("flash");
        double consoleCost = proUsage.cost + flashUsage.cost;
        if (consoleCost > 0) return consoleCost;
        String monthPrefix = new SimpleDateFormat("M/", Locale.CHINA).format(new Date());
        double total = 0;
        for (int i = 1; i < history.size(); i++) {
            Snapshot before = history.get(i - 1);
            Snapshot now = history.get(i);
            if (now.label.startsWith(monthPrefix)) {
                total += Math.max(0, before.value - now.value);
            }
        }
        return total;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        if (event.getAction() == MotionEvent.ACTION_DOWN
                && (refreshHit.contains(x, y) || settingsHit.contains(x, y))) {
            animateScale(.975f);
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_UP) {
            animateScale(1f);
            haptic();
            if (refreshHit.contains(x, y)) actions.refresh();
            else if (settingsHit.contains(x, y)) actions.settings();
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_CANCEL) animateScale(1f);
        return true;
    }

    private void animateScale(float target) {
        ValueAnimator animator = ValueAnimator.ofFloat(pressedScale, target);
        animator.setDuration(target == 1f ? 420 : 120);
        animator.setInterpolator(target == 1f ? new OvershootInterpolator(2.3f) : null);
        animator.addUpdateListener(a -> {
            pressedScale = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    private void haptic() {
        Vibrator vibrator = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createOneShot(18, 80));
        }
    }

    private void loadHistory() {
        try {
            String raw = getContext().getSharedPreferences("history", Context.MODE_PRIVATE)
                    .getString("snapshots", "[]");
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                history.add(new Snapshot(item.getDouble("value"), item.getString("label")));
            }
        } catch (Exception ignored) {}
    }

    private void saveSnapshot(double value) {
        String label = new SimpleDateFormat("M/d", Locale.CHINA).format(new Date());
        if (!history.isEmpty() && history.get(history.size() - 1).label.equals(label)) {
            history.set(history.size() - 1, new Snapshot(value, label));
        } else {
            history.add(new Snapshot(value, label));
        }
        while (history.size() > 40) history.remove(0);
        JSONArray array = new JSONArray();
        try {
            for (Snapshot item : history) {
                JSONObject object = new JSONObject();
                object.put("value", item.value);
                object.put("label", item.label);
                array.put(object);
            }
        } catch (Exception ignored) {}
        SharedPreferences prefs = getContext().getSharedPreferences("history", Context.MODE_PRIVATE);
        prefs.edit().putString("snapshots", array.toString()).apply();
    }

    private float dp(float value) {
        return value * density * layoutScale;
    }

    private static final class Snapshot {
        final double value;
        final String label;

        Snapshot(double value, String label) {
            this.value = value;
            this.label = label;
        }
    }

    private static final class UsageStats {
        final double tokens;
        final double cost;

        UsageStats(double tokens, double cost) {
            this.tokens = tokens;
            this.cost = cost;
        }
    }
}
