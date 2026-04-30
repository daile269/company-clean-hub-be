-- Thêm các field lưu công thức tính hóa đơn theo tháng
ALTER TABLE invoices ADD COLUMN planned_days_in_period INT NULL;
ALTER TABLE invoices ADD COLUMN num_employees INT NULL;
ALTER TABLE invoices ADD COLUMN absence_days INT NULL;
