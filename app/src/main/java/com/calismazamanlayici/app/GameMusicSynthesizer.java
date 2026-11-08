package com.calismazamanlayici.app;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

/**
 * Harici ses dosyasına ihtiyaç duymadan her oyun temasına özel neşeli arka plan müziği
 * ve doğru/yanlış cevap ses efektleri üreten gerçek zamanlı ses sentezleyici.
 */
public class GameMusicSynthesizer {

    private static final int SAMPLE_RATE = 22050;
    private volatile boolean playing = false;
    private volatile boolean muted = false;
    private volatile int currentMelodyIndex = 0;
    private Thread musicThread;
    private AudioTrack bgTrack;

    // 8 farklı oyun atmosferi için nota dizileri (Hz cinsinden frekanslar)
    private static final int[][] MELODIES = new int[][]{
            // 0: Galaksi Sayı Avcısı (Uzay Macerası)
            {523, 659, 784, 1046, 784, 659, 587, 784, 880, 1046, 880, 784},
            // 1: Gizemli Kelime Kalesi (Masalsı Kale)
            {440, 523, 659, 880, 784, 659, 587, 523, 494, 587, 784, 659},
            // 2: Çılgın Laboratuvar Keşfi (Bilim Ritmi)
            {587, 659, 784, 659, 880, 784, 1046, 880, 784, 659, 587, 523},
            // 3: Zaman Gezgini Hazine Avı (Keşif Marşı)
            {392, 523, 659, 784, 659, 784, 880, 784, 659, 587, 523, 392},
            // 4: Sihirli Köprü Parolası (Neşeli Melodi)
            {523, 587, 659, 784, 880, 784, 659, 587, 523, 659, 784, 1046},
            // 5: İyilik Bahçesi Yıldızları (Huzurlu Bahçe)
            {523, 659, 784, 659, 880, 1046, 880, 784, 659, 523, 587, 523},
            // 6: Süper Kaptan Güvenli Rota (Yarış Heyecanı)
            {659, 784, 880, 1046, 880, 1046, 1175, 1046, 880, 784, 659, 784},
            // 7: Barış Adası Kahramanları (Ada Festivali)
            {523, 784, 659, 880, 784, 1046, 880, 659, 587, 784, 659, 523}
    };

    public synchronized void startBackgroundMusic(int melodyIndex) {
        this.currentMelodyIndex = Math.abs(melodyIndex) % MELODIES.length;
        if (playing) return;
        playing = true;

        musicThread = new Thread(new Runnable() {
            @Override
            public void run() {
                runMusicLoop();
            }
        }, "GameBgMusicThread");
        musicThread.start();
    }

    public void setMelodyIndex(int melodyIndex) {
        this.currentMelodyIndex = Math.abs(melodyIndex) % MELODIES.length;
    }

    public boolean toggleMute() {
        muted = !muted;
        return muted;
    }

    public boolean isMuted() {
        return muted;
    }

    public synchronized void stopBackgroundMusic() {
        playing = false;
        if (musicThread != null) {
            musicThread.interrupt();
            musicThread = null;
        }
        try {
            if (bgTrack != null) {
                bgTrack.stop();
                bgTrack.release();
                bgTrack = null;
            }
        } catch (Exception ignored) {
        }
    }

    private void runMusicLoop() {
        try {
            int minBuf = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
            );
            int noteDurationMs = 230;
            int samplesPerNote = (SAMPLE_RATE * noteDurationMs) / 1000;
            bgTrack = new AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    Math.max(minBuf, samplesPerNote * 2),
                    AudioTrack.MODE_STREAM
            );
            bgTrack.play();

            short[] buffer = new short[samplesPerNote];
            int step = 0;

            while (playing) {
                int[] notes = MELODIES[currentMelodyIndex % MELODIES.length];
                int freq = notes[step % notes.length];
                step++;

                if (muted) {
                    for (int i = 0; i < samplesPerNote; i++) {
                        buffer[i] = 0;
                    }
                } else {
                    for (int i = 0; i < samplesPerNote; i++) {
                        double t = (double) i / SAMPLE_RATE;
                        // Yumuşak zarf (Attack & Decay) ile hoş chiptune tınısı
                        double env = 1.0 - ((double) i / samplesPerNote) * 0.75;
                        if (i < 400) {
                            env *= ((double) i / 400.0);
                        }
                        double wave = Math.sin(2.0 * Math.PI * freq * t) * 0.7
                                + Math.sin(2.0 * Math.PI * (freq * 1.5) * t) * 0.2;
                        buffer[i] = (short) (wave * env * 2600);
                    }
                }
                if (bgTrack != null && playing) {
                    bgTrack.write(buffer, 0, samplesPerNote);
                }
            }
        } catch (Exception ignored) {
        }
    }

    public void playSuccessEffect() {
        if (muted) return;
        playShortEffectAsync(new int[]{523, 659, 784, 1046}, 75);
    }

    public void playWrongEffect() {
        if (muted) return;
        playShortEffectAsync(new int[]{311, 277}, 140);
    }

    private void playShortEffectAsync(final int[] freqs, final int noteMs) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    int samplesPerNote = (SAMPLE_RATE * noteMs) / 1000;
                    int totalSamples = samplesPerNote * freqs.length;
                    short[] pcm = new short[totalSamples];

                    for (int n = 0; n < freqs.length; n++) {
                        int f = freqs[n];
                        int offset = n * samplesPerNote;
                        for (int i = 0; i < samplesPerNote; i++) {
                            double t = (double) i / SAMPLE_RATE;
                            double env = 1.0 - ((double) i / samplesPerNote) * 0.5;
                            pcm[offset + i] = (short) (Math.sin(2.0 * Math.PI * f * t) * env * 6500);
                        }
                    }

                    AudioTrack sfx = new AudioTrack(
                            AudioManager.STREAM_MUSIC,
                            SAMPLE_RATE,
                            AudioFormat.CHANNEL_OUT_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                            totalSamples * 2,
                            AudioTrack.MODE_STATIC
                    );
                    sfx.write(pcm, 0, totalSamples);
                    sfx.play();
                    Thread.sleep((long) freqs.length * noteMs + 100L);
                    sfx.stop();
                    sfx.release();
                } catch (Exception ignored) {
                }
            }
        }).start();
    }
}
