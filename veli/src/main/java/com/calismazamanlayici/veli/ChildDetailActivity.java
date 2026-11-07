package com.calismazamanlayici.veli;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Veli uygulamasında çocuğun adına tıklandığında açılan detaylı takip ekranı:
 * - Anlık çalışma durumu ve canlı sayaç
 * - Anne / Baba senaryosuna uygun uzaktan sesli övgü gönderme ("Annenden/Babandan övgü aldın tebrikler", "Aferin sana")
 * - Haftalık başarı ödülleri / rozetler
 * - Çalışma Takvimi kayıtları (Ödev, Test Çözümü, Kitap Okuma)
 */
public class ChildDetailActivity extends Activity {

    private String childId = "";
    private String childName = "Öğrenci";
    private String childIp = "";

    private TextView tvDetailChildName;
    private TextView tvDetailLivePhase;
    private TextView tvDetailLiveTimers;
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

        tvDetailChildName = findViewById(R.id.tvDetailChildName);
        tvDetailLivePhase = findViewById(R.id.tvDetailLivePhase);
        tvDetailLiveTimers = findViewById(R.id.tvDetailLiveTimers);
        tvDetailWeeklySummary = findViewById(R.id.tvDetailWeeklySummary);
        layoutDetailBadges = findViewById(R.id.layoutDetailBadges);
        layoutDetailCalendarRecords = findViewById(R.id.layoutDetailCalendarRecords);

        Button btnSendRolePraise = findViewById(R.id.btnSendRolePraise);
        Button btnSendAferinPraise = findViewById(R.id.btnSendAferinPraise);
        Button btnDirectCallFromDetail = findViewById(R.id.btnDirectCallFromDetail);
        Button btnBack = findViewById(R.id.btnBackFromDetail);

        tvDetailChildName.setText("👤 " + childName + " • Canlı Takip ve Takvim");

        final String parentRole = VeliStorage.getParentRole(this);
        final String rolePraiseText = "BABA".equalsIgnoreCase(parentRole)
                ? "Babandan övgü aldın tebrikler"
                : "Annenden övgü aldın tebrikler";

        btnSendRolePraise.setText("🎉 '" + rolePraiseText + "' Gönder");
        btnSendAferinPraise.setText("🌟 '" + rolePraiseText + "! Aferin sana!' Gönder");

        btnSendRolePraise.setOnClickListener(v -> sendPraiseToChild(rolePraiseText));
        btnSendAferinPraise.setOnClickListener(v -> sendPraiseToChild(rolePraiseText + "! Aferin sana!"));

        btnDirectCallFromDetail.setOnClickListener(v -> {
            Intent callIntent = new Intent(ChildDetailActivity.this, VeliCallActivity.class);
            callIntent.putExtra("EXTRA_CHILD_ID", childId);
            callIntent.putExtra("EXTRA_CHILD_NAME", childName);
            callIntent.putExtra("EXTRA_CHILD_IP", childIp);
            callIntent.putExtra("EXTRA_INCOMING_APPROVAL", false);
            startActivity(callIntent);
        });

        btnBack.setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        polling = true;
        uiHandler.removeCallbacks(detailPoller);
        uiHandler.post(detailPoller);
    }

    @Override
    protected void onPause() {
        polling = false;
        uiHandler.removeCallbacks(detailPoller);
        super.onPause();
    }

    private void sendPraiseToChild(final String praiseSpeech) {
        final String parentRole = VeliStorage.getParentRole(this);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject body = new JSONObject();
                    body.put("parentRole", parentRole);
                    body.put("praiseText", praiseSpeech);
                    byte[] payload = body.toString().getBytes("UTF-8");

                    URL url = new URL("http://" + childIp + ":" + ParentBackgroundService.STUDENT_HTTP_PORT + "/praise");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    conn.setConnectTimeout(3000);
                    conn.setDoOutput(true);
                    OutputStream os = conn.getOutputStream();
                    os.write(payload);
                    os.flush();
                    os.close();

                    final int code = conn.getResponseCode();
                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (code == 200) {
                                Toast.makeText(ChildDetailActivity.this,
                                        "👏 Övgü mesajı gönderildi ve sesli anons edildi: '''" + praiseSpeech + "'''",
                                        Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(ChildDetailActivity.this, "Gönderilemedi (HTTP " + code + ")", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                } catch (final Exception e) {
                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(ChildDetailActivity.this, "Bağlantı hatası: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void fetchFullReportFromChild() {
        // Güncel IP adresini VeliStorage üzerinden kontrol et
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
                    conn.setConnectTimeout(2000);
                    conn.setReadTimeout(2000);
                    if (conn.getResponseCode() != 200) return;

                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();

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
