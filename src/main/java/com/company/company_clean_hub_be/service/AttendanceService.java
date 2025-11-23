package com.company.company_clean_hub_be.service;

import com.company.company_clean_hub_be.entity.Attendance;
import java.util.List;
import java.util.Optional;

public interface AttendanceService {
    List<Attendance> findAll();
    Optional<Attendance> findById(Long id);
    Attendance save(Attendance attendance);
    void deleteById(Long id);
}
