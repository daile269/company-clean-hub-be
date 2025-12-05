package com.company.company_clean_hub_be.service.impl;

import com.company.company_clean_hub_be.dto.response.PayRollExportExcel;
import com.company.company_clean_hub_be.service.ExcelExportService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
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

    private <T> Workbook createWorkbookGeneric(List<T> data, List<Column<T>> columns, String sheetName,String title) {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet(sheetName);

        // --- Style header "lớn" ---
        CellStyle bigHeaderStyle = workbook.createCellStyle();
        bigHeaderStyle.setAlignment(HorizontalAlignment.CENTER);
        bigHeaderStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        Font bigFont = workbook.createFont();
        bigFont.setBold(true);
        bigFont.setFontHeightInPoints((short) 16);
        bigHeaderStyle.setFont(bigFont);

        // Merge header lớn: chiếm 4 hàng đầu tiên, toàn bộ cột
        int totalColumns = columns.size();
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 3, 0, totalColumns - 1));
        Row bigHeaderRow = sheet.createRow(0);
        Cell bigHeaderCell = bigHeaderRow.createCell(0);
        bigHeaderCell.setCellValue(title);
        bigHeaderCell.setCellStyle(bigHeaderStyle);

        // --- Style header cột ---
        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setBorderTop(BorderStyle.THIN);
        headerStyle.setBorderBottom(BorderStyle.THIN);
        headerStyle.setBorderLeft(BorderStyle.THIN);
        headerStyle.setBorderRight(BorderStyle.THIN);
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);

        // --- Style body ---
        CellStyle bodyStyle = workbook.createCellStyle();
        bodyStyle.setBorderTop(BorderStyle.THIN);
        bodyStyle.setBorderBottom(BorderStyle.THIN);
        bodyStyle.setBorderLeft(BorderStyle.THIN);
        bodyStyle.setBorderRight(BorderStyle.THIN);

        // Write column headers (bắt đầu từ hàng thứ 4, tức row index 4)
        Row headerRow = sheet.createRow(4);
        for (int i = 0; i < columns.size(); i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(columns.get(i).getHeader());
            cell.setCellStyle(headerStyle);
        }

        // Write data rows (bắt đầu từ hàng 5, index 5)
        int rowIdx = 5;
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
    public ByteArrayResource exportUsersToExcel(List<PayRollExportExcel> payRollExportExcels, Integer month,Integer year) {
        List<Column<PayRollExportExcel>> columns = List.of(
                new Column<>("Mã nhân viên", PayRollExportExcel::getEmployeeId),
                new Column<>("Họ tên", PayRollExportExcel::getEmployeeName),
                new Column<>("Ngân hàng", PayRollExportExcel::getBankName),
                new Column<>("Số tài khoản", PayRollExportExcel::getBankAccount),
                new Column<>("Số điện thoại", PayRollExportExcel::getPhone),
                new Column<>("Công trình", p -> {
                    List<String> pcs = p.getProjectCompanies();
                    return (pcs == null || pcs.isEmpty()) ? "" : String.join(", ", pcs);
                }),
                new Column<>("Tổng ngày", PayRollExportExcel::getTotalDays),
                new Column<>("Thưởng", PayRollExportExcel::getTotalBonus),
                new Column<>("Phạt", PayRollExportExcel::getTotalPenalty),
                new Column<>("Phụ cấp", PayRollExportExcel::getTotalAllowance),
                new Column<>("Bảo hiểm", PayRollExportExcel::getTotalInsurance),
                new Column<>("Lương ứng", PayRollExportExcel::getTotalAdvance),
                new Column<>("Tổng lương", PayRollExportExcel::getFinalSalary)
        );
        String title = "CÔNG TY TNHH PANPANCIFIC BẢNG THANH TOÁN TIỀN LƯƠNG THÁNG "+month+"/"+year;
        Workbook workbook = createWorkbookGeneric(payRollExportExcels, columns, "Bảng lương",title);

        return convertWorkbookToResource(workbook);
    }
}
