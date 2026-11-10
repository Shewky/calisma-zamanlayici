package com.calismazamanlayici.veli;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

/**
 * Rahatlatıcı, uzun döngülü (64 adımlı / ~30-38 saniye), polifonik (akor + yumuşak melodi + eko/reverb)
 * akustik oyun müziği sentezleyicisi.
 * - Kulak tırmalayan kare dalgalar ve tiz sesler tamamen kaldırılmıştır.
 * - Akustik Piyano, Kalimba (Müzik Kutusu), Yumuşak Arp, Sıcak Rhodes ve Huzurlu Pad tınıları kullanılır.
 */
public class VeliGameMusicSynthesizer {

    private static final int SAMPLE_RATE = 22050;
    private volatile boolean playing = false;
    private volatile boolean muted = false;
    private volatile int currentMelodyIndex = 0;
    private Thread musicThread;
    private AudioTrack bgTrack;

    // Rahatlatıcı alt-orta oktav notaları (Hz):
    // C3=131, D3=147, E3=165, F3=175, G3=196, A3=220, B3=247
    // C4=262, D4=294, E4=330, F4=349, G4=392, A4=440, B4=494, C5=523, D5=587, E5=659
    // Her oyun için 64 adımlı (4 bölümlü A-B-C-D) uzun, dinlendirici melodiler:
    private static final int[][] MELODIES = new int[][]{
            // 0: Galaksi Sayı Avcısı (Uzay Ambient Piyano & Kalimba - Cmaj7 / Am7 / Fmaj7 / G6)
            {
                262, 330, 392, 494, 392, 330, 294, 0,   220, 262, 330, 440, 392, 330, 262, 0,
                175, 220, 262, 349, 330, 262, 220, 196, 196, 247, 294, 392, 330, 294, 262, 0,
                330, 392, 523, 494, 440, 392, 330, 294, 262, 330, 440, 392, 349, 330, 294, 0,
                175, 262, 330, 392, 349, 330, 294, 262, 196, 247, 294, 330, 294, 247, 262, 0
            },
            // 1: Gizemli Kelime Kalesi (Huzurlu Akustik Arp Valsi - Am / F / C / G)
            {
                220, 262, 330, 440, 330, 262, 220, 0,   175, 220, 262, 349, 262, 220, 175, 0,
                262, 330, 392, 523, 392, 330, 262, 0,   196, 247, 294, 392, 294, 247, 196, 0,
                440, 392, 330, 262, 294, 330, 262, 220, 349, 330, 262, 220, 247, 294, 247, 196,
                220, 262, 330, 392, 349, 330, 294, 262, 247, 220, 196, 220, 262, 247, 220, 0
            },
            // 2: Çılgın Laboratuvar Keşfi (Sıcak Lo-Fi Rhodes Piyano - Dm7 / G7 / Cmaj7 / Am7)
            {
                294, 349, 440, 523, 440, 349, 294, 0,   196, 247, 294, 349, 330, 294, 247, 0,
                262, 330, 392, 494, 440, 392, 330, 0,   220, 262, 330, 392, 349, 330, 262, 0,
                349, 440, 523, 494, 440, 392, 349, 330, 294, 349, 440, 392, 349, 330, 294, 262,
                220, 262, 330, 440, 392, 349, 330, 294, 262, 294, 330, 392, 330, 294, 262, 0
            },
            // 3: Zaman Gezgini Hazine Avı (Yumuşak Akustik Gitar & Flüt - G / Em / C / D)
            {
                196, 247, 294, 392, 330, 294, 247, 0,   165, 196, 247, 330, 294, 247, 196, 0,
                262, 330, 392, 440, 392, 330, 262, 0,   294, 330, 392, 440, 392, 330, 294, 0,
                392, 440, 494, 392, 330, 294, 247, 196, 262, 294, 330, 392, 330, 294, 262, 220,
                196, 247, 294, 392, 440, 392, 330, 294, 262, 294, 330, 294, 247, 220, 196, 0
            },
            // 4: Sihirli Köprü Parolası (Tatlı Ahşap Marimba & Çan - F / Dm / Bb / C)
            {
                175, 220, 262, 349, 330, 262, 220, 0,   294, 349, 440, 392, 349, 294, 262, 0,
                233, 294, 349, 466, 440, 349, 294, 0,   262, 330, 392, 440, 392, 330, 262, 0,
                349, 440, 523, 440, 392, 349, 330, 294, 262, 349, 440, 392, 349, 294, 262, 220,
                233, 294, 349, 440, 392, 349, 330, 294, 262, 294, 349, 330, 294, 262, 349, 0
            },
            // 5: İyilik Bahçesi Yıldızları (Doğa Piyanosu & Nenni Huzuru - C / G / Am / F)
            {
                262, 294, 330, 392, 330, 294, 262, 0,   196, 247, 294, 392, 330, 294, 247, 0,
                220, 262, 330, 440, 392, 330, 262, 0,   175, 220, 262, 349, 330, 294, 262, 0,
                330, 392, 440, 392, 349, 330, 294, 262, 220, 262, 330, 392, 349, 330, 294, 0,
                175, 220, 262, 330, 294, 262, 247, 196, 262, 294, 330, 294, 262, 247, 262, 0
            },
            // 6: Süper Kaptan Güvenli Rota (Akıcı Yumuşak Synth-Pop - Em / C / G / D)
            {
                165, 196, 247, 330, 294, 247, 196, 0,   262, 330, 392, 330, 294, 262, 247, 0,
                196, 247, 294, 392, 330, 294, 247, 0,   294, 330, 392, 440, 392, 330, 294, 0,
                330, 392, 494, 440, 392, 330, 294, 247, 262, 330, 392, 440, 392, 330, 294, 0,
                196, 247, 294, 392, 330, 294, 262, 247, 165, 196, 247, 330, 294, 247, 165, 0
            },
            // 7: Barış Adası Kahramanları (Sıcak Akustik Ada Melodisi - C / F / G / C)
            {
                262, 330, 392, 330, 349, 440, 349, 262, 196, 247, 294, 392, 330, 294, 262, 0,
                262, 330, 392, 523, 440, 392, 349, 330, 294, 349, 392, 440, 392, 330, 294, 0,
                330, 262, 349, 262, 392, 330, 294, 247, 220, 262, 349, 330, 294, 262, 247, 196,
                262, 294, 330, 349, 392, 440, 392, 330, 294, 330, 294, 247, 262, 330, 262, 0
            },
            // 8: Süper Yıldız Zıplama Macerası (Keyifli, Yumuşak Macera Kalimba & Pad Melodisi)
            {
                262, 330, 392, 523, 494, 392, 330, 262, 294, 349, 440, 523, 440, 349, 294, 0,
                220, 262, 330, 440, 392, 330, 262, 220, 196, 247, 294, 392, 349, 294, 247, 0,
                349, 440, 523, 440, 392, 330, 262, 330, 294, 349, 440, 392, 349, 294, 247, 196,
                262, 330, 392, 440, 523, 494, 440, 392, 349, 330, 294, 247, 262, 330, 262, 0
            }
    };

