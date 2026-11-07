package com.calismazamanlayici.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class RewardsActivity extends Activity {

    private StudyDatabaseHelper dbHelper;
    private TextView tvWeeklySummaryStats;
    private LinearLayout layoutBadgesContainer;
    private boolean togglePraise = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rewards);

        dbHelper = new StudyDatabaseHelper(this);
        ScrollView scrollRootRewards = findViewById(R.id.scrollRootRewards);
        LinearLayout layoutRewardsHeaderCard = findViewById(R.id.layoutRewardsHeaderCard);
        TextView tvRewardsHeaderTitle = findViewById(R.id.tvRewardsHeaderTitle);
        tvWeeklySummaryStats = findViewById(R.id.tvWeeklySummaryStats);
        layoutBadgesContainer = findViewById(R.id.layoutBadgesContainer);
        Button btnPlayPraiseVoice = findViewById(R.id.btnPlayPraiseVoice);
        Button btnBack = findViewById(R.id.btnBackFromRewards);

        ThemeHelper.applyWindowAndSurface(this, scrollRootRewards);
        ThemeHelper.styleHeader(this, layoutRewardsHeaderCard);
        ThemeHelper.styleAccentButton(this, btnPlayPraiseVoice);
        ThemeHelper.stylePrimaryButton(this, btnBack);

        final String userName = StudyDatabaseHelper.getUserName(this);
        tvRewardsHeaderTitle.setText("🏆 " + userName + " • Başarı Ödülleri");
        btnPlayPraiseVoice.setText("🎉 Başarı Anonsunu Dinle (Aferin " + userName + "!)");

        StudyDatabaseHelper.WeeklyStats stats = dbHelper.getWeeklyStats();
        renderBadges(userName, stats);

        if (stats.getTotalSessionsCount() > 0) {
            triggerPraiseSpeech("Aferin " + userName + ", çalışmaya devam et");
        }

        btnPlayPraiseVoice.setOnClickListener(v -> {
            togglePraise = !togglePraise;
            if (togglePraise) {
                triggerPraiseSpeech("Aferin " + userName + ", çalışmaya devam et");
            } else {
                triggerPraiseSpeech("Aferin " + userName + ", nice başarılı günlere");
            }
        });

        btnBack.setOnClickListener(v -> finish());
    }

    private void triggerPraiseSpeech(String text) {
        Intent i = new Intent(this, TimerForegroundService.class);
        i.setAction(TimerForegroundService.ACTION_SPEAK_CUSTOM);
        i.putExtra(TimerForegroundService.EXTRA_CUSTOM_SPEECH, text);
        startService(i);
    }

    private void renderBadges(String userName, StudyDatabaseHelper.WeeklyStats stats) {
        tvWeeklySummaryStats.setText("Son 7 Günde: " + stats.totalHomeworkCount + " Ödev, "
                + stats.totalTestCount + " Test, "
                + stats.totalBookCount + " Kitap Okuma ("
                + StudyDatabaseHelper.formatDurationTr(stats.getTotalSeconds()) + ")");

        layoutBadgesContainer.removeAllViews();

        addBadgeCard(
                "🌟 İlk Adım Yıldızı",
                "İlk ödev, test veya kitap okuma çalışmasını başarıyla tamamlama ödülü!",
                stats.getTotalSessionsCount() >= 1
        );

        addBadgeCard(
                "🐝 Çalışkan Arı Ödülü",
                "Haftada en az 3 farklı çalışmayı bitirme ödülü! Aferin " + userName + "!",
                stats.getTotalSessionsCount() >= 3
        );

        addBadgeCard(
                "📖 Kitap Kurdu Madalyası",
                "Kitap Oku özelliğiyle kitap okuma alışkanlığını sürdürme ödülü!",
                stats.totalBookCount >= 1
        );

        addBadgeCard(
                "📚 4. Sınıf Ödev Kahramanı",
                "Haftalık toplam 40 dakika ve üzeri ödev çalışma ödülü!",
                stats.totalHomeworkSeconds >= 40 * 60
        );

        addBadgeCard(
                "🎯 Süper Test Ustası",
                "Haftada en az 2 dersin test çözümünü başarıyla tamamlama ödülü!",
                stats.totalTestCount >= 2
        );

        addBadgeCard(
                "👑 Haftanın Şampiyon Öğrencisi",
                "Haftada en az 4 farklı derste çalışma yapıp başarıya ulaşma kupası! 'Aferin nice başarılı günlere!'",
                stats.distinctSubjectsCount >= 4 || stats.getTotalSeconds() >= 120 * 60
        );
    }

    private void addBadgeCard(String title, String desc, boolean unlocked) {
        ThemeHelper.Palette p = ThemeHelper.getPalette(this);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_white_card);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        int margin = (int) (12 * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(0, 0, 0, margin);
        card.setLayoutParams(lp);
        card.setPadding(pad, pad, pad, pad);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title + (unlocked ? "  ✅ KAZANILDI!" : "  🔒 (Hedef)"));
        tvTitle.setTextSize(17f);
        tvTitle.setTextColor(unlocked ? p.primaryDark : 0xFF4B5563);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView tvDesc = new TextView(this);
        tvDesc.setText(desc);
        tvDesc.setTextSize(14f);
        tvDesc.setTextColor(0xFF1F2937);
        tvDesc.setPadding(0, pad / 3, 0, 0);

        card.addView(tvTitle);
        card.addView(tvDesc);
        layoutBadgesContainer.addView(card);
    }
}
