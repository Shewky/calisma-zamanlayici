package com.calismazamanlayici.app;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Öğrenci uygulamasının yanlışlıkla kaldırılıp yeniden yüklenmesi durumunda:
 * 1. Verileri her değişiklikte telefonun kalıcı ortak klasörlerine (Download ve Documents)
 *    ve aynı ağdaki Veli cihazına otomatik yedekler.
 * 2. Uygulama yeniden kurulduğunda yedeği bulur ve
 *    "Öğrenci yedeği bulundu (Tarih: ...), yüklemek ister misiniz?" mesajıyla geri yükler.
 */
public class BackupManager {

    private static final String BACKUP_FOLDER = "CalismaZamanlayici_Yedek";
    private static final String STUDENT_BACKUP_FILE = "ogrenci_yedek.json";
    private static final String MIRROR_VELI_BACKUP_FILE = "veli_yedek_ayna.json";

    private static File[] getCandidateBackupFiles() {
        File dlDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), BACKUP_FOLDER);
        File docDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), BACKUP_FOLDER);
        File sdDir = new File(Environment.getExternalStorageDirectory(), BACKUP_FOLDER);
        return new File[]{
                new File(dlDir, STUDENT_BACKUP_FILE),
                new File(docDir, STUDENT_BACKUP_FILE),
                new File(sdDir, STUDENT_BACKUP_FILE)
        };
    }

    public static void saveStudentBackupAsync(final Context context) {
        if (!StudyDatabaseHelper.isUserOnboarded(context)) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    StudyDatabaseHelper db = new StudyDatabaseHelper(context);
                    JSONObject backupJson = db.exportFullBackupJson(context);
                    byte[] bytes = backupJson.toString().getBytes("UTF-8");

                    // 1. Cihazdaki ortak kalıcı klasörlere yaz
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

                    // 2. Eşleşmiş Veli cihazlarına da yedek kopyasını gönder (Çapraz Cihaz Yedeği)
                    for (String role : new String[]{"ANNE", "BABA"}) {
                        String pIp = StudyDatabaseHelper.getPairedParentIp(context, role);
                        if (pIp != null && !pIp.isEmpty()) {
                            pushBackupToParentDevice(pIp, bytes);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    private static void pushBackupToParentDevice(String parentIp, byte[] jsonBytes) {
        try {
            URL url = new URL("http://" + parentIp + ":" + LanSyncServer.PARENT_HTTP_PORT + "/save_child_backup");
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

    /**
     * Veli cihazından gelen Veli yedeğini öğrenci cihazında saklar (Veli uygulaması silinirse geri yüklemek için).
     */
    public static void saveMirroredVeliBackup(Context context, String veliJsonStr) {
        try {
            File internalFile = new File(context.getFilesDir(), MIRROR_VELI_BACKUP_FILE);
            FileOutputStream fos = new FileOutputStream(internalFile);
            fos.write(veliJsonStr.getBytes("UTF-8"));
            fos.flush();
            fos.close();
        } catch (Exception ignored) {
        }
    }

    public static String getMirroredVeliBackup(Context context) {
        try {
            File internalFile = new File(context.getFilesDir(), MIRROR_VELI_BACKUP_FILE);
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

    /**
     * Uygulama ilk kez açıldığında (veya silinip tekrar kurulduğunda) hem yerel depolamayı
     * hem de yerel ağdaki Veli cihazlarını tarayarak öğrenci yedeğini arar.
     */
    public static void checkAndPromptRestoreIfAvailable(final Activity activity, final Runnable onRestoredCallback) {
        if (StudyDatabaseHelper.isUserOnboarded(activity)) return;

        final Handler mainHandler = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override
            public void run() {
                JSONObject foundBackup = readLocalBackupFile();
                if (foundBackup == null) {
                    foundBackup = fetchBackupFromLanParentDevices(activity);
                }
                if (foundBackup != null && foundBackup.has("userName")) {
                    final JSONObject finalBackup = foundBackup;
                    final String backupDate = finalBackup.optString("backupDate", "Bilinmiyor");
                    final String backupUser = finalBackup.optString("userName", "Öğrenci");
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (activity.isFinishing() || StudyDatabaseHelper.isUserOnboarded(activity)) return;
                            new AlertDialog.Builder(activity)
                                    .setTitle("💾 Öğrenci Yedeği Bulundu")
                                    .setMessage("Öğrenci yedeği bulundu, yüklemek ister misiniz?\n\n"
                                            + "👤 Öğrenci: " + backupUser + "\n"
                                            + "📅 Yedek Tarihi: " + backupDate)
                                    .setCancelable(false)
                                    .setPositiveButton("Evet, Yedeği Yükle", (dialog, which) -> {
                                        try {
                                            StudyDatabaseHelper db = new StudyDatabaseHelper(activity);
                                            db.restoreFromBackupJson(activity, finalBackup);
                                            Toast.makeText(activity, "✅ Öğrenci yedeği ve takvim kayıtları başarıyla geri yüklendi!", Toast.LENGTH_LONG).show();
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

    private static JSONObject fetchBackupFromLanParentDevices(Context context) {
        try {
            String myIp = LanSyncServer.getLocalIpAddress(context);
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
                            s.connect(new InetSocketAddress(ip, LanSyncServer.PARENT_HTTP_PORT), 280);
                            s.close();

                            URL url = new URL("http://" + ip + ":" + LanSyncServer.PARENT_HTTP_PORT + "/get_child_backup");
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
                                if (obj.has("userName")) {
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
