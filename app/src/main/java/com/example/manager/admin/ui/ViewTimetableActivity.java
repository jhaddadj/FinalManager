package com.example.manager.admin.ui;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.manager.R;
import com.example.manager.timetable.TimetableSession;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Activity for viewing a generated timetable
 */
public class ViewTimetableActivity extends AppCompatActivity {
    private static final String TAG = "ViewTimetableActivity";
    
    private LinearLayout timetableContentLayout;
    private TextView emptyView;
    
    private String timetableId;
    private List<TimetableSession> sessions = new ArrayList<>();
    
    // Timetable grid dimensions
    private static final int START_HOUR = 8; // 8 AM
    private static final int END_HOUR = 18;  // 6 PM
    private static final int DAYS_PER_WEEK = 5; // Monday to Friday
    private static final String[] DAY_NAMES = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday"};
    
    // Cell dimensions
    private static final int TIME_CELL_WIDTH_DP = 80;
    private static final int DAY_CELL_WIDTH_DP = 130;
    private static final int CELL_HEIGHT_DP = 60;
    private static final int TIME_CELL_HEIGHT_DP = 60;
    
    // Maximum sessions to display in a single cell
    private static final int MAX_SESSIONS_PER_CELL = 3;
    
    // Session colors for different courses/subjects
    private Map<String, Integer> courseColors = new HashMap<>();
    private Random random = new Random();
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_view_timetable);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        
        // Get timetable ID from intent
        timetableId = getIntent().getStringExtra("timetableId");
        if (timetableId == null) {
            Toast.makeText(this, "Error: No timetable ID provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // Initialize views
        timetableContentLayout = findViewById(R.id.timetableContentLayout);
        emptyView = findViewById(R.id.emptyView);
        
        // Load timetable data
        loadTimetableSessions();
        
        // Set up back button
        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        
        // Log for debugging view structure
        View mainLayout = findViewById(R.id.main);
        if (mainLayout == null) {
            Log.e(TAG, "timetableMainLayout not found in layout XML");
        }
    }
    
    private void loadTimetableSessions() {
        DatabaseReference database = FirebaseDatabase.getInstance().getReference();
        database.child("timetableSessions")
                .orderByChild("timetableId")
                .equalTo(timetableId)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        sessions.clear();
                        for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                            TimetableSession session = snapshot.getValue(TimetableSession.class);
                            if (session != null) {
                                sessions.add(session);
                            }
                        }
                        
                        // Add detailed logging of loaded sessions
                        Log.d(TAG, "Loaded " + sessions.size() + " timetable sessions from Firebase");
                        for (int i = 0; i < sessions.size(); i++) {
                            TimetableSession session = sessions.get(i);
                            Log.d(TAG, "Session " + (i+1) + ": Day=" + session.getDayOfWeek() + 
                                  ", Time=" + session.getStartTime() + "-" + session.getEndTime() + 
                                  ", Course=" + session.getCourseName() + 
                                  ", CourseId=" + session.getCourseId());
                        }
                        
                        // Validate that all sessions have the correct data
                        validateSessions();
                        
                        if (sessions.isEmpty()) {
                            emptyView.setVisibility(View.VISIBLE);
                            findViewById(R.id.timetableScroll).setVisibility(View.GONE);
                        } else {
                            emptyView.setVisibility(View.GONE);
                            findViewById(R.id.timetableScroll).setVisibility(View.VISIBLE);
                            renderTimetableGrid();
                        }
                    }
                    
                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.e(TAG, "Error loading timetable sessions", databaseError.toException());
                        Toast.makeText(ViewTimetableActivity.this, 
                                "Failed to load timetable data", Toast.LENGTH_SHORT).show();
                    }
                });
    }
    
    private void validateSessions() {
        // Validate that sessions have proper day and time values
        int validCount = 0;
        int invalidCount = 0;
        
        for (TimetableSession session : sessions) {
            int day = parseDayOfWeek(session.getDayOfWeek());
            int startHour = parseHour(session.getStartTime());
            int endHour = parseHour(session.getEndTime());
            
            if (day >= 0 && day < DAYS_PER_WEEK && startHour >= 0 && endHour > startHour) {
                validCount++;
                // Check if session is outside our visible time range
                if (startHour < START_HOUR || endHour > END_HOUR) {
                    Log.w(TAG, "Session for " + session.getCourseName() + 
                          " (" + session.getStartTime() + "-" + session.getEndTime() + 
                          ") is outside visible time range (" + START_HOUR + "-" + END_HOUR + ")");
                }
            } else {
                invalidCount++;
                Log.e(TAG, "Invalid session data: " + session.getCourseName() + 
                      ", Day=" + session.getDayOfWeek() + " (" + day + ")" +
                      ", Time=" + session.getStartTime() + "-" + session.getEndTime() + 
                      " (" + startHour + "-" + endHour + ")");
            }
        }
        
        Log.d(TAG, "Session validation: " + validCount + " valid, " + invalidCount + " invalid");
    }
    
    private void renderTimetableGrid() {
        // Clear the existing content first
        timetableContentLayout.removeAllViews();
        
        // Skip adding a header row since it already exists in the XML layout
        // The header row with days was defined twice - once in XML and once in code
        
        // Create time slots (rows)
        for (int hour = START_HOUR; hour < END_HOUR; hour++) {
            // Create a row for this time slot
            LinearLayout timeSlotRow = createTimeSlotRow(hour);
            timetableContentLayout.addView(timeSlotRow);
        }
        
        // Log summary of displayed sessions
        Map<String, Integer> displayedSessionsPerCourse = new HashMap<>();
        Map<String, Set<Integer>> sessionHoursPerCourse = new HashMap<>();
        
        // Count sessions by course and track which hours they appear in
        for (TimetableSession session : sessions) {
            String courseId = session.getCourseId();
            displayedSessionsPerCourse.put(courseId, displayedSessionsPerCourse.getOrDefault(courseId, 0) + 1);
            
            // Track which hours this session appears in
            int startHour = parseHour(session.getStartTime());
            int endHour = parseHour(session.getEndTime());
            
            // Create set for this course if it doesn't exist
            if (!sessionHoursPerCourse.containsKey(courseId)) {
                sessionHoursPerCourse.put(courseId, new HashSet<>());
            }
            
            // Add all hours this session spans
            for (int hour = startHour; hour < endHour; hour++) {
                sessionHoursPerCourse.get(courseId).add(hour);
            }
        }
        
        // Log how many unique time slots are used by each course
        Log.d(TAG, "Total sessions in timetable: " + sessions.size());
        for (Map.Entry<String, Integer> entry : displayedSessionsPerCourse.entrySet()) {
            String courseId = entry.getKey();
            int sessionCount = entry.getValue();
            int hourCount = sessionHoursPerCourse.getOrDefault(courseId, new HashSet<>()).size();
            
            Log.d(TAG, "Course " + courseId + " has " + sessionCount + " sessions spanning " + hourCount + " hours");
        }
        
        // Add a summary section below the timetable
        addCourseSummary(displayedSessionsPerCourse);
    }
    
    private void addCourseSummary(Map<String, Integer> sessionsPerCourse) {
        // Create a collapsible summary panel
        
        // Create the parent container for the collapsible panel
        LinearLayout summaryContainer = new LinearLayout(this);
        summaryContainer.setOrientation(LinearLayout.VERTICAL);
        summaryContainer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        
        // Create a handle/header for the expandable panel
        LinearLayout headerLayout = new LinearLayout(this);
        headerLayout.setOrientation(LinearLayout.HORIZONTAL);
        headerLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(30)));
        headerLayout.setBackgroundColor(Color.parseColor("#6200EE"));
        headerLayout.setGravity(Gravity.CENTER);
        
        // Add a visual indicator for dragging
        View dragHandle = new View(this);
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(
                dpToPx(40),
                dpToPx(5));
        dragHandle.setLayoutParams(handleParams);
        dragHandle.setBackgroundColor(Color.WHITE);
        headerLayout.addView(dragHandle);
        
        // Add the header text
        TextView headerText = new TextView(this);
        headerText.setText("Drag to show course summary");
        headerText.setTextColor(Color.WHITE);
        headerText.setTextSize(12);
        headerText.setPadding(0, dpToPx(5), 0, 0);
        headerLayout.addView(headerText);
        
        // Add the header to the container
        summaryContainer.addView(headerLayout);
        
        // Create the content panel
        LinearLayout summaryLayout = new LinearLayout(this);
        summaryLayout.setOrientation(LinearLayout.VERTICAL);
        summaryLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        summaryLayout.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
        summaryLayout.setBackgroundColor(Color.parseColor("#F8F8F8"));
        summaryLayout.setVisibility(View.GONE); // Initially hidden
        
        // Add a title
        TextView titleView = new TextView(this);
        titleView.setText("Course Sessions Summary");
        titleView.setTextSize(16);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setPadding(0, 0, 0, dpToPx(8));
        summaryLayout.addView(titleView);
        
        // Get all course names from the sessions
        Map<String, String> courseNames = new HashMap<>();
        for (TimetableSession session : sessions) {
            courseNames.put(session.getCourseId(), session.getCourseName());
        }
        
        // Add a row for each course
        for (Map.Entry<String, Integer> entry : sessionsPerCourse.entrySet()) {
            String courseId = entry.getKey();
            int sessionCount = entry.getValue();
            String courseName = courseNames.getOrDefault(courseId, "Unknown");
            
            LinearLayout courseRow = new LinearLayout(this);
            courseRow.setOrientation(LinearLayout.HORIZONTAL);
            courseRow.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            courseRow.setPadding(0, dpToPx(4), 0, dpToPx(4));
            
            // Color box
            View colorBox = new View(this);
            LinearLayout.LayoutParams boxParams = new LinearLayout.LayoutParams(dpToPx(16), dpToPx(16));
            boxParams.setMargins(0, dpToPx(2), dpToPx(8), 0);
            colorBox.setLayoutParams(boxParams);
            colorBox.setBackgroundColor(getCourseColor(courseId));
            courseRow.addView(colorBox);
            
            // Course name
            TextView nameView = new TextView(this);
            nameView.setText(courseName);
            nameView.setLayoutParams(new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1.0f));
            nameView.setTextSize(14);
            courseRow.addView(nameView);
            
            // Session count
            TextView countView = new TextView(this);
            countView.setText(sessionCount + " sessions");
            countView.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            countView.setTextSize(14);
            courseRow.addView(countView);
            
            summaryLayout.addView(courseRow);
        }
        
        // Add summary content to the container
        summaryContainer.addView(summaryLayout);
        
        // Set up touch handling to expand/collapse panel when dragged
        headerLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Toggle visibility of the summary panel
                if (summaryLayout.getVisibility() == View.VISIBLE) {
                    summaryLayout.setVisibility(View.GONE);
                    headerText.setText("Drag to show course summary");
                } else {
                    summaryLayout.setVisibility(View.VISIBLE);
                    headerText.setText("Tap to hide course summary");
                }
            }
        });
        
        // Add the summary panel to the main layout at the bottom
        ConstraintLayout mainLayout = (ConstraintLayout) findViewById(R.id.main);
        if (mainLayout != null) {
            ConstraintLayout.LayoutParams params = new ConstraintLayout.LayoutParams(
                    ConstraintLayout.LayoutParams.MATCH_PARENT,
                    ConstraintLayout.LayoutParams.WRAP_CONTENT);
            
            params.bottomToTop = R.id.backButton;
            params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
            params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
            
            mainLayout.addView(summaryContainer, params);
            
            // Update back button constraints
            View backButton = findViewById(R.id.backButton);
            ConstraintLayout.LayoutParams backParams = 
                (ConstraintLayout.LayoutParams) backButton.getLayoutParams();
            backParams.topToBottom = summaryContainer.getId();
            backButton.setLayoutParams(backParams);
            
            Log.d(TAG, "Added collapsible course summary panel");
        } else {
            Log.e(TAG, "Could not find main layout to add summary panel");
        }
    }
    
    private LinearLayout createHeaderRow() {
        // Create a horizontal layout for the header row
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dpToPx(CELL_HEIGHT_DP)));
        
        // Add empty cell for the time column
        TextView emptyCell = new TextView(this);
        emptyCell.setLayoutParams(new LinearLayout.LayoutParams(
                dpToPx(TIME_CELL_WIDTH_DP),
                LinearLayout.LayoutParams.MATCH_PARENT));
        emptyCell.setBackgroundColor(Color.parseColor("#F0F0F0"));
        headerRow.addView(emptyCell);
        
        // Add day headers
        for (int day = 0; day < DAYS_PER_WEEK; day++) {
            TextView dayHeader = new TextView(this);
            dayHeader.setLayoutParams(new LinearLayout.LayoutParams(
                    dpToPx(DAY_CELL_WIDTH_DP),
                    LinearLayout.LayoutParams.MATCH_PARENT));
            dayHeader.setText(DAY_NAMES[day]);
            dayHeader.setGravity(Gravity.CENTER);
            dayHeader.setBackgroundColor(Color.parseColor("#D0D0D0"));
            dayHeader.setTextColor(Color.BLACK);
            dayHeader.setTextSize(16);
            dayHeader.setTypeface(null, android.graphics.Typeface.BOLD);
            headerRow.addView(dayHeader);
        }
        
        return headerRow;
    }
    
    private LinearLayout createTimeSlotRow(int hour) {
        // Create a horizontal layout for this time slot row
        LinearLayout timeSlotRow = new LinearLayout(this);
        timeSlotRow.setOrientation(LinearLayout.HORIZONTAL);
        timeSlotRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dpToPx(CELL_HEIGHT_DP)));
        
        // Add the time label (first column)
        TextView timeLabel = new TextView(this);
        timeLabel.setLayoutParams(new LinearLayout.LayoutParams(
                dpToPx(TIME_CELL_WIDTH_DP),
                LinearLayout.LayoutParams.MATCH_PARENT));
        timeLabel.setText(formatTimeSlot(hour));
        timeLabel.setGravity(Gravity.CENTER);
        timeLabel.setBackgroundColor(Color.parseColor("#F5F5F5"));
        timeLabel.setTextColor(Color.BLACK);
        timeSlotRow.addView(timeLabel);
        
        // Add cells for each day of the week
        for (int day = 0; day < DAYS_PER_WEEK; day++) {
            // Find all sessions for this day and time
            List<TimetableSession> sessionsForCell = findSessionsForDayAndTime(day, hour);
            
            // Create and add the cell view
            View cellView = createCellView(day, hour, sessionsForCell);
            timeSlotRow.addView(cellView);
        }
        
        return timeSlotRow;
    }
    
    private List<TimetableSession> findSessionsForDayAndTime(int day, int hour) {
        List<TimetableSession> matchingSessions = new ArrayList<>();
        
        for (TimetableSession session : sessions) {
            // Parse day of week
            int sessionDay = parseDayOfWeek(session.getDayOfWeek());
            if (sessionDay != day) continue; // Skip if not the right day
            
            // Parse start and end time
            int sessionStartHour = parseHour(session.getStartTime());
            int sessionEndHour = parseHour(session.getEndTime());
            
            // Check if the session covers this hour 
            // A session covers the hour if it starts at or before the hour and ends after the hour
            if (sessionStartHour <= hour && sessionEndHour > hour) {
                matchingSessions.add(session);
                
                // Add debug logging for session matching
                Log.d(TAG, "Session match for day " + day + " hour " + hour + ": " + 
                      session.getCourseName() + " (" + session.getStartTime() + "-" + 
                      session.getEndTime() + ")");
            }
        }
        
        if (matchingSessions.isEmpty()) {
            Log.d(TAG, "No sessions found for day " + day + " hour " + hour);
        } else {
            Log.d(TAG, "Found " + matchingSessions.size() + " sessions for day " + day + " hour " + hour);
        }
        
        return matchingSessions;
    }
    
    private View createCellView(int day, int hour, List<TimetableSession> sessionsForCell) {
        // If no sessions, create empty cell
        if (sessionsForCell.isEmpty()) {
            return createEmptyCell();
        }
        
        // If only one session, create simple cell
        if (sessionsForCell.size() == 1) {
            return createSingleSessionCell(sessionsForCell.get(0), hour);
        }
        
        // If multiple sessions, create a container with multiple session views
        return createMultiSessionCell(sessionsForCell, hour);
    }
    
    private View createEmptyCell() {
        TextView cellView = new TextView(this);
        cellView.setLayoutParams(new LinearLayout.LayoutParams(
                dpToPx(DAY_CELL_WIDTH_DP),
                LinearLayout.LayoutParams.MATCH_PARENT));
        cellView.setBackgroundResource(R.drawable.cell_border);
        cellView.setBackgroundColor(Color.WHITE);
        return cellView;
    }
    
    private View createSingleSessionCell(TimetableSession session, int hour) {
        TextView cellView = new TextView(this);
        cellView.setLayoutParams(new LinearLayout.LayoutParams(
                dpToPx(DAY_CELL_WIDTH_DP),
                LinearLayout.LayoutParams.MATCH_PARENT));
        cellView.setBackgroundResource(R.drawable.cell_border);
        
        // Check if this cell is the first hour of a multi-hour session
        int sessionStartHour = parseHour(session.getStartTime());
        
        // Only show content in the first cell of a multi-hour session
        if (sessionStartHour == hour) {
            // Generate or retrieve a color for this course
            String courseId = session.getCourseId();
            int backgroundColor = getCourseColor(courseId);
            
            // Set the text to display
            cellView.setText(session.getCourseName());
            cellView.setTextColor(Color.WHITE);
            cellView.setBackgroundColor(backgroundColor);
            cellView.setGravity(Gravity.CENTER);
            cellView.setPadding(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2));
        } else {
            // For continuation cells, just use the background color
            cellView.setBackgroundColor(getCourseColor(session.getCourseId()));
        }
        
        // Add click listener to show session details
        cellView.setOnClickListener(v -> showSessionDetails(session));
        
        return cellView;
    }
    
    private View createMultiSessionCell(List<TimetableSession> sessions, int hour) {
        LinearLayout containerLayout = new LinearLayout(this);
        containerLayout.setOrientation(LinearLayout.VERTICAL);
        containerLayout.setLayoutParams(new LinearLayout.LayoutParams(
                dpToPx(DAY_CELL_WIDTH_DP),
                LinearLayout.LayoutParams.MATCH_PARENT));
        containerLayout.setBackgroundResource(R.drawable.cell_border);
        
        // Determine the maximum number of sessions to display
        int maxSessionsToDisplay = Math.min(sessions.size(), 3); // Show up to 3 sessions
        int cellHeight = dpToPx(TIME_CELL_HEIGHT_DP);
        
        // Calculate height per session (minus padding)
        int heightPerSession = (cellHeight - dpToPx(4 * maxSessionsToDisplay)) / maxSessionsToDisplay;
        
        // Add views for each session up to the maximum number to display
        for (int i = 0; i < maxSessionsToDisplay; i++) {
            TimetableSession session = sessions.get(i);
            
            // Extract the info we want to display
            String courseId = session.getCourseId();
            String displayText = session.getCourseName();
            
            // Create the session TextView
            TextView sessionView = new TextView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    heightPerSession);
            params.setMargins(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2));
            sessionView.setLayoutParams(params);
            sessionView.setText(displayText);
            sessionView.setTextSize(10);
            sessionView.setTextColor(Color.WHITE);
            sessionView.setBackgroundColor(getCourseColor(courseId));
            sessionView.setGravity(Gravity.CENTER);
            sessionView.setPadding(dpToPx(2), dpToPx(1), dpToPx(2), dpToPx(1));
            sessionView.setMaxLines(2);
            sessionView.setEllipsize(TextUtils.TruncateAt.END);
            
            // Add click listener to show session details
            final TimetableSession finalSession = session;
            sessionView.setOnClickListener(v -> showSessionDetails(finalSession));
            
            containerLayout.addView(sessionView);
        }
        
        // If there are more sessions than we can display, add an indicator
        if (sessions.size() > maxSessionsToDisplay) {
            int remaining = sessions.size() - maxSessionsToDisplay;
            
            TextView moreIndicator = new TextView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.gravity = Gravity.END;
            params.setMargins(0, 0, dpToPx(4), dpToPx(2));
            moreIndicator.setLayoutParams(params);
            moreIndicator.setText("+" + remaining + " more");
            moreIndicator.setTextSize(8);
            moreIndicator.setTextColor(Color.RED);
            containerLayout.addView(moreIndicator);
            
            // Add click listener to show all sessions in this time slot
            moreIndicator.setOnClickListener(v -> showAllSessionsInTimeSlot(sessions));
        }
        
        return containerLayout;
    }
    
    private int getCourseColor(String courseId) {
        if (!courseColors.containsKey(courseId)) {
            // Generate a new color for this course
            int color = generateRandomColor();
            courseColors.put(courseId, color);
        }
        return courseColors.get(courseId);
    }
    
    private int generateRandomColor() {
        // Generate a dark but visible color (avoid too light or too dark)
        int red = random.nextInt(156) + 50;   // 50-205
        int green = random.nextInt(156) + 50; // 50-205
        int blue = random.nextInt(156) + 50;  // 50-205
        return Color.rgb(red, green, blue);
    }
    
    private String getCourseAbbreviation(String courseName) {
        if (courseName == null || courseName.isEmpty()) {
            return "???";
        }
        
        // Create abbreviation from first letters of words
        String[] words = courseName.split(" ");
        StringBuilder abbreviation = new StringBuilder();
        
        for (String word : words) {
            if (!word.isEmpty()) {
                abbreviation.append(word.charAt(0));
            }
        }
        
        return abbreviation.toString().toUpperCase();
    }
    
    private String formatTimeSlot(int hour) {
        // Format hour as "9:00 AM" or "2:00 PM"
        String amPm = hour >= 12 ? "PM" : "AM";
        int displayHour = hour > 12 ? hour - 12 : hour;
        return String.format("%d:00 %s", displayHour, amPm);
    }
    
    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
    
    private int parseDayOfWeek(String dayOfWeek) {
        if (dayOfWeek == null) return -1;
        
        // Map day string to index (0-4 for Monday-Friday)
        switch (dayOfWeek.toLowerCase()) {
            case "monday": return 0;
            case "tuesday": return 1;
            case "wednesday": return 2;
            case "thursday": return 3;
            case "friday": return 4;
            default: return -1;
        }
    }
    
    private int parseHour(String timeString) {
        if (timeString == null || timeString.isEmpty()) {
            return -1;
        }
        
        try {
            // Assuming time format like "09:00" or "14:30"
            String[] parts = timeString.split(":");
            int hour = Integer.parseInt(parts[0]);
            
            // Log hour parsing for debugging
            Log.d(TAG, "Parsing time '" + timeString + "' to hour: " + hour);
            
            return hour;
        } catch (Exception e) {
            Log.e(TAG, "Error parsing time: " + timeString, e);
            return -1;
        }
    }
    
    /**
     * Display a dialog with detailed information about a selected session
     */
    private void showSessionDetails(TimetableSession session) {
        // Create an AlertDialog to show session details
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Class Details");
        
        // Create the content view
        LinearLayout contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8));
        
        // Add a colored header for the course name
        TextView headerView = new TextView(this);
        headerView.setText(session.getCourseName());
        headerView.setTextSize(18);
        headerView.setTypeface(null, Typeface.BOLD);
        headerView.setTextColor(Color.WHITE);
        headerView.setBackgroundColor(getCourseColor(session.getCourseId()));
        headerView.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
        headerView.setGravity(Gravity.CENTER);
        contentLayout.addView(headerView);
        
        // Add course details
        addDetailRow(contentLayout, "Time", session.getStartTime() + " - " + session.getEndTime());
        addDetailRow(contentLayout, "Day", session.getDayOfWeek());
        addDetailRow(contentLayout, "Lecturer", session.getLecturerName());
        addDetailRow(contentLayout, "Location", session.getResourceName());
        
        // Add any additional details you want to display
        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(contentLayout);
        
        builder.setView(scrollView);
        builder.setPositiveButton("Close", (dialog, which) -> dialog.dismiss());
        
        AlertDialog dialog = builder.create();
        dialog.show();
        
        // Make sure the dialog doesn't get too tall (limit to 80% of screen height)
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
        layoutParams.copyFrom(dialog.getWindow().getAttributes());
        layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        layoutParams.height = (int) (getResources().getDisplayMetrics().heightPixels * 0.8);
        dialog.getWindow().setAttributes(layoutParams);
    }
    
    /**
     * Get a course code from the course ID
     * If the course ID appears to be a code already, use it directly
     * Otherwise, create a code based on the ID
     */
    private String getCourseCode(String courseId) {
        if (courseId == null || courseId.isEmpty()) {
            return "N/A";
        }
        
        // If the courseId is already in a code-like format (contains numbers), use it
        if (courseId.matches(".*\\d.*")) {
            return courseId.toUpperCase();
        }
        
        // Otherwise, generate a simple code based on the ID
        // Take first 3 letters and add a sequence number
        String prefix = courseId.length() > 3 ? courseId.substring(0, 3) : courseId;
        return prefix.toUpperCase() + "-" + Math.abs(courseId.hashCode() % 1000);
    }
    
    /**
     * Display a dialog showing all sessions in a particular time slot
     */
    private void showAllSessionsInTimeSlot(List<TimetableSession> sessions) {
        // Create an AlertDialog to show all sessions
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("All Classes in This Time Slot");
        
        // Create the content view
        LinearLayout contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8));
        
        // Create a reference to the dialog that will be created
        final AlertDialog[] dialogRef = new AlertDialog[1];
        
        // Add details for each session
        for (TimetableSession session : sessions) {
            // Add a colored header for the course name
            TextView headerView = new TextView(this);
            headerView.setText(session.getCourseName());
            headerView.setTextSize(16);
            headerView.setTypeface(null, Typeface.BOLD);
            headerView.setTextColor(Color.WHITE);
            headerView.setBackgroundColor(getCourseColor(session.getCourseId()));
            headerView.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
            headerView.setGravity(Gravity.CENTER);
            
            // Add some margin at the top if not the first item
            if (sessions.indexOf(session) > 0) {
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                params.topMargin = dpToPx(16);
                headerView.setLayoutParams(params);
            }
            
            contentLayout.addView(headerView);
            
            // Add basic details
            addDetailRow(contentLayout, "Lecturer", session.getLecturerName());
            addDetailRow(contentLayout, "Location", session.getResourceName());
            
            // Add a view details button for this session
            Button detailsButton = new Button(this);
            detailsButton.setText("View Full Details");
            detailsButton.setBackgroundColor(Color.parseColor("#6200EE"));
            detailsButton.setTextColor(Color.WHITE);
            LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            buttonParams.gravity = Gravity.END;
            buttonParams.setMargins(0, dpToPx(4), 0, dpToPx(8));
            detailsButton.setLayoutParams(buttonParams);
            
            final TimetableSession finalSession = session;
            detailsButton.setOnClickListener(v -> {
                // Dismiss this dialog and show details for the selected session
                if (dialogRef[0] != null) {
                    dialogRef[0].dismiss();
                }
                showSessionDetails(finalSession);
            });
            
            contentLayout.addView(detailsButton);
        }
        
        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(contentLayout);
        
        builder.setView(scrollView);
        builder.setPositiveButton("Close", (dialog, which) -> dialog.dismiss());
        
        AlertDialog dialog = builder.create();
        dialogRef[0] = dialog;  // Store reference to the dialog
        dialog.show();
        
        // Make sure the dialog doesn't get too tall (limit to 80% of screen height)
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
        layoutParams.copyFrom(dialog.getWindow().getAttributes());
        layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        layoutParams.height = (int) (getResources().getDisplayMetrics().heightPixels * 0.8);
        dialog.getWindow().setAttributes(layoutParams);
    }
    
    /**
     * Helper method to add a detail row to a layout
     */
    private void addDetailRow(LinearLayout parentLayout, String label, String value) {
        if (value == null || value.isEmpty()) {
            value = "Not specified";
        }
        
        // Create a horizontal layout for this detail row
        LinearLayout rowLayout = new LinearLayout(this);
        rowLayout.setOrientation(LinearLayout.HORIZONTAL);
        rowLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        rowLayout.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
        
        // Add the label
        TextView labelView = new TextView(this);
        labelView.setText(label + ":");
        labelView.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                0.4f));
        labelView.setTextSize(14);
        labelView.setTypeface(null, Typeface.BOLD);
        labelView.setTextColor(Color.parseColor("#333333"));
        rowLayout.addView(labelView);
        
        // Add the value
        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                0.6f));
        valueView.setTextSize(14);
        valueView.setTextColor(Color.parseColor("#666666"));
        rowLayout.addView(valueView);
        
        // Add a separator line
        View separator = new View(this);
        separator.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                1));
        separator.setBackgroundColor(Color.parseColor("#DDDDDD"));
        
        // Add both views to the parent layout
        parentLayout.addView(rowLayout);
        parentLayout.addView(separator);
    }
}
