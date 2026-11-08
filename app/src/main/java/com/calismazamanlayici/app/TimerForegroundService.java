package com.calismazamanlayici.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.wifi.WifiManager;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Binder;
import android.widget.Toast;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;

import java.util.Locale;

/**
 * Öğrenci uygulamasında sürekli arka planda ve ekran kapalıyken bile çalışan servis:
 * - WakeLock + WifiLock + MulticastLock ile ekran kapansa veya uygulama arka plana atılsa bile
 *   hem sayaç/anonsları sürdürür hem de Veli uygulamasından gelen onaysız doğrudan aramaları anında başlatır.
 */
public class TimerForegroundService extends Service implements TextToSpeech.OnInitListener {

    public static final String ACTION_PREPARE_SESSION = "com.calismazamanlayici.app.ACTION_PREPARE_SESSION";
    public static final String ACTION_START_PREPARED_SESSION = "com.calismazamanlayici.app.ACTION_START_PREPARED_SESSION";
    public static final String ACTION_START_BOOK_SESSION = "com.calismazamanlayici.app.ACTION_START_BOOK_SESSION";
    public static final String ACTION_CONFIRM_POST_BREAK_CONTINUE = "com.calismazamanlayici.app.ACTION_CONFIRM_POST_BREAK_CONTINUE";
    public static final String ACTION_PAUSE_RESUME = "com.calismazamanlayici.app.ACTION_PAUSE_RESUME";
    public static final String ACTION_FINISH_SUBJECT = "com.calismazamanlayici.app.ACTION_FINISH_SUBJECT";
    public static final String ACTION_CANCEL_SESSION = "com.calismazamanlayici.app.ACTION_CANCEL_SESSION";
    public static final String ACTION_SPEAK_CUSTOM = "com.calismazamanlayici.app.ACTION_SPEAK_CUSTOM";
    public static final String ACTION_END_ACTIVE_CALL = "com.calismazamanlayici.app.ACTION_END_ACTIVE_CALL";

    public static final String EXTRA_MODE = "extra_mode";
    public static final String EXTRA_SUBJECT = "extra_subject";
    public static final String EXTRA_CUSTOM_SPEECH = "extra_custom_speech";

    public static final int PHASE_IDLE = 0;
    public static final int PHASE_READY_TO_START = 1;
    public static final int PHASE_STUDY = 2;
    public static final int PHASE_BREAK = 3;
    public static final int PHASE_POST_BREAK_PROMPT = 4;

    private static final String CHANNEL_ID = "CalismaTimerChannel";
    private static final int NOTIF_ID = 1001;

    private final IBinder binder = new LocalBinder();
    private Handler handler;
    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;
    private WifiManager.MulticastLock multicastLock;
    private TextToSpeech tts;
    private LanSyncServer lanSyncServer;
    private boolean ttsReady = false;
    private String pendingSpeech = null;

    private int currentPhase = PHASE_IDLE;
    private boolean isPaused = false;
    private String currentMode = StudyDatabaseHelper.MODE_HOMEWORK;
    private String currentSubject = "Türkçe";

    private int remainingSeconds = StudyDatabaseHelper.FULL_STUDY_CYCLE_SECONDS;
    private int subjectElapsedSeconds = 0;
    private int carriedCycleSeconds = 0;
    private boolean breakWarningAnnounced = false;
    private boolean battery21Announced = false;

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            if (level < 0 || scale <= 0) return;

