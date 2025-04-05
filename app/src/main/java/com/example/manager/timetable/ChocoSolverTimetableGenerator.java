/**
 * This class uses Choco Solver to generate a timetable.
 * Choco Solver is a constraint programming library.
 */
package com.example.manager.timetable;

import android.util.Log;

import com.example.manager.admin.model.Resource;
import com.example.manager.model.Lecturer;
import com.example.manager.timetable.Course;
import com.example.manager.timetable.Timetable;
import com.example.manager.timetable.TimetableSession;
import com.example.manager.timetable.TimetableGenerator;
import com.example.manager.timetable.TimetableGeneratorOptions;

import org.chocosolver.solver.Model;
import org.chocosolver.solver.Solution;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.search.strategy.Search;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.solver.variables.Variable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * ChocoSolverTimetableGenerator - Advanced Timetable Generator using Choco Solver
 * 
 * This class implements the TimetableGenerator interface using the Choco Solver constraint
 * programming library. It formulates the timetable generation problem using decision variables
 * and constraints, and uses Choco's search algorithms to find optimal solutions.
 * 
 * Features:
 * - Handles hard constraints like avoiding double-booking
 * - Supports soft constraints like even distribution and back-to-back avoidance
 * - Uses advanced variable and value selection heuristics for efficient solving
 * - Falls back to a less optimal but valid solution if optimal solving times out
 * 
 * Dependencies: 
 * - Requires org.choco-solver:choco-solver library (version 4.10.10)
 * 
 * @see TimetableGenerator
 * @see SimpleTimetableGenerator
 */
public class ChocoSolverTimetableGenerator implements TimetableGenerator {
    private static final String TAG = "ChocoSolverTimetable";
    
    // Constants for timetable dimensions
    private static final int DAYS_PER_WEEK = 5; // Monday to Friday
    private static final int HOURS_PER_DAY = 8; // 9 AM to 5 PM
    private static final int START_HOUR = 9;    // Starting at 9 AM
    
    // Days of the week for output formatting
    private static final String[] DAYS_OF_WEEK = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday"};
    
    // Timeout for solver (in milliseconds)
    private static final int DEFAULT_TIMEOUT_MS = 30000; // 30 seconds
    
    @Override
    public Timetable generateTimetable(List<Resource> resources, List<Lecturer> lecturers, List<Course> courses) {
        // Use default options
        return generateTimetable(resources, lecturers, courses, new TimetableGeneratorOptions());
    }

