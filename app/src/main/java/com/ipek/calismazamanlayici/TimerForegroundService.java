package com.ipek.calismazamanlayici;

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
import android.os.Binder;
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
 * Arka planda ve ekran kapalıyken bile (WakeLock + ForegroundService + Alarm Ses Kanalı)
 * kesintisiz çalışan, 40 dk çalışma / 10 dk mola döngüsünü ve Türkçe sesli anonsları yöneten servis.
 */
public class TimerForegroundService extends Service implements TextToSpeech.OnInitListener {

    public static final String ACTION_START_SESSION = "com.ipek.calismazamanlayici.ACTION_START_SESSION";
    public static final String ACTION_PAUSE_RESUME = "com.ipek.calismazamanlayici.ACTION_PAUSE_RESUME";
    public static final String ACTION_FINISH_SUBJECT = "com.ipek.calismazamanlayici.ACTION_FINISH_SUBJECT";
    public static final String ACTION_CANCEL_SESSION = "com.ipek.calismazamanlayici.ACTION_CANCEL_SESSION";
    public static final String ACTION_SPEAK_CUSTOM = "com.ipek.calismazamanlayici.ACTION_SPEAK_CUSTOM";

    public static final String EXTRA_MODE = "extra_mode";
    public static final String EXTRA_SUBJECT = "extra_subject";
    public static final String EXTRA_CUSTOM_SPEECH = "extra_custom_speech";

    public static final int PHASE_IDLE = 0;
    public static final int PHASE_STUDY = 1;
    public static final int PHASE_BREAK = 2;

    private static final String CHANNEL_ID = "IpekTimerChannel";
    private static final int NOTIF_ID = 1001;

    private final IBinder binder = new LocalBinder();
    private Handler handler;
    private PowerManager.WakeLock wakeLock;
    private TextToSpeech tts;
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
    private String lastAnnouncement = "Çalışmaya başla";

    private long lastTickRealtimeMs = 0L;

    public class LocalBinder extends Binder {
        public TimerForegroundService getService() {
            return TimerForegroundService.this;
        }
    }

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            if (currentPhase == PHASE_IDLE || isPaused) {
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
                if (currentPhase == PHASE_IDLE) {
                    break;
                }
            }

            updateForegroundNotification();
            handler.postDelayed(this, 1000L);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
        acquireWakeLock();
        tts = new TextToSpeech(getApplicationContext(), this);
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

