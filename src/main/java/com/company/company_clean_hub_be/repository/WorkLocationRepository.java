package com.company.company_clean_hub_be.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.company.company_clean_hub_be.entity.WorkLocation;

@Repository
public interface WorkLocationRepository extends JpaRepository<WorkLocation, Long> {
    List<WorkLocation> findByContractId(Long contractId);
    List<WorkLocation> findByContractIdAndIsActiveTrue(Long contractId);
}
