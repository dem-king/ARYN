package com.aryn.cloud.product.support;

import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.support.ProductMeasurementRules;
import java.util.Objects;

/** 全量度量提交带版本；旧调用未提交该组字段时保留存量事实。 */
public final class GoodsMeasurementSupport {
    private GoodsMeasurementSupport() { }
    public static void prepare(GoodsSku requested, GoodsSku stored) {
        boolean submitted = requested.getSalesUnit() != null || requested.getBaseDimension() != null
            || requested.getBaseUnit() != null || requested.getBaseQtyPerSalesUnit() != null
            || requested.getQuantityPrecision() != null || requested.getMinOrderQty() != null
            || requested.getStepQty() != null || requested.getUnitSpecText() != null;
        if (stored != null && !submitted) {
            copy(stored, requested);
            return;
        }
        if (stored != null && requested.getMeasurementRevision() != null
                && !requested.getMeasurementRevision().equals(stored.getMeasurementRevision() == null ? 0L : stored.getMeasurementRevision()))
            throw new IllegalArgumentException("商品度量已变化，请刷新当前SKU");
        if (stored != null && submitted && stored.getMeasurementRevision() != null
                && stored.getMeasurementRevision() > 0 && requested.getMeasurementRevision() == null)
            throw new IllegalArgumentException("修改商品度量必须提交当前版本");
        var facts = ProductMeasurementRules.facts(requested);
        ProductMeasurementRules.validate(facts);
        if (requested.getUnitSpecText() != null && requested.getUnitSpecText().length() > 500)
            throw new IllegalArgumentException("度量展示规格最多500字");
        requested.setSalesUnit(facts.salesUnit());
        requested.setQuantityPrecision(facts.quantityPrecision());
        requested.setMinOrderQty(facts.minOrderQty()); requested.setStepQty(facts.stepQty());
        if (requested.getBaseQtyPerSalesUnit() != null)
            requested.setBaseQtyPerSalesUnit(requested.getBaseQtyPerSalesUnit().stripTrailingZeros());
        long previous = stored == null || stored.getMeasurementRevision() == null ? 0 : stored.getMeasurementRevision();
        boolean same = stored != null && sameFacts(stored, requested);
        requested.setMeasurementRevision(same ? previous : Math.addExact(previous, 1));
    }
    private static boolean sameFacts(GoodsSku a, GoodsSku b) {
        var fa = ProductMeasurementRules.facts(a); var fb = ProductMeasurementRules.facts(b);
        return Objects.equals(fa.salesUnit(), fb.salesUnit()) && Objects.equals(fa.baseDimension(), fb.baseDimension())
            && Objects.equals(fa.baseUnit(), fb.baseUnit()) && decimalEqual(fa.baseQtyPerSalesUnit(), fb.baseQtyPerSalesUnit())
            && Objects.equals(fa.quantityPrecision(), fb.quantityPrecision()) && Objects.equals(fa.minOrderQty(), fb.minOrderQty())
            && Objects.equals(fa.stepQty(), fb.stepQty()) && Objects.equals(a.getUnitSpecText(), b.getUnitSpecText());
    }
    private static boolean decimalEqual(java.math.BigDecimal a, java.math.BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }
    private static void copy(GoodsSku from, GoodsSku to) {
        var facts = ProductMeasurementRules.facts(from);
        to.setSalesUnit(facts.salesUnit()); to.setBaseDimension(facts.baseDimension()); to.setBaseUnit(facts.baseUnit());
        to.setBaseQtyPerSalesUnit(facts.baseQtyPerSalesUnit()); to.setQuantityPrecision(facts.quantityPrecision());
        to.setMinOrderQty(facts.minOrderQty()); to.setStepQty(facts.stepQty());
        to.setUnitSpecText(from.getUnitSpecText()); to.setMeasurementRevision(facts.measurementRevision());
    }
}
