package com.example.manager.model;

public class Lecturer {
    private final String id;
    private final String name;
    private final String contact;
    private final int proximityScore;

    public Lecturer(String id, String name,String contact, int proximityScore) {
        this.id = id;
        this.name = name;
        this.contact=contact;
        this.proximityScore = proximityScore;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getContact() {
        return contact;
    }

    public int getProximityScore() {
        return proximityScore;
    }

}
