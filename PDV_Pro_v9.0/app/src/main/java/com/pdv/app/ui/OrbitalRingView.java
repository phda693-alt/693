package com.pdv.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;

/**
 * Aneis orbitais animados em estilo holografico/futurista para o
 * PDV Pro. Renderiza dois aneis girando em direcoes opostas com
 * gradiente sweep neon, mais um circulo central pulsante.
 *
 * <p>Custom View pura (sem dependencia de XML adicional). Pode ser
 * adicionada em qualquer layout ou programaticamente.</p>
 *
 * <p>Performance: usa apenas Canvas e Paint, ~60fps em qualquer
 * dispositivo Android 5+. Para reduzido consumo de bateria, a
 * animacao para automaticamente quando a view sai da tela
 * ({@link #onDetachedFromWindow()}).</p>
 */
public class OrbitalRingView extends View {

    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring2Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint corePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect1 = new RectF();
    private final RectF rect2 = new RectF();

    private float rotation = 0f;
    private float rotation2 = 0f;
    private long lastFrameNs = 0L;
    private boolean running = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable frameRunnable = new Runnable() {
        @Override public void run() {
            if (!running) return;
            long now = System.nanoTime();
            float dt = lastFrameNs == 0L ? 0.016f : Math.min(0.05f, (now - lastFrameNs) / 1_000_000_000f);
            lastFrameNs = now;
            rotation = (rotation + 60f * dt) % 360f;     // 60 graus/seg
            rotation2 = (rotation2 - 35f * dt) % 360f;   // contra-rotacao mais lenta
            invalidate();
            handler.postDelayed(this, 16L); // ~60 fps
        }
    };

    public OrbitalRingView(Context ctx) { super(ctx); init(); }
    public OrbitalRingView(Context ctx, AttributeSet a) { super(ctx, a); init(); }
    public OrbitalRingView(Context ctx, AttributeSet a, int s) { super(ctx, a, s); init(); }

    private void init() {
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeCap(Paint.Cap.ROUND);
        ring2Paint.setStyle(Paint.Style.STROKE);
        ring2Paint.setStrokeCap(Paint.Cap.ROUND);
        corePaint.setStyle(Paint.Style.FILL);
        tickPaint.setStyle(Paint.Style.STROKE);
        tickPaint.setStrokeCap(Paint.Cap.ROUND);
        tickPaint.setColor(Color.parseColor("#A000E5FF"));
        // Inicia animacao apenas quando attached
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float cx = w / 2f, cy = h / 2f;
        float rOuter = Math.min(w, h) / 2f - dp(4);
        float rInner = rOuter * 0.78f;
        rect1.set(cx - rOuter, cy - rOuter, cx + rOuter, cy + rOuter);
        rect2.set(cx - rInner, cy - rInner, cx + rInner, cy + rInner);

        Shader sweep1 = new SweepGradient(cx, cy,
                new int[]{0x0000E5FF, 0xFF00E5FF, 0xFFFF1493, 0x0000E5FF},
                new float[]{0f, 0.5f, 0.85f, 1f});
        ringPaint.setShader(sweep1);
        ringPaint.setStrokeWidth(dp(2.4f));

        Shader sweep2 = new SweepGradient(cx, cy,
                new int[]{0x00FFD740, 0xFFFFD740, 0xFF00BCD4, 0x00FFD740},
                new float[]{0f, 0.5f, 0.85f, 1f});
        ring2Paint.setShader(sweep2);
        ring2Paint.setStrokeWidth(dp(1.6f));

        tickPaint.setStrokeWidth(dp(1.2f));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f, cy = getHeight() / 2f;

        // Outer ring rotating clockwise
        canvas.save();
        canvas.rotate(rotation, cx, cy);
        canvas.drawArc(rect1, 0f, 360f, false, ringPaint);
        canvas.restore();

        // Inner ring counter-rotating
        canvas.save();
        canvas.rotate(rotation2, cx, cy);
        canvas.drawArc(rect2, 0f, 360f, false, ring2Paint);
        canvas.restore();

        // Tick marks (12 around the outer ring, static)
        float rOut = (rect1.width() / 2f);
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12 - Math.PI / 2;
            float x1 = cx + (float) Math.cos(a) * (rOut - dp(2));
            float y1 = cy + (float) Math.sin(a) * (rOut - dp(2));
            float x2 = cx + (float) Math.cos(a) * (rOut - dp(8));
            float y2 = cy + (float) Math.sin(a) * (rOut - dp(8));
            canvas.drawLine(x1, y1, x2, y2, tickPaint);
        }

        // Central glow pulse
        float pulse = 1f + 0.06f * (float) Math.sin(System.currentTimeMillis() / 250.0);
        corePaint.setColor(0x3000E5FF);
        canvas.drawCircle(cx, cy, dp(40) * pulse, corePaint);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        running = true;
        lastFrameNs = 0L;
        handler.post(frameRunnable);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        running = false;
        handler.removeCallbacks(frameRunnable);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
