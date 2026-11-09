package com.calismazamanlayici.veli;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

/**
 * Her oyunun atmosferine özel farklı melodi, farklı tempo (BPM) ve farklı enstrüman tınısı
 * (8-bit Kare Dalga, Arp Tınısı, Flüt Vibrato, Marimba, Yarış Bass-Synth, Platformer Koşu)
 * üreten çok kanallı gerçek zamanlı müzik sentezleyici.
 */
public class VeliGameMusicSynthesizer {

    private static final int SAMPLE_RATE = 22050;
    private volatile boolean playing = false;
    private volatile boolean muted = false;
    private volatile int currentMelodyIndex = 0;
    private Thread musicThread;
    private AudioTrack bgTrack;

    // 9 farklı oyun için 16 notalık özgün melodiler (Hz)
    private static final int[][] MELODIES = new int[][]{
            // 0: Galaksi Sayı Avcısı (Hızlı Uzay Synth Arpej)
            {523, 659, 784, 1046, 987, 784, 659, 523, 587, 698, 880, 1175, 1046, 880, 698, 587},
            // 1: Gizemli Kelime Kalesi (Ortaçağ Kalesi Arp Valsi)
            {440, 523, 659, 880, 784, 659, 523, 440, 392, 494, 587, 784, 659, 523, 494, 440},
            // 2: Çılgın Laboratuvar Keşfi (Elektronik Bilim Laboratuvarı Ritmi)
            {587, 0, 698, 784, 880, 0, 784, 698, 587, 698, 880, 1046, 880, 784, 698, 587},
            // 3: Zaman Gezgini Hazine Avı (Destansı Keşif Marşı)
            {392, 392, 523, 659, 784, 659, 523, 587, 659, 659, 784, 880, 784, 659, 587, 523},
            // 4: Sihirli Köprü Parolası (Neşeli Marimba Pop)
            {523, 659, 587, 784, 659, 880, 784, 1046, 880, 784, 659, 587, 523, 659, 587, 523},
            // 5: İyilik Bahçesi Yıldızları (Huzurlu Doğa Flütü)
            {523, 587, 659, 784, 659, 587, 523, 440, 523, 587, 659, 880, 784, 659, 587, 523},
            // 6: Süper Kaptan Güvenli Rota (Yüksek Tempolu Yarış Heyecanı)
            {659, 659, 784, 880, 987, 880, 784, 659, 784, 784, 880, 987, 1046, 987, 880, 784},
            // 7: Barış Adası Kahramanları (Tropikal Ada Kalipso)
            {523, 784, 659, 523, 587, 880, 784, 587, 659, 987, 880, 659, 784, 659, 587, 523},
            // 8: Süper Yıldız Zıplama Macerası (İlerlemeli Zıplamalı Klasik Platformer Koşu Müziği!)
            {659, 659, 0, 659, 0, 523, 659, 0, 784, 0, 392, 0, 523, 392, 330, 440}
    };

    // Her oyunun kendine özgü nota hızı (ms)
    private static final int[] TEMPO_MS = new int[]{
            165, // 0: Uzay (Hızlı)
            250, // 1: Kale (Masalsı Vals)
            180, // 2: Laboratuvar (Ritmik)
            215, // 3: Hazine Avı (Marş)
            190, // 4: Sihirli Köprü (Pop)
            270, // 5: İyilik Bahçesi (Akıcı Flüt)
            145, // 6: Güvenli Rota (Hızlı Yarış)
            200, // 7: Barış Adası (Ada Ritmi)
            150  // 8: Zıplama Macerası (Dinamik Platformer)
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
            int maxSamples = (SAMPLE_RATE * 300) / 1000;
            bgTrack = new AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    Math.max(minBuf, maxSamples * 2),
                    AudioTrack.MODE_STREAM
            );
            bgTrack.play();

            short[] buffer = new short[maxSamples];
            int step = 0;

            while (playing) {
                int idx = currentMelodyIndex % MELODIES.length;
                int[] notes = MELODIES[idx];
                int noteDurationMs = TEMPO_MS[idx];
                int samplesPerNote = (SAMPLE_RATE * noteDurationMs) / 1000;

                int freq = notes[step % notes.length];
                int bassFreq = (freq > 0) ? (freq / 2) : 196;
                step++;

                if (muted) {
                    for (int i = 0; i < samplesPerNote; i++) {
                        buffer[i] = 0;
                    }
                } else {
                    for (int i = 0; i < samplesPerNote; i++) {
                        double t = (double) i / SAMPLE_RATE;
                        double progress = (double) i / samplesPerNote;
                        double env = 1.0 - progress * 0.72;
                        if (i < 300) {
                            env *= ((double) i / 300.0);
                        }

                        double wave = 0.0;
                        if (freq > 0) {
                            if (idx == 0 || idx == 6 || idx == 8) {
                                // Chiptune Kare + Bass armonisi (Uzay, Yarış, Zıplama Oyunu)
                                double sq = Math.sin(2.0 * Math.PI * freq * t) >= 0 ? 0.65 : -0.65;
                                double sub = Math.sin(2.0 * Math.PI * bassFreq * t) * 0.35;
                                wave = sq + sub;
                            } else if (idx == 1 || idx == 5) {
                                // Yumuşak Flüt / Arp Vibrato tınısı (Kelime Kalesi, İyilik Bahçesi)
                                double vib = Math.sin(2.0 * Math.PI * 6.0 * t) * 3.0;
                                wave = Math.sin(2.0 * Math.PI * (freq + vib) * t) * 0.8
                                        + Math.sin(2.0 * Math.PI * (freq * 2.0) * t) * 0.2;
                            } else {
                                // Zengin Harmonik Marimba / Keşif tınısı
                                wave = Math.sin(2.0 * Math.PI * freq * t) * 0.65
                                        + Math.sin(2.0 * Math.PI * (freq * 1.5) * t) * 0.25
                                        + Math.sin(2.0 * Math.PI * bassFreq * t) * 0.2;
                            }
                        } else {
                            // Ritmik alt bas vuruşu
                            wave = Math.sin(2.0 * Math.PI * bassFreq * t) * 0.3 * (1.0 - progress);
                        }
                        buffer[i] = (short) (wave * env * 2500);
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
        playShortEffectAsync(new int[]{523, 659, 784, 1046}, 70);
    }

    public void playWrongEffect() {
        if (muted) return;
        playShortEffectAsync(new int[]{311, 261}, 130);
    }

    public void playJumpEffect() {
        if (muted) return;
        playShortEffectAsync(new int[]{330, 440, 587, 784}, 38);
    }

    public void playCoinEffect() {
        if (muted) return;
        playShortEffectAsync(new int[]{987, 1318}, 60);
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
                            double env = 1.0 - ((double) i / samplesPerNote) * 0.45;
                            pcm[offset + i] = (short) (Math.sin(2.0 * Math.PI * f * t) * env * 6200);
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
                    Thread.sleep((long) freqs.length * noteMs + 90L);
                    sfx.stop();
                    sfx.release();
                } catch (Exception ignored) {
                }
            }
        }).start();
    }
}
