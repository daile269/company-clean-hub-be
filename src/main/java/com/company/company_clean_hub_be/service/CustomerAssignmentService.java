package com.company.company_clean_hub_be.service;

import com.company.company_clean_hub_be.dto.request.CustomerAssignmentRequest;
import com.company.company_clean_hub_be.dto.response.CustomerAssignmentResponse;
import com.company.company_clean_hub_be.dto.response.CustomerResponse;
import com.company.company_clean_hub_be.dto.response.PageResponse;

import java.util.List;

public interface CustomerAssignmentService {

    /**
     * Phân công khách hàng cho quản lý
     * 
     * @param request    Thông tin phân công
     * @param assignerId ID người thực hiện phân công
     * @return Thông tin phân công đã tạo
     */
    CustomerAssignmentResponse assignCustomer(CustomerAssignmentRequest request, Long assignerId);

    /**
     * Hủy phân công khách hàng
     * 
     * @param managerId   ID quản lý
     * @param customerId  ID khách hàng
     * @param requesterId ID người yêu cầu hủy phân công
     */
    void revokeAssignment(Long managerId, Long customerId, Long requesterId);

    /**
     * Lấy danh sách khách hàng được phân công cho một manager (có phân trang)
     * 
     * @param managerId ID manager
     * @param keyword   Từ khóa tìm kiếm (optional)
     * @param page      Trang hiện tại
     * @param pageSize  Số lượng items mỗi trang
     * @return Danh sách khách hàng có phân trang
     */
    PageResponse<CustomerResponse> getAssignedCustomers(Long managerId, String keyword, int page, int pageSize);

    /**
     * Lấy danh sách khách hàng của user hiện tại (có phân trang hoặc lấy hết)
     * 
     * @param keyword  Từ khóa tìm kiếm (optional)
     * @param page     Trang hiện tại
     * @param pageSize Số lượng items mỗi trang
     * @param all      Nếu true, lấy toàn bộ dữ liệu không phân trang
     * @return Danh sách khách hàng có phân trang hoặc toàn bộ
     */
    PageResponse<CustomerResponse> getMyAssignedCustomers(String keyword, int page, int pageSize, boolean all);

    /**
     * Lấy danh sách phân công của một manager
     * 
     * @param managerId ID manager
     * @return Danh sách phân công
     */
    List<CustomerAssignmentResponse> getAssignmentsByManager(Long managerId);

    /**
     * Lấy danh sách manager được phân công cho một customer
     * 
     * @param customerId ID customer
     * @param role       Mã role để lọc manager (optional)
     * @return Danh sách phân công
     */
    List<CustomerAssignmentResponse> getAssignmentsByCustomer(Long customerId, String role);
}
