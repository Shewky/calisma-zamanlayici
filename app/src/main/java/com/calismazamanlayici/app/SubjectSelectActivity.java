package com.calismazamanlayici.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class SubjectSelectActivity extends Activity {

    private String selectedMode = StudyDatabaseHelper.MODE_HOMEWORK;
    private ScrollView scrollRootSubject;
    private LinearLayout layoutSubjectHeaderCard;
    private TextView tvCarriedTimeNotice;
    private Button btnResetCycleMemory;

    // Türkçe, Matematik, Fen Bilimleri, Sosyal Bilgiler, İngilizce, Din Kültürü, Trafik Güvenliği, İnsan Hakları
    private static final String[] SUBJECT_EMOJIS = new String[]{
            "📕", "🔢", "🔬", "🌍", "🇬🇧", "🕌", "🚦", "⚖️"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subject_select);

        if (getIntent() != null && getIntent().getStringExtra(TimerForegroundService.EXTRA_MODE) != null) {
            selectedMode = getIntent().getStringExtra(TimerForegroundService.EXTRA_MODE);
        }

        scrollRootSubject = findViewById(R.id.scrollRootSubject);
        layoutSubjectHeaderCard = findViewById(R.id.layoutSubjectHeaderCard);
        TextView tvHeaderTitle = findViewById(R.id.tvSubjectHeaderTitle);
        TextView tvSubtitle = findViewById(R.id.tvSubjectModeSubtitle);
        tvCarriedTimeNotice = findViewById(R.id.tvCarriedTimeNotice);
        btnResetCycleMemory = findViewById(R.id.btnResetCycleMemory);
        LinearLayout layoutButtons = findViewById(R.id.layoutSubjectButtons);
        Button btnBack = findViewById(R.id.btnBackToMainFromSubjects);

        ThemeHelper.Palette palette = ThemeHelper.getPalette(this);
        ThemeHelper.applyWindowAndSurface(this, scrollRootSubject);
        ThemeHelper.styleHeader(this, layoutSubjectHeaderCard);
        btnResetCycleMemory.setBackground(ThemeHelper.createButtonDrawable(this, palette.accent));

        String userName = StudyDatabaseHelper.getUserName(this);
        if (StudyDatabaseHelper.MODE_TEST.equals(selectedMode)) {
            tvHeaderTitle.setText("📝 " + userName + ", Hangi Dersin Testini Çözüyoruz?");
            tvSubtitle.setText("4. Sınıf Test Çözümü Ders Seçimi");
        } else {
            tvHeaderTitle.setText("📚 " + userName + ", Hangi Dersin Ödevine Başlıyoruz?");
            tvSubtitle.setText("4. Sınıf Ödev Ders Seçimi");
        }

        int marginPx = (int) (12 * getResources().getDisplayMetrics().density);
        int heightPx = (int) (60 * getResources().getDisplayMetrics().density);
        int[] btnColors = new int[]{palette.primary, palette.secondary, palette.accent, palette.primaryDark};

        for (int i = 0; i < StudyDatabaseHelper.FOURTH_GRADE_SUBJECTS.length; i++) {
            final String subject = StudyDatabaseHelper.FOURTH_GRADE_SUBJECTS[i];
            String emoji = SUBJECT_EMOJIS[i % SUBJECT_EMOJIS.length];

            Button btn = new Button(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, heightPx);
            lp.setMargins(0, 0, 0, marginPx);
            btn.setLayoutParams(lp);
            btn.setBackground(ThemeHelper.createButtonDrawable(this, btnColors[i % btnColors.length]));
            btn.setText(emoji + "  " + subject);
            btn.setAllCaps(false);
            btn.setTextColor(0xFFFFFFFF);
            btn.setTextSize(18f);

            btn.setOnClickListener(v -> onSubjectClicked(subject));
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
        String userName = StudyDatabaseHelper.getUserName(this);
        int carriedSeconds = StudyDatabaseHelper.getCarriedCycleSeconds(this);
        int remainingFor40 = StudyDatabaseHelper.FULL_STUDY_CYCLE_SECONDS - carriedSeconds;

        if (carriedSeconds > 0) {
            tvCarriedTimeNotice.setText("💡 Önceki derste moladan sonra "
                    + StudyDatabaseHelper.formatDurationTr(carriedSeconds)
                    + " çalıştın " + userName + "!\nYeni derste Başlat'a tıkladığında 40 dakikadan otomatik düşülecek ve "
                    + StudyDatabaseHelper.formatDurationTr(remainingFor40)
                    + " sonra mola zamanı gelecek.");
            btnResetCycleMemory.setVisibility(View.VISIBLE);
        } else {
            tvCarriedTimeNotice.setText("⏱️ Derse tıkladığında seçilen dersin adı sesli anons edilir. Ardından Başlat butonuna tıkladığında 'Çalışmaya başla' anonsuyla 40 dakikalık süre başlar.");
            btnResetCycleMemory.setVisibility(View.GONE);
        }
    }

    /**
     * Derse tıklar tıklamaz süreyi otomatik başlatmaz!
     * Dersin ismini sesli anons eder ve Başlat butonunun bulunduğu sayaç ekranını açar.
     */
    private void onSubjectClicked(String subject) {
        Intent serviceIntent = new Intent(this, TimerForegroundService.class);
        serviceIntent.setAction(TimerForegroundService.ACTION_PREPARE_SESSION);
        serviceIntent.putExtra(TimerForegroundService.EXTRA_MODE, selectedMode);
        serviceIntent.putExtra(TimerForegroundService.EXTRA_SUBJECT, subject);
        startService(serviceIntent);

        Intent timerUiIntent = new Intent(this, TimerActivity.class);
        timerUiIntent.putExtra(TimerForegroundService.EXTRA_MODE, selectedMode);
        timerUiIntent.putExtra(TimerForegroundService.EXTRA_SUBJECT, subject);
        startActivity(timerUiIntent);
    }
}
