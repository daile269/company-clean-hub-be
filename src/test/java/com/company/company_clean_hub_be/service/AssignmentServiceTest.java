package com.company.company_clean_hub_be.service;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.company.company_clean_hub_be.entity.Assignment;
import com.company.company_clean_hub_be.entity.AssignmentStatus;
import com.company.company_clean_hub_be.entity.Attendance;
import com.company.company_clean_hub_be.entity.Employee;
import com.company.company_clean_hub_be.repository.AssignmentRepository;
import com.company.company_clean_hub_be.repository.AttendanceRepository;
import com.company.company_clean_hub_be.repository.EmployeeRepository;
import com.company.company_clean_hub_be.service.impl.AssignmentServiceImpl;
import com.company.company_clean_hub_be.dto.response.AssignmentResponse;

@ExtendWith(MockitoExtension.class)
public class AssignmentServiceTest {

    @Mock
    private AssignmentRepository assignmentRepository;

    @Mock
    private AttendanceRepository attendanceRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private AssignmentServiceImpl assignmentService;

    @Test
    public void getTodayAssignmentsForCapture_ShouldReturnAssignments_WhenAttendancesExist() {
        Long employeeId = 1L;
        Employee employee = new Employee();
        employee.setId(employeeId);
        
        Assignment assignment = new Assignment();
        assignment.setId(10L);
        assignment.setStatus(AssignmentStatus.IN_PROGRESS);
        assignment.setAssignmentType(com.company.company_clean_hub_be.entity.AssignmentType.FIXED_BY_CONTRACT);
        assignment.setEmployee(employee);
        
        com.company.company_clean_hub_be.entity.Contract contract = new com.company.company_clean_hub_be.entity.Contract();
        contract.setId(1L);
        com.company.company_clean_hub_be.entity.Customer customer = new com.company.company_clean_hub_be.entity.Customer();
        customer.setId(1L);
        customer.setName("Test Customer");
        contract.setCustomer(customer);
        assignment.setContract(contract);

        Attendance attendance = new Attendance();
        attendance.setId(100L);
        attendance.setAssignment(assignment);
        attendance.setDate(LocalDate.now());

        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        when(attendanceRepository.findByEmployeeIdAndDateAndImageUrlIsNull(eq(employeeId), any(LocalDate.class)))
            .thenReturn(Arrays.asList(attendance));

        List<AssignmentResponse> results = assignmentService.getTodayAssignmentsForCapture(employeeId);

        assertEquals(1, results.size());
        assertEquals(10L, results.get(0).getId());
    }
}
