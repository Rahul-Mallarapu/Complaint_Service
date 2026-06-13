package com.smart_complaint_service.project.Schedulers;

import com.smart_complaint_service.project.Entities.*;
import com.smart_complaint_service.project.Repositories.*;
import com.smart_complaint_service.project.Services.EmailService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class AdminAvailabilityScheduler {

    @Autowired private UserRepository userRepository;
    @Autowired private PendingComplaintAlertRepository pendingAlertRepository;
    @Autowired private EmailService emailService;
    @Autowired private ComplaintRepository complaintRepository;
    @Autowired private AdminComplaintTaskRepository adminComplaintTaskRepository;
    @Autowired private ComplaintAssignmentRepository complaintAssignmentRepository;
    @Autowired private BlacklistedStaffRepository blacklistedStaffRepository;

    @Value("${admin.alert.email}")
    private String adminAlertEmail;

    @Transactional
    @Scheduled(fixedRate = 300000)
    public void checkAdminAndNotify() {
        List<PendingComplaintAlert> pendingAlerts =
                pendingAlertRepository.findByAdminNotifiedFalse();
        if (pendingAlerts.isEmpty()) return;

        LocalDateTime now = LocalDateTime.now();
        User admin = userRepository.findAdmin();

        for (PendingComplaintAlert alert : pendingAlerts) {
            long minutesWaited = ChronoUnit.MINUTES
                    .between(alert.getRaisedAt(), now);

            if (minutesWaited >= 30 && !alert.isUserNotifiedOfClosure()) {
                complaintRepository.findById(alert.getComplaintId())
                        .ifPresent(c -> {
                            c.setCurrentStatus("CLOSED");
                            complaintRepository.save(c);
                        });

                emailService.sendEmail(alert.getUserEmail(),
                        "We're Sorry - Your Complaint Has Been Closed",
                        "Dear " + alert.getUserName() + ",\n\n" +
                                "Since no admin has been available for 30 minutes, " +
                                "your complaint (ID: " + alert.getComplaintId() +
                                ") has been automatically closed.\n\n" +
                                "Please register again once services are restored.\n\n" +
                                "- Smart Complaint Management Team");

                alert.setUserNotifiedOfClosure(true);
                alert.setClosedAt(now);
                pendingAlertRepository.save(alert);

            } else if (admin != null && !alert.isUserNotifiedOfClosure()) {
                emailService.sendEmail(alert.getUserEmail(),
                        "Admin Now Available",
                        "Dear " + alert.getUserName() + ",\n\n" +
                                "An admin is now available. Your complaint will be " +
                                "attended to shortly.\n\n" +
                                "Description : " + alert.getDescription() + "\n" +
                                "Service Type: " + alert.getServiceType() + "\n" +
                                "Location    : " + alert.getLocation() + "\n\n" +
                                "- Smart Complaint Management Team");

                alert.setAdminNotified(true);
                pendingAlertRepository.save(alert);

            } else if (admin == null && !alert.isUserNotifiedOfClosure()) {
                emailService.sendEmail(adminAlertEmail,
                        "New Complaint Pending - No Admin Available",
                        "Complaint ID  : " + alert.getComplaintId() + "\n" +
                                "Description   : " + alert.getDescription() + "\n" +
                                "User Name     : " + alert.getUserName() + "\n" +
                                "User Email    : " + alert.getUserEmail() + "\n" +
                                "Service Type  : " + alert.getServiceType() + "\n\n" +
                                "Please hire/assign an admin.");
            }
        }
    }

    @Transactional
    @Scheduled(fixedRate = 300000)
    public void checkAdminAssignmentDeadline() {
        List<AdminComplaintTask> tasks = adminComplaintTaskRepository
                .findByResolvedFalseAndUserNotifiedOfFailureFalse();
        if (tasks.isEmpty()) return;

        LocalDateTime now = LocalDateTime.now();

        for (AdminComplaintTask task : tasks) {
            long minutesElapsed = ChronoUnit.MINUTES
                    .between(task.getAssignedToAdminAt(), now);

            if (minutesElapsed >= 20) {
                complaintRepository.findById(task.getComplaintId())
                        .ifPresent(c -> {
                            c.setCurrentStatus("CLOSED");
                            complaintRepository.save(c);
                        });

                emailService.sendEmail(task.getUserEmail(),
                        "We're Sorry - Please Try Again Tomorrow",
                        "Dear " + task.getUserName() + ",\n\n" +
                                "Our admin failed to assign a staff member to your " +
                                "complaint (ID: " + task.getComplaintId() + ") within " +
                                "the required time.\n\n" +
                                "Please try registering again tomorrow.\n\n" +
                                "- Smart Complaint Management Team");

                task.setUserNotifiedOfFailure(true);
                task.setFailureNotifiedAt(now);  // ── NEW ──
                adminComplaintTaskRepository.save(task);

            } else {
                emailService.sendEmail(adminAlertEmail,
                        "Reminder: Assign Staff - " + minutesElapsed + " min elapsed",
                        "Complaint ID  : " + task.getComplaintId() + "\n" +
                                "User          : " + task.getUserName() + "\n" +
                                "Time Elapsed  : " + minutesElapsed + " minutes\n" +
                                "Time Remaining: " + (20 - minutesElapsed) + " minutes\n\n" +
                                "Please assign immediately or complaint will be auto-closed.");
            }
        }
    }

    @Transactional
    @Scheduled(fixedRate = 60000)
    public void checkResolutionTimer() {
        List<Complaint> assignedComplaints =
                complaintRepository.findByCurrentStatus("ASSIGNED");
        LocalDateTime now = LocalDateTime.now();

        for (Complaint complaint : assignedComplaints) {
            if (complaint.getAssignedAt() == null ||
                    complaint.getEstimatedResolutionMinutes() == null) continue;

            complaint.setCurrentStatus("IN_PROGRESS");
            complaintRepository.save(complaint);

            complaintAssignmentRepository
                    .findByCmpComplaintIdAndActiveTrue(complaint.getComplaintId())
                    .ifPresent(assignment -> {
                        User staff = assignment.getAssignedTo();

                        if (!complaint.isStaffStartEmailSent()) {
                            emailService.sendEmail(
                                    staff.getEmailId(),
                                    "Complaint Assigned - Resolution Timer Started",
                                    "Dear " + staff.getUserName() + ",\n\n" +
                                            "A complaint has been assigned to you and the " +
                                            "resolution timer has started.\n\n" +
                                            "Complaint Details:\n" +
                                            "Complaint ID       : " + complaint.getComplaintId() + "\n" +
                                            "Description        : " + complaint.getDescription() + "\n" +
                                            "Service Type       : " + complaint.getServiceType() + "\n" +
                                            "Location           : " + complaint.getLocation() + "\n\n" +
                                            "User Details:\n" +
                                            "Name               : " + complaint.getUser().getUserName() + "\n" +
                                            "Email              : " + complaint.getUser().getEmailId() + "\n\n" +
                                            "Resolution Timer:\n" +
                                            "Assigned At        : " + complaint.getAssignedAt() + "\n" +
                                            "Estimated Time     : " + complaint.getEstimatedResolutionMinutes() + " minutes\n" +
                                            "Deadline           : " + complaint.getAssignedAt()
                                            .plusMinutes(complaint.getEstimatedResolutionMinutes()) + "\n\n" +
                                            "Please resolve before the deadline to maintain your ranking.\n\n" +
                                            "Once resolved, notify admin via:\n" +
                                            "POST /staff/complaint/resolve/" + complaint.getComplaintId() + "\n\n" +
                                            "- Smart Complaint Management Team"
                            );

                            complaint.setStaffStartEmailSent(true);
                            complaintRepository.save(complaint);
                        }
                    });
        }
    }

    @Transactional
    @Scheduled(fixedRate = 60000)
    public void checkInProgressTimer() {
        List<Complaint> inProgressComplaints =
                complaintRepository.findByCurrentStatus("IN_PROGRESS");
        LocalDateTime now = LocalDateTime.now();

        for (Complaint complaint : inProgressComplaints) {

            if (complaint.getAssignedAt() == null ||
                    complaint.getEstimatedResolutionMinutes() == null) continue;

            long minutesElapsed = ChronoUnit.MINUTES
                    .between(complaint.getAssignedAt(), now);

            int reminderAt = complaint.getEstimatedResolutionMinutes() - 10;
            if (minutesElapsed >= reminderAt && !complaint.isStaffReminderSent()) {

                complaintAssignmentRepository
                        .findByCmpComplaintIdAndActiveTrue(complaint.getComplaintId())
                        .ifPresent(assignment -> {
                            User staff = assignment.getAssignedTo();
                            int remainingMinutes = complaint.getEstimatedResolutionMinutes()
                                    - (int) minutesElapsed;

                            emailService.sendEmail(
                                    staff.getEmailId(),
                                    "⚠ Action Required: Please Wrap Up Complaint ID "
                                            + complaint.getComplaintId(),
                                    "Dear " + staff.getUserName() + ",\n\n" +
                                            "This is a reminder to wrap up the following complaint.\n\n" +
                                            "Complaint Details:\n" +
                                            "Complaint ID  : " + complaint.getComplaintId() + "\n" +
                                            "Description   : " + complaint.getDescription() + "\n" +
                                            "Location      : " + complaint.getLocation() + "\n\n" +
                                            "User Details:\n" +
                                            "Name          : " + complaint.getUser().getUserName() + "\n" +
                                            "Email         : " + complaint.getUser().getEmailId() + "\n\n" +
                                            "Time Remaining: ~" + remainingMinutes + " minute(s)\n\n" +
                                            "Once resolved, notify admin immediately via:\n" +
                                            "POST /staff/complaint/resolve/"
                                            + complaint.getComplaintId() + "\n\n" +
                                            "- Smart Complaint Management Team"
                            );
                        });

                complaint.setStaffReminderSent(true);
                complaintRepository.save(complaint);
            }

            if (minutesElapsed <= complaint.getEstimatedResolutionMinutes()) continue;

            long delayMinutes = minutesElapsed - complaint.getEstimatedResolutionMinutes();
            long currentBucket = delayMinutes / 5;

            if (currentBucket <= 0) continue;
            if (currentBucket <= complaint.getLastPenaltyBucket()) continue;

            complaint.setLastPenaltyBucket((int) currentBucket);
            complaintRepository.save(complaint);

            final long finalDelayMinutes = delayMinutes;
            final long finalBucket = currentBucket;

            complaintAssignmentRepository
                    .findByCmpComplaintIdAndActiveTrue(complaint.getComplaintId())
                    .ifPresent(assignment -> {
                        User staff = assignment.getAssignedTo();

                        int newRank = staff.getRankingScore() + 1;
                        staff.setRankingScore(newRank);
                        userRepository.save(staff);

                        System.out.println("RANK PENALTY: Staff " + staff.getUserName()
                                + " → rank " + newRank
                                + " (delay bucket " + finalBucket + ")");

                        emailService.sendEmail(
                                staff.getEmailId(),
                                "Performance Alert - Complaint Overdue by "
                                        + finalDelayMinutes + " minutes",
                                "Dear " + staff.getUserName() + ",\n\n" +
                                        "Your complaint resolution is overdue.\n\n" +
                                        "Complaint ID     : " + complaint.getComplaintId() + "\n" +
                                        "Description      : " + complaint.getDescription() + "\n" +
                                        "Estimated Time   : " + complaint.getEstimatedResolutionMinutes()
                                        + " minutes\n" +
                                        "Time Elapsed     : " + (complaint.getEstimatedResolutionMinutes()
                                        + finalDelayMinutes) + " minutes\n" +
                                        "Minutes Delayed  : " + finalDelayMinutes + " minutes\n" +
                                        "Current Rank     : " + newRank + "\n\n" +
                                        "Please resolve and notify admin immediately via:\n" +
                                        "POST /staff/complaint/resolve/"
                                        + complaint.getComplaintId() + "\n\n" +
                                        "- Smart Complaint Management Team"
                        );

                        if (newRank == 4) {
                            emailService.sendEmail(
                                    staff.getEmailId(),
                                    "⚠ Final Warning - Ranking Score: 4",
                                    "Dear " + staff.getUserName() + ",\n\n" +
                                            "Your ranking score has reached 4. This is your FINAL WARNING.\n\n" +
                                            "Complaint ID     : " + complaint.getComplaintId() + "\n" +
                                            "Description      : " + complaint.getDescription() + "\n" +
                                            "Estimated Time   : " + complaint.getEstimatedResolutionMinutes()
                                            + " minutes\n" +
                                            "Total Elapsed    : " + (complaint.getEstimatedResolutionMinutes()
                                            + finalDelayMinutes) + " minutes\n" +
                                            "Minutes Delayed  : " + finalDelayMinutes + " minutes\n" +
                                            "Current Rank     : " + newRank + "\n\n" +
                                            "WARNING: 1 more delay of 5 minutes each will result in\n" +
                                            "permanent removal of your account from the system.\n\n" +
                                            "Please resolve complaint ID " + complaint.getComplaintId()
                                            + " immediately.\n\n" +
                                            "- Smart Complaint Management Team"
                            );

                            emailService.sendEmail(
                                    adminAlertEmail,
                                    "Staff Final Warning - " + staff.getUserName(),
                                    "Dear Admin,\n\n" +
                                            "Staff member " + staff.getUserName()
                                            + " has reached ranking score 4.\n\n" +
                                            "Staff Details:\n" +
                                            "Name             : " + staff.getUserName() + "\n" +
                                            "Email            : " + staff.getEmailId() + "\n" +
                                            "Department       : " + (staff.getDept() != null
                                            ? staff.getDept().getDepartmentName() : "N/A") + "\n" +
                                            "Current Rank     : " + newRank + "\n\n" +
                                            "Complaint Details:\n" +
                                            "Complaint ID     : " + complaint.getComplaintId() + "\n" +
                                            "Estimated Time   : " + complaint.getEstimatedResolutionMinutes()
                                            + " minutes\n" +
                                            "Minutes Delayed  : " + finalDelayMinutes + " minutes\n\n" +
                                            "They will be auto-removed if rank reaches 5.\n\n" +
                                            "- Smart Complaint Management Team"
                            );
                        }

                        if (newRank >= 5) {
                            handleStaffDeletion(staff, complaint);
                        }
                    });
        }
    }

    private void handleStaffDeletion(User staff, Complaint triggerComplaint) {

        List<ComplaintAssignment> activeAssignments =
                complaintAssignmentRepository
                        .findByAssignedToIdAndActiveTrue(staff.getId());

        for (ComplaintAssignment assignment : activeAssignments) {
            Complaint complaint = assignment.getCmp();

            userRepository.findNextAvailableStaffForDept(
                            staff.getDept().getId(), staff.getId())
                    .ifPresentOrElse(
                            nextStaff -> {
                                assignment.setActive(false);
                                complaintAssignmentRepository.save(assignment);

                                ComplaintAssignment newAssignment = new ComplaintAssignment();
                                newAssignment.setCmp(complaint);
                                newAssignment.setAssignedTo(nextStaff);
                                newAssignment.setAssignedBy(userRepository.findAdmin());
                                newAssignment.setActive(true);
                                complaintAssignmentRepository.save(newAssignment);

                                nextStaff.setActiveComplaintCount(
                                        nextStaff.getActiveComplaintCount() + 1);
                                userRepository.save(nextStaff);

                                String phoneLink = nextStaff.getPhoneNumber() != null
                                        ? "tel:" + nextStaff.getPhoneNumber()
                                        : "Not provided";

                                emailService.sendEmail(
                                        complaint.getUser().getEmailId(),
                                        "Your Complaint Has Been Reassigned",
                                        "Dear " + complaint.getUser().getUserName() + ",\n\n" +
                                                "Your complaint (ID: " + complaint.getComplaintId() +
                                                ") has been reassigned to a new staff member.\n\n" +
                                                "New Staff Details:\n" +
                                                "Name       : " + nextStaff.getUserName() + "\n" +
                                                "Phone      : " + phoneLink + "\n" +
                                                "Department : " + (nextStaff.getDept() != null
                                                ? nextStaff.getDept().getDepartmentName()
                                                : "N/A") + "\n\n" +
                                                "We apologize for the inconvenience.\n\n" +
                                                "- Smart Complaint Management Team");
                            },
                            () -> {
                                complaint.setCurrentStatus("OPEN");
                                complaintRepository.save(complaint);

                                assignment.setActive(false);
                                complaintAssignmentRepository.save(assignment);

                                emailService.sendEmail(
                                        adminAlertEmail,
                                        "Urgent: Unresolved Complaint Needs Reassignment - ID: " + complaint.getComplaintId(),
                                        "Dear Admin,\n\n" +
                                                "No available staff member was found to reassign the following complaint.\n" +
                                                "Please manually assign a staff member as soon as possible.\n\n" +
                                                "Complaint Details:\n" +
                                                "Complaint ID  : " + complaint.getComplaintId() + "\n" +
                                                "Description   : " + complaint.getDescription() + "\n" +
                                                "Service Type  : " + complaint.getServiceType() + "\n" +
                                                "Location      : " + complaint.getLocation() + "\n\n" +
                                                "User Details:\n" +
                                                "Name          : " + complaint.getUser().getUserName() + "\n" +
                                                "Email         : " + complaint.getUser().getEmailId() + "\n\n" +
                                                "Please assign a staff member via:\n" +
                                                "POST /admin/assign/complaint/" + complaint.getComplaintId() + "\n\n" +
                                                "- Smart Complaint Management Team"
                                );

                                boolean taskExists = adminComplaintTaskRepository
                                        .findByComplaintIdAndResolvedFalse(complaint.getComplaintId())
                                        .isPresent();

                                if (!taskExists) {
                                    AdminComplaintTask task = new AdminComplaintTask();
                                    task.setComplaintId(complaint.getComplaintId());
                                    task.setUserEmail(complaint.getUser().getEmailId());
                                    task.setUserName(complaint.getUser().getUserName());
                                    task.setAssignedToAdminAt(LocalDateTime.now());
                                    task.setResolved(false);
                                    task.setUserNotifiedOfFailure(false);
                                    adminComplaintTaskRepository.save(task);
                                }

                                emailService.sendEmail(
                                        complaint.getUser().getEmailId(),
                                        "Update on Your Complaint",
                                        "Dear " + complaint.getUser().getUserName() + ",\n\n" +
                                                "Your complaint (ID: " + complaint.getComplaintId() +
                                                ") is being reassigned. We will notify you shortly.\n\n" +
                                                "- Smart Complaint Management Team");
                            }
                    );
        }

        List<ComplaintAssignment> allAssignments =
                complaintAssignmentRepository.findByAssignedToId(staff.getId());
        for (ComplaintAssignment a : allAssignments) {
            a.setAssignedTo(null);
            complaintAssignmentRepository.save(a);
        }

        emailService.sendEmail(adminAlertEmail,
                "Staff Member Removed - Poor Performance",
                "Dear Admin,\n\n" +
                        "Staff member " + staff.getUserName() +
                        " has been removed due to poor performance " +
                        "(ranking score reached 10).\n\n" +
                        "Staff Details:\n" +
                        "Name       : " + staff.getUserName() + "\n" +
                        "Email      : " + staff.getEmailId() + "\n" +
                        "Department : " + (staff.getDept() != null
                        ? staff.getDept().getDepartmentName() : "N/A") + "\n\n" +
                        "All active complaints have been reassigned.\n\n" +
                        "- Smart Complaint Management Team");

        BlacklistedStaff blacklisted = new BlacklistedStaff();
        blacklisted.setEmail(staff.getEmailId());
        blacklisted.setName(staff.getUserName());
        blacklisted.setBlacklistedAt(LocalDateTime.now());
        blacklistedStaffRepository.save(blacklisted);

        emailService.sendEmail(
                staff.getEmailId(),
                "Regarding Your Performance Status",
                "Dear " + staff.getUserName() + ",\n\n" +
                        "We regret to inform you that due to consistent poor performance, " +
                        "you have been removed from the " +
                        (staff.getDept() != null
                                ? staff.getDept().getDepartmentName()
                                : "your") +
                        " department.\n\n" +
                        "Performance Summary:\n" +
                        "Final Ranking Score      : " + staff.getRankingScore() + "\n" +
                        "Total Complaints Handled : " + staff.getTotalComplaintsHandled() + "\n\n" +
                        "We wish you the best in your future endeavors.\n\n" +
                        "- Smart Complaint Management Team"
        );

        userRepository.delete(staff);
    }
}