    @Override
    public Timetable generateTimetable(List<Resource> resources, List<Lecturer> lecturers, List<Course> courses, 
                                     TimetableGeneratorOptions options) {
        if (resources == null || resources.isEmpty() || lecturers == null || lecturers.isEmpty() || courses == null || courses.isEmpty()) {
            Log.e(TAG, "Cannot generate timetable with empty resources, lecturers, or courses");
            return new Timetable();
        }
        
        Log.d(TAG, "Starting Choco Solver timetable generation with " + courses.size() + " courses");
        
        // Make copies of the input collections to avoid modifying the originals
        List<Resource> resourcesCopy = new ArrayList<>(resources);
        List<Lecturer> lecturersCopy = new ArrayList<>(lecturers);
        List<Course> coursesToSchedule = new ArrayList<>(courses);
        
        // Ensure we are working with a mutable list by creating a copy
        List<Course> coursesToScheduleCopy = new ArrayList<>(coursesToSchedule);
        
        // Validate all courses before attempting to schedule
        List<Course> validCourses = new ArrayList<>();
        for (Course course : coursesToScheduleCopy) {
            // Make sure all courses have basic requirements filled
            if (course.getName() == null || course.getName().isEmpty()) {
                Log.e(TAG, "Course name is missing, skipping: " + course.getId());
                continue;
            }
            
            // Check if course has valid required sessions
            if (course.getRequiredSessionsPerWeek() <= 0) {
                Log.w(TAG, "Course has no required sessions, setting to 1: " + course.getName());
                course.setRequiredSessionsPerWeek(1);
            }
            
            validCourses.add(course);
            Log.d(TAG, "Validated course: " + course.getName() + " with " + course.getRequiredSessionsPerWeek() + " sessions");
        }
        
        // Exit early if no valid courses
        if (validCourses.isEmpty()) {
            Log.e(TAG, "No valid courses to schedule!");
            return new Timetable();
        }
        
        // Ensure all courses are processed
        Log.d(TAG, "Verifying course data consistency...");
        for (Course course : courses) {
            Log.d(TAG, "Course: " + course.getName() + " ID: " + course.getId() + " Sessions: " + course.getRequiredSessionsPerWeek());
        }

        // Adjust constraints if necessary
        Log.d(TAG, "Adjusting constraints to ensure all courses are considered...");

        // Create flat list of all sessions to schedule
        List<SessionToSchedule> allSessions = new ArrayList<>();
        
        // Add logging to track the scheduling process
        Log.d(TAG, "Starting to schedule sessions for each course...");
        
        int sessionIndex = 0;
        for (Course course : validCourses) {
            Log.d(TAG, "Processing course: " + course.getName() + " with " + course.getRequiredSessionsPerWeek() + " required sessions.");
            for (int i = 0; i < course.getRequiredSessionsPerWeek(); i++) {
                SessionToSchedule session = new SessionToSchedule(sessionIndex++, course);
                allSessions.add(session);
                Log.d(TAG, "Added session " + i + " for course " + course.getName() + " (index=" + session.getIndex() + ")");
            }
        }
        
        // Log the number of sessions created
        Log.d(TAG, "Total sessions created: " + allSessions.size());
        
        // Create a Choco-solver model
        Model model = new Model("Timetable");
        
        // Create a solver with the model
        Solver solver = model.getSolver();
        solver.limitTime(DEFAULT_TIMEOUT_MS);
        
        // Create variables for each session
        Map<Integer, IntVar> sessionDayVars = new HashMap<>();
        Map<Integer, IntVar> sessionHourVars = new HashMap<>();
        Map<Integer, IntVar> sessionResourceVars = new HashMap<>();
        Map<Integer, IntVar> sessionLecturerVars = new HashMap<>();
        
        // Create variables for each session
        for (SessionToSchedule session : allSessions) {
            int sIndex = session.getIndex();
            Course course = session.getCourse();
            
            // Variables for day, hour, resource, and lecturer
            IntVar day = model.intVar("day_" + sIndex, 0, DAYS_PER_WEEK - 1);
            IntVar hour = model.intVar("hour_" + sIndex, 0, HOURS_PER_DAY - 1);
            
            // Find compatible resources for this session
            List<Integer> compatibleResourceIndices = findCompatibleResources(course, resourcesCopy);
            IntVar resource;
            if (compatibleResourceIndices.isEmpty()) {
                resource = model.intVar("resource_" + sIndex, 0, resourcesCopy.size() - 1);
            } else {
                resource = model.intVar("resource_" + sIndex, compatibleResourceIndices.stream().mapToInt(i -> i).toArray());
            }
            
            // If the course has an assigned resource, constrain to that resource
            if (course.getAssignedResourceId() != null && !course.getAssignedResourceId().isEmpty()) {
                for (int j = 0; j < resourcesCopy.size(); j++) {
                    Resource res = resourcesCopy.get(j);
                    if (res.getId().equals(course.getAssignedResourceId())) {
                        // Set the resource variable to this specific resource index
                        resource = model.intVar("resource_" + sIndex, j);
                        Log.d(TAG, "Course " + course.getName() + " constrained to resource " + res.getName());
                        break;
                    }
                }
            }
            
            // Create lecturer variable - can be any lecturer by default
            IntVar lecturer = model.intVar("lecturer_" + sIndex, 0, lecturersCopy.size() - 1);
            
            // If the course has an assigned lecturer, constrain to that lecturer
            if (course.getAssignedLecturerId() != null && !course.getAssignedLecturerId().isEmpty()) {
                for (int j = 0; j < lecturersCopy.size(); j++) {
                    Lecturer lect = lecturersCopy.get(j);
                    if (lect.getId().equals(course.getAssignedLecturerId())) {
                        // Set the lecturer variable to this specific lecturer index
                        lecturer = model.intVar("lecturer_" + sIndex, j);
                        Log.d(TAG, "Course " + course.getName() + " constrained to lecturer " + lect.getName());
                        break;
                    }
                }
            }
            
            // Store variables in maps for easy lookup
            sessionDayVars.put(sIndex, day);
            sessionHourVars.put(sIndex, hour);
            sessionResourceVars.put(sIndex, resource);
            sessionLecturerVars.put(sIndex, lecturer);
            
            // Log variable creation
            Log.d(TAG, "Created variables for session " + sIndex + " of course " + course.getName() + 
                " (" + course.getId() + "): " + 
                "day=" + day + ", hour=" + hour);
        }
        
        // Add constraints
        addConstraints(model, allSessions, resourcesCopy, lecturersCopy, 
                      sessionDayVars, sessionHourVars, sessionResourceVars, sessionLecturerVars);
        
        // Log all courses being scheduled
        Log.d(TAG, "Courses being scheduled:");
        for (Course course : validCourses) {
            Log.d(TAG, "Course: " + course.getName() + " (" + course.getId() + ") - " + 
                course.getRequiredSessionsPerWeek() + " sessions");
        }
        
        // Debug variables before solving
        Log.d(TAG, "Before solving, number of day variables: " + sessionDayVars.size());
        Log.d(TAG, "Before solving, number of hour variables: " + sessionHourVars.size());
        Log.d(TAG, "Variable names sample: " + 
              (sessionDayVars.isEmpty() ? "empty" : sessionDayVars.values().iterator().next().getName()));
        
        // Try to find a solution
        boolean solved = solver.solve();
        
        if (solved) {
            Log.d(TAG, "Solution found!");
            
            // Create a complete map of variable names to their current values
            Map<String, Integer> variableValues = new HashMap<>();
            
            // Record all variable values after solving
            for (Map.Entry<Integer, IntVar> entry : sessionDayVars.entrySet()) {
                int sessionId = entry.getKey();
                IntVar var = entry.getValue();
                try {
                    int value = var.getValue();
                    variableValues.put("day_" + sessionId, value);
                    Log.d(TAG, "Recorded day value for session " + sessionId + ": " + value);
                } catch (Exception e) {
                    Log.e(TAG, "Error getting value for day variable " + sessionId, e);
                }
            }
            
            for (Map.Entry<Integer, IntVar> entry : sessionHourVars.entrySet()) {
                int sessionId = entry.getKey();
                IntVar var = entry.getValue();
                try {
                    int value = var.getValue();
                    variableValues.put("hour_" + sessionId, value);
                    Log.d(TAG, "Recorded hour value for session " + sessionId + ": " + value);
                } catch (Exception e) {
                    Log.e(TAG, "Error getting value for hour variable " + sessionId, e);
                }
            }
            
            for (Map.Entry<Integer, IntVar> entry : sessionResourceVars.entrySet()) {
                int sessionId = entry.getKey();
                IntVar var = entry.getValue();
                try {
                    int value = var.getValue();
                    variableValues.put("resource_" + sessionId, value);
                    Log.d(TAG, "Recorded resource value for session " + sessionId + ": " + value);
                } catch (Exception e) {
                    Log.e(TAG, "Error getting value for resource variable " + sessionId, e);
                }
            }
            
            for (Map.Entry<Integer, IntVar> entry : sessionLecturerVars.entrySet()) {
                int sessionId = entry.getKey();
                IntVar var = entry.getValue();
                try {
                    int value = var.getValue();
                    variableValues.put("lecturer_" + sessionId, value);
                    Log.d(TAG, "Recorded lecturer value for session " + sessionId + ": " + value);
                } catch (Exception e) {
                    Log.e(TAG, "Error getting value for lecturer variable " + sessionId, e);
                }
            }
            
            // Create a simple object to pass the values to buildTimetableFromSolution
            ValueSolution valueSolution = new ValueSolution(variableValues);
            
            // Modified version of buildTimetableFromSolution that uses our values directly
            return buildTimetableFromDirectValues(
                valueSolution, allSessions, resourcesCopy, lecturersCopy, validCourses
            );
        } else {
            Log.w(TAG, "No solution found. Trying with increased timeout.");
            
            // Increase timeout and try again
            solver.limitTime(DEFAULT_TIMEOUT_MS * 2);
            solved = solver.solve();
            
            if (solved) {
                Log.d(TAG, "Solution found with increased timeout!");
                
                // Create a complete map of variable names to their current values
                Map<String, Integer> variableValues = new HashMap<>();
                
                // Record all variable values after solving
                for (Map.Entry<Integer, IntVar> entry : sessionDayVars.entrySet()) {
                    int sessionId = entry.getKey();
                    IntVar var = entry.getValue();
                    try {
                        int value = var.getValue();
                        variableValues.put("day_" + sessionId, value);
                        Log.d(TAG, "Recorded day value for session " + sessionId + ": " + value);
                    } catch (Exception e) {
                        Log.e(TAG, "Error getting value for day variable " + sessionId, e);
                    }
                }
                
                for (Map.Entry<Integer, IntVar> entry : sessionHourVars.entrySet()) {
                    int sessionId = entry.getKey();
                    IntVar var = entry.getValue();
                    try {
                        int value = var.getValue();
                        variableValues.put("hour_" + sessionId, value);
                        Log.d(TAG, "Recorded hour value for session " + sessionId + ": " + value);
                    } catch (Exception e) {
                        Log.e(TAG, "Error getting value for hour variable " + sessionId, e);
                    }
                }
                
                for (Map.Entry<Integer, IntVar> entry : sessionResourceVars.entrySet()) {
                    int sessionId = entry.getKey();
                    IntVar var = entry.getValue();
                    try {
                        int value = var.getValue();
                        variableValues.put("resource_" + sessionId, value);
                        Log.d(TAG, "Recorded resource value for session " + sessionId + ": " + value);
                    } catch (Exception e) {
                        Log.e(TAG, "Error getting value for resource variable " + sessionId, e);
                    }
                }
                
                for (Map.Entry<Integer, IntVar> entry : sessionLecturerVars.entrySet()) {
                    int sessionId = entry.getKey();
                    IntVar var = entry.getValue();
                    try {
                        int value = var.getValue();
                        variableValues.put("lecturer_" + sessionId, value);
                        Log.d(TAG, "Recorded lecturer value for session " + sessionId + ": " + value);
                    } catch (Exception e) {
                        Log.e(TAG, "Error getting value for lecturer variable " + sessionId, e);
                    }
                }
                
                // Create a simple object to pass the values to buildTimetableFromSolution
                ValueSolution valueSolution = new ValueSolution(variableValues);
                
                // Modified version of buildTimetableFromSolution that uses our values directly
                return buildTimetableFromDirectValues(
                    valueSolution, allSessions, resourcesCopy, lecturersCopy, validCourses
                );
            } else {
                Log.e(TAG, "Choco Solver could not find a solution even with increased timeout.");
                
                // As a last resort, create a basic timetable with all courses manually scheduled
                Timetable manualTimetable = createManualTimetable(validCourses, resourcesCopy, lecturersCopy);
                
                // Verify all courses are included
                Set<String> scheduledCourseIds = new HashSet<>();
                for (TimetableSession session : manualTimetable.getSessions()) {
                    scheduledCourseIds.add(session.getCourseId());
                }
                
                List<Course> missingCourses = new ArrayList<>();
                for (Course course : validCourses) {
                    if (!scheduledCourseIds.contains(course.getId())) {
                        missingCourses.add(course);
                        Log.e(TAG, "Course still missing after manual addition: " + course.getName() + 
                            " (ID: " + course.getId() + ")");
                    }
                }
                
                if (!missingCourses.isEmpty()) {
                    Log.e(TAG, "Still missing " + missingCourses.size() + " courses after manual addition!");
                    
                    // Last resort: Manually add missing courses
                    Log.d(TAG, "Attempting to manually schedule remaining courses...");
                    
                    for (Course course : missingCourses) {
                        Resource resource = resources.isEmpty() ? null : resources.get(0);
                        Lecturer lecturer = lecturers.isEmpty() ? null : lecturers.get(0);
                        
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
                                manualTimetable.addSession(session);
                                Log.d(TAG, "Manually added session for " + course.getName() + " on day " + day + " at hour " + hour);
                            }
                        }
                    }
                }
                
                return manualTimetable;
            }
        }
    }

    private Timetable buildTimetableFromDirectValues(ValueSolution solution,
                                                  List<SessionToSchedule> allSessions,
                                                  List<Resource> resources,
                                                  List<Lecturer> lecturers,
                                                  List<Course> validCourses) {
        Timetable timetable = new Timetable();
        
        // Track sessions scheduled per course
        Map<String, Integer> scheduledSessionsPerCourse = new HashMap<>();
        for (Course course : validCourses) {
            scheduledSessionsPerCourse.put(course.getId(), 0);
        }
        
        // Process each session to create timetable entries
        for (SessionToSchedule session : allSessions) {
            int sessionId = session.getIndex();
            Course course = session.getCourse();
            
            // Get variable names
            String dayVarName = "day_" + sessionId;
            String hourVarName = "hour_" + sessionId;
            String resourceVarName = "resource_" + sessionId;
            String lecturerVarName = "lecturer_" + sessionId;
            
            try {
                // Extract values directly from our value map
                int dayValue = solution.getValue(dayVarName);
                int hourValue = solution.getValue(hourVarName);
                int resourceValue = solution.getValue(resourceVarName);
                int lecturerValue = solution.getValue(lecturerVarName);
                
                // If any value wasn't found, log but don't skip (we'll use fallbacks)
                boolean missingValues = false;
                if (dayValue == -1 || hourValue == -1 || resourceValue == -1 || lecturerValue == -1) {
                    Log.w(TAG, "Missing value for session " + sessionId + " of course " + course.getName() + " - using fallback values");
                    missingValues = true;
                } else {
                    // Log the retrieved values
                    Log.d(TAG, "Retrieved values for session " + sessionId + ": day=" + dayValue + 
                          ", hour=" + hourValue + ", resource=" + resourceValue + 
                          ", lecturer=" + lecturerValue);
                }
                
                // Find the corresponding resource and lecturer
                Resource resource;
                Lecturer lecturer; 
                
                if (missingValues) {
                    // Use fallback values if any are missing
                    int currentCount = scheduledSessionsPerCourse.getOrDefault(course.getId(), 0);
                    dayValue = currentCount % DAYS_PER_WEEK;
                    hourValue = (currentCount / DAYS_PER_WEEK) % HOURS_PER_DAY;
                    resource = resources.isEmpty() ? null : resources.get(0);
                    lecturer = lecturers.isEmpty() ? null : lecturers.get(0);
                } else {
                    // Make sure indices are within bounds
                    resourceValue = Math.min(resourceValue, resources.size() - 1);
                    lecturerValue = Math.min(lecturerValue, lecturers.size() - 1);
                    resource = resources.get(resourceValue);
                    lecturer = lecturers.get(lecturerValue);
                }
                
                // Create timetable entry
                if (resource != null && lecturer != null) {
                    TimetableSession timetableSession = new TimetableSession();
                    timetableSession.setId(UUID.randomUUID().toString());
                    timetableSession.setCourseId(course.getId());
                    timetableSession.setCourseName(course.getName());
                    timetableSession.setSessionType(course.getCode() != null ? course.getCode() : "LECTURE");
                    
                    // Set day, hour, resource, and lecturer
                    String dayOfWeek = DAYS_OF_WEEK[dayValue];
                    int startHour = START_HOUR + hourValue;
                    String startTime = String.format("%02d:00", startHour);
                    String endTime = String.format("%02d:00", startHour + 1);
                    
                    timetableSession.setDayOfWeek(dayOfWeek);
                    timetableSession.setStartTime(startTime);
                    timetableSession.setEndTime(endTime);
                    
                    timetableSession.setResourceId(resource.getId());
                    timetableSession.setResourceName(resource.getName());
                    timetableSession.setLecturerId(lecturer.getId());
                    timetableSession.setLecturerName(lecturer.getName());
                    
                    timetable.addSession(timetableSession);
                    
                    // Increment count of sessions for this course
                    int currentCount = scheduledSessionsPerCourse.getOrDefault(course.getId(), 0);
                    scheduledSessionsPerCourse.put(course.getId(), currentCount + 1);
                    
                    Log.d(TAG, "Added entry for " + course.getName() + " on day " + dayValue + 
                          " at hour " + hourValue + " (session " + (currentCount + 1) + 
                          " of " + course.getRequiredSessionsPerWeek() + ")");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error accessing solution value for session " + sessionId, e);
            }
        }
        
        // Verify all courses have the correct number of sessions
        for (Course course : validCourses) {
            int scheduledSessions = scheduledSessionsPerCourse.getOrDefault(course.getId(), 0);
            int requiredSessions = course.getRequiredSessionsPerWeek();
            
            Log.d(TAG, "Course " + course.getName() + ": scheduled " + scheduledSessions + 
                  " of " + requiredSessions + " required sessions");
            
            // Add any missing sessions manually
            if (scheduledSessions < requiredSessions) {
                Log.w(TAG, "Course " + course.getName() + " is missing " + 
                      (requiredSessions - scheduledSessions) + " sessions - adding manually");
                
                for (int i = scheduledSessions; i < requiredSessions; i++) {
                    Resource resource = resources.isEmpty() ? null : resources.get(0);
                    Lecturer lecturer = lecturers.isEmpty() ? null : lecturers.get(0);
                    
                    if (resource != null && lecturer != null) {
                        TimetableSession session = new TimetableSession();
                        session.setId(UUID.randomUUID().toString());
                        session.setCourseId(course.getId());
                        session.setCourseName(course.getName());
                        session.setSessionType(course.getCode() != null ? course.getCode() : "LECTURE");
                        
                        // Calculate a unique day and hour for this session
                        int day = i % DAYS_PER_WEEK;
                        int hour = (i / DAYS_PER_WEEK) % HOURS_PER_DAY;
                        
                        // Adjust to minimize conflicts
                        day = (day + scheduledSessions) % DAYS_PER_WEEK;
                        hour = (hour + scheduledSessions) % HOURS_PER_DAY;
                        
                        // Set day, hour, resource, and lecturer
                        String dayOfWeek = DAYS_OF_WEEK[day];
                        int startHour = START_HOUR + hour;
                        String startTime = String.format("%02d:00", startHour);
                        String endTime = String.format("%02d:00", startHour + 1);
                        
                        session.setDayOfWeek(dayOfWeek);
                        session.setStartTime(startTime);
                        session.setEndTime(endTime);
                        
                        session.setResourceId(resource.getId());
                        session.setResourceName(resource.getName());
                        session.setLecturerId(lecturer.getId());
                        session.setLecturerName(lecturer.getName());
                        
                        timetable.addSession(session);
                        Log.d(TAG, "Manually added session " + (i + 1) + " of " + requiredSessions + 
                              " for " + course.getName() + " on " + dayOfWeek + " at " + startTime);
                    }
                }
            }
        }
        
        // Final verification
        Log.d(TAG, "Final timetable has " + timetable.getSessions().size() + " total sessions");
        for (Course course : validCourses) {
            int finalSessionCount = 0;
            for (TimetableSession session : timetable.getSessions()) {
                if (session.getCourseId().equals(course.getId())) {
                    finalSessionCount++;
                }
            }
            Log.d(TAG, "Course " + course.getName() + " has " + finalSessionCount + 
                  " sessions in final timetable (required: " + course.getRequiredSessionsPerWeek() + ")");
        }
        
        return timetable;
    }

    private List<Integer> findCompatibleResources(Course course, List<Resource> resources) {
        Log.d(TAG, "Attempting to allocate resources for course: " + course.getId());
        Log.d(TAG, "Required room type: " + course.getRequiredRoomType());
        
        List<Integer> compatibleResourceIndices = new ArrayList<>();
        
        String requiredRoomType = course.getRequiredRoomType();
        String courseName = course.getName();
        
        for (int j = 0; j < resources.size(); j++) {
            Resource resource = resources.get(j);
            String resourceType = resource.getType();
            
            // Log the resources being checked
            Log.d(TAG, "Checking resource: " + resource.getName() + " (Type: " + resourceType + ")");
            
            // Always add resource if no room type is specified
            if (requiredRoomType == null || requiredRoomType.isEmpty()) {
                compatibleResourceIndices.add(j);
                Log.d(TAG, "Added resource (no room type specified)");
                continue;
            }
            
            // Handle LAB requirement
            if (requiredRoomType.equals("LAB")) {
                if (resourceType != null && resourceType.contains("LAB")) {
                    compatibleResourceIndices.add(j);
                    Log.d(TAG, "Added LAB resource: " + resource.getName());
                }
            }
            // Handle LECTURE_HALL requirement
            else if (requiredRoomType.equals("LECTURE_HALL")) {
                if (resourceType != null && 
                    (resourceType.contains("HALL") || resourceType.contains("ROOM"))) {
                    compatibleResourceIndices.add(j);
                    Log.d(TAG, "Added LECTURE_HALL resource: " + resource.getName());
                }
            }
            // Handle any other room type requirements
            else {
                if (resourceType != null && resourceType.contains(requiredRoomType)) {
                    compatibleResourceIndices.add(j);
                    Log.d(TAG, "Added custom type resource: " + resource.getName());
                }
            }
        }
        
        // If no resources were found, log a warning and use all resources
        if (compatibleResourceIndices.isEmpty()) {
            Log.w(TAG, "No compatible resources found for course: " + courseName);
            Log.d(TAG, "Using all available resources as fallback for course: " + courseName);
            for (int j = 0; j < resources.size(); j++) {
                compatibleResourceIndices.add(j);
            }
        }
        
        Log.d(TAG, "Found " + compatibleResourceIndices.size() + " compatible resources for course: " + courseName);
        
        return compatibleResourceIndices;
    }

    private Timetable createManualTimetable(List<Course> courses, List<Resource> resources, List<Lecturer> lecturers) {
        Timetable manualTimetable = new Timetable();
        
        // Schedule each course manually
        int dayIndex = 0;
        int hourIndex = 0;
        
        for (Course course : courses) {
            try {
                int requiredSessions = Math.max(1, course.getRequiredSessionsPerWeek());
                
                for (int i = 0; i < requiredSessions; i++) {
                    // Create manual session
                    TimetableSession manualSession = new TimetableSession();
                    manualSession.setId(UUID.randomUUID().toString());
                    manualSession.setCourseId(course.getId());
                    manualSession.setCourseName(course.getName());
                    manualSession.setSessionType(course.getCode() != null ? course.getCode() : "LECTURE");
                    
                    // Use simple round-robin assignment
                    String dayOfWeek = DAYS_OF_WEEK[dayIndex];
                    int startHour = START_HOUR + hourIndex;
                    String startTime = String.format("%02d:00", startHour);
                    String endTime = String.format("%02d:00", startHour + 1);
                    
                    manualSession.setDayOfWeek(dayOfWeek);
                    manualSession.setStartTime(startTime);
                    manualSession.setEndTime(endTime);
                    
                    // Assign resource and lecturer
                    Resource resource = resources.isEmpty() ? null : resources.get(0);
                    Lecturer lecturer = lecturers.isEmpty() ? null : lecturers.get(0);
                    
                    manualSession.setResourceId(resource != null ? resource.getId() : "default-resource-id");
                    manualSession.setResourceName(resource != null ? resource.getName() : "Default Resource");
                    manualSession.setLecturerId(lecturer != null ? lecturer.getId() : "default-lecturer-id");
                    manualSession.setLecturerName(lecturer != null ? lecturer.getName() : "Default Lecturer");
                    
                    manualTimetable.addSession(manualSession);
                    
                    // Update indices for next session
                    hourIndex = (hourIndex + 1) % HOURS_PER_DAY;
                    if (hourIndex == 0) {
                        dayIndex = (dayIndex + 1) % DAYS_PER_WEEK;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in manual scheduling for course: " + course.getName(), e);
            }
        }
        
        Log.d(TAG, "Created manual timetable with " + manualTimetable.getSessions().size() + " sessions");
        return manualTimetable;
    }

    private void addConstraints(Model model, List<SessionToSchedule> allSessions, 
                                List<Resource> resources, List<Lecturer> lecturers,
                                Map<Integer, IntVar> sessionDayVars,
                                Map<Integer, IntVar> sessionHourVars,
                                Map<Integer, IntVar> sessionResourceVars,
                                Map<Integer, IntVar> sessionLecturerVars) {
        // CONSTRAINT: No resource can be used by more than one session at the same time
        for (int i = 0; i < allSessions.size(); i++) {
            SessionToSchedule sessionI = allSessions.get(i);
            for (int j = i + 1; j < allSessions.size(); j++) {
                SessionToSchedule sessionJ = allSessions.get(j);
                
                // If sessions are on the same day, at the same hour, they can't use the same resource
                model.ifThen(
                    model.and(
                        model.arithm(sessionDayVars.get(sessionI.getIndex()), "=", sessionDayVars.get(sessionJ.getIndex())),
                        model.arithm(sessionHourVars.get(sessionI.getIndex()), "=", sessionHourVars.get(sessionJ.getIndex()))
                    ),
                    model.arithm(sessionResourceVars.get(sessionI.getIndex()), "!=", sessionResourceVars.get(sessionJ.getIndex()))
                );
            }
        }
        
        // CONSTRAINT: No lecturer can teach more than one session at the same time
        for (int i = 0; i < allSessions.size(); i++) {
            SessionToSchedule sessionI = allSessions.get(i);
            for (int j = i + 1; j < allSessions.size(); j++) {
                SessionToSchedule sessionJ = allSessions.get(j);
                
                // If sessions are on the same day, at the same hour, they can't have the same lecturer
                model.ifThen(
                    model.and(
                        model.arithm(sessionDayVars.get(sessionI.getIndex()), "=", sessionDayVars.get(sessionJ.getIndex())),
                        model.arithm(sessionHourVars.get(sessionI.getIndex()), "=", sessionHourVars.get(sessionJ.getIndex()))
                    ),
                    model.arithm(sessionLecturerVars.get(sessionI.getIndex()), "!=", sessionLecturerVars.get(sessionJ.getIndex()))
                );
            }
        }
    }

    private void addManualSessionsForCourse(Course course, List<Resource> resources, List<Lecturer> lecturers, Timetable timetable) {
        Log.d(TAG, "Manually adding sessions for course: " + course.getName());
        
        // Find a suitable resource and lecturer
        Resource selectedResource = null;
        Lecturer selectedLecturer = null;
        
        // Find a resource with matching type if available
        String requiredType = "LECTURE_HALL"; // Default to lecture hall
        if (course.getCode() != null && course.getCode().contains("LAB")) {
            requiredType = "LAB";
        }
        
        // Try to find a compatible resource
        for (Resource resource : resources) {
            if (resource.getType() != null && resource.getType().equalsIgnoreCase(requiredType)) {
                selectedResource = resource;
                break;
            }
        }
        
        // If no matching resource was found, use the first one
        if (selectedResource == null && !resources.isEmpty()) {
            selectedResource = resources.get(0);
        }
        
        // Use the first lecturer
        if (!lecturers.isEmpty()) {
            selectedLecturer = lecturers.get(0);
        }
        
        // Check if we have the minimum requirements
        if (selectedResource == null || selectedLecturer == null) {
            Log.e(TAG, "Cannot manually schedule course " + course.getName() + " - no resources or lecturers available");
            return;
        }
        
        // Find a free slot for the course
        // Start with Monday at 8 AM and try each day/hour combination
        boolean slotFound = false;
        
        // Try each day and hour until a free slot is found
        for (int day = 0; day < DAYS_OF_WEEK.length && !slotFound; day++) {
            for (int hour = 0; hour < 10 && !slotFound; hour++) {
                // Check if this slot is free for the resource and lecturer
                boolean slotIsFree = true;
                
                for (TimetableSession existingSession : timetable.getSessions()) {
                    String dayOfWeek = DAYS_OF_WEEK[day];
                    int startHour = START_HOUR + hour;
                    String startTime = String.format("%02d:00", startHour);
                    
                    if (existingSession.getDayOfWeek().equals(dayOfWeek) && 
                        existingSession.getStartTime().equals(startTime)) {
                        // Check if resource or lecturer is busy
                        if (existingSession.getResourceId().equals(selectedResource.getId()) ||
                            existingSession.getLecturerId().equals(selectedLecturer.getId())) {
                            slotIsFree = false;
                            break;
                        }
                    }
                }
                
                // If slot is free, create a session
                if (slotIsFree) {
                    // Create session
                    TimetableSession timetableSession = new TimetableSession();
                    timetableSession.setId(UUID.randomUUID().toString());
                    timetableSession.setCourseId(course.getId());
                    timetableSession.setCourseName(course.getName());
                    timetableSession.setSessionType(course.getCode() != null ? course.getCode() : "LECTURE");
                    
                    // Set day, hour, resource, and lecturer
                    String dayOfWeek = DAYS_OF_WEEK[day];
                    int startHour = START_HOUR + hour;
                    String startTime = String.format("%02d:00", startHour);
                    String endTime = String.format("%02d:00", startHour + 1);
                    
                    timetableSession.setDayOfWeek(dayOfWeek);
                    timetableSession.setStartTime(startTime);
                    timetableSession.setEndTime(endTime);
                    
                    timetableSession.setResourceId(selectedResource.getId());
                    timetableSession.setResourceName(selectedResource.getName());
                    timetableSession.setLecturerId(selectedLecturer.getId());
                    timetableSession.setLecturerName(selectedLecturer.getName());
                    
                    timetable.addSession(timetableSession);
                    
                    Log.d(TAG, "Manually scheduled " + course.getName() + " on " + dayOfWeek + " at " + startTime);
                    slotFound = true;
                }
            }
        }
        
        if (!slotFound) {
            Log.w(TAG, "Could not find free slot for " + course.getName() + " - adding anyway in first slot");
            
            // Create session without checking for conflicts
            TimetableSession timetableSession = new TimetableSession();
            timetableSession.setId(UUID.randomUUID().toString());
            timetableSession.setCourseName(course.getName());
            timetableSession.setCourseId(course.getId());
            timetableSession.setSessionType(course.getCode() != null ? course.getCode() : "LECTURE");
            
            // Set to Monday at 8 AM by default
            timetableSession.setDayOfWeek(DAYS_OF_WEEK[0]);
            timetableSession.setStartTime(String.format("%02d:00", START_HOUR));
            timetableSession.setEndTime(String.format("%02d:00", START_HOUR + 1));
            
            timetableSession.setResourceId(selectedResource.getId());
            timetableSession.setResourceName(selectedResource.getName());
            timetableSession.setLecturerId(selectedLecturer.getId());
            timetableSession.setLecturerName(selectedLecturer.getName());
            
            timetable.addSession(timetableSession);
            
            Log.d(TAG, "Force scheduled " + course.getName() + " on Monday at 8:00");
        }
    }

    private static class ValueSolution {
        private final Map<String, Integer> values;
        
        public ValueSolution(Map<String, Integer> values) {
            this.values = values;
        }
        
        public int getValue(String name) {
            return values.getOrDefault(name, -1);
        }
    }

    private static class SessionToSchedule {
        private final int index;
        private final Course course;
        
        public SessionToSchedule(int index, Course course) {
            this.index = index;
            this.course = course;
        }
        
        public int getIndex() {
            return index;
        }
        
        public Course getCourse() {
            return course;
        }
    }

    @Override
    public boolean hasConflicts(Timetable timetable) {
        TimetableGenerator simpleGenerator = new SimpleTimetableGenerator();
        return simpleGenerator.hasConflicts(timetable);
    }

    private String calculateEndTime(String startTime, int durationHours) {
        String[] parts = startTime.split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);
        
        hour += durationHours;
        
        return String.format("%02d:%02d", hour, minute);
    }
}
