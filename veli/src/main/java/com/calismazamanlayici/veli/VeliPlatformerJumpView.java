package com.calismazamanlayici.veli;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
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
 * Cep Telefonu Ekranı İçin Tam Ölçeklendirilmiş (DP / SP Uyumlu), Tam Ekran,
 * 3-2-1-BAŞLA! Geri Sayımlı ve 6 Farklı Parkurlu "Süper Yıldız Zıplama Macerası":
 * - Tüm yazılar, karakter, yıldızlar, platformlar, engeller ve fizik hızları telefon ekran yoğunluğuna (dp/sp)
 *   göre ölçeklendirilir; hiçbir öğe küçük kalmaz.
 * - Her bölüme başlarken ekranda büyük "3 - 2 - 1 - BAŞLA!" geri sayımı yapılır.
 */
public class VeliPlatformerJumpView extends View {

    public interface JumpGameListener {
        void onStatsUpdated(int score, int stage, String stageName, int lives);
        void onJumpTriggered();
        void onStarCollected();
        void onObstacleHit();
        void onCountdownTick(boolean isGo);
        void onExitRequested();
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path tempPath = new Path();
    private final Random random = new Random();
    private final JumpGameListener listener;

    private final float dp;
    private final float sp;

    private boolean running = false;
    private boolean gameOver = false;

    // 3-2-1-BAŞLA geri sayımı
    private boolean inCountdown = false;
    private long countdownStartRealtimeMs = 0L;
    private int lastAnnouncedCountdownSecond = -1;

    private int score = 0;
    private int highScore = 0;
    private int stage = 1; // 1..6 Parkur
    private int lives = 3;
    private boolean hasShield = false;
    private int magnetFrames = 0;
    private int invulnerableFrames = 0;

    // Parkur ilerleme mesafesi (dp ölçekli)
    private float parkourDistanceDp = 0f;
    private static final float PARKOUR_TARGET_DISTANCE_DP = 950f;
    private boolean finishGateSpawned = false;
    private float finishGateX = -500f;

    // Karakter fizik değişkenleri (dp ölçekli)
    private float playerX;
    private float playerY;
    private float playerVy = 0f;
    private final float playerRadius;
    private int jumpCount = 0;
    private float groundY;
    private float scrollSpeed;
    private float animTick = 0f;
    private final float[] trailX = new float[7];
    private final float[] trailY = new float[7];

    private static class Platform {
        float x;
        float y;
        float baseY;
        float width;
        float height;
        boolean movingVertically;
        boolean movingHorizontally;
        float movePhase;
        boolean hasTrampoline;
        float trampolineOffsetX;
    }

    private static class GroundPit {
        float x;
        float width;
        int pitType; // 0: Nehir, 1: Lav, 2: Gökyüzü Boşluğu
    }

    private static class Obstacle {
        float x;
        float y;
        float baseY;
        float width;
        float height;
        int type; // 0: Diken, 1: Salınımlı Dron/Ateş Topu, 2: Yuvarlanan Kaya, 3: Üst Sarkıt
        float phase;
    }

    private static class Collectible {
        float x;
        float y;
        int type; // 0: Altın Yıldız (+15), 1: Süper Elmas (+30), 2: Kalkan (🛡️), 3: Can Kalbi (❤️), 4: Mıknatıs (🧲)
    }

    private static class AmbientParticle {
        float x, y, vx, vy, size;
    }

    private static class Sparkle {
        float x, y, vx, vy;
        int color;
        int life;
    }

    private final List<Platform> platforms = new ArrayList<>();
    private final List<GroundPit> pits = new ArrayList<>();
    private final List<Obstacle> obstacles = new ArrayList<>();
    private final List<Collectible> collectibles = new ArrayList<>();
    private final List<AmbientParticle> ambientParticles = new ArrayList<>();
    private final List<Sparkle> sparkles = new ArrayList<>();

    private final RectF btnExitRect = new RectF();
    private final RectF[] parkourTabRects = new RectF[6];
    private final RectF btnRetryRect = new RectF();
    private final RectF btnNextParkourRect = new RectF();
    private final RectF btnLobbyOnGameOverRect = new RectF();

    private static final String[] STAGE_NAMES = new String[]{
            "Zümrüt Orman Köprüleri",
            "Kayan Bulut Adaları",
            "Lav Mağarası & Trambolin",
            "Kristal Buzul Tepeleri",
            "Uzay İstasyonu Lazerleri",
            "Altın Tapınak Piramidi"
    };

    private static final String[] SHORT_TAB_NAMES = new String[]{
            "1.Orman", "2.Bulut", "3.Lav", "4.Buzul", "5.Uzay", "6.Tapınak"
    };

    private static final int[] SKY_COLORS = new int[]{
            0xFF4FC3F7, // 1: Zümrüt Orman
            0xFF29B6F6, // 2: Bulut Adaları
            0xFF2D1612, // 3: Lav Mağarası
            0xFFB3E5FC, // 4: Kristal Buzul
            0xFF0B132B, // 5: Uzay İstasyonu
            0xFFFFE082  // 6: Altın Tapınak
    };

    private static final int[] GROUND_COLORS = new int[]{
            0xFF2E7D32, // 1: Orman Çimeni
            0xFF0277BD, // 2: Gökyüzü Kristal Zemin
            0xFF4E342E, // 3: Volkanik Kaya
            0xFF039BE5, // 4: Buzul Zemini
            0xFF263238, // 5: Metalik Uzay Güvertesi
            0xFFD4AF37  // 6: Altın Tapınak Taşı
    };

    private static final int[] PLATFORM_COLORS = new int[]{
            0xFF795548, // 1: Ahşap Köprü
            0xFFFFFFFF, // 2: Beyaz Bulut Platform
            0xFF8D6E63, // 3: Bazalt Taş Köprü
            0xFFE1F5FE, // 4: Parlak Buz Kütleleri
            0xFF00E5FF, // 5: Neon Siber Platform
            0xFFFFB300  // 6: Altın Basamaklar
    };

