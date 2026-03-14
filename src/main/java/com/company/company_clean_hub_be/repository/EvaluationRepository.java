package com.company.company_clean_hub_be.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.company.company_clean_hub_be.entity.Evaluation;

@Repository
public interface EvaluationRepository extends JpaRepository<Evaluation, Long> {
    Optional<Evaluation> findByAttendanceId(Long attendanceId);
    
    @Query("""
        SELECT e FROM Evaluation e
        LEFT JOIN FETCH e.attendance a
        LEFT JOIN FETCH e.employee emp
        LEFT JOIN FETCH e.evaluatedBy eb
        LEFT JOIN FETCH a.approvedBy ab
        LEFT JOIN FETCH a.assignment assign
        LEFT JOIN FETCH assign.contract c
        LEFT JOIN FETCH assign.assignedBy assignBy
        LEFT JOIN FETCH c.customer cust
        LEFT JOIN FETCH c.services s
        LEFT JOIN FETCH a.assignmentVerification av
        WHERE e.id = :evaluationId
        """)
    Optional<Evaluation> findByIdWithAllRelations(@Param("evaluationId") Long evaluationId);
}
