package com.calismazamanlayici.veli;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

/**
 * Ferah, nefes alan (bol es/sessizlik içeren), boğucu alt uğultusu (drone) OLMAYAN
 * ve her oyunda tamamen farklı enstrüman karakteri, farklı ritim kalıbı ve farklı nota süreleri
 * kullanan doğal akustik ses & müzik motoru.
 *
 * Enstrüman Profilleri:
 * 0 - Galaksi Sayı Avcısı: Kristal Müzik Kutusu (Celesta) - Uzun esli, ferah pentatonik yıldız tınıları
 * 1 - Gizemli Kelime Kalesi: Telli Akustik Arp (Plucked Harp) - 3/4 vals ritmi
 * 2 - Çılgın Laboratuvar Keşfi: Bambu Marimba & Su Damlası (Kısa, tok ahşap vuruşlar, bol sessizlik)
 * 3 - Zaman Gezgini Hazine Avı: Bambu Flüt & Akustik Telli Eşlik
 * 4 - Sihirli Köprü Parolası: Neşeli Pizzicato / Ukulele (Kısa zıplayan teller)
 * 5 - İyilik Bahçesi Yıldızları: Doğa Huzuru (Tatlı Kuş Cıvıltısı + Seyrek Yumuşak Piyano)
 * 6 - Süper Kaptan Güvenli Rota: Ferah Akustik Gitar Yürüyüşü
 * 7 - Barış Adası Kahramanları: Tropikal Kalimba (Başparmak Piyanosu)
 * 8 - Süper Yıldız Zıplama Macerası: Neşeli Staccato Macera Ritmi (Kısa ve net zıplama notaları)
 */
public class VeliGameMusicSynthesizer {

    private static final int SAMPLE_RATE = 22050;
    private volatile boolean playing = false;
    private volatile boolean muted = false;
    private volatile int currentMelodyIndex = 0;
    private Thread musicThread;
    private AudioTrack bgTrack;

