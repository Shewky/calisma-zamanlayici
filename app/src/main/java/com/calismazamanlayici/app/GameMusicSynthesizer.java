package com.calismazamanlayici.app;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import java.util.Random;

/**
 * Gerçek Enstrüman Fiziksel Modelleme (Physical Modeling) Ses Motoru:
 * - Eski tip polifonik telefon zil sesi (tekil sinüs dalgası ve kesik notalar) TAMAMEN KALDIRILDI.
 * - Yerine:
 *   1) Karplus-Strong Fiziksel Tel Titreşimi Modeli (Klasik Naylon Telli Gitar, Akustik Gitar, Konser Arpı)
 *   2) 3-Telli Unison Kuyruklu Piyano & Keçe Çekiç (Felt Piano) Fiziksel Modeli (Inharmonicity + Sustain Pedalı)
 *   3) Gül Ağacı Konser Marimba (1 : 3.98 : 9.2 ahşap rezonans modları + yumuşak tokmak vuruşu)
 * - 12 eşzamanlı ses kanalı (Voice Pool) sayesinde bir nota çalındığında hemen kesilmez; tıpkı gerçek
 *   piyano pedalı veya gitar teli gibi 1.5 - 2.5 saniye boyunca doğal olarak tınlamaya devam ederken
 *   yeni notalar üst üste binerek gerçek akustik kayıt hissi verir.
 */
public class GameMusicSynthesizer {

    private static final int SAMPLE_RATE = 22050;
    private static final int MAX_VOICES = 12;

    private volatile boolean playing = false;
    private volatile boolean muted = false;
    private volatile int currentMelodyIndex = 0;
    private Thread musicThread;
    private AudioTrack bgTrack;

    // Enstrüman Tipleri
    private static final int INST_NYLON_GUITAR = 0;   // Klasik Akustik Gitar (Karplus-Strong)
    private static final int INST_CONCERT_HARP = 1;   // Konser Arpı (Parlak Karplus-Strong + Gövde Rezonansı)
    private static final int INST_GRAND_PIANO = 2;    // Kuyruklu Akustik Piyano (3-Tel Unison + Çekiç + Sustain)
    private static final int INST_FELT_PIANO = 3;     // Yumuşak Keçe Piyano (Sıcak, Kadifemsi Piyano)
    private static final int INST_WOOD_MARIMBA = 4;   // Gül Ağacı Akustik Marimba (Ahşap Bar Rezonansı)
    private static final int INST_STEEL_GUITAR = 5;   // Akustik Çelik Telli Gitar

    /**
     * Aynı anda tınlayan tek bir tel / piyano tuşu (Sustain pedalı etkisiyle üst üste tınlar).
     */
    private static class AcousticVoice {
        boolean active = false;
        int instrument = INST_NYLON_GUITAR;
        int samplePos = 0;
        int totalSamples = 0;
        float amplitude = 0.25f;

        // Karplus-Strong tel gecikme hattı (Gitar / Arp için gerçek tel fiziği)
        float[] stringLine;
        int lineLen = 0;
        int lineIdx = 0;
        float prevSample = 0f;
        float damping = 0.993f;
        float bodyState = 0f;

        // Piyano / Marimba değişkenleri
        double freq = 261.63;
        double phase1 = 0, phase2 = 0, phase3 = 0, phaseOvertone1 = 0, phaseOvertone2 = 0;

        void trigger(int inst, int freqHz, int sustainMs, float amp, Random rng) {
            this.instrument = inst;
            this.freq = freqHz;
            this.samplePos = 0;
            this.totalSamples = (SAMPLE_RATE * sustainMs) / 1000;
            this.amplitude = amp;
            this.bodyState = 0f;

            if (inst == INST_NYLON_GUITAR || inst == INST_CONCERT_HARP || inst == INST_STEEL_GUITAR) {
                this.lineLen = Math.max(12, SAMPLE_RATE / Math.max(55, freqHz));
                if (this.stringLine == null || this.stringLine.length < this.lineLen + 2) {
                    this.stringLine = new float[this.lineLen + 4];
                }
                // Telin parmakla çekilme (pluck) başlangıç uyarımı (yumuşatılmış akustik darbe)
                float prev = 0f;
                for (int i = 0; i < this.lineLen; i++) {
                    double posRatio = (double) i / this.lineLen;
                    // Telin ortasından çekilme üçgen zarfı + sıcak parmak dokunuşu
                    float tri = (posRatio < 0.35) ? (float) (posRatio / 0.35) : (float) ((1.0 - posRatio) / 0.65);
                    float noise = (rng.nextFloat() * 2f - 1f) * 0.45f;
                    float raw = tri * 0.65f + noise;
                    // Başlangıç alçak geçiren yumuşatma (metalik tınıyı önler, sıcak ahşap tel sesi verir)
                    float alpha = (inst == INST_NYLON_GUITAR) ? 0.32f : (inst == INST_CONCERT_HARP ? 0.42f : 0.55f);
                    prev = prev + alpha * (raw - prev);
                    this.stringLine[i] = prev;
                }
                this.lineIdx = 0;
                this.prevSample = 0f;
                this.damping = (inst == INST_CONCERT_HARP) ? 0.9955f : (inst == INST_STEEL_GUITAR ? 0.9945f : 0.9925f);
            } else {
                this.phase1 = 0;
                this.phase2 = 0;
                this.phase3 = 0;
                this.phaseOvertone1 = 0;
                this.phaseOvertone2 = 0;
            }
            this.active = true;
        }

