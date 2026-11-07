# 🚀 Çalışma Zamanlayıcı (Öğrenci) & Veli Takip Uygulaması (v3.0 - Çift APK)

Bu proje tek bir GitHub deposu içinden **iki ayrı Android APK** üretir ve yayınlar:
1. **`CalismaZamanlayici.apk` (Öğrenci Uygulaması):** 1. Sınıftan 8. Sınıfa kadar tüm öğrenciler için ödev, test çözümü, kitap okuma zamanlayıcısı, çalışma takvimi, başarı rozetleri, Anne/Baba eşleştirme karekodu ve veliye sesli çağrı gönderme uygulaması.
2. **`CalismaZamanlayiciVeli.apk` (Veli Uygulaması):** Anne veya Baba olarak giriş yapılan, aynı Wi-Fi ağındaki birden fazla çocuğun karekodunu okutarak takip eden, değişen IP adreslerini otomatik tanıyan, uzaktan sesli tebrik/övgü gönderen ve tek tuşla çift yönlü sesli görüşme başlatan veli uygulaması.

---

## ✨ Yeni Eklenen Özellikler (v3.0)

- **1. Sınıf – 8. Sınıf Seçimi ve Özelleştirilebilir Dersler:**
  - İlk giriş ekranında ve sağ üstteki **⚙️ Ayarlar** menüsünde `1. Sınıf` ile `8. Sınıf` arasında seçim yapılabilir.
  - **4. Sınıf** seçildiğinde varsayılan 8 ders (*Türkçe, Matematik, Fen Bilimleri, Sosyal Bilgiler, İngilizce, Din Kültürü, Trafik Güvenliği, İnsan Hakları*) otomatik olarak gelir.
  - Öğrenci veya veli, **➕ Yeni Ders Yaz / Dersleri Düzenle** butonuyla istediği sınıfa kendi derslerini klavyeden yazarak ekleyebilir veya çıkarabilir.
- **Anne ve Baba İçin Ayrı Karekod (QR) ile Eşleştirme:**
  - Öğrenci uygulamasındaki **📱 Anne / Baba Eşleştirme Karekodu Göster** ekranında Anne ve Baba için ayrı karekod üretilir.
  - Veli uygulamasında **📷 Karekod Okut (Çocuk Ekle)** ile okutulduğunda öğrenci veli ekranına eklenir. Birden fazla çocuk eklenebilir.
- **Otomatik Değişen IP Tespiti ve Kullanıcı İsmi Eşleştirme:**
  - Modem/Wi-Fi üzerinde cihazların IP adresi değişse bile Veli uygulaması yerel ağı (`UDP 45677` + `/24 Alt Ağ Taraması`) otomatik tarayarak çocuğun yeni IP adresini bulur ve Çalışma Zamanlayıcı uygulamasına giriş yapılan güncel kullanıcı ismini ekrana getirir.
- **Canlı Çalışma Takibi, Takvim, Ödüller ve Uzaktan Sesli Övgü:**
  - Veli uygulamasında çocuğun adına tıklandığında anlık çalıştığı ders/kitap, kalan süre, çalışma takvimi geçmişi ve kazandığı rozetler canlı görüntülenir.
  - Veli kendi rolüne göre (**Anne** veya **Baba**) tek tuşla uzaktan **"Annenden övgü aldın tebrikler"**, **"Babandan övgü aldın tebrikler"** ve **"Aferin sana"** sesli anonsu gönderebilir.
- **Çift Yönlü Sesli Görüşme (2 Farklı Senaryo):**
  1. **Çocuktan Veliye Çağrı (Onaylı):** Çocuk **👩 Anne'ye Çağrı** veya **👨 Baba'ya Çağrı** butonuna bastığında Veli uygulaması titreşim verir ve **"[Kullanıcı İsmi] çağrı gönderiyor. Yanıtlamak için uygulamadan onay ver"** diye sesli anons yapar. Veli onay verdiğinde ikili sesli görüşme başlar.
  2. **Veliden Çocuğa Doğrudan Arama (Onaysız):** Veli uygulamasında çocuğun isminin yanındaki **📞 Ara** butonuna basıldığı anda, çocuk tarafında onaya gerek kalmadan doğrudan ikili sesli görüşme başlar.
