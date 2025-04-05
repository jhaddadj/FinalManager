package com.example.manager.timetable;

/**
 * Represents a course or subject in the academic system.
 * Contains information about the course requirements, such as
 * duration, preferred resources, eligible lecturers, etc.
 */
public class Course {
    private String id;
    private String name;
    private String code;
    private int creditHours;
    private String department;
    private int requiredSessionsPerWeek;
    private String requiredRoomType;
    private String assignedLecturerId; // ID of assigned lecturer from course management
    private String assignedResourceId; // ID of assigned resource (room) from course management
    
    // Empty constructor for Firebase
    public Course() {
    }
    
    public Course(String id, String name, String code, int creditHours, String department, int requiredSessionsPerWeek) {
        this.id = id;
        this.name = name;
        this.code = code;
        this.creditHours = creditHours;
        this.department = department;
        this.requiredSessionsPerWeek = requiredSessionsPerWeek;
        this.requiredRoomType = null; // Default to null
        this.assignedLecturerId = null; // Default to null
        this.assignedResourceId = null; // Default to null
    }
    
    public Course(String id, String name, String code, int creditHours, String department, int requiredSessionsPerWeek, String requiredRoomType) {
        this.id = id;
        this.name = name;
        this.code = code;
        this.creditHours = creditHours;
        this.department = department;
        this.requiredSessionsPerWeek = requiredSessionsPerWeek;
        this.requiredRoomType = requiredRoomType;
        this.assignedLecturerId = null; // Default to null
        this.assignedResourceId = null; // Default to null
    }
    
    // Full constructor with assignedLecturerId and assignedResourceId
    public Course(String id, String name, String code, int creditHours, String department, 
                 int requiredSessionsPerWeek, String requiredRoomType, String assignedLecturerId, String assignedResourceId) {
        this.id = id;
        this.name = name;
        this.code = code;
        this.creditHours = creditHours;
        this.department = department;
        this.requiredSessionsPerWeek = requiredSessionsPerWeek;
        this.requiredRoomType = requiredRoomType;
        this.assignedLecturerId = assignedLecturerId;
        this.assignedResourceId = assignedResourceId;
    }
    
    // Getters and setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getCode() {
        return code;
    }
    
    public void setCode(String code) {
        this.code = code;
    }
    
    public int getCreditHours() {
        return creditHours;
    }
    
    public void setCreditHours(int creditHours) {
        this.creditHours = creditHours;
    }
    
    public String getDepartment() {
        return department;
    }
    
    public void setDepartment(String department) {
        this.department = department;
    }
    
    public int getRequiredSessionsPerWeek() {
        return requiredSessionsPerWeek;
    }
    
    public void setRequiredSessionsPerWeek(int requiredSessionsPerWeek) {
        this.requiredSessionsPerWeek = requiredSessionsPerWeek;
    }
    
    /**
     * Gets the required room type for this course (e.g., "LAB", "LECTURE_HALL", "SEMINAR_ROOM").
     * If a specific room type is not set, defaults to the department as a fallback.
     * 
     * @return The required room type, or null if no constraints exist
     */
    public String getRequiredRoomType() {
        return requiredRoomType != null ? requiredRoomType : department;
    }
    
    /**
     * Sets the required room type for this course.
     * 
     * @param requiredRoomType The type of room required, e.g., "LAB", "LECTURE_HALL", etc.
     */
    public void setRequiredRoomType(String requiredRoomType) {
        this.requiredRoomType = requiredRoomType;
    }
    
    /**
     * Gets the assigned lecturer ID from the course management system.
     * 
     * @return The ID of the lecturer assigned to this course, or null if none assigned
     */
    public String getAssignedLecturerId() {
        return assignedLecturerId;
    }
    
    /**
     * Sets the assigned lecturer ID.
     * 
     * @param assignedLecturerId The ID of the lecturer to assign to this course
     */
    public void setAssignedLecturerId(String assignedLecturerId) {
        this.assignedLecturerId = assignedLecturerId;
    }
    
    /**
     * Gets the assigned resource ID from the course management system.
     * 
     * @return The ID of the resource (room) assigned to this course, or null if none assigned
     */
    public String getAssignedResourceId() {
        return assignedResourceId;
    }
    
    /**
     * Sets the assigned resource ID.
     * 
     * @param assignedResourceId The ID of the resource (room) to assign to this course
     */
    public void setAssignedResourceId(String assignedResourceId) {
        this.assignedResourceId = assignedResourceId;
    }
    
    /**
     * Calculates the typical duration for a session of this course
     * based on credit hours and required sessions per week
     * 
     * @return Duration in minutes
     */
    public int getTypicalSessionDuration() {
        // Simple formula: (credit hours * 60) / sessions per week
        // This is an example - adjust based on your academic regulations
        return (creditHours * 60) / requiredSessionsPerWeek;
    }
    
    /* 
     * Future constraint solver integration:
     * - Add preferred lecturers list
     * - Add required resource types (lab, lecture hall, etc.)
     * - Add student group associations
     * - Add prerequisites and corequisites
     */
}
