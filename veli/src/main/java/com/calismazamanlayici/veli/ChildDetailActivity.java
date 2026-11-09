package com.calismazamanlayici.veli;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.CalendarView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Veli uygulamasında çocuğun adına tıklandığında açılan detaylı takip ekranı:
 * - Öğrenci uygulamasındaki gibi gün gün tıklanabilir etkileşimli Takvim (CalendarView) içerir.
 * - Aynı ağda alınan tüm takvim ve ödül verilerini cihaz hafızasına kaydeder; ağdan çıkılsa bile geçmiş görünür.
 * - Oyunlar çalışma olarak sayılmaz; takvimde yalnızca Ödev, Test ve Kitap Okuma çalışma olarak gösterilir.
 */
public class ChildDetailActivity extends Activity {

    private String childId = "";
    private String childName = "Öğrenci";
    private String childIp = "";

    private ScrollView scrollRootChildDetail;
    private LinearLayout layoutDetailHeaderCard;
    private TextView tvDetailChildName;
    private TextView tvDetailLivePhase;
    private TextView tvDetailLiveTimers;
    private TextView tvPraiseSectionTitle;
    private TextView tvCustomPraiseLabel;
    private EditText etCustomPraiseInput;
    private Button btnSendRolePraise;
    private Button btnSendAferinPraise;
    private Button btnSendCustomPraise;

    private CalendarView calendarViewVeliChild;
    private TextView tvVeliSelectedDateTitle;
    private TextView tvVeliSelectedDateSummary;
    private LinearLayout layoutDetailCalendarRecords;

    private TextView tvDetailWeeklySummary;
    private LinearLayout layoutDetailBadges;

    private String selectedCalendarDate = "";
    private JSONObject lastLoadedReport = null;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private boolean polling = false;

