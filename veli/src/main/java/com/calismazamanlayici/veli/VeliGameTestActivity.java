package com.calismazamanlayici.veli;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Öğrenci Uygulaması Eğlence & Macera Merkezi (v5.2):
 * - "🏃‍♂️ Süper Yıldız Zıplama Macerası" komple TAM EKRAN (Full-Screen Immersive) çalışır ve
 *   her bölüme/parkura başlarken ekranda "3 - 2 - 1 - BAŞLA!" geri sayımı yapar.
 * - Diğer 8 oyun, her oyuna özel canlı 60 FPS animasyonlu interaktif oyun sahnesi (Interactive Arcade Canvas),
 *   3 etkileşimli Joker/Güçlendirici (⚡ %50 İki Şık Ele, 💡 İpucu, 🔥 2x Çifte Puan) ve
 *   en kolaydan başlayıp soru bildikçe zorlaşan 4 kademeli (Seviye 1 Isınma ➔ Seviye 4 Şampiyon)
 *   akıllı soru üretim motoru içerir.
 */
public class VeliGameTestActivity extends Activity {

    private ScrollView scrollRootEntertainment;
    private LinearLayout layoutRootInnerContainer;
    private LinearLayout layoutEntHeaderCard;
    private TextView tvEntHeaderTitle;
    private TextView tvEntTimerStatus;
    private Button btnToggleGameMusic;

    private LinearLayout layoutGameLobbyContainer;
    private LinearLayout layoutGameCardsList;
    private LinearLayout layoutPlatformerJumpArena;
    private LinearLayout layoutJumpCardWrapper;
    private LinearLayout layoutActiveGameArena;

    private TextView tvJumpGameStatus;
    private FrameLayout frameJumpGameCanvas;
    private Button btnJumpAction;
    private Button btnBackFromJumpGame;
    private VeliPlatformerJumpView jumpGameView;

    private TextView tvActiveGameTitle;
    private TextView tvGameStatsBar;
    private TextView tvGameQuestionPrompt;
    private TextView tvGameFeedbackBanner;
    private LinearLayout layoutJokerBar;
    private Button btnJokerFifty;
    private Button btnJokerHint;
    private Button btnJokerDouble;
    private LinearLayout layoutGameOptionButtons;
    private FrameLayout frameEffectOverlay;
    private StarBurstEffectView starEffectView;

    private int testGradeLevel = 5;
    private Spinner spinnerVeliTestGrade;
    private final VeliGameMusicSynthesizer musicSynth = new VeliGameMusicSynthesizer();
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final Set<String> askedPrompts = new HashSet<>();

    private int totalEntElapsedSeconds = 0;
    private boolean elevenMinWarningShown = false;

    private boolean inActiveGame = false;
    private int activeGameIndex = 8;
    private String activeGameTitle = "";
    private int activeGameSessionSeconds = 0;
    private int currentScore = 0;
    private int currentStreak = 0;
    private int bestStreak = 0;
    private int currentLives = 3;
    private int questionsSolvedInSession = 0;

    // Joker / Güçlendirici hakları
    private int jokerFiftyCount = 2;
    private int jokerHintCount = 2;
    private int jokerDoubleCount = 2;
    private boolean doubleScoreActiveOnCurrentQuestion = false;
    private QuestionItem currentQuestionItem = null;

    public static class GameDefinition {
        public final int index;
        public final String gameTitle;
        public final String subtitle;
        public final int accentColor;
        public final boolean isPlatformerJump;

        public GameDefinition(int index, String gameTitle, String subtitle, int accentColor, boolean isPlatformerJump) {
            this.index = index;
            this.gameTitle = gameTitle;
            this.subtitle = subtitle;
            this.accentColor = accentColor;
            this.isPlatformerJump = isPlatformerJump;
        }
    }

    public static class QuestionItem {
        public final String prompt;
        public final String correctAnswer;
        public final List<String> options;

        public QuestionItem(String prompt, String correctAnswer, String w1, String w2, String w3) {
            this.prompt = prompt;
            this.correctAnswer = correctAnswer;
            this.options = new ArrayList<>();
            this.options.add(correctAnswer);
            this.options.add(w1);
            this.options.add(w2);
            this.options.add(w3);
            Collections.shuffle(this.options);
        }
    }

    /**
     * Her oyuna özel sürekli hareketli, canlı 60 FPS Arcade Sahnesi + Patlama Efektleri + Seviye Göstergesi.
     */
    private static class StarBurstEffectView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final float[] px = new float[28];
        private final float[] py = new float[28];
        private final float[] vx = new float[28];
        private final float[] vy = new float[28];
        private final int[] colors = new int[]{
                0xFFFFD54F, 0xFFFF4081, 0xFF00E5FF, 0xFF69F0AE, 0xFFFFAB40, 0xFFEA80FC
        };
        private int animFrame = 0;
        private boolean animatingBurst = false;
        private String badgeText = "";
        private int laserFrames = 0;

        private int gameThemeIndex = 0;
        private int solvedCount = 0;
        private int difficultyTier = 1;
        private float tick = 0f;
        private boolean sceneActive = false;

        public StarBurstEffectView(Context context) {
            super(context);
        }

        public void setSceneState(int gameIndex, int solved, int tier, boolean active) {
            this.gameThemeIndex = Math.abs(gameIndex) % 8;
            this.solvedCount = solved;
            this.difficultyTier = tier;
            this.sceneActive = active;
            invalidate();
        }

