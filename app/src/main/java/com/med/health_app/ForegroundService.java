package com.med.health_app;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.*;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.*;

public class ForegroundService extends Service implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor lightSensor;
    private MediaRecorder mediaRecorder;
    private final List<Float> lightValues = new ArrayList<>();
    private final List<Float> soundValues = new ArrayList<>();
    private DatabaseHelper dbHelper;

    private final Handler handler = new Handler();

    @Override
    public void onCreate() {
        super.onCreate();
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        dbHelper = new DatabaseHelper(getApplicationContext());
        createNotificationChannel();





    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("ForegroundService", "🚀 Foreground service started!");
        Notification notification = buildNotification("Measuring sound...");
        startForeground(1, notification);

        sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);

        start10SecondMeasurement();

        return START_STICKY;
    }

    private final Runnable soundUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            if (mediaRecorder != null) {
                float soundLevel = getSoundLevel();
                Log.d("ForegroundService", "🎙 Sound Level: " + soundLevel);
                soundValues.add(soundLevel);
            }
            handler.postDelayed(this, 1000);
        }
    };

    private void updateSoundLevel() {
        handler.post(soundUpdateRunnable);
    }

    private void start10SecondMeasurement() {
        Log.d("ForegroundService", "🧪 Running 10-second auto measurement...");

        lightValues.clear();
        soundValues.clear();

        startSoundMeasurement();
        updateSoundLevel();  // <-- Just like in manual mode

        if (lightSensor != null) {
            sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }

        handler.postDelayed(() -> {
            stopSoundMeasurement();
            sensorManager.unregisterListener(this);
            handler.removeCallbacks(soundUpdateRunnable); // Stop loop

            storeAveragesToDatabase();

            stopForeground(true);
            stopSelf();

        }, 10_000); // 10 seconds
    }

    private void startSoundMeasurement() {
        try {
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mediaRecorder.setOutputFile(getCacheDir().getAbsolutePath() + "/temp_audio.3gp");

            mediaRecorder.prepare();
            mediaRecorder.start();

        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
        }
    }

    private void stopSoundMeasurement() {
        try {
            if (mediaRecorder != null) {
                mediaRecorder.stop();
                mediaRecorder.release();
                mediaRecorder = null;
            }
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
    }

    private float getSoundLevel() {
        if (mediaRecorder != null) {
            int amplitude = mediaRecorder.getMaxAmplitude();
            if (amplitude > 0) {
                return (float) (20 * Math.log10((double) amplitude / 32767.0) + 90);
            }
        }
        return 0f;
    }

    private void storeAveragesToDatabase() {
        float avgLight = average(lightValues);
        float avgSound = average(soundValues);

        dbHelper.insertMeasurementData(avgLight, avgSound, getTimestamp());
        Log.d("ForegroundService", "Saved: light=" + avgLight + " sound=" + avgSound);

        SharedPreferences prefs = getSharedPreferences("ThresholdPrefs", MODE_PRIVATE);
        int lightThreshold = prefs.getInt("light_threshold", 500);
        int soundThreshold = prefs.getInt("sound_threshold", 60);

        boolean isSafe = avgLight <= lightThreshold && avgSound <= soundThreshold;
        sendEnvironmentNotification(isSafe, avgLight, avgSound);

        if (!isSafe) {
            sendBluetoothAlertToSelectedDevices("⚠️ Warning: Unsafe environment detected!\nLight: " + avgLight + " lux\nSound: " + avgSound + " dB");
        }
    }

    private float average(List<Float> list) {
        float sum = 0;
        for (float val : list) sum += val;
        return list.isEmpty() ? 0 : sum / list.size();
    }

    private String getTimestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
    }

    private Notification buildNotification(String message) {
        return new NotificationCompat.Builder(this, "health_channel")
                .setContentTitle("Health Auto-Measurement")
                .setContentText(message)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);

            NotificationChannel serviceChannel = new NotificationChannel(
                    "health_channel", "Health Tests", NotificationManager.IMPORTANCE_LOW);
            manager.createNotificationChannel(serviceChannel);

            NotificationChannel alertChannel = new NotificationChannel(
                    "health_alerts", "Health Alerts", NotificationManager.IMPORTANCE_HIGH);
            alertChannel.setDescription("Notifications for unsafe light/sound levels");
            manager.createNotificationChannel(alertChannel);
        }
    }

    private void sendEnvironmentNotification(boolean isSafe, float light, float sound) {
        String title = isSafe ? "All Clear ✅" : "Warning ⚠️";
        String message = isSafe ?
                "Environment is safe.\nLight: " + light + " lux\nSound: " + sound + " dB" :
                "Unsafe levels detected!\nLight: " + light + " lux\nSound: " + sound + " dB";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "health_alerts")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            manager.notify((int) System.currentTimeMillis(), builder.build());
        }
    }

    private void sendBluetoothAlertToSelectedDevices(String alertMessage) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                Log.w("Bluetooth", "BLUETOOTH_CONNECT or BLUETOOTH_SCAN not granted.");
                return;
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.w("Bluetooth", "BLUETOOTH_CONNECT permission not granted.");
            return;
        }

        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Log.w("Bluetooth", "Bluetooth is not available or not enabled.");
            return;
        }

        bluetoothAdapter.cancelDiscovery(); // ✅ cancel discovery before connecting

        List<String> selectedMacs = dbHelper.getSelectedDeviceMacs();
        Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();

        for (BluetoothDevice device : bondedDevices) {
            if (!selectedMacs.contains(device.getAddress())) continue;

            Log.d("BluetoothSend", "Preparing to send to: " + device.getName());

            new Thread(() -> {
                try {
                    bluetoothAdapter.cancelDiscovery();
                    Log.d("BluetoothSend", "Trying to connect to " + device.getAddress());

//                    BluetoothSocket socket = device.createRfcommSocketToServiceRecord(
//                            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")); // SPP UUID

                    BluetoothSocket socket = device.createInsecureRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));


                    socket.connect(); // 🔴 If this fails, check receiver is running

                    OutputStream outputStream = socket.getOutputStream();
                    outputStream.write(alertMessage.getBytes());
                    outputStream.flush();
                    Thread.sleep(1000); // Give receiver time to read
                    outputStream.close();
                    socket.close();

                    Log.d("BluetoothSend", "✅ Alert sent to " + device.getAddress());
                } catch (IOException e) {
                    Log.e("BluetoothSend", "❌ Failed to send to device: " + device.getAddress(), e);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }).start();
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
            lightValues.add(event.values[0]);
        }
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