    private final Runnable detailPoller = new Runnable() {
        @Override
        public void run() {
            if (!polling) return;
            fetchFullReportFromChild();
            uiHandler.postDelayed(this, 3000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_child_detail);

        if (getIntent() != null) {
            String cid = getIntent().getStringExtra("EXTRA_CHILD_ID");
            if (cid != null) childId = cid;
            String cname = getIntent().getStringExtra("EXTRA_CHILD_NAME");
            if (cname != null) childName = cname;
            String cip = getIntent().getStringExtra("EXTRA_CHILD_IP");
            if (cip != null) childIp = cip;
        }

        selectedCalendarDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());

        scrollRootChildDetail = findViewById(R.id.scrollRootChildDetail);
        layoutDetailHeaderCard = findViewById(R.id.layoutDetailHeaderCard);
        tvDetailChildName = findViewById(R.id.tvDetailChildName);
        tvDetailLivePhase = findViewById(R.id.tvDetailLivePhase);
        tvDetailLiveTimers = findViewById(R.id.tvDetailLiveTimers);
        tvPraiseSectionTitle = findViewById(R.id.tvPraiseSectionTitle);
        tvCustomPraiseLabel = findViewById(R.id.tvCustomPraiseLabel);
        etCustomPraiseInput = findViewById(R.id.etCustomPraiseInput);
        btnSendRolePraise = findViewById(R.id.btnSendRolePraise);
        btnSendAferinPraise = findViewById(R.id.btnSendAferinPraise);
        btnSendCustomPraise = findViewById(R.id.btnSendCustomPraise);

        calendarViewVeliChild = findViewById(R.id.calendarViewVeliChild);
        tvVeliSelectedDateTitle = findViewById(R.id.tvVeliSelectedDateTitle);
        tvVeliSelectedDateSummary = findViewById(R.id.tvVeliSelectedDateSummary);
        layoutDetailCalendarRecords = findViewById(R.id.layoutDetailCalendarRecords);

        tvDetailWeeklySummary = findViewById(R.id.tvDetailWeeklySummary);
        layoutDetailBadges = findViewById(R.id.layoutDetailBadges);

        Button btnDirectCallFromDetail = findViewById(R.id.btnDirectCallFromDetail);
        Button btnBack = findViewById(R.id.btnBackFromDetail);

        VeliThemeHelper.Palette p = VeliThemeHelper.getPalette(this);
        VeliThemeHelper.applyWindowAndSurface(this, scrollRootChildDetail);
        if (layoutDetailHeaderCard != null) {
            layoutDetailHeaderCard.setBackground(VeliThemeHelper.createHeaderDrawable(this));
        }
        btnSendRolePraise.setBackground(VeliThemeHelper.createButtonDrawable(this, p.secondary));
        btnSendAferinPraise.setBackground(VeliThemeHelper.createButtonDrawable(this, p.accent));
        btnSendCustomPraise.setBackground(VeliThemeHelper.createButtonDrawable(this, p.primary));
        btnDirectCallFromDetail.setBackground(VeliThemeHelper.createButtonDrawable(this, p.primaryDark));
        btnBack.setBackground(VeliThemeHelper.createButtonDrawable(this, p.primary));

        tvDetailChildName.setText("👤 " + childName + " • Canlı Takip ve Takvim");

        refreshRoleBasedPraiseLabels();

        // Takvimden gün seçildiğinde o günün kayıtlarını göster
        calendarViewVeliChild.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            selectedCalendarDate = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth);
            renderSelectedCalendarDate();
        });

        // Ağdan çıkılmış (offline) olunsa bile hafızaya kaydedilmiş son raporu anında ekrana getir!
        JSONObject cached = VeliStorage.getCachedChildReport(this, childId);
        if (cached != null) {
            lastLoadedReport = cached;
            renderReportOnUi(cached, false);
        } else {
            renderSelectedCalendarDate();
        }

        btnSendRolePraise.setOnClickListener(v -> {
            String role = VeliStorage.getParentRole(ChildDetailActivity.this);
            String msg = "BABA".equalsIgnoreCase(role)
                    ? "Babandan övgü aldın tebrikler"
                    : "Annenden övgü aldın tebrikler";
            sendPraiseToChild(role, msg);
        });

        btnSendAferinPraise.setOnClickListener(v -> {
            String role = VeliStorage.getParentRole(ChildDetailActivity.this);
            String msg = "BABA".equalsIgnoreCase(role)
                    ? "Babandan övgü aldın tebrikler! Aferin sana!"
                    : "Annenden övgü aldın tebrikler! Aferin sana!";
            sendPraiseToChild(role, msg);
        });

        btnSendCustomPraise.setOnClickListener(v -> {
            String typed = etCustomPraiseInput.getText().toString().trim();
            if (typed.isEmpty()) {
                Toast.makeText(ChildDetailActivity.this, "Lütfen göndermek istediğiniz övgü mesajını yazın ✍️", Toast.LENGTH_SHORT).show();
                return;
            }
            String role = VeliStorage.getParentRole(ChildDetailActivity.this);
            String prefix = "BABA".equalsIgnoreCase(role) ? "Babandan mesaj: " : "Annenden mesaj: ";
            String fullAnnouncement = prefix + typed;
            sendPraiseToChild(role, fullAnnouncement);
            etCustomPraiseInput.setText("");
        });

        btnDirectCallFromDetail.setOnClickListener(v -> {
            checkChildOnlineBeforeDetailCall();
        });

        btnBack.setOnClickListener(v -> finish());
    }

    private void refreshRoleBasedPraiseLabels() {
        String parentRole = VeliStorage.getParentRole(this);
        boolean isBaba = "BABA".equalsIgnoreCase(parentRole);
        String rolePraiseText = isBaba
                ? "Babandan övgü aldın tebrikler"
                : "Annenden övgü aldın tebrikler";
        String roleTitle = isBaba ? "👨 Baba Olarak Sesli Övgü Gönder:" : "👩 Anne Olarak Sesli Övgü Gönder:";
        String customPrefixInfo = isBaba
                ? "✍️ Özelleştirilmiş Övgü Gönder ('Babandan mesaj:' diyerek başlar):"
                : "✍️ Özelleştirilmiş Övgü Gönder ('Annenden mesaj:' diyerek başlar):";

        tvPraiseSectionTitle.setText(roleTitle);
        btnSendRolePraise.setText("🎉 '" + rolePraiseText + "' Gönder");
        btnSendAferinPraise.setText("🌟 '" + rolePraiseText + "! Aferin sana!' Gönder");
        tvCustomPraiseLabel.setText(customPrefixInfo);
    }

    @Override
    protected void onResume() {
        super.onResume();
        VeliMainActivity.setForegroundVisible(true);
        refreshRoleBasedPraiseLabels();
        polling = true;
        uiHandler.removeCallbacks(detailPoller);
        uiHandler.post(detailPoller);
    }

    @Override
    protected void onPause() {
        VeliMainActivity.setForegroundVisible(false);
        polling = false;
        uiHandler.removeCallbacks(detailPoller);
        super.onPause();
    }

    private void checkChildOnlineBeforeDetailCall() {
        for (VeliStorage.PairedChild c : VeliStorage.getPairedChildren(this)) {
            if (c.childId.equalsIgnoreCase(childId) && c.ipAddress != null && !c.ipAddress.isEmpty()) {
                childIp = c.ipAddress;
                break;
            }
        }
        if (childIp == null || childIp.isEmpty()) {
            Toast.makeText(this, "⚠️ Şu anda offline görünüyor", Toast.LENGTH_SHORT).show();
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean online = false;
                try {
                    URL url = new URL("http://" + childIp + ":" + ParentBackgroundService.STUDENT_HTTP_PORT + "/ping");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("Connection", "close");
                    conn.setConnectTimeout(900);
                    conn.setReadTimeout(900);
                    if (conn.getResponseCode() == 200) {
                        online = true;
                    }
                    conn.disconnect();
                } catch (Exception ignored) {
                }
                final boolean finalOnline = online;
                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) return;
                        if (!finalOnline) {
                            Toast.makeText(ChildDetailActivity.this, "⚠️ Şu anda offline görünüyor", Toast.LENGTH_SHORT).show();
                            tvDetailLivePhase.setText("⚪ Şu anda offline görünüyor (Kayıtlı geçmiş gösteriliyor)");
                        } else {
                            Intent callIntent = new Intent(ChildDetailActivity.this, VeliCallActivity.class);
                            callIntent.putExtra("EXTRA_CHILD_ID", childId);
                            callIntent.putExtra("EXTRA_CHILD_NAME", childName);
                            callIntent.putExtra("EXTRA_CHILD_IP", childIp);
                            callIntent.putExtra("EXTRA_INCOMING_APPROVAL", false);
                            startActivity(callIntent);
                        }
                    }
                });
            }
        }).start();
    }

    private void sendPraiseToChild(final String parentRole, final String praiseSpeech) {
        for (VeliStorage.PairedChild c : VeliStorage.getPairedChildren(this)) {
            if (c.childId.equalsIgnoreCase(childId) && c.ipAddress != null && !c.ipAddress.isEmpty()) {
                childIp = c.ipAddress;
                break;
            }
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean sentOk = false;

                try {
                    String udpMsg = "CZ_PRAISE|" + parentRole + "|" + praiseSpeech;
                    byte[] udpBytes = udpMsg.getBytes("UTF-8");
                    DatagramSocket ds = new DatagramSocket();
                    ds.setBroadcast(true);
                    if (childIp != null && !childIp.isEmpty()) {
                        ds.send(new DatagramPacket(udpBytes, udpBytes.length,
                                InetAddress.getByName(childIp), ParentBackgroundService.DISCOVERY_UDP_PORT));
                        sentOk = true;
                    }
                    ds.send(new DatagramPacket(udpBytes, udpBytes.length,
                            InetAddress.getByName("255.255.255.255"), ParentBackgroundService.DISCOVERY_UDP_PORT));
                    ds.close();
                } catch (Exception ignored) {
                }

                if (childIp != null && !childIp.isEmpty()) {
                    try {
                        String q = "role=" + URLEncoder.encode(parentRole, "UTF-8")
                                + "&text=" + URLEncoder.encode(praiseSpeech, "UTF-8");
                        URL url = new URL("http://" + childIp + ":" + ParentBackgroundService.STUDENT_HTTP_PORT + "/praise?" + q);
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("GET");
                        conn.setRequestProperty("Connection", "close");
                        conn.setConnectTimeout(2000);
                        conn.setReadTimeout(2000);
                        int code = conn.getResponseCode();
                        conn.disconnect();
                        if (code == 200) {
                            sentOk = true;
                        }
                    } catch (Exception ignored) {
                    }
                }

                final boolean finalSuccess = sentOk;
                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (finalSuccess) {
                            Toast.makeText(ChildDetailActivity.this,
                                    "🔊 Sesli anons gönderildi: '" + praiseSpeech + "'",
                                    Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(ChildDetailActivity.this,
                                    "⚠️ Şu anda offline görünüyor",
                                    Toast.LENGTH_SHORT).show();
                            ParentBackgroundService.scanLanAndSyncChildren(ChildDetailActivity.this, false, null);
                        }
                    }
                });
            }
        }).start();
    }

    private void fetchFullReportFromChild() {
        for (VeliStorage.PairedChild c : VeliStorage.getPairedChildren(this)) {
            if (c.childId.equalsIgnoreCase(childId) && c.ipAddress != null && !c.ipAddress.isEmpty()) {
                childIp = c.ipAddress;
                break;
            }
        }
        if (childIp == null || childIp.isEmpty()) return;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL("http://" + childIp + ":" + ParentBackgroundService.STUDENT_HTTP_PORT + "/full_report");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("Connection", "close");
                    conn.setConnectTimeout(2000);
                    conn.setReadTimeout(2000);
                    if (conn.getResponseCode() != 200) {
                        conn.disconnect();
                        return;
                    }

                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();
                    conn.disconnect();

                    final String rawJson = sb.toString();
                    final JSONObject report = new JSONObject(rawJson);

                    // Aynı ağda veriyi alır almaz kalıcı hafızaya kaydet (Ağdan çıkınca da takvim görünsün!)
                    VeliStorage.saveCachedChildReport(ChildDetailActivity.this, childId, rawJson);

                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            lastLoadedReport = report;
                            renderReportOnUi(report, true);
                        }
                    });
                } catch (Exception e) {
                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (lastLoadedReport != null) {
                                tvDetailLivePhase.setText("⚪ Şu anda offline görünüyor (Kayıtlı geçmiş gösteriliyor)");
                            }
                        }
                    });
                    ParentBackgroundService.scanLanAndSyncChildren(ChildDetailActivity.this, false, null);
                }
            }
        }).start();
    }

    private void renderReportOnUi(JSONObject report, boolean isLiveOnline) {
        String latestName = report.optString("userName", childName);
        int grade = report.optInt("gradeLevel", 4);
        String phaseText = isLiveOnline
                ? report.optString("phaseText", "🟢 Çevrimiçi")
                : "⚪ Şu anda offline görünüyor (Kayıtlı geçmiş gösteriliyor)";
        String remFmt = report.optString("remainingFormatted", "40:00");
        String elapsedFmt = report.optString("subjectElapsedFormatted", "0 sn");
        String todayFmt = report.optString("todayTotalFormatted", "0 dk");
        int todayCount = report.optInt("todaySessionCount", 0);

        tvDetailChildName.setText("👤 " + latestName + " (" + grade + ". Sınıf)");
        tvDetailLivePhase.setText(phaseText);
        tvDetailLiveTimers.setText("⏱️ Kalan Sayaç: " + remFmt
                + " • Bu Derste Geçen: " + elapsedFmt
                + "\n🎯 Bugün Tamamlanan Çalışma (Ödev/Test/Kitap): " + todayCount + " (" + todayFmt + ")");

        JSONObject weekly = report.optJSONObject("weeklyStats");
        if (weekly != null) {
            tvDetailWeeklySummary.setText("Son 7 Gün Çalışma: "
                    + weekly.optInt("homeworkCount", 0) + " Ödev (" + weekly.optString("homeworkFormatted", "0 dk") + ") • "
                    + weekly.optInt("testCount", 0) + " Test (" + weekly.optString("testFormatted", "0 dk") + ") • "
                    + weekly.optInt("bookCount", 0) + " Kitap (" + weekly.optString("bookFormatted", "0 dk") + ")");
        }

        int pad = (int) (6 * getResources().getDisplayMetrics().density);
        JSONArray badges = report.optJSONArray("badges");
        if (badges != null) {
            layoutDetailBadges.removeAllViews();
            for (int i = 0; i < badges.length(); i++) {
                JSONObject b = badges.optJSONObject(i);
                if (b == null) continue;
                boolean unlocked = b.optBoolean("unlocked", false);
                TextView tv = new TextView(this);
                tv.setText(b.optString("title", "") + (unlocked ? "  ✅ KAZANILDI" : "  🔒 (Hedef)"));
                tv.setTextColor(unlocked ? 0xFF00897B : 0xFF64748B);
                tv.setTextSize(14f);
                tv.setTypeface(null, unlocked ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
                tv.setPadding(0, pad / 2, 0, pad / 2);
                layoutDetailBadges.addView(tv);
            }
        }

        renderSelectedCalendarDate();
    }

    /**
     * Takvimde (CalendarView) tıklanan güne ait Ödev, Test ve Kitap Okuma çalışmalarını
     * (ve çalışma sayılmayan eğlence oyun kayıtlarını) listeler.
     */
    private void renderSelectedCalendarDate() {
        tvVeliSelectedDateTitle.setText("📌 Seçilen Gün: " + selectedCalendarDate);
        layoutDetailCalendarRecords.removeAllViews();
        int pad = (int) (8 * getResources().getDisplayMetrics().density);

        if (lastLoadedReport == null || !lastLoadedReport.has("calendarRecords")) {
            tvVeliSelectedDateSummary.setText("Henüz senkronize edilmiş takvim verisi yok.");
            TextView empty = new TextView(this);
            empty.setText("Öğrenci cihazıyla aynı Wi-Fi ağına bağlandığınızda tüm takvim geçmişi otomatik olarak kaydedilecektir.");
            empty.setTextColor(0xFF64748B);
            empty.setTextSize(14f);
            layoutDetailCalendarRecords.addView(empty);
            return;
        }

        JSONArray calArr = lastLoadedReport.optJSONArray("calendarRecords");
        int matchCount = 0;
        int studyCount = 0;
        int gameCount = 0;

        if (calArr != null) {
            for (int i = 0; i < calArr.length(); i++) {
                JSONObject item = calArr.optJSONObject(i);
                if (item == null) continue;
                String date = item.optString("date", "");
                if (!selectedCalendarDate.equals(date)) continue;

                matchCount++;
                String time = item.optString("time", "");
                String mode = item.optString("mode", "");
                String subj = item.optString("subject", "");
                String dur = item.optString("durationFormatted", "");

                String icon;
                if ("Oyun".equalsIgnoreCase(mode)) {
                    gameCount++;
                    icon = "🎮 [Eğlence / Oyun - Çalışma Dışı] ";
                } else {
                    studyCount++;
                    if ("Test Çözümü".equalsIgnoreCase(mode)) {
                        icon = "📝 [Test Çözümü] ";
                    } else if ("Kitap Okuma".equalsIgnoreCase(mode)) {
                        icon = "📖 [Kitap Okuma] ";
                    } else {
                        icon = "📚 [Ödev] ";
                    }
                }

                TextView tv = new TextView(this);
                tv.setText(icon + subj + " — " + dur + " (Saat: " + time + ")");
                tv.setTextColor("Oyun".equalsIgnoreCase(mode) ? 0xFF64748B : 0xFF0F172A);
                tv.setTextSize(14f);
                tv.setPadding(0, pad / 2, 0, pad / 2);
                layoutDetailCalendarRecords.addView(tv);
            }
        }

        tvVeliSelectedDateSummary.setText("🎯 Bu Günkü Çalışma Sayısı (Ödev/Test/Kitap): " + studyCount
                + (gameCount > 0 ? "  •  🎮 Oyun (Çalışma Dışı): " + gameCount : ""));

        if (matchCount == 0) {
            TextView empty = new TextView(this);
            empty.setText("Seçilen tarihte (" + selectedCalendarDate + ") kaydedilmiş bir çalışma bulunmuyor.");
            empty.setTextColor(0xFF64748B);
            empty.setTextSize(14f);
            empty.setPadding(0, pad, 0, pad);
            layoutDetailCalendarRecords.addView(empty);
        }
    }
}
