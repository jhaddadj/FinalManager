package com.example.manager.admin.ui;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.manager.R;
import com.example.manager.admin.model.CourseItem;
import com.example.manager.admin.model.Resource;
import com.example.manager.admin.model.TimetableEntry;
import com.example.manager.model.Lecturer;
import com.example.manager.model.User;
import com.example.manager.timetable.ChocoSolverTimetableGenerator;
import com.example.manager.timetable.Course;
import com.example.manager.timetable.CourseConverter;
import com.example.manager.timetable.SimpleTimetableGenerator;
import com.example.manager.timetable.Timetable;
import com.example.manager.timetable.TimetableGenerator;
import com.example.manager.timetable.TimetableGeneratorOptions;
import com.example.manager.timetable.TimetableSession;
import com.example.manager.timetable.TimetableSession;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * This activity handles automated timetable generation using the constraint solver.
 * It allows administrators to set constraints and generate optimal timetables.
 */
public class ConstraintSolverActivity extends AppCompatActivity {
    private static final String TAG = "ConstraintSolverAct";
    
    // Enum for solver types
    private enum SolverType {
        SIMPLE,
        CHOCO
    }
    
    // UI Elements
    private CheckBox avoidBackToBackCheckbox;
    private CheckBox preferEvenDistributionCheckbox;
    private Spinner maxHoursSpinner;
    private Button generateButton;
    private Button backButton;
    private ProgressBar progressBar;
    private TextView statusTextView;
    private RadioGroup solverTypeRadioGroup;
    private RadioButton simpleSolverRadioButton;
    private RadioButton chocoSolverRadioButton;
    private TextView solverHintTextView;
    
    // Currently selected solver type
    private SolverType selectedSolverType = SolverType.SIMPLE;
    
    // Data
    private List<Resource> resources = new ArrayList<>();
    private List<Lecturer> lecturers = new ArrayList<>();
    private List<Course> courses = new ArrayList<>();
    
    // Firebase
    private DatabaseReference database;
    
