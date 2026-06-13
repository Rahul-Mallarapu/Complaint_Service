package com.smart_complaint_service.project.Services;

import com.smart_complaint_service.project.Entities.*;
import com.smart_complaint_service.project.Enums.Role;
import com.smart_complaint_service.project.Repositories.*;
import com.smart_complaint_service.project.Tools.EstimatedTimeResolutionTool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class AdminService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private ComplaintRepository complaintRepository;

    @Autowired
    private ComplaintAssignmentRepository complaintAssignmentRepository;

    @Autowired
    private AdminComplaintTaskRepository adminComplaintTaskRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private EstimatedTimeResolutionTool estimatedTimeResolutionTool;

    @Value("${admin.alert.email}")
    private String adminAlertEmail;

    // ── Helper: estimate resolution time (AI with default fallback) ───
    private String safeEstimateResolutionTime(String description,
                                              String serviceType,
                                              boolean needToActFast) {
        try {
            String response = estimatedTimeResolutionTool.estimateResolutionTime(
                    description, serviceType, needToActFast);

            if (response != null && !response.isBlank()) {
                return response;
            }

            System.out.println("AdminService - AI time estimation returned blank, "
                    + "using default fallback");

        } catch (Exception e) {
            System.out.println("AdminService - AI time estimation failed: "
                    + e.getMessage() + " | using default fallback");
        }

        // Fallback: PHYSICAL = 60 min, ONLINE = 30 min, priority halves the time
        int defaultMinutes = serviceType.equalsIgnoreCase("PHYSICAL") ? 60 : 30;
        if (needToActFast) defaultMinutes = defaultMinutes / 2;
        return "VALID|" + defaultMinutes;
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    public String getAllActiveComplaints() {

        List<Complaint> activeComplaints = complaintRepository
                .findByCurrentStatusIn(List.of("OPEN", "ASSIGNED", "IN_PROGRESS"));

        if (activeComplaints.isEmpty()) {
            return "No active complaints.";
        }

        StringBuilder response = new StringBuilder();
        response.append("Active Complaints (").append(activeComplaints.size()).append("):\n")
                .append("─────────────────────────────────────\n");

        for (Complaint c : activeComplaints) {
            response.append("Complaint ID  : ").append(c.getComplaintId()).append("\n")
                    .append("Description   : ").append(c.getDescription()).append("\n")
                    .append("Service Type  : ").append(c.getServiceType()).append("\n")
                    .append("Location      : ").append(c.getLocation()).append("\n")
                    .append("Status        : ").append(c.getCurrentStatus()).append("\n")
                    .append("Department    : ").append(c.getDept() != null
                            ? c.getDept().getDepartmentName() : "Not assigned").append("\n")
                    .append("User Name     : ").append(c.getUser().getUserName()).append("\n")
                    .append("User Email    : ").append(c.getUser().getEmailId()).append("\n");

            complaintAssignmentRepository
                    .findByCmpComplaintIdAndActiveTrue(c.getComplaintId())
                    .ifPresent(assignment -> {
                        response.append("Assigned To   : ").append(assignment.getAssignedTo().getUserName()).append("\n");
                        if (c.getAssignedAt() != null) {
                            response.append("Assigned At   : ").append(c.getAssignedAt()).append("\n");
                            response.append("Deadline      : ").append(c.getAssignedAt()
                                    .plusMinutes(c.getEstimatedResolutionMinutes())).append("\n");
                        }
                    });

            if (c.getCurrentStatus().equals("OPEN")) {
                response.append("Action        : GET /admin/complaint/")
                        .append(c.getComplaintId()).append("/available-staff\n");
            }

            response.append("─────────────────────────────────────\n");
        }

        return response.toString();
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    public String assignStaffToDepartment(Long staffId, Long deptId) {
        User staff = userRepository.findById(staffId).orElse(null);

        if (staff == null || staff.getRole() != Role.STAFF) {
            return "Invalid staff member";
        }

        Department dept = departmentRepository.findById(deptId).orElse(null);

        if (dept == null) {
            return "Department not found";
        }

        staff.setDept(dept);
        userRepository.save(staff);

        emailService.sendEmail(
                staff.getEmailId(),
                "You Have Been Assigned to a Department",
                "Dear " + staff.getUserName() + ",\n\n" +
                        "You have been assigned to the department: " + dept.getDepartmentName() + "\n\n" +
                        "Please check your pending complaints.\n\n" +
                        "- Smart Complaint Management Team"
        );

        emailService.sendEmail(
                adminAlertEmail,
                "Staff Member Now Available - " + dept.getDepartmentName(),
                "Dear Admin,\n\n" +
                        "Staff member " + staff.getUserName() + " has been successfully " +
                        "assigned to department: " + dept.getDepartmentName() + "\n\n" +
                        "Staff Details:\n" +
                        "Name                       : " + staff.getUserName() + "\n" +
                        "Email                      : " + staff.getEmailId() + "\n" +
                        "Willing To Take Complaints : " + (staff.getWillingToTakeComplaints() ? "Yes" : "No") + "\n\n" +
                        "Please assign pending complaints to this staff member if applicable.\n\n" +
                        "- Smart Complaint Management Team"
        );

        return "Staff " + staff.getUserName() + " assigned to department " + dept.getDepartmentName();
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    public String getAvailableStaffForComplaint(Long complaintId) {
        Complaint complaint = complaintRepository.findById(complaintId).orElse(null);

        if (complaint == null) {
            return "Complaint not found";
        }

        if (complaint.getDept() == null) {
            return "Complaint has no department assigned yet";
        }

        List<User> availableStaff = userRepository
                .findAvailableStaffForDeptOrderedByTieBreakers(
                        complaint.getDept().getId());

        if (availableStaff.isEmpty()) {
            return "No available staff found in department: "
                    + complaint.getDept().getDepartmentName();
        }

        StringBuilder response = new StringBuilder();
        response.append("Complaint ID  : ").append(complaintId).append("\n")
                .append("Department    : ").append(complaint.getDept().getDepartmentName()).append("\n")
                .append("Description   : ").append(complaint.getDescription()).append("\n\n")
                .append("Available Staff (ordered by tie-breakers):\n")
                .append("─────────────────────────────────────\n");

        int rank = 1;
        for (User staff : availableStaff) {
            response.append("Rank #").append(rank++).append("\n")
                    .append("Staff ID                : ").append(staff.getId()).append("\n")
                    .append("Name                    : ").append(staff.getUserName()).append("\n")
                    .append("Email                   : ").append(staff.getEmailId()).append("\n")
                    .append("Phone                   : ").append(staff.getPhoneNumber() != null
                            ? staff.getPhoneNumber() : "Not provided").append("\n")
                    .append("Ranking Score           : ").append(staff.getRankingScore()).append("\n")
                    .append("Total Complaints Handled: ").append(staff.getTotalComplaintsHandled()).append("\n")
                    .append("Active Complaint Count  : ").append(staff.getActiveComplaintCount()).append("\n")
                    .append("─────────────────────────────────────\n");
        }

        response.append("\nTo assign, call:\n")
                .append("POST /admin/assign/complaint/staff?complaintId=")
                .append(complaintId).append("&staffId=<staffId>");

        return response.toString();
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    public String assignComplaintToStaff(Long complaintId, Long staffId) {
        Complaint complaint = complaintRepository.findById(complaintId).orElse(null);

        if (complaint == null) {
            return "Complaint not found";
        }

        User staff = userRepository.findById(staffId).orElse(null);

        if (staff == null || staff.getRole() != Role.STAFF) {
            return "Invalid staff member";
        }

        if (complaintAssignmentRepository.existsByCmpComplaintIdAndActiveTrue(complaintId)) {
            return "Complaint " + complaintId + " is already assigned to a staff member";
        }

        if (!staff.getWillingToTakeComplaints()) {
            return "Staff member " + staff.getUserName() + " is not willing to take complaints currently";
        }

        if (staff.getDept() == null ||
                !staff.getDept().getId().equals(complaint.getDept().getId())) {
            return "Staff member does not belong to the complaint's department: "
                    + complaint.getDept().getDepartmentName();
        }

        ComplaintAssignment assignment = new ComplaintAssignment();
        assignment.setCmp(complaint);
        assignment.setAssignedTo(staff);
        assignment.setAssignedBy(userRepository.findAdmin());
        complaintAssignmentRepository.save(assignment);

        String aiTimeResponse = safeEstimateResolutionTime(
                complaint.getDescription(),
                complaint.getServiceType().name(),
                complaint.isNeedToActFast()
        );

        String[] parts = aiTimeResponse.split("\\|", 2);
        String status = parts[0].trim().toUpperCase();
        String detail = parts.length > 1 ? parts[1].trim() : "";

        if (status.equals("INVALID")) {
            emailService.sendEmail(
                    complaint.getUser().getEmailId(),
                    "Complaint Service Type Mismatch - Action Required",
                    "Dear " + complaint.getUser().getUserName() + ",\n\n" +
                            "Your complaint (ID: " + complaint.getComplaintId() +
                            ") could not be processed.\n\n" +
                            "Reason        : " + detail + "\n" +
                            "Description   : " + complaint.getDescription() + "\n" +
                            "Service Type  : " + complaint.getServiceType() + "\n" +
                            "Location      : " + complaint.getLocation() + "\n\n" +
                            "Please re-register your complaint with the correct service type.\n" +
                            "Hint: " + (complaint.getServiceType().name().equals("PHYSICAL")
                            ? "Try using ONLINE service type instead."
                            : "Try using PHYSICAL service type instead.") + "\n\n" +
                            "We apologize for the inconvenience.\n\n" +
                            "- Smart Complaint Management Team"
            );

            complaint.setCurrentStatus("CLOSED");
            complaintRepository.save(complaint);

            complaintAssignmentRepository
                    .findByCmpComplaintIdAndActiveTrue(complaint.getComplaintId())
                    .ifPresent(a -> {
                        a.setActive(false);
                        complaintAssignmentRepository.save(a);
                    });

            return "Complaint " + complaintId + " has invalid service type and has been closed. " +
                    "User has been notified.";
        }

        int estimatedMinutes;
        try {
            estimatedMinutes = Integer.parseInt(detail.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            estimatedMinutes = complaint.getServiceType().name().equals("PHYSICAL") ? 60 : 30;
        }
        if (estimatedMinutes <= 0) {
            estimatedMinutes = complaint.getServiceType().name().equals("PHYSICAL") ? 60 : 30;
        }

        complaint.setCurrentStatus("ASSIGNED");
        complaint.setEstimatedResolutionMinutes(estimatedMinutes);
        complaint.setAssignedAt(LocalDateTime.now());
        complaintRepository.save(complaint);

        staff.setActiveComplaintCount(staff.getActiveComplaintCount() + 1);
        userRepository.save(staff);

        adminComplaintTaskRepository
                .findByComplaintIdAndResolvedFalse(complaintId)
                .ifPresent(task -> {
                    task.setResolved(true);
                    adminComplaintTaskRepository.save(task);
                });

        emailService.sendEmail(
                complaint.getUser().getEmailId(),
                "Your Complaint Has Been Assigned",
                "Dear " + complaint.getUser().getUserName() + ",\n\n" +
                        "Your complaint (ID: " + complaintId + ") has been assigned to " +
                        "our staff member " + staff.getUserName() + ".\n\n" +
                        "Estimated Resolution Time : " + estimatedMinutes + " minute(s)\n\n" +
                        "They will be in touch with you shortly.\n\n" +
                        "- Smart Complaint Management Team"
        );

        emailService.sendEmail(
                staff.getEmailId(),
                "New Complaint Assigned to You - ID: " + complaintId,
                "Dear " + staff.getUserName() + ",\n\n" +
                        "A new complaint has been assigned to you by Admin.\n\n" +
                        "Complaint Details:\n" +
                        "Complaint ID   : " + complaint.getComplaintId() + "\n" +
                        "Description    : " + complaint.getDescription() + "\n" +
                        "Service Type   : " + complaint.getServiceType().name() + "\n" +
                        "Location       : " + complaint.getLocation() + "\n" +
                        "Priority       : " + (complaint.isNeedToActFast() ? "YES ⚡" : "NO") + "\n\n" +
                        "User Details:\n" +
                        "Name           : " + complaint.getUser().getUserName() + "\n" +
                        "Email          : " + complaint.getUser().getEmailId() + "\n\n" +
                        "Resolution Details:\n" +
                        "Assigned At    : " + complaint.getAssignedAt() + "\n" +
                        "Estimated Time : " + estimatedMinutes + " minute(s)\n" +
                        "Deadline       : " + complaint.getAssignedAt()
                        .plusMinutes(estimatedMinutes) + "\n\n" +
                        "Please resolve before the deadline to maintain your ranking.\n\n" +
                        "Once resolved, notify admin via:\n" +
                        "POST /staff/complaint/resolve/" + complaint.getComplaintId() + "\n\n" +
                        "- Smart Complaint Management Team"
        );

        return "Complaint " + complaintId + " successfully assigned to " + staff.getUserName()
                + ". Estimated resolution: " + estimatedMinutes + " minutes.";
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    public String autoAssignComplaint(Long complaintId) {

        Complaint complaint = complaintRepository.findById(complaintId).orElse(null);
        if (complaint == null) return "Complaint not found";

        if (!complaint.getCurrentStatus().equals("OPEN")) {
            return "Complaint is not OPEN. Current status: " + complaint.getCurrentStatus();
        }

        if (complaint.getDept() == null) {
            return "Complaint has no department assigned yet. Cannot auto-assign.";
        }

        if (complaintAssignmentRepository.existsByCmpComplaintIdAndActiveTrue(complaintId)) {
            return "Complaint " + complaintId + " is already assigned to a staff member";
        }

        List<User> candidates = userRepository
                .findAvailableStaffForDeptOrderedByTieBreakers(complaint.getDept().getId());

        if (candidates.isEmpty()) {
            boolean taskExists = adminComplaintTaskRepository
                    .findByComplaintIdAndResolvedFalse(complaintId).isPresent();

            if (!taskExists) {
                AdminComplaintTask task = new AdminComplaintTask();
                task.setComplaintId(complaintId);
                task.setUserEmail(complaint.getUser().getEmailId());
                task.setUserName(complaint.getUser().getUserName());
                task.setAssignedToAdminAt(LocalDateTime.now());
                task.setResolved(false);
                task.setUserNotifiedOfFailure(false);
                adminComplaintTaskRepository.save(task);
            }

            emailService.sendEmail(
                    adminAlertEmail,
                    "Auto-Assign Failed - No Staff Available for Complaint ID: " + complaintId,
                    "Dear Admin,\n\n" +
                            "Auto-assignment could not be completed as no available " +
                            "staff members were found in the department.\n\n" +
                            "Complaint Details:\n" +
                            "Complaint ID  : " + complaintId + "\n" +
                            "Description   : " + complaint.getDescription() + "\n" +
                            "Service Type  : " + complaint.getServiceType() + "\n" +
                            "Department    : " + complaint.getDept().getDepartmentName() + "\n\n" +
                            "User Details:\n" +
                            "Name          : " + complaint.getUser().getUserName() + "\n" +
                            "Email         : " + complaint.getUser().getEmailId() + "\n\n" +
                            "Please assign manually via:\n" +
                            "POST /admin/assign/complaint/staff?complaintId=" + complaintId
                            + "&staffId=<staffId>\n\n" +
                            "- Smart Complaint Management Team"
            );

            return "No available staff in department: " + complaint.getDept().getDepartmentName()
                    + ". Admin has been notified to assign manually.";
        }

        User best = candidates.get(0);

        User selectedStaff = userRepository.findRandomTiedStaffForDept(
                complaint.getDept().getId(),
                best.getRankingScore(),
                best.getTotalComplaintsHandled(),
                best.getActiveComplaintCount()
        ).orElse(best);

        ComplaintAssignment assignment = new ComplaintAssignment();
        assignment.setCmp(complaint);
        assignment.setAssignedTo(selectedStaff);
        assignment.setAssignedBy(userRepository.findAdmin());
        assignment.setActive(true);
        complaintAssignmentRepository.save(assignment);

        String aiTimeResponse = safeEstimateResolutionTime(
                complaint.getDescription(),
                complaint.getServiceType().name(),
                complaint.isNeedToActFast()
        );

        String[] parts = aiTimeResponse.split("\\|", 2);
        String status = parts[0].trim().toUpperCase();
        String detail = parts.length > 1 ? parts[1].trim() : "";

        if (status.equals("INVALID")) {
            emailService.sendEmail(
                    complaint.getUser().getEmailId(),
                    "Complaint Service Type Mismatch - Action Required",
                    "Dear " + complaint.getUser().getUserName() + ",\n\n" +
                            "Your complaint (ID: " + complaintId +
                            ") could not be processed.\n\n" +
                            "Reason        : " + detail + "\n" +
                            "Description   : " + complaint.getDescription() + "\n" +
                            "Service Type  : " + complaint.getServiceType() + "\n" +
                            "Location      : " + complaint.getLocation() + "\n\n" +
                            "Please re-register your complaint with the correct service type.\n\n" +
                            "- Smart Complaint Management Team"
            );

            complaint.setCurrentStatus("CLOSED");
            complaintRepository.save(complaint);

            complaintAssignmentRepository
                    .findByCmpComplaintIdAndActiveTrue(complaintId)
                    .ifPresent(a -> {
                        a.setActive(false);
                        complaintAssignmentRepository.save(a);
                    });

            return "Complaint " + complaintId + " has invalid service type and has been closed. "
                    + "User has been notified.";
        }

        int estimatedMinutes;
        try {
            estimatedMinutes = Integer.parseInt(detail.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            estimatedMinutes = complaint.getServiceType().name().equals("PHYSICAL") ? 60 : 30;
        }
        if (estimatedMinutes <= 0) {
            estimatedMinutes = complaint.getServiceType().name().equals("PHYSICAL") ? 60 : 30;
        }

        complaint.setCurrentStatus("ASSIGNED");
        complaint.setEstimatedResolutionMinutes(estimatedMinutes);
        complaint.setAssignedAt(LocalDateTime.now());
        complaintRepository.save(complaint);

        selectedStaff.setActiveComplaintCount(selectedStaff.getActiveComplaintCount() + 1);
        userRepository.save(selectedStaff);

        adminComplaintTaskRepository
                .findByComplaintIdAndResolvedFalse(complaintId)
                .ifPresent(task -> {
                    task.setResolved(true);
                    adminComplaintTaskRepository.save(task);
                });

        emailService.sendEmail(
                complaint.getUser().getEmailId(),
                "Your Complaint Has Been Assigned",
                "Dear " + complaint.getUser().getUserName() + ",\n\n" +
                        "Your complaint (ID: " + complaintId + ") has been auto-assigned to " +
                        "our staff member " + selectedStaff.getUserName() + ".\n\n" +
                        "Complaint Details:\n" +
                        "Complaint ID   : " + complaintId + "\n" +
                        "Description    : " + complaint.getDescription() + "\n" +
                        "Service Type   : " + complaint.getServiceType().name() + "\n" +
                        "Location       : " + complaint.getLocation() + "\n\n" +
                        "Assigned Staff Details:\n" +
                        "Name           : " + selectedStaff.getUserName() + "\n" +
                        "Phone Number   : " + (selectedStaff.getPhoneNumber() != null
                        ? "tel:" + selectedStaff.getPhoneNumber() : "Not provided") + "\n" +
                        "Department     : " + complaint.getDept().getDepartmentName() + "\n\n" +
                        "Estimated Resolution Time : " + estimatedMinutes + " minute(s)\n" +
                        "Complaint Status          : ASSIGNED\n\n" +
                        "- Smart Complaint Management Team"
        );

        emailService.sendEmail(
                selectedStaff.getEmailId(),
                "New Complaint Auto-Assigned to You - ID: " + complaintId,
                "Dear " + selectedStaff.getUserName() + ",\n\n" +
                        "A new complaint has been auto-assigned to you by Admin.\n\n" +
                        "Complaint Details:\n" +
                        "Complaint ID   : " + complaint.getComplaintId() + "\n" +
                        "Description    : " + complaint.getDescription() + "\n" +
                        "Service Type   : " + complaint.getServiceType().name() + "\n" +
                        "Location       : " + complaint.getLocation() + "\n" +
                        "Priority       : " + (complaint.isNeedToActFast() ? "YES ⚡" : "NO") + "\n\n" +
                        "User Details:\n" +
                        "Name           : " + complaint.getUser().getUserName() + "\n" +
                        "Email          : " + complaint.getUser().getEmailId() + "\n\n" +
                        "Resolution Details:\n" +
                        "Assigned At    : " + complaint.getAssignedAt() + "\n" +
                        "Estimated Time : " + estimatedMinutes + " minute(s)\n" +
                        "Deadline       : " + complaint.getAssignedAt()
                        .plusMinutes(estimatedMinutes) + "\n\n" +
                        "Please resolve before the deadline to maintain your ranking.\n\n" +
                        "Once resolved, notify admin via:\n" +
                        "POST /staff/complaint/resolve/" + complaintId + "\n\n" +
                        "- Smart Complaint Management Team"
        );

        emailService.sendEmail(
                adminAlertEmail,
                "Complaint Auto-Assigned - ID: " + complaintId,
                "Dear Admin,\n\n" +
                        "Complaint ID " + complaintId + " has been auto-assigned.\n\n" +
                        "Complaint Details:\n" +
                        "Complaint ID   : " + complaintId + "\n" +
                        "Description    : " + complaint.getDescription() + "\n" +
                        "Service Type   : " + complaint.getServiceType().name() + "\n" +
                        "Department     : " + complaint.getDept().getDepartmentName() + "\n\n" +
                        "Assigned To:\n" +
                        "Staff Name     : " + selectedStaff.getUserName() + "\n" +
                        "Ranking Score  : " + selectedStaff.getRankingScore() + "\n" +
                        "Total Handled  : " + selectedStaff.getTotalComplaintsHandled() + "\n" +
                        "Active Count   : " + selectedStaff.getActiveComplaintCount() + "\n" +
                        "Est. Resolution: " + estimatedMinutes + " minutes\n\n" +
                        "- Smart Complaint Management Team"
        );

        return "Complaint " + complaintId + " auto-assigned to " + selectedStaff.getUserName()
                + ". Estimated resolution: " + estimatedMinutes + " minutes.";
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    public String closeComplaint(Long complaintId) {
        Complaint complaint = complaintRepository.findById(complaintId).orElse(null);
        if (complaint == null) return "Complaint not found";

        if (!complaint.getCurrentStatus().equals("PENDING_ADMIN_VERIFICATION")) {
            return "Complaint must be in PENDING_ADMIN_VERIFICATION status. Current: "
                    + complaint.getCurrentStatus();
        }

        LocalDateTime now = LocalDateTime.now();
        long actualMinutes = ChronoUnit.MINUTES
                .between(complaint.getAssignedAt(), complaint.getResolvedAt());

        complaint.setResolvedAt(now);
        complaint.setActualTimeTaken((int) actualMinutes);
        complaint.setCurrentStatus("CLOSED");
        complaintRepository.save(complaint);

        complaintAssignmentRepository
                .findByCmpComplaintIdAndActiveTrue(complaintId)
                .ifPresent(assignment -> {

                    boolean resolvedWithinTime = actualMinutes
                            <= complaint.getEstimatedResolutionMinutes();
                    assignment.setResolvedWithinTime(resolvedWithinTime);
                    complaintAssignmentRepository.save(assignment);

                    User staff = assignment.getAssignedTo();

                    staff.setTotalComplaintsHandled(
                            staff.getTotalComplaintsHandled() + 1);

                    if (resolvedWithinTime) {
                        if (staff.getTotalComplaintsHandled() > 1) {
                            int newRank = Math.max(1, staff.getRankingScore() - 1);
                            staff.setRankingScore(newRank);
                        }
                    }

                    staff.setActiveComplaintCount(
                            Math.max(0, staff.getActiveComplaintCount() - 1));
                    userRepository.save(staff);

                    String phoneLink = staff.getPhoneNumber() != null
                            ? "tel:" + staff.getPhoneNumber() : "Not provided";

                    emailService.sendEmail(
                            complaint.getUser().getEmailId(),
                            "Your Complaint Has Been Resolved",
                            "Dear " + complaint.getUser().getUserName() + ",\n\n" +
                                    "Your complaint has been successfully resolved.\n\n" +
                                    "Complaint Details:\n" +
                                    "Complaint ID  : " + complaint.getComplaintId() + "\n" +
                                    "Description   : " + complaint.getDescription() + "\n" +
                                    "Service Type  : " + complaint.getServiceType() + "\n" +
                                    "Location      : " + complaint.getLocation() + "\n" +
                                    "Status        : CLOSED\n\n" +
                                    "Resolved By:\n" +
                                    "Staff Name    : " + staff.getUserName() + "\n" +
                                    "Department    : " + (staff.getDept() != null
                                    ? staff.getDept().getDepartmentName() : "N/A") + "\n" +
                                    "Contact       : " + phoneLink + "\n\n" +
                                    "For any queries, please feel free to reach out.\n\n" +
                                    "- Smart Complaint Management Team"
                    );
                });

        return "Complaint " + complaintId + " has been successfully closed.";
    }
}