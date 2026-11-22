package com.aryn.cloud.product.api.support;

import com.aryn.cloud.product.api.support.ProductMeasurementRules.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class ProductMeasurementRulesTest {
    private Measurement bag(String precision) {
        return new Measurement("袋", "MASS", "g", new BigDecimal("1000"), precision, 1, 1, 2L);
    }
    private Decision convert(String quantity, String unit, Measurement measurement) {
        return ProductMeasurementRules.convert(quantity == null ? null : new BigDecimal(quantity), unit, false, measurement);
    }
    @Test void threeJinAndOnePointFiveKgKeepDecimalAndExplainExtraHalfKg() {
        var jin = convert("3", "斤", bag("EXACT"));
        var kg = convert("1.5", "kg", bag("EXACT"));
        assertEquals(0, new BigDecimal("1500").compareTo(jin.normalizedBaseQuantity()));
        assertEquals(jin.suggestedSalesQuantity(), kg.suggestedSalesQuantity());
        assertEquals(2, jin.suggestedSalesQuantity());
        assertEquals(0, new BigDecimal("500").compareTo(jin.excessBaseQuantity()));
        assertTrue(jin.requiresReview());
        assertEquals("PACKAGING_OR_THRESHOLD_ROUNDING", jin.reason());
        assertEquals(2L, jin.measurementRevision());
    }
    @Test void volumePackagingAndApproximationAreExplicit() {
        var bottle = new Measurement("瓶", "VOLUME", "ml", new BigDecimal("500"), "EXACT", 1, 1, 1L);
        assertEquals(1, convert("0.5", "L", bottle).suggestedSalesQuantity());
        assertFalse(convert("500", "ml", bottle).requiresReview());
        assertTrue(convert("2", "kg", bag("APPROX")).requiresReview());
        assertEquals("APPROX", convert("2", "kg", bag("APPROX")).unitStatus());
        assertTrue(ProductMeasurementRules.convert(new BigDecimal("2"), "kg", true, bag("EXACT")).requiresReview());
        var box = new Measurement("箱", "COUNT", "pcs", new BigDecimal("24"), "EXACT", 1, 1, 1L);
        assertEquals(2, convert("48", "件", box).suggestedSalesQuantity());
    }
    @ParameterizedTest @CsvSource({"1,ml,MISMATCH", "1,桶,UNKNOWN", "1, ,UNKNOWN"})
    void incompatibleOrUnknownUnitsNeverProduceAnAutomaticQuantity(String quantity, String unit, String expected) {
        var decision = convert(quantity, unit, bag("EXACT"));
        assertNull(decision.suggestedSalesQuantity());
        assertEquals(expected, decision.unitStatus());
        assertTrue(decision.requiresReview());
    }
    @Test void unknownFactsPermitExplicitSalesPiecesButNeverInferWeight() {
        var legacy = new Measurement("件", null, null, null, "UNKNOWN", 1, 1, 0L);
        assertEquals(3, convert("3", "件", legacy).suggestedSalesQuantity());
        assertFalse(convert("3", "件", legacy).requiresReview());
        assertNull(convert("3", "kg", legacy).suggestedSalesQuantity());
        assertNull(convert("1", "kg", bag("UNKNOWN")).suggestedSalesQuantity());
    }
    @ParameterizedTest @CsvSource({"0", "-1", "999999999999999999999999999999999999999999999", "0.00000000000000000001"})
    void invalidOrUnrepresentableQuantitiesCannotBeConfirmed(String quantity) {
        assertNull(convert(quantity, "袋", bag("EXACT")).suggestedSalesQuantity());
        assertTrue(convert(quantity, "袋", bag("EXACT")).requiresReview());
    }
    @Test void missingQuantityIsNeverOneAndMoqStepAreOffsetFromMinimum() {
        assertNull(convert(null, "袋", bag("EXACT")).suggestedSalesQuantity());
        assertFalse(ReplenishMatchRules.quantityAcceptable(2, 3, 2));
        assertTrue(ReplenishMatchRules.quantityAcceptable(3, 3, 2));
        assertFalse(ReplenishMatchRules.quantityAcceptable(4, 3, 2));
        assertTrue(ReplenishMatchRules.quantityAcceptable(5, 3, 2));
        assertEquals(5, ReplenishMatchRules.suggestedQuantity(6, 3, 2));
        var threshold = new Measurement("袋", "MASS", "g", new BigDecimal("1000"), "EXACT", 3, 2, 1L);
        assertEquals(5, convert("4", "袋", threshold).suggestedSalesQuantity());
        assertTrue(convert("4", "袋", threshold).requiresReview());
    }
    @Test void malformedFactsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> ProductMeasurementRules.validate(new Measurement("袋", "MASS", "ml", BigDecimal.ONE, "EXACT", 1, 1, 1L)));
        assertThrows(IllegalArgumentException.class, () -> ProductMeasurementRules.validate(new Measurement("袋", "MASS", "g", BigDecimal.ZERO, "EXACT", 1, 1, 1L)));
        assertThrows(IllegalArgumentException.class, () -> ProductMeasurementRules.validate(new Measurement("袋", null, null, null, "EXACT", 1, 1, 1L)));
        assertThrows(IllegalArgumentException.class, () -> ProductMeasurementRules.validate(new Measurement("袋", null, null, null, "UNKNOWN", 0, 1, 1L)));
    }
}
