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
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class VeliMainActivity extends Activity {

    private ScrollView scrollRootVeliMain;
    private LinearLayout layoutVeliHeaderCard;
    private LinearLayout layoutVeliOnboarding;
    private LinearLayout layoutVeliDashboard;
    private RadioButton rbRoleAnne;
    private RadioButton rbRoleBaba;
    private TextView tvVeliHeaderTitle;
    private TextView tvOtherParentStatusTitle;
    private EditText etOtherParentMessage;
    private Button btnSendOtherParentMessage;
    private TextView tvLastReceivedParentMessage;
    private Button btnRepeatLastParentMessage;
    private TextView tvVeliVersionInfo;
    private LinearLayout layoutPairedChildrenList;
    private Button btnScanQrCode;
    private Button btnScanNetworkIps;
    private Button btnVeliCheckUpdate;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private boolean autoRefreshActive = false;
    private static volatile boolean foregroundVisible = false;
    private static VeliMainActivity currentInstance = null;

    public static boolean isAppInForeground() {
        return foregroundVisible;
    }

    public static void setForegroundVisible(boolean visible) {
        foregroundVisible = visible;
    }

    public static void refreshLastMessageOnScreenIfVisible() {
        if (currentInstance != null && !currentInstance.isFinishing()) {
            currentInstance.runOnUiThread(currentInstance::updateOtherParentUi);
        }
    }

    private final Runnable liveStatusPoller = new Runnable() {
        @Override
        public void run() {
            if (!autoRefreshActive) return;
            pollPairedChildrenStatus();
            pollOtherParentStatus();
            uiHandler.postDelayed(this, 3000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_veli_main);

        scrollRootVeliMain = findViewById(R.id.scrollRootVeliMain);
        layoutVeliHeaderCard = findViewById(R.id.layoutVeliHeaderCard);
        layoutVeliOnboarding = findViewById(R.id.layoutVeliOnboarding);
        layoutVeliDashboard = findViewById(R.id.layoutVeliDashboard);
        rbRoleAnne = findViewById(R.id.rbRoleAnne);
        rbRoleBaba = findViewById(R.id.rbRoleBaba);
        Button btnSaveVeliRole = findViewById(R.id.btnSaveVeliRole);

        tvVeliHeaderTitle = findViewById(R.id.tvVeliHeaderTitle);
        tvOtherParentStatusTitle = findViewById(R.id.tvOtherParentStatusTitle);
        etOtherParentMessage = findViewById(R.id.etOtherParentMessage);
        btnSendOtherParentMessage = findViewById(R.id.btnSendOtherParentMessage);
        tvLastReceivedParentMessage = findViewById(R.id.tvLastReceivedParentMessage);
        btnRepeatLastParentMessage = findViewById(R.id.btnRepeatLastParentMessage);

        btnRepeatLastParentMessage.setOnClickListener(v -> {
            String lastMsg = VeliStorage.getLastReceivedParentMessage(VeliMainActivity.this);
            if (lastMsg == null || lastMsg.trim().isEmpty()) {
                Toast.makeText(VeliMainActivity.this, "Henüz tekrar edilecek bir mesaj bulunmuyor.", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent speakIntent = new Intent(VeliMainActivity.this, ParentBackgroundService.class);
            speakIntent.setAction(ParentBackgroundService.ACTION_SPEAK);
            speakIntent.putExtra(ParentBackgroundService.EXTRA_SPEECH_TEXT, lastMsg);
            startService(speakIntent);
            Toast.makeText(VeliMainActivity.this, "🔊 Son mesaj tekrar okunuyor:  + lastMsg + ", Toast.LENGTH_SHORT).show();
        });
        tvVeliVersionInfo = findViewById(R.id.tvVeliVersionInfo);
        Button btnChangeVeliRole = findViewById(R.id.btnChangeVeliRole);
        btnScanQrCode = findViewById(R.id.btnScanQrCode);
        btnScanNetworkIps = findViewById(R.id.btnScanNetworkIps);
        layoutPairedChildrenList = findViewById(R.id.layoutPairedChildrenList);
        btnVeliCheckUpdate = findViewById(R.id.btnVeliCheckUpdate);

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
            Toast.makeText(VeliMainActivity.this, "🔍 Ağdaki cihazlar taranıyor...", Toast.LENGTH_SHORT).show();
            ParentBackgroundService.scanLanAndSyncChildren(VeliMainActivity.this, false, new Runnable() {
                @Override
                public void run() {
                    renderChildrenCards();
                    pollPairedChildrenStatus();
                    updateOtherParentUi();
                    Toast.makeText(VeliMainActivity.this, "✅ Cihazlar ve durumlar yenilendi!", Toast.LENGTH_SHORT).show();
                }
            });
        });

        btnSendOtherParentMessage.setOnClickListener(v -> {
            String msg = etOtherParentMessage.getText().toString().trim();
            if (msg.isEmpty()) {
                Toast.makeText(VeliMainActivity.this, "Lütfen göndermek istediğiniz mesajı yazın ✍️", Toast.LENGTH_SHORT).show();
                return;
            }
            sendVoiceMessageToOtherParent(msg);
            etOtherParentMessage.setText("");
        });

        btnVeliCheckUpdate.setOnClickListener(v -> {
            VeliGitHubUpdater.checkUpdates(VeliMainActivity.this, true);
        });

        btnVeliCheckUpdate.setOnLongClickListener(v -> {
            VeliGitHubUpdater.showConfigureRepoDialog(VeliMainActivity.this);
            return true;
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(new Intent(this, ParentBackgroundService.class));
        } else {
            startService(new Intent(this, ParentBackgroundService.class));
        }

        requestVeliPermissions();
        applyVeliTheme();
        refreshUiState();

        // Veli uygulaması silinip tekrar kurulduysa yedeği kontrol et ve sor
        VeliBackupManager.checkAndPromptRestoreIfAvailable(this, new Runnable() {
            @Override
            public void run() {
                applyVeliTheme();
                refreshUiState();
            }
        });

        VeliGitHubUpdater.checkUpdates(this, false);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        VeliBackupManager.checkAndPromptRestoreIfAvailable(this, new Runnable() {
            @Override
            public void run() {
                applyVeliTheme();
                refreshUiState();
            }
        });
    }

    private void applyVeliTheme() {
        VeliThemeHelper.Palette p = VeliThemeHelper.getPalette(this);
        VeliThemeHelper.applyWindowAndSurface(this, scrollRootVeliMain);
        if (layoutVeliHeaderCard != null) {
            layoutVeliHeaderCard.setBackground(VeliThemeHelper.createHeaderDrawable(this));
        }
        btnScanQrCode.setBackground(VeliThemeHelper.createButtonDrawable(this, p.accent));
        btnScanNetworkIps.setBackground(VeliThemeHelper.createButtonDrawable(this, p.primary));
        btnSendOtherParentMessage.setBackground(VeliThemeHelper.createButtonDrawable(this, p.primaryDark));
        if (btnRepeatLastParentMessage != null) {
            btnRepeatLastParentMessage.setBackground(VeliThemeHelper.createButtonDrawable(this, p.secondary));
        }
        btnVeliCheckUpdate.setBackground(VeliThemeHelper.createButtonDrawable(this, p.secondary));
    }

    private void updateOtherParentUi() {
        String otherTitle = VeliStorage.getOtherParentTitleTr(this);
        boolean online = VeliStorage.isOtherParentOnline(this);
        if (online) {
            tvOtherParentStatusTitle.setText(otherTitle + " Durumu: 🟢 Çevrimiçi");
        } else {
            tvOtherParentStatusTitle.setText(otherTitle + " Durumu: ⚪ Çevrimdışı (Offline)");
        }
        btnSendOtherParentMessage.setText("🔊 " + otherTitle + " Cihazına Sesli Mesaj Gönder");
        String lastMsg = VeliStorage.getLastReceivedParentMessage(this);
        if (tvLastReceivedParentMessage != null) {
            if (lastMsg == null || lastMsg.isEmpty()) {
                tvLastReceivedParentMessage.setText("📩 Son Gelen Mesaj: Henüz mesaj yok");
            } else {
                tvLastReceivedParentMessage.setText("📩 Son Gelen Mesaj: \"" + lastMsg + "\"");
            }
        }
    }

    private void pollOtherParentStatus() {
        final String otherIp = VeliStorage.getOtherParentIp(this);
        final String myRole = VeliStorage.getParentRole(this);
        if (otherIp != null && !otherIp.isEmpty()) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        URL url = new URL("http://" + otherIp + ":" + ParentBackgroundService.PARENT_HTTP_PORT + "/parent_status");
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
                            String role = obj.optString("parentRole", "");
                            if (!role.isEmpty() && !myRole.equalsIgnoreCase(role)) {
                                VeliStorage.updateOtherParentSeen(VeliMainActivity.this, otherIp);
                            }
                        }
                        conn.disconnect();
                    } catch (Exception ignored) {
                    }
                    uiHandler.post(VeliMainActivity.this::updateOtherParentUi);
                }
            }).start();
        } else {
            updateOtherParentUi();
        }
    }

    /**
     * Veliler (Anne <-> Baba) arasında klavyeden girilen özelleştirilebilir mesajı
     * diğer velinin cihazına gönderir ve sesli anons ettirir.
     */
    private void sendVoiceMessageToOtherParent(final String messageText) {
        final String myRole = VeliStorage.getParentRole(this);
        final String otherIp = VeliStorage.getOtherParentIp(this);
        final String otherTitle = VeliStorage.getOtherParentTitleTr(this);

        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean sentOk = false;

                // 1. UDP paketi ile gönder
                try {
                    String udpPayload = "CZ_PARENT_MSG|" + myRole + "|" + messageText;
                    byte[] data = udpPayload.getBytes("UTF-8");
                    DatagramSocket ds = new DatagramSocket();
                    ds.setBroadcast(true);
                    if (otherIp != null && !otherIp.isEmpty()) {
                        ds.send(new DatagramPacket(data, data.length,
                                InetAddress.getByName(otherIp), ParentBackgroundService.DISCOVERY_UDP_PORT));
                        sentOk = true;
                    }
                    ds.send(new DatagramPacket(data, data.length,
                            InetAddress.getByName("255.255.255.255"), ParentBackgroundService.DISCOVERY_UDP_PORT));
                    ds.close();
                } catch (Exception ignored) {
                }

                // 2. HTTP GET ile gönder
                if (otherIp != null && !otherIp.isEmpty()) {
                    try {
                        String q = "fromRole=" + URLEncoder.encode(myRole, "UTF-8")
                                + "&text=" + URLEncoder.encode(messageText, "UTF-8");
                        URL url = new URL("http://" + otherIp + ":" + ParentBackgroundService.PARENT_HTTP_PORT + "/parent_message?" + q);
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

                final boolean finalSent = sentOk;
                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (finalSent) {
                            Toast.makeText(VeliMainActivity.this,
                                    "🔊 " + otherTitle + " cihazına sesli mesaj gönderildi: '" + messageText + "'",
                                    Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(VeliMainActivity.this,
                                    "⚠️ " + otherTitle + " şu anda çevrimdışı görünüyor, ağ yayını gönderildi.",
                                    Toast.LENGTH_SHORT).show();
                            ParentBackgroundService.scanLanAndSyncChildren(VeliMainActivity.this, false, null);
                        }
                    }
                });
            }
        }).start();
    }

    private void showRoleAndSettingsDialog() {
        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        ScrollView dialogScroll = new ScrollView(this);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(pad, pad / 2, pad, pad / 2);
        dialogScroll.addView(container);

        TextView tvRoleLabel = new TextView(this);
        tvRoleLabel.setText("👨‍👩‍👧 Veli Rolü:");
        tvRoleLabel.setTextSize(15f);
        tvRoleLabel.setTextColor(0xFF0F172A);
        container.addView(tvRoleLabel);

        final RadioGroup rgRole = new RadioGroup(this);
        RadioButton rbAnne = new RadioButton(this);
        rbAnne.setId(3001);
        rbAnne.setText("👩 Anne Olarak Kullan");
        RadioButton rbBaba = new RadioButton(this);
        rbBaba.setId(3002);
        rbBaba.setText("👨 Baba Olarak Kullan");
        rgRole.addView(rbAnne);
        rgRole.addView(rbBaba);
        if ("BABA".equalsIgnoreCase(VeliStorage.getParentRole(this))) {
            rbBaba.setChecked(true);
        } else {
            rbAnne.setChecked(true);
        }
        container.addView(rgRole);

        TextView tvThemeLabel = new TextView(this);
        tvThemeLabel.setText("\n🎨 Tema Seçimi:");
        tvThemeLabel.setTextSize(15f);
        tvThemeLabel.setTextColor(0xFF0F172A);
        container.addView(tvThemeLabel);

        final RadioGroup rgThemes = new RadioGroup(this);
        rgThemes.setOrientation(RadioGroup.VERTICAL);
        final String[] keys = VeliThemeHelper.ALL_THEME_KEYS;
        final String[] titles = VeliThemeHelper.ALL_THEME_TITLES;
        String currentTheme = VeliStorage.getThemeKey(this);

        for (int i = 0; i < keys.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(4000 + i);
            rb.setText(titles[i]);
            rb.setTextSize(14f);
            rgThemes.addView(rb);
            if (keys[i].equals(currentTheme)) {
                rb.setChecked(true);
            }
        }
        container.addView(rgThemes);

        new AlertDialog.Builder(this)
                .setTitle("⚙️ Veli Ayarları ve Tema")
                .setView(dialogScroll)
                .setPositiveButton("Kaydet", (dialog, which) -> {
                    String newRole = (rgRole.getCheckedRadioButtonId() == 3002) ? "BABA" : "ANNE";
                    VeliStorage.saveParentRole(VeliMainActivity.this, newRole);

                    int checkedThemeId = rgThemes.getCheckedRadioButtonId();
                    int idx = checkedThemeId - 4000;
                    if (idx >= 0 && idx < keys.length) {
                        VeliStorage.setThemeKey(VeliMainActivity.this, keys[idx]);
                    }
                    applyVeliTheme();
                    refreshUiState();
                    Toast.makeText(VeliMainActivity.this, "✅ Veli ayarları ve tema güncellendi!", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("GitHub Depo Ayarı", (dialog, which) -> {
                    VeliGitHubUpdater.showConfigureRepoDialog(VeliMainActivity.this);
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
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.READ_EXTERNAL_STORAGE);
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
        currentInstance = this;
        foregroundVisible = true;
        applyVeliTheme();
        refreshUiState();
        autoRefreshActive = true;
        uiHandler.removeCallbacks(liveStatusPoller);
        uiHandler.post(liveStatusPoller);
        ParentBackgroundService.scanLanAndSyncChildren(this, false, new Runnable() {
            @Override
            public void run() {
                renderChildrenCards();
                updateOtherParentUi();
            }
        });
    }

    @Override
    protected void onPause() {
        foregroundVisible = false;
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

        updateOtherParentUi();
        renderChildrenCards();
    }

    private void renderChildrenCards() {
        layoutPairedChildrenList.removeAllViews();
        final List<VeliStorage.PairedChild> children = VeliStorage.getPairedChildren(this);
        VeliThemeHelper.Palette p = VeliThemeHelper.getPalette(this);
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
            tvStatus.setText(child.liveStatusText);
            tvStatus.setTextColor(p.primary);
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

            Button btnDirectAra = new Button(this);
            int btnWidth = (int) (96 * getResources().getDisplayMetrics().density);
            int btnHeight = (int) (52 * getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(btnWidth, btnHeight);
            btnLp.setMargins(pad / 2, 0, 0, 0);
            btnDirectAra.setLayoutParams(btnLp);
            btnDirectAra.setBackground(VeliThemeHelper.createButtonDrawable(this, p.accent));
            btnDirectAra.setText("📞 Ara");
            btnDirectAra.setAllCaps(false);
            btnDirectAra.setTextColor(0xFFFFFFFF);
            btnDirectAra.setTextSize(15f);
            btnDirectAra.setTypeface(null, android.graphics.Typeface.BOLD);

            btnDirectAra.setOnClickListener(v -> {
                checkChildOnlineAndStartDirectCall(child.childId, child.userName, child.ipAddress);
            });

            card.addView(infoCol);
            card.addView(btnDirectAra);
            layoutPairedChildrenList.addView(card);
        }
    }


    /**
     * Çocuk aranırken önce o an çevrimiçi olup olmadığını kontrol eder.
     * Eğer offline ise "Şu anda offline görünüyor" yazar ve görüşmeyi başlatmaz.
     */
    public void checkChildOnlineAndStartDirectCall(final String childId, final String childName, final String childIp) {
        if (childIp == null || childIp.isEmpty()) {
            Toast.makeText(this, "⚠️ Şu anda offline görünüyor", Toast.LENGTH_SHORT).show();
            ParentBackgroundService.scanLanAndSyncChildren(this, false, null);
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean isOnline = false;
                try {
                    URL url = new URL("http://" + childIp + ":" + ParentBackgroundService.STUDENT_HTTP_PORT + "/ping");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("Connection", "close");
                    conn.setConnectTimeout(900);
                    conn.setReadTimeout(900);
                    if (conn.getResponseCode() == 200) {
                        isOnline = true;
                    }
                    conn.disconnect();
                } catch (Exception ignored) {
                }

                final boolean finalOnline = isOnline;
                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) return;
                        if (!finalOnline) {
                            Toast.makeText(VeliMainActivity.this, "⚠️ Şu anda offline görünüyor", Toast.LENGTH_SHORT).show();
                            View v = layoutPairedChildrenList.findViewWithTag("status_" + childId);
                            if (v instanceof TextView) {
                                ((TextView) v).setText("⚪ Şu anda offline görünüyor");
                            }
                            ParentBackgroundService.scanLanAndSyncChildren(VeliMainActivity.this, false, null);
                        } else {
                            Intent callIntent = new Intent(VeliMainActivity.this, VeliCallActivity.class);
                            callIntent.putExtra("EXTRA_CHILD_ID", childId);
                            callIntent.putExtra("EXTRA_CHILD_NAME", childName);
                            callIntent.putExtra("EXTRA_CHILD_IP", childIp);
                            callIntent.putExtra("EXTRA_INCOMING_APPROVAL", false);
                            startActivity(callIntent);
                        }
                    }
                });
            }
        }).start();
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
                            conn.disconnect();

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
                                        ((TextView) v).setText(phaseText + " (" + remFmt + ")");
                                    }
                                }
                            });
                        } else {
                            conn.disconnect();
                            throw new Exception("HTTP " + conn.getResponseCode());
                        }
                    } catch (Exception e) {
                        ParentBackgroundService.scanLanAndSyncChildren(VeliMainActivity.this, false, null);
                    }
                }
            }).start();
        }
    }
}
