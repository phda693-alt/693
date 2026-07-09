package com.pdv.app.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * v8.0.10 - Classe utilitária para gerenciar fotos das Ordens de Serviço
 * Responsável por salvar, comprimir e gerenciar caminhos de fotos
 */
public class PhotoManager {
    private static final String TAG = "PhotoManager";
    private static final int MAX_WIDTH = 1024;
    private static final int MAX_HEIGHT = 1024;
    private static final int COMPRESSION_QUALITY = 85;

    private Context context;
    private File photoDir;

    public PhotoManager(Context context) {
        this.context = context;
        initPhotoDirectory();
    }

    /**
     * Inicializa o diretório de fotos
     */
    private void initPhotoDirectory() {
        try {
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                photoDir = new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_PICTURES), "PDV_Pro/OS_Fotos");
            } else {
                photoDir = new File(context.getFilesDir(), "OS_Fotos");
            }

            if (!photoDir.exists()) {
                photoDir.mkdirs();
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao inicializar diretório de fotos", e);
        }
    }

    /**
     * Salva uma foto comprimida e retorna o caminho
     */
    public String saveBitmap(Bitmap bitmap) {
        if (bitmap == null) return null;

        try {
            // Comprimir a imagem
            Bitmap compressed = compressBitmap(bitmap);

            // Gerar nome único para a foto
            String fileName = "OS_" + System.currentTimeMillis() + ".jpg";
            File photoFile = new File(photoDir, fileName);

            // Salvar arquivo
            try (FileOutputStream fos = new FileOutputStream(photoFile)) {
                compressed.compress(Bitmap.CompressFormat.JPEG, COMPRESSION_QUALITY, fos);
                fos.flush();
            }

            Log.d(TAG, "Foto salva: " + photoFile.getAbsolutePath());
            return photoFile.getAbsolutePath();
        } catch (IOException e) {
            Log.e(TAG, "Erro ao salvar foto", e);
            return null;
        }
    }

    /**
     * Comprime uma imagem para reduzir tamanho
     */
    private Bitmap compressBitmap(Bitmap original) {
        int width = original.getWidth();
        int height = original.getHeight();

        if (width <= MAX_WIDTH && height <= MAX_HEIGHT) {
            return original;
        }

        float scale = Math.min((float) MAX_WIDTH / width, (float) MAX_HEIGHT / height);
        int newWidth = (int) (width * scale);
        int newHeight = (int) (height * scale);

        return Bitmap.createScaledBitmap(original, newWidth, newHeight, true);
    }

    /**
     * Carrega uma foto do caminho
     */
    public Bitmap loadBitmap(String filePath) {
        try {
            return BitmapFactory.decodeFile(filePath);
        } catch (Exception e) {
            Log.e(TAG, "Erro ao carregar foto: " + filePath, e);
            return null;
        }
    }

    /**
     * Deleta uma foto do armazenamento
     */
    public boolean deletePhoto(String filePath) {
        try {
            File file = new File(filePath);
            if (file.exists()) {
                boolean deleted = file.delete();
                Log.d(TAG, "Foto deletada: " + filePath + " - Sucesso: " + deleted);
                return deleted;
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Erro ao deletar foto: " + filePath, e);
            return false;
        }
    }

    /**
     * Retorna o diretório de fotos
     */
    public File getPhotoDirectory() {
        return photoDir;
    }

    /**
     * Gera um nome de arquivo de foto único
     */
    public String generatePhotoFileName() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
        return "OS_" + sdf.format(new Date()) + ".jpg";
    }
}
