/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.liaochong.myexcel.core;

import com.github.liaochong.myexcel.core.parser.HtmlTableParser;
import com.github.liaochong.myexcel.core.parser.ParseConfig;
import com.github.liaochong.myexcel.core.parser.Table;
import com.github.liaochong.myexcel.core.parser.Td;
import com.github.liaochong.myexcel.core.parser.Tr;
import com.github.liaochong.myexcel.core.strategy.SheetStrategy;
import com.github.liaochong.myexcel.core.strategy.WidthStrategy;
import com.github.liaochong.myexcel.utils.StringUtil;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * HtmlToExcelFactory
 * <p>
 * 用于将html table解析成excel
 * </p>
 *
 * @author liaochong
 * @version 1.0
 */
public class HtmlToExcelFactory extends AbstractExcelFactory {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(HtmlToExcelFactory.class);
    private HtmlTableParser htmlTableParser;

    /**
     * 读取html
     *
     * @param htmlFile html文件
     * @return HtmlToExcelFactory
     * @throws Exception 解析异常
     */
    public static HtmlToExcelFactory readHtml(File htmlFile) throws Exception {
        if (Objects.isNull(htmlFile) || !htmlFile.exists()) {
            throw new NoSuchFileException("html file is not exist");
        }
        HtmlToExcelFactory factory = new HtmlToExcelFactory();
        factory.htmlTableParser = HtmlTableParser.of(htmlFile);
        return factory;
    }

    /**
     * 读取html
     *
     * @param html html字符串
     * @return HtmlToExcelFactory
     */
    public static HtmlToExcelFactory readHtml(String html) {
        HtmlToExcelFactory factory = new HtmlToExcelFactory();
        factory.htmlTableParser = HtmlTableParser.of(html);
        return factory;
    }

    /**
     * 读取html
     *
     * @param htmlFile           html文件
     * @param htmlToExcelFactory 实例对象
     * @return HtmlToExcelFactory
     * @throws Exception 解析异常
     */
    public static HtmlToExcelFactory readHtml(File htmlFile, HtmlToExcelFactory htmlToExcelFactory) throws Exception {
        if (Objects.isNull(htmlFile) || !htmlFile.exists()) {
            throw new NoSuchFileException("Html file is not exist");
        }
        if (Objects.isNull(htmlToExcelFactory)) {
            throw new NullPointerException("HtmlToExcelFactory can not be null");
        }
        htmlToExcelFactory.htmlTableParser = HtmlTableParser.of(htmlFile);
        return htmlToExcelFactory;
    }

    /**
     * 读取html
     *
     * @param html               html内容
     * @param htmlToExcelFactory 实例对象
     * @return HtmlToExcelFactory
     * @throws Exception 解析异常
     */
    public static HtmlToExcelFactory readHtml(String html, HtmlToExcelFactory htmlToExcelFactory) throws Exception {
        if (StringUtil.isBlank(html)) {
            throw new IllegalArgumentException("Html content is empty");
        }
        if (Objects.isNull(htmlToExcelFactory)) {
            throw new NullPointerException("HtmlToExcelFactory can not be null");
        }
        htmlToExcelFactory.htmlTableParser = HtmlTableParser.of(html);
        return htmlToExcelFactory;
    }

