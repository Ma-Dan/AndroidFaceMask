package com.example.androidfacemask.facemask;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import com.example.androidfacemask.util.ImageUtils;

import static com.example.androidfacemask.Config.LOGGING_TAG;

/**
 * 口罩检测
 */
public class FaceMask {
    private static final String MODEL_FILE = "face_mask_detection.tflite";

    public static final int INPUT_IMAGE_SIZE = 260; // 需要feed数据的placeholder的图片宽高
    public static final float CONF_THRESHOLD = 0.5f; // 置信度阈值
    public static final float IOU_THRESHOLD = 0.4f; // IoU阈值

    private static final int[] feature_map_sizes = new int[] {33, 17, 9, 5, 3};
    private static final float[][] anchor_sizes = new float[][] {{0.04f, 0.056f}, {0.08f, 0.11f}, {0.16f, 0.22f}, {0.32f, 0.45f}, {0.64f, 0.72f}};
    private static final float[] anchor_ratios = new float[] {1.0f, 0.62f, 0.42f};

    private float[][] anchors;

    private Interpreter interpreter;

    public FaceMask(AssetManager assetManager) throws IOException {
        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(4);
        interpreter = new Interpreter(ImageUtils.loadModelFile(assetManager, MODEL_FILE), options);
        generateAnchors();
    }

    public Vector<Box> detectFaceMasks(Bitmap bitmap) {
        int[] ddims = {1, INPUT_IMAGE_SIZE, INPUT_IMAGE_SIZE, 3};
        float[][][][] datasets = new float[ddims[0]][ddims[1]][ddims[2]][ddims[3]];

        datasets[0] = ImageUtils.normalizeImage(bitmap);

        float[][][] loc = new float[1][5972][4];
        float[][][] cls = new float[1][5972][2];

        Map<Integer, Object> outputs = new HashMap<>();
        outputs.put(interpreter.getOutputIndex("loc_branch_concat_1/concat"), loc);
        outputs.put(interpreter.getOutputIndex("cls_branch_concat_1/concat"), cls);
        interpreter.runForMultipleInputsOutputs(new Object[]{datasets}, outputs);

        //先通过score筛选Box，减少后续计算
        Vector<Box> filteredBoxes = new Vector<>();
        for(int i=0; i<5972; i++) {
            int idxCls = -1;
            if(cls[0][i][0] > cls[0][i][1]) {
                idxCls = 0;
            } else {
                idxCls = 1;
            }

            if(cls[0][i][idxCls] > CONF_THRESHOLD) {
                Box box = new Box();
                // core
                box.score = cls[0][i][idxCls];
                // box
                box.box[0] = loc[0][i][0];
                box.box[1] = loc[0][i][1];
                box.box[2] = loc[0][i][2];
                box.box[3] = loc[0][i][3];

                box.cls = idxCls;

                if(idxCls == 0) {
                    box.title = "有口罩";
                } else {
                    box.title = "无口罩";
                }

                box.index = i;

                filteredBoxes.add(box);
            }
        }

        //解码Box参数
        decodeBBox(filteredBoxes);

        //NMS
        nms(filteredBoxes, IOU_THRESHOLD, "Union");

        //Log.i(LOGGING_TAG, String.format("Detected: %d", filteredBoxes.size()));

        return filteredBoxes;
    }

    private void generateAnchors() {
        int anchorTotal = 0;
        for(int i=0; i<5; i++) {
            anchorTotal += feature_map_sizes[i] * feature_map_sizes[i];
        }
        anchorTotal *= 4;

        anchors = new float[anchorTotal][4];

        int index = 0;
        for(int i=0; i<5; i++) {
            float center[] = new float[feature_map_sizes[i]];
            for(int j=0; j<feature_map_sizes[i]; j++) {
                center[j] = 1.0f * (float)(-feature_map_sizes[i]/2 + j) / (float)feature_map_sizes[i] + 0.5f;
            }
            float offset[][] = new float[4][4];
            for(int j=0; j<2; j++) {
                float ratio = anchor_ratios[0];
                float width = anchor_sizes[i][j] * (float)Math.sqrt((double)ratio);
                float height = anchor_sizes[i][j] / (float)Math.sqrt((double)ratio);
                offset[j] = new float[] {-width / 2.0f, -height / 2.0f, width / 2.0f, height / 2.0f};
            }
            for(int j=0; j<2; j++) {
                float s1 = anchor_sizes[i][0];
                float ratio = anchor_ratios[1+j];
                float width = anchor_sizes[i][j] * (float)Math.sqrt((double)ratio);
                float height = anchor_sizes[i][j] / (float)Math.sqrt((double)ratio);
                offset[2+j] = new float[] {-width / 2.0f, -height / 2.0f, width / 2.0f, height / 2.0f};
            }
            for(int y=0; y<feature_map_sizes[i]; y++) {
                for(int x=0; x<feature_map_sizes[i]; x++) {
                    for(int j=0; j<4; j++) {
                        anchors[index] = new float[]{center[x]+offset[j][0], center[y]+offset[j][1], center[x]+offset[j][2], center[y]+offset[j][3]};
                        index++;
                    }
                }
            }
        }
    }

    private void decodeBBox(Vector<Box> boxes) {
        for(int i=0; i<boxes.size(); i++) {
            Box box = boxes.get(i);
            float anchor_center_x = (anchors[box.index][0] + anchors[box.index][2])/2;
            float anchor_center_y = (anchors[box.index][1] + anchors[box.index][3])/2;
            float anchor_w = anchors[box.index][2] - anchors[box.index][0];
            float anchor_h = anchors[box.index][3] - anchors[box.index][1];

            float predict_center_x = box.box[0] * 0.1f * anchor_w + anchor_center_x;
            float predict_center_y = box.box[1] * 0.1f * anchor_h + anchor_center_y;
            float predict_w = (float)Math.exp((double)box.box[2] * 0.2) * anchor_w;
            float predict_h = (float)Math.exp((double)box.box[3] * 0.2) * anchor_h;

            box.box[0] = predict_center_x - predict_w / 2;
            box.box[1] = predict_center_y - predict_h / 2;
            box.box[2] = predict_center_x + predict_w / 2;
            box.box[3] = predict_center_y + predict_h / 2;
        }
    }

    private void nms(Vector<Box> boxes, float threshold, String method) {
        // NMS.两两比对
        // int delete_cnt = 0;
        for (int i = 0; i < boxes.size(); i++) {
            Box box = boxes.get(i);
            if (!box.deleted) {
                // score<0表示当前矩形框被删除
                for (int j = i + 1; j < boxes.size(); j++) {
                    Box box2 = boxes.get(j);
                    if ((!box2.deleted) && (box2.cls==box.cls)) {
                        float x1 = Math.max(box.box[0], box2.box[0]);
                        float y1 = Math.max(box.box[1], box2.box[1]);
                        float x2 = Math.min(box.box[2], box2.box[2]);
                        float y2 = Math.min(box.box[3], box2.box[3]);
                        if (x2 < x1 || y2 < y1) continue;
                        float areaIoU = (x2 - x1 + 1) * (y2 - y1 + 1);
                        float iou = 0f;
                        if (method.equals("Union"))
                            iou = 1.0f * areaIoU / (box.area() + box2.area() - areaIoU);
                        else if (method.equals("Min"))
                            iou = 1.0f * areaIoU / (Math.min(box.area(), box2.area()));
                        if (iou >= threshold) { // 删除prob小的那个框
                            if (box.score > box2.score)
                                box2.deleted = true;
                            else
                                box.deleted = true;
                        }
                    }
                }
            }
        }
    }
}
