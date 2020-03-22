package com.example.androidfacemask.view;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.media.Image;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Environment;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;

import com.example.androidfacemask.R;
import com.example.androidfacemask.facemask.FaceMask;
import com.example.androidfacemask.facemask.Box;
import com.example.androidfacemask.util.ImageUtils;
import com.example.androidfacemask.view.components.BorderedText;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Vector;

import static com.example.androidfacemask.Config.LOGGING_TAG;

public class FaceActivity extends com.example.androidfacemask.view.TextToSpeechActivity implements OnImageAvailableListener {
    private boolean MAINTAIN_ASPECT = true;
    private float TEXT_SIZE_DIP = 10;

    private Integer sensorOrientation;
    private int previewWidth = 0;
    private int previewHeight = 0;
    private Bitmap croppedBitmap = null;
    private boolean computing = false;
    private Matrix frameToCropTransform;

    private OverlayView overlayView;
    private BorderedText borderedText;
    private long lastProcessingTimeMs;

    private FaceMask facemask; // 口罩检测

    @Override
    public void onPreviewSizeChosen(final Size size, final int rotation) {
        final float textSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                TEXT_SIZE_DIP, getResources().getDisplayMetrics());
        borderedText = new BorderedText(textSizePx);
        borderedText.setTypeface(Typeface.MONOSPACE);

        overlayView = findViewById(R.id.overlay);
        previewWidth = size.getWidth();
        previewHeight = size.getHeight();

        final int screenOrientation = getWindowManager().getDefaultDisplay().getRotation();

        Log.i(LOGGING_TAG, String.format("Sensor orientation: %d, Screen orientation: %d",
                rotation, screenOrientation));

        sensorOrientation = rotation + screenOrientation;

        Log.i(LOGGING_TAG, String.format("Initializing at size %dx%d", previewWidth, previewHeight));

        croppedBitmap = Bitmap.createBitmap(FaceMask.INPUT_IMAGE_SIZE, FaceMask.INPUT_IMAGE_SIZE, Config.ARGB_8888);

        frameToCropTransform = ImageUtils.getTransformationMatrix(previewWidth, previewHeight,
                FaceMask.INPUT_IMAGE_SIZE, FaceMask.INPUT_IMAGE_SIZE, sensorOrientation, MAINTAIN_ASPECT);
        frameToCropTransform.invert(new Matrix());

        try {
            facemask = new FaceMask(getAssets());
        } catch (IOException e) {
            e.printStackTrace();
        }

        addCallback((final Canvas canvas) -> renderAdditionalInformation(canvas));
    }

    @Override
    public void onImageAvailable(final ImageReader reader) {
        Image image = null;

        try {
            image = reader.acquireLatestImage();

            if (image == null) {
                return;
            }

            if (computing) {
                image.close();
                return;
            }

            computing = true;
            fillCroppedBitmap(image);
            image.close();
        } catch (final Exception ex) {
            if (image != null) {
                image.close();
            }
            Log.e(LOGGING_TAG, ex.getMessage());
        }

        runInBackground(() -> {
            final long startTime = SystemClock.uptimeMillis();
            Vector<Box> facemask_boxes = null;
            try {
                facemask_boxes = facemask.detectFaceMasks(croppedBitmap);
            } catch (Exception e) {
                e.printStackTrace();
            }
            lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;
            if (facemask_boxes != null) {
                overlayView.setResults(facemask_boxes);
            } else {
                overlayView.setResults(null);
            }
            requestRender();
            computing = false;
        });
    }

    private void fillCroppedBitmap(final Image image) {
        Bitmap rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
        rgbFrameBitmap.setPixels(ImageUtils.convertYUVToARGB(image, previewWidth, previewHeight),
                0, previewWidth, 0, 0, previewWidth, previewHeight);
        new Canvas(croppedBitmap).drawBitmap(rgbFrameBitmap, frameToCropTransform, null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private void renderAdditionalInformation(final Canvas canvas) {
        final Vector<String> lines = new Vector();

        lines.add("Frame: " + previewWidth + "x" + previewHeight);
        lines.add("View: " + canvas.getWidth() + "x" + canvas.getHeight());
        lines.add("Rotation: " + sensorOrientation);
        lines.add("Inference time: " + lastProcessingTimeMs + "ms");

        borderedText.drawLines(canvas, 10, 10, lines);
    }
}
