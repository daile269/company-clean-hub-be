-- Allow verification_image.assignment_verification_id to be null
-- for CONTRACT_REQUIREMENT photo captures (no verification record needed)
ALTER TABLE verification_images
    MODIFY COLUMN assignment_verification_id BIGINT NULL;
