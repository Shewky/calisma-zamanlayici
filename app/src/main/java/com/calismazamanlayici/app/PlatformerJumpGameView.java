package com.calismazamanlayici.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * 6 Farklı Parkurlu, Çok Katmanlı Platform, Trambolin, Çukur ve Bitiş Kapılı
 * "Süper Yıldız Zıplama Macerası" Oyunu:
 * - Canlar (3 Kalp) bittiğinde oyun gerçekten sona erer ("Oyun Bitti" ekranı açılır, sınırsız sıfırlanmaz).
 * - Kalpler, puan ve parkur ilerleme çubuğu doğrudan Canvas içinde sabit koordinatta çizilir;
 *   böylece metin kayması veya ekran zıplaması asla yaşanmaz.
 * - Üstteki 6 Parkur Sekmesine (P1..P6) dokunarak istenilen parkur anında seçilebilir veya
 *   %100 Bitiş Kapısına (🏁) ulaşılarak sıradaki parkura geçilir.
 */
public class PlatformerJumpGameView extends View {

    public interface JumpGameListener {
        void onStatsUpdated(int score, int stage, String stageName, int lives);
        void onJumpTriggered();
        void onStarCollected();
        void onObstacleHit();
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path tempPath = new Path();
    private final Random random = new Random();
    private final JumpGameListener listener;

    private boolean running = false;
    private boolean gameOver = false;
    private boolean stageClearedBanner = false;
    private int stageClearedTimer = 0;

    private int score = 0;
    private int highScore = 0;
    private int stage = 1; // 1..6 Parkur
    private int lives = 3;
    private boolean hasShield = false;
    private int invulnerableFrames = 0;

    // Parkur ilerleme mesafesi (her parkur 1800 birim uzunluğunda ve sonunda Bitiş Kapısı 🏁 var)
    private float parkourDistance = 0f;
    private static final float PARKOUR_TARGET_DISTANCE = 1800f;
    private boolean finishGateSpawned = false;
    private float finishGateX = -500f;

    // Karakter fizik değişkenleri
    private float playerX = 140f;
    private float playerY = 260f;
    private float playerVy = 0f;
    private final float playerRadius = 26f;
    private int jumpCount = 0;
    private float groundY = 420f;
    private float scrollSpeed = 7.2f;
    private float animTick = 0f;

    // Çok katlı platform ve trambolin sınıfı
    private static class Platform {
        float x;
        float y;
        float baseY;
        float width;
        float height;
        boolean movingVertically;
        float movePhase;
        boolean hasTrampoline;
        float trampolineOffsetX;
    }

    // Zemin çukuru (Su / Lav / Boşluk çukuru)
    private static class GroundPit {
        float x;
        float width;
        int pitType; // 0: Su, 1: Lav, 2: Uzay/Bulut Boşluğu
    }

    // Çeşitli engeller
    private static class Obstacle {
        float x;
        float y;
        float baseY;
        float width;
        float height;
        int type; // 0: Diken/Kaktüs, 1: Hareketli Uçan Dron/Ateş Topu, 2: Yuvarlanan Kaya/Buz, 3: Üst Sarkıt/Lazer
        float phase;
    }

    private static class Collectible {
        float x;
        float y;
        int type; // 0: Altın Yıldız (+15), 1: Süper Elmas (+30), 2: Kalkan (🛡️), 3: Can Kalbi (❤️)
    }

    private static class Cloud {
        float x;
        float y;
        float size;
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
    private final List<Cloud> clouds = new ArrayList<>();
    private final List<Sparkle> sparkles = new ArrayList<>();

    // Üst parkur seçici sekme kutuları ve Oyun Bitti butonları
    private final RectF[] parkourTabRects = new RectF[6];
    private final RectF btnRetryRect = new RectF();
    private final RectF btnNextParkourRect = new RectF();

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
            0xFF81D4FA, // 1: Zümrüt Orman Açık Gökyüzü
            0xFFB3E5FC, // 2: Bulut Adaları Ferah Gökyüzü
            0xFF3E2723, // 3: Lav Mağarası Koyu Kehribar
            0xFFE1F5FE, // 4: Kristal Buzul Kutup Gökyüzü
            0xFF0D1B2A, // 5: Uzay İstasyonu Derin Lacivert
            0xFFFFF8E1  // 6: Altın Tapınak Gün Batımı
    };

    private static final int[] GROUND_COLORS = new int[]{
            0xFF2E7D32, // 1: Orman Çimeni
            0xFF0288D1, // 2: Gökyüzü Kristal Zemin
            0xFF4E342E, // 3: Volkanik Kaya
            0xFF4FC3F7, // 4: Buzul Zemini
            0xFF37474F, // 5: Metalik Uzay Güvertesi
            0xFFD4AF37  // 6: Altın Tapınak Taşı
    };

