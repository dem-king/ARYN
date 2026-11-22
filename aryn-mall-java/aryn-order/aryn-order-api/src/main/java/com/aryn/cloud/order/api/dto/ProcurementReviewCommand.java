package com.aryn.cloud.order.api.dto;
import java.io.Serializable;
/** CONFIRM_SALES明确销售件数；SET_OWNER只修订来源归属标签，不创建成员账号。 */
public record ProcurementReviewCommand(long expectedRevision, String action, String selectedSkuId,
        Integer salesQuantity, boolean acceptRounding, String reason, String ownerName) implements Serializable {
    public ProcurementReviewCommand(long revision, String action, String sku, Integer quantity, boolean rounding, String reason) {
        this(revision, action, sku, quantity, rounding, reason, null);
    }
}
