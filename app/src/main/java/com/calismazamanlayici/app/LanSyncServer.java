package com.calismazamanlayici.app;

import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Enumeration;
import java.util.List;

/**
 * Öğrenci cihazında arka planda çalışan yerel ağ (LAN) sunucusu.
 * - Port 45677 (UDP): IP adresi değişse bile Veli uygulamasının öğrenci cihazını otomatik bulmasını sağlar.
 * - Port 45678 (HTTP/TCP): Veli uygulamasına anlık çalışma durumu, takvim geçmişi, başarı rozetleri sunar;
 *   uzaktan övgü ("Annenden/Babandan övgü aldın tebrikler") ve doğrudan/onaylı sesli çağrı komutlarını işler.
 */
public class LanSyncServer {

    public static final int DISCOVERY_UDP_PORT = 45677;
    public static final int STUDENT_HTTP_PORT = 45678;
    public static final int PARENT_HTTP_PORT = 45679;

    private final TimerForegroundService service;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean running = false;
    private ServerSocket httpServerSocket;
    private DatagramSocket udpSocket;

    public LanSyncServer(TimerForegroundService service) {
        this.service = service;
    }

    public void start() {
        if (running) return;
        running = true;
        startUdpDiscoveryResponder();
        startUdpPeriodicBeacon();
        startHttpServer();
    }

    public void stop() {
        running = false;
        try {
            if (httpServerSocket != null) httpServerSocket.close();
        } catch (Exception ignored) {
        }
        try {
            if (udpSocket != null) udpSocket.close();
        } catch (Exception ignored) {
        }
    }

    public static String getLocalIpAddress(Context context) {
        try {
            WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                WifiInfo wi = wm.getConnectionInfo();
                int ipInt = wi != null ? wi.getIpAddress() : 0;
                if (ipInt != 0) {
                    return String.format(java.util.Locale.US, "%d.%d.%d.%d",
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

    private String buildBeaconPayload() {
        String childId = StudyDatabaseHelper.getChildDeviceId(service);
        String userName = StudyDatabaseHelper.getUserName(service);
        int grade = StudyDatabaseHelper.getGradeLevel(service);
        String ip = getLocalIpAddress(service);
        return "CZ_CHILD_HERE|" + childId + "|" + userName + "|" + grade + "|" + ip + "|" + STUDENT_HTTP_PORT;
    }

    private void startUdpDiscoveryResponder() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    udpSocket = new DatagramSocket(DISCOVERY_UDP_PORT);
                    udpSocket.setBroadcast(true);
                    byte[] buf = new byte[1024];
                    while (running && !udpSocket.isClosed()) {
                        DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                        udpSocket.receive(pkt);
                        String msg = new String(pkt.getData(), 0, pkt.getLength(), "UTF-8").trim();
                        String senderIp = pkt.getAddress().getHostAddress();

                        if (msg.startsWith("CZ_DISCOVER|")) {
                            // Format: CZ_DISCOVER|<parentRole>|<parentIp>
                            String[] parts = msg.split("\\|");
                            if (parts.length >= 2) {
                                String role = parts[1];
                                if (senderIp != null && !senderIp.isEmpty()) {
                                    StudyDatabaseHelper.setPairedParentIp(service, role, senderIp);
                                }
                            }
                            byte[] replyBytes = buildBeaconPayload().getBytes("UTF-8");
                            DatagramPacket reply = new DatagramPacket(
                                    replyBytes, replyBytes.length, pkt.getAddress(), DISCOVERY_UDP_PORT);
                            udpSocket.send(reply);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }, "StudentUdpResponder").start();
    }

    private void startUdpPeriodicBeacon() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (running) {
                    try {
                        String payload = buildBeaconPayload();
                        byte[] data = payload.getBytes("UTF-8");
                        DatagramSocket ds = new DatagramSocket();
                        ds.setBroadcast(true);
                        DatagramPacket pkt = new DatagramPacket(
                                data, data.length, InetAddress.getByName("255.255.255.255"), DISCOVERY_UDP_PORT);
                        ds.send(pkt);
                        ds.close();
                    } catch (Exception ignored) {
                    }
                    try {
                        Thread.sleep(5000L);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        }, "StudentUdpBeacon").start();
    }

    private void startHttpServer() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    httpServerSocket = new ServerSocket(STUDENT_HTTP_PORT);
                    while (running && !httpServerSocket.isClosed()) {
                        final Socket client = httpServerSocket.accept();
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
        }, "StudentHttpServer").start();
    }

