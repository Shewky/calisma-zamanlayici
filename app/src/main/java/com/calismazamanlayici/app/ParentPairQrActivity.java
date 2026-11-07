package com.calismazamanlayici.app;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

/**
 * Anne ve Baba için ayrı ayrı eşleştirme karekodu (QR Code) üretir.
 * Veli uygulamasında bu karekod okutulduğunda öğrenci cihazı veli ekranına otomatik eklenir.
 */
public class ParentPairQrActivity extends Activity {

    private TextView tvQrRoleTitle;
    private ImageView ivQrCodeBitmap;
    private TextView tvQrNetworkInfo;
    private String currentRole = "ANNE";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parent_pair_qr);

        ScrollView scrollRootQr = findViewById(R.id.scrollRootQr);
        LinearLayout layoutQrHeaderCard = findViewById(R.id.layoutQrHeaderCard);
        Button btnShowAnneQr = findViewById(R.id.btnShowAnneQr);
        Button btnShowBabaQr = findViewById(R.id.btnShowBabaQr);
        tvQrRoleTitle = findViewById(R.id.tvQrRoleTitle);
        ivQrCodeBitmap = findViewById(R.id.ivQrCodeBitmap);
        tvQrNetworkInfo = findViewById(R.id.tvQrNetworkInfo);
        Button btnBack = findViewById(R.id.btnBackFromQr);

        ThemeHelper.Palette p = ThemeHelper.getPalette(this);
        ThemeHelper.applyWindowAndSurface(this, scrollRootQr);
        ThemeHelper.styleHeader(this, layoutQrHeaderCard);
        btnShowAnneQr.setBackground(ThemeHelper.createButtonDrawable(this, p.primary));
        btnShowBabaQr.setBackground(ThemeHelper.createButtonDrawable(this, p.secondary));

        btnShowAnneQr.setOnClickListener(v -> {
            currentRole = "ANNE";
            renderQrCodeForRole("ANNE");
        });

        btnShowBabaQr.setOnClickListener(v -> {
            currentRole = "BABA";
            renderQrCodeForRole("BABA");
        });

        btnBack.setOnClickListener(v -> finish());

        renderQrCodeForRole("ANNE");
    }

    private void renderQrCodeForRole(String role) {
        String childId = StudyDatabaseHelper.getChildDeviceId(this);
        String userName = StudyDatabaseHelper.getUserName(this);
        int grade = StudyDatabaseHelper.getGradeLevel(this);
        String localIp = LanSyncServer.getLocalIpAddress(this);

        if ("BABA".equals(role)) {
            tvQrRoleTitle.setText("👨 BABA İÇİN EŞLEŞTİRME KAREKODU");
        } else {
            tvQrRoleTitle.setText("👩 ANNE İÇİN EŞLEŞTİRME KAREKODU");
        }

        // Karekod içeriği: CZPAIR|<ROLE>|<CHILD_ID>|<IP>|<PORT>|<USER_NAME>|<GRADE>
        String qrPayload = "CZPAIR|" + role + "|" + childId + "|" + localIp + "|"
                + LanSyncServer.STUDENT_HTTP_PORT + "|" + userName + "|" + grade;

        Bitmap bmp = generateQrBitmap(qrPayload, 600);
        if (bmp != null) {
            ivQrCodeBitmap.setImageBitmap(bmp);
        }

        tvQrNetworkInfo.setText("Öğrenci: " + userName + " (" + grade + ". Sınıf)"
                + "\nAğ IP: " + localIp + " • Cihaz Kodu: " + childId
                + "\n(IP adresi değişse bile Veli uygulaması ağda otomatik bulur)");
    }

    private Bitmap generateQrBitmap(String content, int sizePx) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx);
            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bmp.setPixel(x, y, bitMatrix.get(x, y) ? 0xFF1F2937 : 0xFFFFFFFF);
                }
            }
            return bmp;
        } catch (Exception e) {
            return null;
        }
    }
}
