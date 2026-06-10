package com.company.company_clean_hub_be.schedule;

import com.company.company_clean_hub_be.entity.Payroll;
import com.company.company_clean_hub_be.entity.PayrollStatus;
import com.company.company_clean_hub_be.entity.Assignment;
import com.company.company_clean_hub_be.entity.Attendance;
import com.company.company_clean_hub_be.repository.PayrollRepository;
import com.company.company_clean_hub_be.repository.AssignmentRepository;
import com.company.company_clean_hub_be.repository.AttendanceRepository;
import com.company.company_clean_hub_be.repository.PaymentHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled job để clean up payroll orphan records
 * (payroll không có attendance hoặc assignment backing data)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PayrollCleanupScheduler {

    private final PayrollRepository payrollRepository;
    private final AttendanceRepository attendanceRepository;
    private final AssignmentRepository assignmentRepository;
    private final PaymentHistoryRepository paymentHistoryRepository;

    /**
     * Chạy mỗi ngày lúc 2h sáng để clean up orphan payrolls
     * Cron: 0 0 2 * * * = giây phút giờ ngày tháng thứ
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupOrphanPayrolls() {
        log.info("=== START cleanupOrphanPayrolls ===");
        LocalDateTime startTime = LocalDateTime.now();

        try {
            // Lấy tất cả payroll chưa thanh toán hoàn toàn
            List<Payroll> allPayrolls = payrollRepository.findAll();
            int totalChecked = 0;
            int totalDeleted = 0;
            int totalSkippedPaid = 0;

            for (Payroll payroll : allPayrolls) {
                totalChecked++;

                // Bỏ qua payroll đã PAID
                if (payroll.getStatus() == PayrollStatus.PAID) {
                    totalSkippedPaid++;
                    continue;
                }

                Long employeeId = payroll.getEmployee() != null ? payroll.getEmployee().getId() : null;
                if (employeeId == null) {
                    log.warn("Payroll {} has no employee, skipping", payroll.getId());
                    continue;
                }

                // Lấy month/year từ created_at
                LocalDateTime createdAt = payroll.getCreatedAt();
                if (createdAt == null) {
                    log.warn("Payroll {} has no createdAt, skipping", payroll.getId());
                    continue;
                }

                Integer month = createdAt.getMonthValue();
                Integer year = createdAt.getYear();

                // Kiểm tra xem còn assignment nào trong tháng/năm này không
                List<Assignment> assignments = assignmentRepository
                        .findDistinctAssignmentsByAttendanceMonthAndEmployee(month, year, employeeId);

                // Kiểm tra xem còn attendance nào trong tháng/năm này không
                List<Attendance> attendances = attendanceRepository
                        .findAttendancesByMonthYearAndEmployee(month, year, employeeId);

                boolean hasData = (assignments != null && !assignments.isEmpty())
                        || (attendances != null && !attendances.isEmpty());

                // Nếu không còn data backing, xóa payroll orphan
                if (!hasData) {
                    try {
                        Long payrollId = payroll.getId();

                        // Xóa payment history trước
                        try {
                            List<com.company.company_clean_hub_be.entity.PaymentHistory> paymentHistories =
                                    paymentHistoryRepository.findByPayrollIdOrderByCreatedAtAsc(payrollId);
                            if (paymentHistories != null && !paymentHistories.isEmpty()) {
                                paymentHistoryRepository.deleteAll(paymentHistories);
                                log.info("Deleted {} payment history records for orphan payrollId={}",
                                        paymentHistories.size(), payrollId);
                            }
                        } catch (Exception ex) {
                            log.warn("Failed to delete payment history for payrollId={}: {}",
                                    payrollId, ex.getMessage());
                        }

                        // Xóa payroll
                        payrollRepository.delete(payroll);
                        totalDeleted++;
                        log.info("Deleted orphan payroll payrollId={} (status={}) for employeeId={}, month={}, year={} - no assignments/attendances found",
                                payrollId, payroll.getStatus(), employeeId, month, year);

                    } catch (Exception ex) {
                        log.error("Failed to delete orphan payroll {}: {}", payroll.getId(), ex.getMessage());
                    }
                }
            }

            LocalDateTime endTime = LocalDateTime.now();
            log.info("=== COMPLETED cleanupOrphanPayrolls === Checked: {}, Deleted: {}, Skipped (PAID): {}, Duration: {}ms",
                    totalChecked, totalDeleted, totalSkippedPaid,
                    java.time.Duration.between(startTime, endTime).toMillis());

        } catch (Exception ex) {
            log.error("Failed to execute cleanupOrphanPayrolls: {}", ex.getMessage(), ex);
        }
    }
}
