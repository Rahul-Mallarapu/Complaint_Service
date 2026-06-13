package com.smart_complaint_service.project.Repositories;

import com.smart_complaint_service.project.Entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query(value = "SELECT u.* FROM users u WHERE u.email_id = :emailId", nativeQuery = true)
    Optional<User> findByEmailId(@Param("emailId") String emailId);

    @Query(value = "SELECT u.* FROM users u WHERE u.role = 'ADMIN'", nativeQuery = true)
    User findAdmin();

    @Query(value = """
            SELECT u.* FROM users u
            WHERE u.role = 'STAFF'
            AND u.willing_to_take_complaints = true
            AND u.dept_id = :deptId
            ORDER BY u.active_complaint_count ASC,
                     u.ranking_score ASC,
                     u.total_complaints_handled DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<User> findBestAvailableStaffForDept(@Param("deptId") Long deptId);

    @Query(value = """
            SELECT u.* FROM users u
            WHERE u.role = 'STAFF'
            AND u.willing_to_take_complaints = true
            AND u.dept_id = :deptId
            AND u.id != :excludeStaffId
            ORDER BY u.ranking_score DESC,
                     u.total_complaints_handled ASC,
                     u.active_complaint_count ASC
            LIMIT 1
            """, nativeQuery = true)
    Optional<User> findNextAvailableStaffForDept(
            @Param("deptId") Long deptId,
            @Param("excludeStaffId") Long excludeStaffId);

    @Query(value = """
        SELECT u.* FROM users u
        WHERE u.role = 'STAFF'
        AND u.willing_to_take_complaints = true
        AND u.dept_id = :deptId
        ORDER BY u.ranking_score DESC,
                 u.total_complaints_handled ASC,
                 u.active_complaint_count ASC
        """, nativeQuery = true)
    List<User> findAvailableStaffForDeptOrderedByTieBreakers(
            @Param("deptId") Long deptId);

    @Query(value = """
    SELECT u.* FROM users u
    WHERE u.role = 'STAFF'
    AND u.willing_to_take_complaints = true
    AND u.dept_id = :deptId
    AND u.ranking_score            = :rankingScore
    AND u.total_complaints_handled = :totalHandled
    AND u.active_complaint_count   = :activeCount
    ORDER BY RAND()
    LIMIT 1
    """, nativeQuery = true)
    Optional<User> findRandomTiedStaffForDept(
            @Param("deptId") Long deptId,
            @Param("rankingScore") int rankingScore,
            @Param("totalHandled") int totalHandled,
            @Param("activeCount") int activeCount);


}