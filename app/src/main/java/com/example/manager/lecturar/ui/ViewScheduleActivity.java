package com.example.manager.lecturar.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.manager.R;
import com.example.manager.admin.model.Comment;
import com.example.manager.databinding.ActivityViewScheduleBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ViewScheduleActivity extends AppCompatActivity {
 private ActivityViewScheduleBinding binding;

    private DatabaseReference databaseReference;
    private String lecName,lecId;
    private DatabaseReference commentsRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding=ActivityViewScheduleBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        databaseReference = FirebaseDatabase.getInstance().getReference();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        commentsRef =  FirebaseDatabase.getInstance().getReference();

        String courseId = getIntent().getStringExtra("courseId");
        lecName = getIntent().getStringExtra("name");
        lecId = getIntent().getStringExtra("lecId");

        if (courseId != null) {
            loadTimetableData(courseId);

        }
        binding.commenteButton.setOnClickListener(v -> addComment(courseId));
    }

    private void loadTimetableData(String courseId) {
        databaseReference.child("timetables").child(courseId).get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult().exists()) {
                DataSnapshot snapshot = task.getResult();
                binding.courseNameText.setText(snapshot.child("courseName").getValue(String.class));
                binding.classDurationText.setText(snapshot.child("timeSlot").getValue(String.class));
                binding.roomText.setText(snapshot.child("roomName").getValue(String.class));
                binding.locationText.setText(snapshot.child("location").getValue(String.class));
                binding.startDateText.setText(snapshot.child("startDate").getValue(String.class));
                binding.endDateText.setText(snapshot.child("endDate").getValue(String.class));


                binding.mondayCheckBox.setChecked(false);
                binding.tuesdayCheckBox.setChecked(false);
                binding.wednesdayCheckBox.setChecked(false);
                binding.thursdayCheckBox.setChecked(false);
                binding.fridayCheckBox.setChecked(false);


                // Populate days checkboxes
                List<String> days = (List<String>) snapshot.child("day").getValue();
                if (days != null) {
                    for (String day : days) {
                        switch (day) {
                            case "Monday":

                                binding.mondayCheckBox.setChecked(true);
                                break;
                            case "Tuesday":
                                binding.tuesdayCheckBox.setChecked(true);
                                break;
                            case "Wednesday":
                                binding.wednesdayCheckBox.setChecked(true);
                                break;
                            case "Thursday":
                                binding.thursdayCheckBox.setChecked(true);
                                break;
                            case "Friday":
                                binding.fridayCheckBox.setChecked(true);
                                break;
                        }
                    }
                }
                binding.main2.setVisibility(View.VISIBLE);
                binding.progressBar.setVisibility(View.INVISIBLE);
            }
        });

    }

    private void addComment(String courseId) {
        String commentText = binding.commentEditText.getText().toString().trim();

        if (commentText.isEmpty()) {
            Toast.makeText(this, "Please enter a comment", Toast.LENGTH_SHORT).show();
            return;
        }
        binding.commenteButton.setEnabled(false);
            binding.progressBar.setVisibility(View.VISIBLE);

        String commentId = commentsRef.push().getKey();
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

        Comment newComment = new Comment(commentId, lecId,lecName, commentText, timestamp);
        commentsRef.child("comments").child(courseId).child(commentId).setValue(newComment)
                .addOnSuccessListener(aVoid -> {

                    Toast.makeText(this, "Comment added successfully", Toast.LENGTH_SHORT).show();
                    binding.commentEditLayout.setHint("Add Your Valuable Suggestion or Issue");
                    binding.commentEditText.setText("");
                    binding.commenteButton.setEnabled(true);
                    binding.progressBar.setVisibility(View.GONE);

                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to add comment", Toast.LENGTH_SHORT).show());
        binding.commenteButton.setEnabled(true);
        binding.progressBar.setVisibility(View.GONE);

    }





}