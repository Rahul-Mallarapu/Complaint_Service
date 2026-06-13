package com.smart_complaint_service.project.Entities;

import jakarta.persistence.*;
import java.util.List;

@Entity
@Table(name = "departments")
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "department_name", nullable = false, unique = true, length = 100)
    private String departmentName;

    @Column(length = 255)
    private String description;

    @OneToMany(mappedBy = "dept")
    private List<User> staffs;

    @OneToMany(mappedBy = "dept")
    private List<Complaint> complaints;

    public Department()
    {

    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<User> getStaffs() { return staffs; }
    public void setStaffs(List<User> staffs) { this.staffs = staffs; }

    public List<Complaint> getComplaints() { return complaints; }
    public void setComplaints(List<Complaint> complaints) { this.complaints = complaints; }
}
