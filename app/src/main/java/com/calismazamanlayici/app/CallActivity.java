package com.calismazamanlayici.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Öğrenci uygulamasındaki İkili Sesli Görüşme ekranı:
 * - Anne arandığında sadece Anne rolündeki Veli cihazını, Baba arandığında sadece Baba rolündeki Veli cihazını arar.
 * - Hedef veli çevrimdışıysa diğer veliyi çaldırmaz; ekranda "Anne offline" veya "Baba offline" bilgisi verir.
 * - Öğrenci çağrıyı iptal ettiğinde Veli uygulamasındaki çağrı isteğini de anında iptal eder.
 */
public class CallActivity extends Activity {

    private static CallActivity activeInstance;
    private TextView tvCallTitle;
    private TextView tvCallStatus;
    private String parentRole = "ANNE";
    private volatile String targetParentIp = "";
    private volatile boolean callCancelledByStudent = false;

    public static void notifyParentAccepted() {
        if (activeInstance != null && !activeInstance.isFinishing()) {
            activeInstance.runOnUiThread(() -> {
                activeInstance.tvCallStatus.setText("🟢 Veli Çağrıyı Onayladı!\nİkili sesli görüşme aktif (Hoparlör Açık)");
            });
        }
    }

    public static void notifyCallEndedRemotely() {
        if (activeInstance != null && !activeInstance.isFinishing()) {
            activeInstance.runOnUiThread(() -> {
                Toast.makeText(activeInstance, "Görüşme sonlandırıldı.", Toast.LENGTH_SHORT).show();
                activeInstance.finish();
            });
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        setContentView(R.layout.activity_call);
        activeInstance = this;
        callCancelledByStudent = false;

        LinearLayout layoutRootCall = findViewById(R.id.layoutRootCall);
        tvCallTitle = findViewById(R.id.tvCallTitle);
        tvCallStatus = findViewById(R.id.tvCallStatus);
        Button btnEndCall = findViewById(R.id.btnEndCall);

        ThemeHelper.applyWindowAndSurface(this, layoutRootCall);
        btnEndCall.setBackground(ThemeHelper.createButtonDrawable(this, 0xFFD32F2F));

        boolean directConnected = false;
        if (getIntent() != null) {
            String r = getIntent().getStringExtra("EXTRA_PARENT_ROLE");
            if (r != null) parentRole = r;
            String ip = getIntent().getStringExtra("EXTRA_PARENT_IP");
            if (ip != null) targetParentIp = ip;
            directConnected = getIntent().getBooleanExtra("EXTRA_DIRECT_CONNECTED", false);
        }

        String roleLabel = "BABA".equalsIgnoreCase(parentRole) ? "👨 Baba" : "👩 Anne";
        tvCallTitle.setText("📞 " + roleLabel + " ile Sesli Görüşme");

        if (directConnected) {
            tvCallStatus.setText("🟢 " + roleLabel + " tarafından doğrudan görüşme başlatıldı!\nİkili sesli görüşme aktif (Hoparlör Açık)");
        } else {
            tvCallStatus.setText("🔍 " + roleLabel + " cihazı kontrol ediliyor...");
            verifyParentOnlineAndSendCall(parentRole);
        }

        btnEndCall.setOnClickListener(v -> {
            callCancelledByStudent = true;
            endCallAndNotifyParent();
            finish();
        });
    }

    /**
     * Önce hedef velinin (Anne veya Baba) çevrimiçi olup olmadığını ve rolünü doğrular.
     * Eğer hedef veli offline ise çağrıyı diğer veliye yönlendirmez ve "Anne offline" / "Baba offline" yazar.
     */
    private void verifyParentOnlineAndSendCall(final String requestedRole) {
        final String childName = StudyDatabaseHelper.getUserName(this);
        final String childId = StudyDatabaseHelper.getChildDeviceId(this);
        final String localIp = LanSyncServer.getLocalIpAddress(this);
        final String savedParentIp = StudyDatabaseHelper.getPairedParentIp(this, requestedRole);
        final String roleNameTr = "BABA".equalsIgnoreCase(requestedRole) ? "Baba" : "Anne";

        new Thread(new Runnable() {
            @Override
            public void run() {
                String verifiedIp = null;

                // 1. Önce kayıtlı IP adresindeki velinin çevrimiçi olup olmadığını ve rolünün gerçekten requestedRole olduğunu kontrol et
                if (savedParentIp != null && !savedParentIp.isEmpty()) {
                    if (checkIfParentIpMatchesRole(savedParentIp, requestedRole)) {
                        verifiedIp = savedParentIp;
                    }
                }

                // 2. Kayıtlı IP yanıt vermediyse veya rolü farklıysa, yerel ağı tarayıp SADECE requestedRole olan veliyi bul
                if (verifiedIp == null && !callCancelledByStudent) {
                    verifiedIp = scanSubnetForSpecificParentRole(localIp, requestedRole);
                }

                if (callCancelledByStudent) return;

                if (verifiedIp == null) {
                    // Hedef veli offline! Diğer veliyi kesinlikle arama!
                    runOnUiThread(() -> {
                        if (isFinishing()) return;
                        String offlineMsg = roleNameTr + " offline";
                        tvCallStatus.setText("⚠️ " + offlineMsg + "\n(" + roleNameTr + " şu anda ağda çevrimiçi değil)");
                        Toast.makeText(CallActivity.this, "⚠️ " + offlineMsg, Toast.LENGTH_LONG).show();
                        Intent speakIntent = new Intent(CallActivity.this, TimerForegroundService.class);
                        speakIntent.setAction(TimerForegroundService.ACTION_SPEAK_CUSTOM);
                        speakIntent.putExtra(TimerForegroundService.EXTRA_CUSTOM_SPEECH, offlineMsg);
                        startService(speakIntent);
                    });
                    return;
                }

                // Hedef veli online ve rolü doğrulandı!
                targetParentIp = verifiedIp;
                StudyDatabaseHelper.setPairedParentIp(CallActivity.this, requestedRole, verifiedIp);

                runOnUiThread(() -> {
                    if (!isFinishing()) {
                        tvCallStatus.setText("🔔 " + roleNameTr + " cihazına çağrı gönderildi!\nOnay bekleniyor...");
                    }
                });

                // Sadece doğrulanmış hedef Veli IP'sine çağrı gönder
                sendHttpCallSignal(verifiedIp, requestedRole, childId, childName, localIp);
                try {
                    String udpMsg = "CZ_CALL_FROM_CHILD|" + requestedRole + "|" + childId + "|" + childName + "|" + localIp;
                    byte[] udpBytes = udpMsg.getBytes("UTF-8");
                    DatagramSocket ds = new DatagramSocket();
                    ds.send(new DatagramPacket(udpBytes, udpBytes.length,
                            InetAddress.getByName(verifiedIp), LanSyncServer.DISCOVERY_UDP_PORT));
                    ds.close();
                } catch (Exception ignored) {
                }
            }
        }, "RoleVerifiedCallSender").start();
    }

    private boolean checkIfParentIpMatchesRole(String candidateIp, String expectedRole) {
        try {
            URL url = new URL("http://" + candidateIp + ":" + LanSyncServer.PARENT_HTTP_PORT + "/parent_status");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Connection", "close");
            conn.setConnectTimeout(700);
            conn.setReadTimeout(700);
            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();
                conn.disconnect();
                JSONObject obj = new JSONObject(sb.toString());
                String actualRole = obj.optString("parentRole", "");
                return expectedRole.equalsIgnoreCase(actualRole);
            }
            conn.disconnect();
        } catch (Exception ignored) {
        }
        return false;
    }

