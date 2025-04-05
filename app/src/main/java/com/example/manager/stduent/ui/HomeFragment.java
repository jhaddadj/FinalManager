package com.example.manager.stduent.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Toast;

import com.example.manager.R;
import com.example.manager.admin.adapter.TimetableAdapter;
import com.example.manager.admin.model.TimetableEntry;
import com.example.manager.databinding.FragmentHome2Binding;
import com.example.manager.lecturar.ui.ViewScheduleActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;


public class HomeFragment extends Fragment {
    private FragmentHome2Binding binding;
    private DatabaseReference databaseReference;
    private FirebaseAuth auth;
    private DatabaseReference databaseReferenceUser;

    private List<TimetableEntry> timetableEntries = new ArrayList<>();
    private List<TimetableEntry> filteredEntries = new ArrayList<>();
    private TimetableAdapter adapter;
    // Fragment parameters for navigation
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // Fragment parameters
    private String mParam1;
    private String mParam2;

    public HomeFragment() {
        // Required empty public constructor
    }

    /**
     * Creates a new instance of the HomeFragment with specified parameters
     */
    public static HomeFragment newInstance(String param1, String param2) {
        HomeFragment fragment = new HomeFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }

        auth = FirebaseAuth.getInstance();
        databaseReferenceUser = FirebaseDatabase.getInstance().getReference("Users");
        databaseReference = FirebaseDatabase.getInstance().getReference("timetables");
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        binding = FragmentHome2Binding.inflate(inflater, container, false);
        binding.timetableRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        adapter = new TimetableAdapter(filteredEntries, this::onItemClicked, this::onItemLongClicked);
        binding.timetableRecyclerView.setAdapter(adapter);
        binding.textView.setVisibility(View.GONE);
        binding.filterButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showFilterDialog();
            }
        });
        setupSearchView();
        return binding.getRoot();    }
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        checkAccountStatus();
    }

    private void checkAccountStatus() {
        String userId = auth.getCurrentUser().getUid();

        // Fetch user data from Realtime Database
        databaseReferenceUser.child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String status = snapshot.child("status").getValue(String.class);

                    if ("rejected".equalsIgnoreCase(status)) {
                        showRejectionLayout();
                    } else if ("pending".equalsIgnoreCase(status)) {
                        showPendingLayout();
                    } else if ("accepted".equalsIgnoreCase(status)) {
                        // Hide overlay if status is accepted
                        binding.overlayLayout.setVisibility(View.GONE);
                        binding.timetableRecyclerView.setVisibility(View.VISIBLE);
                        binding.upperLayout.setVisibility(View.VISIBLE);
                        loadTimetableData();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Handle database error
            }
        });
    }

    private void showFilterDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.filter_dialog, null);
        builder.setView(dialogView);

        Button startDateButton = dialogView.findViewById(R.id.startDateButton);
        Button endDateButton = dialogView.findViewById(R.id.endDateButton);
        Button startTimeButton = dialogView.findViewById(R.id.startTimeButton);
        Button endTimeButton = dialogView.findViewById(R.id.endTimeButton);
        Button applyFilterButton = dialogView.findViewById(R.id.applyFilterButton);

        CheckBox monday = dialogView.findViewById(R.id.checkbox_monday);
        CheckBox tuesday = dialogView.findViewById(R.id.checkbox_tuesday);
        CheckBox wednesday = dialogView.findViewById(R.id.checkbox_wednesday);
        CheckBox thursday = dialogView.findViewById(R.id.checkbox_thursday);
        CheckBox friday = dialogView.findViewById(R.id.checkbox_friday);

        SharedPreferences preferences = getActivity().getSharedPreferences("FilterPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();

        // Set Default Values
        startDateButton.setText(preferences.getString("startDate", "All"));
        endDateButton.setText(preferences.getString("endDate", "All"));
        startTimeButton.setText(preferences.getString("startTime", "09:00"));
        endTimeButton.setText(preferences.getString("endTime", "14:00"));

        monday.setChecked(preferences.getBoolean("monday", true));
        tuesday.setChecked(preferences.getBoolean("tuesday", true));
        wednesday.setChecked(preferences.getBoolean("wednesday", true));
        thursday.setChecked(preferences.getBoolean("thursday", true));
        friday.setChecked(preferences.getBoolean("friday", true));

        startDateButton.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            new DatePickerDialog(getContext(), (view, year, month, day) -> {
                String date = day + "/" + (month + 1) + "/" + year;
                startDateButton.setText(date);
                editor.putString("startDate", date);
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
        });

        endDateButton.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            new DatePickerDialog(getContext(), (view, year, month, day) -> {
                String date = day + "/" + (month + 1) + "/" + year;
                endDateButton.setText(date);
                editor.putString("endDate", date);
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
        });

        startTimeButton.setOnClickListener(v -> {
            new TimePickerDialog(getContext(), (view, hour, minute) -> {
                if (hour >= 9 && hour <= 14) {
                    String time = String.format("%02d:%02d", hour, minute);
                    startTimeButton.setText(time);
                    editor.putString("startTime", time);
                }
            }, 9, 0, true).show();
        });

        endTimeButton.setOnClickListener(v -> {
            new TimePickerDialog(getContext(), (view, hour, minute) -> {
                if (hour >= 9 && hour <= 14) {
                    String time = String.format("%02d:%02d", hour, minute);
                    endTimeButton.setText(time);
                    editor.putString("endTime", time);
                }
            }, 14, 0, true).show();
        });
        AlertDialog alertDialog = builder.create();

        applyFilterButton.setOnClickListener(v -> {
            editor.putBoolean("monday", monday.isChecked());
            editor.putBoolean("tuesday", tuesday.isChecked());
            editor.putBoolean("wednesday", wednesday.isChecked());
            editor.putBoolean("thursday", thursday.isChecked());
            editor.putBoolean("friday", friday.isChecked());
            editor.apply();
            filterTimetable();
            alertDialog.dismiss();
        });

        alertDialog.show();
    }

    private void filterTimetable() {
        SharedPreferences preferences = getActivity().getSharedPreferences("FilterPrefs", Context.MODE_PRIVATE);
        String startDate = preferences.getString("startDate", "All");
        String endDate = preferences.getString("endDate", "All");
        String startTime = preferences.getString("startTime", "09:00");
        String endTime = preferences.getString("endTime", "14:00");

        filteredEntries.clear();
        for (TimetableEntry entry : timetableEntries) {
            if (matchesFilter(entry, startDate, endDate, startTime, endTime)) {
                filteredEntries.add(entry);
            }
        }
        adapter.notifyDataSetChanged();
    }

    private boolean matchesFilter(TimetableEntry entry, String startDate, String endDate, String startTime, String endTime) {
        SharedPreferences preferences = getActivity().getSharedPreferences("FilterPrefs", Context.MODE_PRIVATE);

        // Get selected weekdays
        boolean monday = preferences.getBoolean("monday", true);
        boolean tuesday = preferences.getBoolean("tuesday", true);
        boolean wednesday = preferences.getBoolean("wednesday", true);
        boolean thursday = preferences.getBoolean("thursday", true);
        boolean friday = preferences.getBoolean("friday", true);

        // Check if the entry's date range falls within the filter range
        if (!isWithinDateRange(entry.getStartDate(), entry.getEndDate(), startDate, endDate)) {
            return false;
        }

        // Check if the entry's weekday matches the selected weekdays
        if (!isDaySelected(entry.getDay(), monday, tuesday, wednesday, thursday, friday)) {
            return false;
        }

        // Check if the entry's time slot is within the selected time range
        if (!isWithinTimeRange(entry.getTimeSlot(), startTime, endTime)) {
            return false;
        }

        return true;
    }

    private boolean isWithinDateRange(String entryStartDate, String entryEndDate, String filterStartDate, String filterEndDate) {
        if (filterStartDate.equals("All") || filterEndDate.equals("All")) {
            return true;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        try {
            Date entryStart = sdf.parse(entryStartDate);
            Date entryEnd = sdf.parse(entryEndDate);
            Date filterStart = sdf.parse(filterStartDate);
            Date filterEnd = sdf.parse(filterEndDate);

            return entryStart != null && entryEnd != null &&
                    filterStart != null && filterEnd != null &&
                    (entryStart.before(filterEnd) && entryEnd.after(filterStart)); // Overlapping check
        } catch (ParseException e) {
            e.printStackTrace();
            return true;
        }
    }

    private boolean isDaySelected(List<String> entryDays, boolean monday, boolean tuesday, boolean wednesday, boolean thursday, boolean friday) {
        for (String day : entryDays) {
            switch (day.toLowerCase()) {
                case "monday": if (monday) return true; break;
                case "tuesday": if (tuesday) return true; break;
                case "wednesday": if (wednesday) return true; break;
                case "thursday": if (thursday) return true; break;
                case "friday": if (friday) return true; break;
            }
        }
        return false; // No match found
    }

    private boolean isWithinTimeRange(String timeSlot, String filterStartTime, String filterEndTime) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());

        // Extract start and end time from entry timeSlot (e.g., "09:00-11:00")
        String[] times = timeSlot.split("-");
        if (times.length != 2) return false; // Invalid time format

        try {
            Date entryStart = sdf.parse(times[0].trim());
            Date entryEnd = sdf.parse(times[1].trim());
            Date filterStart = sdf.parse(filterStartTime);
            Date filterEnd = sdf.parse(filterEndTime);

            return entryStart != null && entryEnd != null &&
                    filterStart != null && filterEnd != null &&
                    (entryStart.before(filterEnd) && entryEnd.after(filterStart)); // Overlapping check
        } catch (ParseException e) {
            e.printStackTrace();
            return true;
        }
    }




    private void loadTimetableData() {

        binding.progressBar.setVisibility(View.VISIBLE);
        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                timetableEntries.clear();
                filteredEntries.clear();

                for (DataSnapshot data : snapshot.getChildren()) {
                    TimetableEntry entry = data.getValue(TimetableEntry.class);
                    if (entry != null) {
                        binding.progressBar.setVisibility(View.INVISIBLE);

                            entry.setCourseId(data.getKey());

                            timetableEntries.add(entry);

                    }else {

                        binding.progressBar.setVisibility(View.INVISIBLE);
                        binding.textView.setVisibility(View.VISIBLE);

                    }
                }
                filteredEntries.addAll(timetableEntries);
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(DatabaseError error) {
                binding.progressBar.setVisibility(View.INVISIBLE);
                Toast.makeText(getActivity(), "Failed to load data.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupSearchView() {
        binding.searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                filterTimetable(query);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filterTimetable(newText);
                return false;
            }
        });
    }

    private void filterTimetable(String query) {
        filteredEntries.clear();
        if (query.isEmpty()) {
            filteredEntries.addAll(timetableEntries);
        } else {
            for (TimetableEntry entry : timetableEntries) {
                if (entry.getCourseName().toLowerCase().contains(query.toLowerCase())) {
                    filteredEntries.add(entry);
                }
            }
        }
        adapter.notifyDataSetChanged();
    }



    private void onItemClicked(TimetableEntry entry) {

        Intent intent = new Intent(getActivity(), ViewScheduleStudentActivity.class);

        intent.putExtra("courseId", entry.getCourseId());
        intent.putExtra("name", entry.getLecturerName());
        intent.putExtra("lecId", entry.getLecturerId());
        startActivity(intent);
    }
    private void onItemLongClicked(TimetableEntry entry) {
        Intent intent = new Intent(getActivity(), ViewScheduleStudentActivity.class);

        intent.putExtra("courseId", entry.getCourseId());
        intent.putExtra("name", entry.getLecturerName());
        intent.putExtra("lecId", entry.getLecturerId());
        startActivity(intent);
    }
    private void showRejectionLayout() {
        binding.overlayLayout.setVisibility(View.VISIBLE);
        binding.overlayMessageTextView.setText("Your account has been rejected by the admin. Please re-upload your correct ID and contract with clear images. If the issue continues, contact the admin department.");
    }

    private void showPendingLayout() {
        binding.overlayLayout.setVisibility(View.VISIBLE);
        binding.overlayMessageTextView.setText("Your account is waiting for admin approval. Please wait until further notification.");
    }
    @Override
    public void onDestroyView() {
        super.onDestroyView();

        binding = null;
    }
}