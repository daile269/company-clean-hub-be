-- Migration to add Cloudinary fields to employee_images table
-- This script adds cloudinary_url and cloudinary_public_id columns to support image storage on Cloudinary

ALTER TABLE employee_images ADD COLUMN cloudinary_url VARCHAR(1024) NULL;
ALTER TABLE employee_images ADD COLUMN cloudinary_public_id VARCHAR(512) NULL;

-- Add index for cloudinary_public_id for faster lookup
CREATE INDEX idx_cloudinary_public_id ON employee_images(cloudinary_public_id);
