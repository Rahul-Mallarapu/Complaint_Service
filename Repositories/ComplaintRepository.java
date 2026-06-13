package com.smart_complaint_service.project.Repositories;

import com.smart_complaint_service.project.Entities.Complaint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {

    List<Complaint> findByCurrentStatus(String status);

    List<Complaint> findByCurrentStatusIn(List<String> statuses);

    @Query(value = """
            SELECT c.* FROM complaint c
            WHERE c.user_id = :userId
            AND c.description = :description
            AND c.current_status IN ('OPEN', 'ASSIGNED', 'IN_PROGRESS', 'PENDING_ADMIN_VERIFICATION')
            """, nativeQuery = true)
    List<Complaint> findActiveComplaintsByUserAndDescription(
            @Param("userId") Long userId,
            @Param("description") String description);

    @Query(value = """
            SELECT c.* FROM complaint c
            WHERE c.user_id = :userId
            AND c.dept_id = :deptId
            AND c.current_status IN ('OPEN', 'ASSIGNED', 'IN_PROGRESS', 'PENDING_ADMIN_VERIFICATION')
            """, nativeQuery = true)
    List<Complaint> findActiveComplaintsByUserAndDept(
            @Param("userId") Long userId,
            @Param("deptId") Long deptId);
}