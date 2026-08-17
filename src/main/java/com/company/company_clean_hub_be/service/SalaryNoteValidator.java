package com.company.company_clean_hub_be.service;

import com.company.company_clean_hub_be.entity.*;
import com.company.company_clean_hub_be.exception.AppException;
import com.company.company_clean_hub_be.exception.ErrorCode;
import com.company.company_clean_hub_be.repository.AssignmentRepository;
import com.company.company_clean_hub_be.repository.AttendanceRepository;
import com.company.company_clean_hub_be.repository.ContractRepository;
import com.company.company_clean_hub_be.repository.SalaryNoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Rule engine cho "Salary Note Validation" (R1/R2/R3).
 *
 * - R1 validateAssignmentType: map AssignmentType ↔ SalaryNoteCategory.
 * - R2 validateSalaryRange: salaryAtTime ∈ [FIXED.amount, TEMPORARY.amount] cho Archetype D.
 * - R3 calculateFixedByDayStreak + shouldNotify: streak ngày làm việc liên tiếp, riêng từng contract.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SalaryNoteValidator {

    private final SalaryNoteRepository salaryNoteRepository;
    private final AssignmentRepository assignmentRepository;
    private final AttendanceRepository attendanceRepository;
    private final ContractRepository contractRepository;

    /**
     * R1 — Chặn sai loại phân công.
     * FIXED_BY_CONTRACT / FIXED_BY_COMPANY → cần MONTHLY_ASSIGNMENT.
     * FIXED_BY_DAY / TEMPORARY           → cần DAILY_ASSIGNMENT.
     * SUPPORT                             → bỏ qua.
     * Không có SalaryNote nào             → bỏ qua.
     */
    public void validateAssignmentType(Contract contract, AssignmentType assignmentType) {
        if (contract == null || assignmentType == null) return;
        if (assignmentType == AssignmentType.SUPPORT) return;

        List<SalaryNote> salaryNotes = salaryNoteRepository.findByContractId(contract.getId());
        if (salaryNotes.isEmpty()) return;

        boolean hasMonthly = salaryNotes.stream()
                .anyMatch(sn -> sn.getCategory() == SalaryNoteCategory.MONTHLY_ASSIGNMENT);
        boolean hasDailyFixed = salaryNotes.stream()
                .anyMatch(sn -> sn.getCategory() == SalaryNoteCategory.DAILY_ASSIGNMENT
                        && sn.getSalaryType() == SalaryNoteType.FIXED);
        boolean hasDailyTemporary = salaryNotes.stream()
                .anyMatch(sn -> sn.getCategory() == SalaryNoteCategory.DAILY_ASSIGNMENT
                        && sn.getSalaryType() == SalaryNoteType.TEMPORARY);

        switch (assignmentType) {
            case FIXED_BY_CONTRACT:
            case FIXED_BY_COMPANY:
                if (!hasMonthly) {
                    throw new AppException(ErrorCode.INVALID_ASSIGNMENT_TYPE);
                }
                break;
            case FIXED_BY_DAY:
                if (!hasDailyFixed) {
                    throw new AppException(ErrorCode.INVALID_ASSIGNMENT_TYPE);
                }
                break;
            case TEMPORARY:
                if (!hasDailyTemporary) {
                    throw new AppException(ErrorCode.INVALID_ASSIGNMENT_TYPE);
                }
                break;
            default:
                break;
        }
    }

    /**
     * R2 — Chặn lương ngoài khoảng [FIXED.amount, TEMPORARY.amount] (Archetype D).
     * Chỉ áp dụng cho loại ngày (FIXED_BY_DAY / TEMPORARY) và khi hợp đồng có đủ
     * DAILY_ASSIGNMENT/FIXED + DAILY_ASSIGNMENT/TEMPORARY.
     */
    public void validateSalaryRange(Contract contract, AssignmentType assignmentType, BigDecimal salaryAtTime) {
        if (contract == null || salaryAtTime == null) return;
        if (assignmentType != AssignmentType.FIXED_BY_DAY
                && assignmentType != AssignmentType.TEMPORARY) {
            return;
        }

        List<SalaryNote> dailyNotes = salaryNoteRepository.findByContractId(contract.getId())
                .stream()
                .filter(sn -> sn.getCategory() == SalaryNoteCategory.DAILY_ASSIGNMENT)
                .toList();

        SalaryNote fixedDaily = dailyNotes.stream()
                .filter(sn -> sn.getSalaryType() == SalaryNoteType.FIXED)
                .findFirst()
                .orElse(null);
        SalaryNote tempDaily = dailyNotes.stream()
                .filter(sn -> sn.getSalaryType() == SalaryNoteType.TEMPORARY)
                .findFirst()
                .orElse(null);

        if (fixedDaily == null || tempDaily == null) return;
        if (fixedDaily.getAmount() == null || tempDaily.getAmount() == null) return;

        BigDecimal min = fixedDaily.getAmount();
        BigDecimal max = tempDaily.getAmount();

        if (salaryAtTime.compareTo(min) < 0 || salaryAtTime.compareTo(max) > 0) {
            throw new AppException(ErrorCode.SALARY_OUT_OF_RANGE);
        }
    }

    /**
     * R3 precondition — phân công FIXED_BY_DAY chỉ được cảnh báo streak khi:
     * 1. Hợp đồng có đủ 2 ghi chú lương DAILY_ASSIGNMENT: FIXED + TEMPORARY (Archetype D).
     * 2. Lương phân công nằm STRICTLY trong khoảng: fixed &lt; salaryAtTime &lt; temporary.
     */
    public boolean isEligibleForStreak(Contract contract, BigDecimal salaryAtTime) {
        if (contract == null || salaryAtTime == null) return false;

        List<SalaryNote> dailyNotes = salaryNoteRepository.findByContractId(contract.getId())
                .stream()
                .filter(sn -> sn.getCategory() == SalaryNoteCategory.DAILY_ASSIGNMENT)
                .toList();

        SalaryNote fixedDaily = dailyNotes.stream()
                .filter(sn -> sn.getSalaryType() == SalaryNoteType.FIXED)
                .findFirst().orElse(null);
        SalaryNote tempDaily = dailyNotes.stream()
                .filter(sn -> sn.getSalaryType() == SalaryNoteType.TEMPORARY)
                .findFirst().orElse(null);

        if (fixedDaily == null || tempDaily == null) return false;
        if (fixedDaily.getAmount() == null || tempDaily.getAmount() == null) return false;

        return salaryAtTime.compareTo(fixedDaily.getAmount()) > 0
                && salaryAtTime.compareTo(tempDaily.getAmount()) < 0;
    }

    /**
     * R3 — Đếm số ngày làm việc liên tiếp của FIXED_BY_DAY (riêng từng contract, bỏ qua cuối tuần/ngày nghỉ).
     */
    public StreakResult calculateFixedByDayStreak(Long employeeId, Long contractId) {
        List<Assignment> assignments = assignmentRepository
                .findByAssignmentTypeAndStatus(AssignmentType.FIXED_BY_DAY, AssignmentStatus.IN_PROGRESS)
                .stream()
                .filter(a -> a.getEmployee() != null && a.getEmployee().getId().equals(employeeId))
                .filter(a -> a.getContract() != null && a.getContract().getId().equals(contractId))
                .toList();

        if (assignments.isEmpty()) return new StreakResult(0, contractId);

        Contract contract = contractRepository.findById(contractId).orElse(null);
        List<DayOfWeek> workingDays = (contract != null
                && contract.getWorkingDaysPerWeek() != null
                && !contract.getWorkingDaysPerWeek().isEmpty())
                ? contract.getWorkingDaysPerWeek()
                : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY);

        List<Long> assignmentIds = assignments.stream().map(Assignment::getId).toList();
        LocalDate today = LocalDate.now();
        List<LocalDate> dates = attendanceRepository
                .findByAssignmentIdInAndDeletedFalse(assignmentIds)
                .stream()
                .map(Attendance::getDate)
                .filter(Objects::nonNull)
                .filter(d -> !d.isAfter(today))
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();

        if (dates.isEmpty()) return new StreakResult(0, contractId);

        int streak = 1;
        for (int i = 0; i < dates.size() - 1; i++) {
            LocalDate current = dates.get(i);
            LocalDate previous = dates.get(i + 1);
            if (hasGapWorkingDay(current, previous, workingDays)) {
                break;
            }
            streak++;
        }

        log.info("[STREAK][FIXED_BY_DAY] employee={}, contract={}, streak={}",
                employeeId, contractId, streak);
        return new StreakResult(streak, contractId);
    }

    /**
     * Giữa 2 ngày chấm công có ngày làm việc nào bị bỏ trống không.
     * - Có → true (đứt streak).
     * - Chỉ toàn cuối tuần/ngày nghỉ → false (liên tục).
     */
    private boolean hasGapWorkingDay(LocalDate current, LocalDate previous, List<DayOfWeek> workingDays) {
        LocalDate cursor = previous.plusDays(1);
        while (cursor.isBefore(current)) {
            if (workingDays.contains(cursor.getDayOfWeek())) {
                return true;
            }
            cursor = cursor.plusDays(1);
        }
        return false;
    }

    /** R3 — báo tại ngày 6, 11, 16, 21... (streak ≥ 6 và streak % 5 == 1). */
    public boolean shouldNotify(int streakDays) {
        return streakDays >= 6 && streakDays % 5 == 1;
    }

    public record StreakResult(int streakDays, Long contractId) {
    }
}
