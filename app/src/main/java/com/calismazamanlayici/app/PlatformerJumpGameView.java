package com.calismazamanlayici.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Tam Ekran (Full-Screen), 3-2-1-BAŞLA! Geri Sayımlı ve 6 Farklı Parkurlu
 * "Süper Yıldız Zıplama Macerası" Oyunu:
 * - Komple tam ekran çalışır; dikey alanı çok katlı platformlar, trambolinler ve gökyüzü yollarıyla kullanır.
 * - Her bölüme/parkura başlarken ekranda büyük, animasyonlu "3 - 2 - 1 - BAŞLA!" geri sayımı yapılır.
 * - Canlar (3 Kalp) bittiğinde oyun gerçekten sona erer.
 */
public class PlatformerJumpGameView extends View {

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

    private boolean running = false;
    private boolean gameOver = false;

    // Her bölüme başlarken 3-2-1-BAŞLA geri sayımı
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

    // Parkur ilerleme mesafesi
    private float parkourDistance = 0f;
    private static final float PARKOUR_TARGET_DISTANCE = 1900f;
    private boolean finishGateSpawned = false;
    private float finishGateX = -500f;

    // Karakter fizik değişkenleri ve kuyruk izi (Trail)
    private float playerX = 150f;
    private float playerY = 300f;
    private float playerVy = 0f;
    private final float playerRadius = 28f;
    private int jumpCount = 0;
    private float groundY = 600f;
    private float scrollSpeed = 7.4f;
    private float animTick = 0f;
    private final float[] trailX = new float[8];
    private final float[] trailY = new float[8];

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
        int type; // 0: Diken, 1: Salınımlı Uçan Dron/Ateş Topu, 2: Yuvarlanan Kaya, 3: Üst Sarkıt
        float phase;
    }

    private static class Collectible {
        float x;
        float y;
        int type; // 0: Altın Yıldız (+15), 1: Süper Elmas (+30), 2: Kalkan (🛡️), 3: Can Kalbi (❤️), 4: Yıldız Mıknatısı (🧲)
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
            0xFF4FC3F7, // 1: Zümrüt Orman Canlı Gökyüzü
            0xFF29B6F6, // 2: Bulut Adaları Gökyüzü
            0xFF2D1612, // 3: Lav Mağarası Koyu Volkan
            0xFFB3E5FC, // 4: Kristal Buzul Kutup Gökyüzü
            0xFF0B132B, // 5: Uzay İstasyonu Derin Uzay
            0xFFFFE082  // 6: Altın Tapınak Gün Batımı
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

    public PlatformerJumpGameView(Context context, JumpGameListener listener) {
        super(context);
        this.listener = listener;
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

    /**
     * Yeni bölüme/parkura geçerken sahneyi kurar ve 3-2-1-BAŞLA! geri sayımını başlatır.
     */
    private void loadParkourStage(int targetStage) {
        this.stage = ((targetStage - 1) % 6) + 1;
        this.parkourDistance = 0f;
        this.finishGateSpawned = false;
        this.finishGateX = -500f;
        this.jumpCount = 0;
        this.playerVy = 0f;
        this.playerY = (groundY > 200f) ? (groundY - playerRadius) : 340f;
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

        for (int i = 0; i < 18; i++) {
            AmbientParticle ap = new AmbientParticle();
            ap.x = random.nextInt(1080);
            ap.y = 130f + random.nextInt(700);
            ap.size = 4f + random.nextFloat() * 7f;
            ap.vx = -(1.5f + random.nextFloat() * 2.5f);
            ap.vy = (stage == 3) ? -(0.8f + random.nextFloat() * 1.5f) : (0.6f + random.nextFloat() * 1.4f);
            ambientParticles.add(ap);
        }

        spawnParkourChunk(460f);
        spawnParkourChunk(920f);

        // 3 - 2 - 1 - BAŞLA! Geri Sayımını Başlat
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
            // Geri sayım bitmeden zıplama başlamaz
            return;
        }
        if (!running) {
            running = true;
            invalidate();
        }
        if (jumpCount < 2) {
            float boost = (stage == 5) ? 1.14f : (stage == 2 ? 1.07f : 1.0f);
            playerVy = ((jumpCount == 0) ? -21.5f : -18.5f) * boost;
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

            // 1. Sol üst Çıkış / Lobi butonu
            if (btnExitRect.contains(tx, ty)) {
                if (listener != null) {
                    listener.onExitRequested();
                }
                return true;
            }

            // 2. Üst Parkur Seçme Sekmeleri (P1..P6)
            for (int i = 0; i < 6; i++) {
                if (parkourTabRects[i].contains(tx, ty)) {
                    selectParkour(i + 1);
                    return true;
                }
            }

            // 3. Oyun Bitti ekranı butonları
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

            // 4. Normal oyunda zıpla
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
            float spd = 3f + random.nextFloat() * 7.5f;
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
        float gY = (groundY > 220f) ? groundY : 620f;
        int pattern = random.nextInt(3);

        if (stage == 1) {
            // Parkur 1: Zümrüt Orman Köprüleri (3 Katlı Ağaç Ev Platformları + Mantar Trambolin + Nehir)
            if (pattern == 0) {
                GroundPit pit = new GroundPit();
                pit.x = startX + 40f;
                pit.width = 145f;
                pit.pitType = 0;
                pits.add(pit);

                Platform pLow = new Platform();
                pLow.x = startX + 15f;
                pLow.baseY = gY - 110f;
                pLow.y = pLow.baseY;
                pLow.width = 180f;
                pLow.height = 22f;
                platforms.add(pLow);

                Platform pHigh = new Platform();
                pHigh.x = startX + 190f;
                pHigh.baseY = gY - 220f;
                pHigh.y = pHigh.baseY;
                pHigh.width = 150f;
                pHigh.height = 22f;
                platforms.add(pHigh);

                spawnStarArc(startX + 40f, pLow.baseY - 48f, 3, 48f, 0);
                spawnStarArc(startX + 205f, pHigh.baseY - 48f, 3, 45f, 1);
            } else if (pattern == 1) {
                Platform p1 = new Platform();
                p1.x = startX;
                p1.baseY = gY - 95f;
                p1.y = p1.baseY;
                p1.width = 140f;
                p1.height = 22f;
                p1.hasTrampoline = true;
                p1.trampolineOffsetX = 48f;
                platforms.add(p1);

                Platform pSky = new Platform();
                pSky.x = startX + 165f;
                pSky.baseY = gY - 260f;
                pSky.y = pSky.baseY;
                pSky.width = 175f;
                pSky.height = 22f;
                platforms.add(pSky);

                spawnStarArc(startX + 185f, pSky.baseY - 50f, 4, 42f, 4); // Mıknatıs ödüllü üst rota!
            } else {
                Obstacle ob = new Obstacle();
                ob.x = startX + 75f;
                ob.width = 50f;
                ob.height = 60f;
                ob.baseY = gY - ob.height;
                ob.y = ob.baseY;
                ob.type = 2;
                obstacles.add(ob);
                spawnStarArc(startX + 50f, gY - 135f, 3, 45f, 0);
            }
        } else if (stage == 2) {
            // Parkur 2: Kayan Bulut Adaları (Hem Dikey Hem Yatay Hareketli Bulutlar)
            GroundPit pit = new GroundPit();
            pit.x = startX + 30f;
            pit.width = 180f;
            pit.pitType = 2;
            pits.add(pit);

            Platform c1 = new Platform();
            c1.x = startX + 10f;
            c1.baseY = gY - 120f;
            c1.y = c1.baseY;
            c1.width = 160f;
            c1.height = 26f;
            c1.movingVertically = true;
            c1.movePhase = random.nextFloat() * 6.28f;
            platforms.add(c1);

            Platform c2 = new Platform();
            c2.x = startX + 195f;
            c2.baseY = gY - 235f;
            c2.y = c2.baseY;
            c2.width = 155f;
            c2.height = 26f;
            c2.movingHorizontally = true;
            c2.movePhase = random.nextFloat() * 6.28f;
            platforms.add(c2);

            if (pattern == 1) {
                Obstacle storm = new Obstacle();
                storm.x = startX + 180f;
                storm.width = 48f;
                storm.height = 48f;
                storm.baseY = gY - 130f;
                storm.y = storm.baseY;
                storm.type = 1;
                storm.phase = random.nextFloat() * 6.28f;
                obstacles.add(storm);
            }
            spawnStarArc(startX + 205f, c2.baseY - 48f, 3, 45f, 1);
        } else if (stage == 3) {
            // Parkur 3: Lav Mağarası & Trambolin (Lav Çukurları + Süper Trambolin + Yüksek Kaya Terasları)
            GroundPit lava = new GroundPit();
            lava.x = startX + 55f;
            lava.width = 175f;
            lava.pitType = 1;
            pits.add(lava);

            Platform launchPad = new Platform();
            launchPad.x = startX - 20f;
            launchPad.baseY = gY - 70f;
            launchPad.y = launchPad.baseY;
            launchPad.width = 95f;
            launchPad.height = 22f;
            launchPad.hasTrampoline = true;
            launchPad.trampolineOffsetX = 25f;
            platforms.add(launchPad);

            Platform upperBridge = new Platform();
            upperBridge.x = startX + 115f;
            upperBridge.baseY = gY - 230f;
            upperBridge.y = upperBridge.baseY;
            upperBridge.width = 170f;
            upperBridge.height = 24f;
            platforms.add(upperBridge);

            Obstacle fireball = new Obstacle();
            fireball.x = startX + 135f;
            fireball.width = 44f;
            fireball.height = 44f;
            fireball.baseY = gY - 95f;
            fireball.y = fireball.baseY;
            fireball.type = 1;
            fireball.phase = random.nextFloat() * 6.28f;
            obstacles.add(fireball);

            spawnStarArc(startX + 130f, upperBridge.baseY - 48f, 3, 46f, 2);
        } else if (stage == 4) {
            // Parkur 4: Kristal Buzul Tepeleri (3 Katlı Buz Terasları + Sarkıt Tünelleri)
            Platform ice1 = new Platform();
            ice1.x = startX;
            ice1.baseY = gY - 95f;
            ice1.y = ice1.baseY;
            ice1.width = 140f;
            ice1.height = 22f;
            platforms.add(ice1);

            Platform ice2 = new Platform();
            ice2.x = startX + 155f;
            ice2.baseY = gY - 195f;
            ice2.y = ice2.baseY;
            ice2.width = 155f;
            ice2.height = 22f;
            platforms.add(ice2);

            if (pattern == 0) {
                Obstacle spike = new Obstacle();
                spike.x = startX + 75f;
                spike.width = 48f;
                spike.height = 58f;
                spike.baseY = gY - spike.height;
                spike.y = spike.baseY;
                spike.type = 0;
                obstacles.add(spike);
            } else {
                Obstacle icicle = new Obstacle();
                icicle.x = startX + 185f;
                icicle.width = 44f;
                icicle.height = 70f;
                icicle.baseY = gY - 320f;
                icicle.y = icicle.baseY;
                icicle.type = 3;
                obstacles.add(icicle);
            }
            spawnStarArc(startX + 170f, ice2.baseY - 48f, 3, 46f, 1);
        } else if (stage == 5) {
            // Parkur 5: Uzay İstasyonu & Lazer Koridoru (Ay Yerçekimi + Çok Katlı Siber Güverteler)
            Platform deck1 = new Platform();
            deck1.x = startX + 15f;
            deck1.baseY = gY - 135f;
            deck1.y = deck1.baseY;
            deck1.width = 165f;
            deck1.height = 20f;
            deck1.movingVertically = true;
            deck1.movePhase = random.nextFloat() * 6.28f;
            platforms.add(deck1);

            Platform deck2 = new Platform();
            deck2.x = startX + 195f;
            deck2.baseY = gY - 265f;
            deck2.y = deck2.baseY;
            deck2.width = 160f;
            deck2.height = 20f;
            platforms.add(deck2);

            Obstacle drone = new Obstacle();
            drone.x = startX + 125f;
            drone.width = 48f;
            drone.height = 48f;
            drone.baseY = (pattern == 0) ? (gY - 70f) : (gY - 200f);
            drone.y = drone.baseY;
            drone.type = 1;
            drone.phase = random.nextFloat() * 6.28f;
            obstacles.add(drone);

            spawnStarArc(startX + 210f, deck2.baseY - 48f, 3, 46f, 4);
        } else {
            // Parkur 6: Altın Tapınak Piramidi (4 Basamaklı Dev Piramit + Zirve Trambolini)
            for (int step = 0; step < 3; step++) {
                Platform stair = new Platform();
                stair.x = startX + step * 100f;
                stair.baseY = gY - 80f - step * 75f;
                stair.y = stair.baseY;
                stair.width = 95f;
                stair.height = 22f;
                if (step == 2) {
                    stair.hasTrampoline = true;
                    stair.trampolineOffsetX = 25f;
                }
                platforms.add(stair);
            }
            Obstacle guard = new Obstacle();
            guard.x = startX + 130f;
            guard.width = 48f;
            guard.height = 54f;
            guard.baseY = gY - guard.height;
            guard.y = guard.baseY;
            guard.type = 2;
            obstacles.add(guard);

            spawnStarArc(startX + 25f, gY - 260f, 4, 50f, 1);
        }

        if (lives < 3 && random.nextInt(100) < 30) {
            Collectible heart = new Collectible();
            heart.x = startX + 95f;
            heart.y = gY - 160f;
            heart.type = 3;
            collectibles.add(heart);
        }
    }

    private void spawnStarArc(float startX, float y, int count, float spacing, int preferredType) {
        for (int i = 0; i < count; i++) {
            Collectible c = new Collectible();
            c.x = startX + i * spacing;
            c.y = y - (i == 1 ? 20f : 0f);
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

        float topHudHeight = 112f;
        groundY = h * 0.84f;
        playerX = w * 0.20f;

        int stageIdx = (stage - 1) % SKY_COLORS.length;

        // 1. Gökyüzü ve Atmosfer Parçacıkları
        canvas.drawColor(SKY_COLORS[stageIdx]);

        // Arka plan dağ / silüet tepeleri (Paralaks derinlik hissi)
        paint.setColor(0x22000000);
        for (int i = 0; i < 4; i++) {
            float hillX = ((i * 380f - (parkourDistance * 0.35f) % 1520f) + 1520f) % 1520f - 200f;
            canvas.drawCircle(hillX, groundY + 40f, 220f, paint);
        }

        // Parkura özgü atmosfer parçacıkları (Yaprak, Kar, Kor Kıvılcımı, Yıldız Tozu)
        paint.setColor(stage == 3 ? 0xAAFF6D00 : (stage == 4 ? 0xDDFFFFFF : 0x88FFFFFF));
        for (AmbientParticle ap : ambientParticles) {
            canvas.drawCircle(ap.x, ap.y, ap.size, paint);
            if (running && !gameOver && !inCountdown) {
                ap.x += ap.vx;
                ap.y += ap.vy;
                if (ap.x < -20f) ap.x = w + 20f;
                if (ap.y < topHudHeight) ap.y = groundY - 20f;
                if (ap.y > groundY) ap.y = topHudHeight + 20f;
            }
        }

        // 2. Zemin ve Çukurlar
        paint.setColor(GROUND_COLORS[stageIdx]);
        canvas.drawRect(0, groundY, w, h, paint);
        paint.setColor(0x33FFFFFF);
        canvas.drawRect(0, groundY, w, groundY + 8f, paint);

        for (GroundPit pit : pits) {
            paint.setColor(SKY_COLORS[stageIdx]);
            canvas.drawRect(pit.x, groundY - 2f, pit.x + pit.width, h, paint);
            if (pit.pitType == 0) {
                paint.setColor(0xFF0288D1);
                canvas.drawRect(pit.x, groundY + 20f, pit.x + pit.width, h, paint);
            } else if (pit.pitType == 1) {
                paint.setColor(0xFFFF3D00);
                canvas.drawRect(pit.x, groundY + 16f, pit.x + pit.width, h, paint);
                paint.setColor(0xFFFFD600);
                canvas.drawRect(pit.x + 8f, groundY + 16f, pit.x + pit.width - 8f, groundY + 26f, paint);
            }
        }

        // 3. 3-2-1-BAŞLA! Geri Sayım Kontrolü veya Fizik Güncellemesi
        int currentCountdownStep = 0; // 3, 2, 1, 0 (0 = BAŞLA!)
        if (inCountdown) {
            long elapsed = SystemClock.elapsedRealtime() - countdownStartRealtimeMs;
            if (elapsed < 850L) {
                currentCountdownStep = 3;
            } else if (elapsed < 1700L) {
                currentCountdownStep = 2;
            } else if (elapsed < 2550L) {
                currentCountdownStep = 1;
            } else if (elapsed < 3150L) {
                currentCountdownStep = 0; // BAŞLA!
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

        // 4. Çok Katmanlı Platformlar ve Trambolinler
        for (Platform p : platforms) {
            paint.setColor(PLATFORM_COLORS[stageIdx]);
            RectF pr = new RectF(p.x, p.y, p.x + p.width, p.y + p.height);
            canvas.drawRoundRect(pr, 12f, 12f, paint);
            paint.setColor(0x44000000);
            canvas.drawRoundRect(new RectF(p.x, p.y + p.height - 6f, p.x + p.width, p.y + p.height), 6f, 6f, paint);

            if (p.hasTrampoline) {
                float tx = p.x + p.trampolineOffsetX;
                paint.setColor(0xFFFF1744);
                canvas.drawRoundRect(new RectF(tx, p.y - 16f, tx + 48f, p.y), 8f, 8f, paint);
                paint.setColor(0xFFFFFFFF);
                canvas.drawCircle(tx + 12f, p.y - 8f, 4f, paint);
                canvas.drawCircle(tx + 24f, p.y - 10f, 4f, paint);
                canvas.drawCircle(tx + 36f, p.y - 8f, 4f, paint);
            }
        }

        // 5. Parkur Bitiş Kapısı (🏁)
        if (finishGateSpawned && finishGateX > -120f) {
            paint.setColor(0xFFFFD600);
            canvas.drawRect(finishGateX, groundY - 190f, finishGateX + 16f, groundY, paint);
            canvas.drawRect(finishGateX + 72f, groundY - 190f, finishGateX + 88f, groundY, paint);
            paint.setColor(0xFF00E676);
            canvas.drawRoundRect(new RectF(finishGateX - 12f, groundY - 220f, finishGateX + 100f, groundY - 170f), 14f, 14f, paint);
            paint.setColor(0xFF0F172A);
            paint.setTextSize(24f);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setFakeBoldText(true);
            canvas.drawText("🏁 BİTİŞ", finishGateX + 44f, groundY - 187f, paint);
        }

        // 6. Toplanabilir Öğeler
        for (Collectible col : collectibles) {
            if (col.type == 0) {
                paint.setColor(0xFFFFD600);
                canvas.drawCircle(col.x, col.y, 19f, paint);
                paint.setColor(0xFFE65100);
                paint.setTextSize(23f);
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("★", col.x, col.y + 8f, paint);
            } else if (col.type == 1) {
                paint.setColor(0xFF00E5FF);
                canvas.drawCircle(col.x, col.y, 21f, paint);
                paint.setColor(0xFF004D40);
                paint.setTextSize(21f);
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("💎", col.x, col.y + 7f, paint);
            } else if (col.type == 2) {
                paint.setColor(0xFF69F0AE);
                canvas.drawCircle(col.x, col.y, 21f, paint);
                paint.setColor(0xFF1B5E20);
                paint.setTextSize(20f);
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("🛡️", col.x, col.y + 7f, paint);
            } else if (col.type == 4) {
                paint.setColor(0xFFEA80FC);
                canvas.drawCircle(col.x, col.y, 21f, paint);
                paint.setColor(0xFF4A148C);
                paint.setTextSize(20f);
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("🧲", col.x, col.y + 7f, paint);
            } else {
                paint.setColor(0xFFFFCDD2);
                canvas.drawCircle(col.x, col.y, 21f, paint);
                paint.setColor(0xFFC62828);
                paint.setTextSize(21f);
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("❤️", col.x, col.y + 7f, paint);
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
                canvas.drawRoundRect(r, 8f, 8f, paint);
            } else {
                paint.setColor(0xFF5D4037);
                RectF r = new RectF(ob.x, ob.y, ob.x + ob.width, ob.y + ob.height);
                canvas.drawRoundRect(r, 14f, 14f, paint);
                paint.setColor(0xFFFFCA28);
                canvas.drawRect(ob.x + 6f, ob.y + 8f, ob.x + ob.width - 6f, ob.y + 15f, paint);
            }
        }

        // 8. Karakter Kuyruk İzi (Hareket Dinamizmi) ve Karakter Çizimi
        for (int i = trailX.length - 1; i > 0; i--) {
            trailX[i] = trailX[i - 1] - scrollSpeed * 0.45f;
            trailY[i] = trailY[i - 1];
            paint.setColor(0x33FFAB40);
            float r = playerRadius * (1f - i * 0.11f);
            if (r > 2f) {
                canvas.drawCircle(trailX[i], trailY[i], r, paint);
            }
        }
        trailX[0] = playerX;
        trailY[0] = playerY;

        if (invulnerableFrames % 4 < 2) {
            if (hasShield) {
                paint.setColor(0x6600E676);
                canvas.drawCircle(playerX, playerY, playerRadius + 14f, paint);
            }
            if (magnetFrames > 0) {
                paint.setColor(0x55EA80FC);
                canvas.drawCircle(playerX, playerY, playerRadius + 20f, paint);
            }
            paint.setColor(0xFFFF5722);
            canvas.drawCircle(playerX, playerY, playerRadius, paint);
            paint.setColor(0xFFFFFFFF);
            canvas.drawCircle(playerX + 9f, playerY - 7f, 6.5f, paint);
            paint.setColor(0xFF1F2937);
            canvas.drawCircle(playerX + 11f, playerY - 7f, 3.2f, paint);
        }

        // 9. Parçacık Efektleri
        Iterator<Sparkle> spIt = sparkles.iterator();
        while (spIt.hasNext()) {
            Sparkle s = spIt.next();
            s.x += s.vx;
            s.y += s.vy;
            s.life--;
            paint.setColor(s.color);
            canvas.drawCircle(s.x, s.y, Math.max(2f, s.life * 0.45f), paint);
            if (s.life <= 0) {
                spIt.remove();
            }
        }

        // 10. Sağ Alt Köşede Yarı Saydam Tam Ekran Zıplama Pedi İpucu
        if (!gameOver && !inCountdown) {
            RectF jumpPad = new RectF(w - 220f, h - 92f, w - 20f, h - 18f);
            paint.setColor(0x991E293B);
            canvas.drawRoundRect(jumpPad, 20f, 20f, paint);
            paint.setColor(0xFFFFFFFF);
            paint.setTextSize(20f);
            paint.setFakeBoldText(true);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("⬆️ ZIPLAMA (2x)", jumpPad.centerX(), jumpPad.centerY() + 7f, paint);
        }

        // 11. Sabit Üst Gösterge Paneli (Çıkış Butonu + 6 Parkur Sekmesi + Kalpler + İlerleme Çubuğu)
        drawTopHudAndParkourTabs(canvas, w, topHudHeight);

        // 12. 3 - 2 - 1 - BAŞLA! Büyük Tam Ekran Geri Sayım Katmanı
        if (inCountdown) {
            drawCountdownOverlay(canvas, w, h, currentCountdownStep);
        }

        // 13. Oyun Bitti (Game Over) Ekranı
        if (gameOver) {
            drawGameOverOverlay(canvas, w, h);
        } else if (running) {
            postInvalidateDelayed(16L);
        }
    }

    private void drawCountdownOverlay(Canvas canvas, int w, int h, int step) {
        paint.setColor(0x770F172A);
        canvas.drawRect(0, 112f, w, h, paint);

        float cx = w * 0.5f;
        float cy = h * 0.48f;

        RectF banner = new RectF(w * 0.12f, cy - 125f, w * 0.88f, cy + 115f);
        paint.setColor(0xEE1E293B);
        canvas.drawRoundRect(banner, 28f, 28f, paint);

        paint.setColor(0xFF38BDF8);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(24f);
        paint.setFakeBoldText(true);
        String stName = STAGE_NAMES[(stage - 1) % STAGE_NAMES.length];
        canvas.drawText("🗺️ PARKUR " + stage + ": " + stName.toUpperCase(), cx, cy - 68f, paint);

        if (step > 0) {
            paint.setColor(0xFFFFD600);
            paint.setTextSize(92f);
            canvas.drawText(String.valueOf(step), cx, cy + 32f, paint);
            paint.setColor(0xFFE2E8F0);
            paint.setTextSize(20f);
            canvas.drawText("Hazır Ol! Ekrana dokunarak çift zıplayabilirsin", cx, cy + 84f, paint);
        } else {
            paint.setColor(0xFF00E676);
            paint.setTextSize(68f);
            canvas.drawText("BAŞLA! 🚀", cx, cy + 28f, paint);
        }
    }

    private void drawTopHudAndParkourTabs(Canvas canvas, int w, float hudH) {
        paint.setColor(0xDD0F172A);
        canvas.drawRect(0, 0, w, hudH, paint);

        // Üst Satır: 6 Parkur Seçme Sekmesi
        float margin = 6f;
        float tabW = (w - margin * 7f) / 6f;
        for (int i = 0; i < 6; i++) {
            float left = margin + i * (tabW + margin);
            parkourTabRects[i].set(left, 8f, left + tabW, 46f);
            boolean active = (stage == i + 1);
            paint.setColor(active ? 0xFFFF6D00 : 0xFF334155);
            canvas.drawRoundRect(parkourTabRects[i], 10f, 10f, paint);

            paint.setColor(0xFFFFFFFF);
            paint.setTextSize(17f);
            paint.setFakeBoldText(active);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(SHORT_TAB_NAMES[i], parkourTabRects[i].centerX(), 33f, paint);
        }

        // Alt Satır Sol: ⬅️ Çıkış Butonu + Sabit 3 Kalp (❤️❤️❤️)
        btnExitRect.set(8f, 56f, 102f, 102f);
        paint.setColor(0xFFDC2626);
        canvas.drawRoundRect(btnExitRect, 10f, 10f, paint);
        paint.setColor(0xFFFFFFFF);
        paint.setTextSize(17f);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("⬅️ Çıkış", btnExitRect.centerX(), 85f, paint);

        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(22f);
        StringBuilder heartsSb = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            heartsSb.append(i < lives ? "❤️" : "🖤");
        }
        if (hasShield) heartsSb.append("🛡️");
        if (magnetFrames > 0) heartsSb.append("🧲");
        canvas.drawText(heartsSb.toString(), 112f, 87f, paint);

        // Alt Satır Orta: Parkur İlerleme Çubuğu (%0 -> %100 🏁)
        float barLeft = w * 0.44f;
        float barRight = w * 0.74f;
        float barTop = 66f;
        float barBottom = 92f;
        paint.setColor(0xFF334155);
        canvas.drawRoundRect(new RectF(barLeft, barTop, barRight, barBottom), 10f, 10f, paint);

        float ratio = Math.min(1f, Math.max(0f, parkourDistance / PARKOUR_TARGET_DISTANCE));
        paint.setColor(0xFF00E676);
        canvas.drawRoundRect(new RectF(barLeft, barTop, barLeft + (barRight - barLeft) * ratio, barBottom), 10f, 10f, paint);

        paint.setColor(0xFFFFFFFF);
        paint.setTextSize(16f);
        paint.setTextAlign(Paint.Align.CENTER);
        int pct = (int) (ratio * 100f);
        canvas.drawText("🏁 %" + pct, (barLeft + barRight) * 0.5f, 85f, paint);

        // Alt Satır Sağ: Puan
        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setTextSize(22f);
        paint.setColor(0xFFFFD600);
        canvas.drawText("⭐ " + score, w - 12f, 87f, paint);
    }

    private void drawGameOverOverlay(Canvas canvas, int w, int h) {
        paint.setColor(0xCC0F172A);
        canvas.drawRect(0, 0, w, h, paint);

        RectF card = new RectF(w * 0.08f, h * 0.22f, w * 0.92f, h * 0.80f);
        paint.setColor(0xFFFFFFFF);
        canvas.drawRoundRect(card, 24f, 24f, paint);

        paint.setColor(0xFFD32F2F);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(32f);
        paint.setFakeBoldText(true);
        canvas.drawText("💔 3 CANIN BİTTİ!", w * 0.5f, h * 0.32f, paint);

        paint.setColor(0xFF1E293B);
        paint.setTextSize(22f);
        String stName = STAGE_NAMES[(stage - 1) % STAGE_NAMES.length];
        canvas.drawText("Parkur " + stage + ": " + stName, w * 0.5f, h * 0.40f, paint);

        paint.setColor(0xFF0F172A);
        paint.setTextSize(25f);
        canvas.drawText("⭐ Toplam Puan: " + score + "   •   🏆 Rekor: " + highScore, w * 0.5f, h * 0.48f, paint);

        btnRetryRect.set(w * 0.12f, h * 0.55f, w * 0.48f, h * 0.65f);
        btnNextParkourRect.set(w * 0.52f, h * 0.55f, w * 0.88f, h * 0.65f);
        btnLobbyOnGameOverRect.set(w * 0.25f, h * 0.68f, w * 0.75f, h * 0.76f);

        paint.setColor(0xFF16A34A);
        canvas.drawRoundRect(btnRetryRect, 14f, 14f, paint);
        paint.setColor(0xFF2563EB);
        canvas.drawRoundRect(btnNextParkourRect, 14f, 14f, paint);
        paint.setColor(0xFF475569);
        canvas.drawRoundRect(btnLobbyOnGameOverRect, 14f, 14f, paint);

        paint.setColor(0xFFFFFFFF);
        paint.setTextSize(20f);
        canvas.drawText("🔄 Tekrar Oyna", btnRetryRect.centerX(), btnRetryRect.centerY() + 7f, paint);
        canvas.drawText("🗺️ Diğer Parkur", btnNextParkourRect.centerX(), btnNextParkourRect.centerY() + 7f, paint);
        canvas.drawText("🏠 Oyun Lobisine Dön", btnLobbyOnGameOverRect.centerX(), btnLobbyOnGameOverRect.centerY() + 7f, paint);
    }

    private void updatePhysicsAndSpawns(int width, int height, float topHudHeight) {
        animTick += 0.09f;
        if (invulnerableFrames > 0) invulnerableFrames--;
        if (magnetFrames > 0) magnetFrames--;

        scrollSpeed = 6.8f + (stage * 0.55f);
        float gravity = (stage == 5) ? 0.88f : (stage == 2 ? 1.00f : 1.16f);

        float prevBottom = playerY + playerRadius;
        playerY += playerVy;
        playerVy += gravity;
        float newBottom = playerY + playerRadius;

        boolean landedOnPlatform = false;
        Iterator<Platform> platIt = platforms.iterator();
        while (platIt.hasNext()) {
            Platform p = platIt.next();
            float extraDx = p.movingHorizontally ? (float) Math.cos(animTick + p.movePhase) * 1.8f : 0f;
            p.x -= (scrollSpeed + extraDx);
            if (p.movingVertically) {
                p.y = p.baseY + (float) Math.sin(animTick + p.movePhase) * 32f;
            }

            if (p.hasTrampoline) {
                float tx = p.x + p.trampolineOffsetX;
                if (playerVy >= 0 && playerX + playerRadius * 0.6f >= tx && playerX - playerRadius * 0.6f <= tx + 48f
                        && prevBottom <= p.y + 14f && newBottom >= p.y - 16f) {
                    playerY = p.y - 16f - playerRadius;
                    playerVy = -26.5f;
                    jumpCount = 1;
                    spawnSparkles(playerX, playerY + playerRadius, 0xFFFF1744, 15);
                    if (listener != null) listener.onJumpTriggered();
                    landedOnPlatform = true;
                }
            }

            if (!landedOnPlatform && playerVy >= 0
                    && playerX + playerRadius * 0.55f >= p.x
                    && playerX - playerRadius * 0.55f <= p.x + p.width
                    && prevBottom <= p.y + 18f
                    && newBottom >= p.y) {
                playerY = p.y - playerRadius;
                playerVy = 0f;
                jumpCount = 0;
                landedOnPlatform = true;
            }

            if (p.x + p.width < -90f) {
                platIt.remove();
            }
        }

        boolean overPit = false;
        Iterator<GroundPit> pitIt = pits.iterator();
        while (pitIt.hasNext()) {
            GroundPit pit = pitIt.next();
            pit.x -= scrollSpeed;
            if (playerX > pit.x + 12f && playerX < pit.x + pit.width - 12f) {
                overPit = true;
            }
            if (pit.x + pit.width < -90f) {
                pitIt.remove();
            }
        }

        if (!landedOnPlatform) {
            if (!overPit && playerY + playerRadius >= groundY) {
                playerY = groundY - playerRadius;
                playerVy = 0f;
                jumpCount = 0;
            } else if (overPit && playerY - playerRadius > groundY + 28f) {
                handlePlayerDamage();
                playerY = groundY - playerRadius - 110f;
                playerVy = -15f;
                jumpCount = 1;
            }
        }

        if (playerY - playerRadius < topHudHeight + 8f) {
            playerY = topHudHeight + 8f + playerRadius;
            if (playerVy < 0) playerVy = 0f;
        }

        parkourDistance += scrollSpeed * 0.25f;
        if (!finishGateSpawned && parkourDistance >= PARKOUR_TARGET_DISTANCE) {
            finishGateSpawned = true;
            finishGateX = width + 140f;
        }

        if (finishGateSpawned) {
            finishGateX -= scrollSpeed;
            if (Math.abs(finishGateX + 40f - playerX) < 48f) {
                score += 100;
                if (lives < 3) lives++;
                spawnSparkles(playerX, playerY, 0xFFFFD600, 26);
                if (listener != null) listener.onStarCollected();
                int nextStage = (stage % 6) + 1;
                loadParkourStage(nextStage);
                return;
            } else if (finishGateX < -100f) {
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
            if (farthestX < width - 140f) {
                spawnParkourChunk(width + 60f);
            }
        }

        Iterator<Collectible> colIt = collectibles.iterator();
        while (colIt.hasNext()) {
            Collectible c = colIt.next();
            c.x -= scrollSpeed;
            if (magnetFrames > 0 && (c.type == 0 || c.type == 1) && Math.abs(c.x - playerX) < 280f) {
                c.x += (playerX - c.x) * 0.18f;
                c.y += (playerY - c.y) * 0.18f;
            }
            float dx = c.x - playerX;
            float dy = c.y - playerY;
            if (Math.sqrt(dx * dx + dy * dy) < (playerRadius + 24f)) {
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
            } else if (c.x < -60f) {
                colIt.remove();
            }
        }

        Iterator<Obstacle> obIt = obstacles.iterator();
        while (obIt.hasNext()) {
            Obstacle ob = obIt.next();
            ob.x -= scrollSpeed;
            if (ob.type == 1) {
                ob.y = ob.baseY + (float) Math.sin(animTick * 1.4f + ob.phase) * 36f;
            }

            boolean hitX = (playerX + playerRadius * 0.68f > ob.x) && (playerX - playerRadius * 0.68f < ob.x + ob.width);
            boolean hitY = (playerY + playerRadius * 0.68f > ob.y) && (playerY - playerRadius * 0.68f < ob.y + ob.height);

            if (hitX && hitY && invulnerableFrames == 0) {
                handlePlayerDamage();
                obIt.remove();
            } else if (ob.x + ob.width < -60f) {
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
