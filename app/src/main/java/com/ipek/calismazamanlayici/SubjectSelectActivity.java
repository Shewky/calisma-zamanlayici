package com.ipek.calismazamanlayici;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class SubjectSelectActivity extends Activity {

    private String selectedMode = StudyDatabaseHelper.MODE_HOMEWORK;
    private TextView tvCarriedTimeNotice;
    private Button btnResetCycleMemory;

    private static final String[] SUBJECT_EMOJIS = new String[]{
            "📕", "🔢", "🌍", "🇬🇧", "🕌", "🚦", "⚖️"
    };

    private static final int[] BUTTON_DRAWABLES = new int[]{
            R.drawable.btn_pink_rounded,
            R.drawable.btn_purple_rounded,
            R.drawable.btn_mint_rounded,
            R.drawable.btn_orange_rounded,
            R.drawable.btn_pink_rounded,
            R.drawable.btn_purple_rounded,
            R.drawable.btn_mint_rounded
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subject_select);

        if (getIntent() != null && getIntent().getStringExtra(TimerForegroundService.EXTRA_MODE) != null) {
            selectedMode = getIntent().getStringExtra(TimerForegroundService.EXTRA_MODE);
        }

        TextView tvHeaderTitle = findViewById(R.id.tvSubjectHeaderTitle);
        TextView tvSubtitle = findViewById(R.id.tvSubjectModeSubtitle);
        tvCarriedTimeNotice = findViewById(R.id.tvCarriedTimeNotice);
        btnResetCycleMemory = findViewById(R.id.btnResetCycleMemory);
        LinearLayout layoutButtons = findViewById(R.id.layoutSubjectButtons);
        Button btnBack = findViewById(R.id.btnBackToMainFromSubjects);

        if (StudyDatabaseHelper.MODE_TEST.equals(selectedMode)) {
            tvHeaderTitle.setText("📝 İpek, Hangi Dersin Testini Çözüyoruz?");
            tvSubtitle.setText("4. Sınıf Test Çözümü Ders Seçimi");
        } else {
            tvHeaderTitle.setText("📚 İpek, Hangi Dersin Ödevine Başlıyoruz?");
            tvSubtitle.setText("4. Sınıf Ödev Ders Seçimi");
        }

        int marginPx = (int) (12 * getResources().getDisplayMetrics().density);
        int heightPx = (int) (62 * getResources().getDisplayMetrics().density);

        for (int i = 0; i < StudyDatabaseHelper.FOURTH_GRADE_SUBJECTS.length; i++) {
            final String subject = StudyDatabaseHelper.FOURTH_GRADE_SUBJECTS[i];
            String emoji = SUBJECT_EMOJIS[i % SUBJECT_EMOJIS.length];

            Button btn = new Button(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, heightPx);
            lp.setMargins(0, 0, 0, marginPx);
            btn.setLayoutParams(lp);
            btn.setBackgroundResource(BUTTON_DRAWABLES[i % BUTTON_DRAWABLES.length]);
            btn.setText(emoji + "  " + subject);
            btn.setAllCaps(false);
            btn.setTextColor(0xFFFFFFFF);
            btn.setTextSize(18f);

            btn.setOnClickListener(v -> startSelectedSubject(subject));
            layoutButtons.addView(btn);
        }

        btnResetCycleMemory.setOnClickListener(v -> {
            StudyDatabaseHelper.resetCarriedCycleSeconds(SubjectSelectActivity.this);
            updateCarriedNotice();
            Toast.makeText(SubjectSelectActivity.this, "Çalışma döngüsü tam 40 dakikaya sıfırlandı!", Toast.LENGTH_SHORT).show();
        });

        btnBack.setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateCarriedNotice();
    }

    private void updateCarriedNotice() {
        int carriedSeconds = StudyDatabaseHelper.getCarriedCycleSeconds(this);
        int remainingFor40 = StudyDatabaseHelper.FULL_STUDY_CYCLE_SECONDS - carriedSeconds;

        if (carriedSeconds > 0) {
            tvCarriedTimeNotice.setText("💡 Önceki derste moladan sonra "
                    + StudyDatabaseHelper.formatDurationTr(carriedSeconds)
                    + " çalıştın İpek!\nYeni derse başladığında 40 dakikadan otomatik düşülecek ve "
                    + StudyDatabaseHelper.formatDurationTr(remainingFor40)
                    + " sonra mola zamanı gelecek.");
            btnResetCycleMemory.setVisibility(View.VISIBLE);
        } else {
            tvCarriedTimeNotice.setText("⏱️ Ders seçimi yaptığında 40 dakikalık çalışma süresi aktif olacak ve 'Çalışmaya başla' anonsu yapılacak.");
            btnResetCycleMemory.setVisibility(View.GONE);
        }
    }

    private void startSelectedSubject(String subject) {
        Intent serviceIntent = new Intent(this, TimerForegroundService.class);
        serviceIntent.setAction(TimerForegroundService.ACTION_START_SESSION);
        serviceIntent.putExtra(TimerForegroundService.EXTRA_MODE, selectedMode);
        serviceIntent.putExtra(TimerForegroundService.EXTRA_SUBJECT, subject);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        Intent timerUiIntent = new Intent(this, TimerActivity.class);
        timerUiIntent.putExtra(TimerForegroundService.EXTRA_MODE, selectedMode);
        timerUiIntent.putExtra(TimerForegroundService.EXTRA_SUBJECT, subject);
        startActivity(timerUiIntent);
    }
}
