package com.company.company_clean_hub_be.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.company.company_clean_hub_be.dto.request.EvaluationRequest;
import com.company.company_clean_hub_be.dto.response.EvaluationResponse;
import com.company.company_clean_hub_be.entity.Attendance;
import com.company.company_clean_hub_be.entity.Employee;
import com.company.company_clean_hub_be.entity.Evaluation;
import com.company.company_clean_hub_be.entity.EvaluationStatus;
import com.company.company_clean_hub_be.entity.User;
import com.company.company_clean_hub_be.repository.AttendanceRepository;
import com.company.company_clean_hub_be.repository.EvaluationRepository;
import com.company.company_clean_hub_be.repository.UserRepository;
import com.company.company_clean_hub_be.service.impl.EvaluationServiceImpl;

@ExtendWith(MockitoExtension.class)
public class EvaluationServiceTest {

    @Mock
    private EvaluationRepository evaluationRepository;

    @Mock
    private AttendanceRepository attendanceRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private EvaluationServiceImpl evaluationService;

    private EvaluationRequest evaluationRequest;
    private Attendance attendance;
    private Employee employee;
    private User evaluator;

    @BeforeEach
    void setUp() {
        employee = Employee.builder().id(1L).name("Test Employee").build();
        attendance = Attendance.builder().id(100L).employee(employee).date(LocalDate.now()).build();
        evaluator = User.builder().id(2L).username("manager").build();

        evaluationRequest = EvaluationRequest.builder()
                .attendanceId(100L)
                .status(EvaluationStatus.APPROVED)
                .internalNotes("Data looks correct")
                .build();
    }

    @Test
    void evaluate_Success() {
        when(attendanceRepository.findById(100L)).thenReturn(Optional.of(attendance));
        when(userRepository.findByUsername("manager")).thenReturn(Optional.of(evaluator));
        when(evaluationRepository.findByAttendanceId(100L)).thenReturn(Optional.empty());
        when(evaluationRepository.save(any(Evaluation.class))).thenAnswer(invocation -> {
            Evaluation saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        EvaluationResponse response = evaluationService.evaluate(evaluationRequest, "manager");

        assertNotNull(response);
        assertEquals(EvaluationStatus.APPROVED, response.getStatus());
        assertEquals("Data looks correct", response.getInternalNotes());
        assertEquals("manager", response.getEvaluatedByUsername());
        verify(evaluationRepository).save(any(Evaluation.class));
    }
}
