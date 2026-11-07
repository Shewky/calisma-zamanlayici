package com.ipek.calismazamanlayici;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class TimerActivity extends Activity {

    private TextView tvTimerIpekcheer;
    private TextView tvTimerModeAndSubject;
    private TextView tvPhaseBadge;
    private TextView tvPhaseLabelInside;
    private TextView tvCountdownClock;
    private TextView tvSubjectElapsedInfo;
    private TextView tvLastAnnouncementText;
    private Button btnFinishCurrentSubject;
    private Button btnPauseResumeTimer;

    private TimerForegroundService timerService;
    private boolean serviceBound = false;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private final Runnable uiRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            updateTimerScreen();
            uiHandler.postDelayed(this, 500L);
        }
    };

    private final ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            TimerForegroundService.LocalBinder binder = (TimerForegroundService.LocalBinder) service;
            timerService = binder.getService();
            serviceBound = true;
            updateTimerScreen();
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
        setContentView(R.layout.activity_timer);

        tvTimerIpekcheer = findViewById(R.id.tvTimerIpekcheer);
        tvTimerModeAndSubject = findViewById(R.id.tvTimerModeAndSubject);
        tvPhaseBadge = findViewById(R.id.tvPhaseBadge);
        tvPhaseLabelInside = findViewById(R.id.tvPhaseLabelInside);
        tvCountdownClock = findViewById(R.id.tvCountdownClock);
        tvSubjectElapsedInfo = findViewById(R.id.tvSubjectElapsedInfo);
        tvLastAnnouncementText = findViewById(R.id.tvLastAnnouncementText);
        btnFinishCurrentSubject = findViewById(R.id.btnFinishCurrentSubject);
        btnPauseResumeTimer = findViewById(R.id.btnPauseResumeTimer);
        Button btnCancelSession = findViewById(R.id.btnCancelSession);

        btnFinishCurrentSubject.setOnClickListener(v -> {
            if (serviceBound && timerService != null) {
                String mode = timerService.getCurrentMode();
                String subj = timerService.getCurrentSubject();
                int elapsed = timerService.getSubjectElapsedSeconds();
                timerService.finishCurrentSubjectAndSave();

                Toast.makeText(TimerActivity.this,
                        "🎉 Harikasın İpek! " + subj + " (" + mode + ") takvime kaydedildi ("
                                + StudyDatabaseHelper.formatDurationTr(elapsed) + ").",
                        Toast.LENGTH_LONG).show();
            }
            // Tekrar ders seçimi ekranına dön
            finish();
        });

        btnPauseResumeTimer.setOnClickListener(v -> {
            if (serviceBound && timerService != null) {
                timerService.togglePauseResume();
                updateTimerScreen();
            }
        });

        btnCancelSession.setOnClickListener(v -> {
            if (serviceBound && timerService != null) {
                timerService.cancelCurrentSession();
            }
            finish();
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent bindIntent = new Intent(this, TimerForegroundService.class);
        bindService(bindIntent, conn, Context.BIND_AUTO_CREATE);
        uiHandler.post(uiRefreshRunnable);
    }

    @Override
    protected void onStop() {
        uiHandler.removeCallbacks(uiRefreshRunnable);
        if (serviceBound) {
            unbindService(conn);
            serviceBound = false;
        }
        super.onStop();
    }

    private void updateTimerScreen() {
        if (!serviceBound || timerService == null) return;

        String mode = timerService.getCurrentMode();
        String subject = timerService.getCurrentSubject();
        int phase = timerService.getCurrentPhase();
        int remaining = timerService.getRemainingSeconds();
        int subjectElapsed = timerService.getSubjectElapsedSeconds();
        int carriedCycle = timerService.getCarriedCycleSeconds();

        tvTimerModeAndSubject.setText("📖 " + mode + " • " + subject);
        tvCountdownClock.setText(StudyDatabaseHelper.formatClock(remaining));
        tvSubjectElapsedInfo.setText("Bu derste geçen süre: " + StudyDatabaseHelper.formatClock(subjectElapsed)
                + "\n40 dk döngüsünde toplam: " + StudyDatabaseHelper.formatClock(carriedCycle));
        tvLastAnnouncementText.setText("🔊 Son Sesli Anons: "" + timerService.getLastAnnouncement() + """);

        if (StudyDatabaseHelper.MODE_TEST.equals(mode)) {
            btnFinishCurrentSubject.setText("✅ Bu dersin testi bitti (veya Ödevi Bitti)");
        } else {
            btnFinishCurrentSubject.setText("✅ Bu dersin ödevi bitti");
        }

        if (phase == TimerForegroundService.PHASE_BREAK) {
            tvTimerIpekcheer.setText("Dinlenme Zamanı İpek! ☕🌸");
            tvPhaseBadge.setText("☕ 10 Dakikalık Mola Süresi Aktif");
            tvPhaseLabelInside.setText("MOLA BİTİMİNE KALAN");
        } else {
            tvTimerIpekcheer.setText("Harika Gidiyorsun İpek! 🌟");
            tvPhaseBadge.setText("🔔 Çalışma Süresi Aktif (40 Dk Döngüsü)");
            tvPhaseLabelInside.setText("MOLAYA KALAN SÜRE");
        }

        if (timerService.isPaused()) {
            btnPauseResumeTimer.setText("▶️ Devam Et");
        } else {
            btnPauseResumeTimer.setText("⏸️ Duraklat");
        }
    }
}