            int pct = (level * 100) / scale;
            boolean isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL);

            if (pct > 21 || isCharging) {
                battery21Announced = false;
            } else if (pct == 21 && !battery21Announced) {
                battery21Announced = true;
                String batteryMsg = "Şarjın azaldı yakın zamanda cihazı şarja takmalısın";
                wakeScreenBriefly();
                announce(batteryMsg, false);
                Toast.makeText(TimerForegroundService.this, "🔋 " + batteryMsg, Toast.LENGTH_LONG).show();
            }
        }
    };
    private String lastAnnouncement = "Hazır";

    private long lastTickRealtimeMs = 0L;

    public class LocalBinder extends Binder {
        public TimerForegroundService getService() {
            return TimerForegroundService.this;
        }
    }

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            if ((currentPhase != PHASE_STUDY && currentPhase != PHASE_BREAK) || isPaused) {
                return;
            }
            long now = SystemClock.elapsedRealtime();
            long elapsedMs = now - lastTickRealtimeMs;
            int deltaSec = (int) (elapsedMs / 1000L);
            if (deltaSec < 1) {
                deltaSec = 1;
            }
            lastTickRealtimeMs = now;

            for (int i = 0; i < deltaSec; i++) {
                stepOneSecond();
                if (currentPhase != PHASE_STUDY && currentPhase != PHASE_BREAK) {
                    break;
                }
            }

            updateForegroundNotification();
            if (currentPhase == PHASE_STUDY || currentPhase == PHASE_BREAK) {
                handler.postDelayed(this, 1000L);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
        acquireLocks();
        // Uygulama arka plandayken de kapanmaması ve Veli aramalarını anında açabilmesi için
        // servisi başlangıçtan itibaren Foreground modunda tutuyoruz.
        startForeground(NOTIF_ID, buildNotification());
        tts = new TextToSpeech(getApplicationContext(), this);
        lanSyncServer = new LanSyncServer(this);
        lanSyncServer.start();
        try {
            registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS && tts != null) {
            Locale trLocale = new Locale("tr", "TR");
            int res = tts.setLanguage(trLocale);
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(Locale.getDefault());
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                AudioAttributes attrs = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build();
                tts.setAudioAttributes(attrs);
            }
            tts.setSpeechRate(0.96f);
            ttsReady = true;
            if (pendingSpeech != null) {
                String toSpeak = pendingSpeech;
                pendingSpeech = null;
                speakNow(toSpeak);
            }
        }
    }

    private void acquireLocks() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && wakeLock == null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CalismaZamanlayici::TimerWakeLock");
                wakeLock.setReferenceCounted(false);
            }
            if (wakeLock != null && !wakeLock.isHeld()) {
                wakeLock.acquire();
            }
        } catch (Exception ignored) {
        }
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                if (wifiLock == null) {
                    wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "CalismaZamanlayici::WifiLock");
                    wifiLock.setReferenceCounted(false);
                }
                if (!wifiLock.isHeld()) {
                    wifiLock.acquire();
                }
                if (multicastLock == null) {
                    multicastLock = wm.createMulticastLock("CalismaZamanlayici::MulticastLock");
                    multicastLock.setReferenceCounted(false);
                }
                if (!multicastLock.isHeld()) {
                    multicastLock.acquire();
                }
            }
        } catch (Exception ignored) {
        }
    }

    public void wakeScreenBriefly() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                @SuppressWarnings("deprecation")
                PowerManager.WakeLock screenLock = pm.newWakeLock(
                        PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                        "CalismaZamanlayici::ScreenWake"
                );
                screenLock.acquire(8000L);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        acquireLocks();
        startForeground(NOTIF_ID, buildNotification());
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            if (ACTION_PREPARE_SESSION.equals(action)) {
                String mode = intent.getStringExtra(EXTRA_MODE);
                String subject = intent.getStringExtra(EXTRA_SUBJECT);
                prepareSubjectSession(mode, subject);
            } else if (ACTION_START_PREPARED_SESSION.equals(action)) {
                startPreparedSubjectSession();
            } else if (ACTION_START_BOOK_SESSION.equals(action)) {
                String bookTitle = intent.getStringExtra(EXTRA_SUBJECT);
                startBookReadingSession(bookTitle);
            } else if (ACTION_CONFIRM_POST_BREAK_CONTINUE.equals(action)) {
                confirmContinueAfterBreak();
            } else if (ACTION_PAUSE_RESUME.equals(action)) {
                togglePauseResume();
            } else if (ACTION_FINISH_SUBJECT.equals(action)) {
                finishCurrentSubjectAndSave();
            } else if (ACTION_CANCEL_SESSION.equals(action)) {
                cancelCurrentSession();
            } else if (ACTION_SPEAK_CUSTOM.equals(action)) {
                String text = intent.getStringExtra(EXTRA_CUSTOM_SPEECH);
                if (text != null && !text.isEmpty()) {
                    speakNow(text);
                }
            } else if (ACTION_END_ACTIVE_CALL.equals(action)) {
                VoiceCallManager.stopCall(this);
                CallActivity.notifyCallEndedRemotely();
                updateForegroundNotification();
            }
        }
        return START_STICKY;
    }

    private void prepareSubjectSession(String mode, String subject) {
        handler.removeCallbacks(tickRunnable);
        if (mode != null) currentMode = mode;
        if (subject != null) currentSubject = subject;

        StudyDatabaseHelper.saveLastStudiedSubject(this, currentMode, currentSubject);

        carriedCycleSeconds = StudyDatabaseHelper.getCarriedCycleSeconds(this);
        if (carriedCycleSeconds < 0 || carriedCycleSeconds >= StudyDatabaseHelper.FULL_STUDY_CYCLE_SECONDS) {
            carriedCycleSeconds = 0;
        }

        remainingSeconds = StudyDatabaseHelper.FULL_STUDY_CYCLE_SECONDS - carriedCycleSeconds;
        subjectElapsedSeconds = 0;
        currentPhase = PHASE_READY_TO_START;
        isPaused = false;
        breakWarningAnnounced = false;

        lastAnnouncement = currentSubject;
        speakNow(currentSubject);
        updateForegroundNotification();
    }

    public void startPreparedSubjectSession() {
        if (currentPhase != PHASE_READY_TO_START && currentPhase != PHASE_IDLE) {
            return;
        }
        carriedCycleSeconds = StudyDatabaseHelper.getCarriedCycleSeconds(this);
        if (carriedCycleSeconds < 0 || carriedCycleSeconds >= StudyDatabaseHelper.FULL_STUDY_CYCLE_SECONDS) {
            carriedCycleSeconds = 0;
        }
        remainingSeconds = StudyDatabaseHelper.FULL_STUDY_CYCLE_SECONDS - carriedCycleSeconds;
        subjectElapsedSeconds = 0;
        currentPhase = PHASE_STUDY;
        isPaused = false;
        breakWarningAnnounced = false;

        announce("Çalışmaya başla", false);
        updateForegroundNotification();

        handler.removeCallbacks(tickRunnable);
        lastTickRealtimeMs = SystemClock.elapsedRealtime();
        handler.postDelayed(tickRunnable, 1000L);
    }

    private void startBookReadingSession(String bookTitle) {
        handler.removeCallbacks(tickRunnable);
        currentMode = StudyDatabaseHelper.MODE_BOOK;
        currentSubject = (bookTitle != null && !bookTitle.trim().isEmpty()) ? bookTitle.trim() : "Kitap";

        StudyDatabaseHelper.saveLastStudiedSubject(this, currentMode, currentSubject);

        carriedCycleSeconds = StudyDatabaseHelper.getCarriedCycleSeconds(this);
        if (carriedCycleSeconds < 0 || carriedCycleSeconds >= StudyDatabaseHelper.FULL_STUDY_CYCLE_SECONDS) {
            carriedCycleSeconds = 0;
        }

        remainingSeconds = StudyDatabaseHelper.FULL_STUDY_CYCLE_SECONDS - carriedCycleSeconds;
        subjectElapsedSeconds = 0;
        currentPhase = PHASE_STUDY;
        isPaused = false;
        breakWarningAnnounced = false;

        String msg = currentSubject + " kitabını iyi okumalar";
        announce(msg, false);
        updateForegroundNotification();

        lastTickRealtimeMs = SystemClock.elapsedRealtime();
        handler.postDelayed(tickRunnable, 1000L);
    }

    private void stepOneSecond() {
        if (currentPhase == PHASE_STUDY) {
            subjectElapsedSeconds++;
            carriedCycleSeconds++;
            remainingSeconds--;

            if (carriedCycleSeconds % 5 == 0) {
                StudyDatabaseHelper.setCarriedCycleSeconds(this, carriedCycleSeconds);
            }

            if (remainingSeconds <= 0) {
                // 40 dakikalık çalışma tamamlandı! O ana kadar bu derste yapılan çalışmayı anında Takvime kaydet
                // (Böylece toplam 40 dakika çalışma tamamlandığı için Eğlence kilidi de hemen açılır!)
                if (subjectElapsedSeconds > 0) {
                    StudyDatabaseHelper db = new StudyDatabaseHelper(this);
                    db.insertSession(currentMode, currentSubject, subjectElapsedSeconds);
                    subjectElapsedSeconds = 0;
                }
                StudyDatabaseHelper.resetCarriedCycleSeconds(this);
                carriedCycleSeconds = 0;
                currentPhase = PHASE_BREAK;
                remainingSeconds = StudyDatabaseHelper.FULL_BREAK_CYCLE_SECONDS;
                breakWarningAnnounced = false;
                wakeScreenBriefly();
                announce("Mola zamanı", true);
            }
        } else if (currentPhase == PHASE_BREAK) {
            remainingSeconds--;

            if (remainingSeconds == StudyDatabaseHelper.BREAK_WARNING_REMAINING_SECONDS && !breakWarningAnnounced) {
                breakWarningAnnounced = true;
                wakeScreenBriefly();
                announce("Mola bitmek üzere", false);
            }

            if (remainingSeconds <= 0) {
                currentPhase = PHASE_POST_BREAK_PROMPT;
                remainingSeconds = 0;
                carriedCycleSeconds = 0;
                StudyDatabaseHelper.resetCarriedCycleSeconds(this);
                breakWarningAnnounced = false;

                wakeScreenBriefly();
                announce("Bu dersten mi devam edeceksin", true);
                bringTimerActivityToFront();
            }
        }
    }

    public void confirmContinueAfterBreak() {
        currentPhase = PHASE_STUDY;
        carriedCycleSeconds = 0;
        StudyDatabaseHelper.resetCarriedCycleSeconds(this);
        remainingSeconds = StudyDatabaseHelper.FULL_STUDY_CYCLE_SECONDS;
        isPaused = false;
        breakWarningAnnounced = false;

        String userName = StudyDatabaseHelper.getUserName(this);
        announce("Çalışmaya devam et " + userName, false);
        updateForegroundNotification();

        handler.removeCallbacks(tickRunnable);
        lastTickRealtimeMs = SystemClock.elapsedRealtime();
        handler.postDelayed(tickRunnable, 1000L);
    }

    private void bringTimerActivityToFront() {
        try {
            Intent openIntent = new Intent(this, TimerActivity.class);
            openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(openIntent);
        } catch (Exception ignored) {
        }
    }

    public void togglePauseResume() {
        if (currentPhase != PHASE_STUDY && currentPhase != PHASE_BREAK) return;
        isPaused = !isPaused;
        if (!isPaused) {
            lastTickRealtimeMs = SystemClock.elapsedRealtime();
            handler.removeCallbacks(tickRunnable);
            handler.postDelayed(tickRunnable, 1000L);
        } else {
            handler.removeCallbacks(tickRunnable);
            StudyDatabaseHelper.setCarriedCycleSeconds(this, carriedCycleSeconds);
        }
        updateForegroundNotification();
    }

    public void finishCurrentSubjectAndSave() {
        handler.removeCallbacks(tickRunnable);

        if (subjectElapsedSeconds > 0) {
            StudyDatabaseHelper db = new StudyDatabaseHelper(this);
            db.insertSession(currentMode, currentSubject, subjectElapsedSeconds);
        }

        if (currentPhase == PHASE_STUDY || currentPhase == PHASE_READY_TO_START) {
            if (carriedCycleSeconds > 0 && carriedCycleSeconds < StudyDatabaseHelper.FULL_STUDY_CYCLE_SECONDS) {
                StudyDatabaseHelper.setCarriedCycleSeconds(this, carriedCycleSeconds);
            }
        } else if (currentPhase == PHASE_BREAK || currentPhase == PHASE_POST_BREAK_PROMPT) {
            StudyDatabaseHelper.resetCarriedCycleSeconds(this);
        }

        String userName = StudyDatabaseHelper.getUserName(this);
        StudyDatabaseHelper db = new StudyDatabaseHelper(this);
        int todayCount = db.getSessionsForDate(StudyDatabaseHelper.getTodayDateString()).size();
        if (todayCount % 2 == 0) {
            speakNow("Aferin " + userName + ", nice başarılı günlere");
        } else {
            speakNow("Aferin " + userName + ", çalışmaya devam et");
        }

        currentPhase = PHASE_IDLE;
        isPaused = false;
        // Servisi durdurmuyoruz, arka planda Veli bağlantısı için çalışmaya devam ediyor!
        updateForegroundNotification();
    }

    public void cancelCurrentSession() {
        handler.removeCallbacks(tickRunnable);
        if (currentPhase == PHASE_STUDY) {
            StudyDatabaseHelper.setCarriedCycleSeconds(this, carriedCycleSeconds);
        }
        currentPhase = PHASE_IDLE;
        isPaused = false;
        updateForegroundNotification();
    }

    public void announce(final String phrase, boolean playCinCinBellSound) {
        lastAnnouncement = phrase;
        if (playCinCinBellSound) {
            playCinCinBellAndSpeak(phrase);
        } else {
            vibrateAndShortBeep();
            speakNow(phrase);
        }
    }

    private void playCinCinBellAndSpeak(final String phrase) {
        try {
            Vibrator vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vib != null && vib.hasVibrator()) {
                vib.vibrate(new long[]{0, 250, 150, 250}, -1);
            }
            final ToneGenerator toneGen = new ToneGenerator(AudioManager.STREAM_ALARM, 95);
            toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 220);
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 260);
                    } catch (Exception ignored) {
                    }
                }
            }, 300L);
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    speakNow(phrase);
                }
            }, 680L);
        } catch (Exception e) {
            speakNow(phrase);
        }
    }

    private void vibrateAndShortBeep() {
        try {
            Vibrator vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vib != null && vib.hasVibrator()) {
                vib.vibrate(300);
            }
            ToneGenerator toneGen = new ToneGenerator(AudioManager.STREAM_ALARM, 85);
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 200);
        } catch (Exception ignored) {
        }
    }

    public void speakNow(final String phrase) {
        lastAnnouncement = phrase;
        if (!ttsReady || tts == null) {
            pendingSpeech = phrase;
            return;
        }
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                am.requestAudioFocus(null, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Bundle params = new Bundle();
                params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ALARM);
                tts.speak(phrase, TextToSpeech.QUEUE_FLUSH, params, "utt_" + System.currentTimeMillis());
            } else {
                java.util.HashMap<String, String> map = new java.util.HashMap<>();
                map.put(TextToSpeech.Engine.KEY_PARAM_STREAM, String.valueOf(AudioManager.STREAM_ALARM));
                tts.speak(phrase, TextToSpeech.QUEUE_FLUSH, map);
            }
        } catch (Exception ignored) {
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Çalışma Zamanlayıcı Arka Plan Servisi",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Arka planda çalışma sayacı, sesli anons ve veli bağlantı servisi");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {
        Intent openIntent = new Intent(this,
                (currentPhase == PHASE_IDLE) ? MainActivity.class : TimerActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pi = PendingIntent.getActivity(this, 0, openIntent, flags);

        String phaseTitle;
        if (VoiceCallManager.isCallRunning()) {
            phaseTitle = "📞 Veli ile Sesli Görüşme Aktif (Hoparlör Açık)";
        } else if (currentPhase == PHASE_BREAK) {
            phaseTitle = "☕ Mola Zamanı (" + StudyDatabaseHelper.formatClock(remainingSeconds) + ")";
        } else if (currentPhase == PHASE_POST_BREAK_PROMPT) {
            phaseTitle = "🔔 Mola Bitti: " + currentSubject + " dersinden devam edilsin mi?";
        } else if (currentPhase == PHASE_STUDY) {
            phaseTitle = "📚 " + currentMode + ": " + currentSubject + " (" + StudyDatabaseHelper.formatClock(remainingSeconds) + ")";
        } else {
            phaseTitle = "🟢 Çalışma Zamanlayıcı Arka Planda Hazır";
        }

        String subText = "Son Anons: '" + lastAnnouncement + "' • Veli bağlantısı aktif";

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        builder.setContentTitle(phaseTitle)
                .setContentText(subText)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentIntent(pi)
                .setOngoing(true);

        return builder.build();
    }

    public void updateForegroundNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIF_ID, buildNotification());
        }
    }

    public int getCurrentPhase() {
        return currentPhase;
    }

    public boolean isPaused() {
        return isPaused;
    }

    public String getCurrentMode() {
        return currentMode;
    }

    public String getCurrentSubject() {
        return currentSubject;
    }

    public int getRemainingSeconds() {
        return remainingSeconds;
    }

    public int getSubjectElapsedSeconds() {
        return subjectElapsedSeconds;
    }

    public int getCarriedCycleSeconds() {
        return carriedCycleSeconds;
    }

    public String getLastAnnouncement() {
        return lastAnnouncement;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(tickRunnable);
        try {
            unregisterReceiver(batteryReceiver);
        } catch (Exception ignored) {
        }
        if (lanSyncServer != null) {
            lanSyncServer.stop();
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (wifiLock != null && wifiLock.isHeld()) {
            wifiLock.release();
        }
        if (multicastLock != null && multicastLock.isHeld()) {
            multicastLock.release();
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        super.onDestroy();
    }
}
