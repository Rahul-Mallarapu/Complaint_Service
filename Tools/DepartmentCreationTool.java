package com.smart_complaint_service.project.Tools;

import com.smart_complaint_service.project.Entities.Department;
import com.smart_complaint_service.project.Repositories.DepartmentRepository;
import com.smart_complaint_service.project.Services.DepartmentService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DepartmentCreationTool {

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private DepartmentService departmentService;

    @Tool(description = "Creates a new department to handle the user's complaint category. "
            + "Use this when a complaint doesn't match any existing department.")
    public String create_department(
            @ToolParam(description = "Name of the new department, e.g. 'Billing Issues'")
            String departmentName,
            @ToolParam(description = "Short description of what complaints it handles")
            String description
    )
    {
        Department dept = new Department();
        dept.setDepartmentName(departmentName);
        dept.setDescription(description);
        departmentRepository.save(dept);
        return "Department created successfully";
    }

    @Tool(description = "Lists all existing departments so the AI can check "
            + "before creating a duplicate.")
    public List<String> listDepartments() {
        return departmentService.findAllDepartments();
    }

}
