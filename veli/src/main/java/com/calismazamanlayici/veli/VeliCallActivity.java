package com.calismazamanlayici.veli;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLEncoder;

public class VeliCallActivity extends Activity {

    private static VeliCallActivity activeInstance;
    private TextView tvVeliCallTitle;
    private TextView tvVeliCallStatus;
    private Button btnApproveIncomingCall;

    private String childId = "";
    private String childName = "Öğrenci";
    private String childIp = "";

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
        setContentView(R.layout.activity_veli_call);
        activeInstance = this;

        tvVeliCallTitle = findViewById(R.id.tvVeliCallTitle);
        tvVeliCallStatus = findViewById(R.id.tvVeliCallStatus);
        btnApproveIncomingCall = findViewById(R.id.btnApproveIncomingCall);
        Button btnEndVeliCall = findViewById(R.id.btnEndVeliCall);

        boolean incomingApprovalNeeded = false;
        if (getIntent() != null) {
            String cid = getIntent().getStringExtra("EXTRA_CHILD_ID");
            if (cid != null) childId = cid;
            String cname = getIntent().getStringExtra("EXTRA_CHILD_NAME");
            if (cname != null) childName = cname;
            String cip = getIntent().getStringExtra("EXTRA_CHILD_IP");
            if (cip != null) childIp = cip;
            incomingApprovalNeeded = getIntent().getBooleanExtra("EXTRA_INCOMING_APPROVAL", false);
        }

        tvVeliCallTitle.setText("📞 " + childName + " ile Sesli Görüşme");

        if (incomingApprovalNeeded) {
            btnApproveIncomingCall.setVisibility(View.VISIBLE);
            tvVeliCallStatus.setText("🔔 " + childName + " çağrı gönderiyor!\nİkili görüşmeyi başlatmak için aşağıdan onay verin:");
        } else {
            btnApproveIncomingCall.setVisibility(View.GONE);
            tvVeliCallStatus.setText("🟢 " + childName + " doğrudan aranıyor...\nOnaya gerek kalmadan ikili görüşme başlatıldı (Hoparlör Açık)");
            triggerDirectCallOnChild();
        }

        btnApproveIncomingCall.setOnClickListener(v -> {
            btnApproveIncomingCall.setVisibility(View.GONE);
            tvVeliCallStatus.setText("🟢 Çağrı Onaylandı!\n" + childName + " ile ikili sesli görüşme aktif (Hoparlör Açık)");
            approveIncomingChildCall();
        });

        btnEndVeliCall.setOnClickListener(v -> {
            endCallAndNotifyChild();
            finish();
        });
    }

    private void triggerDirectCallOnChild() {
        VeliVoiceCallManager.startCall(this, childIp);
        final String parentRole = VeliStorage.getParentRole(this);
        final String myIp = ParentBackgroundService.getLocalIpAddress(this);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String udpMsg = "CZ_DIRECT_CALL|" + parentRole + "|" + myIp;
                    byte[] udpBytes = udpMsg.getBytes("UTF-8");
                    DatagramSocket ds = new DatagramSocket();
                    ds.setBroadcast(true);
                    if (childIp != null && !childIp.isEmpty()) {
                        ds.send(new DatagramPacket(udpBytes, udpBytes.length,
                                InetAddress.getByName(childIp), ParentBackgroundService.DISCOVERY_UDP_PORT));
                    }
                    ds.send(new DatagramPacket(udpBytes, udpBytes.length,
                            InetAddress.getByName("255.255.255.255"), ParentBackgroundService.DISCOVERY_UDP_PORT));
                    ds.close();
                } catch (Exception ignored) {
                }

                try {
                    String q = "role=" + URLEncoder.encode(parentRole, "UTF-8")
                            + "&ip=" + URLEncoder.encode(myIp, "UTF-8");
                    URL url = new URL("http://" + childIp + ":" + ParentBackgroundService.STUDENT_HTTP_PORT + "/direct_call_from_parent?" + q);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("Connection", "close");
                    conn.setConnectTimeout(2000);
                    conn.setReadTimeout(2000);
                    conn.getResponseCode();
                    conn.disconnect();
                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    private void approveIncomingChildCall() {
        VeliVoiceCallManager.startCall(this, childIp);
        final String myIp = ParentBackgroundService.getLocalIpAddress(this);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String udpMsg = "CZ_CALL_ACCEPTED|" + myIp;
                    byte[] udpBytes = udpMsg.getBytes("UTF-8");
                    DatagramSocket ds = new DatagramSocket();
                    ds.setBroadcast(true);
                    if (childIp != null && !childIp.isEmpty()) {
                        ds.send(new DatagramPacket(udpBytes, udpBytes.length,
                                InetAddress.getByName(childIp), ParentBackgroundService.DISCOVERY_UDP_PORT));
                    }
                    ds.send(new DatagramPacket(udpBytes, udpBytes.length,
                            InetAddress.getByName("255.255.255.255"), ParentBackgroundService.DISCOVERY_UDP_PORT));
                    ds.close();
                } catch (Exception ignored) {
                }

                try {
                    String q = "ip=" + URLEncoder.encode(myIp, "UTF-8");
                    URL url = new URL("http://" + childIp + ":" + ParentBackgroundService.STUDENT_HTTP_PORT + "/parent_accepted_call?" + q);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("Connection", "close");
                    conn.setConnectTimeout(2000);
                    conn.setReadTimeout(2000);
                    conn.getResponseCode();
                    conn.disconnect();
                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    private void endCallAndNotifyChild() {
        VeliVoiceCallManager.stopCall(this);
        if (childIp != null && !childIp.isEmpty()) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        URL url = new URL("http://" + childIp + ":" + ParentBackgroundService.STUDENT_HTTP_PORT + "/end_call");
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
        VeliVoiceCallManager.stopCall(this);
        if (activeInstance == this) {
            activeInstance = null;
        }
        super.onDestroy();
    }
}
