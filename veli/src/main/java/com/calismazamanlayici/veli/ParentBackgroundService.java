package com.calismazamanlayici.veli;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Enumeration;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Veli uygulamasında sürekli arka planda ve ekran kapalıyken bile çalışan servis:
 * 1. Çocuktan gelen çağrıları SADECE veli rolü (Anne/Baba) eşleşiyorsa çaldırır.
 * 2. Öğrenci çağrıyı iptal ettiğinde titreşimi, anonsu ve çağrı ekranını anında kapatır.
 * 3. Anne ve Baba'nın birbirini ağda görmesini ve birbirlerine sesli mesaj göndermesini sağlar.
 * 4. Öğrenci yedeğini saklar ve gerektiğinde geri yükleme için sunar.
 */
public class ParentBackgroundService extends Service implements TextToSpeech.OnInitListener {

    public static final int DISCOVERY_UDP_PORT = 45677;
    public static final int STUDENT_HTTP_PORT = 45678;
    public static final int PARENT_HTTP_PORT = 45679;

    public static final String ACTION_SPEAK = "com.calismazamanlayici.veli.ACTION_SPEAK";
    public static final String EXTRA_SPEECH_TEXT = "extra_speech_text";
    public static final String ACTION_APPROVE_BREAK_PHONE = "com.calismazamanlayici.veli.ACTION_APPROVE_BREAK_PHONE";
    public static final String ACTION_DENY_BREAK_PHONE = "com.calismazamanlayici.veli.ACTION_DENY_BREAK_PHONE";

    private static final String CHANNEL_ID = "VeliMonitorChannel";
    private static final String CALL_ALERT_CHANNEL_ID = "VeliIncomingCallAlertChannel";
    private static final String SILENT_BREAK_REQ_CHANNEL_ID = "VeliSilentBreakRequestChannel";
    private static final int NOTIF_ID = 2001;
    private static final int CALL_ALERT_NOTIF_ID = 2002;
    private static final int BREAK_REQ_NOTIF_ID = 2003;

    private final IBinder binder = new LocalBinder();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;
    private WifiManager.MulticastLock multicastLock;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private String pendingSpeech = null;

    private volatile boolean running = false;
    private ServerSocket httpServer;
    private DatagramSocket udpSocket;
    private long lastIncomingAlertTimeMs = 0L;
    private long lastParentMsgTimeMs = 0L;