        float nextSample() {
            if (!active) return 0f;
            if (samplePos >= totalSamples) {
                active = false;
                return 0f;
            }

            float out = 0f;
            double t = (double) samplePos / SAMPLE_RATE;
            double progress = (double) samplePos / totalSamples;

            if (instrument == INST_NYLON_GUITAR || instrument == INST_CONCERT_HARP || instrument == INST_STEEL_GUITAR) {
                // Karplus-Strong fiziksel tel denklemi
                int nextIdx = (lineIdx + 1) % lineLen;
                float curr = stringLine[lineIdx];
                float next = stringLine[nextIdx];

                // Köprü sönümlenme filtresi
                float filtered = (instrument == INST_NYLON_GUITAR)
                        ? (0.58f * curr + 0.42f * next)
                        : (0.52f * curr + 0.48f * next);
                stringLine[lineIdx] = filtered * damping;
                lineIdx = nextIdx;

                // Ahşap gövde rezonansı (Warm Soundboard Filter)
                bodyState = bodyState * 0.82f + curr * 0.18f;
                out = (curr * 0.65f + bodyState * 0.35f);

                // Son %15'te yumuşak sönümlenme
                if (progress > 0.85) {
                    out *= (float) ((1.0 - progress) / 0.15);
                }
            } else if (instrument == INST_GRAND_PIANO || instrument == INST_FELT_PIANO) {
                // Gerçek Akustik Piyano: 3 hafif akort farkı olan tel (Unison Chorus) + Inharmonicity + Keçe Çekiç
                double detune = (instrument == INST_GRAND_PIANO) ? 1.0007 : 1.0004;
                phase1 += (2.0 * Math.PI * freq) / SAMPLE_RATE;
                phase2 += (2.0 * Math.PI * (freq * detune)) / SAMPLE_RATE;
                phase3 += (2.0 * Math.PI * (freq / detune)) / SAMPLE_RATE;
                // Gerçek çelik tel inharmonicity (üst harmonikler hafif genişler ve daha hızlı söner)
                phaseOvertone1 += (2.0 * Math.PI * (freq * 2.001)) / SAMPLE_RATE;
                phaseOvertone2 += (2.0 * Math.PI * (freq * 3.004)) / SAMPLE_RATE;

                double envMain = Math.exp(-2.8 * progress);
                double envHarm2 = Math.exp(-5.5 * progress);
                double envHarm3 = Math.exp(-9.0 * progress);

                // Yumuşak keçe çekiç dokunuşu (ilk 8ms)
                double attack = (samplePos < 180) ? ((double) samplePos / 180.0) : 1.0;
                double hammerThump = (samplePos < 260)
                        ? Math.sin(2.0 * Math.PI * (freq * 0.5) * t) * (1.0 - samplePos / 260.0) * 0.18
                        : 0.0;

                double strings = (Math.sin(phase1) + Math.sin(phase2) * 0.85 + Math.sin(phase3) * 0.85) * 0.37 * envMain;
                double harm2Factor = (instrument == INST_FELT_PIANO) ? 0.14 : 0.24;
                double harm3Factor = (instrument == INST_FELT_PIANO) ? 0.03 : 0.08;
                double overtones = Math.sin(phaseOvertone1) * harm2Factor * envHarm2
                        + Math.sin(phaseOvertone2) * harm3Factor * envHarm3;

                out = (float) ((strings + overtones + hammerThump) * attack);
                if (progress > 0.88) {
                    out *= (float) ((1.0 - progress) / 0.12);
                }
            } else {
                // Gül Ağacı Akustik Marimba: Gerçek ahşap bar modları (1 : 3.98) + Hızlı ahşap sönümlenme
                phase1 += (2.0 * Math.PI * freq) / SAMPLE_RATE;
                phaseOvertone1 += (2.0 * Math.PI * (freq * 3.98)) / SAMPLE_RATE;
                double envFund = Math.exp(-5.2 * progress);
                double envWood = Math.exp(-18.0 * progress);
                double attack = (samplePos < 90) ? ((double) samplePos / 90.0) : 1.0;
                out = (float) ((Math.sin(phase1) * 0.82 * envFund + Math.sin(phaseOvertone1) * 0.18 * envWood) * attack);
            }

            samplePos++;
            return out * amplitude;
        }
    }

