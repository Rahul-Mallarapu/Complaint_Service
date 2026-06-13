package com.smart_complaint_service.project.Repositories;

import com.smart_complaint_service.project.Entities.ComplaintAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ComplaintAssignmentRepository extends JpaRepository<ComplaintAssignment, Long> {

    List<ComplaintAssignment> findByAssignedToIdAndActiveTrue(Long staffId);

    Optional<ComplaintAssignment> findByCmpComplaintIdAndActiveTrue(Long complaintId);

    boolean existsByCmpComplaintIdAndActiveTrue(Long complaintId);

    @Query("SELECT COUNT(ca) FROM ComplaintAssignment ca " +
            "WHERE ca.assignedTo.id = :staffId " +
            "AND ca.resolvedWithinTime IS NOT NULL")
    int countUniqueComplaintsHandledByStaff(@Param("staffId") Long staffId);

    List<ComplaintAssignment> findByAssignedToId(Long staffId);
}