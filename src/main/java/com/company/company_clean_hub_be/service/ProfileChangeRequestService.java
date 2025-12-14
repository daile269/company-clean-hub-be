package com.company.company_clean_hub_be.service;

import com.company.company_clean_hub_be.dto.request.ProfileChangeRequestRequest;
import com.company.company_clean_hub_be.dto.response.PageResponse;
import com.company.company_clean_hub_be.dto.response.ProfileChangeRequestResponse;
import com.company.company_clean_hub_be.entity.ProfileChangeRequest.RequestStatus;

import java.util.List;

public interface ProfileChangeRequestService {
    ProfileChangeRequestResponse createRequest(ProfileChangeRequestRequest request);
    
    ProfileChangeRequestResponse getRequestById(Long id);
    
    List<ProfileChangeRequestResponse> getRequestsByEmployee(Long employeeId);
    
    PageResponse<ProfileChangeRequestResponse> getRequestsWithFilters(
        Long employeeId, 
        RequestStatus status, 
        int page, 
        int pageSize
    );
    
    ProfileChangeRequestResponse approveRequest(Long id);
    
    ProfileChangeRequestResponse rejectRequest(Long id, String rejectionReason);
    
    ProfileChangeRequestResponse cancelRequest(Long id);
}
