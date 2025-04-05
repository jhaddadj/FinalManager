package com.example.manager.admin.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.manager.R;

public class TimetableInitializationActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_timetable_initialization);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Set mode description
        TextView modeDescriptionText = findViewById(R.id.modeDescriptionText);
        modeDescriptionText.setText("Constraint Solver automatically generates optimal timetables based on resources, lecturers, and course constraints. It uses advanced constraint programming to ensure the best possible schedule.");

        // Setup proceed button
        findViewById(R.id.proceedButton).setOnClickListener(v -> {
            Intent intent = new Intent(this, ConstraintSolverActivity.class);
            startActivity(intent);
        });
    }
}