package com.example.manager.timetable;

/**
 * Configuration options for timetable generation.
 * This class holds various constraints and preferences that affect
 * how the timetable is generated.
 */
public class TimetableGeneratorOptions {
    private boolean avoidBackToBackClasses;
    private boolean preferEvenDistribution;
    private int maxHoursPerDay;
    private ResourceFilter filter; // Added resource filter field
    
    /**
     * Creates a default set of timetable generator options
     */
    public TimetableGeneratorOptions() {
        this.avoidBackToBackClasses = false;
        this.preferEvenDistribution = false;
        this.maxHoursPerDay = 6; // Default max hours
        this.filter = null; // Default no filter
    }
    
    /**
     * Creates timetable generator options with specified constraints
     * 
     * @param avoidBackToBackClasses Whether to avoid scheduling back-to-back classes for lecturers
     * @param preferEvenDistribution Whether to prefer evenly distributing classes across the week
     * @param maxHoursPerDay Maximum teaching hours per day for lecturers
     */
    public TimetableGeneratorOptions(boolean avoidBackToBackClasses, boolean preferEvenDistribution, int maxHoursPerDay) {
        this.avoidBackToBackClasses = avoidBackToBackClasses;
        this.preferEvenDistribution = preferEvenDistribution;
        this.maxHoursPerDay = maxHoursPerDay;
        this.filter = null; // Default no filter
    }
    
    /**
     * Determines whether back-to-back classes for lecturers should be avoided.
     * 
     * @return true if back-to-back classes should be avoided, false otherwise
     */
    public boolean shouldAvoidBackToBackClasses() {
        return avoidBackToBackClasses;
    }
    
    /**
     * Sets whether back-to-back classes for lecturers should be avoided.
     * 
     * @param avoidBackToBackClasses true to avoid back-to-back classes, false otherwise
     */
    public void setAvoidBackToBackClasses(boolean avoidBackToBackClasses) {
        this.avoidBackToBackClasses = avoidBackToBackClasses;
    }
    
    /**
     * Determines whether classes should be evenly distributed across the week.
     * 
     * @return true if even distribution is preferred, false otherwise
     */
    public boolean shouldPreferEvenDistribution() {
        return preferEvenDistribution;
    }
    
    /**
     * Sets whether classes should be evenly distributed across the week.
     * 
     * @param preferEvenDistribution true for even distribution, false otherwise
     */
    public void setPreferEvenDistribution(boolean preferEvenDistribution) {
        this.preferEvenDistribution = preferEvenDistribution;
    }
    
    /**
     * Gets the maximum teaching hours per day for lecturers.
     * 
     * @return Maximum teaching hours per day
     */
    public int getMaxHoursPerDay() {
        return maxHoursPerDay;
    }
    
    /**
     * Sets the maximum teaching hours per day for lecturers.
     * 
     * @param maxHoursPerDay Maximum teaching hours per day
     */
    public void setMaxHoursPerDay(int maxHoursPerDay) {
        this.maxHoursPerDay = maxHoursPerDay;
    }
    
    /**
     * Gets the resource filter for this timetable generation.
     * 
     * @return The current resource filter, or null if no filter is set
     */
    public ResourceFilter getFilter() {
        return filter;
    }
    
    /**
     * Sets a resource filter for timetable generation.
     * 
     * @param filter The resource filter to apply
     */
    public void setFilter(ResourceFilter filter) {
        this.filter = filter;
    }
}
