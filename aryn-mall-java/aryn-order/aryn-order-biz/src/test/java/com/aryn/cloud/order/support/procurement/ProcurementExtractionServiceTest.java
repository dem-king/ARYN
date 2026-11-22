package com.aryn.cloud.order.support.procurement;

import com.aryn.cloud.order.api.entity.ProcurementSourceBatch;
import com.aryn.cloud.order.service.impl.ProcurementExtractionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class ProcurementExtractionServiceTest {
    private final ProcurementExtractionService extraction = new ProcurementExtractionService(2000);
    @Test void textKeepsDecimalUnitsPositionsAndUnknownSourceLines() {
        var rows = extraction.text("苹果3斤\n牛奶1.5升\n数量未知商品");
        assertEquals(3, rows.size());
        assertEquals(new BigDecimal("3"), rows.get(0).quantity()); assertEquals("斤", rows.get(0).unit());
        assertEquals(new BigDecimal("1.5"), rows.get(1).quantity()); assertEquals("升", rows.get(1).unit());
        assertEquals(2, rows.get(1).row()); assertTrue(rows.get(1).offset() > 0);
        assertNull(rows.get(2).quantity()); assertNotNull(rows.get(2).rawText());
    }
    @Test void negativeAndRangeNeverBecomePositivePurchases() {
        var rows = extraction.text("苹果-2斤\n香蕉1-2斤");
        assertTrue(rows.stream().allMatch(row -> row.quantity() == null));
    }
    @Test void excelKeepsPhysicalRowsMergedOwnerAndDecimalWithoutFillingMissingUnitsOrQuantity() throws Exception {
        byte[] bytes;
        try (var book = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = book.createSheet("首批"); var head = sheet.createRow(0);
            String[] labels = {"品名", "数量", "单位", "归属人", "规格", "码制", "商品编码"};
            for (int i = 0; i < labels.length; i++) head.createCell(i).setCellValue(labels[i]);
            var a = sheet.createRow(2); a.createCell(0).setCellValue("牛奶"); a.createCell(1).setCellValue("0.123456789123456789");
            a.createCell(2).setCellValue("升"); a.createCell(3).setCellValue("张三");
            var b = sheet.createRow(3); b.createCell(0).setCellValue("苹果");
            sheet.addMergedRegion(new CellRangeAddress(2, 3, 3, 3));
            var repeat = sheet.createRow(4); for (int i = 0; i < labels.length; i++) repeat.createCell(i).setCellValue(labels[i]);
            book.write(out); bytes = out.toByteArray();
        }
        var rows = extraction.excel(bytes); assertEquals(2, rows.size());
        assertEquals(3, rows.get(0).row()); assertEquals("首批", rows.get(0).sheet());
        assertEquals(new BigDecimal("0.123456789123456789"), rows.get(0).quantity());
        assertEquals("张三", rows.get(1).owner()); assertNull(rows.get(1).quantity()); assertNull(rows.get(1).unit());
    }
    @Test void modelMustCoverEverySourceExactlyOnceWithoutInventingNameOrQuantity() throws Exception {
        var sources = extraction.text("苹果3斤\n香蕉2件"); var json = new ObjectMapper();
        assertThrows(IllegalArgumentException.class, () -> extraction.grounded(sources, json.readTree("[]")));
        String first = sources.get(0).position(), second = sources.get(1).position();
        String valid = "[{\"position\":\"" + first + "\",\"name\":\"苹果\",\"quantity\":3,\"unit\":\"斤\"},{\"position\":\"" + second + "\",\"name\":\"香蕉\",\"quantity\":2,\"unit\":\"件\"}]";
        assertEquals(2, extraction.grounded(sources, json.readTree(valid)).rows().size());
        assertThrows(IllegalArgumentException.class, () -> extraction.grounded(sources, json.readTree(valid.replace("香蕉", "牛肉"))));
        assertThrows(IllegalArgumentException.class, () -> extraction.grounded(sources, json.readTree(valid.replace(second, first))));
        assertThrows(IllegalArgumentException.class, () -> extraction.grounded(sources, json.readTree(valid.replace("\"quantity\":3", "\"quantity\":30"))));
    }
    @Test void overLimitInputFailsWithNoTruncationAndExtractionRejectsOpenTransaction() {
        assertThrows(RuntimeException.class, () -> extraction.text("苹果3斤\n".repeat(2001)));
        var batch = new ProcurementSourceBatch(); batch.setInputType("TEXT"); batch.setInputText("苹果3斤");
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try { assertThrows(IllegalStateException.class, () -> extraction.sources(batch)); }
        finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
    }
}
