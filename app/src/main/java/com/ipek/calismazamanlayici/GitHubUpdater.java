package com.ipek.calismazamanlayici;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * GitHub Releases üzerinden yeni sürümü otomatik denetler, APK dosyasını indirir
 * ve mevcut uygulamanın üzerine (verileri silmeden) güncelleme kurulumunu başlatır.
 */
public class GitHubUpdater {

    public static void checkUpdates(final Activity activity, final boolean showNoUpdateToast) {
        final String repo = StudyDatabaseHelper.getGitHubRepo(activity);
        if (repo == null || repo.isEmpty() || repo.equals("kullanici/calisma-zamanlayici")) {
            if (showNoUpdateToast) {
                showConfigureRepoDialog(activity);
            }
            return;
        }

        final Handler mainHandler = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String apiUrl = "https://api.github.com/repos/" + repo + "/releases/latest";
                    HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                    conn.setRequestProperty("User-Agent", "IpekCalismaZamanlayici-Android");
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);

                    int code = conn.getResponseCode();
                    if (code != 200) {
                        if (showNoUpdateToast) {
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(activity, "GitHub sürüm bilgisi alınamadı (HTTP " + code + "). Repo ayarını kontrol edebilirsiniz.", Toast.LENGTH_LONG).show();
                                    showConfigureRepoDialog(activity);
                                }
                            });
                        }
                        return;
                    }

                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();

                    JSONObject releaseJson = new JSONObject(sb.toString());
                    final String tagName = releaseJson.optString("tag_name", "");
                    final String releaseNotes = releaseJson.optString("body", "Yeni özellikler ve iyileştirmeler.");
                    JSONArray assets = releaseJson.optJSONArray("assets");
                    String foundApkUrl = null;
                    if (assets != null) {
                        for (int i = 0; i < assets.length(); i++) {
                            JSONObject asset = assets.getJSONObject(i);
                            String name = asset.optString("name", "");
                            if (name.endsWith(".apk")) {
                                foundApkUrl = asset.optString("browser_download_url", null);
                                break;
                            }
                        }
                    }

                    final String apkDownloadUrl = foundApkUrl;
                    final String currentVersion = "v" + BuildConfig.VERSION_NAME;

                    if (apkDownloadUrl != null && !tagName.isEmpty() && isNewerVersion(tagName, currentVersion)) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (!activity.isFinishing()) {
                                    showUpdateAvailableDialog(activity, tagName, releaseNotes, apkDownloadUrl);
                                }
                            }
                        });
                    } else if (showNoUpdateToast) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(activity, "✅ Uygulamanız güncel! (" + currentVersion + ")", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                } catch (final Exception e) {
                    if (showNoUpdateToast) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(activity, "Güncelleme kontrolü sırasında bağlantı hatası: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                }
            }
        }).start();
    }

    private static boolean isNewerVersion(String remoteTag, String localTag) {
        String r = remoteTag.replaceAll("[^0-9.]", "");
        String l = localTag.replaceAll("[^0-9.]", "");
        if (r.equals(l)) return false;
        String[] rParts = r.split("\\.");
        String[] lParts = l.split("\\.");
        int len = Math.max(rParts.length, lParts.length);
        for (int i = 0; i < len; i++) {
            int rv = i < rParts.length ? parseIntSafe(rParts[i]) : 0;
            int lv = i < lParts.length ? parseIntSafe(lParts[i]) : 0;
            if (rv > lv) return true;
            if (rv < lv) return false;
        }
        return !remoteTag.equals(localTag);
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return 0;
        }
    }

    private static void showUpdateAvailableDialog(final Activity activity, String newTag, String notes, final String apkUrl) {
        new AlertDialog.Builder(activity)
                .setTitle("🎉 Yeni Güncelleme Bulundu! (" + newTag + ")")
                .setMessage("GitHub üzerinde yeni bir sürüm yayınlandı. İpek'in çalışma takvimi ve ödülleri silinmeden mevcut uygulamanın üzerine güncellenecektir.\n\nDeğişiklikler:\n" + notes)
                .setPositiveButton("İndir ve Güncelle", (dialog, which) -> downloadAndInstallApk(activity, apkUrl))
                .setNeutralButton("GitHub Repo Ayarı", (dialog, which) -> showConfigureRepoDialog(activity))
                .setNegativeButton("Daha Sonra", null)
                .show();
    }

    public static void showConfigureRepoDialog(final Activity activity) {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * activity.getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad / 2, pad, pad / 2);

        final EditText etRepo = new EditText(activity);
        etRepo.setHint("ornek: kullaniciadi/calisma-zamanlayici");
        etRepo.setText(StudyDatabaseHelper.getGitHubRepo(activity));
        layout.addView(etRepo);

        new AlertDialog.Builder(activity)
                .setTitle("⚙️ GitHub Otomatik Güncelleme Deposu")
                .setMessage("Projeyi yüklediğiniz GitHub adresini 'kullaniciadi/depo-adi' biçiminde girin. Her güncelleme yayınladığınızda cihaz otomatik olarak buradan yeni APK'yı indirip kuracaktır:")
                .setView(layout)
                .setPositiveButton("Kaydet ve Kontrol Et", (dialog, which) -> {
                    String entered = etRepo.getText().toString().trim();
                    entered = entered.replace("https://github.com/", "").replaceAll("/$", "");
                    StudyDatabaseHelper.setGitHubRepo(activity, entered);
                    Toast.makeText(activity, "GitHub deposu kaydedildi: " + entered, Toast.LENGTH_SHORT).show();
                    checkUpdates(activity, true);
                })
                .setNegativeButton("İptal", null)
                .show();
    }

    private static void downloadAndInstallApk(final Activity activity, final String apkUrl) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.getPackageManager().canRequestPackageInstalls()) {
                Toast.makeText(activity, "Güncellemeyi kurabilmek için 'Bilinmeyen Uygulamaları Yükle' iznini açın.", Toast.LENGTH_LONG).show();
                Intent permIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(permIntent);
                return;
            }
        }

        final ProgressDialog progressDialog = new ProgressDialog(activity);
        progressDialog.setTitle("Yeni Sürüm İndiriliyor...");
        progressDialog.setMessage("Lütfen bekleyin, güncelleme paketi GitHub'dan indiriliyor...");
        progressDialog.setIndeterminate(false);
        progressDialog.setMax(100);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setCancelable(false);
        progressDialog.show();

        final Handler mainHandler = new Handler(Looper.getMainLooper());

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL(apkUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setInstanceFollowRedirects(true);
                    conn.connect();

                    int fileLength = conn.getContentLength();
                    File dir = activity.getExternalFilesDir(null);
                    if (dir == null) {
                        dir = activity.getCacheDir();
                    }
                    final File outputFile = new File(dir, "CalismaZamanlayici_update.apk");
                    if (outputFile.exists()) {
                        outputFile.delete();
                    }

                    InputStream input = conn.getInputStream();
                    FileOutputStream output = new FileOutputStream(outputFile);

                    byte[] data = new byte[8192];
                    long total = 0;
                    int count;
                    while ((count = input.read(data)) != -1) {
                        total += count;
                        if (fileLength > 0) {
                            final int progress = (int) (total * 100 / fileLength);
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    progressDialog.setProgress(progress);
                                }
                            });
                        }
                        output.write(data, 0, count);
                    }

                    output.flush();
                    output.close();
                    input.close();

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            progressDialog.dismiss();
                            promptInstallApk(activity, outputFile);
                        }
                    });
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            progressDialog.dismiss();
                            Toast.makeText(activity, "İndirme hatası: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }

    private static void promptInstallApk(Activity activity, File apkFile) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri apkUri;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            apkUri = ApkFileProvider.getUriForFile(apkFile);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            apkUri = Uri.fromFile(apkFile);
        }
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(intent);
    }
}
