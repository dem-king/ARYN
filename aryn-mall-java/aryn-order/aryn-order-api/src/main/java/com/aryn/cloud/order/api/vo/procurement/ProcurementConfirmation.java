package com.aryn.cloud.order.api.vo.procurement;
import java.io.Serializable;
import java.util.List;
public record ProcurementConfirmation(String batchId, String targetType, String targetId, String status,
        long batchRevision, List<Item> items) implements Serializable {
    public record Item(String skuId, String spuId, int quantity, List<String> sourceRowIds) implements Serializable { }
}
