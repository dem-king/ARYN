package com.aryn.cloud.product.support;

import com.aryn.cloud.product.api.entity.GoodsSku;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class GoodsMeasurementSupportTest {
    private GoodsSku exact() {
        return new GoodsSku().setSalesUnit("袋").setBaseDimension("MASS").setBaseUnit("g")
            .setBaseQtyPerSalesUnit(new BigDecimal("1000")).setQuantityPrecision("EXACT")
            .setMinOrderQty(3).setStepQty(2).setMeasurementRevision(7L);
    }
    @Test void legacySaveRetainsAuditedFactsAndRevision() {
        var requested = new GoodsSku();
        GoodsMeasurementSupport.prepare(requested, exact());
        assertEquals("EXACT", requested.getQuantityPrecision());
        assertEquals(new BigDecimal("1000"), requested.getBaseQtyPerSalesUnit());
        assertEquals(7L, requested.getMeasurementRevision());
        assertEquals(3, requested.getMinOrderQty());
    }
    @Test void manualRevisionProtectsConcurrentEditsAndCopyStartsNewVersion() {
        var stale = exact().setMeasurementRevision(6L);
        assertThrows(IllegalArgumentException.class, () -> GoodsMeasurementSupport.prepare(stale, exact()));
        var changed = exact().setBaseQtyPerSalesUnit(new BigDecimal("500"));
        GoodsMeasurementSupport.prepare(changed, exact());
        assertEquals(8L, changed.getMeasurementRevision());
        var copied = exact(); GoodsMeasurementSupport.prepare(copied, null);
        assertEquals(1L, copied.getMeasurementRevision());
    }
    @Test void formattingDoesNotChangeVersionAndUnauditedLegacyHasNoWeight() {
        var same = exact().setBaseQtyPerSalesUnit(new BigDecimal("1000.000"));
        GoodsMeasurementSupport.prepare(same, exact()); assertEquals(7L, same.getMeasurementRevision());
        var legacy = new GoodsSku(); GoodsMeasurementSupport.prepare(legacy, null);
        assertEquals("件", legacy.getSalesUnit()); assertEquals("UNKNOWN", legacy.getQuantityPrecision());
        assertNull(legacy.getBaseQtyPerSalesUnit()); assertEquals(1, legacy.getMinOrderQty());
    }
}