    private String scanSubnetForSpecificParentRole(String localIp, final String expectedRole) {
        int lastDot = localIp.lastIndexOf('.');
        if (lastDot <= 0 || localIp.equals("127.0.0.1")) return null;
        final String prefix = localIp.substring(0, lastDot + 1);
        final AtomicReference<String> matchedIp = new AtomicReference<>(null);
        ExecutorService pool = Executors.newFixedThreadPool(32);
        for (int i = 1; i <= 254; i++) {
            final String candidateIp = prefix + i;
            pool.submit(new Runnable() {
                @Override
                public void run() {
                    if (matchedIp.get() != null || callCancelledByStudent) return;
                    try {
                        Socket s = new Socket();
                        s.connect(new InetSocketAddress(candidateIp, LanSyncServer.PARENT_HTTP_PORT), 260);
                        s.close();
                        if (checkIfParentIpMatchesRole(candidateIp, expectedRole)) {
                            matchedIp.compareAndSet(null, candidateIp);
                        }
                    } catch (Exception ignored) {
                    }
                }
            });
        }
        pool.shutdown();
        try {
            pool.awaitTermination(1600L, TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {
        }
        return matchedIp.get();
    }

    private boolean sendHttpCallSignal(String parentIp, String role, String childId, String childName, String childIp) {
        try {
            String query = "role=" + URLEncoder.encode(role, "UTF-8")
                    + "&childId=" + URLEncoder.encode(childId, "UTF-8")
                    + "&childName=" + URLEncoder.encode(childName, "UTF-8")
                    + "&childIp=" + URLEncoder.encode(childIp, "UTF-8");
            URL url = new URL("http://" + parentIp + ":" + LanSyncServer.PARENT_HTTP_PORT + "/incoming_call_from_child?" + query);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Connection", "close");
            conn.setConnectTimeout(1200);
            conn.setReadTimeout(1200);
            int code = conn.getResponseCode();
            conn.disconnect();
            return code == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Öğrenci çağrıyı iptal ettiğinde veya sonlandırdığında hem UDP hem HTTP üzerinden
     * Veli uygulamasındaki çalan çağrı isteğini anında iptal eder!
     */
    private void endCallAndNotifyParent() {
        VoiceCallManager.stopCall(this);
        final String ip = !targetParentIp.isEmpty() ? targetParentIp : StudyDatabaseHelper.getPairedParentIp(this, parentRole);
        final String role = parentRole;
        final String childId = StudyDatabaseHelper.getChildDeviceId(this);

        new Thread(new Runnable() {
            @Override
            public void run() {
                // 1. Anında UDP İptal Sinyali Gönder (3 kez)
                try {
                    String cancelMsg = "CZ_CANCEL_CALL|" + role + "|" + childId;
                    byte[] data = cancelMsg.getBytes("UTF-8");
                    DatagramSocket ds = new DatagramSocket();
                    ds.setBroadcast(true);
                    for (int k = 0; k < 3; k++) {
                        if (ip != null && !ip.isEmpty()) {
                            try {
                                ds.send(new DatagramPacket(data, data.length,
                                        InetAddress.getByName(ip), LanSyncServer.DISCOVERY_UDP_PORT));
                            } catch (Exception ignored) {
                            }
                        }
                        try {
                            ds.send(new DatagramPacket(data, data.length,
                                    InetAddress.getByName("255.255.255.255"), LanSyncServer.DISCOVERY_UDP_PORT));
                        } catch (Exception ignored) {
                        }
                        Thread.sleep(80L);
                    }
                    ds.close();
                } catch (Exception ignored) {
                }

                // 2. HTTP /end_call gönder
                if (ip != null && !ip.isEmpty()) {
                    try {
                        URL url = new URL("http://" + ip + ":" + LanSyncServer.PARENT_HTTP_PORT + "/end_call?role=" + URLEncoder.encode(role, "UTF-8"));
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("GET");
                        conn.setRequestProperty("Connection", "close");
                        conn.setConnectTimeout(1200);
                        conn.getResponseCode();
                        conn.disconnect();
                    } catch (Exception ignored) {
                    }
                }
            }
        }).start();
    }

    @Override
    public void onBackPressed() {
        callCancelledByStudent = true;
        endCallAndNotifyParent();
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        callCancelledByStudent = true;
        VoiceCallManager.stopCall(this);
        if (activeInstance == this) {
            activeInstance = null;
        }
        super.onDestroy();
    }
}
