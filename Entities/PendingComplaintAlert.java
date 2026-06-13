package com.smart_complaint_service.project.Entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "pending_complaint_alert")
public class PendingComplaintAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long complaintId;

    private String userEmail;
    private String userName;
    private String description;
    private String serviceType;
    private String location;
    private LocalDateTime raisedAt;
    private boolean adminNotified = false;

    @Column
    private LocalDateTime closedAt;

    @Column
    private boolean userNotifiedOfClosure = false;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getComplaintId() { return complaintId; }
    public void setComplaintId(Long complaintId) { this.complaintId = complaintId; }

    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getServiceType() { return serviceType; }
    public void setServiceType(String serviceType) { this.serviceType = serviceType; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public LocalDateTime getRaisedAt() { return raisedAt; }
    public void setRaisedAt(LocalDateTime raisedAt) { this.raisedAt = raisedAt; }

    public boolean isAdminNotified() { return adminNotified; }
    public void setAdminNotified(boolean adminNotified) { this.adminNotified = adminNotified; }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(LocalDateTime closedAt) {
        this.closedAt = closedAt;
    }

    public boolean isUserNotifiedOfClosure() {
        return userNotifiedOfClosure;
    }

    public void setUserNotifiedOfClosure(boolean userNotifiedOfClosure) {
        this.userNotifiedOfClosure = userNotifiedOfClosure;
    }
}