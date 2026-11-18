package com.calismazamanlayici.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;

import java.util.Random;

/**
 * 🏰 BULMACA KULESİ (Puzzle Tower) - 20 Katlı, 4 Bölgeli, Etkileşimli Hikaye & Bulmaca Oyunu:
 * - Hikaye: Kulenin enerjisini sağlayan "Büyük Kristal" parçalanmış ve her kata bir parça dağılmıştır.
 *   Sevimli Kaşif Robot (🤖) ve sakar ama sevimli yardımcı "Bilge Baykuş Puhu" (🦉) birlikte 20 katı tırmanır!
 * - 4 Bölge ve 12 Farklı Bulmaca Mekanikleri:
 *   1. Bölge (Kat 1-5): Oyuncak ve Saat Atölyesi (Dişli Çark Yerleştirme, Renkli Kablo Birleştirme, Denge Terazisi)
 *   2. Bölge (Kat 6-10): Dev Mutfak ve Şekerleme Katı (Tarif Sırası Karıştırma, Meyve Suyu Borusu Döndürme, Gizli Şekerleme Bulma)
 *   3. Bölge (Kat 11-15): Sera ve Doğa Katı (Ayna ile Güneş Işığı Yansıtma, Su Kanalı Açma, Hayvan Ayak İzi Şifresi)
 *   4. Bölge (Kat 16-20): Gözlemevi ve Yıldız Katı (Takımyıldızı Çizerek Birleştirme, Gezegen Yörüngesi Hizalama, Gölge Oyunu Kapısı)
 * - 9-10 Yaş Ödül & İlerleme Sistemi:
 *   - Her katta gizlenmiş 3 adet "🔩 Altın Vida / ✨ Yıldız Tozu" (Dokunarak toplanır).
 *   - Karakter Özelleştirme Dolabı (🎩 Pervane Şapka, 👑 Kral Tacı, 🦸 Süper Pelerin, 🎒 Roket Çanta).
 *   - Kule Müzesi (Koleksiyon): Her bölge ve kat geçildikçe müzeye hareketli ve dokununca dans eden hatıra eşyaları
 *     (🫖 Dans Eden Çaydanlık, 🕰️ Guguklu Saat, 🪴 Şarkı Söyleyen Çiçek, 🔭 Altın Teleskop) eklenir!
 *   - 1 Dakika İpucu Sistemi: Oyuncu bir katta 60 saniye takılırsa Bilge Baykuş Puhu otomatik olarak çözümü fısıldar.
 *   - Hatasız Deneyim: Yanlış hamlede "Oyun Bitti" ekranı ÇIKMAZ; makine komik duman ("💨 PUF! Gıdıklandı!") çıkarıp
 *     sıfırlanır ve tekrar denemeyi teşvik eder.
 */
public class PuzzleTowerGameView extends View {

    public interface PuzzleTowerListener {
        void onTowerActionSound(boolean success);
        void onCountdownTick(boolean isGo);
        void onExitRequested();
    }

    private static final String PREFS_TOWER = "BulmacaKulesiSavePrefs";
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Random random = new Random();
    private final PuzzleTowerListener listener;

    private final float dp;
    private final float sp;

    private boolean running = false;
    private boolean frameScheduled = false;

    // 3-2-1-BAŞLA Geri sayımı
    private boolean inCountdown = false;
    private long countdownStartMs = 0L;
    private int lastAnnouncedCountdown = -1;

    // İlerleme ve Ödül Verileri (Kalıcı Kaydedilir)
    private int currentFloor = 1; // 1..20
    private int unlockedMaxFloor = 1;
    private int totalGoldenScrews = 0; // 🔩 Altın Vida & Yıldız Tozu
    private int totalCrystalsCollected = 0; // 💎 Büyük Kristal Parçaları
    private int equippedHat = 0;   // 0: Yok, 1: 🎩 Sihirbaz, 2: 🧢 Pervane, 3: 👑 Taç
    private int equippedCape = 0;  // 0: Yok, 1: 🧣 Kırmızı Pelerin, 2: 🌟 Yıldız Pelerin
    private int equippedBag = 0;   // 0: Yok, 1: 🎒 Roket Çanta, 2: 🔋 Kristal Çanta
    private int ownedMask = 1;     // Bitmask

    // Alt Ekran Modları: 0 = Kule Bulmaca Sahnesi, 1 = 🎩 Karakter Özelleştirme Dolabı, 2 = 🏛️ Kule Müzesi
    private int subScreenMode = 0;

    // Her katta gizlenmiş 3 Altın Vida / Yıldız Tozu
    private final float[] hiddenScrewX = new float[3];
    private final float[] hiddenScrewY = new float[3];
    private final boolean[] hiddenScrewFound = new boolean[3];

    // Baykuş Puhu İpucu ve 60 Saniye Zamanlayıcısı
    private long floorStartRealtimeMs = 0L;
    private boolean autoHintTriggered = false;
    private String owlSpeechText = "🦉 Puhu: Merhaba Kaşif! Büyük Kristal parçasını almak için bulmacayı çözelim!";

    // Komik Duman / Hatasız Deneyim Animasyonu ("Oyun Bitti" yerine komik makine dumanı!)
    private int funnySmokeFrames = 0;
    private String funnySmokeMessage = "";

    // Kat Tamamlandı Kutlama Animasyonu
    private int floorClearFrames = 0;
    private float animTick = 0f;

    // Bulmaca Durum Değişkenleri (Sürükle-Bırak ve Dokunma destekli)
    private int activePuzzleType = 0; // 0..11 (12 farklı bulmaca mekaniği)

    // 0: Dişli Çark (3 çarkı sürükleyip 3 yuvaya yerleştir)
    private final float[] gearX = new float[3];
    private final float[] gearY = new float[3];
    private final float[] slotX = new float[3];
    private final float[] slotY = new float[3];
    private final boolean[] gearPlaced = new boolean[3];
    private int draggingIdx = -1;

    // 1: Renkli Kablo Birleştirme (Sol 3 fişi sağdaki aynı renk sokete sürükle)
    private final boolean[] wireConnected = new boolean[3];
    private int activeWireDrag = -1;
    private float wireDragCurX = 0f, wireDragCurY = 0f;

    // 2: Denge Terazisi (Sol kefedeki ağırlığa eşit olacak şekilde sağ kefeye ağırlıkları ekle/çıkar)
    private int scaleTargetWeight = 7;
    private final int[] scaleBlockValues = new int[]{2, 3, 4, 5};
    private final boolean[] scaleBlockOnPan = new boolean[4];

    // 3: Tarif Sırası Karıştırma (Kazana sırayla 4 tatlı malzemeyi dokunarak at)
    private int recipeStep = 0;
    private final int[] recipeOrder = new int[]{0, 1, 2, 3};

    // 4: Meyve Suyu Borusu Döndürme (4 boru parçasını dokunarak doğru yöne çevir)
    private final int[] pipeAngles = new int[]{1, 2, 3, 1}; // 0 olunca düz bağlanır

    // 5: Gizli Şekerleme Bulma (Mutfak dolaplarının arkasına saklanmış 4 kavanozu bul)
    private final boolean[] candyFound = new boolean[4];
    private final int[] candyCupboardPos = new int[]{0, 2, 4, 5};

    // 6: Ayna ile Güneş Işığı Yansıtma (3 aynayı dokunarak çevirip güneş ışığını çiçeğe ulaştır)
    private final int[] mirrorStates = new int[]{1, 2, 1}; // Hepsi 0 olunca ışık çiçeği açtırır

    // 7: Su Kanalı Kapaklarını Açma (3 su vanasını doğru basınç seviyesine getir)
    private final int[] valveLevels = new int[]{0, 2, 1};
    private final int[] valveTargets = new int[]{2, 1, 2};

    // 8: Hayvan Ayak İzi Şifresi (Ormandaki ayak izi sırasını takip et)
    private int trackStep = 0;
    private final int[] trackPattern = new int[]{1, 0, 2, 1};

    // 9: Takımyıldızı Çizerek Birleştirme (Yıldızları 1 -> 2 -> 3 -> 4 -> 5 sırasıyla parmağınla birleştir)
    private int starConnectCount = 1;
    private final float[] constStarX = new float[5];
    private final float[] constStarY = new float[5];

    // 10: Gezegen Yörüngesi Hizalama (3 gezegeni dokunarak/sürükleyerek altın hizalama çizgisine getir)
    private final int[] planetSlots = new int[]{2, 1, 3}; // Hepsi 0 (Üst hiza) olunca kapı açılır

    // 11: Gölge Oyunu Kapısı (3 gölge parçasını döndürerek anahtar şeklini oluştur)
    private final int[] shadowPieces = new int[]{1, 3, 2};

    // Kule Müzesi Etkileşimi
    private int museumDanceTimer = 0;
    private int activeMuseumItem = -1;

