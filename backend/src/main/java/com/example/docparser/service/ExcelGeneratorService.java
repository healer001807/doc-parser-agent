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
import java.util.UUID;

@Slf4j
@Service
public class ExcelGeneratorService {

    @Value("${file.excel-dir:./exports}")
    private String excelDir;

    private static final String[] TABLE_HEADERS = {
            "字段英文名", "字段中文名", "字段类型", "字段长度",
            "是否必传（Y/N）", "备注", "字段英文名", "外部接口字段名"
    };

    private static final String INPUT_SECTION = "【输入参数（请求）】";
    private static final String OUTPUT_SECTION = "【输出参数（返回）】";

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

            fillBasicInfo(sheet, parseResult.getInterfaceInfo());
            fillInputTable(sheet, parseResult.getRequestHeaders(), parseResult.getRequestBody());
            fillOutputTable(sheet, parseResult.getResponseCodes(), parseResult.getResponseBody());

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

    private void fillBasicInfo(Sheet sheet, InterfaceInfo info) {
        String[][] leftFields = {
                {"接口中文名", safe(info != null ? info.getApiNameCn() : "")},
                {"数据引进方式", safe(info != null ? info.getDataImportMethod() : "")},
                {"接口调用频率", safe(info != null ? info.getApiCallFrequency() : "")},
                {"外部厂商接口地址", safe(info != null ? info.getExternalVendorUrl() : "")},
                {"访问外数接口地址", safe(info != null ? info.getExternalDataUrl() : "")}
        };

        String[][] rightFields = {
                {"接口中文名", safe(info != null ? info.getApiNameCn() : "")},
                {"接口英文名", safe(info != null ? info.getApiNameEn() : "")},
                {"接口地址", safe(info != null ? info.getApiUrl() : "")}
        };

        CellStyle labelStyle = createLabelStyle(sheet.getWorkbook());
        CellStyle valueStyle = createValueStyle(sheet.getWorkbook());

        for (int i = 0; i < leftFields.length; i++) {
            Row row = getOrCreateRow(sheet, i);
            setCell(row, 0, leftFields[i][0], labelStyle);
            setCell(row, 1, leftFields[i][1], valueStyle);
        }

        for (int i = 0; i < rightFields.length; i++) {
            Row row = getOrCreateRow(sheet, i);
            setCell(row, 6, rightFields[i][0], labelStyle);
            setCell(row, 7, rightFields[i][1], valueStyle);
        }
    }

    private void fillInputTable(Sheet sheet, List<FieldDefinition> headers, List<FieldDefinition> body) {
        int rowNum = 6;

        rowNum = createSectionRow(sheet, rowNum, INPUT_SECTION);
        rowNum = createTableHeaderRow(sheet, rowNum);

        rowNum = createSubSectionRow(sheet, rowNum, "请求头（reqHeader）");
        if (headers != null && !headers.isEmpty()) {
            rowNum = createFieldRows(sheet, rowNum, headers, 0);
        }

        rowNum = createSubSectionRow(sheet, rowNum, "请求体");
        if (body != null && !body.isEmpty()) {
            rowNum = createFieldRows(sheet, rowNum, body, 0);
        }
    }

    private void fillOutputTable(Sheet sheet, List<FieldDefinition> codes, List<FieldDefinition> body) {
        int rowNum = findNextRow(sheet);

        rowNum = createSectionRow(sheet, rowNum, OUTPUT_SECTION);
        rowNum = createTableHeaderRow(sheet, rowNum);

        rowNum = createSubSectionRow(sheet, rowNum, "输出");
        if (codes != null && !codes.isEmpty()) {
            rowNum = createFieldRows(sheet, rowNum, codes, 0);
        }

        rowNum = createSubSectionRow(sheet, rowNum, "响应体");
        if (body != null && !body.isEmpty()) {
            rowNum = createFieldRows(sheet, rowNum, body, 0);
        }
    }

    private int createSectionRow(Sheet sheet, int rowNum, String text) {
        Row row = getOrCreateRow(sheet, rowNum);
        CellStyle style = createSectionStyle(sheet.getWorkbook());
        for (int i = 0; i < 8; i++) {
            setCell(row, i, i == 0 ? text : "", style);
        }
        return rowNum + 1;
    }

    private int createSubSectionRow(Sheet sheet, int rowNum, String text) {
        Row row = getOrCreateRow(sheet, rowNum);
        CellStyle style = createSubSectionStyle(sheet.getWorkbook());
        for (int i = 0; i < 8; i++) {
            setCell(row, i, i == 0 ? text : "", style);
        }
        return rowNum + 1;
    }

    private int createTableHeaderRow(Sheet sheet, int rowNum) {
        Row row = getOrCreateRow(sheet, rowNum);
        CellStyle style = createTableHeaderStyle(sheet.getWorkbook());
        for (int i = 0; i < TABLE_HEADERS.length; i++) {
            setCell(row, i, TABLE_HEADERS[i], style);
        }
        return rowNum + 1;
    }

    private int createFieldRows(Sheet sheet, int rowNum, List<FieldDefinition> fields, int depth) {
        CellStyle dataStyle = createDataStyle(sheet.getWorkbook());
        for (FieldDefinition field : fields) {
            Row row = getOrCreateRow(sheet, rowNum);
            String indent = depth > 0 ? "  ".repeat(depth) : "";

            setCell(row, 0, indent + safe(field.getFieldNameEn()), dataStyle);
            setCell(row, 1, safe(field.getFieldNameCn()), dataStyle);
            setCell(row, 2, safe(field.getFieldType()), dataStyle);
            setCell(row, 3, safe(field.getFieldLength()), dataStyle);
            setCell(row, 4, safe(field.getRequired()), dataStyle);
            setCell(row, 5, safe(field.getRemark()), dataStyle);
            setCell(row, 6, safe(field.getFieldNameEn2()), dataStyle);
            setCell(row, 7, safe(field.getExternalFieldName()), dataStyle);

            rowNum++;

            if (field.getChildren() != null && !field.getChildren().isEmpty()) {
                rowNum = createFieldRows(sheet, rowNum, field.getChildren(), depth + 1);
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

    private int findNextRow(Sheet sheet) {
        return sheet.getLastRowNum() + 1;
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

    private CellStyle createLabelStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setFontHeightInPoints((short) 10);
        font.setBold(true);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.RIGHT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        return style;
    }

    private CellStyle createValueStyle(Workbook wb) {
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
        font.setFontHeightInPoints((short) 11);
        font.setBold(true);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle createSubSectionStyle(Workbook wb) {
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

    private CellStyle createTableHeaderStyle(Workbook wb) {
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

    private CellStyle createDataStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        return style;
    }
}
