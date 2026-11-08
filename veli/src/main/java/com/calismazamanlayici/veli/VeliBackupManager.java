package com.calismazamanlayici.veli;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Veli uygulamasının yanlışlıkla kaldırılıp yeniden yüklenmesi durumunda:
 * 1. Veli rolünü, temasını ve eşleştirilmiş çocuk listesini ortak depolamaya (Download / Documents)
 *    ve ağdaki öğrenci cihazlarına otomatik yedekler.
 * 2. Yeniden kurulduğunda "Veli yedeği bulundu (Tarih: ...), yüklemek ister misiniz?" mesajıyla geri yükler.
 */
public class VeliBackupManager {

    private static final String BACKUP_FOLDER = "CalismaZamanlayici_Yedek";
    private static final String VELI_BACKUP_FILE = "veli_yedek.json";
    private static final String MIRROR_STUDENT_BACKUP_FILE = "ogrenci_yedek_ayna.json";

    private static File[] getCandidateBackupFiles() {
        File dlDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), BACKUP_FOLDER);
        File docDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), BACKUP_FOLDER);
        File sdDir = new File(Environment.getExternalStorageDirectory(), BACKUP_FOLDER);
        return new File[]{
                new File(dlDir, VELI_BACKUP_FILE),
                new File(docDir, VELI_BACKUP_FILE),
                new File(sdDir, VELI_BACKUP_FILE)
        };
    }

    public static void saveVeliBackupAsync(final Context context) {
        if (!VeliStorage.isOnboarded(context)) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject backupJson = VeliStorage.exportFullVeliBackupJson(context);
                    byte[] bytes = backupJson.toString().getBytes("UTF-8");

                    for (File f : getCandidateBackupFiles()) {
                        try {
                            File parent = f.getParentFile();
                            if (parent != null && !parent.exists()) {
                                parent.mkdirs();
                            }
                            FileOutputStream fos = new FileOutputStream(f);
                            fos.write(bytes);
                            fos.flush();
                            fos.close();
                        } catch (Exception ignored) {
                        }
                    }

                    List<VeliStorage.PairedChild> children = VeliStorage.getPairedChildren(context);
                    for (VeliStorage.PairedChild c : children) {
                        if (c.ipAddress != null && !c.ipAddress.isEmpty()) {
                            pushBackupToChildDevice(c.ipAddress, bytes);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    private static void pushBackupToChildDevice(String childIp, byte[] jsonBytes) {
        try {
            URL url = new URL("http://" + childIp + ":" + ParentBackgroundService.STUDENT_HTTP_PORT + "/save_veli_backup");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Connection", "close");
            conn.setConnectTimeout(1500);
            conn.setReadTimeout(1500);
            conn.setDoOutput(true);
            OutputStream os = conn.getOutputStream();
            os.write(jsonBytes);
            os.flush();
            os.close();
            conn.getResponseCode();
            conn.disconnect();
        } catch (Exception ignored) {
        }
    }

    public static void saveMirroredChildBackup(Context context, String childJsonStr) {
        try {
            File internalFile = new File(context.getFilesDir(), MIRROR_STUDENT_BACKUP_FILE);
            FileOutputStream fos = new FileOutputStream(internalFile);
            fos.write(childJsonStr.getBytes("UTF-8"));
            fos.flush();
            fos.close();
        } catch (Exception ignored) {
        }
    }

    public static String getMirroredChildBackup(Context context) {
        try {
            File internalFile = new File(context.getFilesDir(), MIRROR_STUDENT_BACKUP_FILE);
            if (!internalFile.exists()) return "";
            FileInputStream fis = new FileInputStream(internalFile);
            byte[] data = new byte[(int) internalFile.length()];
            fis.read(data);
            fis.close();
            return new String(data, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    public static void checkAndPromptRestoreIfAvailable(final Activity activity, final Runnable onRestoredCallback) {
        if (VeliStorage.isOnboarded(activity)) return;

        final Handler mainHandler = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override
            public void run() {
                JSONObject foundBackup = readLocalBackupFile();
                if (foundBackup == null) {
                    foundBackup = fetchBackupFromLanChildDevices(activity);
                }
                if (foundBackup != null && foundBackup.has("parentRole")) {
                    final JSONObject finalBackup = foundBackup;
                    final String backupDate = finalBackup.optString("backupDate", "Bilinmiyor");
                    final String role = finalBackup.optString("parentRole", "ANNE");
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (activity.isFinishing() || VeliStorage.isOnboarded(activity)) return;
                            new AlertDialog.Builder(activity)
                                    .setTitle("💾 Veli Yedeği Bulundu")
                                    .setMessage("Veli yedeği bulundu, yüklemek ister misiniz?\n\n"
                                            + "👨‍👩‍👧 Veli Rolü: " + ("BABA".equalsIgnoreCase(role) ? "Baba" : "Anne") + "\n"
                                            + "📅 Yedek Tarihi: " + backupDate)
                                    .setCancelable(false)
                                    .setPositiveButton("Evet, Yedeği Yükle", (dialog, which) -> {
                                        try {
                                            VeliStorage.restoreFromVeliBackupJson(activity, finalBackup);
                                            Toast.makeText(activity, "✅ Veli yedeği ve eşleşmeler başarıyla geri yüklendi!", Toast.LENGTH_LONG).show();
                                            if (onRestoredCallback != null) {
                                                onRestoredCallback.run();
                                            }
                                        } catch (Exception e) {
                                            Toast.makeText(activity, "Yedek yüklenirken hata: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                        }
                                    })
                                    .setNegativeButton("Hayır, Yeni Başla", null)
                                    .show();
                        }
                    });
                }
            }
        }).start();
    }

    private static JSONObject readLocalBackupFile() {
        for (File f : getCandidateBackupFiles()) {
            try {
                if (f.exists() && f.length() > 10) {
                    FileInputStream fis = new FileInputStream(f);
                    byte[] data = new byte[(int) f.length()];
                    fis.read(data);
                    fis.close();
                    return new JSONObject(new String(data, "UTF-8"));
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static JSONObject fetchBackupFromLanChildDevices(Context context) {
        try {
            String myIp = ParentBackgroundService.getLocalIpAddress(context);
            int lastDot = myIp.lastIndexOf('.');
            if (lastDot <= 0 || myIp.equals("127.0.0.1")) return null;
            final String prefix = myIp.substring(0, lastDot + 1);
            final AtomicReference<JSONObject> found = new AtomicReference<>(null);
            ExecutorService pool = Executors.newFixedThreadPool(32);
            for (int i = 1; i <= 254; i++) {
                final String ip = prefix + i;
                pool.submit(new Runnable() {
                    @Override
                    public void run() {
                        if (found.get() != null) return;
                        try {
                            Socket s = new Socket();
                            s.connect(new InetSocketAddress(ip, ParentBackgroundService.STUDENT_HTTP_PORT), 280);
                            s.close();

                            URL url = new URL("http://" + ip + ":" + ParentBackgroundService.STUDENT_HTTP_PORT + "/get_veli_backup");
                            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                            conn.setRequestMethod("GET");
                            conn.setRequestProperty("Connection", "close");
                            conn.setConnectTimeout(1000);
                            conn.setReadTimeout(1000);
                            if (conn.getResponseCode() == 200) {
                                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                                StringBuilder sb = new StringBuilder();
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    sb.append(line);
                                }
                                reader.close();
                                JSONObject obj = new JSONObject(sb.toString());
                                if (obj.has("parentRole")) {
                                    found.compareAndSet(null, obj);
                                }
                            }
                            conn.disconnect();
                        } catch (Exception ignored) {
                        }
                    }
                });
            }
            pool.shutdown();
            pool.awaitTermination(2500L, TimeUnit.MILLISECONDS);
            return found.get();
        } catch (Exception e) {
            return null;
        }
    }
}
