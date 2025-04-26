package com.med.health_app;
import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.github.anastr.speedviewlib.AwesomeSpeedometer;
import com.github.anastr.speedviewlib.SpeedView;
import com.med.health_app.DatabaseHelper;

import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import android.os.Build;



public class MeasureFragment extends Fragment implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor lightSensor;
    private MediaRecorder mediaRecorder;
    private DatabaseHelper dbHelper;

    private TextView lightTextView, soundTextView;

    private SpeedView lightmeter;
    private AwesomeSpeedometer soundmeter;

    private final ArrayList<Float> lightValues = new ArrayList<>();
    private final ArrayList<Float> soundValues = new ArrayList<>();





    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startSoundMeasurement();  // ✅ Start MediaRecorder if permission granted
            } else {
                soundTextView.setText("Microphone permission denied");
            }
        }
    }


    private final android.os.Handler handler = new android.os.Handler();
    private final Runnable soundUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            if (mediaRecorder != null) {
                float soundLevel = getSoundLevel();

                soundTextView.setText("Sound Level: " + soundLevel);
                soundmeter.speedTo(soundLevel);
                soundValues.add(soundLevel);  // ✅ Add to buffer
            }
            handler.postDelayed(this, 1000); // Update sound level every 1 second
        }
    };

    private void updateSoundLevel() {
        handler.post(soundUpdateRunnable); // Start the loop
    }



    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_measure, container, false);


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 2001);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            List<String> permissionsToRequest = new ArrayList<>();
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN);
            }

            if (!permissionsToRequest.isEmpty()) {
                requestPermissions(permissionsToRequest.toArray(new String[0]), 3001);
            }
        }


        lightTextView = view.findViewById(R.id.lightValue);
        soundTextView = view.findViewById(R.id.soundValue);

        lightmeter = view.findViewById(R.id.lightGauge);
        soundmeter = view.findViewById(R.id.soundGauge);
        Button viewresults = view.findViewById(R.id.btn_viewresults);
        viewresults.setOnClickListener(v -> {
            // Launch MeasureActivity
            startActivity(new android.content.Intent(getActivity(), HomeFragment.class));
        });

        sensorManager = (SensorManager) requireActivity().getSystemService(Context.SENSOR_SERVICE);
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        dbHelper = new DatabaseHelper(requireContext());

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{Manifest.permission.RECORD_AUDIO}, 1001);
        } else {
//            startSoundMeasurement();
//            updateSoundLevel();
            start10SecondTest();  // <- start as soon as the fragment is created
        }

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (lightSensor != null) {
            sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (lightSensor != null) {
            sensorManager.unregisterListener(this);
        }
        stopSoundMeasurement();
        handler.removeCallbacks(soundUpdateRunnable); // Stop sound updates
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(soundUpdateRunnable); // Stop updates on exit
    }



    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
            float lightValue = event.values[0];
            lightTextView.setText("Light: " + lightValue + " lux");
            // Update speedometer with current light value
            lightmeter.speedTo(lightValue);  // <-- Move it here
            lightValues.add(lightValue);  // ✅ Add to buffer
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void startSoundMeasurement() {
        try {
            if (mediaRecorder != null) {
                mediaRecorder.release(); // Ensure previous instance is released
                mediaRecorder = null;
            }

            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mediaRecorder.setOutputFile(requireContext().getCacheDir().getAbsolutePath() + "/temp_audio.3gp");

            mediaRecorder.prepare();
            mediaRecorder.start();
            soundTextView.setText("Recording Started...");  // ✅ Confirm recording

        } catch (SecurityException e) {
            soundTextView.setText("Microphone permission required!");
            e.printStackTrace();
        } catch (IOException e) {
            soundTextView.setText("Error preparing microphone.");
            e.printStackTrace();
        } catch (RuntimeException e) {
            soundTextView.setText("Failed to start microphone. Try restarting your phone.");
            e.printStackTrace();
        }
    }



    private float getSoundLevel() {
        if (mediaRecorder != null) {
            int amplitude = mediaRecorder.getMaxAmplitude();
            if (amplitude > 0) {
                // Convert to dB and shift to 0–90 range
                return (float) (20 * Math.log10((double) amplitude / 32767.0) + 90);
            }
        }
        return 0.0f; // Silence
    }





    private void stopSoundMeasurement() {
        if (mediaRecorder != null) {
            try {
                mediaRecorder.stop();
                soundTextView.setText("Recording Stopped.");
            } catch (RuntimeException e) {
                e.printStackTrace();
                soundTextView.setText("Failed to stop recording.");
            }
            mediaRecorder.release();
            mediaRecorder = null;
        }
    }

    private void storeAveragesToDatabase() {
        float averageLight = 0.0f;
        float averageSound = 0.0f;

        if (!lightValues.isEmpty()) {
            for (float val : lightValues) {
                averageLight += val;
            }
            averageLight /= lightValues.size();
        }

        if (!soundValues.isEmpty()) {
            for (float val : soundValues) {
                averageSound += val;
            }
            averageSound /= soundValues.size();
        }

        // Insert into DB
        dbHelper.insertMeasurementData(averageLight, averageSound,getCurrentTimestamp());

        // Update UI
        lightTextView.setText("Average Light: " + averageLight + " lux");
        soundTextView.setText("Average Sound: " + averageSound + " dB");

        // Get thresholds from SharedPreferences
        SharedPreferences prefs = requireContext().getSharedPreferences("ThresholdPrefs", Context.MODE_PRIVATE);
        int lightThreshold = prefs.getInt("light_threshold", 50);
        int soundThreshold = prefs.getInt("sound_threshold", 60);

        // Compare and notify
        boolean isSafe = averageLight <= lightThreshold && averageSound <= soundThreshold;
        sendEnvironmentNotification(isSafe, averageLight, averageSound);

        if (!isSafe) {
            sendBluetoothAlertToSelectedDevices("⚠️ Warning: Unsafe environment detected!\nLight: " + averageLight + " lux\nSound: " + averageSound + " dB");
        }

    }



    private void start10SecondTest() {
        lightValues.clear();
        soundValues.clear();



        startSoundMeasurement();
        updateSoundLevel();

        if (lightSensor != null) {
            sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }

        // Stop test after 10 seconds
        handler.postDelayed(() -> {
            stopSoundMeasurement();
            sensorManager.unregisterListener(this);
            handler.removeCallbacks(soundUpdateRunnable);

            storeAveragesToDatabase();

        }, 10000); // 10 seconds = 10000 ms
    }

    private String getCurrentTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date());
    }


    private void sendEnvironmentNotification(boolean isSafe, float light, float sound) {
        String title, message;

        if (isSafe) {
            title = "All Clear ✅";
            message = "Environment is safe.\nLight: " + light + " lux\nSound: " + sound + " dB";
        } else {
            title = "Warning ⚠️";
            message = "Unsafe levels detected!\nLight: " + light + " lux\nSound: " + sound + " dB";
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(requireContext(), "health_alerts")
                .setSmallIcon(R.drawable.ic_notification)  // Make sure you have a suitable icon
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        NotificationManagerCompat manager = NotificationManagerCompat.from(requireContext());
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {

            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        manager.notify((int) System.currentTimeMillis(), builder.build());  // Unique ID for each
    }


    private void sendBluetoothAlertToSelectedDevices(String alertMessage) {


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                Log.w("Bluetooth", "BLUETOOTH_CONNECT or BLUETOOTH_SCAN not granted.");
                return;
            }
        }



        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Log.w("Bluetooth", "Bluetooth is not available or not enabled.");
            return;
        }

        bluetoothAdapter.cancelDiscovery(); // ✅ cancel discovery before connecting
        Log.d("BluetoothSend", "empika");
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
}
