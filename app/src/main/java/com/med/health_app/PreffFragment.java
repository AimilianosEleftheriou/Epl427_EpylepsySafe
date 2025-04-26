package com.med.health_app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import android.os.PowerManager;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.concurrent.TimeUnit;

public class PreffFragment extends Fragment {

    private SeekBar lightSeekBar, soundSeekBar;
    private TextView lightThresholdLabel, soundThresholdLabel;

    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "ThresholdPrefs";
    private static final String LIGHT_KEY = "light_threshold";
    private static final String SOUND_KEY = "sound_threshold";

    private Switch autoTestSwitch;
    private SeekBar intervalSeekBar;
    private TextView intervalLabel;

    private static final String AUTO_TEST_KEY = "auto_test_enabled";
    private static final String INTERVAL_KEY = "test_interval";  // in minutes

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_preff, container, false);

        sharedPreferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);



        // UI Elements
        lightSeekBar = view.findViewById(R.id.lightSeekBar);
        soundSeekBar = view.findViewById(R.id.soundSeekBar);
        lightThresholdLabel = view.findViewById(R.id.lightThresholdLabel);
        soundThresholdLabel = view.findViewById(R.id.soundThresholdLabel);
        autoTestSwitch = view.findViewById(R.id.autoTestSwitch);
        intervalSeekBar = view.findViewById(R.id.intervalSeekBar);
        intervalLabel = view.findViewById(R.id.intervalLabel);
        Button resetButton = view.findViewById(R.id.resetButton);

        // Load saved thresholds
        int savedLight = sharedPreferences.getInt(LIGHT_KEY, 500);
        int savedSound = sharedPreferences.getInt(SOUND_KEY, 60);
        lightSeekBar.setProgress(savedLight);
        soundSeekBar.setProgress(savedSound);
        lightThresholdLabel.setText("Light Threshold: " + savedLight + " lux");
        soundThresholdLabel.setText("Sound Threshold: " + savedSound + " dB");

        lightSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                lightThresholdLabel.setText("Light Threshold: " + progress + " lux");
                sharedPreferences.edit().putInt(LIGHT_KEY, progress).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        soundSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                soundThresholdLabel.setText("Sound Threshold: " + progress + " dB");
                sharedPreferences.edit().putInt(SOUND_KEY, progress).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        resetButton.setOnClickListener(v -> {
            int defaultLight = 500;
            int defaultSound = 60;
            sharedPreferences.edit()
                    .putInt(LIGHT_KEY, defaultLight)
                    .putInt(SOUND_KEY, defaultSound)
                    .apply();

            lightSeekBar.setProgress(defaultLight);
            soundSeekBar.setProgress(defaultSound);
            lightThresholdLabel.setText("Light Threshold: " + defaultLight + " lux");
            soundThresholdLabel.setText("Sound Threshold: " + defaultSound + " dB");
        });

        // Load auto-test settings
        boolean autoTestEnabled = sharedPreferences.getBoolean(AUTO_TEST_KEY, false);
        int interval = sharedPreferences.getInt(INTERVAL_KEY, 30);
        autoTestSwitch.setChecked(autoTestEnabled);
        intervalSeekBar.setProgress(interval);
        intervalLabel.setText("Test Interval: " + interval + " min");

        intervalSeekBar.setVisibility(autoTestEnabled ? View.VISIBLE : View.GONE);
        intervalLabel.setVisibility(autoTestEnabled ? View.VISIBLE : View.GONE);

        intervalSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int adjustedProgress = Math.max(progress, 1);
                intervalLabel.setText("Test Interval: " + adjustedProgress + " min");
                sharedPreferences.edit().putInt(INTERVAL_KEY, adjustedProgress).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // FINAL SINGLE Switch listener
        autoTestSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sharedPreferences.edit().putBoolean(AUTO_TEST_KEY, isChecked).apply();
            intervalSeekBar.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            intervalLabel.setVisibility(isChecked ? View.VISIBLE : View.GONE);

            int updatedInterval = sharedPreferences.getInt(INTERVAL_KEY, 30);

            if (isChecked) {
//                requestBatteryOptimizationException(); // ✅ Ask for whitelisting
                scheduleForegroundService(requireContext(), updatedInterval);
            } else {
                cancelForegroundService(requireContext());
//                WorkManager.getInstance(requireContext()).cancelUniqueWork("AutoMeasurement");
            }
        });

        return view;
    }

    private void scheduleAutoMeasurement(int intervalInMinutes) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .setRequiresBatteryNotLow(true)
                .build();

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                MeasurementWorker.class,
                intervalInMinutes, TimeUnit.MINUTES)
                .addTag("auto")
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(requireContext()).enqueueUniquePeriodicWork(
                "AutoMeasurement",
                ExistingPeriodicWorkPolicy.UPDATE,
                request
        );
    }

    private void scheduleForegroundService(Context context, int intervalMinutes) {
        Intent intent = new Intent(context, ForegroundService.class);
        PendingIntent pendingIntent = PendingIntent.getService(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        long intervalMillis = intervalMinutes * 60 * 1000L;
        long startTime = System.currentTimeMillis() + 5000;

        alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                startTime,
                intervalMillis,
                pendingIntent
        );
    }

    private void cancelForegroundService(Context context) {
        Intent intent = new Intent(context, ForegroundService.class);
        PendingIntent pendingIntent = PendingIntent.getService(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(pendingIntent);
    }

    private void requestBatteryOptimizationException() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) requireContext().getSystemService(Context.POWER_SERVICE);
            String packageName = requireContext().getPackageName();
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + packageName));
                startActivity(intent);
            }
        }
    }





}
