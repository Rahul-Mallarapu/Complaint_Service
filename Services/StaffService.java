package com.smart_complaint_service.project.Services;

import com.smart_complaint_service.project.Entities.Complaint;
import com.smart_complaint_service.project.Entities.ComplaintAssignment;
import com.smart_complaint_service.project.Entities.User;
import com.smart_complaint_service.project.Enums.Role;
import com.smart_complaint_service.project.Repositories.ComplaintAssignmentRepository;
import com.smart_complaint_service.project.Repositories.ComplaintRepository;
import com.smart_complaint_service.project.Repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class StaffService {

    @Autowired private ComplaintRepository complaintRepository;
    @Autowired private ComplaintAssignmentRepository complaintAssignmentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EmailService emailService;

    @Value("${admin.alert.email}")
    private String adminAlertEmail;

    public String resolveComplaint(Long complaintId, Long staffId) {

        User staff = userRepository.findById(staffId).orElse(null);
        if (staff == null || staff.getRole() != Role.STAFF) {
            return "Staff not found";
        }

        Complaint complaint = complaintRepository.findById(complaintId)
                .orElse(null);
        if (complaint == null) return "Complaint not found";

        ComplaintAssignment assignment = complaintAssignmentRepository
                .findByCmpComplaintIdAndActiveTrue(complaintId)
                .orElse(null);

        if (assignment == null) return "No active assignment found for this complaint";

        if (!assignment.getAssignedTo().getId().equals(staff.getId())) {
            return "This complaint is not assigned to you";
        }

        if (!complaint.getCurrentStatus().equals("IN_PROGRESS")) {
            return "Complaint is not in IN_PROGRESS status. Current: "
                    + complaint.getCurrentStatus();
        }

        complaint.setCurrentStatus("PENDING_ADMIN_VERIFICATION");
        complaint.setResolvedAt(LocalDateTime.now());
        complaintRepository.save(complaint);

        emailService.sendEmail(
                adminAlertEmail,
                "Staff Resolved Complaint - Verification Required",
                "Dear Admin,\n\n" +
                        "Staff member " + staff.getUserName() +
                        " has marked complaint as resolved.\n\n" +
                        "Complaint Details:\n" +
                        "Complaint ID  : " + complaint.getComplaintId() + "\n" +
                        "Description   : " + complaint.getDescription() + "\n" +
                        "Service Type  : " + complaint.getServiceType() + "\n" +
                        "Location      : " + complaint.getLocation() + "\n\n" +
                        "Staff Details:\n" +
                        "Name          : " + staff.getUserName() + "\n" +
                        "Email         : " + staff.getEmailId() + "\n\n" +
                        "Status        : PENDING_ADMIN_VERIFICATION\n\n" +
                        "Please verify and close via:\n" +
                        "POST /admin/close/complaint/" + complaintId + "\n\n" +
                        "- Smart Complaint Management Team"
        );

        return "Complaint marked as resolved. Awaiting admin verification.";
    }
}