package com.calismazamanlayici.app;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import java.util.Random;

/**
 * Hafif, Oyun Dostu, Yorucu Olmayan Akustik Ritim & Melodi Motoru (v5.4):
 * - Yoğun "full enstrümantal konser/resital" hissi veren üst üste binmiş uzun akor duvarları kaldırıldı.
 * - Yerine oyun atmosferini destekleyen, hafif, tekil ve net akustik dokunuşlar (Ahşap Marimba,
 *   Kısa Pizzicato Tel, Yumuşak Kalimba ve Hafif Ahşap Ritim/Tokmak) kullanıldı.
 */
public class GameMusicSynthesizer {

    private static final int SAMPLE_RATE = 22050;
    private static final int MAX_VOICES = 4;

    private volatile boolean playing = false;
    private volatile boolean muted = false;
    private volatile int currentMelodyIndex = 0;
    private volatile boolean racingAudioActive = false;
    private volatile float racingSpeedRatio = 0f;
    private volatile int racingLevel = 1;
    private double enginePhase = 0.0;
    private Thread musicThread;
    private AudioTrack bgTrack;

    private static final int INST_PIZZICATO_STRING = 0; // Kısa, neşeli parmakla çekilen akustik tel
    private static final int INST_WOOD_MARIMBA = 1;     // Tok ve hafif ahşap marimba
    private static final int INST_SOFT_KALIMBA = 2;     // Yumuşak kalimba
    private static final int INST_SOFT_PIANO = 3;       // Kısa sönümlü hafif akustik piyano
    private static final int INST_WOOD_PERC = 4;        // Hafif ahşap ritim tıkırtısı (tokmak)

    private static class AcousticVoice {
        boolean active = false;
        int instrument = INST_WOOD_MARIMBA;
        int samplePos = 0;
        int totalSamples = 0;
        float amplitude = 0.22f;

        float[] stringLine;
        int lineLen = 0;
        int lineIdx = 0;
        float damping = 0.986f;
        float bodyState = 0f;

        double freq = 261.63;
        double phase1 = 0, phase2 = 0;

        void trigger(int inst, int freqHz, int sustainMs, float amp, Random rng) {
            this.instrument = inst;
            this.freq = freqHz;
            this.samplePos = 0;
            this.totalSamples = (SAMPLE_RATE * sustainMs) / 1000;
            this.amplitude = amp;
            this.bodyState = 0f;

            if (inst == INST_PIZZICATO_STRING) {
                this.lineLen = Math.max(12, SAMPLE_RATE / Math.max(80, freqHz));
                if (this.stringLine == null || this.stringLine.length < this.lineLen + 2) {
                    this.stringLine = new float[this.lineLen + 4];
                }
                float prev = 0f;
                for (int i = 0; i < this.lineLen; i++) {
                    double posRatio = (double) i / this.lineLen;
                    float tri = (posRatio < 0.4) ? (float) (posRatio / 0.4) : (float) ((1.0 - posRatio) / 0.6);
                    float noise = (rng.nextFloat() * 2f - 1f) * 0.30f;
                    float raw = tri * 0.70f + noise;
                    prev = prev + 0.38f * (raw - prev);
                    this.stringLine[i] = prev;
                }
                this.lineIdx = 0;
                // Kısa, net pizzicato sönümlenmesi (uzun uğultu yapmaz)
                this.damping = 0.985f;
            } else {
                this.phase1 = 0;
                this.phase2 = 0;
            }
            this.active = true;
        }

