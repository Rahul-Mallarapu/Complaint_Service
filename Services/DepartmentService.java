package com.smart_complaint_service.project.Services;

import com.smart_complaint_service.project.Repositories.DepartmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DepartmentService {

    @Autowired
    private DepartmentRepository departmentRepository;

    public List<String> findAllDepartments() {
        return departmentRepository.findAllDepartments();
    }
}