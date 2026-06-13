package com.smart_complaint_service.project.Entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Table(name = "complaint_assignment")
@Entity
public class ComplaintAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "complaint_id", nullable = false)
    private Complaint cmp;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id", nullable = true)
    private User assignedTo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by_user_id", nullable = false)
    private User assignedBy;

    @Column(nullable = false)
    private int priorityRank = 0;

    @Column(nullable = false)
    private boolean active = true;

    @Column(length = 255)
    private String remarks;

    @Column(nullable = false)
    private LocalDateTime assignedAt = LocalDateTime.now();

    @Column
    private Boolean resolvedWithinTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Complaint getCmp() { return cmp; }
    public void setCmp(Complaint cmp) { this.cmp = cmp; }

    public User getAssignedTo() { return assignedTo; }
    public void setAssignedTo(User assignedTo) { this.assignedTo = assignedTo; }

    public User getAssignedBy() { return assignedBy; }
    public void setAssignedBy(User assignedBy) { this.assignedBy = assignedBy; }

    public int getPriorityRank() { return priorityRank; }
    public void setPriorityRank(int priorityRank) { this.priorityRank = priorityRank; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }

    public LocalDateTime getAssignedAt() { return assignedAt; }
    public void setAssignedAt(LocalDateTime assignedAt) { this.assignedAt = assignedAt; }

    public Boolean getResolvedWithinTime() { return resolvedWithinTime; }
    public void setResolvedWithinTime(Boolean resolvedWithinTime) {
        this.resolvedWithinTime = resolvedWithinTime;
    }
}