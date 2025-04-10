package com.example.manager.timetable;

import android.util.Log;

import com.example.manager.admin.model.Resource;
import com.example.manager.model.Lecturer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * A simple timetable generator that uses a greedy algorithm to allocate resources
 * and time slots. This does not rely on OR-Tools and avoids JNI issues.
 */
public class SimpleTimetableGenerator implements TimetableGenerator {
    private static final String TAG = "SimpleTimetableGen";
    private static final int DAYS_PER_WEEK = 5; // Monday to Friday
    private static final int HOURS_PER_DAY = 8; // 9 AM to 5 PM
    private static final int START_HOUR = 9; // Starting at 9 AM
    
    // Days of the week for output formatting
    private static final String[] DAYS_OF_WEEK = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday"};
    
    private Random random = new Random();
    // Default options
    private boolean avoidBackToBackClasses = false;
    private boolean preferEvenDistribution = false;
    private int maxHoursPerDay = 6;

    @Override
    public Timetable generateTimetable(List<Resource> resources, List<Lecturer> lecturers, List<Course> courses) {
        // Use default options
        return generateTimetable(resources, lecturers, courses, new TimetableGeneratorOptions());
    }

    @Override
    public Timetable generateTimetable(List<Resource> resources, List<Lecturer> lecturers, List<Course> courses,
                                      TimetableGeneratorOptions options) {
        Log.d(TAG, "Starting timetable generation with simple greedy algorithm");
        
        // Apply options
        this.avoidBackToBackClasses = options.shouldAvoidBackToBackClasses();
        this.preferEvenDistribution = options.shouldPreferEvenDistribution();
        this.maxHoursPerDay = options.getMaxHoursPerDay();
        
        Log.d(TAG, "Using options: avoidBackToBack=" + avoidBackToBackClasses + 
              ", preferEvenDistribution=" + preferEvenDistribution + 
              ", maxHoursPerDay=" + maxHoursPerDay);
        
        // Create a new timetable
        Timetable timetable = new Timetable();
        
        // Check for empty inputs
        if (resources.isEmpty() || lecturers.isEmpty() || courses.isEmpty()) {
            Log.e(TAG, "Cannot generate timetable with empty resources, lecturers, or courses");
            return timetable;
        }
        
        // Filter out unwanted rooms that shouldn't be used for scheduling
        List<String> unwantedRoomNames = new ArrayList<>();
        unwantedRoomNames.add("gy");
        unwantedRoomNames.add("a5");
        unwantedRoomNames.add("a6");
        unwantedRoomNames.add("ac4");
        
        List<Resource> filteredResources = new ArrayList<>();
        for (Resource resource : resources) {
            String resourceName = resource.getName().toLowerCase().trim();
            boolean isUnwanted = false;
            
            for (String unwantedName : unwantedRoomNames) {
                if (resourceName.equals(unwantedName.toLowerCase().trim())) {
                    isUnwanted = true;
                    Log.d(TAG, "Filtering out unwanted room: " + resource.getName());
                    break;
                }
            }
            
            if (!isUnwanted) {
                filteredResources.add(resource);
            }
        }
        
        // If we filtered out all resources, return empty timetable
        if (filteredResources.isEmpty()) {
            Log.e(TAG, "All resources were filtered out as unwanted. Please add valid rooms.");
            return timetable;
        }
        
        // Use filtered resources for the rest of the generation process
        resources = filteredResources;
        
        // Resource availability: resource index -> day -> hour -> available?
        boolean[][][] resourceAvailability = new boolean[resources.size()][DAYS_PER_WEEK][HOURS_PER_DAY];
        // Lecturer availability: lecturer index -> day -> hour -> available?
        boolean[][][] lecturerAvailability = new boolean[lecturers.size()][DAYS_PER_WEEK][HOURS_PER_DAY];
        
        // Initialize all slots as available
        for (int r = 0; r < resources.size(); r++) {
            for (int d = 0; d < DAYS_PER_WEEK; d++) {
                for (int h = 0; h < HOURS_PER_DAY; h++) {
                    resourceAvailability[r][d][h] = true;
                }
            }
        }
        
        for (int l = 0; l < lecturers.size(); l++) {
            for (int d = 0; d < DAYS_PER_WEEK; d++) {
                for (int h = 0; h < HOURS_PER_DAY; h++) {
                    lecturerAvailability[l][d][h] = true;
                }
            }
        }
        
        // Pre-compute lecturer indices for quick lookup
        int teacher1Index = -1;
        
        // Find the index of Teacher 1 (for VR courses)
        for (int i = 0; i < lecturers.size(); i++) {
            Lecturer lecturer = lecturers.get(i);
            Log.d(TAG, "Checking lecturer #" + i + ": '" + lecturer.getName() + "'");
            
            // Try exact match first for "teacher1" (without space)
            if (lecturer.getName().equals("teacher1")) {
                teacher1Index = i;
                Log.d(TAG, "FOUND EXACT MATCH for teacher1: '" + lecturer.getName() + "' at index " + i);
                break;
            }
        }
        
        // If no exact match, try case-insensitive
        if (teacher1Index == -1) {
            Log.d(TAG, "No exact match for 'teacher1', trying case-insensitive search");
            for (int i = 0; i < lecturers.size(); i++) {
                Lecturer lecturer = lecturers.get(i);
                if (lecturer.getName().toLowerCase().equals("teacher1")) {
                    teacher1Index = i;
                    Log.d(TAG, "FOUND CASE-INSENSITIVE MATCH for teacher1: '" + lecturer.getName() + "' at index " + i);
                    break;
                }
            }
        }
        
        if (teacher1Index == -1) {
            Log.e(TAG, "WARNING: TEACHER 1 NOT FOUND in the lecturer list! VR courses will not be properly assigned.");
        } else {
            Log.d(TAG, "Teacher 1 found at index " + teacher1Index + ": " + lecturers.get(teacher1Index).getName());
        }
        
        // Pre-compute lab room indices for quick lookup
        List<Integer> labRoomIndices = new ArrayList<>();
        for (int i = 0; i < resources.size(); i++) {
            Resource resource = resources.get(i);
            if (resource.getName().contains("Lab") || 
                resource.getName().contains("Polly Vacher")) {
                labRoomIndices.add(i);
            }
        }
        
        // Process each course
        for (Course course : courses) {
            String courseName = course.getName();
            String courseCode = course.getCode() != null ? course.getCode() : "";
            boolean isVRCourse = courseName.contains("Virtual Reality") || courseCode.contains("VR");
            
            // Determine how many sessions are needed
            int sessionsNeeded = course.getRequiredSessionsPerWeek();
            if (sessionsNeeded <= 0) {
                sessionsNeeded = 1; // Default to at least one session
            }
            
            // Find suitable resources for this course
            List<Integer> suitableResources;
            
            if (isVRCourse && !labRoomIndices.isEmpty()) {
                // VR courses need lab rooms
                suitableResources = new ArrayList<>(labRoomIndices);
                Log.d(TAG, "VR course will use lab rooms (" + suitableResources.size() + " available)");
            } else {
                // Other courses can use any room
                suitableResources = new ArrayList<>();
                for (int r = 0; r < resources.size(); r++) {
                    suitableResources.add(r);
                }
            }
            
            // Find suitable lecturers for this course
            List<Integer> suitableLecturers = new ArrayList<>();
            
            // Check if there's a pre-assigned lecturer from course management
            String assignedLecturerId = course.getAssignedLecturerId();
            
            if (assignedLecturerId != null && !assignedLecturerId.isEmpty()) {
                // Try to find the assigned lecturer by ID
                int assignedLecturerIndex = -1;
                for (int l = 0; l < lecturers.size(); l++) {
                    if (assignedLecturerId.equals(lecturers.get(l).getId())) {
                        assignedLecturerIndex = l;
                        break;
                    }
                }
                
                if (assignedLecturerIndex >= 0) {
                    // Use only the assigned lecturer
                    suitableLecturers.add(assignedLecturerIndex);
                    Log.d(TAG, "Using pre-assigned lecturer '" + lecturers.get(assignedLecturerIndex).getName() + 
                          "' for course: " + courseName);
                } else {
                    // Assigned lecturer not found - treat as unassigned
                    Log.w(TAG, "Assigned lecturer ID " + assignedLecturerId + " not found for course: " + 
                          courseName + " - will use any suitable lecturer");
                    
                    // For VR courses, prefer teacher1 if available
                    if (isVRCourse && teacher1Index >= 0) {
                        suitableLecturers.add(teacher1Index);
                        Log.d(TAG, "VR course without valid assignment will use teacher1");
                    } else {
                        // For non-VR courses or if teacher1 not available, use any lecturer
                        for (int l = 0; l < lecturers.size(); l++) {
                            suitableLecturers.add(l);
                        }
                    }
                }
            } else {
                // No pre-assigned lecturer - use default logic
                // For VR courses, prefer teacher1
                if (isVRCourse && teacher1Index >= 0) {
                    suitableLecturers.add(teacher1Index);
                    Log.d(TAG, "VR course without assignment will use teacher1");
                } else {
                    // For non-VR courses, use any lecturer
                    for (int l = 0; l < lecturers.size(); l++) {
                        suitableLecturers.add(l);
                    }
                }
            }
            
            // Skip if no suitable resources or lecturers
            if (suitableResources.isEmpty() || suitableLecturers.isEmpty()) {
                Log.w(TAG, "Cannot schedule " + courseName + " - no suitable resources or lecturers");
                continue;
            }
            
            // Try to schedule each session
            int sessionsScheduled = 0;
            
            // For each session needed for this course
            for (int session = 0; session < sessionsNeeded; session++) {
                // Choose best resource and lecturer
                Collections.shuffle(suitableResources, random);
                Collections.shuffle(suitableLecturers, random);
                
                int resourceIndex = suitableResources.get(0);
                int lecturerIndex = suitableLecturers.get(0);
                
                // For even distribution, randomize the order of days
                List<Integer> dayOrder = new ArrayList<>();
                for (int d = 0; d < DAYS_PER_WEEK; d++) {
                    dayOrder.add(d);
                }
                
                // If prefer even distribution, shuffle the days to avoid clustering
                if (preferEvenDistribution) {
                    Collections.shuffle(dayOrder, random);
                    Log.d(TAG, "Applying even distribution constraint - randomizing day order");
                }
                
                // Try to find an available slot for this course session
                boolean sessionAllocated = false;
                
                // Calculate lecturer's teaching hours per day
                int[] lecturerHoursPerDay = new int[DAYS_PER_WEEK];
                for (int d = 0; d < DAYS_PER_WEEK; d++) {
                    lecturerHoursPerDay[d] = 0;
                    for (int h = 0; h < HOURS_PER_DAY; h++) {
                        if (!lecturerAvailability[lecturerIndex][d][h]) {
                            // Count hours already allocated to this lecturer
                            lecturerHoursPerDay[d]++;
                        }
                    }
                }
                
                // Try each day in the (potentially shuffled) order
                for (int dayIndex = 0; !sessionAllocated && dayIndex < DAYS_PER_WEEK; dayIndex++) {
                    int d = dayOrder.get(dayIndex);
                    
                    // If respecting max hours constraint, skip days where lecturer already has maximum hours
                    if (lecturerHoursPerDay[d] >= maxHoursPerDay) {
                        Log.d(TAG, "Skipping day " + DAYS_OF_WEEK[d] + " - lecturer already has " + 
                              lecturerHoursPerDay[d] + " hours (max: " + maxHoursPerDay + ")");
                        continue;
                    }
                    
                    // For avoiding back-to-back, sort hours to prefer those that don't create back-to-back
                    List<Integer> hourOrder = new ArrayList<>();
                    for (int h = 0; h < HOURS_PER_DAY; h++) {
                        hourOrder.add(h);
                    }
                    
                    if (avoidBackToBackClasses) {
                        Log.d(TAG, "Applying back-to-back avoidance constraint");
                        Collections.sort(hourOrder, (hour1, hour2) -> {
                            int backToBack1 = countBackToBackHours(lecturerAvailability[lecturerIndex], d, hour1, 1);
                            int backToBack2 = countBackToBackHours(lecturerAvailability[lecturerIndex], d, hour2, 1);
                            return Integer.compare(backToBack1, backToBack2); // Prefer fewer back-to-back hours
                        });
                    } else {
                        // Otherwise, just shuffle for variety
                        Collections.shuffle(hourOrder, random);
                    }
                    
                    // Try each hour in the preferred order
                    for (int hourIndex = 0; !sessionAllocated && hourIndex < hourOrder.size(); hourIndex++) {
                        int h = hourOrder.get(hourIndex);
                        
                        // Check if slot is available
                        if (h < HOURS_PER_DAY && 
                            resourceAvailability[resourceIndex][d][h] && 
                            lecturerAvailability[lecturerIndex][d][h]) {
                            
                            // Create a unique ID for this session
                            String sessionId = UUID.randomUUID().toString();
                            
                            // Create session
                            TimetableSession timetableSession = new TimetableSession();
                            timetableSession.setId(sessionId);
                            timetableSession.setCourseId(course.getId());
                            timetableSession.setCourseName(course.getName());
                            timetableSession.setLecturerId(lecturers.get(lecturerIndex).getId());
                            timetableSession.setLecturerName(lecturers.get(lecturerIndex).getName());
                            timetableSession.setResourceId(resources.get(resourceIndex).getId());
                            timetableSession.setResourceName(resources.get(resourceIndex).getName());
                            timetableSession.setDayOfWeek(DAYS_OF_WEEK[d]);
                            timetableSession.setStartTime((START_HOUR + h) + ":00");
                            timetableSession.setEndTime((START_HOUR + h + 1) + ":00");
                            timetableSession.setSessionType(course.getCode());
                            
                            // Mark as unavailable
                            resourceAvailability[resourceIndex][d][h] = false;
                            lecturerAvailability[lecturerIndex][d][h] = false;
                            
                            // Add to timetable
                            timetable.addSession(timetableSession);
                            
                            // Log
                            Log.d(TAG, "Scheduled " + course.getName() + 
                                   " on " + DAYS_OF_WEEK[d] + 
                                   " at " + (START_HOUR + h) + ":00" +
                                   " with " + lecturers.get(lecturerIndex).getName() +
                                   " in " + resources.get(resourceIndex).getName() +
                                   " (constraints: back-to-back=" + avoidBackToBackClasses + 
                                   ", even-distribution=" + preferEvenDistribution + ")");
                            
                            sessionAllocated = true;
                            sessionsScheduled++;
                        }
                    }
                }
                
                if (!sessionAllocated) {
                    Log.w(TAG, "Could not schedule session " + (session + 1) + " for " + courseName);
                }
            }
            
            Log.d(TAG, "Scheduled " + sessionsScheduled + "/" + sessionsNeeded + " sessions for " + courseName);
        }
        
        Log.d(TAG, "Timetable generation completed with " + timetable.getSessions().size() + " sessions");
        
        return timetable;
    }
    