    public class LocalBinder extends Binder {
        public ParentBackgroundService getService() {
            return ParentBackgroundService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();
        acquireLocks();
        tts = new TextToSpeech(getApplicationContext(), this);
        startForeground(NOTIF_ID, buildNotification("Veli Arka Plan Çağrı ve Takip Servisi Aktif"));
        startServers();
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS && tts != null) {
            Locale tr = new Locale("tr", "TR");
            int res = tts.setLanguage(tr);
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(Locale.getDefault());
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                AudioAttributes attrs = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build();
                tts.setAudioAttributes(attrs);
            }
            tts.setSpeechRate(0.96f);
            ttsReady = true;
            if (pendingSpeech != null) {
                String s = pendingSpeech;
                pendingSpeech = null;
                speakNow(s);
            }
        }
    }

    private void acquireLocks() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && wakeLock == null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CalismaZamanlayiciVeli::WakeLock");
                wakeLock.setReferenceCounted(false);
            }
            if (wakeLock != null && !wakeLock.isHeld()) {
                wakeLock.acquire();
            }
        } catch (Exception ignored) {
        }
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                if (wifiLock == null) {
                    wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "CalismaZamanlayiciVeli::WifiLock");
                    wifiLock.setReferenceCounted(false);
                }
                if (!wifiLock.isHeld()) {
                    wifiLock.acquire();
                }
                if (multicastLock == null) {
                    multicastLock = wm.createMulticastLock("CalismaZamanlayiciVeli::MulticastLock");
                    multicastLock.setReferenceCounted(false);
                }
                if (!multicastLock.isHeld()) {
                    multicastLock.acquire();
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void wakeScreenBriefly() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                @SuppressWarnings("deprecation")
                PowerManager.WakeLock screenLock = pm.newWakeLock(
                        PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                        "CalismaZamanlayiciVeli::CallScreenWake"
                );
                screenLock.acquire(10000L);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        acquireLocks();
        startForeground(NOTIF_ID, buildNotification("Veli Arka Plan Çağrı ve Takip Servisi Aktif"));
        if (intent != null && intent.getAction() != null) {
            String act = intent.getAction();
            if (ACTION_SPEAK.equals(act)) {
                String text = intent.getStringExtra(EXTRA_SPEECH_TEXT);
                if (text != null && !text.isEmpty()) {
                    speakNow(text);
                }
            } else if (ACTION_APPROVE_BREAK_PHONE.equals(act) || ACTION_DENY_BREAK_PHONE.equals(act)) {
                boolean approve = ACTION_APPROVE_BREAK_PHONE.equals(act);
                String cid = intent.getStringExtra("EXTRA_CHILD_ID");
                String cip = intent.getStringExtra("EXTRA_CHILD_IP");
                sendBreakPhoneDecisionToChild(this, cid, cip, approve);
                try {
                    NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    if (nm != null) nm.cancel(BREAK_REQ_NOTIF_ID);
                } catch (Exception ignored) {
                }
            }
        }
        return START_STICKY;
    }

    private void startServers() {
        if (running) return;
        running = true;
        startUdpListener();
        startParentPresenceBeacon();
        startHttpServer();
    }

    public static String getLocalIpAddress(Context context) {
        try {
            WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                WifiInfo wi = wm.getConnectionInfo();
                int ipInt = wi != null ? wi.getIpAddress() : 0;
                if (ipInt != 0) {
                    return String.format(Locale.US, "%d.%d.%d.%d",
                            (ipInt & 0xff), (ipInt >> 8 & 0xff), (ipInt >> 16 & 0xff), (ipInt >> 24 & 0xff));
                }
            }
        } catch (Exception ignored) {
        }
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface nif = interfaces.nextElement();
                if (nif.isLoopback() || !nif.isUp()) continue;
                Enumeration<InetAddress> addrs = nif.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "127.0.0.1";
    }

    /**
     * Diğer velinin (Anne <-> Baba) bu cihazı otomatik görebilmesi için periyodik UDP varlık sinyali yayar.
     */
    private void startParentPresenceBeacon() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (running) {
                    try {
                        if (VeliStorage.isOnboarded(ParentBackgroundService.this)) {
                            String myRole = VeliStorage.getParentRole(ParentBackgroundService.this);
                            String myIp = getLocalIpAddress(ParentBackgroundService.this);
                            if (!myIp.equals("127.0.0.1")) {
                                String msg = "CZ_PARENT_HERE|" + myRole + "|" + myIp;
                                byte[] data = msg.getBytes("UTF-8");
                                DatagramSocket ds = new DatagramSocket();
                                ds.setBroadcast(true);
                                ds.send(new DatagramPacket(data, data.length,
                                        InetAddress.getByName("255.255.255.255"), DISCOVERY_UDP_PORT));
                                ds.close();
                            }
                        }
                    } catch (Exception ignored) {
                    }
                    try {
                        Thread.sleep(4000L);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        }, "VeliPresenceBeacon").start();
    }

    private void startUdpListener() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    udpSocket = new DatagramSocket(null);
                    udpSocket.setReuseAddress(true);
                    udpSocket.bind(new InetSocketAddress(DISCOVERY_UDP_PORT));
                    byte[] buf = new byte[2048];

                    while (running && !udpSocket.isClosed()) {
                        DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                        udpSocket.receive(pkt);
                        String msg = new String(pkt.getData(), 0, pkt.getLength(), "UTF-8").trim();
                        String senderIp = pkt.getAddress().getHostAddress();
                        String myRole = VeliStorage.getParentRole(ParentBackgroundService.this);

                        if (msg.startsWith("CZ_CHILD_HERE|")) {
                            String[] parts = msg.split("\\|");
                            if (parts.length >= 5) {
                                String childId = parts[1];
                                String userName = parts[2];
                                int grade = 4;
                                try {
                                    grade = Integer.parseInt(parts[3]);
                                } catch (Exception ignored) {
                                }
                                String ip = (senderIp != null && !senderIp.isEmpty()) ? senderIp : parts[4];
                                VeliStorage.updateExistingChildNetworkInfo(
                                        ParentBackgroundService.this, childId, userName, grade, ip);
                            }
                        } else if (msg.startsWith("CZ_PARENT_HERE|")) {
                            String[] parts = msg.split("\\|");
                            if (parts.length >= 3) {
                                String role = parts[1];
                                String ip = (senderIp != null && !senderIp.isEmpty()) ? senderIp : parts[2];
                                if (!myRole.equalsIgnoreCase(role)) {
                                    VeliStorage.updateOtherParentSeen(ParentBackgroundService.this, ip);
                                }
                            }
                        } else if (msg.startsWith("CZ_PARENT_MSG|")) {
                            // Format: CZ_PARENT_MSG|<fromRole>|<messageText>
                            String[] parts = msg.split("\\|", 3);
                            if (parts.length >= 3) {
                                String fromRole = parts[1];
                                String text = parts[2];
                                if (!myRole.equalsIgnoreCase(fromRole)) {
                                    handleIncomingParentMessage(fromRole, text);
                                }
                            }
                        } else if (msg.startsWith("CZ_CALL_FROM_CHILD|")) {
                            String[] parts = msg.split("\\|");
                            if (parts.length >= 5) {
                                String targetRole = parts[1];
                                String childId = parts[2];
                                String childName = parts[3];
                                String childIp = (senderIp != null && !senderIp.isEmpty()) ? senderIp : parts[4];
                                VeliStorage.updateExistingChildNetworkInfo(
                                        ParentBackgroundService.this, childId, childName, 4, childIp);

                                if (myRole.equalsIgnoreCase(targetRole)) {
                                    handleIncomingChildCall(childId, childName, childIp);
                                }
                            }
                        } else if (msg.startsWith("CZ_CANCEL_CALL|")) {
                            // Öğrenci çağrıyı iptal etti!
                            cancelIncomingOrActiveCall();
                        } else if (msg.startsWith("CZ_BREAK_PHONE_REQ|")) {
                            String[] parts = msg.split("\\|");
                            if (parts.length >= 3) {
                                String cid = parts[1];
                                String cname = parts[2];
                                String cip = (senderIp != null && !senderIp.isEmpty())
                                        ? senderIp : (parts.length >= 4 ? parts[3] : "");
                                handleIncomingBreakPhoneRequest(cid, cname, cip);
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }, "VeliUdpListener").start();
    }

    private void startHttpServer() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    httpServer = new ServerSocket();
                    httpServer.setReuseAddress(true);
                    httpServer.bind(new InetSocketAddress(PARENT_HTTP_PORT));
                    while (running && !httpServer.isClosed()) {
                        final Socket client = httpServer.accept();
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                handleHttpClient(client);
                            }
                        }).start();
                    }
                } catch (Exception ignored) {
                }
            }
        }, "VeliHttpServer").start();
    }

    private void handleHttpClient(Socket client) {
        try {
            client.setSoTimeout(4000);
            client.setTcpNoDelay(true);
            String remoteIp = client.getInetAddress().getHostAddress();
            InputStream in = client.getInputStream();

            ByteArrayOutputStream headerBuf = new ByteArrayOutputStream();
            int b;
            int state = 0;
            while ((b = in.read()) != -1) {
                headerBuf.write(b);
                if ((state == 0 || state == 2) && b == 13) {
                    state++;
                } else if ((state == 1 || state == 3) && b == 10) {
                    state++;
                    if (state == 4) break;
                } else {
                    state = 0;
                }
                if (headerBuf.size() > 8192) break;
            }

            String headerStr = new String(headerBuf.toByteArray(), "UTF-8");
            String[] lines = headerStr.split("\r\n");
            if (lines.length == 0 || lines[0].isEmpty()) {
                client.close();
                return;
            }
            String[] reqParts = lines[0].split(" ");
            String fullUri = reqParts.length > 1 ? reqParts[1] : "/";
            Uri parsedUri = Uri.parse("http://localhost" + fullUri);
            String path = parsedUri.getPath() != null ? parsedUri.getPath() : "/";

            int contentLength = 0;
            for (int i = 1; i < lines.length; i++) {
                String h = lines[i];
                if (h.toLowerCase(Locale.US).startsWith("content-length:")) {
                    try {
                        contentLength = Integer.parseInt(h.substring(15).trim());
                    } catch (Exception ignored) {
                    }
                }
            }

            String body = "";
            if (contentLength > 0 && contentLength < 131072) {
                byte[] bodyBytes = new byte[contentLength];
                int totalRead = 0;
                while (totalRead < contentLength) {
                    int r = in.read(bodyBytes, totalRead, contentLength - totalRead);
                    if (r == -1) break;
                    totalRead += r;
                }
                body = new String(bodyBytes, 0, totalRead, "UTF-8");
            }

            int statusCode = 200;
            JSONObject resp = new JSONObject();
            resp.put("ok", true);
            String myRole = VeliStorage.getParentRole(this);

            if (path.startsWith("/parent_status")) {
                resp.put("parentRole", myRole);
                resp.put("online", true);
            } else if (path.startsWith("/parent_message")) {
                String fromRole = parsedUri.getQueryParameter("fromRole");
                String text = parsedUri.getQueryParameter("text");
                if (text != null) {
                    try {
                        text = URLDecoder.decode(text, "UTF-8");
                    } catch (Exception ignored) {
                    }
                }
                if (remoteIp != null && !remoteIp.isEmpty() && fromRole != null && !myRole.equalsIgnoreCase(fromRole)) {
                    VeliStorage.updateOtherParentSeen(this, remoteIp);
                }
                handleIncomingParentMessage(fromRole != null ? fromRole : "ANNE", text != null ? text : "");
            } else if (path.startsWith("/incoming_call_from_child")) {
                String targetRole = parsedUri.getQueryParameter("role");
                String childId = parsedUri.getQueryParameter("childId");
                String childName = parsedUri.getQueryParameter("childName");
                String childIp = parsedUri.getQueryParameter("childIp");
                if ((childName == null || childName.isEmpty()) && !body.isEmpty()) {
                    JSONObject req = new JSONObject(body);
                    targetRole = req.optString("targetRole", "");
                    childId = req.optString("childId", "");
                    childName = req.optString("childName", "Öğrenci");
                    childIp = req.optString("childIp", remoteIp);
                }
                // Hedef rol bu velinin rolü değilse kesinlikle çaldırma!
                if (targetRole != null && !targetRole.isEmpty() && !myRole.equalsIgnoreCase(targetRole)) {
                    statusCode = 409;
                    resp.put("ok", false);
                    resp.put("actualRole", myRole);
                } else {
                    if (childId == null) childId = "";
                    if (childName == null || childName.isEmpty()) childName = "Öğrenci";
                    if (childIp == null || childIp.isEmpty()) childIp = remoteIp;

                    if (!childId.isEmpty()) {
                        VeliStorage.updateExistingChildNetworkInfo(this, childId, childName, 4, childIp);
                    }
                    handleIncomingChildCall(childId, childName, childIp);
                }
            } else if (path.startsWith("/request_break_phone")) {
                String cid = parsedUri.getQueryParameter("childId");
                String cname = parsedUri.getQueryParameter("childName");
                String cip = parsedUri.getQueryParameter("childIp");
                if (cname != null) {
                    try {
                        cname = URLDecoder.decode(cname, "UTF-8");
                    } catch (Exception ignored) {
                    }
                }
                if (cip == null || cip.isEmpty()) cip = remoteIp;
                handleIncomingBreakPhoneRequest(cid != null ? cid : "", cname != null ? cname : "Öğrenci", cip);
            } else if (path.startsWith("/end_call")) {
                cancelIncomingOrActiveCall();
            } else if (path.startsWith("/save_child_backup")) {
                if (!body.isEmpty()) {
                    VeliBackupManager.saveMirroredChildBackup(this, body);
                }
            } else if (path.startsWith("/get_child_backup")) {
                String mirrored = VeliBackupManager.getMirroredChildBackup(this);
                if (mirrored != null && !mirrored.isEmpty()) {
                    resp = new JSONObject(mirrored);
                }
            }

            byte[] respBytes = resp.toString().getBytes("UTF-8");
            OutputStream out = client.getOutputStream();
            String statusText = (statusCode == 200) ? "200 OK" : "409 Conflict";
            String headers = "HTTP/1.1 " + statusText + "\r\n"
                    + "Content-Type: application/json; charset=utf-8\r\n"
                    + "Content-Length: " + respBytes.length + "\r\n"
                    + "Connection: close\r\n\r\n";
            out.write(headers.getBytes("UTF-8"));
            out.write(respBytes);
            out.flush();
            try {
                client.shutdownOutput();
            } catch (Exception ignored) {
            }
            client.close();
        } catch (Exception ignored) {
            try {
                client.close();
            } catch (Exception ignored2) {
            }
        }
    }

    /**
     * Diğer veliden (Anne <-> Baba) gelen özelleştirilmiş mesajı sesli anons eder ve ekranda gösterir.
     */
    private synchronized void handleIncomingParentMessage(final String fromRole, final String text) {
        if (text == null || text.trim().isEmpty()) return;
        long now = System.currentTimeMillis();
        if (now - lastParentMsgTimeMs < 1500L) return;
        lastParentMsgTimeMs = now;

        final String cleanMsg = text.trim();
        VeliStorage.saveLastReceivedParentMessage(this, cleanMsg);
        // Ön ek olmadan doğrudan özelleştirilmiş mesajı okur ve bitince "tekrar dinlemek için uygulamaya dön" der
        final String fullSpeech = VeliMainActivity.isAppInForeground()
                ? cleanMsg
                : (cleanMsg + ". Tekrar dinlemek için uygulamaya dön");

        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                wakeScreenBriefly();
                vibrateAndChime();
                speakNow(fullSpeech);
                Toast.makeText(ParentBackgroundService.this, "💬 Gelen Mesaj: " + cleanMsg, Toast.LENGTH_LONG).show();
                VeliMainActivity.refreshLastMessageOnScreenIfVisible();
            }
        });
    }

    private synchronized void handleIncomingChildCall(final String childId, final String childName, final String childIp) {
        long now = System.currentTimeMillis();
        if (now - lastIncomingAlertTimeMs < 3000L) {
            return;
        }
        lastIncomingAlertTimeMs = now;

        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                wakeScreenBriefly();
                vibrateAndChime();
                String speech = childName + " çağrı gönderiyor. Yanıtlamak için uygulamadan onay ver";
                speakNow(speech);

                Intent callIntent = new Intent(ParentBackgroundService.this, VeliCallActivity.class);
                callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                callIntent.putExtra("EXTRA_CHILD_ID", childId);
                callIntent.putExtra("EXTRA_CHILD_NAME", childName);
                callIntent.putExtra("EXTRA_CHILD_IP", childIp);
                callIntent.putExtra("EXTRA_INCOMING_APPROVAL", true);

                showIncomingCallHighPriorityNotification(childName, callIntent);

                try {
                    startActivity(callIntent);
                } catch (Exception ignored) {
                }
            }
        });
    }

    /**
     * Öğrenci aramayı iptal ettiğinde titreşimi, konuşmayı, bildirimi ve çağrı ekranını anında kapatır.
     */
    public void cancelIncomingOrActiveCall() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    Vibrator vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                    if (vib != null) vib.cancel();
                } catch (Exception ignored) {
                }
                try {
                    if (tts != null) tts.stop();
                } catch (Exception ignored) {
                }
                try {
                    NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    if (nm != null) nm.cancel(CALL_ALERT_NOTIF_ID);
                } catch (Exception ignored) {
                }
                VeliVoiceCallManager.stopCall(ParentBackgroundService.this);
                VeliCallActivity.notifyCallEndedRemotely();
            }
        });
    }

    private void showIncomingCallHighPriorityNotification(String childName, Intent callIntent) {
        try {
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }
            PendingIntent fullScreenPi = PendingIntent.getActivity(this, 99, callIntent, flags);

            Notification.Builder b = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    ? new Notification.Builder(this, CALL_ALERT_CHANNEL_ID)
                    : new Notification.Builder(this);

            b.setContentTitle("📞 " + childName + " Çağrı Gönderiyor!")
                    .setContentText("Yanıtlamak ve görüşmeyi başlatmak için dokunun.")
                    .setSmallIcon(android.R.drawable.sym_action_call)
                    .setContentIntent(fullScreenPi)
                    .setFullScreenIntent(fullScreenPi, true)
                    .setAutoCancel(true);

            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.notify(CALL_ALERT_NOTIF_ID, b.build());
            }
        } catch (Exception ignored) {
        }
    }

    private void vibrateAndChime() {
        try {
            Vibrator vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vib != null && vib.hasVibrator()) {
                vib.vibrate(new long[]{0, 450, 200, 450, 200, 600}, -1);
            }
            ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_ALARM, 95);
            tg.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 450);
        } catch (Exception ignored) {
        }
    }

    public void speakNow(final String phrase) {
        if (!ttsReady || tts == null) {
            pendingSpeech = phrase;
            return;
        }
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                am.requestAudioFocus(null, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Bundle params = new Bundle();
                params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ALARM);
                tts.speak(phrase, TextToSpeech.QUEUE_FLUSH, params, "veli_utt_" + System.currentTimeMillis());
            } else {
                java.util.HashMap<String, String> map = new java.util.HashMap<>();
                map.put(TextToSpeech.Engine.KEY_PARAM_STREAM, String.valueOf(AudioManager.STREAM_ALARM));
                tts.speak(phrase, TextToSpeech.QUEUE_FLUSH, map);
            }
        } catch (Exception ignored) {
        }
    }

    public static void scanLanAndSyncChildren(final Context context, final boolean pairAllFound, final Runnable onComplete) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String myIp = getLocalIpAddress(context);
                    String myRole = VeliStorage.getParentRole(context);

                    try {
                        String disc = "CZ_DISCOVER|" + myRole + "|" + myIp;
                        byte[] data = disc.getBytes("UTF-8");
                        DatagramSocket ds = new DatagramSocket();
                        ds.setBroadcast(true);
                        DatagramPacket pkt = new DatagramPacket(
                                data, data.length, InetAddress.getByName("255.255.255.255"), DISCOVERY_UDP_PORT);
                        ds.send(pkt);
                        ds.close();
                    } catch (Exception ignored) {
                    }

                    int lastDot = myIp.lastIndexOf('.');
                    if (lastDot > 0 && !myIp.equals("127.0.0.1")) {
                        final String prefix = myIp.substring(0, lastDot + 1);
                        ExecutorService pool = Executors.newFixedThreadPool(32);
                        for (int i = 1; i <= 254; i++) {
                            final String candidateIp = prefix + i;
                            pool.submit(new Runnable() {
                                @Override
                                public void run() {
                                    probeCandidateIp(context, candidateIp, pairAllFound);
                                    probeOtherParentIp(context, candidateIp);
                                }
                            });
                        }
                        pool.shutdown();
                        pool.awaitTermination(3500L, TimeUnit.MILLISECONDS);
                    }
                } catch (Exception ignored) {
                }
                if (onComplete != null) {
                    new Handler(Looper.getMainLooper()).post(onComplete);
                }
            }
        }, "LanSubnetScanner").start();
    }

    private static void probeOtherParentIp(Context context, String ip) {
        try {
            Socket sock = new Socket();
            sock.connect(new InetSocketAddress(ip, PARENT_HTTP_PORT), 280);
            sock.close();

            URL url = new URL("http://" + ip + ":" + PARENT_HTTP_PORT + "/parent_status");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Connection", "close");
            conn.setConnectTimeout(800);
            conn.setReadTimeout(800);
            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();
                JSONObject obj = new JSONObject(sb.toString());
                String foundRole = obj.optString("parentRole", "");
                String myRole = VeliStorage.getParentRole(context);
                if (!foundRole.isEmpty() && !myRole.equalsIgnoreCase(foundRole)) {
                    VeliStorage.updateOtherParentSeen(context, ip);
                }
            }
            conn.disconnect();
        } catch (Exception ignored) {
        }
    }

    private static void probeCandidateIp(Context context, String ip, boolean pairAllFound) {
        try {
            Socket sock = new Socket();
            sock.connect(new InetSocketAddress(ip, STUDENT_HTTP_PORT), 320);
            sock.close();

            URL url = new URL("http://" + ip + ":" + STUDENT_HTTP_PORT + "/status");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(900);
            conn.setReadTimeout(900);
            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();

                JSONObject json = new JSONObject(sb.toString());
                String childId = json.optString("childId", "");
                String userName = json.optString("userName", "Öğrenci");
                int grade = json.optInt("gradeLevel", 4);

                if (!childId.isEmpty()) {
                    if (pairAllFound) {
                        VeliStorage.upsertPairedChild(
                                context, childId, userName, grade, ip, VeliStorage.getParentRole(context));
                        registerParentWithChild(context, ip);
                    } else {
                        boolean existed = VeliStorage.updateExistingChildNetworkInfo(
                                context, childId, userName, grade, ip);
                        if (existed) {
                            registerParentWithChild(context, ip);
                            fetchAndCacheChildFullReport(context, childId, ip);
                        }
                    }
                }
            }
            conn.disconnect();
        } catch (Exception ignored) {
        }
    }

    public static void fetchAndCacheChildFullReport(final Context context, final String childId, final String childIp) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL("http://" + childIp + ":" + STUDENT_HTTP_PORT + "/full_report");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("Connection", "close");
                    conn.setConnectTimeout(1500);
                    conn.setReadTimeout(1500);
                    if (conn.getResponseCode() == 200) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                        }
                        reader.close();
                        VeliStorage.saveCachedChildReport(context, childId, sb.toString());
                    }
                    conn.disconnect();
                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    public static void registerParentWithChild(final Context context, final String childIp) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String role = VeliStorage.getParentRole(context);
                    String myIp = getLocalIpAddress(context);
                    URL url = new URL("http://" + childIp + ":" + STUDENT_HTTP_PORT + "/pair?role=" + role + "&ip=" + myIp);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("Connection", "close");
                    conn.setConnectTimeout(1500);
                    conn.setReadTimeout(1500);
                    if (conn.getResponseCode() == 200) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                        }
                        reader.close();
                        JSONObject resp = new JSONObject(sb.toString());
                        String childId = resp.optString("childId", "");
                        String userName = resp.optString("userName", "");
                        int grade = resp.optInt("gradeLevel", 4);
                        if (!childId.isEmpty()) {
                            VeliStorage.upsertPairedChild(
                                    context, childId, userName, grade, childIp, role);
                        }
                    }
                    conn.disconnect();
                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm == null) return;

            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    "Veli Arka Plan Servisi",
                    NotificationManager.IMPORTANCE_LOW
            );
            nm.createNotificationChannel(ch);

            NotificationChannel callCh = new NotificationChannel(
                    CALL_ALERT_CHANNEL_ID,
                    "Çocuktan Gelen Çağrı Uyarıları",
                    NotificationManager.IMPORTANCE_HIGH
            );
            callCh.setDescription("Çocuk çağrı gönderdiğinde arka planda sesli ve ekran üstü uyarı verir");
            nm.createNotificationChannel(callCh);

            NotificationChannel silentReqCh = new NotificationChannel(
                    SILENT_BREAK_REQ_CHANNEL_ID,
                    "Mola Telefon Kullanım İzin İstekleri (Sessiz Bildirim)",
                    NotificationManager.IMPORTANCE_LOW
            );
            silentReqCh.setDescription("Öğrenci molada bir seferlik telefonun diğer özelliklerini kullanmak için izin istediğinde sessiz bildirim gösterir");
            silentReqCh.setSound(null, null);
            silentReqCh.enableVibration(false);
            nm.createNotificationChannel(silentReqCh);
        }
    }

    private Notification buildNotification(String text) {
        Intent i = new Intent(this, VeliMainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pi = PendingIntent.getActivity(this, 0, i, flags);
        Notification.Builder b = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        b.setContentTitle("Çalışma Zamanlayıcı Veli")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_myplaces)
                .setContentIntent(pi)
                .setOngoing(true);
        return b.build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        running = false;
        try {
            if (httpServer != null) httpServer.close();
        } catch (Exception ignored) {
        }
        try {
            if (udpSocket != null) udpSocket.close();
        } catch (Exception ignored) {
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (wifiLock != null && wifiLock.isHeld()) {
            wifiLock.release();
        }
        if (multicastLock != null && multicastLock.isHeld()) {
            multicastLock.release();
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        super.onDestroy();
    }

    private long lastBreakReqTimeMs = 0L;

    /**
     * Öğrenci molada "Telefonun diğer özelliklerini kullanmak için izin iste" butonuna bastığında
     * Veli telefonuna SESSİZ BİLDİRİM düşürür ve Veli uygulaması açıksa onay penceresini gösterir.
     */
    private synchronized void handleIncomingBreakPhoneRequest(final String childId, final String childName, final String childIp) {
        long now = System.currentTimeMillis();
        if (now - lastBreakReqTimeMs < 2000L) return;
        lastBreakReqTimeMs = now;

        if (!childId.isEmpty() && childIp != null && !childIp.isEmpty()) {
            VeliStorage.updateExistingChildNetworkInfo(this, childId, childName, 4, childIp);
        }
        VeliStorage.savePendingBreakPhoneRequest(this, childId, childName, childIp);

        final String promptText = childName + " molada bir seferlik Telefonun Diğer özelliklerini kullanmak istiyor izin veriyor musun?";

        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                showSilentBreakPermissionNotification(childId, childName, childIp, promptText);
                ChildDetailActivity.notifyBreakPermissionRequestIfVisible(childId, childName, childIp, promptText);
                VeliMainActivity.notifyBreakPermissionRequestIfVisible(childId, childName, childIp, promptText);
            }
        });
    }

    private void showSilentBreakPermissionNotification(String childId, String childName, String childIp, String promptText) {
        try {
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }

            Intent openDetail = new Intent(this, ChildDetailActivity.class);
            openDetail.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            openDetail.putExtra("EXTRA_CHILD_ID", childId);
            openDetail.putExtra("EXTRA_CHILD_NAME", childName);
            openDetail.putExtra("EXTRA_CHILD_IP", childIp);
            openDetail.putExtra("EXTRA_SHOW_BREAK_PERMISSION_PROMPT", true);
            PendingIntent contentPi = PendingIntent.getActivity(this, 201, openDetail, flags);

            Intent approveIntent = new Intent(this, ParentBackgroundService.class);
            approveIntent.setAction(ACTION_APPROVE_BREAK_PHONE);
            approveIntent.putExtra("EXTRA_CHILD_ID", childId);
            approveIntent.putExtra("EXTRA_CHILD_IP", childIp);
            PendingIntent approvePi = PendingIntent.getService(this, 202, approveIntent, flags);

            Intent denyIntent = new Intent(this, ParentBackgroundService.class);
            denyIntent.setAction(ACTION_DENY_BREAK_PHONE);
            denyIntent.putExtra("EXTRA_CHILD_ID", childId);
            denyIntent.putExtra("EXTRA_CHILD_IP", childIp);
            PendingIntent denyPi = PendingIntent.getService(this, 203, denyIntent, flags);

            Notification.Builder b = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    ? new Notification.Builder(this, SILENT_BREAK_REQ_CHANNEL_ID)
                    : new Notification.Builder(this);

            b.setContentTitle("📱 " + childName + " Telefon Kullanım İzni İstiyor")
                    .setContentText(promptText)
                    .setStyle(new Notification.BigTextStyle().bigText(promptText))
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentIntent(contentPi)
                    .setAutoCancel(true)
                    .addAction(android.R.drawable.ic_input_add, "Evet, İzin Ver", approvePi)
                    .addAction(android.R.drawable.ic_delete, "Hayır", denyPi);

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                b.setDefaults(0);
                b.setSound(null);
                b.setVibrate(null);
            }

            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.notify(BREAK_REQ_NOTIF_ID, b.build());
            }
        } catch (Exception ignored) {
        }
    }

    public static void sendBreakPhoneDecisionToChild(final Context context, final String childId, final String childIp, final boolean approve) {
        VeliStorage.clearPendingBreakPhoneRequest(context);
        try {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(BREAK_REQ_NOTIF_ID);
        } catch (Exception ignored) {
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                String targetIp = childIp;
                if ((targetIp == null || targetIp.isEmpty()) && childId != null) {
                    for (VeliStorage.PairedChild c : VeliStorage.getPairedChildren(context)) {
                        if (c.childId.equalsIgnoreCase(childId) && c.ipAddress != null && !c.ipAddress.isEmpty()) {
                            targetIp = c.ipAddress;
                            break;
                        }
                    }
                }

                boolean sentOk = false;
                String udpCmd = approve ? ("CZ_BREAK_PHONE_APPROVED|" + childId) : ("CZ_BREAK_PHONE_DENIED|" + childId);
                try {
                    byte[] data = udpCmd.getBytes("UTF-8");
                    DatagramSocket ds = new DatagramSocket();
                    ds.setBroadcast(true);
                    if (targetIp != null && !targetIp.isEmpty()) {
                        ds.send(new DatagramPacket(data, data.length,
                                InetAddress.getByName(targetIp), DISCOVERY_UDP_PORT));
                        sentOk = true;
                    }
                    ds.send(new DatagramPacket(data, data.length,
                            InetAddress.getByName("255.255.255.255"), DISCOVERY_UDP_PORT));
                    ds.close();
                } catch (Exception ignored) {
                }

                if (targetIp != null && !targetIp.isEmpty()) {
                    try {
                        URL url = new URL("http://" + targetIp + ":" + STUDENT_HTTP_PORT
                                + "/approve_break_phone?approved=" + (approve ? "1" : "0"));
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("GET");
                        conn.setRequestProperty("Connection", "close");
                        conn.setConnectTimeout(1500);
                        conn.setReadTimeout(1500);
                        if (conn.getResponseCode() == 200) {
                            sentOk = true;
                        }
                        conn.disconnect();
                    } catch (Exception ignored) {
                    }
                }

                final boolean ok = sentOk;
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        String msg = approve
                                ? "✅ Öğrenciye molada bir seferlik telefon kullanım izni verildi!"
                                : "❌ Öğrencinin molada telefon kullanım isteği reddedildi.";
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }

    public static void pushAlwaysAllowPolicyToChild(final Context context, final String childId, final String childIp, final boolean allow) {
        VeliStorage.setChildAllowedOtherPhoneFeatures(context, childId, allow);
        new Thread(new Runnable() {
            @Override
            public void run() {
                String targetIp = childIp;
                if ((targetIp == null || targetIp.isEmpty()) && childId != null) {
                    for (VeliStorage.PairedChild c : VeliStorage.getPairedChildren(context)) {
                        if (c.childId.equalsIgnoreCase(childId) && c.ipAddress != null && !c.ipAddress.isEmpty()) {
                            targetIp = c.ipAddress;
                            break;
                        }
                    }
                }

                String udpCmd = "CZ_SET_ALLOW_OTHER_FEATURES|" + (allow ? "1" : "0");
                try {
                    byte[] data = udpCmd.getBytes("UTF-8");
                    DatagramSocket ds = new DatagramSocket();
                    ds.setBroadcast(true);
                    if (targetIp != null && !targetIp.isEmpty()) {
                        ds.send(new DatagramPacket(data, data.length,
                                InetAddress.getByName(targetIp), DISCOVERY_UDP_PORT));
                    }
                    ds.send(new DatagramPacket(data, data.length,
                            InetAddress.getByName("255.255.255.255"), DISCOVERY_UDP_PORT));
                    ds.close();
                } catch (Exception ignored) {
                }

                if (targetIp != null && !targetIp.isEmpty()) {
                    try {
                        URL url = new URL("http://" + targetIp + ":" + STUDENT_HTTP_PORT
                                + "/set_phone_feature_policy?allow=" + (allow ? "1" : "0"));
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("GET");
                        conn.setRequestProperty("Connection", "close");
                        conn.setConnectTimeout(1500);
                        conn.setReadTimeout(1500);
                        conn.getResponseCode();
                        conn.disconnect();
                    } catch (Exception ignored) {
                    }
                }
            }
        }).start();
    }

}
