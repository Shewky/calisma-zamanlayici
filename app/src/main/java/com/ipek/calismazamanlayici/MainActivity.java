package com.ipek.calismazamanlayici;

import android.Manifest;
import android.app.Activity;
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
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private TextView tvTodaySummaryBanner;
    private TextView tvCycleMemoryInfo;
    private TextView tvAppVersionInfo;
    private Button btnResumeActiveTimer;

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

        tvTodaySummaryBanner = findViewById(R.id.tvTodaySummaryBanner);
        tvCycleMemoryInfo = findViewById(R.id.tvCycleMemoryInfo);
        tvAppVersionInfo = findViewById(R.id.tvAppVersionInfo);
        btnResumeActiveTimer = findViewById(R.id.btnResumeActiveTimer);

        Button btnStartHomework = findViewById(R.id.btnStartHomework);
        Button btnStartTest = findViewById(R.id.btnStartTest);
        Button btnOpenCalendar = findViewById(R.id.btnOpenCalendar);
        Button btnOpenRewards = findViewById(R.id.btnOpenRewards);
        Button btnTestVoice = findViewById(R.id.btnTestVoice);
        Button btnCheckUpdate = findViewById(R.id.btnCheckUpdate);

        tvAppVersionInfo.setText("Çalışma Zamanlayıcı v" + BuildConfig.VERSION_NAME
                + " (Kod: " + BuildConfig.VERSION_CODE + ") • Android 6+ Uyumlu");

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

        btnOpenCalendar.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, CalendarActivity.class));
        });

        btnOpenRewards.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, RewardsActivity.class));
        });

        btnResumeActiveTimer.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, TimerActivity.class));
        });

        btnTestVoice.setOnClickListener(v -> {
            Intent voiceIntent = new Intent(MainActivity.this, TimerForegroundService.class);
            voiceIntent.setAction(TimerForegroundService.ACTION_SPEAK_CUSTOM);
            voiceIntent.putExtra(TimerForegroundService.EXTRA_CUSTOM_SPEECH,
                    "Merhaba İpek! Çalışmaya başla. Aferin çalışmaya devam et!");
            startService(voiceIntent);
            Toast.makeText(MainActivity.this, "🔊 Sesli anons testi çalınıyor...", Toast.LENGTH_SHORT).show();
        });

        btnCheckUpdate.setOnClickListener(v -> {
            GitHubUpdater.checkUpdates(MainActivity.this, true);
        });

        btnCheckUpdate.setOnLongClickListener(v -> {
            GitHubUpdater.showConfigureRepoDialog(MainActivity.this);
            return true;
        });

        requestNecessaryPermissions();
        // Uygulama açıldığında GitHub'da yeni güncelleme olup olmadığını sessizce kontrol et
        GitHubUpdater.checkUpdates(this, false);
    }

    private void requestNecessaryPermissions() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
        // Ekran kapalıyken bile pil tasarrufunun zamanlayıcıyı durdurmaması için izin kontrolü (Android 6.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
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
        refreshDashboardUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
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
        StudyDatabaseHelper db = new StudyDatabaseHelper(this);
        int todaySecs = db.getTotalSecondsForDate(StudyDatabaseHelper.getTodayDateString());
        int todayCount = db.getSessionsForDate(StudyDatabaseHelper.getTodayDateString()).size();

        if (todayCount == 0) {
            tvTodaySummaryBanner.setText("🎯 Bugün henüz çalışma kaydı yok. Hadi başlayalım İpek!");
        } else {
            tvTodaySummaryBanner.setText("🌟 Harikasın İpek! Bugün " + todayCount + " çalışma tamamladın ("
                    + StudyDatabaseHelper.formatDurationTr(todaySecs) + ")");
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

        if (serviceBound && timerService != null && timerService.getCurrentPhase() != TimerForegroundService.PHASE_IDLE) {
            btnResumeActiveTimer.setVisibility(View.VISIBLE);
            btnResumeActiveTimer.setText("▶️ Aktif Sayaç: " + timerService.getCurrentSubject()
                    + " (" + StudyDatabaseHelper.formatClock(timerService.getRemainingSeconds()) + ")");
        } else {
            btnResumeActiveTimer.setVisibility(View.GONE);
        }
    }
}