    /**
     * Find an available resource from the list of suitable resources for a given time slot.
     */
    private Integer findAvailableResource(boolean[][][] resourceAvailability, List<Integer> suitableResources, 
                                         int day, int hour) {
        // Randomize the order to avoid always picking the same resource
        List<Integer> shuffledResources = new ArrayList<>(suitableResources);
        Collections.shuffle(shuffledResources, random);
        
        for (Integer resourceIndex : shuffledResources) {
            if (resourceAvailability[resourceIndex][day][hour]) {
                return resourceIndex;
            }
        }
        return null; // No available resource found
    }
    
    /**
     * Find an available lecturer from the list of suitable lecturers for a given time slot.
     */
    private Integer findAvailableLecturer(boolean[][][] lecturerAvailability, List<Integer> suitableLecturers, 
                                         int day, int hour) {
        // Randomize the order to avoid always picking the same lecturer
        List<Integer> shuffledLecturers = new ArrayList<>(suitableLecturers);
        Collections.shuffle(shuffledLecturers, random);
        
        for (Integer lecturerIndex : shuffledLecturers) {
            if (lecturerAvailability[lecturerIndex][day][hour]) {
                return lecturerIndex;
            }
        }
        return null; // No available lecturer found
    }

    @Override
    public boolean hasConflicts(Timetable timetable) {
        // Check for conflicts in the timetable
        List<TimetableSession> sessions = timetable.getSessions();
        
        // Map to track resource usage: day -> hour -> resourceId -> session
        Map<String, Map<String, Map<String, TimetableSession>>> resourceUsage = new HashMap<>();
        
        // Map to track lecturer assignments: day -> hour -> lecturerId -> session
        Map<String, Map<String, Map<String, TimetableSession>>> lecturerAssignments = new HashMap<>();
        
        for (TimetableSession session : sessions) {
            String day = session.getDayOfWeek();
            String startTime = session.getStartTime();
            String resourceId = session.getResourceId();
            String lecturerId = session.getLecturerId();
            
            // Initialize maps if needed
            if (!resourceUsage.containsKey(day)) {
                resourceUsage.put(day, new HashMap<>());
            }
            if (!resourceUsage.get(day).containsKey(startTime)) {
                resourceUsage.get(day).put(startTime, new HashMap<>());
            }
            
            if (!lecturerAssignments.containsKey(day)) {
                lecturerAssignments.put(day, new HashMap<>());
            }
            if (!lecturerAssignments.get(day).containsKey(startTime)) {
                lecturerAssignments.get(day).put(startTime, new HashMap<>());
            }
            
            // Check for resource conflicts
            if (resourceUsage.get(day).get(startTime).containsKey(resourceId)) {
                // Resource already used at this time
                return true;
            }
            
            // Check for lecturer conflicts
            if (lecturerAssignments.get(day).get(startTime).containsKey(lecturerId)) {
                // Lecturer already assigned at this time
                return true;
            }
            
            // Record usage
            resourceUsage.get(day).get(startTime).put(resourceId, session);
            lecturerAssignments.get(day).get(startTime).put(lecturerId, session);
        }
        
        return false; // No conflicts found
    }

    /**
     * Counts how many back-to-back hours would be created if a session is scheduled
     * at the given day and starting hour.
     * 
     * @param lecturerAvail The lecturer's availability array
     * @param day The day to check
     * @param startHour The starting hour
     * @param duration The session duration in hours
     * @return The number of back-to-back hours created
     */
    private int countBackToBackHours(boolean[][] lecturerAvail, int day, int startHour, int duration) {
        int count = 0;
        
        // Check hour before session
        if (startHour > 0 && !lecturerAvail[day][startHour - 1]) {
            count++;
        }
        
        // Check hour after session
        if (startHour + duration < HOURS_PER_DAY && !lecturerAvail[day][startHour + duration]) {
            count++;
        }
        
        return count;
    }
}
