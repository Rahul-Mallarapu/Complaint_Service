package com.smart_complaint_service.project.Repositories;

import com.smart_complaint_service.project.Entities.BlacklistedStaff;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlacklistedStaffRepository extends JpaRepository<BlacklistedStaff, Long> {
    boolean existsByEmail(String email);
}