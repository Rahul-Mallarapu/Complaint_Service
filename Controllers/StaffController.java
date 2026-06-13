package com.smart_complaint_service.project.Controllers;

import com.smart_complaint_service.project.Services.StaffService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/staff")
public class StaffController {

    @Autowired
    private StaffService staffService;

    @PostMapping("/complaint/resolve/{complaintId}")
    public ResponseEntity<String> resolveComplaint(
            @PathVariable Long complaintId,
            @RequestParam Long staffId) {
        String result = staffService.resolveComplaint(complaintId, staffId);
        if (result.startsWith("Complaint not found") ||
                result.startsWith("No active") ||
                result.startsWith("This complaint") ||
                result.startsWith("Complaint is not") ||
                result.startsWith("Staff not found")) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }
}