    // UI Buton Alanları
    private final RectF btnExitRect = new RectF();
    private final RectF btnPrevFloorRect = new RectF();
    private final RectF btnNextFloorRect = new RectF();
    private final RectF btnWardrobeTabRect = new RectF();
    private final RectF btnMuseumTabRect = new RectF();
    private final RectF btnPuzzleTabRect = new RectF();
    private final RectF btnOwlHintRect = new RectF();
    private final RectF playAreaRect = new RectF();

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            frameScheduled = false;
            if (running) {
                invalidate();
            }
        }
    };

    public PuzzleTowerGameView(Context context, PuzzleTowerListener listener) {
        super(context);
        this.listener = listener;
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        this.dp = dm.density;
        this.sp = dm.scaledDensity;
        loadProgressFromPrefs();
        setupFloorPuzzle(currentFloor, true);
    }

    private void scheduleTick() {
        if (!frameScheduled && running) {
            frameScheduled = true;
            postDelayed(tickRunnable, 20L);
        }
    }

    public void startGame() {
        running = true;
        inCountdown = true;
        countdownStartMs = SystemClock.elapsedRealtime();
        lastAnnouncedCountdown = -1;
        scheduleTick();
        invalidate();
    }

    public void stopGame() {
        running = false;
        removeCallbacks(tickRunnable);
        frameScheduled = false;
    }

    private void loadProgressFromPrefs() {
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS_TOWER, Context.MODE_PRIVATE);
        unlockedMaxFloor = prefs.getInt("unlockedMaxFloor", 1);
        currentFloor = prefs.getInt("currentFloor", 1);
        totalGoldenScrews = prefs.getInt("totalGoldenScrews", 6);
        totalCrystalsCollected = prefs.getInt("totalCrystalsCollected", 0);
        equippedHat = prefs.getInt("equippedHat", 0);
        equippedCape = prefs.getInt("equippedCape", 0);
        equippedBag = prefs.getInt("equippedBag", 0);
        ownedMask = prefs.getInt("ownedMask", 1);
    }

    private void saveProgressToPrefs() {
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS_TOWER, Context.MODE_PRIVATE);
        prefs.edit()
                .putInt("unlockedMaxFloor", unlockedMaxFloor)
                .putInt("currentFloor", currentFloor)
                .putInt("totalGoldenScrews", totalGoldenScrews)
                .putInt("totalCrystalsCollected", totalCrystalsCollected)
                .putInt("equippedHat", equippedHat)
                .putInt("equippedCape", equippedCape)
                .putInt("equippedBag", equippedBag)
                .putInt("ownedMask", ownedMask)
                .apply();
    }

    private int getZoneIndexForFloor(int floor) {
        if (floor <= 5) return 0;  // 1. Bölge: Oyuncak ve Saat Atölyesi (Kat 1-5)
        if (floor <= 10) return 1; // 2. Bölge: Dev Mutfak ve Şekerleme Katı (Kat 6-10)
        if (floor <= 15) return 2; // 3. Bölge: Sera ve Doğa Katı (Kat 11-15)
        return 3;                  // 4. Bölge: Gözlemevi ve Yıldız Katı (Kat 16-20)
    }

    private String getZoneTitle(int zone) {
        if (zone == 0) return "⚙️ 1. Bölge: Oyuncak ve Saat Atölyesi";
        if (zone == 1) return "🍭 2. Bölge: Dev Mutfak ve Şekerleme Katı";
        if (zone == 2) return "🌿 3. Bölge: Sera ve Doğa Katı";
        return "🔭 4. Bölge: Gözlemevi ve Yıldız Katı";
    }

    private void setupFloorPuzzle(int floor, boolean withCountdown) {
        if (floor < 1) floor = 1;
        if (floor > 20) floor = 20;
        this.currentFloor = floor;
        this.floorStartRealtimeMs = SystemClock.elapsedRealtime();
        this.autoHintTriggered = false;
        this.funnySmokeFrames = 0;
        this.floorClearFrames = 0;
        this.draggingIdx = -1;
        this.activeWireDrag = -1;

        int zone = getZoneIndexForFloor(floor);
        int subType = (floor - 1) % 3;
        this.activePuzzleType = zone * 3 + subType;

        // Her katta gizlenmiş 3 Altın Vida / Yıldız Tozu rastgele köşelere yerleştirilir
        for (int i = 0; i < 3; i++) {
            hiddenScrewFound[i] = false;
            hiddenScrewX[i] = 0.14f + i * 0.34f + (random.nextFloat() * 0.08f);
            hiddenScrewY[i] = (i == 1) ? 0.18f : 0.84f;
        }

        // Bulmaca türüne göre başlangıç durumu ve Bilge Baykuş Puhu mesajı
        if (activePuzzleType == 0) {
            for (int i = 0; i < 3; i++) {
                gearPlaced[i] = false;
                gearX[i] = 0.22f + i * 0.28f;
                gearY[i] = 0.72f;
                slotX[i] = 0.22f + i * 0.28f;
                slotY[i] = 0.38f;
            }
            owlSpeechText = "🦉 Puhu: Alt sıradaki 3 altın dişli çarkı parmağınla yukarıdaki yuvalarına sürükle!";
        } else if (activePuzzleType == 1) {
            for (int i = 0; i < 3; i++) wireConnected[i] = false;
            owlSpeechText = "🦉 Puhu: Soldaki renkli enerji kablolarını sağdaki aynı renkli soketlere sürükleyip birleştir!";
        } else if (activePuzzleType == 2) {
            scaleTargetWeight = (floor == 3) ? 7 : 9;
            for (int i = 0; i < 4; i++) scaleBlockOnPan[i] = false;
            owlSpeechText = "🦉 Puhu: Terazinin dengelenmesi için toplamı " + scaleTargetWeight + " kg olan ağırlıklara dokun!";
        } else if (activePuzzleType == 3) {
            recipeStep = 0;
            owlSpeechText = "🦉 Puhu: Dev Şekerleme Kazanı için yukarıdaki tarif sırasına göre malzemelere sırayla dokun!";
        } else if (activePuzzleType == 4) {
            pipeAngles[0] = 1;
            pipeAngles[1] = 2;
            pipeAngles[2] = 3;
            pipeAngles[3] = 1;
            owlSpeechText = "🦉 Puhu: Borulara dokunarak döndür ve çilek suyunu bardağa akıt!";
        } else if (activePuzzleType == 5) {
            for (int i = 0; i < 4; i++) candyFound[i] = false;
            owlSpeechText = "🦉 Puhu: Mutfak dolaplarına dokunarak gizlenmiş 4 Kristal Şeker Kavanozunu bul!";
        } else if (activePuzzleType == 6) {
            mirrorStates[0] = 1;
            mirrorStates[1] = 2;
            mirrorStates[2] = 1;
            owlSpeechText = "🦉 Puhu: 3 aynaya dokunarak doğru açıya çevir ve güneş ışığını Sihirli Çiçeğe yansıt!";
        } else if (activePuzzleType == 7) {
            valveLevels[0] = 0;
            valveLevels[1] = 2;
            valveLevels[2] = 0;
            valveTargets[0] = 2;
            valveTargets[1] = 1;
            valveTargets[2] = 3;
            owlSpeechText = "🦉 Puhu: Su kanalı kapaklarına dokunarak her kanalın su seviyesini yeşil hedef çizgisine getir!";
        } else if (activePuzzleType == 8) {
            trackStep = 0;
            owlSpeechText = "🦉 Puhu: Yukarıdaki hayvan ayak izi şifresini (🐾 -> 🦌 -> 🐻 -> 🐾) sırasıyla takip et!";
        } else if (activePuzzleType == 9) {
            starConnectCount = 1;
            constStarX[0] = 0.20f; constStarY[0] = 0.65f;
            constStarX[1] = 0.35f; constStarY[1] = 0.38f;
            constStarX[2] = 0.55f; constStarY[2] = 0.58f;
            constStarX[3] = 0.72f; constStarY[3] = 0.34f;
            constStarX[4] = 0.84f; constStarY[4] = 0.62f;
            owlSpeechText = "🦉 Puhu: Gökyüzündeki takımyıldızını 1'den 5'e kadar sırayla dokunarak veya çizerek birleştir!";
        } else if (activePuzzleType == 10) {
            planetSlots[0] = 2;
            planetSlots[1] = 1;
            planetSlots[2] = 3;
            owlSpeechText = "🦉 Puhu: 3 gezegene dokunarak hepsini ortadaki Altın Tutulma Çizgisinde (⭐) hizala!";
        } else {
            shadowPieces[0] = 1;
            shadowPieces[1] = 2;
            shadowPieces[2] = 3;
            owlSpeechText = "🦉 Puhu: Gölge disklerine dokunarak döndür ve Sihirli Kule Anahtarının gölgesini tamamla!";
        }

        if (withCountdown) {
            inCountdown = true;
            countdownStartMs = SystemClock.elapsedRealtime();
            lastAnnouncedCountdown = -1;
        }
        saveProgressToPrefs();
        invalidate();
    }

    private void triggerFunnyMistakeSmoke(String msg) {
        funnySmokeFrames = 45;
        funnySmokeMessage = msg;
        owlSpeechText = "🦉 Puhu: Haha! Makine gıdıklanıp duman çıkardı 💨 Hiç sorun değil, tekrar dene!";
        if (listener != null) {
            listener.onTowerActionSound(false);
        }
        invalidate();
    }

    private void onPuzzleSolved() {
        floorClearFrames = 75;
        totalCrystalsCollected++;
        totalGoldenScrews += 2;
        if (currentFloor >= unlockedMaxFloor && unlockedMaxFloor < 20) {
            unlockedMaxFloor = currentFloor + 1;
        }
        owlSpeechText = "🦉 Puhu: HARİKASIN! Kat " + currentFloor + " Büyük Kristal Parçası (💎) kurtarıldı!";
        saveProgressToPrefs();
        if (listener != null) {
            listener.onTowerActionSound(true);
        }
        invalidate();
    }

    private void giveOwlHint() {
        String[] hints = new String[]{
                "🦉 İpucu: Alt sıradaki her çarkı tam üstündeki noktalı dairenin üstüne sürükleyip bırak!",
                "🦉 İpucu: Kırmızı fişi kırmızıya, maviyi maviye, sarıyı sarıya parmağınla çek!",
                "🦉 İpucu: 3 kg ve 4 kg (veya 4 kg ve 5 kg) bloklarına dokunarak hedef sayıya ulaş!",
                "🦉 İpucu: Üstteki tarifte 1. sırada Çilek, 2. sırada Bal, 3. sırada Süt, 4. sırada Kakao var!",
                "🦉 İpucu: Tüm borular yatay (━) olana kadar eğik borulara dokun!",
                "🦉 İpucu: Üst sol, üst sağ ve alt ortadaki dolap kapaklarına dokun!",
                "🦉 İpucu: Üç aynanın da ok işareti sağa (➔) bakana kadar aynalara dokun!",
                "🦉 İpucu: 1. vanayı 2 kademeye, 2. vanayı 1 kademeye, 3. vanayı 3 kademeye getir!",
                "🦉 İpucu: Sırasıyla Kedi Patisi (🐾), Geyik İzi (🦌), Ayı İzi (🐻) ve tekrar Kedi Patisi (🐾)!",
                "🦉 İpucu: Şimdi " + (starConnectCount + 1) + " numaralı parlayan yıldıza dokun!",
                "🦉 İpucu: Halka içindeki gezegenlere dokunarak üçünü de yeşil merkez çizgiye getir!",
                "🦉 İpucu: Üç gölge çarkını da ⬆️ (Dik) konuma gelene kadar çevir!"
        };
        owlSpeechText = hints[activePuzzleType % hints.length];
        if (listener != null) {
            listener.onTowerActionSound(true);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        animTick += 0.07f;
        long nowMs = SystemClock.elapsedRealtime();

        // 1 dakikadan (60.000 ms) fazla takılırsa Bilge Baykuş Puhu otomatik ipucu sunar!
        if (!inCountdown && subScreenMode == 0 && floorClearFrames == 0 && !autoHintTriggered
                && (nowMs - floorStartRealtimeMs > 60000L)) {
            autoHintTriggered = true;
            giveOwlHint();
        }

        int zone = getZoneIndexForFloor(currentFloor);
        int[] zoneBgColors = new int[]{
                0xFF1E293B, // 1. Bölge: Saat Atölyesi
                0xFF4A154B, // 2. Bölge: Şekerleme Mutfağı
                0xFF064E3B, // 3. Bölge: Doğa Serası
                0xFF0F172A  // 4. Bölge: Yıldız Gözlemevi
        };
        canvas.drawColor(zoneBgColors[zone]);

        // 1. Üst Başlık ve Sekmeler (Kule Katı, Altın Vida 🔩, Kristal 💎, Dolap 🎩, Müze 🏛️)
        float hudH = 98f * dp;
        drawTowerHeader(canvas, w, hudH, zone);

        // 2. Orta Oyun / Dolap / Müze Alanı
        float bottomOwlH = 88f * dp;
        playAreaRect.set(10f * dp, hudH + 8f * dp, w - 10f * dp, h - bottomOwlH - 8f * dp);

        if (subScreenMode == 1) {
            drawCharacterCustomizationScreen(canvas);
        } else if (subScreenMode == 2) {
            drawTowerMuseumScreen(canvas);
        } else {
            drawActiveFloorPuzzle(canvas, zone);
        }

        // 3. Alt Yardımcı Karakter (Bilge Baykuş Puhu + Kaşif Robot + İpucu Butonu)
        drawBottomOwlAndExplorerBar(canvas, w, h, bottomOwlH);

        // 4. 3-2-1-BAŞLA! Geri Sayım Katmanı
        if (inCountdown) {
            int step = 0;
            long el = nowMs - countdownStartMs;
            if (el < 850L) step = 3;
            else if (el < 1700L) step = 2;
            else if (el < 2550L) step = 1;
            else if (el < 3100L) step = 0;
            else {
                inCountdown = false;
                floorStartRealtimeMs = nowMs;
            }
            if (inCountdown && step != lastAnnouncedCountdown) {
                lastAnnouncedCountdown = step;
                if (listener != null) listener.onCountdownTick(step == 0);
            }
            if (inCountdown) {
                drawCountdownOverlay(canvas, w, h, step);
            }
        }

        if (running) {
            scheduleTick();
        }
    }

    private void drawTowerHeader(Canvas canvas, int w, float hudH, int zone) {
        paint.setColor(0xDD0F172A);
        canvas.drawRect(0, 0, w, hudH, paint);

        float r1Top = 6f * dp;
        float r1Bot = 42f * dp;

        // Sol: Çıkış
        btnExitRect.set(6f * dp, r1Top, 74f * dp, r1Bot);
        paint.setColor(0xFFDC2626);
        canvas.drawRoundRect(btnExitRect, 8f * dp, 8f * dp, paint);
        paint.setColor(0xFFFFFFFF);
        paint.setTextSize(12f * sp);
        paint.setFakeBoldText(true);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("⬅️ Lobi", btnExitRect.centerX(), r1Bot - 11f * dp, paint);

        // Kat Seçici (< Kat X/20 >)
        btnPrevFloorRect.set(80f * dp, r1Top, 112f * dp, r1Bot);
        btnNextFloorRect.set(208f * dp, r1Top, 240f * dp, r1Bot);
        paint.setColor(0xFF334155);
        canvas.drawRoundRect(btnPrevFloorRect, 8f * dp, 8f * dp, paint);
        canvas.drawRoundRect(btnNextFloorRect, 8f * dp, 8f * dp, paint);
        paint.setColor(0xFFFFFFFF);
        paint.setTextSize(14f * sp);
        canvas.drawText("◀", btnPrevFloorRect.centerX(), r1Bot - 10f * dp, paint);
        canvas.drawText("▶", btnNextFloorRect.centerX(), r1Bot - 10f * dp, paint);

        paint.setColor(0xFFFFD600);
        paint.setTextSize(13.5f * sp);
        canvas.drawText("🏰 KAT " + currentFloor + "/20", 160f * dp, r1Bot - 11f * dp, paint);

        // Sağ: Altın Vida (🔩) ve Büyük Kristal (💎) Sayacı
        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setTextSize(13f * sp);
        paint.setColor(0xFF38BDF8);
        canvas.drawText("🔩 " + totalGoldenScrews + "  💎 " + totalCrystalsCollected + "/20", w - 8f * dp, r1Bot - 11f * dp, paint);

        // Alt Satır: 3 Sekme (🧩 Bulmaca Katı | 🎩 Karakter Dolabı | 🏛️ Kule Müzesi)
        float r2Top = 50f * dp;
        float r2Bot = 88f * dp;
        float gap = 6f * dp;
        float tabW = (w - gap * 4f) / 3f;

        btnPuzzleTabRect.set(gap, r2Top, gap + tabW, r2Bot);
        btnWardrobeTabRect.set(gap * 2 + tabW, r2Top, gap * 2 + tabW * 2, r2Bot);
        btnMuseumTabRect.set(gap * 3 + tabW * 2, r2Top, gap * 3 + tabW * 3, r2Bot);

        drawTabButton(canvas, btnPuzzleTabRect, "🧩 Kule Katı", subScreenMode == 0, 0xFF2563EB);
        drawTabButton(canvas, btnWardrobeTabRect, "🎩 Özelleştir", subScreenMode == 1, 0xFF9333EA);
        drawTabButton(canvas, btnMuseumTabRect, "🏛️ Kule Müzesi", subScreenMode == 2, 0xFFD97706);
    }

    private void drawTabButton(Canvas canvas, RectF rect, String title, boolean active, int activeColor) {
        paint.setColor(active ? activeColor : 0xFF334155);
        canvas.drawRoundRect(rect, 9f * dp, 9f * dp, paint);
        paint.setColor(0xFFFFFFFF);
        paint.setTextSize(12.5f * sp);
        paint.setFakeBoldText(active);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(title, rect.centerX(), rect.bottom - 11f * dp, paint);
    }

    private void drawActiveFloorPuzzle(Canvas canvas, int zone) {
        int[] cardColors = new int[]{0xFFF8FAFC, 0xFFFFF1F2, 0xFFF0FDF4, 0xFFEFF6FF};
        paint.setColor(cardColors[zone]);
        canvas.drawRoundRect(playAreaRect, 18f * dp, 18f * dp, paint);

        float left = playAreaRect.left;
        float top = playAreaRect.top;
        float pw = playAreaRect.width();
        float ph = playAreaRect.height();

        // Bölge Başlığı Bandı
        paint.setColor(0xFF1E293B);
        canvas.drawRoundRect(new RectF(left + 10f * dp, top + 10f * dp, left + pw - 10f * dp, top + 46f * dp), 10f * dp, 10f * dp, paint);
        paint.setColor(0xFFFFD600);
        paint.setTextSize(13.5f * sp);
        paint.setFakeBoldText(true);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(getZoneTitle(zone) + " (Kat " + currentFloor + ")", playAreaRect.centerX(), top + 33f * dp, paint);

        // Katta Gizlenmiş 3 Altın Vida / Yıldız Tozu (🔩 / ✨)
        String screwIcon = (zone >= 2) ? "✨" : "🔩";
        for (int i = 0; i < 3; i++) {
            if (!hiddenScrewFound[i]) {
                float sx = left + hiddenScrewX[i] * pw;
                float sy = top + hiddenScrewY[i] * ph;
                paint.setColor(0x55FFD600);
                canvas.drawCircle(sx, sy, 18f * dp, paint);
                paint.setColor(0xFF0F172A);
                paint.setTextSize(17f * sp);
                canvas.drawText(screwIcon, sx, sy + 6f * dp, paint);
            }
        }

        // 12 Farklı Bulmaca Çizimi
        if (activePuzzleType == 0) {
            // 1. Dişli Çarkları Doğru Yuvalara Sürükleme
            String[] gearColors = new String[]{"⚙️ Altın Çark", "⚙️ Gümüş Çark", "⚙️ Bakır Çark"};
            int[] gCols = new int[]{0xFFF59E0B, 0xFF64748B, 0xFFD97706};
            for (int i = 0; i < 3; i++) {
                float sx = left + slotX[i] * pw;
                float sy = top + slotY[i] * ph;
                paint.setColor(0xFFCBD5E1);
                canvas.drawCircle(sx, sy, 34f * dp, paint);
                paint.setColor(0xFF475569);
                paint.setTextSize(11f * sp);
                canvas.drawText("Yuva " + (i + 1), sx, sy + 4f * dp, paint);
            }
            for (int i = 0; i < 3; i++) {
                float gx = left + gearX[i] * pw;
                float gy = top + gearY[i] * ph;
                paint.setColor(gCols[i]);
                canvas.drawCircle(gx, gy, 32f * dp, paint);
                paint.setColor(0xFFFFFFFF);
                paint.setTextSize(22f * sp);
                canvas.drawText("⚙️", gx, gy + 8f * dp, paint);
            }
        } else if (activePuzzleType == 1) {
            // 2. Renkli Kabloları Birleştirme
            int[] wCols = new int[]{0xFFEF4444, 0xFF3B82F6, 0xFFEAB308};
            String[] wNames = new String[]{"🔴 Kırmızı", "🔵 Mavi", "🟡 Sarı"};
            for (int i = 0; i < 3; i++) {
                float ly = top + (0.30f + i * 0.22f) * ph;
                float lx = left + 0.20f * pw;
                float rx = left + 0.80f * pw;

                paint.setColor(wCols[i]);
                canvas.drawCircle(lx, ly, 26f * dp, paint);
                canvas.drawCircle(rx, ly, 26f * dp, paint);

                paint.setColor(0xFFFFFFFF);
                paint.setTextSize(11f * sp);
                canvas.drawText(wNames[i], lx, ly + 4f * dp, paint);
                canvas.drawText(wireConnected[i] ? "✅" : "🔌", rx, ly + 5f * dp, paint);

                if (wireConnected[i]) {
                    paint.setColor(wCols[i]);
                    paint.setStrokeWidth(7f * dp);
                    canvas.drawLine(lx, ly, rx, ly, paint);
                    paint.setStrokeWidth(1f);
                }
            }
            if (activeWireDrag >= 0) {
                float ly = top + (0.30f + activeWireDrag * 0.22f) * ph;
                float lx = left + 0.20f * pw;
                paint.setColor(wCols[activeWireDrag]);
                paint.setStrokeWidth(7f * dp);
                canvas.drawLine(lx, ly, wireDragCurX, wireDragCurY, paint);
                paint.setStrokeWidth(1f);
            }
        } else if (activePuzzleType == 2) {
            // 3. Denge Terazisi Bulmacası
            int currentSum = 0;
            for (int i = 0; i < 4; i++) {
                if (scaleBlockOnPan[i]) currentSum += scaleBlockValues[i];
            }
            float cx = playAreaRect.centerX();
            float beamY = top + 0.44f * ph;
            float tilt = Math.max(-18f, Math.min(18f, (currentSum - scaleTargetWeight) * 3.5f)) * dp;

            paint.setColor(0xFF475569);
            paint.setStrokeWidth(6f * dp);
            canvas.drawLine(cx - 105f * dp, beamY - tilt, cx + 105f * dp, beamY + tilt, paint);
            paint.setStrokeWidth(1f);

            // Sol Kefe (Hedef)
            paint.setColor(0xFF2563EB);
            canvas.drawRoundRect(new RectF(cx - 135f * dp, beamY - tilt, cx - 55f * dp, beamY - tilt + 44f * dp), 8f * dp, 8f * dp, paint);
            paint.setColor(0xFFFFFFFF);
            paint.setTextSize(15f * sp);
            canvas.drawText("Hedef: " + scaleTargetWeight + "kg", cx - 95f * dp, beamY - tilt + 27f * dp, paint);

            // Sağ Kefe (Seçilen)
            paint.setColor(currentSum == scaleTargetWeight ? 0xFF16A34A : 0xFFD97706);
            canvas.drawRoundRect(new RectF(cx + 55f * dp, beamY + tilt, cx + 135f * dp, beamY + tilt + 44f * dp), 8f * dp, 8f * dp, paint);
            paint.setColor(0xFFFFFFFF);
            canvas.drawText("Kefe: " + currentSum + "kg", cx + 95f * dp, beamY + tilt + 27f * dp, paint);

            // Alt Ağırlık Blokları
            for (int i = 0; i < 4; i++) {
                float bx = left + (0.17f + i * 0.22f) * pw;
                float by = top + 0.74f * ph;
                paint.setColor(scaleBlockOnPan[i] ? 0xFF16A34A : 0xFF334155);
                canvas.drawRoundRect(new RectF(bx - 30f * dp, by - 26f * dp, bx + 30f * dp, by + 26f * dp), 10f * dp, 10f * dp, paint);
                paint.setColor(0xFFFFFFFF);
                paint.setTextSize(15f * sp);
                canvas.drawText(scaleBlockValues[i] + " kg", bx, by + 6f * dp, paint);
            }
        } else if (activePuzzleType == 3) {
            // 4. Tarif Sırasına Göre Malzeme Karıştırma
            String[] items = new String[]{"🍓 Çilek", "🍯 Bal", "🥛 Süt", "🍫 Kakao"};
            paint.setColor(0xFF831843);
            paint.setTextSize(14f * sp);
            canvas.drawText("📜 Tarif Sırası: 1.🍓 Çilek ➔ 2.🍯 Bal ➔ 3.🥛 Süt ➔ 4.🍫 Kakao", playAreaRect.centerX(), top + 0.28f * ph, paint);

            paint.setColor(0xFF334155);
            canvas.drawRoundRect(new RectF(playAreaRect.centerX() - 75f * dp, top + 0.36f * ph, playAreaRect.centerX() + 75f * dp, top + 0.56f * ph), 18f * dp, 18f * dp, paint);
            paint.setColor(0xFFFFFFFF);
            paint.setTextSize(15f * sp);
            canvas.drawText("🍲 Kazan (" + recipeStep + "/4)", playAreaRect.centerX(), top + 0.48f * ph, paint);

            for (int i = 0; i < 4; i++) {
                float bx = left + (0.16f + i * 0.22f) * pw;
                float by = top + 0.74f * ph;
                boolean added = i < recipeStep;
                paint.setColor(added ? 0xFF16A34A : 0xFFDB2777);
                canvas.drawRoundRect(new RectF(bx - 34f * dp, by - 28f * dp, bx + 34f * dp, by + 28f * dp), 12f * dp, 12f * dp, paint);
                paint.setColor(0xFFFFFFFF);
                paint.setTextSize(13f * sp);
                canvas.drawText(items[i], bx, by + 5f * dp, paint);
            }
        } else if (activePuzzleType == 4) {
            // 5. Borulardan Meyve Suyu Akıtma
            paint.setColor(0xFF831843);
            paint.setTextSize(14f * sp);
            canvas.drawText("🍓 Meyve Suyu Borularına Dokunarak Hepsini Yatay (━) Hizala!", playAreaRect.centerX(), top + 0.30f * ph, paint);
            String[] pipeSymbols = new String[]{"━", "┃", "┏", "┛"};
            for (int i = 0; i < 4; i++) {
                float bx = left + (0.18f + i * 0.21f) * pw;
                float by = top + 0.56f * ph;
                boolean aligned = (pipeAngles[i] % 4 == 0);
                paint.setColor(aligned ? 0xFF16A34A : 0xFF0284C7);
                canvas.drawRoundRect(new RectF(bx - 32f * dp, by - 32f * dp, bx + 32f * dp, by + 32f * dp), 12f * dp, 12f * dp, paint);
                paint.setColor(0xFFFFFFFF);
                paint.setTextSize(26f * sp);
                canvas.drawText(pipeSymbols[pipeAngles[i] % 4], bx, by + 10f * dp, paint);
            }
        } else if (activePuzzleType == 5) {
            // 6. Gizli Şekerleme Kavanozlarını Bulma
            paint.setColor(0xFF831843);
            paint.setTextSize(14f * sp);
            canvas.drawText("🍬 Dolap Kapaklarına Dokunarak 4 Gizli Şeker Kavanozunu Bul!", playAreaRect.centerX(), top + 0.26f * ph, paint);
            for (int i = 0; i < 6; i++) {
                int r = i / 3;
                int c = i % 3;
                float bx = left + (0.22f + c * 0.28f) * pw;
                float by = top + (0.44f + r * 0.26f) * ph;

                int candyIdx = -1;
                for (int k = 0; k < 4; k++) {
                    if (candyCupboardPos[k] == i) candyIdx = k;
                }
                boolean opened = (candyIdx >= 0 && candyFound[candyIdx]);
                paint.setColor(opened ? 0xFF16A34A : 0xFF9333EA);
                canvas.drawRoundRect(new RectF(bx - 38f * dp, by - 30f * dp, bx + 38f * dp, by + 30f * dp), 12f * dp, 12f * dp, paint);
                paint.setColor(0xFFFFFFFF);
                paint.setTextSize(20f * sp);
                canvas.drawText(opened ? "🏺✨" : "🚪 Dolap", bx, by + 7f * dp, paint);
            }
        } else if (activePuzzleType == 6) {
            // 7. Ayna Kullanarak Güneş Işığını Bitkilere Yansıtma
            paint.setColor(0xFF065F46);
            paint.setTextSize(14f * sp);
            canvas.drawText("☀️ Aynalara Dokunarak Güneş Işığını Sihirli Çiçeğe (🌻) Yansıt!", playAreaRect.centerX(), top + 0.28f * ph, paint);
            String[] mirrorDirs = new String[]{"🪞 ➔", "🪞 ⬇", "🪞 ⬅", "🪞 ⬆"};
            for (int i = 0; i < 3; i++) {
                float bx = left + (0.22f + i * 0.28f) * pw;
                float by = top + 0.56f * ph;
                boolean ok = (mirrorStates[i] % 4 == 0);
                paint.setColor(ok ? 0xFFEAB308 : 0xFF0284C7);
                canvas.drawCircle(bx, by, 36f * dp, paint);
                paint.setColor(0xFFFFFFFF);
                paint.setTextSize(16f * sp);
                canvas.drawText(mirrorDirs[mirrorStates[i] % 4], bx, by + 6f * dp, paint);
            }
        } else if (activePuzzleType == 7) {
            // 8. Su Kanallarını Açma
            paint.setColor(0xFF065F46);
            paint.setTextSize(14f * sp);
            canvas.drawText("💧 Vanalara Dokunarak Su Seviyelerini Hedef Sayılara Eşitle!", playAreaRect.centerX(), top + 0.28f * ph, paint);
            for (int i = 0; i < 3; i++) {
                float bx = left + (0.22f + i * 0.28f) * pw;
                float by = top + 0.58f * ph;
                boolean ok = (valveLevels[i] == valveTargets[i]);
                paint.setColor(ok ? 0xFF16A34A : 0xFF0284C7);
                canvas.drawRoundRect(new RectF(bx - 38f * dp, by - 42f * dp, bx + 38f * dp, by + 42f * dp), 12f * dp, 12f * dp, paint);
                paint.setColor(0xFFFFFFFF);
                paint.setTextSize(14f * sp);
                canvas.drawText("Hedef: " + valveTargets[i], bx, by - 14f * dp, paint);
                paint.setTextSize(18f * sp);
                canvas.drawText("💧 " + valveLevels[i], bx, by + 18f * dp, paint);
            }
        } else if (activePuzzleType == 8) {
            // 9. Hayvanların Ayak İzlerini Takip Ederek Şifre Çözme
            paint.setColor(0xFF065F46);
            paint.setTextSize(14f * sp);
            canvas.drawText("🐾 İpucu Sırası: 1.Kedi(🐾) ➔ 2.Geyik(🦌) ➔ 3.Ayı(🐻) ➔ 4.Kedi(🐾)", playAreaRect.centerX(), top + 0.30f * ph, paint);
            String[] footprints = new String[]{"🦌 Geyik", "🐾 Kedi", "🐻 Ayı"};
            for (int i = 0; i < 3; i++) {
                float bx = left + (0.22f + i * 0.28f) * pw;
                float by = top + 0.62f * ph;
                paint.setColor(0xFF15803D);
                canvas.drawRoundRect(new RectF(bx - 40f * dp, by - 32f * dp, bx + 40f * dp, by + 32f * dp), 12f * dp, 12f * dp, paint);
                paint.setColor(0xFFFFFFFF);
                paint.setTextSize(15f * sp);
                canvas.drawText(footprints[i], bx, by + 6f * dp, paint);
            }
        } else if (activePuzzleType == 9) {
            // 10. Takımyıldızlarını Çizerek Birleştirme
            paint.setColor(0xFF1E3A8A);
            paint.setTextSize(14f * sp);
            canvas.drawText("✨ Yıldızları 1'den 5'e Kadar Sırayla Dokunarak Birleştir!", playAreaRect.centerX(), top + 0.24f * ph, paint);

            paint.setColor(0xFFF59E0B);
            paint.setStrokeWidth(5f * dp);
            for (int i = 0; i < starConnectCount - 1; i++) {
                canvas.drawLine(left + constStarX[i] * pw, top + constStarY[i] * ph,
                        left + constStarX[i + 1] * pw, top + constStarY[i + 1] * ph, paint);
            }
            paint.setStrokeWidth(1f);

            for (int i = 0; i < 5; i++) {
                float sx = left + constStarX[i] * pw;
                float sy = top + constStarY[i] * ph;
                boolean connected = (i < starConnectCount);
                paint.setColor(connected ? 0xFFF59E0B : 0xFF1E293B);
                canvas.drawCircle(sx, sy, 24f * dp, paint);
                paint.setColor(0xFFFFFFFF);
                paint.setTextSize(15f * sp);
                canvas.drawText("⭐" + (i + 1), sx, sy + 5f * dp, paint);
            }
        } else if (activePuzzleType == 10) {
            // 11. Gezegenlerin Yörüngelerini Hizalama
            paint.setColor(0xFF1E3A8A);
            paint.setTextSize(14f * sp);
            canvas.drawText("🪐 Gezegenlere Dokunarak Üçünü de Yeşil Tutulma Çizgisinde Hizala!", playAreaRect.centerX(), top + 0.26f * ph, paint);

            float targetLineY = top + 0.56f * ph;
            paint.setColor(0xFF16A34A);
            paint.setStrokeWidth(4f * dp);
            canvas.drawLine(left + 20f * dp, targetLineY, left + pw - 20f * dp, targetLineY, paint);
            paint.setStrokeWidth(1f);

            String[] pIcons = new String[]{"🌍 Dünya", "🪐 Satürn", "🔴 Mars"};
            for (int i = 0; i < 3; i++) {
                float px = left + (0.22f + i * 0.28f) * pw;
                int offset = (planetSlots[i] % 4);
                float py = targetLineY + (offset == 0 ? 0f : (offset == 1 ? -45f * dp : (offset == 2 ? 45f * dp : -25f * dp)));
                paint.setColor(offset == 0 ? 0xFF16A34A : 0xFF2563EB);
                canvas.drawCircle(px, py, 32f * dp, paint);
                paint.setColor(0xFFFFFFFF);
                paint.setTextSize(12f * sp);
                canvas.drawText(pIcons[i], px, py + 4f * dp, paint);
            }
        } else {
            // 12. Gölge Oyunlarıyla Kapı Açma
            paint.setColor(0xFF1E3A8A);
            paint.setTextSize(14f * sp);
            canvas.drawText("🔑 Gölge Disklerine Dokunarak Anahtar Gölgelerini Dik (⬆️) Hizala!", playAreaRect.centerX(), top + 0.28f * ph, paint);
            String[] shDirs = new String[]{"🔑 ⬆️", "🔑 ➡️", "🔑 ⬇️", "🔑 ⬅️"};
            for (int i = 0; i < 3; i++) {
                float bx = left + (0.22f + i * 0.28f) * pw;
                float by = top + 0.58f * ph;
                boolean ok = (shadowPieces[i] % 4 == 0);
                paint.setColor(ok ? 0xFF16A34A : 0xFF1E293B);
                canvas.drawCircle(bx, by, 38f * dp, paint);
                paint.setColor(0xFFFFFFFF);
                paint.setTextSize(16f * sp);
                canvas.drawText(shDirs[shadowPieces[i] % 4], bx, by + 6f * dp, paint);
            }
        }

        // Komik Duman Animasyonu (Hatasız Deneyim: Oyun Bitti yerine komik makine dumanı!)
        if (funnySmokeFrames > 0) {
            funnySmokeFrames--;
            float cx = playAreaRect.centerX();
            float cy = playAreaRect.centerY();
            paint.setColor(0xDD334155);
            canvas.drawRoundRect(new RectF(cx - 140f * dp, cy - 45f * dp, cx + 140f * dp, cy + 45f * dp), 16f * dp, 16f * dp, paint);
            paint.setColor(0xFFFFFFFF);
            paint.setTextSize(16f * sp);
            canvas.drawText("💨 PUF! " + funnySmokeMessage, cx, cy - 4f * dp, paint);
            paint.setTextSize(13f * sp);
            paint.setColor(0xFFFDE047);
            canvas.drawText("Makine gıdıklandı ve sıfırlandı, tekrar dene! 😊", cx, cy + 22f * dp, paint);
        }

        // Kat Tamamlandı Kutlaması
        if (floorClearFrames > 0) {
            floorClearFrames--;
            float cx = playAreaRect.centerX();
            float cy = playAreaRect.centerY();
            paint.setColor(0xEE16A34A);
            canvas.drawRoundRect(new RectF(cx - 145f * dp, cy - 55f * dp, cx + 145f * dp, cy + 55f * dp), 18f * dp, 18f * dp, paint);
            paint.setColor(0xFFFFFFFF);
            paint.setTextSize(18f * sp);
            canvas.drawText("🎉 KAT " + currentFloor + " TAMAMLANDI!", cx, cy - 10f * dp, paint);
            paint.setTextSize(14f * sp);
            canvas.drawText("💎 Büyük Kristal Parçası + 🔩 2 Altın Vida Kazandın!", cx, cy + 20f * dp, paint);
            if (floorClearFrames == 0) {
                int nextF = (currentFloor < 20) ? (currentFloor + 1) : 1;
                setupFloorPuzzle(nextF, true);
            }
        }
    }

    /**
     * 🎩 Karakter Özelleştirme Ekranı (Altın Vidalarla Komik Şapkalar, Pelerinler ve Çantalar)
     */
    private void drawCharacterCustomizationScreen(Canvas canvas) {
        paint.setColor(0xFFF8FAFC);
        canvas.drawRoundRect(playAreaRect, 18f * dp, 18f * dp, paint);

        float cx = playAreaRect.centerX();
        float top = playAreaRect.top;

        paint.setColor(0xFF4C1D95);
        paint.setTextSize(16f * sp);
        paint.setFakeBoldText(true);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("🎩 KAŞİF ÖZELLEŞTİRME DOLABI (🔩 " + totalGoldenScrews + " Altın Vida)", cx, top + 30f * dp, paint);

        // Kaşif Karakter Önizlemesi
        String[] hatIcons = new String[]{"", "🎩", "🧢", "👑"};
        String[] capeIcons = new String[]{"", "🧣", "🌟"};
        String[] bagIcons = new String[]{"", "🎒", "🔋"};

        paint.setColor(0xFFEDE9FE);
        canvas.drawCircle(cx, top + 90f * dp, 44f * dp, paint);
        paint.setColor(0xFF0F172A);
        paint.setTextSize(34f * sp);
        canvas.drawText("🤖", cx, top + 102f * dp, paint);
        if (equippedHat > 0) {
            paint.setTextSize(24f * sp);
            canvas.drawText(hatIcons[equippedHat], cx, top + 62f * dp, paint);
        }
        if (equippedCape > 0) {
            paint.setTextSize(22f * sp);
            canvas.drawText(capeIcons[equippedCape], cx - 34f * dp, top + 104f * dp, paint);
        }
        if (equippedBag > 0) {
            paint.setTextSize(22f * sp);
            canvas.drawText(bagIcons[equippedBag], cx + 34f * dp, top + 104f * dp, paint);
        }

        // 6 Özelleştirme Öğesi Kartı
        String[] itemNames = new String[]{
                "🎩 Sihirbaz Şapkası (3🔩)", "🧢 Pervane Şapka (4🔩)",
                "👑 Altın Kral Tacı (6🔩)", "🧣 Süper Pelerin (4🔩)",
                "🌟 Yıldız Pelerin (5🔩)", "🎒 Roket Çanta (5🔩)"
        };
        float startY = top + 148f * dp;
        float rowH = 52f * dp;
        float colW = playAreaRect.width() * 0.44f;

        for (int i = 0; i < 6; i++) {
            int r = i / 2;
            int c = i % 2;
            float left = playAreaRect.left + (c == 0 ? 12f * dp : playAreaRect.width() * 0.52f);
            float ry = startY + r * (rowH + 10f * dp);
            boolean owned = ((ownedMask & (1 << (i + 1))) != 0);
            paint.setColor(owned ? 0xFF16A34A : 0xFF7C3AED);
            canvas.drawRoundRect(new RectF(left, ry, left + colW, ry + rowH), 10f * dp, 10f * dp, paint);
            paint.setColor(0xFFFFFFFF);
            paint.setTextSize(12f * sp);
            canvas.drawText(itemNames[i], left + colW * 0.5f, ry + 24f * dp, paint);
            canvas.drawText(owned ? "✅ Kuşan (Açık)" : "🔓 Satın Al & Kuşan", left + colW * 0.5f, ry + 42f * dp, paint);
        }
    }

    /**
     * 🏛️ Kule Müzesi (Koleksiyon) Ekranı: Dokununca dans eden ve hareket eden hatıra eşyaları!
     */
    private void drawTowerMuseumScreen(Canvas canvas) {
        paint.setColor(0xFFFFFBEB);
        canvas.drawRoundRect(playAreaRect, 18f * dp, 18f * dp, paint);

        if (museumDanceTimer > 0) museumDanceTimer--;

        float cx = playAreaRect.centerX();
        float top = playAreaRect.top;

        paint.setColor(0xFF92400E);
        paint.setTextSize(16f * sp);
        paint.setFakeBoldText(true);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("🏛️ KULE MÜZESİ (Eşyalara Dokun, Dans Etsinler!)", cx, top + 30f * dp, paint);

        String[] exhibits = new String[]{
                "🕰️ Guguklu Saat (Kat 2)",
                "🤖 Kurmalı Robot (Kat 5)",
                "🫖 Dans Eden Çaydanlık (Kat 8)",
                "🧁 Uçan Pasta (Kat 10)",
                "🪴 Şarkıcı Çiçek (Kat 13)",
                "🔭 Altın Teleskop (Kat 18)"
        };
        int[] reqFloors = new int[]{2, 5, 8, 10, 13, 18};

        float startY = top + 52f * dp;
        float rowH = 68f * dp;
        float colW = playAreaRect.width() * 0.44f;

        for (int i = 0; i < 6; i++) {
            int r = i / 2;
            int c = i % 2;
            float left = playAreaRect.left + (c == 0 ? 12f * dp : playAreaRect.width() * 0.52f);
            float ry = startY + r * (rowH + 12f * dp);
            boolean unlocked = (unlockedMaxFloor >= reqFloors[i]);

            float danceBounce = (unlocked && (activeMuseumItem == i && museumDanceTimer > 0))
                    ? (float) Math.sin(animTick * 4f) * 8f * dp
                    : (unlocked ? (float) Math.sin(animTick + i) * 3f * dp : 0f);

            paint.setColor(unlocked ? 0xFFD97706 : 0xFF94A3B8);
            canvas.drawRoundRect(new RectF(left, ry + danceBounce, left + colW, ry + rowH + danceBounce), 12f * dp, 12f * dp, paint);
            paint.setColor(0xFFFFFFFF);
            paint.setTextSize(12.5f * sp);
            canvas.drawText(exhibits[i], left + colW * 0.5f, ry + 28f * dp + danceBounce, paint);
            canvas.drawText(unlocked ? "🎵 Dokun & Dans Ettir!" : "🔒 Kat " + reqFloors[i] + " Gerekli", left + colW * 0.5f, ry + 50f * dp + danceBounce, paint);
        }
    }

    private void drawBottomOwlAndExplorerBar(Canvas canvas, int w, int h, float barH) {
        float top = h - barH;
        paint.setColor(0xEE0F172A);
        canvas.drawRect(0, top, w, h, paint);

        // Sol: Yardımcı Bilge Baykuş Puhu + İpucu Butonu
        btnOwlHintRect.set(8f * dp, top + 10f * dp, 96f * dp, h - 10f * dp);
        paint.setColor(0xFFF59E0B);
        canvas.drawRoundRect(btnOwlHintRect, 12f * dp, 12f * dp, paint);
        paint.setColor(0xFF0F172A);
        paint.setTextSize(13f * sp);
        paint.setFakeBoldText(true);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("🦉 Puhu", btnOwlHintRect.centerX(), top + 34f * dp, paint);
        paint.setTextSize(11.5f * sp);
        canvas.drawText("💡 İpucu Al", btnOwlHintRect.centerX(), top + 56f * dp, paint);

        // Sağ: Baykuş Puhu Konuşma Balonu
        RectF bubble = new RectF(104f * dp, top + 10f * dp, w - 8f * dp, h - 10f * dp);
        paint.setColor(0xFF1E293B);
        canvas.drawRoundRect(bubble, 12f * dp, 12f * dp, paint);

        paint.setColor(0xFFE2E8F0);
        paint.setTextSize(12f * sp);
        paint.setTextAlign(Paint.Align.LEFT);
        // Uzun cümleyi 2 satıra böl
        String msg = owlSpeechText;
        if (msg.length() > 48) {
            int split = msg.lastIndexOf(' ', 48);
            if (split <= 0) split = 48;
            canvas.drawText(msg.substring(0, split), bubble.left + 10f * dp, bubble.top + 26f * dp, paint);
            canvas.drawText(msg.substring(split).trim(), bubble.left + 10f * dp, bubble.top + 48f * dp, paint);
        } else {
            canvas.drawText(msg, bubble.left + 10f * dp, bubble.centerY() + 5f * dp, paint);
        }
    }

    private void drawCountdownOverlay(Canvas canvas, int w, int h, int step) {
        paint.setColor(0xAA0F172A);
        canvas.drawRect(0, 0, w, h, paint);

        float cx = w * 0.5f;
        float cy = h * 0.48f;
        RectF card = new RectF(w * 0.10f, cy - 100f * dp, w * 0.90f, cy + 95f * dp);
        paint.setColor(0xFF1E293B);
        canvas.drawRoundRect(card, 20f * dp, 20f * dp, paint);

        paint.setColor(0xFF38BDF8);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(16f * sp);
        paint.setFakeBoldText(true);
        canvas.drawText("🏰 BULMACA KULESİ • KAT " + currentFloor, cx, cy - 55f * dp, paint);

        if (step > 0) {
            paint.setColor(0xFFFFD600);
            paint.setTextSize(60f * sp);
            canvas.drawText(String.valueOf(step), cx, cy + 20f * dp, paint);
            paint.setColor(0xFFE2E8F0);
            paint.setTextSize(13.5f * sp);
            canvas.drawText(getZoneTitle(getZoneIndexForFloor(currentFloor)), cx, cy + 65f * dp, paint);
        } else {
            paint.setColor(0xFF00E676);
            paint.setTextSize(42f * sp);
            canvas.drawText("BAŞLA! 🦉", cx, cy + 18f * dp, paint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float tx = event.getX();
        float ty = event.getY();
        int action = event.getActionMasked();

        if (inCountdown) return true;

        if (action == MotionEvent.ACTION_DOWN) {
            if (btnExitRect.contains(tx, ty)) {
                if (listener != null) listener.onExitRequested();
                return true;
            }
            if (btnPrevFloorRect.contains(tx, ty)) {
                if (currentFloor > 1) {
                    subScreenMode = 0;
                    setupFloorPuzzle(currentFloor - 1, true);
                }
                return true;
            }
            if (btnNextFloorRect.contains(tx, ty)) {
                int nextF = (currentFloor < 20) ? (currentFloor + 1) : 1;
                subScreenMode = 0;
                setupFloorPuzzle(nextF, true);
                return true;
            }
            if (btnPuzzleTabRect.contains(tx, ty)) {
                subScreenMode = 0;
                invalidate();
                return true;
            }
            if (btnWardrobeTabRect.contains(tx, ty)) {
                subScreenMode = 1;
                owlSpeechText = "🦉 Puhu: Topladığın Altın Vidalarla (🔩) kaşife komik şapka, pelerin ve çanta alabilirsin!";
                invalidate();
                return true;
            }
            if (btnMuseumTabRect.contains(tx, ty)) {
                subScreenMode = 2;
                owlSpeechText = "🦉 Puhu: Kule Müzesindeki hatıra eşyalarına dokunursan senin için dans ederler!";
                invalidate();
                return true;
            }
            if (btnOwlHintRect.contains(tx, ty)) {
                giveOwlHint();
                return true;
            }

            if (subScreenMode == 1) {
                handleWardrobeTouch(tx, ty);
                return true;
            }
            if (subScreenMode == 2) {
                handleMuseumTouch(tx, ty);
                return true;
            }

            // Katta gizlenmiş 3 Altın Vida / Yıldız Tozu kontrolü
            float left = playAreaRect.left;
            float top = playAreaRect.top;
            float pw = playAreaRect.width();
            float ph = playAreaRect.height();
            for (int i = 0; i < 3; i++) {
                if (!hiddenScrewFound[i]) {
                    float sx = left + hiddenScrewX[i] * pw;
                    float sy = top + hiddenScrewY[i] * ph;
                    if (Math.hypot(tx - sx, ty - sy) < 26f * dp) {
                        hiddenScrewFound[i] = true;
                        totalGoldenScrews++;
                        owlSpeechText = "🦉 Puhu: Süper gözlem! Gizli bir Altın Vida / Yıldız Tozu (🔩) buldun!";
                        saveProgressToPrefs();
                        if (listener != null) listener.onTowerActionSound(true);
                        invalidate();
                        return true;
                    }
                }
            }

            handlePuzzleTouchDown(tx, ty, left, top, pw, ph);
            return true;
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (subScreenMode == 0) {
                handlePuzzleTouchMove(tx, ty, playAreaRect.left, playAreaRect.top, playAreaRect.width(), playAreaRect.height());
            }
            return true;
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (subScreenMode == 0) {
                handlePuzzleTouchUp(tx, ty, playAreaRect.left, playAreaRect.top, playAreaRect.width(), playAreaRect.height());
            }
            return true;
        }
        return super.onTouchEvent(event);
    }

    private void handleWardrobeTouch(float tx, float ty) {
        float startY = playAreaRect.top + 148f * dp;
        float rowH = 52f * dp;
        float colW = playAreaRect.width() * 0.44f;
        int[] costs = new int[]{3, 4, 6, 4, 5, 5};

        for (int i = 0; i < 6; i++) {
            int r = i / 2;
            int c = i % 2;
            float left = playAreaRect.left + (c == 0 ? 12f * dp : playAreaRect.width() * 0.52f);
            float ry = startY + r * (rowH + 10f * dp);
            RectF box = new RectF(left, ry, left + colW, ry + rowH);
            if (box.contains(tx, ty)) {
                int bit = (1 << (i + 1));
                if ((ownedMask & bit) == 0) {
                    if (totalGoldenScrews < costs[i]) {
                        owlSpeechText = "🦉 Puhu: Bunun için " + costs[i] + " Altın Vida (🔩) gerekiyor. Katlardaki gizli vidalara dokun!";
                        if (listener != null) listener.onTowerActionSound(false);
                        invalidate();
                        return;
                    }
                    totalGoldenScrews -= costs[i];
                    ownedMask |= bit;
                }
                if (i == 0) equippedHat = 1;
                else if (i == 1) equippedHat = 2;
                else if (i == 2) equippedHat = 3;
                else if (i == 3) equippedCape = 1;
                else if (i == 4) equippedCape = 2;
                else equippedBag = 1;

                owlSpeechText = "🦉 Puhu: Harika yakıştı! Yeni kostümünle kulede parlıyorsun!";
                saveProgressToPrefs();
                if (listener != null) listener.onTowerActionSound(true);
                invalidate();
                return;
            }
        }
    }

    private void handleMuseumTouch(float tx, float ty) {
        float startY = playAreaRect.top + 52f * dp;
        float rowH = 68f * dp;
        float colW = playAreaRect.width() * 0.44f;
        for (int i = 0; i < 6; i++) {
            int r = i / 2;
            int c = i % 2;
            float left = playAreaRect.left + (c == 0 ? 12f * dp : playAreaRect.width() * 0.52f);
            float ry = startY + r * (rowH + 12f * dp);
            if (new RectF(left, ry, left + colW, ry + rowH).contains(tx, ty)) {
                activeMuseumItem = i;
                museumDanceTimer = 75;
                owlSpeechText = "🦉 Puhu: Haha! Müzedeki hatıra eşyamız neşeyle dans ediyor! 🎶";
                if (listener != null) listener.onTowerActionSound(true);
                invalidate();
                return;
            }
        }
    }

    private void handlePuzzleTouchDown(float tx, float ty, float left, float top, float pw, float ph) {
        if (floorClearFrames > 0) return;

        if (activePuzzleType == 0) {
            // Dişli çark sürükleme başlangıcı
            for (int i = 0; i < 3; i++) {
                if (!gearPlaced[i]) {
                    float gx = left + gearX[i] * pw;
                    float gy = top + gearY[i] * ph;
                    if (Math.hypot(tx - gx, ty - gy) < 38f * dp) {
                        draggingIdx = i;
                        return;
                    }
                }
            }
        } else if (activePuzzleType == 1) {
            // Kablo sürükleme başlangıcı
            for (int i = 0; i < 3; i++) {
                if (!wireConnected[i]) {
                    float lx = left + 0.20f * pw;
                    float ly = top + (0.30f + i * 0.22f) * ph;
                    if (Math.hypot(tx - lx, ty - ly) < 36f * dp) {
                        activeWireDrag = i;
                        wireDragCurX = tx;
                        wireDragCurY = ty;
                        return;
                    }
                }
            }
        } else if (activePuzzleType == 2) {
            // Terazi blokları
            for (int i = 0; i < 4; i++) {
                float bx = left + (0.17f + i * 0.22f) * pw;
                float by = top + 0.74f * ph;
                if (Math.hypot(tx - bx, ty - by) < 36f * dp) {
                    scaleBlockOnPan[i] = !scaleBlockOnPan[i];
                    int sum = 0;
                    for (int k = 0; k < 4; k++) if (scaleBlockOnPan[k]) sum += scaleBlockValues[k];
                    if (sum == scaleTargetWeight) {
                        onPuzzleSolved();
                    } else if (sum > scaleTargetWeight + 3) {
                        for (int k = 0; k < 4; k++) scaleBlockOnPan[k] = false;
                        triggerFunnyMistakeSmoke("Terazi Fazla Ağır Geldi!");
                    } else if (listener != null) {
                        listener.onTowerActionSound(true);
                    }
                    invalidate();
                    return;
                }
            }
        } else if (activePuzzleType == 3) {
            // Tarif sırası
            for (int i = 0; i < 4; i++) {
                float bx = left + (0.16f + i * 0.22f) * pw;
                float by = top + 0.74f * ph;
                if (Math.hypot(tx - bx, ty - by) < 38f * dp) {
                    if (i == recipeOrder[recipeStep]) {
                        recipeStep++;
                        if (listener != null) listener.onTowerActionSound(true);
                        if (recipeStep >= 4) {
                            onPuzzleSolved();
                        }
                    } else {
                        recipeStep = 0;
                        triggerFunnyMistakeSmoke("Kazan Köpürüp Taştı!");
                    }
                    invalidate();
                    return;
                }
            }
        } else if (activePuzzleType == 4) {
            // Boru döndürme
            for (int i = 0; i < 4; i++) {
                float bx = left + (0.18f + i * 0.21f) * pw;
                float by = top + 0.56f * ph;
                if (Math.hypot(tx - bx, ty - by) < 38f * dp) {
                    pipeAngles[i] = (pipeAngles[i] + 1) % 4;
                    if (listener != null) listener.onTowerActionSound(true);
                    boolean allOk = true;
                    for (int k = 0; k < 4; k++) if (pipeAngles[k] % 4 != 0) allOk = false;
                    if (allOk) onPuzzleSolved();
                    invalidate();
                    return;
                }
            }
        } else if (activePuzzleType == 5) {
            // Gizli şekerleme dolapları
            for (int i = 0; i < 6; i++) {
                int r = i / 3;
                int c = i % 3;
                float bx = left + (0.22f + c * 0.28f) * pw;
                float by = top + (0.44f + r * 0.26f) * ph;
                if (Math.hypot(tx - bx, ty - by) < 42f * dp) {
                    int foundIdx = -1;
                    for (int k = 0; k < 4; k++) if (candyCupboardPos[k] == i) foundIdx = k;
                    if (foundIdx >= 0) {
                        candyFound[foundIdx] = true;
                        if (listener != null) listener.onTowerActionSound(true);
                        boolean allFound = true;
                        for (int k = 0; k < 4; k++) if (!candyFound[k]) allFound = false;
                        if (allFound) onPuzzleSolved();
                    } else {
                        triggerFunnyMistakeSmoke("Dolaptan Kurabiye Canavarı Çıktı!");
                    }
                    invalidate();
                    return;
                }
            }
        } else if (activePuzzleType == 6) {
            // Ayna yansıtma
            for (int i = 0; i < 3; i++) {
                float bx = left + (0.22f + i * 0.28f) * pw;
                float by = top + 0.56f * ph;
                if (Math.hypot(tx - bx, ty - by) < 40f * dp) {
                    mirrorStates[i] = (mirrorStates[i] + 1) % 4;
                    if (listener != null) listener.onTowerActionSound(true);
                    if (mirrorStates[0] == 0 && mirrorStates[1] == 0 && mirrorStates[2] == 0) {
                        onPuzzleSolved();
                    }
                    invalidate();
                    return;
                }
            }
        } else if (activePuzzleType == 7) {
            // Su kanalı vanaları
            for (int i = 0; i < 3; i++) {
                float bx = left + (0.22f + i * 0.28f) * pw;
                float by = top + 0.58f * ph;
                if (Math.hypot(tx - bx, ty - by) < 45f * dp) {
                    valveLevels[i] = (valveLevels[i] + 1) % 4;
                    if (listener != null) listener.onTowerActionSound(true);
                    if (valveLevels[0] == valveTargets[0] && valveLevels[1] == valveTargets[1] && valveLevels[2] == valveTargets[2]) {
                        onPuzzleSolved();
                    }
                    invalidate();
                    return;
                }
            }
        } else if (activePuzzleType == 8) {
            // Hayvan ayak izi şifresi
            for (int i = 0; i < 3; i++) {
                float bx = left + (0.22f + i * 0.28f) * pw;
                float by = top + 0.62f * ph;
                if (Math.hypot(tx - bx, ty - by) < 42f * dp) {
                    if (i == trackPattern[trackStep]) {
                        trackStep++;
                        if (listener != null) listener.onTowerActionSound(true);
                        if (trackStep >= 4) onPuzzleSolved();
                    } else {
                        trackStep = 0;
                        triggerFunnyMistakeSmoke("Yapraklar Havaya Uçuştu!");
                    }
                    invalidate();
                    return;
                }
            }
        } else if (activePuzzleType == 9) {
            // Takımyıldızı birleştirme
            checkConstellationTouch(tx, ty, left, top, pw, ph);
        } else if (activePuzzleType == 10) {
            // Gezegen yörüngesi hizalama
            for (int i = 0; i < 3; i++) {
                float px = left + (0.22f + i * 0.28f) * pw;
                if (Math.abs(tx - px) < 42f * dp) {
                    planetSlots[i] = (planetSlots[i] + 1) % 4;
                    if (listener != null) listener.onTowerActionSound(true);
                    if (planetSlots[0] == 0 && planetSlots[1] == 0 && planetSlots[2] == 0) {
                        onPuzzleSolved();
                    }
                    invalidate();
                    return;
                }
            }
        } else {
            // Gölge oyunu kapısı
            for (int i = 0; i < 3; i++) {
                float bx = left + (0.22f + i * 0.28f) * pw;
                float by = top + 0.58f * ph;
                if (Math.hypot(tx - bx, ty - by) < 42f * dp) {
                    shadowPieces[i] = (shadowPieces[i] + 1) % 4;
                    if (listener != null) listener.onTowerActionSound(true);
                    if (shadowPieces[0] == 0 && shadowPieces[1] == 0 && shadowPieces[2] == 0) {
                        onPuzzleSolved();
                    }
                    invalidate();
                    return;
                }
            }
        }
    }

    private void checkConstellationTouch(float tx, float ty, float left, float top, float pw, float ph) {
        if (starConnectCount >= 5) return;
        float sx = left + constStarX[starConnectCount] * pw;
        float sy = top + constStarY[starConnectCount] * ph;
        if (Math.hypot(tx - sx, ty - sy) < 38f * dp) {
            starConnectCount++;
            if (listener != null) listener.onTowerActionSound(true);
            if (starConnectCount >= 5) {
                onPuzzleSolved();
            }
            invalidate();
        }
    }

    private void handlePuzzleTouchMove(float tx, float ty, float left, float top, float pw, float ph) {
        if (activePuzzleType == 0 && draggingIdx >= 0) {
            gearX[draggingIdx] = Math.max(0.10f, Math.min(0.90f, (tx - left) / pw));
            gearY[draggingIdx] = Math.max(0.18f, Math.min(0.86f, (ty - top) / ph));
            invalidate();
        } else if (activePuzzleType == 1 && activeWireDrag >= 0) {
            wireDragCurX = tx;
            wireDragCurY = ty;
            invalidate();
        } else if (activePuzzleType == 9) {
            checkConstellationTouch(tx, ty, left, top, pw, ph);
        }
    }

    private void handlePuzzleTouchUp(float tx, float ty, float left, float top, float pw, float ph) {
        if (activePuzzleType == 0 && draggingIdx >= 0) {
            int i = draggingIdx;
            draggingIdx = -1;
            float sx = left + slotX[i] * pw;
            float sy = top + slotY[i] * ph;
            float gx = left + gearX[i] * pw;
            float gy = top + gearY[i] * ph;
            if (Math.hypot(gx - sx, gy - sy) < 48f * dp) {
                gearX[i] = slotX[i];
                gearY[i] = slotY[i];
                gearPlaced[i] = true;
                if (listener != null) listener.onTowerActionSound(true);
                if (gearPlaced[0] && gearPlaced[1] && gearPlaced[2]) {
                    onPuzzleSolved();
                }
            } else {
                gearX[i] = 0.22f + i * 0.28f;
                gearY[i] = 0.72f;
                triggerFunnyMistakeSmoke("Çark Yaylanıp Yerine Zıpladı!");
            }
            invalidate();
        } else if (activePuzzleType == 1 && activeWireDrag >= 0) {
            int i = activeWireDrag;
            activeWireDrag = -1;
            float rx = left + 0.80f * pw;
            float ry = top + (0.30f + i * 0.22f) * ph;
            if (Math.hypot(tx - rx, ty - ry) < 48f * dp) {
                wireConnected[i] = true;
                if (listener != null) listener.onTowerActionSound(true);
                if (wireConnected[0] && wireConnected[1] && wireConnected[2]) {
                    onPuzzleSolved();
                }
            } else {
                triggerFunnyMistakeSmoke("Kablo Kıvılcım Çıkarıp Geri Sarıldı!");
            }
            invalidate();
        }
    }
}
