package com.example.manager.admin.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.manager.R;
import com.example.manager.databinding.ActivityMainBinding;
import com.example.manager.ui.SelectActivity;
import com.google.firebase.auth.FirebaseAuth;

public class AdminMainActivity extends AppCompatActivity {
     private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding= ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        binding.resourceManagementButton.setOnClickListener(v -> openResourceManagement());
        binding.timetableInitializationButton.setOnClickListener(v -> openTimetableInitialization());
        binding.reuestCheckButton.setOnClickListener(v -> openRequestCheck());
        binding.logoutButton.setOnClickListener(v -> logout());
    }

    // Navigate to Resource Management Activity
    private void openResourceManagement() {
        Intent intent = new Intent(this, ResourceManagementActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        startActivity(intent);
    }



    // Navigate to Timetable Initialization Activity
    private void openTimetableInitialization() {
        Intent intent = new Intent(this, TimetableInitializationActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    private void openRequestCheck() {
        Intent intent = new Intent(this, RequestCheckActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    // Logout functionality
    private void logout() {
        FirebaseAuth.getInstance().signOut();
        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, SelectActivity.class);
        startActivity(intent);
        finish();

    }
}