package com.company.company_clean_hub_be.service.impl;

import com.company.company_clean_hub_be.dto.request.EmployeeRequest;
import com.company.company_clean_hub_be.dto.response.EmployeeResponse;
import com.company.company_clean_hub_be.dto.response.PageResponse;
import com.company.company_clean_hub_be.entity.Employee;
import com.company.company_clean_hub_be.entity.EmploymentType;
import com.company.company_clean_hub_be.entity.Role;
import com.company.company_clean_hub_be.exception.AppException;
import com.company.company_clean_hub_be.exception.ErrorCode;
import com.company.company_clean_hub_be.repository.EmployeeRepository;
import com.company.company_clean_hub_be.repository.RoleRepository;
import com.company.company_clean_hub_be.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class EmployeeServiceImpl implements EmployeeService {
    private final EmployeeRepository employeeRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public List<EmployeeResponse> getAllEmployees() {
        return employeeRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public PageResponse<EmployeeResponse> getEmployeesWithFilter(String keyword, EmploymentType employmentType, int page, int pageSize) {
        Pageable pageable = PageRequest.of(page, pageSize, Sort.by("createdAt").descending());
        Page<Employee> employeePage = employeeRepository.findByFilters(keyword, employmentType, pageable);

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
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.EMPLOYEE_NOT_FOUND));
        return mapToResponse(employee);
    }

    @Override
    public EmployeeResponse createEmployee(EmployeeRequest request) {
        // Kiểm tra trùng username
        if (employeeRepository.existsByUsername(request.getUsername())) {
            throw new AppException(ErrorCode.USERNAME_ALREADY_EXISTS);
        }
        
        // Kiểm tra trùng phone
        if (request.getPhone() != null && employeeRepository.existsByPhone(request.getPhone())) {
            throw new AppException(ErrorCode.PHONE_ALREADY_EXISTS);
        }
        
        // Kiểm tra trùng employeeCode
        if (employeeRepository.existsByEmployeeCode(request.getEmployeeCode())) {
            throw new AppException(ErrorCode.EMPLOYEE_CODE_ALREADY_EXISTS);
        }
        
        // Kiểm tra trùng CCCD
        if (employeeRepository.existsByCccd(request.getCccd())) {
            throw new AppException(ErrorCode.CCCD_ALREADY_EXISTS);
        }
        
        // Kiểm tra trùng bankAccount
        if (request.getBankAccount() != null && employeeRepository.existsByBankAccount(request.getBankAccount())) {
            throw new AppException(ErrorCode.BANK_ACCOUNT_ALREADY_EXISTS);
        }
        
        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));

        Employee employee = Employee.builder()
            .employeeCode(request.getEmployeeCode())
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .email(request.getEmail())
                .role(role)
                .status(request.getStatus())
                .employeeCode(request.getEmployeeCode())
                .cccd(request.getCccd())
                .address(request.getAddress())
                .name(request.getName())
                .bankAccount(request.getBankAccount())
                .bankName(request.getBankName())
                .employmentType(request.getEmploymentType())
                .baseSalary(request.getBaseSalary())
                .dailySalary(request.getDailySalary())
                .socialInsurance(request.getSocialInsurance())
                .healthInsurance(request.getHealthInsurance())
                .allowance(request.getAllowance())
                .description(request.getDescription())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Employee savedEmployee = employeeRepository.save(employee);
        return mapToResponse(savedEmployee);
    }

    @Override
    public EmployeeResponse updateEmployee(Long id, EmployeeRequest request) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.EMPLOYEE_NOT_FOUND));

        // Kiểm tra trùng phone (ngoại trừ chính nó)
        if (request.getPhone() != null && employeeRepository.existsByPhoneAndIdNot(request.getPhone(), id)) {
            throw new AppException(ErrorCode.PHONE_ALREADY_EXISTS);
        }
        
        // Kiểm tra trùng employeeCode (ngoại trừ chính nó)
        if (employeeRepository.existsByEmployeeCodeAndIdNot(request.getEmployeeCode(), id)) {
            throw new AppException(ErrorCode.EMPLOYEE_CODE_ALREADY_EXISTS);
        }
        
        // Kiểm tra trùng CCCD (ngoại trừ chính nó)
        if (employeeRepository.existsByCccdAndIdNot(request.getCccd(), id)) {
            throw new AppException(ErrorCode.CCCD_ALREADY_EXISTS);
        }
        
        // Kiểm tra trùng bankAccount (ngoại trừ chính nó)
        if (request.getBankAccount() != null && employeeRepository.existsByBankAccountAndIdNot(request.getBankAccount(), id)) {
            throw new AppException(ErrorCode.BANK_ACCOUNT_ALREADY_EXISTS);
        }
        
        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));

//        employee.setUsername(request.getUsername());
//        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
//            employee.setPassword(passwordEncoder.encode(request.getPassword()));
//        }
        employee.setPhone(request.getPhone());
        employee.setEmail(request.getEmail());
        employee.setRole(role);
        employee.setStatus(request.getStatus());
        employee.setCccd(request.getCccd());
        employee.setEmployeeCode(request.getEmployeeCode());
        employee.setAddress(request.getAddress());
        employee.setName(request.getName());
        employee.setBankAccount(request.getBankAccount());
        employee.setBankName(request.getBankName());
        employee.setEmploymentType(request.getEmploymentType());
        employee.setBaseSalary(request.getBaseSalary());
        employee.setDailySalary(request.getDailySalary());
        employee.setSocialInsurance(request.getSocialInsurance());
        employee.setHealthInsurance(request.getHealthInsurance());
        employee.setAllowance(request.getAllowance());
        employee.setDescription(request.getDescription());
        employee.setUpdatedAt(LocalDateTime.now());

        Employee updatedEmployee = employeeRepository.save(employee);
        return mapToResponse(updatedEmployee);
    }

    @Override
    public void deleteEmployee(Long id) {
        if (!employeeRepository.existsById(id)) {
            throw new AppException(ErrorCode.EMPLOYEE_NOT_FOUND);
        }
        employeeRepository.deleteById(id);
    }

    private EmployeeResponse mapToResponse(Employee employee) {
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
                .employmentType(employee.getEmploymentType())
                .baseSalary(employee.getBaseSalary())
                .dailySalary(employee.getDailySalary())
                .socialInsurance(employee.getSocialInsurance())
                .healthInsurance(employee.getHealthInsurance())
                .allowance(employee.getAllowance())
                .description(employee.getDescription())
                .createdAt(employee.getCreatedAt())
                .updatedAt(employee.getUpdatedAt())
                .build();
    }
}