    private static final int[] PLATFORM_COLORS = new int[]{
            0xFF8D6E63, // 1: Ahşap Köprü
            0xFFFFFFFF, // 2: Beyaz Bulut Platform
            0xFF795548, // 3: Bazalt Taş Köprü
            0xFF81D4FA, // 4: Şeffaf Buz Kütleleri
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
        this.parkourDistance = 0f;
        this.finishGateSpawned = false;
        this.finishGateX = -500f;
        this.jumpCount = 0;
        this.playerVy = 0f;
        this.playerY = 220f;
        this.invulnerableFrames = 40;

        platforms.clear();
        pits.clear();
        obstacles.clear();
        collectibles.clear();
        clouds.clear();
        sparkles.clear();

        for (int i = 0; i < 5; i++) {
            Cloud c = new Cloud();
            c.x = i * 230f + 50f;
            c.y = 95f + random.nextInt(80);
            c.size = 30f + random.nextInt(18);
            clouds.add(c);
        }

        // Başlangıçta oyuncunun hemen önünde parkura özgü örnek platformlar oluştur
        spawnParkourChunk(420f);
        spawnParkourChunk(820f);
        notifyStats();
    }

    public void startGame() {
        if (gameOver) {
            resetGame();
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
        if (!running) {
            running = true;
            invalidate();
        }
        if (jumpCount < 2) {
            // Uzay parkurunda (Stage 5) ve Bulut parkurunda (Stage 2) daha yüksek süzülmeli zıplama
            float boost = (stage == 5) ? 1.12f : (stage == 2 ? 1.06f : 1.0f);
            playerVy = ((jumpCount == 0) ? -19.5f : -17.0f) * boost;
            jumpCount++;
            spawnSparkles(playerX, playerY + playerRadius, 0xFFFFFFFF, 8);
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

            // 1. Üstteki Parkur Seçim Sekmelerine (P1..P6) dokunuldu mu kontrol et
            for (int i = 0; i < 6; i++) {
                if (parkourTabRects[i].contains(tx, ty)) {
                    selectParkour(i + 1);
                    return true;
                }
            }

            // 2. Oyun Bitti ekranındaysa butonları kontrol et
            if (gameOver) {
                if (btnNextParkourRect.contains(tx, ty)) {
                    selectParkour((stage % 6) + 1);
                    return true;
                }
                // Tekrar oyna veya ekranın herhangi bir yeri
                resetGame();
                running = true;
                invalidate();
                return true;
            }

            // 3. Normal oyun sırasında zıpla
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
            float spd = 2.5f + random.nextFloat() * 6.5f;
            s.vx = (float) Math.cos(ang) * spd;
            s.vy = (float) Math.sin(ang) * spd;
            s.color = color;
            s.life = 16;
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

    /**
     * Her parkurun (1..6) kendine özgü platform, trambolin, çukur ve engel dizilimini üretir.
     */
    private void spawnParkourChunk(float startX) {
        if (finishGateSpawned) return;
        float gY = (groundY > 100f) ? groundY : 380f;
        int pattern = random.nextInt(3);

        if (stage == 1) {
            // Parkur 1: Zümrüt Orman Köprüleri (Ahşap çok katlı köprüler + Mantar Trambolin + Dere Çukuru)
            if (pattern == 0) {
                GroundPit pit = new GroundPit();
                pit.x = startX + 40f;
                pit.width = 130f;
                pit.pitType = 0; // Su deresi
                pits.add(pit);

                Platform p = new Platform();
                p.x = startX + 20f;
                p.baseY = gY - 85f;
                p.y = p.baseY;
                p.width = 170f;
                p.height = 20f;
                platforms.add(p);

                spawnStarArc(startX + 45f, p.baseY - 45f, 3, 45f, 0);
            } else if (pattern == 1) {
                Platform p1 = new Platform();
                p1.x = startX;
                p1.baseY = gY - 75f;
                p1.y = p1.baseY;
                p1.width = 130f;
                p1.height = 20f;
                p1.hasTrampoline = true;
                p1.trampolineOffsetX = 45f;
                platforms.add(p1);

                Platform p2 = new Platform();
                p2.x = startX + 165f;
                p2.baseY = gY - 155f;
                p2.y = p2.baseY;
                p2.width = 150f;
                p2.height = 20f;
                platforms.add(p2);

                spawnStarArc(startX + 185f, p2.baseY - 42f, 3, 45f, 1);
            } else {
                Obstacle ob = new Obstacle();
                ob.x = startX + 60f;
                ob.width = 46f;
                ob.height = 54f;
                ob.baseY = gY - ob.height;
                ob.y = ob.baseY;
                ob.type = 2; // Yuvarlanan kütük
                obstacles.add(ob);
                spawnStarArc(startX + 40f, gY - 115f, 3, 40f, 0);
            }
        } else if (stage == 2) {
            // Parkur 2: Kayan Bulut Adaları (Yukarı-aşağı süzülen bulut platformlar + Gökyüzü boşlukları)
            GroundPit pit = new GroundPit();
            pit.x = startX + 30f;
            pit.width = 160f;
            pit.pitType = 2;
            pits.add(pit);

            Platform cloudPlat = new Platform();
            cloudPlat.x = startX + 10f;
            cloudPlat.baseY = gY - 95f - random.nextInt(45);
            cloudPlat.y = cloudPlat.baseY;
            cloudPlat.width = 165f;
            cloudPlat.height = 24f;
            cloudPlat.movingVertically = true;
            cloudPlat.movePhase = random.nextFloat() * 6.28f;
            platforms.add(cloudPlat);

            if (pattern == 1) {
                Obstacle storm = new Obstacle();
                storm.x = startX + 210f;
                storm.width = 48f;
                storm.height = 48f;
                storm.baseY = gY - 135f;
                storm.y = storm.baseY;
                storm.type = 1; // Hareketli şimşek bulutu
                storm.phase = random.nextFloat() * 6.28f;
                obstacles.add(storm);
            }
            spawnStarArc(startX + 35f, cloudPlat.baseY - 50f, 3, 48f, (pattern == 2) ? 2 : 0);
        } else if (stage == 3) {
            // Parkur 3: Lav Mağarası & Trambolin (Lav çukurları + Süper Yay Trambolinler + Zıplayan Ateş Topları)
            GroundPit lava = new GroundPit();
            lava.x = startX + 50f;
            lava.width = 155f;
            lava.pitType = 1; // Lav çukuru
            pits.add(lava);

            Platform beforeLava = new Platform();
            beforeLava.x = startX - 25f;
            beforeLava.baseY = gY - 55f;
            beforeLava.y = beforeLava.baseY;
            beforeLava.width = 85f;
            beforeLava.height = 20f;
            beforeLava.hasTrampoline = true;
            beforeLava.trampolineOffsetX = 20f;
            platforms.add(beforeLava);

            Platform highRock = new Platform();
            highRock.x = startX + 110f;
            highRock.baseY = gY - 145f;
            highRock.y = highRock.baseY;
            highRock.width = 140f;
            highRock.height = 22f;
            platforms.add(highRock);

            Obstacle fireball = new Obstacle();
            fireball.x = startX + 125f;
            fireball.width = 40f;
            fireball.height = 40f;
            fireball.baseY = gY - 75f;
            fireball.y = fireball.baseY;
            fireball.type = 1; // Lavdan yükselen ateş topu
            fireball.phase = random.nextFloat() * 6.28f;
            obstacles.add(fireball);

            spawnStarArc(startX + 125f, highRock.baseY - 45f, 3, 42f, 1);
        } else if (stage == 4) {
            // Parkur 4: Kristal Buzul Tepeleri (2 Katlı Buz Terasları + Üst Buz Sarkıtları)
            Platform ice1 = new Platform();
            ice1.x = startX;
            ice1.baseY = gY - 75f;
            ice1.y = ice1.baseY;
            ice1.width = 135f;
            ice1.height = 20f;
            platforms.add(ice1);

            Platform ice2 = new Platform();
            ice2.x = startX + 150f;
            ice2.baseY = gY - 140f;
            ice2.y = ice2.baseY;
            ice2.width = 140f;
            ice2.height = 20f;
            platforms.add(ice2);

            if (pattern == 0) {
                // Alt yolda buz dikeni
                Obstacle spike = new Obstacle();
                spike.x = startX + 70f;
                spike.width = 46f;
                spike.height = 56f;
                spike.baseY = gY - spike.height;
                spike.y = spike.baseY;
                spike.type = 0;
                obstacles.add(spike);
            } else {
                // Üst sarkıt engel
                Obstacle icicle = new Obstacle();
                icicle.x = startX + 180f;
                icicle.width = 42f;
                icicle.height = 65f;
                icicle.baseY = gY - 245f;
                icicle.y = icicle.baseY;
                icicle.type = 3;
                obstacles.add(icicle);
            }
            spawnStarArc(startX + 20f, ice1.baseY - 42f, 3, 45f, 0);
        } else if (stage == 5) {
            // Parkur 5: Uzay İstasyonu & Lazer Koridoru (Ay Yerçekimi + Siber Platformlar + Uçan Dronlar)
            Platform cyber = new Platform();
            cyber.x = startX + 20f;
            cyber.baseY = gY - 110f;
            cyber.y = cyber.baseY;
            cyber.width = 160f;
            cyber.height = 18f;
            cyber.movingVertically = (pattern == 1);
            cyber.movePhase = random.nextFloat() * 6.28f;
            platforms.add(cyber);

            Obstacle drone = new Obstacle();
            drone.x = startX + 110f;
            drone.width = 46f;
            drone.height = 46f;
            drone.baseY = (pattern == 0) ? (gY - 60f) : (gY - 175f);
            drone.y = drone.baseY;
            drone.type = 1; // Uçan Uzay Dronu
            drone.phase = random.nextFloat() * 6.28f;
            obstacles.add(drone);

            spawnStarArc(startX + 40f, cyber.baseY - 48f, 3, 48f, (pattern == 2) ? 2 : 1);
        } else {
            // Parkur 6: Altın Tapınak Piramidi (3 Basamaklı Piramit + Trambolin + Hazine Yıldızları)
            for (int step = 0; step < 3; step++) {
                Platform stair = new Platform();
                stair.x = startX + step * 95f;
                stair.baseY = gY - 60f - step * 52f;
                stair.y = stair.baseY;
                stair.width = 90f;
                stair.height = 20f;
                if (step == 2) {
                    stair.hasTrampoline = true;
                    stair.trampolineOffsetX = 25f;
                }
                platforms.add(stair);
            }
            Obstacle guard = new Obstacle();
            guard.x = startX + 130f;
            guard.width = 44f;
            guard.height = 50f;
            guard.baseY = gY - guard.height;
            guard.y = guard.baseY;
            guard.type = 2;
            obstacles.add(guard);

            spawnStarArc(startX + 30f, gY - 190f, 4, 48f, 1);
        }

        // Canı azalmışsa ekstra Can Kalbi (❤️) şansı
        if (lives < 3 && random.nextInt(100) < 28) {
            Collectible heart = new Collectible();
            heart.x = startX + 90f;
            heart.y = gY - 135f;
            heart.type = 3; // ❤️ Can Kalbi
            collectibles.add(heart);
        }
    }

    private void spawnStarArc(float startX, float y, int count, float spacing, int preferredType) {
        for (int i = 0; i < count; i++) {
            Collectible c = new Collectible();
            c.x = startX + i * spacing;
            c.y = y - (i == 1 ? 18f : 0f);
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

        float topHudHeight = 86f;
        groundY = h * 0.80f;
        playerX = w * 0.18f;

        int stageIdx = (stage - 1) % SKY_COLORS.length;

        // 1. Arka Plan Gökyüzü
        canvas.drawColor(SKY_COLORS[stageIdx]);

        // Uzay veya gece parkurlarında arka plan yıldızları
        if (stage == 3 || stage == 5) {
            paint.setColor(0x66FFFFFF);
            for (int i = 0; i < 12; i++) {
                float sx = ((i * 97 + (int) (parkourDistance * 0.2f)) % w);
                if (sx < 0) sx += w;
                float sy = topHudHeight + 20f + ((i * 53) % (int) Math.max(60f, groundY - topHudHeight - 40f));
                canvas.drawCircle(sx, sy, (i % 3) + 2f, paint);
            }
        }

        // Bulutlar
        paint.setColor(stage == 3 ? 0x44FFAB91 : 0xCCFFFFFF);
        for (Cloud c : clouds) {
            canvas.drawCircle(c.x, c.y, c.size, paint);
            canvas.drawCircle(c.x + c.size * 0.65f, c.y + 5f, c.size * 0.78f, paint);
            if (running && !gameOver) {
                c.x -= scrollSpeed * 0.25f;
                if (c.x < -100f) {
                    c.x = w + 80f;
                    c.y = topHudHeight + 25f + random.nextInt(70);
                }
            }
        }

        // 2. Zemin ve Çukurlar (Su / Lav / Boşluk)
        paint.setColor(GROUND_COLORS[stageIdx]);
        canvas.drawRect(0, groundY, w, h, paint);
        paint.setColor(0x33000000);
        canvas.drawRect(0, groundY, w, groundY + 9f, paint);

        for (GroundPit pit : pits) {
            // Çukur boşluğunu gökyüzü rengiyle aç ve alt kısmına su/lav çiz
            paint.setColor(SKY_COLORS[stageIdx]);
            canvas.drawRect(pit.x, groundY - 2f, pit.x + pit.width, h, paint);
            if (pit.pitType == 0) {
                paint.setColor(0xFF0288D1); // Nehir Suyu
                canvas.drawRect(pit.x, groundY + 18f, pit.x + pit.width, h, paint);
            } else if (pit.pitType == 1) {
                paint.setColor(0xFFFF3D00); // Parlayan Lav
                canvas.drawRect(pit.x, groundY + 14f, pit.x + pit.width, h, paint);
                paint.setColor(0xFFFFD600);
                canvas.drawRect(pit.x + 6f, groundY + 14f, pit.x + pit.width - 6f, groundY + 22f, paint);
            }
        }

        if (running && !gameOver) {
            updatePhysicsAndSpawns(w, h);
        }

        // 3. Çok Katmanlı Platformlar ve Trambolinler
        for (Platform p : platforms) {
            paint.setColor(PLATFORM_COLORS[stageIdx]);
            RectF pr = new RectF(p.x, p.y, p.x + p.width, p.y + p.height);
            canvas.drawRoundRect(pr, 10f, 10f, paint);
            paint.setColor(0x44000000);
            canvas.drawRoundRect(new RectF(p.x, p.y + p.height - 6f, p.x + p.width, p.y + p.height), 6f, 6f, paint);

            if (p.hasTrampoline) {
                float tx = p.x + p.trampolineOffsetX;
                paint.setColor(0xFFFF1744);
                canvas.drawRoundRect(new RectF(tx, p.y - 14f, tx + 44f, p.y), 8f, 8f, paint);
                paint.setColor(0xFFFFFFFF);
                canvas.drawCircle(tx + 12f, p.y - 7f, 3.5f, paint);
                canvas.drawCircle(tx + 22f, p.y - 9f, 3.5f, paint);
                canvas.drawCircle(tx + 32f, p.y - 7f, 3.5f, paint);
            }
        }

        // 4. Parkur Bitiş Kapısı (🏁)
        if (finishGateSpawned && finishGateX > -120f) {
            paint.setColor(0xFFFFD600);
            canvas.drawRect(finishGateX, groundY - 165f, finishGateX + 14f, groundY, paint);
            canvas.drawRect(finishGateX + 64f, groundY - 165f, finishGateX + 78f, groundY, paint);
            paint.setColor(0xFF00E676);
            canvas.drawRoundRect(new RectF(finishGateX - 10f, groundY - 190f, finishGateX + 88f, groundY - 145f), 12f, 12f, paint);
            paint.setColor(0xFF0F172A);
            paint.setTextSize(22f);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setFakeBoldText(true);
            canvas.drawText("🏁 BİTİŞ", finishGateX + 39f, groundY - 160f, paint);
        }

        // 5. Toplanabilir Yıldızlar, Elmaslar, Kalkanlar ve Kalpler
        for (Collectible col : collectibles) {
            if (col.type == 0) {
                paint.setColor(0xFFFFD600);
                canvas.drawCircle(col.x, col.y, 18f, paint);
                paint.setColor(0xFFE65100);
                paint.setTextSize(22f);
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("★", col.x, col.y + 8f, paint);
            } else if (col.type == 1) {
                paint.setColor(0xFF00E5FF);
                canvas.drawCircle(col.x, col.y, 20f, paint);
                paint.setColor(0xFF004D40);
                paint.setTextSize(20f);
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("💎", col.x, col.y + 7f, paint);
            } else if (col.type == 2) {
                paint.setColor(0xFF69F0AE);
                canvas.drawCircle(col.x, col.y, 20f, paint);
                paint.setColor(0xFF1B5E20);
                paint.setTextSize(19f);
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("🛡️", col.x, col.y + 7f, paint);
            } else {
                paint.setColor(0xFFFFCDD2);
                canvas.drawCircle(col.x, col.y, 20f, paint);
                paint.setColor(0xFFC62828);
                paint.setTextSize(20f);
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("❤️", col.x, col.y + 7f, paint);
            }
        }

        // 6. Engeller
        for (Obstacle ob : obstacles) {
            if (ob.type == 0) {
                // Üçgen Diken / Buz Dikeni
                paint.setColor(stage == 4 ? 0xFF0288D1 : 0xFFD32F2F);
                tempPath.reset();
                tempPath.moveTo(ob.x, ob.y + ob.height);
                tempPath.lineTo(ob.x + ob.width * 0.5f, ob.y);
                tempPath.lineTo(ob.x + ob.width, ob.y + ob.height);
                tempPath.close();
                canvas.drawPath(tempPath, paint);
            } else if (ob.type == 1) {
                // Hareketli Uçan Dron / Ateş Topu / Şimşek
                paint.setColor(stage == 3 ? 0xFFFF6D00 : (stage == 5 ? 0xFFAA00FF : 0xFFE53935));
                canvas.drawCircle(ob.x + ob.width * 0.5f, ob.y + ob.height * 0.5f, ob.width * 0.5f, paint);
                paint.setColor(0xFFFFEB3B);
                canvas.drawCircle(ob.x + ob.width * 0.5f, ob.y + ob.height * 0.5f, ob.width * 0.22f, paint);
            } else if (ob.type == 3) {
                // Üst Sarkıt / Lazer Bariyeri
                paint.setColor(0xFF00ACC1);
                RectF r = new RectF(ob.x, ob.y, ob.x + ob.width, ob.y + ob.height);
                canvas.drawRoundRect(r, 8f, 8f, paint);
            } else {
                // Yuvarlanan Kaya / Kütük
                paint.setColor(0xFF5D4037);
                RectF r = new RectF(ob.x, ob.y, ob.x + ob.width, ob.y + ob.height);
                canvas.drawRoundRect(r, 14f, 14f, paint);
                paint.setColor(0xFFFFCA28);
                canvas.drawRect(ob.x + 6f, ob.y + 8f, ob.x + ob.width - 6f, ob.y + 15f, paint);
            }
        }

        // 7. Karakter Çizimi
        if (invulnerableFrames % 4 < 2) {
            if (hasShield) {
                paint.setColor(0x6600E676);
                canvas.drawCircle(playerX, playerY, playerRadius + 12f, paint);
            }
            paint.setColor(0xFFFF5722);
            canvas.drawCircle(playerX, playerY, playerRadius, paint);
            paint.setColor(0xFFFFFFFF);
            canvas.drawCircle(playerX + 8f, playerY - 6f, 6f, paint);
            paint.setColor(0xFF1F2937);
            canvas.drawCircle(playerX + 10f, playerY - 6f, 3f, paint);
        }

        // 8. Parçacık Efektleri
        Iterator<Sparkle> spIt = sparkles.iterator();
        while (spIt.hasNext()) {
            Sparkle s = spIt.next();
            s.x += s.vx;
            s.y += s.vy;
            s.life--;
            paint.setColor(s.color);
            canvas.drawCircle(s.x, s.y, Math.max(2f, s.life * 0.4f), paint);
            if (s.life <= 0) {
                spIt.remove();
            }
        }

        // 9. SABİT ÜST GÖSTERGE PANELİ (HUD) VE PARKUR SEÇİCİ SEKMELER (Ekran Kaymasını %100 Önler!)
        drawTopHudAndParkourTabs(canvas, w, topHudHeight);

        // 10. Parkur Tamamlandı Kutlama Bandı
        if (stageClearedBanner && stageClearedTimer > 0) {
            stageClearedTimer--;
            paint.setColor(0xDD1B5E20);
            RectF banner = new RectF(w * 0.12f, h * 0.36f, w * 0.88f, h * 0.54f);
            canvas.drawRoundRect(banner, 18f, 18f, paint);
            paint.setColor(0xFFFFFFFF);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(26f);
            paint.setFakeBoldText(true);
            canvas.drawText("🎉 PARKUR TAMAMLANDI! (+100 BONUS)", w * 0.5f, h * 0.43f, paint);
            paint.setTextSize(20f);
            canvas.drawText("Yeni Parkur: " + STAGE_NAMES[(stage - 1) % STAGE_NAMES.length], w * 0.5f, h * 0.50f, paint);
            if (stageClearedTimer <= 0) {
                stageClearedBanner = false;
            }
        }

        // 11. OYUN BİTTİ (GAME OVER) EKRANI (3 Can Bittiğinde Gösterilir)
        if (gameOver) {
            drawGameOverOverlay(canvas, w, h);
        } else if (running) {
            postInvalidateDelayed(16L);
        }
    }

    private void drawTopHudAndParkourTabs(Canvas canvas, int w, float hudH) {
        // Yarı saydam koyu üst panel
        paint.setColor(0xCC0F172A);
        canvas.drawRect(0, 0, w, hudH, paint);

        // Üst Satır: 6 Parkur Seçme Sekmesi (Dokunarak Parkur Değiştirme)
        float margin = 6f;
        float tabW = (w - margin * 7f) / 6f;
        for (int i = 0; i < 6; i++) {
            float left = margin + i * (tabW + margin);
            parkourTabRects[i].set(left, 6f, left + tabW, 38f);
            boolean active = (stage == i + 1);
            paint.setColor(active ? 0xFFFF6D00 : 0xFF334155);
            canvas.drawRoundRect(parkourTabRects[i], 8f, 8f, paint);

            paint.setColor(0xFFFFFFFF);
            paint.setTextSize(17f);
            paint.setFakeBoldText(active);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(SHORT_TAB_NAMES[i], parkourTabRects[i].centerX(), 27f, paint);
        }

        // Alt Satır Sol: Sabit Genişlikli 3 Kalp (❤️❤️❤️) ve Kalkan
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(22f);
        paint.setFakeBoldText(true);
        StringBuilder heartsSb = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            heartsSb.append(i < lives ? "❤️" : "🖤");
        }
        if (hasShield) {
            heartsSb.append("🛡️");
        }
        paint.setColor(0xFFFFFFFF);
        canvas.drawText(heartsSb.toString(), 10f, 68f, paint);

        // Alt Satır Orta: Parkur İlerleme Çubuğu (%0 -> %100 🏁)
        float barLeft = w * 0.28f;
        float barRight = w * 0.64f;
        float barTop = 52f;
        float barBottom = 70f;
        paint.setColor(0xFF334155);
        canvas.drawRoundRect(new RectF(barLeft, barTop, barRight, barBottom), 9f, 9f, paint);

        float ratio = Math.min(1f, Math.max(0f, parkourDistance / PARKOUR_TARGET_DISTANCE));
        paint.setColor(0xFF00E676);
        canvas.drawRoundRect(new RectF(barLeft, barTop, barLeft + (barRight - barLeft) * ratio, barBottom), 9f, 9f, paint);

        paint.setColor(0xFFFFFFFF);
        paint.setTextSize(15f);
        paint.setTextAlign(Paint.Align.CENTER);
        int pct = (int) (ratio * 100f);
        canvas.drawText("🏁 %" + pct, (barLeft + barRight) * 0.5f, 66f, paint);

        // Alt Satır Sağ: Puan
        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setTextSize(21f);
        paint.setColor(0xFFFFD600);
        canvas.drawText("⭐ " + score, w - 12f, 68f, paint);
    }

    private void drawGameOverOverlay(Canvas canvas, int w, int h) {
        paint.setColor(0xCC0F172A);
        canvas.drawRect(0, 0, w, h, paint);

        RectF card = new RectF(w * 0.08f, h * 0.18f, w * 0.92f, h * 0.84f);
        paint.setColor(0xFFFFFFFF);
        canvas.drawRoundRect(card, 22f, 22f, paint);

        paint.setColor(0xFFD32F2F);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(30f);
        paint.setFakeBoldText(true);
        canvas.drawText("💔 3 CANIN BİTTİ!", w * 0.5f, h * 0.30f, paint);

        paint.setColor(0xFF1E293B);
        paint.setTextSize(22f);
        String stName = STAGE_NAMES[(stage - 1) % STAGE_NAMES.length];
        canvas.drawText("Parkur " + stage + ": " + stName, w * 0.5f, h * 0.40f, paint);

        paint.setColor(0xFF0F172A);
        paint.setTextSize(24f);
        canvas.drawText("⭐ Toplam Puanın: " + score + "   •   🏆 Rekor: " + highScore, w * 0.5f, h * 0.50f, paint);

        // Tekrar Oyna ve Sonraki Parkur Butonları
        btnRetryRect.set(w * 0.13f, h * 0.60f, w * 0.48f, h * 0.76f);
        btnNextParkourRect.set(w * 0.52f, h * 0.60f, w * 0.87f, h * 0.76f);

        paint.setColor(0xFF16A34A);
        canvas.drawRoundRect(btnRetryRect, 14f, 14f, paint);
        paint.setColor(0xFF2563EB);
        canvas.drawRoundRect(btnNextParkourRect, 14f, 14f, paint);

        paint.setColor(0xFFFFFFFF);
        paint.setTextSize(20f);
        canvas.drawText("🔄 Tekrar Oyna", btnRetryRect.centerX(), btnRetryRect.centerY() + 7f, paint);
        canvas.drawText("🗺️ Diğer Parkur", btnNextParkourRect.centerX(), btnNextParkourRect.centerY() + 7f, paint);
    }

    private void updatePhysicsAndSpawns(int width, int height) {
        animTick += 0.09f;
        if (invulnerableFrames > 0) {
            invulnerableFrames--;
        }

        // Parkura özel hız ve yerçekimi
        scrollSpeed = 6.8f + (stage * 0.55f);
        float gravity = (stage == 5) ? 0.90f : (stage == 2 ? 1.02f : 1.18f);

        float prevBottom = playerY + playerRadius;
        playerY += playerVy;
        playerVy += gravity;
        float newBottom = playerY + playerRadius;

        // 1. Platformların hareketi ve platform üzerine iniş kontrolü
        boolean landedOnPlatform = false;
        Iterator<Platform> platIt = platforms.iterator();
        while (platIt.hasNext()) {
            Platform p = platIt.next();
            p.x -= scrollSpeed;
            if (p.movingVertically) {
                p.y = p.baseY + (float) Math.sin(animTick + p.movePhase) * 28f;
            }

            // Trambolin kontrolü
            if (p.hasTrampoline) {
                float tx = p.x + p.trampolineOffsetX;
                if (playerVy >= 0 && playerX + playerRadius * 0.6f >= tx && playerX - playerRadius * 0.6f <= tx + 44f
                        && prevBottom <= p.y + 12f && newBottom >= p.y - 16f) {
                    playerY = p.y - 14f - playerRadius;
                    playerVy = -25.5f; // Süper Trambolin Fırlatışı!
                    jumpCount = 1;
                    spawnSparkles(playerX, playerY + playerRadius, 0xFFFF1744, 14);
                    if (listener != null) listener.onJumpTriggered();
                    landedOnPlatform = true;
                }
            }

            // Platform üzerine basma kontrolü
            if (!landedOnPlatform && playerVy >= 0
                    && playerX + playerRadius * 0.55f >= p.x
                    && playerX - playerRadius * 0.55f <= p.x + p.width
                    && prevBottom <= p.y + 16f
                    && newBottom >= p.y) {
                playerY = p.y - playerRadius;
                playerVy = 0f;
                jumpCount = 0;
                landedOnPlatform = true;
            }

            if (p.x + p.width < -80f) {
                platIt.remove();
            }
        }

        // 2. Zemin çukuru (Su / Lav / Boşluk) kontrolü
        boolean overPit = false;
        Iterator<GroundPit> pitIt = pits.iterator();
        while (pitIt.hasNext()) {
            GroundPit pit = pitIt.next();
            pit.x -= scrollSpeed;
            if (playerX > pit.x + 12f && playerX < pit.x + pit.width - 12f) {
                overPit = true;
            }
            if (pit.x + pit.width < -80f) {
                pitIt.remove();
            }
        }

        if (!landedOnPlatform) {
            if (!overPit && playerY + playerRadius >= groundY) {
                playerY = groundY - playerRadius;
                playerVy = 0f;
                jumpCount = 0;
            } else if (overPit && playerY - playerRadius > groundY + 26f) {
                // Çukura düştü!
                handlePlayerDamage();
                playerY = groundY - playerRadius - 90f;
                playerVy = -14f;
                jumpCount = 1;
            }
        }

        // Tavan sınırı
        if (playerY - playerRadius < 90f) {
            playerY = 90f + playerRadius;
            if (playerVy < 0) playerVy = 0f;
        }

        // 3. Parkur İlerlemesi ve Bitiş Kapısı (🏁) Kontrolü
        parkourDistance += scrollSpeed * 0.25f;
        if (!finishGateSpawned && parkourDistance >= PARKOUR_TARGET_DISTANCE) {
            finishGateSpawned = true;
            finishGateX = width + 140f;
        }

        if (finishGateSpawned) {
            finishGateX -= scrollSpeed;
            if (Math.abs(finishGateX + 35f - playerX) < 45f) {
                // Parkur tamamlandı! +100 Bonus Puan ve 1 Can Ödülü, sıradaki parkura geç!
                score += 100;
                if (lives < 3) lives++;
                spawnSparkles(playerX, playerY, 0xFFFFD600, 24);
                if (listener != null) listener.onStarCollected();
                stageClearedBanner = true;
                stageClearedTimer = 95;
                int nextStage = (stage % 6) + 1;
                loadParkourStage(nextStage);
                return;
            } else if (finishGateX < -100f) {
                int nextStage = (stage % 6) + 1;
                loadParkourStage(nextStage);
                return;
            }
        } else {
            // Yeni parkur parçası üretimi
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

        // 4. Yıldız / Elmas / Kalkan / Kalp Çarpışma Kontrolü
        Iterator<Collectible> colIt = collectibles.iterator();
        while (colIt.hasNext()) {
            Collectible c = colIt.next();
            c.x -= scrollSpeed;
            float dx = c.x - playerX;
            float dy = c.y - playerY;
            if (Math.sqrt(dx * dx + dy * dy) < (playerRadius + 22f)) {
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

        // 5. Engel Hareketi ve Çarpışma Kontrolü
        Iterator<Obstacle> obIt = obstacles.iterator();
        while (obIt.hasNext()) {
            Obstacle ob = obIt.next();
            ob.x -= scrollSpeed;
            if (ob.type == 1) {
                ob.y = ob.baseY + (float) Math.sin(animTick * 1.4f + ob.phase) * 34f;
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