        float nextSample() {
            if (!active) return 0f;
            if (samplePos >= totalSamples) {
                active = false;
                return 0f;
            }

            float out;
            double progress = (double) samplePos / totalSamples;

            if (instrument == INST_PIZZICATO_STRING) {
                int nextIdx = (lineIdx + 1) % lineLen;
                float curr = stringLine[lineIdx];
                float next = stringLine[nextIdx];
                stringLine[lineIdx] = (0.55f * curr + 0.45f * next) * damping;
                lineIdx = nextIdx;
                bodyState = bodyState * 0.80f + curr * 0.20f;
                out = (curr * 0.60f + bodyState * 0.40f) * (float) Math.exp(-3.5 * progress);
            } else if (instrument == INST_WOOD_PERC) {
                // Hafif ahşap ritim vuruşu (Woodblock / Shaker hissi)
                phase1 += (2.0 * Math.PI * freq) / SAMPLE_RATE;
                phase2 += (2.0 * Math.PI * (freq * 2.4)) / SAMPLE_RATE;
                double env = Math.exp(-22.0 * progress);
                out = (float) ((Math.sin(phase1) * 0.75 + Math.sin(phase2) * 0.25) * env);
            } else if (instrument == INST_WOOD_MARIMBA) {
                phase1 += (2.0 * Math.PI * freq) / SAMPLE_RATE;
                phase2 += (2.0 * Math.PI * (freq * 3.98)) / SAMPLE_RATE;
                double envFund = Math.exp(-7.5 * progress);
                double envWood = Math.exp(-24.0 * progress);
                double attack = (samplePos < 80) ? ((double) samplePos / 80.0) : 1.0;
                out = (float) ((Math.sin(phase1) * 0.85 * envFund + Math.sin(phase2) * 0.15 * envWood) * attack);
            } else if (instrument == INST_SOFT_KALIMBA) {
                phase1 += (2.0 * Math.PI * freq) / SAMPLE_RATE;
                phase2 += (2.0 * Math.PI * (freq * 2.0)) / SAMPLE_RATE;
                double env = Math.exp(-6.0 * progress);
                double attack = (samplePos < 100) ? ((double) samplePos / 100.0) : 1.0;
                out = (float) ((Math.sin(phase1) * 0.82 + Math.sin(phase2) * 0.18 * Math.exp(-10.0 * progress)) * env * attack);
            } else {
                // INST_SOFT_PIANO
                phase1 += (2.0 * Math.PI * freq) / SAMPLE_RATE;
                phase2 += (2.0 * Math.PI * (freq * 2.001)) / SAMPLE_RATE;
                double env = Math.exp(-5.0 * progress);
                double attack = (samplePos < 140) ? ((double) samplePos / 140.0) : 1.0;
                out = (float) ((Math.sin(phase1) * 0.80 + Math.sin(phase2) * 0.20 * Math.exp(-8.0 * progress)) * env * attack);
            }

            samplePos++;
            return out * amplitude;
        }
    }

