package com.smart_complaint_service.project.Repositories;

import com.smart_complaint_service.project.Entities.AdminComplaintTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AdminComplaintTaskRepository extends JpaRepository<AdminComplaintTask, Long> {
    List<AdminComplaintTask> findByResolvedFalseAndUserNotifiedOfFailureFalse();
    Optional<AdminComplaintTask> findByComplaintIdAndResolvedFalse(Long complaintId);

    Optional<AdminComplaintTask> findByUserEmailAndUserNotifiedOfFailureTrueAndFailureNotifiedAtBetween(
            String userEmail,
            LocalDateTime start,
            LocalDateTime end
    );
}