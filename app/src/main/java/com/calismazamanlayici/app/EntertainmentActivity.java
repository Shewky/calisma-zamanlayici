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
import java.util.Random;

/**
 * Öğrenci uygulamasındaki Eğlence (Oyun) Merkezi:
 * - Oyun isimlerinde ders ismi geçmez; öğrencinin kaçıncı sınıfta olduğuna (1.-8. Sınıf) göre
 *   her dersin kazanımlarına uygun etkileşimli, müzikli ve görsel efektli oyunlar üretir.
 * - 11. dakikada "Normal mola süresinden daha uzun zaman harcadın. 1 dakika sonra otomatik çıkış yapılacak"
 *   pop-up uyarısı verir.
 * - 12. dakikada otomatik olarak ana ekrana çıkış yapar, Eğlence özelliğini yeniden kilitler
 *   (açılması için 40 dakika daha çalışma gerekir) ve oynanan oyunları süresiyle Takvime ekler.
 */
public class EntertainmentActivity extends Activity {

    private ScrollView scrollRootEntertainment;
    private LinearLayout layoutEntHeaderCard;
    private TextView tvEntHeaderTitle;
    private TextView tvEntTimerStatus;
    private Button btnToggleGameMusic;

    private LinearLayout layoutGameLobbyContainer;
    private LinearLayout layoutGameCardsList;
    private LinearLayout layoutActiveGameArena;

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

    private int totalEntElapsedSeconds = 0;
    private boolean elevenMinWarningShown = false;

    // Aktif oynanan oyunun bilgileri ve takvim süresi
    private boolean inActiveGame = false;
    private int activeGameIndex = 0;
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

