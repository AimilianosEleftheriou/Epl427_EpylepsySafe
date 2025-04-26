package com.med.health_app;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.media.MediaRecorder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

public class MeasurementWorker extends Worker {

    private MediaRecorder mediaRecorder;
    private SensorManager sensorManager;
    private DatabaseHelper dbHelper;
    private List<Float> lightValues = new ArrayList<>();

    public MeasurementWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        dbHelper = new DatabaseHelper(context);
    }

    @NonNull
    @Override
    public Result doWork() {
        float light = getLightAverage();
        float sound = getSoundAverage();

        dbHelper.insertMeasurementData(light, sound, getCurrentTimestamp());
        Log.d("MeasurementWorker", "Auto test saved: Light=" + light + ", Sound=" + sound);

        return Result.success();
    }

    private float getSoundAverage() {
        float total = 0;
        int count = 0;
        try {
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mediaRecorder.setOutputFile(getApplicationContext().getCacheDir().getAbsolutePath() + "/temp_auto.3gp");

            mediaRecorder.prepare();
            mediaRecorder.start();

            for (int i = 0; i < 10; i++) {
                Thread.sleep(1000);
                int amplitude = mediaRecorder.getMaxAmplitude();
                if (amplitude > 0) {
                    float db = (float) (20 * Math.log10((double) amplitude / 32767.0) + 90);
                    total += db;
                    count++;
                }
            }

            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return count > 0 ? total / count : 0f;
    }

    private float getLightAverage() {
        // Optional: read from a service, sensor cache, or fake value for now
        return 0f; // Stub: sensor access not allowed in background without foreground service
    }

    private String getCurrentTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date());
    }
}

