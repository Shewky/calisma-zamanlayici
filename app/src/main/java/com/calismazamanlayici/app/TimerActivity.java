package com.calismazamanlayici.app;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class TimerActivity extends Activity {

    private ScrollView scrollRootTimer;
    private LinearLayout layoutTimerBanner;
    private LinearLayout layoutTimerCircle;
    private LinearLayout layoutPostBreakPrompt;

    private TextView tvTimerUserCheer;
    private TextView tvTimerModeAndSubject;
    private TextView tvPhaseBadge;
    private TextView tvPhaseLabelInside;
    private TextView tvCountdownClock;
    private TextView tvSubjectElapsedInfo;
    private TextView tvLastAnnouncementText;
    private TextView tvPostBreakQuestion;

    private Button btnStartTimerNow;
    private Button btnPostBreakYes;
    private Button btnPostBreakSwitch;
    private Button btnFinishCurrentSubject;
    private Button btnPauseResumeTimer;
    private Button btnRequestBreakPhonePermission;
    private int consecutiveBackPresses = 0;
    private long lastBackPressTimeMs = 0L;

    private TimerForegroundService timerService;
    private boolean serviceBound = false;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private final Runnable uiRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            updateTimerScreen();
            uiHandler.postDelayed(this, 400L);
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

        scrollRootTimer = findViewById(R.id.scrollRootTimer);
        layoutTimerBanner = findViewById(R.id.layoutTimerBanner);
        layoutTimerCircle = findViewById(R.id.layoutTimerCircle);
        layoutPostBreakPrompt = findViewById(R.id.layoutPostBreakPrompt);

        tvTimerUserCheer = findViewById(R.id.tvTimerUserCheer);
        tvTimerModeAndSubject = findViewById(R.id.tvTimerModeAndSubject);
        tvPhaseBadge = findViewById(R.id.tvPhaseBadge);
        tvPhaseLabelInside = findViewById(R.id.tvPhaseLabelInside);
        tvCountdownClock = findViewById(R.id.tvCountdownClock);
        tvSubjectElapsedInfo = findViewById(R.id.tvSubjectElapsedInfo);
        tvLastAnnouncementText = findViewById(R.id.tvLastAnnouncementText);
        tvPostBreakQuestion = findViewById(R.id.tvPostBreakQuestion);

        btnStartTimerNow = findViewById(R.id.btnStartTimerNow);
        btnPostBreakYes = findViewById(R.id.btnPostBreakYes);
        btnPostBreakSwitch = findViewById(R.id.btnPostBreakSwitch);
        btnFinishCurrentSubject = findViewById(R.id.btnFinishCurrentSubject);
        btnPauseResumeTimer = findViewById(R.id.btnPauseResumeTimer);
        btnRequestBreakPhonePermission = findViewById(R.id.btnRequestBreakPhonePermission);
        if (btnRequestBreakPhonePermission != null) {
            btnRequestBreakPhonePermission.setOnClickListener(v -> handleTimerBreakPermissionClick());
        }
        Button btnCancelSession = findViewById(R.id.btnCancelSession);

        ThemeHelper.Palette palette = ThemeHelper.getPalette(this);
        ThemeHelper.applyWindowAndSurface(this, scrollRootTimer);
        ThemeHelper.styleHeader(this, layoutTimerBanner);
        layoutTimerCircle.setBackground(ThemeHelper.createTimerCircleDrawable(this, palette.primary));

        btnStartTimerNow.setBackground(ThemeHelper.createButtonDrawable(this, palette.primary));
        btnPostBreakYes.setBackground(ThemeHelper.createButtonDrawable(this, palette.primary));
        btnFinishCurrentSubject.setBackground(ThemeHelper.createButtonDrawable(this, palette.accent));
        btnPauseResumeTimer.setBackground(ThemeHelper.createButtonDrawable(this, palette.secondary));

        // "Başlat" butonuna tıklandığında "Çalışmaya başla" diyerek sayacı başlatır
        btnStartTimerNow.setOnClickListener(v -> {
            if (serviceBound && timerService != null) {
                timerService.startPreparedSubjectSession();
                updateTimerScreen();
            }
        });

        // Mola bittikten sonra "Evet, Bu Dersten Devam Et" tıklandığında:
        // "Çalışmaya devam et [Kullanıcı İsmi]" diyerek sayacı başlatır
        btnPostBreakYes.setOnClickListener(v -> {
            if (serviceBound && timerService != null) {
                timerService.confirmContinueAfterBreak();
                updateTimerScreen();
            }
        });

        // Mola bittikten sonra başka ders seçmek isterse
        btnPostBreakSwitch.setOnClickListener(v -> {
            if (serviceBound && timerService != null) {
                if (timerService.getSubjectElapsedSeconds() > 0) {
                    StudyDatabaseHelper db = new StudyDatabaseHelper(TimerActivity.this);
                    db.insertSession(timerService.getCurrentMode(), timerService.getCurrentSubject(), timerService.getSubjectElapsedSeconds());
                }
                timerService.cancelCurrentSession();
                StudyDatabaseHelper.resetCarriedCycleSeconds(TimerActivity.this);
            }
            finish();
        });

        btnFinishCurrentSubject.setOnClickListener(v -> {
            if (serviceBound && timerService != null) {
                String mode = timerService.getCurrentMode();
                String subj = timerService.getCurrentSubject();
                int elapsed = timerService.getSubjectElapsedSeconds();
                timerService.finishCurrentSubjectAndSave();

                String userName = StudyDatabaseHelper.getUserName(TimerActivity.this);
                Toast.makeText(TimerActivity.this,
                        "🎉 Harikasın " + userName + "! " + subj + " (" + mode + ") takvime kaydedildi ("
                                + StudyDatabaseHelper.formatDurationTr(elapsed) + ").",
                        Toast.LENGTH_LONG).show();
            }
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
    protected void onResume() {
        super.onResume();
        TimerForegroundService.setStudentActivityInForeground(true);
    }

    @Override
    protected void onPause() {
        TimerForegroundService.setStudentActivityInForeground(false);
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        boolean alwaysAllowed = StudyDatabaseHelper.isParentAlwaysAllowOtherFeatures(this);
        boolean inBreak = serviceBound && timerService != null
                && timerService.getCurrentPhase() == TimerForegroundService.PHASE_BREAK;
        boolean oneTimeGranted = inBreak && StudyDatabaseHelper.isOneTimeBreakPhoneGranted(this);

        if (alwaysAllowed || oneTimeGranted) {
            TimerForegroundService.setSystemExemptLaunch(true);
            moveTaskToBack(true);
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastBackPressTimeMs > 4000L) {
            consecutiveBackPresses = 1;
        } else {
            consecutiveBackPresses++;
        }
        lastBackPressTimeMs = now;

        if (consecutiveBackPresses >= 3) {
            consecutiveBackPresses = 0;
            String speech = "Annenden ya da babandan izin alarak, telefonunun diğer özelliklerini bir seferlik mola süresince kullanabilirsin";
            if (serviceBound && timerService != null) {
                timerService.speakNow(speech);
            }
            StudyDatabaseHelper.setBreakPermissionButtonUnlocked(this, true);
            updateTimerScreen();
            if (inBreak) {
                Toast.makeText(this, "📱 " + speech + ". Ekrandaki 'İzin İste' butonuna basabilirsin.", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "🔒 " + speech + " (Mola süresine geçtiğinde izin isteyebilirsin).", Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(this, "🔒 Çalışma veya mola sırasında uygulama kapatılamaz.", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleTimerBreakPermissionClick() {
        boolean alwaysAllowed = StudyDatabaseHelper.isParentAlwaysAllowOtherFeatures(this);
        boolean inBreak = serviceBound && timerService != null
                && timerService.getCurrentPhase() == TimerForegroundService.PHASE_BREAK;
        boolean oneTimeGranted = inBreak && StudyDatabaseHelper.isOneTimeBreakPhoneGranted(this);

        if (alwaysAllowed || oneTimeGranted) {
            TimerForegroundService.setSystemExemptLaunch(true);
            moveTaskToBack(true);
        } else if (inBreak) {
            LanSyncServer.sendBreakPhonePermissionRequestToParents(this);
            updateTimerScreen();
        }
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

        String userName = StudyDatabaseHelper.getUserName(this);
        String mode = timerService.getCurrentMode();
        String subject = timerService.getCurrentSubject();
        int phase = timerService.getCurrentPhase();
        int remaining = timerService.getRemainingSeconds();
        int subjectElapsed = timerService.getSubjectElapsedSeconds();
        int carriedCycle = timerService.getCarriedCycleSeconds();

        tvTimerModeAndSubject.setText("📖 " + mode + " • " + subject);
        tvCountdownClock.setText(StudyDatabaseHelper.formatClock(remaining));
        tvSubjectElapsedInfo.setText("Bu çalışmada geçen süre: " + StudyDatabaseHelper.formatClock(subjectElapsed)
                + "\n40 dk döngüsünde toplam: " + StudyDatabaseHelper.formatClock(carriedCycle));
        tvLastAnnouncementText.setText("🔊 Son Sesli Anons: '" + timerService.getLastAnnouncement() + "'");

        if (StudyDatabaseHelper.MODE_BOOK.equals(mode)) {
            btnFinishCurrentSubject.setText("✅ Kitap okumayı bitir ve takvime kaydet");
        } else if (StudyDatabaseHelper.MODE_TEST.equals(mode)) {
            btnFinishCurrentSubject.setText("✅ Bu dersin testi bitti");
        } else {
            btnFinishCurrentSubject.setText("✅ Bu dersin ödevi bitti");
        }

        if (phase == TimerForegroundService.PHASE_READY_TO_START) {
            // Ders seçildi, henüz Başlat butonuna basılmadı
            layoutPostBreakPrompt.setVisibility(View.GONE);
            btnStartTimerNow.setVisibility(View.VISIBLE);
            btnFinishCurrentSubject.setVisibility(View.GONE);
            btnPauseResumeTimer.setVisibility(View.GONE);

            tvTimerUserCheer.setText("Hazır mısın " + userName + "? 🌟");
            tvPhaseBadge.setText("🔔 Ders Seçildi • Başlat Butonuna Tıkla");
            tvPhaseLabelInside.setText("BAŞLAMAYA HAZIR");
        } else if (phase == TimerForegroundService.PHASE_POST_BREAK_PROMPT) {
            // 10 dakikalık mola bitti, son çalışılan dersi ekrana getirip kullanıcıya sorar
            layoutPostBreakPrompt.setVisibility(View.VISIBLE);
            tvPostBreakQuestion.setText("🔔 10 Dakikalık Mola Süresi Bitti!\nEn son çalıştığın '"
                    + subject + "' dersinden mi devam edeceksin?");

            btnStartTimerNow.setVisibility(View.GONE);
            btnFinishCurrentSubject.setVisibility(View.VISIBLE);
            btnPauseResumeTimer.setVisibility(View.GONE);

            tvTimerUserCheer.setText("Mola Bitti " + userName + "! 🔔");
            tvPhaseBadge.setText("❓ Bu dersten mi devam edeceksin?");
            tvPhaseLabelInside.setText("ONAY BEKLENİYOR");
        } else if (phase == TimerForegroundService.PHASE_BREAK) {
            layoutPostBreakPrompt.setVisibility(View.GONE);
            btnStartTimerNow.setVisibility(View.GONE);
            btnFinishCurrentSubject.setVisibility(View.VISIBLE);
            btnPauseResumeTimer.setVisibility(View.VISIBLE);

            tvTimerUserCheer.setText("Dinlenme Zamanı " + userName + "! ☕");
            tvPhaseBadge.setText("☕ 10 Dakikalık Mola Süresi Aktif");
            tvPhaseLabelInside.setText("MOLA BİTİMİNE KALAN");
        } else {
            layoutPostBreakPrompt.setVisibility(View.GONE);
            btnStartTimerNow.setVisibility(View.GONE);
            btnFinishCurrentSubject.setVisibility(View.VISIBLE);
            btnPauseResumeTimer.setVisibility(View.VISIBLE);

            tvTimerUserCheer.setText("Harika Gidiyorsun " + userName + "! 🌟");
            tvPhaseBadge.setText("🔔 Çalışma Süresi Aktif (40 Dk Döngüsü)");
            tvPhaseLabelInside.setText("MOLAYA KALAN SÜRE");
        }

        if (timerService.isPaused()) {
            btnPauseResumeTimer.setText("▶️ Devam Et");
        } else {
            btnPauseResumeTimer.setText("⏸️ Duraklat");
        }

        if (btnRequestBreakPhonePermission != null) {
            boolean alwaysAllowed = StudyDatabaseHelper.isParentAlwaysAllowOtherFeatures(this);
            boolean inBreak = (phase == TimerForegroundService.PHASE_BREAK);
            boolean oneTimeGranted = inBreak && StudyDatabaseHelper.isOneTimeBreakPhoneGranted(this);
            boolean unlocked = inBreak && StudyDatabaseHelper.isBreakPermissionButtonUnlocked(this);

            if (inBreak && (alwaysAllowed || oneTimeGranted)) {
                btnRequestBreakPhonePermission.setVisibility(View.VISIBLE);
                btnRequestBreakPhonePermission.setBackground(ThemeHelper.createButtonDrawable(this, 0xFF16A34A));
                btnRequestBreakPhonePermission.setText("📱 Telefonun Diğer Özelliklerini Kullan (İzin Verildi - Arka Plana At)");
            } else if (inBreak && unlocked) {
                btnRequestBreakPhonePermission.setVisibility(View.VISIBLE);
                btnRequestBreakPhonePermission.setBackground(ThemeHelper.createButtonDrawable(this, 0xFFD97706));
                if (StudyDatabaseHelper.isBreakPermissionPending(this)) {
                    btnRequestBreakPhonePermission.setText("⏳ Veli Onayı Bekleniyor... (Tekrar İstek Gönder)");
                } else {
                    btnRequestBreakPhonePermission.setText("📱 Veli'den Bir Seferlik Telefon İzni İste");
                }
            } else {
                btnRequestBreakPhonePermission.setVisibility(View.GONE);
            }
        }
    }
}
