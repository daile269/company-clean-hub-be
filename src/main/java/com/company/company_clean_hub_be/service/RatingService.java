package com.company.company_clean_hub_be.service;

import com.company.company_clean_hub_be.dto.request.CreateRatingRequest;
import com.company.company_clean_hub_be.dto.request.UpdateRatingRequest;
import com.company.company_clean_hub_be.dto.response.PageResponse;
import com.company.company_clean_hub_be.dto.response.RatingResponse;

import java.util.List;

public interface RatingService {
    RatingResponse createRating(CreateRatingRequest request);
    RatingResponse getRating(Long id);
    List<RatingResponse> getRatingsByContract(Long contractId);
    List<RatingResponse> getRatingsByEmployee(Long employeeId);
    List<RatingResponse> getRatingsByReviewer(Long reviewerId);
    PageResponse<RatingResponse> getRatingsWithFilter(Long contractId, Long assignmentId, Long customerId, Long employeeId, int page, int pageSize);
    RatingResponse updateRating(Long id, UpdateRatingRequest request);
    void deleteRating(Long id);
}

