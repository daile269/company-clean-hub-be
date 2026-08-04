package com.company.company_clean_hub_be.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.company_clean_hub_be.dto.request.SalaryNoteRequest;
import com.company.company_clean_hub_be.dto.response.SalaryNoteResponse;
import com.company.company_clean_hub_be.entity.Contract;
import com.company.company_clean_hub_be.entity.SalaryNote;
import com.company.company_clean_hub_be.exception.AppException;
import com.company.company_clean_hub_be.exception.ErrorCode;
import com.company.company_clean_hub_be.repository.ContractRepository;
import com.company.company_clean_hub_be.repository.SalaryNoteRepository;
import com.company.company_clean_hub_be.service.SalaryNoteService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class SalaryNoteServiceImpl implements SalaryNoteService {

    private final SalaryNoteRepository salaryNoteRepository;
    private final ContractRepository contractRepository;

    @Override
    public List<SalaryNoteResponse> getSalaryNotesByContractId(Long contractId) {
        log.info("getSalaryNotesByContractId: contractId={}", contractId);
        return salaryNoteRepository.findByContractId(contractId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public SalaryNoteResponse getSalaryNoteById(Long id) {
        log.info("getSalaryNoteById: id={}", id);
        SalaryNote salaryNote = salaryNoteRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.SALARY_NOTE_NOT_FOUND));
        return mapToResponse(salaryNote);
    }

    @Override
    @Transactional
    public SalaryNoteResponse createSalaryNote(SalaryNoteRequest request) {
        log.info("createSalaryNote: contractId={}, category={}, salaryType={}, amount={}",
                request.getContractId(), request.getCategory(), request.getSalaryType(), request.getAmount());

        Contract contract = contractRepository.findById(request.getContractId())
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

        SalaryNote salaryNote = SalaryNote.builder()
                .contract(contract)
                .category(request.getCategory())
                .salaryType(request.getSalaryType())
                .amount(request.getAmount())
                .description(request.getDescription())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        SalaryNote saved = salaryNoteRepository.save(salaryNote);
        log.info("createSalaryNote completed: id={}", saved.getId());
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public SalaryNoteResponse updateSalaryNote(Long id, SalaryNoteRequest request) {
        log.info("updateSalaryNote: id={}", id);

        SalaryNote salaryNote = salaryNoteRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.SALARY_NOTE_NOT_FOUND));

        salaryNote.setCategory(request.getCategory());
        salaryNote.setSalaryType(request.getSalaryType());
        salaryNote.setAmount(request.getAmount());
        salaryNote.setDescription(request.getDescription());
        salaryNote.setUpdatedAt(LocalDateTime.now());

        SalaryNote updated = salaryNoteRepository.save(salaryNote);
        log.info("updateSalaryNote completed: id={}", updated.getId());
        return mapToResponse(updated);
    }

    @Override
    @Transactional
    public void deleteSalaryNote(Long id) {
        log.info("deleteSalaryNote: id={}", id);
        if (!salaryNoteRepository.existsById(id)) {
            throw new AppException(ErrorCode.SALARY_NOTE_NOT_FOUND);
        }
        salaryNoteRepository.deleteById(id);
        log.info("deleteSalaryNote completed: id={}", id);
    }

    private SalaryNoteResponse mapToResponse(SalaryNote salaryNote) {
        Contract contract = salaryNote.getContract();
        return SalaryNoteResponse.builder()
                .id(salaryNote.getId())
                .contractId(contract != null ? contract.getId() : null)
                .contractDescription(contract != null ? contract.getDescription() : null)
                .category(salaryNote.getCategory())
                .salaryType(salaryNote.getSalaryType())
                .amount(salaryNote.getAmount())
                .description(salaryNote.getDescription())
                .createdAt(salaryNote.getCreatedAt())
                .updatedAt(salaryNote.getUpdatedAt())
                .build();
    }
}
