package com.calismazamanlayici.veli;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class VeliMainActivity extends Activity {

    private LinearLayout layoutVeliOnboarding;
    private LinearLayout layoutVeliDashboard;
    private RadioButton rbRoleAnne;
    private RadioButton rbRoleBaba;
    private TextView tvVeliHeaderTitle;
    private TextView tvVeliVersionInfo;
    private LinearLayout layoutPairedChildrenList;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private boolean autoRefreshActive = false;

    private final Runnable liveStatusPoller = new Runnable() {
        @Override
        public void run() {
            if (!autoRefreshActive) return;
            pollPairedChildrenStatus();
            uiHandler.postDelayed(this, 3500L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_veli_main);

        layoutVeliOnboarding = findViewById(R.id.layoutVeliOnboarding);
        layoutVeliDashboard = findViewById(R.id.layoutVeliDashboard);
        rbRoleAnne = findViewById(R.id.rbRoleAnne);
        rbRoleBaba = findViewById(R.id.rbRoleBaba);
        Button btnSaveVeliRole = findViewById(R.id.btnSaveVeliRole);

        tvVeliHeaderTitle = findViewById(R.id.tvVeliHeaderTitle);
        tvVeliVersionInfo = findViewById(R.id.tvVeliVersionInfo);
        Button btnChangeVeliRole = findViewById(R.id.btnChangeVeliRole);
        Button btnScanQrCode = findViewById(R.id.btnScanQrCode);
        Button btnScanNetworkIps = findViewById(R.id.btnScanNetworkIps);
        layoutPairedChildrenList = findViewById(R.id.layoutPairedChildrenList);
        Button btnVeliCheckUpdate = findViewById(R.id.btnVeliCheckUpdate);

        tvVeliVersionInfo.setText("Çalışma Zamanlayıcı Veli v" + BuildConfig.VERSION_NAME
                + " (Kod: " + BuildConfig.VERSION_CODE + ") • Android 6+ Uyumlu");

        btnSaveVeliRole.setOnClickListener(v -> {
            String role = rbRoleBaba.isChecked() ? "BABA" : "ANNE";
            VeliStorage.saveParentRole(VeliMainActivity.this, role);
            refreshUiState();
            Toast.makeText(VeliMainActivity.this, "✅ " + (role.equals("BABA") ? "Baba" : "Anne") + " olarak giriş yapıldı!", Toast.LENGTH_SHORT).show();
        });

        btnChangeVeliRole.setOnClickListener(v -> showRoleAndSettingsDialog());

        btnScanQrCode.setOnClickListener(v -> {
            startActivity(new Intent(VeliMainActivity.this, QrScannerActivity.class));
        });

        btnScanNetworkIps.setOnClickListener(v -> {
            Toast.makeText(VeliMainActivity.this, "🔍 Ağdaki değişen IP adresleri ve kullanıcı isimleri taranıyor...", Toast.LENGTH_SHORT).show();
            ParentBackgroundService.scanLanAndSyncChildren(VeliMainActivity.this, false, new Runnable() {
                @Override
                public void run() {
                    renderChildrenCards();
                    pollPairedChildrenStatus();
                    Toast.makeText(VeliMainActivity.this, "✅ IP taraması ve isim eşleştirmesi tamamlandı!", Toast.LENGTH_SHORT).show();
                }
            });
        });

        btnVeliCheckUpdate.setOnClickListener(v -> {
            VeliGitHubUpdater.checkUpdates(VeliMainActivity.this, true);
        });

        btnVeliCheckUpdate.setOnLongClickListener(v -> {
            VeliGitHubUpdater.showConfigureRepoDialog(VeliMainActivity.this);
            return true;
        });

        // Arka plan çağrı dinleme servisini başlat
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(new Intent(this, ParentBackgroundService.class));
        } else {
            startService(new Intent(this, ParentBackgroundService.class));
        }

        requestVeliPermissions();
        refreshUiState();
        VeliGitHubUpdater.checkUpdates(this, false);
    }

    private void showRoleAndSettingsDialog() {
        String currentRole = VeliStorage.getParentRole(this);
        final String[] options = new String[]{
                "👩 Anne Olarak Kullan",
                "👨 Baba Olarak Kullan",
                "🔍 Ağdaki Tüm Öğrenci Cihazlarını Otomatik Bul ve Ekle",
                "⚙️ GitHub Otomatik Güncelleme Deposu Ayarı"
        };
        new AlertDialog.Builder(this)
                .setTitle("⚙️ Veli Ayarları (Mevcut: " + ("BABA".equals(currentRole) ? "Baba" : "Anne") + ")")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        VeliStorage.saveParentRole(VeliMainActivity.this, "ANNE");
                        refreshUiState();
                    } else if (which == 1) {
                        VeliStorage.saveParentRole(VeliMainActivity.this, "BABA");
                        refreshUiState();
                    } else if (which == 2) {
                        ParentBackgroundService.scanLanAndSyncChildren(VeliMainActivity.this, true, new Runnable() {
                            @Override
                            public void run() {
                                renderChildrenCards();
                                pollPairedChildrenStatus();
                            }
                        });
                    } else if (which == 3) {
                        VeliGitHubUpdater.showConfigureRepoDialog(VeliMainActivity.this);
                    }
                })
                .setNegativeButton("Kapat", null)
                .show();
    }

    private void requestVeliPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            List<String> perms = new ArrayList<>();
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.RECORD_AUDIO);
            }
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.CAMERA);
            }
            if (Build.VERSION.SDK_INT >= 33) {
                if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    perms.add(Manifest.permission.POST_NOTIFICATIONS);
                }
            }
            if (!perms.isEmpty()) {
                requestPermissions(perms.toArray(new String[0]), 301);
            }

            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                try {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception ignored) {
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUiState();
        autoRefreshActive = true;
        uiHandler.removeCallbacks(liveStatusPoller);
        uiHandler.post(liveStatusPoller);
        // Açılışta değişen IP adreslerini otomatik tara
        ParentBackgroundService.scanLanAndSyncChildren(this, false, new Runnable() {
            @Override
            public void run() {
                renderChildrenCards();
            }
        });
    }

    @Override
    protected void onPause() {
        autoRefreshActive = false;
        uiHandler.removeCallbacks(liveStatusPoller);
        super.onPause();
    }

    private void refreshUiState() {
        if (!VeliStorage.isOnboarded(this)) {
            layoutVeliOnboarding.setVisibility(View.VISIBLE);
            layoutVeliDashboard.setVisibility(View.GONE);
            return;
        }
        layoutVeliOnboarding.setVisibility(View.GONE);
        layoutVeliDashboard.setVisibility(View.VISIBLE);

        String role = VeliStorage.getParentRole(this);
        String roleTitle = "BABA".equalsIgnoreCase(role) ? "👨 Baba Kontrol Paneli" : "👩 Anne Kontrol Paneli";
        tvVeliHeaderTitle.setText(roleTitle);

        renderChildrenCards();
    }

    private void renderChildrenCards() {
        layoutPairedChildrenList.removeAllViews();
        final List<VeliStorage.PairedChild> children = VeliStorage.getPairedChildren(this);
        int pad = (int) (14 * getResources().getDisplayMetrics().density);
        int margin = (int) (12 * getResources().getDisplayMetrics().density);

        if (children.isEmpty()) {
            TextView emptyTv = new TextView(this);
            emptyTv.setBackgroundResource(R.drawable.bg_veli_card);
            emptyTv.setPadding(pad, pad, pad, pad);
            emptyTv.setText("Henüz eşleştirilmiş çocuk bulunmuyor.\n\nYukarıdaki '📷 Karekod Okut (Çocuk Ekle)' butonuna basarak Çalışma Zamanlayıcı uygulamasındaki Anne veya Baba karekodunu okutabilirsiniz.");
            emptyTv.setTextColor(0xFF475569);
            emptyTv.setTextSize(14f);
            layoutPairedChildrenList.addView(emptyTv);
            return;
        }

        for (final VeliStorage.PairedChild child : children) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setBackgroundResource(R.drawable.bg_veli_card);
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            cardLp.setMargins(0, 0, 0, margin);
            card.setLayoutParams(cardLp);
            card.setPadding(pad, pad, pad, pad);

            // Sol Kısım: Çocuğun Adı, Sınıfı, IP Adresi ve Canlı Durumu (Tıklanınca Detay/Takvim/Ödül açılır)
            LinearLayout infoCol = new LinearLayout(this);
            infoCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            infoCol.setLayoutParams(infoLp);

            TextView tvName = new TextView(this);
            tvName.setText("👤 " + child.userName + " (" + child.gradeLevel + ". Sınıf)");
            tvName.setTextColor(0xFF0F172A);
            tvName.setTextSize(18f);
            tvName.setTypeface(null, android.graphics.Typeface.BOLD);

            TextView tvStatus = new TextView(this);
            tvStatus.setTag("status_" + child.childId);
            tvStatus.setText(child.liveStatusText + "\n📡 IP: " + (child.ipAddress.isEmpty() ? "Taranıyor..." : child.ipAddress));
            tvStatus.setTextColor(0xFF0D47A1);
            tvStatus.setTextSize(13f);
            tvStatus.setPadding(0, pad / 3, 0, 0);

            infoCol.addView(tvName);
            infoCol.addView(tvStatus);

            infoCol.setOnClickListener(v -> {
                Intent detailIntent = new Intent(VeliMainActivity.this, ChildDetailActivity.class);
                detailIntent.putExtra("EXTRA_CHILD_ID", child.childId);
                detailIntent.putExtra("EXTRA_CHILD_NAME", child.userName);
                detailIntent.putExtra("EXTRA_CHILD_IP", child.ipAddress);
                startActivity(detailIntent);
            });

            infoCol.setOnLongClickListener(v -> {
                new AlertDialog.Builder(VeliMainActivity.this)
                        .setTitle(child.userName + " Kaydını Kaldır")
                        .setMessage("Bu öğrenciyi takip listesinden kaldırmak istiyor musunuz?")
                        .setPositiveButton("Sil", (d, w) -> {
                            VeliStorage.removePairedChild(VeliMainActivity.this, child.childId);
                            renderChildrenCards();
                        })
                        .setNegativeButton("İptal", null)
                        .show();
                return true;
            });

            // Sağ Kısım: Kullanıcı İsminin Yanında Doğrudan "📞 Ara" Butonu (Onaya gerek kalmadan görüşme başlatır!)
            Button btnDirectAra = new Button(this);
            int btnWidth = (int) (96 * getResources().getDisplayMetrics().density);
            int btnHeight = (int) (52 * getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(btnWidth, btnHeight);
            btnLp.setMargins(pad / 2, 0, 0, 0);
            btnDirectAra.setLayoutParams(btnLp);
            btnDirectAra.setBackgroundResource(R.drawable.btn_veli_green);
            btnDirectAra.setText("📞 Ara");
            btnDirectAra.setAllCaps(false);
            btnDirectAra.setTextColor(0xFFFFFFFF);
            btnDirectAra.setTextSize(15f);
            btnDirectAra.setTypeface(null, android.graphics.Typeface.BOLD);

            btnDirectAra.setOnClickListener(v -> {
                if (child.ipAddress == null || child.ipAddress.isEmpty()) {
                    Toast.makeText(VeliMainActivity.this, "Öğrenci IP adresi taranıyor, lütfen 2 saniye sonra tekrar basın.", Toast.LENGTH_SHORT).show();
                    ParentBackgroundService.scanLanAndSyncChildren(VeliMainActivity.this, false, null);
                    return;
                }
                Intent callIntent = new Intent(VeliMainActivity.this, VeliCallActivity.class);
                callIntent.putExtra("EXTRA_CHILD_ID", child.childId);
                callIntent.putExtra("EXTRA_CHILD_NAME", child.userName);
                callIntent.putExtra("EXTRA_CHILD_IP", child.ipAddress);
                callIntent.putExtra("EXTRA_INCOMING_APPROVAL", false); // Onaysız doğrudan arama!
                startActivity(callIntent);
            });

            card.addView(infoCol);
            card.addView(btnDirectAra);
            layoutPairedChildrenList.addView(card);
        }
    }

    private void pollPairedChildrenStatus() {
        final List<VeliStorage.PairedChild> children = VeliStorage.getPairedChildren(this);
        for (final VeliStorage.PairedChild child : children) {
            if (child.ipAddress == null || child.ipAddress.isEmpty()) continue;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        URL url = new URL("http://" + child.ipAddress + ":" + ParentBackgroundService.STUDENT_HTTP_PORT + "/status");
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
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

                            JSONObject json = new JSONObject(sb.toString());
                            final String latestName = json.optString("userName", child.userName);
                            final int latestGrade = json.optInt("gradeLevel", child.gradeLevel);
                            final String phaseText = json.optString("phaseText", "🟢 Çevrimiçi");
                            final String remFmt = json.optString("remainingFormatted", "");

                            VeliStorage.updateExistingChildNetworkInfo(
                                    VeliMainActivity.this, child.childId, latestName, latestGrade, child.ipAddress);

                            uiHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    View v = layoutPairedChildrenList.findViewWithTag("status_" + child.childId);
                                    if (v instanceof TextView) {
                                        ((TextView) v).setText(phaseText + " (" + remFmt + ")\n📡 IP: " + child.ipAddress);
                                    }
                                }
                            });
                        } else {
                            throw new Exception("HTTP " + conn.getResponseCode());
                        }
                    } catch (Exception e) {
                        // Cihazın IP adresi değişmiş olabilir -> arka planda otomatik IP taraması başlat
                        ParentBackgroundService.scanLanAndSyncChildren(VeliMainActivity.this, false, null);
                    }
                }
            }).start();
        }
    }
}
