package com.calismazamanlayici.app;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Öğrenci uygulamasındaki İkili Sesli Görüşme ekranı.
 * Çocuktan Veliye çağrı gönderirken hem hızlı UDP sinyal patlaması (burst) hem de
 * GET tabanlı hızlı HTTP isteği ve otomatik Veli IP taraması kullanarak tek seferde anında bağlanır.
 */
public class CallActivity extends Activity {

    private static CallActivity activeInstance;
    private TextView tvCallTitle;
    private TextView tvCallStatus;
    private String parentRole = "ANNE";
    private String targetParentIp = "";

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
            tvCallStatus.setText("🔔 " + roleLabel + " cihazına çağrı gönderildi!\nVeli uygulamasından onay bekleniyor...");
            sendFastOutgoingCallRequestToParent(parentRole);
        }

        btnEndCall.setOnClickListener(v -> {
            endCallAndNotifyParent();
            finish();
        });
    }

    private void sendFastOutgoingCallRequestToParent(final String role) {
        final String childName = StudyDatabaseHelper.getUserName(this);
        final String childId = StudyDatabaseHelper.getChildDeviceId(this);
        final String localIp = LanSyncServer.getLocalIpAddress(this);
        final String savedParentIp = StudyDatabaseHelper.getPairedParentIp(this, role);

        new Thread(new Runnable() {
            @Override
            public void run() {
                // 1. Anında UDP sinyali gönder (255.255.255.255, alt ağ .255 ve kayıtlı Veli IP adresine)
                try {
                    String udpMsg = "CZ_CALL_FROM_CHILD|" + role + "|" + childId + "|" + childName + "|" + localIp;
                    byte[] udpBytes = udpMsg.getBytes("UTF-8");
                    DatagramSocket ds = new DatagramSocket();
                    ds.setBroadcast(true);

                    for (int attempt = 0; attempt < 3; attempt++) {
                        try {
                            ds.send(new DatagramPacket(udpBytes, udpBytes.length,
                                    InetAddress.getByName("255.255.255.255"), LanSyncServer.DISCOVERY_UDP_PORT));
                        } catch (Exception ignored) {
                        }
                        int lastDot = localIp.lastIndexOf('.');
                        if (lastDot > 0) {
                            String subnetBroadcast = localIp.substring(0, lastDot + 1) + "255";
                            try {
                                ds.send(new DatagramPacket(udpBytes, udpBytes.length,
                                        InetAddress.getByName(subnetBroadcast), LanSyncServer.DISCOVERY_UDP_PORT));
                            } catch (Exception ignored) {
                            }
                        }
                        if (savedParentIp != null && !savedParentIp.isEmpty()) {
                            try {
                                ds.send(new DatagramPacket(udpBytes, udpBytes.length,
                                        InetAddress.getByName(savedParentIp), LanSyncServer.DISCOVERY_UDP_PORT));
                            } catch (Exception ignored) {
                            }
                        }
                        Thread.sleep(120L);
                    }
                    ds.close();
                } catch (Exception ignored) {
                }

                // 2. Kayıtlı Veli IP'sine hızlı GET isteği gönder (POST gövdesi beklemeden anında tetikler!)
                boolean deliveredHttp = false;
                if (savedParentIp != null && !savedParentIp.isEmpty()) {
                    targetParentIp = savedParentIp;
                    deliveredHttp = sendHttpCallSignal(savedParentIp, role, childId, childName, localIp);
                }

                // 3. Eğer Veli IP'si kayıtlı değilse veya değişmişse, alt ağı paralel tarayıp Veli cihazını ~0.8 saniyede bul ve tetikle!
                if (!deliveredHttp) {
                    int lastDot = localIp.lastIndexOf('.');
                    if (lastDot > 0 && !localIp.equals("127.0.0.1")) {
                        final String prefix = localIp.substring(0, lastDot + 1);
                        final AtomicBoolean foundParent = new AtomicBoolean(false);
                        ExecutorService pool = Executors.newFixedThreadPool(32);
                        for (int i = 1; i <= 254; i++) {
                            final String candidateIp = prefix + i;
                            pool.submit(new Runnable() {
                                @Override
                                public void run() {
                                    if (foundParent.get()) return;
                                    try {
                                        Socket s = new Socket();
                                        s.connect(new InetSocketAddress(candidateIp, LanSyncServer.PARENT_HTTP_PORT), 300);
                                        s.close();
                                        if (sendHttpCallSignal(candidateIp, role, childId, childName, localIp)) {
                                            foundParent.set(true);
                                            targetParentIp = candidateIp;
                                            StudyDatabaseHelper.setPairedParentIp(CallActivity.this, role, candidateIp);
                                        }
                                    } catch (Exception ignored) {
                                    }
                                }
                            });
                        }
                        pool.shutdown();
                        try {
                            pool.awaitTermination(2500L, TimeUnit.MILLISECONDS);
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        }, "FastCallSender").start();
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
                        conn.setRequestMethod("GET");
                        conn.setRequestProperty("Connection", "close");
                        conn.setConnectTimeout(1500);
                        conn.getResponseCode();
                        conn.disconnect();
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
