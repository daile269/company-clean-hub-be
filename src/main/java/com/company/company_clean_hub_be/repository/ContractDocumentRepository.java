package com.company.company_clean_hub_be.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.company.company_clean_hub_be.entity.ContractDocument;

@Repository
public interface ContractDocumentRepository extends JpaRepository<ContractDocument, Long> {
    List<ContractDocument> findByContractId(Long contractId);
}