    // Frekans çiftleri: {Frekans_Hz, Süre_Ms}
    // 0 Hz = Sessizlik (Nefes alma boşluğu - kalp sıkışıklığını önler ve doğal müzik hissi verir)
    private static final int[][][] COMPOSITIONS = new int[][][]{
            // 0: Galaksi Sayı Avcısı - Kristal Müzik Kutusu (Ferah Majör Pentatonik, bol dinlenme boşluklu)
            {
                {523, 320}, {659, 320}, {784, 480}, {0, 650},
                {880, 320}, {784, 320}, {659, 480}, {0, 750},
                {587, 320}, {659, 320}, {523, 520}, {0, 850},
                {392, 320}, {523, 320}, {659, 550}, {0, 900},
                {784, 300}, {880, 300}, {1046, 500}, {0, 700},
                {880, 320}, {784, 320}, {587, 480}, {0, 800},
                {659, 320}, {587, 320}, {523, 600}, {0, 1100}
            },
            // 1: Gizemli Kelime Kalesi - Akustik Arp Valsi (3/4 Ritim: Uzun-Kısa-Kısa)
            {
                {330, 460}, {392, 240}, {494, 240}, {659, 480}, {0, 460},
                {587, 460}, {494, 240}, {392, 240}, {330, 520}, {0, 600},
                {294, 460}, {370, 240}, {440, 240}, {587, 480}, {0, 460},
                {494, 460}, {392, 240}, {330, 240}, {294, 520}, {0, 650},
                {392, 450}, {494, 240}, {587, 240}, {784, 500}, {0, 500},
                {659, 450}, {494, 240}, {392, 240}, {330, 600}, {0, 950}
            },
            // 2: Çılgın Laboratuvar Keşfi - Bambu Ksiliyon / Su Damlası (Kısa tok ahşap notalar + sessizlik)
            {
                {392, 160}, {0, 180}, {494, 160}, {587, 180}, {0, 450},
                {659, 160}, {0, 180}, {587, 160}, {494, 180}, {0, 550},
                {392, 150}, {440, 150}, {494, 150}, {587, 220}, {0, 600},
                {784, 160}, {0, 200}, {659, 160}, {587, 220}, {0, 700},
                {494, 150}, {587, 150}, {494, 150}, {392, 240}, {0, 850}
            },
            // 3: Zaman Gezgini Hazine Avı - Yumuşak Bambu Flüt & Telli Gitar
            {
                {294, 550}, {330, 300}, {392, 600}, {0, 500},
                {440, 500}, {392, 300}, {330, 600}, {0, 650},
                {262, 500}, {294, 300}, {330, 550}, {0, 500},
                {392, 450}, {330, 300}, {294, 650}, {0, 850},
                {440, 520}, {494, 300}, {587, 620}, {0, 550},
                {494, 450}, {440, 300}, {392, 650}, {0, 950}
            },
            // 4: Sihirli Köprü Parolası - Neşeli Pizzicato / Ukulele (Zıplayan hafif teller)
            {
                {349, 140}, {0, 140}, {440, 140}, {0, 140}, {523, 200}, {0, 420},
                {587, 140}, {0, 140}, {523, 140}, {0, 140}, {440, 220}, {0, 500},
                {392, 140}, {0, 140}, {494, 140}, {0, 140}, {587, 200}, {0, 420},
                {523, 150}, {440, 150}, {349, 260}, {0, 750},
                {523, 140}, {587, 140}, {698, 220}, {0, 350},
                {587, 140}, {523, 140}, {440, 260}, {0, 850}
            },
            // 5: İyilik Bahçesi Yıldızları - Doğa Huzuru (Kuş Cıvıltısı + Seyrek Dinlendirici Piyano)
            {
                {262, 650}, {330, 650}, {392, 800}, {0, 900},
                {-1, 420}, {0, 700}, // -1 = Tatlı kuş cıvıltısı efekti!
                {349, 650}, {330, 650}, {294, 800}, {0, 950},
                {262, 650}, {294, 650}, {330, 850}, {0, 850},
                {-1, 420}, {0, 750},
                {392, 650}, {330, 650}, {262, 900}, {0, 1200}
            },
            // 6: Süper Kaptan Güvenli Rota - Ferah Akustik Gitar Melodisi
            {
                {294, 240}, {370, 240}, {440, 340}, {0, 260},
                {392, 240}, {494, 240}, {587, 360}, {0, 420},
                {440, 240}, {370, 240}, {294, 380}, {0, 480},
                {330, 240}, {392, 240}, {494, 340}, {0, 300},
                {440, 240}, {370, 240}, {294, 480}, {0, 800}
            },
            // 7: Barış Adası Kahramanları - Tropikal Kalimba (Başparmak Piyanosu)
            {
                {392, 280}, {587, 280}, {494, 380}, {0, 480},
                {330, 280}, {494, 280}, {392, 380}, {0, 560},
                {294, 280}, {440, 280}, {392, 380}, {0, 480},
                {330, 280}, {392, 280}, {294, 460}, {0, 850},
                {494, 260}, {587, 260}, {659, 400}, {0, 500},
                {587, 280}, {494, 280}, {392, 480}, {0, 900}
            },
            // 8: Süper Yıldız Zıplama Macerası - Neşeli, Net, Boğucu Olmayan Macera Melodisi
            {
                {392, 180}, {0, 80}, {494, 180}, {0, 80}, {587, 240}, {0, 220},
                {659, 180}, {0, 80}, {587, 180}, {0, 80}, {494, 260}, {0, 360},
                {392, 180}, {440, 180}, {494, 220}, {0, 200},
                {587, 200}, {494, 200}, {392, 300}, {0, 520},
                {523, 180}, {0, 80}, {659, 180}, {0, 80}, {784, 260}, {0, 280},
                {659, 180}, {587, 180}, {494, 240}, {0, 260},
                {440, 180}, {494, 180}, {392, 340}, {0, 650}
            }
    };

