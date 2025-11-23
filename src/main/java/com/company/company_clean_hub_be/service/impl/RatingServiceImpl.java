package com.company.company_clean_hub_be.service.impl;

import com.company.company_clean_hub_be.entity.Rating;
import com.company.company_clean_hub_be.repository.RatingRepository;
import com.company.company_clean_hub_be.service.RatingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class RatingServiceImpl implements RatingService {
    private final RatingRepository repository;

    @Override
    public List<Rating> findAll() {
        return repository.findAll();
    }

    @Override
    public Optional<Rating> findById(Long id) {
        return repository.findById(id);
    }

    @Override
    public Rating save(Rating rating) {
        return repository.save(rating);
    }

    @Override
    public void deleteById(Long id) {
        repository.deleteById(id);
    }
}
