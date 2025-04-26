package com.med.health_app;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MyPageFragment extends Fragment {

    private ListView listView;
    private BluetoothDeviceAdapter adapter;
    private DatabaseHelper dbHelper;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_mypage, container, false);

        listView = view.findViewById(R.id.listBluetoothDevices);
        dbHelper = new DatabaseHelper(requireContext());

        loadBluetoothDevices();

        return view;
    }

    private void loadBluetoothDevices() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Toast.makeText(getContext(), "Bluetooth is not enabled", Toast.LENGTH_SHORT).show();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 3010);
                return;
            }
        }

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        List<BluetoothDeviceItem> deviceItems = new ArrayList<>();

        for (BluetoothDevice device : pairedDevices) {
            String mac = device.getAddress();
            String originalName = device.getName();
            String customName = dbHelper.getCustomName(mac);
            boolean isSelected = dbHelper.isDeviceSelected(mac);

            if (!dbHelper.isDeviceInDatabase(mac)) {
                dbHelper.insertBluetoothDevice(mac, originalName);
            }

            deviceItems.add(new BluetoothDeviceItem(mac, originalName, customName, isSelected));
        }

        adapter = new BluetoothDeviceAdapter(getContext(), deviceItems, dbHelper);
        listView.setAdapter(adapter);
    }

    public static class BluetoothDeviceItem {
        public String macAddress;
        public String originalName;
        public String customName;
        public boolean isSelected;

        public BluetoothDeviceItem(String macAddress, String originalName, String customName, boolean isSelected) {
            this.macAddress = macAddress;
            this.originalName = originalName;
            this.customName = customName;
            this.isSelected = isSelected;
        }
    }

    public static class BluetoothDeviceAdapter extends ArrayAdapter<BluetoothDeviceItem> {

        private final Context context;
        private final List<BluetoothDeviceItem> devices;
        private final DatabaseHelper dbHelper;

        public BluetoothDeviceAdapter(Context context, List<BluetoothDeviceItem> devices, DatabaseHelper dbHelper) {
            super(context, R.layout.list_item_bluetooth_device, devices);
            this.context = context;
            this.devices = devices;
            this.dbHelper = dbHelper;
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.list_item_bluetooth_device, parent, false);
            }

            BluetoothDeviceItem device = devices.get(position);

            TextView nameText = convertView.findViewById(R.id.deviceName);
            CheckBox checkBox = convertView.findViewById(R.id.deviceCheckBox);
            Button renameButton = convertView.findViewById(R.id.renameButton);

            String displayName = (device.customName != null && !device.customName.isEmpty()) ? device.customName : device.originalName;
            nameText.setText(displayName);
            checkBox.setChecked(device.isSelected);

            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                dbHelper.setDeviceSelected(device.macAddress, isChecked);
            });

            renameButton.setOnClickListener(v -> showRenameDialog(device, nameText));

            return convertView;
        }

        private void showRenameDialog(BluetoothDeviceItem device, TextView nameText) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle("Rename Device");

            final EditText input = new EditText(context);
            input.setInputType(InputType.TYPE_CLASS_TEXT);
            input.setText(device.customName);
            builder.setView(input);

            builder.setPositiveButton("Save", (dialog, which) -> {
                String newName = input.getText().toString().trim();
                dbHelper.setCustomDeviceName(device.macAddress, newName);
                device.customName = newName;
                nameText.setText(newName);
            });

            builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

            builder.show();
        }
    }
}
