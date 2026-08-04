package com.company.company_clean_hub_be.service.impl;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.company_clean_hub_be.dto.request.EmployeeRequest;
import com.company.company_clean_hub_be.dto.response.EmployeeExportDto;
import com.company.company_clean_hub_be.dto.response.EmployeeResponse;
import com.company.company_clean_hub_be.dto.response.PageResponse;
import com.company.company_clean_hub_be.entity.Employee;
import com.company.company_clean_hub_be.entity.EmploymentType;
import com.company.company_clean_hub_be.entity.Role;
import com.company.company_clean_hub_be.entity.User;
import com.company.company_clean_hub_be.exception.AppException;
import com.company.company_clean_hub_be.exception.ErrorCode;
import com.company.company_clean_hub_be.entity.WorkSchedule;
import com.company.company_clean_hub_be.entity.WorkScheduleReason;
import com.company.company_clean_hub_be.entity.WorkScheduleStatus;
import com.company.company_clean_hub_be.repository.EmployeeRepository;
import com.company.company_clean_hub_be.repository.RoleRepository;
import com.company.company_clean_hub_be.repository.UserRepository;
import com.company.company_clean_hub_be.repository.WorkScheduleRepository;
import com.company.company_clean_hub_be.service.EmployeeService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class EmployeeServiceImpl implements EmployeeService {
        private final EmployeeRepository employeeRepository;
        private final RoleRepository roleRepository;
        private final PasswordEncoder passwordEncoder;
        private final UserRepository userRepository;
        private final com.company.company_clean_hub_be.service.NotificationService notificationService;
        private final com.company.company_clean_hub_be.repository.AssignmentRepository assignmentRepository;
        private final com.company.company_clean_hub_be.repository.AttendanceRepository attendanceRepository;
        private final WorkScheduleRepository workScheduleRepository;
        private final com.company.company_clean_hub_be.service.EmployeeImageService employeeImageService;
        private final com.company.company_clean_hub_be.service.FileStorageService fileStorageService;

        @Override
        public String generateEmployeeCode(EmploymentType employmentType) {
                String prefix = employmentType == EmploymentType.COMPANY_STAFF ? "NVVP" : "NV";

                Pageable pageable = PageRequest.of(0, 1);
                List<String> existingCodes = employeeRepository.findTopByEmployeeCodeStartingWith(prefix, pageable);

                int nextNumber = 1;
                if (!existingCodes.isEmpty()) {
                        String lastCode = existingCodes.get(0);
                        try {
                                String numberPart = lastCode.substring(prefix.length());
                                nextNumber = Integer.parseInt(numberPart) + 1;
                        } catch (Exception e) {
                                log.warn("Cannot parse employee code: {}", lastCode);
                        }
                }

                String generatedCode = prefix + String.format("%06d", nextNumber);

                int attempts = 0;
                final int MAX_ATTEMPTS = 1000;
                while (employeeRepository.existsByEmployeeCode(generatedCode)) {
                        nextNumber++;
                        generatedCode = prefix + String.format("%06d", nextNumber);
                        attempts++;
                        if (attempts >= MAX_ATTEMPTS) {
                                log.error("Failed to generate unique employee code for prefix {} after {} attempts",
                                                prefix,
                                                MAX_ATTEMPTS);
                                throw new AppException(ErrorCode.EMPLOYEE_CODE_ALREADY_EXISTS);
                        }
                }

                log.info("Generated employee code: {} for type: {}", generatedCode, employmentType);
                return generatedCode;
        }

        @Override
        public List<EmployeeResponse> getAllEmployees() {
                log.info("getAllEmployees requested");
                return employeeRepository.findAll().stream()
                                .map(this::mapToResponse)
                                .collect(Collectors.toList());
        }

        @Override
        public PageResponse<EmployeeResponse> getEmployeesWithFilter(String keyword,
                        com.company.company_clean_hub_be.entity.EmploymentType employmentType,
                        String province, Boolean unassigned, int page, int pageSize) {
                log.info("getEmployeesWithFilter requested: keyword='{}', employmentType={}, province='{}', unassigned={}, page={}, pageSize={}",
                                keyword, employmentType, province, unassigned, page, pageSize);
                Pageable pageable = PageRequest.of(page, pageSize, Sort.by("employeeCode").descending());
                Page<Employee> employeePage = employeeRepository.findByFilters(keyword, employmentType, province, unassigned, pageable);

                List<EmployeeResponse> employees = employeePage.getContent().stream()
                                .map(this::mapToResponse)
                                .collect(Collectors.toList());

                return PageResponse.<EmployeeResponse>builder()
                                .content(employees)
                                .page(employeePage.getNumber())
                                .pageSize(employeePage.getSize())
                                .totalElements(employeePage.getTotalElements())
                                .totalPages(employeePage.getTotalPages())
                                .first(employeePage.isFirst())
                                .last(employeePage.isLast())
                                .build();
        }

        @Override
        public EmployeeResponse getEmployeeById(Long id) {
                log.info("getEmployeeById requested: id={}", id);
                Employee employee = employeeRepository.findById(id)
                                .orElseThrow(() -> new AppException(ErrorCode.EMPLOYEE_NOT_FOUND));
                return mapToResponse(employee);
        }

        @Override
        public EmployeeResponse createEmployee(EmployeeRequest request) {
                String username = org.springframework.security.core.context.SecurityContextHolder
                                .getContext().getAuthentication().getName();
                log.info("createEmployee by {}: username={}, phone={}, employeeCode={}", username, request.getUsername(),
                                request.getPhone(), request.getEmployeeCode());

                // Nếu username rỗng hoặc chưa nhập, tự động lấy phone làm username
                if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
                        request.setUsername(request.getPhone());
                }

                // Nếu employeeCode rỗng, tự động sinh mã nhân viên
                if (request.getEmployeeCode() == null || request.getEmployeeCode().trim().isEmpty()) {
                        request.setEmployeeCode(generateEmployeeCode(
                                        request.getEmploymentType() != null ? request.getEmploymentType() : EmploymentType.CONTRACT_STAFF
                        ));
                }

                // Kiểm tra trùng username trong bảng employees & users
                if (employeeRepository.existsByUsername(request.getUsername()) || userRepository.existsByUsername(request.getUsername())) {
                        throw new AppException(ErrorCode.USERNAME_ALREADY_EXISTS);
                }

                // Kiểm tra trùng phone trong bảng employees & users
                if (request.getPhone() != null && !request.getPhone().trim().isEmpty()) {
                        if (employeeRepository.existsByPhone(request.getPhone()) || userRepository.existsByPhone(request.getPhone())) {
                                throw new AppException(ErrorCode.PHONE_ALREADY_EXISTS);
                        }
                }

                // Kiểm tra trùng bankAccount (nếu có nhập)
                if (request.getBankAccount() != null && !request.getBankAccount().trim().isEmpty()) {
                        if (employeeRepository.existsByBankAccount(request.getBankAccount().trim())) {
                                throw new AppException(ErrorCode.BANK_ACCOUNT_ALREADY_EXISTS);
                        }
                }

                // Kiểm tra trùng employeeCode
                if (employeeRepository.existsByEmployeeCode(request.getEmployeeCode())) {
                        throw new AppException(ErrorCode.EMPLOYEE_CODE_ALREADY_EXISTS);
                }

                // Kiểm tra trùng CCCD (nếu có nhập)
                if (request.getCccd() != null && !request.getCccd().trim().isEmpty()) {
                        if (employeeRepository.existsByCccd(request.getCccd().trim())) {
                                throw new AppException(ErrorCode.CCCD_ALREADY_EXISTS);
                        }
                }

                // Nếu người tạo là Quản lý vùng (code = 'QLV') thì không được thêm Nhân viên
                // văn phòng — thông tin role lấy từ bảng `users`
                User creator = userRepository.findByUsername(username)
                                .orElseThrow(() -> new AppException(ErrorCode.USER_IS_NOT_EXISTS));
                if (creator.getRole() != null && "QLV".equalsIgnoreCase(creator.getRole().getCode())
                                && request.getEmploymentType() == com.company.company_clean_hub_be.entity.EmploymentType.COMPANY_STAFF) {
                        throw new AppException(ErrorCode.FORBIDDEN);
                }

                Role role = roleRepository.findById(request.getRoleId())
                                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));

                String finalCccd = (request.getCccd() != null && !request.getCccd().trim().isEmpty()) ? request.getCccd().trim() : null;
                String finalBankAccount = (request.getBankAccount() != null && !request.getBankAccount().trim().isEmpty()) ? request.getBankAccount().trim() : null;

                Employee employee = Employee.builder()
                                .employeeCode(request.getEmployeeCode())
                                .username(request.getUsername())
                                .password(passwordEncoder.encode(request.getPassword()))
                                .phone(request.getPhone())
                                .role(role)
                                .status(request.getStatus())
                                .cccd(finalCccd)
                                .address(request.getAddress())
                                .name(request.getName())
                                .bankAccount(finalBankAccount)
                                .bankName(request.getBankName())
                                .description(request.getDescription())
                                .employmentType(request.getEmploymentType())
                                .monthlySalary(request.getMonthlySalary())
                                .allowance(request.getAllowance())
                                .insuranceSalary(request.getInsuranceSalary())
                                // [DEPRECATED] .monthlyAdvanceLimit(request.getMonthlyAdvanceLimit())
                                .monthlySupport(request.getMonthlySupport())
                                .cccdFrontImage(request.getCccdFrontImage())
                                .cccdBackImage(request.getCccdBackImage())
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build();

                Employee savedEmployee = employeeRepository.save(employee);
                log.info("createEmployee completed by {}: employeeId={}", username, savedEmployee.getId());

                // Gửi notification cho Quản lý tổng (QLT1)
                try {
                        java.util.List<User> managers = userRepository.findActiveUsersByRoleCode("QLT1");
                        log.info("[NOTIFY][NEW_EMPLOYEE] Found {} manager(s) with role QLT1 to notify",
                                        managers.size());
                        if (managers.isEmpty()) {
                                log.warn("[NOTIFY][NEW_EMPLOYEE] No QLT1 managers found — notification will NOT be sent for employeeId={}",
                                                savedEmployee.getId());
                        }
                        String createdTime = LocalDateTime.now()
                                        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy"));
                        for (User manager : managers) {
                                log.info("[NOTIFY][NEW_EMPLOYEE] Sending to userId={} ({})",
                                                manager.getId(), manager.getUsername());
                                notificationService.createNotification(
                                                manager,
                                                com.company.company_clean_hub_be.entity.NotificationType.NEW_EMPLOYEE_CREATED,
                                                "[NV MOI] Nhân viên mới được thêm vào hệ thống",
                                                String.format("Nhân viên %s (%s) vừa được thêm bởi %s vào lúc %s.",
                                                                savedEmployee.getName(),
                                                                savedEmployee.getEmployeeCode(),
                                                                username,
                                                                createdTime),
                                                savedEmployee.getId(),
                                                null,
                                                null);
                                log.info("[NOTIFY][NEW_EMPLOYEE] ✅ Sent successfully to userId={}", manager.getId());
                        }
                } catch (Exception e) {
                        log.warn("[NOTIFY][NEW_EMPLOYEE] ❌ Failed for employeeId={}: {}",
                                        savedEmployee.getId(), e.getMessage(), e);
                }

                return mapToResponse(savedEmployee);
        }

        @Override
        public EmployeeResponse updateEmployee(Long id, EmployeeRequest request) {
                String username = org.springframework.security.core.context.SecurityContextHolder
                                .getContext().getAuthentication().getName();
                log.info("updateEmployee by {}: id={}, employeeCode={}", username, id, request.getEmployeeCode());
                Employee employee = employeeRepository.findById(id)
                                .orElseThrow(() -> new AppException(ErrorCode.EMPLOYEE_NOT_FOUND));

                // Kiểm tra trùng phone (ngoại trừ chính nó)
                if (request.getPhone() != null && !request.getPhone().trim().isEmpty() 
                                && (employeeRepository.existsByPhoneAndIdNot(request.getPhone(), id) || userRepository.existsByPhoneAndIdNot(request.getPhone(), id))) {
                        throw new AppException(ErrorCode.PHONE_ALREADY_EXISTS);
                }

                // Kiểm tra trùng bankAccount (nếu có nhập, ngoại trừ chính nó)
                if (request.getBankAccount() != null && !request.getBankAccount().trim().isEmpty()
                                && employeeRepository.existsByBankAccountAndIdNot(request.getBankAccount().trim(), id)) {
                        throw new AppException(ErrorCode.BANK_ACCOUNT_ALREADY_EXISTS);
                }

                // Kiểm tra trùng employeeCode (ngoại trừ chính nó)
                if (request.getEmployeeCode() != null && !request.getEmployeeCode().trim().isEmpty()
                                && employeeRepository.existsByEmployeeCodeAndIdNot(request.getEmployeeCode(), id)) {
                        throw new AppException(ErrorCode.EMPLOYEE_CODE_ALREADY_EXISTS);
                }

                // Kiểm tra trùng CCCD (nếu có nhập, ngoại trừ chính nó)
                if (request.getCccd() != null && !request.getCccd().trim().isEmpty() 
                                && employeeRepository.existsByCccdAndIdNot(request.getCccd().trim(), id)) {
                        throw new AppException(ErrorCode.CCCD_ALREADY_EXISTS);
                }

                // Nếu người cập nhật là Quản lý vùng (code = 'QLV')
                User updater = userRepository.findByUsername(username)
                                .orElseThrow(() -> new AppException(ErrorCode.USER_IS_NOT_EXISTS));
                if (updater.getRole() != null && "QLV".equalsIgnoreCase(updater.getRole().getCode())) {
                        LocalDateTime createdAt = employee.getCreatedAt();
                        LocalDateTime now = LocalDateTime.now();
                        java.time.LocalDate today = now.toLocalDate();
                        java.time.LocalDate employeeCreatedDate = createdAt.toLocalDate();

                        // Kiểm tra 1: Nếu nhân viên được tạo trước hôm nay thì không được sửa
                        if (employeeCreatedDate.isBefore(today)) {
                                log.warn("QLV cannot update employee created before today: employeeId={}, createdDate={}",
                                                id, employeeCreatedDate);
                                throw new AppException(ErrorCode.FORBIDDEN);
                        }

                        // Kiểm tra 2: Nếu tạo trong hôm nay nhưng quá 1 tiếng
                        long hoursSinceCreation = java.time.Duration.between(createdAt, now).toHours();
                        if (hoursSinceCreation >= 1) {
                                // Quá 1 giờ
                                if (employee.getEmploymentType() == EmploymentType.CONTRACT_STAFF) {
                                        // Nhân viên hợp đồng: không được sửa gì cả (advance update qua payroll API)
                                        log.warn("QLV cannot update contract employee after 1 hour: employeeId={}, createdAt={}, hoursSince={}",
                                                        id, createdAt, hoursSinceCreation);
                                        throw new AppException(ErrorCode.EMPLOYEE_UPDATE_TIME_EXPIRED);
                                }

                                // Nhân viên văn phòng: chỉ cho phép update monthlyAdvanceLimit
                                // Kiểm tra xem có thay đổi field nào khác không
                                boolean hasOtherChanges = !request.getName().equals(employee.getName()) ||
                                                !request.getPhone().equals(employee.getPhone()) ||
                                                !request.getCccd().equals(employee.getCccd()) ||
                                                !request.getAddress().equals(employee.getAddress()) ||
                                                !request.getBankAccount().equals(employee.getBankAccount()) ||
                                                !request.getBankName().equals(employee.getBankName()) ||
                                                !request.getEmploymentType().equals(employee.getEmploymentType()) ||
                                                !request.getStatus().equals(employee.getStatus()) ||
                                                (request.getMonthlySalary() != null && !request
                                                                .getMonthlySalary().equals(employee.getMonthlySalary()))
                                                ||
                                                (request.getAllowance() != null && !request.getAllowance()
                                                                .equals(employee.getAllowance()))
                                                ||
                                                (request.getInsuranceSalary() != null && !request.getInsuranceSalary()
                                                                .equals(employee.getInsuranceSalary()));

                                if (hasOtherChanges) {
                                        log.warn("QLV can only update advance limit after 1 hour for company staff: employeeId={}, hoursSince={}",
                                                        id, hoursSinceCreation);
                                        throw new AppException(ErrorCode.EMPLOYEE_UPDATE_TIME_EXPIRED_EXCEPT_ADVANCE);
                                }
                        }
                }

                Role role = roleRepository.findById(request.getRoleId())
                                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));

                // employee.setUsername(request.getUsername());
                // if (request.getPassword() != null && !request.getPassword().isEmpty()) {
                // employee.setPassword(passwordEncoder.encode(request.getPassword()));
                // }
                employee.setPhone(request.getPhone());
                employee.setRole(role);
                employee.setStatus(request.getStatus());
                employee.setCccd(request.getCccd());
                // Không cho phép sửa employeeCode vì đã tự động sinh
                employee.setAddress(request.getAddress());
                employee.setName(request.getName());
                employee.setBankAccount(request.getBankAccount());
                employee.setBankName(request.getBankName());
                employee.setDescription(request.getDescription());
                employee.setEmploymentType(request.getEmploymentType());
                employee.setMonthlySalary(request.getMonthlySalary());
                employee.setAllowance(request.getAllowance());
                employee.setInsuranceSalary(request.getInsuranceSalary());
                // [DEPRECATED] employee.setMonthlyAdvanceLimit(request.getMonthlyAdvanceLimit());
                employee.setMonthlySupport(request.getMonthlySupport());
                if (request.getCccdFrontImage() != null) {
                        String front = request.getCccdFrontImage().trim();
                        String oldFront = employee.getCccdFrontImage();
                        if (front.isEmpty()) {
                                if (oldFront != null && !oldFront.isEmpty()) {
                                        try { fileStorageService.deleteFile(oldFront); } catch (Exception e) { log.warn("Failed to delete old front CCCD image: {}", oldFront, e); }
                                }
                                employee.setCccdFrontImage(null);
                        } else {
                                if (oldFront != null && !oldFront.isEmpty() && !oldFront.equals(front)) {
                                        try { fileStorageService.deleteFile(oldFront); } catch (Exception e) { log.warn("Failed to delete old front CCCD image: {}", oldFront, e); }
                                }
                                employee.setCccdFrontImage(front);
                        }
                }
                if (request.getCccdBackImage() != null) {
                        String back = request.getCccdBackImage().trim();
                        String oldBack = employee.getCccdBackImage();
                        if (back.isEmpty()) {
                                if (oldBack != null && !oldBack.isEmpty()) {
                                        try { fileStorageService.deleteFile(oldBack); } catch (Exception e) { log.warn("Failed to delete old back CCCD image: {}", oldBack, e); }
                                }
                                employee.setCccdBackImage(null);
                        } else {
                                if (oldBack != null && !oldBack.isEmpty() && !oldBack.equals(back)) {
                                        try { fileStorageService.deleteFile(oldBack); } catch (Exception e) { log.warn("Failed to delete old back CCCD image: {}", oldBack, e); }
                                }
                                employee.setCccdBackImage(back);
                        }
                }
                employee.setUpdatedAt(LocalDateTime.now());

                Employee updatedEmployee = employeeRepository.save(employee);
                log.info("updateEmployee completed by {}: id={}", username, updatedEmployee.getId());
                return mapToResponse(updatedEmployee);
        }

        @Override
        public EmployeeResponse uploadCccdImages(Long id, org.springframework.web.multipart.MultipartFile frontFile, org.springframework.web.multipart.MultipartFile backFile) throws java.io.IOException {
                log.info("EmployeeService.uploadCccdImages - start: employeeId={}", id);
                Employee employee = employeeRepository.findById(id)
                                .orElseThrow(() -> new AppException(ErrorCode.EMPLOYEE_NOT_FOUND));

                if (frontFile != null && !frontFile.isEmpty()) {
                        String oldFront = employee.getCccdFrontImage();
                        if (oldFront != null && !oldFront.isEmpty()) {
                                try { fileStorageService.deleteFile(oldFront); } catch (Exception e) { log.warn("Failed to delete old front CCCD image: {}", oldFront, e); }
                        }
                        String frontPublicId = fileStorageService.storeFile(frontFile);
                        employee.setCccdFrontImage(frontPublicId);
                }
                if (backFile != null && !backFile.isEmpty()) {
                        String oldBack = employee.getCccdBackImage();
                        if (oldBack != null && !oldBack.isEmpty()) {
                                try { fileStorageService.deleteFile(oldBack); } catch (Exception e) { log.warn("Failed to delete old back CCCD image: {}", oldBack, e); }
                        }
                        String backPublicId = fileStorageService.storeFile(backFile);
                        employee.setCccdBackImage(backPublicId);
                }

                Employee updated = employeeRepository.save(employee);
                return mapToResponse(updated);
        }

        @Override
        public void deleteEmployee(Long id) {
                String username = org.springframework.security.core.context.SecurityContextHolder
                                .getContext().getAuthentication().getName();
                log.info("deleteEmployee requested by {}: id={}", username, id);
                if (!employeeRepository.existsById(id)) {
                        throw new AppException(ErrorCode.EMPLOYEE_NOT_FOUND);
                }
                employeeRepository.deleteById(id);
                log.info("deleteEmployee completed: id={}", id);
        }

        private EmployeeResponse mapToResponse(Employee employee) {
                // Lấy tên các khách hàng đang làm việc (IN_PROGRESS)
                List<com.company.company_clean_hub_be.entity.Customer> activeCustomers =
                        assignmentRepository.findActiveCustomersByEmployee(employee.getId());
                String currentCustomerName = activeCustomers.isEmpty() ? null :
                        activeCustomers.stream()
                                .map(c -> c.getName())
                                .collect(Collectors.joining(", "));

                Long currentCustomerId = activeCustomers.isEmpty() ? null : activeCustomers.get(0).getId();

                return EmployeeResponse.builder()
                                .id(employee.getId())
                                .username(employee.getUsername())
                                .phone(employee.getPhone())
                                .email(employee.getEmail())
                                .roleId(employee.getRole() != null ? employee.getRole().getId() : null)
                                .roleName(employee.getRole() != null ? employee.getRole().getName() : null)
                                .status(employee.getStatus())
                                .employeeCode(employee.getEmployeeCode())
                                .cccd(employee.getCccd())
                                .address(employee.getAddress())
                                .name(employee.getName())
                                .bankAccount(employee.getBankAccount())
                                .bankName(employee.getBankName())
                                .description(employee.getDescription())
                                .employmentType(employee.getEmploymentType())
                                .monthlySalary(employee.getMonthlySalary())
                                .allowance(employee.getAllowance())
                                .insuranceSalary(employee.getInsuranceSalary())
                                // [DEPRECATED] .monthlyAdvanceLimit(employee.getMonthlyAdvanceLimit())
                                .monthlySupport(employee.getMonthlySupport())
                                .cccdFrontImage(employee.getCccdFrontImage())
                                .cccdBackImage(employee.getCccdBackImage())
                                .createdAt(employee.getCreatedAt())
                                .updatedAt(employee.getUpdatedAt())
                                .currentCustomerName(currentCustomerName)
                                .currentCustomerId(currentCustomerId)
                                .build();
        }

        @Override
        public List<EmployeeExportDto> getAllEmployeesForExport() {
                log.info("getAllEmployeesForExport requested");
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
                return employeeRepository.findAll().stream()
                                .map(employee -> EmployeeExportDto.builder()
                                                .id(employee.getId())
                                                .employeeCode(employee.getEmployeeCode())
                                                .name(employee.getName())
                                                .username(employee.getUsername())
                                                .email(employee.getEmail())
                                                .phone(employee.getPhone())
                                                .address(employee.getAddress())
                                                .cccd(employee.getCccd())
                                                .bankAccount(employee.getBankAccount())
                                                .bankName(employee.getBankName())
                                                .description(employee.getDescription())
                                                .createdAt(employee.getCreatedAt() != null
                                                                ? employee.getCreatedAt().format(formatter)
                                                                : "")
                                                .updatedAt(employee.getUpdatedAt() != null
                                                                ? employee.getUpdatedAt().format(formatter)
                                                                : "")
                                                .build())
                                .collect(Collectors.toList());
        }

        @Override
        public List<EmployeeExportDto> getEmployeesForExportByType(
                        com.company.company_clean_hub_be.entity.EmploymentType employmentType) {
                log.info("getEmployeesForExportByType requested: employmentType={}", employmentType);
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
                return employeeRepository.findByEmploymentType(employmentType).stream()
                                .map(employee -> EmployeeExportDto.builder()
                                                .id(employee.getId())
                                                .employeeCode(employee.getEmployeeCode())
                                                .name(employee.getName())
                                                .username(employee.getUsername())
                                                .email(employee.getEmail())
                                                .phone(employee.getPhone())
                                                .address(employee.getAddress())
                                                .cccd(employee.getCccd())
                                                .bankAccount(employee.getBankAccount())
                                                .bankName(employee.getBankName())
                                                .description(employee.getDescription())
                                                .createdAt(employee.getCreatedAt() != null
                                                                ? employee.getCreatedAt().format(formatter)
                                                                : "")
                                                .updatedAt(employee.getUpdatedAt() != null
                                                                ? employee.getUpdatedAt().format(formatter)
                                                                : "")
                                                .build())
                                .collect(Collectors.toList());
        }

        // [DEPRECATED] Replaced by advanceNoteSummary from Assignment.advanceNote
        // @Override
        // public EmployeeResponse updateAdvanceSalary(Long id, java.math.BigDecimal monthlyAdvanceLimit) {
        //         String username = org.springframework.security.core.context.SecurityContextHolder
        //                         .getContext().getAuthentication().getName();
        //         log.info("updateAdvanceSalary by {}: id={}, monthlyAdvanceLimit={}", username, id, monthlyAdvanceLimit);
        //
        //         Employee employee = employeeRepository.findById(id)
        //                         .orElseThrow(() -> new AppException(ErrorCode.EMPLOYEE_NOT_FOUND));
        //
        //         // Kiểm tra loại nhân viên phải là COMPANY_STAFF
        //         if (employee.getEmploymentType() != EmploymentType.COMPANY_STAFF) {
        //                 log.warn("Cannot update advance salary for non-company staff: employeeId={}, type={}",
        //                                 id, employee.getEmploymentType());
        //                 throw new AppException(ErrorCode.FORBIDDEN);
        //         }
        //
        //         // Cập nhật chỉ trường monthlyAdvanceLimit
        //         employee.setMonthlyAdvanceLimit(monthlyAdvanceLimit);
        //         employee.setUpdatedAt(LocalDateTime.now());
        //
        //         Employee updatedEmployee = employeeRepository.save(employee);
        //         log.info("updateAdvanceSalary completed by {}: id={}", username, updatedEmployee.getId());
        //         return mapToResponse(updatedEmployee);
        // }

        @Override
        public EmployeeResponse takeCompanyLeave(Long id, java.time.LocalDate leaveDate) {
                Employee employee = employeeRepository.findById(id)
                                .orElseThrow(() -> new AppException(ErrorCode.EMPLOYEE_NOT_FOUND));

                List<com.company.company_clean_hub_be.entity.Assignment> assignments = assignmentRepository.findByEmployeeId(id);
                boolean foundAttendance = false;
                
                for (com.company.company_clean_hub_be.entity.Assignment asn : assignments) {
                        if (asn.getAssignmentType() == com.company.company_clean_hub_be.entity.AssignmentType.FIXED_BY_COMPANY) {
                                java.util.Optional<com.company.company_clean_hub_be.entity.Attendance> attOpt = 
                                        attendanceRepository.findByAssignmentAndEmployeeAndDate(asn.getId(), id, leaveDate);
                                if (attOpt.isPresent()) {
                                        foundAttendance = true;
                                        com.company.company_clean_hub_be.entity.Attendance att = attOpt.get();
                                        att.setDeleted(true);
                                        att.setDeletedAt(LocalDateTime.now());
                                        att.setDescription("Nghỉ phép tự động (Company)");
                                        attendanceRepository.save(att);
                                        log.info("Marked attendance deleted for company assignmentId={} date={}", asn.getId(), leaveDate);
                                }
                        }
                }
                
                if (!foundAttendance) {
                        throw new AppException(ErrorCode.ATTENDANCE_NOT_FOUND);
                }
                
                return mapToResponse(employee);
        }

        @Override
        public EmployeeResponse cancelCompanyLeave(Long id, java.time.LocalDate leaveDate) {
                Employee employee = employeeRepository.findById(id)
                                .orElseThrow(() -> new AppException(ErrorCode.EMPLOYEE_NOT_FOUND));

                List<com.company.company_clean_hub_be.entity.Assignment> assignments = assignmentRepository.findByEmployeeId(id);
                for (com.company.company_clean_hub_be.entity.Assignment asn : assignments) {
                        if (asn.getAssignmentType() == com.company.company_clean_hub_be.entity.AssignmentType.FIXED_BY_COMPANY) {
                                java.util.Optional<com.company.company_clean_hub_be.entity.Attendance> attOpt = 
                                        attendanceRepository.findDeletedByAssignmentAndEmployeeAndDate(asn.getId(), id, leaveDate);
                                if (attOpt.isPresent()) {
                                        com.company.company_clean_hub_be.entity.Attendance att = attOpt.get();
                                        att.setDeleted(false);
                                        att.setDeletedAt(null);
                                        att.setDescription("");
                                        attendanceRepository.save(att);
                                        log.info("Restored attendance for company assignmentId={} date={}", asn.getId(), leaveDate);
                                }
                        }
                }
                return mapToResponse(employee);
        }

        @Override
        public EmployeeResponse resignOfficeWork(Long id, java.time.LocalDate resignDate) {
                log.info("Starting resignOfficeWork for employeeId={}, resignDate={}", id, resignDate);
                Employee employee = employeeRepository.findById(id)
                                .orElseThrow(() -> new AppException(ErrorCode.EMPLOYEE_NOT_FOUND));

                List<com.company.company_clean_hub_be.entity.Assignment> assignments = assignmentRepository.findByEmployeeId(id);
                log.info("Found {} total assignments for employeeId={}", assignments.size(), id);
                
                int terminatedCount = 0;
                for (com.company.company_clean_hub_be.entity.Assignment asn : assignments) {
                        if (asn.getAssignmentType() == com.company.company_clean_hub_be.entity.AssignmentType.FIXED_BY_COMPANY 
                            && (asn.getStatus() == com.company.company_clean_hub_be.entity.AssignmentStatus.IN_PROGRESS 
                                || asn.getStatus() == com.company.company_clean_hub_be.entity.AssignmentStatus.SCHEDULED)) {
                                
                                log.info("Processing TERMINATION for company assignmentId={}, currentStatus={}, startDate={}", 
                                        asn.getId(), asn.getStatus(), asn.getStartDate());
                                
                                asn.setStatus(com.company.company_clean_hub_be.entity.AssignmentStatus.TERMINATED);
                                asn.setEndDate(resignDate);
                                asn.setUpdatedAt(LocalDateTime.now());
                                terminatedCount++;

                                // Subtract attendances from resignDate onwards
                                List<com.company.company_clean_hub_be.entity.Attendance> allAtts = attendanceRepository.findByAssignmentId(asn.getId());
                                log.info("Found {} attendance records for assignmentId={}", allAtts.size(), asn.getId());
                                
                                int newWorkDays = 0;
                                for (com.company.company_clean_hub_be.entity.Attendance att : allAtts) {
                                        boolean isDeleted = false;
                                        if (!att.getDate().isBefore(resignDate) && (att.getDeleted() == null || !att.getDeleted())) {
                                                att.setDeleted(true);
                                                att.setDeletedAt(LocalDateTime.now());
                                                att.setDescription("Đã thôi việc văn phòng từ " + resignDate);
                                                attendanceRepository.save(att);
                                                isDeleted = true;
                                        } else if (att.getDeleted() != null && att.getDeleted()) {
                                                isDeleted = true;
                                        }

                                        if (!isDeleted) {
                                                newWorkDays++;
                                        }
                                }
                                
                                log.info("Updating workDays for assignmentId={} to {}", asn.getId(), newWorkDays);
                                asn.setWorkDays(newWorkDays);

                                // Delete SCHEDULED WorkSchedule records from resignDate onwards
                                List<WorkSchedule> scheduledWsList = workScheduleRepository
                                    .findByAssignmentIdAndScheduledDateFromAndStatus(
                                        asn.getId(), resignDate, WorkScheduleStatus.SCHEDULED);
                                log.info("Found {} SCHEDULED WorkSchedule records to delete for assignmentId={} from {}",
                                    scheduledWsList.size(), asn.getId(), resignDate);
                                for (WorkSchedule ws : scheduledWsList) {
                                        workScheduleRepository.delete(ws);
                                }

                                assignmentRepository.save(asn);
                        }
                }
                
                if (terminatedCount == 0) {
                        log.warn("No active company assignment found for employeeId={} to terminate", id);
                }
                
                log.info("Completed resignOfficeWork for employeeId={}. Total assignments terminated: {}", id, terminatedCount);
                return mapToResponse(employee);
        }

        @Override
        public EmployeeResponse cancelResignOfficeWork(Long id) {
                log.info("Starting cancelResignOfficeWork for employeeId={}", id);
                Employee employee = employeeRepository.findById(id)
                                .orElseThrow(() -> new AppException(ErrorCode.EMPLOYEE_NOT_FOUND));

                List<com.company.company_clean_hub_be.entity.Assignment> assignments = assignmentRepository.findByEmployeeId(id);
                int restoredCount = 0;
                
                for (com.company.company_clean_hub_be.entity.Assignment asn : assignments) {
                        if (asn.getAssignmentType() == com.company.company_clean_hub_be.entity.AssignmentType.FIXED_BY_COMPANY 
                            && asn.getStatus() == com.company.company_clean_hub_be.entity.AssignmentStatus.TERMINATED) {
                                
                                log.info("Restoring TERMINATED office assignmentId={}", asn.getId());
                                asn.setStatus(com.company.company_clean_hub_be.entity.AssignmentStatus.IN_PROGRESS);
                                asn.setEndDate(null); 
                                asn.setUpdatedAt(LocalDateTime.now());
                                restoredCount++;

                                // Restore attendances that were auto-deleted during resign
                                List<com.company.company_clean_hub_be.entity.Attendance> allAtts = 
                                        attendanceRepository.findByAssignmentId(asn.getId());
                                        
                                int newWorkDays = 0;
                                for (com.company.company_clean_hub_be.entity.Attendance att : allAtts) {
                                        boolean isDeleted = (att.getDeleted() != null && att.getDeleted());
                                        
                                        if (isDeleted && att.getDescription() != null && att.getDescription().contains("Đã thôi việc văn phòng")) {
                                                att.setDeleted(false);
                                                att.setDeletedAt(null);
                                                att.setDescription(""); 
                                                attendanceRepository.save(att);
                                                isDeleted = false;
                                        }
                                        
                                        if (!isDeleted) {
                                                newWorkDays++;
                                        }
                                }
                                
                                log.info("Restoring workDays for assignmentId={} to {}", asn.getId(), newWorkDays);
                                asn.setWorkDays(newWorkDays);

                                // Recreate WorkSchedule records for restored attendances
                                int recreatedWsCount = 0;
                                List<com.company.company_clean_hub_be.entity.Attendance> restoredAtts =
                                        attendanceRepository.findByAssignmentId(asn.getId());
                                for (com.company.company_clean_hub_be.entity.Attendance att : restoredAtts) {
                                        if (att.getDeleted() == null || !att.getDeleted()) {
                                                java.util.Optional<WorkSchedule> existingWs = workScheduleRepository
                                                        .findByAssignmentIdAndScheduledDate(asn.getId(), att.getDate());
                                                if (existingWs.isEmpty()) {
                                                        WorkSchedule ws = WorkSchedule.builder()
                                                                .assignment(asn)
                                                                .employee(employee)
                                                                .scheduledDate(att.getDate())
                                                                .status(WorkScheduleStatus.SCHEDULED)
                                                                .reason(WorkScheduleReason.AUTO_ATTENDANCE)
                                                                .attendance(att)
                                                                .build();
                                                        workScheduleRepository.save(ws);
                                                        recreatedWsCount++;
                                                }
                                        }
                                }
                                log.info("Recreated {} WorkSchedule records for assignmentId={}", recreatedWsCount, asn.getId());

                                assignmentRepository.save(asn);
                        }
                }
                
                log.info("Completed cancelResignOfficeWork for employeeId={}. Total assignments restored: {}", id, restoredCount);
                return mapToResponse(employee);
        }

        @Override
        public List<String> getCompanyLeaves(Long id, Integer month, Integer year) {
                if (!employeeRepository.existsById(id)) {
                        throw new AppException(ErrorCode.EMPLOYEE_NOT_FOUND);
                }
                List<java.time.LocalDate> dates = attendanceRepository.findCompanyLeaveDates(id, month, year);
                return dates.stream()
                        .map(date -> date.toString())
                        .collect(Collectors.toList());
        }

}
