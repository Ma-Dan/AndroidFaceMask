package com.example.androidfacemask.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import com.example.androidfacemask.Config;

import com.example.androidfacemask.facemask.Box;

import java.util.LinkedList;
import java.util.List;

public class OverlayView extends View {
    private final Paint paint;
    private final List<DrawCallback> callbacks = new LinkedList();
    private List<Box> results;

    public static final int IMAGE_WIDTH = 1;
    public static final int IMAGE_HEIGHT = 1;

    public OverlayView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        paint = new Paint();
        paint.setColor(Color.GREEN);
        paint.setStyle(Paint.Style.STROKE);
        paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                15, getResources().getDisplayMetrics()));
    }

    public void addCallback(final DrawCallback callback) {
        callbacks.add(callback);
    }

    @Override
    public synchronized void onDraw(final Canvas canvas) {
        for (final DrawCallback callback : callbacks) {
            callback.drawCallback(canvas);
        }

        if (results != null) {
            for (int i = 0; i < results.size(); i++) {
                if(results.get(i).deleted) {
                    continue;
                }
                RectF box = reCalcSize(results.get(i));
                String title = String.format("%s:%f", results.get(i).title, results.get(i).score);
                if(results.get(i).cls == 0) {
                    paint.setColor(0xff00ff00);
                } else {
                    paint.setColor(0xffff0000);
                }
                canvas.drawRect(box, paint);
                canvas.drawText(title, box.left, box.top, paint);
            }
        }
    }

    public void setResults(final List<Box> results) {
        this.results = results;
        postInvalidate();
    }

    private RectF reCalcSize(Box box) {
        int padding = 5;
        float overlayViewHeight = this.getHeight();
        float sizeMultiplier = Math.min((float) this.getWidth() / (float) IMAGE_WIDTH,
                overlayViewHeight / (float) IMAGE_HEIGHT);

        float offsetX = (this.getWidth() - IMAGE_WIDTH * sizeMultiplier) / 2;
        float offsetY = (overlayViewHeight - IMAGE_HEIGHT * sizeMultiplier) / 2;

        float left = Math.max(padding, sizeMultiplier * box.box[0] + offsetX);
        float top = Math.max(offsetY + padding, sizeMultiplier * box.box[1] + offsetY);

        float right = Math.min(box.box[2] * sizeMultiplier, this.getWidth() - padding);
        float bottom = Math.min(box.box[3] * sizeMultiplier + offsetY, this.getHeight() - padding);

        return new RectF(left, top, right, bottom);
    }

    /**
     * Interface defining the callback for client classes.
     */
    public interface DrawCallback {
        void drawCallback(final Canvas canvas);
    }
}
