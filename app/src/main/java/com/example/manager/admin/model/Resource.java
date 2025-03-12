package com.example.manager.admin.model;

public class Resource {
    private String id;
    private String name;
    private String type;
    private String capacity;
    private String adminId;
    private String location;
    private String isAvailable;

    public Resource() {
    }

    public Resource(String id, String name, String type, String capacity, String adminId,String location, String isAvailable) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.capacity = capacity;
        this.adminId = adminId;
        this.location=location;
        this.isAvailable = isAvailable;
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }



    public String getAdminId() {
        return adminId;
    }

    public void setAdminId(String adminId) {
        this.adminId = adminId;
    }

    public String getCapacity() {
        return capacity;
    }

    public void setCapacity(String capacity) {
        this.capacity = capacity;
    }

    public String getIsAvailable() {
        return isAvailable;
    }

    public void setIsAvailable(String isAvailable) {
        this.isAvailable = isAvailable;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }
}

