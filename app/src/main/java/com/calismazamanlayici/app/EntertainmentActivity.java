package com.calismazamanlayici.app;

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
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Öğrenci uygulamasındaki Eğlence (Oyun) Merkezi:
 * - İlerlemeli ve zıplamalı "🏃‍♂️ Süper Yıldız Zıplama Macerası" oyununu ve
 *   her dersin kazanımlarına uygun (oyun adında ders ismi geçmeyen) zenginleştirilmiş sorulu 8 oyunu içerir.
 * - Her oyunun kendine özgü farklı arka plan müziği vardır.
 * - Oynanan oyunlar çalışma sayılmaz, eğlence geçmişi olarak Takvime eklenir.
 */
public class EntertainmentActivity extends Activity {

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
    private PlatformerJumpGameView jumpGameView;

    private TextView tvActiveGameTitle;
    private TextView tvGameStatsBar;
    private TextView tvGameQuestionPrompt;
    private TextView tvGameFeedbackBanner;
    private LinearLayout layoutGameOptionButtons;
    private FrameLayout frameEffectOverlay;
    private StarBurstEffectView starEffectView;

    private StudyDatabaseHelper dbHelper;
    private final GameMusicSynthesizer musicSynth = new GameMusicSynthesizer();
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final Set<String> askedPrompts = new HashSet<>();
    private int bestStreak = 0;

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
            StudyDatabaseHelper.setCurrentEntSpentSeconds(EntertainmentActivity.this, totalEntElapsedSeconds);

            if (inActiveGame) {
                activeGameSessionSeconds++;
            }

            updateTimerBannerUi();

            if (totalEntElapsedSeconds >= StudyDatabaseHelper.ENTERTAINMENT_WARNING_SECONDS && !elevenMinWarningShown) {
                elevenMinWarningShown = true;
                showElevenMinuteWarningPopup();
            }

            if (totalEntElapsedSeconds >= StudyDatabaseHelper.ENTERTAINMENT_MAX_LIMIT_SECONDS) {
                performAutoExitAndRelock();
                return;
            }

            timerHandler.postDelayed(this, 1000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_entertainment);

