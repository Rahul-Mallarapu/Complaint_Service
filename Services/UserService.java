package com.smart_complaint_service.project.Services;

import com.smart_complaint_service.project.Entities.*;
import com.smart_complaint_service.project.Repositories.*;
import com.smart_complaint_service.project.Utils.FallbackDepartmentClassifier;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class UserService {

    private final ChatClient chatClient;

    @Autowired
    private ComplaintRepository complaintRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PendingComplaintAlertRepository pendingAlertRepository;

    @Autowired
    private AdminComplaintTaskRepository adminComplaintTaskRepository;

    @Autowired
    private ComplaintAssignmentRepository complaintAssignmentRepository;

    @Autowired
    private EmailService emailService;

    @Value("${admin.alert.email}")
    private String adminAlertEmail;

    public UserService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    private String classifyDepartment(String description, String serviceType) {
        try {
            String aiResult = Objects.requireNonNull(
                    chatClient.prompt()
                            .system("""
                                    You are a complaint classifier.
                                    Return ONLY a department name in UPPERCASE_WITH_UNDERSCORES.
                                    Examples: WATER_MAINTENANCE, IT_SUPPORT, ELECTRICAL_MAINTENANCE
                                    No explanation. No sentences. Just the name.
                                    """)
                            .user("Complaint: " + description
                                    + "\nService type: " + serviceType)
                            .call()
                            .content()
            ).trim().replaceAll("[^A-Z_]", "");

            if (!aiResult.isBlank()) {
                System.out.println("UserService - AI DEPT = " + aiResult);
                return aiResult;
            }

            System.out.println("UserService - AI returned blank, using keyword fallback");

        } catch (Exception e) {
            System.out.println("UserService - AI department classification failed: "
                    + e.getMessage() + " | using keyword fallback");
        }

        String fallback = FallbackDepartmentClassifier.classify(description, serviceType);
        System.out.println("UserService - FALLBACK DEPT = " + fallback);
        return fallback;
    }

    @PreAuthorize("hasAuthority('USER')")
    public String register_complaint(Complaint complaint) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();

        User user = userRepository.findByEmailId(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        Optional<AdminComplaintTask> sameDayFailure = adminComplaintTaskRepository
                .findByUserEmailAndUserNotifiedOfFailureTrueAndFailureNotifiedAtBetween(
                        email, startOfDay, endOfDay);

        if (sameDayFailure.isPresent()) {
            return "We regret that your complaint could not be processed today. " +
                    "Please try again tomorrow.";
        }

        Optional<PendingComplaintAlert> closedAlert =
                pendingAlertRepository.findByUserEmailAndUserNotifiedOfClosureTrue(email);

        if (closedAlert.isPresent()) {
            return "Please re-register your complaint.";
        }

        List<Complaint> duplicates = complaintRepository
                .findActiveComplaintsByUserAndDescription(
                        user.getId(), complaint.getDescription());

        if (!duplicates.isEmpty()) {

            if (complaint.isSpecificIssue()) {

                if (complaint.getSpecificIssueDescription() == null ||
                        complaint.getSpecificIssueDescription().isBlank()) {
                    return "Please provide a specific issue description " +
                            "to register a separate complaint.";
                }

                // ── AI duplicate check (with fallback to SEPARATE) ────
                String aiResult = "SEPARATE"; // safe fallback default
                try {
                    String aiValidation = chatClient.prompt()
                            .system("""
                                    You are a complaint validation assistant.
                                    Your job is to determine if a new complaint is genuinely
                                    a SEPARATE issue from an existing one, or just a duplicate.

                                    Rules:
                                    - If the specific issue is clearly a different problem
                                      that needs separate attention → respond SEPARATE
                                    - If it's the same problem rephrased or a minor variation
                                      → respond DUPLICATE

                                    Respond with ONLY one word: SEPARATE or DUPLICATE
                                    """)
                            .user("Original complaint: " + duplicates.get(0).getDescription()
                                    + "\nNew specific issue: " + complaint.getSpecificIssueDescription()
                                    + "\nService type: " + complaint.getServiceType().name())
                            .call()
                            .content();

                    if (aiValidation != null && !aiValidation.isBlank()) {
                        aiResult = aiValidation.trim().toUpperCase();
                    } else {
                        System.out.println("UserService - AI duplicate check returned blank, "
                                + "defaulting to SEPARATE");
                    }
                } catch (Exception e) {
                    System.out.println("UserService - AI duplicate check failed: "
                            + e.getMessage() + " | defaulting to SEPARATE");
                }

                if (aiResult.equals("DUPLICATE")) {
                    emailService.sendEmail(
                            user.getEmailId(),
                            "Complaint Registration Rejected - Duplicate Issue",
                            "Dear " + user.getUserName() + ",\n\n" +
                                    "We have reviewed your new complaint and determined that it " +
                                    "is the same issue as your existing complaint.\n\n" +
                                    "Existing Complaint Details:\n" +
                                    "Complaint ID  : " + duplicates.get(0).getComplaintId() + "\n" +
                                    "Description   : " + duplicates.get(0).getDescription() + "\n" +
                                    "Status        : " + duplicates.get(0).getCurrentStatus() + "\n\n" +
                                    "Our staff member is already working on your complaint. " +
                                    "Please wait for it to be resolved.\n\n" +
                                    "If you believe this is a genuinely separate issue, please " +
                                    "re-register with a more detailed specific issue description.\n\n" +
                                    "- Smart Complaint Management Team"
                    );
                    return "Your complaint is already being handled. " +
                            "Please wait for the existing complaint to be resolved.";
                }

                if (aiResult.equals("SEPARATE")) {

                    Department existingDept = duplicates.get(0).getDept();

                    if (existingDept == null) {
                        return "Your existing complaint is still being processed. " +
                                "Please wait for department assignment.";
                    }

                    Optional<User> freeStaff = userRepository
                            .findBestAvailableStaffForDept(existingDept.getId());

                    if (freeStaff.isEmpty()) {
                        emailService.sendEmail(
                                user.getEmailId(),
                                "Specific Issue Acknowledged - No Staff Available",
                                "Dear " + user.getUserName() + ",\n\n" +
                                        "We have acknowledged your specific issue:\n\n" +
                                        "\"" + complaint.getSpecificIssueDescription() + "\"\n\n" +
                                        "However, all staff members in the " +
                                        existingDept.getDepartmentName() +
                                        " department are currently busy.\n\n" +
                                        "You will be notified as soon as a staff member " +
                                        "becomes available to handle your specific issue.\n\n" +
                                        "- Smart Complaint Management Team"
                        );
                        return "Your specific issue has been acknowledged but no staff " +
                                "is currently available. You will be notified shortly.";
                    }

                    complaint.setUser(user);
                    complaint.setDept(existingDept);
                    complaint.setCurrentStatus("OPEN");
                    complaint.setSpecificIssue(true);
                    Complaint savedSpecific = complaintRepository.save(complaint);

                    emailService.sendEmail(
                            user.getEmailId(),
                            "Specific Issue Registered - ID: " + savedSpecific.getComplaintId(),
                            "Dear " + user.getUserName() + ",\n\n" +
                                    "Your specific issue has been validated and registered.\n\n" +
                                    "New Complaint Details:\n" +
                                    "Complaint ID          : " + savedSpecific.getComplaintId() + "\n" +
                                    "Specific Issue        : " + complaint.getSpecificIssueDescription() + "\n" +
                                    "Department            : " + existingDept.getDepartmentName() + "\n" +
                                    "Status                : OPEN\n\n" +
                                    "A staff member will be assigned shortly.\n\n" +
                                    "- Smart Complaint Management Team"
                    );

                    return "Specific issue validated and registered under complaint ID: "
                            + savedSpecific.getComplaintId();
                }
            }

            ComplaintAssignment activeAssignment = complaintAssignmentRepository
                    .findByCmpComplaintIdAndActiveTrue(
                            duplicates.get(0).getComplaintId())
                    .orElse(null);

            String staffDetails = activeAssignment != null
                    ? "Staff Name   : " + activeAssignment.getAssignedTo().getUserName() + "\n" +
                    "Department   : " + (activeAssignment.getAssignedTo().getDept() != null
                    ? activeAssignment.getAssignedTo().getDept().getDepartmentName()
                    : "N/A") + "\n" +
                    "Phone        : " + (activeAssignment.getAssignedTo().getPhoneNumber() != null
                    ? "tel:" + activeAssignment.getAssignedTo().getPhoneNumber()
                    : "Not provided")
                    : "Staff is being assigned shortly.";

            emailService.sendEmail(
                    user.getEmailId(),
                    "Complaint Already Being Handled - ID: "
                            + duplicates.get(0).getComplaintId(),
                    "Dear " + user.getUserName() + ",\n\n" +
                            "Your complaint is already being handled by our team.\n\n" +
                            "Existing Complaint Details:\n" +
                            "Complaint ID  : " + duplicates.get(0).getComplaintId() + "\n" +
                            "Description   : " + duplicates.get(0).getDescription() + "\n" +
                            "Status        : " + duplicates.get(0).getCurrentStatus() + "\n\n" +
                            "Assigned Staff:\n" +
                            staffDetails + "\n\n" +
                            "Please wait for your complaint to be resolved.\n\n" +
                            "If you have a SEPARATE specific issue that needs to be handled " +
                            "independently, please re-register with:\n" +
                            "  specificIssue: true\n" +
                            "  specificIssueDescription: <describe your specific issue>\n\n" +
                            "- Smart Complaint Management Team"
            );

            return "Your complaint is already being handled. " +
                    "Please wait or re-register with specificIssue: true " +
                    "if you have a separate issue.";
        }

        complaint.setUser(user);

        User adminPresent = userRepository.findAdmin();

        if (adminPresent == null) {
            complaint.setCurrentStatus("OPEN");
            complaint.setDept(null);
            Complaint saved = complaintRepository.save(complaint);

            emailService.sendEmail(
                    user.getEmailId(),
                    "Complaint Registration Failed - Admin Not Available",
                    "Dear " + user.getUserName() + ",\n\n" +
                            "We regret to inform you that no admin is currently available " +
                            "to handle your complaint.\n\n" +
                            "Complaint ID  : " + saved.getComplaintId() + "\n" +
                            "Description   : " + complaint.getDescription() + "\n" +
                            "Service Type  : " + complaint.getServiceType() + "\n" +
                            "Location      : " + complaint.getLocation() + "\n\n" +
                            "You will be notified once an admin becomes available.\n\n" +
                            "Thank you for your patience.\n\n" +
                            "- Smart Complaint Management Team"
            );

            emailService.sendEmail(
                    adminAlertEmail,
                    "New Complaint Pending - No Admin Available",
                    "A new complaint has been raised, but no admins are available.\n\n" +
                            "Complaint ID  : " + saved.getComplaintId() + "\n" +
                            "Description   : " + complaint.getDescription() + "\n" +
                            "User Name     : " + user.getUserName() + "\n" +
                            "User Email    : " + user.getEmailId() + "\n" +
                            "Location      : " + complaint.getLocation() + "\n" +
                            "Service Type  : " + complaint.getServiceType() + "\n\n" +
                            "Please hire/assign an admin as per the requirement."
            );

            PendingComplaintAlert alert = new PendingComplaintAlert();
            alert.setComplaintId(saved.getComplaintId());
            alert.setUserEmail(user.getEmailId());
            alert.setUserName(user.getUserName());
            alert.setDescription(complaint.getDescription());
            alert.setServiceType(complaint.getServiceType().name());
            alert.setLocation(complaint.getLocation());
            alert.setRaisedAt(LocalDateTime.now());
            alert.setAdminNotified(false);
            pendingAlertRepository.save(alert);

            return "Admin not present. You will be notified via email once admin is available.";
        }

        String departmentName = classifyDepartment(
                complaint.getDescription(),
                complaint.getServiceType().name()
        );

        Department dept = departmentRepository.findByDepartmentName(departmentName)
                .orElseGet(() -> {
                    Department newDept = new Department();
                    newDept.setDepartmentName(departmentName);
                    newDept.setDescription("Auto-created for: "
                            + complaint.getServiceType().name());
                    return departmentRepository.save(newDept);
                });

        complaint.setDept(dept);
        complaint.setCurrentStatus("OPEN");
        complaintRepository.save(complaint);

        emailService.sendEmail(
                adminAlertEmail,
                "New Complaint Registered - Action Required",
                "Dear Admin,\n\n" +
                        "A new complaint has been registered and requires your attention.\n\n" +
                        "Complaint Details:\n" +
                        "Complaint ID  : " + complaint.getComplaintId() + "\n" +
                        "Description   : " + complaint.getDescription() + "\n" +
                        "Service Type  : " + complaint.getServiceType() + "\n" +
                        "Location      : " + complaint.getLocation() + "\n" +
                        "Department    : " + dept.getDepartmentName() + "\n\n" +
                        "User Details:\n" +
                        "Name          : " + user.getUserName() + "\n" +
                        "Email         : " + user.getEmailId() + "\n\n" +
                        "To view available staff and assign:\n" +
                        "GET /admin/complaint/" + complaint.getComplaintId()
                        + "/available-staff\n\n" +
                        "- Smart Complaint Management Team"
        );

        return "Complaint registered under: " + dept.getDepartmentName();
    }

    @PreAuthorize("hasAuthority('USER')")
    public String cancelComplaint(Long complaintId, String reason) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();

        User user = userRepository.findByEmailId(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!reason.equalsIgnoreCase("BREACH") && !reason.equalsIgnoreCase("MISTAKE")) {
            return "Invalid reason. Use BREACH or MISTAKE";
        }

        Complaint complaint = complaintRepository.findById(complaintId).orElse(null);
        if (complaint == null) return "Complaint not found";

        if (!complaint.getUser().getId().equals(user.getId())) {
            return "This complaint does not belong to you";
        }

        if (complaint.getCurrentStatus().equals("CLOSED") ||
                complaint.getCurrentStatus().equals("CANCELLED")) {
            return "Complaint is already " + complaint.getCurrentStatus();
        }

        ComplaintAssignment assignment = complaintAssignmentRepository
                .findByCmpComplaintIdAndActiveTrue(complaintId)
                .orElse(null);

        String reasonUpper = reason.toUpperCase();

        if (reasonUpper.equals("BREACH")) {

            complaint.setCurrentStatus("CANCELLED");
            complaint.setCancellationReason("BREACH");
            complaint.setCancelledAt(LocalDateTime.now());
            complaintRepository.save(complaint);

            if (assignment != null) {
                assignment.setActive(false);
                complaintAssignmentRepository.save(assignment);

                User staff = assignment.getAssignedTo();
                staff.setActiveComplaintCount(
                        Math.max(0, staff.getActiveComplaintCount() - 1));
                complaint.setLastPenaltyBucket(0);
                complaint.setStaffReminderSent(false);
                userRepository.save(staff);

                emailService.sendEmail(
                        staff.getEmailId(),
                        "Complaint Cancelled by User - ID: " + complaintId,
                        "Dear " + staff.getUserName() + ",\n\n" +
                                "The user has cancelled complaint ID: " + complaintId +
                                " due to breach of estimated resolution time.\n\n" +
                                "Complaint Details:\n" +
                                "Complaint ID  : " + complaint.getComplaintId() + "\n" +
                                "Description   : " + complaint.getDescription() + "\n" +
                                "Location      : " + complaint.getLocation() + "\n\n" +
                                "Note: Your ranking has already been impacted due to the delay.\n\n" +
                                "- Smart Complaint Management Team"
                );
            }

            emailService.sendEmail(
                    adminAlertEmail,
                    "Complaint Cancelled - Breach of Resolution Time - ID: " + complaintId,
                    "Dear Admin,\n\n" +
                            "Complaint ID " + complaintId +
                            " has been cancelled by the user due to breach of resolution time.\n\n" +
                            "Complaint Details:\n" +
                            "Complaint ID  : " + complaint.getComplaintId() + "\n" +
                            "Description   : " + complaint.getDescription() + "\n" +
                            "Location      : " + complaint.getLocation() + "\n" +
                            "Cancelled At  : " + complaint.getCancelledAt() + "\n\n" +
                            "- Smart Complaint Management Team"
            );

            emailService.sendEmail(
                    user.getEmailId(),
                    "Your Complaint Has Been Cancelled - ID: " + complaintId,
                    "Dear " + user.getUserName() + ",\n\n" +
                            "Your complaint (ID: " + complaintId +
                            ") has been cancelled due to breach of estimated resolution time.\n\n" +
                            "You may re-register your complaint if the issue persists.\n\n" +
                            "- Smart Complaint Management Team"
            );

            return "Complaint cancelled due to breach of resolution time. " +
                    "You may re-register your complaint.";
        }

        if (reasonUpper.equals("MISTAKE")) {

            complaint.setCurrentStatus("CANCELLED");
            complaint.setCancellationReason("MISTAKE");
            complaint.setCancelledAt(LocalDateTime.now());
            complaintRepository.save(complaint);

            if (assignment != null) {
                assignment.setActive(false);
                complaintAssignmentRepository.save(assignment);

                User staff = assignment.getAssignedTo();
                staff.setActiveComplaintCount(
                        Math.max(0, staff.getActiveComplaintCount() - 1));
                userRepository.save(staff);

                emailService.sendEmail(
                        staff.getEmailId(),
                        "Complaint Cancelled by User - ID: " + complaintId,
                        "Dear " + staff.getUserName() + ",\n\n" +
                                "The user has cancelled complaint ID: " + complaintId +
                                " by mistake. No action required from your side.\n\n" +
                                "Your ranking has not been impacted.\n\n" +
                                "- Smart Complaint Management Team"
                );
            }

            emailService.sendEmail(
                    adminAlertEmail,
                    "Complaint Cancelled by Mistake - ID: " + complaintId,
                    "Dear Admin,\n\n" +
                            "Complaint ID " + complaintId +
                            " has been cancelled by the user by mistake.\n\n" +
                            "Complaint Details:\n" +
                            "Complaint ID  : " + complaint.getComplaintId() + "\n" +
                            "Description   : " + complaint.getDescription() + "\n" +
                            "Cancelled At  : " + complaint.getCancelledAt() + "\n\n" +
                            "No staff ranking impact.\n\n" +
                            "- Smart Complaint Management Team"
            );

            emailService.sendEmail(
                    user.getEmailId(),
                    "Your Complaint Has Been Cancelled - ID: " + complaintId,
                    "Dear " + user.getUserName() + ",\n\n" +
                            "Your complaint (ID: " + complaintId +
                            ") has been successfully cancelled.\n\n" +
                            "If you need to raise a new complaint, feel free to register again.\n\n" +
                            "- Smart Complaint Management Team"
            );

            return "Complaint cancelled successfully. No staff ranking impact.";
        }

        return "Invalid cancellation reason.";
    }
}