package com.med.health_app;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;


import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class HomeFragment extends Fragment {

    private DatabaseHelper dbHelper;
    private LineChart lightChart, soundChart;
    private TextView analysisText;
    private SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    private SimpleDateFormat dayFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private TextView moodIcon;
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        // DB init
        dbHelper = new DatabaseHelper(getContext());

        // UI elements
        Button measureButton = view.findViewById(R.id.btn_measure);
        measureButton.setOnClickListener(v -> {
            // Launch MeasureActivity
            startActivity(new android.content.Intent(getActivity(), MeasureActivity.class));
        });



        Button datePickerButton = view.findViewById(R.id.btn_select_date);
        datePickerButton.setOnClickListener(v -> showDatePicker());


        lightChart = view.findViewById(R.id.lightChart);
        soundChart = view.findViewById(R.id.soundChart);
        analysisText = view.findViewById(R.id.analysisText);
        moodIcon = view.findViewById(R.id.moodIcon);




        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        loadDataAndDisplay(today);

//        loadDataAndDisplay();

        return view;
    }

    private void showDatePicker() {
        final Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                getContext(),
                (view, selectedYear, selectedMonth, selectedDay) -> {
                    Calendar selectedCal = Calendar.getInstance();
                    selectedCal.set(selectedYear, selectedMonth, selectedDay);
                    String selectedDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            .format(selectedCal.getTime());
                    loadDataAndDisplay(selectedDate); // Load graph for selected date
                },
                year, month, day
        );

        // ⛔ Disallow future dates
        datePickerDialog.getDatePicker().setMaxDate(System.currentTimeMillis());

        datePickerDialog.show();
    }



    private void loadDataAndDisplay(String targetDate) {
        SharedPreferences prefs = requireContext().getSharedPreferences("ThresholdPrefs", Context.MODE_PRIVATE);
        int lightThreshold = prefs.getInt("light_threshold", 50);
        int soundThreshold = prefs.getInt("sound_threshold", 60);
        Cursor cursor = dbHelper.getAllMeasurements();

        List<Entry> lightEntries = new ArrayList<>();
        List<Entry> soundEntries = new ArrayList<>();
        List<String> timeLabels = new ArrayList<>();
        List<String> dangerTimes = new ArrayList<>();

        SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        SimpleDateFormat dayFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        int index = 0;
        int totalReadings = 0;
        int dangerReadings = 0;

        if (cursor.moveToFirst()) {
            do {
                float light = cursor.getFloat(cursor.getColumnIndexOrThrow("light"));
                float sound = cursor.getFloat(cursor.getColumnIndexOrThrow("sound"));
                String createdAt = cursor.getString(cursor.getColumnIndexOrThrow("created_at"));

                try {
                    Date fullDate = inputFormat.parse(createdAt);
                    String dateOnly = dayFormat.format(fullDate);
                    String timeOnly = timeFormat.format(fullDate);

                    if (dateOnly.equals(targetDate)) {
                        totalReadings++;
                        lightEntries.add(new Entry(index, light));
                        soundEntries.add(new Entry(index, sound));
                        timeLabels.add(timeOnly);

                        if (light > lightThreshold || sound > soundThreshold) {
                            dangerReadings++;
                            dangerTimes.add(timeOnly);
                        }

                        index++;
                    }
                } catch (ParseException e) {
                    e.printStackTrace();
                }
            } while (cursor.moveToNext());
        }
        cursor.close();

        setChartData(lightChart, lightEntries, "Light", timeLabels);
        setChartData(soundChart, soundEntries, "Sound", timeLabels);

        StringBuilder analysis = new StringBuilder();

        if (totalReadings == 0) {
            analysis.append("📭 No measurements found for ").append(targetDate);
        } else {
            int safeReadings = totalReadings - dangerReadings;
            float score = (safeReadings / (float) totalReadings) * 100;

            // Round score to 1 decimal
            String scoreStr = String.format(Locale.getDefault(), "%.1f", score);

            analysis.append("📊 Exposure Score: ").append(scoreStr).append("/100\n");

            if (score >= 90) {
                analysis.append("✅ You had a peaceful and safe day.");
                moodIcon.setText("😊");
            } else if (score >= 70) {
                analysis.append("😐 Some exposure detected, but overall your day was okay.");
                moodIcon.setText("😐");
            } else {
                analysis.append("⚠️ Be careful! Today had high exposure and may not have been safe.");
                moodIcon.setText("😟");
            }


            if (!dangerTimes.isEmpty()) {
                analysis.append("\n🕒 High exposure at: ");
                analysis.append(String.join(", ", dangerTimes));
            }
        }

        analysisText.setText(analysis.toString());
    }




    private float average(List<Float> list) {
        float sum = 0;
        for (float val : list) sum += val;
        return list.size() > 0 ? sum / list.size() : 0;
    }

    private void setChartData(LineChart chart, List<Entry> entries, String label, List<String> labels) {
        LineDataSet dataSet = new LineDataSet(entries, label);
        dataSet.setColor(android.graphics.Color.BLUE);
        dataSet.setCircleColor(android.graphics.Color.RED);
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(4f);
        dataSet.setValueTextSize(10f);
        dataSet.setDrawValues(true);
        dataSet.setDrawCircleHole(false);

        LineData lineData = new LineData(dataSet);
        chart.setData(lineData);

        // X-Axis styling
        XAxis xAxis = chart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setGranularityEnabled(true);
        xAxis.setDrawGridLines(false); // ✅ remove vertical grid lines
        xAxis.setDrawAxisLine(false);  // ✅ remove axis line
        xAxis.setLabelRotationAngle(-45);

        // Y-Axis styling
        chart.getAxisLeft().setDrawGridLines(false); // ✅ remove horizontal grid lines
        chart.getAxisLeft().setDrawAxisLine(false);  // ✅ remove axis line
        chart.getAxisRight().setEnabled(false);      // ✅ disable right Y-axis completely

        // General chart styling
        chart.getDescription().setEnabled(false);    // ✅ hide description text
        chart.setDrawBorders(false);                 // ✅ no borders
        chart.setDrawGridBackground(true);          // ✅ no background texture
        chart.setBackgroundColor(android.graphics.Color.TRANSPARENT); // ✅ clean BG

        chart.invalidate(); // refresh
    }



}
