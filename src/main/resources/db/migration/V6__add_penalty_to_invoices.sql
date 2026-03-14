-- Add penalty column to invoices table
ALTER TABLE invoices ADD COLUMN penalty DECIMAL(15,2);

-- Add penalty reason column to invoices table
ALTER TABLE invoices ADD COLUMN penalty_reason VARCHAR(500);

-- Add comments to explain the columns
COMMENT ON COLUMN invoices.penalty IS 'Tiền phạt sẽ được trừ vào tổng hóa đơn (penalty amount deducted from invoice total)';
COMMENT ON COLUMN invoices.penalty_reason IS 'Lý do khấu trừ/phạt (reason for deduction/penalty)';
