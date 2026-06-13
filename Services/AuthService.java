package com.smart_complaint_service.project.Services;

import com.smart_complaint_service.project.Entities.AdminComplaintTask;
import com.smart_complaint_service.project.Entities.Complaint;
import com.smart_complaint_service.project.Entities.User;
import com.smart_complaint_service.project.Enums.Role;
import com.smart_complaint_service.project.Repositories.*;
import com.smart_complaint_service.project.security.JWTService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JWTService jwtService;

    @Autowired
    private BlacklistedStaffRepository blacklistedStaffRepository;

    @Autowired
    private ComplaintRepository complaintRepository;

    @Autowired
    private AdminComplaintTaskRepository adminComplaintTaskRepository;

    @Autowired
    private ComplaintAssignmentRepository complaintAssignmentRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private StaffAssignmentService staffAssignmentService;

    @Value("${admin.alert.email}")
    private String adminAlertEmail;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JWTService jwtService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    public String register(User user) {

        if (user.getRole() == null) {
            return "Role is required";
        }

        String role = user.getRole().name().replaceAll("\\s+", "");

        List<String> roleList = Arrays.stream(Role.values())
                .map(Role::name)
                .toList();

        if (!roleList.contains(role)) {
            return "Invalid Role. Try: USER or ADMIN or STAFF";
        }

        if (userRepository.findByEmailId(user.getEmailId()).isPresent()) {
            return "User already registered";
        }

        if (Role.valueOf(role) == Role.STAFF &&
                blacklistedStaffRepository.existsByEmail(user.getEmailId())) {

            String message = "Dear " + user.getUserName() + ",\n\n" +
                    "Your performance was poor earlier. As you have ignored multiple " +
                    "reminders to resolve complaints on time, we are sorry to say that " +
                    "we can't trust you once more.\n\n" +
                    "- Smart Complaint Management Team";

            emailService.sendEmail(user.getEmailId(),
                    "Registration Rejected - Poor Performance History",
                    message);

            return message;
        }

        user.setRole(Role.valueOf(role));
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userRepository.save(user);

        if (user.getRole() == Role.ADMIN) {
            return "Admin registered successfully";
        }

        if (user.getRole() == Role.USER) {
            return "User registered successfully";
        }

        if (user.getRole() == Role.STAFF && Boolean.TRUE.equals(user.getWillingToTakeComplaints())) {

            staffAssignmentService.handleNewStaffRegistration(user);

            StringBuilder response = new StringBuilder();
            response.append("Staff registered successfully\n\n");

            List<Complaint> assignedComplaints =
                    complaintRepository.findByCurrentStatus("ASSIGNED");

            List<Complaint> myComplaints = assignedComplaints.stream()
                    .filter(c -> complaintAssignmentRepository
                            .findByCmpComplaintIdAndActiveTrue(c.getComplaintId())
                            .map(a -> a.getAssignedTo().getId().equals(user.getId()))
                            .orElse(false))
                    .toList();

            if (!myComplaints.isEmpty()) {
                response.append("─────────────────────────────────────\n");
                response.append("Complaints Assigned To You:\n\n");

                for (Complaint c : myComplaints) {
                    response.append("Complaint ID       : ").append(c.getComplaintId()).append("\n")
                            .append("Description        : ").append(c.getDescription()).append("\n")
                            .append("Service Type       : ").append(c.getServiceType().name()).append("\n")
                            .append("Location           : ").append(c.getLocation()).append("\n\n")
                            .append("User Details:\n")
                            .append("Name               : ").append(c.getUser().getUserName()).append("\n")
                            .append("Email              : ").append(c.getUser().getEmailId()).append("\n\n")
                            .append("Resolution Timer:\n")
                            .append("Assigned At        : ").append(c.getAssignedAt()).append("\n")
                            .append("Estimated Time     : ").append(c.getEstimatedResolutionMinutes()).append(" minutes\n")
                            .append("Deadline           : ").append(c.getAssignedAt()
                                    .plusMinutes(c.getEstimatedResolutionMinutes())).append("\n\n")
                            .append("Please resolve before the deadline to maintain your ranking.\n")
                            .append("Once resolved, notify admin via:\n")
                            .append("POST /staff/complaint/resolve/").append(c.getComplaintId()).append("\n")
                            .append("─────────────────────────────────────\n");
                }
            } else {
                response.append("No complaints assigned yet. You will be notified via email.");
            }

            return response.toString();
        }

        return "Staff registered successfully";
    }

    public String verify(String email, String password) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, password)
        );

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        assert userDetails != null;
        String token = jwtService.generateToken(userDetails);

        User loggedInUser = userRepository.findByEmailId(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (loggedInUser.getRole() == Role.ADMIN) {

            List<Complaint> activeComplaints = complaintRepository
                    .findByCurrentStatusIn(List.of("OPEN", "ASSIGNED", "IN_PROGRESS"));

            StringBuilder responseBody = new StringBuilder();
            responseBody.append("Admin logged in successfully\n\n")
                    .append("Token: ").append(token).append("\n\n");

            if (!activeComplaints.isEmpty()) {

                responseBody.append("⚠ There are active complaints, please view them.\n\n")
                        .append("Call: GET /admin/complaints/active\n\n");

                List<Complaint> openComplaints = activeComplaints.stream()
                        .filter(c -> c.getCurrentStatus().equals("OPEN")
                                && c.getDept() != null
                                && !complaintAssignmentRepository
                                .existsByCmpComplaintIdAndActiveTrue(c.getComplaintId()))
                        .toList();

                if (!openComplaints.isEmpty()) {
                    responseBody.append("─────────────────────────────────────\n")
                            .append("Open Complaints Available for Auto-Assign:\n\n");

                    for (Complaint c : openComplaints) {
                        responseBody.append("Complaint ID  : ").append(c.getComplaintId()).append("\n")
                                .append("Description   : ").append(c.getDescription()).append("\n")
                                .append("Department    : ").append(c.getDept().getDepartmentName()).append("\n")
                                .append("Auto-Assign   : POST /admin/auto-assign/complaint/")
                                .append(c.getComplaintId()).append("\n")
                                .append("─────────────────────────────────────\n");
                    }
                }

                StringBuilder emailBody = new StringBuilder();
                emailBody.append("Dear Admin,\n\n")
                        .append("You have ").append(activeComplaints.size())
                        .append(" active complaint(s) that require your attention.\n\n");

                for (Complaint c : activeComplaints) {

                    if (c.getCurrentStatus().equals("OPEN")) {
                        boolean taskExists = adminComplaintTaskRepository
                                .findByComplaintIdAndResolvedFalse(c.getComplaintId()).isPresent();

                        if (!taskExists) {
                            AdminComplaintTask task = new AdminComplaintTask();
                            task.setComplaintId(c.getComplaintId());
                            task.setUserEmail(c.getUser().getEmailId());
                            task.setUserName(c.getUser().getUserName());
                            task.setAssignedToAdminAt(LocalDateTime.now());
                            task.setResolved(false);
                            task.setUserNotifiedOfFailure(false);
                            adminComplaintTaskRepository.save(task);
                        }
                    }

                    emailBody.append("Complaint ID  : ").append(c.getComplaintId()).append("\n")
                            .append("Description   : ").append(c.getDescription()).append("\n")
                            .append("Service Type  : ").append(c.getServiceType()).append("\n")
                            .append("Location      : ").append(c.getLocation()).append("\n")
                            .append("User Name     : ").append(c.getUser().getUserName()).append("\n")
                            .append("User Email    : ").append(c.getUser().getEmailId()).append("\n")
                            .append("Status        : ").append(c.getCurrentStatus()).append("\n")
                            .append("─────────────────────────────────────\n");
                }

                emailBody.append("\nTo view all active complaints:\n")
                        .append("GET /admin/complaints/active\n\n")
                        .append("- Smart Complaint Management Team");

                emailService.sendEmail(
                        adminAlertEmail,
                        "Active Complaints Require Your Attention",
                        emailBody.toString()
                );

            } else {
                responseBody.append("No active complaints.");
            }

            return responseBody.toString();
        }

        if (loggedInUser.getRole() == Role.USER) {
            return "User logged in successfully\n\nToken: " + token;
        }

        return "Token: " + token;
    }
}