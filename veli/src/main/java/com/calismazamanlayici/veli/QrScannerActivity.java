package com.calismazamanlayici.veli;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Button;
import android.widget.Toast;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import java.util.List;

/**
 * Çalışma Zamanlayıcı uygulamasında Anne ve Baba için ayrı ayrı oluşturulan karekodu
 * telefon kamerasıyla okutarak eşleştirir.
 */
@SuppressWarnings("deprecation")
public class QrScannerActivity extends Activity implements SurfaceHolder.Callback, Camera.PreviewCallback {

    private SurfaceView surfaceCameraPreview;
    private Camera camera;
    private boolean qrFound = false;
    private final QRCodeReader qrCodeReader = new QRCodeReader();
    private long lastDecodeAttemptMs = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_scanner);

        surfaceCameraPreview = findViewById(R.id.surfaceCameraPreview);
        Button btnAutoDiscover = findViewById(R.id.btnAutoDiscoverPairFallback);
        Button btnClose = findViewById(R.id.btnCloseScanner);

        SurfaceHolder holder = surfaceCameraPreview.getHolder();
        holder.addCallback(this);

        btnAutoDiscover.setOnClickListener(v -> {
            Toast.makeText(QrScannerActivity.this, "🔍 Aynı ağdaki öğrenci cihazları taranıyor...", Toast.LENGTH_SHORT).show();
            ParentBackgroundService.scanLanAndSyncChildren(QrScannerActivity.this, true, new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(QrScannerActivity.this, "✅ Ağ taraması tamamlandı ve bulunan öğrenci cihazları eşleştirildi!", Toast.LENGTH_LONG).show();
                    finish();
                }
            });
        });

        btnClose.setOnClickListener(v -> finish());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, 202);
            }
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        startCameraPreview(holder);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        stopCameraPreview();
    }

    private void startCameraPreview(SurfaceHolder holder) {
        try {
            camera = Camera.open();
            if (camera == null) return;
            camera.setDisplayOrientation(90);
            Camera.Parameters params = camera.getParameters();
            List<String> focusModes = params.getSupportedFocusModes();
            if (focusModes != null) {
                if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                    params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                    params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                }
            }
            camera.setParameters(params);
            camera.setPreviewDisplay(holder);
            camera.setPreviewCallback(this);
            camera.startPreview();
        } catch (Exception ignored) {
        }
    }

    private void stopCameraPreview() {
        if (camera != null) {
            try {
                camera.setPreviewCallback(null);
                camera.stopPreview();
                camera.release();
            } catch (Exception ignored) {
            }
            camera = null;
        }
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera cam) {
        if (qrFound) return;
        long now = System.currentTimeMillis();
        if (now - lastDecodeAttemptMs < 200L) return;
        lastDecodeAttemptMs = now;

        try {
            Camera.Size size = cam.getParameters().getPreviewSize();
            if (size == null) return;
            int width = size.width;
            int height = size.height;

            PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(
                    data, width, height, 0, 0, width, height, false);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            Result result = qrCodeReader.decode(bitmap);
            if (result != null && result.getText() != null) {
                handleScannedQrText(result.getText().trim());
            }
        } catch (Exception ignored) {
        } finally {
            qrCodeReader.reset();
        }
    }

    private void handleScannedQrText(String text) {
        // Format: CZPAIR|<ROLE>|<CHILD_ID>|<IP>|<PORT>|<USER_NAME>|<GRADE>
        if (!text.startsWith("CZPAIR|")) {
            return;
        }
        String[] parts = text.split("\\|");
        if (parts.length < 6) return;

        qrFound = true;
        String qrRole = parts[1];
        String childId = parts[2];
        String ip = parts[3];
        String userName = parts[5];
        int grade = 4;
        if (parts.length >= 7) {
            try {
                grade = Integer.parseInt(parts[6]);
            } catch (Exception ignored) {
            }
        }

        // Karekoddaki Anne/Baba rolünü ve öğrenciyi kaydet
        if (!VeliStorage.isOnboarded(this)) {
            VeliStorage.saveParentRole(this, qrRole);
        }
        String activeParentRole = VeliStorage.getParentRole(this);
        VeliStorage.upsertPairedChild(this, childId, userName, grade, ip, activeParentRole);
        ParentBackgroundService.registerParentWithChild(this, ip);

        // IP adresi değişmiş ihtimaline karşı arka planda IP taraması da başlat
        ParentBackgroundService.scanLanAndSyncChildren(this, false, null);

        Intent speakIntent = new Intent(this, ParentBackgroundService.class);
        speakIntent.setAction(ParentBackgroundService.ACTION_SPEAK);
        speakIntent.putExtra(ParentBackgroundService.EXTRA_SPEECH_TEXT, userName + " başarıyla eşleştirildi");
        startService(speakIntent);

        Toast.makeText(this, "🎉 " + userName + " (" + qrRole + " karekodu) başarıyla eşleştirildi!", Toast.LENGTH_LONG).show();
        finish();
    }
}