    /**
     * Hafif, nefes alan, tek sesli/ritmik oyun motifleri:
     * {Melodi_Hz, Ahşap_Ritim_Hz (0=Yok), Nota_Sustain_Ms, Adım_Bekleme_Ms}
     */
    private static final int[][][] SCORES = new int[][][]{
            // 0: Galaksi Sayı Avcısı (Yumuşak Kalimba - Seyrek, ferah yıldız dokunuşları)
            {
                {330, 0, 420, 380}, {392, 0, 420, 380}, {523, 0, 500, 650}, {0, 0, 0, 550},
                {440, 0, 420, 380}, {392, 0, 420, 380}, {330, 0, 500, 700}, {0, 0, 0, 650},
                {294, 0, 400, 380}, {349, 0, 400, 380}, {392, 0, 500, 650}, {0, 0, 0, 600},
                {330, 0, 400, 380}, {294, 0, 400, 380}, {262, 0, 550, 800}, {0, 0, 0, 850}
            },
            // 1: Gizemli Kelime Kalesi (Kısa Pizzicato Tel - Zarif, aralıklı)
            {
                {220, 0, 380, 340}, {262, 0, 350, 340}, {330, 0, 420, 520}, {0, 0, 0, 480},
                {294, 0, 380, 340}, {262, 0, 350, 340}, {220, 0, 450, 600}, {0, 0, 0, 600},
                {262, 0, 380, 340}, {330, 0, 350, 340}, {392, 0, 420, 520}, {0, 0, 0, 500},
                {330, 0, 380, 340}, {294, 0, 350, 340}, {220, 0, 480, 750}, {0, 0, 0, 800}
            },
            // 2: Çılgın Laboratuvar Keşfi (Ahşap Marimba - Kısa ritmik damlalar)
            {
                {294, 180, 260, 280}, {0, 0, 0, 220}, {349, 0, 260, 280}, {392, 240, 300, 420}, {0, 0, 0, 500},
                {440, 180, 260, 280}, {0, 0, 0, 220}, {392, 0, 260, 280}, {349, 240, 300, 450}, {0, 0, 0, 600},
                {294, 180, 260, 280}, {330, 0, 260, 280}, {392, 0, 280, 320}, {294, 240, 340, 600}, {0, 0, 0, 750}
            },
            // 3: Zaman Gezgini Hazine Avı (Pizzicato Gezgin Adımları)
            {
                {196, 160, 340, 360}, {247, 0, 300, 320}, {294, 220, 380, 480}, {0, 0, 0, 450},
                {330, 160, 340, 360}, {294, 0, 300, 320}, {247, 220, 380, 500}, {0, 0, 0, 550},
                {262, 160, 340, 360}, {294, 0, 300, 320}, {196, 220, 420, 650}, {0, 0, 0, 750}
            },
            // 4: Sihirli Köprü Parolası (Neşeli Kısa Marimba)
            {
                {349, 0, 260, 260}, {440, 0, 260, 260}, {523, 0, 320, 420}, {0, 0, 0, 450},
                {466, 0, 260, 260}, {392, 0, 260, 260}, {349, 0, 340, 500}, {0, 0, 0, 600},
                {294, 0, 260, 260}, {349, 0, 260, 260}, {440, 0, 320, 450}, {349, 0, 380, 650}, {0, 0, 0, 700}
            },
            // 5: İyilik Bahçesi Yıldızları (Yumuşak Piyano - Çok sakin, seyrek)
            {
                {262, 0, 550, 550}, {330, 0, 550, 550}, {392, 0, 650, 800}, {0, 0, 0, 800},
                {349, 0, 550, 550}, {330, 0, 550, 550}, {294, 0, 650, 850}, {0, 0, 0, 850},
                {262, 0, 550, 550}, {294, 0, 550, 550}, {262, 0, 700, 950}, {0, 0, 0, 1000}
            },
            // 6: Süper Kaptan Güvenli Rota (Hafif Ritim & Pizzicato)
            {
                {294, 190, 280, 280}, {370, 0, 280, 280}, {440, 260, 320, 380}, {0, 0, 0, 360},
                {392, 190, 280, 280}, {370, 0, 280, 280}, {294, 260, 340, 480}, {0, 0, 0, 520}
            },
            // 7: Barış Adası Kahramanları (Tropikal Kalimba)
            {
                {262, 0, 340, 340}, {330, 0, 340, 340}, {392, 0, 400, 480}, {0, 0, 0, 480},
                {349, 0, 340, 340}, {294, 0, 340, 340}, {262, 0, 420, 650}, {0, 0, 0, 700}
            },
            // 8: Süper Yıldız Zıplama Macerası (Oyun Havasına Uygun, Hafif Ahşap Marimba & Ritim Tıkırtısı!)
            {
                {330, 180, 190, 220}, {0, 260, 120, 160}, {392, 180, 190, 220}, {440, 260, 210, 260},
                {0, 180, 120, 220},   {392, 260, 190, 220}, {330, 180, 220, 300}, {0, 0, 0, 320},
                {294, 180, 190, 220}, {330, 260, 190, 220}, {392, 180, 210, 260}, {0, 260, 120, 220},
                {330, 180, 190, 220}, {294, 260, 190, 220}, {262, 180, 240, 340}, {0, 0, 0, 420}
            },
            // 9: Süper Turbo Araba Yarışı (Tempolu Aksiyon & Yarış Ritmi - Hızlandıkça hızlanır!)
            {
                {220, 150, 150, 155}, {262, 240, 130, 155}, {330, 150, 150, 155}, {392, 280, 140, 155},
                {440, 150, 160, 155}, {392, 240, 130, 155}, {330, 150, 150, 155}, {294, 280, 140, 155},
                {262, 150, 150, 155}, {330, 240, 130, 155}, {392, 150, 150, 155}, {494, 280, 150, 155},
                {523, 150, 170, 155}, {440, 240, 140, 155}, {392, 150, 150, 155}, {330, 280, 150, 155}
            }
    };

    private static final int[] GAME_INSTRUMENTS = new int[]{
            INST_SOFT_KALIMBA,     // 0
            INST_PIZZICATO_STRING, // 1
            INST_WOOD_MARIMBA,     // 2
            INST_PIZZICATO_STRING, // 3
            INST_WOOD_MARIMBA,     // 4
            INST_SOFT_PIANO,       // 5
            INST_PIZZICATO_STRING, // 6
            INST_SOFT_KALIMBA,     // 7
            INST_WOOD_MARIMBA,     // 8: Parkur oyunu için kısa, hafif, neşeli ahşap marimba + ritim!
            INST_PIZZICATO_STRING  // 9: Araba yarışı için dinamik, enerjik aksiyon ritmi!
    };

