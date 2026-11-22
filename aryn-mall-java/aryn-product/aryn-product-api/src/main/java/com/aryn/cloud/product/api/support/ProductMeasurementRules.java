package com.aryn.cloud.product.api.support;

import com.aryn.cloud.product.api.entity.GoodsSku;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** 纯度量规则。质量g、体积ml、长度mm、计数pcs；销售数量始终为整数。 */
public final class ProductMeasurementRules {
    private ProductMeasurementRules() { }

    public record Measurement(String salesUnit, String baseDimension, String baseUnit,
            BigDecimal baseQtyPerSalesUnit, String quantityPrecision, Integer minOrderQty,
            Integer stepQty, Long measurementRevision) implements Serializable { }

    public record Decision(BigDecimal rawQuantity, String rawUnit, BigDecimal normalizedBaseQuantity,
            Integer suggestedSalesQuantity, BigDecimal excessBaseQuantity, String unitStatus,
            String quantityStatus, boolean requiresReview, String reason, Long measurementRevision) implements Serializable { }

    private record Unit(String dimension, String base, BigDecimal factor) { }
    private static final Map<String, Unit> UNITS = Map.ofEntries(
        Map.entry("g", unit("MASS", "g", "1")), Map.entry("克", unit("MASS", "g", "1")),
        Map.entry("mg", unit("MASS", "g", "0.001")), Map.entry("毫克", unit("MASS", "g", "0.001")),
        Map.entry("kg", unit("MASS", "g", "1000")), Map.entry("千克", unit("MASS", "g", "1000")),
        Map.entry("公斤", unit("MASS", "g", "1000")), Map.entry("斤", unit("MASS", "g", "500")),
        Map.entry("两", unit("MASS", "g", "50")),
        Map.entry("ml", unit("VOLUME", "ml", "1")), Map.entry("毫升", unit("VOLUME", "ml", "1")),
        Map.entry("l", unit("VOLUME", "ml", "1000")), Map.entry("升", unit("VOLUME", "ml", "1000")),
        Map.entry("mm", unit("LENGTH", "mm", "1")), Map.entry("毫米", unit("LENGTH", "mm", "1")),
        Map.entry("cm", unit("LENGTH", "mm", "10")), Map.entry("厘米", unit("LENGTH", "mm", "10")),
        Map.entry("m", unit("LENGTH", "mm", "1000")), Map.entry("米", unit("LENGTH", "mm", "1000")),
        Map.entry("pcs", unit("COUNT", "pcs", "1")), Map.entry("件", unit("COUNT", "pcs", "1")),
        Map.entry("个", unit("COUNT", "pcs", "1")));

