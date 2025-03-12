package com.example.manager.admin.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.manager.R;
import com.example.manager.admin.adapter.CommentAdapter;
import com.example.manager.admin.model.Comment;
import com.example.manager.databinding.ActivityAddScheduleBinding;
import com.example.manager.lecturar.ui.ViewScheduleActivity;
import com.example.manager.model.Lecturer;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AddScheduleActivity extends AppCompatActivity {
    private ActivityAddScheduleBinding binding;
    private DatabaseReference databaseReference;
    private Calendar startDateCalendar = Calendar.getInstance();
    private Calendar endDateCalendar = Calendar.getInstance();

    private List<String> filteredLecturerIds = new ArrayList<>();


    private List<String> lecturerIds = new ArrayList<>();
    private List<String> lecContact = new ArrayList<>();
    private List<String> lecturerNames = new ArrayList<>();

    private List<String> roomIds = new ArrayList<>();
    private List<String> roomNames = new ArrayList<>();
    private List<String> roomLocations = new ArrayList<>();

    List<String> filteredRoomIds = new ArrayList<>();
    List<String> filteredRoomNames = new ArrayList<>();
    private String lecturerIdis;

    private String roomIdis;

    private String selectedStartTime = "";
    private String selectedEndTime = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityAddScheduleBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        databaseReference = FirebaseDatabase.getInstance().getReference();

        //    loadLecturers();

        setupDayCheckBoxListeners();
        setupDatePickers();

        binding.commentsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.commentsRecyclerView.setHasFixedSize(true);

        binding.saveTimetableButton.setOnClickListener(v -> loadCoursesAndValidate());
        String courseId = getIntent().getStringExtra("courseId");
        if (courseId != null) {
            loadTimetableData(courseId);
            binding.startDateText.setHint("");
            binding.endDateText.setHint("");
        }else {

            binding.main2.setVisibility(View.VISIBLE);
            binding.progressBar.setVisibility(View.INVISIBLE);
            setupTimeSlotPicker();

            filterLecturersByPreferences();
            loadRooms();
        }

    }

