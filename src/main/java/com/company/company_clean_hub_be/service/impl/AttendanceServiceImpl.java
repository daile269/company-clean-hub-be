package com.company.company_clean_hub_be.service.impl;

import com.company.company_clean_hub_be.entity.Attendance;
import com.company.company_clean_hub_be.repository.AttendanceRepository;
import com.company.company_clean_hub_be.service.AttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class AttendanceServiceImpl implements AttendanceService {
    private final AttendanceRepository repository;

    @Override
    public List<Attendance> findAll() {
        return repository.findAll();
    }

    @Override
    public Optional<Attendance> findById(Long id) {
        return repository.findById(id);
    }

    @Override
    public Attendance save(Attendance attendance) {
        return repository.save(attendance);
    }

    @Override
    public void deleteById(Long id) {
        repository.deleteById(id);
    }
}