    /**
     * 开始构建
     *
     * @return Workbook
     */
    @Override
    public Workbook build() {
        try {
            ParseConfig parseConfig = new ParseConfig(widthStrategy, sheetStrategy);
            List<Table> tables = htmlTableParser.getAllTable(parseConfig);
            htmlTableParser = null;
            return this.build(tables);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 开始构建
     *
     * @param tables   tables
     * @param workbook workbook
     * @return Workbook
     */
    Workbook build(List<Table> tables, Workbook workbook) {
        if (Objects.nonNull(workbook)) {
            this.workbook = workbook;
        }
        return build(tables);
    }

    /**
     * 开始构建
     *
     * @param tables tables
     * @return Workbook
     */
    Workbook build(List<Table> tables) {
        if (Objects.isNull(tables) || tables.isEmpty()) {
            log.warn("There is no any table exist");
            return emptyWorkbook();
        }
        log.info("Start build excel");
        long startTime = System.currentTimeMillis();
        // 1、创建工作簿
        if (Objects.isNull(workbook)) {
            workbook = new XSSFWorkbook();
        }
        this.initCellStyle(workbook);
        // 2、处理解析表格
        if (SheetStrategy.isMultiSheet(sheetStrategy)) {
            buildTablesWithMultiSheet(tables);
        } else {
            buildTablesWithOneSheet(tables);
        }
        log.info("Build excel takes {} ms", System.currentTimeMillis() - startTime);
        return workbook;
    }

    /**
     * MultiSheet 策略
     *
     * @param tables
     */
    private void buildTablesWithMultiSheet(List<Table> tables) {
        for (int i = 0, size = tables.size(); i < size; i++) {
            Table table = tables.get(i);
            String sheetName = this.getRealSheetName(table.getCaption());
            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                sheet = workbook.createSheet(sheetName);
            }
            boolean hasTd = table.getTrList().stream().map(Tr::getTdList).anyMatch(list -> !list.isEmpty());
            if (!hasTd) {
                continue;
            }
            // 设置单元格样式
            this.setTdOfTable(table, sheet);
            this.freezePane(i, sheet);
            // 移除table
            tables.set(i, null);
        }
    }

    /**
     * oneSheet 策略
     *
     * @param tables tables
     */
    private void buildTablesWithOneSheet(List<Table> tables) {
        String sheetName = this.getRealSheetName(tables.get(0).getCaption());
        for (int i = 0; i < tables.size(); i++) {
            Table table = tables.get(i);
            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                sheet = workbook.createSheet(sheetName);
            }
            boolean hasTd = table.getTrList().stream().map(Tr::getTdList).anyMatch(list -> !list.isEmpty());
            if (!hasTd) {
                continue;
            }
            // 设置单元格样式
            this.setTdOfTable(table, sheet);
            this.freezePane(i, sheet);
            // 移除table
            tables.set(i, null);
        }
    }

    /**
     * 设置所有单元格，自适应列宽，单元格最大支持字符长度255
     */
    private void setTdOfTable(Table table, Sheet sheet) {
        int maxColIndex = 0;
        if (WidthStrategy.isAutoWidth(widthStrategy) && !table.getTrList().isEmpty()) {
            maxColIndex = table.getTrList().parallelStream()
                    .mapToInt(tr -> tr.getTdList().stream().mapToInt(Td::getCol).max().orElse(0))
                    .max()
                    .orElse(0);
        }
        Map<Integer, Integer> colMaxWidthMap = this.getColMaxWidthMap(table.getTrList());
        final Integer sheetLastRowIndex = sheet.getLastRowNum();
        for (int i = 0, size = table.getTrList().size(); i < size; i++) {
            Tr tr = table.getTrList().get(i);
            this.updateTrIndex(tr, sheetLastRowIndex);
            this.createRow(tr, sheet);
            tr.setTdList(null);
        }
        table.setTrList(null);
        this.setColWidth(colMaxWidthMap, sheet, maxColIndex);
    }

    /**
     * 为 oneSheet 策略更新 tr,td 的 rowIndex
     *
     * @param tr                当前tr
     * @param sheetLastRowIndex sheet 最后行下标
     */
    private void updateTrIndex(Tr tr, Integer sheetLastRowIndex) {
        if (SheetStrategy.isOneSheet(sheetStrategy)) {
            if (sheetLastRowIndex != 0) {
                sheetLastRowIndex += 1;
            }
            tr.setIndex(tr.getIndex() + sheetLastRowIndex);
            tr.getTdList().forEach(td -> td.setRow(tr.getIndex()));
        }
    }
}
