package com.company.company_clean_hub_be.repository;

import com.company.company_clean_hub_be.entity.Invoice;
import com.company.company_clean_hub_be.entity.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    // Tìm hóa đơn theo contract và tháng/năm
    @Query("SELECT i FROM Invoice i WHERE i.contract.id = :contractId AND i.invoiceMonth = :month AND i.invoiceYear = :year")
    Optional<Invoice> findByContractIdAndMonthAndYear(
            @Param("contractId") Long contractId,
            @Param("month") Integer month,
            @Param("year") Integer year
    );

    // Lấy tất cả hóa đơn của một contract
    @Query("SELECT i FROM Invoice i WHERE i.contract.id = :contractId ORDER BY i.invoiceYear DESC, i.invoiceMonth DESC")
    List<Invoice> findByContractId(@Param("contractId") Long contractId);

    // Lấy tất cả hóa đơn của một customer
    @Query("SELECT i FROM Invoice i WHERE i.contract.customer.id = :customerId ORDER BY i.invoiceYear DESC, i.invoiceMonth DESC")
    List<Invoice> findByCustomerId(@Param("customerId") Long customerId);

    // Lấy hóa đơn theo trạng thái
    @Query("SELECT i FROM Invoice i WHERE i.status = :status ORDER BY i.invoiceYear DESC, i.invoiceMonth DESC")
    List<Invoice> findByStatus(@Param("status") InvoiceStatus status);

    // Lấy hóa đơn theo tháng/năm
    @Query("SELECT i FROM Invoice i WHERE i.invoiceMonth = :month AND i.invoiceYear = :year ORDER BY i.contract.customer.name")
    List<Invoice> findByMonthAndYear(@Param("month") Integer month, @Param("year") Integer year);

    // Kiểm tra xem hóa đơn đã tồn tại chưa
    @Query("SELECT CASE WHEN COUNT(i) > 0 THEN true ELSE false END FROM Invoice i " +
            "WHERE i.contract.id = :contractId AND i.invoiceMonth = :month AND i.invoiceYear = :year")
    boolean existsByContractIdAndMonthAndYear(
            @Param("contractId") Long contractId,
            @Param("month") Integer month,
            @Param("year") Integer year
    );
}