    private void handleHttpClient(Socket client) {
        try {
            client.setSoTimeout(5000);
            String remoteIp = client.getInetAddress().getHostAddress();
            BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), "UTF-8"));
            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isEmpty()) {
                client.close();
                return;
            }

            String[] reqParts = requestLine.split(" ");
            String path = reqParts.length > 1 ? reqParts[1] : "/";

            int contentLength = 0;
            String headerLine;
            while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
                if (headerLine.toLowerCase(java.util.Locale.US).startsWith("content-length:")) {
                    try {
                        contentLength = Integer.parseInt(headerLine.substring(15).trim());
                    } catch (Exception ignored) {
                    }
                }
            }

            String body = "";
            if (contentLength > 0) {
                char[] bodyChars = new char[contentLength];
                int totalRead = 0;
                while (totalRead < contentLength) {
                    int r = reader.read(bodyChars, totalRead, contentLength - totalRead);
                    if (r == -1) break;
                    totalRead += r;
                }
                body = new String(bodyChars, 0, totalRead);
            }

            JSONObject responseJson;
            if (path.startsWith("/ping") || path.startsWith("/status")) {
                responseJson = buildStatusJson();
            } else if (path.startsWith("/full_report")) {
                responseJson = buildFullReportJson();
            } else if (path.startsWith("/pair")) {
                JSONObject req = body.isEmpty() ? new JSONObject() : new JSONObject(body);
                String role = req.optString("parentRole", "ANNE");
                String parentIp = req.optString("parentIp", remoteIp);
                if (parentIp == null || parentIp.isEmpty()) parentIp = remoteIp;
                StudyDatabaseHelper.setPairedParentIp(service, role, parentIp);
                responseJson = buildStatusJson();
                responseJson.put("paired", true);
            } else if (path.startsWith("/praise")) {
                JSONObject req = body.isEmpty() ? new JSONObject() : new JSONObject(body);
                final String role = req.optString("parentRole", "ANNE");
                final String customPraise = req.optString("praiseText", "");
                final String speech;
                if (!customPraise.isEmpty()) {
                    speech = customPraise;
                } else if ("BABA".equalsIgnoreCase(role)) {
                    speech = "Babandan övgü aldın tebrikler! Aferin sana!";
                } else {
                    speech = "Annenden övgü aldın tebrikler! Aferin sana!";
                }
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        service.announce(speech, true);
                        Toast.makeText(service, "🎉 " + speech, Toast.LENGTH_LONG).show();
                    }
                });
                responseJson = new JSONObject();
                responseJson.put("ok", true);
            } else if (path.startsWith("/direct_call_from_parent")) {
                // Veli uygulamasında "Ara" butonuna basıldığı anda onaya gerek kalmadan görüşmeyi başlatır!
                JSONObject req = body.isEmpty() ? new JSONObject() : new JSONObject(body);
                final String role = req.optString("parentRole", "ANNE");
                String pIp = req.optString("parentIp", remoteIp);
                if (pIp == null || pIp.isEmpty()) pIp = remoteIp;
                final String finalParentIp = pIp;
                StudyDatabaseHelper.setPairedParentIp(service, role, finalParentIp);

                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        service.wakeScreenBriefly();
                        VoiceCallManager.startCall(service, finalParentIp);
                        service.updateForegroundNotification();
                        Intent callIntent = new Intent(service, CallActivity.class);
                        callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        callIntent.putExtra("EXTRA_PARENT_ROLE", role);
                        callIntent.putExtra("EXTRA_PARENT_IP", finalParentIp);
                        callIntent.putExtra("EXTRA_DIRECT_CONNECTED", true);
                        service.startActivity(callIntent);
                    }
                });
                responseJson = new JSONObject();
                responseJson.put("ok", true);
            } else if (path.startsWith("/parent_accepted_call")) {
                // Çocuk çağrı gönderdiğinde Veli "Onay Ver" dediğinde çağrıyı başlatır!
                JSONObject req = body.isEmpty() ? new JSONObject() : new JSONObject(body);
                String pIp = req.optString("parentIp", remoteIp);
                if (pIp == null || pIp.isEmpty()) pIp = remoteIp;
                final String finalParentIp = pIp;

                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        VoiceCallManager.startCall(service, finalParentIp);
                        CallActivity.notifyParentAccepted();
                    }
                });
                responseJson = new JSONObject();
                responseJson.put("ok", true);
            } else if (path.startsWith("/end_call")) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        VoiceCallManager.stopCall(service);
                        CallActivity.notifyCallEndedRemotely();
                    }
                });
                responseJson = new JSONObject();
                responseJson.put("ok", true);
            } else {
                responseJson = buildStatusJson();
            }

            byte[] respBytes = responseJson.toString().getBytes("UTF-8");
            OutputStream out = client.getOutputStream();
            String headers = "HTTP/1.1 200 OK\r\n"
                    + "Content-Type: application/json; charset=utf-8\r\n"
                    + "Content-Length: " + respBytes.length + "\r\n"
                    + "Connection: close\r\n\r\n";
            out.write(headers.getBytes("UTF-8"));
            out.write(respBytes);
            out.flush();
            client.close();
        } catch (Exception ignored) {
            try {
                client.close();
            } catch (Exception ignored2) {
            }
        }
    }

    private JSONObject buildStatusJson() throws Exception {
        JSONObject obj = new JSONObject();
        StudyDatabaseHelper db = new StudyDatabaseHelper(service);
        String today = StudyDatabaseHelper.getTodayDateString();

        obj.put("childId", StudyDatabaseHelper.getChildDeviceId(service));
        obj.put("userName", StudyDatabaseHelper.getUserName(service));
        obj.put("gradeLevel", StudyDatabaseHelper.getGradeLevel(service));
        obj.put("ip", getLocalIpAddress(service));

        int phase = service.getCurrentPhase();
        obj.put("phase", phase);
        String phaseText;
        if (phase == TimerForegroundService.PHASE_STUDY) {
            phaseText = "📚 Çalışıyor: " + service.getCurrentMode() + " - " + service.getCurrentSubject();
        } else if (phase == TimerForegroundService.PHASE_BREAK) {
            phaseText = "☕ 10 Dakikalık Molada";
        } else if (phase == TimerForegroundService.PHASE_READY_TO_START) {
            phaseText = "⏳ Hazır (Başlat Bekleniyor): " + service.getCurrentSubject();
        } else if (phase == TimerForegroundService.PHASE_POST_BREAK_PROMPT) {
            phaseText = "🔔 Mola Bitti (Devam Onayı Bekleniyor): " + service.getCurrentSubject();
        } else {
            phaseText = "🟢 Çevrimiçi (Beklemede)";
        }
        obj.put("phaseText", phaseText);
        obj.put("mode", service.getCurrentMode());
        obj.put("subject", service.getCurrentSubject());
        obj.put("remainingSeconds", service.getRemainingSeconds());
        obj.put("remainingFormatted", StudyDatabaseHelper.formatClock(service.getRemainingSeconds()));
        obj.put("subjectElapsedSeconds", service.getSubjectElapsedSeconds());
        obj.put("subjectElapsedFormatted", StudyDatabaseHelper.formatDurationTr(service.getSubjectElapsedSeconds()));
        obj.put("todayTotalSeconds", db.getTotalSecondsForDate(today));
        obj.put("todayTotalFormatted", StudyDatabaseHelper.formatDurationTr(db.getTotalSecondsForDate(today)));
        obj.put("todaySessionCount", db.getSessionsForDate(today).size());
        return obj;
    }

    private JSONObject buildFullReportJson() throws Exception {
        JSONObject obj = buildStatusJson();
        StudyDatabaseHelper db = new StudyDatabaseHelper(service);
        StudyDatabaseHelper.WeeklyStats stats = db.getWeeklyStats();

        JSONObject weekly = new JSONObject();
        weekly.put("homeworkCount", stats.totalHomeworkCount);
        weekly.put("testCount", stats.totalTestCount);
        weekly.put("bookCount", stats.totalBookCount);
        weekly.put("homeworkFormatted", StudyDatabaseHelper.formatDurationTr(stats.totalHomeworkSeconds));
        weekly.put("testFormatted", StudyDatabaseHelper.formatDurationTr(stats.totalTestSeconds));
        weekly.put("bookFormatted", StudyDatabaseHelper.formatDurationTr(stats.totalBookSeconds));
        weekly.put("totalFormatted", StudyDatabaseHelper.formatDurationTr(stats.getTotalSeconds()));
        obj.put("weeklyStats", weekly);

        JSONArray badges = new JSONArray();
        badges.put(makeBadge("🌟 İlk Adım Yıldızı", stats.getTotalSessionsCount() >= 1));
        badges.put(makeBadge("🐝 Çalışkan Arı Ödülü", stats.getTotalSessionsCount() >= 3));
        badges.put(makeBadge("📖 Kitap Kurdu Madalyası", stats.totalBookCount >= 1));
        badges.put(makeBadge("📚 Ödev Kahramanı (40+ dk)", stats.totalHomeworkSeconds >= 40 * 60));
        badges.put(makeBadge("🎯 Süper Test Ustası", stats.totalTestCount >= 2));
        badges.put(makeBadge("👑 Haftanın Şampiyon Öğrencisi", stats.distinctSubjectsCount >= 4 || stats.getTotalSeconds() >= 120 * 60));
        obj.put("badges", badges);

        List<StudyDatabaseHelper.SessionRecord> recent = db.getRecentSessions(40);
        JSONArray calArr = new JSONArray();
        for (StudyDatabaseHelper.SessionRecord r : recent) {
            JSONObject item = new JSONObject();
            item.put("date", r.date);
            item.put("time", r.time);
            item.put("mode", r.mode);
            item.put("subject", r.subject);
            item.put("durationFormatted", StudyDatabaseHelper.formatDurationTr(r.durationSeconds));
            calArr.put(item);
        }
        obj.put("calendarRecords", calArr);
        return obj;
    }

    private JSONObject makeBadge(String title, boolean unlocked) throws Exception {
        JSONObject b = new JSONObject();
        b.put("title", title);
        b.put("unlocked", unlocked);
        return b;
    }
}
