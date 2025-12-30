package com.company.company_clean_hub_be.repository;

import com.company.company_clean_hub_be.entity.CustomerAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerAssignmentRepository extends JpaRepository<CustomerAssignment, Long> {

    /**
     * Tìm tất cả các phân công của một manager
     */
    List<CustomerAssignment> findByManagerId(Long managerId);

    /**
     * Tìm tất cả các manager được phân công cho một customer
     */
    List<CustomerAssignment> findByCustomerId(Long customerId);

    /**
     * Kiểm tra xem một manager đã được phân công cho customer chưa
     */
    boolean existsByManagerIdAndCustomerId(Long managerId, Long customerId);

    /**
     * Tìm một phân công cụ thể
     */
    Optional<CustomerAssignment> findByManagerIdAndCustomerId(Long managerId, Long customerId);

    /**
     * Xóa phân công giữa manager và customer
     */
    void deleteByManagerIdAndCustomerId(Long managerId, Long customerId);

    /**
     * Lấy danh sách customer IDs mà manager được phân công
     */
    @Query("SELECT ca.customer.id FROM CustomerAssignment ca WHERE ca.manager.id = :managerId")
    List<Long> findCustomerIdsByManagerId(@Param("managerId") Long managerId);

    /**
     * Lấy danh sách customers được phân công cho một manager (có phân trang)
     */
    @Query("SELECT ca.customer FROM CustomerAssignment ca WHERE ca.manager.id = :managerId")
    org.springframework.data.domain.Page<com.company.company_clean_hub_be.entity.Customer> findCustomersByManagerId(
            @Param("managerId") Long managerId,
            org.springframework.data.domain.Pageable pageable);
}
