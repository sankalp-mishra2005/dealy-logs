package com.bsp.delay.controller;

import com.bsp.delay.dto.ApiResponse;
import com.bsp.delay.dto.ReportResponse;
import com.bsp.delay.service.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/report")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<ReportResponse>> getReport(
            @RequestParam Long shopId,
            @RequestParam(required = false) Long areaId,
            @RequestParam(required = false, defaultValue = "MONTHLY") String reportType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long delayTypeId,
            @RequestParam(required = false) String shift) {
        
        ReportResponse report = reportService.generateReport(shopId, areaId, reportType, date, month, year, fromDate, toDate, delayTypeId, shift);
        return ResponseEntity.ok(ApiResponse.success(report, "DPR Report generated successfully"));
    }

    @GetMapping("/export-excel")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam Long shopId,
            @RequestParam(required = false) Long areaId,
            @RequestParam(required = false, defaultValue = "MONTHLY") String reportType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long delayTypeId,
            @RequestParam(required = false) String shift) throws IOException {

        byte[] excelBytes = reportService.generateExcelReport(shopId, areaId, reportType, date, month, year, fromDate, toDate, delayTypeId, shift);
        String filename = "BSP_DPR_Report_" + (date != null ? date : LocalDate.now()) + ".xlsx";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelBytes);
    }

    @GetMapping("/export-pdf")
    public ResponseEntity<byte[]> exportPdf(
            @RequestParam Long shopId,
            @RequestParam(required = false) Long areaId,
            @RequestParam(required = false, defaultValue = "MONTHLY") String reportType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long delayTypeId,
            @RequestParam(required = false) String shift) throws IOException {

        byte[] pdfBytes = reportService.generatePdfReport(shopId, areaId, reportType, date, month, year, fromDate, toDate, delayTypeId, shift);
        String filename = reportService.getPdfReportFilename(shopId, areaId, reportType, date, month, year, fromDate, toDate);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }
}
