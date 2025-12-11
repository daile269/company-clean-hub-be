-- Migration: Add scope field to assignments table for COMPANY vs CONTRACT assignments

-- Add scope column with default CONTRACT
ALTER TABLE assignments ADD COLUMN scope VARCHAR(20) DEFAULT 'CONTRACT';

-- Update existing assignments to CONTRACT scope
UPDATE assignments SET scope = 'CONTRACT' WHERE scope IS NULL;

-- Make scope NOT NULL after populating existing data
ALTER TABLE assignments MODIFY scope VARCHAR(20) NOT NULL;

-- Make contract_id nullable to support COMPANY scope (no contract)
ALTER TABLE assignments MODIFY contract_id BIGINT NULL;

-- Add index for scope to improve query performance
CREATE INDEX idx_assignments_scope ON assignments(scope);

-- Add composite index for common queries
CREATE INDEX idx_assignments_scope_status ON assignments(scope, status);
