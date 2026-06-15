package com.example.docparser.service;

import com.example.docparser.model.dto.FieldDefinition;
import com.example.docparser.model.dto.ParseResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

/**
 * Excel生成服务 - 按照项目规范生成三部分结构的Excel文档
 */
@Slf4j
@Service
public class ExcelGeneratorService {

    @Value("${file.excel-dir:./exports}")
    private String excelDir;

    // Excel样式常量
    private static final String[] HEADER_COLUMNS = {
            "字段英文(文档提供)", "字段描述", "类型", "长度", "是否必传",
            "字段英文(文档提供)", "字段英文(文档提供)"
    };

    /**
     * 生成标准格式的Excel文件
     *
     * @param documentId  文档ID
     * @param parseResult 解析结果
     * @param originalName 原始文件名
     * @return 生成的Excel文件路径
     */
    public String generateExcel(Long documentId, ParseResult parseResult, String originalName) throws IOException {
        // 确保目录存在
        Path exportPath = Paths.get(excelDir);
        Files.createDirectories(exportPath);

        String excelFileName = "接口文档_" + documentId + "_" + UUID.randomUUID().toString().substring(0, 8) + ".xlsx";
        Path excelFilePath = exportPath.resolve(excelFileName);

        try (Workbook workbook = new XSSFWorkbook()) {
            // 创建样式
            CellStyle titleStyle = createTitleStyle(workbook);
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);
            CellStyle sectionStyle = createSectionStyle(workbook);

            // ========== Sheet1: 接口信息 ==========
            Sheet infoSheet = workbook.createSheet("接口信息");
            createInfoSheet(infoSheet, parseResult, titleStyle, headerStyle, dataStyle, sectionStyle);

            // ========== Sheet2: 请求参数 ==========
            Sheet requestSheet = workbook.createSheet("请求参数");
            createParamSheet(requestSheet, "请求头", parseResult.getRequestHeaders(),
                    "请求体参数", parseResult.getRequestParams(),
                    titleStyle, headerStyle, dataStyle, sectionStyle);

            // ========== Sheet3: 响应参数 ==========
            Sheet responseSheet = workbook.createSheet("响应参数");
            createParamSheet(responseSheet, "响应公共体", parseResult.getResponseCommonBody(),
                    "响应业务参数", parseResult.getResponseParams(),
                    titleStyle, headerStyle, dataStyle, sectionStyle);

            // 写入文件
            try (FileOutputStream fos = new FileOutputStream(excelFilePath.toFile())) {
                workbook.write(fos);
            }
        }