        dbHelper = new StudyDatabaseHelper(this);
        if (!dbHelper.isEntertainmentUnlocked(this)) {
            Toast.makeText(this, "🔒 Erişim sağlamak için, bugün 40 dakikalık bir çalışma tamamla", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        totalEntElapsedSeconds = StudyDatabaseHelper.getCurrentEntElapsedSafe(this);

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

        jumpGameView = new PlatformerJumpGameView(this, new PlatformerJumpGameView.JumpGameListener() {
            @Override
            public void onStatsUpdated(int score, int stage, String stageName, int lives) {
                tvJumpGameStatus.setSingleLine(true);
                tvJumpGameStatus.setText("🗺️ Parkur " + stage + "/6: " + stageName + " (Üstten Parkur Seçebilirsin)");
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

        ThemeHelper.Palette p = ThemeHelper.getPalette(this);
        ThemeHelper.applyWindowAndSurface(this, scrollRootEntertainment);
        ThemeHelper.styleHeader(this, layoutEntHeaderCard);
        btnJumpAction.setBackground(ThemeHelper.createButtonDrawable(this, p.primary));

        int grade = StudyDatabaseHelper.getGradeLevel(this);
        tvEntHeaderTitle.setText("🎮 Eğlence Dünyası (" + grade + ". Sınıf)");

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

    private void updateTimerBannerUi() {
        int remainingToRelock = Math.max(0, StudyDatabaseHelper.ENTERTAINMENT_MAX_LIMIT_SECONDS - totalEntElapsedSeconds);
        tvEntTimerStatus.setText("⏱️ Geçen Eğlence Süresi: "
                + StudyDatabaseHelper.formatClock(totalEntElapsedSeconds)
                + " • Kalan Hak: " + StudyDatabaseHelper.formatClock(remainingToRelock));
    }

    private void showElevenMinuteWarningPopup() {
        speakViaService("Normal mola süresinden daha uzun zaman harcadın. 1 dakika sonra otomatik çıkış yapılacak");
        if (!isFinishing()) {
            new AlertDialog.Builder(this)
                    .setTitle("⏰ Eğlence Süresi Uyarısı")
                    .setMessage("Normal mola süresinden daha uzun zaman harcadın. 1 dakika sonra otomatik çıkış yapılacak")
                    .setPositiveButton("Tamam", null)
                    .show();
        }
    }

    private void performAutoExitAndRelock() {
        timerHandler.removeCallbacks(secondTickRunnable);
        if (jumpGameView != null) jumpGameView.stopGame();
        musicSynth.stopBackgroundMusic();
        saveActiveGameSessionToCalendarIfNeeded();

        dbHelper.relockEntertainmentAfterLimit(this);
        speakViaService("Eğlence süresi tamamlandı. Tekrar açmak için kırk dakika çalışma yapmalısın.");
        Toast.makeText(this,
                "🔒 Eğlence süresi doldu ve yeniden kilitlendi! Açmak için 40 dakika daha çalışma yapmalısın.",
                Toast.LENGTH_LONG).show();
        finish();
    }

    private void speakViaService(String text) {
        try {
            Intent i = new Intent(this, TimerForegroundService.class);
            i.setAction(TimerForegroundService.ACTION_SPEAK_CUSTOM);
            i.putExtra(TimerForegroundService.EXTRA_CUSTOM_SPEECH, text);
            startService(i);
        } catch (Exception ignored) {
        }
    }

    private void saveActiveGameSessionToCalendarIfNeeded() {
        if (inActiveGame && activeGameSessionSeconds >= 3 && activeGameTitle != null && !activeGameTitle.isEmpty()) {
            dbHelper.insertSession(StudyDatabaseHelper.MODE_GAME, activeGameTitle, activeGameSessionSeconds);
            activeGameSessionSeconds = 0;
        }
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
        int grade = StudyDatabaseHelper.getGradeLevel(this);
        int subjCount = StudyDatabaseHelper.getSubjectsForCurrentGrade(this).size();
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
            btnPlay.setBackground(ThemeHelper.createButtonDrawable(this, g.accentColor));
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
        bestStreak = 0;
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
        if (currentLives <= 0) return;
        int grade = StudyDatabaseHelper.getGradeLevel(this);
        QuestionItem candidate = generateGradeScaledQuestion(activeGameIndex % 8, grade, random);
        for (int attempt = 0; attempt < 45; attempt++) {
            if (!askedPrompts.contains(candidate.prompt)) {
                break;
            }
            candidate = generateGradeScaledQuestion(activeGameIndex % 8, grade, random);
        }
        if (askedPrompts.size() > 350) {
            askedPrompts.clear();
        }
        askedPrompts.add(candidate.prompt);
        final QuestionItem q = candidate;

        tvGameQuestionPrompt.setText(q.prompt);
        layoutGameOptionButtons.removeAllViews();

        ThemeHelper.Palette p = ThemeHelper.getPalette(this);
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
            btn.setBackground(ThemeHelper.createButtonDrawable(this, optionColors[i % optionColors.length]));
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
            if (currentStreak > bestStreak) {
                bestStreak = currentStreak;
            }
            int gained = 10 + (currentStreak * 5);
            currentScore += gained;
            musicSynth.playSuccessEffect();
            starEffectView.triggerBurst("🌟 +" + gained + " PUAN! (" + currentStreak + "x SERİ!) 🌟");
            tvGameFeedbackBanner.setText("✅ Harika! Doğru cevap: " + correct);
            updateStatsBar();
            timerHandler.postDelayed(this::loadNextQuestion, 600L);
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
                        + "\n⭐ Toplam Puanın: " + currentScore
                        + "\n🔥 En Yüksek Serin: " + bestStreak + "x"
                        + "\n\nYeni üretilen sorularla tekrar oynamak ister misin?")
                .setPositiveButton("🔄 Tekrar Oyna", (d, w) -> {
                    currentScore = 0;
                    currentStreak = 0;
                    bestStreak = 0;
                    currentLives = 3;
                    updateStatsBar();
                    loadNextQuestion();
                })
                .setNegativeButton("🏠 Oyun Lobisine Dön", (d, w) -> {
                    saveActiveGameSessionToCalendarIfNeeded();
                    showLobbyScreen();
                })
                .show();
    }

    // =========================================================================
    // AKILLI DİNAMİK SORU ÜRETİM MOTORU (Yüzlerce Tekrarsız Kombinasyon & Şablon)
    // =========================================================================

    private static final String[][] SYNONYM_PAIRS = new String[][]{
            {"Özgürlük", "Hürriyet"}, {"Misafir", "Konuk"}, {"Okul", "Mektep"}, {"İhtiyar", "Yaşlı"},
            {"Hediye", "Armağan"}, {"Hekim", "Doktor"}, {"Yıl", "Sene"}, {"Sonbahar", "Güz"},
            {"Rüzgâr", "Yel"}, {"Kalp", "Yürek"}, {"Soru", "Sual"}, {"Cevap", "Yanıt"},
            {"Öykü", "Hikâye"}, {"Anı", "Hatıra"}, {"Zaman", "Vakit"}, {"Doğa", "Tabiat"},
            {"Yetenek", "Kabiliyet"}, {"Görev", "Vazife"}, {"Ulus", "Millet"}, {"Yurt", "Vatan"},
            {"Barış", "Sulh"}, {"Tutsak", "Esir"}, {"Fayda", "Yarar"}, {"Zarar", "Ziyan"},
            {"Kırmızı", "Al"}, {"Beyaz", "Ak"}, {"Siyah", "Kara"}, {"Yoksul", "Fakir"},
            {"Kafa", "Baş"}, {"Konut", "Ev"}, {"Pabuç", "Ayakkabı"}, {"Giysi", "Elbise"},
            {"Besin", "Gıda"}, {"Asır", "Yüzyıl"}, {"Kelime", "Sözcük"}, {"Cümle", "Tümce"},
            {"Öğrenci", "Talebe"}, {"Öğretmen", "Muallim"}, {"Sınav", "İmtihan"}, {"Delil", "Kanıt"},
            {"Kuvvet", "Güç"}, {"Sevinç", "Mutluluk"}, {"Özlem", "Hasret"}, {"Uzak", "Irak"},
            {"Nehir", "Irmak"}, {"Duru", "Berrak"}, {"Kılavuz", "Rehber"}, {"Şair", "Ozan"}
    };

    private static final String[][] ANTONYM_PAIRS = new String[][]{
            {"Siyah", "Beyaz"}, {"Cesur", "Korkak"}, {"Acemi", "Usta"}, {"Taze", "Bayat"},
            {"Sıcak", "Soğuk"}, {"Uzun", "Kısa"}, {"Geniş", "Dar"}, {"Yüksek", "Alçak"},
            {"Ağır", "Hafif"}, {"Dolu", "Boş"}, {"Zengin", "Fakir"}, {"Ucuz", "Pahalı"},
            {"Erken", "Geç"}, {"Hızlı", "Yavaş"}, {"Doğru", "Yanlış"}, {"Güzel", "Çirkin"},
            {"İyi", "Kötü"}, {"Büyük", "Küçük"}, {"Genç", "Yaşlı"}, {"Açık", "Kapalı"},
            {"İleri", "Geri"}, {"Alt", "Üst"}, {"İç", "Dış"}, {"Sağ", "Sol"},
            {"Doğu", "Batı"}, {"Kuzey", "Güney"}, {"Barış", "Savaş"}, {"Ödül", "Ceza"},
            {"Sevinç", "Üzüntü"}, {"Dost", "Düşman"}, {"İlk", "Son"}, {"Var", "Yok"},
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
            {"Güneş'e en yakın olan gezegen hangisidir?", "Merkür", "Venüs", "Mars", "Jüpiter"},
            {"Güneş sisteminin en büyük gezegeni hangisidir?", "Jüpiter", "Satürn", "Dünya", "Neptün"},
            {"Kızıl Gezegen olarak bilinen gezegen hangisidir?", "Mars", "Venüs", "Merkür", "Uranüs"},
            {"Etrafındaki belirgin halkalarıyla ünlü gezegen hangisidir?", "Satürn", "Mars", "Merkür", "Venüs"},
            {"Dünya'nın doğal uydusu aşağıdakilerden hangisidir?", "Ay", "Güneş", "Phobos", "Titan"},
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
            {"Işığın kırılması sonucu yağmur sonrası gökyüzünde oluşan renk kuşağına ne denir?", "Gökkuşağı", "Yıldırım", "Şimşek", "Kutup Işığı"},
            {"Vücudumuzda kanı tüm organlara pompalayan kaslı organımız hangisidir?", "Kalp", "Akciğer", "Karaciğer", "Böbrek"},
            {"Kanımızı süzerek atık maddeleri idrar olarak uzaklaştıran organımız hangisidir?", "Böbrek", "Mide", "Akciğer", "Kalp"},
            {"Soluk alıp verirken oksijenin kana geçtiği temel solunum organımız hangisidir?", "Akciğer", "Mide", "Kalp", "Dalak"},
            {"Besinlerin kimyasal sindiriminin tamamlandığı ve kana emildiği organ hangisidir?", "İnce bağırsak", "Yutak", "Yemek borusu", "Kalın bağırsak"}
    };

    private static final String[][] GEOGRAPHY_HISTORY_FACTS = new String[][]{
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
            {"Büyük Taarruz'un zaferle sonuçlandığı Zafer Bayramı hangi tarihte kutlanır?", "30 Ağustos", "29 Ekim", "19 Mayıs", "23 Nisan"},
            {"Ekvator'a paralel olarak çizildiği varsayılan hayali çemberlere ne ad verilir?", "Paralel (Enlem)", "Meridyen (Boylam)", "Ölçek", "İzohips"},
            {"İki meridyen arasındaki yerel saat farkı her yerde kaç dakikadır?", "4 dakika", "10 dakika", "15 dakika", "60 dakika"},
            {"Peri bacaları ve yer altı şehirleriyle ünlü Kapadokya yöremiz hangi bölgemizdedir?", "İç Anadolu Bölgesi", "Karadeniz Bölgesi", "Marmara Bölgesi", "Ege Bölgesi"},
            {"Pamukkale travertenleri hangi ilimizde yer almaktadır?", "Denizli", "Antalya", "Muğla", "Aydın"},
            {"Sümela Manastırı ve Uzungöl hangi ilimizde bulunmaktadır?", "Trabzon", "Rize", "Artvin", "Ordu"}
    };

    private static final String[][] ENGLISH_VOCAB = new String[][]{
            {"Apple", "Elma", "Meyve"}, {"Orange", "Portakal", "Meyve"}, {"Banana", "Muz", "Meyve"},
            {"Strawberry", "Çilek", "Meyve"}, {"Watermelon", "Karpuz", "Meyve"}, {"Cherry", "Kiraz", "Meyve"},
            {"Lion", "Aslan", "Hayvan"}, {"Elephant", "Fil", "Hayvan"}, {"Dolphin", "Yunus", "Hayvan"},
            {"Butterfly", "Kelebek", "Hayvan"}, {"Rabbit", "Tavşan", "Hayvan"}, {"Turtle", "Kaplumbağa", "Hayvan"},
            {"Eagle", "Kartal", "Hayvan"}, {"Monkey", "Maymun", "Hayvan"}, {"Horse", "At", "Hayvan"},
            {"Red", "Kırmızı", "Renk"}, {"Blue", "Mavi", "Renk"}, {"Green", "Yeşil", "Renk"},
            {"Yellow", "Sarı", "Renk"}, {"Purple", "Mor", "Renk"}, {"White", "Beyaz", "Renk"},
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
        if (gameCategory == 0) {
            return generateMathQuestion(grade, random);
        } else if (gameCategory == 1) {
            return generateTurkishQuestion(grade, random);
        } else if (gameCategory == 2) {
            return generateScienceQuestion(grade, random);
        } else if (gameCategory == 3) {
            return generateSocialStudiesQuestion(grade, random);
        } else if (gameCategory == 4) {
            return generateEnglishQuestion(grade, random);
        } else if (gameCategory == 5) {
            return generateValuesQuestion(grade, random);
        } else if (gameCategory == 6) {
            return generateSafetyQuestion(grade, random);
        } else {
            return generateCivicsQuestion(grade, random);
        }
    }

    private static QuestionItem generateMathQuestion(int grade, Random random) {
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

    private static QuestionItem generateTurkishQuestion(int grade, Random random) {
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

    private static QuestionItem generateScienceQuestion(int grade, Random random) {
        int mode = random.nextInt(4);
        if (mode == 0) {
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
        } else if (mode == 1) {
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
        } else if (mode == 2 && grade >= 5) {
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

    private static QuestionItem generateSocialStudiesQuestion(int grade, Random random) {
        int mode = random.nextInt(4);
        if (mode == 0) {
            int[] years = new int[]{1071, 1299, 1453, 1517, 1789, 1876, 1919, 1923, 1938, 2024};
            int y = years[random.nextInt(years.length)];
            int century = (y / 100) + 1;
            return new QuestionItem("🧭 Zaman Çizelgesi Görevi:\nTarihteki " + y + " yılı kaçıncı yüzyıl içerisinde yer alır?",
                    century + ". Yüzyıl", (century - 1) + ". Yüzyıl", (century + 1) + ". Yüzyıl", (century - 2) + ". Yüzyıl");
        } else if (mode == 1) {
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

    private static QuestionItem generateEnglishQuestion(int grade, Random random) {
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
        } else if (mode == 2) {
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

    private static QuestionItem generateValuesQuestion(int grade, Random random) {
        if (random.nextInt(4) == 0) {
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
        String[] pick = bank[random.nextInt(bank.length)];
        return new QuestionItem(pick[0], pick[1], pick[2], pick[3], pick[4]);
    }

    private static QuestionItem generateSafetyQuestion(int grade, Random random) {
        if (random.nextInt(3) == 0) {
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
        String[] pick = bank[random.nextInt(bank.length)];
        return new QuestionItem(pick[0], pick[1], pick[2], pick[3], pick[4]);
    }

    private static QuestionItem generateCivicsQuestion(int grade, Random random) {
        if (random.nextInt(3) == 0) {
            int tons = 2 + random.nextInt(8);
            int trees = tons * 17;
            return new QuestionItem("⚖️ Doğa ve Geri Dönüşüm Görevi:\n1 ton atık kağıdın geri dönüştürülmesi 17 ağacı kesilmekten kurtarmaktadır. Okulumuzda " + tons + " ton kağıt geri dönüştürülürse kaç ağaç kurtulur?",
                    trees + " ağaç", (trees + 10) + " ağaç", (tons + 17) + " ağaç", (trees - 7) + " ağaç");
        }
        String[][] bank = new String[][]{
                {"⚖️ Birleşmiş Milletler sözleşmesine göre 18 yaşına kadar her bireyin eğitim, sağlık ve oyun hakkına ne denir?", "Çocuk Hakları", "Yarışma Kuralları", "Nöbet Çizelgesi", "Kulüp Tüzüğü"},
                {"⚖️ Bir kişinin özgürlüğünün sınırı nerede biter?", "Başkasının hak ve özgürlüğünün başladığı yerde", "Hiçbir yerde bitmez", "Sadece okulda biter", "Akşam saatlerinde"},
                {"⚖️ Sınıf başkanlığı seçiminde her öğrencinin eşit oy hakkına sahip olması hangi kavramla ilgilidir?", "Demokrasi ve eşitlik", "Ayrıcalık", "Zorlama", "Rastlantı"},
                {"⚖️ İnternet ortamında şifrelerimizi ve kişisel bilgilerimizi yabancılarla paylaşmamak neyin gereğidir?", "Dijital güvenlik ve mahremiyet", "Oyun kuralı", "İnternet hızı", "Bilgisayar bakımı"},
                {"⚖️ Bir anlaşmazlık yaşadığımızda sorunu çözmenin en adil ve barışçıl yolu nedir?", "İletişim kurarak ve uzlaşarak çözmek", "Bağırarak kabul ettirmek", "Küserek konuşmamak", "Eşyaları fırlatmak"},
                {"⚖️ Doğayı, ormanları ve su kaynaklarını korumak kimin sorumluluğudur?", "Tüm insanların ortak vatandaşlık sorumluluğudur", "Sadece belediyenin", "Sadece yetişkinlerin", "Hiç kimsenin"},
                {"⚖️ Grup çalışmalarında başarıya ulaşmanın en güzel yolu aşağıdakilerden hangisidir?", "Görev paylaşımı ve iş birliği yapmak", "Her işi tek bir kişiye yüklemek", "Başkalarını dinlememek", "Çalışmayı yarıda bırakmak"},
                {"⚖️ İnsanların sırf dış görünüşü veya kökeni yüzünden peşin hükümle değerlendirilmesine ne denir?", "Önyargı (Kalıp yargı)", "Empati", "Hoşgörü", "Dayanışma"},
                {"⚖️ Haberleşme ve özel hayatın gizliliği hakkı ne ile güvence altına alınmıştır?", "Anayasa ve yasalarla", "Oyun kurallarıyla", "Komşu kararıyla", "Hava durumuyla"},
                {"⚖️ Alışveriş yaptıktan sonra fiş veya fatura almak hangi açıdan önemlidir?", "Tüketici haklarımızı korumak ve vergi bilinci için", "Çantayı doldurmak için", "Kağıt biriktirmek için", "Hiçbir önemi yoktur"}
        };
        String[] pick = bank[random.nextInt(bank.length)];
        return new QuestionItem(pick[0], pick[1], pick[2], pick[3], pick[4]);
    }
}
