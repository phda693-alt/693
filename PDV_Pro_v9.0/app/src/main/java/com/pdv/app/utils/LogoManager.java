package com.pdv.app.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/**
 * LogoManager - Gerencia o logo do cupom fiscal.
 * Salva, carrega e remove o logo escolhido pelo usuario.
 * O logo é armazenado como arquivo PNG no diretório interno do app.
 */
public class LogoManager {
    private static final String LOGO_FILE_NAME = "cupom_logo.png";

    /**
     * Salva o logo no armazenamento interno do app.
     */
    public static void salvarLogo(Context context, Bitmap bitmap) {
        try {
            File file = getLogoFile(context);
            FileOutputStream fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
            fos.close();
        } catch (Exception e) {
            android.util.Log.e("LogoManager", "Erro ao salvar logo: " + e.getMessage());
        }
    }

    /**
     * Carrega o logo do armazenamento interno.
     * Retorna null se nenhum logo estiver salvo.
     */
    public static Bitmap carregarLogo(Context context) {
        try {
            File file = getLogoFile(context);
            if (!file.exists()) return null;
            FileInputStream fis = new FileInputStream(file);
            Bitmap bitmap = BitmapFactory.decodeStream(fis);
            fis.close();
            return bitmap;
        } catch (Exception e) {
            android.util.Log.e("LogoManager", "Erro ao carregar logo: " + e.getMessage());
            return null;
        }
    }

    /**
     * Remove o logo salvo.
     */
    public static void removerLogo(Context context) {
        File file = getLogoFile(context);
        if (file.exists()) {
            file.delete();
        }
    }

    /**
     * Verifica se existe um logo salvo.
     */
    public static boolean temLogo(Context context) {
        return getLogoFile(context).exists();
    }

    /**
     * Redimensiona o bitmap mantendo a proporção.
     * @param bitmap Bitmap original
     * @param maxWidth Largura máxima em pixels
     */
    public static Bitmap redimensionarLogo(Bitmap bitmap, int maxWidth) {
        if (bitmap == null) return null;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        if (width <= maxWidth) return bitmap;

        float ratio = (float) maxWidth / width;
        int newHeight = (int) (height * ratio);
        return Bitmap.createScaledBitmap(bitmap, maxWidth, newHeight, true);
    }

    private static File getLogoFile(Context context) {
        return new File(context.getFilesDir(), LOGO_FILE_NAME);
    }
}
