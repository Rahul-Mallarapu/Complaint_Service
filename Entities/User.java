package com.smart_complaint_service.project.Entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.smart_complaint_service.project.Enums.Role;
import jakarta.persistence.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@Entity
@Table(name = "users",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"emailId", "password"})})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_name", nullable = false, unique = true, length = 50)
    private String userName;

    @Column(name = "email_id", nullable = false, unique = true, length = 100)
    private String emailId;

    @Column(nullable = false, unique = true, length = 100)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(length = 15)
    private String phoneNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dept_id")
    private Department dept;

    @Column
    private Boolean available = true;

    @Column
    private Boolean willingToTakeComplaints = false;

    @Column
    private Integer activeComplaintCount = 0;

    @Column
    private Integer rankingScore = 1;

    @Column
    private Integer totalComplaintsHandled = 0;

    @Column(length = 100)
    private String location;

    @OneToMany(mappedBy = "assignedTo")
    private List<ComplaintAssignment> complaintsAssigned;

    @OneToMany(mappedBy = "user")
    @JsonIgnore
    private List<Complaint> complaints;

    @OneToMany(mappedBy = "assignedBy")
    private List<ComplaintAssignment> assignmentsCreated;

    private List<SimpleGrantedAuthority> simpleGrantedAuthorities;

    public User() {}

    public User(String email, String password,
                List<SimpleGrantedAuthority> simpleGrantedAuthorities) {
        this.emailId = email;
        this.password = password;
        this.simpleGrantedAuthorities = simpleGrantedAuthorities;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getEmailId() { return emailId; }
    public void setEmailId(String emailId) { this.emailId = emailId; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public Department getDept() { return dept; }
    public void setDept(Department dept) { this.dept = dept; }

    public Boolean getAvailable() { return available; }
    public void setAvailable(Boolean available) { this.available = available; }

    public Boolean getWillingToTakeComplaints() { return willingToTakeComplaints; }
    public void setWillingToTakeComplaints(Boolean willingToTakeComplaints) {
        this.willingToTakeComplaints = willingToTakeComplaints;
    }

    public Integer getActiveComplaintCount() { return activeComplaintCount; }
    public void setActiveComplaintCount(Integer activeComplaintCount) {
        this.activeComplaintCount = activeComplaintCount;
    }

    public Integer getRankingScore() { return rankingScore; }
    public void setRankingScore(Integer rankingScore) { this.rankingScore = rankingScore; }

    public Integer getTotalComplaintsHandled() { return totalComplaintsHandled; }
    public void setTotalComplaintsHandled(Integer totalComplaintsHandled) {
        this.totalComplaintsHandled = totalComplaintsHandled;
    }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public List<ComplaintAssignment> getComplaintsAssigned() { return complaintsAssigned; }
    public void setComplaintsAssigned(List<ComplaintAssignment> complaintsAssigned) {
        this.complaintsAssigned = complaintsAssigned;
    }

    public List<Complaint> getComplaints() { return complaints; }
    public void setComplaints(List<Complaint> complaints) { this.complaints = complaints; }

    public List<ComplaintAssignment> getAssignmentsCreated() { return assignmentsCreated; }
    public void setAssignmentsCreated(List<ComplaintAssignment> assignmentsCreated) {
        this.assignmentsCreated = assignmentsCreated;
    }
}