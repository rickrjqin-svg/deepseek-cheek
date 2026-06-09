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
        drawLogo(canvas, x + dp(22), shell.top + dp(46), dp(22));
        text(canvas, "DeepSeek Balance", x + dp(58), shell.top + dp(55),
                dp(25), WHITE, true, Paint.Align.LEFT);
        drawRefresh(canvas, right - dp(50), shell.top + dp(46), t);
        drawSettings(canvas, right, shell.top + dp(46));
        refreshHit.set(right - dp(76), shell.top + dp(18), right - dp(28), shell.top + dp(72));
        settingsHit.set(right - dp(24), shell.top + dp(18), right + dp(24), shell.top + dp(72));

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
        miniCard(canvas, charge, "充值余额", balance == null ? "--" : money(balance.toppedUp, balance.currency),
                Color.rgb(255, 203, 113));
        miniCard(canvas, gift, "赠送余额", balance == null ? "--" : money(balance.granted, balance.currency),
                Color.rgb(123, 224, 255));

        float proTop = account.bottom + dp(12);
        RectF pro = new RectF(x, proTop, right, proTop + dp(68));
        modelCard(canvas, pro, "DeepSeek Pro", "等待统计数据", "-- Tokens",
                .34f, Color.rgb(173, 77, 238), "✦");

        float flashTop = pro.bottom + dp(10);
        RectF flash = new RectF(x, flashTop, right, flashTop + dp(68));
        modelCard(canvas, flash, "DeepSeek Flash", "等待统计数据", "-- Tokens",
                .78f, Color.rgb(65, 168, 255), "ϟ");

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
        text(c, "等待统计数据",
                r.right - dp(18), r.top + dp(36), dp(13),
                Color.argb(190, 235, 246, 255), false, Paint.Align.RIGHT);
        float top = r.top + dp(62);
        float bottom = r.bottom - dp(30);
        int count = 7;
        float gap = dp(9);
        float usable = r.width() - dp(40);
        float barW = (usable - gap * (count - 1)) / count;
        double max = .01;
        double[] changes = new double[count];
        String[] labels = new String[count];
        int offset = Math.max(0, history.size() - count);
        for (int i = 0; i < count; i++) {
            int index = offset + i;
            if (index < history.size()) {
                Snapshot now = history.get(index);
                Snapshot before = index > 0 ? history.get(index - 1) : now;
                changes[i] = Math.abs(before.value - now.value);
                labels[i] = now.label;
                max = Math.max(max, changes[i]);
            } else {
                changes[i] = 0;
                labels[i] = "·";
            }
        }
        for (int i = 1; i <= 2; i++) {
            stroke.setColor(Color.argb(35, 255, 255, 255));
            stroke.setStrokeWidth(dp(1));
            float y = top + (bottom - top) * i / 3f;
            c.drawLine(r.left + dp(20), y, r.right - dp(20), y, stroke);
        }
        for (int i = 0; i < count; i++) {
            float left = r.left + dp(20) + i * (barW + gap);
            float barH = changes[i] == 0 ? dp(4)
                    : Math.max(dp(8), (float) (changes[i] / max) * (bottom - top));
            RectF bar = new RectF(left, bottom - barH, left + barW, bottom);
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
                           float ratio, int color, String symbol) {
        glass(c, r, dp(20), 32, false);
        drawOrb(c, r.left + dp(36), r.centerY(), dp(22), color);
        text(c, symbol, r.left + dp(36), r.centerY() + dp(7), dp(18),
                Color.WHITE, true, Paint.Align.CENTER);
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
        text(c, "D", x, y + dp(8), dp(24), Color.WHITE, true, Paint.Align.CENTER);
    }

    private void drawOrb(Canvas c, float x, float y, float radius, int color) {
        paint.setShader(new RadialGradient(x - radius * .3f, y - radius * .4f, radius * 1.4f,
                Color.WHITE, color, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, radius, paint);
        paint.setShader(null);
        text(c, "◆", x, y + dp(7), dp(20), Color.WHITE, true, Paint.Align.CENTER);
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
        while (history.size() > 14) history.remove(0);
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
}
