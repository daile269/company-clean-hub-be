package com.company.company_clean_hub_be.service.impl;

import com.company.company_clean_hub_be.dto.request.AssignmentRequest;
import com.company.company_clean_hub_be.dto.response.AssignmentResponse;
import com.company.company_clean_hub_be.entity.Assignment;
import com.company.company_clean_hub_be.entity.Contract;
import com.company.company_clean_hub_be.entity.Employee;
import com.company.company_clean_hub_be.exception.AppException;
import com.company.company_clean_hub_be.exception.ErrorCode;
import com.company.company_clean_hub_be.repository.AssignmentRepository;
import com.company.company_clean_hub_be.repository.ContractRepository;
import com.company.company_clean_hub_be.repository.EmployeeRepository;
import com.company.company_clean_hub_be.service.AssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AssignmentServiceImpl implements AssignmentService {
    private final AssignmentRepository assignmentRepository;
    private final EmployeeRepository employeeRepository;
    private final ContractRepository contractRepository;

    @Override
    public List<AssignmentResponse> getAllAssignments() {
        return assignmentRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public AssignmentResponse getAssignmentById(Long id) {
        Assignment assignment = assignmentRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ASSIGNMENT_NOT_FOUND));
        return mapToResponse(assignment);
    }

    @Override
    public AssignmentResponse createAssignment(AssignmentRequest request) {
        Employee employee = employeeRepository.findById(request.getEmployeeId())
                .orElseThrow(() -> new AppException(ErrorCode.EMPLOYEE_NOT_FOUND));

        Contract contract = contractRepository.findById(request.getContractId())
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

        Assignment assignment = Assignment.builder()
                .employee(employee)
                .contract(contract)
                .startDate(request.getStartDate())
                .status(request.getStatus())
                .salaryAtTime(request.getSalaryAtTime())
                .workDays(request.getWorkDays())
                .description(request.getDescription())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Assignment savedAssignment = assignmentRepository.save(assignment);
        return mapToResponse(savedAssignment);
    }

    @Override
    public AssignmentResponse updateAssignment(Long id, AssignmentRequest request) {
        Assignment assignment = assignmentRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ASSIGNMENT_NOT_FOUND));

        Employee employee = employeeRepository.findById(request.getEmployeeId())
                .orElseThrow(() -> new AppException(ErrorCode.EMPLOYEE_NOT_FOUND));

        Contract contract = contractRepository.findById(request.getContractId())
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

        assignment.setEmployee(employee);
        assignment.setContract(contract);
        assignment.setStartDate(request.getStartDate());
        assignment.setStatus(request.getStatus());
        assignment.setSalaryAtTime(request.getSalaryAtTime());
        assignment.setWorkDays(request.getWorkDays());
        assignment.setDescription(request.getDescription());
        assignment.setUpdatedAt(LocalDateTime.now());

        Assignment updatedAssignment = assignmentRepository.save(assignment);
        return mapToResponse(updatedAssignment);
    }

    @Override
    public void deleteAssignment(Long id) {
        Assignment assignment = assignmentRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ASSIGNMENT_NOT_FOUND));
        assignmentRepository.delete(assignment);
    }

    private AssignmentResponse mapToResponse(Assignment assignment) {
        return AssignmentResponse.builder()
                .id(assignment.getId())
                .employeeId(assignment.getEmployee().getId())
                .employeeName(assignment.getEmployee().getName())
                .employeeCode(assignment.getEmployee().getEmployeeCode())
                .contractId(assignment.getContract().getId())
                .startDate(assignment.getStartDate())
                .status(assignment.getStatus())
                .salaryAtTime(assignment.getSalaryAtTime())
                .workDays(assignment.getWorkDays())
                .description(assignment.getDescription())
                .createdAt(assignment.getCreatedAt())
                .updatedAt(assignment.getUpdatedAt())
                .build();
    }
}
