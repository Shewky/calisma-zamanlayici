package com.calismazamanlayici.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * İlerlemeli ve Zıplamalı Macera Oyunu ("Süper Yıldız Zıplama Macerası"):
 * - Karakter ilerleyen platformlar üzerinde koşar, engellerin üzerinden zıplar (çift zıplama destekli!),
 *   altın yıldızları (⭐), hız elmaslarını (💎) ve koruyucu kalkanları (🛡️) toplar.
 * - Her 150 puanda bir sonraki aşamaya (Yeşil Vadi -> Altın Çöl -> Kristal Kule -> Uzay Yolu -> Ejderha Dağı) geçer!
 */
public class PlatformerJumpGameView extends View {

    public interface JumpGameListener {
        void onStatsUpdated(int score, int stage, String stageName, int lives);
        void onJumpTriggered();
        void onStarCollected();
        void onObstacleHit();
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private JumpGameListener listener;

    private boolean running = false;
    private int score = 0;
    private int stage = 1;
    private int lives = 3;
    private boolean hasShield = false;
    private int invulnerableFrames = 0;

    // Karakter fizik değişkenleri
    private float playerX = 140f;
    private float playerY = 300f;
    private float playerVy = 0f;
    private float playerRadius = 32f;
    private int jumpCount = 0;
    private float groundY = 420f;
    private float scrollSpeed = 8.5f;

    private static class Obstacle {
        float x;
        float width;
        float height;
        int type; // 0: Kaktüs/Diken, 1: Yüksek Bariyer
    }

    private static class Collectible {
        float x;
        float y;
        int type; // 0: Altın Yıldız (+15), 1: Süper Elmas (+30), 2: Kalkan (🛡️)
    }

    private static class Cloud {
        float x;
        float y;
        float size;
    }

    private static classSparkle {
        float x, y, vx, vy;
        int color;
        int life;
    }

    private final List<Obstacle> obstacles = new ArrayList<>();
    private final List<Collectible> collectibles = new ArrayList<>();
    private final List<Cloud> clouds = new ArrayList<>();
    private final List<classSparkle> sparkles = new ArrayList<>();

    private static final String[] STAGE_NAMES = new String[]{
            "Yeşil Vadi",
            "Altın Çöl",
            "Kristal Kule",
            "Uzay Yolu",
            "Volkan Dağı",
            "Şampiyonlar Adası"
    };

    private static final int[] SKY_COLORS = new int[]{
            0xFF81D4FA, 0xFFFFE082, 0xFFCE93D8, 0xFF1A237E, 0xFFFFAB91, 0xFF80CBC4
    };

    private static final int[] GROUND_COLORS = new int[]{
            0xFF43A047, 0xFFF57C00, 0xFF7B1FA2, 0xFF00ACC1, 0xFFD84315, 0xFF00897B
    };

    public PlatformerJumpGameView(Context context, JumpGameListener listener) {
        super(context);
        this.listener = listener;
        resetGame();
    }

    public void resetGame() {
        score = 0;
        stage = 1;
        lives = 3;
        hasShield = false;
        invulnerableFrames = 0;
        jumpCount = 0;
        playerVy = 0f;
        obstacles.clear();
        collectibles.clear();
        clouds.clear();
        sparkles.clear();

        for (int i = 0; i < 4; i++) {
            Cloud c = new Cloud();
            c.x = i * 260f + 60f;
            c.y = 60f + random.nextInt(90);
            c.size = 38f + random.nextInt(22);
            clouds.add(c);
        }
        notifyStats();
    }

    public void startGame() {
        running = true;
        invalidate();
    }

    public void stopGame() {
        running = false;
    }

    /**
     * Ekrana dokunulduğunda veya "⬆️ Zıpla!" butonuna basıldığında karakteri zıplatır (Çift zıplama destekli).
     */
    public void triggerJump() {
        if (jumpCount < 2) {
            playerVy = (jumpCount == 0) ? -22f : -19f;
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
            triggerJump();
            return true;
        }
        return super.onTouchEvent(event);
    }

    private void spawnSparkles(float cx, float cy, int color, int count) {
        for (int i = 0; i < count; i++) {
            classSparkle s = new classSparkle();
            s.x = cx;
            s.y = cy;
            double ang = random.nextDouble() * Math.PI * 2;
            float spd = 3f + random.nextFloat() * 7f;
            s.vx = (float) Math.cos(ang) * spd;
            s.vy = (float) Math.sin(ang) * spd;
            s.color = color;
            s.life = 16;
            sparkles.add(s);
        }
    }

