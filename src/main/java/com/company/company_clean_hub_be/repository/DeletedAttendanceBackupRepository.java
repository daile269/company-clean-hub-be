package com.company.company_clean_hub_be.repository;

import com.company.company_clean_hub_be.entity.DeletedAttendanceBackup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeletedAttendanceBackupRepository extends JpaRepository<DeletedAttendanceBackup, Long> {
    
    List<DeletedAttendanceBackup> findByAssignmentId(Long assignmentId);
    
    void deleteByAssignmentId(Long assignmentId);
}
