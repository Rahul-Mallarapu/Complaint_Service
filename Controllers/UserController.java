package com.smart_complaint_service.project.Controllers;

import com.smart_complaint_service.project.Entities.Complaint;
import com.smart_complaint_service.project.Services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;

@RequestMapping("/user")
@RestController
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping("/register/complaint")
    public ResponseEntity<String> register_complaint(@RequestBody Complaint complaint) {
        String result = userService.register_complaint(complaint);
        if (result.startsWith("Admin not present")) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(result);
        } else if (result.startsWith("Invalid") ||
                result.startsWith("User not found")) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/cancel/complaint/{complaintId}")
    public ResponseEntity<String> cancelComplaint(
            @PathVariable Long complaintId,
            @RequestParam String reason) {
        String result = userService.cancelComplaint(complaintId, reason);
        if (result.startsWith("Complaint not found") ||
                result.startsWith("Invalid reason") ||
                result.startsWith("This complaint") ||
                result.startsWith("Complaint is already")) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }
}