    public synchronized void startBackgroundMusic(int melodyIndex) {
        this.currentMelodyIndex = Math.abs(melodyIndex) % COMPOSITIONS.length;
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
        this.currentMelodyIndex = Math.abs(melodyIndex) % COMPOSITIONS.length;
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

    public void stopMusic() {
        stopBackgroundMusic();
    }

    private void runMusicLoop() {
        try {
            int minBuf = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
            );
            int maxSamples = (SAMPLE_RATE * 1300) / 1000;
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
            int lastIdx = -1;

            while (playing) {
                int idx = currentMelodyIndex % COMPOSITIONS.length;
                if (idx != lastIdx) {
                    step = 0;
                    lastIdx = idx;
                }
                int[][] seq = COMPOSITIONS[idx];
                int[] item = seq[step % seq.length];
                int freq = item[0];
                int durMs = item[1];
                step++;

                int samples = Math.min(maxSamples, (SAMPLE_RATE * durMs) / 1000);
                if (muted || freq == 0) {
                    for (int i = 0; i < samples; i++) {
                        buffer[i] = 0;
                    }
                } else if (freq == -1) {
                    // Track 5 (İyilik Bahçesi): Tatlı doğal kuş cıvıltısı
                    for (int i = 0; i < samples; i++) {
                        double t = (double) i / SAMPLE_RATE;
                        double p = (double) i / samples;
                        double env = Math.sin(Math.PI * p) * Math.exp(-2.0 * p);
                        double chirpFreq = 1650.0 + Math.sin(2.0 * Math.PI * 14.0 * t) * 280.0 + p * 320.0;
                        buffer[i] = (short) (Math.sin(2.0 * Math.PI * chirpFreq * t) * env * 650);
                    }
                } else {
                    int attack = Math.min(350, samples / 6);
                    for (int i = 0; i < samples; i++) {
                        double t = (double) i / SAMPLE_RATE;
                        double p = (double) i / samples;
                        double env;
                        double wave;

                        if (idx == 0) {
                            // 0: Kristal Müzik Kutusu (Celesta) - Berrak çan سönümlenmesi
                            env = Math.exp(-4.2 * p);
                            wave = Math.sin(2.0 * Math.PI * freq * t) * 0.82
                                    + Math.sin(2.0 * Math.PI * (freq * 3.0) * t) * Math.exp(-9.0 * p) * 0.18;
                        } else if (idx == 1) {
                            // 1: Telli Akustik Arp - Yumuşak arpej tınısı
                            env = Math.exp(-3.6 * p);
                            wave = Math.sin(2.0 * Math.PI * freq * t) * 0.75
                                    + Math.sin(2.0 * Math.PI * (freq * 2.0) * t) * Math.exp(-6.0 * p) * 0.25;
                        } else if (idx == 2 || idx == 4) {
                            // 2 & 4: Bambu Marimba / Pizzicato Ukulele - Çok kısa, tok ve ferah
                            env = Math.exp(-7.5 * p);
                            wave = Math.sin(2.0 * Math.PI * freq * t) * 0.88
                                    + Math.sin(2.0 * Math.PI * (freq * 4.0) * t) * Math.exp(-14.0 * p) * 0.12;
                        } else if (idx == 3) {
                            // 3: Yumuşak Bambu Flüt (Nefesli yumuşak giriş)
                            env = Math.sin(Math.PI * p) * Math.exp(-1.4 * p);
                            double vib = Math.sin(2.0 * Math.PI * 4.5 * t) * 1.8;
                            wave = Math.sin(2.0 * Math.PI * (freq + vib) * t) * 0.85
                                    + Math.sin(2.0 * Math.PI * (freq * 2.0) * t) * 0.15;
                        } else if (idx == 5) {
                            // 5: Huzurlu Doğa Piyanosu
                            env = Math.exp(-3.2 * p);
                            wave = Math.sin(2.0 * Math.PI * freq * t) * 0.78
                                    + Math.sin(2.0 * Math.PI * (freq * 2.0) * t) * Math.exp(-5.0 * p) * 0.22;
                        } else {
                            // 6, 7, 8: Kalimba / Akustik Macera Teli
                            env = Math.exp(-5.2 * p);
                            wave = Math.sin(2.0 * Math.PI * freq * t) * 0.80
                                    + Math.sin(2.0 * Math.PI * (freq * 2.0) * t) * Math.exp(-8.0 * p) * 0.20;
                        }

                        if (i < attack) {
                            env *= ((double) i / attack);
                        }
                        if (i > samples - attack) {
                            env *= ((double) (samples - i) / attack);
                        }

                        // Çok hafif ve yumuşak ses yüksekliği (arka planda asla yormaz)
                        buffer[i] = (short) (wave * env * 1150);
                    }
                }

                if (bgTrack != null && playing) {
                    bgTrack.write(buffer, 0, samples);
                }
            }
        } catch (Exception ignored) {
        }
    }

    public void playSuccessEffect() {
        if (muted) return;
        playShortEffectAsync(new int[]{392, 494, 587, 784}, 65, 1800);
    }

    public void playWrongEffect() {
        if (muted) return;
        playShortEffectAsync(new int[]{294, 247}, 110, 1500);
    }

    public void playJumpEffect() {
        if (muted) return;
        playShortEffectAsync(new int[]{330, 440, 587}, 38, 1400);
    }

    public void playCoinEffect() {
        if (muted) return;
        playShortEffectAsync(new int[]{523, 659}, 60, 1600);
    }

    public void playCountdownTick(boolean isGo) {
        if (muted) return;
        if (isGo) {
            playShortEffectAsync(new int[]{523, 659, 784}, 55, 1700);
        } else {
            playShortEffectAsync(new int[]{440}, 70, 1400);
        }
    }

    private void playShortEffectAsync(final int[] freqs, final int noteMs, final int amplitude) {
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
                        int attack = Math.min(200, samplesPerNote / 4);
                        for (int i = 0; i < samplesPerNote; i++) {
                            double t = (double) i / SAMPLE_RATE;
                            double progress = (double) i / samplesPerNote;
                            double env = Math.exp(-4.0 * progress);
                            if (i < attack) {
                                env *= ((double) i / attack);
                            }
                            double wave = Math.sin(2.0 * Math.PI * f * t) * 0.85
                                    + Math.sin(2.0 * Math.PI * (f * 2.0) * t) * 0.15 * Math.exp(-6.0 * progress);
                            pcm[offset + i] = (short) (wave * env * amplitude);
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
