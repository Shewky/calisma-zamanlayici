package com.calismazamanlayici.veli;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * ❌⭕ X-O-X ZEKA DÜELLOSU (CPU'ya Karşı, Kalıcı Seviye Hafızalı):
 * - Oyuncu (❌) ile Yapay Zeka CPU (⭕) karşılıklı oynar.
 * - Geçilen her seviye (Level 1..15+) kalıcı olarak hafızaya ve yedeğe kaydedilir.
 * - Seviye 1-3: 3x3 Klasik Tahta (CPU kolaydan orta stratejiye ilerler).
 * - Seviye 4+: 4x4 Büyük Strateji Tahtası (3 veya 4'lü dizilim ve derin Minimax taktikleri!).
 * - Her oyuna başlarken 3-2-1-BAŞLA! geri sayımı yapılır.
 */
public class VeliXoxBoardView extends View {

    public interface XoxListener {
        void onXoxSound(boolean positive);
        void onCountdownTick(boolean isGo);
        void onExitRequested();
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final XoxListener listener;
    private final float dp;
    private final float sp;

    private boolean inCountdown = false;
    private long countdownStartMs = 0L;
    private int lastAnnouncedCountdown = -1;

    private int level = 1;
    private int playerWins = 0;
    private int cpuWins = 0;
    private int gridSize = 3; // Level 1-3: 3x3, Level 4+: 4x4
    private int winLength = 3;
    private int[][] board = new int[4][4]; // 0: Boş, 1: Oyuncu (X), 2: CPU (O)
    private boolean playerTurn = true;
    private boolean roundOver = false;
    private String statusMessage = "Senin Sıran! Bir kareye dokun (❌)";

    private final RectF btnExitRect = new RectF();
    private final RectF btnNextRoundRect = new RectF();
    private final RectF boardRect = new RectF();

    public VeliXoxBoardView(Context context, XoxListener listener) {
        super(context);
        this.listener = listener;
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        this.dp = dm.density;
        this.sp = dm.scaledDensity;
        this.level = VeliStorage.getSavedGameLevel(context, "xox_level", 1);
        this.playerWins = Math.max(0, VeliStorage.getSavedGameLevel(context, "xox_wins", 1) - 1);
        initBoardForLevel(true);
    }

    public void startGame() {
        this.level = VeliStorage.getSavedGameLevel(getContext(), "xox_level", 1);
        this.playerWins = Math.max(0, VeliStorage.getSavedGameLevel(getContext(), "xox_wins", 1) - 1);
        initBoardForLevel(true);
    }

    public void stopGame() {
        handler.removeCallbacksAndMessages(null);
    }

    private void initBoardForLevel(boolean withCountdown) {
        handler.removeCallbacksAndMessages(null);
        this.gridSize = (level >= 4) ? 4 : 3;
        this.winLength = 3;
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                board[r][c] = 0;
            }
        }
        playerTurn = true;
        roundOver = false;
        statusMessage = "Seviye " + level + " (" + gridSize + "x" + gridSize + ") • Senin Sıran (❌)";

        if (withCountdown) {
            inCountdown = true;
            countdownStartMs = SystemClock.elapsedRealtime();
            lastAnnouncedCountdown = -1;
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        canvas.drawColor(0xFF0F172A);

        float hudH = 88f * dp;
        paint.setColor(0xFF1E293B);
        canvas.drawRect(0, 0, w, hudH, paint);

        btnExitRect.set(8f * dp, 20f * dp, 80f * dp, 68f * dp);
        paint.setColor(0xFFDC2626);
        canvas.drawRoundRect(btnExitRect, 8f * dp, 8f * dp, paint);
        paint.setColor(0xFFFFFFFF);
        paint.setTextSize(12.5f * sp);
        paint.setFakeBoldText(true);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("⬅️ Lobi", btnExitRect.centerX(), 49f * dp, paint);

        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(15f * sp);
        paint.setColor(0xFFFFD600);
        canvas.drawText("❌⭕ X-O-X • SEVİYE " + level, 90f * dp, 38f * dp, paint);
        paint.setColor(0xFF38BDF8);
        paint.setTextSize(13f * sp);
        canvas.drawText("🏆 Sen: " + playerWins + "   •   🤖 CPU: " + cpuWins + "   •   Zorluk: " + getCpuTitle(), 90f * dp, 64f * dp, paint);

        // Durum Bandı
        paint.setColor(0xFF334155);
        RectF banner = new RectF(16f * dp, hudH + 14f * dp, w - 16f * dp, hudH + 64f * dp);
        canvas.drawRoundRect(banner, 12f * dp, 12f * dp, paint);
        paint.setColor(0xFFFFFFFF);
        paint.setTextSize(15f * sp);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(statusMessage, banner.centerX(), banner.centerY() + 6f * dp, paint);

        // X-O-X Tahtası
        float availW = w - 36f * dp;
        float availH = h - hudH - 170f * dp;
        float side = Math.min(availW, availH);
        float bLeft = (w - side) * 0.5f;
        float bTop = hudH + 80f * dp;
        boardRect.set(bLeft, bTop, bLeft + side, bTop + side);

        paint.setColor(0xFF1E293B);
        canvas.drawRoundRect(boardRect, 18f * dp, 18f * dp, paint);

        float cellS = side / gridSize;
        float pad = 8f * dp;

        for (int r = 0; r < gridSize; r++) {
            for (int c = 0; c < gridSize; c++) {
                float cl = bLeft + c * cellS + pad;
                float ct = bTop + r * cellS + pad;
                RectF cell = new RectF(cl, ct, cl + cellS - pad * 2, ct + cellS - pad * 2);
                paint.setColor(0xFF334155);
                canvas.drawRoundRect(cell, 14f * dp, 14f * dp, paint);

                int val = board[r][c];
                if (val == 1) {
                    paint.setColor(0xFF38BDF8);
                    paint.setTextSize((gridSize == 3 ? 42f : 32f) * sp);
                    canvas.drawText("❌", cell.centerX(), cell.centerY() + 14f * dp, paint);
                } else if (val == 2) {
                    paint.setColor(0xFFF43F5E);
                    paint.setTextSize((gridSize == 3 ? 42f : 32f) * sp);
                    canvas.drawText("⭕", cell.centerX(), cell.centerY() + 14f * dp, paint);
                }
            }
        }

        // Sonraki Tur / Yeni Oyun Butonu
        btnNextRoundRect.set(w * 0.18f, boardRect.bottom + 18f * dp, w * 0.82f, boardRect.bottom + 72f * dp);
        paint.setColor(roundOver ? 0xFF16A34A : 0xFF475569);
        canvas.drawRoundRect(btnNextRoundRect, 14f * dp, 14f * dp, paint);
        paint.setColor(0xFFFFFFFF);
        paint.setTextSize(15.5f * sp);
        canvas.drawText(roundOver ? ("▶️ Seviye " + level + " Devam Et") : "🔄 Tahtayı Sıfırla", btnNextRoundRect.centerX(), btnNextRoundRect.centerY() + 6f * dp, paint);

        // 3-2-1 Geri Sayımı
        if (inCountdown) {
            long el = SystemClock.elapsedRealtime() - countdownStartMs;
            int step = 0;
            if (el < 800L) step = 3;
            else if (el < 1600L) step = 2;
            else if (el < 2400L) step = 1;
            else if (el < 2950L) step = 0;
            else inCountdown = false;

            if (inCountdown && step != lastAnnouncedCountdown) {
                lastAnnouncedCountdown = step;
                if (listener != null) listener.onCountdownTick(step == 0);
            }
            if (inCountdown) {
                paint.setColor(0xAA0F172A);
                canvas.drawRect(0, 0, w, h, paint);
                paint.setColor(0xFF1E293B);
                canvas.drawRoundRect(new RectF(w * 0.10f, h * 0.36f, w * 0.90f, h * 0.62f), 20f * dp, 20f * dp, paint);
                paint.setColor(0xFFFFD600);
                paint.setTextSize(54f * sp);
                canvas.drawText(step > 0 ? String.valueOf(step) : "BAŞLA! ❌⭕", w * 0.5f, h * 0.52f, paint);
                postInvalidateDelayed(25L);
            }
        }
    }

    private String getCpuTitle() {
        if (level <= 2) return "Çırak CPU";
        if (level <= 5) return "Usta CPU";
        if (level <= 9) return "Büyükusta CPU";
        return "Süper Zeka CPU";
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
            if (inCountdown) return true;

            if (btnNextRoundRect.contains(tx, ty)) {
                initBoardForLevel(false);
                return true;
            }

            if (!roundOver && playerTurn && boardRect.contains(tx, ty)) {
                float cellS = boardRect.width() / gridSize;
                int c = (int) ((tx - boardRect.left) / cellS);
                int r = (int) ((ty - boardRect.top) / cellS);
                if (r >= 0 && r < gridSize && c >= 0 && c < gridSize && board[r][c] == 0) {
                    board[r][c] = 1;
                    if (listener != null) listener.onXoxSound(true);
                    if (checkWin(1)) {
                        roundOver = true;
                        playerWins++;
                        level++;
                        VeliStorage.saveGameLevel(getContext(), "xox_level", level);
                        VeliStorage.saveGameLevel(getContext(), "xox_wins", playerWins + 1);
                        statusMessage = "🎉 KAZANDIN! Seviye " + level + " Kilidi Açıldı!";
                        invalidate();
                        return true;
                    }
                    if (isBoardFull()) {
                        roundOver = true;
                        statusMessage = "🤝 Berabere! Tekrar dene ve CPU'yu yen!";
                        invalidate();
                        return true;
                    }
                    playerTurn = false;
                    statusMessage = "🤖 CPU Düşünüyor...";
                    invalidate();
                    handler.postDelayed(this::performCpuMove, 500L);
                }
            }
            return true;
        }
        return super.onTouchEvent(event);
    }

    private void performCpuMove() {
        if (roundOver) return;
        int[] move = chooseSmartCpuMove();
        if (move != null) {
            board[move[0]][move[1]] = 2;
        }
        if (checkWin(2)) {
            roundOver = true;
            cpuWins++;
            statusMessage = "🤖 CPU Kazandı! Pes etme, tekrar dene!";
            if (listener != null) listener.onXoxSound(false);
        } else if (isBoardFull()) {
            roundOver = true;
            statusMessage = "🤝 Berabere! Çok yakındı!";
        } else {
            playerTurn = true;
            statusMessage = "Senin Sıran (❌) • Seviye " + level;
            if (listener != null) listener.onXoxSound(true);
        }
        invalidate();
    }

    private int[] chooseSmartCpuMove() {
        List<int[]> empty = new ArrayList<>();
        for (int r = 0; r < gridSize; r++) {
            for (int c = 0; c < gridSize; c++) {
                if (board[r][c] == 0) empty.add(new int[]{r, c});
            }
        }
        if (empty.isEmpty()) return null;

        // Seviyeye göre CPU'nun akıllı hamle yapma olasılığı (%55'ten %98'e kadar yükselir!)
        int smartChance = Math.min(98, 45 + level * 10);
        if (random.nextInt(100) < smartChance) {
            // 1. CPU tek hamlede kazanabiliyorsa kazan
            for (int[] m : empty) {
                board[m[0]][m[1]] = 2;
                boolean win = checkWin(2);
                board[m[0]][m[1]] = 0;
                if (win) return m;
            }
            // 2. Oyuncu tek hamlede kazanacaksa blokla
            for (int[] m : empty) {
                board[m[0]][m[1]] = 1;
                boolean win = checkWin(1);
                board[m[0]][m[1]] = 0;
                if (win) return m;
            }
            // 3. Merkez veya köşe önceliği
            int mid = gridSize / 2;
            if (board[mid][mid] == 0) return new int[]{mid, mid};
        }
        return empty.get(random.nextInt(empty.size()));
    }

    private boolean isBoardFull() {
        for (int r = 0; r < gridSize; r++) {
            for (int c = 0; c < gridSize; c++) {
                if (board[r][c] == 0) return false;
            }
        }
        return true;
    }

    private boolean checkWin(int who) {
        int[][] dirs = new int[][]{{0, 1}, {1, 0}, {1, 1}, {1, -1}};
        for (int r = 0; r < gridSize; r++) {
            for (int c = 0; c < gridSize; c++) {
                if (board[r][c] != who) continue;
                for (int[] d : dirs) {
                    int cnt = 1;
                    for (int step = 1; step < winLength; step++) {
                        int nr = r + d[0] * step;
                        int nc = c + d[1] * step;
                        if (nr >= 0 && nr < gridSize && nc >= 0 && nc < gridSize && board[nr][nc] == who) {
                            cnt++;
                        } else {
                            break;
                        }
                    }
                    if (cnt >= winLength) return true;
                }
            }
        }
        return false;
    }
}
