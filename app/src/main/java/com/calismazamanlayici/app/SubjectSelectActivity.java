package com.calismazamanlayici.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class SubjectSelectActivity extends Activity {

    private String selectedMode = StudyDatabaseHelper.MODE_HOMEWORK;
    private ScrollView scrollRootSubject;
    private LinearLayout layoutSubjectHeaderCard;
    private LinearLayout layoutSubjectButtons;
    private TextView tvCarriedTimeNotice;
    private TextView tvSubjectModeSubtitle;
    private Button btnResetCycleMemory;
    private Button btnManageCustomSubjects;

    private static final String[] SUBJECT_EMOJIS = new String[]{
            "📕", "🔢", "🔬", "🌍", "🇬🇧", "🕌", "🚦", "⚖️", "🎨", "🎵", "💻", "🏃"
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
        tvSubjectModeSubtitle = findViewById(R.id.tvSubjectModeSubtitle);
        tvCarriedTimeNotice = findViewById(R.id.tvCarriedTimeNotice);
        btnResetCycleMemory = findViewById(R.id.btnResetCycleMemory);
        btnManageCustomSubjects = findViewById(R.id.btnManageCustomSubjects);
        layoutSubjectButtons = findViewById(R.id.layoutSubjectButtons);
        Button btnBack = findViewById(R.id.btnBackToMainFromSubjects);

        ThemeHelper.Palette palette = ThemeHelper.getPalette(this);
        ThemeHelper.applyWindowAndSurface(this, scrollRootSubject);
        ThemeHelper.styleHeader(this, layoutSubjectHeaderCard);
        btnResetCycleMemory.setBackground(ThemeHelper.createButtonDrawable(this, palette.accent));
        btnManageCustomSubjects.setBackground(ThemeHelper.createButtonDrawable(this, palette.primaryDark));

        String userName = StudyDatabaseHelper.getUserName(this);
        int grade = StudyDatabaseHelper.getGradeLevel(this);

        if (StudyDatabaseHelper.MODE_TEST.equals(selectedMode)) {
            tvHeaderTitle.setText("📝 " + userName + ", Hangi Dersin Testini Çözüyoruz?");
            tvSubjectModeSubtitle.setText(grade + ". Sınıf Test Çözümü Ders Seçimi");
        } else {
            tvHeaderTitle.setText("📚 " + userName + ", Hangi Dersin Ödevine Başlıyoruz?");
            tvSubjectModeSubtitle.setText(grade + ". Sınıf Ödev Ders Seçimi");
        }

        btnManageCustomSubjects.setOnClickListener(v -> showManageSubjectsDialog());

        btnResetCycleMemory.setOnClickListener(v -> {
            StudyDatabaseHelper.resetCarriedCycleSeconds(SubjectSelectActivity.this);
            updateCarriedNotice();
            Toast.makeText(SubjectSelectActivity.this, "Çalışma döngüsü tam 40 dakikaya sıfırlandı!", Toast.LENGTH_SHORT).show();
        });

        btnBack.setOnClickListener(v -> finish());

        renderSubjectButtons();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateCarriedNotice();
        renderSubjectButtons();
    }

    private void renderSubjectButtons() {
        layoutSubjectButtons.removeAllViews();
        ThemeHelper.Palette palette = ThemeHelper.getPalette(this);
        List<String> subjects = StudyDatabaseHelper.getSubjectsForCurrentGrade(this);
        int grade = StudyDatabaseHelper.getGradeLevel(this);

        int marginPx = (int) (12 * getResources().getDisplayMetrics().density);
        int heightPx = (int) (60 * getResources().getDisplayMetrics().density);
        int[] btnColors = new int[]{palette.primary, palette.secondary, palette.accent, palette.primaryDark};

        if (subjects.isEmpty()) {
            TextView tvEmpty = new TextView(this);
            tvEmpty.setText("📌 " + grade + ". Sınıf için henüz ders eklenmemiş.\nYukarıdaki '➕ Yeni Ders Yaz / Dersleri Düzenle' butonuna tıklayarak derslerini hemen yazıp ekleyebilirsin!");
            tvEmpty.setTextSize(15f);
            tvEmpty.setTextColor(0xFF1F2937);
            int pad = (int) (14 * getResources().getDisplayMetrics().density);
            tvEmpty.setPadding(pad, pad, pad, pad);
            layoutSubjectButtons.addView(tvEmpty);
            return;
        }

        for (int i = 0; i < subjects.size(); i++) {
            final String subject = subjects.get(i);
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
            layoutSubjectButtons.addView(btn);
        }
    }

    /**
     * Kullanıcının kendi ders seçeneklerini yazıp ekleyebildiği veya silebildiği pencere.
     */
    private void showManageSubjectsDialog() {
        final int grade = StudyDatabaseHelper.getGradeLevel(this);
        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(pad, pad / 2, pad, pad / 2);

        TextView tvAddInfo = new TextView(this);
        tvAddInfo.setText(grade + ". Sınıf için yeni ders ismi yazın:");
        tvAddInfo.setTextSize(15f);
        tvAddInfo.setTextColor(0xFF1F2937);
        container.addView(tvAddInfo);

        final EditText etNewSubject = new EditText(this);
        etNewSubject.setHint("Örn: Fen Bilimleri, Görsel Sanatlar...");
        container.addView(etNewSubject);

        new AlertDialog.Builder(this)
                .setTitle("📚 " + grade + ". Sınıf Derslerini Yönet")
                .setView(container)
                .setPositiveButton("➕ Dersi Ekle", (dialog, which) -> {
                    String entered = etNewSubject.getText().toString().trim();
                    if (entered.isEmpty()) {
                        Toast.makeText(SubjectSelectActivity.this, "Lütfen ders adını yazın.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    StudyDatabaseHelper.addSubjectToCurrentGrade(SubjectSelectActivity.this, entered);
                    renderSubjectButtons();
                    Toast.makeText(SubjectSelectActivity.this, "✅ '" + entered + "' dersi eklendi!", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("🗑️ Ders Sil", (dialog, which) -> showDeleteSubjectDialog())
                .setNegativeButton("Kapat", null)
                .show();
    }

    private void showDeleteSubjectDialog() {
        final List<String> subjects = StudyDatabaseHelper.getSubjectsForCurrentGrade(this);
        if (subjects.isEmpty()) {
            Toast.makeText(this, "Silinecek ders bulunmuyor.", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] items = subjects.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("Silmek İstediğiniz Derse Tıklayın")
                .setItems(items, (dialog, which) -> {
                    String removed = subjects.get(which);
                    StudyDatabaseHelper.removeSubjectFromCurrentGrade(SubjectSelectActivity.this, which);
                    renderSubjectButtons();
                    Toast.makeText(SubjectSelectActivity.this, "🗑️ '" + removed + "' silindi.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("İptal", null)
                .show();
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
