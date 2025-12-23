package com.company.company_clean_hub_be.repository;

import com.company.company_clean_hub_be.entity.Invoice;
import com.company.company_clean_hub_be.entity.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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

    @Query("SELECT i FROM Invoice i " +
            "LEFT JOIN i.contract c " +
            "LEFT JOIN c.customer cust " +
            "WHERE (:customerCode IS NULL OR :customerCode = '' OR cust.customerCode = :customerCode) " +
            "AND (:month IS NULL OR i.invoiceMonth = :month) " +
            "AND (:year IS NULL OR i.invoiceYear = :year) " +
            "ORDER BY i.invoiceYear DESC, i.invoiceMonth DESC")
    Page<Invoice> findByFilters(@Param("customerCode") String customerCode,
                                @Param("month") Integer month,
                                @Param("year") Integer year,
                                Pageable pageable);

    // Lấy tất cả hóa đơn cùng invoice lines + contract + customer để xuất tổng hợp (theo month/year)
    @Query("SELECT DISTINCT i FROM Invoice i " +
            "LEFT JOIN FETCH i.invoiceLines l " +
            "LEFT JOIN FETCH i.contract c " +
            "LEFT JOIN FETCH c.customer cust " +
            "WHERE i.invoiceMonth = :month AND i.invoiceYear = :year " +
            "ORDER BY cust.name")
    List<Invoice> findAllWithLinesByMonthAndYear(@Param("month") Integer month, @Param("year") Integer year);

    // Variant: for a specific customer
    @Query("SELECT DISTINCT i FROM Invoice i " +
            "LEFT JOIN FETCH i.invoiceLines l " +
            "LEFT JOIN FETCH i.contract c " +
            "LEFT JOIN FETCH c.customer cust " +
            "WHERE i.invoiceMonth = :month AND i.invoiceYear = :year AND cust.id = :customerId " +
            "ORDER BY cust.name")
    List<Invoice> findAllWithLinesByMonthYearAndCustomer(@Param("month") Integer month, @Param("year") Integer year, @Param("customerId") Long customerId);

    // Kiểm tra xem hóa đơn đã tồn tại chưa
    @Query("SELECT CASE WHEN COUNT(i) > 0 THEN true ELSE false END FROM Invoice i " +
            "WHERE i.contract.id = :contractId AND i.invoiceMonth = :month AND i.invoiceYear = :year")
    boolean existsByContractIdAndMonthAndYear(
            @Param("contractId") Long contractId,
            @Param("month") Integer month,
            @Param("year") Integer year
    );
}
