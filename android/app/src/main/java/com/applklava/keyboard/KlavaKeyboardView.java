package com.applklava.keyboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Compact soft keyboard: Nano dynamic zones, space-swipe language switch (RU/EN),
 * slang dictionary suggestions. No zone-ring overlays.
 */
public class KlavaKeyboardView extends View {

    public interface Listener {
        void onKey(String label);
        void onSwitchToSystem();
        void onOpenSettings();
        /** Current composing word prefix before cursor (letters only). */
        String currentWordPrefix();
        /** Wipe all text in the focused field. */
        void onDeleteAll();
    }

    private static final String[][] LAYOUT_RU = {
            {"й", "ц", "у", "к", "е", "н", "г", "ш", "щ", "з", "х"},
            {"ф", "ы", "в", "а", "п", "р", "о", "л", "д", "ж", "э"},
            {"⇧", "я", "ч", "с", "м", "и", "т", "ь", "б", "ю", "⌫"},
            {"🌐", ",", "␣", ".", "↵"}
    };

    private static final String[][] LAYOUT_EN = {
            {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"},
            {"a", "s", "d", "f", "g", "h", "j", "k", "l"},
            {"⇧", "z", "x", "c", "v", "b", "n", "m", "⌫"},
            {"🌐", ",", "␣", ".", "↵"}
    };

    private static final Map<String, Float> FREQ_RU = new HashMap<>();
    private static final Map<String, Float> FREQ_EN = new HashMap<>();
    static {
        FREQ_RU.put("о", 1.00f); FREQ_RU.put("е", 0.92f); FREQ_RU.put("а", 0.90f); FREQ_RU.put("и", 0.82f);
        FREQ_RU.put("н", 0.78f); FREQ_RU.put("т", 0.72f); FREQ_RU.put("с", 0.68f); FREQ_RU.put("р", 0.62f);
        FREQ_RU.put("в", 0.58f); FREQ_RU.put("л", 0.55f); FREQ_RU.put("к", 0.50f); FREQ_RU.put("м", 0.46f);
        FREQ_RU.put("д", 0.44f); FREQ_RU.put("п", 0.42f); FREQ_RU.put("у", 0.40f); FREQ_RU.put("я", 0.36f);
        FREQ_RU.put("ы", 0.34f); FREQ_RU.put("ь", 0.32f); FREQ_RU.put("г", 0.30f); FREQ_RU.put("з", 0.28f);
        FREQ_RU.put("б", 0.26f); FREQ_RU.put("ч", 0.24f); FREQ_RU.put("й", 0.20f); FREQ_RU.put("х", 0.18f);
        FREQ_RU.put("ж", 0.16f); FREQ_RU.put("ш", 0.15f); FREQ_RU.put("ю", 0.14f); FREQ_RU.put("ц", 0.12f);
        FREQ_RU.put("щ", 0.10f); FREQ_RU.put("э", 0.08f); FREQ_RU.put("ф", 0.06f);

        FREQ_EN.put("e", 1.00f); FREQ_EN.put("t", 0.91f); FREQ_EN.put("a", 0.82f); FREQ_EN.put("o", 0.75f);
        FREQ_EN.put("i", 0.70f); FREQ_EN.put("n", 0.67f); FREQ_EN.put("s", 0.63f); FREQ_EN.put("h", 0.60f);
        FREQ_EN.put("r", 0.55f); FREQ_EN.put("d", 0.43f); FREQ_EN.put("l", 0.40f); FREQ_EN.put("c", 0.28f);
        FREQ_EN.put("u", 0.27f); FREQ_EN.put("m", 0.24f); FREQ_EN.put("w", 0.23f); FREQ_EN.put("f", 0.22f);
        FREQ_EN.put("g", 0.20f); FREQ_EN.put("y", 0.19f); FREQ_EN.put("p", 0.18f); FREQ_EN.put("b", 0.15f);
        FREQ_EN.put("v", 0.10f); FREQ_EN.put("k", 0.08f); FREQ_EN.put("j", 0.02f); FREQ_EN.put("x", 0.02f);
        FREQ_EN.put("q", 0.01f); FREQ_EN.put("z", 0.01f);
    }

    private final List<Key> keys = new ArrayList<>();
    private final List<RectF> suggestHit = new ArrayList<>();
    private final List<String> suggestWords = new ArrayList<>();
    private final WordDictionary dict = new WordDictionary();

    private final Paint keyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint keyPressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint specialPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chipTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint langToastPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private Listener listener;
    private SharedPreferences prefs;

    private boolean english;
    private boolean shift;
    private boolean caps;
    private Key pressedKey;
    private Key chosenKey;
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
    private float downX, downY;
    private boolean spaceTracking;
    private boolean spaceSwiped;
    private String langToast;
    private long langToastUntil;

    private float suggestH;
    private float keyboardH;
    private int navInsetPx;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean backspaceTracking;
    private boolean backspaceDidFull;
    private final Runnable backspaceLongPress = new Runnable() {
        @Override public void run() {
            if (!backspaceTracking) return;
            backspaceDidFull = true;
            if (listener != null) listener.onDeleteAll();
            // keep pressed look briefly, then clear
            scheduleFlashClear(1800);
            refreshSuggestions();
            invalidate();
        }
    };
    private final Runnable flashClear = new Runnable() {
        @Override public void run() {
            chosenKey = null;
            pressedKey = null;
            langToast = null;
            invalidate();
        }
    };

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
        chipPaint.setColor(0xFF16253B);
        chipTextPaint.setColor(0xFFE8F1FF);
        chipTextPaint.setTextAlign(Paint.Align.CENTER);
        chipTextPaint.setFakeBoldText(true);
        langToastPaint.setColor(0xEE3DD6C6);
        langToastPaint.setTextAlign(Paint.Align.CENTER);
        langToastPaint.setFakeBoldText(true);
        langToastPaint.setTextSize(dp(16));

        setClickable(true);
        setFocusable(false);

        ViewCompat.setOnApplyWindowInsetsListener(this, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
            int bottom = Math.max(bars.bottom, fallbackNavInset());
            if (navInsetPx != bottom) {
                navInsetPx = bottom;
                requestLayout();
            }
            return insets;
        });
    }

    private int fallbackNavInset() {
        int resId = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        int sys = resId > 0 ? getResources().getDimensionPixelSize(resId) : 0;
        // Extra cushion so space isn't under Home / gesture bar.
        return Math.max(sys, (int) dp(28));
    }

    private int navPad() {
        return navInsetPx > 0 ? navInsetPx : fallbackNavInset();
    }

    private void scheduleFlashClear(long delayMs) {
        handler.removeCallbacks(flashClear);
        flashUntil = SystemClock.uptimeMillis() + delayMs;
        handler.postDelayed(flashClear, delayMs);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void reloadPrefs() {
        loadPrefs();
        refreshSuggestions();
        invalidate();
    }

    private void loadPrefs() {
        zoneWidth = prefs.getFloat("zoneWidth", 1.45f);
        freqWeight = prefs.getFloat("freqWeight", 0.55f);
        speedForgive = prefs.getFloat("speedForgive", 0.70f);
        calibRate = prefs.getFloat("calibRate", 0.12f);
        biasX = prefs.getFloat("biasX", 0f);
        biasY = prefs.getFloat("biasY", 0f);
        english = prefs.getBoolean("english", false);
    }

    private void saveLang() {
        prefs.edit().putBoolean("english", english).apply();
    }

    private void saveBias() {
        prefs.edit().putFloat("biasX", biasX).putFloat("biasY", biasY).apply();
    }

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private int screenHeight() {
        WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics dm = new DisplayMetrics();
        if (wm != null) {
            wm.getDefaultDisplay().getMetrics(dm);
            return dm.heightPixels;
        }
        return getResources().getDisplayMetrics().heightPixels;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        // Fit small phones: ~30% of screen for keys+suggestions, plus nav pad.
        int target = Math.round(screenHeight() * 0.30f);
        int minH = (int) dp(190);
        int maxH = (int) dp(236);
        int content = Math.max(minH, Math.min(maxH, target));
        suggestH = dp(34);
        keyboardH = content - suggestH;
        int h = content + navPad();
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        suggestH = dp(34);
        int nav = navPad();
        keyboardH = Math.max(dp(140), h - suggestH - nav);
        layoutKeys(w, (int) keyboardH);
        refreshSuggestions();
    }

    private String[][] currentLayout() {
        return english ? LAYOUT_EN : LAYOUT_RU;
    }

    private void layoutKeys(int w, int h) {
        keys.clear();
        String[][] layout = currentLayout();
        float pad = dp(4);
        float gap = dp(3.5f);
        float topPad = dp(4);
        float rowH = (h - topPad - pad - gap * (layout.length - 1)) / (float) layout.length;

        for (int r = 0; r < layout.length; r++) {
            String[] row = layout[r];
            float[] weights = new float[row.length];
            float total = 0f;
            for (int i = 0; i < row.length; i++) {
                weights[i] = weightFor(row[i]);
                total += weights[i];
            }
            float x = pad;
            float usable = w - pad * 2 - gap * (row.length - 1);
            float y = suggestH + topPad + r * (rowH + gap);
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
            case "␣": return 5.4f;
            case "⌫":
            case "↵":
            case "⇧": return 1.4f;
            case "🌐": return 1.55f;
            case ",":
            case ".": return 1.05f;
            default: return 1f;
        }
    }

    public void refreshSuggestions() {
        String prefix = listener != null ? safe(listener.currentWordPrefix()) : "";
        suggestWords.clear();
        suggestWords.addAll(dict.suggestions(prefix, english, 4));
        layoutSuggestionHits();
        invalidate();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private void layoutSuggestionHits() {
        suggestHit.clear();
        if (suggestWords.isEmpty() || getWidth() <= 0) return;
        float pad = dp(4);
        float gap = dp(4);
        float chipW = (getWidth() - pad * 2 - gap * (suggestWords.size() - 1)) / (float) suggestWords.size();
        float top = dp(4);
        float h = suggestH - dp(8);
        for (int i = 0; i < suggestWords.size(); i++) {
            float x = pad + i * (chipW + gap);
            suggestHit.add(new RectF(x, top, x + chipW, top + h));
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawRect(0, 0, getWidth(), getHeight(), bgPaint);
        long now = SystemClock.uptimeMillis();

        // Suggestion chips
        chipTextPaint.setTextSize(dp(13));
        for (int i = 0; i < suggestWords.size() && i < suggestHit.size(); i++) {
            RectF r = suggestHit.get(i);
            canvas.drawRoundRect(r, dp(8), dp(8), chipPaint);
            float ty = r.centerY() - (chipTextPaint.descent() + chipTextPaint.ascent()) / 2f;
            canvas.drawText(suggestWords.get(i), r.centerX(), ty, chipTextPaint);
        }

        float radius = dp(7);
        for (Key k : keys) {
            boolean press = k == pressedKey || (k == chosenKey && now < flashUntil);
            Paint fill = press ? keyPressPaint : keyPaint;
            canvas.drawRoundRect(k.bounds, radius, radius, fill);

            String draw = displayLabel(k.label);
            Paint tp = isSpecial(k.label) ? specialPaint : textPaint;
            tp.setTextSize(textSizeFor(k.label));
            float textY = k.cy - (tp.descent() + tp.ascent()) / 2f;
            canvas.drawText(draw, k.cx, textY, tp);
        }

        if (langToast != null && now < langToastUntil) {
            canvas.drawText(langToast, getWidth() / 2f, suggestH + keyboardH / 2f, langToastPaint);
        }
    }

    private boolean isSpecial(String label) {
        return "⌫".equals(label) || "↵".equals(label) || "⇧".equals(label)
                || "🌐".equals(label) || "␣".equals(label) || ",".equals(label) || ".".equals(label);
    }

    private float textSizeFor(String label) {
        if ("␣".equals(label)) return dp(12);
        if ("🌐".equals(label)) return dp(11);
        if ("⇧".equals(label) || "⌫".equals(label) || "↵".equals(label)) return dp(14);
        return dp(17);
    }

    private String displayLabel(String label) {
        switch (label) {
            case "␣": return english ? "en · swipe" : "ru · свайп";
            case "🌐": return "Обыч.";
            case "⇧": return "⇧";
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
                downX = event.getX(idx);
                downY = event.getY(idx);
                spaceSwiped = false;
                spaceTracking = false;
                backspaceTracking = false;
                backspaceDidFull = false;
                handler.removeCallbacks(backspaceLongPress);

                int sug = hitSuggestion(downX, downY);
                if (sug >= 0) {
                    pressedKey = null;
                    invalidate();
                    return true;
                }

                Key hit = hardHit(downX, downY);
                pressedKey = hit;
                spaceTracking = hit != null && "␣".equals(hit.label);
                if (hit != null && "⌫".equals(hit.label)) {
                    backspaceTracking = true;
                    // Hold ~0.4s → wipe ALL text (not one-by-one).
                    handler.postDelayed(backspaceLongPress, 400);
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                int idx = event.findPointerIndex(activePointerId);
                if (idx < 0) return true;
                float x = event.getX(idx);
                float y = event.getY(idx);

                if (spaceTracking) {
                    float dx = x - downX;
                    if (Math.abs(dx) > dp(36)) {
                        spaceSwiped = true;
                        langToast = english ? "Русский" : "English";
                        langToastUntil = SystemClock.uptimeMillis() + 900;
                        scheduleFlashClear(900);
                        invalidate();
                    }
                    return true;
                }

                if (backspaceTracking) {
                    Key under = hardHit(x, y);
                    if (under == null || !"⌫".equals(under.label)) {
                        // slid off delete — cancel full wipe
                        handler.removeCallbacks(backspaceLongPress);
                        backspaceTracking = false;
                        pressedKey = under;
                        invalidate();
                    }
                    return true;
                }

                Key under = hardHit(x, y);
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
                    if (event.getPointerId(event.getActionIndex()) == activePointerId) {
                        activePointerId = MotionEvent.INVALID_POINTER_ID;
                        pressedKey = null;
                        handler.removeCallbacks(backspaceLongPress);
                        backspaceTracking = false;
                        invalidate();
                    }
                    return true;
                }
                float x = event.getX(idx);
                float y = event.getY(idx);
                activePointerId = MotionEvent.INVALID_POINTER_ID;
                pressedKey = null;
                handler.removeCallbacks(backspaceLongPress);

                int sug = hitSuggestion(downX, downY);
                if (sug >= 0 && Math.hypot(x - downX, y - downY) < dp(24)) {
                    commitSuggestion(suggestWords.get(sug));
                    spaceTracking = false;
                    backspaceTracking = false;
                    invalidate();
                    return true;
                }

                if (spaceTracking) {
                    float dx = x - downX;
                    if (spaceSwiped || Math.abs(dx) > dp(36)) {
                        toggleLanguage();
                    } else {
                        commitAt(downX, downY);
                    }
                    spaceTracking = false;
                    spaceSwiped = false;
                    invalidate();
                    return true;
                }

                if (backspaceTracking) {
                    boolean full = backspaceDidFull;
                    backspaceTracking = false;
                    backspaceDidFull = false;
                    if (!full) {
                        // short tap → one char
                        commitAt(downX, downY);
                    } else {
                        scheduleFlashClear(1800);
                    }
                    invalidate();
                    return true;
                }

                commitAt(x, y);
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                activePointerId = MotionEvent.INVALID_POINTER_ID;
                pressedKey = null;
                spaceTracking = false;
                spaceSwiped = false;
                backspaceTracking = false;
                backspaceDidFull = false;
                handler.removeCallbacks(backspaceLongPress);
                scheduleFlashClear(0);
                invalidate();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private int hitSuggestion(float x, float y) {
        for (int i = 0; i < suggestHit.size(); i++) {
            if (suggestHit.get(i).contains(x, y)) return i;
        }
        return -1;
    }

    private void commitSuggestion(String word) {
        if (listener == null || word == null) return;
        String prefix = safe(listener.currentWordPrefix());
        // delete prefix letters then commit full word + space
        for (int i = 0; i < prefix.length(); i++) {
            listener.onKey("⌫");
        }
        listener.onKey(word);
        listener.onKey(" ");
        if (shift && !caps) shift = false;
        refreshSuggestions();
    }

    private void toggleLanguage() {
        english = !english;
        saveLang();
        shift = false;
        langToast = english ? "English" : "Русский";
        langToastUntil = SystemClock.uptimeMillis() + 900;
        scheduleFlashClear(900);
        layoutKeys(getWidth(), (int) keyboardH);
        refreshSuggestions();
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

        chosenKey = chose;
        // Press highlight stays briefly, then fades (~2 sec max).
        scheduleFlashClear(1800);

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
        refreshSuggestions();
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
        // Prefer keys in keyboard area (ignore suggestion strip for key hits)
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

        if (hard != null && ("⌫".equals(hard.label) || "↵".equals(hard.label)
                || "␣".equals(hard.label) || "⇧".equals(hard.label) || "🌐".equals(hard.label))) {
            Pick p = new Pick();
            p.chose = hard;
            p.zoneMul = zoneMul;
            return p;
        }

        String prefix = listener != null ? safe(listener.currentWordPrefix()) : "";
        Map<String, Float> freq = english ? FREQ_EN : FREQ_RU;

        Key best = null;
        float bestScore = -Float.MAX_VALUE;
        for (Key k : keys) {
            if ("⌫".equals(k.label) || "↵".equals(k.label) || "␣".equals(k.label)
                    || "⇧".equals(k.label) || "🌐".equals(k.label)) continue;

            float dx = (cx - k.cx) / (k.halfW * zoneMul);
            float dy = (cy - k.cy) / (k.halfH * zoneMul);
            float nd2 = dx * dx + dy * dy;
            if (nd2 > 2.8f) continue;

            float base = freq.containsKey(k.label) ? freq.get(k.label) : 0.05f;
            float slangBoost = 0f;
            if (k.label.length() == 1) {
                char ch = k.label.charAt(0);
                slangBoost = 0.15f * dict.letterBoost(ch, english)
                        + 0.55f * dict.nextLetterBoost(prefix, ch, english);
            }
            float score = (float) ((Math.pow(base, freqWeight) + slangBoost) / (nd2 + 0.08f));
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
