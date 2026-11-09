package com.calismazamanlayici.veli;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
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
import java.util.List;
import java.util.Random;

/**
 * Öğrenci uygulamasındaki Eğlence (Oyun) Merkezi:
 * - İlerlemeli ve zıplamalı "🏃‍♂️ Süper Yıldız Zıplama Macerası" oyununu ve
 *   her dersin kazanımlarına uygun (oyun adında ders ismi geçmeyen) zenginleştirilmiş sorulu 8 oyunu içerir.
 * - Her oyunun kendine özgü farklı arka plan müziği vardır.
 * - Oynanan oyunlar çalışma sayılmaz, eğlence geçmişi olarak Takvime eklenir.
 */
public class VeliGameTestActivity extends Activity {

    private ScrollView scrollRootEntertainment;
    private LinearLayout layoutEntHeaderCard;
    private TextView tvEntHeaderTitle;
    private TextView tvEntTimerStatus;
    private Button btnToggleGameMusic;

    private LinearLayout layoutGameLobbyContainer;
    private LinearLayout layoutGameCardsList;
    private LinearLayout layoutPlatformerJumpArena;
    private LinearLayout layoutActiveGameArena;

    private TextView tvJumpGameStatus;
    private FrameLayout frameJumpGameCanvas;
    private Button btnJumpAction;
    private VeliPlatformerJumpView jumpGameView;

    private TextView tvActiveGameTitle;
    private TextView tvGameStatsBar;
    private TextView tvGameQuestionPrompt;
    private TextView tvGameFeedbackBanner;
    private LinearLayout layoutGameOptionButtons;
    private FrameLayout frameEffectOverlay;
    private StarBurstEffectView starEffectView;

    private int testGradeLevel = 5;
    private Spinner spinnerVeliTestGrade;
    private final VeliGameMusicSynthesizer musicSynth = new VeliGameMusicSynthesizer();
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    private int totalEntElapsedSeconds = 0;
    private boolean elevenMinWarningShown = false;

    private boolean inActiveGame = false;
    private int activeGameIndex = 8;
    private String activeGameTitle = "";
    private int activeGameSessionSeconds = 0;
    private int currentScore = 0;
    private int currentStreak = 0;
    private int currentLives = 3;

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

    private static class StarBurstEffectView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float[] px = new float[24];
        private final float[] py = new float[24];
        private final float[] vx = new float[24];
        private final float[] vy = new float[24];
        private final int[] colors = new int[]{
                0xFFFFD54F, 0xFFFF4081, 0xFF00E5FF, 0xFF69F0AE, 0xFFFFAB40, 0xFFEA80FC
        };
        private int animFrame = 0;
        private boolean animating = false;
        private String badgeText = "🌟 SÜPER! 🌟";

        public StarBurstEffectView(Context context) {
            super(context);
        }

