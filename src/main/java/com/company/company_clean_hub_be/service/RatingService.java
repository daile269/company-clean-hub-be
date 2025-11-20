package com.company.company_clean_hub_be.service;

import com.company.company_clean_hub_be.entity.Rating;
import java.util.List;
import java.util.Optional;

public interface RatingService {
    List<Rating> findAll();
    Optional<Rating> findById(Long id);
    Rating save(Rating rating);
    void deleteById(Long id);
}
