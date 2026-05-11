package com.ufla.visionproject;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class EpipolarOverlayView extends View {
    private final List<PointF> points = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public EpipolarOverlayView(Context context) {
        super(context);
        setBackgroundColor(Color.rgb(18, 18, 22));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_DOWN) return true;

        float half = getWidth() / 2f;
        float x = Math.min(event.getX(), half - 8);
        float y = event.getY();

        if (points.size() >= 5) points.clear();
        points.add(new PointF(x, y));
        invalidate();
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        float half = w / 2f;

        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(30f);
        paint.setColor(Color.WHITE);
        canvas.drawText("Imagem 1: pontos", 18, 38, paint);
        canvas.drawText("Imagem 2: linhas epipolares", half + 18, 38, paint);

        paint.setColor(Color.GRAY);
        paint.setStrokeWidth(3f);
        canvas.drawLine(half, 0, half, h, paint);

        paint.setTextSize(22f);
        paint.setColor(Color.LTGRAY);
        canvas.drawText("Modelo retificado: l' = Fp → linha quase horizontal", 18, h - 18, paint);

        int[] colors = {
                Color.rgb(255, 82, 82),
                Color.rgb(255, 193, 7),
                Color.rgb(76, 175, 80),
                Color.rgb(3, 169, 244),
                Color.rgb(186, 104, 200)
        };

        for (int i = 0; i < points.size(); i++) {
            PointF p = points.get(i);
            paint.setColor(colors[i % colors.length]);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(p.x, p.y, 10f, paint);
            canvas.drawText("p" + (i + 1), p.x + 12, p.y - 10, paint);

            float yRight = p.y;
            float slope = (i - 2) * 0.035f;
            float x1 = half + 10;
            float x2 = w - 10;
            float y1 = yRight - slope * 120;
            float y2 = yRight + slope * 120;

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(5f);
            canvas.drawLine(x1, y1, x2, y2, paint);
        }
    }
}
