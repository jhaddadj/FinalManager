package com.example.manager.admin.model;

/**
 * CourseItem represents a course in the educational system.
 * It contains information such as name, code, duration hours, department,
 * the number of lecture and lab sessions required, and the assigned lecturer.
 */
public class CourseItem {
    private String id;
    private String name;
    private String code;
    private int durationHours;
    private String department;
    private int numberOfLectures;
    private int numberOfLabs;
    private String adminId;
    private String assignedLecturerId;
    private String assignedResourceId;

    /**
     * Default constructor required for Firebase
     */
    public CourseItem() {
        // Required empty constructor for Firebase
    }

    /**
     * Constructor to create a new CourseItem
     *
     * @param id The unique identifier of the course
     * @param name The name of the course
     * @param code The course code
     * @param durationHours Duration of the course in hours
     * @param department Department the course belongs to
     * @param numberOfLectures Number of lecture sessions required 
     * @param numberOfLabs Number of lab sessions required
     * @param adminId ID of the admin who created this course
     * @param assignedLecturerId ID of the lecturer assigned to this course (can be empty)
     * @param assignedResourceId ID of the resource (room) assigned to this course (can be empty)
     */
    public CourseItem(String id, String name, String code, int durationHours, String department, 
                       int numberOfLectures, int numberOfLabs, String adminId, String assignedLecturerId,
                       String assignedResourceId) {
        this.id = id;
        this.name = name;
        this.code = code;
        this.durationHours = durationHours;
        this.department = department;
        this.numberOfLectures = numberOfLectures;
        this.numberOfLabs = numberOfLabs;
        this.adminId = adminId;
        this.assignedLecturerId = assignedLecturerId;
        this.assignedResourceId = assignedResourceId;
    }

    // Getters and Setters
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

    public int getDurationHours() {
        return durationHours;
    }

    public void setDurationHours(int durationHours) {
        this.durationHours = durationHours;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public int getNumberOfLectures() {
        return numberOfLectures;
    }

    public void setNumberOfLectures(int numberOfLectures) {
        this.numberOfLectures = numberOfLectures;
    }
    
    public int getNumberOfLabs() {
        return numberOfLabs;
    }

    public void setNumberOfLabs(int numberOfLabs) {
        this.numberOfLabs = numberOfLabs;
    }

    public String getAdminId() {
        return adminId;
    }

    public void setAdminId(String adminId) {
        this.adminId = adminId;
    }

    public String getAssignedLecturerId() {
        return assignedLecturerId;
    }

    public void setAssignedLecturerId(String assignedLecturerId) {
        this.assignedLecturerId = assignedLecturerId;
    }
    
    public String getAssignedResourceId() {
        return assignedResourceId;
    }
    
    public void setAssignedResourceId(String assignedResourceId) {
        this.assignedResourceId = assignedResourceId;
    }
    
    /**
     * Gets the total number of sessions required per week
     * (sum of lectures and labs)
     * 
     * @return Total number of sessions
     */
    public int getTotalSessionsPerWeek() {
        return numberOfLectures + numberOfLabs;
    }
}