    // Arka plan yumuşak akor kök frekansları (her 8 adımda bir değişen sıcak pad akorları)
    private static final int[][] CHORD_ROOTS = new int[][]{
            {131, 131, 110, 110, 175, 175, 196, 196},
            {110, 110, 175, 175, 131, 131, 196, 196},
            {147, 147, 196, 196, 131, 131, 110, 110},
            {196, 196, 165, 165, 131, 131, 147, 147},
            {175, 175, 147, 147, 116, 116, 131, 131},
            {131, 131, 196, 196, 110, 110, 175, 175},
            {165, 165, 131, 131, 196, 196, 147, 147},
            {131, 131, 175, 175, 196, 196, 131, 131},
            {131, 147, 110, 196, 175, 147, 131, 196}
    };

    // Rahatlatıcı, telaşsız nota süreleri (ms): 64 nota x ~460ms = ~30 saniyelik uzun döngü!
    private static final int[] TEMPO_MS = new int[]{
            440, // 0: Uzay Ambient
            480, // 1: Kelime Kalesi Arp
            450, // 2: Laboratuvar Lo-Fi Rhodes
            460, // 3: Zaman Gezgini Akustik
            430, // 4: Sihirli Köprü Marimba
            520, // 5: İyilik Bahçesi Huzurlu Piyano
            420, // 6: Güvenli Rota Yumuşak Pop
            460, // 7: Barış Adası Akustik
            390  // 8: Zıplama Macerası Keyifli Kalimba
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
            int maxSamples = (SAMPLE_RATE * 650) / 1000;
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
            // Yumuşak akustik eko (reverb) tamponu (260 ms gecikmeli sıcak yankı)
            int echoLen = (SAMPLE_RATE * 260) / 1000;
            float[] echoRing = new float[echoLen];
            int echoPos = 0;

            int step = 0;
            int cycle = 0;
            double padPhase1 = 0.0;
            double padPhase2 = 0.0;

            while (playing) {
                int idx = currentMelodyIndex % MELODIES.length;
                int[] notes = MELODIES[idx];
                int[] roots = CHORD_ROOTS[idx];
                int noteDurationMs = TEMPO_MS[idx];
                int samplesPerNote = Math.min(maxSamples, (SAMPLE_RATE * noteDurationMs) / 1000);

                int noteStep = step % notes.length;
                if (noteStep == 0 && step > 0) {
                    cycle++;
                }
                int freq = notes[noteStep];
                // Her döngüde hafif oktav/harmoni varyasyonu ekleyerek müziğin tekdüzeleşmesini önle
                if (cycle % 2 == 1 && (noteStep % 4 == 2) && freq > 0) {
                    freq = (int) (freq * 1.25); // Yumuşak üçlü armoni varyasyonu
                }

                int chordRoot = roots[(noteStep / 8) % roots.length];
                int chordFifth = (chordRoot * 3) / 2;
                step++;

                if (muted) {
                    for (int i = 0; i < samplesPerNote; i++) {
                        buffer[i] = 0;
                    }
                } else {
                    int attackSamples = Math.min(900, samplesPerNote / 5);
                    int releaseSamples = Math.min(900, samplesPerNote / 5);

                    for (int i = 0; i < samplesPerNote; i++) {
                        double t = (double) i / SAMPLE_RATE;
                        double progress = (double) i / samplesPerNote;

                        // Tıklama/çıtırtı önleyici yumuşak zarf (Soft Attack + Doğal Akustik Sönümlenme)
                        double env = Math.exp(-3.1 * progress);
                        if (i < attackSamples) {
                            env *= ((double) i / attackSamples);
                        } else if (i > samplesPerNote - releaseSamples) {
                            env *= ((double) (samplesPerNote - i) / releaseSamples);
                        }

                        // Arka plan sıcak Pad akoru (kesintisiz faz ile yumuşak ambiyans)
                        padPhase1 += (2.0 * Math.PI * chordRoot) / SAMPLE_RATE;
                        padPhase2 += (2.0 * Math.PI * chordFifth) / SAMPLE_RATE;
                        if (padPhase1 > Math.PI * 200) padPhase1 -= Math.PI * 200;
                        if (padPhase2 > Math.PI * 200) padPhase2 -= Math.PI * 200;

                        double warmPad = (Math.sin(padPhase1) * 0.22 + Math.sin(padPhase2) * 0.14);

                        double melodyWave = 0.0;
                        if (freq > 0) {
                            // Akustik Piyano / Kalimba / Arp tınısı (yumuşak temel dalga + sıcak 2. ve 3. harmonik)
                            double fundamental = Math.sin(2.0 * Math.PI * freq * t);
                            double secondHarm = Math.sin(2.0 * Math.PI * (freq * 2.0) * t) * Math.exp(-5.5 * progress) * 0.22;
                            double thirdHarm = Math.sin(2.0 * Math.PI * (freq * 3.0) * t) * Math.exp(-8.0 * progress) * 0.06;
                            melodyWave = (fundamental * 0.72 + secondHarm + thirdHarm) * env;
                        }

                        float drySample = (float) (melodyWave * 0.75 + warmPad * 0.25);
                        float echoed = drySample + echoRing[echoPos] * 0.34f;
                        echoRing[echoPos] = echoed;
                        echoPos = (echoPos + 1) % echoLen;

                        // Yumuşak ses seviyesi (rahatsız etmeyen dinlendirici arka plan düzeyi)
                        int pcmVal = (int) (echoed * 1650.0f);
                        if (pcmVal > 12000) pcmVal = 12000;
                        if (pcmVal < -12000) pcmVal = -12000;
                        buffer[i] = (short) pcmVal;
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
        // Yumuşak akustik arp akoru (C4 - E4 - G4 - C5)
        playShortEffectAsync(new int[]{262, 330, 392, 523}, 85, 2400);
    }

    public void playWrongEffect() {
        if (muted) return;
        // Nazik, yumuşak uyarı tonu (sert buzzer yerine sıcak ahşap marimba ikilisi)
        playShortEffectAsync(new int[]{247, 220}, 140, 2000);
    }

    public void playJumpEffect() {
        if (muted) return;
        // Yumuşak su damlası / kalimba zıplama sesi
        playShortEffectAsync(new int[]{294, 392, 494}, 45, 1800);
    }

    public void playCoinEffect() {
        if (muted) return;
        // Tatlı müzik kutusu yıldız sesi
        playShortEffectAsync(new int[]{440, 587}, 75, 2100);
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
                        int attack = Math.min(250, samplesPerNote / 4);
                        for (int i = 0; i < samplesPerNote; i++) {
                            double t = (double) i / SAMPLE_RATE;
                            double progress = (double) i / samplesPerNote;
                            double env = Math.exp(-3.5 * progress);
                            if (i < attack) {
                                env *= ((double) i / attack);
                            }
                            double wave = Math.sin(2.0 * Math.PI * f * t) * 0.8
                                    + Math.sin(2.0 * Math.PI * (f * 2.0) * t) * 0.2 * Math.exp(-5.0 * progress);
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
                    Thread.sleep((long) freqs.length * noteMs + 100L);
                    sfx.stop();
                    sfx.release();
                } catch (Exception ignored) {
                }
            }
        }).start();
    }
}
