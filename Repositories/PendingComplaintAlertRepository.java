package com.smart_complaint_service.project.Repositories;

import com.smart_complaint_service.project.Entities.PendingComplaintAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PendingComplaintAlertRepository extends JpaRepository<PendingComplaintAlert, Long> {
    List<PendingComplaintAlert> findByAdminNotifiedFalse();

    Optional<PendingComplaintAlert> findByUserEmailAndUserNotifiedOfClosureTrue(String userEmail);
}
