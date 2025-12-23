package com.company.company_clean_hub_be.schedule;

import com.company.company_clean_hub_be.entity.Assignment;
import com.company.company_clean_hub_be.entity.AssignmentStatus;
import com.company.company_clean_hub_be.entity.AssignmentType;
import com.company.company_clean_hub_be.entity.Attendance;
import com.company.company_clean_hub_be.entity.Contract;
import com.company.company_clean_hub_be.entity.ContractType;
import com.company.company_clean_hub_be.repository.AssignmentRepository;
import com.company.company_clean_hub_be.repository.AttendanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class AssignmentScheduler {

    private final AssignmentRepository assignmentRepository;
    private final AttendanceRepository attendanceRepository;

    /**
     * Chạy lúc 1h sáng hàng ngày để cập nhật trạng thái các phân công tạm thời đã qua
     * Cron: 0 0 1 * * * = giây phút giờ ngày tháng thứ
     */
    @Scheduled(cron = "0 0 1 * * *")
    @Transactional
    public void updateExpiredTemporaryAssignments() {
        executeUpdateExpiredTemporaryAssignments();
    }

    /**
     * Chạy lúc 2h sáng ngày đầu tiên của mỗi tháng để cập nhật trạng thái phân công cố định đã hết hạn
     * Cron: 0 0 2 1 * * = giây phút giờ ngày tháng thứ (ngày 1 hàng tháng)
     */
    @Scheduled(cron = "0 0 2 1 * *")
    @Transactional
    public void updateExpiredFixedAssignments() {
        executeUpdateExpiredFixedAssignments();
    }

    /**
     * Method public để có thể gọi test từ controller
     */
    @Transactional
    public void executeUpdateExpiredTemporaryAssignments() {
        log.info("=== BẮT ĐẦU QUÉT CẬP NHẬT PHÂN CÔNG TẠM THỜI ===");
        log.info("Thời gian chạy: {}", LocalDateTime.now());

        LocalDate yesterday = LocalDate.now().minusDays(1);
        
        try {
            // Tìm tất cả các phân công TEMPORARY có startDate < hôm nay và status IN_PROGRESS
            List<Assignment> expiredAssignments = assignmentRepository.findExpiredTemporaryAssignments(yesterday);
            
            log.info("Tìm thấy {} phân công tạm thời đã hết hạn", expiredAssignments.size());

            if (!expiredAssignments.isEmpty()) {
                int updatedCount = 0;
                
                for (Assignment assignment : expiredAssignments) {
                    log.info("Cập nhật assignment ID: {} - Employee: {} - StartDate: {} - Status: {} → COMPLETED",
                            assignment.getId(),
                            assignment.getEmployee().getName(),
                            assignment.getStartDate(),
                            assignment.getStatus());
                    
                    assignment.setStatus(AssignmentStatus.COMPLETED);
                    assignment.setUpdatedAt(LocalDateTime.now());
                    assignmentRepository.save(assignment);
                    updatedCount++;
                }
                
                log.info("✓ Đã cập nhật {} phân công tạm thời sang trạng thái COMPLETED", updatedCount);
            } else {
                log.info("Không có phân công tạm thời nào cần cập nhật");
            }

            // --- SUPPORT assignments: complete when no upcoming attendances remain ---
            try {
                List<Assignment> supportAssignments = assignmentRepository.findByAssignmentTypeAndStatus(AssignmentType.SUPPORT, AssignmentStatus.IN_PROGRESS);
                int supportCompleted = 0;
                for (Assignment sup : supportAssignments) {
                    try {
                        Long total = attendanceRepository.countAttendancesByAssignment(sup.getId());
                        java.time.LocalDate last = attendanceRepository.findMaxAttendanceDateByAssignmentId(sup.getId());

                        boolean hasWorkDays = sup.getWorkDays() != null && sup.getWorkDays() > 0;

                        // If workDays present: complete when total >= workDays and last attendance date is before today
                        if (hasWorkDays) {
                            if (total != null && total >= sup.getWorkDays()) {
                                if (last == null || last.isBefore(LocalDate.now())) {
                                    log.info("Cập nhật SUPPORT assignment ID={} -> COMPLETED (workDays met and lastAttendance < today)", sup.getId());
                                    sup.setStatus(AssignmentStatus.COMPLETED);
                                    sup.setUpdatedAt(LocalDateTime.now());
                                    assignmentRepository.save(sup);
                                    supportCompleted++;
                                }
                            }
                        } else {
                            // fallback: behave like previous logic — complete if no upcoming attendances
                            Long upcoming = attendanceRepository.countAttendancesOnOrAfter(sup.getId(), LocalDate.now());
                            if (upcoming == null || upcoming == 0) {
                                log.info("Cập nhật SUPPORT assignment ID={} -> COMPLETED (no upcoming attendances)", sup.getId());
                                sup.setStatus(AssignmentStatus.COMPLETED);
                                sup.setUpdatedAt(LocalDateTime.now());
                                assignmentRepository.save(sup);
                                supportCompleted++;
                            }
                        }
                    } catch (Exception inner) {
                        log.warn("Failed to evaluate SUPPORT assignment id={}: {}", sup.getId(), inner.getMessage());
                    }
                }
                if (supportCompleted > 0) log.info("✓ Đã cập nhật {} SUPPORT assignment sang COMPLETED", supportCompleted);
            } catch (Exception ex) {
                log.warn("Failed to auto-complete SUPPORT assignments: {}", ex.getMessage());
            }
            
        } catch (Exception e) {
            log.error("❌ LỖI khi cập nhật phân công tạm thời: {}", e.getMessage(), e);
        }
        
        log.info("=== KẾT THÚC QUÉT CẬP NHẬT PHÂN CÔNG TẠM THỜI ===\n");
    }

    /**
     * Method public để có thể gọi test từ controller
     */
    @Transactional
    public void executeUpdateExpiredFixedAssignments() {
        log.info("=== BẮT ĐẦU QUÉT CẬP NHẬT PHÂN CÔNG CỐ ĐỊNH ===");
        log.info("Thời gian chạy: {}", LocalDateTime.now());

        LocalDate endOfLastMonth = LocalDate.now().withDayOfMonth(1).minusDays(1);
        
        try {
            // Tìm tất cả các phân công FIXED có startDate <= cuối tháng trước và status IN_PROGRESS
            List<Assignment> expiredAssignments = assignmentRepository.findExpiredFixedAssignments(endOfLastMonth);
            
            log.info("Tìm thấy {} phân công cố định đã hết hạn", expiredAssignments.size());

            if (!expiredAssignments.isEmpty()) {
                int updatedCount = 0;
                
                for (Assignment assignment : expiredAssignments) {
                    log.info("Cập nhật assignment ID: {} - Employee: {} - Type: {} - StartDate: {} - Status: {} → COMPLETED",
                            assignment.getId(),
                            assignment.getEmployee().getName(),
                            assignment.getAssignmentType(),
                            assignment.getStartDate(),
                            assignment.getStatus());
                    
                    assignment.setStatus(AssignmentStatus.COMPLETED);
                    assignment.setUpdatedAt(LocalDateTime.now());
                    assignmentRepository.save(assignment);
                    updatedCount++;
                }
                
                log.info("✓ Đã cập nhật {} phân công cố định sang trạng thái COMPLETED", updatedCount);
            } else {
                log.info("Không có phân công cố định nào cần cập nhật");
            }
            
        } catch (Exception e) {
            log.error("❌ LỖI khi cập nhật phân công cố định: {}", e.getMessage(), e);
        }
        
        log.info("=== KẾT THÚC QUÉT CẬP NHẬT PHÂN CÔNG CỐ ĐỊNH ===\n");
    }

    /**
     * Chạy lúc 1h sáng ngày đầu tiên của mỗi tháng để tự động sinh chấm công cho tháng mới
     * Cron: 0 0 1 1 * * = giây phút giờ ngày tháng thứ (ngày 1 hàng tháng lúc 1h sáng)
     */
    @Scheduled(cron = "0 0 1 1 * *")
    @Transactional
    public void generateMonthlyAttendances() {
        executeGenerateMonthlyAttendances();
    }

    /**
     * Method public để có thể gọi test từ controller
     */
    @Transactional
    public void executeGenerateMonthlyAttendances() {
        log.info("=== BẮT ĐẦU SINH ASSIGNMENT VÀ CHẤM CÔNG THÁNG MỚI ===");
        log.info("Thời gian chạy: {}", LocalDateTime.now());

        LocalDate today = LocalDate.now();
        YearMonth currentMonth = YearMonth.from(today);
        int year = currentMonth.getYear();
        int month = currentMonth.getMonthValue();
        
        try {
            // Tìm tất cả các phân công đang IN_PROGRESS của tháng trước
            YearMonth lastMonth = currentMonth.minusMonths(1);
            List<Assignment> lastMonthAssignments = assignmentRepository.findAll().stream()
                    .filter(a -> a.getStatus() == AssignmentStatus.IN_PROGRESS)
                    .filter(a -> {
                        YearMonth assignmentMonth = YearMonth.from(a.getStartDate());
                        return assignmentMonth.equals(lastMonth);
                    })
                    .toList();
            
            log.info("Tìm thấy {} phân công của tháng {} cần tạo cho tháng mới", 
                    lastMonthAssignments.size(), lastMonth);

            int totalCreatedAssignments = 0;
            int totalGeneratedAttendances = 0;
            int skippedCount = 0;

            for (Assignment oldAssignment : lastMonthAssignments) {
                Contract contract = oldAssignment.getContract();
                
                // Chỉ xử lý hợp đồng MONTHLY
                if (contract.getContractType() != ContractType.MONTHLY_FIXED 
                    && contract.getContractType() != ContractType.MONTHLY_ACTUAL) {
                    continue;
                }

                // Kiểm tra hợp đồng còn hiệu lực không
                if (contract.getEndDate() != null && contract.getEndDate().isBefore(today)) {
                    log.info("Hợp đồng ID {} đã hết hạn, bỏ qua", contract.getId());
                    continue;
                }

                // Kiểm tra xem tháng mới đã có Assignment chưa
                Optional<Assignment> existingAssignment = assignmentRepository
                        .findByEmployeeAndContractAndMonth(
                                oldAssignment.getEmployee().getId(),
                                contract.getId(),
                                year,
                                month
                        );
                
                if (existingAssignment.isPresent()) {
                    log.info("Tháng {}/{} đã có Assignment ID {} cho Employee {} - Contract {}, bỏ qua", 
                            month, year, existingAssignment.get().getId(),
                            oldAssignment.getEmployee().getName(), contract.getId());
                    skippedCount++;
                    continue;
                }

                // Skip automatic generation for SUPPORT assignments
                if (oldAssignment.getAssignmentType() == AssignmentType.SUPPORT) {
                    log.info("Bỏ qua tự động sinh cho Assignment SUPPORT ID={} (sinh thủ công theo dates)", oldAssignment.getId());
                    skippedCount++;
                    continue;
                }

                // Tạo Assignment mới cho tháng mới (copy thông tin từ tháng trước)
                LocalDate firstDayOfMonth = currentMonth.atDay(1);
                Assignment newAssignment = Assignment.builder()
                        .employee(oldAssignment.getEmployee())
                        .contract(contract)
                        .startDate(firstDayOfMonth)
                        .status(AssignmentStatus.IN_PROGRESS)
                        .assignmentType(oldAssignment.getAssignmentType()) // Giữ nguyên type
                        .salaryAtTime(oldAssignment.getSalaryAtTime())
                        .workDays(0) // Reset về 0
                        .plannedDays(oldAssignment.getPlannedDays())
                        .workingDaysPerWeek(oldAssignment.getWorkingDaysPerWeek())
                        .additionalAllowance(oldAssignment.getAdditionalAllowance())
                        .description(oldAssignment.getDescription())
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
                
                Assignment savedAssignment = assignmentRepository.save(newAssignment);
                totalCreatedAssignments++;
                
                log.info("✓ Đã tạo Assignment mới ID {} cho tháng {}/{} (Employee: {}, Type: {})",
                        savedAssignment.getId(), month, year, 
                        savedAssignment.getEmployee().getName(),
                        savedAssignment.getAssignmentType());

                // Xác định ngày kết thúc: min của (cuối tháng, ngày hết hạn hợp đồng)
                LocalDate endOfMonth = currentMonth.atEndOfMonth();
                LocalDate endDate = endOfMonth;
                
                if (contract.getEndDate() != null && contract.getEndDate().isBefore(endDate)) {
                    endDate = contract.getEndDate();
                    log.info("Hợp đồng ID {} sẽ hết hạn ngày {}, chỉ sinh chấm công tới ngày đó", 
                            contract.getId(), endDate);
                }

                // Lấy thông tin từ Assignment mới để sinh chấm công
                BigDecimal workHours = BigDecimal.valueOf(8);
                BigDecimal bonus = BigDecimal.ZERO;
                BigDecimal penalty = BigDecimal.ZERO;
                BigDecimal supportCost = savedAssignment.getAdditionalAllowance() != null 
                        ? savedAssignment.getAdditionalAllowance() 
                        : BigDecimal.ZERO;

                // Sinh chấm công cho từng ngày
                int generatedDays = 0;
                List<DayOfWeek> workingDays = contract.getWorkingDaysPerWeek();
                LocalDate currentDate = firstDayOfMonth;

                while (!currentDate.isAfter(endDate)) {
                    // Kiểm tra có phải ngày làm việc không
                    if (workingDays != null && !workingDays.isEmpty()) {
                        if (!workingDays.contains(currentDate.getDayOfWeek())) {
                            currentDate = currentDate.plusDays(1);
                            continue;
                        }
                    }

                    // Kiểm tra xem attendance đã tồn tại chưa
                        Optional<Attendance> existing = attendanceRepository
                            .findByAssignmentAndEmployeeAndDate(savedAssignment.getId(), savedAssignment.getEmployee().getId(), currentDate);

                        if (existing.isEmpty()) {
                        Attendance attendance = Attendance.builder()
                                .employee(savedAssignment.getEmployee())
                                .assignment(savedAssignment) // Dùng assignment mới
                                .date(currentDate)
                                .workHours(workHours)
                                .bonus(bonus)
                                .penalty(penalty)
                                .supportCost(supportCost)
                                .isOvertime(false)
                                .deleted(false)
                                .description("Tự động sinh cho tháng " + currentMonth)
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build();
                        
                        attendanceRepository.save(attendance);
                        generatedDays++;
                    }

                    currentDate = currentDate.plusDays(1);
                }

                if (generatedDays > 0) {
                    log.info("  → Sinh {} ngày chấm công cho Assignment mới ID {}", 
                            generatedDays, savedAssignment.getId());
                    totalGeneratedAttendances += generatedDays;
                }
            }
            
            log.info("✓ Tổng kết: Đã tạo {} Assignment mới, sinh {} ngày chấm công, bỏ qua {} assignment", 
                    totalCreatedAssignments, totalGeneratedAttendances, skippedCount);
            
        } catch (Exception e) {
            log.error("❌ LỖI khi sinh assignment và chấm công tháng mới: {}", e.getMessage(), e);
        }
        
        log.info("=== KẾT THÚC SINH ASSIGNMENT VÀ CHẤM CÔNG THÁNG MỚI ===\n");
    }
}
