package com.calismazamanlayici.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * 🏎️ SÜPER TURBO ARABA YARIŞI (CPU Rakiplere Karşı, Kalıcı Seviye Hafızalı):
 * - 3 veya 4 Şeritli otoyolda CPU yarış araçlarını sollayarak Bitiş Çizgisine (🏁) ulaşma yarışı!
 * - Geçilen her seviye (Level 1..15+) kalıcı olarak hafızaya ve yedeğe kaydedilir.
 * - Seviye yükseldikçe CPU araçlarının hızı, şerit değiştirme zekası ve trafik yoğunluğu artar.
 * - Sol/Sağ yön butonları ve ⚡ NİTRO TURBO butonu bulunur; her yarış başında 3-2-1-BAŞLA! geri sayımı yapılır.
 */
public class CarRacingGameView extends View {

    public interface CarRaceListener {
        void onRaceSound(boolean positive);
        void onCountdownTick(boolean isGo);
        void onExitRequested();
        void onSpeedChanged(float speedRatio, int level, boolean active);
        void onNitroBoost();
        void onLaneSwitch();
        void onCrashSound();
        void onOvertakeSound();
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final CarRaceListener listener;
    private final float dp;
    private final float sp;

    private boolean running = false;
    private boolean gameOver = false;
    private boolean levelCleared = false;
    private boolean frameScheduled = false;
    private long lastStepMs = 0L;

    // 3-2-1-BAŞLA geri sayımı
    private boolean inCountdown = false;
    private long countdownStartMs = 0L;
    private int lastAnnouncedCountdown = -1;

    private int level = 1;
    private int score = 0;
    private int highScore = 0;
    private int lives = 3;
    private int nitroFuel = 3;
    private int nitroActiveFrames = 0;
    private int invulnerableFrames = 0;
    private float animTick = 0f;

    private int playerLane = 1; // 0: Sol, 1: Orta, 2: Sağ
    private float playerVisualX = 0f;
    private float roadScrollOffset = 0f;
    private float raceDistance = 0f;
    private float targetDistance = 1200f;

    private static class CpuCar {
        int lane;
        float x;
        float y;
        float speed;
        int color;
        String icon;
        boolean canSwitchLane;
        int style;
    }

    private static class RoadPick {
        int lane;
        float y;
        int type; // 0: ⭐ Puan, 1: ⚡ Nitro, 2: 🛡️ Tamir Kalbi
    }

    private final List<CpuCar> cpuCars = new ArrayList<>();
    private final List<RoadPick> pickups = new ArrayList<>();

    private final RectF btnExitRect = new RectF();
    private final RectF btnLeftSteerRect = new RectF();
    private final RectF btnRightSteerRect = new RectF();
    private final RectF btnNitroRect = new RectF();
    private final RectF btnModalPrimaryRect = new RectF();
    private final RectF btnModalLobbyRect = new RectF();

    private final Runnable loopRunnable = new Runnable() {
        @Override
        public void run() {
            if (running && !gameOver && !levelCleared) {
                invalidate();
                postDelayed(this, 16L);
            } else {
                frameScheduled = false;
            }
        }
    };

    public CarRacingGameView(Context context, CarRaceListener listener) {
        super(context);
        this.listener = listener;
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        this.dp = dm.density;
        this.sp = dm.scaledDensity;
        this.level = StudyDatabaseHelper.getSavedGameLevel(context, "car_race_level", 1);
        this.highScore = Math.max(0, StudyDatabaseHelper.getSavedGameLevel(context, "car_race_high_score", 1) - 1);
        setupRaceLevel(this.level);
    }

