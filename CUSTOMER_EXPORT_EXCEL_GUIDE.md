# Hướng dẫn xuất Excel danh sách khách hàng & hợp đồng

## Backend Implementation (Java Spring Boot)

### 1. Tạo DTO Response cho xuất Excel

```java
// File: CustomerContractExportDto.java
package com.company.company_clean_hub_be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerContractExportDto {
    private Integer stt;
    private String customerName;
    private String address;
    private String taxCode;
    private String email;
    private String contractCode;
    private String startDate;
    private String endDate;
    private String workingDays;
    private Double contractValue;
    private Integer workDays;
    private Double vatAmount;
    private Double totalValue;
    
    // Thêm field để track số dòng merge cho khách hàng
    private Integer mergeRowCount;
}
```

### 2. Tạo Entity/Model wrapper để group khách hàng & hợp đồng

```java
// File: CustomerContractGroupDto.java
package com.company.company_clean_hub_be.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerContractGroupDto {
    private Long customerId;
    private String customerName;
    private String address;
    private String taxCode;
    private String email;
    private List<ContractDetailDto> contracts;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
class ContractDetailDto {
    private String contractCode;
    private String startDate;
    private String endDate;
    private String workingDays;
    private Double contractValue;
    private Integer workDays;
    private Double vatAmount;
    private Double totalValue;
}
```

### 3. Thêm method vào CustomerService

```java
// Trong CustomerService.java

public List<CustomerContractGroupDto> getCustomersWithContractsForExport() {
    List<Customer> customers = customerRepository.findAll();
    List<CustomerContractGroupDto> result = new ArrayList<>();
    
    for (Customer customer : customers) {
        CustomerContractGroupDto group = new CustomerContractGroupDto();
        group.setCustomerId(customer.getId());
        group.setCustomerName(customer.getName());
        group.setAddress(customer.getAddress());
        group.setTaxCode(customer.getTaxCode());
        group.setEmail(customer.getEmail());
        
        // Lấy danh sách hợp đồng của khách hàng này
        List<Contract> contracts = contractRepository.findByCustomerId(customer.getId());
        List<ContractDetailDto> contractDetails = new ArrayList<>();
        
        for (Contract contract : contracts) {
            ContractDetailDto contractDetail = new ContractDetailDto();
            contractDetail.setContractCode(contract.getContractCode());
            contractDetail.setStartDate(formatDate(contract.getStartDate()));
            contractDetail.setEndDate(formatDate(contract.getEndDate()));
            contractDetail.setWorkingDays(String.join(", ", contract.getWorkingDaysPerWeek()));
            contractDetail.setContractValue(contract.getPrice());
            contractDetail.setWorkDays(contract.getWorkingDaysPerWeek() != null ? 
                contract.getWorkingDaysPerWeek().size() : 0);
            contractDetail.setVatAmount(contract.getVat());
            contractDetail.setTotalValue(contract.getFinalPrice());
            contractDetails.add(contractDetail);
        }
        
        group.setContracts(contractDetails);
        result.add(group);
    }
    
    return result;
}

private String formatDate(LocalDate date) {
    return date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
}
```

### 4. Tạo method xuất Excel trong ExcelExportService

