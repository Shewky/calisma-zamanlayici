package com.calismazamanlayici.veli;

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
public class VeliGameMusicSynthesizer {

    private static final int SAMPLE_RATE = 22050;
    private static final int MAX_VOICES = 4;

    private volatile boolean playing = false;
    private volatile boolean muted = false;
    private volatile int currentMelodyIndex = 0;
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
            // Enstrümantal konser gibi DEĞİL; hafif, eğlenceli, zıplama ritmine uygun arcade dokunuşları!
            {
                {330, 180, 190, 220}, {0, 260, 120, 160}, {392, 180, 190, 220}, {440, 260, 210, 260},
                {0, 180, 120, 220},   {392, 260, 190, 220}, {330, 180, 220, 300}, {0, 0, 0, 320},
                {294, 180, 190, 220}, {330, 260, 190, 220}, {392, 180, 210, 260}, {0, 260, 120, 220},
                {330, 180, 190, 220}, {294, 260, 190, 220}, {262, 180, 240, 340}, {0, 0, 0, 420}
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
            INST_WOOD_MARIMBA      // 8: Parkur oyunu için kısa, hafif, neşeli ahşap marimba + ritim!
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
                int sustainMs = Math.max(120, ev[2]);
                int stepMs = Math.max(120, ev[3]);
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

    public void playJumpEffect() {
        if (muted) return;
        playPluckedChordAsync(new int[]{330, 440}, 32, 220);
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
}
