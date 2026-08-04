package com.company.company_clean_hub_be.service;

import java.util.List;

import com.company.company_clean_hub_be.dto.request.SalaryNoteRequest;
import com.company.company_clean_hub_be.dto.response.SalaryNoteResponse;

public interface SalaryNoteService {
    List<SalaryNoteResponse> getSalaryNotesByContractId(Long contractId);

    SalaryNoteResponse getSalaryNoteById(Long id);

    SalaryNoteResponse createSalaryNote(SalaryNoteRequest request);

    SalaryNoteResponse updateSalaryNote(Long id, SalaryNoteRequest request);

    void deleteSalaryNote(Long id);
}