    private void scheduleNext() {
        if (!frameScheduled && running && !gameOver && !levelCleared) {
            frameScheduled = true;
            removeCallbacks(loopRunnable);
            postDelayed(loopRunnable, 16L);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (running && !gameOver && !levelCleared) {
            scheduleNext();
            invalidate();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stopGame();
        super.onDetachedFromWindow();
    }

    public void startGame() {
        this.level = StudyDatabaseHelper.getSavedGameLevel(getContext(), "car_race_level", 1);
        setupRaceLevel(this.level);
        running = true;
        frameScheduled = false;
        removeCallbacks(loopRunnable);
        scheduleNext();
        invalidate();
    }

    public void stopGame() {
        running = false;
        if (listener != null) listener.onSpeedChanged(0f, level, false);
        removeCallbacks(loopRunnable);
        frameScheduled = false;
    }

    private void setupRaceLevel(int targetLvl) {
        this.level = Math.max(1, targetLvl);
        this.lives = 3;
        this.nitroFuel = 3;
        this.nitroActiveFrames = 0;
        this.invulnerableFrames = 35;
        this.playerLane = 1;
        this.raceDistance = 0f;
        this.targetDistance = (1050f + level * 180f);
        this.gameOver = false;
        this.levelCleared = false;
        cpuCars.clear();
        pickups.clear();

        inCountdown = true;
        countdownStartMs = SystemClock.elapsedRealtime();
        lastAnnouncedCountdown = -1;
        lastStepMs = SystemClock.elapsedRealtime();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float hudH = 88f * dp;
        float roadLeft = w * 0.14f;
        float roadRight = w * 0.86f;
        float roadW = roadRight - roadLeft;
        float laneW = roadW / 3f;

        // Çimen / Çöl / Gece kenar arka planı (Seviyeye göre değişir)
        int[] sideColors = new int[]{0xFF15803D, 0xFFD97706, 0xFF1E293B, 0xFF0369A1};
        canvas.drawColor(sideColors[(level - 1) % sideColors.length]);

        // Asfalt Yol ve Kaldırım Bordürleri
        paint.setColor(0xFFEF4444);
        canvas.drawRect(roadLeft - 10f * dp, hudH, roadLeft, h, paint);
        canvas.drawRect(roadRight, hudH, roadRight + 10f * dp, h, paint);

        paint.setColor(0xFF334155);
        canvas.drawRect(roadLeft, hudH, roadRight, h, paint);

        // Şerit Çizgileri
        paint.setColor(0xFFF8FAFC);
        float dashH = 38f * dp;
        float gapH = 32f * dp;
        float totalDash = dashH + gapH;
        for (int l = 1; l <= 2; l++) {
            float lx = roadLeft + l * laneW;
            for (float y = hudH - totalDash + (roadScrollOffset % totalDash); y < h; y += totalDash) {
                canvas.drawRect(lx - 3f * dp, y, lx + 3f * dp, y + dashH, paint);
            }
        }

        // Geri Sayım veya Fizik Güncellemesi
        long nowMs = SystemClock.elapsedRealtime();
        int cdStep = 0;
        if (inCountdown) {
            long el = nowMs - countdownStartMs;
            if (el < 800L) cdStep = 3;
            else if (el < 1600L) cdStep = 2;
            else if (el < 2400L) cdStep = 1;
            else if (el < 2950L) cdStep = 0;
            else {
                inCountdown = false;
                lastStepMs = nowMs;
            }
            if (inCountdown && cdStep != lastAnnouncedCountdown) {
                lastAnnouncedCountdown = cdStep;
                if (listener != null) listener.onCountdownTick(cdStep == 0);
            }
        } else if (running && !gameOver && !levelCleared) {
            if (nowMs - lastStepMs >= 15L) {
                lastStepMs = nowMs;
                updateRacePhysics(w, h, hudH, roadLeft, laneW);
            }
        }

        // Yoldaki Ödüller (⭐, ⚡, ❤️)
        for (RoadPick rp : pickups) {
            float px = roadLeft + (rp.lane + 0.5f) * laneW;
            paint.setColor(rp.type == 1 ? 0xFF38BDF8 : (rp.type == 2 ? 0xFFFFCDD2 : 0xFFFFD600));
            canvas.drawCircle(px, rp.y, 18f * dp, paint);
            paint.setColor(0xFF0F172A);
            paint.setTextSize(17f * sp);
            paint.setTextAlign(Paint.Align.CENTER);
            String ic = (rp.type == 1) ? "⚡" : (rp.type == 2 ? "❤️" : "★");
            canvas.drawText(ic, px, rp.y + 6f * dp, paint);
        }

        // Nitro Hız Çizgileri (Yüksek hız efekti)
        if (!inCountdown && running && nitroActiveFrames > 0) {
            paint.setColor(0x55FFFFFF);
            paint.setStrokeWidth(2.5f * dp);
            for (int sl = 0; sl < 6; sl++) {
                float sx = roadLeft + (sl + 0.5f) * (roadW / 6f);
                float sy = hudH + ((roadScrollOffset * 2.2f + sl * 130f * dp) % (h - hudH));
                canvas.drawLine(sx, sy, sx, sy + 48f * dp, paint);
            }
        }

        // CPU Rakip Araçlar (Gerçekçi vektör çizimli araçlar)
        for (CpuCar c : cpuCars) {
            drawRealisticCar(canvas, c.x, c.y, 46f * dp, 84f * dp, c.color, 0xFF0F172A, c.style, false, false);
        }

        // Oyuncu Yarış Arabası (Gerçekçi süper spor araba)
        float targetPlayerX = roadLeft + (playerLane + 0.5f) * laneW;
        if (playerVisualX == 0f) playerVisualX = targetPlayerX;
        playerVisualX += (targetPlayerX - playerVisualX) * 0.28f;
        float playerY = h - 145f * dp;

        if (invulnerableFrames % 4 < 2) {
            drawRealisticCar(canvas, playerVisualX, playerY, 50f * dp, 90f * dp,
                    nitroActiveFrames > 0 ? 0xFFEA580C : 0xFFDC2626, 0xFFF8FAFC, 99, true, nitroActiveFrames > 0);
        }

        // Alt Kontrol Butonları (⬅️ SOL ŞERİT | ⚡ NİTRO | ➡️ SAĞ ŞERİT)
        if (!inCountdown && !gameOver && !levelCleared) {
            float btnYTop = h - 74f * dp;
            float btnYBot = h - 14f * dp;
            btnLeftSteerRect.set(12f * dp, btnYTop, w * 0.34f, btnYBot);
            btnNitroRect.set(w * 0.36f, btnYTop, w * 0.64f, btnYBot);
            btnRightSteerRect.set(w * 0.66f, btnYTop, w - 12f * dp, btnYBot);

            paint.setColor(0xDD1E293B);
            canvas.drawRoundRect(btnLeftSteerRect, 14f * dp, 14f * dp, paint);
            canvas.drawRoundRect(btnRightSteerRect, 14f * dp, 14f * dp, paint);
            paint.setColor(nitroFuel > 0 ? 0xDDEA580C : 0x88334155);
            canvas.drawRoundRect(btnNitroRect, 14f * dp, 14f * dp, paint);

            paint.setColor(0xFFFFFFFF);
            paint.setTextSize(15f * sp);
            paint.setFakeBoldText(true);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("⬅️ SOL", btnLeftSteerRect.centerX(), btnLeftSteerRect.centerY() + 6f * dp, paint);
            canvas.drawText("⚡ NİTRO (" + nitroFuel + ")", btnNitroRect.centerX(), btnNitroRect.centerY() + 6f * dp, paint);
            canvas.drawText("SAĞ ➡️", btnRightSteerRect.centerX(), btnRightSteerRect.centerY() + 6f * dp, paint);
        }

        // Üst Gösterge Paneli
        drawRaceHud(canvas, w, hudH);

        if (inCountdown) {
            drawCountdownOverlay(canvas, w, h, cdStep);
        } else if (levelCleared || gameOver) {
            drawEndModal(canvas, w, h);
        }
        if (running && !gameOver && !levelCleared) {
            scheduleNext();
        }
    }

    private void drawRaceHud(Canvas canvas, int w, float hudH) {
        paint.setColor(0xEE0F172A);
        canvas.drawRect(0, 0, w, hudH, paint);

        btnExitRect.set(8f * dp, 18f * dp, 78f * dp, 66f * dp);
        paint.setColor(0xFFDC2626);
        canvas.drawRoundRect(btnExitRect, 8f * dp, 8f * dp, paint);
        paint.setColor(0xFFFFFFFF);
        paint.setTextSize(12.5f * sp);
        paint.setFakeBoldText(true);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("⬅️ Lobi", btnExitRect.centerX(), 47f * dp, paint);

        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(14f * sp);
        StringBuilder hearts = new StringBuilder();
        for (int i = 0; i < 3; i++) hearts.append(i < lives ? "❤️" : "🖤");
        canvas.drawText("🏎️ SEVİYE " + level + "  " + hearts, 86f * dp, 38f * dp, paint);

        // İlerleme çubuğu
        float barL = 86f * dp;
        float barR = w - 95f * dp;
        paint.setColor(0xFF334155);
        canvas.drawRoundRect(new RectF(barL, 48f * dp, barR, 68f * dp), 7f * dp, 7f * dp, paint);
        float ratio = Math.min(1f, Math.max(0f, raceDistance / targetDistance));
        paint.setColor(0xFF22C55E);
        canvas.drawRoundRect(new RectF(barL, 48f * dp, barL + (barR - barL) * ratio, 68f * dp), 7f * dp, 7f * dp, paint);
        paint.setColor(0xFFFFFFFF);
        paint.setTextSize(11.5f * sp);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("🏁 %" + (int) (ratio * 100), (barL + barR) * 0.5f, 62f * dp, paint);

        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setTextSize(15f * sp);
        paint.setColor(0xFFFFD600);
        canvas.drawText("⭐ " + score, w - 10f * dp, 48f * dp, paint);
    }

    private void drawRealisticCar(Canvas canvas, float cx, float cy, float carW, float carH,
                                  int bodyColor, int stripeColor, int style, boolean isPlayer, boolean nitroOn) {
        float hw = carW * 0.5f;
        float hh = carH * 0.5f;

        // 1. Yumuşak asfalt gölgesi
        paint.setShader(null);
        paint.setColor(0x70000000);
        Path shadowPath = new Path();
        shadowPath.moveTo(cx - hw * 0.72f + 4f * dp, cy - hh + 5f * dp);
        shadowPath.lineTo(cx + hw * 0.72f + 4f * dp, cy - hh + 5f * dp);
        shadowPath.quadTo(cx + hw * 1.08f + 4f * dp, cy, cx + hw * 0.96f + 4f * dp, cy + hh + 5f * dp);
        shadowPath.lineTo(cx - hw * 0.96f + 4f * dp, cy + hh + 5f * dp);
        shadowPath.quadTo(cx - hw * 1.08f + 4f * dp, cy, cx - hw * 0.72f + 4f * dp, cy - hh + 5f * dp);
        shadowPath.close();
        canvas.drawPath(shadowPath, paint);

        // 2. 4 adet çıkıntılı lastik ve gümüş jant detayı
        float wheelW = 6.5f * dp;
        float wheelH = 17f * dp;
        float[][] wheelPos = new float[][]{
                {cx - hw - 2.5f * dp, cy - hh + 12f * dp},
                {cx + hw - wheelW + 2.5f * dp, cy - hh + 12f * dp},
                {cx - hw - 2.5f * dp, cy + hh - 28f * dp},
                {cx + hw - wheelW + 2.5f * dp, cy + hh - 28f * dp}
        };
        for (float[] wp : wheelPos) {
            paint.setColor(0xFF0A0E17);
            canvas.drawRoundRect(new RectF(wp[0], wp[1], wp[0] + wheelW, wp[1] + wheelH), 3f * dp, 3f * dp, paint);
            paint.setColor(0xFF94A3B8);
            canvas.drawRect(wp[0] + 1.5f * dp, wp[1] + 4f * dp, wp[0] + wheelW - 1.5f * dp, wp[1] + wheelH - 4f * dp, paint);
        }

        if (isPlayer) {
            // Nitro egzoz alevi (çift egzozdan mavi-sarı alev)
            if (nitroOn) {
                float flameLen = (24f + (float) Math.abs(Math.sin(animTick * 2.2f)) * 20f) * dp;
                paint.setColor(0xFF38BDF8);
                canvas.drawOval(new RectF(cx - 13f * dp, cy + hh - 2f * dp, cx - 3f * dp, cy + hh + flameLen), paint);
                canvas.drawOval(new RectF(cx + 3f * dp, cy + hh - 2f * dp, cx + 13f * dp, cy + hh + flameLen), paint);
                paint.setColor(0xFFFACC15);
                canvas.drawOval(new RectF(cx - 10f * dp, cy + hh, cx - 6f * dp, cy + hh + flameLen * 0.65f), paint);
                canvas.drawOval(new RectF(cx + 6f * dp, cy + hh, cx + 10f * dp, cy + hh + flameLen * 0.65f), paint);
            }

            // İleri far ışık konileri
            paint.setColor(0x28FEF08A);
            Path leftBeam = new Path();
            leftBeam.moveTo(cx - hw * 0.62f, cy - hh + 4f * dp);
            leftBeam.lineTo(cx - hw * 1.15f, cy - hh - 58f * dp);
            leftBeam.lineTo(cx - hw * 0.15f, cy - hh - 58f * dp);
            leftBeam.close();
            canvas.drawPath(leftBeam, paint);

            Path rightBeam = new Path();
            rightBeam.moveTo(cx + hw * 0.62f, cy - hh + 4f * dp);
            rightBeam.lineTo(cx + hw * 0.15f, cy - hh - 58f * dp);
            rightBeam.lineTo(cx + hw * 1.15f, cy - hh - 58f * dp);
            rightBeam.close();
            canvas.drawPath(rightBeam, paint);

            // Aerodinamik süper spor araba gövdesi (Sivri burun + çamurluk çıkıntıları + arka difüzör)
            Path body = new Path();
            body.moveTo(cx, cy - hh - 2f * dp); // Sivri burun ucu
            body.quadTo(cx + hw * 0.72f, cy - hh + 4f * dp, cx + hw * 0.92f, cy - hh * 0.45f);
            body.quadTo(cx + hw * 1.05f, cy - hh * 0.15f, cx + hw * 0.86f, cy + hh * 0.12f); // Bel kıvrımı
            body.quadTo(cx + hw * 1.04f, cy + hh * 0.55f, cx + hw * 0.94f, cy + hh - 6f * dp); // Arka çamurluk
            body.lineTo(cx + hw * 0.68f, cy + hh);
            body.lineTo(cx - hw * 0.68f, cy + hh);
            body.lineTo(cx - hw * 0.94f, cy + hh - 6f * dp);
            body.quadTo(cx - hw * 1.04f, cy + hh * 0.55f, cx - hw * 0.86f, cy + hh * 0.12f);
            body.quadTo(cx - hw * 1.05f, cy - hh * 0.15f, cx - hw * 0.92f, cy - hh * 0.45f);
            body.quadTo(cx - hw * 0.72f, cy - hh + 4f * dp, cx, cy - hh - 2f * dp);
            body.close();

            int lightBody = nitroOn ? 0xFFFB923C : 0xFFF87171;
            int darkBody = nitroOn ? 0xFFC2410C : 0xFF991B1B;
            paint.setShader(new LinearGradient(cx - hw, cy, cx + hw, cy,
                    new int[]{darkBody, lightBody, bodyColor, darkBody}, null, Shader.TileMode.CLAMP));
            canvas.drawPath(body, paint);
            paint.setShader(null);

            // Yan aynalar
            paint.setColor(darkBody);
            canvas.drawOval(new RectF(cx - hw - 4f * dp, cy - hh * 0.24f, cx - hw * 0.76f, cy - hh * 0.08f), paint);
            canvas.drawOval(new RectF(cx + hw * 0.76f, cy - hh * 0.24f, cx + hw + 4f * dp, cy - hh * 0.08f), paint);

            // Çift yarış şeridi (kaput ve tavan boyunca)
            paint.setColor(stripeColor);
            canvas.drawRect(cx - 4.5f * dp, cy - hh + 7f * dp, cx - 1.2f * dp, cy + hh - 10f * dp, paint);
            canvas.drawRect(cx + 1.2f * dp, cy - hh + 7f * dp, cx + 4.5f * dp, cy + hh - 10f * dp, paint);

            // Kaput hava girişi
            paint.setColor(0xFF1E293B);
            Path scoop = new Path();
            scoop.moveTo(cx - 6f * dp, cy - hh * 0.62f);
            scoop.lineTo(cx + 6f * dp, cy - hh * 0.62f);
            scoop.lineTo(cx + 4f * dp, cy - hh * 0.44f);
            scoop.lineTo(cx - 4f * dp, cy - hh * 0.44f);
            scoop.close();
            canvas.drawPath(scoop, paint);

            // Ön & arka eğimli kokpit camı
            Path glass = new Path();
            glass.moveTo(cx - hw * 0.56f, cy - hh * 0.36f);
            glass.quadTo(cx, cy - hh * 0.50f, cx + hw * 0.56f, cy - hh * 0.36f);
            glass.lineTo(cx + hw * 0.48f, cy + hh * 0.44f);
            glass.lineTo(cx - hw * 0.48f, cy + hh * 0.44f);
            glass.close();
            paint.setShader(new LinearGradient(cx, cy - hh * 0.45f, cx, cy + hh * 0.44f,
                    0xFF38BDF8, 0xFF0F172A, Shader.TileMode.CLAMP));
            canvas.drawPath(glass, paint);
            paint.setShader(null);

            // Tavan kabini
            paint.setColor(bodyColor);
            Path roof = new Path();
            roof.moveTo(cx - hw * 0.38f, cy - hh * 0.10f);
            roof.lineTo(cx + hw * 0.38f, cy - hh * 0.10f);
            roof.lineTo(cx + hw * 0.34f, cy + hh * 0.24f);
            roof.lineTo(cx - hw * 0.34f, cy + hh * 0.24f);
            roof.close();
            canvas.drawPath(roof, paint);

            // Karbon arka GT rüzgarlık (spoiler)
            paint.setColor(0xFF0F172A);
            Path wing = new Path();
            wing.moveTo(cx - hw - 2f * dp, cy + hh - 10f * dp);
            wing.lineTo(cx + hw + 2f * dp, cy + hh - 10f * dp);
            wing.lineTo(cx + hw - 2f * dp, cy + hh - 2f * dp);
            wing.lineTo(cx - hw + 2f * dp, cy + hh - 2f * dp);
            wing.close();
            canvas.drawPath(wing, paint);

            // LED Ön farlar & Arka stop barı
            paint.setColor(0xFFFEF08A);
            canvas.drawOval(new RectF(cx - hw * 0.72f, cy - hh + 6f * dp, cx - hw * 0.36f, cy - hh + 13f * dp), paint);
            canvas.drawOval(new RectF(cx + hw * 0.36f, cy - hh + 6f * dp, cx + hw * 0.72f, cy - hh + 13f * dp), paint);
            paint.setColor(0xFFFF1744);
            canvas.drawRect(cx - hw * 0.74f, cy + hh - 3f * dp, cx - hw * 0.26f, cy + hh, paint);
            canvas.drawRect(cx + hw * 0.26f, cy + hh - 3f * dp, cx + hw * 0.74f, cy + hh, paint);
        } else if (style == 3) {
            // Ağır Kamyon (Aerodinamik Kabin + Metalik Römork)
            paint.setShader(new LinearGradient(cx - hw, cy, cx + hw, cy,
                    new int[]{0xFFCBD5E1, 0xFFF8FAFC, 0xFF94A3B8}, null, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(new RectF(cx - hw * 0.95f, cy - hh, cx + hw * 0.95f, cy + hh * 0.32f), 5f * dp, 5f * dp, paint);
            paint.setShader(null);
            paint.setColor(0xFF94A3B8);
            for (int r = -2; r <= 2; r++) {
                canvas.drawLine(cx - hw * 0.85f, cy - hh * 0.32f + r * 10f * dp, cx + hw * 0.85f, cy - hh * 0.32f + r * 10f * dp, paint);
            }
            // Kabin
            Path cab = new Path();
            cab.moveTo(cx - hw * 0.88f, cy + hh * 0.34f);
            cab.lineTo(cx + hw * 0.88f, cy + hh * 0.34f);
            cab.lineTo(cx + hw * 0.76f, cy + hh);
            cab.lineTo(cx - hw * 0.76f, cy + hh);
            cab.close();
            paint.setColor(bodyColor);
            canvas.drawPath(cab, paint);
            paint.setColor(0xFF0F172A);
            canvas.drawRoundRect(new RectF(cx - hw * 0.65f, cy + hh * 0.56f, cx + hw * 0.65f, cy + hh * 0.84f), 4f * dp, 4f * dp, paint);
            paint.setColor(0xFFFF1744);
            canvas.drawRect(cx - hw * 0.72f, cy + hh - 3f * dp, cx - hw * 0.28f, cy + hh, paint);
            canvas.drawRect(cx + hw * 0.28f, cy + hh - 3f * dp, cx + hw * 0.72f, cy + hh, paint);
        } else {
            // CPU Sedan / Polis / Taksi / GT Coupe (Aşağı yönlü sculpted silüet)
            Path body = new Path();
            body.moveTo(cx - hw * 0.76f, cy - hh);
            body.lineTo(cx + hw * 0.76f, cy - hh);
            body.quadTo(cx + hw * 1.02f, cy - hh * 0.40f, cx + hw * 0.88f, cy);
            body.quadTo(cx + hw * 1.02f, cy + hh * 0.45f, cx + hw * 0.72f, cy + hh - 3f * dp);
            body.quadTo(cx, cy + hh + 3f * dp, cx - hw * 0.72f, cy + hh - 3f * dp);
            body.quadTo(cx - hw * 1.02f, cy + hh * 0.45f, cx - hw * 0.88f, cy);
            body.quadTo(cx - hw * 1.02f, cy - hh * 0.40f, cx - hw * 0.76f, cy - hh);
            body.close();

            int edgeCol = (style == 0) ? 0xFF0F172A : ((bodyColor & 0x00FEFEFE) >> 1 | 0xFF000000);
            paint.setShader(new LinearGradient(cx - hw, cy, cx + hw, cy,
                    new int[]{edgeCol, bodyColor, edgeCol}, null, Shader.TileMode.CLAMP));
            canvas.drawPath(body, paint);
            paint.setShader(null);

            if (style == 0) {
                paint.setColor(0xFFF8FAFC);
                canvas.drawRect(cx - hw * 0.86f, cy - hh * 0.28f, cx + hw * 0.86f, cy + hh * 0.28f, paint);
            }

            // Yan aynalar
            paint.setColor(edgeCol);
            canvas.drawOval(new RectF(cx - hw - 3.5f * dp, cy + hh * 0.05f, cx - hw * 0.78f, cy + hh * 0.20f), paint);
            canvas.drawOval(new RectF(cx + hw * 0.78f, cy + hh * 0.05f, cx + hw + 3.5f * dp, cy + hh * 0.20f), paint);

            // Camlar (trapezoid ön + arka cam)
            Path glass = new Path();
            glass.moveTo(cx - hw * 0.56f, cy - hh * 0.44f);
            glass.lineTo(cx + hw * 0.56f, cy - hh * 0.44f);
            glass.lineTo(cx + hw * 0.62f, cy + hh * 0.42f);
            glass.quadTo(cx, cy + hh * 0.54f, cx - hw * 0.62f, cy + hh * 0.42f);
            glass.close();
            paint.setShader(new LinearGradient(cx, cy - hh * 0.44f, cx, cy + hh * 0.48f,
                    0xFF1E293B, 0xFF334155, Shader.TileMode.CLAMP));
            canvas.drawPath(glass, paint);
            paint.setShader(null);

            // Tavan
            paint.setColor(style == 0 ? 0xFFF8FAFC : bodyColor);
            canvas.drawRoundRect(new RectF(cx - hw * 0.42f, cy - hh * 0.22f, cx + hw * 0.42f, cy + hh * 0.14f), 4f * dp, 4f * dp, paint);

            if (style == 0) {
                boolean flash = ((int) animTick) % 2 == 0;
                paint.setColor(flash ? 0xFFEF4444 : 0xFF3B82F6);
                canvas.drawRoundRect(new RectF(cx - 11f * dp, cy - 4f * dp, cx, cy + 3f * dp), 2f * dp, 2f * dp, paint);
                paint.setColor(flash ? 0xFF3B82F6 : 0xFFEF4444);
                canvas.drawRoundRect(new RectF(cx, cy - 4f * dp, cx + 11f * dp, cy + 3f * dp), 2f * dp, 2f * dp, paint);
            } else if (style == 1) {
                paint.setColor(0xFFFEF08A);
                canvas.drawRoundRect(new RectF(cx - 8f * dp, cy - 4f * dp, cx + 8f * dp, cy + 3f * dp), 2f * dp, 2f * dp, paint);
            }

            // Bize bakan arka stop lambaları (kırmızı) ve ön farlar (sarı)
            paint.setColor(0xFFFF1744);
            canvas.drawRect(cx - hw * 0.68f, cy - hh, cx - hw * 0.24f, cy - hh + 4f * dp, paint);
            canvas.drawRect(cx + hw * 0.24f, cy - hh, cx + hw * 0.68f, cy - hh + 4f * dp, paint);
            paint.setColor(0xFFFEF08A);
            canvas.drawOval(new RectF(cx - hw * 0.68f, cy + hh - 8f * dp, cx - hw * 0.30f, cy + hh - 2f * dp), paint);
            canvas.drawOval(new RectF(cx + hw * 0.30f, cy + hh - 8f * dp, cx + hw * 0.68f, cy + hh - 2f * dp), paint);
        }
    }

    private void drawCountdownOverlay(Canvas canvas, int w, int h, int step) {
        paint.setColor(0x990F172A);
        canvas.drawRect(0, 0, w, h, paint);
        float cx = w * 0.5f;
        float cy = h * 0.48f;
        paint.setColor(0xFF1E293B);
        canvas.drawRoundRect(new RectF(w * 0.10f, cy - 95f * dp, w * 0.90f, cy + 95f * dp), 20f * dp, 20f * dp, paint);
        paint.setColor(0xFF38BDF8);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(17f * sp);
        paint.setFakeBoldText(true);
        canvas.drawText("🏎️ ARABA YARIŞI • SEVİYE " + level, cx, cy - 52f * dp, paint);
        if (step > 0) {
            paint.setColor(0xFFFFD600);
            paint.setTextSize(58f * sp);
            canvas.drawText(String.valueOf(step), cx, cy + 20f * dp, paint);
        } else {
            paint.setColor(0xFF00E676);
            paint.setTextSize(40f * sp);
            canvas.drawText("BAŞLA! 🏁", cx, cy + 18f * dp, paint);
        }
    }

    private void drawEndModal(Canvas canvas, int w, int h) {
        paint.setColor(0xCC0F172A);
        canvas.drawRect(0, 0, w, h, paint);
        float cy = h * 0.50f;
        paint.setColor(0xFFFFFFFF);
        canvas.drawRoundRect(new RectF(w * 0.08f, cy - 135f * dp, w * 0.92f, cy + 145f * dp), 20f * dp, 20f * dp, paint);

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFakeBoldText(true);
        paint.setColor(levelCleared ? 0xFF16A34A : 0xFFDC2626);
        paint.setTextSize(22f * sp);
        canvas.drawText(levelCleared ? ("🏆 SEVİYE " + (level - 1) + " GEÇİLDİ!") : "💥 KAZA YAPTIN (3 CAN BİTTİ)", w * 0.5f, cy - 82f * dp, paint);

        paint.setColor(0xFF1E293B);
        paint.setTextSize(16f * sp);
        canvas.drawText("⭐ Puan: " + score + "   •   🏆 Rekor: " + highScore, w * 0.5f, cy - 38f * dp, paint);
        canvas.drawText("Sıradaki Zorluk Seviyesi: Seviye " + level, w * 0.5f, cy - 10f * dp, paint);

        btnModalPrimaryRect.set(w * 0.16f, cy + 18f * dp, w * 0.84f, cy + 68f * dp);
        btnModalLobbyRect.set(w * 0.16f, cy + 80f * dp, w * 0.84f, cy + 126f * dp);

        paint.setColor(0xFF16A34A);
        canvas.drawRoundRect(btnModalPrimaryRect, 12f * dp, 12f * dp, paint);
        paint.setColor(0xFF475569);
        canvas.drawRoundRect(btnModalLobbyRect, 12f * dp, 12f * dp, paint);

        paint.setColor(0xFFFFFFFF);
        paint.setTextSize(15.5f * sp);
        canvas.drawText(levelCleared ? ("▶️ Seviye " + level + " Yarışına Başla") : "🔄 Bu Seviyeyi Tekrar Dene", btnModalPrimaryRect.centerX(), btnModalPrimaryRect.centerY() + 5f * dp, paint);
        canvas.drawText("🏠 Oyun Lobisine Dön", btnModalLobbyRect.centerX(), btnModalLobbyRect.centerY() + 5f * dp, paint);
    }

    private void updateRacePhysics(int w, int h, float hudH, float roadLeft, float laneW) {
        animTick += 0.25f;
        if (invulnerableFrames > 0) invulnerableFrames--;
        if (nitroActiveFrames > 0) nitroActiveFrames--;

        float baseSpeed = (4.8f + Math.min(5.5f, level * 0.42f)) * dp;
        float speed = (nitroActiveFrames > 0) ? (baseSpeed * 1.65f) : baseSpeed;
        float speedRatio = Math.min(1.45f, ((speed / dp) - 3.5f) / 10.0f);
        if (listener != null) listener.onSpeedChanged(speedRatio, level, true);
        roadScrollOffset += speed;
        raceDistance += (speed / dp) * 0.25f;

        if (raceDistance >= targetDistance) {
            score += 120 + level * 20;
            level++;
            StudyDatabaseHelper.saveGameLevel(getContext(), "car_race_level", level);
            if (score > highScore) {
                highScore = score;
                StudyDatabaseHelper.saveGameLevel(getContext(), "car_race_high_score", highScore + 1);
            }
            levelCleared = true;
            if (listener != null) {
                listener.onSpeedChanged(0f, level, false);
                listener.onRaceSound(true);
            }
            return;
        }

        // Yeni CPU rakip araç üretimi (Seviye yükseldikçe sıklaşır ve şerit değiştiren CPU araçları çıkar)
        float minGapY = Math.max(135f * dp, (230f - level * 8f) * dp);
        float highestY = h;
        for (CpuCar c : cpuCars) {
            if (c.y < highestY) highestY = c.y;
        }
        if (cpuCars.isEmpty() || highestY > hudH + minGapY) {
            CpuCar c = new CpuCar();
            c.lane = random.nextInt(3);
            c.x = roadLeft + (c.lane + 0.5f) * laneW;
            c.y = hudH - 45f * dp;
            c.speed = (2.2f + random.nextFloat() * 1.6f + Math.min(3.0f, level * 0.22f)) * dp;
            int[] cols = new int[]{0xFF3B82F6, 0xFFEAB308, 0xFF9333EA, 0xFF10B981};
            String[] ics = new String[]{"🚓", "🚕", "🚙", "🚛"};
            int pick = random.nextInt(cols.length);
            c.color = cols[pick];
            c.icon = ics[pick];
            c.style = pick;
            c.canSwitchLane = (level >= 3 && random.nextInt(100) < (20 + level * 6));
            cpuCars.add(c);
        }

        // Yoldaki Ödül Üretimi
        if (pickups.isEmpty() && random.nextInt(100) < 6) {
            RoadPick rp = new RoadPick();
            rp.lane = random.nextInt(3);
            rp.y = hudH - 30f * dp;
            int roll = random.nextInt(100);
            rp.type = (roll < 65) ? 0 : (roll < 88 ? 1 : 2);
            pickups.add(rp);
        }

        float playerY = h - 145f * dp;

        Iterator<RoadPick> pIt = pickups.iterator();
        while (pIt.hasNext()) {
            RoadPick rp = pIt.next();
            rp.y += speed * 0.85f;
            float rx = roadLeft + (rp.lane + 0.5f) * laneW;
            if (Math.abs(rx - playerVisualX) < 34f * dp && Math.abs(rp.y - playerY) < 44f * dp) {
                if (rp.type == 0) score += 25;
                else if (rp.type == 1) { nitroFuel++; score += 20; }
                else if (lives < 3) lives++;
                if (listener != null) listener.onRaceSound(true);
                pIt.remove();
            } else if (rp.y > h + 40f * dp) {
                pIt.remove();
            }
        }

        Iterator<CpuCar> cIt = cpuCars.iterator();
        while (cIt.hasNext()) {
            CpuCar c = cIt.next();
            c.y += (speed - c.speed * 0.45f);
            if (c.canSwitchLane && c.y > hudH + 70f * dp && c.y < playerY - 120f * dp) {
                c.canSwitchLane = false;
                if (c.lane == 0) c.lane = 1;
                else if (c.lane == 2) c.lane = 1;
                else c.lane = random.nextBoolean() ? 0 : 2;
            }
            float targetCx = roadLeft + (c.lane + 0.5f) * laneW;
            c.x += (targetCx - c.x) * 0.16f;

            if (Math.abs(c.x - playerVisualX) < 38f * dp && Math.abs(c.y - playerY) < 62f * dp && invulnerableFrames == 0) {
                invulnerableFrames = 45;
                lives--;
                if (listener != null) listener.onCrashSound();
                cIt.remove();
                if (lives <= 0) {
                    lives = 0;
                    gameOver = true;
                    if (listener != null) listener.onSpeedChanged(0f, level, false);
                }
            } else if (c.y > h + 60f * dp) {
                score += 10 + level * 2;
                if (score > highScore) {
                    highScore = score;
                    StudyDatabaseHelper.saveGameLevel(getContext(), "car_race_high_score", highScore + 1);
                }
                if (listener != null && random.nextInt(3) == 0) listener.onOvertakeSound();
                cIt.remove();
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            float tx = event.getX();
            float ty = event.getY();

            if (btnExitRect.contains(tx, ty)) {
                if (listener != null) listener.onExitRequested();
                return true;
            }

            if (levelCleared || gameOver) {
                if (btnModalPrimaryRect.contains(tx, ty)) {
                    setupRaceLevel(level);
                    scheduleNext();
                    invalidate();
                    return true;
                }
                if (btnModalLobbyRect.contains(tx, ty)) {
                    if (listener != null) listener.onExitRequested();
                    return true;
                }
                return true;
            }

            if (inCountdown) return true;

            if (btnLeftSteerRect.contains(tx, ty)) {
                if (playerLane > 0) { playerLane--; if (listener != null) listener.onLaneSwitch(); }
                invalidate();
                return true;
            }
            if (btnRightSteerRect.contains(tx, ty)) {
                if (playerLane < 2) { playerLane++; if (listener != null) listener.onLaneSwitch(); }
                invalidate();
                return true;
            }
            if (btnNitroRect.contains(tx, ty)) {
                if (nitroFuel > 0 && nitroActiveFrames == 0) {
                    nitroFuel--;
                    nitroActiveFrames = 75;
                    if (listener != null) listener.onNitroBoost();
                }
                invalidate();
                return true;
            }

            // Yola doğrudan dokunarak da sağ/sol şerit değiştirebilir
            if (tx < getWidth() * 0.5f) {
                if (playerLane > 0) { playerLane--; if (listener != null) listener.onLaneSwitch(); }
            } else {
                if (playerLane < 2) { playerLane++; if (listener != null) listener.onLaneSwitch(); }
            }
            invalidate();
            return true;
        }
        return super.onTouchEvent(event);
    }
}
