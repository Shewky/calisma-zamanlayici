package com.calismazamanlayici.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;

/**
 * Öğrenci uygulamasındaki İkili Sesli Görüşme ekranı.
 * 1. Çocuk "Anne'ye Çağrı" veya "Baba'ya Çağrı" butonuna bastığında Veli cihazına çağrı gönderir;
 *    Veli uygulamasından onay verildiğinde ikili sesli görüşme başlar.
 * 2. Veli uygulamasından "Ara" butonuna basıldığında onaya gerek kalmadan doğrudan sesli görüşme başlar.
 */
public class CallActivity extends Activity {

    private static CallActivity activeInstance;
    private TextView tvCallTitle;
    private TextView tvCallStatus;
    private String parentRole = "ANNE";
    private String targetParentIp = "";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

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
        setContentView(R.layout.activity_call);
        activeInstance = this;

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
            tvCallStatus.setText("🔔 " + roleLabel + " cihazına çağrı gönderiliyor...\nVeli uygulamasından onay bekleniyor...");
            sendOutgoingCallRequestToParent(parentRole);
        }

        btnEndCall.setOnClickListener(v -> {
            endCallAndNotifyParent();
            finish();
        });
    }

    private void sendOutgoingCallRequestToParent(final String role) {
        final String childName = StudyDatabaseHelper.getUserName(this);
        final String childId = StudyDatabaseHelper.getChildDeviceId(this);
        final String localIp = LanSyncServer.getLocalIpAddress(this);
        final String savedParentIp = StudyDatabaseHelper.getPairedParentIp(this, role);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // 1. UDP Broadcast ile aynı ağdaki Veli cihazına anında çağrı sinyali gönder
                    // (Veli cihazının IP adresi değişmiş olsa bile UDP sinyalini yakalar!)
                    String udpMsg = "CZ_CALL_FROM_CHILD|" + role + "|" + childId + "|" + childName + "|" + localIp;
                    byte[] udpBytes = udpMsg.getBytes("UTF-8");
                    DatagramSocket ds = new DatagramSocket();
                    ds.setBroadcast(true);
                    DatagramPacket pkt = new DatagramPacket(
                            udpBytes, udpBytes.length,
                            InetAddress.getByName("255.255.255.255"),
                            LanSyncServer.DISCOVERY_UDP_PORT
                    );
                    ds.send(pkt);
                    ds.close();

                    // 2. Kayıtlı Veli IP adresi varsa doğrudan HTTP POST gönder
                    if (savedParentIp != null && !savedParentIp.isEmpty()) {
                        targetParentIp = savedParentIp;
                        JSONObject body = new JSONObject();
                        body.put("targetRole", role);
                        body.put("childId", childId);
                        body.put("childName", childName);
                        body.put("childIp", localIp);

                        byte[] payload = body.toString().getBytes("UTF-8");
                        URL url = new URL("http://" + savedParentIp + ":" + LanSyncServer.PARENT_HTTP_PORT + "/incoming_call_from_child");
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("POST");
                        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                        conn.setConnectTimeout(4000);
                        conn.setReadTimeout(4000);
                        conn.setDoOutput(true);
                        OutputStream os = conn.getOutputStream();
                        os.write(payload);
                        os.flush();
                        os.close();
                        conn.getResponseCode();
                    }
                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    private void endCallAndNotifyParent() {
        VoiceCallManager.stopCall(this);
        final String ip = !targetParentIp.isEmpty() ? targetParentIp : StudyDatabaseHelper.getPairedParentIp(this, parentRole);
        if (ip != null && !ip.isEmpty()) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        URL url = new URL("http://" + ip + ":" + LanSyncServer.PARENT_HTTP_PORT + "/end_call");
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("POST");
                        conn.setConnectTimeout(2000);
                        conn.getResponseCode();
                    } catch (Exception ignored) {
                    }
                }
            }).start();
        }
    }

    @Override
    protected void onDestroy() {
        VoiceCallManager.stopCall(this);
        if (activeInstance == this) {
            activeInstance = null;
        }
        super.onDestroy();
    }
}
