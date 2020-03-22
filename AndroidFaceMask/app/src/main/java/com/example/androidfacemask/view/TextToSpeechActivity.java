package com.example.androidfacemask.view;

import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.Locale;

import static com.example.androidfacemask.Config.LOGGING_TAG;


public abstract class TextToSpeechActivity extends CameraActivity implements TextToSpeech.OnInitListener {
    private TextToSpeech textToSpeech;
    private String lastRecognizedClass = "";

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = textToSpeech.setLanguage(Locale.US);
            if (result == TextToSpeech.LANG_MISSING_DATA
                    || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(LOGGING_TAG, "Text to speech error: This Language is not supported");
            }
        } else {
            Log.e(LOGGING_TAG, "Text to speech: Initilization Failed!");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        textToSpeech = new TextToSpeech(this, this);
    }
}
