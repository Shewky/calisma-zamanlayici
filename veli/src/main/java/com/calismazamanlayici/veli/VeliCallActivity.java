package com.calismazamanlayici.veli;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Veli uygulamasındaki İkili Sesli Görüşme ekranı:
 * 1. Çocuktan çağrı geldiğinde: "Yanıtlamak için uygulamadan onay ver" uyarısıyla açılır;
 *    Veli "Onay Ver ve Görüşmeyi Başlat" butonuna tıkladığında ikili görüşme başlar.
 * 2. Veli "Ara" butonuna bastığında: Onaya gerek kalmadan doğrudan Çalışma Zamanlayıcı
 *    uygulamasında görüşmeyi başlatır.
 */
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

    /**
     * Veli doğrudan "Ara" butonuna bastığında Çalışma Zamanlayıcı uygulamasında
     * onaya gerek kalmadan görüşmeyi anında başlatır.
     */
    private void triggerDirectCallOnChild() {
        VeliVoiceCallManager.startCall(this, childIp);
        final String parentRole = VeliStorage.getParentRole(this);
        final String myIp = ParentBackgroundService.getLocalIpAddress(this);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject body = new JSONObject();
                    body.put("parentRole", parentRole);
                    body.put("parentIp", myIp);
                    byte[] payload = body.toString().getBytes("UTF-8");

                    URL url = new URL("http://" + childIp + ":" + ParentBackgroundService.STUDENT_HTTP_PORT + "/direct_call_from_parent");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    conn.setConnectTimeout(3000);
                    conn.setDoOutput(true);
                    OutputStream os = conn.getOutputStream();
                    os.write(payload);
                    os.flush();
                    os.close();
                    conn.getResponseCode();
                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    /**
     * Çocuktan gelen çağrıya Veli onay verdiğinde ikili görüşmeyi başlatır.
     */
    private void approveIncomingChildCall() {
        VeliVoiceCallManager.startCall(this, childIp);
        final String myIp = ParentBackgroundService.getLocalIpAddress(this);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject body = new JSONObject();
                    body.put("parentIp", myIp);
                    byte[] payload = body.toString().getBytes("UTF-8");

                    URL url = new URL("http://" + childIp + ":" + ParentBackgroundService.STUDENT_HTTP_PORT + "/parent_accepted_call");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    conn.setConnectTimeout(3000);
                    conn.setDoOutput(true);
                    OutputStream os = conn.getOutputStream();
                    os.write(payload);
                    os.flush();
                    os.close();
                    conn.getResponseCode();
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
        VeliVoiceCallManager.stopCall(this);
        if (activeInstance == this) {
            activeInstance = null;
        }
        super.onDestroy();
    }
}
