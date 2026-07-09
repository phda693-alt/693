package com.phda.phserver;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.phda.phserver.qr.QrCode;

/**
 * Helper que pega uma string e devolve um Bitmap Android com o QR Code
 * correspondente, usando a biblioteca Nayuki QR-Code-generator (MIT)
 * embutida em com.phda.phserver.qr.
 */
final class QrBitmap {

    private QrBitmap() {}

    /**
     * Renderiza um QR Code para o texto dado.
     *
     * @param text       texto a codificar (URI, URL, etc)
     * @param sizePx     largura/altura desejada do bitmap em pixels
     * @param marginCells modulos brancos de borda (tipicamente 4)
     */
    static Bitmap encode(String text, int sizePx, int marginCells) {
        QrCode qr = QrCode.encodeText(text, QrCode.Ecc.MEDIUM);
        int qrSize = qr.size;
        int totalCells = qrSize + 2 * marginCells;
        int cellPx = Math.max(1, sizePx / totalCells);
        int realSize = cellPx * totalCells;

        Bitmap bmp = Bitmap.createBitmap(realSize, realSize, Bitmap.Config.ARGB_8888);
        bmp.eraseColor(Color.WHITE);
        for (int y = 0; y < qrSize; y++) {
            for (int x = 0; x < qrSize; x++) {
                if (qr.getModule(x, y)) {
                    int sx = (x + marginCells) * cellPx;
                    int sy = (y + marginCells) * cellPx;
                    for (int dy = 0; dy < cellPx; dy++) {
                        for (int dx = 0; dx < cellPx; dx++) {
                            bmp.setPixel(sx + dx, sy + dy, Color.BLACK);
                        }
                    }
                }
            }
        }
        return bmp;
    }
}
