package com.aryn.cloud.order.support.procurement;

import com.aryn.cloud.order.api.dto.ProcurementReviewCommand;
import com.aryn.cloud.order.api.dto.ProcurementBulkReviewCommand;
import com.aryn.cloud.order.api.entity.*;
import com.aryn.cloud.order.service.impl.*;
import com.aryn.cloud.product.api.remote.RemoteReplenishImportMatchService;
import com.aryn.cloud.product.api.support.ProductMeasurementRules.Measurement;
import com.aryn.cloud.product.api.vo.ReplenishImportMatchVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProcurementReviewServiceTest {
    private final ProcurementDraftService drafts = mock(ProcurementDraftService.class);
    private final ProcurementReviewRepository repo = mock(ProcurementReviewRepository.class);
    private final RemoteReplenishImportMatchService products = mock(RemoteReplenishImportMatchService.class);
    private final ProcurementReviewService service = new ProcurementReviewService(drafts, repo, new ObjectMapper());
    private ProcurementDraftRow row;
    private ReplenishImportMatchVO fact;
    @BeforeEach void setup() {
        ReflectionTestUtils.setField(service, "products", products);
        row = new ProcurementDraftRow(); row.setId("row"); row.setBatchId("batch"); row.setRawText("苹果约3斤");
        row.setRawQuantity(new BigDecimal("3")); row.setRawUnit("斤"); row.setReviewStatus("REQUIRED");
        when(repo.row("batch", "row")).thenReturn(row);
        fact = new ReplenishImportMatchVO(); fact.setSkuId("sku"); fact.setSkuStatus("0"); fact.setSpuStatus("1"); fact.setStock(20);
        fact.setMeasurement(new Measurement("袋", "MASS", "g", new BigDecimal("1000"), "EXACT", 1, 1, 2L));
        when(products.matchSkuIds("tenant", List.of("sku"))).thenReturn(List.of(fact));
        when(repo.apply(eq("actor"), anyString(), anyLong(), any(), any())).thenAnswer(call -> new ProcurementReviewRepository.Outcome("APPLIED", call.getArgument(3), 2));
    }
    private ProcurementReviewCommand command(String action, Integer quantity, boolean rounding) {
        return new ProcurementReviewCommand(0, action, "sku", quantity, rounding, "按实际需求核对");
    }
    @Test void approximateRoundingRequiresConsentAndKeepsExcessEvidence() {
        assertThrows(RuntimeException.class, () -> service.review("tenant", "actor", "draft", "batch", "row", command("ACCEPT_SUGGESTION", null, false)));
        var outcome = service.review("tenant", "actor", "draft", "batch", "row", command("ACCEPT_SUGGESTION", null, true));
        assertEquals(2, outcome.row().getConfirmedSalesQuantity()); assertEquals("CONFIRMED", outcome.row().getReviewStatus());
        assertEquals(0, new BigDecimal("500").compareTo(outcome.row().getExcessBaseQuantity()));
    }
    @Test void missingOrMismatchedUnitsCannotBeAcceptedButExplicitSalesQuantityCanResolveThem() {
        row.setRawUnit("米");
        assertThrows(RuntimeException.class, () -> service.review("tenant", "actor", "draft", "batch", "row", command("ACCEPT_SUGGESTION", null, true)));
        var outcome = service.review("tenant", "actor", "draft", "batch", "row", command("CONFIRM_SALES", 3, false));
        assertEquals("MANUAL_SALES", outcome.row().getUnitStatus()); assertEquals(3, outcome.row().getConfirmedSalesQuantity());
    }
    @Test void explicitSalesStillCannotBypassMoqStepStockOrOffShelf() {
        fact.setMeasurement(new Measurement("袋", "MASS", "g", new BigDecimal("1000"), "EXACT", 3, 2, 3L));
        assertThrows(RuntimeException.class, () -> service.review("tenant", "actor", "draft", "batch", "row", command("CONFIRM_SALES", 4, true)));
        assertThrows(RuntimeException.class, () -> service.review("tenant", "actor", "draft", "batch", "row", command("CONFIRM_SALES", -1, true)));
        fact.setStock(2);
        assertThrows(RuntimeException.class, () -> service.review("tenant", "actor", "draft", "batch", "row", command("CONFIRM_SALES", 3, true)));
        fact.setSkuStatus("1");
        assertThrows(RuntimeException.class, () -> service.review("tenant", "actor", "draft", "batch", "row", command("SELECT_SKU", null, false)));
        verify(repo, never()).apply(anyString(), anyString(), anyLong(), any(), any());
    }
    @Test void rejectingRowDoesNotRequireAvailableProductAndAuthorizationRunsFirst() {
        var result = service.review("tenant", "actor", "draft", "batch", "row", command("REJECT", null, false));
        assertEquals("REJECTED", result.row().getReviewStatus()); verifyNoInteractions(products);
        doThrow(new IllegalStateException("access revoked")).when(drafts).batch("tenant", "actor", "draft", "batch");
        assertThrows(RuntimeException.class, () -> service.review("tenant", "actor", "draft", "batch", "row", command("CONFIRM_SALES", 3, false)));
        verifyNoInteractions(products);
    }
    @Test void bulkReviewKeepsUnknownRowsAndConflictsPendingWhileAcceptingExactRows() {
        row.setRevision(0L); row.setParseStatus("PARSED"); row.setMatchStatus("MATCHED"); row.setUnitStatus("EXACT");
        row.setQuantityStatus("VALID"); row.setSelectedSkuId("sku"); row.setRawText("苹果2袋");
        row.setRawQuantity(new BigDecimal("2")); row.setRawUnit("袋");
        var unknown = new ProcurementDraftRow(); org.springframework.beans.BeanUtils.copyProperties(row, unknown);
        unknown.setId("unknown"); unknown.setUnitStatus("UNKNOWN"); when(repo.row("batch", "unknown")).thenReturn(unknown);
        var result = service.reviewReady("tenant", "actor", "draft", "batch", new ProcurementBulkReviewCommand(List.of(
            new ProcurementBulkReviewCommand.RowRevision("row", 0), new ProcurementBulkReviewCommand.RowRevision("unknown", 0),
            new ProcurementBulkReviewCommand.RowRevision("row-conflict", 9)), "已核对精确行"));
        assertEquals(1, result.applied().size()); assertEquals(2, result.problems().size());
        assertEquals(2, result.applied().get(0).row().getConfirmedSalesQuantity());
        assertEquals("REVIEW_REQUIRED", result.problems().get(0).code()); assertEquals("CONFLICT", result.problems().get(1).code());
    }
    @Test void bulkReviewRechecksRoundingAndStockAndRejectsDuplicateSelection() {
        row.setRevision(0L); row.setParseStatus("PARSED"); row.setMatchStatus("MATCHED"); row.setUnitStatus("EXACT");
        row.setQuantityStatus("VALID"); row.setSelectedSkuId("sku"); row.setRawText("苹果3斤");
        var selection = new ProcurementBulkReviewCommand(List.of(new ProcurementBulkReviewCommand.RowRevision("row", 0)), "核对");
        assertEquals(1, service.reviewReady("tenant", "actor", "draft", "batch", selection).problems().size());
        row.setRawQuantity(new BigDecimal("2")); row.setRawUnit("袋"); fact.setStock(1);
        assertEquals(1, service.reviewReady("tenant", "actor", "draft", "batch", selection).problems().size());
        assertThrows(RuntimeException.class, () -> service.reviewReady("tenant", "actor", "draft", "batch", new ProcurementBulkReviewCommand(
            List.of(selection.rows().get(0), selection.rows().get(0)), "核对")));
        verify(repo, never()).apply(anyString(), anyString(), anyLong(), any(), any());
    }
}
