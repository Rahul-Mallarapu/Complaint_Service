package com.smart_complaint_service.project.Entities;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.smart_complaint_service.project.Enums.ServiceType;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.List;

@Table(name = "complaint")
@Entity
public class Complaint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long complaintId;

    @Column(nullable = false, length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ServiceType serviceType;

    @Column(nullable = false, length = 30)
    private String currentStatus = "OPEN";

    @Column(nullable = false)
    private boolean needToActFast = false;

    @Column(length = 100)
    private String location;

    @Column
    private Integer estimatedResolutionMinutes;

    @Column
    private LocalDateTime assignedAt;

    @Column
    private LocalDateTime resolvedAt;

    @Column
    private Integer actualTimeTaken;

    @Column
    private boolean staffReminderSent = false;

    @Column(columnDefinition = "boolean default false")
    private boolean staffStartEmailSent = false;

    @Column
    private Integer lastPenaltyBucket = 0;

    // ── Specific issue handling ───────────────────────────────────────
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean specificIssue = false;

    @Column(length = 500)
    private String specificIssueDescription;

    // ── Cancellation handling ─────────────────────────────────────────
    @Column(length = 20)
    private String cancellationReason;         // BREACH or MISTAKE

    @Column
    private LocalDateTime cancelledAt;

    @OneToMany(mappedBy = "cmp", cascade = CascadeType.ALL)
    private List<ComplaintAssignment> complaintsAssignments;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "complaints"})
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dept_id", nullable = true)
    private Department dept;

    @OneToMany(mappedBy = "complaint", cascade = CascadeType.ALL)
    private List<ComplaintStatusHistory> complaintsStatusHistory;

    public Complaint() {}

    public Long getComplaintId() { return complaintId; }
    public void setComplaintId(Long complaintId) { this.complaintId = complaintId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public ServiceType getServiceType() { return serviceType; }
    public void setServiceType(ServiceType serviceType) { this.serviceType = serviceType; }

    public String getCurrentStatus() { return currentStatus; }
    public void setCurrentStatus(String currentStatus) { this.currentStatus = currentStatus; }

    public boolean isNeedToActFast() { return needToActFast; }
    public void setNeedToActFast(boolean needToActFast) { this.needToActFast = needToActFast; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public Integer getEstimatedResolutionMinutes() { return estimatedResolutionMinutes; }
    public void setEstimatedResolutionMinutes(Integer estimatedResolutionMinutes) {
        this.estimatedResolutionMinutes = estimatedResolutionMinutes;
    }

    public LocalDateTime getAssignedAt() { return assignedAt; }
    public void setAssignedAt(LocalDateTime assignedAt) { this.assignedAt = assignedAt; }

    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }

    public Integer getActualTimeTaken() { return actualTimeTaken; }
    public void setActualTimeTaken(Integer actualTimeTaken) {
        this.actualTimeTaken = actualTimeTaken;
    }

    public boolean isStaffReminderSent() { return staffReminderSent; }
    public void setStaffReminderSent(boolean staffReminderSent) {
        this.staffReminderSent = staffReminderSent;
    }

    public boolean isStaffStartEmailSent() { return staffStartEmailSent; }
    public void setStaffStartEmailSent(boolean staffStartEmailSent) {
        this.staffStartEmailSent = staffStartEmailSent;
    }

    public Integer getLastPenaltyBucket() { return lastPenaltyBucket; }
    public void setLastPenaltyBucket(Integer lastPenaltyBucket) {
        this.lastPenaltyBucket = lastPenaltyBucket;
    }

    public boolean isSpecificIssue() { return specificIssue; }
    public void setSpecificIssue(boolean specificIssue) {
        this.specificIssue = specificIssue;
    }

    public String getSpecificIssueDescription() { return specificIssueDescription; }
    public void setSpecificIssueDescription(String specificIssueDescription) {
        this.specificIssueDescription = specificIssueDescription;
    }

    public String getCancellationReason() { return cancellationReason; }
    public void setCancellationReason(String cancellationReason) {
        this.cancellationReason = cancellationReason;
    }

    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public List<ComplaintAssignment> getComplaintsAssignments() { return complaintsAssignments; }
    public void setComplaintsAssignments(List<ComplaintAssignment> complaintsAssignments) {
        this.complaintsAssignments = complaintsAssignments;
    }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Department getDept() { return dept; }
    public void setDept(Department dept) { this.dept = dept; }

    public List<ComplaintStatusHistory> getComplaintsStatusHistory() { return complaintsStatusHistory; }
    public void setComplaintsStatusHistory(List<ComplaintStatusHistory> complaintsStatusHistory) {
        this.complaintsStatusHistory = complaintsStatusHistory;
    }
}