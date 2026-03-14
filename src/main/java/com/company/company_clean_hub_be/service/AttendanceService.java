package com.company.company_clean_hub_be.service;

import com.company.company_clean_hub_be.dto.request.AttendanceRequest;
import com.company.company_clean_hub_be.dto.request.AutoAttendanceRequest;
import com.company.company_clean_hub_be.dto.response.AttendanceResponse;
import com.company.company_clean_hub_be.dto.response.PageResponse;
import com.company.company_clean_hub_be.dto.response.TotalDaysResponse;

import com.company.company_clean_hub_be.dto.request.AttendanceCaptureRequest;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

public interface AttendanceService {
    AttendanceResponse createAttendance(AttendanceRequest request);
    List<AttendanceResponse> autoGenerateAttendances(AutoAttendanceRequest request);
    AttendanceResponse getAttendanceById(Long id);
    List<AttendanceResponse> getAllAttendances();
    PageResponse<AttendanceResponse> getAttendancesWithFilter(String keyword, Integer month, Integer year, int page, int pageSize);
    PageResponse<AttendanceResponse> getAttendancesByEmployee(Long employeeId, Integer month, Integer year, int page, int pageSize);
    AttendanceResponse updateAttendance(Long id, AttendanceRequest request);
    void deleteAttendance(Long id);
    void softDeleteAttendance(com.company.company_clean_hub_be.dto.request.AttendanceDeleteRequest request);
    void restoreAttendance(Long id);
    void restoreByDateContractEmployee(com.company.company_clean_hub_be.dto.request.AttendanceRestoreRequest request);
    com.company.company_clean_hub_be.dto.response.PageResponse<com.company.company_clean_hub_be.dto.response.AttendanceResponse> getDeletedAttendances(Long contractId, Long employeeId, Integer month, Integer year, int page, int pageSize);
    TotalDaysResponse getTotalDaysByEmployee(Long employeeId, Integer month, Integer year);
    java.util.Map<Long, java.util.Map<Long, java.util.List<AttendanceResponse>>> getAttendancesGroupedByContractAndEmployee(Integer month, Integer year);
    java.util.Map<Long, java.util.List<AttendanceResponse>> getAttendancesByContractGroupedByEmployee(Long contractId, Integer month, Integer year);
    
    List<AttendanceResponse> getTodayAttendancesByEmployee(Long employeeId);
    AttendanceResponse captureAttendance(AttendanceCaptureRequest request) throws IOException;
    
    // New methods for verification-aware attendance generation
    List<AttendanceResponse> autoGenerateAttendancesWithVerification(AutoAttendanceRequest request);
    AttendanceResponse generateSingleAttendance(Long assignmentId, LocalDate date);
    void generateRemainingAttendances(Long assignmentId, LocalDate fromDate);
}