package com.company.company_clean_hub_be.service.impl;

import com.company.company_clean_hub_be.entity.Assignment;
import com.company.company_clean_hub_be.repository.AssignmentRepository;
import com.company.company_clean_hub_be.service.AssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class AssignmentServiceImpl implements AssignmentService {
    private final AssignmentRepository repository;

    @Override
    public List<Assignment> findAll() {
        return repository.findAll();
    }

    @Override
    public Optional<Assignment> findById(Long id) {
        return repository.findById(id);
    }

    @Override
    public Assignment save(Assignment assignment) {
        return repository.save(assignment);
    }

    @Override
    public void deleteById(Long id) {
        repository.deleteById(id);
    }
}
