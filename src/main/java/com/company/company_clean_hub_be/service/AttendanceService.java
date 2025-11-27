package com.company.company_clean_hub_be.service;

import com.company.company_clean_hub_be.dto.request.AttendanceRequest;
import com.company.company_clean_hub_be.dto.request.TemporaryAssignmentRequest;
import com.company.company_clean_hub_be.dto.response.AttendanceResponse;
import com.company.company_clean_hub_be.dto.response.PageResponse;
import com.company.company_clean_hub_be.dto.response.TemporaryAssignmentResponse;
import com.company.company_clean_hub_be.dto.response.TotalDaysResponse;

import java.util.List;

public interface AttendanceService {
    AttendanceResponse createAttendance(AttendanceRequest request);
    List<AttendanceResponse> autoGenerateAttendances(com.company.company_clean_hub_be.dto.request.AutoAttendanceRequest request);
    AttendanceResponse getAttendanceById(Long id);
    List<AttendanceResponse> getAllAttendances();
    PageResponse<AttendanceResponse> getAttendancesWithFilter(String keyword, Integer month, Integer year, int page, int pageSize);
    PageResponse<AttendanceResponse> getAttendancesByEmployee(Long employeeId, Integer month, Integer year, int page, int pageSize);
    AttendanceResponse updateAttendance(Long id, AttendanceRequest request);
    void deleteAttendance(Long id);
    TotalDaysResponse getTotalDaysByEmployee(Long employeeId, Integer month, Integer year);
}
