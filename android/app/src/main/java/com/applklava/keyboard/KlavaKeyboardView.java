package com.applklava.keyboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Soft keyboard with dynamic press zones (Nano): weighted score by
 * letter frequency / distance², speed forgiveness, and bias self-calibration.
 */
public class KlavaKeyboardView extends View {

    public interface Listener {
        void onKey(String label);
        void onSwitchToSystem();
        void onOpenSettings();
    }

    private static final String[][] LAYOUT = {
            {"й", "ц", "у", "к", "е", "н", "г", "ш", "щ", "з", "х"},
            {"ф", "ы", "в", "а", "п", "р", "о", "л", "д", "ж", "э"},
            {"⇧", "я", "ч", "с", "м", "и", "т", "ь", "б", "ю", "⌫"},
            {"🌐", ",", "␣", ".", "↵"}
    };

    private static final Map<String, Float> FREQ = new HashMap<>();
    static {
        FREQ.put("о", 1.00f); FREQ.put("е", 0.92f); FREQ.put("а", 0.90f); FREQ.put("и", 0.82f);
        FREQ.put("н", 0.78f); FREQ.put("т", 0.72f); FREQ.put("с", 0.68f); FREQ.put("р", 0.62f);
        FREQ.put("в", 0.58f); FREQ.put("л", 0.55f); FREQ.put("к", 0.50f); FREQ.put("м", 0.46f);
        FREQ.put("д", 0.44f); FREQ.put("п", 0.42f); FREQ.put("у", 0.40f); FREQ.put("я", 0.36f);
        FREQ.put("ы", 0.34f); FREQ.put("ь", 0.32f); FREQ.put("г", 0.30f); FREQ.put("з", 0.28f);
        FREQ.put("б", 0.26f); FREQ.put("ч", 0.24f); FREQ.put("й", 0.20f); FREQ.put("х", 0.18f);
        FREQ.put("ж", 0.16f); FREQ.put("ш", 0.15f); FREQ.put("ю", 0.14f); FREQ.put("ц", 0.12f);
        FREQ.put("щ", 0.10f); FREQ.put("э", 0.08f); FREQ.put("ф", 0.06f); FREQ.put("ъ", 0.04f);
        FREQ.put(",", 0.20f); FREQ.put(".", 0.20f);
    }

    private final List<Key> keys = new ArrayList<>();
    private final Paint keyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint keyPressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint specialPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tmp = new RectF();

    private Listener listener;
    private SharedPreferences prefs;

    private boolean shift;
    private boolean caps;
    private Key pressedKey;
    private Key ghostKey;
    private Key chosenKey;
    private float ringCx, ringCy, ringR;
    private long ringUntil;
    private long flashUntil;

    private float biasX, biasY;
    private float lastX, lastY;
    private long lastT;
    private boolean hasLast;

    private float zoneWidth = 1.45f;
    private float freqWeight = 0.55f;
    private float speedForgive = 0.70f;
    private float calibRate = 0.12f;

    private int activePointerId = MotionEvent.INVALID_POINTER_ID;

    public KlavaKeyboardView(Context context) {
        super(context);
        init(context);
    }

    public KlavaKeyboardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public KlavaKeyboardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        prefs = context.getSharedPreferences("applklava", Context.MODE_PRIVATE);
        loadPrefs();

        bgPaint.setColor(0xFF0B1420);
        keyPaint.setColor(0xFF1A2A42);
        keyPressPaint.setColor(0xFF2E4A72);
        textPaint.setColor(0xFFE8F1FF);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        specialPaint.setColor(0xFF8AA0BD);
        specialPaint.setTextAlign(Paint.Align.CENTER);
        specialPaint.setFakeBoldText(true);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(dp(2));
        ringPaint.setColor(0xD93DD6C6);
        hintPaint.setColor(0xFF6EA8FF);
        hintPaint.setTextAlign(Paint.Align.CENTER);
        hintPaint.setTextSize(dp(11));

        setClickable(true);
        setFocusable(false);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void reloadPrefs() {
        loadPrefs();
        invalidate();
    }

    private void loadPrefs() {
        zoneWidth = prefs.getFloat("zoneWidth", 1.45f);
        freqWeight = prefs.getFloat("freqWeight", 0.55f);
        speedForgive = prefs.getFloat("speedForgive", 0.70f);
        calibRate = prefs.getFloat("calibRate", 0.12f);
        biasX = prefs.getFloat("biasX", 0f);
        biasY = prefs.getFloat("biasY", 0f);
    }

