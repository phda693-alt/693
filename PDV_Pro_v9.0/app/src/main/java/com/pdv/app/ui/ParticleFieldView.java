package com.pdv.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/**
 * Campo de particulas flutuantes em fundo holografico/futurista.
 *
 * <p>Renderiza ~40 particulas (configuravel) em movimento sutil
 * subindo, com piscar suave de alpha. Usado como background
 * decorativo das telas Splash/Login do PDV Pro.</p>
 *
 * <p>Implementacao 100% nativa (Canvas) sem dependencias. Uso de
 * memoria baixissimo (apenas arrays de floats). Auto-pause quando
 * detached.</p>
 */
public class ParticleFieldView extends View {

    private static final int PARTICLE_COUNT = 42;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rnd = new Random(0xC0FFEEL);

    private float[] xs = new float[PARTICLE_COUNT];
    private float[] ys = new float[PARTICLE_COUNT];
    private float[] vy = new float[PARTICLE_COUNT];
    private float[] r  = new float[PARTICLE_COUNT];
    private float[] phase = new float[PARTICLE_COUNT];
    private int[]   colors = new int[PARTICLE_COUNT];

    private boolean running = false;
    private long lastFrameNs = 0L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable frame = new Runnable() {
        @Override public void run() {
            if (!running) return;
            long now = System.nanoTime();
            float dt = lastFrameNs == 0L ? 0.016f : Math.min(0.05f, (now - lastFrameNs) / 1_000_000_000f);
            lastFrameNs = now;
            int w = getWidth(), h = getHeight();
            if (w == 0 || h == 0) {
                handler.postDelayed(this, 16L);
                return;
            }
            for (int i = 0; i < PARTICLE_COUNT; i++) {
                ys[i] -= vy[i] * dt;
                phase[i] += dt * 1.6f;
                if (ys[i] < -r[i] * 4f) {
                    // respawn at bottom
                    ys[i] = h + r[i] * 4f;
                    xs[i] = rnd.nextFloat() * w;
                }
            }
            invalidate();
            handler.postDelayed(this, 16L);
        }
    };

    public ParticleFieldView(Context ctx) { super(ctx); }
    public ParticleFieldView(Context ctx, AttributeSet a) { super(ctx, a); }
    public ParticleFieldView(Context ctx, AttributeSet a, int s) { super(ctx, a, s); }

    private void seed(int w, int h) {
        int[] palette = {
                0xFF00E5FF, 0xFF00BCD4, 0xFFFFD740,
                0xFFFF1493, 0xFFFFFFFF, 0xFF39FF14
        };
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            xs[i] = rnd.nextFloat() * w;
            ys[i] = rnd.nextFloat() * h;
            vy[i] = (10 + rnd.nextFloat() * 30) * getResources().getDisplayMetrics().density;
            r[i]  = (1.0f + rnd.nextFloat() * 1.8f) * getResources().getDisplayMetrics().density;
            phase[i] = rnd.nextFloat() * (float) (Math.PI * 2);
            colors[i] = palette[rnd.nextInt(palette.length)];
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        seed(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            float a = 0.35f + 0.65f * (float) (0.5f + 0.5f * Math.sin(phase[i]));
            paint.setColor(colors[i]);
            paint.setAlpha((int) (255 * a));
            canvas.drawCircle(xs[i], ys[i], r[i], paint);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        running = true;
        lastFrameNs = 0L;
        handler.post(frame);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        running = false;
        handler.removeCallbacks(frame);
    }
}