private void setupDatePickers() {
    // Start Date Picker
    binding.startDateText.setClickable(true);
    binding.startDateText.setFocusable(true);
    binding.startDateText.setOnClickListener(v -> {
        DatePickerDialog startDatePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    startDateCalendar.set(year, month, dayOfMonth);
                    String startDate = dayOfMonth + "/" + (month + 1) + "/" + year;
                    binding.startDateText.setText(startDate);

                    // Enable end date selection
                    binding.endDateText.setVisibility(View.VISIBLE);
                    binding.endDateText.setClickable(true);
                    binding.endDateText.setFocusable(true);



                },
                startDateCalendar.get(Calendar.YEAR),
                startDateCalendar.get(Calendar.MONTH),
                startDateCalendar.get(Calendar.DAY_OF_MONTH)
        );

        // Set minimum date to today
        startDatePickerDialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        startDatePickerDialog.show();
    });

    // End Date Picker
    binding.endDateText.setOnClickListener(v -> {
        DatePickerDialog endDatePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    endDateCalendar.set(year, month, dayOfMonth);
                    String endDate = dayOfMonth + "/" + (month + 1) + "/" + year;
                    binding.endDateText.setText(endDate);

                    // Validate end date
                    if (endDateCalendar.before(startDateCalendar) || endDateCalendar.equals(startDateCalendar)) {
                        Toast.makeText(this, "End date must be after start date", Toast.LENGTH_SHORT).show();

                    }
                },
                endDateCalendar.get(Calendar.YEAR),
                endDateCalendar.get(Calendar.MONTH),
                endDateCalendar.get(Calendar.DAY_OF_MONTH)
        );

        // Set minimum date to start date + 1 day
        endDatePickerDialog.getDatePicker().setMinDate(startDateCalendar.getTimeInMillis() + (24 * 60 * 60 * 1000));
        endDatePickerDialog.show();
    });
}

    private void loadComments(String courseId) {
        DatabaseReference commentsRef = FirebaseDatabase.getInstance().getReference().child("comments").child(courseId);


        commentsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Comment> commentsList = new ArrayList<>();
                if (snapshot.exists()) {
                    for (DataSnapshot commentSnapshot : snapshot.getChildren()) {
                        Comment comment = commentSnapshot.getValue(Comment.class);
                        if (comment != null) {
                            commentsList.add(comment);
                        }
                    }

                    CommentAdapter adapter = new CommentAdapter(commentsList);
                    binding.commentsRecyclerView.setAdapter(adapter);

                    binding.linearLayout4.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }
    private void loadTimetableData(String courseId) {
        databaseReference.child("timetables").child(courseId).get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult().exists()) {
                DataSnapshot snapshot = task.getResult();
                binding.courseNameEditText.setText(snapshot.child("courseName").getValue(String.class));
                binding.classDurationEditText.setText(snapshot.child("timeSlot").getValue(String.class));
                binding.startDateText.setText(snapshot.child("startDate").getValue(String.class));
                binding.endDateText.setText(snapshot.child("endDate").getValue(String.class));

                // Select lecturer and room based on their IDs
                lecturerIdis = snapshot.child("lecturerId").getValue(String.class);
                roomIdis = snapshot.child("roomId").getValue(String.class);
                if (lecturerIdis != null && !lecturerIds.isEmpty()) {
                    int lecturerIndex = lecturerIds.indexOf(lecturerIdis);
                    if (lecturerIndex >= 0) {
                        binding.lecturerSpinner.setSelection(lecturerIndex);
                    }
                }

                if (roomIdis != null && !roomIds.isEmpty()) {
                    int roomIndex = roomIds.indexOf(roomIdis);
                    if (roomIndex >= 0) {
                        binding.roomSpinner.setSelection(roomIndex);
                    }
                }

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
                setupTimeSlotPicker();
                loadComments(courseId);

            }
        });
        filterLecturersByPreferences();
        loadRooms();
    }


    private void setupDayCheckBoxListeners() {
        CompoundButton.OnCheckedChangeListener listener = (buttonView, isChecked) -> {
            // Check if all checkboxes are unchecked
            if (!binding.mondayCheckBox.isChecked() &&
                    !binding.tuesdayCheckBox.isChecked() &&
                    !binding.wednesdayCheckBox.isChecked() &&
                    !binding.thursdayCheckBox.isChecked() &&
                    !binding.fridayCheckBox.isChecked()) {

                // Automatically check all checkboxes
                binding.mondayCheckBox.setChecked(true);
                binding.tuesdayCheckBox.setChecked(true);
                binding.wednesdayCheckBox.setChecked(true);
                binding.thursdayCheckBox.setChecked(true);
                binding.fridayCheckBox.setChecked(true);

            } else {
                // Reload rooms and filter lecturers when a checkbox is toggled
                loadRooms();
                filterLecturersByPreferences();
            }
        };

        // Attach the listener to each checkbox
        binding.mondayCheckBox.setOnCheckedChangeListener(listener);
        binding.tuesdayCheckBox.setOnCheckedChangeListener(listener);
        binding.wednesdayCheckBox.setOnCheckedChangeListener(listener);
        binding.thursdayCheckBox.setOnCheckedChangeListener(listener);
        binding.fridayCheckBox.setOnCheckedChangeListener(listener);
    }


    private void setupTimeSlotPicker() {
        binding.classDurationEditText.setOnClickListener(v -> {
            TimePickerDialog startTimePicker = new TimePickerDialog(this, (view, hourOfDay, minute) -> {
                selectedStartTime = formatTime(hourOfDay, minute);

                TimePickerDialog endTimePicker = new TimePickerDialog(this, (view1, hourOfDay1, minute1) -> {
                    selectedEndTime = formatTime(hourOfDay1, minute1);

                    if (isValidTimeSlot(selectedStartTime, selectedEndTime)) {
                        binding.classDurationEditText.setText(selectedStartTime + " - " + selectedEndTime);
                        filterLecturersByPreferences();
                        loadRooms();

                    } else {
                        Toast.makeText(this, "Invalid time slot. Please select between 9 AM and 2 PM.", Toast.LENGTH_SHORT).show();
                    }
                }, 14, 0, true);
                endTimePicker.show();
            }, 9, 0, true);
            startTimePicker.show();
        });
    }

    private void setupSpinners() {
        binding.lecturerSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new ArrayList<>()));
        binding.roomSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new ArrayList<>()));
    }

    private String formatTime(int hourOfDay, int minute) {
        return String.format("%02d:%02d", hourOfDay, minute);
    }

    private boolean isValidTimeSlot(String start, String end) {
        return start.compareTo("09:00") >= 0 && end.compareTo("14:00") <= 0 && start.compareTo(end) < 0;
    }

    private void loadCoursesAndValidate() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.saveTimetableButton.setEnabled(false);
        databaseReference.child("timetables").get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                String courseId = getIntent().getStringExtra("courseId");
                if (courseId != null) {
                    validateRoomAvailabilityAndSave();

                } else {
                    for (DataSnapshot snapshot : task.getResult().getChildren()) {
                        String existingCourseName = snapshot.child("courseName").getValue(String.class);

                        if (binding.courseNameEditText.getText().toString().equalsIgnoreCase(existingCourseName)) {
                            Toast.makeText(this, "Course name already exists. Please choose another name.", Toast.LENGTH_SHORT).show();
                            binding.progressBar.setVisibility(View.INVISIBLE);
                            binding.saveTimetableButton.setEnabled(true);
                            return;
                        }
                    }
                    validateRoomAvailabilityAndSave();
                }
            } else {
                binding.progressBar.setVisibility(View.INVISIBLE);
                binding.saveTimetableButton.setEnabled(true);
            }
        });
    }

    private void validateRoomAvailabilityAndSave() {
        databaseReference.child("timetables").get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                String selectedRoomId = roomIds.get(binding.roomSpinner.getSelectedItemPosition());

                for (DataSnapshot snapshot : task.getResult().getChildren()) {
                    String roomId = snapshot.child("roomId").getValue(String.class);
                    String timeSlot = snapshot.child("timeSlot").getValue(String.class);
                    List<String> days = (List<String>) snapshot.child("day").getValue();


                }
                saveTimetableData();
            } else {
                binding.progressBar.setVisibility(View.INVISIBLE);
                binding.saveTimetableButton.setEnabled(true);
            }
        });
    }

    private void loadLecturers() {
        databaseReference.child("preferences").get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                for (DataSnapshot snapshot : task.getResult().getChildren()) {
                    String lecturerName = snapshot.child("lecName").getValue(String.class);
                    String lecturerContact = snapshot.child("lecContact").getValue(String.class);
                    lecturerIds.add(snapshot.getKey());
                    lecturerNames.add(lecturerName);
                    lecContact.add(lecturerContact);
                }
                updateLecturerSpinner();
            }
        });
    }

    private void updateLecturerSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, lecturerNames);
        binding.lecturerSpinner.setAdapter(adapter);
    }


    private void loadRooms() {
        databaseReference.child("resources").get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                List<String> filteredRoomIds = new ArrayList<>();
                List<String> filteredRoomNames = new ArrayList<>();
                List<String> filteredRoomLocation = new ArrayList<>();

                for (DataSnapshot snapshot : task.getResult().getChildren()) {
                    String roomName = snapshot.child("name").getValue(String.class);
                    String location = snapshot.child("location").getValue(String.class);
                    String roomId = snapshot.getKey();
                    String adminId = snapshot.child("adminId").getValue(String.class);
                    String available = snapshot.child("isAvailable").getValue(String.class);
                    binding.saveTimetableButton.setEnabled(true);
                    if (FirebaseAuth.getInstance().getCurrentUser().getUid().equals(adminId) && "yes".equalsIgnoreCase(available)) {
                        filteredRoomIds.add(roomId);
                        filteredRoomNames.add(roomName);
                        filteredRoomLocation.add(location);
                    }
                }

                databaseReference.child("timetables").get().addOnCompleteListener(timetableTask -> {
                    if (timetableTask.isSuccessful()) {
                        List<String> roomsToRemove = new ArrayList<>();
                        for (DataSnapshot timetableSnapshot : timetableTask.getResult().getChildren()) {
                            String roomId = timetableSnapshot.child("roomId").getValue(String.class);
                            String preferredHours = timetableSnapshot.child("timeSlot").getValue(String.class);

                            List<String> assignedDays = (List<String>) timetableSnapshot.child("day").getValue();

                            if (preferredHours != null) {
                                String[] timeRange = preferredHours.split("-");
                                if (timeRange.length == 2) {
                                    String existingStartTime = timeRange[0];
                                    String existingEndTime = timeRange[1];

                                    if (roomId != null) {
                                        boolean daysOverlap = matchesSelectedDays(assignedDays);
                                        boolean timeOverlap = isTimeOverlap(selectedStartTime, selectedEndTime, existingStartTime, existingEndTime);

                                        // Check if both time and day overlap
                                        if (daysOverlap && !timeOverlap) {
                                            String courseId = getIntent().getStringExtra("courseId");
                                            if (courseId != null) {
                                                if (!roomIdis.equalsIgnoreCase(roomId)) {
                                                    roomsToRemove.add(roomId);
                                                }
                                            } else {
                                                roomsToRemove.add(roomId);

                                            }
                                        }

                                    }

                                }
                            }

                            for (String roomIds : roomsToRemove) {
                                int index = filteredRoomIds.indexOf(roomIds);
                                if (index >= 0) {
                                    filteredRoomIds.remove(index);
                                    filteredRoomNames.remove(index);
                                    filteredRoomLocation.remove(index);
                                }
                            }

                            roomIds.clear();
                            roomNames.clear();
                            roomLocations.clear();
                            roomIds.addAll(filteredRoomIds);
                            roomNames.addAll(filteredRoomNames);
                            roomLocations.addAll(filteredRoomLocation);

                            updateRoomSpinner();
                        }
                    }
                });

                // Check each room for overlapping schedules in the timetables

            }
        });
    }

    private void updateRoomSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, roomNames);
        binding.roomSpinner.setAdapter(adapter);
    }


    private void filterLecturersByPreferences() {
        databaseReference.child("preferences").get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                List<Lecturer> lecturers = new ArrayList<>();
                List<String> lecToRemove = new ArrayList<>();
                List<String> filteredLecIds = new ArrayList<>();
                List<String> filteredLecNames = new ArrayList<>();
                List<String> filteredLecContact = new ArrayList<>();


                // Step 1: Populate initial lecturer list based on preferences
                for (DataSnapshot snapshot : task.getResult().getChildren()) {
                    String lecturerId = snapshot.getKey();
                    String lecturerName = snapshot.child("lecName").getValue(String.class);
                    String lecturerContact = snapshot.child("lecContact").getValue(String.class);

                    String preferredHours = snapshot.child("hours").getValue(String.class);
                    List<String> preferredDays = (List<String>) snapshot.child("days").getValue();

                    if (preferredDays != null && preferredHours != null) {
                        if (matchesSelectedDays(preferredDays)) {
                            int proximityScore = calculateProximity(preferredHours, selectedStartTime, selectedEndTime);
                            if (proximityScore != Integer.MAX_VALUE) {
                                lecturers.add(new Lecturer(lecturerId, lecturerName,lecturerContact, proximityScore));
                            }
                        }
                    }
                }

                // Step 2: Check if courseId is provided, and filter by timetable conflicts

                databaseReference.child("timetables").get().addOnCompleteListener(timetableTask -> {
                    if (timetableTask.isSuccessful()) {
                        for (DataSnapshot timetableSnapshot : timetableTask.getResult().getChildren()) {
                            String lecturerId = timetableSnapshot.child("lecturerId").getValue(String.class);
                            String preferredHours = timetableSnapshot.child("timeSlot").getValue(String.class);
                            List<String> assignedDays = (List<String>) timetableSnapshot.child("day").getValue();

                            if (lecturerId != null && preferredHours != null && assignedDays != null) {
                                String[] timeRange = preferredHours.split("-");
                                if (timeRange.length == 2) {
                                    String existingStartTime = timeRange[0];
                                    String existingEndTime = timeRange[1];

                                    boolean daysOverlap = matchesSelectedDays(assignedDays);
                                    boolean timeOverlap = isTimeOverlap(selectedStartTime, selectedEndTime, existingStartTime, existingEndTime);

                                    // If both days and time overlap, mark the lecturer for removal
                                    if (daysOverlap && !timeOverlap) {
                                        String courseId = getIntent().getStringExtra("courseId");
                                        if (courseId != null) {
                                            if (!lecturerIdis.equalsIgnoreCase(lecturerId)) {

                                                lecToRemove.add(lecturerId);
                                            }
                                        } else {
                                            lecToRemove.add(lecturerId);

                                        }

                                    }
                                }
                            }
                        }

                        lecturers.removeIf(lecturer -> lecToRemove.contains(lecturer.getId()));

                        updateLecturerLists(lecturers, filteredLecIds, filteredLecNames,filteredLecContact);
                    }
                });

            }
        });
    }

    private void updateLecturerLists(List<Lecturer> lecturers, List<String> filteredLecIds, List<String> filteredLecNames,List<String> filteredLecContact) {

        Collections.sort(lecturers, Comparator.comparingInt(Lecturer::getProximityScore));

        filteredLecIds.clear();
        filteredLecNames.clear();
        filteredLecContact.clear();
        for (Lecturer lecturer : lecturers) {
            filteredLecIds.add(lecturer.getId());
            filteredLecNames.add(lecturer.getName());
            filteredLecContact.add(lecturer.getContact());
        }

        lecturerNames.clear();
        lecturerIds.clear();
        lecContact.clear();
        lecturerNames.addAll(filteredLecNames);
        lecturerIds.addAll(filteredLecIds);
        lecContact.addAll(filteredLecContact);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, lecturerNames);
        binding.lecturerSpinner.setAdapter(adapter);

    }



    private int calculateProximity(String preferredHours, String courseStartTime, String courseEndTime) {
        String[] times = preferredHours.split("-");
        if (times.length == 2) {
            String lecturerStartTime = times[0];
            String lecturerEndTime = times[1];

            // Calculate proximity
            int startDifference = getTimeDifferenceInMinutes(courseStartTime, lecturerStartTime);
            int endDifference = getTimeDifferenceInMinutes(courseEndTime, lecturerEndTime);

            // Total proximity score
            return Math.abs(startDifference) + Math.abs(endDifference);
        }
        return Integer.MAX_VALUE;
    }

    private int getTimeDifferenceInMinutes(String time1, String time2) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            Date date1 = sdf.parse(time1);
            Date date2 = sdf.parse(time2);

            if (date1 != null && date2 != null) {
                long diff = date1.getTime() - date2.getTime();

                if (diff < 0) {
                    diff += 24 * 60 * 60 * 1000;
                }

                return (int) (diff / (1000 * 60));
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return Integer.MAX_VALUE;
    }


    private boolean matchesSelectedDays(List<String> preferredDays) {
        List<String> selectedDays = getSelectedDays();
        for (String day : selectedDays) {
            if (preferredDays.contains(day)) return true;
        }
        return false;
    }

    private List<String> getSelectedDays() {
        List<String> selectedDays = new ArrayList<>();
        if (binding.mondayCheckBox.isChecked()) selectedDays.add("Monday");
        if (binding.tuesdayCheckBox.isChecked()) selectedDays.add("Tuesday");
        if (binding.wednesdayCheckBox.isChecked()) selectedDays.add("Wednesday");
        if (binding.thursdayCheckBox.isChecked()) selectedDays.add("Thursday");
        if (binding.fridayCheckBox.isChecked()) selectedDays.add("Friday");
        return selectedDays;
    }

    private boolean isTimeOverlap(String start1, String end1, String start2, String end2) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());

            Date startTime1 = sdf.parse(start1);
            Date endTime1 = sdf.parse(end1);
            Date startTime2 = sdf.parse(start2);
            Date endTime2 = sdf.parse(end2);

            if (startTime1 != null && endTime1 != null && startTime2 != null && endTime2 != null) {
                return endTime1.compareTo(startTime2) <= 0 || endTime2.compareTo(startTime1) <= 0;
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }

        return false;
    }


    private void saveTimetableData() {
        String courseName = binding.courseNameEditText.getText().toString();
        String duration = binding.classDurationEditText.getText().toString();
        String start = binding.startDateText.getText().toString();
        String end = binding.endDateText.getText().toString();

        int lecturerPosition = binding.lecturerSpinner.getSelectedItemPosition();
        int roomPosition = binding.roomSpinner.getSelectedItemPosition();
        List<String> selectedDays = getSelectedDays();
        if (courseName.isEmpty()) {
            Toast.makeText(this, "Course name cannot be empty.", Toast.LENGTH_SHORT).show();
            binding.progressBar.setVisibility(View.INVISIBLE);
            binding.saveTimetableButton.setEnabled(true);
            return;
        }
        if (start.isEmpty() || end.isEmpty()) {
            Toast.makeText(this, "Date cannot be empty.", Toast.LENGTH_SHORT).show();
            binding.progressBar.setVisibility(View.INVISIBLE);
            binding.saveTimetableButton.setEnabled(true);
            return;
        }

        if (selectedDays.isEmpty()) {
            selectedDays = Arrays.asList("Monday", "Tuesday", "Wednesday", "Thursday", "Friday");
        }

        if (selectedStartTime.isEmpty() || selectedEndTime.isEmpty()) {

            selectedStartTime = "09:00";
            selectedEndTime = "14:00";
        }
        String lecturerId = lecturerIds.get(lecturerPosition);
        String lecturerName = lecturerNames.get(lecturerPosition);
        String lecturerContact = lecContact.get(lecturerPosition);
        String roomId = roomIds.get(roomPosition);
        String roomName = roomNames.get(roomPosition);
        String roomLcation = roomLocations.get(roomPosition);

        Map<String, Object> timetableEntry = new HashMap<>();
        timetableEntry.put("courseName", courseName);
        timetableEntry.put("adminId", FirebaseAuth.getInstance().getCurrentUser().getUid());
        timetableEntry.put("lecturerId", lecturerId);
        timetableEntry.put("lecturerName", lecturerName);
        timetableEntry.put("lecContact", lecturerContact);
        timetableEntry.put("roomId", roomId);
        timetableEntry.put("roomName", roomName);
        timetableEntry.put("location", roomLcation);
        timetableEntry.put("timeSlot", duration);
        timetableEntry.put("day", selectedDays);
        timetableEntry.put("startDate", start);
        timetableEntry.put("endDate", end);
        String courseId = getIntent().getStringExtra("courseId");
        if (courseId != null) {
            databaseReference.child("timetables").child(courseId).updateChildren(timetableEntry)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            binding.progressBar.setVisibility(View.INVISIBLE);
                            binding.saveTimetableButton.setEnabled(true);
                            Toast.makeText(this, "Timetable data updated successfully!", Toast.LENGTH_SHORT).show();
                            finish();
                        } else {
                            binding.progressBar.setVisibility(View.INVISIBLE);
                            binding.saveTimetableButton.setEnabled(true);
                            Toast.makeText(this, "Failed to update data.", Toast.LENGTH_SHORT).show();
                        }
                    });
        } else {

            databaseReference.child("timetables").push().setValue(timetableEntry).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    binding.progressBar.setVisibility(View.INVISIBLE);
                    binding.saveTimetableButton.setEnabled(true);
                    Toast.makeText(this, "Timetable data saved successfully!", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    binding.progressBar.setVisibility(View.INVISIBLE);
                    binding.saveTimetableButton.setEnabled(true);
                    Toast.makeText(this, "Failed to save data.", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
}