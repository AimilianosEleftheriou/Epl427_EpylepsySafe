package com.med.health_app;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class MeasureActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_measure);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainer, new MeasureFragment())
                    .commit();
        }

//        if (savedInstanceState == null) {
//            getSupportFragmentManager().beginTransaction()
//                    .replace(R.id.fragmentContainer, new blank_fragment()) // Use a different test fragment
//                    .commit();
//        }

    }
}