    private void notifyStats() {
        if (listener != null) {
            String stName = STAGE_NAMES[(stage - 1) % STAGE_NAMES.length];
            listener.onStatsUpdated(score, stage, stName, lives);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        groundY = h * 0.78f;
        playerX = w * 0.18f;

        int stageIdx = (stage - 1) % SKY_COLORS.length;

        // 1. Gökyüzü ve Zemin Çizimi
        canvas.drawColor(SKY_COLORS[stageIdx]);

        // Bulutlar
        paint.setColor(0xCCFFFFFF);
        for (Cloud c : clouds) {
            canvas.drawCircle(c.x, c.y, c.size, paint);
            canvas.drawCircle(c.x + c.size * 0.7f, c.y + 6f, c.size * 0.8f, paint);
            if (running) {
                c.x -= scrollSpeed * 0.3f;
                if (c.x < -100f) {
                    c.x = w + 80f;
                    c.y = 50f + random.nextInt(100);
                }
            }
        }

        // Zemin
        paint.setColor(GROUND_COLORS[stageIdx]);
        canvas.drawRect(0, groundY, w, h, paint);
        paint.setColor(0x33000000);
        canvas.drawRect(0, groundY, w, groundY + 10f, paint);

        if (running) {
            updatePhysicsAndSpawns(w);
        }

        // 2. Toplanabilir Yıldızlar, Elmaslar ve Kalkanlar
        for (Collectible col : collectibles) {
            if (col.type == 0) {
                paint.setColor(0xFFFFD600);
                canvas.drawCircle(col.x, col.y, 20f, paint);
                paint.setColor(0xFFFF6F00);
                paint.setTextSize(26f);
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("★", col.x, col.y + 9f, paint);
            } else if (col.type == 1) {
                paint.setColor(0xFF00E5FF);
                canvas.drawCircle(col.x, col.y, 22f, paint);
                paint.setColor(0xFF004D40);
                paint.setTextSize(24f);
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("💎", col.x, col.y + 8f, paint);
            } else {
                paint.setColor(0xFF69F0AE);
                canvas.drawCircle(col.x, col.y, 22f, paint);
                paint.setColor(0xFF1B5E20);
                paint.setTextSize(22f);
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("🛡️", col.x, col.y + 8f, paint);
            }
        }

        // 3. Engeller
        for (Obstacle ob : obstacles) {
            if (ob.type == 0) {
                paint.setColor(0xFFD32F2F);
            } else {
                paint.setColor(0xFF5D4037);
            }
            RectF r = new RectF(ob.x, groundY - ob.height, ob.x + ob.width, groundY);
            canvas.drawRoundRect(r, 12f, 12f, paint);
            // Üst uyarı şeridi
            paint.setColor(0xFFFFEB3B);
            canvas.drawRect(ob.x + 4f, groundY - ob.height + 6f, ob.x + ob.width - 4f, groundY - ob.height + 14f, paint);
        }

        // 4. Karakter Çizimi
        if (invulnerableFrames % 4 < 2) {
            if (hasShield) {
                paint.setColor(0x6600E676);
                canvas.drawCircle(playerX, playerY, playerRadius + 14f, paint);
            }
            paint.setColor(0xFFFF5722);
            canvas.drawCircle(playerX, playerY, playerRadius, paint);
            // Gülen yüz ve gözler
            paint.setColor(0xFFFFFFFF);
            canvas.drawCircle(playerX + 10f, playerY - 8f, 7f, paint);
            paint.setColor(0xFF1F2937);
            canvas.drawCircle(playerX + 12f, playerY - 8f, 3.5f, paint);
        }

        // 5. Parçacık Efektleri
        Iterator<classSparkle> spIt = sparkles.iterator();
        while (spIt.hasNext()) {
            classSparkle s = spIt.next();
            s.x += s.vx;
            s.y += s.vy;
            s.life--;
            paint.setColor(s.color);
            canvas.drawCircle(s.x, s.y, Math.max(2f, s.life * 0.45f), paint);
            if (s.life <= 0) {
                spIt.remove();
            }
        }

        // Üst Bilgi Yazısı
        paint.setColor(0xFF1F2937);
        paint.setTextSize(28f);
        paint.setFakeBoldText(true);
        paint.setTextAlign(Paint.Align.LEFT);
        String stName = STAGE_NAMES[(stage - 1) % STAGE_NAMES.length];
        canvas.drawText("⭐ Puan: " + score + "  •  🏞️ Aşama " + stage + ": " + stName, 24f, 42f, paint);

        if (running) {
            postInvalidateDelayed(16L);
        }
    }

    private void updatePhysicsAndSpawns(int width) {
        if (invulnerableFrames > 0) {
            invulnerableFrames--;
        }

        // Yerçekimi ve zıplama hareketi
        playerY += playerVy;
        playerVy += 1.25f;

        if (playerY + playerRadius >= groundY) {
            playerY = groundY - playerRadius;
            playerVy = 0f;
            jumpCount = 0;
        }

        scrollSpeed = 8.0f + Math.min(7.0f, stage * 0.9f);

        // Yeni engel üretimi
        if (obstacles.isEmpty() || (width - obstacles.get(obstacles.size() - 1).x) > (280 + random.nextInt(220))) {
            if (random.nextInt(100) < 65) {
                Obstacle ob = new Obstacle();
                ob.x = width + 40f;
                ob.type = random.nextInt(2);
                ob.width = (ob.type == 0) ? 44f : 54f;
                ob.height = (ob.type == 0) ? 58f : 78f;
                obstacles.add(ob);
            }
        }

        // Yeni yıldız / elmas / kalkan üretimi
        if (collectibles.isEmpty() || (width - collectibles.get(collectibles.size() - 1).x) > (190 + random.nextInt(160))) {
            Collectible c = new Collectible();
            c.x = width + 60f;
            c.y = groundY - 70f - random.nextInt(130);
            int roll = random.nextInt(100);
            if (roll < 72) {
                c.type = 0; // Altın Yıldız
            } else if (roll < 92) {
                c.type = 1; // Elmas
            } else {
                c.type = 2; // Kalkan
            }
            collectibles.add(c);
        }

        // Yıldız çarpışma kontrolü
        Iterator<Collectible> colIt = collectibles.iterator();
        while (colIt.hasNext()) {
            Collectible c = colIt.next();
            c.x -= scrollSpeed;
            float dx = c.x - playerX;
            float dy = c.y - playerY;
            if (Math.sqrt(dx * dx + dy * dy) < (playerRadius + 24f)) {
                if (c.type == 0) {
                    score += 15;
                    spawnSparkles(c.x, c.y, 0xFFFFD600, 12);
                } else if (c.type == 1) {
                    score += 30;
                    spawnSparkles(c.x, c.y, 0xFF00E5FF, 16);
                } else {
                    hasShield = true;
                    score += 20;
                    spawnSparkles(c.x, c.y, 0xFF69F0AE, 14);
                }
                int newStage = 1 + (score / 150);
                if (newStage > stage) {
                    stage = newStage;
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

        // Engel çarpışma kontrolü
        Iterator<Obstacle> obIt = obstacles.iterator();
        while (obIt.hasNext()) {
            Obstacle ob = obIt.next();
            ob.x -= scrollSpeed;

            boolean hitX = (playerX + playerRadius * 0.7f > ob.x) && (playerX - playerRadius * 0.7f < ob.x + ob.width);
            boolean hitY = (playerY + playerRadius * 0.8f > groundY - ob.height);

            if (hitX && hitY && invulnerableFrames == 0) {
                if (hasShield) {
                    hasShield = false;
                    invulnerableFrames = 45;
                    spawnSparkles(playerX, playerY, 0xFF69F0AE, 15);
                } else {
                    invulnerableFrames = 55;
                    lives--;
                    spawnSparkles(playerX, playerY, 0xFFD32F2F, 15);
                    if (listener != null) {
                        listener.onObstacleHit();
                    }
                    if (lives <= 0) {
                        lives = 3;
                    }
                    notifyStats();
                }
                obIt.remove();
            } else if (ob.x + ob.width < -50f) {
                score += 5;
                int newStage = 1 + (score / 150);
                if (newStage > stage) {
                    stage = newStage;
                }
                notifyStats();
                obIt.remove();
            }
        }
    }
}
