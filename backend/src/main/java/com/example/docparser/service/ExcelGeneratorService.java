package com.example.docparser.service;

import com.example.docparser.model.dto.FieldDefinition;
import com.example.docparser.model.dto.InterfaceInfo;
import com.example.docparser.model.dto.ParseResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Slf4j
@Service
public class ExcelGeneratorService {

    @Value("${file.excel-dir:./exports}")
    private String excelDir;

    private static final String[] TABLE_HEADERS = {
            "字段英文名", "字段中文名", "字段类型", "字段长度",
            "是否必传", "备注", "字段表英文名", "外部接口字段名"
    };

    public String generateExcel(Long documentId, ParseResult parseResult, String originalName) throws IOException {
        Path exportPath = Paths.get(excelDir);
        Files.createDirectories(exportPath);

        String apiName = parseResult.getInterfaceInfo() != null
                ? parseResult.getInterfaceInfo().getApiNameCn()
                : "未命名接口";
        String safeName = apiName.replaceAll("[\\\\/:*?\"<>|]", "_");
        String excelFileName = safeName + "-接口信息表.xlsx";
        Path excelFilePath = exportPath.resolve(excelFileName);

        Workbook workbook = null;
        try {
            workbook = tryLoadTemplate();
            if (workbook == null) {
                workbook = new XSSFWorkbook();
            }

            Sheet sheet;
            if (workbook.getSheet("Sheet1") != null) {
                sheet = workbook.getSheet("Sheet1");
            } else if (workbook.getNumberOfSheets() > 0) {
                sheet = workbook.getSheetAt(0);
            } else {
                sheet = workbook.createSheet("Sheet1");
            }

            clearSheet(sheet);
            buildSheet(sheet, parseResult);

            for (int i = 0; i < 8; i++) {
                sheet.autoSizeColumn(i);
            }

            try (FileOutputStream fos = new FileOutputStream(excelFilePath.toFile())) {
                workbook.write(fos);
            }
        } finally {
            if (workbook != null) {
                try { workbook.close(); } catch (IOException ignored) {}
            }
        }

        log.info("Excel文件已生成: {}", excelFilePath);
        return excelFilePath.toString();
    }