    public VeliPlatformerJumpView(Context context, JumpGameListener listener) {
        super(context);
        this.listener = listener;
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        this.dp = dm.density;
        this.sp = dm.scaledDensity;
        this.playerRadius = 23f * dp; // 46dp çapında büyük, net karakter!
        this.playerX = 72f * dp;
        this.groundY = 480f * dp;
        this.playerY = this.groundY - this.playerRadius;
        this.scrollSpeed = 3.2f * dp;

        for (int i = 0; i < 6; i++) {
            parkourTabRects[i] = new RectF();
        }
        resetGame();
    }

    public void resetGame() {
        score = 0;
        lives = 3;
        gameOver = false;
        hasShield = false;
        magnetFrames = 0;
        invulnerableFrames = 0;
        loadParkourStage(stage);
    }

    public void selectParkour(int newStage) {
        if (newStage < 1 || newStage > 6) newStage = 1;
        this.stage = newStage;
        if (gameOver) {
            lives = 3;
            score = 0;
            gameOver = false;
        }
        loadParkourStage(this.stage);
        running = true;
        invalidate();
    }

    private void loadParkourStage(int targetStage) {
        this.stage = ((targetStage - 1) % 6) + 1;
        this.parkourDistanceDp = 0f;
        this.finishGateSpawned = false;
        this.finishGateX = -500f * dp;
        this.jumpCount = 0;
        this.playerVy = 0f;
        float gY = (getHeight() > 200) ? (getHeight() * 0.80f) : (460f * dp);
        this.groundY = gY;
        this.playerY = gY - playerRadius;
        this.invulnerableFrames = 45;

        for (int i = 0; i < trailX.length; i++) {
            trailX[i] = playerX;
            trailY[i] = playerY;
        }

        platforms.clear();
        pits.clear();
        obstacles.clear();
        collectibles.clear();
        ambientParticles.clear();
        sparkles.clear();

        int w = Math.max(getWidth(), (int) (360 * dp));
        int h = Math.max(getHeight(), (int) (640 * dp));
        for (int i = 0; i < 16; i++) {
            AmbientParticle ap = new AmbientParticle();
            ap.x = random.nextInt(w);
            ap.y = 110f * dp + random.nextInt(Math.max(100, (int) (h * 0.65f)));
            ap.size = (3.5f + random.nextFloat() * 4.5f) * dp;
            ap.vx = -(0.8f + random.nextFloat() * 1.4f) * dp;
            ap.vy = ((stage == 3) ? -(0.5f + random.nextFloat() * 0.9f) : (0.4f + random.nextFloat() * 0.9f)) * dp;
            ambientParticles.add(ap);
        }

        spawnParkourChunk(220f * dp);
        spawnParkourChunk(520f * dp);

        inCountdown = true;
        countdownStartRealtimeMs = SystemClock.elapsedRealtime();
        lastAnnouncedCountdownSecond = -1;

        notifyStats();
    }

    public void startGame() {
        if (gameOver) {
            resetGame();
        } else {
            inCountdown = true;
            countdownStartRealtimeMs = SystemClock.elapsedRealtime();
            lastAnnouncedCountdownSecond = -1;
        }
        running = true;
        invalidate();
    }

    public void stopGame() {
        running = false;
    }