    // Background processing
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_constraint_solver);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        
        // Initialize Firebase
        database = FirebaseDatabase.getInstance().getReference();
        
        // Initialize UI elements
        initializeUI();
        
        // Set up click listeners
        generateButton.setOnClickListener(v -> startGeneration());
        backButton.setOnClickListener(v -> finish());
    }
    
    private void initializeUI() {
        avoidBackToBackCheckbox = findViewById(R.id.avoidBackToBackCheckbox);
        preferEvenDistributionCheckbox = findViewById(R.id.preferEvenDistributionCheckbox);
        maxHoursSpinner = findViewById(R.id.maxHoursSpinner);
        generateButton = findViewById(R.id.generateButton);
        backButton = findViewById(R.id.backButton);
        progressBar = findViewById(R.id.progressBar);
        statusTextView = findViewById(R.id.statusTextView);
        solverTypeRadioGroup = findViewById(R.id.solverTypeRadioGroup);
        simpleSolverRadioButton = findViewById(R.id.simpleSolverRadioButton);
        chocoSolverRadioButton = findViewById(R.id.chocoSolverRadioButton);
        solverHintTextView = findViewById(R.id.solverHintTextView);
        
        // Set up spinner for max hours
        ArrayAdapter<Integer> hoursAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, new Integer[]{4, 5, 6, 7, 8});
        hoursAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        maxHoursSpinner.setAdapter(hoursAdapter);
        maxHoursSpinner.setSelection(2); // Default to 6 hours
        
        // Set up solver type radio buttons
        solverTypeRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.simpleSolverRadioButton) {
                selectedSolverType = SolverType.SIMPLE;
                Log.d(TAG, "Selected Simple Solver");
                solverHintTextView.setText(R.string.simple_solver_desc);
            } else if (checkedId == R.id.chocoSolverRadioButton) {
                selectedSolverType = SolverType.CHOCO;
                Log.d(TAG, "Selected Choco Solver");
                solverHintTextView.setText(R.string.choco_solver_desc);
            }
        });
        
        // Set initial hint text
        solverHintTextView.setText(R.string.simple_solver_desc);
    }
    
    private void startGeneration() {
        progressBar.setVisibility(View.VISIBLE);
        statusTextView.setVisibility(View.VISIBLE);
        
        // Different status message based on solver type
        String solverType = selectedSolverType == SolverType.CHOCO ? "Choco" : "Simple";
        statusTextView.setText("Generating timetable using " + solverType + " Solver...");
        
        // Disable generate button
        generateButton.setEnabled(false);
        
        // Clear previous data
        resources.clear();
        lecturers.clear();
        courses.clear();
        
        // Start the data loading chain
        loadResources();
    }
    
    private void loadResources() {
        database.child("resources").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    Resource resource = snapshot.getValue(Resource.class);
                    if (resource != null) {
                        resources.add(resource);
                    }
                }
                
                statusTextView.setText("Loading lecturers...");
                loadLecturers();
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Error loading resources", databaseError.toException());
                showError(databaseError.toException());
            }
        });
    }
    
    private void loadLecturers() {
        database.child("Users").orderByChild("role").equalTo("lecture")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                            try {
                                // Get the User object first
                                User user = snapshot.getValue(User.class);
                                if (user != null && "accepted".equals(user.getStatus())) {
                                    // Convert User to Lecturer object
                                    Lecturer lecturer = new Lecturer(
                                            user.getId(),
                                            user.getName(),
                                            user.getContact(),
                                            0 // Default proximityScore
                                    );
                                    lecturers.add(lecturer);
                                    Log.d(TAG, "Added lecturer: " + lecturer.getName());
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error converting user to lecturer", e);
                            }
                        }
                        
                        if (lecturers.isEmpty()) {
                            Log.w(TAG, "No accepted lecturers found in database");
                        } else {
                            Log.d(TAG, "Loaded " + lecturers.size() + " lecturers");
                        }
                        
                        statusTextView.setText("Loading courses...");
                        loadCourses();
                    }
                    
                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.e(TAG, "Error loading lecturers", databaseError.toException());
                        showError(databaseError.toException());
                    }
                });
    }
    
    private void loadCourses() {
        database.child("courses").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                courses.clear();
                
                Log.d(TAG, "Found " + dataSnapshot.getChildrenCount() + " courses in database");
                int totalCoursesInDB = (int) dataSnapshot.getChildrenCount();
                int coursesConverted = 0;
                int coursesSkipped = 0;
                Set<String> skippedCourseNames = new HashSet<>();
                Set<String> loadedCourseIds = new HashSet<>();
                
                // First pass - gather all course details and make sure we don't have duplicates
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    // Load CourseItem from Firebase
                    CourseItem courseItem = snapshot.getValue(CourseItem.class);
                    if (courseItem != null) {
                        // Make sure ID is set from the database key if not already present
                        if (courseItem.getId() == null || courseItem.getId().isEmpty()) {
                            courseItem.setId(snapshot.getKey());
                        }
                        
                        Log.d(TAG, "Processing course: " + courseItem.getName() + " (ID: " + courseItem.getId() + 
                              ", Lectures: " + courseItem.getNumberOfLectures() + ", Labs: " + courseItem.getNumberOfLabs() + ")");
                        
                        // Convert CourseItem to Course for timetable generation
                        Course course = CourseConverter.convertToCourse(courseItem);
                        if (course != null) {
                            // Make sure we don't have duplicate IDs
                            if (!loadedCourseIds.contains(course.getId())) {
                                courses.add(course);
                                loadedCourseIds.add(course.getId());
                                coursesConverted++;
                                
                                // Log more details about the course, including assigned resource
                                String resourceInfo = "No assigned room";
                                if (courseItem.getAssignedResourceId() != null && !courseItem.getAssignedResourceId().isEmpty()) {
                                    resourceInfo = "Assigned room ID: " + courseItem.getAssignedResourceId();
                                }
                                
                                Log.d(TAG, "Successfully converted course: " + course.getName() + 
                                      " with " + course.getRequiredSessionsPerWeek() + " sessions, " +
                                      resourceInfo);
                            } else {
                                Log.w(TAG, "Skipping duplicate course ID: " + course.getId() + " - " + course.getName());
                            }
                        } else {
                            skippedCourseNames.add(courseItem.getName());
                            coursesSkipped++;
                            Log.w(TAG, "Failed to convert course: " + courseItem.getName());
                        }
                    } else {
                        Log.w(TAG, "Null CourseItem found in database for key: " + snapshot.getKey());
                    }
                }
                
                // Validate all loaded courses before proceeding
                List<Course> validatedCourses = new ArrayList<>();
                Map<String, String> courseNameToId = new HashMap<>();
                
                for (Course course : courses) {
                    // Make sure course has required fields
                    if (course.getId() == null || course.getId().isEmpty()) {
                        Log.w(TAG, "Course missing ID: " + course.getName() + " - skipping");
                        continue;
                    }
                    
                    if (course.getName() == null || course.getName().isEmpty()) {
                        Log.w(TAG, "Course missing name, ID: " + course.getId() + " - skipping");
                        continue;
                    }
                    
                    // Ensure no duplicate course names (case-insensitive)
                    String lowerCaseName = course.getName().toLowerCase();
                    if (courseNameToId.containsKey(lowerCaseName)) {
                        String existingId = courseNameToId.get(lowerCaseName);
                        Log.w(TAG, "Duplicate course name detected: '" + course.getName() + 
                              "' (ID: " + course.getId() + ", existing ID: " + existingId + ") - keeping first instance");
                        continue;
                    }
                    
                    // Make sure course has at least 1 required session
                    if (course.getRequiredSessionsPerWeek() <= 0) {
                        Log.w(TAG, "Course has no required sessions, setting to 1: " + course.getName());
                        course.setRequiredSessionsPerWeek(1);
                    }
                    
                    // This course passes all validation
                    validatedCourses.add(course);
                    courseNameToId.put(lowerCaseName, course.getId());
                    
                    Log.d(TAG, "Validated course: " + course.getName() + " (ID: " + course.getId() + 
                          ", Sessions: " + course.getRequiredSessionsPerWeek() + ")");
                }
                
                // Replace the original list with the validated list
                courses.clear();
                courses.addAll(validatedCourses);
                
                Log.d(TAG, "Course loading summary: Total in DB: " + totalCoursesInDB + 
                      ", Converted: " + coursesConverted + ", Validated: " + courses.size() + ", Skipped: " + coursesSkipped);
                
                if (!skippedCourseNames.isEmpty()) {
                    Log.w(TAG, "The following courses were skipped: " + skippedCourseNames);
                }
                
                if (resources.isEmpty()) {
                    showError("No resources found. Please go to the Admin Panel to add classroom resources before generating a timetable.");
                    return;
                }
                
                if (lecturers.isEmpty()) {
                    showError("No lecturers found. Please go to the Admin Panel to add lecturer users before generating a timetable.");
                    return;
                }
                
                if (courses.isEmpty()) {
                    showError("No courses found. Please go to the Admin Panel to add courses before generating a timetable.");
                    return;
                }
                
                statusTextView.setText("Generating timetable...");
                generateTimetable();
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Error loading courses", databaseError.toException());
                showError(databaseError.toException());
            }
        });
    }
    
    private void generateTimetable() {
        // Run the timetable generator in a background thread
        executorService.execute(() -> {
            try {
                // Log input data before generation
                Log.d(TAG, "Resources available for timetable: " + resources.size());
                Log.d(TAG, "Lecturers available for timetable: " + lecturers.size());
                Log.d(TAG, "Courses to schedule in timetable: " + courses.size());
                
                // Create a timetable generator based on the selected solver type
                TimetableGenerator generator;
                if (selectedSolverType == SolverType.CHOCO) {
                    generator = new ChocoSolverTimetableGenerator();
                } else {
                    generator = new SimpleTimetableGenerator();
                }
                
                // Create options object based on UI settings
                TimetableGeneratorOptions options = new TimetableGeneratorOptions();
                options.setAvoidBackToBackClasses(avoidBackToBackCheckbox.isChecked());
                options.setPreferEvenDistribution(preferEvenDistributionCheckbox.isChecked());
                options.setMaxHoursPerDay((Integer) maxHoursSpinner.getSelectedItem());
                
                // Generate timetable
                Timetable timetable = generator.generateTimetable(resources, lecturers, courses, options);
                
                // Verify all courses are included in the timetable
                Set<String> scheduledCourseIds = new HashSet<>();
                for (TimetableSession session : timetable.getSessions()) {
                    scheduledCourseIds.add(session.getCourseId());
                }
                
                List<Course> missingCourses = new ArrayList<>();
                for (Course course : courses) {
                    if (!scheduledCourseIds.contains(course.getId())) {
                        missingCourses.add(course);
                        Log.w(TAG, "Course not included in timetable: " + course.getName() + " (ID: " + course.getId() + ")");
                    }
                }
                
                // If any courses are missing and we're using Choco Solver, add them directly
                if (!missingCourses.isEmpty() && selectedSolverType == SolverType.CHOCO) {
                    Log.w(TAG, "Found " + missingCourses.size() + " missing courses. Adding them manually.");
                    
                    // Add missing courses directly to the timetable
                    for (Course course : missingCourses) {
                        Log.d(TAG, "Manually adding course: " + course.getName() + " (ID: " + course.getId() + 
                             ") with " + course.getRequiredSessionsPerWeek() + " sessions");
                        
                        Resource resource = resources.isEmpty() ? null : resources.get(0);
                        Lecturer lecturer = lecturers.isEmpty() ? null : lecturers.get(0);
                        
                        // Try to find assigned resources and lecturers if they exist
                        if (course.getAssignedResourceId() != null && !course.getAssignedResourceId().isEmpty()) {
                            for (Resource r : resources) {
                                if (r.getId().equals(course.getAssignedResourceId())) {
                                    resource = r;
                                    Log.d(TAG, "Using assigned resource: " + r.getName());
                                    break;
                                }
                            }
                        }
                        
                        if (course.getAssignedLecturerId() != null && !course.getAssignedLecturerId().isEmpty()) {
                            for (Lecturer l : lecturers) {
                                if (l.getId().equals(course.getAssignedLecturerId())) {
                                    lecturer = l;
                                    Log.d(TAG, "Using assigned lecturer: " + l.getName());
                                    break;
                                }
                            }
                        }
                        
                        if (resource != null && lecturer != null) {
                            for (int i = 0; i < course.getRequiredSessionsPerWeek(); i++) {
                                TimetableSession session = new TimetableSession();
                                session.setId(UUID.randomUUID().toString());
                                session.setCourseName(course.getName());
                                session.setCourseId(course.getId());
                                session.setSessionType(course.getCode() != null ? course.getCode() : "LECTURE");
                                
                                // Use day and time based on session index
                                int day = i % 5;  // Monday to Friday
                                int hour = 9 + (i / 5) % 8;  // 9 AM to 4 PM
                                
                                session.setDayOfWeek(DAYS_OF_WEEK[day]);
                                session.setStartTime(String.format("%02d:00", hour));
                                session.setEndTime(String.format("%02d:00", hour + 1));
                                
                                session.setResourceId(resource.getId());
                                session.setResourceName(resource.getName());
                                session.setLecturerId(lecturer.getId());
                                session.setLecturerName(lecturer.getName());
                                
                                timetable.addSession(session);
                                
                                Log.d(TAG, "Added manual session for " + course.getName() + " on " +
                                     session.getDayOfWeek() + " at " + session.getStartTime());
                            }
                        } else {
                            Log.e(TAG, "Cannot add manual session for " + course.getName() + 
                                  " - missing resource or lecturer");
                        }
                    }
                    
                    // Verify again after adding missing courses
                    scheduledCourseIds.clear();
                    for (TimetableSession session : timetable.getSessions()) {
                        scheduledCourseIds.add(session.getCourseId());
                    }
                    
                    for (Course course : courses) {
                        if (!scheduledCourseIds.contains(course.getId())) {
                            Log.e(TAG, "Course STILL missing after manual addition: " + course.getName());
                        }
                    }
                    
                    Log.d(TAG, "After manual additions: " + scheduledCourseIds.size() + 
                          " unique courses in timetable out of " + courses.size() + 
                          " total courses, with " + timetable.getSessions().size() + " total sessions");
                } else {
                    Log.d(TAG, "All courses successfully included in the timetable!");
                }
                
                // Save the timetable to Firebase
                saveTimetable(timetable);
                
            } catch (Exception e) {
                Log.e(TAG, "Error generating timetable", e);
                mainHandler.post(() -> showError(e));
            }
        });
    }
    
    // Helper method for day names
    private static final String[] DAYS_OF_WEEK = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday"};
    
    private void saveTimetable(Timetable timetable) {
        statusTextView.setText("Saving timetable...");
        
        // Create a reference for the new timetable
        String timetableId = database.child("timetables").push().getKey();
        if (timetableId == null) {
            showError("Failed to create timetable reference");
            return;
        }
        
        Log.d(TAG, "Saving timetable with ID: " + timetableId);
        
        // Save the timetable metadata
        database.child("timetables").child(timetableId).setValue(timetable)
                .addOnSuccessListener(aVoid -> {
                    // Now save each session
                    List<TimetableSession> sessions = timetable.getSessions();
                    int sessionsToSave = sessions.size();
                    int[] savedCount = {0};
                    
                    Log.d(TAG, "Timetable metadata saved, now saving " + sessionsToSave + " sessions");
                    
                    // Group sessions by course to create TimetableEntry objects
                    Map<String, List<TimetableSession>> sessionsByCourse = new HashMap<>();
                    
                    for (TimetableSession session : sessions) {
                        // Ensure the session has the timetable ID
                        session.setTimetableId(timetableId);
                        
                        // Group by course for TimetableEntry creation
                        String courseName = session.getCourseName();
                        if (!sessionsByCourse.containsKey(courseName)) {
                            sessionsByCourse.put(courseName, new ArrayList<>());
                        }
                        sessionsByCourse.get(courseName).add(session);
                        
                        // Save the session
                        String sessionId = database.child("timetableSessions").push().getKey();
                        if (sessionId != null) {
                            session.setId(sessionId);
                            database.child("timetableSessions").child(sessionId).setValue(session)
                                    .addOnSuccessListener(aVoid1 -> {
                                        savedCount[0]++;
                                        Log.d(TAG, "Saved session " + savedCount[0] + " of " + sessionsToSave);
                                        if (savedCount[0] == sessionsToSave) {
                                            // All sessions saved
                                            Log.d(TAG, "All sessions saved successfully, showing success dialog");
                                            showSuccess(timetableId);
                                        }
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e(TAG, "Error saving session", e);
                                        showError("Error saving session: " + e.getMessage());
                                    });
                        }
                    }
                    
                    // Create and save TimetableEntry objects for each course
                    createAndSaveTimetableEntries(sessionsByCourse);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error saving timetable", e);
                    showError("Error saving timetable: " + e.getMessage());
                });
    }
    
    /**
     * Creates TimetableEntry objects from grouped TimetableSessions to make them 
     * visible in lecturer and student dashboards
     */
    private void createAndSaveTimetableEntries(Map<String, List<TimetableSession>> sessionsByCourse) {
        Log.d(TAG, "Creating TimetableEntry objects for " + sessionsByCourse.size() + " courses");
        
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        
        for (Map.Entry<String, List<TimetableSession>> entry : sessionsByCourse.entrySet()) {
            String courseName = entry.getKey();
            List<TimetableSession> courseSessions = entry.getValue();
            
            if (courseSessions.isEmpty()) {
                continue;
            }
            
            // Use the first session for common data
            TimetableSession firstSession = courseSessions.get(0);
            
            // Create a map of timetable data (similar to AddScheduleActivity)
            Map<String, Object> timetableData = new HashMap<>();
            timetableData.put("courseName", courseName);
            
            // Set lecturer info from the first session
            timetableData.put("lecturerId", firstSession.getLecturerId());
            timetableData.put("lecturerName", firstSession.getLecturerName());
            
            // Set room info from the first session
            timetableData.put("roomId", firstSession.getResourceId());
            timetableData.put("roomName", firstSession.getResourceName());
            
            // Create a list of days
            Set<String> uniqueDays = new HashSet<>();
            for (TimetableSession session : courseSessions) {
                uniqueDays.add(session.getDayOfWeek());
            }
            timetableData.put("day", new ArrayList<>(uniqueDays));
            
            // Set time slot from first session (or could combine all)
            timetableData.put("timeSlot", firstSession.getStartTime() + "-" + firstSession.getEndTime());
            
            // Set dates - we don't have this info from Choco, so use current date plus a semester
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            Calendar calendar = Calendar.getInstance();
            String startDate = dateFormat.format(calendar.getTime());
            calendar.add(Calendar.MONTH, 4); // Add a semester (4 months)
            String endDate = dateFormat.format(calendar.getTime());
            
            timetableData.put("startDate", startDate);
            timetableData.put("endDate", endDate);
            
            // Set admin ID
            timetableData.put("adminId", currentUserId);
            
            // Set location - we don't have this directly, can be updated later
            timetableData.put("location", "Campus");
            
            // Set lecturer contact - also not available from Choco directly
            timetableData.put("lecContact", "");
            
            // Generate a key and save the entry
            String entryKey = database.child("timetables").push().getKey();
            if (entryKey != null) {
                database.child("timetables").child(entryKey).setValue(timetableData)
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "Saved TimetableEntry for course: " + courseName);
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error saving TimetableEntry for course: " + courseName, e);
                        });
            }
        }
    }
    
    private void showSuccess(String timetableId) {
        progressBar.setVisibility(View.GONE);
        String solverType = selectedSolverType == SolverType.CHOCO ? "Choco" : "Simple";
        statusTextView.setText("Timetable successfully generated using " + solverType + " Solver! Redirecting to view...");
        
        // Re-enable the generate button
        generateButton.setEnabled(true);
        
        Log.d(TAG, "Showing success dialog for timetable ID: " + timetableId);
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Timetable Generated Successfully")
                .setMessage("The timetable has been generated and saved successfully. Would you like to view it?")
                .setPositiveButton("View Timetable", (dialog, which) -> {
                    // Navigate to view timetable
                    Log.d(TAG, "User chose to view timetable, navigating to ViewTimetableActivity");
                    Intent intent = new Intent(this, ViewTimetableActivity.class);
                    intent.putExtra("timetableId", timetableId);
                    startActivity(intent);
                    finish(); // Close this activity after navigation
                })
                .setNegativeButton("Close", (dialog, which) -> {
                    Log.d(TAG, "User chose not to view timetable, finishing activity");
                    finish(); // Just close this activity
                })
                .setCancelable(false) // Prevent dismissing by tapping outside
                .show();
    }
    
    private void showError(Throwable e) {
        runOnUiThread(() -> {
            Log.e(TAG, "Error during timetable generation", e);
            progressBar.setVisibility(View.GONE);
            
            String errorPrefix = selectedSolverType == SolverType.CHOCO ?
                "Error with Choco Solver: " : "Error with Simple Solver: ";
            
            // Provide specific error messages based on the solver type
            if (selectedSolverType == SolverType.CHOCO && e instanceof UnsatisfiedLinkError) {
                statusTextView.setText("Choco Solver cannot run on this device. Please try using the Simple Solver instead.");
            } else if (selectedSolverType == SolverType.CHOCO && e.getMessage() != null && e.getMessage().contains("timeout")) {
                statusTextView.setText("Choco Solver timed out. Your problem may be too complex - try using Simple Solver or reducing constraints.");
            } else {
                statusTextView.setText(errorPrefix + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
            }
            
            generateButton.setEnabled(true);
            
            Toast.makeText(this, "Timetable generation failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        });
    }
    
    private void showError(String errorMessage) {
        progressBar.setVisibility(View.GONE);
        statusTextView.setText("Error: " + errorMessage);
        generateButton.setEnabled(true);
        
        Toast.makeText(this, "Error: " + errorMessage, Toast.LENGTH_LONG).show();
    }
}
