package com.company.company_clean_hub_be.repository;

import com.company.company_clean_hub_be.entity.AssignmentHistory;
import com.company.company_clean_hub_be.entity.HistoryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AssignmentHistoryRepository extends JpaRepository<AssignmentHistory, Long> {

    // Lấy lịch sử theo assignment cũ
    List<AssignmentHistory> findByOldAssignmentId(Long oldAssignmentId);

    // Lấy lịch sử theo assignment mới
    List<AssignmentHistory> findByNewAssignmentId(Long newAssignmentId);

    // Lấy lịch sử theo nhân viên bị thay
    List<AssignmentHistory> findByReplacedEmployeeIdOrderByCreatedAtDesc(Long replacedEmployeeId);

    // Lấy lịch sử theo nhân viên thay thế
    List<AssignmentHistory> findByReplacementEmployeeIdOrderByCreatedAtDesc(Long replacementEmployeeId);

    // Lấy lịch sử theo hợp đồng
    List<AssignmentHistory> findByContractIdOrderByCreatedAtDesc(Long contractId);

    // Lấy lịch sử theo trạng thái
    List<AssignmentHistory> findByStatusOrderByCreatedAtDesc(HistoryStatus status);

    // Tìm lịch sử điều động chứa ngày cụ thể và đang ACTIVE
    @Query("SELECT ah FROM AssignmentHistory ah WHERE :date MEMBER OF ah.reassignmentDates AND ah.status = 'ACTIVE'")
    List<AssignmentHistory> findActiveHistoriesByDate(@Param("date") LocalDate date);

    // Tìm lịch sử có thể rollback (ACTIVE và mới nhất)
    @Query("SELECT ah FROM AssignmentHistory ah WHERE ah.id = :id AND ah.status = 'ACTIVE'")
    Optional<AssignmentHistory> findActiveHistoryById(@Param("id") Long id);

    // Lấy lịch sử điều động giữa 2 nhân viên cụ thể
    @Query("SELECT ah FROM AssignmentHistory ah WHERE ah.replacedEmployeeId = :replacedId " +
           "AND ah.replacementEmployeeId = :replacementId " +
           "AND ah.contractId = :contractId " +
           "ORDER BY ah.createdAt DESC")
    List<AssignmentHistory> findReassignmentHistory(
            @Param("replacedId") Long replacedEmployeeId,
            @Param("replacementId") Long replacementEmployeeId,
            @Param("contractId") Long contractId
    );
}
