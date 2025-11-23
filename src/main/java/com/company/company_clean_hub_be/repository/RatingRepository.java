package com.company.company_clean_hub_be.repository;

import com.company.company_clean_hub_be.entity.Rating;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RatingRepository extends JpaRepository<Rating, Long> {
}