    public void triggerJump() {
        if (gameOver) {
            resetGame();
            running = true;
            invalidate();
            return;
        }
        if (inCountdown) {
            return;
        }
        if (!running) {
            running = true;
            invalidate();
        }
        if (jumpCount < 2) {
            float boost = (stage == 5) ? 1.14f : (stage == 2 ? 1.07f : 1.0f);
            playerVy = ((jumpCount == 0) ? (-9.8f * dp) : (-8.6f * dp)) * boost;
            jumpCount++;
            spawnSparkles(playerX, playerY + playerRadius, 0xFFFFFFFF, 10);
            if (listener != null) {
                listener.onJumpTriggered();
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            float tx = event.getX();
            float ty = event.getY();

            if (btnExitRect.contains(tx, ty)) {
                if (listener != null) {
                    listener.onExitRequested();
                }
                return true;
            }

            for (int i = 0; i < 6; i++) {
                if (parkourTabRects[i].contains(tx, ty)) {
                    selectParkour(i + 1);
                    return true;
                }
            }

            if (gameOver) {
                if (btnNextParkourRect.contains(tx, ty)) {
                    selectParkour((stage % 6) + 1);
                    return true;
                }
                if (btnLobbyOnGameOverRect.contains(tx, ty)) {
                    if (listener != null) {
                        listener.onExitRequested();
                    }
                    return true;
                }
                resetGame();
                running = true;
                invalidate();
                return true;
            }

            triggerJump();
            return true;
        }
        return super.onTouchEvent(event);
    }

    private void spawnSparkles(float cx, float cy, int color, int count) {
        for (int i = 0; i < count; i++) {
            Sparkle s = new Sparkle();
            s.x = cx;
            s.y = cy;
            double ang = random.nextDouble() * Math.PI * 2;
            float spd = (1.8f + random.nextFloat() * 4.2f) * dp;
            s.vx = (float) Math.cos(ang) * spd;
            s.vy = (float) Math.sin(ang) * spd;
            s.color = color;
            s.life = 18;
            sparkles.add(s);
        }
    }

    private void notifyStats() {
        if (score > highScore) {
            highScore = score;
        }
        if (listener != null) {
            String stName = STAGE_NAMES[(stage - 1) % STAGE_NAMES.length];
            listener.onStatsUpdated(score, stage, stName, lives);
        }
    }

    private void spawnParkourChunk(float startX) {
        if (finishGateSpawned) return;
        float gY = (groundY > 150f * dp) ? groundY : (460f * dp);
        int pattern = random.nextInt(3);

        float platH = 18f * dp;

        if (stage == 1) {
            // Parkur 1: Zümrüt Orman Köprüleri
            if (pattern == 0) {
                GroundPit pit = new GroundPit();
                pit.x = startX + 25f * dp;
                pit.width = 95f * dp;
                pit.pitType = 0;
                pits.add(pit);

                Platform pLow = new Platform();
                pLow.x = startX + 10f * dp;
                pLow.baseY = gY - 82f * dp;
                pLow.y = pLow.baseY;
                pLow.width = 125f * dp;
                pLow.height = platH;
                platforms.add(pLow);

                Platform pHigh = new Platform();
                pHigh.x = startX + 145f * dp;
                pHigh.baseY = gY - 160f * dp;
                pHigh.y = pHigh.baseY;
                pHigh.width = 115f * dp;
                pHigh.height = platH;
                platforms.add(pHigh);

                spawnStarArc(startX + 25f * dp, pLow.baseY - 36f * dp, 3, 36f * dp, 0);
                spawnStarArc(startX + 160f * dp, pHigh.baseY - 36f * dp, 3, 34f * dp, 1);
            } else if (pattern == 1) {
                Platform p1 = new Platform();
                p1.x = startX;
                p1.baseY = gY - 75f * dp;
                p1.y = p1.baseY;
                p1.width = 110f * dp;
                p1.height = platH;
                p1.hasTrampoline = true;
                p1.trampolineOffsetX = 32f * dp;
                platforms.add(p1);

                Platform pSky = new Platform();
                pSky.x = startX + 130f * dp;
                pSky.baseY = gY - 190f * dp;
                pSky.y = pSky.baseY;
                pSky.width = 130f * dp;
                pSky.height = platH;
                platforms.add(pSky);

                spawnStarArc(startX + 145f * dp, pSky.baseY - 38f * dp, 3, 36f * dp, 4);
            } else {
                Obstacle ob = new Obstacle();
                ob.x = startX + 55f * dp;
                ob.width = 38f * dp;
                ob.height = 46f * dp;
                ob.baseY = gY - ob.height;
                ob.y = ob.baseY;
                ob.type = 2;
                obstacles.add(ob);
                spawnStarArc(startX + 35f * dp, gY - 105f * dp, 3, 36f * dp, 0);
            }
        } else if (stage == 2) {
            // Parkur 2: Kayan Bulut Adaları
            GroundPit pit = new GroundPit();
            pit.x = startX + 20f * dp;
            pit.width = 120f * dp;
            pit.pitType = 2;
            pits.add(pit);

            Platform c1 = new Platform();
            c1.x = startX + 8f * dp;
            c1.baseY = gY - 90f * dp;
            c1.y = c1.baseY;
            c1.width = 120f * dp;
            c1.height = 20f * dp;
            c1.movingVertically = true;
            c1.movePhase = random.nextFloat() * 6.28f;
            platforms.add(c1);

            Platform c2 = new Platform();
            c2.x = startX + 145f * dp;
            c2.baseY = gY - 175f * dp;
            c2.y = c2.baseY;
            c2.width = 120f * dp;
            c2.height = 20f * dp;
            c2.movingHorizontally = true;
            c2.movePhase = random.nextFloat() * 6.28f;
            platforms.add(c2);

            if (pattern == 1) {
                Obstacle storm = new Obstacle();
                storm.x = startX + 135f * dp;
                storm.width = 38f * dp;
                storm.height = 38f * dp;
                storm.baseY = gY - 95f * dp;
                storm.y = storm.baseY;
                storm.type = 1;
                storm.phase = random.nextFloat() * 6.28f;
                obstacles.add(storm);
            }
            spawnStarArc(startX + 155f * dp, c2.baseY - 38f * dp, 3, 36f * dp, 1);
        } else if (stage == 3) {
            // Parkur 3: Lav Mağarası & Trambolin
            GroundPit lava = new GroundPit();
            lava.x = startX + 40f * dp;
            lava.width = 125f * dp;
            lava.pitType = 1;
            pits.add(lava);

            Platform launchPad = new Platform();
            launchPad.x = startX - 15f * dp;
            launchPad.baseY = gY - 60f * dp;
            launchPad.y = launchPad.baseY;
            launchPad.width = 80f * dp;
            launchPad.height = platH;
            launchPad.hasTrampoline = true;
            launchPad.trampolineOffsetX = 18f * dp;
            platforms.add(launchPad);

            Platform upperBridge = new Platform();
            upperBridge.x = startX + 90f * dp;
            upperBridge.baseY = gY - 175f * dp;
            upperBridge.y = upperBridge.baseY;
            upperBridge.width = 130f * dp;
            upperBridge.height = platH;
            platforms.add(upperBridge);

            Obstacle fireball = new Obstacle();
            fireball.x = startX + 100f * dp;
            fireball.width = 36f * dp;
            fireball.height = 36f * dp;
            fireball.baseY = gY - 75f * dp;
            fireball.y = fireball.baseY;
            fireball.type = 1;
            fireball.phase = random.nextFloat() * 6.28f;
            obstacles.add(fireball);

            spawnStarArc(startX + 105f * dp, upperBridge.baseY - 38f * dp, 3, 36f * dp, 2);
        } else if (stage == 4) {
            // Parkur 4: Kristal Buzul Tepeleri
            Platform ice1 = new Platform();
            ice1.x = startX;
            ice1.baseY = gY - 80f * dp;
            ice1.y = ice1.baseY;
            ice1.width = 115f * dp;
            ice1.height = platH;
            platforms.add(ice1);

            Platform ice2 = new Platform();
            ice2.x = startX + 125f * dp;
            ice2.baseY = gY - 155f * dp;
            ice2.y = ice2.baseY;
            ice2.width = 120f * dp;
            ice2.height = platH;
            platforms.add(ice2);

            if (pattern == 0) {
                Obstacle spike = new Obstacle();
                spike.x = startX + 55f * dp;
                spike.width = 38f * dp;
                spike.height = 46f * dp;
                spike.baseY = gY - spike.height;
                spike.y = spike.baseY;
                spike.type = 0;
                obstacles.add(spike);
            } else {
                Obstacle icicle = new Obstacle();
                icicle.x = startX + 145f * dp;
                icicle.width = 36f * dp;
                icicle.height = 55f * dp;
                icicle.baseY = gY - 250f * dp;
                icicle.y = icicle.baseY;
                icicle.type = 3;
                obstacles.add(icicle);
            }
            spawnStarArc(startX + 135f * dp, ice2.baseY - 38f * dp, 3, 36f * dp, 1);
        } else if (stage == 5) {
            // Parkur 5: Uzay İstasyonu & Lazer Koridoru
            Platform deck1 = new Platform();
            deck1.x = startX + 10f * dp;
            deck1.baseY = gY - 100f * dp;
            deck1.y = deck1.baseY;
            deck1.width = 125f * dp;
            deck1.height = platH;
            deck1.movingVertically = true;
            deck1.movePhase = random.nextFloat() * 6.28f;
            platforms.add(deck1);

            Platform deck2 = new Platform();
            deck2.x = startX + 150f * dp;
            deck2.baseY = gY - 190f * dp;
            deck2.y = deck2.baseY;
            deck2.width = 125f * dp;
            deck2.height = platH;
            platforms.add(deck2);

            Obstacle drone = new Obstacle();
            drone.x = startX + 95f * dp;
            drone.width = 38f * dp;
            drone.height = 38f * dp;
            drone.baseY = (pattern == 0) ? (gY - 55f * dp) : (gY - 145f * dp);
            drone.y = drone.baseY;
            drone.type = 1;
            drone.phase = random.nextFloat() * 6.28f;
            obstacles.add(drone);

            spawnStarArc(startX + 160f * dp, deck2.baseY - 38f * dp, 3, 36f * dp, 4);
        } else {
            // Parkur 6: Altın Tapınak Piramidi
            for (int step = 0; step < 3; step++) {
                Platform stair = new Platform();
                stair.x = startX + step * 82f * dp;
                stair.baseY = gY - (65f + step * 58f) * dp;
                stair.y = stair.baseY;
                stair.width = 78f * dp;
                stair.height = platH;
                if (step == 2) {
                    stair.hasTrampoline = true;
                    stair.trampolineOffsetX = 18f * dp;
                }
                platforms.add(stair);
            }
            Obstacle guard = new Obstacle();
            guard.x = startX + 105f * dp;
            guard.width = 38f * dp;
            guard.height = 44f * dp;
            guard.baseY = gY - guard.height;
            guard.y = guard.baseY;
            guard.type = 2;
            obstacles.add(guard);

            spawnStarArc(startX + 20f * dp, gY - 200f * dp, 3, 38f * dp, 1);
        }

        if (lives < 3 && random.nextInt(100) < 30) {
            Collectible heart = new Collectible();
            heart.x = startX + 75f * dp;
            heart.y = gY - 125f * dp;
            heart.type = 3;
            collectibles.add(heart);
        }
    }

    private void spawnStarArc(float startX, float y, int count, float spacing, int preferredType) {
        for (int i = 0; i < count; i++) {
            Collectible c = new Collectible();
            c.x = startX + i * spacing;
            c.y = y - (i == 1 ? 15f * dp : 0f);
            c.type = (i == count - 1) ? preferredType : 0;
            collectibles.add(c);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float topHudHeight = 98f * dp;
        groundY = h * 0.82f;
        playerX = w * 0.22f;

        int stageIdx = (stage - 1) % SKY_COLORS.length;

        // 1. Gökyüzü ve Paralaks Dağ Tepeleri
        canvas.drawColor(SKY_COLORS[stageIdx]);

        paint.setColor(0x22000000);
        float hillSpan = 320f * dp;
        for (int i = 0; i < 4; i++) {
            float hillX = ((i * hillSpan - (parkourDistanceDp * 0.4f * dp) % (hillSpan * 4)) + (hillSpan * 4)) % (hillSpan * 4) - 120f * dp;
            canvas.drawCircle(hillX, groundY + 30f * dp, 160f * dp, paint);
        }

        // Atmosfer Parçacıkları
        paint.setColor(stage == 3 ? 0xAAFF6D00 : (stage == 4 ? 0xDDFFFFFF : 0x88FFFFFF));
        for (AmbientParticle ap : ambientParticles) {
            canvas.drawCircle(ap.x, ap.y, ap.size, paint);
            if (running && !gameOver && !inCountdown) {
                ap.x += ap.vx;
                ap.y += ap.vy;
                if (ap.x < -20f * dp) ap.x = w + 20f * dp;
                if (ap.y < topHudHeight) ap.y = groundY - 20f * dp;
                if (ap.y > groundY) ap.y = topHudHeight + 20f * dp;
            }
        }

        // 2. Zemin ve Çukurlar
        paint.setColor(GROUND_COLORS[stageIdx]);
        canvas.drawRect(0, groundY, w, h, paint);
        paint.setColor(0x44FFFFFF);
        canvas.drawRect(0, groundY, w, groundY + 6f * dp, paint);

        for (GroundPit pit : pits) {
            paint.setColor(SKY_COLORS[stageIdx]);
            canvas.drawRect(pit.x, groundY - 2f * dp, pit.x + pit.width, h, paint);
            if (pit.pitType == 0) {
                paint.setColor(0xFF0288D1);
                canvas.drawRect(pit.x, groundY + 14f * dp, pit.x + pit.width, h, paint);
            } else if (pit.pitType == 1) {
                paint.setColor(0xFFFF3D00);
                canvas.drawRect(pit.x, groundY + 12f * dp, pit.x + pit.width, h, paint);
                paint.setColor(0xFFFFD600);
                canvas.drawRect(pit.x + 6f * dp, groundY + 12f * dp, pit.x + pit.width - 6f * dp, groundY + 20f * dp, paint);
            }
        }

        // 3. 3-2-1-BAŞLA! Geri Sayımı veya Fizik Güncellemesi
        int currentCountdownStep = 0;
        if (inCountdown) {
            long elapsed = SystemClock.elapsedRealtime() - countdownStartRealtimeMs;
            if (elapsed < 850L) {
                currentCountdownStep = 3;
            } else if (elapsed < 1700L) {
                currentCountdownStep = 2;
            } else if (elapsed < 2550L) {
                currentCountdownStep = 1;
            } else if (elapsed < 3150L) {
                currentCountdownStep = 0;
            } else {
                inCountdown = false;
            }

            if (inCountdown && currentCountdownStep != lastAnnouncedCountdownSecond) {
                lastAnnouncedCountdownSecond = currentCountdownStep;
                if (listener != null) {
                    listener.onCountdownTick(currentCountdownStep == 0);
                }
            }
        } else if (running && !gameOver) {
            updatePhysicsAndSpawns(w, h, topHudHeight);
        }

        // 4. Platformlar ve Trambolinler
        for (Platform p : platforms) {
            paint.setColor(PLATFORM_COLORS[stageIdx]);
            RectF pr = new RectF(p.x, p.y, p.x + p.width, p.y + p.height);
            canvas.drawRoundRect(pr, 9f * dp, 9f * dp, paint);
            paint.setColor(0x44000000);
            canvas.drawRoundRect(new RectF(p.x, p.y + p.height - 5f * dp, p.x + p.width, p.y + p.height), 5f * dp, 5f * dp, paint);

            if (p.hasTrampoline) {
                float tx = p.x + p.trampolineOffsetX;
                float tw = 42f * dp;
                float th = 13f * dp;
                paint.setColor(0xFFFF1744);
                canvas.drawRoundRect(new RectF(tx, p.y - th, tx + tw, p.y), 6f * dp, 6f * dp, paint);
                paint.setColor(0xFFFFFFFF);
                canvas.drawCircle(tx + tw * 0.25f, p.y - th * 0.5f, 3f * dp, paint);
                canvas.drawCircle(tx + tw * 0.50f, p.y - th * 0.6f, 3f * dp, paint);
                canvas.drawCircle(tx + tw * 0.75f, p.y - th * 0.5f, 3f * dp, paint);
            }
        }

        // 5. Parkur Bitiş Kapısı (🏁)
        if (finishGateSpawned && finishGateX > -120f * dp) {
            paint.setColor(0xFFFFD600);
            canvas.drawRect(finishGateX, groundY - 140f * dp, finishGateX + 12f * dp, groundY, paint);
            canvas.drawRect(finishGateX + 64f * dp, groundY - 140f * dp, finishGateX + 76f * dp, groundY, paint);
            paint.setColor(0xFF00E676);
            canvas.drawRoundRect(new RectF(finishGateX - 10f * dp, groundY - 168f * dp, finishGateX + 86f * dp, groundY - 128f * dp), 10f * dp, 10f * dp, paint);
            paint.setColor(0xFF0F172A);
            paint.setTextSize(16f * sp);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setFakeBoldText(true);
            canvas.drawText("🏁 BİTİŞ", finishGateX + 38f * dp, groundY - 142f * dp, paint);
        }

        // 6. Toplanabilir Yıldız, Elmas, Kalkan, Mıknatıs, Kalp (Telefon ekranı için büyük boyutlu)
        float colRadius = 17f * dp;
        for (Collectible col : collectibles) {
            if (col.type == 0) {
                paint.setColor(0xFFFFD600);
                canvas.drawCircle(col.x, col.y, colRadius, paint);
                paint.setColor(0xFFE65100);
                paint.setTextSize(19f * sp);
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("★", col.x, col.y + 7f * dp, paint);
            } else if (col.type == 1) {
                paint.setColor(0xFF00E5FF);
                canvas.drawCircle(col.x, col.y, colRadius + 2f * dp, paint);
                paint.setColor(0xFF004D40);
                paint.setTextSize(18f * sp);
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("💎", col.x, col.y + 7f * dp, paint);
            } else if (col.type == 2) {
                paint.setColor(0xFF69F0AE);
                canvas.drawCircle(col.x, col.y, colRadius + 2f * dp, paint);
                paint.setColor(0xFF1B5E20);
                paint.setTextSize(18f * sp);
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("🛡️", col.x, col.y + 7f * dp, paint);
            } else if (col.type == 4) {
                paint.setColor(0xFFEA80FC);
                canvas.drawCircle(col.x, col.y, colRadius + 2f * dp, paint);
                paint.setColor(0xFF4A148C);
                paint.setTextSize(18f * sp);
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("🧲", col.x, col.y + 7f * dp, paint);
            } else {
                paint.setColor(0xFFFFCDD2);
                canvas.drawCircle(col.x, col.y, colRadius + 2f * dp, paint);
                paint.setColor(0xFFC62828);
                paint.setTextSize(18f * sp);
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("❤️", col.x, col.y + 7f * dp, paint);
            }
        }

        // 7. Engeller
        for (Obstacle ob : obstacles) {
            if (ob.type == 0) {
                paint.setColor(stage == 4 ? 0xFF0288D1 : 0xFFD32F2F);
                tempPath.reset();
                tempPath.moveTo(ob.x, ob.y + ob.height);
                tempPath.lineTo(ob.x + ob.width * 0.5f, ob.y);
                tempPath.lineTo(ob.x + ob.width, ob.y + ob.height);
                tempPath.close();
                canvas.drawPath(tempPath, paint);
            } else if (ob.type == 1) {
                paint.setColor(stage == 3 ? 0xFFFF6D00 : (stage == 5 ? 0xFFAA00FF : 0xFFE53935));
                canvas.drawCircle(ob.x + ob.width * 0.5f, ob.y + ob.height * 0.5f, ob.width * 0.5f, paint);
                paint.setColor(0xFFFFEB3B);
                canvas.drawCircle(ob.x + ob.width * 0.5f, ob.y + ob.height * 0.5f, ob.width * 0.24f, paint);
            } else if (ob.type == 3) {
                paint.setColor(0xFF00ACC1);
                RectF r = new RectF(ob.x, ob.y, ob.x + ob.width, ob.y + ob.height);
                canvas.drawRoundRect(r, 6f * dp, 6f * dp, paint);
            } else {
                paint.setColor(0xFF5D4037);
                RectF r = new RectF(ob.x, ob.y, ob.x + ob.width, ob.y + ob.height);
                canvas.drawRoundRect(r, 10f * dp, 10f * dp, paint);
                paint.setColor(0xFFFFCA28);
                canvas.drawRect(ob.x + 5f * dp, ob.y + 6f * dp, ob.x + ob.width - 5f * dp, ob.y + 12f * dp, paint);
            }
        }

        // 8. Karakter Kuyruk İzi ve Büyük Net Karakter Çizimi
        for (int i = trailX.length - 1; i > 0; i--) {
            trailX[i] = trailX[i - 1] - scrollSpeed * 0.45f;
            trailY[i] = trailY[i - 1];
            paint.setColor(0x33FFAB40);
            float r = playerRadius * (1f - i * 0.12f);
            if (r > 2f * dp) {
                canvas.drawCircle(trailX[i], trailY[i], r, paint);
            }
        }
        trailX[0] = playerX;
        trailY[0] = playerY;

        if (invulnerableFrames % 4 < 2) {
            if (hasShield) {
                paint.setColor(0x6600E676);
                canvas.drawCircle(playerX, playerY, playerRadius + 10f * dp, paint);
            }
            if (magnetFrames > 0) {
                paint.setColor(0x55EA80FC);
                canvas.drawCircle(playerX, playerY, playerRadius + 15f * dp, paint);
            }
            // Karakter gövdesi
            paint.setColor(0xFFFF5722);
            canvas.drawCircle(playerX, playerY, playerRadius, paint);
            // Kahraman bandanası
            paint.setColor(0xFFFFD600);
            canvas.drawRect(playerX - playerRadius * 0.85f, playerY - playerRadius * 0.55f,
                    playerX + playerRadius * 0.85f, playerY - playerRadius * 0.25f, paint);
            // Göz akı ve göz bebeği
            paint.setColor(0xFFFFFFFF);
            canvas.drawCircle(playerX + playerRadius * 0.35f, playerY - playerRadius * 0.15f, 5.5f * dp, paint);
            paint.setColor(0xFF0F172A);
            canvas.drawCircle(playerX + playerRadius * 0.44f, playerY - playerRadius * 0.15f, 2.8f * dp, paint);
        }

        // 9. Parçacık Efektleri
        Iterator<Sparkle> spIt = sparkles.iterator();
        while (spIt.hasNext()) {
            Sparkle s = spIt.next();
            s.x += s.vx;
            s.y += s.vy;
            s.life--;
            paint.setColor(s.color);
            canvas.drawCircle(s.x, s.y, Math.max(2f * dp, s.life * 0.32f * dp), paint);
            if (s.life <= 0) {
                spIt.remove();
            }
        }

        // 10. Sağ Alt Köşede Telefon Ekranına Uygun Zıplama Butonu
        if (!gameOver && !inCountdown) {
            RectF jumpPad = new RectF(w - 168f * dp, h - 66f * dp, w - 12f * dp, h - 14f * dp);
            paint.setColor(0xAA0F172A);
            canvas.drawRoundRect(jumpPad, 14f * dp, 14f * dp, paint);
            paint.setColor(0xFFFFFFFF);
            paint.setTextSize(15f * sp);
            paint.setFakeBoldText(true);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("⬆️ ZIPLAMA (2x)", jumpPad.centerX(), jumpPad.centerY() + 5f * dp, paint);
        }

        // 11. Sabit Üst Gösterge Paneli (DP/SP Ölçekli)
        drawTopHudAndParkourTabs(canvas, w, topHudHeight);

        // 12. 3 - 2 - 1 - BAŞLA! Geri Sayım Katmanı (DP/SP Ölçekli)
        if (inCountdown) {
            drawCountdownOverlay(canvas, w, h, topHudHeight, currentCountdownStep);
        }

        // 13. Oyun Bitti Ekranı (DP/SP Ölçekli)
        if (gameOver) {
            drawGameOverOverlay(canvas, w, h);
        } else if (running) {
            postInvalidateDelayed(16L);
        }
    }

    private void drawCountdownOverlay(Canvas canvas, int w, int h, float topHudH, int step) {
        paint.setColor(0x880F172A);
        canvas.drawRect(0, topHudH, w, h, paint);

        float cx = w * 0.5f;
        float cy = h * 0.48f;

        RectF banner = new RectF(w * 0.08f, cy - 105f * dp, w * 0.92f, cy + 95f * dp);
        paint.setColor(0xEE1E293B);
        canvas.drawRoundRect(banner, 20f * dp, 20f * dp, paint);

        paint.setColor(0xFF38BDF8);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(17f * sp);
        paint.setFakeBoldText(true);
        String stName = STAGE_NAMES[(stage - 1) % STAGE_NAMES.length];
        canvas.drawText("🗺️ PARKUR " + stage + ": " + stName, cx, cy - 60f * dp, paint);

        if (step > 0) {
            paint.setColor(0xFFFFD600);
            paint.setTextSize(62f * sp);
            canvas.drawText(String.valueOf(step), cx, cy + 20f * dp, paint);
            paint.setColor(0xFFE2E8F0);
            paint.setTextSize(14f * sp);
            canvas.drawText("Hazır Ol! Ekrana dokunarak çift zıplayabilirsin", cx, cy + 66f * dp, paint);
        } else {
            paint.setColor(0xFF00E676);
            paint.setTextSize(42f * sp);
            canvas.drawText("BAŞLA! 🚀", cx, cy + 18f * dp, paint);
        }
    }

    private void drawTopHudAndParkourTabs(Canvas canvas, int w, float hudH) {
        paint.setColor(0xDD0F172A);
        canvas.drawRect(0, 0, w, hudH, paint);

        // Üst Satır: 6 Parkur Seçme Sekmesi
        float margin = 4f * dp;
        float tabW = (w - margin * 7f) / 6f;
        float tabTop = 6f * dp;
        float tabBottom = 42f * dp;
        for (int i = 0; i < 6; i++) {
            float left = margin + i * (tabW + margin);
            parkourTabRects[i].set(left, tabTop, left + tabW, tabBottom);
            boolean active = (stage == i + 1);
            paint.setColor(active ? 0xFFFF6D00 : 0xFF334155);
            canvas.drawRoundRect(parkourTabRects[i], 7f * dp, 7f * dp, paint);

            paint.setColor(0xFFFFFFFF);
            paint.setTextSize(11.5f * sp);
            paint.setFakeBoldText(active);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(SHORT_TAB_NAMES[i], parkourTabRects[i].centerX(), tabBottom - 11f * dp, paint);
        }

        // Alt Satır Sol: ⬅️ Çıkış Butonu + 3 Kalp (❤️❤️❤️)
        float row2Top = 50f * dp;
        float row2Bottom = 88f * dp;
        btnExitRect.set(6f * dp, row2Top, 82f * dp, row2Bottom);
        paint.setColor(0xFFDC2626);
        canvas.drawRoundRect(btnExitRect, 8f * dp, 8f * dp, paint);
        paint.setColor(0xFFFFFFFF);
        paint.setTextSize(13f * sp);
        paint.setFakeBoldText(true);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("⬅️ Çıkış", btnExitRect.centerX(), row2Bottom - 12f * dp, paint);

        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(15f * sp);
        StringBuilder heartsSb = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            heartsSb.append(i < lives ? "❤️" : "🖤");
        }
        if (hasShield) heartsSb.append("🛡️");
        if (magnetFrames > 0) heartsSb.append("🧲");
        canvas.drawText(heartsSb.toString(), 88f * dp, row2Bottom - 11f * dp, paint);

        // Alt Satır Orta: Parkur İlerleme Çubuğu (%0 -> %100 🏁)
        float barLeft = w * 0.48f;
        float barRight = w * 0.74f;
        float barTop = 56f * dp;
        float barBottom = 82f * dp;
        paint.setColor(0xFF334155);
        canvas.drawRoundRect(new RectF(barLeft, barTop, barRight, barBottom), 8f * dp, 8f * dp, paint);

        float ratio = Math.min(1f, Math.max(0f, parkourDistanceDp / PARKOUR_TARGET_DISTANCE_DP));
        paint.setColor(0xFF00E676);
        canvas.drawRoundRect(new RectF(barLeft, barTop, barLeft + (barRight - barLeft) * ratio, barBottom), 8f * dp, 8f * dp, paint);

        paint.setColor(0xFFFFFFFF);
        paint.setTextSize(12.5f * sp);
        paint.setTextAlign(Paint.Align.CENTER);
        int pct = (int) (ratio * 100f);
        canvas.drawText("🏁 %" + pct, (barLeft + barRight) * 0.5f, barBottom - 7f * dp, paint);

        // Alt Satır Sağ: Puan
        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setTextSize(15.5f * sp);
        paint.setColor(0xFFFFD600);
        canvas.drawText("⭐ " + score, w - 8f * dp, row2Bottom - 11f * dp, paint);
    }

    private void drawGameOverOverlay(Canvas canvas, int w, int h) {
        paint.setColor(0xCC0F172A);
        canvas.drawRect(0, 0, w, h, paint);

        float cy = h * 0.50f;
        RectF card = new RectF(w * 0.06f, cy - 155f * dp, w * 0.94f, cy + 165f * dp);
        paint.setColor(0xFFFFFFFF);
        canvas.drawRoundRect(card, 20f * dp, 20f * dp, paint);

        paint.setColor(0xFFD32F2F);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(23f * sp);
        paint.setFakeBoldText(true);
        canvas.drawText("💔 3 CANIN BİTTİ!", w * 0.5f, cy - 105f * dp, paint);

        paint.setColor(0xFF1E293B);
        paint.setTextSize(16f * sp);
        String stName = STAGE_NAMES[(stage - 1) % STAGE_NAMES.length];
        canvas.drawText("Parkur " + stage + ": " + stName, w * 0.5f, cy - 68f * dp, paint);

        paint.setColor(0xFF0F172A);
        paint.setTextSize(18f * sp);
        canvas.drawText("⭐ Puan: " + score + "   •   🏆 Rekor: " + highScore, w * 0.5f, cy - 30f * dp, paint);

        btnRetryRect.set(w * 0.10f, cy + 5f * dp, w * 0.48f, cy + 56f * dp);
        btnNextParkourRect.set(w * 0.52f, cy + 5f * dp, w * 0.90f, cy + 56f * dp);
        btnLobbyOnGameOverRect.set(w * 0.18f, cy + 72f * dp, w * 0.82f, cy + 122f * dp);

        paint.setColor(0xFF16A34A);
        canvas.drawRoundRect(btnRetryRect, 12f * dp, 12f * dp, paint);
        paint.setColor(0xFF2563EB);
        canvas.drawRoundRect(btnNextParkourRect, 12f * dp, 12f * dp, paint);
        paint.setColor(0xFF475569);
        canvas.drawRoundRect(btnLobbyOnGameOverRect, 12f * dp, 12f * dp, paint);

        paint.setColor(0xFFFFFFFF);
        paint.setTextSize(15f * sp);
        canvas.drawText("🔄 Tekrar Oyna", btnRetryRect.centerX(), btnRetryRect.centerY() + 5f * dp, paint);
        canvas.drawText("🗺️ Diğer Parkur", btnNextParkourRect.centerX(), btnNextParkourRect.centerY() + 5f * dp, paint);
        canvas.drawText("🏠 Oyun Lobisine Dön", btnLobbyOnGameOverRect.centerX(), btnLobbyOnGameOverRect.centerY() + 5f * dp, paint);
    }

    private void updatePhysicsAndSpawns(int width, int height, float topHudHeight) {
        animTick += 0.09f;
        if (invulnerableFrames > 0) invulnerableFrames--;
        if (magnetFrames > 0) magnetFrames--;

        scrollSpeed = (3.0f + stage * 0.24f) * dp;
        float gravity = ((stage == 5) ? 0.36f : (stage == 2 ? 0.41f : 0.47f)) * dp;

        float prevBottom = playerY + playerRadius;
        playerY += playerVy;
        playerVy += gravity;
        float newBottom = playerY + playerRadius;

        boolean landedOnPlatform = false;
        Iterator<Platform> platIt = platforms.iterator();
        while (platIt.hasNext()) {
            Platform p = platIt.next();
            float extraDx = p.movingHorizontally ? (float) Math.cos(animTick + p.movePhase) * 0.9f * dp : 0f;
            p.x -= (scrollSpeed + extraDx);
            if (p.movingVertically) {
                p.y = p.baseY + (float) Math.sin(animTick + p.movePhase) * 20f * dp;
            }

            if (p.hasTrampoline) {
                float tx = p.x + p.trampolineOffsetX;
                float tw = 42f * dp;
                if (playerVy >= 0 && playerX + playerRadius * 0.6f >= tx && playerX - playerRadius * 0.6f <= tx + tw
                        && prevBottom <= p.y + 10f * dp && newBottom >= p.y - 14f * dp) {
                    playerY = p.y - 14f * dp - playerRadius;
                    playerVy = -12.6f * dp;
                    jumpCount = 1;
                    spawnSparkles(playerX, playerY + playerRadius, 0xFFFF1744, 15);
                    if (listener != null) listener.onJumpTriggered();
                    landedOnPlatform = true;
                }
            }

            if (!landedOnPlatform && playerVy >= 0
                    && playerX + playerRadius * 0.55f >= p.x
                    && playerX - playerRadius * 0.55f <= p.x + p.width
                    && prevBottom <= p.y + 12f * dp
                    && newBottom >= p.y) {
                playerY = p.y - playerRadius;
                playerVy = 0f;
                jumpCount = 0;
                landedOnPlatform = true;
            }

            if (p.x + p.width < -60f * dp) {
                platIt.remove();
            }
        }

        boolean overPit = false;
        Iterator<GroundPit> pitIt = pits.iterator();
        while (pitIt.hasNext()) {
            GroundPit pit = pitIt.next();
            pit.x -= scrollSpeed;
            if (playerX > pit.x + 8f * dp && playerX < pit.x + pit.width - 8f * dp) {
                overPit = true;
            }
            if (pit.x + pit.width < -60f * dp) {
                pitIt.remove();
            }
        }

        if (!landedOnPlatform) {
            if (!overPit && playerY + playerRadius >= groundY) {
                playerY = groundY - playerRadius;
                playerVy = 0f;
                jumpCount = 0;
            } else if (overPit && playerY - playerRadius > groundY + 18f * dp) {
                handlePlayerDamage();
                playerY = groundY - playerRadius - 80f * dp;
                playerVy = -7.5f * dp;
                jumpCount = 1;
            }
        }

        if (playerY - playerRadius < topHudHeight + 6f * dp) {
            playerY = topHudHeight + 6f * dp + playerRadius;
            if (playerVy < 0) playerVy = 0f;
        }

        parkourDistanceDp += (scrollSpeed / dp) * 0.35f;
        if (!finishGateSpawned && parkourDistanceDp >= PARKOUR_TARGET_DISTANCE_DP) {
            finishGateSpawned = true;
            finishGateX = width + 90f * dp;
        }

        if (finishGateSpawned) {
            finishGateX -= scrollSpeed;
            if (Math.abs(finishGateX + 35f * dp - playerX) < 35f * dp) {
                score += 100;
                if (lives < 3) lives++;
                spawnSparkles(playerX, playerY, 0xFFFFD600, 26);
                if (listener != null) listener.onStarCollected();
                int nextStage = (stage % 6) + 1;
                loadParkourStage(nextStage);
                return;
            } else if (finishGateX < -80f * dp) {
                int nextStage = (stage % 6) + 1;
                loadParkourStage(nextStage);
                return;
            }
        } else {
            float farthestX = 0f;
            for (Platform p : platforms) {
                if (p.x + p.width > farthestX) farthestX = p.x + p.width;
            }
            for (Obstacle ob : obstacles) {
                if (ob.x + ob.width > farthestX) farthestX = ob.x + ob.width;
            }
            if (farthestX < width - 90f * dp) {
                spawnParkourChunk(width + 45f * dp);
            }
        }

        Iterator<Collectible> colIt = collectibles.iterator();
        while (colIt.hasNext()) {
            Collectible c = colIt.next();
            c.x -= scrollSpeed;
            if (magnetFrames > 0 && (c.type == 0 || c.type == 1) && Math.abs(c.x - playerX) < 190f * dp) {
                c.x += (playerX - c.x) * 0.18f;
                c.y += (playerY - c.y) * 0.18f;
            }
            float dx = c.x - playerX;
            float dy = c.y - playerY;
            if (Math.sqrt(dx * dx + dy * dy) < (playerRadius + 18f * dp)) {
                if (c.type == 0) {
                    score += 15;
                    spawnSparkles(c.x, c.y, 0xFFFFD600, 10);
                } else if (c.type == 1) {
                    score += 30;
                    spawnSparkles(c.x, c.y, 0xFF00E5FF, 14);
                } else if (c.type == 2) {
                    hasShield = true;
                    score += 20;
                    spawnSparkles(c.x, c.y, 0xFF69F0AE, 12);
                } else if (c.type == 4) {
                    magnetFrames = 320;
                    score += 25;
                    spawnSparkles(c.x, c.y, 0xFFEA80FC, 14);
                } else {
                    if (lives < 3) lives++;
                    score += 25;
                    spawnSparkles(c.x, c.y, 0xFFFF5252, 14);
                }
                if (listener != null) {
                    listener.onStarCollected();
                }
                notifyStats();
                colIt.remove();
            } else if (c.x < -50f * dp) {
                colIt.remove();
            }
        }

        Iterator<Obstacle> obIt = obstacles.iterator();
        while (obIt.hasNext()) {
            Obstacle ob = obIt.next();
            ob.x -= scrollSpeed;
            if (ob.type == 1) {
                ob.y = ob.baseY + (float) Math.sin(animTick * 1.4f + ob.phase) * 24f * dp;
            }

            boolean hitX = (playerX + playerRadius * 0.68f > ob.x) && (playerX - playerRadius * 0.68f < ob.x + ob.width);
            boolean hitY = (playerY + playerRadius * 0.68f > ob.y) && (playerY - playerRadius * 0.68f < ob.y + ob.height);

            if (hitX && hitY && invulnerableFrames == 0) {
                handlePlayerDamage();
                obIt.remove();
            } else if (ob.x + ob.width < -50f * dp) {
                score += 5;
                notifyStats();
                obIt.remove();
            }
        }
    }

    private void handlePlayerDamage() {
        if (hasShield) {
            hasShield = false;
            invulnerableFrames = 45;
            spawnSparkles(playerX, playerY, 0xFF69F0AE, 15);
            notifyStats();
            return;
        }
        invulnerableFrames = 55;
        lives--;
        spawnSparkles(playerX, playerY, 0xFFD32F2F, 16);
        if (listener != null) {
            listener.onObstacleHit();
        }
        if (lives <= 0) {
            lives = 0;
            gameOver = true;
            running = false;
        }
        notifyStats();
    }
}
