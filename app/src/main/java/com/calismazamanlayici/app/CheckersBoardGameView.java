package com.calismazamanlayici.app;

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
 * 🏁 DAMA ŞAMPİYONASI (CPU'ya Karşı, Kalıcı Seviye Hafızalı):
 * - 8x8 Dama tahtasında Oyuncu (🔵 Mavi/Beyaz Taşlar) ile CPU (🔴 Kırmızı Taşlar) karşılıklı oynar.
 * - Türk Daması ve Klasik Dama hibrit hareketleri (ileri, sağa, sola ve çapraz hamle + üzerinden atlayarak taş yeme
 *   + karşı satıra ulaşınca 👑 DAMA olma ve çoklu taş yeme!) desteklenir.
 * - Geçilen her seviye (Level 1..15+) kalıcı olarak hafızaya ve yedeğe kaydedilir; seviye yükseldikçe CPU'nun
 *   taş yeme ve Dama yapma strateji puanlaması güçlenir.
 */
public class CheckersBoardGameView extends View {

    public interface CheckersListener {
        void onCheckersSound(boolean positive);
        void onCountdownTick(boolean isGo);
        void onExitRequested();
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final CheckersListener listener;
    private final float dp;
    private final float sp;

    private boolean inCountdown = false;
    private long countdownStartMs = 0L;
    private int lastAnnouncedCountdown = -1;

    private int level = 1;
    private int playerWins = 0;
    // 0: Boş, 1: Oyuncu Taşı, 2: Oyuncu Daması (👑), 3: CPU Taşı, 4: CPU Daması (👑)
    private final int[][] board = new int[8][8];
    private int selectedR = -1;
    private int selectedC = -1;
    private boolean playerTurn = true;
    private boolean matchOver = false;
    private String statusText = "Taşına dokun ve yeşil kareye ilerle!";

    private static class MoveOption {
        int fromR, fromC;
        int toR, toC;
        int capR = -1, capC = -1;
        int scoreWeight = 0;
    }

    private final List<MoveOption> validMovesForSelected = new ArrayList<>();
    private final RectF btnExitRect = new RectF();
    private final RectF btnNewMatchRect = new RectF();
    private final RectF boardRect = new RectF();

    public CheckersBoardGameView(Context context, CheckersListener listener) {
        super(context);
        this.listener = listener;
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        this.dp = dm.density;
        this.sp = dm.scaledDensity;
        this.level = StudyDatabaseHelper.getSavedGameLevel(context, "checkers_level", 1);
        this.playerWins = Math.max(0, StudyDatabaseHelper.getSavedGameLevel(context, "checkers_wins", 1) - 1);
        setupCheckersMatch(true);
    }

    public void startGame() {
        this.level = StudyDatabaseHelper.getSavedGameLevel(getContext(), "checkers_level", 1);
        this.playerWins = Math.max(0, StudyDatabaseHelper.getSavedGameLevel(getContext(), "checkers_wins", 1) - 1);
        setupCheckersMatch(true);
    }

    public void stopGame() {
        handler.removeCallbacksAndMessages(null);
    }

    private void setupCheckersMatch(boolean withCountdown) {
        handler.removeCallbacksAndMessages(null);
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                board[r][c] = 0;
            }
        }
        // Türk Daması dizilimi: CPU üstte (1. ve 2. satırlar -> 16 taş veya hızlı oyun için 8'er taş)
        for (int r = 1; r <= 2; r++) {
            for (int c = 0; c < 8; c++) {
                if ((r + c) % 2 == 0 || level >= 2) {
                    board[r][c] = 3; // CPU
                }
            }
        }
        for (int r = 5; r <= 6; r++) {
            for (int c = 0; c < 8; c++) {
                if ((r + c) % 2 == 0 || level >= 2) {
                    board[r][c] = 1; // Oyuncu
                }
            }
        }

        selectedR = -1;
        selectedC = -1;
        validMovesForSelected.clear();
        playerTurn = true;
        matchOver = false;
        statusText = "Seviye " + level + " • Mavi Taşlarından (🔵) birini seç!";

        if (withCountdown) {
            inCountdown = true;
            countdownStartMs = SystemClock.elapsedRealtime();
            lastAnnouncedCountdown = -1;
        }
        invalidate();
    }

    private boolean isPlayerPiece(int v) {
        return v == 1 || v == 2;
    }

    private boolean isCpuPiece(int v) {
        return v == 3 || v == 4;
    }

    private List<MoveOption> computeMovesForPiece(int r, int c, boolean forPlayer) {
        List<MoveOption> list = new ArrayList<>();
        int p = board[r][c];
        if (p == 0) return list;
        boolean isKing = (p == 2 || p == 4);

        // Türk Daması yönleri (İleri, Sağ, Sol; Dama ise Geri de gider!)
        int forwardDr = forPlayer ? -1 : 1;
        int[][] dirs = isKing
                ? new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-1, -1}, {-1, 1}, {1, -1}, {1, 1}}
                : new int[][]{{forwardDr, 0}, {0, -1}, {0, 1}, {forwardDr, -1}, {forwardDr, 1}};

        for (int[] d : dirs) {
            int nr = r + d[0];
            int nc = c + d[1];
            if (nr >= 0 && nr < 8 && nc >= 0 && nc < 8) {
                if (board[nr][nc] == 0) {
                    MoveOption mo = new MoveOption();
                    mo.fromR = r; mo.fromC = c;
                    mo.toR = nr; mo.toC = nc;
                    mo.scoreWeight = (nr == 0 || nr == 7) ? 25 : 5;
                    list.add(mo);
                } else if ((forPlayer && isCpuPiece(board[nr][nc])) || (!forPlayer && isPlayerPiece(board[nr][nc]))) {
                    int jr = nr + d[0];
                    int jc = nc + d[1];
                    if (jr >= 0 && jr < 8 && jc >= 0 && jc < 8 && board[jr][jc] == 0) {
                        MoveOption cap = new MoveOption();
                        cap.fromR = r; cap.fromC = c;
                        cap.toR = jr; cap.toC = jc;
                        cap.capR = nr; cap.capC = nc;
                        cap.scoreWeight = 60 + ((jr == 0 || jr == 7) ? 30 : 0);
                        list.add(cap);
                    }
                }
            }
        }
        return list;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        canvas.drawColor(0xFF1E1B18);

        float hudH = 88f * dp;
        paint.setColor(0xFF0F172A);
        canvas.drawRect(0, 0, w, hudH, paint);

        btnExitRect.set(8f * dp, 20f * dp, 80f * dp, 68f * dp);
        paint.setColor(0xFFDC2626);
        canvas.drawRoundRect(btnExitRect, 8f * dp, 8f * dp, paint);
        paint.setColor(0xFFFFFFFF);
        paint.setTextSize(12.5f * sp);
        paint.setFakeBoldText(true);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("⬅️ Lobi", btnExitRect.centerX(), 49f * dp, paint);

        int pCount = 0, cCount = 0;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (isPlayerPiece(board[r][c])) pCount++;
                else if (isCpuPiece(board[r][c])) cCount++;
            }
        }

        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(15f * sp);
        paint.setColor(0xFFFFD600);
        canvas.drawText("🏁 DAMA ŞAMPİYONASI • SEVİYE " + level, 88f * dp, 38f * dp, paint);
        paint.setColor(0xFF38BDF8);
        paint.setTextSize(13f * sp);
        canvas.drawText("🔵 Sen: " + pCount + " Taş   •   🔴 CPU: " + cCount + " Taş   •   🏆 Zafer: " + playerWins, 88f * dp, 64f * dp, paint);

        // Durum Bandı
        paint.setColor(0xFF334155);
        RectF banner = new RectF(12f * dp, hudH + 10f * dp, w - 12f * dp, hudH + 56f * dp);
        canvas.drawRoundRect(banner, 10f * dp, 10f * dp, paint);
        paint.setColor(0xFFFFFFFF);
        paint.setTextSize(13.5f * sp);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(statusText, banner.centerX(), banner.centerY() + 5f * dp, paint);

        // 8x8 Dama Tahtası
        float availW = w - 20f * dp;
        float availH = h - hudH - 145f * dp;
        float side = Math.min(availW, availH);
        float bLeft = (w - side) * 0.5f;
        float bTop = hudH + 66f * dp;
        boardRect.set(bLeft, bTop, bLeft + side, bTop + side);

        float cellS = side / 8f;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                float cl = bLeft + c * cellS;
                float ct = bTop + r * cellS;
                boolean dark = ((r + c) % 2 == 1);
                paint.setColor(dark ? 0xFF78350F : 0xFFFEF3C7);
                canvas.drawRect(cl, ct, cl + cellS, ct + cellS, paint);

                if (r == selectedR && c == selectedC) {
                    paint.setColor(0x8838BDF8);
                    canvas.drawRect(cl, ct, cl + cellS, ct + cellS, paint);
                }

                int p = board[r][c];
                if (p > 0) {
                    float cx = cl + cellS * 0.5f;
                    float cy = ct + cellS * 0.5f;
                    paint.setColor(isPlayerPiece(p) ? 0xFF0284C7 : 0xFFDC2626);
                    canvas.drawCircle(cx, cy, cellS * 0.38f, paint);
                    paint.setColor(0x44FFFFFF);
                    canvas.drawCircle(cx, cy, cellS * 0.28f, paint);
                    if (p == 2 || p == 4) {
                        paint.setColor(0xFFFFD600);
                        paint.setTextSize(15f * sp);
                        canvas.drawText("👑", cx, cy + 5f * dp, paint);
                    }
                }
            }
        }

        // Seçili taşın gidebileceği yeşil hedef noktaları
        for (MoveOption mo : validMovesForSelected) {
            float cx = bLeft + (mo.toC + 0.5f) * cellS;
            float cy = bTop + (mo.toR + 0.5f) * cellS;
            paint.setColor(mo.capR >= 0 ? 0xCCFF6D00 : 0xCC16A34A);
            canvas.drawCircle(cx, cy, cellS * 0.24f, paint);
        }

        btnNewMatchRect.set(w * 0.18f, boardRect.bottom + 14f * dp, w * 0.82f, boardRect.bottom + 64f * dp);
        paint.setColor(matchOver ? 0xFF16A34A : 0xFF475569);
        canvas.drawRoundRect(btnNewMatchRect, 12f * dp, 12f * dp, paint);
        paint.setColor(0xFFFFFFFF);
        paint.setTextSize(15f * sp);
        canvas.drawText(matchOver ? ("▶️ Seviye " + level + " Yeni Maça Başla") : "🔄 Maçı Baştan Diz", btnNewMatchRect.centerX(), btnNewMatchRect.centerY() + 5f * dp, paint);

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
                canvas.drawText(step > 0 ? String.valueOf(step) : "BAŞLA! 🏁", w * 0.5f, h * 0.52f, paint);
                postInvalidateDelayed(25L);
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
            if (inCountdown) return true;

            if (btnNewMatchRect.contains(tx, ty)) {
                setupCheckersMatch(false);
                return true;
            }

            if (!matchOver && playerTurn && boardRect.contains(tx, ty)) {
                float cellS = boardRect.width() / 8f;
                int c = (int) ((tx - boardRect.left) / cellS);
                int r = (int) ((ty - boardRect.top) / cellS);
                if (r < 0 || r >= 8 || c < 0 || c >= 8) return true;

                // 1. Geçerli bir hedef kareye mi dokundu?
                for (MoveOption mo : validMovesForSelected) {
                    if (mo.toR == r && mo.toC == c) {
                        executeMove(mo, true);
                        return true;
                    }
                }

                // 2. Kendi taşlarından birine mi dokundu?
                if (isPlayerPiece(board[r][c])) {
                    selectedR = r;
                    selectedC = c;
                    validMovesForSelected.clear();
                    validMovesForSelected.addAll(computeMovesForPiece(r, c, true));
                    statusText = validMovesForSelected.isEmpty()
                            ? "Bu taşın önü kapalı, başka bir mavi taş seç!"
                            : "Yeşil/Turuncu hedef noktalardan birine dokun!";
                    invalidate();
                }
            }
            return true;
        }
        return super.onTouchEvent(event);
    }

    private void executeMove(MoveOption mo, boolean byPlayer) {
        int piece = board[mo.fromR][mo.fromC];
        board[mo.fromR][mo.fromC] = 0;
        if (mo.capR >= 0 && mo.capC >= 0) {
            board[mo.capR][mo.capC] = 0;
        }
        // Dama (👑) terfisi
        if (byPlayer && mo.toR == 0 && piece == 1) {
            piece = 2;
        } else if (!byPlayer && mo.toR == 7 && piece == 3) {
            piece = 4;
        }
        board[mo.toR][mo.toC] = piece;
        selectedR = -1;
        selectedC = -1;
        validMovesForSelected.clear();

        if (listener != null) listener.onCheckersSound(true);

        // Eğer taş yediyse ve aynı taşla zincirleme yeme (multi-jump) varsa devam etsin!
        if (mo.capR >= 0) {
            List<MoveOption> followUps = computeMovesForPiece(mo.toR, mo.toC, byPlayer);
            List<MoveOption> extraCaps = new ArrayList<>();
            for (MoveOption f : followUps) {
                if (f.capR >= 0) extraCaps.add(f);
            }
            if (!extraCaps.isEmpty()) {
                if (byPlayer) {
                    selectedR = mo.toR;
                    selectedC = mo.toC;
                    validMovesForSelected.addAll(extraCaps);
                    statusText = "🔥 Süper! Zincirleme taş yeme hakkın var, turuncu hedefe dokun!";
                    invalidate();
                    return;
                } else {
                    MoveOption nextCap = extraCaps.get(0);
                    executeMove(nextCap, false);
                    return;
                }
            }
        }

        checkMatchEndOrSwitchTurn(byPlayer);
    }

    private void checkMatchEndOrSwitchTurn(boolean justMovedPlayer) {
        int pCount = 0, cCount = 0;
        List<MoveOption> cpuMoves = new ArrayList<>();
        List<MoveOption> playerMoves = new ArrayList<>();

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (isPlayerPiece(board[r][c])) {
                    pCount++;
                    playerMoves.addAll(computeMovesForPiece(r, c, true));
                } else if (isCpuPiece(board[r][c])) {
                    cCount++;
                    cpuMoves.addAll(computeMovesForPiece(r, c, false));
                }
            }
        }

        if (cCount == 0 || cpuMoves.isEmpty()) {
            matchOver = true;
            playerWins++;
            level++;
            StudyDatabaseHelper.saveGameLevel(getContext(), "checkers_level", level);
            StudyDatabaseHelper.saveGameLevel(getContext(), "checkers_wins", playerWins + 1);
            statusText = "🏆 TEBRİKLER! Dama Maçını Kazandın! Seviye " + level + " Açıldı!";
            invalidate();
            return;
        }
        if (pCount == 0 || playerMoves.isEmpty()) {
            matchOver = true;
            statusText = "🤖 CPU Maçı Kazandı! Tekrar dene!";
            if (listener != null) listener.onCheckersSound(false);
            invalidate();
            return;
        }

        if (justMovedPlayer) {
            playerTurn = false;
            statusText = "🤖 CPU Hamle Düşünüyor...";
            invalidate();
            handler.postDelayed(() -> performCpuCheckersTurn(cpuMoves), 550L);
        } else {
            playerTurn = true;
            statusText = "Senin Sıran (🔵) • Seviye " + level;
            invalidate();
        }
    }

    private void performCpuCheckersTurn(List<MoveOption> allCpuMoves) {
        if (matchOver || allCpuMoves.isEmpty()) return;

        // Zorunlu/öncelikli taş yeme kontrolü ve seviyeye göre strateji puanlaması
        MoveOption best = allCpuMoves.get(0);
        int bestScore = -9999;

        for (MoveOption mo : allCpuMoves) {
            int sc = mo.scoreWeight + random.nextInt(Math.max(4, 22 - level * 2));
            // Seviye yükseldikçe oyuncunun yiyebileceği karelerden kaçın
            if (level >= 3 && mo.toR < 7) {
                if ((mo.toC > 0 && isPlayerPiece(board[mo.toR + 1][mo.toC - 1]))
                        || (mo.toC < 7 && isPlayerPiece(board[mo.toR + 1][mo.toC + 1]))) {
                    sc -= 18;
                }
            }
            if (sc > bestScore) {
                bestScore = sc;
                best = mo;
            }
        }
        executeMove(best, false);
    }
}
