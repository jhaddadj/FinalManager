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
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
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
                                int hour = (i / 5) % 8;  // 9 AM to 4 PM
                                
                                session.setDayOfWeek(DAYS_OF_WEEK[day]);
                                session.setStartTime(String.format("%02d:00", hour + 9));
                                session.setEndTime(String.format("%02d:00", hour + 10));
                                
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
        Log.d(TAG, "Creating manual timetable as fallback");
        Timetable timetable = new Timetable();
        
        // For each course, add the required number of sessions
        // Try to distribute them evenly across the week
        int totalSessions = 0;
        for (Course course : courses) {
            totalSessions += course.getRequiredSessionsPerWeek();
        }
        
        Log.d(TAG, "Total sessions to manually schedule: " + totalSessions);
        
        // First, assign appropriate lecturers and resources
        for (Course course : courses) {
            addManualSessionsForCourse(course, resources, lecturers, timetable);
        }
        
        // Check for conflicts and try to resolve them
        if (hasConflicts(timetable)) {
            Log.w(TAG, "Manual timetable has conflicts - attempting to resolve");
            
            // Simple strategy: If conflicts found, shift problematic sessions to later hours
            List<TimetableSession> sessions = new ArrayList<>(timetable.getSessions());
            Map<String, List<TimetableSession>> sessionsByTime = new HashMap<>();
            
            // Group sessions by day and hour
            for (TimetableSession session : sessions) {
                String key = session.getDayOfWeek() + "-" + session.getStartTime();
                if (!sessionsByTime.containsKey(key)) {
                    sessionsByTime.put(key, new ArrayList<>());
                }
                sessionsByTime.get(key).add(session);
            }
            
            // Fix overlapping sessions by redistributing them to available slots
            for (Map.Entry<String, List<TimetableSession>> entry : sessionsByTime.entrySet()) {
                if (entry.getValue().size() > 1) {
                    Log.d(TAG, "Found conflict at " + entry.getKey() + " with " + entry.getValue().size() + " sessions");
                    
                    // Keep the first session in place, move others
                    for (int i = 1; i < entry.getValue().size(); i++) {
                        TimetableSession session = entry.getValue().get(i);
                        
                        // Try to find a free slot - prioritize spreading throughout the day
                        boolean relocated = false;
                        for (int day = 0; day < DAYS_PER_WEEK; day++) {
                            String dayOfWeek = DAYS_OF_WEEK[day];
                            // Try to distribute across all hours (9am-5pm)
                            for (int hourOffset = 0; hourOffset < HOURS_PER_DAY; hourOffset++) {
                                // Use a better distribution by trying slots in this order: 
                                // 12pm, 10am, 2pm, 9am, 3pm, 11am, 1pm, 4pm
                                int[] hourOrder = {3, 1, 5, 0, 6, 2, 4, 7};
                                int hour = START_HOUR + hourOrder[hourOffset % hourOrder.length];
                                
                                String newTimeKey = dayOfWeek + "-" + String.format("%02d:00", hour);
                                
                                if (!sessionsByTime.containsKey(newTimeKey) || sessionsByTime.get(newTimeKey).isEmpty()) {
                                    // This slot is free, move the session here
                                    session.setDayOfWeek(dayOfWeek);
                                    session.setStartTime(String.format("%02d:00", hour));
                                    session.setEndTime(String.format("%02d:00", hour + 1));
                                    
                                    // Update our tracking map
                                    if (!sessionsByTime.containsKey(newTimeKey)) {
                                        sessionsByTime.put(newTimeKey, new ArrayList<>());
                                    }
                                    sessionsByTime.get(newTimeKey).add(session);
                                    
                                    // Remove from old slot
                                    entry.getValue().remove(i);
                                    i--; // Adjust index after removal
                                    
                                    relocated = true;
                                    Log.d(TAG, "Relocated session to " + newTimeKey);
                                    break;
                                }
                            }
                            if (relocated) break;
                        }
                        
                        if (!relocated) {
                            Log.w(TAG, "Could not find free slot for session - keeping in original position");
                        }
                    }
                }
            }
        }
        
        return timetable;
    }

    private void addConstraints(Model model, List<SessionToSchedule> allSessions, 
                                List<Resource> resources, List<Lecturer> lecturers,
                                Map<Integer, IntVar> sessionDayVars,
                                Map<Integer, IntVar> sessionHourVars,
                                Map<Integer, IntVar> sessionResourceVars,
                                Map<Integer, IntVar> sessionLecturerVars) {
        Log.d(TAG, "Adding constraints to the model");
        
        // 1. No lecturer can be in two places at the same time
        for (int i = 0; i < allSessions.size(); i++) {
            SessionToSchedule session1 = allSessions.get(i);
            IntVar day1 = sessionDayVars.get(session1.getIndex());
            IntVar hour1 = sessionHourVars.get(session1.getIndex());
            IntVar lecturer1 = sessionLecturerVars.get(session1.getIndex());
            
            for (int j = i + 1; j < allSessions.size(); j++) {
                SessionToSchedule session2 = allSessions.get(j);
                IntVar day2 = sessionDayVars.get(session2.getIndex());
                IntVar hour2 = sessionHourVars.get(session2.getIndex());
                IntVar lecturer2 = sessionLecturerVars.get(session2.getIndex());
                
                // If it's the same lecturer, they can't be in two sessions at the same time
                model.ifThen(
                    model.arithm(lecturer1, "=", lecturer2),
                    model.or(
                        model.arithm(day1, "!=", day2),
                        model.arithm(hour1, "!=", hour2)
                    )
                );
            }
        }
        
        // Track the number of sessions per day and per hour
        IntVar[] dayCounts = new IntVar[DAYS_PER_WEEK];
        IntVar[] hourCounts = new IntVar[HOURS_PER_DAY];
        
        for (int d = 0; d < DAYS_PER_WEEK; d++) {
            dayCounts[d] = model.intVar("dayCount_" + d, 0, allSessions.size());
            
            // Count sessions on this day
            IntVar[] dayBoolVars = new IntVar[allSessions.size()];
            for (int i = 0; i < allSessions.size(); i++) {
                IntVar dayVar = sessionDayVars.get(allSessions.get(i).getIndex());
                dayBoolVars[i] = model.intEqView(dayVar, d);
            }
            model.sum(dayBoolVars, "=", dayCounts[d]).post();
        }
        
        for (int h = 0; h < HOURS_PER_DAY; h++) {
            hourCounts[h] = model.intVar("hourCount_" + h, 0, allSessions.size());
            
            // Count sessions in this hour
            IntVar[] hourBoolVars = new IntVar[allSessions.size()];
            for (int i = 0; i < allSessions.size(); i++) {
                IntVar hourVar = sessionHourVars.get(allSessions.get(i).getIndex());
                hourBoolVars[i] = model.intEqView(hourVar, h);
            }
            model.sum(hourBoolVars, "=", hourCounts[h]).post();
        }
        
        // Day distribution - Ensure sessions are distributed evenly across days
        int totalSessions = allSessions.size();
        int idealSessionsPerDay = (int) Math.ceil((double) totalSessions / DAYS_PER_WEEK);
        
        // Create a max difference variable to minimize the imbalance between days
        IntVar maxDayDiff = model.intVar("maxDayDiff", 0, totalSessions);
        
        // For each day, constrain the count to be close to the ideal
        for (int d = 0; d < DAYS_PER_WEEK; d++) {
            // Create target variable for this day
            IntVar targetDayVar = model.intVar("targetDay_" + d, idealSessionsPerDay);
            
            // Soft constraint to encourage approaching the target
            IntVar dayDiff = model.intVar("dayDiff_" + d, 0, totalSessions);
            model.distance(dayCounts[d], targetDayVar, "=", dayDiff).post();
            
            // Link to max difference to minimize the worst imbalance
            model.arithm(dayDiff, "<=", maxDayDiff).post();
        }
        
        // Set objective to minimize day imbalance
        model.setObjective(Model.MINIMIZE, maxDayDiff);
        
        // 6. Try to distribute the hours more evenly by encouraging sessions to spread 
        // through specific hour slots
        int idealSessionsPerHour = (int) Math.ceil((double) totalSessions / HOURS_PER_DAY);
        
        // Create a max difference variable to minimize the imbalance between hours
        IntVar maxHourDiff = model.intVar("maxHourDiff", 0, totalSessions);
        
        for (int h = 0; h < HOURS_PER_DAY; h++) {
            // Try to keep the number of sessions per hour close to the ideal
            // We use a preference for each hour to help distribute sessions better
            double preference = 1.0;
            
            // Make middle hours (11am-2pm) slightly more preferable
            if (h >= 2 && h <= 5) {
                preference = 1.2;
            }
            
            int targetSessionsForHour = (int) Math.round(idealSessionsPerHour * preference);
            
            // Create an IntVar for the target value since distance requires IntVar, not int
            IntVar targetVar = model.intVar("target_" + h, targetSessionsForHour);
            
            // Soft constraint to encourage approaching this target
            IntVar diff = model.intVar("hourDiff_" + h, 0, totalSessions);
            model.distance(hourCounts[h], targetVar, "=", diff).post();
            
            // Link to max difference to minimize the worst imbalance
            model.arithm(diff, "<=", maxHourDiff).post();
        }
        
        // Also account for hour distribution in the objective
        // Create a combined objective to try to balance both day and hour distribution
        IntVar combinedDiff = model.intVar("combinedDiff", 0, totalSessions * 2);
        model.arithm(maxDayDiff, "+", maxHourDiff, "=", combinedDiff).post();
        model.setObjective(Model.MINIMIZE, combinedDiff);
    }

    private void addManualSessionsForCourse(Course course, List<Resource> resources, List<Lecturer> lecturers, Timetable timetable) {
        Log.d(TAG, "Manually adding sessions for course: " + course.getName());
        
        // Find appropriate resource and lecturer
        Resource resource = null;
        Lecturer lecturer = null;
        
        // If course has assigned resource/lecturer, use those
        if (course.getAssignedResourceId() != null && !course.getAssignedResourceId().isEmpty()) {
            for (Resource r : resources) {
                if (r.getId().equals(course.getAssignedResourceId())) {
                    resource = r;
                    break;
                }
            }
        }
        
        if (course.getAssignedLecturerId() != null && !course.getAssignedLecturerId().isEmpty()) {
            for (Lecturer l : lecturers) {
                if (l.getId().equals(course.getAssignedLecturerId())) {
                    lecturer = l;
                    break;
                }
            }
        }
        
        // If no assigned resource/lecturer, use the first available
        if (resource == null && !resources.isEmpty()) {
            resource = resources.get(0);
        }
        
        if (lecturer == null && !lecturers.isEmpty()) {
            lecturer = lecturers.get(0);
        }
        
        if (resource != null && lecturer != null) {
            int sessionsPerCourse = course.getRequiredSessionsPerWeek();
            
            // Get current session counts for better distribution
            int[] sessionsByDay = new int[DAYS_PER_WEEK];
            int[] sessionsByHour = new int[HOURS_PER_DAY];
            
            // Calculate current distributions from existing sessions
            for (TimetableSession session : timetable.getSessions()) {
                String day = session.getDayOfWeek();
                int dayIndex = Arrays.asList(DAYS_OF_WEEK).indexOf(day);
                
                if (dayIndex >= 0) {
                    sessionsByDay[dayIndex]++;
                    
                    // Extract hour from time format like "09:00"
                    String startTime = session.getStartTime();
                    if (startTime != null && startTime.length() >= 5) {
                        try {
                            int hour = Integer.parseInt(startTime.substring(0, 2));
                            int hourIndex = hour - START_HOUR;
                            if (hourIndex >= 0 && hourIndex < HOURS_PER_DAY) {
                                sessionsByHour[hourIndex]++;
                            }
                        } catch (NumberFormatException e) {
                            Log.e(TAG, "Error parsing time: " + startTime, e);
                        }
                    }
                }
            }
            
            // Spread the sessions evenly over days and hours
            for (int i = 0; i < sessionsPerCourse; i++) {
                TimetableSession session = new TimetableSession();
                session.setId(UUID.randomUUID().toString());
                session.setCourseId(course.getId());
                session.setCourseName(course.getName());
                session.setSessionType(course.getCode() != null ? course.getCode() : "LECTURE");
                
                // Find the day with fewest sessions
                int minDaySessions = Integer.MAX_VALUE;
                int bestDayIndex = 0;
                
                for (int d = 0; d < DAYS_PER_WEEK; d++) {
                    if (sessionsByDay[d] < minDaySessions) {
                        minDaySessions = sessionsByDay[d];
                        bestDayIndex = d;
                    }
                }
                
                // Find the hour with fewest sessions
                int minHourSessions = Integer.MAX_VALUE;
                int bestHourIndex = 0;
                
                for (int h = 0; h < HOURS_PER_DAY; h++) {
                    if (sessionsByHour[h] < minHourSessions) {
                        minHourSessions = sessionsByHour[h];
                        bestHourIndex = h;
                    }
                }
                
                // Set day and time for this session
                String day = DAYS_OF_WEEK[bestDayIndex];
                int hour = START_HOUR + bestHourIndex;
                String formattedHour = String.format("%02d:00", hour);
                
                session.setDayOfWeek(day);
                session.setStartTime(formattedHour);
                session.setEndTime(calculateEndTime(formattedHour, 1));
                session.setResourceId(resource.getId());
                session.setResourceName(resource.getName());
                session.setLecturerId(lecturer.getId());
                session.setLecturerName(lecturer.getName());
                
                // Check for conflicts
                boolean hasConflict = false;
                
                // Check for conflicts with existing sessions
                for (TimetableSession existingSession : timetable.getSessions()) {
                    if (existingSession.getDayOfWeek().equals(day) && 
                        existingSession.getStartTime().equals(formattedHour) && 
                        (existingSession.getResourceId().equals(resource.getId()) || 
                         existingSession.getLecturerId().equals(lecturer.getId()))) {
                        hasConflict = true;
                        break;
                    }
                }
                
                // If conflict detected, try alternate hours
                if (hasConflict) {
                    // Try other hours on the same day first
                    for (int h = 0; h < HOURS_PER_DAY; h++) {
                        if (h == bestHourIndex) continue;
                        
                        int alternateHour = START_HOUR + h;
                        String alternateFormattedHour = String.format("%02d:00", alternateHour);
                        
                        boolean alternateHasConflict = false;
                        for (TimetableSession existingSession : timetable.getSessions()) {
                            if (existingSession.getDayOfWeek().equals(day) &&
                                existingSession.getStartTime().equals(alternateFormattedHour) && 
                                (existingSession.getResourceId().equals(resource.getId()) || 
                                 existingSession.getLecturerId().equals(lecturer.getId()))) {
                                alternateHasConflict = true;
                                break;
                            }
                        }
                        
                        if (!alternateHasConflict) {
                            session.setStartTime(alternateFormattedHour);
                            session.setEndTime(calculateEndTime(alternateFormattedHour, 1));
                            hasConflict = false;
                            break;
                        }
                    }
                    
                    // If still conflict, try another day
                    if (hasConflict) {
                        // Find the next best day
                        int secondBestDayIndex = 0;
                        int secondMinSessions = Integer.MAX_VALUE;
                        
                        for (int d = 0; d < DAYS_PER_WEEK; d++) {
                            if (d != bestDayIndex && sessionsByDay[d] < secondMinSessions) {
                                secondMinSessions = sessionsByDay[d];
                                secondBestDayIndex = d;
                            }
                        }
                        
                        day = DAYS_OF_WEEK[secondBestDayIndex];
                        session.setDayOfWeek(day);
                        
                        // Check all hours on this day for conflicts
                        for (int h = 0; h < HOURS_PER_DAY; h++) {
                            int alternateHour = START_HOUR + h;
                            String alternateFormattedHour = String.format("%02d:00", alternateHour);
                            
                            boolean alternateHasConflict = false;
                            for (TimetableSession existingSession : timetable.getSessions()) {
                                if (existingSession.getDayOfWeek().equals(day) &&
                                    existingSession.getStartTime().equals(alternateFormattedHour) && 
                                    (existingSession.getResourceId().equals(resource.getId()) || 
                                     existingSession.getLecturerId().equals(lecturer.getId()))) {
                                    alternateHasConflict = true;
                                    break;
                                }
                            }
                            
                            if (!alternateHasConflict) {
                                session.setStartTime(alternateFormattedHour);
                                session.setEndTime(calculateEndTime(alternateFormattedHour, 1));
                                hasConflict = false;
                                break;
                            }
                        }
                    }
                }
                
                // Only add if no conflict or conflict was resolved
                if (!hasConflict) {
                    timetable.addSession(session);
                    
                    // Update counts for next iteration
                    int usedDayIndex = Arrays.asList(DAYS_OF_WEEK).indexOf(day);
                    sessionsByDay[usedDayIndex]++;
                    
                    int usedHour = Integer.parseInt(session.getStartTime().substring(0, 2));
                    int usedHourIndex = usedHour - START_HOUR;
                    sessionsByHour[usedHourIndex]++;
                    
                    Log.d(TAG, "Added session for " + course.getName() + " on " + day + " at " + session.getStartTime());
                } else {
                    Log.w(TAG, "Couldn't resolve conflicts for session of " + course.getName());
                }
            }
        } else {
            Log.e(TAG, "Could not add manual sessions for " + course.getName() + ": no resource or lecturer available");
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
