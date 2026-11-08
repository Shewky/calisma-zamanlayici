package com.calismazamanlayici.veli;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
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

/**
 * Veli uygulamasında çocuğun adına tıklandığında açılan detaylı takip ekranı:
 * - Anne veya Baba rolünü dinamik ayırt ederek hazır övgüleri ("Annenden/Babandan övgü aldın tebrikler") gönderir.
 * - "Özelleştirilmiş Övgü Gönder" özelliği ile klavyeden girilen mesajı
 *   Anne ise "Annenden mesaj: ...", Baba ise "Babandan mesaj: ..." diyerek öğrenci cihazında sesli anons ettirir.
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
    private TextView tvDetailWeeklySummary;
    private LinearLayout layoutDetailBadges;
    private LinearLayout layoutDetailCalendarRecords;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private boolean polling = false;

    private final Runnable detailPoller = new Runnable() {
        @Override
        public void run() {
            if (!polling) return;
            fetchFullReportFromChild();
            uiHandler.postDelayed(this, 2500L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_child_detail);

        if (getIntent() != null) {
            childId = getIntent().getStringExtra("EXTRA_CHILD_ID");
            childName = getIntent().getStringExtra("EXTRA_CHILD_NAME");
            childIp = getIntent().getStringExtra("EXTRA_CHILD_IP");
        }

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
        tvDetailWeeklySummary = findViewById(R.id.tvDetailWeeklySummary);
        layoutDetailBadges = findViewById(R.id.layoutDetailBadges);
        layoutDetailCalendarRecords = findViewById(R.id.layoutDetailCalendarRecords);

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
                            tvDetailLivePhase.setText("⚪ Şu anda offline görünüyor");
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

                // 1. Hızlı UDP paketi ile gönder
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

                // 2. GET tabanlı HTTP isteği ile gönder
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
                                    "Öğrenci cihazı ağda aranıyor, lütfen tekrar deneyin.",
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

                    final JSONObject report = new JSONObject(sb.toString());
                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            renderReportOnUi(report);
                        }
                    });
                } catch (Exception e) {
                    ParentBackgroundService.scanLanAndSyncChildren(ChildDetailActivity.this, false, null);
                }
            }
        }).start();
    }

    private void renderReportOnUi(JSONObject report) {
        String latestName = report.optString("userName", childName);
        int grade = report.optInt("gradeLevel", 4);
        String phaseText = report.optString("phaseText", "🟢 Çevrimiçi");
        String remFmt = report.optString("remainingFormatted", "40:00");
        String elapsedFmt = report.optString("subjectElapsedFormatted", "0 sn");
        String todayFmt = report.optString("todayTotalFormatted", "0 dk");
        int todayCount = report.optInt("todaySessionCount", 0);

        tvDetailChildName.setText("👤 " + latestName + " (" + grade + ". Sınıf)");
        tvDetailLivePhase.setText(phaseText);
        tvDetailLiveTimers.setText("⏱️ Kalan Sayaç: " + remFmt
                + " • Bu Derste Geçen: " + elapsedFmt
                + "\n🎯 Bugün Tamamlanan: " + todayCount + " çalışma (" + todayFmt + ")");

        JSONObject weekly = report.optJSONObject("weeklyStats");
        if (weekly != null) {
            tvDetailWeeklySummary.setText("Son 7 Gün: "
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

        JSONArray calArr = report.optJSONArray("calendarRecords");
        if (calArr != null) {
            layoutDetailCalendarRecords.removeAllViews();
            if (calArr.length() == 0) {
                TextView empty = new TextView(this);
                empty.setText("Henüz takvimde tamamlanmış çalışma kaydı bulunmuyor.");
                empty.setTextColor(0xFF64748B);
                empty.setTextSize(14f);
                layoutDetailCalendarRecords.addView(empty);
            } else {
                for (int i = 0; i < calArr.length(); i++) {
                    JSONObject item = calArr.optJSONObject(i);
                    if (item == null) continue;
                    String date = item.optString("date", "");
                    String time = item.optString("time", "");
                    String mode = item.optString("mode", "");
                    String subj = item.optString("subject", "");
                    String dur = item.optString("durationFormatted", "");

                    TextView tv = new TextView(this);
                    tv.setText("🗓️ " + date + " (" + time + ") • [" + mode + "] " + subj + " — " + dur);
                    tv.setTextColor(0xFF0F172A);
                    tv.setTextSize(14f);
                    tv.setPadding(0, pad / 2, 0, pad / 2);
                    layoutDetailCalendarRecords.addView(tv);
                }
            }
        }
    }
}