        log.info("Excel文件已生成: {}", excelFilePath);
        return excelFilePath.toString();
    }

    /**
     * 创建接口信息Sheet
     */
    private void createInfoSheet(Sheet sheet, ParseResult result,
                                 CellStyle titleStyle, CellStyle headerStyle,
                                 CellStyle dataStyle, CellStyle sectionStyle) {
        // 标题行
        Row titleRow = sheet.createRow(0);
        titleRow.setHeightInPoints(30);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("第三方接口文档解析 - 接口基本信息");
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 6));

        // 列宽设置
        int[] columnWidths = {20, 40, 20, 20, 20, 20, 20};
        for (int i = 0; i < columnWidths.length; i++) {
            sheet.setColumnWidth(i, columnWidths[i] * 256);
        }

        // 基本信息数据
        String[][] infoData = {
                {"内部接口名", getSafe(result.getInterfaceInfo() != null ? result.getInterfaceInfo().getInternalApiName() : "")},
                {"外部接口名", getSafe(result.getInterfaceInfo() != null ? result.getInterfaceInfo().getExternalApiName() : "")},
                {"第三方公司简称", getSafe(result.getInterfaceInfo() != null ? result.getInterfaceInfo().getCompanyCode() : "")},
                {"数据来源", getSafe(result.getInterfaceInfo() != null ? result.getInterfaceInfo().getDataSource() : "")},
                {"请求方式", getSafe(result.getInterfaceInfo() != null ? result.getInterfaceInfo().getHttpMethod() : "")},
                {"请求URL", getSafe(result.getInterfaceInfo() != null ? result.getInterfaceInfo().getRequestUrl() : "")},
                {"协议", getSafe(result.getInterfaceInfo() != null ? result.getInterfaceInfo().getProtocol() : "")},
                {"数据格式", getSafe(result.getInterfaceInfo() != null ? result.getInterfaceInfo().getDataFormat() : "")},
                {"接口描述", getSafe(result.getInterfaceInfo() != null ? result.getInterfaceInfo().getApiDescription() : "")},
                {"备注", getSafe(result.getInterfaceInfo() != null ? result.getInterfaceInfo().getRemark() : "")},
        };

        int rowNum = 2;
        // 小标题
        Row sectionRow = sheet.createRow(rowNum++);
        Cell sectionCell = sectionRow.createCell(0);
        sectionCell.setCellValue("接口基本信息");
        sectionCell.setCellStyle(sectionStyle);
        sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, 6));

        for (String[] data : infoData) {
            Row row = sheet.createRow(rowNum++);
            Cell keyCell = row.createCell(0);
            keyCell.setCellValue(data[0]);
            keyCell.setCellStyle(headerStyle);
            Cell valueCell = row.createCell(1);
            valueCell.setCellValue(data[1]);
            valueCell.setCellStyle(dataStyle);
            sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 1, 6));
        }
    }

    /**
     * 创建参数Sheet（请求参数或响应参数）
     */
    private void createParamSheet(Sheet sheet, String section1Name, List<FieldDefinition> section1Data,
                                  String section2Name, List<FieldDefinition> section2Data,
                                  CellStyle titleStyle, CellStyle headerStyle,
                                  CellStyle dataStyle, CellStyle sectionStyle) {

        // 标题行
        Row titleRow = sheet.createRow(0);
        titleRow.setHeightInPoints(30);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("第三方接口文档解析 - " + section1Name + " & " + section2Name);
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 6));

        // 列宽设置
        int[] columnWidths = {25, 30, 15, 12, 12, 25, 25};
        for (int i = 0; i < columnWidths.length; i++) {
            sheet.setColumnWidth(i, columnWidths[i] * 256);
        }

        int rowNum = 2;

        // ====== 第一部分 ======
        Row section1Row = sheet.createRow(rowNum++);
        Cell s1Cell = section1Row.createCell(0);
        s1Cell.setCellValue(section1Name);
        s1Cell.setCellStyle(sectionStyle);
        sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, 6));

        // 表头
        rowNum = createHeaderRow(sheet, rowNum, headerStyle);

        // 数据行
        if (section1Data != null && !section1Data.isEmpty()) {
            rowNum = createFieldRows(sheet, rowNum, section1Data, dataStyle, 0);
        } else {
            Row emptyRow = sheet.createRow(rowNum++);
            Cell emptyCell = emptyRow.createCell(0);
            emptyCell.setCellValue("（暂无数据）");
            emptyCell.setCellStyle(dataStyle);
        }

        // 空行
        rowNum++;

        // ====== 第二部分 ======
        Row section2Row = sheet.createRow(rowNum++);
        Cell s2Cell = section2Row.createCell(0);
        s2Cell.setCellValue(section2Name);
        s2Cell.setCellStyle(sectionStyle);
        sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, 6));

        // 表头
        rowNum = createHeaderRow(sheet, rowNum, headerStyle);

        // 数据行
        if (section2Data != null && !section2Data.isEmpty()) {
            rowNum = createFieldRows(sheet, rowNum, section2Data, dataStyle, 0);
        } else {
            Row emptyRow = sheet.createRow(rowNum++);
            Cell emptyCell = emptyRow.createCell(0);
            emptyCell.setCellValue("（暂无数据）");
            emptyCell.setCellStyle(dataStyle);
        }
    }

    /**
     * 创建表头行
     */
    private int createHeaderRow(Sheet sheet, int rowNum, CellStyle headerStyle) {
        Row headerRow = sheet.createRow(rowNum++);
        for (int i = 0; i < HEADER_COLUMNS.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(HEADER_COLUMNS[i]);
            cell.setCellStyle(headerStyle);
        }
        return rowNum;
    }

    /**
     * 递归创建字段行（支持嵌套子字段）
     */
    private int createFieldRows(Sheet sheet, int rowNum, List<FieldDefinition> fields,
                                CellStyle dataStyle, int depth) {
        for (FieldDefinition field : fields) {
            Row row = sheet.createRow(rowNum++);

            // 缩进处理：嵌套字段在前面加空格或点号表示层级
            String prefix = depth > 0 ? "└─ " + "  ".repeat(depth - 1) : "";

            String[] values = {
                    prefix + getSafe(field.getFieldNameEn()),
                    getSafe(field.getFieldDescription()),
                    getSafe(field.getFieldType()),
                    getSafe(field.getLength()),
                    getSafe(field.getRequired()),
                    getSafe(field.getFieldNameEn2()),
                    getSafe(field.getFieldNameEn3())
            };

            for (int i = 0; i < values.length; i++) {
                Cell cell = row.createCell(i);
                cell.setCellValue(values[i]);
                cell.setCellStyle(dataStyle);
            }

            // 递归处理子字段
            if (field.getChildren() != null && !field.getChildren().isEmpty()) {
                rowNum = createFieldRows(sheet, rowNum, field.getChildren(), dataStyle, depth + 1);
            }
        }
        return rowNum;
    }

    // ==================== 样式定义 ====================

    private CellStyle createTitleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontName("微软雅黑");
        font.setFontHeightInPoints((short) 16);
        font.setBold(true);
        font.setColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        setBorders(style);
        return style;
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontName("微软雅黑");
        font.setFontHeightInPoints((short) 11);
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setWrapText(true);
        setBorders(style);
        return style;
    }

    private CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontName("微软雅黑");
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        setBorders(style);
        // 交替行颜色
        return style;
    }

    private CellStyle createSectionStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontName("微软雅黑");
        font.setFontHeightInPoints((short) 12);
        font.setBold(true);
        font.setColor(IndexedColors.DARK_GREEN.getIndex());
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        setBorders(style);
        return style;
    }

    private void setBorders(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    private String getSafe(String value) {
        return value != null ? value : "";
    }
}