    public synchronized void startBackgroundMusic(int melodyIndex) {
        this.currentMelodyIndex = Math.abs(melodyIndex) % SCORES.length;
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
        this.currentMelodyIndex = Math.abs(melodyIndex) % SCORES.length;
    }

    public void setRacingTelemetry(boolean active, float speedRatio, int level) {
        this.racingAudioActive = active;
        this.racingSpeedRatio = Math.max(0f, Math.min(1.5f, speedRatio));
        this.racingLevel = Math.max(1, level);
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

    private void triggerVoice(AcousticVoice[] pool, int inst, int freqHz, int sustainMs, float amp, Random rng) {
        if (freqHz <= 0) return;
        for (AcousticVoice v : pool) {
            if (!v.active) {
                v.trigger(inst, freqHz, sustainMs, amp, rng);
                return;
            }
        }
        AcousticVoice oldest = pool[0];
        for (AcousticVoice v : pool) {
            if (v.samplePos > oldest.samplePos) {
                oldest = v;
            }
        }
        oldest.trigger(inst, freqHz, sustainMs, amp, rng);
    }

    private void runMusicLoop() {
        try {
            int minBuf = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
            );
            int maxStepSamples = (SAMPLE_RATE * 1100) / 1000;
            bgTrack = new AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    Math.max(minBuf, maxStepSamples * 2),
                    AudioTrack.MODE_STREAM
            );
            bgTrack.play();

            AcousticVoice[] voices = new AcousticVoice[MAX_VOICES];
            for (int i = 0; i < MAX_VOICES; i++) {
                voices[i] = new AcousticVoice();
            }

            short[] buffer = new short[maxStepSamples];
            Random rng = new Random();
            int step = 0;
            int lastIdx = -1;

            while (playing) {
                int idx = currentMelodyIndex % SCORES.length;
                if (idx != lastIdx) {
                    step = 0;
                    lastIdx = idx;
                    for (AcousticVoice v : voices) v.active = false;
                }

                int[][] score = SCORES[idx];
                int[] ev = score[step % score.length];
                int melFreq = ev[0];
                int percFreq = ev[1];
                int sustainMs = Math.max(100, ev[2]);
                int stepMs = Math.max(70, ev[3]);
                if (racingAudioActive && idx == 9) {
                    float tempoFactor = Math.max(0.45f, 1.0f - (racingLevel - 1) * 0.045f - racingSpeedRatio * 0.30f);
                    stepMs = Math.max(68, Math.round(stepMs * tempoFactor));
                    sustainMs = Math.max(90, Math.round(sustainMs * Math.max(0.65f, tempoFactor)));
                }
                step++;

                int inst = GAME_INSTRUMENTS[idx];

                if (!muted) {
                    if (melFreq > 0) {
                        triggerVoice(voices, inst, melFreq, sustainMs, 0.22f, rng);
                    }
                    if (percFreq > 0) {
                        triggerVoice(voices, INST_WOOD_PERC, percFreq, 90, 0.12f, rng);
                    }
                }

                int stepSamples = Math.min(maxStepSamples, (SAMPLE_RATE * stepMs) / 1000);
                if (muted) {
                    for (int i = 0; i < stepSamples; i++) {
                        buffer[i] = 0;
                    }
                } else {
                    for (int i = 0; i < stepSamples; i++) {
                        float mix = 0f;
                        for (int v = 0; v < MAX_VOICES; v++) {
                            if (voices[v].active) {
                                mix += voices[v].nextSample();
                            }
                        }
                        if (racingAudioActive && racingSpeedRatio > 0.02f) {
                            double engFreq = 148.0 + racingSpeedRatio * 185.0 + Math.min(60.0, (racingLevel - 1) * 6.0);
                            enginePhase += (2.0 * Math.PI * engFreq) / SAMPLE_RATE;
                            if (enginePhase > 2.0 * Math.PI * 100.0) {
                                enginePhase -= 2.0 * Math.PI * 100.0;
                            }
                            double pulse = 0.82 + 0.18 * Math.sin(enginePhase * 0.25);
                            double engWave = (Math.sin(enginePhase) * 0.42
                                    + Math.sin(enginePhase * 2.0) * 0.30
                                    + Math.sin(enginePhase * 3.0) * 0.18
                                    + Math.sin(enginePhase * 4.0) * 0.10) * pulse;
                            float engVol = 0.13f + Math.min(0.14f, racingSpeedRatio * 0.11f);
                            mix += (float) (engWave * engVol);
                        }
                        int pcm = (int) (mix * 4800.0f);
                        if (pcm > 14000) pcm = 14000;
                        if (pcm < -14000) pcm = -14000;
                        buffer[i] = (short) pcm;
                    }
                }

                if (bgTrack != null && playing) {
                    bgTrack.write(buffer, 0, stepSamples);
                }
            }
        } catch (Exception ignored) {
        }
    }

    public void playSuccessEffect() {
        if (muted) return;
        playPluckedChordAsync(new int[]{330, 392, 523}, 55, 380);
    }

    public void playWrongEffect() {
        if (muted) return;
        playPluckedChordAsync(new int[]{247, 220}, 95, 320);
    }

    /**
     * Tatlı, yumuşak yaylı zıplama sesi (240 Hz -> 680 Hz tatlı yukarı kayan balon/yay tınısı).
     */
    public void playJumpEffect() {
        if (muted) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    int durationMs = 175;
                    int totalSamples = (SAMPLE_RATE * durationMs) / 1000;
                    short[] pcm = new short[totalSamples];
                    double phase = 0.0;
                    for (int i = 0; i < totalSamples; i++) {
                        double p = (double) i / totalSamples;
                        // Tatlı kavisli yukarı frekans kayması (Sweet Bubble Boing)
                        double freq = 250.0 + 460.0 * Math.pow(p, 0.72) + Math.sin(p * Math.PI * 5.0) * 18.0;
                        phase += (2.0 * Math.PI * freq) / SAMPLE_RATE;
                        double env = Math.sin(Math.PI * p) * Math.exp(-1.4 * p);
                        double wave = Math.sin(phase) * 0.82 + Math.sin(phase * 2.0) * 0.18;
                        pcm[i] = (short) (wave * env * 6200);
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
                    Thread.sleep(durationMs + 40L);
                    sfx.stop();
                    sfx.release();
                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    /**
     * Lazer ateşleme sesi (1450 Hz -> 260 Hz hızlı fütüristik lazer ışını 'Pew!' efekti).
     */
    public void playLaserEffect() {
        if (muted) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    int durationMs = 150;
                    int totalSamples = (SAMPLE_RATE * durationMs) / 1000;
                    short[] pcm = new short[totalSamples];
                    double phase = 0.0;
                    for (int i = 0; i < totalSamples; i++) {
                        double p = (double) i / totalSamples;
                        double freq = 1420.0 * Math.exp(-2.4 * p) + 180.0;
                        phase += (2.0 * Math.PI * freq) / SAMPLE_RATE;
                        double env = (i < 120 ? (i / 120.0) : 1.0) * Math.exp(-2.8 * p);
                        double wave = Math.sin(phase) * 0.75 + Math.sin(phase * 3.0) * 0.25;
                        pcm[i] = (short) (wave * env * 6600);
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
                    Thread.sleep(durationMs + 35L);
                    sfx.stop();
                    sfx.release();
                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    public void playLaneSwitchEffect() {
        if (muted) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    int samples = (SAMPLE_RATE * 110) / 1000;
                    short[] buf = new short[samples];
                    Random rng = new Random();
                    double phase = 0.0;
                    for (int i = 0; i < samples; i++) {
                        double t = (double) i / samples;
                        double freq = 520.0 + Math.sin(t * Math.PI) * 320.0;
                        phase += (2.0 * Math.PI * freq) / SAMPLE_RATE;
                        double env = Math.sin(Math.PI * t) * (1.0 - t * 0.4);
                        double chirp = Math.sin(phase) * 0.55 + (rng.nextFloat() * 2f - 1f) * 0.35;
                        buf[i] = (short) (chirp * env * 2600);
                    }
                    playOneShotPcm(buf);
                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    private int lastScreamVariant = -1;

    public void playRandomHumanScreamEffect() {
        if (muted) return;
        final Random rng = new Random();
        int variant = rng.nextInt(6);
        if (variant == lastScreamVariant) {
            variant = (variant + 1) % 6;
        }
        lastScreamVariant = variant;
        final int v = variant;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(70L);
                    // 6 farklı vokal formant profili: (F1, F2, F3, startHz, peakHz, endHz, durationMs)
                    double[][] profiles = new double[][]{
                            {760, 1260, 2600, 520, 620, 260, 480}, // Yüksekten düşen panik "Aaaah!"
                            {480, 900, 2380, 340, 390, 165, 520},  // Kalın erkek bağırışı "Ooooh!"
                            {540, 1860, 2560, 460, 560, 220, 430}, // Keskin çığlık "Eeeey!"
                            {710, 1420, 2520, 360, 640, 270, 540}, // Yükselip düşen "Yaaah!"
                            {370, 790, 2260, 300, 330, 145, 500},  // Şaşkın homurtu "Uuuuh!"
                            {690, 1980, 2760, 610, 710, 380, 390}  // Kısa ince çığlık "Aiiiy!"
                    };
                    double[] p = profiles[v];
                    double jitterScale = 0.92 + rng.nextDouble() * 0.16;
                    double f1 = p[0] * (0.95 + rng.nextDouble() * 0.10);
                    double f2 = p[1] * (0.95 + rng.nextDouble() * 0.10);
                    double f3 = p[2];
                    double startHz = p[3] * jitterScale;
                    double peakHz = p[4] * jitterScale;
                    double endHz = p[5] * jitterScale;
                    int durMs = (int) (p[6] * (0.92 + rng.nextDouble() * 0.18));

                    int samples = (SAMPLE_RATE * durMs) / 1000;
                    short[] buf = new short[samples];

                    // 3 paralel biquad rezonans filtresi (F1, F2, F3)
                    double[][] bq = new double[3][4]; // x1, x2, y1, y2
                    double[] freqs = new double[]{f1, f2, f3};
                    double[] bws = new double[]{95.0, 130.0, 180.0};
                    double[] gains = new double[]{1.0, 0.55, 0.25};

                    double glottalPhase = 0.0;
                    double vibPhase = 0.0;

                    for (int i = 0; i < samples; i++) {
                        double t = (double) i / samples;
                        double pitchHz;
                        if (t < 0.22) {
                            double k = t / 0.22;
                            pitchHz = startHz + (peakHz - startHz) * k;
                        } else {
                            double k = (t - 0.22) / 0.78;
                            pitchHz = peakHz + (endHz - peakHz) * Math.pow(k, 0.85);
                        }
                        vibPhase += (2.0 * Math.PI * 6.4) / SAMPLE_RATE;
                        double vibrato = 1.0 + 0.032 * Math.sin(vibPhase);
                        double jitter = 1.0 + (rng.nextDouble() - 0.5) * 0.025;
                        glottalPhase += (pitchHz * vibrato * jitter) / SAMPLE_RATE;
                        if (glottalPhase >= 1.0) glottalPhase -= 1.0;

                        // Rosenberg benzeri doğal gırtlak darbesi + hafif nefes gürültüsü
                        double gp = glottalPhase;
                        double glottal;
                        if (gp < 0.45) {
                            glottal = 0.5 * (1.0 - Math.cos(Math.PI * gp / 0.45));
                        } else if (gp < 0.65) {
                            glottal = Math.cos(Math.PI * (gp - 0.45) / 0.40);
                        } else {
                            glottal = 0.0;
                        }
                        glottal = (glottal - 0.32) + (rng.nextDouble() * 2.0 - 1.0) * 0.14;

                        double vocalSum = 0.0;
                        for (int fIdx = 0; fIdx < 3; fIdx++) {
                            double w0 = 2.0 * Math.PI * freqs[fIdx] / SAMPLE_RATE;
                            double r = Math.exp(-Math.PI * bws[fIdx] / SAMPLE_RATE);
                            double a1 = -2.0 * r * Math.cos(w0);
                            double a2 = r * r;
                            double b0 = (1.0 - r) * gains[fIdx];
                            double y = b0 * (glottal - bq[fIdx][1]) - a1 * bq[fIdx][2] - a2 * bq[fIdx][3];
                            bq[fIdx][1] = bq[fIdx][0];
                            bq[fIdx][0] = glottal;
                            bq[fIdx][3] = bq[fIdx][2];
                            bq[fIdx][2] = y;
                            vocalSum += y;
                        }

                        double attack = Math.min(1.0, t / 0.07);
                        double release = Math.min(1.0, (1.0 - t) / 0.24);
                        double env = attack * release;
                        double out = Math.tanh(vocalSum * 2.2) * env;
                        buf[i] = (short) (out * 13500);
                    }
                    playOneShotPcm(buf);
                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    public void playCrashEffect() {
        if (muted) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    int samples = (SAMPLE_RATE * 340) / 1000;
                    short[] buf = new short[samples];
                    Random rng = new Random();
                    double phase = 0.0;
                    float lp = 0f;
                    for (int i = 0; i < samples; i++) {
                        double t = (double) i / samples;
                        double freq = 190.0 * (1.0 - t * 0.65);
                        phase += (2.0 * Math.PI * freq) / SAMPLE_RATE;
                        float noise = rng.nextFloat() * 2f - 1f;
                        lp = lp * 0.65f + noise * 0.35f;
                        double env = Math.exp(-4.5 * t);
                        double impact = Math.sin(phase) * 0.45 + lp * 0.85;
                        buf[i] = (short) (Math.max(-1.0, Math.min(1.0, impact)) * env * 14500);
                    }
                    playOneShotPcm(buf);
                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    public void playOvertakeEffect() {
        if (muted) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    int samples = (SAMPLE_RATE * 140) / 1000;
                    short[] buf = new short[samples];
                    double phase = 0.0;
                    for (int i = 0; i < samples; i++) {
                        double t = (double) i / samples;
                        double freq = 420.0 - t * 240.0;
                        phase += (2.0 * Math.PI * freq) / SAMPLE_RATE;
                        double env = Math.sin(Math.PI * t);
                        buf[i] = (short) (Math.sin(phase) * env * 6500);
                    }
                    playOneShotPcm(buf);
                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    public void playNitroBoostEffect() {
        if (muted) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    int samples = (SAMPLE_RATE * 260) / 1000;
                    short[] buf = new short[samples];
                    double phase = 0.0;
                    for (int i = 0; i < samples; i++) {
                        double t = (double) i / samples;
                        double freq = 220.0 + t * 560.0;
                        phase += (2.0 * Math.PI * freq) / SAMPLE_RATE;
                        double env = Math.sin(Math.PI * t) * (1.0 - t * 0.35);
                        double wave = Math.sin(phase) * 0.65 + Math.sin(phase * 2.0) * 0.35;
                        buf[i] = (short) (wave * env * 10500);
                    }
                    playOneShotPcm(buf);
                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    public void playCoinEffect() {
        if (muted) return;
        playPluckedChordAsync(new int[]{440, 587}, 45, 260);
    }

    public void playCountdownTick(boolean isGo) {
        if (muted) return;
        if (isGo) {
            playPluckedChordAsync(new int[]{330, 440, 587}, 35, 350);
        } else {
            playPluckedChordAsync(new int[]{330}, 40, 220);
        }
    }

    private void playPluckedChordAsync(final int[] freqs, final int strumDelayMs, final int totalDurationMs) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    int totalSamples = (SAMPLE_RATE * totalDurationMs) / 1000;
                    short[] pcm = new short[totalSamples];
                    Random rng = new Random();

                    for (int n = 0; n < freqs.length; n++) {
                        int startSample = (SAMPLE_RATE * (n * strumDelayMs)) / 1000;
                        int remSamples = totalSamples - startSample;
                        if (remSamples <= 80) continue;

                        AcousticVoice voice = new AcousticVoice();
                        voice.trigger(INST_WOOD_MARIMBA, freqs[n], (remSamples * 1000) / SAMPLE_RATE, 0.32f, rng);
                        for (int i = startSample; i < totalSamples; i++) {
                            int v = pcm[i] + (int) (voice.nextSample() * 6500.0f);
                            if (v > 20000) v = 20000;
                            if (v < -20000) v = -20000;
                            pcm[i] = (short) v;
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
                    Thread.sleep(totalDurationMs + 50L);
                    sfx.stop();
                    sfx.release();
                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    private void playOneShotPcm(short[] buf) {
        if (buf == null || buf.length == 0) return;
        AudioTrack track = null;
        try {
            int minBuf = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
            );
            track = new AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    Math.max(minBuf, buf.length * 2),
                    AudioTrack.MODE_STATIC
            );
            track.write(buf, 0, buf.length);
            track.play();
            Thread.sleep((buf.length * 1000L) / SAMPLE_RATE + 40L);
        } catch (Exception ignored) {
        } finally {
            if (track != null) {
                try {
                    track.stop();
                    track.release();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
