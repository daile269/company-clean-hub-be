package com.company.company_clean_hub_be.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.company.company_clean_hub_be.entity.PaymentHistory;

@Repository
public interface PaymentHistoryRepository extends JpaRepository<PaymentHistory, Long> {
    List<PaymentHistory> findByPayrollIdOrderByCreatedAtAsc(Long payrollId);

    Integer countByPayrollId(Long payrollId);

    @Query("SELECT MAX(ph.installmentNumber) FROM PaymentHistory ph WHERE ph.payroll.id = :payrollId")
    Integer findMaxInstallmentNumberByPayrollId(@Param("payrollId") Long payrollId);
}
