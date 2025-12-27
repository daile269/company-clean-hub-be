-- Add reviewer_id column to ratings for peer reviews
ALTER TABLE ratings ADD COLUMN reviewer_id bigint;
ALTER TABLE ratings ADD CONSTRAINT fk_ratings_reviewer FOREIGN KEY (reviewer_id) REFERENCES employees(id);
