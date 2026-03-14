# Evaluation Detail API - Single Call Optimization

## Overview
This document describes the optimized evaluation detail endpoint that consolidates multiple data sources into a single API call, reducing frontend complexity and improving performance.

## Endpoint
```
GET /api/v1/evaluations/{id}/details
```

## Purpose
Instead of making multiple API calls to fetch:
- Evaluation data
- Attendance data  
- Employee information
- Assignment details
- Contract information
- Customer data
- Service details
- Verification images
- User information (evaluators, approvers, assigners)

The frontend can now make **ONE** API call to get all necessary information.

## Response Structure
```json
{
  "success": true,
  "message": "Evaluation details retrieved successfully",
  "code": 200,
  "data": {
    // Evaluation info
    "evaluationId": 123,
    "evaluationStatus": "APPROVED",
    "internalNotes": "Good performance",
    "evaluatedAt": "2026-03-14T10:30:00",
    "evaluatedByUsername": "manager1",
    "evaluatedByName": "John Manager",
    
    // Attendance info
    "attendanceId": 456,
    "attendanceDate": "2026-03-14",
    "bonus": 100000,
    "penalty": 0,
    "supportCost": 50000,
    "workHours": 8.0,
    "isOvertime": false,
    "overtimeAmount": 0,
    "attendanceDescription": "Regular work day",
    "approvedByUsername": "supervisor1",
    "approvedByName": "Jane Supervisor",
    
    // Employee info
    "employeeId": 789,
    "employeeName": "Nguyen Van A",
    "employeePhone": "0901234567",
    "employeeEmail": "nguyenvana@company.com",
    "employmentType": "FULL_TIME",
    "baseSalary": 5000000,
    
    // Assignment info
    "assignmentId": 101,
    "assignmentStartDate": "2026-03-01",
    "assignmentEndDate": "2026-03-31",
    "assignmentStatus": "ACTIVE",
    "salaryAtTime": 5000000,
    "workDays": 22,
    "plannedDays": 22,
    "workingDaysPerWeek": ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"],
    "additionalAllowance": 200000,
    "assignmentDescription": "Office cleaning assignment",
    "assignmentType": "CLEANING",
    "assignmentScope": "CONTRACT",
    "assignedByUsername": "admin1",
    "assignedByName": "Admin User",
    
    // Contract info (if applicable)
    "contractId": 202,
    "contractTitle": "Office Cleaning Contract - ABC Company",
    "contractDescription": "Monthly office cleaning services",
    "contractStartDate": "2026-01-01",
    "contractEndDate": "2026-12-31",
    "contractValue": 120000000,
    
    // Customer info (if applicable)
    "customerId": 303,
    "customerName": "ABC Company Ltd",
    "customerPhone": "0281234567",
    "customerEmail": "contact@abc.com",
    "customerAddress": "123 Business Street, District 1, Ho Chi Minh City",
    
    // Service info (if applicable)
    "serviceId": 404,
    "serviceName": "Office Cleaning",
    "serviceDescription": "Complete office cleaning service",
    "servicePrice": 10000000,
    
    // Verification info (if applicable)
    "verificationId": 505,
    "verificationStatus": "APPROVED",
    "verificationNotes": "All images verified successfully",
    "verificationImages": [
      {
        "imageId": 601,
        "imageUrl": "https://cloudinary.com/image1.jpg",
        "imageType": "BEFORE_WORK",
        "uploadedAt": "2026-03-14T08:00:00"
      },
      {
        "imageId": 602,
        "imageUrl": "https://cloudinary.com/image2.jpg", 
        "imageType": "AFTER_WORK",
        "uploadedAt": "2026-03-14T17:00:00"
      }
    ],
    
    // Timestamps
    "createdAt": "2026-03-14T08:00:00",
    "updatedAt": "2026-03-14T10:30:00"
  }
}
```

## Benefits

### Performance Improvements
- **Reduced Network Calls**: 1 API call instead of 6-8 separate calls
- **Optimized Database Query**: Single query with JOIN FETCH to load all related data
- **Reduced Latency**: Eliminates multiple round-trips between frontend and backend

### Frontend Simplification
- **Single Loading State**: Only need to manage one loading state
- **Simplified Error Handling**: One error handling path instead of multiple
- **Reduced Code Complexity**: No need to coordinate multiple API calls
- **Better User Experience**: Faster page load, no progressive loading of different sections

### Backend Optimization
- **Efficient Data Fetching**: Uses JOIN FETCH to avoid N+1 query problems
- **Transactional Consistency**: All data fetched in single transaction
- **Reduced Server Load**: Fewer HTTP requests to handle

## Usage Example

### Before (Multiple API Calls)
```javascript
// Frontend had to make multiple calls
const evaluation = await api.get(`/evaluations/${id}`);
const attendance = await api.get(`/attendance/${evaluation.attendanceId}`);
const employee = await api.get(`/employees/${evaluation.employeeId}`);
const assignment = await api.get(`/assignments/${attendance.assignmentId}`);
const contract = await api.get(`/contracts/${assignment.contractId}`);
const customer = await api.get(`/customers/${contract.customerId}`);
const verification = await api.get(`/verifications/${attendance.verificationId}`);
```

### After (Single API Call)
```javascript
// Now just one call gets everything
const evaluationDetails = await api.get(`/evaluations/${id}/details`);
// All data is available in evaluationDetails.data
```

## Security
- Requires `MANAGER_GENERAL_1`, `MANAGER_GENERAL_2`, or `ADMIN` role
- Same security level as individual endpoints
- No additional permissions needed

## Error Handling
- Returns 404 if evaluation not found
- Returns 403 if user lacks required permissions
- All related data is optional - missing relationships don't cause errors

## Implementation Notes
- Uses `@Transactional(readOnly = true)` for optimal performance
- Lazy loading relationships are eagerly fetched to avoid LazyInitializationException
- Null-safe mapping handles cases where related entities don't exist
- Verification images are fetched separately due to collection nature