    /**
     * Her adım: {Bas/Akor Kök Nota Hz, Melodi Nota Hz, İkinci Akor/Armoni Hz, Adım Bekleme Süresi Ms}
     * Notlar çalındığında kesilmez, 1800-2400 ms boyunca akustik olarak tınlamaya devam eder!
     */
    private static final int[][][] SCORES = new int[][][]{
            // 0: Galaksi Sayı Avcısı -> Kuyruklu Akustik Piyano (Sıcak Arpejli Piyano Bestesi)
            {
                {131, 262, 330, 520}, {0, 392, 0, 520}, {0, 330, 0, 520}, {0, 294, 0, 680},
                {110, 220, 262, 520}, {0, 330, 0, 520}, {0, 294, 0, 520}, {0, 262, 0, 720},
                {175, 262, 349, 520}, {0, 330, 0, 520}, {0, 294, 0, 520}, {0, 262, 0, 680},
                {196, 247, 294, 520}, {0, 330, 0, 520}, {131, 262, 330, 900}, {0, 0, 0, 800}
            },
            // 1: Gizemli Kelime Kalesi -> Konser Arpı (Gerçek Telli Arp Valsi 3/4)
            {
                {110, 220, 330, 480}, {0, 262, 0, 380}, {0, 330, 0, 420},
                {175, 262, 349, 480}, {0, 330, 0, 380}, {0, 262, 0, 420},
                {131, 262, 330, 480}, {0, 392, 0, 380}, {0, 330, 0, 420},
                {196, 247, 294, 480}, {0, 220, 0, 420}, {110, 220, 330, 850}, {0, 0, 0, 750}
            },
            // 2: Çılgın Laboratuvar Keşfi -> Gül Ağacı Akustik Marimba & Klasik Gitar Bas
            {
                {131, 262, 0, 360}, {0, 330, 0, 360}, {196, 392, 0, 420}, {0, 330, 0, 500},
                {147, 294, 0, 360}, {0, 349, 0, 360}, {220, 440, 0, 420}, {0, 392, 0, 550},
                {175, 349, 0, 360}, {0, 330, 0, 360}, {147, 294, 0, 420}, {131, 262, 330, 800}, {0, 0, 0, 700}
            },
            // 3: Zaman Gezgini Hazine Avı -> Klasik Naylon Telli Akustik Gitar (Fingerstyle)
            {
                {196, 294, 392, 460}, {0, 330, 0, 420}, {0, 392, 0, 480}, {0, 294, 0, 540},
                {165, 247, 330, 460}, {0, 294, 0, 420}, {0, 330, 0, 480}, {0, 247, 0, 560},
                {131, 262, 330, 460}, {0, 294, 0, 420}, {147, 294, 370, 520}, {196, 247, 392, 850}, {0, 0, 0, 750}
            },
            // 4: Sihirli Köprü Parolası -> Çelik Telli Akustik Gitar & Arp
            {
                {175, 262, 349, 400}, {0, 330, 0, 360}, {0, 392, 0, 440}, {0, 349, 0, 520},
                {147, 220, 294, 400}, {0, 262, 0, 360}, {0, 349, 0, 440}, {0, 294, 0, 520},
                {131, 262, 330, 400}, {0, 294, 0, 360}, {175, 262, 349, 820}, {0, 0, 0, 720}
            },
            // 5: İyilik Bahçesi Yıldızları -> Kadifemsi Keçe Piyano (Felt Piano - Çok Huzurlu Akustik)
            {
                {131, 262, 330, 680}, {0, 294, 0, 620}, {0, 330, 0, 750},
                {175, 220, 262, 680}, {0, 294, 0, 620}, {0, 262, 0, 800},
                {110, 220, 262, 680}, {0, 330, 0, 620}, {196, 247, 294, 820},
                {131, 196, 262, 950}, {0, 0, 0, 900}
            },
            // 6: Süper Kaptan Güvenli Rota -> Akustik Gitar Düeti (Ritim + Melodi Teli)
            {
                {147, 294, 370, 350}, {0, 330, 0, 320}, {0, 370, 0, 380}, {196, 294, 392, 460},
                {220, 330, 440, 350}, {0, 370, 0, 320}, {0, 330, 0, 380}, {147, 220, 294, 650},
                {196, 247, 294, 360}, {220, 277, 330, 360}, {147, 220, 294, 800}, {0, 0, 0, 650}
            },
            // 7: Barış Adası Kahramanları -> Klasik Gitar & Ahşap Marimba Akustik Karışımı
            {
                {131, 262, 330, 440}, {0, 392, 0, 400}, {175, 262, 349, 460}, {0, 330, 0, 520},
                {196, 247, 294, 440}, {0, 330, 0, 400}, {131, 196, 262, 780}, {0, 0, 0, 680}
            },
            // 8: Süper Yıldız Zıplama Macerası -> Akustik Gitar & Kuyruklu Piyano Canlı Eşliği
            {
                {131, 262, 330, 320}, {0, 330, 0, 280}, {196, 294, 392, 340}, {0, 330, 0, 360},
                {175, 262, 349, 320}, {0, 330, 0, 280}, {147, 220, 294, 380}, {196, 247, 294, 420},
                {131, 262, 392, 320}, {0, 349, 0, 280}, {0, 330, 0, 280}, {0, 294, 0, 340},
                {175, 262, 349, 340}, {196, 247, 294, 340}, {131, 196, 262, 720}, {0, 0, 0, 520}
            }
    };