```java
// Trong ExcelExportService.java

public ByteArrayResource exportCustomersWithContracts() throws IOException {
    List<CustomerContractGroupDto> customerGroups = 
        customerService.getCustomersWithContractsForExport();
    
    try (Workbook workbook = new XSSFWorkbook()) {
        Sheet sheet = workbook.createSheet("Danh sách khách hàng");
        
        // Tạo style
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle dataStyle = createDataStyle(workbook);
        CellStyle numberStyle = createNumberStyle(workbook);
        
        // Header
        int currentRowIndex = 0;
        Row headerRow = sheet.createRow(currentRowIndex++);
        String[] headers = {"STT", "Khách hàng", "Địa chỉ", "Mã số thuế", "Email",
                "Mã hợp đồng", "Ngày ký", "Ngày hết hạn", "Ngày làm việc",
                "Giá trị HĐ", "Số ngày làm", "Thuế VAT", "Tổng giá trị"};
        
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
                
                // STT (chỉ ghi lần đầu)
                Cell sttCell = dataRow.createCell(0);
                sttCell.setCellValue(stt);
                sttCell.setCellStyle(dataStyle);
                
                // Khách hàng (chỉ ghi lần đầu)
                Cell customerCell = dataRow.createCell(1);
                customerCell.setCellValue(customerGroup.getCustomerName());
                customerCell.setCellStyle(dataStyle);
                
                // Địa chỉ (chỉ ghi lần đầu)
                Cell addressCell = dataRow.createCell(2);
                addressCell.setCellValue(customerGroup.getAddress());
                addressCell.setCellStyle(dataStyle);
                
                // Mã số thuế (chỉ ghi lần đầu)
                Cell taxCell = dataRow.createCell(3);
                taxCell.setCellValue(customerGroup.getTaxCode() != null ? 
                    customerGroup.getTaxCode() : "");
                taxCell.setCellStyle(dataStyle);
                
                // Email (chỉ ghi lần đầu)
                Cell emailCell = dataRow.createCell(4);
                emailCell.setCellValue(customerGroup.getEmail() != null ? 
                    customerGroup.getEmail() : "");
                emailCell.setCellStyle(dataStyle);
                
                // Mã hợp đồng
                Cell contractCodeCell = dataRow.createCell(5);
                contractCodeCell.setCellValue(contract.getContractCode());
                contractCodeCell.setCellStyle(dataStyle);
                
                // Ngày ký
                Cell startDateCell = dataRow.createCell(6);
                startDateCell.setCellValue(contract.getStartDate());
                startDateCell.setCellStyle(dataStyle);
                
                // Ngày hết hạn
                Cell endDateCell = dataRow.createCell(7);
                endDateCell.setCellValue(contract.getEndDate());
                endDateCell.setCellStyle(dataStyle);
                
                // Ngày làm việc
                Cell workingDaysCell = dataRow.createCell(8);
                workingDaysCell.setCellValue(contract.getWorkingDays());
                workingDaysCell.setCellStyle(dataStyle);
                
                // Giá trị HĐ
                Cell contractValueCell = dataRow.createCell(9);
                contractValueCell.setCellValue(contract.getContractValue());
                contractValueCell.setCellStyle(numberStyle);
                
                // Số ngày làm
                Cell workDaysCell = dataRow.createCell(10);
                workDaysCell.setCellValue(contract.getWorkDays());
                workDaysCell.setCellStyle(numberStyle);
                
                // Thuế VAT
                Cell vatCell = dataRow.createCell(11);
                vatCell.setCellValue(contract.getVatAmount());
                vatCell.setCellStyle(numberStyle);
                
                // Tổng giá trị
                Cell totalValueCell = dataRow.createCell(12);
                totalValueCell.setCellValue(contract.getTotalValue());
                totalValueCell.setCellStyle(numberStyle);
            }
            
            // Merge các ô khách hàng nếu có nhiều hợp đồng
            if (contracts.size() > 1) {
                int mergeEndRow = currentRowIndex - 1;
                // Merge STT
                sheet.addMergedRegion(new CellRangeAddress(mergeStartRow, mergeEndRow, 0, 0));
                // Merge khách hàng
                sheet.addMergedRegion(new CellRangeAddress(mergeStartRow, mergeEndRow, 1, 1));
                // Merge địa chỉ
                sheet.addMergedRegion(new CellRangeAddress(mergeStartRow, mergeEndRow, 2, 2));
                // Merge mã số thuế
                sheet.addMergedRegion(new CellRangeAddress(mergeStartRow, mergeEndRow, 3, 3));
                // Merge email
                sheet.addMergedRegion(new CellRangeAddress(mergeStartRow, mergeEndRow, 4, 4));
            }
            
            stt++;
        }
        
        // Auto-size columns
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        return new ByteArrayResource(outputStream.toByteArray());
    }
}

private CellStyle createHeaderStyle(Workbook workbook) {
    CellStyle style = workbook.createCellStyle();
    Font font = workbook.createFont();
    font.setBold(true);
    font.setColor(IndexedColors.WHITE.getIndex());
    style.setFont(font);
    style.setFillForegroundColor(IndexedColors.BLUE.getIndex());
    style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    style.setAlignment(HorizontalAlignment.CENTER);
    style.setVerticalAlignment(VerticalAlignment.CENTER);
    style.setBorderBottom(BorderStyle.THIN);
    style.setBorderTop(BorderStyle.THIN);
    style.setBorderLeft(BorderStyle.THIN);
    style.setBorderRight(BorderStyle.THIN);
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

private CellStyle createNumberStyle(Workbook workbook) {
    CellStyle style = createDataStyle(workbook);
    style.setAlignment(HorizontalAlignment.RIGHT);
    style.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
    return style;
}
```

### 5. Thêm endpoint vào CustomerController

```java
// Trong CustomerController.java

@GetMapping("/export/excel")
public ResponseEntity<ByteArrayResource> exportCustomersWithContracts() {
    try {
        ByteArrayResource resource = excelExportService.exportCustomersWithContracts();
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, 
                "attachment; filename=Danh_sach_khach_hang.xlsx")
            .contentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(resource);
    } catch (IOException e) {
        log.error("Error exporting customers to Excel", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
}
```

### 6. Đảm bảo Import đúng

```java
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
```

## Frontend (đã hoàn thành)

✅ Component `CustomerContractExportModal.tsx` - Hiển thị dialog xác nhận xuất
✅ Method `exportCustomersWithContractsToExcel()` trong customerService
✅ Nút "Xuất Excel" trên trang Quản lý khách hàng
✅ Gọi API `/api/customers/export/excel`

## Quy trình hoạt động

1. User click nút "Xuất Excel" trên trang khách hàng
2. Modal xác nhận hiện lên với nội dung file sẽ xuất
3. User click "Xuất Excel"
4. Frontend gọi `customerService.exportCustomersWithContractsToExcel()`
5. API call đến `GET /api/customers/export/excel`
6. Backend:
   - Lấy danh sách khách hàng từ DB
   - Với mỗi khách hàng, lấy danh sách hợp đồng
   - Group các dòng hợp đồng theo khách hàng
   - Tạo Excel với merge cells cho các dòng khách hàng
   - Trả về file Excel cho browser
7. Browser tự động download file

## Kết quả Excel

| STT | Khách hàng | Địa chỉ | Mã số thuế | Email | Mã HĐ | Ngày ký | ... |
|-----|-----------|---------|-----------|-------|-------|---------|-----|
| 1 | Công ty A (merge 3 dòng) | ... | 123456 | a@email.com | HĐ001 | 01/01/2024 | ... |
| | | | | | HĐ002 | 15/02/2024 | ... |
| | | | | | HĐ003 | 20/03/2024 | ... |
| 2 | Công ty B (merge 1 dòng) | ... | 789012 | b@email.com | HĐ004 | 10/01/2024 | ... |

