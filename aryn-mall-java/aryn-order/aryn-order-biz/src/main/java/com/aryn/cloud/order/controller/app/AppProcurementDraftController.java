package com.aryn.cloud.order.controller.app;

import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.order.api.dto.ProcurementCommands.*;
import com.aryn.cloud.order.service.impl.ProcurementDraftService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/app/procurement-drafts")
public class AppProcurementDraftController {
    private final ProcurementDraftService service;
    private final com.aryn.cloud.order.service.impl.ProcurementHistoryService history;
    private final com.aryn.cloud.order.service.impl.ProcurementCheckoutService checkout;
    private final com.aryn.cloud.order.service.impl.ProcurementReviewService reviews;
    private final com.aryn.cloud.order.service.impl.ProcurementConfirmationService confirmations;
    private String tenant() { return ArynTenantContextHolder.getTenantId(); }
    private String actor() { return SecurityUtils.requireUser(DeviceTypeEnum.TOC).getUserId(); }
    @PostMapping public Result<?> create(@RequestBody CreateDraft input) {
        return Result.success(service.create(tenant(), actor(), input));
    }
    @GetMapping public Result<?> list() { return Result.success(service.list(tenant(), actor())); }
    @GetMapping("/{id}") public Result<?> get(@PathVariable String id) {
        return Result.success(service.get(tenant(), actor(), id));
    }
    @GetMapping("/{id}/items") public Result<?> currentItems(@PathVariable String id) {
        return Result.success(checkout.current(tenant(), actor(), id));
    }
    @GetMapping("/{id}/batches") public Result<?> batches(@PathVariable String id) {
        return Result.success(service.batches(tenant(), actor(), id));
    }
    @GetMapping("/{id}/batches/{batchId}") public Result<?> batch(@PathVariable String id, @PathVariable String batchId) {
        return Result.success(service.batch(tenant(), actor(), id, batchId));
    }
    @GetMapping("/{id}/batches/{batchId}/rows") public Result<?> rows(@PathVariable String id, @PathVariable String batchId,
            @RequestParam(required = false) String after) {
        return Result.success(service.rows(tenant(), actor(), id, batchId, after));
    }
    @PutMapping("/{id}/batches/{batchId}/rows/{rowId}") public Result<?> review(@PathVariable String id,
            @PathVariable String batchId, @PathVariable String rowId,
            @RequestBody com.aryn.cloud.order.api.dto.ProcurementReviewCommand input) {
        return Result.success(reviews.review(tenant(), actor(), id, batchId, rowId, input));
    }
    @PostMapping("/{id}/batches/{batchId}/confirm") public Result<?> confirm(@PathVariable String id,
            @PathVariable String batchId, @RequestBody com.aryn.cloud.order.api.dto.ProcurementConfirmCommand input) {
        return Result.success(confirmations.confirm(tenant(), actor(), id, batchId, input));
    }
    @PostMapping("/{id}/batches/{batchId}/review-ready") public Result<?> reviewReady(@PathVariable String id,
            @PathVariable String batchId, @RequestBody com.aryn.cloud.order.api.dto.ProcurementBulkReviewCommand input) {
        return Result.success(reviews.reviewReady(tenant(), actor(), id, batchId, input));
    }
    public record BatchRevision(long expectedRevision) { }
    @PostMapping("/{id}/batches/{batchId}/cancel") public Result<?> cancel(@PathVariable String id,
            @PathVariable String batchId, @RequestBody BatchRevision input) {
        return Result.success(service.cancel(tenant(), actor(), id, batchId, input.expectedRevision()));
    }
    @PostMapping("/{id}/batches/{batchId}/retry") public Result<?> retry(@PathVariable String id,
            @PathVariable String batchId, @RequestBody BatchRevision input) {
        return Result.success(service.retry(tenant(), actor(), id, batchId, input.expectedRevision()));
    }
    @PostMapping("/{id}/history") public Result<?> history(@PathVariable String id,
            @RequestBody com.aryn.cloud.order.service.impl.ProcurementHistoryService.HistoryCommand command) {
        return Result.success(history.submit(tenant(), actor(), id, command));
    }
    @PostMapping("/{id}/text") public Result<?> text(@PathVariable String id, @RequestBody SubmitText input) {
        return Result.success(service.submitText(tenant(), actor(), id, input));
    }
    @PostMapping("/{id}/excel") public Result<?> excel(@PathVariable String id, @RequestParam String clientRequestId,
            @RequestParam long expectedRevision, @RequestParam(defaultValue = "false") boolean explicitNew,
            @RequestParam MultipartFile file) throws IOException {
        String actor = actor();
        if (file.getSize() > 8 * 1024 * 1024) throw new IllegalArgumentException("文件超过8MB");
        return Result.success(service.submitExcel(tenant(), actor, id, clientRequestId, expectedRevision,
            explicitNew, file.getOriginalFilename(), file.getBytes()));
    }
}
