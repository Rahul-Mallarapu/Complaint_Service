package com.smart_complaint_service.project.Services;

import com.smart_complaint_service.project.Entities.Complaint;
import com.smart_complaint_service.project.Entities.ComplaintAssignment;
import com.smart_complaint_service.project.Entities.Department;
import com.smart_complaint_service.project.Entities.User;
import com.smart_complaint_service.project.Repositories.*;
import com.smart_complaint_service.project.Tools.EstimatedTimeResolutionTool;
import com.smart_complaint_service.project.Utils.FallbackDepartmentClassifier;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class StaffAssignmentService {

    private final ChatClient chatClient;
    private final EstimatedTimeResolutionTool estimatedTimeResolutionTool;

    @Autowired
    private ComplaintRepository complaintRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ComplaintAssignmentRepository complaintAssignmentRepository;

    @Autowired
    private AdminComplaintTaskRepository adminComplaintTaskRepository;

    @Autowired
    private EmailService emailService;

    @Value("${admin.alert.email}")
    private String adminAlertEmail;

    public StaffAssignmentService(ChatClient chatClient,
                                  EstimatedTimeResolutionTool estimatedTimeResolutionTool) {
        this.chatClient = chatClient;
        this.estimatedTimeResolutionTool = estimatedTimeResolutionTool;
    }

    private String classifyDepartment(String description, String serviceType) {
        try {
            String aiResult = chatClient.prompt()
                    .system("""
                            You are a complaint classifier.
                            Return ONLY a department name in UPPERCASE_WITH_UNDERSCORES.
                            Examples: WATER_MAINTENANCE, IT_SUPPORT, ELECTRICAL_MAINTENANCE
                            No explanation. No sentences. Just the name.
                            """)
                    .user("Complaint: " + description
                            + "\nService type: " + serviceType)
                    .call()
                    .content();

            if (aiResult != null) {
                String cleaned = aiResult.trim().replaceAll("[^A-Z_]", "");
                if (!cleaned.isBlank()) {
                    System.out.println("StaffAssignmentService - AI DEPT = " + cleaned);
                    return cleaned;
                }
            }

            System.out.println("StaffAssignmentService - AI returned blank, "
                    + "using keyword fallback");

        } catch (Exception e) {
            System.out.println("StaffAssignmentService - AI department classification failed: "
                    + e.getMessage() + " | using keyword fallback");
        }

        String fallback = FallbackDepartmentClassifier.classify(description, serviceType);
        System.out.println("StaffAssignmentService - FALLBACK DEPT = " + fallback);
        return fallback;
    }

    private String safeEstimateResolutionTime(String description,
                                              String serviceType,
                                              boolean needToActFast) {
        try {
            String response = estimatedTimeResolutionTool.estimateResolutionTime(
                    description, serviceType, needToActFast);

            if (response != null && !response.isBlank()) {
                return response;
            }

            System.out.println("StaffAssignmentService - AI time estimation returned blank, "
                    + "using default fallback");

        } catch (Exception e) {
            System.out.println("StaffAssignmentService - AI time estimation failed: "
                    + e.getMessage() + " | using default fallback");
        }

        int defaultMinutes = serviceType.equalsIgnoreCase("PHYSICAL") ? 60 : 30;
        if (needToActFast) defaultMinutes = defaultMinutes / 2;
        return "VALID|" + defaultMinutes;
    }

    public void handleNewStaffRegistration(User staff) {

        System.out.println("handleNewStaffRegistration called for staff: "
                + staff.getUserName()
                + " | dept: " + (staff.getDept() != null
                ? staff.getDept().getId() + " - " + staff.getDept().getDepartmentName()
                : "NULL"));

        List<Complaint> openComplaints =
                complaintRepository.findByCurrentStatus("OPEN");

        System.out.println("Open complaints found: " + openComplaints.size());

        boolean anyAssigned = false;

        for (Complaint complaint : openComplaints) {

            System.out.println("Processing complaint ID: " + complaint.getComplaintId()
                    + " | dept: " + (complaint.getDept() != null
                    ? complaint.getDept().getId() + " - " + complaint.getDept().getDepartmentName()
                    : "NULL"));

            if (complaintAssignmentRepository
                    .existsByCmpComplaintIdAndActiveTrue(
                            complaint.getComplaintId())) {
                System.out.println("  → SKIPPED: already assigned");
                continue;
            }

            Department dept;

            if (complaint.getDept() != null) {
                dept = complaint.getDept();
                System.out.println("StaffAssignmentService - REUSING DEPT = "
                        + dept.getDepartmentName());

            } else {
                String aiDeptName = classifyDepartment(
                        complaint.getDescription(),
                        complaint.getServiceType().name()
                );

                if (aiDeptName.isBlank()) continue;

                dept = departmentRepository.findByDepartmentName(aiDeptName)
                        .orElseGet(() -> {
                            Department newDept = new Department();
                            newDept.setDepartmentName(aiDeptName);
                            newDept.setDescription("Auto-created for: "
                                    + complaint.getServiceType().name());
                            return departmentRepository.save(newDept);
                        });

                complaint.setDept(dept);
                complaintRepository.save(complaint);
            }

            if (staff.getDept() != null &&
                    !staff.getDept().getId().equals(dept.getId())) {
                System.out.println("  → SKIPPED: dept mismatch. Staff dept: "
                        + staff.getDept().getId()
                        + " | Complaint dept: " + dept.getId());
                continue;
            }

            staff.setDept(dept);
            userRepository.save(staff);

            User admin = userRepository.findAdmin();

            ComplaintAssignment assignment = new ComplaintAssignment();
            assignment.setCmp(complaint);
            assignment.setAssignedTo(staff);
            assignment.setAssignedBy(admin);
            assignment.setActive(true);
            complaintAssignmentRepository.save(assignment);

            staff.setActiveComplaintCount(staff.getActiveComplaintCount() + 1);
            userRepository.save(staff);

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
                                "Reason: " + detail + "\n\n" +
                                "Please re-register with the correct service type.\n\n" +
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
                staff.setActiveComplaintCount(
                        Math.max(0, staff.getActiveComplaintCount() - 1));
                userRepository.save(staff);
                continue;
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

            System.out.println("  → ASSIGNED complaint " + complaint.getComplaintId()
                    + " to staff " + staff.getUserName());

            adminComplaintTaskRepository
                    .findByComplaintIdAndResolvedFalse(complaint.getComplaintId())
                    .ifPresent(task -> {
                        task.setResolved(true);
                        adminComplaintTaskRepository.save(task);
                    });

            emailService.sendEmail(
                    complaint.getUser().getEmailId(),
                    "Your Complaint Has Been Assigned",
                    "Dear " + complaint.getUser().getUserName() + ",\n\n" +
                            "Your complaint has been validated and assigned.\n\n" +
                            "Complaint Details:\n" +
                            "Complaint ID   : " + complaint.getComplaintId() + "\n" +
                            "Description    : " + complaint.getDescription() + "\n" +
                            "Service Type   : " + complaint.getServiceType().name() + "\n" +
                            "Location       : " + complaint.getLocation() + "\n\n" +
                            "Assigned Staff Details:\n" +
                            "Name           : " + staff.getUserName() + "\n" +
                            "Phone Number   : " + (staff.getPhoneNumber() != null
                            ? "tel:" + staff.getPhoneNumber() : "Not provided") + "\n" +
                            "Department     : " + dept.getDepartmentName() + "\n\n" +
                            "Estimated Resolution Time : " + estimatedMinutes + " minute(s)\n" +
                            "Complaint Status          : ASSIGNED\n\n" +
                            "- Smart Complaint Management Team"
            );

            emailService.sendEmail(
                    staff.getEmailId(),
                    "New Complaint Assigned to You - ID: " + complaint.getComplaintId(),
                    "Dear " + staff.getUserName() + ",\n\n" +
                            "A new complaint has been assigned to you.\n\n" +
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

            emailService.sendEmail(
                    adminAlertEmail,
                    "Complaint Auto Assigned - ID: " + complaint.getComplaintId(),
                    "Dear Admin,\n\n" +
                            "Complaint ID " + complaint.getComplaintId()
                            + " has been auto-assigned.\n\n" +
                            "Complaint Details:\n" +
                            "Complaint ID   : " + complaint.getComplaintId() + "\n" +
                            "Description    : " + complaint.getDescription() + "\n" +
                            "Service Type   : " + complaint.getServiceType().name() + "\n" +
                            "Location       : " + complaint.getLocation() + "\n\n" +
                            "User Details:\n" +
                            "Name           : " + complaint.getUser().getUserName() + "\n" +
                            "Email          : " + complaint.getUser().getEmailId() + "\n\n" +
                            "Assigned To:\n" +
                            "Staff Name     : " + staff.getUserName() + "\n" +
                            "Department     : " + dept.getDepartmentName() + "\n" +
                            "Est. Resolution: " + estimatedMinutes + " minutes\n\n" +
                            "- Smart Complaint Management Team"
            );

            anyAssigned = true;
        }
        if (!anyAssigned) {
            System.out.println("No complaints assigned to staff: " + staff.getUserName());
        }
    }
}