    private void acquireWakeLock() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null && wakeLock == null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CalismaZamanlayici::IpekTimerWakeLock");
            wakeLock.setReferenceCounted(false);
        }
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire(4 * 60 * 60 * 1000L); // 4 saatlik güvenli arka plan kilidi
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        acquireWakeLock();
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            if (ACTION_START_SESSION.equals(action)) {
                String mode = intent.getStringExtra(EXTRA_MODE);
                String subject = intent.getStringExtra(EXTRA_SUBJECT);
                startNewSubjectSession(mode, subject);
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
            }
        }
        return START_STICKY;
    }

    private void startNewSubjectSession(String mode, String subject) {
        if (mode != null) currentMode = mode;
        if (subject != null) currentSubject = subject;

        // Önceki dersten hafızada tutulan çalışma süresini al ve 40 dakikadan otomatik düş
        carriedCycleSeconds = StudyDatabaseHelper.getCarriedCycleSeconds(this);
        if (carriedCycleSeconds < 0 || carriedCycleSeconds >= StudyDatabaseHelper.FULL_STUDY_CYCLE_SECONDS) {
            carriedCycleSeconds = 0;
        }

        remainingSeconds = StudyDatabaseHelper.FULL_STUDY_CYCLE_SECONDS - carriedCycleSeconds;
        subjectElapsedSeconds = 0;
        currentPhase = PHASE_STUDY;
        isPaused = false;
        breakWarningAnnounced = false;

        startForeground(NOTIF_ID, buildNotification());

        // "Çalışmaya başla" sesli anonsu
        announce("Çalışmaya başla");

        handler.removeCallbacks(tickRunnable);
        lastTickRealtimeMs = SystemClock.elapsedRealtime();
        handler.postDelayed(tickRunnable, 1000L);
    }

    private void stepOneSecond() {
        if (currentPhase == PHASE_STUDY) {
            subjectElapsedSeconds++;
            carriedCycleSeconds++;
            remainingSeconds--;

            // Her 5 saniyede bir hafızadaki döngü süresini kalıcı kaydet
            if (carriedCycleSeconds % 5 == 0) {
                StudyDatabaseHelper.setCarriedCycleSeconds(this, carriedCycleSeconds);
            }

            if (remainingSeconds <= 0) {
                // 40 dakikalık çalışma süresi doldu -> "Mola zamanı" anonsu!
                StudyDatabaseHelper.resetCarriedCycleSeconds(this);
                carriedCycleSeconds = 0;
                currentPhase = PHASE_BREAK;
                remainingSeconds = StudyDatabaseHelper.FULL_BREAK_CYCLE_SECONDS; // 10 dakika mola
                breakWarningAnnounced = false;
                announce("Mola zamanı");
            }
        } else if (currentPhase == PHASE_BREAK) {
            remainingSeconds--;

            // 10 dakikalık molanın bitmesine yakın (son 1 dakika kala) -> "Mola bitmek üzere" uyarısı!
            if (remainingSeconds == StudyDatabaseHelper.BREAK_WARNING_REMAINING_SECONDS && !breakWarningAnnounced) {
                breakWarningAnnounced = true;
                announce("Mola bitmek üzere");
            }

            if (remainingSeconds <= 0) {
                // 10 dakikalık mola süresi doldu -> "Çalışmaya devam et" anonsu!
                currentPhase = PHASE_STUDY;
                carriedCycleSeconds = 0;
                StudyDatabaseHelper.resetCarriedCycleSeconds(this);
                remainingSeconds = StudyDatabaseHelper.FULL_STUDY_CYCLE_SECONDS; // Yeni 40 dakika döngüsü
                breakWarningAnnounced = false;
                announce("Çalışmaya devam et");
            }
        }
    }

    public void togglePauseResume() {
        if (currentPhase == PHASE_IDLE) return;
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

    /**
     * "Bu dersin ödevi bitti" veya "Bu dersin testi bitti" tıklandığında:
     * 1. Bu derste çalışılan süreyi takvim veritabanına kaydeder.
     * 2. Moladan sonraki (veya mevcut 40 dakikalık döngüdeki) geçen çalışma süresini hafızada tutar.
     * 3. Başarılı günlerde teşvik edici sesli anons ("Aferin çalışmaya devam et" / "Aferin nice başarılı günlere") yapar.
     */
    public void finishCurrentSubjectAndSave() {
        handler.removeCallbacks(tickRunnable);

        if (subjectElapsedSeconds > 0) {
            StudyDatabaseHelper db = new StudyDatabaseHelper(this);
            db.insertSession(currentMode, currentSubject, subjectElapsedSeconds);
        }

        if (currentPhase == PHASE_STUDY) {
            // Moladan sonraki çalışma süresi içinde geçen süreyi hafızada tut
            StudyDatabaseHelper.setCarriedCycleSeconds(this, carriedCycleSeconds);
        } else {
            // Moladayken ders bitirildiyse yeni ders tam 40 dakika ile başlasın
            StudyDatabaseHelper.resetCarriedCycleSeconds(this);
        }

        // Teşvik edici başarı anonsu
        StudyDatabaseHelper db = new StudyDatabaseHelper(this);
        int todayCount = db.getSessionsForDate(StudyDatabaseHelper.getTodayDateString()).size();
        if (todayCount % 2 == 0) {
            speakNow("Aferin nice başarılı günlere");
        } else {
            speakNow("Aferin çalışmaya devam et");
        }

        currentPhase = PHASE_IDLE;
        isPaused = false;
        stopForeground(true);
    }

    public void cancelCurrentSession() {
        handler.removeCallbacks(tickRunnable);
        if (currentPhase == PHASE_STUDY) {
            StudyDatabaseHelper.setCarriedCycleSeconds(this, carriedCycleSeconds);
        }
        currentPhase = PHASE_IDLE;
        isPaused = false;
        stopForeground(true);
    }

    public void announce(String phrase) {
        lastAnnouncement = phrase;
        vibrateAndChime();
        speakNow(phrase);
    }

    public void speakNow(final String phrase) {
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
                tts.speak(phrase, TextToSpeech.QUEUE_FLUSH, params, "ipek_utt_" + System.currentTimeMillis());
            } else {
                java.util.HashMap<String, String> map = new java.util.HashMap<>();
                map.put(TextToSpeech.Engine.KEY_PARAM_STREAM, String.valueOf(AudioManager.STREAM_ALARM));
                tts.speak(phrase, TextToSpeech.QUEUE_FLUSH, map);
            }
        } catch (Exception ignored) {
        }
    }

    private void vibrateAndChime() {
        try {
            Vibrator vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vib != null && vib.hasVibrator()) {
                vib.vibrate(400);
            }
            ToneGenerator toneGen = new ToneGenerator(AudioManager.STREAM_ALARM, 85);
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 350);
        } catch (Exception ignored) {
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "İpek Çalışma Zamanlayıcı Bildirimleri",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Arka planda çalışma ve mola sayaç durumu");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {
        Intent openIntent = new Intent(this, TimerActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pi = PendingIntent.getActivity(this, 0, openIntent, flags);

        String phaseTitle = (currentPhase == PHASE_BREAK)
                ? "☕ Mola Zamanı (" + StudyDatabaseHelper.formatClock(remainingSeconds) + ")"
                : "📚 " + currentMode + ": " + currentSubject + " (" + StudyDatabaseHelper.formatClock(remainingSeconds) + ")";

        String subText = "Son Anons: "" + lastAnnouncement + "" • İpek için arka planda çalışıyor";

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

    private void updateForegroundNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null && currentPhase != PHASE_IDLE) {
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
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        super.onDestroy();
    }
}
