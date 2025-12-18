-- Create invoice_lines table to store service details per invoice
CREATE TABLE invoice_lines (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_id BIGINT NOT NULL,
    service_id BIGINT,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    service_type VARCHAR(50) NOT NULL,
    unit VARCHAR(50),
    quantity INT,
    price DECIMAL(15,2) NOT NULL,
    base_amount DECIMAL(15,2) NOT NULL,
    vat DECIMAL(5,2),
    vat_amount DECIMAL(15,2) NOT NULL,
    total_amount DECIMAL(15,2) NOT NULL,
    contract_days INT,
    actual_days INT,
    effective_from DATE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (invoice_id) REFERENCES invoices(id) ON DELETE CASCADE,
    FOREIGN KEY (service_id) REFERENCES services(id) ON DELETE SET NULL
);

-- Remove price management fields from contracts (moved to invoices)
ALTER TABLE contracts DROP COLUMN IF EXISTS planned_days;
ALTER TABLE contracts DROP COLUMN IF EXISTS work_days;
ALTER TABLE contracts DROP COLUMN IF EXISTS final_price;
