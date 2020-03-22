package com.example.androidfacemask.facemask;

import android.graphics.Point;
import android.graphics.Rect;

import static java.lang.Math.max;

/**
 * 人脸框
 */
public class Box {
    public float[] box;         // left:box[0],top:box[1],right:box[2],bottom:box[3]
    public float score;         // probability
    public int index;
    public boolean deleted;
    public int cls;
    public String title;

    public Box() {
        box = new float[4];
        deleted = false;
        title = "";
    }

    public float left() {
        return box[0];
    }

    public float right() {
        return box[2];
    }

    public float top() {
        return box[1];
    }

    public float bottom() {
        return box[2];
    }

    public float width() {
        return box[2] - box[0] + 1;
    }

    public float height() {
        return box[3] - box[1] + 1;
    }

    // 转为rect
    public Rect transform2Rect() {
        Rect rect = new Rect();
        rect.left = (int)box[0];
        rect.top = (int)box[1];
        rect.right = (int)box[2];
        rect.bottom = (int)box[3];
        return rect;
    }

    // 面积
    public int area() {
        return (int)(width() * height());
    }
}
