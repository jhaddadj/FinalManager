package com.example.manager.admin.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.manager.R;
import com.example.manager.admin.adapter.TimetableAdapter;
import com.example.manager.admin.model.TimetableEntry;
import com.example.manager.databinding.ActivityTimetableInitializationBinding;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class TimetableInitializationActivity extends AppCompatActivity {
    private ActivityTimetableInitializationBinding binding;
    private DatabaseReference databaseReference;
    private List<TimetableEntry> timetableEntries = new ArrayList<>();
    private TimetableAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityTimetableInitializationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        databaseReference = FirebaseDatabase.getInstance().getReference("timetables");

        // Setup RecyclerView
        binding.timetableRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TimetableAdapter(timetableEntries, this::onItemClicked, this::onItemLongClicked);
        binding.timetableRecyclerView.setAdapter(adapter);

        // Load timetable data
        loadTimetableData();

        // Add new timetable
        binding.addTimeTableButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, AddScheduleActivity.class);
            startActivity(intent);
        });

    }
    private void loadTimetableData() {
        binding.progressBar.setVisibility(View.VISIBLE);
        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                timetableEntries.clear();
                for (DataSnapshot data : snapshot.getChildren()) {
                    TimetableEntry entry = data.getValue(TimetableEntry.class);
                    if (entry != null) {
                        binding.progressBar.setVisibility(View.INVISIBLE);
                        entry.setCourseId(data.getKey());
                        timetableEntries.add(entry);
                    }else {
                        binding.progressBar.setVisibility(View.INVISIBLE);

                    }
                }
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(DatabaseError error) {
                binding.progressBar.setVisibility(View.INVISIBLE);
                Toast.makeText(TimetableInitializationActivity.this, "Failed to load data.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void onItemClicked(TimetableEntry entry) {
        Intent intent = new Intent(this, AddScheduleActivity.class);
        intent.putExtra("courseId", entry.getCourseId());
        startActivity(intent);
    }

    private void onItemLongClicked(TimetableEntry entry) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Timetable Entry")
                .setMessage("Are you sure you want to delete this entry?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    databaseReference.child(entry.getCourseId()).removeValue().addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(this, "Entry deleted successfully.", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "Failed to delete entry.", Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}