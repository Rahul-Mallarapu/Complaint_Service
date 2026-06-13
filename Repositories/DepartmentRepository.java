package com.smart_complaint_service.project.Repositories;

import com.smart_complaint_service.project.Entities.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    @Query(value = "SELECT d.department_name FROM departments d", nativeQuery = true)
    List<String> findAllDepartments();

    @Query(value = "SELECT * FROM departments d WHERE d.department_name = :deptName", nativeQuery = true)
    Optional<Department> findByDepartmentName(@Param("deptName") String departmentName);
}