    private static Unit unit(String dimension, String base, String factor) {
        return new Unit(dimension, base, new BigDecimal(factor));
    }
    private static String normalized(String text) {
        return text == null ? "" : text.strip().toLowerCase(Locale.ROOT);
    }
    public static Measurement facts(GoodsSku sku) {
        return new Measurement(sku.getSalesUnit() == null ? "件" : sku.getSalesUnit(), sku.getBaseDimension(),
            sku.getBaseUnit(), sku.getBaseQtyPerSalesUnit(), sku.getQuantityPrecision() == null ? "UNKNOWN" : sku.getQuantityPrecision(),
            sku.getMinOrderQty() == null ? 1 : sku.getMinOrderQty(), sku.getStepQty() == null ? 1 : sku.getStepQty(),
            sku.getMeasurementRevision() == null ? 0L : sku.getMeasurementRevision());
    }
    public static void validate(Measurement facts) {
        if (facts == null || facts.salesUnit() == null || facts.salesUnit().isBlank() || facts.salesUnit().length() > 24
                || facts.salesUnit().chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("销售单位不能为空且最多24字");
        if (!java.util.Set.of("EXACT", "APPROX", "UNKNOWN").contains(Objects.toString(facts.quantityPrecision(), "")))
            throw new IllegalArgumentException("度量精度必须为EXACT/APPROX/UNKNOWN");
        if (facts.minOrderQty() == null || facts.minOrderQty() < 1 || facts.stepQty() == null || facts.stepQty() < 1)
            throw new IllegalArgumentException("起订量和步长必须为正整数");
        boolean anyBase = facts.baseDimension() != null || facts.baseUnit() != null || facts.baseQtyPerSalesUnit() != null;
        if (anyBase || !"UNKNOWN".equals(facts.quantityPrecision())) {
            Unit base = UNITS.get(normalized(facts.baseUnit()));
            BigDecimal coefficient = facts.baseQtyPerSalesUnit();
            if (base == null || !base.dimension().equals(facts.baseDimension()) || !base.base().equals(facts.baseUnit())
                    || coefficient == null || coefficient.signum() <= 0 || coefficient.precision() > 24
                    || coefficient.scale() > 9 || coefficient.precision() - coefficient.scale() > 15)
                throw new IllegalArgumentException("基准量纲、单位和每销售单位基准量不一致或超出范围");
            Unit sales = UNITS.get(normalized(facts.salesUnit()));
            if (sales != null && !"COUNT".equals(sales.dimension()) && (!sales.dimension().equals(base.dimension()) || sales.factor().compareTo(coefficient) != 0))
                throw new IllegalArgumentException("标准销售单位必须与基准量纲和换算系数一致");
        }
    }
    public static void requireSalesQuantity(GoodsSku sku, Integer quantity) {
        if (!ReplenishMatchRules.quantityAcceptable(quantity, sku.getMinOrderQty(), sku.getStepQty()))
            throw new IllegalArgumentException("数量须至少为" + (sku.getMinOrderQty() == null ? 1 : sku.getMinOrderQty())
                + "，并按步长" + (sku.getStepQty() == null ? 1 : sku.getStepQty()) + "递增");
    }

    public static Decision convert(BigDecimal raw, String rawUnit, boolean approximate, Measurement facts) {
        validate(facts);
        if (raw == null) return missing(raw, rawUnit, facts, "MISSING_QUANTITY", "MISSING", "UNKNOWN");
        if (raw.signum() <= 0 || raw.precision() > 38 || raw.scale() > 18 || raw.precision() - raw.scale() > 20)
            return missing(raw, rawUnit, facts, "INVALID_QUANTITY", "INVALID", "UNKNOWN");
        if (normalized(rawUnit).isEmpty()) return missing(raw, rawUnit, facts, "MISSING_UNIT", "VALID", "UNKNOWN");
        BigDecimal base = null;
        BigDecimal needed;
        boolean sameSalesUnit = normalized(rawUnit).equals(normalized(facts.salesUnit()));
        if (sameSalesUnit) {
            needed = raw;
            if (facts.baseQtyPerSalesUnit() != null && !"UNKNOWN".equals(facts.quantityPrecision())) base = raw.multiply(facts.baseQtyPerSalesUnit());
        } else {
            Unit input = UNITS.get(normalized(rawUnit));
            if (input == null) return missing(raw, rawUnit, facts, "UNKNOWN_UNIT", "VALID", "UNKNOWN");
            if (facts.baseDimension() == null || "UNKNOWN".equals(facts.quantityPrecision()))
                return missing(raw, rawUnit, facts, "UNREVIEWED_MEASUREMENT", "VALID", "UNKNOWN");
            if (!input.dimension().equals(facts.baseDimension())) return missing(raw, rawUnit, facts, "DIMENSION_MISMATCH", "VALID", "MISMATCH");
            base = raw.multiply(input.factor());
            needed = base.divide(facts.baseQtyPerSalesUnit(), 0, RoundingMode.CEILING);
        }
        try {
            BigDecimal minimum = BigDecimal.valueOf(facts.minOrderQty());
            BigDecimal step = BigDecimal.valueOf(facts.stepQty());
            BigDecimal extraSteps = needed.subtract(minimum).max(BigDecimal.ZERO).divide(step, 0, RoundingMode.CEILING);
            int salesQuantity = minimum.add(extraSteps.multiply(step)).intValueExact();
            BigDecimal excess = base == null ? null : facts.baseQtyPerSalesUnit().multiply(BigDecimal.valueOf(salesQuantity)).subtract(base);
            boolean rounded = sameSalesUnit ? raw.compareTo(BigDecimal.valueOf(salesQuantity)) != 0 : excess.signum() != 0;
            boolean approximateFacts = approximate || (!sameSalesUnit && "APPROX".equals(facts.quantityPrecision()));
            return new Decision(raw, rawUnit, base, salesQuantity, excess, approximateFacts ? "APPROX" : "EXACT", "VALID",
                approximateFacts || rounded, approximateFacts ? "APPROXIMATE_REQUIRES_CONFIRMATION" : rounded ? "PACKAGING_OR_THRESHOLD_ROUNDING" : "EXACT",
                facts.measurementRevision());
        } catch (ArithmeticException overflow) {
            return missing(raw, rawUnit, facts, "QUANTITY_EXCEEDS_LIMIT", "EXCEEDS_LIMIT", "UNKNOWN");
        }
    }
    private static Decision missing(BigDecimal raw, String unit, Measurement facts, String reason, String quantity, String status) {
        return new Decision(raw, unit, null, null, null, status, quantity, true, reason, facts.measurementRevision());
    }
}
