package com.company.company_clean_hub_be.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;

import com.company.company_clean_hub_be.dto.response.ContractDetailDto;
import com.company.company_clean_hub_be.dto.response.CustomerContractGroupDto;
import com.company.company_clean_hub_be.dto.response.EmployeeExportDto;
import com.company.company_clean_hub_be.dto.response.PayRollAssignmentExportExcel;
import com.company.company_clean_hub_be.dto.response.PayRollExportExcel;
import com.company.company_clean_hub_be.service.ExcelExportService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExcelExportServiceImpl implements ExcelExportService {

    private static class Column<T> {
        private final String header;
        private final Function<T, Object> extractor;

        Column(String header, Function<T, Object> extractor) {
            this.header = header;
            this.extractor = extractor;
        }

        public String getHeader() {
            return header;
        }

        public Function<T, Object> getExtractor() {
            return extractor;
        }
    }

    private <T> Workbook createWorkbookGeneric(List<T> data, List<Column<T>> columns, String sheetName, String title) {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet(sheetName);

        int totalColumns = columns.size();

        // --- Style company header ---
        CellStyle companyHeaderStyle = createCompanyHeaderStyle(workbook);

        // --- Style header "lớn" (title) ---
        CellStyle bigHeaderStyle = workbook.createCellStyle();
        bigHeaderStyle.setAlignment(HorizontalAlignment.CENTER);
        bigHeaderStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        Font bigFont = workbook.createFont();
        bigFont.setBold(true);
        bigFont.setFontHeightInPoints((short) 12);
        bigHeaderStyle.setFont(bigFont);

        // Company info rows
        int currentRowIndex = 0;

        // Row 0: Company name
        Row companyRow = sheet.createRow(currentRowIndex++);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, totalColumns - 1));
        Cell companyCell = companyRow.createCell(0);
        companyCell.setCellValue("CÔNG TY TNHH TMDV PANPACIFIC");
        companyCell.setCellStyle(companyHeaderStyle);

        // Row 1: Address 1
        Row address1Row = sheet.createRow(currentRowIndex++);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(1, 1, 0, totalColumns - 1));
        Cell address1Cell = address1Row.createCell(0);
        address1Cell.setCellValue("VPĐD 1: 877 Lê Đức Thọ, Phường 16, Quận Gò Vấp, TP.HCM");
        address1Cell.setCellStyle(companyHeaderStyle);

        // Row 2: Address 2
        Row address2Row = sheet.createRow(currentRowIndex++);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(2, 2, 0, totalColumns - 1));
        Cell address2Cell = address2Row.createCell(0);
        address2Cell.setCellValue("VPĐD 2: 90/31 Thành Thái, Phường 12, Quận 10, TP.HCM");
        address2Cell.setCellStyle(companyHeaderStyle);

        // Row 3: Phone and Document Title
        Row phoneRow = sheet.createRow(currentRowIndex++);

        // Phone part (left)
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(3, 3, 0, totalColumns / 2 - 1));
        Cell phoneCell = phoneRow.createCell(0);
        phoneCell.setCellValue("Điện Thoại: 0901417674 - 0762833102");
        phoneCell.setCellStyle(companyHeaderStyle);

        // Document Title part (right)
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(3, 3, totalColumns / 2, totalColumns - 1));
        Cell titleCell = phoneRow.createCell(totalColumns / 2);
        titleCell.setCellValue(title);
        titleCell.setCellStyle(companyHeaderStyle);

        // Empty row
        currentRowIndex++;

        // --- Style header cột ---
        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setBorderTop(BorderStyle.THIN);
        headerStyle.setBorderBottom(BorderStyle.THIN);
        headerStyle.setBorderLeft(BorderStyle.THIN);
        headerStyle.setBorderRight(BorderStyle.THIN);
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        headerStyle.setFont(headerFont);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);

        // --- Style body ---
        CellStyle bodyStyle = workbook.createCellStyle();
        bodyStyle.setBorderTop(BorderStyle.THIN);
        bodyStyle.setBorderBottom(BorderStyle.THIN);
        bodyStyle.setBorderLeft(BorderStyle.THIN);
        bodyStyle.setBorderRight(BorderStyle.THIN);

        // Write column headers
        Row headerRow = sheet.createRow(currentRowIndex++);
        for (int i = 0; i < columns.size(); i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(columns.get(i).getHeader());
            cell.setCellStyle(headerStyle);
        }

        // Write data rows
        int rowIdx = currentRowIndex;
        for (T item : data) {
            Row row = sheet.createRow(rowIdx++);
            for (int colIdx = 0; colIdx < columns.size(); colIdx++) {
                Object value;
                try {
                    value = columns.get(colIdx).getExtractor().apply(item);
                } catch (Exception e) {
                    value = null;
                }
                Cell cell = row.createCell(colIdx);

                if (value == null) {
                    cell.setCellValue("");
                } else if (value instanceof Number) {
                    cell.setCellValue(((Number) value).doubleValue());
                } else if (value instanceof Boolean) {
                    cell.setCellValue((Boolean) value);
                } else if (value instanceof java.util.Date) {
                    cell.setCellValue((java.util.Date) value);
                } else {
                    cell.setCellValue(value.toString());
                }
                cell.setCellStyle(bodyStyle);
            }
        }

        // Auto-size tất cả cột
        for (int i = 0; i < columns.size(); i++) {
            sheet.autoSizeColumn(i);
        }

        return workbook;
    }

    private ByteArrayResource convertWorkbookToResource(Workbook workbook) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.write(out);
            workbook.close();
            return new ByteArrayResource(out.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to convert workbook to resource", e);
        }
    }

    @Override
    public ByteArrayResource exportUsersToExcel(List<PayRollExportExcel> payRollExportExcels, Integer month,
            Integer year) {
        // For backward compatibility, convert to new format and use new export method
        // This should not be called anymore, but keeping for compatibility
        return exportPayrollAssignmentsToExcel(convertToAssignmentFormat(payRollExportExcels), month, year);
    }

    @Override
    public ByteArrayResource exportPayrollAssignmentsToExcel(List<PayRollAssignmentExportExcel> assignmentData,
            Integer month, Integer year) {
        log.info("exportPayrollAssignmentsToExcel started: total rows={}", assignmentData.size());
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Bảng lương");

            // Create styles
            CellStyle companyHeaderStyle = createCompanyHeaderStyle(workbook);
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);
            CellStyle numberStyle = createNumberStyle(workbook);

            // ===== COMPANY HEADER SECTION =====
            int currentRowIndex = 0;
            int totalColumns = 16; // Updated to exclude advance salary column

            // Row 0: Company name
            Row companyRow = sheet.createRow(currentRowIndex++);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, totalColumns - 1));
            Cell companyCell = companyRow.createCell(0);
            companyCell.setCellValue("CÔNG TY TNHH TMDV PANPACIFIC");
            companyCell.setCellStyle(companyHeaderStyle);

            // Row 1: Address 1
            Row address1Row = sheet.createRow(currentRowIndex++);
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, totalColumns - 1));
            Cell address1Cell = address1Row.createCell(0);
            address1Cell.setCellValue("VPĐD 1: 877 Lê Đức Thọ, Phường 16, Quận Gò Vấp, TP.HCM");
            address1Cell.setCellStyle(companyHeaderStyle);

            // Row 2: Address 2
            Row address2Row = sheet.createRow(currentRowIndex++);
            sheet.addMergedRegion(new CellRangeAddress(2, 2, 0, totalColumns - 1));
            Cell address2Cell = address2Row.createCell(0);
            address2Cell.setCellValue("VPĐD 2: 90/31 Thành Thái, Phường 12, Quận 10, TP.HCM");
            address2Cell.setCellStyle(companyHeaderStyle);

            // Row 3: Phone and Document Title
            Row phoneRow = sheet.createRow(currentRowIndex++);

            // Phone part (left)
            sheet.addMergedRegion(new CellRangeAddress(3, 3, 0, 4));
            Cell phoneCell = phoneRow.createCell(0);
            phoneCell.setCellValue("Điện Thoại: 0901417674 - 0762833102");
            phoneCell.setCellStyle(companyHeaderStyle);

            // Document Title part (right)
            sheet.addMergedRegion(new CellRangeAddress(3, 3, 5, totalColumns - 1));
            Cell titleCell = phoneRow.createCell(5);
            titleCell.setCellValue("CÔNG TY TNHH PANPANCIFIC BẢNG THANH TOÁN TIỀN LƯƠNG THÁNG " + month + "/" + year);
            titleCell.setCellStyle(companyHeaderStyle);

            // Empty row
            currentRowIndex++;

            // Header row - Simplified summary columns
            Row headerRow = sheet.createRow(currentRowIndex++);
            String[] headers = {
                    "Mã NV", "Họ tên", "Ngân hàng", "Số TK", "SĐT",
                    "Công trình", "Ngày công", "Lương ngày công", "Thưởng", "Phạt",
                    "Phụ cấp", "Bảo hiểm", "Tổng lương tháng", "Lương đã thanh toán", "Lương còn lại",
                    "Ghi chú"
            };

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Data rows - each employee is one summary row
            for (PayRollAssignmentExportExcel row : assignmentData) {
                Row dataRow = sheet.createRow(currentRowIndex++);

                // Mã nhân viên
                Cell cell0 = dataRow.createCell(0);
                cell0.setCellValue(row.getEmployeeId() != null ? row.getEmployeeId().toString() : "");
                cell0.setCellStyle(dataStyle);

                // Họ tên
                Cell cell1 = dataRow.createCell(1);
                cell1.setCellValue(row.getEmployeeName() != null ? row.getEmployeeName() : "");
                cell1.setCellStyle(dataStyle);

                // Ngân hàng
                Cell cell2 = dataRow.createCell(2);
                cell2.setCellValue(row.getBankName() != null ? row.getBankName() : "");
                cell2.setCellStyle(dataStyle);

                // Số tài khoản
                Cell cell3 = dataRow.createCell(3);
                cell3.setCellValue(row.getBankAccount() != null ? row.getBankAccount() : "");
                cell3.setCellStyle(dataStyle);

                // Số điện thoại
                Cell cell4 = dataRow.createCell(4);
                cell4.setCellValue(row.getPhone() != null ? row.getPhone() : "");
                cell4.setCellStyle(dataStyle);

                // Công trình (joined projects list)
                Cell cell5 = dataRow.createCell(5);
                cell5.setCellValue(row.getProjectCompany() != null ? row.getProjectCompany() : "");
                cell5.setCellStyle(dataStyle);

                // Tổng ngày công
                Cell cell6 = dataRow.createCell(6);
                cell6.setCellValue(row.getTotalDays() != null ? row.getTotalDays() : 0);
                cell6.setCellStyle(numberStyle);

                // Lương ngày công (Base Salary)
                Cell cell7 = dataRow.createCell(7);
                cell7.setCellValue(row.getBaseSalary() != null ? row.getBaseSalary().doubleValue() : 0);
                cell7.setCellStyle(numberStyle);

                // Tổng thưởng
                Cell cell8 = dataRow.createCell(8);
                cell8.setCellValue(row.getTotalBonus() != null ? row.getTotalBonus().doubleValue() : 0);
                cell8.setCellStyle(numberStyle);

                // Tổng phạt
                Cell cell9 = dataRow.createCell(9);
                cell9.setCellValue(row.getTotalPenalty() != null ? row.getTotalPenalty().doubleValue() : 0);
                cell9.setCellStyle(numberStyle);

                // Tổng phụ cấp
                Cell cell10 = dataRow.createCell(10);
                cell10.setCellValue(row.getTotalAllowance() != null ? row.getTotalAllowance().doubleValue() : 0);
                cell10.setCellStyle(numberStyle);

                // Bảo hiểm
                Cell cell11 = dataRow.createCell(11);
                cell11.setCellValue(row.getTotalInsurance() != null ? row.getTotalInsurance().doubleValue() : 0);
                cell11.setCellStyle(numberStyle);

                // Tổng lương (before advance)
                Cell cell12 = dataRow.createCell(12);
                cell12.setCellValue(
                        row.getTotalSalaryBeforeAdvance() != null ? row.getTotalSalaryBeforeAdvance().doubleValue()
                                : 0);
                cell12.setCellStyle(numberStyle);

                // Lương đã thanh toán
                Cell cell13 = dataRow.createCell(13);
                cell13.setCellValue(row.getPaidAmount() != null ? row.getPaidAmount().doubleValue() : 0);
                cell13.setCellStyle(numberStyle);

                // Lương còn lại (after advance)
                Cell cell14 = dataRow.createCell(14);
                cell14.setCellValue(row.getFinalSalary() != null ? row.getFinalSalary().doubleValue() : 0);
                cell14.setCellStyle(numberStyle);

                // Ghi chú (note)
                Cell cell15 = dataRow.createCell(15);
                cell15.setCellValue(row.getNote() != null ? row.getNote() : "");
                cell15.setCellStyle(dataStyle);
            }

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            // Set minimum widths for readability
            sheet.setColumnWidth(1, 4000); // Name
            sheet.setColumnWidth(5, 8000); // Projects (can be long list)
            sheet.setColumnWidth(15, 130 * 256); // Note column (wider for formulas)

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            ByteArrayResource resource = new ByteArrayResource(outputStream.toByteArray());
            log.info("exportPayrollAssignmentsToExcel completed successfully");
            return resource;
        } catch (IOException e) {
            log.error("Error exporting payroll to Excel", e);
            throw new UncheckedIOException(e);
        }
    }

    private List<PayRollAssignmentExportExcel> convertToAssignmentFormat(List<PayRollExportExcel> oldData) {
        // This is a fallback conversion - should not be used in normal flow
        List<PayRollAssignmentExportExcel> result = new ArrayList<>();
        for (PayRollExportExcel old : oldData) {
            PayRollAssignmentExportExcel newData = PayRollAssignmentExportExcel.builder()
                    .employeeId(old.getEmployeeId() )
                    .employeeName(old.getEmployeeName())
                    .bankName(old.getBankName())
                    .bankAccount(old.getBankAccount())
                    .phone(old.getPhone())
                    .assignmentType(old.getEmployeeType())
                    .projectCompany(old.getProjectCompanies() != null && !old.getProjectCompanies().isEmpty()
                            ? String.join(", ", old.getProjectCompanies())
                            : null)
                    .assignmentDays(old.getTotalDays())
                    .assignmentBonus(old.getTotalBonus())
                    .assignmentPenalty(old.getTotalPenalty())
                    .assignmentAllowance(old.getTotalAllowance())
                    .assignmentInsurance(old.getTotalInsurance())
                    .assignmentAdvance(old.getTotalAdvance())
                    .totalDays(old.getTotalDays())
                    .totalBonus(old.getTotalBonus())
                    .totalPenalty(old.getTotalPenalty())
                    .totalAllowance(old.getTotalAllowance())
                    .totalInsurance(old.getTotalInsurance())
                    .totalAdvance(old.getTotalAdvance())
                    .finalSalary(old.getFinalSalary())
                    .isTotalRow(false)
                    .build();
            result.add(newData);
        }
        return result;
    }

    @Override
    public ByteArrayResource exportCustomersWithContractsToExcel(List<CustomerContractGroupDto> customerGroups) {
        log.info("exportCustomersWithContractsToExcel started: total customers={}", customerGroups.size());
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Danh sách khách hàng");

            // Create styles
            CellStyle companyHeaderStyle = createCompanyHeaderStyle(workbook);
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);
            CellStyle numberStyle = createNumberStyle(workbook);
            CellStyle mergedCellStyle = createMergedCellStyle(workbook);

            // ===== COMPANY HEADER SECTION =====
            int currentRowIndex = 0;
            int totalColumns = 11;

            // Row 0: Company name
            Row companyRow = sheet.createRow(currentRowIndex++);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, totalColumns - 1));
            Cell companyCell = companyRow.createCell(0);
            companyCell.setCellValue("CÔNG TY TNHH TMDV PANPACIFIC");
            companyCell.setCellStyle(companyHeaderStyle);

            // Row 1: Address 1
            Row address1Row = sheet.createRow(currentRowIndex++);
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, totalColumns - 1));
            Cell address1Cell = address1Row.createCell(0);
            address1Cell.setCellValue("VPĐD 1: 877 Lê Đức Thọ, Phường 16, Quận Gò Vấp, TP.HCM");
            address1Cell.setCellStyle(companyHeaderStyle);

            // Row 2: Address 2
            Row address2Row = sheet.createRow(currentRowIndex++);
            sheet.addMergedRegion(new CellRangeAddress(2, 2, 0, totalColumns - 1));
            Cell address2Cell = address2Row.createCell(0);
            address2Cell.setCellValue("VPĐD 2: 90/31 Thành Thái, Phường 12, Quận 10, TP.HCM");
            address2Cell.setCellStyle(companyHeaderStyle);

            // Row 3: Phone and Document Title (2 columns merged for each)
            Row phoneRow = sheet.createRow(currentRowIndex++);

            // Phone part (left)
            sheet.addMergedRegion(new CellRangeAddress(3, 3, 0, 5));
            Cell phoneCell = phoneRow.createCell(0);
            phoneCell.setCellValue("Điện Thoại: 0901417674 - 0762833102");
            phoneCell.setCellStyle(companyHeaderStyle);

            // Document Title part (right)
            sheet.addMergedRegion(new CellRangeAddress(3, 3, 6, totalColumns - 1));
            Cell titleCell = phoneRow.createCell(6);
            titleCell.setCellValue("TỔNG HỢP KHÁCH HÀNG");
            titleCell.setCellStyle(companyHeaderStyle);

            // Empty row
            currentRowIndex++;

            // Header row for table
            Row headerRow = sheet.createRow(currentRowIndex++);
            String[] headers = { "STT", "Khách hàng", "Địa chỉ", "Mã số thuế", "Email",
                    "Mã hợp đồng", "Ngày ký", "Ngày hết hạn", "Ngày làm việc",
                    // "Giá trị HĐ", "Tổng giá trị",
                    "Số ngày làm", "Thuế VAT" };

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Data rows with merge
            int stt = 1;
            for (CustomerContractGroupDto customerGroup : customerGroups) {
                List<ContractDetailDto> contracts = customerGroup.getContracts();
                int mergeStartRow = currentRowIndex;

                for (int i = 0; i < contracts.size(); i++) {
                    Row dataRow = sheet.createRow(currentRowIndex++);
                    ContractDetailDto contract = contracts.get(i);

                    // STT (only first row)
                    Cell sttCell = dataRow.createCell(0);
                    sttCell.setCellValue(stt);
                    sttCell.setCellStyle(i == 0 ? dataStyle : mergedCellStyle);

                    // Customer name (only first row)
                    Cell customerCell = dataRow.createCell(1);
                    customerCell.setCellValue(
                            customerGroup.getCustomerName() != null ? customerGroup.getCustomerName() : "");
                    customerCell.setCellStyle(i == 0 ? dataStyle : mergedCellStyle);

                    // Address (only first row)
                    Cell addressCell = dataRow.createCell(2);
                    addressCell.setCellValue(customerGroup.getAddress() != null ? customerGroup.getAddress() : "");
                    addressCell.setCellStyle(i == 0 ? dataStyle : mergedCellStyle);

                    // Tax code (only first row)
                    Cell taxCell = dataRow.createCell(3);
                    taxCell.setCellValue(customerGroup.getTaxCode() != null ? customerGroup.getTaxCode() : "");
                    taxCell.setCellStyle(i == 0 ? dataStyle : mergedCellStyle);

                    // Email (only first row)
                    Cell emailCell = dataRow.createCell(4);
                    emailCell.setCellValue(customerGroup.getEmail() != null ? customerGroup.getEmail() : "");
                    emailCell.setCellStyle(i == 0 ? dataStyle : mergedCellStyle);

                    // Contract code
                    Cell contractCodeCell = dataRow.createCell(5);
                    contractCodeCell.setCellValue(contract.getContractCode());
                    contractCodeCell.setCellStyle(dataStyle);

                    // Start date
                    Cell startDateCell = dataRow.createCell(6);
                    startDateCell.setCellValue(contract.getStartDate());
                    startDateCell.setCellStyle(dataStyle);

                    // End date
                    Cell endDateCell = dataRow.createCell(7);
                    endDateCell.setCellValue(contract.getEndDate());
                    endDateCell.setCellStyle(dataStyle);

                    // Working days
                    Cell workingDaysCell = dataRow.createCell(8);
                    workingDaysCell.setCellValue(contract.getWorkingDays());
                    workingDaysCell.setCellStyle(dataStyle);

                    // // Contract value
                    // Cell contractValueCell = dataRow.createCell(9);
                    // contractValueCell.setCellValue(contract.getContractValue());
                    // contractValueCell.setCellStyle(numberStyle);

                    // Work days
                    Cell workDaysCell = dataRow.createCell(9);
                    workDaysCell.setCellValue(contract.getWorkDays());
                    workDaysCell.setCellStyle(numberStyle);

                    // VAT amount
                    Cell vatCell = dataRow.createCell(10);
                    vatCell.setCellValue(contract.getVatAmount());
                    vatCell.setCellStyle(numberStyle);

                    // // Total value
                    // Cell totalValueCell = dataRow.createCell(11);
                    // totalValueCell.setCellValue(contract.getTotalValue());
                    // totalValueCell.setCellStyle(numberStyle);
                }

                // Merge cells if customer has multiple contracts
                if (contracts.size() > 1) {
                    int mergeEndRow = currentRowIndex - 1;
                    sheet.addMergedRegion(new CellRangeAddress(mergeStartRow, mergeEndRow, 0, 0)); // STT
                    sheet.addMergedRegion(new CellRangeAddress(mergeStartRow, mergeEndRow, 1, 1)); // Customer
                    sheet.addMergedRegion(new CellRangeAddress(mergeStartRow, mergeEndRow, 2, 2)); // Address
                    sheet.addMergedRegion(new CellRangeAddress(mergeStartRow, mergeEndRow, 3, 3)); // Tax code
                    sheet.addMergedRegion(new CellRangeAddress(mergeStartRow, mergeEndRow, 4, 4)); // Email
                }

                stt++;
            }

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // Set column widths
            sheet.setColumnWidth(1, 5000); // Customer name
            sheet.setColumnWidth(2, 6000); // Address
            sheet.setColumnWidth(8, 5000); // Working days

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            ByteArrayResource resource = new ByteArrayResource(outputStream.toByteArray());
            log.info("exportCustomersWithContractsToExcel completed successfully");
            return resource;
        } catch (IOException e) {
            log.error("Error exporting customers to Excel", e);
            throw new UncheckedIOException(e);
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createCompanyHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        return style;
    }

    private CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        style.setWrapText(true);
        return style;
    }

    private CellStyle createMergedCellStyle(Workbook workbook) {
        CellStyle style = createDataStyle(workbook);
        style.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle createNumberStyle(Workbook workbook) {
        CellStyle style = createDataStyle(workbook);
        style.setAlignment(HorizontalAlignment.RIGHT);
        style.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
        return style;
    }

    @Override
    public ByteArrayResource exportEmployeesToExcel(List<EmployeeExportDto> employees) {
        log.info("exportEmployeesToExcel requested with {} employees", employees.size());
        try (Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Danh sách nhân viên");

            // Create header styles
            CellStyle companyHeaderStyle = createCompanyHeaderStyle(workbook);
            CellStyle headerStyle = createHeaderStyle(workbook);

            // ===== COMPANY HEADER SECTION =====
            int currentRowIndex = 0;
            int totalColumns = 13;

            // Row 0: Company name
            Row companyRow = sheet.createRow(currentRowIndex++);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, totalColumns - 1));
            Cell companyCell = companyRow.createCell(0);
            companyCell.setCellValue("CÔNG TY TNHH TMDV PANPACIFIC");
            companyCell.setCellStyle(companyHeaderStyle);

            // Row 1: Address 1
            Row address1Row = sheet.createRow(currentRowIndex++);
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, totalColumns - 1));
            Cell address1Cell = address1Row.createCell(0);
            address1Cell.setCellValue("VPĐD 1: 877 Lê Đức Thọ, Phường 16, Quận Gò Vấp, TP.HCM");
            address1Cell.setCellStyle(companyHeaderStyle);

            // Row 2: Address 2
            Row address2Row = sheet.createRow(currentRowIndex++);
            sheet.addMergedRegion(new CellRangeAddress(2, 2, 0, totalColumns - 1));
            Cell address2Cell = address2Row.createCell(0);
            address2Cell.setCellValue("VPĐD 2: 90/31 Thành Thái, Phường 12, Quận 10, TP.HCM");
            address2Cell.setCellStyle(companyHeaderStyle);

            // Row 3: Phone and Document Title
            Row phoneRow = sheet.createRow(currentRowIndex++);

            // Phone part (left)
            sheet.addMergedRegion(new CellRangeAddress(3, 3, 0, 5));
            Cell phoneCell = phoneRow.createCell(0);
            phoneCell.setCellValue("Điện Thoại: 0901417674 - 0762833102");
            phoneCell.setCellStyle(companyHeaderStyle);

            // Document Title part (right)
            sheet.addMergedRegion(new CellRangeAddress(3, 3, 6, totalColumns - 1));
            Cell titleCell = phoneRow.createCell(6);
            titleCell.setCellValue("TỔNG HỢP NHÂN VIÊN");
            titleCell.setCellStyle(companyHeaderStyle);

            // Empty row
            currentRowIndex++;

            // Header columns
            String[] headers = {
                    "STT", "Mã nhân viên", "Tên", "Tên đăng nhập", "Email", "SĐT",
                    "Địa chỉ", "CCCD", "Tài khoản ngân hàng", "Ngân hàng", "Mô tả", "Ngày tạo", "Cập nhật lần cuối"
            };

            // Create header row
            Row headerRow = sheet.createRow(currentRowIndex++);
            headerRow.setHeightInPoints(20);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Create data style
            CellStyle dataStyle2 = createDataStyle(workbook);

            // Fill data rows
            for (int i = 0; i < employees.size(); i++) {
                EmployeeExportDto emp = employees.get(i);
                Row row = sheet.createRow(currentRowIndex++);

                Cell sttCell = row.createCell(0);
                sttCell.setCellValue(i + 1);
                sttCell.setCellStyle(dataStyle2);

                Cell codeCell = row.createCell(1);
                codeCell.setCellValue(emp.getEmployeeCode() != null ? emp.getEmployeeCode() : "");
                codeCell.setCellStyle(dataStyle2);

                Cell nameCell = row.createCell(2);
                nameCell.setCellValue(emp.getName() != null ? emp.getName() : "");
                nameCell.setCellStyle(dataStyle2);

                Cell usernameCell = row.createCell(3);
                usernameCell.setCellValue(emp.getUsername() != null ? emp.getUsername() : "");
                usernameCell.setCellStyle(dataStyle2);

                Cell emailCell = row.createCell(4);
                emailCell.setCellValue(emp.getEmail() != null ? emp.getEmail() : "");
                emailCell.setCellStyle(dataStyle2);

                Cell phoneCellData = row.createCell(5);
                phoneCellData.setCellValue(emp.getPhone() != null ? emp.getPhone() : "");
                phoneCellData.setCellStyle(dataStyle2);

                Cell addressCell = row.createCell(6);
                addressCell.setCellValue(emp.getAddress() != null ? emp.getAddress() : "");
                addressCell.setCellStyle(dataStyle2);

                Cell cccdCell = row.createCell(7);
                cccdCell.setCellValue(emp.getCccd() != null ? emp.getCccd() : "");
                cccdCell.setCellStyle(dataStyle2);

                Cell bankAccountCell = row.createCell(8);
                bankAccountCell.setCellValue(emp.getBankAccount() != null ? emp.getBankAccount() : "");
                bankAccountCell.setCellStyle(dataStyle2);

                Cell bankNameCell = row.createCell(9);
                bankNameCell.setCellValue(emp.getBankName() != null ? emp.getBankName() : "");
                bankNameCell.setCellStyle(dataStyle2);

                Cell descriptionCell = row.createCell(10);
                descriptionCell.setCellValue(emp.getDescription() != null ? emp.getDescription() : "");
                descriptionCell.setCellStyle(dataStyle2);

                Cell createdAtCell = row.createCell(11);
                createdAtCell.setCellValue(emp.getCreatedAt() != null ? emp.getCreatedAt() : "");
                createdAtCell.setCellStyle(dataStyle2);

                Cell updatedAtCell = row.createCell(12);
                updatedAtCell.setCellValue(emp.getUpdatedAt() != null ? emp.getUpdatedAt() : "");
                updatedAtCell.setCellStyle(dataStyle2);
            }

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            // Set specific widths for better readability
            sheet.setColumnWidth(2, 5000); // Tên
            sheet.setColumnWidth(6, 6000); // Địa chỉ
            sheet.setColumnWidth(12, 5000); // Mô tả

            workbook.write(out);
            return new ByteArrayResource(out.toByteArray());
        } catch (IOException e) {
            log.error("Error exporting employees to Excel", e);
            throw new UncheckedIOException(e);
        }
    }
}