        public void triggerBurst(String text) {
            this.badgeText = text;
            this.animFrame = 0;
            this.animatingBurst = true;
            this.laserFrames = 18;
            float cx = Math.max(getWidth() * 0.72f, 180f);
            float cy = Math.max(getHeight() * 0.52f, 65f);
            Random r = new Random();
            for (int i = 0; i < px.length; i++) {
                px[i] = cx;
                py[i] = cy;
                double angle = (Math.PI * 2 * i) / px.length + r.nextDouble() * 0.3;
                float speed = 4f + r.nextFloat() * 10f;
                vx[i] = (float) Math.cos(angle) * speed;
                vy[i] = (float) Math.sin(angle) * speed;
            }
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            tick += 0.08f;

            // 1. Oyuna Özel Hareketli Arka Plan Sahnesi
            int[] bgColors = new int[]{
                    0xFF0F172A, // 0: Uzay
                    0xFF1E1B4B, // 1: Kelime Kalesi
                    0xFF064E3B, // 2: Laboratuvar
                    0xFF0C4A6E, // 3: Hazine Avı Deniz
                    0xFF3B0764, // 4: Sihirli Köprü
                    0xFF14532D, // 5: İyilik Bahçesi
                    0xFF1E293B, // 6: Yarış Pisti
                    0xFF0F766E  // 7: Barış Adası
            };
            paint.setColor(bgColors[gameThemeIndex]);
            canvas.drawRoundRect(new RectF(0, 0, w, h), 22f, 22f, paint);

            float dp = getResources().getDisplayMetrics().density;
            float sp = getResources().getDisplayMetrics().scaledDensity;

            // Orta Hareketli Aksiyon Alanı (Telefon ekranına tam ölçekli)
            float cy = h * 0.50f;
            float heroX = w * 0.18f + (float) Math.sin(tick) * 6f * dp;
            float targetX = w * 0.80f + (float) Math.cos(tick * 1.2f) * 8f * dp;
            float targetY = cy + (float) Math.sin(tick * 1.5f) * 9f * dp;

            paint.setColor(0x44FFFFFF);
            for (int i = 0; i < 10; i++) {
                float sx = ((i * 85f * dp - tick * 22f * dp) % w + w) % w;
                float sy = 14f * dp + ((i * 29 * (int) dp) % Math.max(30, h - (int) (28 * dp)));
                canvas.drawCircle(sx, sy, ((i % 3) + 2f) * dp, paint);
            }

            if (gameThemeIndex == 0) {
                paint.setColor(0xFFFF6D00);
                canvas.drawCircle(heroX - 18f * dp, cy, (6f + (float) Math.abs(Math.sin(tick * 3f)) * 5f) * dp, paint);
                paint.setColor(0xFF38BDF8);
                path.reset();
                path.moveTo(heroX + 22f * dp, cy);
                path.lineTo(heroX - 16f * dp, cy - 17f * dp);
                path.lineTo(heroX - 16f * dp, cy + 17f * dp);
                path.close();
                canvas.drawPath(path, paint);

                paint.setColor(0xFFEF4444);
                canvas.drawCircle(targetX, targetY, 24f * dp, paint);
                paint.setColor(0xFFFFFFFF);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTextSize(22f * sp);
                canvas.drawText("☄️", targetX, targetY + 8f * dp, paint);
            } else if (gameThemeIndex == 6) {
                paint.setColor(0xFF475569);
                canvas.drawRect(12f * dp, cy - 22f * dp, w - 12f * dp, cy + 22f * dp, paint);
                paint.setColor(0xFFFDE047);
                for (int i = 0; i < 6; i++) {
                    float lx = ((i * 90f * dp - tick * 45f * dp) % w + w) % w;
                    canvas.drawRect(lx, cy - 2.5f * dp, lx + 34f * dp, cy + 2.5f * dp, paint);
                }
                float carX = w * 0.16f + Math.min(w * 0.54f, (solvedCount % 8) * (w * 0.068f));
                paint.setColor(0xFFEF4444);
                canvas.drawRoundRect(new RectF(carX - 24f * dp, cy - 15f * dp, carX + 24f * dp, cy + 15f * dp), 8f * dp, 8f * dp, paint);
                paint.setColor(0xFFFFFFFF);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTextSize(22f * sp);
                canvas.drawText("🏎️", carX, cy + 7f * dp, paint);
                canvas.drawText("🏁", w - 32f * dp, cy + 7f * dp, paint);
            } else {
                String[] heroIcons = new String[]{"🚀", "🧙‍♂️", "🧑‍🔬", "⛵", "🦸", "🦋", "🏎️", "🕊️"};
                String[] targetIcons = new String[]{"☄️", "🏰", "⚡", "🏝️", "🌉", "🌻", "🏆", "🗼"};

                int steps = 6;
                float startX = w * 0.30f;
                float stepGap = (w * 0.40f) / steps;
                for (int i = 0; i < steps; i++) {
                    boolean lit = (solvedCount % (steps + 1)) > i;
                    paint.setColor(lit ? 0xFFFFD600 : 0x44FFFFFF);
                    canvas.drawCircle(startX + i * stepGap, cy + (float) Math.sin(tick + i) * 4f * dp, (lit ? 7f : 4.5f) * dp, paint);
                }

                paint.setColor(0x33FFFFFF);
                canvas.drawCircle(heroX, cy, 25f * dp, paint);
                canvas.drawCircle(targetX, targetY, 25f * dp, paint);

                paint.setColor(0xFFFFFFFF);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTextSize(24f * sp);
                canvas.drawText(heroIcons[gameThemeIndex], heroX, cy + 8f * dp, paint);
                canvas.drawText(targetIcons[gameThemeIndex], targetX, targetY + 8f * dp, paint);
            }

            if (laserFrames > 0) {
                laserFrames--;
                paint.setColor(0xFF00E676);
                paint.setStrokeWidth(5f * dp);
                canvas.drawLine(heroX + 16f * dp, cy, targetX, targetY, paint);
                paint.setStrokeWidth(1f);
            }

            if (animatingBurst) {
                for (int i = 0; i < px.length; i++) {
                    px[i] += vx[i];
                    py[i] += vy[i];
                    paint.setColor(colors[i % colors.length]);
                    float radius = Math.max(2.5f * dp, (9f - animFrame * 0.3f) * dp);
                    canvas.drawCircle(px[i], py[i], radius, paint);
                }

                paint.setColor(0xDD0F172A);
                canvas.drawRoundRect(new RectF(w * 0.10f, h - 34f * dp, w * 0.90f, h - 4f * dp), 10f * dp, 10f * dp, paint);
                paint.setColor(0xFFFFD600);
                paint.setTextSize(14f * sp);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setFakeBoldText(true);
                canvas.drawText(badgeText, w / 2f, h - 13f * dp, paint);

                animFrame++;
                if (animFrame >= 24) {
                    animatingBurst = false;
                }
            }

            if (sceneActive) {
                postInvalidateDelayed(30L);
            }
        }
    }

    private final Runnable secondTickRunnable = new Runnable() {
        @Override
        public void run() {
            totalEntElapsedSeconds++;
            if (inActiveGame) {
                activeGameSessionSeconds++;
            }
            updateTimerBannerUi();
            timerHandler.postDelayed(this, 1000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_veli_game_test);

        totalEntElapsedSeconds = 0;
        List<VeliStorage.PairedChild> paired = VeliStorage.getPairedChildren(this);
        if (!paired.isEmpty() && paired.get(0).gradeLevel >= 1 && paired.get(0).gradeLevel <= 8) {
            testGradeLevel = paired.get(0).gradeLevel;
        }

        scrollRootEntertainment = findViewById(R.id.scrollRootEntertainment);
        if (scrollRootEntertainment.getChildCount() > 0 && scrollRootEntertainment.getChildAt(0) instanceof LinearLayout) {
            layoutRootInnerContainer = (LinearLayout) scrollRootEntertainment.getChildAt(0);
        }
        layoutEntHeaderCard = findViewById(R.id.layoutEntHeaderCard);
        tvEntHeaderTitle = findViewById(R.id.tvEntHeaderTitle);
        tvEntTimerStatus = findViewById(R.id.tvEntTimerStatus);
        btnToggleGameMusic = findViewById(R.id.btnToggleGameMusic);

        layoutGameLobbyContainer = findViewById(R.id.layoutGameLobbyContainer);
        layoutGameCardsList = findViewById(R.id.layoutGameCardsList);
        layoutPlatformerJumpArena = findViewById(R.id.layoutPlatformerJumpArena);
        if (layoutPlatformerJumpArena.getChildCount() > 0 && layoutPlatformerJumpArena.getChildAt(0) instanceof LinearLayout) {
            layoutJumpCardWrapper = (LinearLayout) layoutPlatformerJumpArena.getChildAt(0);
        }
        layoutActiveGameArena = findViewById(R.id.layoutActiveGameArena);

        tvJumpGameStatus = findViewById(R.id.tvJumpGameStatus);
        frameJumpGameCanvas = findViewById(R.id.frameJumpGameCanvas);
        btnJumpAction = findViewById(R.id.btnJumpAction);
        btnBackFromJumpGame = findViewById(R.id.btnBackFromJumpGame);

        tvActiveGameTitle = findViewById(R.id.tvActiveGameTitle);
        tvGameStatsBar = findViewById(R.id.tvGameStatsBar);
        tvGameQuestionPrompt = findViewById(R.id.tvGameQuestionPrompt);
        tvGameFeedbackBanner = findViewById(R.id.tvGameFeedbackBanner);
        layoutGameOptionButtons = findViewById(R.id.layoutGameOptionButtons);
        frameEffectOverlay = findViewById(R.id.frameEffectOverlay);

        Button btnExitEntertainment = findViewById(R.id.btnExitEntertainment);
        Button btnBackToGameLobby = findViewById(R.id.btnBackToGameLobby);

        starEffectView = new StarBurstEffectView(this);
        frameEffectOverlay.addView(starEffectView);

        setupInteractiveJokerBar();

        jumpGameView = new VeliPlatformerJumpView(this, new VeliPlatformerJumpView.JumpGameListener() {
            @Override
            public void onStatsUpdated(int score, int stage, String stageName, int lives) {
                tvJumpGameStatus.setSingleLine(true);
                tvJumpGameStatus.setText("🗺️ Parkur " + stage + "/6: " + stageName);
            }

            @Override
            public void onJumpTriggered() {
                musicSynth.playJumpEffect();
            }

            @Override
            public void onStarCollected() {
                musicSynth.playCoinEffect();
            }

            @Override
            public void onObstacleHit() {
                musicSynth.playWrongEffect();
            }

            @Override
            public void onCountdownTick(boolean isGo) {
                musicSynth.playCountdownTick(isGo);
            }

            @Override
            public void onExitRequested() {
                if (jumpGameView != null) jumpGameView.stopGame();
                saveActiveGameSessionToCalendarIfNeeded();
                showLobbyScreen();
            }

            @Override
            public boolean onToggleMuteRequested() {
                boolean muted = musicSynth.toggleMute();
                btnToggleGameMusic.setText(muted ? "🔇 Ses: Kapalı" : "🎵 Ses: Açık");
                return muted;
            }
        });
        frameJumpGameCanvas.addView(jumpGameView);

        VeliThemeHelper.Palette p = VeliThemeHelper.getPalette(this);
        VeliThemeHelper.applyWindowAndSurface(this, scrollRootEntertainment);
        if (layoutEntHeaderCard != null) {
            layoutEntHeaderCard.setBackground(VeliThemeHelper.createHeaderDrawable(this));
        }
        btnJumpAction.setBackground(VeliThemeHelper.createButtonDrawable(this, p.primary));

        tvEntHeaderTitle.setText("🛠️ Veli Oyun Test Merkezi (" + testGradeLevel + ". Sınıf)");

        if (layoutEntHeaderCard != null) {
            int pad = (int) (8 * getResources().getDisplayMetrics().density);
            LinearLayout gradeRow = new LinearLayout(this);
            gradeRow.setOrientation(LinearLayout.HORIZONTAL);
            gradeRow.setGravity(Gravity.CENTER_VERTICAL);
            gradeRow.setPadding(0, pad, 0, 0);

            TextView tvGradeLbl = new TextView(this);
            tvGradeLbl.setText("🎓 Test Sınıf Seviyesi: ");
            tvGradeLbl.setTextColor(0xFFFFFFFF);
            tvGradeLbl.setTextSize(14f);
            tvGradeLbl.setTypeface(null, android.graphics.Typeface.BOLD);

            spinnerVeliTestGrade = new Spinner(this);
            spinnerVeliTestGrade.setBackgroundColor(0xFFEEF2FF);
            spinnerVeliTestGrade.setPadding(pad, pad / 2, pad, pad / 2);
            String[] grades = new String[]{"1. Sınıf", "2. Sınıf", "3. Sınıf", "4. Sınıf", "5. Sınıf", "6. Sınıf", "7. Sınıf", "8. Sınıf"};
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, grades);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerVeliTestGrade.setAdapter(adapter);
            spinnerVeliTestGrade.setSelection(testGradeLevel - 1);
            spinnerVeliTestGrade.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    testGradeLevel = position + 1;
                    tvEntHeaderTitle.setText("🛠️ Veli Oyun Test Merkezi (" + testGradeLevel + ". Sınıf)");
                    buildGameLobbyCards();
                    if (inActiveGame && activeGameIndex < 8) {
                        loadNextQuestion();
                    }
                }
                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });

            gradeRow.addView(tvGradeLbl);
            gradeRow.addView(spinnerVeliTestGrade);
            layoutEntHeaderCard.addView(gradeRow);
        }

        btnToggleGameMusic.setOnClickListener(v -> {
            boolean muted = musicSynth.toggleMute();
            btnToggleGameMusic.setText(muted ? "🔇 Ses: Kapalı" : "🎵 Ses: Açık");
        });

        btnJumpAction.setOnClickListener(v -> {
            if (jumpGameView != null) {
                jumpGameView.triggerJump();
            }
        });

        btnBackFromJumpGame.setOnClickListener(v -> {
            if (jumpGameView != null) jumpGameView.stopGame();
            saveActiveGameSessionToCalendarIfNeeded();
            showLobbyScreen();
        });

        btnBackToGameLobby.setOnClickListener(v -> {
            saveActiveGameSessionToCalendarIfNeeded();
            showLobbyScreen();
        });

        btnExitEntertainment.setOnClickListener(v -> {
            if (jumpGameView != null) jumpGameView.stopGame();
            saveActiveGameSessionToCalendarIfNeeded();
            finish();
        });

        buildGameLobbyCards();
        updateTimerBannerUi();
    }

    /**
     * Soru oyunlarına heyecan ve etkileşim katan 3 Joker (Güçlendirici) butonunu oluşturur.
     */
    private void setupInteractiveJokerBar() {
        int dp8 = (int) (8 * getResources().getDisplayMetrics().density);
        layoutJokerBar = new LinearLayout(this);
        layoutJokerBar.setOrientation(LinearLayout.HORIZONTAL);
        layoutJokerBar.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        barLp.setMargins(0, 0, 0, dp8);
        layoutJokerBar.setLayoutParams(barLp);

        btnJokerFifty = createJokerButton("⚡ %50 Ele (2)", 0xFF7C3AED);
        btnJokerHint = createJokerButton("💡 İpucu (2)", 0xFF0284C7);
        btnJokerDouble = createJokerButton("🔥 2x Puan (2)", 0xFFEA580C);

        btnJokerFifty.setOnClickListener(v -> useFiftyFiftyJoker());
        btnJokerHint.setOnClickListener(v -> useHintJoker());
        btnJokerDouble.setOnClickListener(v -> useDoubleScoreJoker());

        layoutJokerBar.addView(btnJokerFifty);
        layoutJokerBar.addView(btnJokerHint);
        layoutJokerBar.addView(btnJokerDouble);

        int idx = layoutActiveGameArena.indexOfChild(layoutGameOptionButtons);
        if (idx >= 0) {
            layoutActiveGameArena.addView(layoutJokerBar, idx);
        } else {
            layoutActiveGameArena.addView(layoutJokerBar);
        }
    }

    private Button createJokerButton(String text, int color) {
        int dp4 = (int) (4 * getResources().getDisplayMetrics().density);
        int h = (int) (44 * getResources().getDisplayMetrics().density);
        Button b = new Button(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, h, 1f);
        lp.setMargins(dp4, 0, dp4, 0);
        b.setLayoutParams(lp);
        b.setBackground(VeliThemeHelper.createButtonDrawable(this, color));
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(0xFFFFFFFF);
        b.setTextSize(12.5f);
        b.setTypeface(null, android.graphics.Typeface.BOLD);
        return b;
    }

    private void updateJokerButtonsUi() {
        if (btnJokerFifty != null) {
            btnJokerFifty.setText("⚡ %50 Ele (" + jokerFiftyCount + ")");
            btnJokerFifty.setAlpha(jokerFiftyCount > 0 ? 1.0f : 0.45f);
        }
        if (btnJokerHint != null) {
            btnJokerHint.setText("💡 İpucu (" + jokerHintCount + ")");
            btnJokerHint.setAlpha(jokerHintCount > 0 ? 1.0f : 0.45f);
        }
        if (btnJokerDouble != null) {
            btnJokerDouble.setText(doubleScoreActiveOnCurrentQuestion ? "🔥 2x AKTİF!" : "🔥 2x Puan (" + jokerDoubleCount + ")");
            btnJokerDouble.setAlpha((jokerDoubleCount > 0 || doubleScoreActiveOnCurrentQuestion) ? 1.0f : 0.45f);
        }
    }

    private void useFiftyFiftyJoker() {
        if (jokerFiftyCount <= 0 || currentQuestionItem == null) {
            Toast.makeText(this, "⚡ %50 Eleme hakkın kalmadı! 5 seri yapınca +1 hak kazanırsın.", Toast.LENGTH_SHORT).show();
            return;
        }
        int removed = 0;
        for (int i = 0; i < layoutGameOptionButtons.getChildCount(); i++) {
            View child = layoutGameOptionButtons.getChildAt(i);
            if (child instanceof Button && child.getVisibility() == View.VISIBLE) {
                Button b = (Button) child;
                if (!b.getText().toString().equals(currentQuestionItem.correctAnswer)) {
                    b.setVisibility(View.INVISIBLE);
                    b.setEnabled(false);
                    removed++;
                    if (removed >= 2) break;
                }
            }
        }
        if (removed > 0) {
            jokerFiftyCount--;
            musicSynth.playCoinEffect();
            tvGameFeedbackBanner.setText("⚡ %50 Joker kullanıldı! İki yanlış seçenek elendi.");
            updateJokerButtonsUi();
        }
    }

    private void useHintJoker() {
        if (jokerHintCount <= 0 || currentQuestionItem == null) {
            Toast.makeText(this, "💡 İpucu hakkın kalmadı! 5 seri yapınca +1 hak kazanırsın.", Toast.LENGTH_SHORT).show();
            return;
        }
        jokerHintCount--;
        musicSynth.playCoinEffect();
        String ans = currentQuestionItem.correctAnswer;
        String hintPreview = ans.length() > 2 ? (ans.substring(0, 2) + "...") : (ans.substring(0, 1) + "...");
        tvGameFeedbackBanner.setText("💡 İPUCU: Doğru cevap '" + hintPreview + "' ile başlıyor!");
        updateJokerButtonsUi();
    }

    private void useDoubleScoreJoker() {
        if (doubleScoreActiveOnCurrentQuestion) return;
        if (jokerDoubleCount <= 0) {
            Toast.makeText(this, "🔥 2x Çifte Puan hakkın kalmadı! 5 seri yapınca +1 hak kazanırsın.", Toast.LENGTH_SHORT).show();
            return;
        }
        jokerDoubleCount--;
        doubleScoreActiveOnCurrentQuestion = true;
        musicSynth.playCoinEffect();
        tvGameFeedbackBanner.setText("🔥 2x ÇİFTE PUAN AKTİF! Bu soruyu bilirsen iki kat puan kazanacaksın!");
        updateJokerButtonsUi();
    }

    private void setFullScreenJumpMode(boolean fullScreen) {
        int pad16 = (int) (16 * getResources().getDisplayMetrics().density);
        if (fullScreen) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
            layoutEntHeaderCard.setVisibility(View.GONE);
            tvJumpGameStatus.setVisibility(View.GONE);
            btnJumpAction.setVisibility(View.GONE);
            btnBackFromJumpGame.setVisibility(View.GONE);

            if (layoutRootInnerContainer != null) {
                layoutRootInnerContainer.setPadding(0, 0, 0, 0);
            }
            if (layoutJumpCardWrapper != null) {
                layoutJumpCardWrapper.setPadding(0, 0, 0, 0);
                layoutJumpCardWrapper.setBackgroundColor(0xFF0F172A);
                LinearLayout.LayoutParams wlp = (LinearLayout.LayoutParams) layoutJumpCardWrapper.getLayoutParams();
                if (wlp != null) {
                    wlp.setMargins(0, 0, 0, 0);
                    layoutJumpCardWrapper.setLayoutParams(wlp);
                }
            }
            int screenH = getResources().getDisplayMetrics().heightPixels;
            ViewGroup.LayoutParams clp = frameJumpGameCanvas.getLayoutParams();
            if (clp != null) {
                clp.height = screenH;
                frameJumpGameCanvas.setLayoutParams(clp);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            layoutEntHeaderCard.setVisibility(View.VISIBLE);
            if (layoutRootInnerContainer != null) {
                layoutRootInnerContainer.setPadding(pad16, pad16, pad16, pad16);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        musicSynth.startBackgroundMusic(activeGameIndex);
        timerHandler.removeCallbacks(secondTickRunnable);
        timerHandler.postDelayed(secondTickRunnable, 1000L);
    }

    @Override
    protected void onPause() {
        timerHandler.removeCallbacks(secondTickRunnable);
        if (jumpGameView != null) jumpGameView.stopGame();
        if (starEffectView != null) starEffectView.setSceneState(activeGameIndex, questionsSolvedInSession, getCurrentDifficultyTier(), false);
        musicSynth.stopBackgroundMusic();
        saveActiveGameSessionToCalendarIfNeeded();
        super.onPause();
    }

    private static String formatClockSeconds(int totalSeconds) {
        int mins = Math.max(0, totalSeconds) / 60;
        int secs = Math.max(0, totalSeconds) % 60;
        return String.format(java.util.Locale.getDefault(), "%02d:%02d", mins, secs);
    }

    private void updateTimerBannerUi() {
        tvEntTimerStatus.setText("🛠️ Veli Test Modu Aktif (Süre / Kilit Sınırı Yok) • Süre: "
                + formatClockSeconds(totalEntElapsedSeconds));
    }

    private void saveActiveGameSessionToCalendarIfNeeded() {
        activeGameSessionSeconds = 0;
    }

    public static List<GameDefinition> buildAllGamesList(int grade, int customSubjectCount) {
        List<GameDefinition> games = new ArrayList<>();
        games.add(new GameDefinition(8, "🏃‍♂️ Süper Yıldız Zıplama Macerası",
                "Tam ekran 6 farklı parkurda 3-2-1 geri sayımla başla, trambolinlerle uç ve yıldızları topla!",
                0xFFFF5722, true));

        games.add(new GameDefinition(0, "🚀 Galaksi Sayı Avcısı",
                grade + ". Sınıf uzay gemisiyle kolaydan zora hedefleri vur ve meteorları parçala!", 0xFF1E88E5, false));
        games.add(new GameDefinition(1, "🏰 Gizemli Kelime Kalesi",
                grade + ". Sınıf şifreli parolaları, atasözlerini ve anlam hazinelerini çöz!", 0xFF8E24AA, false));
        games.add(new GameDefinition(2, "🧪 Çılgın Laboratuvar Keşfi",
                grade + ". Sınıf gezegenler, doğa gizemleri ve enerji deneylerini tamamla!", 0xFF00897B, false));
        games.add(new GameDefinition(3, "🧭 Zaman Gezgini Hazine Avı",
                grade + ". Sınıf pusula, harita ve kültür hazinelerinin izini sür!", 0xFFF57C00, false));
        games.add(new GameDefinition(4, "🌍 Sihirli Köprü Parolası",
                grade + ". Sınıf dünya kelimelerini eşleştirerek altın köprüleri aç!", 0xFFD81B60, false));
        games.add(new GameDefinition(5, "🕊️ İyilik Bahçesi Yıldızları",
                grade + ". Sınıf erdem, paylaşım ve güzel davranış yıldızlarını topla!", 0xFF43A047, false));
        games.add(new GameDefinition(6, "🏎️ Süper Kaptan Güvenli Rota",
                grade + ". Sınıf işaretleri tanı, güvenli rotayı seç ve kupayı kazan!", 0xFFE53935, false));
        games.add(new GameDefinition(7, "⚖️ Barış Adası Kahramanları",
                grade + ". Sınıf adalet, haklar ve dayanışma görevlerini tamamla!", 0xFF3949AB, false));

        for (int i = 8; i < customSubjectCount; i++) {
            int extraIdx = i - 7;
            games.add(new GameDefinition(i % 8, "🌟 Bilgelik Kristali Macerası #" + extraIdx,
                    grade + ". Sınıf özel bilgi kristallerini toplayarak rekor kır!", 0xFF00ACC1, false));
        }
        return games;
    }

    private void buildGameLobbyCards() {
        layoutGameCardsList.removeAllViews();
        int grade = testGradeLevel;
        int subjCount = 8;
        List<GameDefinition> games = buildAllGamesList(grade, subjCount);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        int margin = (int) (12 * getResources().getDisplayMetrics().density);

        for (final GameDefinition g : games) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundResource(R.drawable.bg_white_card);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            lp.setMargins(0, 0, 0, margin);
            card.setLayoutParams(lp);
            card.setPadding(pad, pad, pad, pad);

            TextView tvTitle = new TextView(this);
            tvTitle.setText(g.gameTitle);
            tvTitle.setTextSize(18f);
            tvTitle.setTextColor(g.accentColor);
            tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);

            TextView tvSub = new TextView(this);
            tvSub.setText(g.subtitle);
            tvSub.setTextSize(14f);
            tvSub.setTextColor(0xFF374151);
            tvSub.setPadding(0, pad / 3, 0, pad / 2);

            Button btnPlay = new Button(this);
            btnPlay.setBackground(VeliThemeHelper.createButtonDrawable(this, g.accentColor));
            btnPlay.setText(g.isPlatformerJump ? "🏃‍♂️ Tam Ekran Zıplama Macerasını Başlat" : "▶️ Canlı Maceraya Başla");
            btnPlay.setAllCaps(false);
            btnPlay.setTextColor(0xFFFFFFFF);
            btnPlay.setTextSize(15f);
            btnPlay.setTypeface(null, android.graphics.Typeface.BOLD);
            btnPlay.setOnClickListener(v -> startSelectedGame(g));

            card.addView(tvTitle);
            card.addView(tvSub);
            card.addView(btnPlay);
            layoutGameCardsList.addView(card);
        }
    }

    private void showLobbyScreen() {
        inActiveGame = false;
        setFullScreenJumpMode(false);
        if (starEffectView != null) {
            starEffectView.setSceneState(0, 0, 1, false);
        }
        layoutPlatformerJumpArena.setVisibility(View.GONE);
        layoutActiveGameArena.setVisibility(View.GONE);
        layoutGameLobbyContainer.setVisibility(View.VISIBLE);
    }

    private int getCurrentDifficultyTier() {
        if (questionsSolvedInSession < 3) return 1; // 1..3. sorular: Seviye 1 (Çok Kolay Isınma)
        if (questionsSolvedInSession < 7) return 2; // 4..7. sorular: Seviye 2 (Gelişim)
        if (questionsSolvedInSession < 12) return 3; // 8..12. sorular: Seviye 3 (Uzman)
        return 4; // 13+ sorular: Seviye 4 (Şampiyonlar Ligi)
    }

    private void startSelectedGame(GameDefinition game) {
        saveActiveGameSessionToCalendarIfNeeded();
        inActiveGame = true;
        activeGameIndex = game.index;
        activeGameTitle = game.gameTitle;
        activeGameSessionSeconds = 0;
        currentScore = 0;
        currentStreak = 0;
        bestStreak = 0;
        currentLives = 3;
        questionsSolvedInSession = 0;
        jokerFiftyCount = 2;
        jokerHintCount = 2;
        jokerDoubleCount = 2;
        doubleScoreActiveOnCurrentQuestion = false;

        musicSynth.setMelodyIndex(game.index);
        layoutGameLobbyContainer.setVisibility(View.GONE);

        if (game.isPlatformerJump) {
            layoutActiveGameArena.setVisibility(View.GONE);
            layoutPlatformerJumpArena.setVisibility(View.VISIBLE);
            setFullScreenJumpMode(true);
            jumpGameView.resetGame();
            jumpGameView.startGame();
        } else {
            setFullScreenJumpMode(false);
            layoutPlatformerJumpArena.setVisibility(View.GONE);
            layoutActiveGameArena.setVisibility(View.VISIBLE);
            tvActiveGameTitle.setText(game.gameTitle);
            tvGameFeedbackBanner.setText("");
            updateStatsBar();
            updateJokerButtonsUi();
            loadNextQuestion();
        }
    }

    private void updateStatsBar() {
        StringBuilder hearts = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            hearts.append(i < currentLives ? "❤️" : "🖤");
        }
        int tier = getCurrentDifficultyTier();
        tvGameStatsBar.setText("⭐ Puan: " + currentScore + "  •  🔥 Seri: " + currentStreak + "x  •  Seviye " + tier + "  •  " + hearts);
        if (starEffectView != null) {
            starEffectView.setSceneState(activeGameIndex, questionsSolvedInSession, tier, inActiveGame);
        }
    }

    private void loadNextQuestion() {
        if (currentLives <= 0) return;
        doubleScoreActiveOnCurrentQuestion = false;
        updateJokerButtonsUi();

        int grade = testGradeLevel;
        int tier = getCurrentDifficultyTier();
        QuestionItem candidate = generateGradeScaledQuestion(activeGameIndex % 8, grade, tier, random);
        for (int attempt = 0; attempt < 45; attempt++) {
            if (!askedPrompts.contains(candidate.prompt)) {
                break;
            }
            candidate = generateGradeScaledQuestion(activeGameIndex % 8, grade, tier, random);
        }
        if (askedPrompts.size() > 350) {
            askedPrompts.clear();
        }
        askedPrompts.add(candidate.prompt);
        currentQuestionItem = candidate;
        final QuestionItem q = candidate;

        tvGameQuestionPrompt.setText(q.prompt);
        layoutGameOptionButtons.removeAllViews();

        VeliThemeHelper.Palette p = VeliThemeHelper.getPalette(this);
        int[] optionColors = new int[]{p.primary, p.secondary, p.accent, p.primaryDark};
        int marginPx = (int) (10 * getResources().getDisplayMetrics().density);
        int heightPx = (int) (56 * getResources().getDisplayMetrics().density);

        for (int i = 0; i < q.options.size(); i++) {
            final String chosen = q.options.get(i);
            Button btn = new Button(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, heightPx);
            lp.setMargins(0, 0, 0, marginPx);
            btn.setLayoutParams(lp);
            btn.setBackground(VeliThemeHelper.createButtonDrawable(this, optionColors[i % optionColors.length]));
            btn.setText(chosen);
            btn.setAllCaps(false);
            btn.setTextColor(0xFFFFFFFF);
            btn.setTextSize(17f);
            btn.setTypeface(null, android.graphics.Typeface.BOLD);

            btn.setOnClickListener(v -> onAnswerSelected(chosen, q.correctAnswer));
            layoutGameOptionButtons.addView(btn);
        }
    }

    private void onAnswerSelected(String chosen, String correct) {
        if (currentLives <= 0) return;
        if (chosen.equals(correct)) {
            currentStreak++;
            questionsSolvedInSession++;
            if (currentStreak > bestStreak) {
                bestStreak = currentStreak;
            }
            int tier = getCurrentDifficultyTier();
            int gained = (10 + (currentStreak * 5) + (tier * 5)) * (doubleScoreActiveOnCurrentQuestion ? 2 : 1);
            currentScore += gained;

            // Her 5 seride +1 Joker hediye et
            if (currentStreak % 5 == 0) {
                jokerFiftyCount++;
                jokerHintCount++;
                tvGameFeedbackBanner.setText("🎁 5x SERİ ÖDÜLÜ! +1 Eleme ve +1 İpucu Jokeri Kazandın!");
            } else {
                tvGameFeedbackBanner.setText("✅ Harika! Doğru cevap: " + correct + " (+" + gained + " Puan)");
            }

            musicSynth.playSuccessEffect();
            starEffectView.triggerBurst("🌟 +" + gained + " PUAN! (" + currentStreak + "x SERİ!) 🌟");
            updateStatsBar();
            updateJokerButtonsUi();
            timerHandler.postDelayed(this::loadNextQuestion, 620L);
        } else {
            currentStreak = 0;
            currentLives--;
            musicSynth.playWrongEffect();
            tvGameFeedbackBanner.setText("💡 Dikkat! Doğru cevap '" + correct + "' olacaktı.");
            updateStatsBar();
            if (currentLives <= 0) {
                showQuizGameOverDialog();
            } else {
                timerHandler.postDelayed(this::loadNextQuestion, 900L);
            }
        }
    }

    private void showQuizGameOverDialog() {
        if (isFinishing()) return;
        new AlertDialog.Builder(this)
                .setCancelable(false)
                .setTitle("💔 3 Canın Bitti! Oyun Sona Erdi")
                .setMessage("Oyun: " + activeGameTitle
                        + "\n🎯 Çözülen Soru: " + questionsSolvedInSession
                        + "\n⭐ Toplam Puanın: " + currentScore
                        + "\n🔥 En Yüksek Serin: " + bestStreak + "x"
                        + "\n\nKolaydan zora yeni sorularla tekrar oynamak ister misin?")
                .setPositiveButton("🔄 Tekrar Oyna", (d, w) -> {
                    currentScore = 0;
                    currentStreak = 0;
                    bestStreak = 0;
                    currentLives = 3;
                    questionsSolvedInSession = 0;
                    jokerFiftyCount = 2;
                    jokerHintCount = 2;
                    jokerDoubleCount = 2;
                    updateStatsBar();
                    updateJokerButtonsUi();
                    loadNextQuestion();
                })
                .setNegativeButton("🏠 Oyun Lobisine Dön", (d, w) -> {
                    saveActiveGameSessionToCalendarIfNeeded();
                    showLobbyScreen();
                })
                .show();
    }

    // =========================================================================
    // KADEMELİ ZORLAŞAN AKILLI SORU MOTORU (Seviye 1 Çok Kolay ➔ Seviye 4 Zor)
    // =========================================================================

    private static final String[][] SYNONYM_PAIRS = new String[][]{
            // İlk 16 tanesi en temel/kolay kelimeler (Seviye 1 Isınma için)
            {"Okul", "Mektep"}, {"Hediye", "Armağan"}, {"Beyaz", "Ak"}, {"Siyah", "Kara"},
            {"Kırmızı", "Al"}, {"Misafir", "Konuk"}, {"Yıl", "Sene"}, {"İhtiyar", "Yaşlı"},
            {"Hekim", "Doktor"}, {"Soru", "Sual"}, {"Cevap", "Yanıt"}, {"Kalp", "Yürek"},
            {"Öykü", "Hikâye"}, {"Kafa", "Baş"}, {"Konut", "Ev"}, {"Öğrenci", "Talebe"},
            // Seviye 2-4 için zenginleştirilmiş kelimeler
            {"Özgürlük", "Hürriyet"}, {"Sonbahar", "Güz"}, {"Rüzgâr", "Yel"}, {"Anı", "Hatıra"},
            {"Zaman", "Vakit"}, {"Doğa", "Tabiat"}, {"Yetenek", "Kabiliyet"}, {"Görev", "Vazife"},
            {"Ulus", "Millet"}, {"Yurt", "Vatan"}, {"Barış", "Sulh"}, {"Tutsak", "Esir"},
            {"Fayda", "Yarar"}, {"Zarar", "Ziyan"}, {"Yoksul", "Fakir"}, {"Pabuç", "Ayakkabı"},
            {"Giysi", "Elbise"}, {"Besin", "Gıda"}, {"Asır", "Yüzyıl"}, {"Kelime", "Sözcük"},
            {"Cümle", "Tümce"}, {"Öğretmen", "Muallim"}, {"Sınav", "İmtihan"}, {"Delil", "Kanıt"},
            {"Kuvvet", "Güç"}, {"Sevinç", "Mutluluk"}, {"Özlem", "Hasret"}, {"Uzak", "Irak"},
            {"Nehir", "Irmak"}, {"Duru", "Berrak"}, {"Kılavuz", "Rehber"}, {"Şair", "Ozan"}
    };

    private static final String[][] ANTONYM_PAIRS = new String[][]{
            // İlk 16 tanesi en kolay zıt anlamlılar (Seviye 1 Isınma)
            {"Siyah", "Beyaz"}, {"Büyük", "Küçük"}, {"Uzun", "Kısa"}, {"Sıcak", "Soğuk"},
            {"Hızlı", "Yavaş"}, {"Açık", "Kapalı"}, {"Gece", "Gündüz"}, {"İyi", "Kötü"},
            {"Dolu", "Boş"}, {"Ağır", "Hafif"}, {"Genç", "Yaşlı"}, {"Güzel", "Çirkin"},
            {"Alt", "Üst"}, {"İç", "Dış"}, {"Sağ", "Sol"}, {"İlk", "Son"},
            // Seviye 2-4 için zıt anlamlılar
            {"Cesur", "Korkak"}, {"Acemi", "Usta"}, {"Taze", "Bayat"}, {"Geniş", "Dar"},
            {"Yüksek", "Alçak"}, {"Zengin", "Fakir"}, {"Ucuz", "Pahalı"}, {"Erken", "Geç"},
            {"Doğru", "Yanlış"}, {"İleri", "Geri"}, {"Doğu", "Batı"}, {"Kuzey", "Güney"},
            {"Barış", "Savaş"}, {"Ödül", "Ceza"}, {"Sevinç", "Üzüntü"}, {"Dost", "Düşman"},
            {"Sert", "Yumuşak"}, {"Derin", "Sığ"}, {"Parlak", "Mat"}, {"Net", "Bulanık"},
            {"Bolluk", "Kıtlık"}, {"Üretim", "Tüketim"}, {"Gelir", "Gider"}, {"Doğal", "Yapay"}
    };

    private static final String[][] PROVERBS_AND_IDIOMS = new String[][]{
            {"Damlaya damlaya göl olur", "Tutumluluk ve küçük birikimlerin büyümesi", "göl", "deniz", "nehir", "yağmur"},
            {"Bir elin nesi var, iki elin sesi var", "Yardımlaşma ve dayanışmanın gücü", "sesi", "gücü", "sözü", "yolu"},
            {"Ağaç yaşken eğilir", "Eğitimin küçük yaşta başlamasının önemi", "yaşken", "büyükken", "uzunken", "yeşilken"},
            {"Sakla samanı, gelir zamanı", "Her eşyanın bir gün işe yarayabileceği (tutumluluk)", "zamanı", "baharı", "sırası", "günü"},
            {"Akacak kan damarda durmaz", "Olacak bir işin önüne geçilemeyeceği", "damarda", "yürekte", "yerde", "suda"},
            {"Gülme komşuna, gelir başına", "Başkasının durumuna alay edilmemesi gerektiği", "başına", "evine", "kapısına", "yoluna"},
            {"İşleyen demir pas tutmaz", "Çalışkan insanın dinç ve başarılı kalacağı", "pas", "toz", "kir", "su"},
            {"Ne ekersen onu biçersin", "Yaptığın davranışların karşılığını görürsün", "biçersin", "bulursun", "alırsın", "yersin"},
            {"Tatlı dil yılanı deliğinden çıkarır", "Güzel ve nazik konuşmanın ikna gücü", "deliğinden", "ormandan", "yuvasından", "taşından"},
            {"Üzüm üzüme baka baka kararır", "Birlikte vakit geçirenlerin birbirine benzemesi", "kararır", "tatlanır", "büyür", "olgunlaşır"},
            {"Aslan yattığı yerden belli olur", "Düzenli ve temiz olmanın kişiliği yansıttığı", "yattığı", "koştuğu", "baktığı", "gezdiği"},
            {"Vakit nakittir", "Zamanın çok değerli olduğu", "nakittir", "altındır", "suyudur", "rüzgârdır"},
            {"Kulak misafiri olmak", "Yanında konuşulanları istemeden duymak", "misafiri", "arkadaşı", "komşusu", "yolcusu"},
            {"Göz boyamak", "Gösterişle yanıltmak, olduğundan farklı göstermek", "boyamak", "açmak", "kapatmak", "silmek"},
            {"Etekleri zil çalmak", "Çok sevinmek, heyecanlanmak", "zil", "davul", "flüt", "çan"},
            {"Burnunda tütmek", "Çok özlemek, hasret duymak", "tütmek", "yanmak", "sızlamak", "büyümek"},
            {"İpe un sermek", "Bir işi yapmamak için bahaneler üretmek", "un", "su", "taş", "kum"},
            {"Ağzı kulaklarına varmak", "Çok mutlu olmak, sevinçten gülümsemek", "kulaklarına", "gözlerine", "ellerine", "omzuna"},
            {"Kılı kırk yarmak", "Çok titiz ve ayrıntılı incelemek", "kırk", "yüz", "on", "bin"},
            {"Pabucu dama atılmak", "Değerini veya önceki ilgisini kaybetmek", "dama", "sokağa", "bahçeye", "çatıya"}
    };

    private static final String[][] SCIENCE_ELEMENTS = new String[][]{
            {"Hidrojen", "H"}, {"Helyum", "He"}, {"Karbon", "C"}, {"Azot", "N"},
            {"Oksijen", "O"}, {"Sodyum", "Na"}, {"Magnezyum", "Mg"}, {"Alüminyum", "Al"},
            {"Klor", "Cl"}, {"Kalsiyum", "Ca"}, {"Demir", "Fe"}, {"Bakır", "Cu"},
            {"Gümüş", "Ag"}, {"Altın", "Au"}, {"Çinko", "Zn"}, {"Potasyum", "K"}
    };

    private static final String[][] SCIENCE_FACTS = new String[][]{
            // İlk 8 tanesi kolay ısınma soruları (Seviye 1)
            {"Dünyamızın ısı ve ışık kaynağı olan yıldız hangisidir?", "Güneş", "Ay", "Mars", "Venüs"},
            {"Suyun donarak katı hale geçmiş şekline ne denir?", "Buz", "Buhar", "Sis", "Bulut"},
            {"Mıknatıs aşağıdaki maddelerden hangisini çeker?", "Demir çivi", "Tahta kalem", "Cam bardak", "Plastik silgi"},
            {"Vücudumuzda kanı pompalayan organımız hangisidir?", "Kalp", "Mide", "Kemik", "Deri"},
            {"Dünya'nın doğal uydusu aşağıdakilerden hangisidir?", "Ay", "Güneş", "Mars", "Satürn"},
            {"Soluk alıp verirken kullandığımız temel solunum organımız hangisidir?", "Akciğer", "Mide", "Böbrek", "Karaciğer"},
            {"Güneş'e en yakın olan gezegen hangisidir?", "Merkür", "Venüs", "Mars", "Jüpiter"},
            {"Etrafındaki belirgin halkalarıyla ünlü gezegen hangisidir?", "Satürn", "Mars", "Merkür", "Venüs"},
            // Orta ve İleri Seviye Sorular (Seviye 2-4)
            {"Güneş sisteminin en büyük gezegeni hangisidir?", "Jüpiter", "Satürn", "Dünya", "Neptün"},
            {"Kızıl Gezegen olarak bilinen gezegen hangisidir?", "Mars", "Venüs", "Merkür", "Uranüs"},
            {"Katı bir maddenin ısı alarak doğrudan gaz haline geçmesine ne denir?", "Süblimleşme", "Erime", "Yoğuşma", "Donma"},
            {"Gaz halindeki bir maddenin ısı vererek sıvı hale geçmesine ne denir?", "Yoğuşma", "Buharlaşma", "Erime", "Kırağılaşma"},
            {"Sıvı bir maddenin ısı vererek katı hale geçmesine ne ad verilir?", "Donma", "Erime", "Kaynama", "Süblimleşme"},
            {"Havadaki su buharının soğuk yüzeylerde doğrudan buz kristallerine dönüşmesine ne denir?", "Kırağılaşma", "Buharlaşma", "Erime", "Kaynama"},
            {"Bitkilerin güneş ışığı yardımıyla besin ve oksijen üretmesine ne ad verilir?", "Fotosentez", "Solunum", "Terleme", "Sindirim"},
            {"Hücrede enerji üretiminden sorumlu organel hangisidir?", "Mitokondri", "Ribozom", "Koful", "Sentrozom"},
            {"Hücrede protein sentezinden sorumlu en küçük organel hangisidir?", "Ribozom", "Mitokondri", "Lizozom", "Golgi"},
            {"Bitki hücresinde fotosentezin gerçekleştiği yeşil renkli organel hangisidir?", "Kloroplast", "Mitokondri", "Çekirdek", "Ribozom"},
            {"Kuvveti ölçmek için kullanılan yaylı ölçü aletine ne ad verilir?", "Dinamometre", "Termometre", "Barometre", "Kronometre"},
            {"Sıcaklığı ölçmek için kullanılan alete ne ad verilir?", "Termometre", "Dinamometre", "Ampermetre", "Voltmetre"},
            {"Elektrik devresinde akım şiddetini ölçen alet hangisidir?", "Ampermetre", "Voltmetre", "Termometre", "Dinamometre"},
            {"Ses en hızlı hangi ortamda yayılır?", "Katı maddelerde", "Sıvılarda", "Gazlarda", "Uzay boşluğunda"},
            {"Kanımızı süzerek atık maddeleri idrar olarak uzaklaştıran organımız hangisidir?", "Böbrek", "Mide", "Akciğer", "Kalp"},
            {"Besinlerin kimyasal sindiriminin tamamlandığı ve kana emildiği organ hangisidir?", "İnce bağırsak", "Yutak", "Yemek borusu", "Kalın bağırsak"}
    };

    private static final String[][] GEOGRAPHY_HISTORY_FACTS = new String[][]{
            // İlk 6 tanesi kolay ısınma soruları (Seviye 1)
            {"Türkiye'nin başkenti hangi şehrimizdir?", "Ankara", "İstanbul", "İzmir", "Bursa"},
            {"Güneş her sabah hangi ana yönden doğar?", "Doğu", "Batı", "Kuzey", "Güney"},
            {"Pusulanın renkli ucu her zaman hangi yönü gösterir?", "Kuzey", "Güney", "Doğu", "Batı"},
            {"Haritalarda denizler ve okyanuslar hangi renkle gösterilir?", "Mavi", "Kahverengi", "Yeşil", "Sarı"},
            {"Türkiye'de toplam kaç coğrafi bölge vardır?", "7 bölge", "5 bölge", "9 bölge", "10 bölge"},
            {"Cumhuriyet Bayramımızı her yıl hangi tarihte kutlarız?", "29 Ekim", "23 Nisan", "19 Mayıs", "30 Ağustos"},
            // Orta ve İleri Seviye Sorular (Seviye 2-4)
            {"Türkiye'nin en yüksek dağı aşağıdakilerden hangisidir?", "Ağrı Dağı", "Erciyes Dağı", "Uludağ", "Palandöken"},
            {"Türkiye'nin en büyük gölü hangisidir?", "Van Gölü", "Tuz Gölü", "Beyşehir Gölü", "Eğirdir Gölü"},
            {"Türkiye sınırları içinde doğup yine Türkiye'de denize dökülen en uzun akarsuyumuz hangisidir?", "Kızılırmak", "Yeşilırmak", "Sakarya", "Gediz"},
            {"Ülkemizde yazların sıcak ve kurak, kışların ılık ve yağışlı geçtiği iklimde bitki örtüsü nedir?", "Maki", "Bozkır", "Gür Orman", "Tundra"},
            {"Karadeniz ikliminin doğal bitki örtüsü aşağıdakilerden hangisidir?", "Orman", "Bozkır", "Maki", "Çöl"},
            {"İç Anadolu Bölgesi'nde görülen karasal iklimin doğal bitki örtüsü nedir?", "Bozkır (Step)", "Maki", "Orman", "Savan"},
            {"Tarihte parayı ilk kez icat eden Anadolu uygarlığı hangisidir?", "Lidyalılar", "Hititler", "Frigler", "Urartular"},
            {"Tarihte ilk yazılı antlaşma olan Kadeş Antlaşması'nı imzalayan Anadolu uygarlığı hangisidir?", "Hititler", "İyonlar", "Sümerler", "Babiller"},
            {"Çivi yazısını bularak tarihi çağları başlatan Mezopotamya uygarlığı hangisidir?", "Sümerler", "Asurlar", "Urartular", "Frigler"},
            {"Çentikli iğne olan 'Fibula'yı ve tapates halılarını yapan Anadolu uygarlığı hangisidir?", "Frigler", "Lidyalılar", "Hititler", "İyonlar"},
            {"Mustafa Kemal Atatürk'ün Samsun'a çıkarak Milli Mücadele'yi başlattığı tarih hangisidir?", "19 Mayıs 1919", "23 Nisan 1920", "29 Ekim 1923", "30 Ağustos 1922"},
            {"Türkiye Büyük Millet Meclisi (TBMM) hangi tarihte Ankara'da açılmıştır?", "23 Nisan 1920", "19 Mayıs 1919", "29 Ekim 1923", "10 Kasım 1938"},
            {"Ekvator'a paralel olarak çizildiği varsayılan hayali çemberlere ne ad verilir?", "Paralel (Enlem)", "Meridyen (Boylam)", "Ölçek", "İzohips"},
            {"İki meridyen arasındaki yerel saat farkı her yerde kaç dakikadır?", "4 dakika", "10 dakika", "15 dakika", "60 dakika"},
            {"Peri bacaları ve yer altı şehirleriyle ünlü Kapadokya yöremiz hangi bölgemizdedir?", "İç Anadolu Bölgesi", "Karadeniz Bölgesi", "Marmara Bölgesi", "Ege Bölgesi"},
            {"Pamukkale travertenleri hangi ilimizde yer almaktadır?", "Denizli", "Antalya", "Muğla", "Aydın"},
            {"Sümela Manastırı ve Uzungöl hangi ilimizde bulunmaktadır?", "Trabzon", "Rize", "Artvin", "Ordu"}
    };

    private static final String[][] ENGLISH_VOCAB = new String[][]{
            // İlk 15 tanesi çok kolay ısınma kelimeleri (Seviye 1)
            {"Apple", "Elma", "Meyve"}, {"Red", "Kırmızı", "Renk"}, {"Blue", "Mavi", "Renk"},
            {"Cat", "Kedi", "Hayvan"}, {"Dog", "Köpek", "Hayvan"}, {"Book", "Kitap", "Eşya"},
            {"School", "Okul", "Mekân"}, {"Water", "Su", "İçecek"}, {"Milk", "Süt", "İçecek"},
            {"Sun", "Güneş", "Doğa"}, {"Star", "Yıldız", "Doğa"}, {"Mother", "Anne", "Aile"},
            {"Father", "Baba", "Aile"}, {"Green", "Yeşil", "Renk"}, {"Yellow", "Sarı", "Renk"},
            // Seviye 2-4 kelimeleri
            {"Orange", "Portakal", "Meyve"}, {"Banana", "Muz", "Meyve"},
            {"Strawberry", "Çilek", "Meyve"}, {"Watermelon", "Karpuz", "Meyve"}, {"Cherry", "Kiraz", "Meyve"},
            {"Lion", "Aslan", "Hayvan"}, {"Elephant", "Fil", "Hayvan"}, {"Dolphin", "Yunus", "Hayvan"},
            {"Butterfly", "Kelebek", "Hayvan"}, {"Rabbit", "Tavşan", "Hayvan"}, {"Turtle", "Kaplumbağa", "Hayvan"},
            {"Eagle", "Kartal", "Hayvan"}, {"Monkey", "Maymun", "Hayvan"}, {"Horse", "At", "Hayvan"},
            {"Purple", "Mor", "Renk"}, {"White", "Beyaz", "Renk"},
            {"Black", "Siyah", "Renk"}, {"Pink", "Pembe", "Renk"}, {"Brown", "Kahverengi", "Renk"},
            {"Teacher", "Öğretmen", "Meslek"}, {"Doctor", "Doktor", "Meslek"}, {"Engineer", "Mühendis", "Meslek"},
            {"Pilot", "Pilot", "Meslek"}, {"Architect", "Mimar", "Meslek"}, {"Scientist", "Bilim İnsanı", "Meslek"},
            {"Dentist", "Diş Hekimi", "Meslek"}, {"Chef", "Aşçı", "Meslek"}, {"Farmer", "Çiftçi", "Meslek"},
            {"Monday", "Pazartesi", "Gün"}, {"Tuesday", "Salı", "Gün"}, {"Wednesday", "Çarşamba", "Gün"},
            {"Thursday", "Perşembe", "Gün"}, {"Friday", "Cuma", "Gün"}, {"Saturday", "Cumartesi", "Gün"},
            {"Sunday", "Pazar", "Gün"}, {"Spring", "İlkbahar", "Mevsim"}, {"Summer", "Yaz", "Mevsim"},
            {"Autumn", "Sonbahar", "Mevsim"}, {"Winter", "Kış", "Mevsim"}, {"Library", "Kütüphane", "Mekân"},
            {"Hospital", "Hastane", "Mekân"}, {"Bakery", "Fırın", "Mekân"}, {"Museum", "Müze", "Mekân"},
            {"Kitchen", "Mutfak", "Ev"}, {"Bedroom", "Yatak Odası", "Ev"}, {"Garden", "Bahçe", "Ev"},
            {"Umbrella", "Şemsiye", "Eşya"}, {"Notebook", "Defter", "Eşya"}, {"Pencil", "Kurşun Kalem", "Eşya"},
            {"Mirror", "Ayna", "Eşya"}, {"Clock", "Saat", "Eşya"}, {"Bridge", "Köprü", "Yapı"}
    };

    private static final String[][] ENGLISH_OPPOSITES = new String[][]{
            {"Big", "Small"}, {"Hot", "Cold"}, {"Fast", "Slow"}, {"Tall", "Short"},
            {"Happy", "Sad"}, {"Young", "Old"}, {"Clean", "Dirty"}, {"Easy", "Difficult"},
            {"Heavy", "Light"}, {"Full", "Empty"}, {"Open", "Closed"}, {"Early", "Late"},
            {"Strong", "Weak"}, {"Rich", "Poor"}, {"Long", "Short"}, {"New", "Old"}
    };

    public static QuestionItem generateGradeScaledQuestion(int gameCategory, int grade, Random random) {
        return generateGradeScaledQuestion(gameCategory, grade, 2, random);
    }

    /**
     * difficultyTier (1 = Çok Kolay Isınma, 2 = Gelişim, 3 = Uzman, 4 = Şampiyon):
     * İlk sorular her zaman Seviye 1 (Kolay) ile başlar, öğrenci soru bildikçe zorluk kademeli artar!
     */
    public static QuestionItem generateGradeScaledQuestion(int gameCategory, int grade, int difficultyTier, Random random) {
        int effectiveGrade = grade;
        if (difficultyTier == 1) {
            effectiveGrade = Math.max(1, grade - 2); // Isınma turunda daha kolay temel sorular
        } else if (difficultyTier == 2) {
            effectiveGrade = Math.max(1, grade - 1);
        } else if (difficultyTier >= 4) {
            effectiveGrade = Math.min(8, grade + 1);
        }

        if (gameCategory == 0) {
            return generateMathQuestion(effectiveGrade, difficultyTier, random);
        } else if (gameCategory == 1) {
            return generateTurkishQuestion(effectiveGrade, difficultyTier, random);
        } else if (gameCategory == 2) {
            return generateScienceQuestion(effectiveGrade, difficultyTier, random);
        } else if (gameCategory == 3) {
            return generateSocialStudiesQuestion(effectiveGrade, difficultyTier, random);
        } else if (gameCategory == 4) {
            return generateEnglishQuestion(effectiveGrade, difficultyTier, random);
        } else if (gameCategory == 5) {
            return generateValuesQuestion(effectiveGrade, difficultyTier, random);
        } else if (gameCategory == 6) {
            return generateSafetyQuestion(effectiveGrade, difficultyTier, random);
        } else {
            return generateCivicsQuestion(effectiveGrade, difficultyTier, random);
        }
    }

    private static QuestionItem generateMathQuestion(int grade, int tier, Random random) {
        // Seviye 1 (Isınma Turu): Her sınıf için hızlı, moral veren kolay zihinden işlemler!
        if (tier == 1) {
            int mode = random.nextInt(3);
            if (mode == 0) {
                int a = 4 + random.nextInt(8 * Math.max(1, grade));
                int b = 3 + random.nextInt(6 * Math.max(1, grade));
                int ans = a + b;
                return new QuestionItem("🚀 Uzay İstasyonu Toplamı:\n" + a + " + " + b + " kaçtır?",
                        String.valueOf(ans), String.valueOf(ans + 2), String.valueOf(Math.max(1, ans - 2)), String.valueOf(ans + 5));
            } else if (mode == 1) {
                int b = 3 + random.nextInt(10);
                int ans = 4 + random.nextInt(10);
                int a = b + ans;
                return new QuestionItem("🚀 Kalkan Enerji Farkı:\n" + a + " - " + b + " kaçtır?",
                        String.valueOf(ans), String.valueOf(ans + 3), String.valueOf(Math.max(1, ans - 1)), String.valueOf(ans + 6));
            } else {
                int a = 2 + random.nextInt(Math.min(9, grade + 3));
                int b = 2 + random.nextInt(6);
                int ans = a * b;
                return new QuestionItem("🚀 Yıldız Çarpımı:\n" + a + " × " + b + " kaçtır?",
                        String.valueOf(ans), String.valueOf(ans + a), String.valueOf(Math.max(1, ans - b)), String.valueOf(a + b));
            }
        }

        int template = random.nextInt(8);
        if (grade <= 2) {
            if (template == 0) {
                int a = 8 + random.nextInt(25 * grade);
                int b = 5 + random.nextInt(20 * grade);
                int ans = a + b;
                return new QuestionItem("🚀 Uzay İstasyonu Toplamı:\n" + a + " + " + b + " kaçtır?",
                        String.valueOf(ans), String.valueOf(ans + 2), String.valueOf(Math.max(1, ans - 3)), String.valueOf(ans + 10));
            } else if (template == 1) {
                int a = 22 + random.nextInt(30 * grade);
                int b = 6 + random.nextInt(18);
                int ans = a - b;
                return new QuestionItem("🚀 Kalkan Enerji Farkı:\n" + a + " - " + b + " kaçtır?",
                        String.valueOf(ans), String.valueOf(ans + 4), String.valueOf(Math.max(0, ans - 2)), String.valueOf(ans + 7));
            } else if (template == 2) {
                int step = 2 + random.nextInt(5);
                int start = step * (1 + random.nextInt(6));
                int ans = start + step * 3;
                return new QuestionItem("🚀 Yıldız Örüntüsünde Sıradaki Sayı:\n" + start + " ➔ " + (start + step) + " ➔ " + (start + step * 2) + " ➔ ?",
                        String.valueOf(ans), String.valueOf(ans + step), String.valueOf(ans - 1), String.valueOf(ans + 2));
            } else if (template == 3) {
                int missing = 7 + random.nextInt(25);
                int add = 9 + random.nextInt(20);
                int sum = missing + add;
                return new QuestionItem("🚀 Gizli Roket Şifresi:\n□ + " + add + " = " + sum + " işleminde □ yerine hangi sayı gelmelidir?",
                        String.valueOf(missing), String.valueOf(missing + 3), String.valueOf(Math.max(1, missing - 2)), String.valueOf(sum + add));
            } else if (template == 4) {
                int tens = 2 + random.nextInt(7);
                int ones = 1 + random.nextInt(8);
                int val = tens * 10 + ones;
                return new QuestionItem("🚀 Basamak Kulesi:\n" + tens + " onluk ve " + ones + " birlikten oluşan sayı kaçtır?",
                        String.valueOf(val), String.valueOf(ones * 10 + tens), String.valueOf(val + 10), String.valueOf(val - 1));
            } else if (template == 5) {
                int boxes = 2 + random.nextInt(5);
                int perBox = 3 + random.nextInt(5);
                int ans = boxes * perBox;
                return new QuestionItem("🚀 Uzay Gemisinde " + boxes + " kutu var ve her kutuda " + perBox + " yıldız kristali bulunuyor. Toplam kaç kristal vardır?",
                        String.valueOf(ans), String.valueOf(boxes + perBox), String.valueOf(ans + perBox), String.valueOf(Math.max(1, ans - 2)));
            } else if (template == 6) {
                int n = 14 + random.nextInt(70);
                int nearestTen = ((n + 5) / 10) * 10;
                if (n % 10 == 5) nearestTen = ((n / 10) + 1) * 10;
                return new QuestionItem("🚀 Yakıt Yuvarlama:\n" + n + " sayısını en yakın onluğa yuvarladığımızda sonuç kaç olur?",
                        String.valueOf(nearestTen), String.valueOf(nearestTen + 10), String.valueOf(Math.max(10, nearestTen - 10)), String.valueOf(n));
            } else {
                int hours = 2 + random.nextInt(6);
                int delta = 2 + random.nextInt(4);
                int ans = hours + delta;
                return new QuestionItem("🚀 Saat " + hours + ":00'da havalanan uzay mekiği " + delta + " saat sonra inmiştir. Saat kaçta inmiştir?",
                        ans + ":00", (ans + 1) + ":00", (ans - 1) + ":00", (ans + 2) + ":00");
            }
        } else if (grade <= 4) {
            if (template == 0) {
                int a = 6 + random.nextInt(12);
                int b = 4 + random.nextInt(9);
                int c = 5 + random.nextInt(25);
                int ans = (a * b) + c;
                return new QuestionItem("🚀 Meteor Kalkanı:\n(" + a + " × " + b + ") + " + c + " işleminin sonucu kaçtır?",
                        String.valueOf(ans), String.valueOf(ans + 6), String.valueOf(Math.max(1, ans - 4)), String.valueOf(ans + 12));
            } else if (template == 1) {
                int b = 4 + random.nextInt(8);
                int q = 6 + random.nextInt(15);
                int a = b * q;
                return new QuestionItem("🚀 Filo Paylaşımı:\n" + a + " ÷ " + b + " işleminin sonucu kaçtır?",
                        String.valueOf(q), String.valueOf(q + 2), String.valueOf(Math.max(1, q - 3)), String.valueOf(q + 5));
            } else if (template == 2) {
                int w = 6 + random.nextInt(14);
                int l = w + 3 + random.nextInt(10);
                int perim = 2 * (w + l);
                return new QuestionItem("🚀 Dikdörtgen Güneş Panelinin kısa kenarı " + w + " m, uzun kenarı " + l + " m'dir. Çevresi kaç metredir?",
                        String.valueOf(perim), String.valueOf(w * l), String.valueOf(w + l), String.valueOf(perim + 10));
            } else if (template == 3) {
                int denom = 3 + random.nextInt(6);
                int mult = 4 + random.nextInt(10);
                int whole = denom * mult;
                int num = 1 + random.nextInt(denom - 1);
                int ans = mult * num;
                return new QuestionItem("🚀 Yakıt Deposundaki " + whole + " litrenin " + num + "/" + denom + "'ü kaç litredir?",
                        String.valueOf(ans), String.valueOf(ans + mult), String.valueOf(whole - ans), String.valueOf(ans + 4));
            } else if (template == 4) {
                int mins = 2 + random.nextInt(5);
                int extraSec = 10 + random.nextInt(45);
                int totalSec = mins * 60 + extraSec;
                return new QuestionItem("🚀 Zaman Dönüşümü:\n" + mins + " dakika " + extraSec + " saniye toplam kaç saniyedir?",
                        String.valueOf(totalSec), String.valueOf(totalSec + 20), String.valueOf(mins * 100 + extraSec), String.valueOf(totalSec - 30));
            } else if (template == 5) {
                int side = 5 + random.nextInt(12);
                int area = side * side;
                return new QuestionItem("🚀 Bir kenarı " + side + " birim olan kare şeklindeki uzay pistinin alanı kaç birimkaredir?",
                        String.valueOf(area), String.valueOf(side * 4), String.valueOf(area + side), String.valueOf(area - 4));
            } else if (template == 6) {
                int a = 120 + random.nextInt(350);
                int b = 85 + random.nextInt(220);
                int ans = a + b;
                return new QuestionItem("🚀 İki Keşif uydusundan birincisi " + a + " fotoğraf, ikincisi " + b + " fotoğraf çekti. Toplam kaç fotoğraf çekildi?",
                        String.valueOf(ans), String.valueOf(ans + 10), String.valueOf(ans - 15), String.valueOf(ans + 25));
            } else {
                int angle1 = 35 + random.nextInt(55);
                int angle2 = 40 + random.nextInt(50);
                int angle3 = 180 - (angle1 + angle2);
                return new QuestionItem("🚀 Üçgen Yelkenli bir uzay aracının iki iç açısı " + angle1 + "° ve " + angle2 + "° ise üçüncü iç açısı kaç derecedir?",
                        angle3 + "°", (angle3 + 10) + "°", (angle3 - 10) + "°", (90 - angle1 / 2) + "°");
            }
        } else if (grade <= 6) {
            if (template == 0) {
                int a = 8 + random.nextInt(14);
                int b = 6 + random.nextInt(12);
                int sub = 9 + random.nextInt(25);
                int ans = (a * b) - sub;
                return new QuestionItem("🚀 İşlem Önceliği Görevi:\n(" + a + " × " + b + ") - " + sub + " sonucu kaçtır?",
                        String.valueOf(ans), String.valueOf(ans + 6), String.valueOf(ans - 5), String.valueOf(ans + 14));
            } else if (template == 1) {
                int base = 2 + random.nextInt(5);
                int exp = (base <= 3) ? (3 + random.nextInt(2)) : 3;
                int ans = 1;
                for (int i = 0; i < exp; i++) ans *= base;
                return new QuestionItem("🚀 Üslü Reaktör Gücü:\n" + base + "^" + exp + " (" + base + " üssü " + exp + ") işleminin değeri kaçtır?",
                        String.valueOf(ans), String.valueOf(base * exp), String.valueOf(ans + base), String.valueOf(ans * 2));
            } else if (template == 2) {
                int pct = (1 + random.nextInt(4)) * 20;
                int whole = (2 + random.nextInt(9)) * 25;
                int ans = (whole * pct) / 100;
                return new QuestionItem("🚀 Enerji Yüzdesi:\n" + whole + " sayısının %" + pct + "'i kaçtır?",
                        String.valueOf(ans), String.valueOf(ans + 10), String.valueOf(ans + 5), String.valueOf(Math.max(1, ans - 8)));
            } else if (template == 3) {
                int b = 6 + random.nextInt(15);
                int h = 4 + random.nextInt(12);
                if ((b * h) % 2 != 0) b++;
                int area = (b * h) / 2;
                return new QuestionItem("🚀 Taban uzunluğu " + b + " cm ve yüksekliği " + h + " cm olan üçgen kanadın alanı kaç cm²'dir?",
                        String.valueOf(area), String.valueOf(b * h), String.valueOf(b + h), String.valueOf(area + 8));
            } else if (template == 4) {
                int n1 = 8 + random.nextInt(16);
                int n2 = n1 + 4 + random.nextInt(12);
                int n3 = n2 + 4 + random.nextInt(12);
                int sum = n1 + n2 + n3;
                int rem = sum % 3;
                if (rem != 0) {
                    n3 += (3 - rem);
                    sum = n1 + n2 + n3;
                }
                int avg = sum / 3;
                return new QuestionItem("🚀 Aritmetik Ortalama:\n" + n1 + ", " + n2 + " ve " + n3 + " sayılarının aritmetik ortalaması kaçtır?",
                        String.valueOf(avg), String.valueOf(avg + 3), String.valueOf(avg - 2), String.valueOf(sum));
            } else if (template == 5) {
                int ratioA = 2 + random.nextInt(4);
                int ratioB = ratioA + 1 + random.nextInt(4);
                int scale = 4 + random.nextInt(9);
                int realA = ratioA * scale;
                int realB = ratioB * scale;
                return new QuestionItem("🚀 Oran Görevi:\nİki gezegenin uydu sayıları oranı " + ratioA + "/" + ratioB + "'dir. Küçük gezegenin " + realA + " uydusu varsa büyüğün kaç uydusu vardır?",
                        String.valueOf(realB), String.valueOf(realB + scale), String.valueOf(realA + ratioB), String.valueOf(realB - 3));
            } else if (template == 6) {
                int edge = 3 + random.nextInt(6);
                int vol = edge * edge * edge;
                return new QuestionItem("🚀 Bir ayrıtı " + edge + " m olan küp şeklindeki kargo modülünün hacmi kaç m³'tür?",
                        String.valueOf(vol), String.valueOf(edge * edge * 6), String.valueOf(edge * 12), String.valueOf(vol + 9));
            } else {
                int speed = 50 + random.nextInt(7) * 10;
                int hours = 2 + random.nextInt(5);
                int dist = speed * hours;
                return new QuestionItem("🚀 Saatte " + speed + " km sabit hızla giden keşif aracı " + hours + " saatte kaç km yol alır?",
                        String.valueOf(dist), String.valueOf(dist + 40), String.valueOf(dist - 30), String.valueOf(speed + hours));
            }
        } else {
            if (template == 0) {
                int x = 3 + random.nextInt(15);
                int k = 2 + random.nextInt(7);
                int m = 5 + random.nextInt(18);
                int rhs = k * x + m;
                return new QuestionItem("🚀 Cebirsel Denklem:\n" + k + "x + " + m + " = " + rhs + " denkleminde x kaçtır?",
                        String.valueOf(x), String.valueOf(x + 2), String.valueOf(Math.max(1, x - 1)), String.valueOf(x + 5));
            } else if (template == 1) {
                int r = 4 + random.nextInt(13);
                int add = 3 + random.nextInt(12);
                int sq = r * r;
                int ans = r + add;
                return new QuestionItem("🚀 Karekök Motoru:\n√" + sq + " + " + add + " işleminin sonucu kaçtır?",
                        String.valueOf(ans), String.valueOf(ans + 3), String.valueOf(r * add), String.valueOf(ans - 2));
            } else if (template == 2) {
                int a = -(4 + random.nextInt(14));
                int b = 6 + random.nextInt(20);
                int ans = a + b;
                return new QuestionItem("🚀 Tam Sayılarla İşlem:\n(" + a + ") + (+" + b + ") işleminin sonucu kaçtır?",
                        String.valueOf(ans), String.valueOf(-ans), String.valueOf(b - a), String.valueOf(ans + 4));
            } else if (template == 3) {
                int[][] pyth = new int[][]{{3, 4, 5}, {6, 8, 10}, {5, 12, 13}, {8, 15, 17}, {9, 12, 15}};
                int[] tri = pyth[random.nextInt(pyth.length)];
                return new QuestionItem("🚀 Dik Üçgen Rotası:\nDik kenarları " + tri[0] + " br ve " + tri[1] + " br olan dik üçgenin hipotenüsü kaç birimdir?",
                        String.valueOf(tri[2]), String.valueOf(tri[0] + tri[1]), String.valueOf(tri[2] + 2), String.valueOf(tri[2] - 1));
            } else if (template == 4) {
                int g = 3 + random.nextInt(7);
                int a = g * 2;
                int b = g * 3;
                int lcm = g * 6;
                return new QuestionItem("🚀 Ortak Katlar:\n" + a + " ve " + b + " sayılarının en küçük ortak katı (EKOK) kaçtır?",
                        String.valueOf(lcm), String.valueOf(g), String.valueOf(a * b), String.valueOf(lcm + g));
            } else if (template == 5) {
                int sides = 5 + random.nextInt(6);
                int sumAngles = (sides - 2) * 180;
                return new QuestionItem("🚀 Çokgen Geometrisi:\n" + sides + " kenarlı bir çokgenin iç açılarının ölçüleri toplamı kaç derecedir?",
                        sumAngles + "°", (sumAngles + 180) + "°", (sumAngles - 180) + "°", (sides * 180) + "°");
            } else if (template == 6) {
                int price = (4 + random.nextInt(12)) * 50;
                int discPct = (1 + random.nextInt(3)) * 10;
                int finalPrice = price - (price * discPct / 100);
                return new QuestionItem("🚀 Etiket fiyatı " + price + " TL olan teleskoba %" + discPct + " indirim uygulanırsa indirimli fiyatı kaç TL olur?",
                        String.valueOf(finalPrice), String.valueOf(price - discPct), String.valueOf(finalPrice + 25), String.valueOf(price * discPct / 100));
            } else {
                int x = 4 + random.nextInt(12);
                int k = 3 + random.nextInt(5);
                int sub = 4 + random.nextInt(10);
                int rhs = k * x - sub;
                return new QuestionItem("🚀 Koordinat Çözümü:\n" + k + "x - " + sub + " = " + rhs + " ise x kaçtır?",
                        String.valueOf(x), String.valueOf(x + 3), String.valueOf(Math.max(1, x - 2)), String.valueOf(x + 1));
            }
        }
    }

    private static QuestionItem generateTurkishQuestion(int grade, int tier, Random random) {
        if (tier == 1) {
            // Seviye 1 Isınma: En kolay temel eş ve zıt anlamlı kelimeler
            if (random.nextBoolean()) {
                int idx = random.nextInt(16);
                String word = SYNONYM_PAIRS[idx][0];
                String correct = SYNONYM_PAIRS[idx][1];
                return new QuestionItem("🏰 '" + word + "' kelimesinin eş anlamlısı hangisidir?",
                        correct, SYNONYM_PAIRS[(idx + 2) % 16][1], SYNONYM_PAIRS[(idx + 5) % 16][1], SYNONYM_PAIRS[(idx + 9) % 16][1]);
            } else {
                int idx = random.nextInt(16);
                String word = ANTONYM_PAIRS[idx][0];
                String correct = ANTONYM_PAIRS[idx][1];
                return new QuestionItem("🏰 '" + word + "' kelimesinin zıt anlamlısı hangisidir?",
                        correct, ANTONYM_PAIRS[(idx + 3) % 16][1], ANTONYM_PAIRS[(idx + 6) % 16][1], ANTONYM_PAIRS[(idx + 10) % 16][1]);
            }
        }
        int mode = random.nextInt(5);
        if (mode == 0) {
            int idx = random.nextInt(SYNONYM_PAIRS.length);
            boolean reverse = random.nextBoolean();
            String word = reverse ? SYNONYM_PAIRS[idx][1] : SYNONYM_PAIRS[idx][0];
            String correct = reverse ? SYNONYM_PAIRS[idx][0] : SYNONYM_PAIRS[idx][1];
            String w1 = SYNONYM_PAIRS[(idx + 3) % SYNONYM_PAIRS.length][1];
            String w2 = SYNONYM_PAIRS[(idx + 7) % SYNONYM_PAIRS.length][0];
            String w3 = SYNONYM_PAIRS[(idx + 11) % SYNONYM_PAIRS.length][1];
            return new QuestionItem("🏰 '" + word + "' kelimesinin eş anlamlısı (anlamdaşı) olan kale parolası hangisidir?",
                    correct, w1, w2, w3);
        } else if (mode == 1) {
            int idx = random.nextInt(ANTONYM_PAIRS.length);
            boolean reverse = random.nextBoolean();
            String word = reverse ? ANTONYM_PAIRS[idx][1] : ANTONYM_PAIRS[idx][0];
            String correct = reverse ? ANTONYM_PAIRS[idx][0] : ANTONYM_PAIRS[idx][1];
            String w1 = ANTONYM_PAIRS[(idx + 4) % ANTONYM_PAIRS.length][1];
            String w2 = ANTONYM_PAIRS[(idx + 9) % ANTONYM_PAIRS.length][0];
            String w3 = ANTONYM_PAIRS[(idx + 13) % ANTONYM_PAIRS.length][1];
            return new QuestionItem("🏰 '" + word + "' kelimesinin zıt (karşıt) anlamlısı olan anahtar hangisidir?",
                    correct, w1, w2, w3);
        } else if (mode == 2) {
            int idx = random.nextInt(PROVERBS_AND_IDIOMS.length);
            String[] p = PROVERBS_AND_IDIOMS[idx];
            String w1 = PROVERBS_AND_IDIOMS[(idx + 2) % PROVERBS_AND_IDIOMS.length][1];
            String w2 = PROVERBS_AND_IDIOMS[(idx + 5) % PROVERBS_AND_IDIOMS.length][1];
            String w3 = PROVERBS_AND_IDIOMS[(idx + 9) % PROVERBS_AND_IDIOMS.length][1];
            return new QuestionItem("🏰 '" + p[0] + "' sözünün anlamı aşağıdakilerden hangisidir?",
                    p[1], w1, w2, w3);
        } else if (mode == 3) {
            int idx = random.nextInt(PROVERBS_AND_IDIOMS.length);
            String[] p = PROVERBS_AND_IDIOMS[idx];
            String masked = p[0].replace(p[2], "____");
            return new QuestionItem("🏰 Boşluğu Tamamla:\n'" + masked + "' sözünde boş bırakılan yere hangi kelime gelmelidir?",
                    p[2], p[3], p[4], p[5]);
        } else {
            String[][] grammarBank = new String[][]{
                    {"🏰 'Cumhuriyetimiz' kelimesinde toplam kaç hece vardır?", "5 hece", "4 hece", "6 hece", "3 hece"},
                    {"🏰 'Bilgisayarlarımızdan' kelimesinde kaç ünlü (sesli) harf ve hece vardır?", "7 hece", "6 hece", "8 hece", "5 hece"},
                    {"🏰 'Kitap - Kitabı' örneğinde görülen ses olayı (p ➔ b) aşağıdakilerden hangisidir?", "Ünsüz yumuşaması", "Ünlü düşmesi", "Ünsüz benzeşmesi", "Ulama"},
                    {"🏰 'Ağız - Ağzı' ve 'Burun - Burnu' kelimelerinde görülen ses olayı nedir?", "Ünlü düşmesi (Hece düşmesi)", "Ünlü daralması", "Ünsüz türemesi", "Kaynaştırma"},
                    {"🏰 'Eyvah, anahtarı içeride unuttum!' cümlesinin sonuna hangi noktalama işareti konur?", "Ünlem işareti (!)", "Soru işareti (?)", "Noktalı virgül (;)", "İki nokta (:)"},
                    {"🏰 'Keskin bakışlarıyla etrafı süzdü.' cümlesinde 'keskin' kelimesi hangi anlamda kullanılmıştır?", "Mecaz anlam", "Gerçek anlam", "Terim anlam", "Zıt anlam"},
                    {"🏰 'Bugün tiyatroda iki perdelik bir oyun izledik.' cümlesinde 'perde' kelimesi hangi anlamdadır?", "Terim anlam", "Mecaz anlam", "Eş sesli", "Deyim"},
                    {"🏰 'Yüz' kelimesi hem sayı hem de çehre/surat anlamına geldiği için nasıl bir kelimedir?", "Eş sesli (Sesteş) kelime", "Eş anlamlı kelime", "Zıt anlamlı kelime", "Soyut kelime"},
                    {"🏰 'Gözlükçü' kelimesinin kökü aşağıdakilerden hangisidir?", "Göz", "Gözlük", "Gözcü", "Görmek"},
                    {"🏰 'Simitçi' kelimesinde 'simit-ci ➔ simitçi' dönüşümü hangi ses olayıdır?", "Ünsüz benzeşmesi (sertleşmesi)", "Ünsüz yumuşaması", "Ünlü düşmesi", "Ünlü türemesi"},
                    {"🏰 'Hafta sonu kütüphaneye gidecek misin' cümlesinin sonuna hangi işaret gelmelidir?", "Soru işareti (?)", "Nokta (.)", "Ünlem (!)", "Üç nokta (...)"},
                    {"🏰 Aşağıdaki kelimelerden hangisi büyük ünlü uyumuna uyar?", "Kelebek", "Elma", "Kalem", "Kardeş"}
            };
            String[] g = grammarBank[random.nextInt(grammarBank.length)];
            return new QuestionItem(g[0], g[1], g[2], g[3], g[4]);
        }
    }

    private static QuestionItem generateScienceQuestion(int grade, int tier, Random random) {
        if (tier == 1) {
            String[] f = SCIENCE_FACTS[random.nextInt(8)];
            return new QuestionItem("" + f[0], f[1], f[2], f[3], f[4]);
        }
        int mode = random.nextInt(4);
        if (mode == 0 && tier >= 3) {
            int speed = (2 + random.nextInt(9)) * 10;
            int time = 2 + random.nextInt(7);
            int dist = speed * time;
            if (random.nextBoolean()) {
                return new QuestionItem("🧪 Hareket Laboratuvarı:\nSabit " + speed + " m/s süratle hareket eden bir deney aracı " + time + " saniyede kaç metre yol alır?",
                        dist + " metre", (dist + 30) + " metre", (speed + time) + " metre", (dist - 20) + " metre");
            } else {
                return new QuestionItem("🧪 Sürat Hesabı:\n" + dist + " metrelik pisti " + time + " saniyede tamamlayan robotun sürati kaç m/s'dir?",
                        speed + " m/s", (speed + 10) + " m/s", (speed - 5) + " m/s", (dist - time) + " m/s");
            }
        } else if (mode == 1 && tier >= 3) {
            if (grade >= 5 && random.nextBoolean()) {
                int d = 2 + random.nextInt(7);
                int vol = (2 + random.nextInt(8)) * 10;
                int mass = d * vol;
                return new QuestionItem("🧪 Madde Laboratuvarı:\nKütlesi " + mass + " gram, hacmi " + vol + " cm³ olan saf bir metalin yoğunluğu (özkütlesi) kaç g/cm³'tür?",
                        d + " g/cm³", (d + 2) + " g/cm³", (mass - vol) + " g/cm³", (d * 2) + " g/cm³");
            } else {
                int perUnitN = 3 + random.nextInt(6);
                int divs = 2 + random.nextInt(7);
                int force = perUnitN * divs;
                return new QuestionItem("🧪 Kuvvet Deneyi:\nHer 1 bölmesi " + perUnitN + " Newton (N) gösteren bir dinamometrede yay " + divs + " bölme uzadığına göre uygulanan kuvvet kaç N'dur?",
                        force + " N", (force + perUnitN) + " N", (perUnitN + divs) + " N", Math.max(1, force - 4) + " N");
            }
        } else if (mode == 2 && grade >= 5 && tier >= 3) {
            int idx = random.nextInt(SCIENCE_ELEMENTS.length);
            boolean askSymbol = random.nextBoolean();
            String name = SCIENCE_ELEMENTS[idx][0];
            String sym = SCIENCE_ELEMENTS[idx][1];
            if (askSymbol) {
                return new QuestionItem("🧪 Periyodik Tablo Görevi:\n'" + name + "' elementinin sembolü aşağıdakilerden hangisidir?",
                        sym,
                        SCIENCE_ELEMENTS[(idx + 2) % SCIENCE_ELEMENTS.length][1],
                        SCIENCE_ELEMENTS[(idx + 5) % SCIENCE_ELEMENTS.length][1],
                        SCIENCE_ELEMENTS[(idx + 9) % SCIENCE_ELEMENTS.length][1]);
            } else {
                return new QuestionItem("🧪 Periyodik Tablo Görevi:\nSembolü '" + sym + "' olan element aşağıdakilerden hangisidir?",
                        name,
                        SCIENCE_ELEMENTS[(idx + 3) % SCIENCE_ELEMENTS.length][0],
                        SCIENCE_ELEMENTS[(idx + 6) % SCIENCE_ELEMENTS.length][0],
                        SCIENCE_ELEMENTS[(idx + 10) % SCIENCE_ELEMENTS.length][0]);
            }
        } else {
            String[] f = SCIENCE_FACTS[random.nextInt(SCIENCE_FACTS.length)];
            return new QuestionItem("🧪 " + f[0], f[1], f[2], f[3], f[4]);
        }
    }

    private static QuestionItem generateSocialStudiesQuestion(int grade, int tier, Random random) {
        if (tier == 1) {
            String[] g = GEOGRAPHY_HISTORY_FACTS[random.nextInt(6)];
            return new QuestionItem("" + g[0], g[1], g[2], g[3], g[4]);
        }
        int mode = random.nextInt(4);
        if (mode == 0 && tier >= 3) {
            int[] years = new int[]{1071, 1299, 1453, 1517, 1789, 1876, 1919, 1923, 1938, 2024};
            int y = years[random.nextInt(years.length)];
            int century = (y / 100) + 1;
            return new QuestionItem("🧭 Zaman Çizelgesi Görevi:\nTarihteki " + y + " yılı kaçıncı yüzyıl içerisinde yer alır?",
                    century + ". Yüzyıl", (century - 1) + ". Yüzyıl", (century + 1) + ". Yüzyıl", (century - 2) + ". Yüzyıl");
        } else if (mode == 1 && tier >= 3) {
            if (random.nextBoolean()) {
                int m1 = 26 + random.nextInt(8);
                int m2 = m1 + 5 + random.nextInt(12);
                int diffMin = (m2 - m1) * 4;
                return new QuestionItem("🧭 Coğrafya Koordinatı:\n" + m1 + "° Doğu meridyeni ile " + m2 + "° Doğu meridyeni arasındaki yerel saat farkı kaç dakikadır?",
                        diffMin + " dakika", (diffMin + 8) + " dakika", (m2 - m1) + " dakika", (diffMin - 4) + " dakika");
            } else {
                int cm = 3 + random.nextInt(8);
                int scaleKmPerCm = (1 + random.nextInt(5)) * 10;
                int realKm = cm * scaleKmPerCm;
                return new QuestionItem("🧭 Harita Ölçek Görevi:\n1 cm'nin gerçekte " + scaleKmPerCm + " km'yi gösterdiği bir haritada " + cm + " cm olarak ölçülen iki şehir arası gerçekte kaç km'dir?",
                        realKm + " km", (realKm + scaleKmPerCm) + " km", (cm + scaleKmPerCm) + " km", Math.max(10, realKm - 15) + " km");
            }
        } else {
            String[] g = GEOGRAPHY_HISTORY_FACTS[random.nextInt(GEOGRAPHY_HISTORY_FACTS.length)];
            return new QuestionItem("🧭 " + g[0], g[1], g[2], g[3], g[4]);
        }
    }

    private static QuestionItem generateEnglishQuestion(int grade, int tier, Random random) {
        if (tier == 1) {
            int idx = random.nextInt(15);
            String en = ENGLISH_VOCAB[idx][0];
            String tr = ENGLISH_VOCAB[idx][1];
            return new QuestionItem("🌍 İngilizce '" + en + "' kelimesinin Türkçe anlamı nedir?",
                    tr,
                    ENGLISH_VOCAB[(idx + 2) % 15][1],
                    ENGLISH_VOCAB[(idx + 5) % 15][1],
                    ENGLISH_VOCAB[(idx + 9) % 15][1]);
        }
        int mode = random.nextInt(4);
        if (mode == 0) {
            int idx = random.nextInt(ENGLISH_VOCAB.length);
            boolean enToTr = random.nextBoolean();
            String en = ENGLISH_VOCAB[idx][0];
            String tr = ENGLISH_VOCAB[idx][1];
            if (enToTr) {
                return new QuestionItem("🌍 İngilizce '" + en + "' kelimesinin Türkçe karşılığı nedir?",
                        tr,
                        ENGLISH_VOCAB[(idx + 4) % ENGLISH_VOCAB.length][1],
                        ENGLISH_VOCAB[(idx + 9) % ENGLISH_VOCAB.length][1],
                        ENGLISH_VOCAB[(idx + 15) % ENGLISH_VOCAB.length][1]);
            } else {
                return new QuestionItem("🌍 Türkçe '" + tr + "' kelimesinin İngilizce karşılığı hangisidir?",
                        en,
                        ENGLISH_VOCAB[(idx + 5) % ENGLISH_VOCAB.length][0],
                        ENGLISH_VOCAB[(idx + 11) % ENGLISH_VOCAB.length][0],
                        ENGLISH_VOCAB[(idx + 17) % ENGLISH_VOCAB.length][0]);
            }
        } else if (mode == 1) {
            int idx = random.nextInt(ENGLISH_OPPOSITES.length);
            boolean rev = random.nextBoolean();
            String w = rev ? ENGLISH_OPPOSITES[idx][1] : ENGLISH_OPPOSITES[idx][0];
            String ans = rev ? ENGLISH_OPPOSITES[idx][0] : ENGLISH_OPPOSITES[idx][1];
            return new QuestionItem("🌍 What is the opposite (zıt anlamlısı) of '" + w + "'?",
                    ans,
                    ENGLISH_OPPOSITES[(idx + 3) % ENGLISH_OPPOSITES.length][1],
                    ENGLISH_OPPOSITES[(idx + 6) % ENGLISH_OPPOSITES.length][0],
                    ENGLISH_OPPOSITES[(idx + 9) % ENGLISH_OPPOSITES.length][1]);
        } else if (mode == 2 && tier >= 3) {
            String[] tensNames = new String[]{"Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"};
            String[] onesNames = new String[]{"", "-one", "-two", "-three", "-four", "-five", "-six", "-seven", "-eight", "-nine"};
            int tIdx = random.nextInt(tensNames.length);
            int oIdx = 1 + random.nextInt(9);
            int numberVal = (tIdx + 2) * 10 + oIdx;
            String engWord = tensNames[tIdx] + onesNames[oIdx];
            return new QuestionItem("🌍 İngilizce '" + engWord + "' ifadesi hangi sayıyı gösterir?",
                    String.valueOf(numberVal),
                    String.valueOf(oIdx * 10 + (tIdx + 2)),
                    String.valueOf(numberVal + 10),
                    String.valueOf(numberVal - 1));
        } else {
            String[][] sentences = new String[][]{
                    {"🌍 'I ___ a student and she ___ a teacher.' boşluklara sırasıyla ne gelmelidir?", "am / is", "is / are", "are / am", "am / are"},
                    {"🌍 'Birds ___ fly, but fish ___ walk.' boşluklara ne gelmelidir?", "can / can't", "can't / can", "are / is", "have / has"},
                    {"🌍 'What time is it? (08:30)' sorusunun doğru cevabı hangisidir?", "It is half past eight", "It is quarter past eight", "It is eight o'clock", "It is half past nine"},
                    {"🌍 'An elephant is ___ than a mouse.' cümlesinde boşluğa hangisi gelir?", "bigger", "smaller", "shorter", "slower"},
                    {"🌍 'Where do you borrow books?' sorusunun cevabı hangi mekândır?", "At the library", "At the bakery", "At the hospital", "At the cinema"},
                    {"🌍 'My mother's brother is my ___.' cümlesini hangi kelime tamamlar?", "Uncle", "Aunt", "Cousin", "Grandfather"},
                    {"🌍 'It is raining outside! Take your ___.' cümlesinde boşluğa ne gelir?", "umbrella", "sunglasses", "bicycle", "notebook"},
                    {"🌍 'How many days are there in a week?' sorusunun cevabı nedir?", "Seven (7)", "Five (5)", "Twelve (12)", "Thirty (30)"}
            };
            String[] s = sentences[random.nextInt(sentences.length)];
            return new QuestionItem(s[0], s[1], s[2], s[3], s[4]);
        }
    }

    private static QuestionItem generateValuesQuestion(int grade, int tier, Random random) {
        if (tier >= 3 && random.nextInt(4) == 0) {
            int savedPerDay = 5 + random.nextInt(16);
            int days = 4 + random.nextInt(7);
            int total = savedPerDay * days;
            return new QuestionItem("🕊️ Paylaşım Kumbarası:\nHer gün kumbarasına " + savedPerDay + " TL koyan bir öğrenci " + days + " gün sonunda ihtiyaç sahipleriyle paylaşmak için kaç TL biriktirmiş olur?",
                    total + " TL", (total + 10) + " TL", (savedPerDay + days) + " TL", (total - 5) + " TL");
        }
        String[][] bank = new String[][]{
                {"🕊️ Bir arkadaşımız bize yardım ettiğinde hangi nezaket sözünü söyleriz?", "Teşekkür ederim", "Acele et", "Bana ne", "Sonra görüşürüz"},
                {"🕊️ Kendimize yapılmasını istemediğimiz bir davranışı başkasına yapmamaya ne denir?", "Empati (Diğerkâmlık)", "Bencillik", "Acelecilik", "Unutkanlık"},
                {"🕊️ Verdiğimiz sözü zamanında yerine getirmek hangi erdemin göstergesidir?", "Güvenilirlik ve dürüstlük", "Kararsızlık", "Acelecilik", "Kıskançlık"},
                {"🕊️ İstemeden bir arkadaşımızı üzdüğümüzde ilk yapmamız gereken güzel davranış nedir?", "İçtenlikle özür dilemek", "Konuyu değiştirmek", "Oradan uzaklaşmak", "Başkasını suçlamak"},
                {"🕊️ Yaşlılara, hamilelere ve engellilere toplu taşımada yer vermek hangi değerimizdir?", "Saygı ve duyarlılık", "Gösteriş", "Zorunluluk", "Yarışma"},
                {"🕊️ Başkalarının sırrını korumak ve emanete sahip çıkmak neyin gereğidir?", "Emanet bilinci ve güven", "Meraklılık", "Unutkanlık", "Sabırsızlık"},
                {"🕊️ Bir hedefe ulaşırken zorluklar karşısında pes etmeden gayret göstermeye ne denir?", "Sabır ve azim", "İnatçılık", "Tembellik", "Acelecilik"},
                {"🕊️ Misafire ikramda bulunmak ve güler yüz göstermek kültürümüzde ne olarak adlandırılır?", "Misafirperverlik", "Savurganlık", "Meraklılık", "Alışveriş"},
                {"🕊️ Kütüphane, sınıf ve park gibi ortak alanları temiz bırakmak hangi erdemdir?", "Sorumluluk ve kul hakkına saygı", "Korku", "Yalnızlık", "Yarışma"},
                {"🕊️ Farklı görüş ve düşüncelere saygıyla yaklaşabilme erdemine ne ad verilir?", "Hoşgörü (Tolerans)", "Önyargı", "Bencillik", "İnatçılık"},
                {"🕊️ Yemekten önce ve sonra sahip olduğumuz nimetlerin kıymetini bilmeye ne denir?", "Şükür ve kanaat", "İsraf", "Acele", "Gösteriş"},
                {"🕊️ Ekmeği, suyu ve elektriği gereksiz yere harcamamaya ne ad verilir?", "Tasarruf (İsraftan kaçınmak)", "Cimrilik", "Yorgunluk", "Zenginlik"}
        };
        int bound = (tier == 1) ? 4 : bank.length;
        String[] pick = bank[random.nextInt(bound)];
        return new QuestionItem(pick[0], pick[1], pick[2], pick[3], pick[4]);
    }

    private static QuestionItem generateSafetyQuestion(int grade, int tier, Random random) {
        if (tier >= 3 && random.nextInt(3) == 0) {
            int speed = (4 + random.nextInt(8)) * 10;
            int safeDist = speed / 2;
            return new QuestionItem("🏎️ Güvenli Takip Mesafesi Kuralı:\nSaatte " + speed + " km hızla giden bir aracın öndeki araçla bırakması gereken güvenli takip mesafesi en az kaç metredir? (Hızın yarısı)",
                    safeDist + " metre", speed + " metre", (safeDist + 15) + " metre", (safeDist / 2) + " metre");
        }
        String[][] bank = new String[][]{
                {"🏎️ Yaya geçidinde yayalar için hangi ışık yandığında güvenle karşıya geçilir?", "Yeşil ışık", "Kırmızı ışık", "Sarı ışık", "Mavi ışık"},
                {"🏎️ Otomobile bindiğimizde güvenliğimiz için ilk yapmamız gereken nedir?", "Emniyet kemerini takmak", "Camı açmak", "Radyoyu açmak", "Ayakta durmak"},
                {"🏎️ Bisiklet, paten ve kaykay kullanırken başımızı korumak için ne takmalıyız?", "Kask", "Şapka", "Kulaklık", "Atkı"},
                {"🏎️ Türkiye'de ambulans, itfaiye, polis ve AFAD için aranan tek ortak acil numara kaçtır?", "112", "155", "182", "114"},
                {"🏎️ Üçgen şeklindeki kırmızı çerçeveli trafik levhaları sürücü ve yayalara ne bildirir?", "Tehlike uyarı işaretidir, dikkatli olunmalıdır", "Park yeri olduğunu", "Otoyol bittiğini", "Hastane olduğunu"},
                {"🏎️ Mavi zeminli kare veya dikdörtgen trafik levhaları ne tür işaretlerdir?", "Bilgi işaretleri", "Yasaklama işaretleri", "Tehlike işaretleri", "Durma işaretleri"},
                {"🏎️ Deprem sırasında bina içindeysek sağlam bir eşyanın yanında hangi hareketi yapmalıyız?", "Çök - Kapan - Tutun", "Asansöre binmek", "Balkondan atlamak", "Merdivenlere koşmak"},
                {"🏎️ Okul servisinden indiğimizde hemen servisin önünden veya arkasından koşarak geçmeli miyiz?", "Hayır, servis uzaklaşana kadar kaldırımda beklemeliyiz", "Evet, hemen geçmeliyiz", "Fark etmez", "Gözümüz kapalı geçmeliyiz"},
                {"🏎️ Yaya kaldırımı bulunmayan yollarda yürürken yolun hangi tarafından yürümeliyiz?", "Karşıdan gelen araçları görebilmek için sol kenardan", "Yolun tam ortasından", "Sağ şeridin ortasından", "Koşarak"},
                {"🏎️ Küçük bir yanık oluştuğunda ilk yardım olarak yanık bölgeye ilk ne uygulanmalıdır?", "10-15 dakika soğuk/normal musluk suyu tutulmalıdır", "Diş macunu sürülmelidir", "Yoğurt sürülmelidir", "Tuz basılmalıdır"},
                {"🏎️ Burun kanaması durumunda doğru ilk yardım uygulaması nasıldır?", "Baş hafifçe öne eğilir ve burun kanatları 5 dk sıkılır", "Baş geriye doğru yatırılır", "Sıcak su içilir", "Koşulur"},
                {"🏎️ Gece yürüyüşlerinde veya bisiklet sürerken sürücülerin bizi fark etmesi için ne kullanmalıyız?", "Reflektörlü (ışık yansıtıcı) giysi veya yelek", "Koyu siyah kıyafet", "Şemsiye", "Güneş gözlüğü"}
        };
        int bound = (tier == 1) ? 4 : bank.length;
        String[] pick = bank[random.nextInt(bound)];
        return new QuestionItem(pick[0], pick[1], pick[2], pick[3], pick[4]);
    }

    private static QuestionItem generateCivicsQuestion(int grade, int tier, Random random) {
        if (tier >= 3 && random.nextInt(3) == 0) {
            int tons = 2 + random.nextInt(8);
            int trees = tons * 17;
            return new QuestionItem("⚖️ Doğa ve Geri Dönüşüm Görevi:\n1 ton atık kağıdın geri dönüştürülmesi 17 ağacı kesilmekten kurtarmaktadır. Okulumuzda " + tons + " ton kağıt geri dönüştürülürse kaç ağaç kurtulur?",
                    trees + " ağaç", (trees + 10) + " ağaç", (tons + 17) + " ağaç", (trees - 7) + " ağaç");
        }
        String[][] bank = new String[][]{
                {"⚖️ Birleşmiş Milletler sözleşmesine göre 18 yaşına kadar her bireyin eğitim, sağlık ve oyun hakkına ne denir?", "Çocuk Hakları", "Yarışma Kuralları", "Nöbet Çizelgesi", "Kulüp Tüzüğü"},
                {"⚖️ Doğayı, ormanları ve su kaynaklarını korumak kimin sorumluluğudur?", "Tüm insanların ortak vatandaşlık sorumluluğudur", "Sadece belediyenin", "Sadece yetişkinlerin", "Hiç kimsenin"},
                {"⚖️ Grup çalışmalarında başarıya ulaşmanın en güzel yolu aşağıdakilerden hangisidir?", "Görev paylaşımı ve iş birliği yapmak", "Her işi tek bir kişiye yüklemek", "Başkalarını dinlememek", "Çalışmayı yarıda bırakmak"},
                {"⚖️ Bir anlaşmazlık yaşadığımızda sorunu çözmenin en adil ve barışçıl yolu nedir?", "İletişim kurarak ve uzlaşarak çözmek", "Bağırarak kabul ettirmek", "Küserek konuşmamak", "Eşyaları fırlatmak"},
                {"⚖️ Bir kişinin özgürlüğünün sınırı nerede biter?", "Başkasının hak ve özgürlüğünün başladığı yerde", "Hiçbir yerde bitmez", "Sadece okulda biter", "Akşam saatlerinde"},
                {"⚖️ Sınıf başkanlığı seçiminde her öğrencinin eşit oy hakkına sahip olması hangi kavramla ilgilidir?", "Demokrasi ve eşitlik", "Ayrıcalık", "Zorlama", "Rastlantı"},
                {"⚖️ İnternet ortamında şifrelerimizi ve kişisel bilgilerimizi yabancılarla paylaşmamak neyin gereğidir?", "Dijital güvenlik ve mahremiyet", "Oyun kuralı", "İnternet hızı", "Bilgisayar bakımı"},
                {"⚖️ İnsanların sırf dış görünüşü veya kökeni yüzünden peşin hükümle değerlendirilmesine ne denir?", "Önyargı (Kalıp yargı)", "Empati", "Hoşgörü", "Dayanışma"},
                {"⚖️ Haberleşme ve özel hayatın gizliliği hakkı ne ile güvence altına alınmıştır?", "Anayasa ve yasalarla", "Oyun kurallarıyla", "Komşu kararıyla", "Hava durumuyla"},
                {"⚖️ Alışveriş yaptıktan sonra fiş veya fatura almak hangi açıdan önemlidir?", "Tüketici haklarımızı korumak ve vergi bilinci için", "Çantayı doldurmak için", "Kağıt biriktirmek için", "Hiçbir önemi yoktur"}
        };
        int bound = (tier == 1) ? 4 : bank.length;
        String[] pick = bank[random.nextInt(bound)];
        return new QuestionItem(pick[0], pick[1], pick[2], pick[3], pick[4]);
    }
}