    private static final int[] GAME_INSTRUMENTS = new int[]{
            INST_GRAND_PIANO,   // 0: Galaksi Sayı Avcısı (Kuyruklu Piyano)
            INST_CONCERT_HARP,  // 1: Gizemli Kelime Kalesi (Konser Arpı)
            INST_WOOD_MARIMBA,  // 2: Çılgın Laboratuvar (Gül Ağacı Marimba)
            INST_NYLON_GUITAR,  // 3: Zaman Gezgini (Klasik Naylon Telli Gitar)
            INST_STEEL_GUITAR,  // 4: Sihirli Köprü (Akustik Çelik Telli Gitar)
            INST_FELT_PIANO,    // 5: İyilik Bahçesi (Yumuşak Keçe Piyano)
            INST_NYLON_GUITAR,  // 6: Süper Kaptan (Akustik Gitar)
            INST_CONCERT_HARP,  // 7: Barış Adası (Akustik Arp & Gitar)
            INST_STEEL_GUITAR   // 8: Zıplama Macerası (Akustik Gitar & Piyano)
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
        // Tüm kanallar doluysa en eski kanalı devral
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
                int bassFreq = ev[0];
                int melFreq = ev[1];
                int harmFreq = ev[2];
                int stepMs = ev[3];
                step++;

                int inst = GAME_INSTRUMENTS[idx];
                int bassInst = (inst == INST_WOOD_MARIMBA) ? INST_NYLON_GUITAR : inst;

                // Notları çok kanallı havuzda tetikle (Sustain süresi adım süresinden çok daha uzundur -> doğal tınlama!)
                if (!muted) {
                    if (bassFreq > 0) {
                        triggerVoice(voices, bassInst, bassFreq, 2200, 0.24f, rng);
                    }
                    if (harmFreq > 0) {
                        triggerVoice(voices, inst, harmFreq, 1850, 0.20f, rng);
                    }
                    if (melFreq > 0) {
                        triggerVoice(voices, inst, melFreq, 1950, 0.32f, rng);
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
                        // Yumuşak doygunluk (Soft-clipping) ve sıcak akustik seviye
                        int pcm = (int) (mix * 6800.0f);
                        if (pcm > 18000) pcm = 18000;
                        if (pcm < -18000) pcm = -18000;
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
        // Akustik gitar / arp akoru (C - E - G - C)
        playPluckedChordAsync(new int[]{262, 330, 392, 523}, 65, 520);
    }

    public void playWrongEffect() {
        if (muted) return;
        // Yumuşak akustik gitar ikilisi
        playPluckedChordAsync(new int[]{220, 196}, 120, 420);
    }

    public void playJumpEffect() {
        if (muted) return;
        playPluckedChordAsync(new int[]{294, 392, 494}, 38, 340);
    }

    public void playCoinEffect() {
        if (muted) return;
        playPluckedChordAsync(new int[]{392, 523}, 55, 380);
    }

    public void playCountdownTick(boolean isGo) {
        if (muted) return;
        if (isGo) {
            playPluckedChordAsync(new int[]{262, 330, 392, 523}, 40, 480);
        } else {
            playPluckedChordAsync(new int[]{330}, 50, 320);
        }
    }

    /**
     * Ses efektlerini bile gerçek Karplus-Strong akustik gitar/arp teli modeliyle üretir!
     */
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
                        if (remSamples <= 100) continue;

                        AcousticVoice voice = new AcousticVoice();
                        voice.trigger(INST_CONCERT_HARP, freqs[n], (remSamples * 1000) / SAMPLE_RATE, 0.35f, rng);
                        for (int i = startSample; i < totalSamples; i++) {
                            int v = pcm[i] + (int) (voice.nextSample() * 7500.0f);
                            if (v > 22000) v = 22000;
                            if (v < -22000) v = -22000;
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
                    Thread.sleep(totalDurationMs + 60L);
                    sfx.stop();
                    sfx.release();
                } catch (Exception ignored) {
                }
            }
        }).start();
    }
}