        public void triggerBurst(String text) {
            this.badgeText = text;
            this.animFrame = 0;
            this.animating = true;
            float cx = Math.max(getWidth() / 2f, 150f);
            float cy = Math.max(getHeight() / 2f, 40f);
            Random r = new Random();
            for (int i = 0; i < px.length; i++) {
                px[i] = cx;
                py[i] = cy;
                double angle = (Math.PI * 2 * i) / px.length + r.nextDouble() * 0.3;
                float speed = 4f + r.nextFloat() * 9f;
                vx[i] = (float) Math.cos(angle) * speed;
                vy[i] = (float) Math.sin(angle) * speed;
            }
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (!animating) return;

            for (int i = 0; i < px.length; i++) {
                px[i] += vx[i];
                py[i] += vy[i];
                paint.setColor(colors[i % colors.length]);
                float radius = Math.max(3f, 12f - animFrame * 0.4f);
                canvas.drawCircle(px[i], py[i], radius, paint);
            }

            paint.setColor(0xFFFF8F00);
            paint.setTextSize(36f);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setFakeBoldText(true);
            canvas.drawText(badgeText, getWidth() / 2f, getHeight() / 2f + 12f, paint);

            animFrame++;
            if (animFrame < 22) {
                postInvalidateDelayed(35L);
            } else {
                animating = false;
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
        layoutEntHeaderCard = findViewById(R.id.layoutEntHeaderCard);
        tvEntHeaderTitle = findViewById(R.id.tvEntHeaderTitle);
        tvEntTimerStatus = findViewById(R.id.tvEntTimerStatus);
        btnToggleGameMusic = findViewById(R.id.btnToggleGameMusic);

        layoutGameLobbyContainer = findViewById(R.id.layoutGameLobbyContainer);
        layoutGameCardsList = findViewById(R.id.layoutGameCardsList);
        layoutPlatformerJumpArena = findViewById(R.id.layoutPlatformerJumpArena);
        layoutActiveGameArena = findViewById(R.id.layoutActiveGameArena);

        tvJumpGameStatus = findViewById(R.id.tvJumpGameStatus);
        frameJumpGameCanvas = findViewById(R.id.frameJumpGameCanvas);
        btnJumpAction = findViewById(R.id.btnJumpAction);
        Button btnBackFromJumpGame = findViewById(R.id.btnBackFromJumpGame);

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

        jumpGameView = new VeliPlatformerJumpView(this, new VeliPlatformerJumpView.JumpGameListener() {
            @Override
            public void onStatsUpdated(int score, int stage, String stageName, int lives) {
                StringBuilder hearts = new StringBuilder();
                for (int i = 0; i < lives; i++) hearts.append("❤️");
                tvJumpGameStatus.setText("🏃‍♂️ Aşama " + stage + " (" + stageName + ") • ⭐ " + score + " • " + hearts);
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
        });
        frameJumpGameCanvas.addView(jumpGameView);

        VeliThemeHelper.Palette p = VeliThemeHelper.getPalette(this);
        VeliThemeHelper.applyWindowAndSurface(this, scrollRootEntertainment);
        if (layoutEntHeaderCard != null) {
            layoutEntHeaderCard.setBackground(VeliThemeHelper.createHeaderDrawable(this));
        }
        btnJumpAction.setBackground(VeliVeliThemeHelper.createButtonDrawable(this, p.primary));

        tvEntHeaderTitle.setText("🛠️ Veli Oyun Test Merkezi (" + testGradeLevel + ". Sınıf)");

        spinnerVeliTestGrade = findViewById(R.id.spinnerVeliTestGrade);
        if (spinnerVeliTestGrade != null) {
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
        }

        btnToggleGameMusic.setOnClickListener(v -> {
            boolean muted = musicSynth.toggleMute();
            btnToggleGameMusic.setText(muted ? "🔇 Müzik: Kapalı" : "🎵 Müzik: Açık");
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
        // 1. İlerlemeli & Zıplamalı Yeni Macera Oyunu!
        games.add(new GameDefinition(8, "🏃‍♂️ Süper Yıldız Zıplama Macerası",
                "İlerlemeli ve çift zıplamalı parkurda engelleri aş, yıldızları ve elmasları toplayarak aşamaları geç!",
                0xFFFF5722, true));

        // 2. Ders kazanımlarına uyumlu (adında ders ismi geçmeyen) 8 farklı oyun
        games.add(new GameDefinition(0, "🚀 Galaksi Sayı Avcısı",
                grade + ". Sınıf uzay gemisiyle yıldız hedeflerini hesapla ve meteorları aş!", 0xFF1E88E5, false));
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
            btnPlay.setText(g.isPlatformerJump ? "🏃‍♂️ Zıplama Macerasını Başlat" : "▶️ Maceraya Başla");
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
        layoutPlatformerJumpArena.setVisibility(View.GONE);
        layoutActiveGameArena.setVisibility(View.GONE);
        layoutGameLobbyContainer.setVisibility(View.VISIBLE);
    }

    private void startSelectedGame(GameDefinition game) {
        saveActiveGameSessionToCalendarIfNeeded();
        inActiveGame = true;
        activeGameIndex = game.index;
        activeGameTitle = game.gameTitle;
        activeGameSessionSeconds = 0;
        currentScore = 0;
        currentStreak = 0;
        currentLives = 3;

        musicSynth.setMelodyIndex(game.index);
        layoutGameLobbyContainer.setVisibility(View.GONE);

        if (game.isPlatformerJump) {
            layoutActiveGameArena.setVisibility(View.GONE);
            layoutPlatformerJumpArena.setVisibility(View.VISIBLE);
            jumpGameView.resetGame();
            jumpGameView.startGame();
        } else {
            layoutPlatformerJumpArena.setVisibility(View.GONE);
            layoutActiveGameArena.setVisibility(View.VISIBLE);
            tvActiveGameTitle.setText(game.gameTitle);
            tvGameFeedbackBanner.setText("🎵 Bu oyuna özel arka plan müziği aktif! Doğru cevaba dokun!");
            updateStatsBar();
            loadNextQuestion();
        }
    }

    private void updateStatsBar() {
        StringBuilder hearts = new StringBuilder();
        for (int i = 0; i < currentLives; i++) hearts.append("❤️");
        if (currentLives <= 0) hearts.append("🔄");
        tvGameStatsBar.setText("⭐ Puan: " + currentScore + "  •  🔥 Seri: " + currentStreak + "x  •  " + hearts);
    }

    private void loadNextQuestion() {
        int grade = testGradeLevel;
        final QuestionItem q = generateGradeScaledQuestion(activeGameIndex % 8, grade, random);

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
        if (chosen.equals(correct)) {
            currentStreak++;
            int gained = 10 + (currentStreak * 5);
            currentScore += gained;
            musicSynth.playSuccessEffect();
            starEffectView.triggerBurst("🌟 +" + gained + " PUAN! (" + currentStreak + "x SERİ!) 🌟");
            tvGameFeedbackBanner.setText("✅ Harika! Doğru cevap: " + correct);
        } else {
            currentStreak = 0;
            currentLives--;
            musicSynth.playWrongEffect();
            tvGameFeedbackBanner.setText("💡 Dikkat! Doğru cevap '" + correct + "' olacaktı. Devam et!");
            if (currentLives <= 0) {
                currentLives = 3;
            }
        }
        updateStatsBar();
        timerHandler.postDelayed(this::loadNextQuestion, 650L);
    }

    /**
     * Zenginleştirilmiş Soru Havuzu (1. Sınıftan 8. Sınıfa kadar uyarlamalı dinamik sorular).
     */
    public static QuestionItem generateGradeScaledQuestion(int gameCategory, int grade, Random random) {
        if (gameCategory == 0) {
            // Galaksi Sayı Avcısı (Dinamik Çok Çeşitli Sayısal Görevler)
            int mode = random.nextInt(3);
            if (grade <= 2) {
                if (mode == 0) {
                    int a = 6 + random.nextInt(18 * grade);
                    int b = 4 + random.nextInt(14 * grade);
                    int ans = a + b;
                    return new QuestionItem("🚀 Uzay İstasyonu Toplamı:\n" + a + " + " + b + " kaçtır?",
                            String.valueOf(ans), String.valueOf(ans + 2), String.valueOf(Math.max(1, ans - 3)), String.valueOf(ans + 5));
                } else if (mode == 1) {
                    int a = 15 + random.nextInt(20 * grade);
                    int b = 4 + random.nextInt(12);
                    int ans = a - b;
                    return new QuestionItem("🚀 Kalkan Enerji Farkı:\n" + a + " - " + b + " kaçtır?",
                            String.valueOf(ans), String.valueOf(ans + 3), String.valueOf(Math.max(0, ans - 2)), String.valueOf(ans + 6));
                } else {
                    int step = 2 + random.nextInt(4);
                    int start = step * (1 + random.nextInt(4));
                    int ans = start + step * 3;
                    return new QuestionItem("🚀 Yıldız Örüntüsünde Sıradaki Sayı:\n" + start + " - " + (start + step) + " - " + (start + step * 2) + " - ?",
                            String.valueOf(ans), String.valueOf(ans + step), String.valueOf(ans - 1), String.valueOf(ans + 2));
                }
            } else if (grade <= 4) {
                if (mode == 0) {
                    int a = 4 + random.nextInt(9);
                    int b = 3 + random.nextInt(9);
                    int c = random.nextInt(20);
                    int ans = (a * b) + c;
                    return new QuestionItem("🚀 Meteor Kalkanı Gücü:\n(" + a + " × " + b + ") + " + c + " işleminin sonucu kaçtır?",
                            String.valueOf(ans), String.valueOf(ans + 4), String.valueOf(Math.max(1, ans - 6)), String.valueOf(ans + 9));
                } else if (mode == 1) {
                    int b = 3 + random.nextInt(7);
                    int q = 5 + random.nextInt(12);
                    int a = b * q;
                    return new QuestionItem("🚀 Filo Paylaşımı:\n" + a + " ÷ " + b + " işleminin sonucu kaçtır?",
                            String.valueOf(q), String.valueOf(q + 2), String.valueOf(Math.max(1, q - 1)), String.valueOf(q + 4));
                } else {
                    int side = 5 + random.nextInt(15);
                    int perim = side * 4;
                    return new QuestionItem("🚀 Kare şeklindeki güneş panelinin bir kenarı " + side + " metredir. Çevresi kaç metredir?",
                            String.valueOf(perim), String.valueOf(side * side), String.valueOf(perim + 8), String.valueOf(side * 3));
                }
            } else if (grade <= 6) {
                if (mode == 0) {
                    int a = 7 + random.nextInt(12);
                    int b = 5 + random.nextInt(11);
                    int sub = 6 + random.nextInt(18);
                    int ans = (a * b) - sub;
                    return new QuestionItem("🚀 Galaksi Hız Denklemi:\n(" + a + " × " + b + ") - " + sub + " sonucu kaçtır?",
                            String.valueOf(ans), String.valueOf(ans + 5), String.valueOf(ans - 4), String.valueOf(ans + 11));
                } else if (mode == 1) {
                    int base = 2 + random.nextInt(5);
                    int ans = base * base * base;
                    return new QuestionItem("🚀 Küp Reaktör Gücü:\n" + base + "³ (" + base + " üssü 3) kaçtır?",
                            String.valueOf(ans), String.valueOf(base * 3), String.valueOf(base * base), String.valueOf(ans + 8));
                } else {
                    int whole = (2 + random.nextInt(8)) * 20;
                    int ans = whole / 4;
                    return new QuestionItem("🚀 Yakıt Deposunun %25'i (dörtte biri):\n" + whole + " litrenin %25'i kaç litredir?",
                            String.valueOf(ans), String.valueOf(ans + 10), String.valueOf(whole / 2), String.valueOf(ans - 5));
                }
            } else {
                if (mode == 0) {
                    int x = 3 + random.nextInt(14);
                    int k = 2 + random.nextInt(6);
                    int m = 4 + random.nextInt(12);
                    int rhs = k * x + m;
                    return new QuestionItem("🚀 Kuantum Koordinatı:\n" + k + "x + " + m + " = " + rhs + " ise x kaçtır?",
                            String.valueOf(x), String.valueOf(x + 2), String.valueOf(Math.max(1, x - 1)), String.valueOf(x + 4));
                } else {
                    int r = 4 + random.nextInt(11);
                    int sq = r * r;
                    return new QuestionItem("🚀 Karekök Motoru:\n√" + sq + " + 5 işleminin sonucu kaçtır?",
                            String.valueOf(r + 5), String.valueOf(r + 2), String.valueOf(r + 8), String.valueOf(sq));
                }
            }
        } else if (gameCategory == 1) {
            // Gizemli Kelime Kalesi (Genişletilmiş Soru Havuzu)
            String[][] bank = new String[][]{
                    {"🏰 'Özgürlük' kelimesinin eş anlamlısı olan kale parolası hangisidir?", "Hürriyet", "Esaret", "Kuvvet", "Adalet"},
                    {"🏰 'Siyah' kelimesinin zıt anlamlısı olan anahtar hangisidir?", "Beyaz", "Karanlık", "Gri", "Gece"},
                    {"🏰 'Misafir' kelimesinin eş anlamlısı nedir?", "Konuk", "Komşu", "Yolcu", "Arkadaş"},
                    {"🏰 'Damlaya damlaya göl olur' atasözü neyin önemini anlatır?", "Tutumluluk ve birikim", "Hızlı koşmak", "Yağmur yağması", "Yüzme öğrenmek"},
                    {"🏰 'Cesur' kelimesinin zıt anlamlısı hangisidir?", "Korkak", "Güçlü", "Kahraman", "Atılgan"},
                    {"🏰 'Okul' kelimesinin eş anlamlısı hangisidir?", "Mektep", "Sınıf", "Kütüphane", "Bahçe"},
                    {"🏰 'Kulak misafiri olmak' deyiminin anlamı nedir?", "Konuşulanları istemeden duymak", "Eve misafir çağırmak", "Müzik dinlemek", "Yüksek sesle konuşmak"},
                    {"🏰 'İhtiyar' kelimesinin eş anlamlısı hangisidir?", "Yaşlı", "Genç", "Çocuk", "Bilge"},
                    {"🏰 'Acemi' kelimesinin zıt anlamlısı hangisidir?", "Usta", "Çırak", "Yorgun", "Hızlı"},
                    {"🏰 'Göz boyamak' deyimi ne anlama gelir?", "Gösterişle yanıltmak, kandırmak", "Resim yapmak", "Evi boyamak", "Gözlük takmak"},
                    {"🏰 'Hediye' kelimesinin eş anlamlısı nedir?", "Armağan", "Ödül", "Emanet", "Mektup"},
                    {"🏰 'Bir elin nesi var, iki elin sesi var' atasözü neyi vurgular?", "Yardımlaşma ve dayanışmayı", "Alkış tutmayı", "Yalnız çalışmayı", "Sessiz olmayı"},
                    {"🏰 'Taze' kelimesinin zıt anlamlısı hangisidir?", "Bayat", "Yeni", "Sıcak", "Yumuşak"},
                    {"🏰 'Hekim' kelimesinin eş anlamlısı nedir?", "Doktor", "Eczacı", "Öğretmen", "Mühendis"}
            };
            String[] pick = bank[random.nextInt(bank.length)];
            return new QuestionItem(pick[0], pick[1], pick[2], pick[3], pick[4]);
        } else if (gameCategory == 2) {
            // Çılgın Laboratuvar Keşfi (Genişletilmiş Soru Havuzu)
            String[][] bank = new String[][]{
                    {"🧪 Dünyamızın ısı ve ışık kaynağı olan yıldız hangisidir?", "Güneş", "Ay", "Mars", "Kutup Yıldızı"},
                    {"🧪 Suyun katı haline laboratuvarda ne ad verilir?", "Buz", "Buhar", "Sis", "Yağmur"},
                    {"🧪 Mıknatıs aşağıdaki maddelerden hangisini çeker?", "Demir çivi", "Tahta kalem", "Cam bardak", "Plastik silgi"},
                    {"🧪 Bitkilerin güneş ışığıyla besin ve oksijen üretmesine ne denir?", "Fotosentez", "Buharlaşma", "Erime", "Yoğuşma"},
                    {"🧪 Güneş sisteminde halkalarıyla ünlü gezegen hangisidir?", "Satürn", "Merkür", "Venüs", "Mars"},
                    {"🧪 Ses aşağıdaki ortamlardan hangisinde yayılmaz?", "Boşlukta (Uzayda)", "Havada", "Suda", "Katı maddelerde"},
                    {"🧪 Dünyamızın kendi etrafında bir tam dönüşü kaç saat sürer?", "24 saat (1 gün)", "365 gün", "12 saat", "30 gün"},
                    {"🧪 Mıknatısın aynı kutupları (N ve N) birbirine yaklaştırılırsa ne olur?", "Birbirini iter", "Birbirini çeker", "Erir", "Işık saçar"},
                    {"🧪 Katı bir maddenin ısı alarak sıvı hale geçmesine ne denir?", "Erime", "Donma", "Kaynama", "Kırağılaşma"},
                    {"🧪 Vücudumuzda kanı pompalayan hayati organımız hangisidir?", "Kalp", "Mide", "Akciğer", "Karaciğer"},
                    {"🧪 Elektrik devresinde elektriği açıp kapatmaya yarayan eleman nedir?", "Anahtar", "Pil", "Kablo", "Duy"},
                    {"🧪 Güneş'e en yakın olan gezegen hangisidir?", "Merkür", "Neptün", "Jüpiter", "Dünya"}
            };
            String[] pick = bank[random.nextInt(bank.length)];
            return new QuestionItem(pick[0], pick[1], pick[2], pick[3], pick[4]);
        } else if (gameCategory == 3) {
            // Zaman Gezgini Hazine Avı (Genişletilmiş Soru Havuzu)
            String[][] bank = new String[][]{
                    {"🧭 Pusulanın renkli ucu her zaman hangi yönü gösterir?", "Kuzey", "Güney", "Doğu", "Batı"},
                    {"🧭 Güneş her sabah hangi yönden doğar?", "Doğu", "Batı", "Kuzey", "Güney"},
                    {"🧭 Türkiye'nin başkenti hangi şehrimizdir?", "Ankara", "İstanbul", "İzmir", "Bursa"},
                    {"🧭 Haritalarda denizler ve okyanuslar hangi renkle gösterilir?", "Mavi", "Kahverengi", "Yeşil", "Sarı"},
                    {"🧭 Cumhuriyetimiz hangi tarihte ilan edilmiştir?", "29 Ekim 1923", "23 Nisan 1920", "19 Mayıs 1919", "30 Ağustos 1922"},
                    {"🧭 Türkiye'de kaç coğrafi bölge bulunmaktadır?", "7 bölge", "5 bölge", "9 bölge", "12 bölge"},
                    {"🧭 Fiziki haritalarda yüksek dağlar hangi renkle gösterilir?", "Kahverengi", "Mavi", "Yeşil", "Beyaz"},
                    {"🧭 TBMM hangi tarihte açılmıştır ve Ulusal Egemenlik ve Çocuk Bayramı olarak kutlanır?", "23 Nisan 1920", "19 Mayıs 1919", "29 Ekim 1923", "30 Ağustos 1922"},
                    {"🧭 Karıncaların yuvalarının ağzı genellikle hangi yönü gösterir?", "Güney", "Kuzey", "Batı", "Doğu"},
                    {"🧭 Asya ile Avrupa kıtalarını birbirine bağlayan şehrimiz hangisidir?", "İstanbul", "Antalya", "Trabzon", "Konya"}
            };
            String[] pick = bank[random.nextInt(bank.length)];
            return new QuestionItem(pick[0], pick[1], pick[2], pick[3], pick[4]);
        } else if (gameCategory == 4) {
            // Sihirli Köprü Parolası (Genişletilmiş Soru Havuzu)
            String[][] bank = new String[][]{
                    {"🌍 'Apple' kelimesinin Türkçe karşılığı nedir?", "Elma", "Armut", "Çilek", "Portakal"},
                    {"🌍 'Book' kelimesinin Türkçe karşılığı nedir?", "Kitap", "Defter", "Kalem", "Masa"},
                    {"🌍 'Blue' hangi rengin İngilizcesidir?", "Mavi", "Kırmızı", "Yeşil", "Sarı"},
                    {"🌍 'Good Morning' ifadesi ne zaman söylenir?", "Sabah (Günaydın)", "Gece (İyi geceler)", "Vedalaşırken", "Teşekkür ederken"},
                    {"🌍 'Cat' ve 'Dog' kelimeleri sırasıyla hangi hayvanlardır?", "Kedi ve Köpek", "Kuş ve Balık", "At ve Aslan", "Tavşan ve Kaplumbağa"},
                    {"🌍 'School' kelimesinin anlamı nedir?", "Okul", "Hastane", "Kütüphane", "Park"},
                    {"🌍 'Teacher' kelimesinin Türkçe karşılığı nedir?", "Öğretmen", "Doktor", "Mühendis", "Pilot"},
                    {"🌍 'Water' ve 'Milk' sırasıyla ne demektir?", "Su ve Süt", "Ekmek ve Peynir", "Çay ve Kahve", "Meyve ve Sebze"},
                    {"🌍 'Sun' ve 'Moon' kelimelerinin anlamı nedir?", "Güneş ve Ay", "Yıldız ve Bulut", "Gece ve Gündüz", "Yağmur ve Kar"},
                    {"🌍 'Thank you' ifadesinin Türkçe karşılığı nedir?", "Teşekkür ederim", "Özür dilerim", "Merhaba", "Güle güle"},
                    {"🌍 'Yellow' ve 'Green' hangi renklerdir?", "Sarı ve Yeşil", "Mor ve Turuncu", "Siyah ve Beyaz", "Pembe ve Kahverengi"},
                    {"🌍 'Library' kelimesinin anlamı nedir?", "Kütüphane", "Sınıf", "Bahçe", "Müzik Odası"}
            };
            String[] pick = bank[random.nextInt(bank.length)];
            return new QuestionItem(pick[0], pick[1], pick[2], pick[3], pick[4]);
        } else if (gameCategory == 5) {
            // İyilik Bahçesi Yıldızları (Genişletilmiş Soru Havuzu)
            String[][] bank = new String[][]{
                    {"🕊️ Bir arkadaşımız bize yardım ettiğinde hangi nezaket sözünü söyleriz?", "Teşekkür ederim", "Acele et", "Bana ne", "Sonra görüşürüz"},
                    {"🕊️ İyilik Bahçesinde en parlak yıldız hangi davranışla kazanılır?", "Dürüst ve yardımsever olmak", "Eşyaları dağınık bırakmak", "Sıraya girmemek", "Arkadaşları küstürmek"},
                    {"🕊️ Verdiğimiz sözü tutmak bize ne kazandırır?", "Güven ve saygı", "Yorgunluk", "Zaman kaybı", "Unutkanlık"},
                    {"🕊️ Büyüklerimizi ve küçüklerimizi koruyup sevgi göstermek hangi değerimizdir?", "Merhamet ve saygı", "Kıskançlık", "İnatçılık", "Acelecilik"},
                    {"🕊️ İstemeden bir hata yaptığımızda ne söylemeliyiz?", "Özür dilerim", "Senin suçun", "Beni ilgilendirmez", "Hiçbir şey"},
                    {"🕊️ Kendimize yapılmasını istemediğimiz bir davranışı başkasına yapmamaya ne denir?", "Empati (Diğerkâmlık)", "Bencillik", "Kızgınlık", "Unutkanlık"},
                    {"🕊️ Sofrada yemeğe başlamadan önce ve bitirdikten sonra ne yapmak güzel bir davranıştır?", "Şükretmek ve elleri yıkamak", "Televizyonu açmak", "Koşmak", "Yemeği dökmek"}
            };
            String[] pick = bank[random.nextInt(bank.length)];
            return new QuestionItem(pick[0], pick[1], pick[2], pick[3], pick[4]);
        } else if (gameCategory == 6) {
            // Süper Kaptan Güvenli Rota (Genişletilmiş Soru Havuzu)
            String[][] bank = new String[][]{
                    {"🏎️ Yaya geçidinde yayalar için hangi ışık yandığında karşıya geçilir?", "Yeşil ışık", "Kırmızı ışık", "Sarı ışık", "Mavi ışık"},
                    {"🏎️ Otomobile bindiğimizde güvenliğimiz için ilk ne yapmalıyız?", "Emniyet kemerini takmalıyız", "Camı açmalıyız", "Radyoyu açmalıyız", "Ayakta durmalıyız"},
                    {"🏎️ Bisiklet ve kaykay sürerken başımızı korumak için ne takmalıyız?", "Kask", "Şapka", "Gözlük", "Atkı"},
                    {"🏎️ Türkiye'de tüm acil durumlar için aranan tek ortak acil numara kaçtır?", "112", "182", "153", "114"},
                    {"🏎️ Üçgen şeklindeki kırmızı çerçeveli trafik levhaları bize ne bildirir?", "Tehlike uyarı işaretidir, dikkatli olunmalıdır", "Park yapılacağını", "Otoyol bittiğini", "Restoran olduğunu"},
                    {"🏎️ Yaya kaldırımı olmayan yollarda yürürken yolun hangi tarafından yürümeliyiz?", "Gelen araçları görebilmek için sol kenardan", "Yolun tam ortasından", "Gözümüz kapalı", "Koşarak sağdan"},
                    {"🏎️ Okul servisinden indiğimizde hemen taşıtın önünden veya arkasından geçmek doğru mudur?", "Hayır, servis uzaklaşana kadar kaldırımda beklemeliyiz", "Evet, koşarak geçmeliyiz", "Fark etmez", "Geri geri yürümeliyiz"}
            };
            String[] pick = bank[random.nextInt(bank.length)];
            return new QuestionItem(pick[0], pick[1], pick[2], pick[3], pick[4]);
        } else {
            // Barış Adası Kahramanları (Genişletilmiş Soru Havuzu)
            String[][] bank = new String[][]{
                    {"⚖️ Her çocuğun eğitim alma, oyun oynama ve sağlık hizmetinden yararlanma hakkına ne denir?", "Çocuk Hakları", "Yarışma Kuralları", "Alışveriş Listesi", "Nöbet Çizelgesi"},
                    {"⚖️ Ortak kullanım alanlarını ve doğayı temiz tutmak kimin görevidir?", "Hepimizin ortak sorumluluğudur", "Sadece yetişkinlerin", "Hiç kimsenin", "Sadece turistlerin"},
                    {"⚖️ Grup çalışmalarında başarıya ulaşmanın en güzel yolu nedir?", "İş birliği ve adaletli paylaşım", "Tek başına karar vermek", "Başkalarını dinlememek", "Çalışmayı yarıda bırakmak"},
                    {"⚖️ İnsanların düşüncelerine saygı duymak Barış Adasında neyi güçlendirir?", "Hoşgörü ve dostluğu", "Kavgayı", "Gürültüyü", "Yalnızlığı"},
                    {"⚖️ Bir anlaşmazlık yaşadığımızda sorunu nasıl çözmeliyiz?", "Konuşarak ve uzlaşarak", "Bağırarak", "Küserek", "Eşyaları fırlatarak"},
                    {"⚖️ Her insanın doğuştan sahip olduğu temel hakların özelliğine ne denir?", "Vazgeçilmez ve eşit haklar", "Sadece zenginlerin hakkı", "Geçici izin", "Oyun kuralı"}
            };
            String[] pick = bank[random.nextInt(bank.length)];
            return new QuestionItem(pick[0], pick[1], pick[2], pick[3], pick[4]);
        }
    }
}