    private void saveBias() {
        prefs.edit().putFloat("biasX", biasX).putFloat("biasY", biasY).apply();
    }

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = (int) dp(280);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        layoutKeys(w, h);
    }

    private void layoutKeys(int w, int h) {
        keys.clear();
        float pad = dp(6);
        float gap = dp(5);
        float topPad = dp(8);
        float rowH = (h - topPad - pad - gap * 3) / 4f;

        for (int r = 0; r < LAYOUT.length; r++) {
            String[] row = LAYOUT[r];
            float[] weights = new float[row.length];
            float total = 0f;
            for (int i = 0; i < row.length; i++) {
                weights[i] = weightFor(row[i]);
                total += weights[i];
            }
            float x = pad;
            float usable = w - pad * 2 - gap * (row.length - 1);
            float y = topPad + r * (rowH + gap);
            for (int i = 0; i < row.length; i++) {
                float kw = usable * (weights[i] / total);
                Key k = new Key();
                k.label = row[i];
                k.bounds.set(x, y, x + kw, y + rowH);
                k.cx = k.bounds.centerX();
                k.cy = k.bounds.centerY();
                k.halfW = kw / 2f;
                k.halfH = rowH / 2f;
                keys.add(k);
                x += kw + gap;
            }
        }
    }

    private float weightFor(String label) {
        switch (label) {
            case "␣": return 5.2f;
            case "⌫":
            case "↵":
            case "⇧": return 1.45f;
            case "🌐": return 1.9f;
            case ",":
            case ".": return 1.1f;
            default: return 1f;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawRect(0, 0, getWidth(), getHeight(), bgPaint);
        long now = SystemClock.uptimeMillis();

        for (Key k : keys) {
            boolean press = k == pressedKey || (k == chosenKey && now < flashUntil);
            Paint fill = press ? keyPressPaint : keyPaint;
            float radius = dp(10);
            canvas.drawRoundRect(k.bounds, radius, radius, fill);

            String draw = displayLabel(k.label);
            Paint tp = isSpecial(k.label) ? specialPaint : textPaint;
            tp.setTextSize(textSizeFor(k.label));
            float textY = k.cy - (tp.descent() + tp.ascent()) / 2f;
            canvas.drawText(draw, k.cx, textY, tp);
        }

        if (now < ringUntil && ringR > 0) {
            canvas.drawCircle(ringCx, ringCy, ringR, ringPaint);
        }
        if (ghostKey != null && chosenKey != null && ghostKey != chosenKey && now < flashUntil) {
            // subtle hint that soft-zone corrected the hit
            canvas.drawText("→ " + displayLabel(chosenKey.label),
                    chosenKey.cx, chosenKey.bounds.top - dp(2), hintPaint);
        }
    }

    private boolean isSpecial(String label) {
        return "⌫".equals(label) || "↵".equals(label) || "⇧".equals(label)
                || "🌐".equals(label) || "␣".equals(label) || ",".equals(label) || ".".equals(label);
    }

    private float textSizeFor(String label) {
        if ("␣".equals(label)) return dp(14);
        if ("🌐".equals(label) || "⇧".equals(label)) return dp(16);
        return dp(20);
    }

    private String displayLabel(String label) {
        switch (label) {
            case "␣": return "пробел";
            case "🌐": return "Обыч.";
            case "⇧": return shift || caps ? "⇧" : "⇧";
            case "⌫": return "⌫";
            case "↵": return "↵";
            default:
                if (shift || caps) return label.toUpperCase(Locale.ROOT);
                return label;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int action = event.getActionMasked();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                if (activePointerId != MotionEvent.INVALID_POINTER_ID) return true;
                int idx = event.getActionIndex();
                activePointerId = event.getPointerId(idx);
                handlePress(event.getX(idx), event.getY(idx));
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                int idx = event.findPointerIndex(activePointerId);
                if (idx < 0) return true;
                Key under = hardHit(event.getX(idx), event.getY(idx));
                if (under != pressedKey) {
                    pressedKey = under;
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                int idx = event.findPointerIndex(activePointerId);
                if (idx < 0) {
                    // wrong pointer ended
                    if (event.getPointerId(event.getActionIndex()) == activePointerId) {
                        activePointerId = MotionEvent.INVALID_POINTER_ID;
                        pressedKey = null;
                        invalidate();
                    }
                    return true;
                }
                float x = event.getX(idx);
                float y = event.getY(idx);
                activePointerId = MotionEvent.INVALID_POINTER_ID;
                pressedKey = null;
                commitAt(x, y);
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                activePointerId = MotionEvent.INVALID_POINTER_ID;
                pressedKey = null;
                invalidate();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private void handlePress(float x, float y) {
        pressedKey = hardHit(x, y);
        invalidate();
    }

    private void commitAt(float x, float y) {
        long now = SystemClock.uptimeMillis();
        float speed = 0f;
        if (hasLast) {
            float dt = Math.max(1f, now - lastT);
            speed = (float) Math.hypot(x - lastX, y - lastY) / dt;
        }
        lastX = x;
        lastY = y;
        lastT = now;
        hasLast = true;

        Key hit = hardHit(x, y);
        Pick pick = smartPick(x, y, speed, hit);
        Key chose = pick.chose;
        if (chose == null) {
            invalidate();
            return;
        }

        ghostKey = hit;
        chosenKey = chose;
        flashUntil = now + 160;
        if (!isSpecial(chose.label) || ",".equals(chose.label) || ".".equals(chose.label)) {
            ringCx = chose.cx;
            ringCy = chose.cy;
            ringR = Math.max(chose.halfW, chose.halfH) * pick.zoneMul * 0.9f;
            ringUntil = now + 220;
        }

        String label = chose.label;
        if ("🌐".equals(label)) {
            if (listener != null) listener.onSwitchToSystem();
            invalidate();
            return;
        }
        if ("⇧".equals(label)) {
            if (shift) {
                caps = !caps;
                shift = false;
            } else {
                shift = true;
            }
            invalidate();
            return;
        }

        String out = mapOutput(label);
        if (listener != null) listener.onKey(out);

        if (isLetter(label)) {
            calibrate(chose, x, y);
            if (shift && !caps) shift = false;
        }
        invalidate();
    }

    private String mapOutput(String label) {
        switch (label) {
            case "␣": return " ";
            case "↵": return "\n";
            case "⌫": return "⌫";
            case ",": return ",";
            case ".": return ".";
            default:
                if (shift || caps) return label.toUpperCase(Locale.ROOT);
                return label;
        }
    }

    private boolean isLetter(String label) {
        return label.length() == 1 && Character.isLetter(label.charAt(0));
    }

    private Key hardHit(float x, float y) {
        Key best = null;
        float bestD = Float.MAX_VALUE;
        for (Key k : keys) {
            if (k.bounds.contains(x, y)) return k;
            float d = dist2(x, y, k.cx, k.cy);
            if (d < bestD) {
                bestD = d;
                best = k;
            }
        }
        return best;
    }

    private Pick smartPick(float x, float y, float speed, Key hard) {
        float speedBoost = 1f + Math.min(2.5f, speed * 18f) * speedForgive;
        float zoneMul = zoneWidth * speedBoost;
        float cx = x - biasX;
        float cy = y - biasY;

        // Special controls: trust hard hit-box.
        if (hard != null && ("⌫".equals(hard.label) || "↵".equals(hard.label)
                || "␣".equals(hard.label) || "⇧".equals(hard.label) || "🌐".equals(hard.label))) {
            Pick p = new Pick();
            p.chose = hard;
            p.zoneMul = zoneMul;
            return p;
        }

        Key best = null;
        float bestScore = -Float.MAX_VALUE;
        for (Key k : keys) {
            if ("⌫".equals(k.label) || "↵".equals(k.label) || "␣".equals(k.label)
                    || "⇧".equals(k.label) || "🌐".equals(k.label)) continue;

            float dx = (cx - k.cx) / (k.halfW * zoneMul);
            float dy = (cy - k.cy) / (k.halfH * zoneMul);
            float nd2 = dx * dx + dy * dy;
            if (nd2 > 2.8f) continue;

            float freq = FREQ.containsKey(k.label) ? FREQ.get(k.label) : 0.05f;
            float score = (float) (Math.pow(freq, freqWeight) / (nd2 + 0.08f));
            if (score > bestScore) {
                bestScore = score;
                best = k;
            }
        }
        Pick p = new Pick();
        p.chose = best != null ? best : hard;
        p.zoneMul = zoneMul;
        return p;
    }

    private void calibrate(Key chose, float x, float y) {
        if (calibRate <= 0f) return;
        float errX = x - chose.cx;
        float errY = y - chose.cy;
        biasX = biasX * (1f - calibRate) + errX * calibRate;
        biasY = biasY * (1f - calibRate) + errY * calibRate;
        saveBias();
    }

    private static float dist2(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2;
        float dy = y1 - y2;
        return dx * dx + dy * dy;
    }

    private static class Key {
        String label;
        final RectF bounds = new RectF();
        float cx, cy, halfW, halfH;
    }

    private static class Pick {
        Key chose;
        float zoneMul;
    }
}
