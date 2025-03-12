package com.example.manager.model;

public class User {
    private String id;
    private String name;
    private String email;
    private String role;
    private String contact;
    private String idPhoto;
    private String contract;
    private String status;

    public User() {
    }

    public User(String id, String name, String email,String contact, String role, String idPhoto, String contract, String status) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.contact=contact;
        this.role = role;
        this.idPhoto = idPhoto;
        this.contract = contract;
        this.status = status;
    }

    public String getContact() {
        return contact;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getIdPhoto() {
        return idPhoto;
    }

    public void setIdPhoto(String idPhoto) {
        this.idPhoto = idPhoto;
    }

    public String getContract() {
        return contract;
    }

    public void setContract(String contract) {
        this.contract = contract;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
