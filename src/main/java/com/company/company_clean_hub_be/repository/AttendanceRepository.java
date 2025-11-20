package com.company.company_clean_hub_be.repository;

import com.company.company_clean_hub_be.entity.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
}
