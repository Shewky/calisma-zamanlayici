# 🌸 Çalışma Zamanlayıcı (İpek'e Özel 4. Sınıf Ödev & Test Zamanlayıcısı)

Bu proje, **İpek** için özel olarak tasarlanmış, **Android 6.0 (API 23) ve üzeri** tüm cihazlarla uyumlu, arka planda ve ekran kapalıyken bile Türkçe sesli anonslarla çalışan, **GitHub üzerinden kendi kendini güncelleyebilen (OTA Update)** açık kaynaklı Android uygulamasıdır.

---

## ✨ Temel Özellikler

1. **İpek'e Özel Neşeli Arayüz:**
   - Uygulama açılışında İpek'e ismiyle hitap eden renkli, neşeli karşılama ekranı ve günlük çalışma özeti.
2. **Ödeve Başla & Test Çözümleri (4. Sınıf Dersleri):**
   - **Türkçe, Matematik, Sosyal Bilgiler, İngilizce, Din Kültürü, Trafik Güvenliği, İnsan Hakları** ders seçenekleri.
3. **Akıllı 40 Dakika Çalışma / 10 Dakika Mola Döngüsü & Hafıza Sistemi:**
   - Ders seçildiğinde **40 dakikalık** sayaç aktif olur ve **"Çalışmaya başla"** sesli anonsu yapılır.
   - 40 dakika dolduğunda **"Mola zamanı"** sesli anonsu yapılır ve **10 dakikalık** mola sayacı başlar.
   - 10 dakikalık molanın bitmesine 1 dakika kala **"Mola bitmek üzere"** sesli uyarısı yapılır.
   - 10 dakikalık mola bittiğinde **"Çalışmaya devam et"** anonsu yapılır ve yeni 40 dakikalık çalışma döngüsü başlar.
   - **"Bu dersin ödevi bitti"** (veya **"Bu dersin testi bitti"**) butonuna tıklandığında:
     - O derste çalışılan süre bugünün tarihine ve saatine **Çalışma Takvimi** veritabanına kaydedilir.
     - Moladan sonraki (veya mevcut 40 dakikalık döngü içindeki) geçen çalışma süresi **hafızada tutulur**.
     - Tekrar **Ders Seçimi** ekranına dönülür.
     - Yeni bir derste ödeve veya teste başlandığında, önceki çalışma süresi **40 dakikadan otomatik olarak düşülür** (`Kalan Süre = 40 dk - Önceki Çalışma Süresi`). Süre dolduğunda yine aynı şekilde mola döngüsü ve anonslar devreye girer.
4. **Arka Planda ve Ekran Kapalıyken Kesintisiz Çalışma:**
   - `TimerForegroundService` ve `PARTIAL_WAKE_LOCK` sayesinde ekran kapansa bile sayaç durmaz.
   - Sesli anonslar `STREAM_ALARM` / `USAGE_ALARM` ses kanalını kullanır; ekran kilitliyken bile uyarı sesi ve Türkçe konuşma net şekilde duyulur.
5. **Çalışma Takvimi ve Haftalık Başarı Ödülleri:**
   - **Takvim** ekranında tıklanan günde yapılan tüm ödevlerin ve çözülen testlerin ders bazında süreleri ve günlük toplamları listelenir.
   - **Başarı Ödüllerim** ekranında 4. sınıf seviyesine uygun haftalık rozetler (*İlk Adım Yıldızı İpek*, *Çalışkan Arı İpek*, *4. Sınıf Ödev Kahramanı*, *Süper Test Ustası*, *Haftanın Şampiyon Öğrencisi*) ve başarılı günlerde **"Aferin çalışmaya devam et"**, **"Aferin nice başarılı günlere"** sesli teşvik anonsları yer alır.
6. **Üzerine Güncelleme & GitHub Otomatik Güncelleme (OTA):**
   - Proje içerisinde sabit imzalama anahtarı (`app/keystore/ipek-release-key.jks`) yer alır. Bu sayede yapılan her güncelleme aynı kriptografik imzaya sahip olur ve mevcut uygulamanın üzerine **takvim/ödül verileri silinmeden** doğrudan yüklenir.
   - `.github/workflows/android-release.yml` dosyası sayesinde GitHub'da yaptığınız her değişiklikte GitHub otomatik olarak yeni `CalismaZamanlayici.apk` dosyasını derler, sürüm numarasını (`versionCode`) 1 artırır ve **Releases** altına yayınlar.
   - Telefondaki uygulama açıldığında (veya **"🔄 Güncelleme Bul"** butonuna basıldığında) GitHub Releases üzerinden yeni sürümü bulur, indirir ve tek dokunuşla günceller!

---

## 🚀 GitHub'da Açma ve İlk APK'yı Otomatik Alma Adımları (Bilgisayara Program Kurmadan)

1. [GitHub](https://github.com) hesabınıza giriş yapın ve sağ üstten **New repository** (Yeni Depo) oluşturun:
   - **Repository name:** `calisma-zamanlayici` (veya istediğiniz bir isim)
   - **Public** (Açık) olarak seçin (cihazın GitHub API üzerinden ücretsiz ve şifresiz güncelleme kontrolü yapabilmesi için Public olması önerilir).
   - **Create repository** butonuna tıklayın.
2. Bu ZIP dosyasının içindeki tüm dosya ve klasörleri (`.github`, `app`, `build.gradle`, `settings.gradle`, `gradle.properties`, `README.md`) yeni açtığınız GitHub deposuna yükleyin (**uploading an existing file** veya `git push` ile).
3. Dosyaları yükleyip **Commit changes** dediğiniz anda GitHub üst menüsündeki **Actions** sekmesine tıklayın:
   - **"Android APK Derle ve Otomatik Güncelleme Yayınla"** iş akışının otomatik başladığını göreceksiniz.
   - Yaklaşık **1-2 dakika** içinde derleme tamamlanır ve deponuzun sağ tarafındaki **Releases** (Sürümler) bölümünde **`CalismaZamanlayici.apk`** dosyası otomatik olarak yayınlanır!
4. İpek'in telefonundan veya tabletinden GitHub deponuzun **Releases** sayfasına girip `CalismaZamanlayici.apk` dosyasını indirerek ilk kurulumu yapın.

---

## 🔄 Sonraki Güncellemeler Nasıl Yapılır?

- GitHub üzerinden (veya Android Studio ile) kodda herhangi bir değişiklik yapıp kaydettiğinizde (**Commit / Push**):
  1. GitHub Actions otomatik olarak sürüm numarasını (`v1.0.2`, `v1.0.3`...) artırır ve yeni `CalismaZamanlayici.apk` dosyasını **Releases** altına koyar.
  2. İpek'in telefonundaki **Çalışma zamanlayıcı** uygulaması açıldığında (veya ana ekrandaki **"🔄 Güncelleme Bul"** butonuna basıldığında) yeni sürümü otomatik algılar, indirir ve eski verileri silmeden doğrudan üzerine kurar!
