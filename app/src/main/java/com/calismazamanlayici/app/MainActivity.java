package com.calismazamanlayici.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private ScrollView scrollRootMain;
    private LinearLayout layoutOnboardingContainer;
    private LinearLayout layoutDashboardContainer;
    private LinearLayout layoutMainHeaderCard;

    private EditText etOnboardName;
    private Spinner spinnerOnboardGrade;
    private RadioButton rbOnboardGirl;
    private RadioButton rbOnboardBoy;
    private Button btnCompleteOnboarding;

    private TextView tvWelcomeUser;
    private TextView tvWelcomeSub;
    private TextView tvTodaySummaryBanner;
    private TextView tvCycleMemoryInfo;
    private TextView tvParentConnectionStatus;
    private TextView tvAppVersionInfo;

    private Button btnOpenSettings;
    private Button btnResumeActiveTimer;
    private Button btnStartHomework;
    private Button btnStartTest;
    private Button btnStartBookRead;
    private Button btnOpenCalendar;
    private Button btnOpenRewards;
    private Button btnCallAnne;
    private Button btnCallBaba;
    private Button btnOpenParentQr;
    private Button btnTestVoice;
    private Button btnCheckUpdate;

    private TimerForegroundService timerService;
    private boolean serviceBound = false;

    private final ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            TimerForegroundService.LocalBinder binder = (TimerForegroundService.LocalBinder) service;
            timerService = binder.getService();
            serviceBound = true;
            refreshDashboardUi();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
            timerService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        scrollRootMain = findViewById(R.id.scrollRootMain);
        layoutOnboardingContainer = findViewById(R.id.layoutOnboardingContainer);
        layoutDashboardContainer = findViewById(R.id.layoutDashboardContainer);
        layoutMainHeaderCard = findViewById(R.id.layoutMainHeaderCard);

        etOnboardName = findViewById(R.id.etOnboardName);
        spinnerOnboardGrade = findViewById(R.id.spinnerOnboardGrade);
        rbOnboardGirl = findViewById(R.id.rbOnboardGirl);
        rbOnboardBoy = findViewById(R.id.rbOnboardBoy);
        btnCompleteOnboarding = findViewById(R.id.btnCompleteOnboarding);

        tvWelcomeUser = findViewById(R.id.tvWelcomeUser);
        tvWelcomeSub = findViewById(R.id.tvWelcomeSub);
        tvTodaySummaryBanner = findViewById(R.id.tvTodaySummaryBanner);
        tvCycleMemoryInfo = findViewById(R.id.tvCycleMemoryInfo);
        tvParentConnectionStatus = findViewById(R.id.tvParentConnectionStatus);
        tvAppVersionInfo = findViewById(R.id.tvAppVersionInfo);

        btnOpenSettings = findViewById(R.id.btnOpenSettings);
        btnResumeActiveTimer = findViewById(R.id.btnResumeActiveTimer);
        btnStartHomework = findViewById(R.id.btnStartHomework);
        btnStartTest = findViewById(R.id.btnStartTest);
        btnStartBookRead = findViewById(R.id.btnStartBookRead);
        btnOpenCalendar = findViewById(R.id.btnOpenCalendar);
        btnOpenRewards = findViewById(R.id.btnOpenRewards);
        btnCallAnne = findViewById(R.id.btnCallAnne);
        btnCallBaba = findViewById(R.id.btnCallBaba);
        btnOpenParentQr = findViewById(R.id.btnOpenParentQr);
        btnTestVoice = findViewById(R.id.btnTestVoice);
        btnCheckUpdate = findViewById(R.id.btnCheckUpdate);

        // 1. Sınıf .. 8. Sınıf Seçenekleri (Varsayılan: 4. Sınıf)
        ArrayAdapter<String> gradeAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                StudyDatabaseHelper.GRADE_LABELS
        );
        spinnerOnboardGrade.setAdapter(gradeAdapter);
        spinnerOnboardGrade.setSelection(3); // 4. Sınıf varsayılan seçili

        tvAppVersionInfo.setText("Çalışma Zamanlayıcı v" + BuildConfig.VERSION_NAME
                + " (Kod: " + BuildConfig.VERSION_CODE + ") • Android 6+ Uyumlu");

        btnCompleteOnboarding.setOnClickListener(v -> {
            String enteredName = etOnboardName.getText().toString().trim();
            if (enteredName.isEmpty()) {
                Toast.makeText(MainActivity.this, "Lütfen önce ismini yaz 😊", Toast.LENGTH_SHORT).show();
                return;
            }
            int selectedGrade = spinnerOnboardGrade.getSelectedItemPosition() + 1;
            boolean isGirl = rbOnboardGirl.isChecked();
            StudyDatabaseHelper.saveOnboardingProfile(MainActivity.this, enteredName, selectedGrade, isGirl);

            applyCurrentTheme();
            refreshDashboardUi();

            String welcomeSpeech = StudyDatabaseHelper.buildRandomWelcomeAnnouncement(enteredName);
            speakTextViaService(welcomeSpeech);
            Toast.makeText(MainActivity.this, "🎉 " + welcomeSpeech, Toast.LENGTH_LONG).show();
        });

        btnOpenSettings.setOnClickListener(v -> showAppSettingsDialog());

        btnStartHomework.setOnClickListener(v -> {
            Intent i = new Intent(MainActivity.this, SubjectSelectActivity.class);
            i.putExtra(TimerForegroundService.EXTRA_MODE, StudyDatabaseHelper.MODE_HOMEWORK);
            startActivity(i);
        });

        btnStartTest.setOnClickListener(v -> {
            Intent i = new Intent(MainActivity.this, SubjectSelectActivity.class);
            i.putExtra(TimerForegroundService.EXTRA_MODE, StudyDatabaseHelper.MODE_TEST);
            startActivity(i);
        });

        btnStartBookRead.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, BookReadActivity.class));
        });

        btnOpenCalendar.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, CalendarActivity.class));
        });

        btnOpenRewards.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, RewardsActivity.class));
        });

        btnResumeActiveTimer.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, TimerActivity.class));
        });

        btnOpenParentQr.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, ParentPairQrActivity.class));
        });

        btnCallAnne.setOnClickListener(v -> {
            startOutgoingCallToParent("ANNE");
        });

        btnCallBaba.setOnClickListener(v -> {
            startOutgoingCallToParent("BABA");
        });

        btnTestVoice.setOnClickListener(v -> {
            String userName = StudyDatabaseHelper.getUserName(MainActivity.this);
            speakTextViaService("Merhaba " + userName + "! Sesli uyarı sistemi hazır. Başarılar dilerim!");
            Toast.makeText(MainActivity.this, "🔊 Sesli anons testi çalınıyor...", Toast.LENGTH_SHORT).show();
        });

        btnCheckUpdate.setOnClickListener(v -> {
            GitHubUpdater.checkUpdates(MainActivity.this, true);
        });

        btnCheckUpdate.setOnLongClickListener(v -> {
            GitHubUpdater.showConfigureRepoDialog(MainActivity.this);
            return true;
        });

        // Servisi başlatarak LAN senkronizasyon sunucusunu aktif tut
        startService(new Intent(this, TimerForegroundService.class));

        requestNecessaryPermissions();
        applyCurrentTheme();
        GitHubUpdater.checkUpdates(this, false);
    }

    private void startOutgoingCallToParent(String role) {
        Intent callIntent = new Intent(this, CallActivity.class);
        callIntent.putExtra("EXTRA_PARENT_ROLE", role);
        callIntent.putExtra("EXTRA_DIRECT_CONNECTED", false);
        startActivity(callIntent);
    }

    private void speakTextViaService(String speech) {
        Intent voiceIntent = new Intent(this, TimerForegroundService.class);
        voiceIntent.setAction(TimerForegroundService.ACTION_SPEAK_CUSTOM);
        voiceIntent.putExtra(TimerForegroundService.EXTRA_CUSTOM_SPEECH, speech);
        startService(voiceIntent);
    }

    private void applyCurrentTheme() {
        ThemeHelper.Palette p = ThemeHelper.getPalette(this);
        ThemeHelper.applyWindowAndSurface(this, scrollRootMain);
        ThemeHelper.styleHeader(this, layoutMainHeaderCard);

        btnCompleteOnboarding.setBackground(ThemeHelper.createButtonDrawable(this, p.primary));
        btnStartHomework.setBackground(ThemeHelper.createButtonDrawable(this, p.primary));
        btnStartTest.setBackground(ThemeHelper.createButtonDrawable(this, p.secondary));
        btnStartBookRead.setBackground(ThemeHelper.createButtonDrawable(this, p.accent));

        btnOpenCalendar.setBackground(ThemeHelper.createButtonDrawable(this, p.secondary));
        btnOpenRewards.setBackground(ThemeHelper.createButtonDrawable(this, p.primaryDark));
        btnResumeActiveTimer.setBackground(ThemeHelper.createButtonDrawable(this, p.accent));

        btnCallAnne.setBackground(ThemeHelper.createButtonDrawable(this, p.primary));
        btnCallBaba.setBackground(ThemeHelper.createButtonDrawable(this, p.secondary));
        btnOpenParentQr.setBackground(ThemeHelper.createButtonDrawable(this, p.accent));

        btnTestVoice.setBackground(ThemeHelper.createButtonDrawable(this, p.primary));
        btnCheckUpdate.setBackground(ThemeHelper.createButtonDrawable(this, p.secondary));
    }

    /**
     * Sağ üstteki "⚙️ Ayarlar" butonuna tıklandığında açılan Uygulama Ayarları:
     * - Kullanıcı İsmi
     * - Sınıf Seçimi (1. Sınıf .. 8. Sınıf)
     * - Tema Rengi Seçimi
     */
    private void showAppSettingsDialog() {
        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(pad, pad / 2, pad, pad / 2);

        TextView tvNameLabel = new TextView(this);
        tvNameLabel.setText("👤 Kullanıcı İsmi:");
        tvNameLabel.setTextSize(15f);
        tvNameLabel.setTextColor(0xFF1F2937);
        container.addView(tvNameLabel);

        final EditText etUserName = new EditText(this);
        etUserName.setText(StudyDatabaseHelper.getUserName(this));
        container.addView(etUserName);

        TextView tvGradeLabel = new TextView(this);
        tvGradeLabel.setText("\n🎓 Sınıf Seçimi (1. - 8. Sınıf):");
        tvGradeLabel.setTextSize(15f);
        tvGradeLabel.setTextColor(0xFF1F2937);
        container.addView(tvGradeLabel);

        final Spinner spGrade = new Spinner(this);
        ArrayAdapter<String> gradeAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                StudyDatabaseHelper.GRADE_LABELS
        );
        spGrade.setAdapter(gradeAdapter);
        spGrade.setSelection(StudyDatabaseHelper.getGradeLevel(this) - 1);
        container.addView(spGrade);

        TextView tvThemeLabel = new TextView(this);
        tvThemeLabel.setText("\n🎨 Tema Rengi Seçimi:");
        tvThemeLabel.setTextSize(15f);
        tvThemeLabel.setTextColor(0xFF1F2937);
        container.addView(tvThemeLabel);

        final RadioGroup rgThemes = new RadioGroup(this);
        rgThemes.setOrientation(RadioGroup.VERTICAL);

        final String[] themeKeys = new String[]{
                ThemeHelper.THEME_BLUE,
                ThemeHelper.THEME_PINK,
                ThemeHelper.THEME_GREEN,
                ThemeHelper.THEME_PURPLE,
                ThemeHelper.THEME_ORANGE
        };
        final String[] themeTitles = new String[]{
                "🔵 Mavi Tonları (Erkek Varsayılanı)",
                "🌸 Pembe Tonları (Kız Varsayılanı)",
                "🟢 Yeşil Tonları (Doğa Yeşili)",
                "🟣 Mor Tonları (Galaksi Moru)",
                "🟠 Turuncu Tonları (Enerjik Mercan)"
        };

        String currentTheme = StudyDatabaseHelper.getThemeKey(this);
        for (int i = 0; i < themeKeys.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(2000 + i);
            rb.setText(themeTitles[i]);
            rb.setTextSize(14f);
            rgThemes.addView(rb);
            if (themeKeys[i].equals(currentTheme)) {
                rb.setChecked(true);
            }
        }
        container.addView(rgThemes);

        new AlertDialog.Builder(this)
                .setTitle("⚙️ Uygulama Ayarları")
                .setView(container)
                .setPositiveButton("Kaydet", (dialog, which) -> {
                    String newName = etUserName.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        StudyDatabaseHelper.setUserName(MainActivity.this, newName);
                    }
                    int chosenGrade = spGrade.getSelectedItemPosition() + 1;
                    StudyDatabaseHelper.setGradeLevel(MainActivity.this, chosenGrade);

                    int checkedId = rgThemes.getCheckedRadioButtonId();
                    int idx = checkedId - 2000;
                    if (idx >= 0 && idx < themeKeys.length) {
                        StudyDatabaseHelper.setThemeKey(MainActivity.this, themeKeys[idx]);
                    }
                    applyCurrentTheme();
                    refreshDashboardUi();
                    Toast.makeText(MainActivity.this, "✅ Ayarlar kaydedildi!", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("GitHub Depo Ayarı", (dialog, which) -> {
                    GitHubUpdater.showConfigureRepoDialog(MainActivity.this);
                })
                .setNegativeButton("İptal", null)
                .show();
    }

    private void requestNecessaryPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            List<String> perms = new ArrayList<>();
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.RECORD_AUDIO);
            }
            if (Build.VERSION.SDK_INT >= 33) {
                if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    perms.add(Manifest.permission.POST_NOTIFICATIONS);
                }
            }
            if (!perms.isEmpty()) {
                requestPermissions(perms.toArray(new String[0]), 101);
            }

            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                try {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception ignored) {
                }
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent bindIntent = new Intent(this, TimerForegroundService.class);
        bindService(bindIntent, conn, Context.BIND_AUTO_CREATE);
        applyCurrentTheme();
        refreshDashboardUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyCurrentTheme();
        refreshDashboardUi();
    }

    @Override
    protected void onStop() {
        if (serviceBound) {
            unbindService(conn);
            serviceBound = false;
        }
        super.onStop();
    }

    private void refreshDashboardUi() {
        if (!StudyDatabaseHelper.isUserOnboarded(this)) {
            layoutOnboardingContainer.setVisibility(View.VISIBLE);
            layoutDashboardContainer.setVisibility(View.GONE);
            return;
        }

        layoutOnboardingContainer.setVisibility(View.GONE);
        layoutDashboardContainer.setVisibility(View.VISIBLE);

        String userName = StudyDatabaseHelper.getUserName(this);
        int grade = StudyDatabaseHelper.getGradeLevel(this);
        tvWelcomeUser.setText("Merhaba " + userName + "! 🌟");
        tvWelcomeSub.setText(grade + ". Sınıf Başarı Yolculuğunda Bugün Harika İşler Yapacaksın!");

        StudyDatabaseHelper db = new StudyDatabaseHelper(this);
        int todaySecs = db.getTotalSecondsForDate(StudyDatabaseHelper.getTodayDateString());
        int todayCount = db.getSessionsForDate(StudyDatabaseHelper.getTodayDateString()).size();

        if (todayCount == 0) {
            tvTodaySummaryBanner.setText("🎯 Bugün henüz çalışma kaydı yok. Hadi başlayalım " + userName + "!");
        } else {
            tvTodaySummaryBanner.setText("🌟 Harikasın " + userName + "! Bugün " + todayCount
                    + " çalışma/okuma tamamladın (" + StudyDatabaseHelper.formatDurationTr(todaySecs) + ")");
        }

        int carriedSecs = StudyDatabaseHelper.getCarriedCycleSeconds(this);
        int nextRemaining = StudyDatabaseHelper.FULL_STUDY_CYCLE_SECONDS - carriedSecs;

        if (carriedSecs > 0) {
            tvCycleMemoryInfo.setText("⏱️ Önceki dersten hafızada tutulan süre: "
                    + StudyDatabaseHelper.formatDurationTr(carriedSecs)
                    + "\n➡️ Yeni derste molaya kalan çalışma süresi: "
                    + StudyDatabaseHelper.formatDurationTr(nextRemaining));
        } else {
            tvCycleMemoryInfo.setText("⏱️ 40 Dakikalık Çalışma Döngüsü: Tam 40 dakika ile başlamaya hazır!");
        }

        String localIp = LanSyncServer.getLocalIpAddress(this);
        String anneIp = StudyDatabaseHelper.getPairedParentIp(this, "ANNE");
        String babaIp = StudyDatabaseHelper.getPairedParentIp(this, "BABA");
        StringBuilder parentInfo = new StringBuilder("📡 Cihaz Ağ IP: " + localIp);
        if (!anneIp.isEmpty()) parentInfo.append(" • 👩 Anne Eşleşti");
        if (!babaIp.isEmpty()) parentInfo.append(" • 👨 Baba Eşleşti");
        tvParentConnectionStatus.setText(parentInfo.toString());

        if (serviceBound && timerService != null && timerService.getCurrentPhase() != TimerForegroundService.PHASE_IDLE) {
            btnResumeActiveTimer.setVisibility(View.VISIBLE);
            btnResumeActiveTimer.setText("▶️ Aktif Ekranı Aç: " + timerService.getCurrentSubject()
                    + " (" + StudyDatabaseHelper.formatClock(timerService.getRemainingSeconds()) + ")");
        } else {
            btnResumeActiveTimer.setVisibility(View.GONE);
        }
    }
}