        public GameDefinition(int index, String gameTitle, String subtitle, int accentColor) {
            this.index = index;
            this.gameTitle = gameTitle;
            this.subtitle = subtitle;
            this.accentColor = accentColor;
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
     * Doğru cevaplarda ekranda yıldız ve konfeti patlaması çizen etkileşimli efekt görünümü.
     */
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
            paint.setTextSize(38f);
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

            // 11. dakikada (660. saniye) pop-up uyarı ver!
            if (totalEntElapsedSeconds >= StudyDatabaseHelper.ENTERTAINMENT_WARNING_SECONDS && !elevenMinWarningShown) {
                elevenMinWarningShown = true;
                showElevenMinuteWarningPopup();
            }

            // 12. dakikada (720. saniye - uyarıdan 1 dk sonra) otomatik çıkış yap ve yeniden kilitle!
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
        layoutActiveGameArena = findViewById(R.id.layoutActiveGameArena);

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

        ThemeHelper.applyWindowAndSurface(this, scrollRootEntertainment);
        ThemeHelper.styleHeader(this, layoutEntHeaderCard);

        int grade = StudyDatabaseHelper.getGradeLevel(this);
        tvEntHeaderTitle.setText("🎮 Eğlence Dünyası (" + grade + ". Sınıf)");

        btnToggleGameMusic.setOnClickListener(v -> {
            boolean muted = musicSynth.toggleMute();
            btnToggleGameMusic.setText(muted ? "🔇 Müzik: Kapalı" : "🎵 Müzik: Açık");
        });

        btnBackToGameLobby.setOnClickListener(v -> {
            saveActiveGameSessionToCalendarIfNeeded();
            showLobbyScreen();
        });

        btnExitEntertainment.setOnClickListener(v -> {
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

    /**
     * 11 dakika dolduğunda ekrana çıkan pop-up uyarı mesajı.
     */
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

    /**
     * 12 dakika dolduğunda (uyarıdan 1 dk sonra) otomatik çıkış yapar ve Eğlenceyi yeniden kilitler.
     */
    private void performAutoExitAndRelock() {
        timerHandler.removeCallbacks(secondTickRunnable);
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

    /**
     * Oyun isimlerinde ders ismi geçmez; ancak her ders için öğrencinin sınıfına uygun bir oyun üretilir.
     */
    private List<GameDefinition> getGamesForStudent() {
        int grade = StudyDatabaseHelper.getGradeLevel(this);
        List<GameDefinition> games = new ArrayList<>();
        games.add(new GameDefinition(0, "🚀 Galaksi Sayı Avcısı",
                grade + ". Sınıf uzay gemisiyle yıldız hedeflerini hesapla ve meteorları aş!", 0xFF1E88E5));
        games.add(new GameDefinition(1, "🏰 Gizemli Kelime Kalesi",
                grade + ". Sınıf şifreli parolaları, atasözlerini ve anlam hazinelerini çöz!", 0xFF8E24AA));
        games.add(new GameDefinition(2, "🧪 Çılgın Laboratuvar Keşfi",
                grade + ". Sınıf gezegenler, doğa gizemleri ve enerji deneylerini tamamla!", 0xFF00897B));
        games.add(new GameDefinition(3, "🧭 Zaman Gezgini Hazine Avı",
                grade + ". Sınıf pusula, harita ve kültür hazinelerinin izini sür!", 0xFFF57C00));
        games.add(new GameDefinition(4, "🌍 Sihirli Köprü Parolası",
                grade + ". Sınıf dünya kelimelerini eşleştirerek altın köprüleri aç!", 0xFFD81B60));
        games.add(new GameDefinition(5, "🕊️ İyilik Bahçesi Yıldızları",
                grade + ". Sınıf erdem, paylaşım ve güzel davranış yıldızlarını topla!", 0xFF43A047));
        games.add(new GameDefinition(6, "🏎️ Süper Kaptan Güvenli Rota",
                grade + ". Sınıf işaretleri tanı, güvenli rotayı seç ve kupayı kazan!", 0xFFE53935));
        games.add(new GameDefinition(7, "⚖️ Barış Adası Kahramanları",
                grade + ". Sınıf adalet, haklar ve dayanışma görevlerini tamamla!", 0xFF3949AB));

        // Kullanıcının eklediği özel ders sayısı 8'den fazlaysa her ek ders için ekstra macera oyunu üret
        List<String> customSubjects = StudyDatabaseHelper.getSubjectsForCurrentGrade(this);
        for (int i = 8; i < customSubjects.size(); i++) {
            int extraIdx = i - 7;
            games.add(new GameDefinition(i, "🌟 Bilgelik Kristali Macerası #" + extraIdx,
                    grade + ". Sınıf özel bilgi kristallerini toplayarak rekor kır!", 0xFF00ACC1));
        }
        return games;
    }

    private void buildGameLobbyCards() {
        layoutGameCardsList.removeAllViews();
        List<GameDefinition> games = getGamesForStudent();
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
            btnPlay.setText("▶️ Maceraya Başla");
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
        layoutActiveGameArena.setVisibility(View.VISIBLE);

        tvActiveGameTitle.setText(game.gameTitle);
        tvGameFeedbackBanner.setText("🎵 Arka plan müziği aktif! Doğru cevaba dokunarak yıldızları patlat!");
        updateStatsBar();
        loadNextQuestion();
    }

    private void updateStatsBar() {
        StringBuilder hearts = new StringBuilder();
        for (int i = 0; i < currentLives; i++) hearts.append("❤️");
        if (currentLives <= 0) hearts.append("🔄");
        tvGameStatsBar.setText("⭐ Puan: " + currentScore + "  •  🔥 Seri: " + currentStreak + "x  •  " + hearts);
    }

    private void loadNextQuestion() {
        int grade = StudyDatabaseHelper.getGradeLevel(this);
        final QuestionItem q = generateGradeScaledQuestion(activeGameIndex % 8, grade);

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
     * Öğrencinin sınıf seviyesine (1. - 8. Sınıf) göre zorluğu otomatik ayarlanan soru/görev üretici.
     */
    private QuestionItem generateGradeScaledQuestion(int gameCategory, int grade) {
        if (gameCategory == 0) {
            // Galaksi Sayı Avcısı (1-8. Sınıf Sayısal Görevler)
            if (grade <= 2) {
                int a = 5 + random.nextInt(15 * grade);
                int b = 3 + random.nextInt(12 * grade);
                int ans = a + b;
                return new QuestionItem("🚀 Uzay İstasyonu Rotası:\n" + a + " + " + b + " kaçtır?",
                        String.valueOf(ans), String.valueOf(ans + 2), String.valueOf(Math.max(1, ans - 3)), String.valueOf(ans + 5));
            } else if (grade <= 4) {
                int a = 4 + random.nextInt(9);
                int b = 3 + random.nextInt(9);
                int c = random.nextInt(15);
                int ans = (a * b) + c;
                return new QuestionItem("🚀 Meteor Kalkanı Gücü:\n(" + a + " × " + b + ") + " + c + " işleminin sonucu kaçtır?",
                        String.valueOf(ans), String.valueOf(ans + 4), String.valueOf(Math.max(1, ans - 6)), String.valueOf(ans + 10));
            } else if (grade <= 6) {
                int a = 6 + random.nextInt(12);
                int b = 4 + random.nextInt(10);
                int sub = 5 + random.nextInt(15);
                int ans = (a * b) - sub;
                return new QuestionItem("🚀 Galaksi Hız Denklemi:\n(" + a + " × " + b + ") - " + sub + " sonucu kaçtır?",
                        String.valueOf(ans), String.valueOf(ans + 5), String.valueOf(ans - 4), String.valueOf(ans + 12));
            } else {
                int x = 3 + random.nextInt(12);
                int k = 2 + random.nextInt(5);
                int m = 4 + random.nextInt(10);
                int rhs = k * x + m;
                return new QuestionItem("🚀 Kuantum Koordinatı:\n" + k + "x + " + m + " = " + rhs + " ise x kaçtır?",
                        String.valueOf(x), String.valueOf(x + 2), String.valueOf(Math.max(1, x - 1)), String.valueOf(x + 4));
            }
        } else if (gameCategory == 1) {
            // Gizemli Kelime Kalesi
            String[][] bank = new String[][]{
                    {"🏰 'Özgürlük' kelimesinin eş anlamlısı olan kale parolası hangisidir?", "Hürriyet", "Esaret", "Kuvvet", "Adalet"},
                    {"🏰 'Siyah' kelimesinin zıt anlamlısı olan anahtar hangisidir?", "Beyaz", "Karanlık", "Gri", "Gece"},
                    {"🏰 'Misafir' kelimesinin eş anlamlısı nedir?", "Konuk", "Komşu", "Yolcu", "Arkadaş"},
                    {"🏰 'Damlaya damlaya göl olur' sözü neyin önemini anlatır?", "Tutumluluk ve birikim", "Hızlı koşmak", "Yağmur yağması", "Yüzme öğrenmek"},
                    {"🏰 'Cesur' kelimesinin zıt anlamlısı hangisidir?", "Korkak", "Güçlü", "Kahraman", "Atılgan"},
                    {"🏰 'Okul' kelimesinin eş anlamlısı hangisidir?", "Mektep", "Sınıf", "Kütüphane", "Bahçe"},
                    {"🏰 'Kulak misafiri olmak' deyiminin anlamı nedir?", "Konuşulanları istemeden duymak", "Eve misafir çağırmak", "Müzik dinlemek", "Yüksek sesle konuşmak"}
            };
            String[] pick = bank[random.nextInt(bank.length)];
            return new QuestionItem(pick[0], pick[1], pick[2], pick[3], pick[4]);
        } else if (gameCategory == 2) {
            // Çılgın Laboratuvar Keşfi
            String[][] bank = new String[][]{
                    {"🧪 Dünyamızın ısı ve ışık kaynağı olan yıldız hangisidir?", "Güneş", "Ay", "Mars", "Kutup Yıldızı"},
                    {"🧪 Suyun katı haline laboratuvarda ne ad verilir?", "Buz", "Buhar", "Sis", "Yağmur"},
                    {"🧪 Mıknatıs aşağıdaki maddelerden hangisini çeker?", "Demir çivi", "Tahta kalem", "Cam bardak", "Plastik silgi"},
                    {"🧪 Bitkilerin güneş ışığıyla besin ve oksijen üretmesine ne denir?", "Fotosentez", "Buharlaşma", "Erime", "Yoğuşma"},
                    {"🧪 Güneş sisteminde halkalarıyla ünlü gezegen hangisidir?", "Satürn", "Merkür", "Venüs", "Mars"},
                    {"🧪 Ses aşağıdaki ortamlardan hangisinde yayılmaz?", "Boşlukta (Uzayda)", "Havada", "Suda", "Katı maddelerde"}
            };
            String[] pick = bank[random.nextInt(bank.length)];
            return new QuestionItem(pick[0], pick[1], pick[2], pick[3], pick[4]);
        } else if (gameCategory == 3) {
            // Zaman Gezgini Hazine Avı
            String[][] bank = new String[][]{
                    {"🧭 Pusulanın renkli ucu her zaman hangi yönü gösterir?", "Kuzey", "Güney", "Doğu", "Batı"},
                    {"🧭 Güneş her sabah hangi yönden doğar?", "Doğu", "Batı", "Kuzey", "Güney"},
                    {"🧭 Türkiye'nin başkenti hangi şehrimizdir?", "Ankara", "İstanbul", "İzmir", "Bursa"},
                    {"🧭 Haritalarda denizler ve okyanuslar hangi renkle gösterilir?", "Mavi", "Kahverengi", "Yeşil", "Sarı"},
                    {"🧭 Cumhuriyetimiz hangi tarihte ilan edilmiştir?", "29 Ekim 1923", "23 Nisan 1920", "19 Mayıs 1919", "30 Ağustos 1922"}
            };
            String[] pick = bank[random.nextInt(bank.length)];
            return new QuestionItem(pick[0], pick[1], pick[2], pick[3], pick[4]);
        } else if (gameCategory == 4) {
            // Sihirli Köprü Parolası
            String[][] bank = new String[][]{
                    {"🌍 'Apple' kelimesinin Türkçe karşılığı nedir?", "Elma", "Armut", "Çilek", "Portakal"},
                    {"🌍 'Book' kelimesinin Türkçe karşılığı nedir?", "Kitap", "Defter", "Kalem", "Masa"},
                    {"🌍 'Blue' hangi rengin İngilizcesidir?", "Mavi", "Kırmızı", "Yeşil", "Sarı"},
                    {"🌍 'Good Morning' ifadesi ne zaman söylenir?", "Sabah (Günaydın)", "Gece (İyi geceler)", "Vedalaşırken", "Teşekkür ederken"},
                    {"🌍 'Cat' ve 'Dog' kelimeleri sırasıyla hangi hayvanlardır?", "Kedi ve Köpek", "Kuş ve Balık", "At ve Aslan", "Tavşan ve Kaplumbağa"},
                    {"🌍 'School' kelimesinin anlamı nedir?", "Okul", "Hastane", "Kütüphane", "Park"}
            };
            String[] pick = bank[random.nextInt(bank.length)];
            return new QuestionItem(pick[0], pick[1], pick[2], pick[3], pick[4]);
        } else if (gameCategory == 5) {
            // İyilik Bahçesi Yıldızları
            String[][] bank = new String[][]{
                    {"🕊️ Bir arkadaşımız bize yardım ettiğinde hangi nezaket sözünü söyleriz?", "Teşekkür ederim", "Acele et", "Bana ne", "Sonra görüşürüz"},
                    {"🕊️ İyilik Bahçesinde en parlak yıldız hangi davranışla kazanılır?", "Dürüst ve yardımsever olmak", "Eşyaları dağınık bırakmak", "Sıraya girmemek", "Arkadaşları küstürmek"},
                    {"🕊️ Verdiğimiz sözü tutmak bize ne kazandırır?", "Güven ve saygı", "Yorgunluk", "Zaman kaybı", "Unutkanlık"},
                    {"🕊️ Büyüklerimizi ve küçüklerimizi koruyup sevgi göstermek hangi değerimizdir?", "Merhamet ve saygı", "Kıskançlık", "İnatçılık", "Acelecilik"}
            };
            String[] pick = bank[random.nextInt(bank.length)];
            return new QuestionItem(pick[0], pick[1], pick[2], pick[3], pick[4]);
        } else if (gameCategory == 6) {
            // Süper Kaptan Güvenli Rota
            String[][] bank = new String[][]{
                    {"🏎️ Yaya geçidinde yayalar için hangi ışık yandığında karşıya geçilir?", "Yeşil ışık", "Kırmızı ışık", "Sarı ışık", "Mavi ışık"},
                    {"🏎️ Otomobile bindiğimizde güvenliğimiz için ilk ne yapmalıyız?", "Emniyet kemerini takmalıyız", "Camı açmalıyız", "Radyoyu açmalıyız", "Ayakta durmalıyız"},
                    {"🏎️ Bisiklet ve kaykay sürerken başımızı korumak için ne takmalıyız?", "Kask", "Şapka", "Gözlük", "Atkı"},
                    {"🏎️ Türkiye'de tüm acil durumlar için aranan tek ortak acil numara kaçtır?", "112", "182", "153", "114"}
            };
            String[] pick = bank[random.nextInt(bank.length)];
            return new QuestionItem(pick[0], pick[1], pick[2], pick[3], pick[4]);
        } else {
            // Barış Adası Kahramanları
            String[][] bank = new String[][]{
                    {"⚖️ Her çocuğun eğitim alma, oyun oynama ve sağlık hizmetinden yararlanma hakkına ne denir?", "Çocuk Hakları", "Yarışma Kuralları", "Alışveriş Listesi", "Nöbet Çizelgesi"},
                    {"⚖️ Ortak kullanım alanlarını ve doğayı temiz tutmak kimin görevidir?", "Hepimizin ortak sorumluluğudur", "Sadece yetişkinlerin", "Hiç kimsenin", "Sadece turistlerin"},
                    {"⚖️ Grup çalışmalarında başarıya ulaşmanın en güzel yolu nedir?", "İş birliği ve adaletli paylaşım", "Tek başına karar vermek", "Başkalarını dinlememek", "Çalışmayı yarıda bırakmak"},
                    {"⚖️ İnsanların düşüncelerine saygı duymak Barış Adasında neyi güçlendirir?", "Hoşgörü ve dostluğu", "Kavgayı", "Gürültüyü", "Yalnızlığı"}
            };
            String[] pick = bank[random.nextInt(bank.length)];
            return new QuestionItem(pick[0], pick[1], pick[2], pick[3], pick[4]);
        }
    }
}
