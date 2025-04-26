package com.med.health_app;





import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "HealthApp.db";
    private static final int DATABASE_VERSION = 4;

    private static final String TABLE_USERS = "users";
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_USERNAME = "username";
    private static final String COLUMN_PASSWORD = "password";
    private static final String COLUMN_ROLE = "role";



    private static final String TABLE_MEASURE_DATA = "data";
    private static final String data_COLUMN_ID = "id";
    private static final String COLUMN_LIGHT = "light";
    private static final String COLUMN_SOUND = "sound";

    private static final String TEST_CREATED_AT = "created_at";


    private static final String TABLE_BLUETOOTH = "bluetooth_devices";

    private static final String mac_address = "mac_address";
    private static final String original_name = "original_name";
    private static final String custom_name = "custom_name";
    private static final String is_selected = "is_selected";



    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {




        String createTableQuery = "CREATE TABLE " + TABLE_MEASURE_DATA + " (" +
                data_COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_LIGHT + " REAL, " +
                COLUMN_SOUND + " REAL, " +
                TEST_CREATED_AT + " TEXT)"; // adding the created_at column as TEXT
        db.execSQL(createTableQuery);



        String createBluetoothQuery = "CREATE TABLE " + TABLE_BLUETOOTH + " (" +
                mac_address + " TEXT PRIMARY KEY, " +
                original_name + " TEXT, " +
                custom_name + " TEXT, " +
                is_selected + " INTEGER) ";
        db.execSQL(createBluetoothQuery);

        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS);

    }


    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Drop existing tables if they exist and recreate them
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_MEASURE_DATA);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_BLUETOOTH);
        onCreate(db);
    }










    // Insert sensor data into the measurements table
    public void insertMeasurementData(float light, float sound, String datetime) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_LIGHT, light);
        values.put(COLUMN_SOUND, sound);
        values.put(TEST_CREATED_AT, datetime);
        db.insert(TABLE_MEASURE_DATA, null, values);
        db.close();
    }

    public Cursor getAllMeasurements() {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT * FROM " + TABLE_MEASURE_DATA + " ORDER BY " + TEST_CREATED_AT + " ASC";
        return db.rawQuery(query, null);
    }


    // Add these methods inside your DatabaseHelper.java class

    public void insertBluetoothDevice(String mac, String originalName) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("mac_address", mac);
        values.put("original_name", originalName);
        values.put("custom_name", "");
        values.put("is_selected", 0);
        db.insert("bluetooth_devices", null, values);
    }

    public boolean isDeviceInDatabase(String mac) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query("bluetooth_devices", null, "mac_address=?", new String[]{mac}, null, null, null);
        boolean exists = (cursor != null && cursor.moveToFirst());
        if (cursor != null) cursor.close();
        return exists;
    }

    public String getCustomName(String mac) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query("bluetooth_devices", new String[]{"custom_name"}, "mac_address=?", new String[]{mac}, null, null, null);
        String name = "";
        if (cursor != null && cursor.moveToFirst()) {
            name = cursor.getString(0);
            cursor.close();
        }
        return name;
    }

    public boolean isDeviceSelected(String mac) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query("bluetooth_devices", new String[]{"is_selected"}, "mac_address=?", new String[]{mac}, null, null, null);
        boolean selected = false;
        if (cursor != null && cursor.moveToFirst()) {
            selected = cursor.getInt(0) == 1;
            cursor.close();
        }
        return selected;
    }

    public void setDeviceSelected(String mac, boolean isSelected) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("is_selected", isSelected ? 1 : 0);
        db.update("bluetooth_devices", values, "mac_address=?", new String[]{mac});
    }

    public void setCustomDeviceName(String mac, String customName) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("custom_name", customName);
        db.update("bluetooth_devices", values, "mac_address=?", new String[]{mac});
    }

    public List<String> getSelectedDeviceMacs() {
        SQLiteDatabase db = this.getReadableDatabase();
        List<String> selectedMacs = new ArrayList<>();
        Cursor cursor = db.query("bluetooth_devices", new String[]{"mac_address"}, "is_selected=1", null, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            do {
                selectedMacs.add(cursor.getString(0));
            } while (cursor.moveToNext());
            cursor.close();
        }
        return selectedMacs;
    }


}
