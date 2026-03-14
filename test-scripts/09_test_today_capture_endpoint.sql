-- ============================================================================
-- TEST SCRIPT: Verify today-capture endpoint filtering logic
-- ============================================================================
-- Purpose: Test that /api/assignments/employee/{id}/today-capture
--          only returns assignments with PENDING or IN_PROGRESS verification
--
-- Test Cases:
-- 1. Assignment WITHOUT verification requirement → Should NOT appear
-- 2. Assignment WITH verification PENDING → Should appear
-- 3. Assignment WITH verification IN_PROGRESS → Should appear
-- 4. Assignment WITH verification APPROVED → Should NOT appear
-- ============================================================================

-- ============================================================================
-- SETUP: Create test data
-- ============================================================================

-- Use an existing employee (or create one)
SET @test_employee_id = 164;  -- Phan Huỳnh Phúc Khang
SET @test_employee_code = 'NV000043';
SET @today = CURDATE();

-- ============================================================================
-- TEST CASE 1: Assignment WITHOUT verification requirement
-- ============================================================================
-- This should NOT appear in today-capture endpoint

-- Check if assignment 271 exists and has no verification
SELECT 
    'TEST CASE 1: Assignment WITHOUT verification' AS test_case,
    a.id,
    a.employee_id,
    a.status,
    c.requires_image_verification,
    av.id AS verification_id,
    av.status AS verification_status
FROM assignments a
LEFT JOIN contracts c ON a.contract_id = c.id
LEFT JOIN assignment_verifications av ON a.id = av.assignment_id
WHERE a.id = 271;

-- Check if there's an attendance for today
SELECT 
    'TEST CASE 1: Attendance for today' AS test_case,
    att.id,
    att.assignment_id,
    att.employee_id,
    att.date,
    att.status
FROM attendances att
WHERE att.assignment_id = 271 AND att.date = @today;

-- ============================================================================
-- TEST CASE 2: Create assignment WITH verification PENDING
-- ============================================================================
-- This SHOULD appear in today-capture endpoint

-- Find a contract with verification enabled
SELECT 
    'TEST CASE 2: Contracts with verification enabled' AS test_case,
    id,
    description,
    requires_image_verification
FROM contracts
WHERE requires_image_verification = TRUE
LIMIT 1;

-- ============================================================================
-- TEST CASE 3: Check existing verifications
-- ============================================================================

SELECT 
    'TEST CASE 3: All verifications for test employee' AS test_case,
    av.id,
    av.assignment_id,
    av.status,
    av.reason,
    a.status AS assignment_status,
    c.requires_image_verification
FROM assignment_verifications av
JOIN assignments a ON av.assignment_id = a.id
JOIN contracts c ON a.contract_id = c.id
WHERE a.employee_id = @test_employee_id
ORDER BY av.created_at DESC;

-- ============================================================================
-- TEST CASE 4: Check attendances for today
-- ============================================================================

SELECT 
    'TEST CASE 4: Attendances for today' AS test_case,
    att.id,
    att.assignment_id,
    att.employee_id,
    att.date,
    att.status,
    a.status AS assignment_status,
    av.id AS verification_id,
    av.status AS verification_status
FROM attendances att
JOIN assignments a ON att.assignment_id = a.id
LEFT JOIN assignment_verifications av ON a.id = av.assignment_id
WHERE att.employee_id = @test_employee_id AND att.date = @today
ORDER BY att.assignment_id;

-- ============================================================================
-- EXPECTED RESULTS FOR today-capture ENDPOINT
-- ============================================================================
-- The endpoint should return ONLY assignments where:
-- 1. Assignment status = IN_PROGRESS or SCHEDULED
-- 2. Attendance exists for today
-- 3. AssignmentVerification exists AND status = PENDING or IN_PROGRESS
--
-- Assignment 271 should NOT appear because:
-- - It has no verification requirement (contract.requires_image_verification = FALSE)
-- - OR verification status is not PENDING/IN_PROGRESS
-- ============================================================================

-- ============================================================================
-- SUMMARY QUERY: What should appear in today-capture
-- ============================================================================

SELECT 
    'SUMMARY: Assignments that SHOULD appear in today-capture' AS summary,
    a.id,
    a.employee_id,
    e.employee_code,
    a.status,
    av.id AS verification_id,
    av.status AS verification_status,
    av.reason,
    att.date
FROM attendances att
JOIN assignments a ON att.assignment_id = a.id
JOIN employees e ON a.employee_id = e.id
LEFT JOIN assignment_verifications av ON a.id = av.assignment_id
WHERE att.employee_id = @test_employee_id 
  AND att.date = @today
  AND a.status IN ('IN_PROGRESS', 'SCHEDULED')
  AND av.id IS NOT NULL
  AND av.status IN ('PENDING', 'IN_PROGRESS')
ORDER BY a.id;

-- ============================================================================
-- VERIFICATION QUERY: Check assignment 271 specifically
-- ============================================================================

SELECT 
    'VERIFICATION: Assignment 271 details' AS verification,
    a.id,
    a.employee_id,
    a.status,
    c.requires_image_verification,
    av.id AS verification_id,
    av.status AS verification_status,
    COUNT(att.id) AS attendance_count_today
FROM assignments a
LEFT JOIN contracts c ON a.contract_id = c.id
LEFT JOIN assignment_verifications av ON a.id = av.assignment_id
LEFT JOIN attendances att ON a.id = att.assignment_id AND att.date = @today
WHERE a.id = 271
GROUP BY a.id;
