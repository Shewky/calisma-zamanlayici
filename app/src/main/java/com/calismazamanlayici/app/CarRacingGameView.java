package com.calismazamanlayici.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
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
            frameScheduled = false;
            if (running && !gameOver && !levelCleared) {
                invalidate();
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
            postDelayed(loopRunnable, 16L);
        }
    }

    public void startGame() {
        this.level = StudyDatabaseHelper.getSavedGameLevel(getContext(), "car_race_level", 1);
        setupRaceLevel(this.level);
        running = true;
        scheduleNext();
        invalidate();
    }

    public void stopGame() {
        running = false;
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

        // CPU Rakip Araçlar
        for (CpuCar c : cpuCars) {
            paint.setColor(c.color);
            RectF cr = new RectF(c.x - 24f * dp, c.y - 38f * dp, c.x + 24f * dp, c.y + 38f * dp);
            canvas.drawRoundRect(cr, 10f * dp, 10f * dp, paint);
            paint.setColor(0xFF0F172A);
            canvas.drawRoundRect(new RectF(c.x - 18f * dp, c.y - 18f * dp, c.x + 18f * dp, c.y + 12f * dp), 6f * dp, 6f * dp, paint);
            paint.setColor(0xFFFFFFFF);
            paint.setTextSize(20f * sp);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(c.icon, c.x, c.y + 8f * dp, paint);
        }

        // Oyuncu Yarış Arabası
        float targetPlayerX = roadLeft + (playerLane + 0.5f) * laneW;
        if (playerVisualX == 0f) playerVisualX = targetPlayerX;
        playerVisualX += (targetPlayerX - playerVisualX) * 0.28f;
        float playerY = h - 145f * dp;

        if (invulnerableFrames % 4 < 2) {
            if (nitroActiveFrames > 0) {
                paint.setColor(0xFFFF6D00);
                canvas.drawCircle(playerVisualX, playerY + 44f * dp, 14f * dp, paint);
                paint.setColor(0xFFFFD600);
                canvas.drawCircle(playerVisualX, playerY + 48f * dp, 8f * dp, paint);
            }
            paint.setColor(0xFFEF4444);
            RectF pr = new RectF(playerVisualX - 26f * dp, playerY - 40f * dp, playerVisualX + 26f * dp, playerY + 40f * dp);
            canvas.drawRoundRect(pr, 12f * dp, 12f * dp, paint);
            paint.setColor(0xFFFFFFFF);
            canvas.drawRect(playerVisualX - 5f * dp, playerY - 38f * dp, playerVisualX + 5f * dp, playerY + 38f * dp, paint);
            paint.setTextSize(22f * sp);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("🏎️", playerVisualX, playerY + 8f * dp, paint);
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
        } else if (running) {
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
        if (invulnerableFrames > 0) invulnerableFrames--;
        if (nitroActiveFrames > 0) nitroActiveFrames--;

        float baseSpeed = (4.8f + Math.min(5.5f, level * 0.42f)) * dp;
        float speed = (nitroActiveFrames > 0) ? (baseSpeed * 1.65f) : baseSpeed;
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
            if (listener != null) listener.onRaceSound(true);
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
                if (listener != null) listener.onRaceSound(false);
                cIt.remove();
                if (lives <= 0) {
                    lives = 0;
                    gameOver = true;
                }
            } else if (c.y > h + 60f * dp) {
                score += 10 + level * 2;
                if (score > highScore) {
                    highScore = score;
                    StudyDatabaseHelper.saveGameLevel(getContext(), "car_race_high_score", highScore + 1);
                }
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
                if (playerLane > 0) playerLane--;
                invalidate();
                return true;
            }
            if (btnRightSteerRect.contains(tx, ty)) {
                if (playerLane < 2) playerLane++;
                invalidate();
                return true;
            }
            if (btnNitroRect.contains(tx, ty)) {
                if (nitroFuel > 0 && nitroActiveFrames == 0) {
                    nitroFuel--;
                    nitroActiveFrames = 75;
                    if (listener != null) listener.onRaceSound(true);
                }
                invalidate();
                return true;
            }

            // Yola doğrudan dokunarak da sağ/sol şerit değiştirebilir
            if (tx < getWidth() * 0.5f) {
                if (playerLane > 0) playerLane--;
            } else {
                if (playerLane < 2) playerLane++;
            }
            invalidate();
            return true;
        }
        return super.onTouchEvent(event);
    }
}
