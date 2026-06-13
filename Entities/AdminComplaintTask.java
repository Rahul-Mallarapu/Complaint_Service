package com.smart_complaint_service.project.Entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "admin_complaint_task")
public class AdminComplaintTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long complaintId;

    @Column(nullable = false)
    private String userEmail;

    @Column(nullable = false)
    private String userName;

    @Column(nullable = false)
    private LocalDateTime assignedToAdminAt;

    @Column(nullable = false)
    private boolean resolved = false;

    @Column(nullable = false)
    private boolean userNotifiedOfFailure = false;

    @Column(nullable = true)
    private LocalDateTime failureNotifiedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getComplaintId() { return complaintId; }
    public void setComplaintId(Long complaintId) { this.complaintId = complaintId; }

    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public LocalDateTime getAssignedToAdminAt() { return assignedToAdminAt; }
    public void setAssignedToAdminAt(LocalDateTime assignedToAdminAt) { this.assignedToAdminAt = assignedToAdminAt; }

    public boolean isResolved() { return resolved; }
    public void setResolved(boolean resolved) { this.resolved = resolved; }

    public boolean isUserNotifiedOfFailure() { return userNotifiedOfFailure; }
    public void setUserNotifiedOfFailure(boolean userNotifiedOfFailure) { this.userNotifiedOfFailure = userNotifiedOfFailure; }

    public LocalDateTime getFailureNotifiedAt() { return failureNotifiedAt; }
    public void setFailureNotifiedAt(LocalDateTime failureNotifiedAt) { this.failureNotifiedAt = failureNotifiedAt; }
}