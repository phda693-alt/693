package com.pdv.app.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import com.pdv.app.R;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Activity de scanner de codigo de barras usando a camera do dispositivo.
 * Utiliza a biblioteca ZXing para decodificacao de codigos de barras.
 * Suporta EAN-13, EAN-8, UPC-A, UPC-E, Code 128, Code 39, QR Code e outros.
 */
@SuppressWarnings("deprecation")
public class BarcodeScannerActivity extends AppCompatActivity implements SurfaceHolder.Callback {
    private static final String TAG = "BarcodeScanner";
    private static final int CAMERA_PERMISSION_REQUEST = 1001;
    public static final String EXTRA_BARCODE_RESULT = "barcode_result";
    public static final String EXTRA_BARCODE_FORMAT = "barcode_format";

    private SurfaceView surfaceView;
    private TextView tvInstrucao;
    private TextView tvUltimoLido;
    private ImageButton btnFlash;
    private ImageButton btnFechar;

    private Camera camera;
    private MultiFormatReader multiFormatReader;
    private boolean isScanning = true;
    private boolean isFlashOn = false;
    private boolean surfaceCreated = false;
    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Fullscreen
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_barcode_scanner);

        surfaceView = findViewById(R.id.surfaceView);
        tvInstrucao = findViewById(R.id.tvInstrucao);
        tvUltimoLido = findViewById(R.id.tvUltimoLido);
        btnFlash = findViewById(R.id.btnFlash);
        btnFechar = findViewById(R.id.btnFechar);

        handler = new Handler(Looper.getMainLooper());

        // Configurar leitor ZXing
        setupReader();

        // Botao flash
        btnFlash.setOnClickListener(v -> toggleFlash());

        // Botao fechar
        btnFechar.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        // Verificar permissao de camera
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
        } else {
            initCamera();
        }
    }

    private void setupReader() {
        multiFormatReader = new MultiFormatReader();
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);

        List<BarcodeFormat> formats = new ArrayList<>();
        formats.add(BarcodeFormat.EAN_13);
        formats.add(BarcodeFormat.EAN_8);
        formats.add(BarcodeFormat.UPC_A);
        formats.add(BarcodeFormat.UPC_E);
        formats.add(BarcodeFormat.CODE_128);
        formats.add(BarcodeFormat.CODE_39);
        formats.add(BarcodeFormat.CODE_93);
        formats.add(BarcodeFormat.CODABAR);
        formats.add(BarcodeFormat.ITF);
        formats.add(BarcodeFormat.QR_CODE);
        formats.add(BarcodeFormat.DATA_MATRIX);

        hints.put(DecodeHintType.POSSIBLE_FORMATS, formats);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        multiFormatReader.setHints(hints);
    }

    private void initCamera() {
        SurfaceHolder holder = surfaceView.getHolder();
        holder.addCallback(this);
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        surfaceCreated = true;
        startCamera(holder);
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
        if (camera != null) {
            try {
                camera.stopPreview();
            } catch (Exception ignored) {}
            startCamera(holder);
        }
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        surfaceCreated = false;
        releaseCamera();
    }

    private void startCamera(SurfaceHolder holder) {
        try {
            if (camera == null) {
                camera = Camera.open();
            }

            Camera.Parameters params = camera.getParameters();

            // Configurar foco automatico continuo
            List<String> focusModes = params.getSupportedFocusModes();
            if (focusModes != null) {
                if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                    params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                    params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
                } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                    params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                }
            }

            // Configurar resolucao de preview
            Camera.Size bestSize = getBestPreviewSize(params);
            if (bestSize != null) {
                params.setPreviewSize(bestSize.width, bestSize.height);
            }

            camera.setParameters(params);
            camera.setDisplayOrientation(90);
            camera.setPreviewDisplay(holder);
            camera.startPreview();

            // Iniciar decodificacao continua
            startDecoding();

        } catch (Exception e) {
            Log.e(TAG, "Erro ao iniciar camera: " + e.getMessage(), e);
            tvInstrucao.setText("Erro ao acessar a camera.\nVerifique as permissoes.");
        }
    }

    private Camera.Size getBestPreviewSize(Camera.Parameters params) {
        Camera.Size bestSize = null;
        int targetPixels = 1280 * 720;
        int minDiff = Integer.MAX_VALUE;

        for (Camera.Size size : params.getSupportedPreviewSizes()) {
            int pixels = size.width * size.height;
            int diff = Math.abs(pixels - targetPixels);
            if (diff < minDiff) {
                minDiff = diff;
                bestSize = size;
            }
        }
        return bestSize;
    }

    private void startDecoding() {
        if (camera == null || !isScanning) return;

        camera.setOneShotPreviewCallback((data, cam) -> {
            if (!isScanning || cam == null) return;

            new Thread(() -> {
                try {
                    Camera.Parameters params = cam.getParameters();
                    if (params == null) return;
                    Camera.Size size = params.getPreviewSize();
                    if (size == null || data == null) return;

                    // Rotacionar dados para orientacao retrato
                    byte[] rotatedData = rotateYUV90(data, size.width, size.height);
                    int rotatedWidth = size.height;
                    int rotatedHeight = size.width;

                    PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(
                            rotatedData, rotatedWidth, rotatedHeight,
                            0, 0, rotatedWidth, rotatedHeight, false);

                    BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

                    try {
                        Result result = multiFormatReader.decodeWithState(bitmap);
                        if (result != null && result.getText() != null && !result.getText().isEmpty()) {
                            String barcodeText = result.getText().trim();
                            String formatName = result.getBarcodeFormat().name();

                            handler.post(() -> onBarcodeDetected(barcodeText, formatName));
                            return;
                        }
                    } catch (Exception ignored) {
                        // Nenhum codigo encontrado neste frame - continuar
                    } finally {
                        multiFormatReader.reset();
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Erro na decodificacao: " + e.getMessage());
                }

                // Continuar escaneando
                handler.postDelayed(() -> {
                    if (isScanning && camera != null) {
                        startDecoding();
                    }
                }, 100);
            }).start();
        });
    }

    /**
     * Rotaciona dados YUV420 em 90 graus para orientacao retrato.
     */
    private byte[] rotateYUV90(byte[] data, int width, int height) {
        byte[] rotated = new byte[data.length];
        int area = width * height;

        // Rotacionar plano Y
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                rotated[x * height + (height - y - 1)] = data[y * width + x];
            }
        }

        // Rotacionar planos UV (interleaved)
        int uvHeight = height / 2;
        int uvWidth = width / 2;
        for (int y = 0; y < uvHeight; y++) {
            for (int x = 0; x < uvWidth; x++) {
                int srcIdx = area + (y * uvWidth + x) * 2;
                int dstIdx = area + ((x * uvHeight + (uvHeight - y - 1)) * 2);
                if (srcIdx + 1 < data.length && dstIdx + 1 < rotated.length) {
                    rotated[dstIdx] = data[srcIdx];
                    rotated[dstIdx + 1] = data[srcIdx + 1];
                }
            }
        }

        return rotated;
    }

    private void onBarcodeDetected(String barcode, String format) {
        isScanning = false;

        // Feedback visual
        tvUltimoLido.setVisibility(View.VISIBLE);
        tvUltimoLido.setText("Lido: " + barcode + " (" + format + ")");
        tvInstrucao.setText("Codigo detectado!");

        // Vibrar se possivel
        try {
            android.os.Vibrator vibrator = (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(200);
            }
        } catch (Exception ignored) {}

        // Retornar resultado apos breve delay visual
        handler.postDelayed(() -> {
            Intent resultIntent = new Intent();
            resultIntent.putExtra(EXTRA_BARCODE_RESULT, barcode);
            resultIntent.putExtra(EXTRA_BARCODE_FORMAT, format);
            setResult(RESULT_OK, resultIntent);
            finish();
        }, 500);
    }

    private void toggleFlash() {
        if (camera == null) return;
        try {
            Camera.Parameters params = camera.getParameters();
            if (isFlashOn) {
                params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                isFlashOn = false;
            } else {
                params.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                isFlashOn = true;
            }
            camera.setParameters(params);
        } catch (Exception e) {
            Log.w(TAG, "Erro ao alternar flash: " + e.getMessage());
        }
    }

    private void releaseCamera() {
        isScanning = false;
        if (camera != null) {
            try {
                camera.setOneShotPreviewCallback(null);
                camera.stopPreview();
                camera.release();
            } catch (Exception e) {
                Log.w(TAG, "Erro ao liberar camera: " + e.getMessage());
            }
            camera = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initCamera();
            } else {
                tvInstrucao.setText("Permissao de camera negada.\nO scanner precisa da camera para funcionar.");
                handler.postDelayed(() -> {
                    setResult(RESULT_CANCELED);
                    finish();
                }, 2000);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        releaseCamera();
    }

    @Override
    protected void onResume() {
        super.onResume();
        isScanning = true;
        if (surfaceCreated && camera == null) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED) {
                startCamera(surfaceView.getHolder());
            }
        }
    }

    @Override
    protected void onDestroy() {
        releaseCamera();
        super.onDestroy();
    }
}
