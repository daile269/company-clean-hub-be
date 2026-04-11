# Invoice Redesign - Implementation Summary

## Overview
Redesigned the invoice system to move pricing calculation from Contract entity to Invoice entity. Invoices now store detailed service information and pricing at the time of creation through invoice_lines table.

## Changes Made

### 1. Database Migration
**File:** `src/main/resources/db/migration/V3__create_invoice_lines_and_remove_contract_fields.sql`

- **Created `invoice_lines` table:**
  - Stores snapshot of service details at invoice creation time
  - Fields: id, invoice_id, service_id, title, description, service_type, unit, quantity, price, base_amount, vat, vat_amount, total_amount, contract_days, actual_days, effective_from, created_at
  - Foreign keys to invoices and services tables
  
- **Removed from `contracts` table:**
  - `planned_days` column
  - `work_days` column  
  - `final_price` column

### 2. New Entities

**InvoiceLine.java**
- Represents one line item in an invoice
- Stores complete service information at time of invoice creation
- Captures pricing calculation details (base amount, VAT, total)
- Tracks contract_days and actual_days for audit purposes

**InvoiceLineRepository.java**
- Repository for InvoiceLine CRUD operations
- Method: `findByInvoiceId(Long invoiceId)`

### 3. Updated Entities

**Invoice.java**
- Added `@OneToMany` relationship to InvoiceLines
- Invoice now has collection of invoice lines

**Contract.java**
- Removed fields: `finalPrice`, `workDays`, `plannedDays`
- Contract now serves as template/agreement without pricing

### 4. Updated DTOs

**ContractResponse.java**
- Removed fields: `finalPrice`, `workDays`, `plannedDays`
- Contract responses no longer include pricing information

**ServiceResponse.java** (existing)
- Fields `amount` and `baseAmount` set to null in contract context
- These fields are now only populated in invoice context

### 5. Service Layer Changes

**InvoiceServiceImpl.java**
- **Major refactor of `createInvoice()` method:**
  - Calculates working days in month from contract.workingDaysPerWeek
  - For each applicable service:
    - Computes base_amount based on service_type and contract_type
    - RECURRING + MONTHLY_ACTUAL: prorated by (actualDays / contractDays)
    - RECURRING + others: full price
    - ONE_TIME: fixed price
    - Calculates VAT per service
    - Creates InvoiceLine record with snapshot of service details
  - Sums all invoice lines to get invoice totals
  - Persists invoice and all lines in transaction

- **Added `calculateContractWorkingDaysInMonth()` method:**
  - Calculates actual working days in a month based on workingDaysPerWeek
  - Iterates through each day of the month
  - Counts days that match contract's working days pattern

- **Removed methods:**
  - `calculateSubtotal()` - logic moved to createInvoice
  - `calculateTotalVat()` - logic moved to createInvoice

**ContractServiceImpl.java**
- **Updated `createContract()`:**
  - Removed finalPrice, plannedDays, workDays calculations
  - No longer calls calculatePriceByContractType
  
- **Updated `updateContract()`:**
  - Removed finalPrice, plannedDays, workDays updates
  
- **Updated `addServiceToContract()`:**
  - Removed finalPrice recalculation
  
- **Updated `removeServiceFromContract()`:**
  - Removed finalPrice recalculation
  
- **Updated `updateServiceInContract()`:**
  - Removed finalPrice recalculation
  
- **Simplified `mapToResponse()`:**
  - Removed plannedDays computation logic
  - No longer calculates amount/baseAmount for services
  - Services in contract response show price/vat only, not totals
  
- **Removed methods:**
  - `calculatePriceByContractType()` - no longer needed
  - `calculateTotalFromServices()` - no longer needed
  - `calculatePlannedDaysForContract()` - no longer needed

## Pricing Logic

### Contract Types and Service Types

The pricing calculation depends on both the contract type and service type:

**MONTHLY_ACTUAL Contracts:**
- RECURRING services: (price × actualDays / contractDays) + VAT
- ONE_TIME/MONTHLY_FIXED services: price + VAT

**ONE_TIME and MONTHLY_FIXED Contracts:**
- RECURRING services: price + VAT
- ONE_TIME services: price + VAT

### Working Days Calculation

Contract working days per month are calculated by:
1. Getting workingDaysPerWeek from contract (e.g., [MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY])
2. Iterating through all days in the target month
3. Counting days that match the working day pattern
4. Default to 20 days if workingDaysPerWeek is not set

Example: If workingDaysPerWeek = Mon-Fri, December 2024 would have ~22 working days.

### Invoice Line Snapshot

Each invoice line stores:
- **Service details at creation time:** title, description, serviceType, price, vat, effectiveFrom
- **Pricing calculations:** baseAmount (before VAT), vatAmount, totalAmount
- **Context:** contractDays (working days in month), actualDays (if MONTHLY_ACTUAL)
- **Reference:** link to service_id (can be null if service deleted later)

This ensures invoice accuracy even if service details change after invoice creation.

## Business Rules

1. **One invoice per contract per month** (unchanged)
2. **Contract is template only:** Defines services, working day patterns, dates
3. **Invoice is pricing authority:** All amount calculations happen at invoice creation
4. **Historical accuracy:** Invoice lines preserve service state at invoice time
5. **Service filtering:** Only applicable services included (based on effectiveFrom and serviceType)
   - RECURRING: included from effectiveFrom onwards
   - ONE_TIME: only in month matching effectiveFrom

## Migration Path

To migrate existing data:
1. Run migration SQL to create invoice_lines and remove contract columns
2. For existing invoices without invoice_lines:
   - Could create script to generate invoice lines from contract.services retroactively
   - Or accept that old invoices don't have detailed line items
3. New invoices will automatically have proper invoice_lines

## Benefits

1. **Accurate historical records:** Invoice lines preserve exact pricing at creation time
2. **Flexible service changes:** Can modify service prices without affecting old invoices
3. **Clear separation:** Contract = agreement, Invoice = billing
4. **Audit trail:** Complete record of how amounts were calculated
5. **Per-service pricing:** Can see breakdown of each service's contribution
6. **Variable working days:** Each month's working days calculated accurately

## Testing Recommendations

1. Create invoice for ONE_TIME contract with mixed service types
2. Create invoice for MONTHLY_FIXED contract with RECURRING services
3. Create invoice for MONTHLY_ACTUAL contract:
   - With actualDays = contractDays
   - With actualDays < contractDays
   - With actualDays > contractDays
4. Verify invoice lines correctly snapshot service data
5. Modify service price after invoice creation, verify old invoice unchanged
6. Test working days calculation for different months (28, 29, 30, 31 days)
7. Test with different workingDaysPerWeek patterns

## Files Modified

- src/main/resources/db/migration/V3__create_invoice_lines_and_remove_contract_fields.sql (new)
- src/main/java/com/company/company_clean_hub_be/entity/InvoiceLine.java (new)
- src/main/java/com/company/company_clean_hub_be/repository/InvoiceLineRepository.java (new)
- src/main/java/com/company/company_clean_hub_be/entity/Invoice.java (modified)
- src/main/java/com/company/company_clean_hub_be/entity/Contract.java (modified)
- src/main/java/com/company/company_clean_hub_be/dto/response/ContractResponse.java (modified)
- src/main/java/com/company/company_clean_hub_be/service/impl/InvoiceServiceImpl.java (major refactor)
- src/main/java/com/company/company_clean_hub_be/service/impl/ContractServiceImpl.java (major cleanup)