    private Workbook tryLoadTemplate() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("excel/B.xlsx")) {
            if (is != null) {
                return WorkbookFactory.create(is);
            }
        } catch (Exception e) {
            log.debug("未找到模板B.xlsx，将从空白生成: {}", e.getMessage());
        }
        return null;
    }

    private void clearSheet(Sheet sheet) {
        for (int i = sheet.getLastRowNum(); i >= 0; i--) {
            Row row = sheet.getRow(i);
            if (row != null) {
                sheet.removeRow(row);
            }
        }
    }

    private void buildSheet(Sheet sheet, ParseResult result) {
        CellStyle boldStyle = createBoldStyle(sheet.getWorkbook());
        CellStyle normalStyle = createNormalStyle(sheet.getWorkbook());
        CellStyle sectionStyle = createSectionStyle(sheet.getWorkbook());
        CellStyle markerStyle = createMarkerStyle(sheet.getWorkbook());
        CellStyle headerStyle = createHeaderStyle(sheet.getWorkbook());

        InterfaceInfo info = result.getInterfaceInfo();

        // === Rows 0-6: Basic info (left block A-B, right block G-H) ===
        String[][] leftFields = {
                {"接口中文名", safe(info != null ? info.getApiNameCn() : "")},
                {"数据引进方式", safe(info != null ? info.getDataImportMethod() : "")},
                {"接口调用频率", safe(info != null ? info.getApiCallFrequency() : "")},
                {"外部厂商接口地址", safe(info != null ? info.getExternalVendorUrl() : "")},
                {"访问外数接口地址", safe(info != null ? info.getExternalDataUrl() : "")},
                {"调用系统英文名", safe(info != null ? info.getCallSystemName() : "")},
                {"请求地址", safe(info != null ? info.getRequestUrl() : "")}
        };

        String[][] rightFields = {
                {"接口中文名", safe(info != null ? info.getApiNameCn() : "")},
                {"接口英文名", safe(info != null ? info.getApiNameEn() : "")},
                {"接口地址", safe(info != null ? info.getApiUrl() : "")}
        };

        for (int i = 0; i < leftFields.length; i++) {
            Row row = getOrCreateRow(sheet, i);
            setCell(row, 0, leftFields[i][0], boldStyle);
            setCell(row, 1, leftFields[i][1], normalStyle);
        }

        for (int i = 0; i < rightFields.length; i++) {
            Row row = getOrCreateRow(sheet, i);
            setCell(row, 6, rightFields[i][0], boldStyle);
            setCell(row, 7, rightFields[i][1], normalStyle);
        }

        // Row 7: empty

        // === Row 8: Table header ===
        Row headerRow = getOrCreateRow(sheet, 8);
        for (int i = 0; i < TABLE_HEADERS.length; i++) {
            setCell(headerRow, i, TABLE_HEADERS[i], headerStyle);
        }

        // === Row 9: 输入 section ===
        Row inputSectionRow = getOrCreateRow(sheet, 9);
        setCell(inputSectionRow, 0, "输入", sectionStyle);
        setCell(inputSectionRow, 6, "输入", sectionStyle);

        // === Row 10: reqHeader start ===
        Row reqHeaderStart = getOrCreateRow(sheet, 10);
        setCell(reqHeaderStart, 0, "reqHeader", markerStyle);
        setCell(reqHeaderStart, 6, "start", markerStyle);
        setCell(reqHeaderStart, 7, "REQHEADER", markerStyle);

        // === Rows 11+: Request params data ===
        int rowNum = 11;
        if (result.getRequestParams() != null && !result.getRequestParams().isEmpty()) {
            rowNum = createFieldRows(sheet, rowNum, result.getRequestParams(), normalStyle, 0);
        }

        // === reqHeader end ===
        Row reqHeaderEnd = getOrCreateRow(sheet, rowNum++);
        setCell(reqHeaderEnd, 0, "reqHeader", markerStyle);
        setCell(reqHeaderEnd, 6, "end", markerStyle);
        setCell(reqHeaderEnd, 7, "REQHEADER", markerStyle);

        // empty row
        rowNum++;

        // === 输出 section ===
        Row outputSectionRow = getOrCreateRow(sheet, rowNum++);
        setCell(outputSectionRow, 0, "输出", sectionStyle);
        setCell(outputSectionRow, 6, "输出", sectionStyle);

        // === errorCode row (fixed) ===
        Row errorCodeRow = getOrCreateRow(sheet, rowNum++);
        setCell(errorCodeRow, 0, "errorCode", boldStyle);
        setCell(errorCodeRow, 1, "返回码", boldStyle);
        setCell(errorCodeRow, 2, "string", normalStyle);
        setCell(errorCodeRow, 3, "20", normalStyle);
        setCell(errorCodeRow, 4, "Y", normalStyle);
        setCell(errorCodeRow, 7, "errorCode", normalStyle);

        // === data start ===
        Row dataStart = getOrCreateRow(sheet, rowNum++);
        setCell(dataStart, 0, "data", markerStyle);
        setCell(dataStart, 2, "Object", markerStyle);
        setCell(dataStart, 6, "start", markerStyle);
        setCell(dataStart, 7, "DATA", markerStyle);

        // === Response params (between data start/end) ===
        if (result.getResponseParams() != null && !result.getResponseParams().isEmpty()) {
            rowNum = createFieldRows(sheet, rowNum, result.getResponseParams(), normalStyle, 0);
        }

        // === data end ===
        Row dataEnd = getOrCreateRow(sheet, rowNum++);
        setCell(dataEnd, 0, "data", markerStyle);
        setCell(dataEnd, 2, "Object", markerStyle);
        setCell(dataEnd, 6, "end", markerStyle);
        setCell(dataEnd, 7, "DATA", markerStyle);
    }

    private int createFieldRows(Sheet sheet, int rowNum, List<FieldDefinition> fields, CellStyle style, int depth) {
        for (FieldDefinition field : fields) {
            Row row = getOrCreateRow(sheet, rowNum);
            String indent = depth > 0 ? "  ".repeat(depth) : "";

            setCell(row, 0, indent + safe(field.getFieldNameEn()), style);
            setCell(row, 1, safe(field.getFieldNameCn()), style);
            setCell(row, 2, safe(field.getFieldType()), style);
            setCell(row, 3, safe(field.getFieldLength()), style);
            setCell(row, 4, safe(field.getRequired()), style);
            setCell(row, 5, safe(field.getRemark()), style);
            setCell(row, 6, safe(field.getFieldNameEn2()).toUpperCase(), style);
            setCell(row, 7, safe(field.getExternalFieldName()), style);

            rowNum++;

            if (field.getChildren() != null && !field.getChildren().isEmpty()) {
                rowNum = createFieldRows(sheet, rowNum, field.getChildren(), style, depth + 1);
            }
        }
        return rowNum;
    }

    private Row getOrCreateRow(Sheet sheet, int rowNum) {
        Row row = sheet.getRow(rowNum);
        if (row == null) {
            row = sheet.createRow(rowNum);
        }
        return row;
    }

    private void setCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    private String safe(String value) {
        return value != null && !value.isBlank() ? value : "无";
    }

    private CellStyle createBoldStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setFontHeightInPoints((short) 10);
        font.setBold(true);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        return style;
    }

    private CellStyle createNormalStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        return style;
    }

    private CellStyle createSectionStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setFontHeightInPoints((short) 10);
        font.setBold(true);
        font.setColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle createMarkerStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setFontHeightInPoints((short) 10);
        font.setItalic(true);
        font.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle createHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setFontHeightInPoints((short) 10);
        font.setBold(true);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
}
