-- Migration V011 to add CCCD Front and Back image columns to employees table
ALTER TABLE employees ADD COLUMN cccd_front_image VARCHAR(512) NULL;
ALTER TABLE employees ADD COLUMN cccd_back_image VARCHAR(512) NULL;
