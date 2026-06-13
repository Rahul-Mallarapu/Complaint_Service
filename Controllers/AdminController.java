package com.smart_complaint_service.project.Controllers;

import com.smart_complaint_service.project.Services.AdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private AdminService adminService;

    @GetMapping("/complaints/active")
    public ResponseEntity<String> getAllActiveComplaints() {
        String result = adminService.getAllActiveComplaints();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/assign/staff/department")
    public ResponseEntity<String> assignStaffToDepartment(
            @RequestParam Long staffId,
            @RequestParam Long deptId) {
        String result = adminService.assignStaffToDepartment(staffId, deptId);
        if (result.startsWith("Invalid") || result.startsWith("Department")) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/complaint/{complaintId}/available-staff")
    public ResponseEntity<String> getAvailableStaffForComplaint(
            @PathVariable Long complaintId) {
        String result = adminService.getAvailableStaffForComplaint(complaintId);
        if (result.startsWith("Complaint not found") ||
                result.startsWith("Complaint has no") ||
                result.startsWith("No available")) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/assign/complaint/staff")
    public ResponseEntity<String> assignComplaintToStaff(
            @RequestParam Long complaintId,
            @RequestParam Long staffId) {
        String result = adminService.assignComplaintToStaff(complaintId, staffId);
        if (result.startsWith("Invalid") || result.startsWith("Complaint not found")
                || result.startsWith("Staff member")) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/close/complaint/{complaintId}")
    public ResponseEntity<String> closeComplaint(@PathVariable Long complaintId) {
        String result = adminService.closeComplaint(complaintId);
        if (result.startsWith("Complaint not found") || result.startsWith("Complaint must")) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/auto-assign/complaint/{complaintId}")
    public ResponseEntity<String> autoAssignComplaint(
            @PathVariable Long complaintId) {
        String result = adminService.autoAssignComplaint(complaintId);
        if (result.startsWith("Complaint not found") ||
                result.startsWith("Complaint is not OPEN") ||
                result.startsWith("Complaint has no") ||
                result.startsWith("Complaint " + complaintId + " is already")) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }
}