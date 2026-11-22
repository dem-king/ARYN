package com.aryn.cloud.order.service.impl;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.dto.DeliveryGroupCommands.*;
import com.aryn.cloud.order.api.entity.*;
import com.aryn.cloud.order.mapper.*;
import com.aryn.cloud.order.support.DeliveryGroupRules;
import com.aryn.cloud.vessel.api.dto.VesselContextDTO;
import com.aryn.cloud.vessel.api.remote.RemoteVesselService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;

/** 外域事实先读取；订单锁→业务组锁→关系锁，状态与outbox在同一本地事务提交。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryGroupService {
    private final DeliveryGroupMapper groups;
    private final DeliveryGroupOrderLinkMapper links;
    private final DeliveryGroupOperationMapper operations;
    private final DeliveryWarehouseConfigMapper warehouses;
    private final OrderInfoMapper orders;
    private final DeliveryTaskMapper tasks;
    private final FulfillmentWaveMapper waves;
    private final FulfillmentPickItemMapper picks;
    private final ObjectMapper json;
    private final TransactionTemplate transactions;
    @DubboReference(check = false) private RemoteVesselService vessels;
    // 仅作公平扫描游标；归组资格和幂等回执始终来自数据库。
    private final Map<String, String> assignmentCursors = new java.util.concurrent.ConcurrentHashMap<>();

    public record Detail(DeliveryGroup group, List<DeliveryGroupOrderLink> orders, List<DeliveryGroupOperation> operations) { }
    private record Facts(VesselContextDTO call, Instant at) { }
    public record Notification(String eventId, String recipientId, String orderId, String summary) { }

    public Receipt create(String tenant, String actor, Create input) {
        context(tenant, actor);
        if (input == null) throw new ArynBusinessException("缺少合送组命令");
        validate(input.requestId(), input.reason());
        if (input.warehouseId() == null || input.warehouseId().isBlank() || input.windowSequence() == null || input.windowSequence() < 1)
            throw new ArynBusinessException("请选择仓库并填写正整数窗口序号");
        capacity(input.maxOrders()); DeliveryGroupRules.schedule(input.windowStart(), input.windowEnd(), input.cutoff());
        String fingerprint = fingerprint(actor, "CREATE", "", input);
        var replay = replay(input.requestId(), fingerprint); if (replay != null) return replay;
        Facts facts = facts(tenant, input.vesselCallId());
        return atomic(input.requestId(), fingerprint, () -> {
            requireFresh(facts);
            var warehouse = warehouses.selectOne(Wrappers.<DeliveryWarehouseConfig>lambdaQuery()
                .eq(DeliveryWarehouseConfig::getTenantId, tenant).eq(DeliveryWarehouseConfig::getId, input.warehouseId()));
            if (warehouse == null) throw new ArynBusinessException(403, "仓库不属于当前租户或已停用");
            requireWindow(facts.call(), input.windowStart(), input.windowEnd());
            var group = new DeliveryGroup(); group.setId(UUID.randomUUID().toString()); group.setTenantId(tenant);
            group.setWarehouseId(input.warehouseId()); group.setVesselId(facts.call().getVesselId());
            group.setVesselCallId(facts.call().getVesselCallId()); group.setPortCode(facts.call().getPortCode());
            group.setWindowSequence(input.windowSequence()); group.setWindowStart(input.windowStart()); group.setWindowEnd(input.windowEnd());
            group.setCutoff(input.cutoff()); group.setMaxOrders(input.maxOrders()); group.setRevision(0L);
            group.setStatus(input.cutoff() != null && !LocalDateTime.now().isBefore(input.cutoff()) ? "CLOSED" : "OPEN");
            group.setCreateBy(actor); group.setUpdateBy(actor); group.setCreateTime(LocalDateTime.now()); group.setUpdateTime(group.getCreateTime()); group.setDelFlag("0");
            groups.insert(group);
            return record(group, actor, input.requestId(), fingerprint, "CREATE", input.reason(), input, List.of(), List.of());
        });
    }

    public Receipt change(String tenant, String actor, String groupId, Change input) {
        context(tenant, actor);
        if (input == null || !Set.of("JOIN", "REMOVE", "SCHEDULE", "CLOSE", "CANCEL", "COMPLETE").contains(Objects.toString(input.action(), "")))
            throw new ArynBusinessException("不支持的合送组动作");
        validate(input.requestId(), input.reason());
        List<String> ids = input.orderIds() == null ? List.of() : input.orderIds();
        if (ids.size() > 100 || ids.stream().anyMatch(id -> id == null || id.isBlank()) || new HashSet<>(ids).size() != ids.size())
            throw new ArynBusinessException("每次最多100个不重复订单");
        if (Set.of("JOIN", "REMOVE").contains(input.action()) && ids.isEmpty()) throw new ArynBusinessException("请选择订单");
        if (!Set.of("JOIN", "REMOVE").contains(input.action()) && !ids.isEmpty()) throw new ArynBusinessException("该动作不接受订单列表");
        String fingerprint = fingerprint(actor, input.action(), groupId, input);
        var replay = replay(input.requestId(), fingerprint); if (replay != null) return replay;
        var before = group(groupId, false);
        Facts facts = Set.of("JOIN", "SCHEDULE").contains(input.action()) ? facts(tenant, before.getVesselCallId()) : null;
        return atomic(input.requestId(), fingerprint, () -> {
            // 所有订单写入路径使用同一顺序；先锁订单，避免跨业务组并发归入同一单。
            Map<String, OrderInfo> selected = new LinkedHashMap<>();
            for (String id : ids.stream().sorted().toList()) selected.put(id, lockOrder(tenant, id));
            var group = group(groupId, true);
            var repeated = replay(input.requestId(), fingerprint); if (repeated != null) return repeated;
            if (!Objects.equals(group.getRevision(), input.expectedRevision())) throw new ArynBusinessException(409, "合送组已更新，请重载");
            if (facts != null) { requireFresh(facts); requireCall(group, facts.call()); }
            var active = activeLinks(groupId, true);
            List<OrderInfo> notify = new ArrayList<>();
            switch (input.action()) {
                case "JOIN" -> {
                    if (input.allowLate()) {
                        // 特批只放开截单，不能越过已执行的波次/任务；管理员明确给出本轮订单容量。
                        capacity(input.maxOrders()); group.setMaxOrders(input.maxOrders()); requireNotExecuting(active);
                    }
                    for (OrderInfo order : selected.values()) {
                        DeliveryGroupRules.order(group, order, LocalDateTime.now(), input.allowLate());
                        var existing = links.selectOne(Wrappers.<DeliveryGroupOrderLink>lambdaQuery()
                            .eq(DeliveryGroupOrderLink::getTenantId, tenant).eq(DeliveryGroupOrderLink::getOrderId, order.getId()).last("FOR UPDATE"));
                        if (existing != null && "ACTIVE".equals(existing.getStatus())) {
                            if (!groupId.equals(existing.getGroupId())) throw new ArynBusinessException(409, "订单已在另一合送组，请先移出");
                            continue;
                        }
                        if (active.size() >= group.getMaxOrders()) throw new ArynBusinessException(409, "已达到本窗口明确的订单容量，请重新排程");
                        String waveId = pendingWave(tenant, order.getId(), group);
                        var task = tasks.selectOne(Wrappers.<DeliveryTask>lambdaQuery().eq(DeliveryTask::getTenantId, tenant)
                            .eq(DeliveryTask::getOrderId, order.getId()).last("FOR UPDATE"));
                        if (task != null && !"1".equals(task.getStatus())) throw new ArynBusinessException(409, "订单配送任务已派出，不能自动归组");
                        if (order.getFulfillmentWarehouseId() != null && !group.getWarehouseId().equals(order.getFulfillmentWarehouseId())
                                || task != null && task.getWarehouseId() != null && !group.getWarehouseId().equals(task.getWarehouseId()))
                            throw new ArynBusinessException(409, "订单或任务已核定其它仓库，不可跨仓归组");
                        orders.update(null, Wrappers.<OrderInfo>lambdaUpdate().eq(OrderInfo::getTenantId, tenant).eq(OrderInfo::getId, order.getId())
                            .set(OrderInfo::getFulfillmentWarehouseId, group.getWarehouseId()));
                        if (task != null) tasks.update(null, Wrappers.<DeliveryTask>lambdaUpdate().eq(DeliveryTask::getTenantId, tenant)
                            .eq(DeliveryTask::getId, task.getId()).set(DeliveryTask::getWarehouseId, group.getWarehouseId()));
                        var link = existing == null ? new DeliveryGroupOrderLink() : existing;
                        if (existing == null) { link.setId(UUID.randomUUID().toString()); link.setTenantId(tenant); link.setRevision(0L); link.setCreateBy(actor); link.setCreateTime(LocalDateTime.now()); link.setDelFlag("0"); }
                        else link.setRevision(link.getRevision() + 1);
                        link.setGroupId(groupId); link.setOrderId(order.getId()); link.setWarehouseId(group.getWarehouseId()); link.setWaveId(waveId);
                        link.setTaskId(task == null ? null : task.getId()); link.setStatus("ACTIVE"); link.setUpdateBy(actor); link.setUpdateTime(LocalDateTime.now());
                        if (existing == null) links.insert(link);
                        else updateLink(link);
                        active.add(link); notify.add(order);
                    }
                }
                case "REMOVE" -> {
                    if (!Set.of("OPEN", "CLOSED").contains(group.getStatus())) throw new ArynBusinessException(409, "执行中的合送组需通过履约异常处理");
                    for (OrderInfo order : selected.values()) {
                        var link = active.stream().filter(item -> item.getOrderId().equals(order.getId())).findFirst()
                            .orElseThrow(() -> new ArynBusinessException(409, "订单不在该组"));
                        requireNotExecuting(List.of(link));
                        if (link.getWaveId() != null) throw new ArynBusinessException(409, "订单已入拣货波次，请先处理波次关系");
                        link.setStatus("REMOVED"); link.setRevision(link.getRevision() + 1); link.setUpdateBy(actor); link.setUpdateTime(LocalDateTime.now()); updateLink(link); notify.add(order);
                    }
                }
                case "SCHEDULE" -> {
                    if (!Set.of("OPEN", "CLOSED").contains(group.getStatus())) throw new ArynBusinessException(409, "执行中的配送需由履约工作台处理延误");
                    if (input.cutoff() == null) throw new ArynBusinessException("请明确截单时间");
                    DeliveryGroupRules.schedule(input.windowStart(), input.windowEnd(), input.cutoff());
                    requireWindow(facts.call(), input.windowStart(), input.windowEnd()); capacity(input.maxOrders());
                    if (input.maxOrders() < active.size()) throw new ArynBusinessException("容量不能小于已归组订单数");
                    group.setWindowStart(input.windowStart()); group.setWindowEnd(input.windowEnd()); group.setCutoff(input.cutoff()); group.setMaxOrders(input.maxOrders());
                    // 延误不自动重开已截单组；新订单应进入下一窗口。
                    if (!LocalDateTime.now().isBefore(input.cutoff())) group.setStatus("CLOSED");
                    notify.addAll(orderSnapshots(tenant, active));
                }
                case "CLOSE" -> {
                    if (!"OPEN".equals(group.getStatus())) throw new ArynBusinessException(409, "仅开放中的合送组可截单");
                    group.setStatus("CLOSED"); notify.addAll(orderSnapshots(tenant, active));
                }
                case "CANCEL" -> {
                    if (!active.isEmpty() || !Set.of("OPEN", "CLOSED").contains(group.getStatus())) throw new ArynBusinessException(409, "请先处理组内订单和履约任务再取消空组");
                    group.setStatus("CANCELLED");
                }
                case "COMPLETE" -> {
                    if (!"FULFILLING".equals(group.getStatus())) throw new ArynBusinessException(409, "仅执行中的合送组可完成");
                    var completed = orderSnapshots(tenant, active);
                    if (completed.stream().anyMatch(order -> !Set.of("4", "11").contains(order.getStatus())))
                        throw new ArynBusinessException(409, "仍有独立订单未完成签收或取消");
                    group.setStatus("COMPLETED");
                }
                default -> throw new IllegalStateException();
            }
            group.setRevision(group.getRevision() + 1); group.setUpdateBy(actor); group.setUpdateTime(LocalDateTime.now()); groups.updateById(group);
            return record(group, actor, input.requestId(), fingerprint, input.action(), input.reason(), input, ids, notify);
        });
    }

    public List<DeliveryGroup> list(String tenant, String actor, String call, String after) {
        context(tenant, actor);
        var page = groups.selectList(Wrappers.<DeliveryGroup>lambdaQuery().eq(DeliveryGroup::getTenantId, tenant)
            .eq(call != null && !call.isBlank(), DeliveryGroup::getVesselCallId, call)
            .gt(after != null && !after.isBlank(), DeliveryGroup::getId, after).orderByAsc(DeliveryGroup::getId).last("LIMIT 100"));
        for (var group : page) {
            group.setOrderCount(links.selectCount(Wrappers.<DeliveryGroupOrderLink>lambdaQuery().eq(DeliveryGroupOrderLink::getTenantId, tenant)
                .eq(DeliveryGroupOrderLink::getGroupId, group.getId()).eq(DeliveryGroupOrderLink::getStatus, "ACTIVE")));
            group.setUnassignedCount(links.selectCount(Wrappers.<DeliveryGroupOrderLink>lambdaQuery().eq(DeliveryGroupOrderLink::getTenantId, tenant)
                .eq(DeliveryGroupOrderLink::getGroupId, group.getId()).eq(DeliveryGroupOrderLink::getStatus, "ACTIVE").isNull(DeliveryGroupOrderLink::getWaveId)));
        }
        return page;
    }
    public record Candidate(String id, String orderNo, String purchaseScene, String assignedGroupId, String warehouseId,
            LocalDateTime windowStart, LocalDateTime windowEnd) { }
    public List<Candidate> candidates(String tenant, String actor, String groupId) {
        context(tenant, actor); var group = group(groupId, false);
        var candidates = orders.selectList(Wrappers.<OrderInfo>lambdaQuery().eq(OrderInfo::getTenantId, tenant)
            .eq(OrderInfo::getStatus, "2").eq(OrderInfo::getDeliveryWay, "4").eq(OrderInfo::getVesselId, group.getVesselId())
            .eq(OrderInfo::getVesselCallId, group.getVesselCallId()).eq(OrderInfo::getPortCode, group.getPortCode())
            .orderByAsc(OrderInfo::getId).last("LIMIT 100"));
        if (candidates.isEmpty()) return List.of();
        var existing = links.selectList(Wrappers.<DeliveryGroupOrderLink>lambdaQuery().eq(DeliveryGroupOrderLink::getTenantId, tenant)
            .in(DeliveryGroupOrderLink::getOrderId, candidates.stream().map(OrderInfo::getId).toList()).eq(DeliveryGroupOrderLink::getStatus, "ACTIVE"));
        Map<String, String> occupied = new HashMap<>(); existing.forEach(link -> occupied.put(link.getOrderId(), link.getGroupId()));
        return candidates.stream().map(order -> new Candidate(order.getId(), order.getOrderNo(), order.getPurchaseScene(), occupied.get(order.getId()),
            order.getFulfillmentWarehouseId(), order.getDeliveryWindowStart(), order.getDeliveryWindowEnd())).toList();
    }
    public Detail detail(String tenant, String actor, String id) {
        context(tenant, actor); var group = group(id, false);
        return new Detail(group, activeLinks(id, false), operations.selectList(Wrappers.<DeliveryGroupOperation>lambdaQuery()
            .eq(DeliveryGroupOperation::getTenantId, tenant).eq(DeliveryGroupOperation::getGroupId, id)
            .orderByDesc(DeliveryGroupOperation::getCreateTime).last("LIMIT 100")));
    }
    public BuyerSchedule buyerSchedule(String tenant, String actor, String orderId) {
        context(tenant, actor);
        var order = orders.selectOne(Wrappers.<OrderInfo>lambdaQuery().eq(OrderInfo::getTenantId, tenant)
            .eq(OrderInfo::getUserId, actor).eq(OrderInfo::getId, orderId));
        if (order == null) throw new ArynBusinessException(403, "订单不存在");
        var link = links.selectOne(Wrappers.<DeliveryGroupOrderLink>lambdaQuery().eq(DeliveryGroupOrderLink::getTenantId, tenant)
            .eq(DeliveryGroupOrderLink::getOrderId, orderId).eq(DeliveryGroupOrderLink::getStatus, "ACTIVE"));
        if (link == null) return new BuyerSchedule(orderId, null, null, null, null, null, true);
        var group = group(link.getGroupId(), false);
        boolean changed = !Objects.equals(order.getVesselCallId(), group.getVesselCallId())
            || !Objects.equals(order.getVesselId(), group.getVesselId()) || !Objects.equals(order.getPortCode(), group.getPortCode());
        return new BuyerSchedule(orderId, group.getId(), group.getStatus(), changed ? null : group.getWindowStart(),
            changed ? null : group.getWindowEnd(), changed ? null : group.getCutoff(), changed || group.getCutoff() == null);
    }
    public CheckoutSchedule checkoutSchedule(String tenant, String actor, String vesselId, String callId) {
        context(tenant, actor);
        if (vesselId == null || vesselId.isBlank() || !vessels.isVesselMember(tenant, vesselId, actor))
            throw new ArynBusinessException(403, "船舶成员关系已失效，请重新选择船舶");
        var call = facts(tenant, callId).call();
        if (!vesselId.equals(call.getVesselId())) throw new ArynBusinessException(409, "靠港与船舶不一致，请重新选择");
        var available = groups.selectList(Wrappers.<DeliveryGroup>lambdaQuery().eq(DeliveryGroup::getTenantId, tenant)
            .eq(DeliveryGroup::getVesselId, vesselId).eq(DeliveryGroup::getVesselCallId, callId).eq(DeliveryGroup::getPortCode, call.getPortCode())
            .eq(DeliveryGroup::getStatus, "OPEN").gt(DeliveryGroup::getCutoff, LocalDateTime.now())
            .gt(DeliveryGroup::getWindowEnd, LocalDateTime.now()).orderByAsc(DeliveryGroup::getWindowSequence).last("LIMIT 1"));
        if (available.isEmpty()) return new CheckoutSchedule(vesselId, callId, "PENDING", call.getDeliveryWindowStart(), call.getDeliveryWindowEnd(), null, true);
        var group = available.get(0);
        return new CheckoutSchedule(vesselId, callId, group.getStatus(), group.getWindowStart(), group.getWindowEnd(), group.getCutoff(), false);
    }
    /** 消费变更事件时读取最新靠港事实，旧消息晚到也不会把窗口退回旧ETA。 */
    public void refreshCall(String tenant, String noticeId, String callId) {
        context(tenant, "SYSTEM");
        if (noticeId == null || noticeId.isBlank() || noticeId.length() > 64) throw new ArynBusinessException("靠港变更缺少幂等事件号");
        var current = vessels.getVesselCallContext(tenant, callId);
        Instant fetchedAt = Instant.now();
        var impacted = groups.selectList(Wrappers.<DeliveryGroup>lambdaQuery().eq(DeliveryGroup::getTenantId, tenant)
            .eq(DeliveryGroup::getVesselCallId, callId).in(DeliveryGroup::getStatus, "OPEN", "CLOSED", "FULFILLING"));
        for (var candidate : impacted) {
            String request = "call:" + candidate.getId() + ":" + noticeId;
            String digest = fingerprint("SYSTEM", "CALL_CHANGED", candidate.getId(), noticeId);
            if (replay(request, digest) != null) continue;
            atomic(request, digest, () -> {
                var group = group(candidate.getId(), true);
                var repeated = replay(request, digest); if (repeated != null) return repeated;
                if (fetchedAt.isBefore(Instant.now().minusSeconds(30))) throw new ArynBusinessException(409, "靠港事实已过期，请重试消息");
                if (current != null && !Boolean.FALSE.equals(current.getCallOrderable())
                        && Objects.equals(current.getVesselId(), group.getVesselId()) && Objects.equals(current.getPortCode(), group.getPortCode())) {
                    group.setWindowStart(current.getDeliveryWindowStart()); group.setWindowEnd(current.getDeliveryWindowEnd());
                    if (group.getCutoff() != null && (group.getWindowStart() == null || group.getCutoff().isAfter(group.getWindowStart()))) group.setCutoff(null);
                } else { group.setWindowStart(null); group.setWindowEnd(null); group.setCutoff(null); }
                if ("OPEN".equals(group.getStatus()) && (group.getCutoff() == null || !LocalDateTime.now().isBefore(group.getCutoff()))) group.setStatus("CLOSED");
                group.setRevision(group.getRevision() + 1); group.setUpdateBy("SYSTEM"); group.setUpdateTime(LocalDateTime.now());
                // 显式set保留失效标记，避免MyBatis默认NOT_NULL忽略清空窗口。
                groups.update(null, Wrappers.<DeliveryGroup>lambdaUpdate().eq(DeliveryGroup::getTenantId, tenant).eq(DeliveryGroup::getId, group.getId())
                    .set(DeliveryGroup::getWindowStart, group.getWindowStart()).set(DeliveryGroup::getWindowEnd, group.getWindowEnd())
                    .set(DeliveryGroup::getCutoff, group.getCutoff()).set(DeliveryGroup::getStatus, group.getStatus())
                    .set(DeliveryGroup::getRevision, group.getRevision()).set(DeliveryGroup::getUpdateBy, "SYSTEM").set(DeliveryGroup::getUpdateTime, group.getUpdateTime()));
                return record(group, "SYSTEM", request, digest, "CALL_CHANGED", "靠港变更，原组ID保留；截单不自动重开", noticeId,
                    List.of(), orderSnapshots(tenant, activeLinks(group.getId(), true)));
            });
        }
    }

    /** 持久扫描补偿截单和订单取消；部分退款不会触发整组取消。 */
    public void reconcile(String tenant) {
        context(tenant, "SYSTEM");
        var due = groups.selectList(Wrappers.<DeliveryGroup>lambdaQuery().eq(DeliveryGroup::getTenantId, tenant)
            .eq(DeliveryGroup::getStatus, "OPEN").le(DeliveryGroup::getCutoff, LocalDateTime.now()).orderByAsc(DeliveryGroup::getCutoff).last("LIMIT 50"));
        for (var candidate : due) {
            String request = "cutoff:" + candidate.getId(); String digest = fingerprint("SYSTEM", "CUTOFF", candidate.getId(), request);
            atomic(request, digest, () -> {
                var group = group(candidate.getId(), true); var repeated = replay(request, digest); if (repeated != null) return repeated;
                if (!"OPEN".equals(group.getStatus()) || group.getCutoff() == null || LocalDateTime.now().isBefore(group.getCutoff()))
                    return new Receipt(group.getId(), group.getStatus(), group.getRevision(), List.of());
                group.setStatus("CLOSED"); group.setRevision(group.getRevision() + 1); group.setUpdateBy("SYSTEM"); group.setUpdateTime(LocalDateTime.now()); groups.updateById(group);
                return record(group, "SYSTEM", request, digest, "CUTOFF", "到达明确截单时间", request, List.of(), orderSnapshots(tenant, activeLinks(group.getId(), true)));
            });
        }
        // 按检查时间轮换一页，避免长期活动链接阻塞后续取消补偿。
        for (var candidate : links.selectList(Wrappers.<DeliveryGroupOrderLink>lambdaQuery().eq(DeliveryGroupOrderLink::getTenantId, tenant)
                .eq(DeliveryGroupOrderLink::getStatus, "ACTIVE").orderByAsc(DeliveryGroupOrderLink::getUpdateTime).last("LIMIT 100"))) {
            transactions.executeWithoutResult(status -> {
                var order = lockOrder(tenant, candidate.getOrderId()); var group = group(candidate.getGroupId(), true);
                var link = links.selectOne(Wrappers.<DeliveryGroupOrderLink>lambdaQuery().eq(DeliveryGroupOrderLink::getTenantId, tenant)
                    .eq(DeliveryGroupOrderLink::getId, candidate.getId()).last("FOR UPDATE"));
                if (link == null || !"ACTIVE".equals(link.getStatus()) || !group.getId().equals(link.getGroupId())) return;
                if ("11".equals(order.getStatus())) {
                    link.setStatus("CANCELLED"); link.setRevision(link.getRevision() + 1); link.setUpdateBy("SYSTEM"); link.setUpdateTime(LocalDateTime.now()); updateLink(link);
                    group.setRevision(group.getRevision() + 1); group.setUpdateBy("SYSTEM"); group.setUpdateTime(LocalDateTime.now()); groups.updateById(group);
                    String request = "cancel-order:" + order.getId();
                    record(group, "SYSTEM", request, fingerprint("SYSTEM", "ORDER_CANCELLED", group.getId(), request), "ORDER_CANCELLED", "本单已取消；其它订单继续独立履约", request, List.of(order.getId()), List.of(order));
                } else if (!Objects.equals(order.getVesselCallId(), group.getVesselCallId())
                        || !Objects.equals(order.getVesselId(), group.getVesselId()) || !Objects.equals(order.getPortCode(), group.getPortCode())) {
                    String request = "replan:" + link.getId() + ":" + fingerprint("SYSTEM", "CONTEXT", order.getId(),
                        Arrays.asList(order.getVesselId(), order.getVesselCallId(), order.getPortCode())).substring(0, 32);
                    String digest = fingerprint("SYSTEM", "ORDER_CONTEXT_CHANGED", group.getId(), request);
                    if (replay(request, digest) != null) {
                        links.update(null, Wrappers.<DeliveryGroupOrderLink>lambdaUpdate().eq(DeliveryGroupOrderLink::getTenantId, tenant)
                            .eq(DeliveryGroupOrderLink::getId, link.getId()).set(DeliveryGroupOrderLink::getUpdateTime, LocalDateTime.now()));
                        return;
                    }
                    // 已有波次/任务必须人工处理，保留关系供异常工作台追溯；启动拣货会重验上下文。
                    if (link.getWaveId() == null && link.getTaskId() == null) {
                        link.setStatus("REPLAN_REQUIRED"); link.setRevision(link.getRevision() + 1); link.setUpdateBy("SYSTEM");
                        link.setUpdateTime(LocalDateTime.now()); updateLink(link);
                    }
                    group.setRevision(group.getRevision() + 1); group.setUpdateBy("SYSTEM"); group.setUpdateTime(LocalDateTime.now()); groups.updateById(group);
                    record(group, "SYSTEM", request, digest, "ORDER_CONTEXT_CHANGED", "本单靠港已变化；未执行订单重新排程，既有波次或任务须人工核对", request, List.of(order.getId()), List.of(order));
                } else {
                    // 轮换扫描起点，不让前100个长期活动链接阻塞后续取消补偿。
                    links.update(null, Wrappers.<DeliveryGroupOrderLink>lambdaUpdate().eq(DeliveryGroupOrderLink::getTenantId, tenant)
                        .eq(DeliveryGroupOrderLink::getId, link.getId()).set(DeliveryGroupOrderLink::getUpdateTime, LocalDateTime.now()));
                }
            });
        }
    }
    /** 支付后补偿扫描：只用管理员已核定的开放窗口，截单后尝试下一窗口，否则保持单独待排程。 */
    public void assignEligible(String tenant) {
        context(tenant, "SYSTEM");
        String after = assignmentCursors.get(tenant);
        var pending = orders.selectList(Wrappers.<OrderInfo>lambdaQuery().eq(OrderInfo::getTenantId, tenant)
            .eq(OrderInfo::getStatus, "2").eq(OrderInfo::getDeliveryWay, "4")
            .gt(after != null, OrderInfo::getId, after)
            .orderByAsc(OrderInfo::getId).last("LIMIT 20"));
        if (pending.size() < 20) assignmentCursors.remove(tenant);
        else assignmentCursors.put(tenant, pending.get(pending.size() - 1).getId());
        if (pending.isEmpty()) return;
        // 分开查询兼容历史表不同字符排序规则，且不把人工移出重新自动加入。
        var occupied = links.selectList(Wrappers.<DeliveryGroupOrderLink>lambdaQuery().eq(DeliveryGroupOrderLink::getTenantId, tenant)
            .in(DeliveryGroupOrderLink::getOrderId, pending.stream().map(OrderInfo::getId).toList())
            .in(DeliveryGroupOrderLink::getStatus, "ACTIVE", "REMOVED", "CANCELLED"))
            .stream().map(DeliveryGroupOrderLink::getOrderId).collect(java.util.stream.Collectors.toSet());
        for (var order : pending) {
            if (occupied.contains(order.getId())) continue;
            var available = groups.selectList(Wrappers.<DeliveryGroup>lambdaQuery().eq(DeliveryGroup::getTenantId, tenant)
                .eq(DeliveryGroup::getVesselId, order.getVesselId()).eq(DeliveryGroup::getVesselCallId, order.getVesselCallId())
                .eq(DeliveryGroup::getPortCode, order.getPortCode()).eq(DeliveryGroup::getStatus, "OPEN")
                .eq(order.getFulfillmentWarehouseId() != null, DeliveryGroup::getWarehouseId, order.getFulfillmentWarehouseId())
                .gt(DeliveryGroup::getCutoff, LocalDateTime.now()).orderByAsc(DeliveryGroup::getWindowSequence).last("LIMIT 20"));
            for (var group : available) {
                try {
                    DeliveryGroupRules.order(group, order, LocalDateTime.now(), false);
                    change(tenant, "SYSTEM", group.getId(), new Change("auto:" + UUID.randomUUID(), group.getRevision(),
                        "JOIN", List.of(order.getId()), null, null, null, null, false, "支付后按已核定窗口补偿归组"));
                    break;
                } catch (ArynBusinessException conflict) {
                    log.debug("自动归组保留待排程；tenant={} order={} group={} errorType={}", tenant, order.getId(), group.getId(), conflict.getClass().getSimpleName());
                }
            }
        }
    }
    private Receipt atomic(String request, String fingerprint, Supplier<Receipt> work) {
        for (int attempt = 0; ; attempt++) {
            try { return transactions.execute(status -> work.get()); }
            catch (DuplicateKeyException duplicate) {
                var result = replay(request, fingerprint); if (result != null) return result;
                throw new ArynBusinessException(409, "窗口序号或订单归组冲突，请重载后重试");
            } catch (TransientDataAccessException contention) { if (attempt >= 2) throw contention; }
        }
    }
    /** 由既有波次事务调用；一个业务组可分多个波次，启动不会替代每单签收。 */
    public void startFulfillment(String tenant, String actor, List<String> ids, String waveId) {
        context(tenant, actor);
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("缺少波次事务");
        for (var id : ids) {
            var group = group(id, true);
            String request = "wave-start:" + waveId + ":" + id; String digest = fingerprint(actor, "START_WAVE", id, waveId);
            if (replay(request, digest) != null) continue;
            if (!Set.of("OPEN", "CLOSED", "FULFILLING").contains(group.getStatus()) || group.getCutoff() == null
                    || group.getWindowEnd() == null || !LocalDateTime.now().isBefore(group.getWindowEnd())
                    || "OPEN".equals(group.getStatus()) && LocalDateTime.now().isBefore(group.getCutoff()))
                throw new ArynBusinessException(409, "请先核定有效排程并截单，再启动拣货");
            group.setStatus("FULFILLING"); group.setRevision(group.getRevision() + 1); group.setUpdateBy(actor); group.setUpdateTime(LocalDateTime.now()); groups.updateById(group);
            record(group, actor, request, digest, "START_WAVE", "显式开始拣货", waveId, List.of(), orderSnapshots(tenant, activeLinks(id, true)));
        }
    }
    private Receipt replay(String request, String fingerprint) {
        var op = operations.selectOne(Wrappers.<DeliveryGroupOperation>lambdaQuery().eq(DeliveryGroupOperation::getTenantId, tenant())
            .eq(DeliveryGroupOperation::getRequestId, request));
        if (op == null) return null;
        if (!fingerprint.equals(op.getFingerprint())) throw new ArynBusinessException(409, "同一请求号不能更换操作或操作者");
        try { return json.readValue(op.getResultJson(), Receipt.class); }
        catch (JsonProcessingException invalid) { throw new IllegalStateException("合送组回执损坏", invalid); }
    }
    private Receipt record(DeliveryGroup group, String actor, String request, String fingerprint, String action,
            String reason, Object input, List<String> ids, List<OrderInfo> notify) {
        var receipt = new Receipt(group.getId(), group.getStatus(), group.getRevision(), List.copyOf(ids));
        var op = new DeliveryGroupOperation(); op.setId(UUID.randomUUID().toString()); op.setGroupId(group.getId()); op.setTenantId(tenant());
        op.setRequestId(request); op.setFingerprint(fingerprint); op.setAction(action); op.setActorId(actor); op.setReason(reason);
        op.setRequestJson(encode(input)); op.setResultJson(encode(receipt));
        var messages = notify.stream().filter(order -> order.getUserId() != null).map(order -> new Notification(
            "delivery-group:" + op.getId() + ":" + order.getId(), order.getUserId(), order.getId(),
            "您的订单配送安排已更新；请查看本单配送窗口，付款及签收仍按每单独立处理。" )).toList();
        op.setNotificationJson(encode(messages)); op.setOutboxStatus(messages.isEmpty() ? "SENT" : "PENDING"); op.setAttempts(0); op.setNextAttemptAt(LocalDateTime.now());
        op.setCreateBy(actor); op.setUpdateBy(actor); op.setCreateTime(LocalDateTime.now()); op.setUpdateTime(op.getCreateTime()); op.setDelFlag("0"); operations.insert(op);
        return receipt;
    }
    private Facts facts(String tenant, String call) {
        if (call == null || call.isBlank()) throw new ArynBusinessException("请选择有效靠港计划");
        var context = vessels.getVesselCallContext(tenant, call);
        if (context == null || Boolean.FALSE.equals(context.getCallOrderable()) || !call.equals(context.getVesselCallId())
                || context.getVesselId() == null || context.getPortCode() == null) throw new ArynBusinessException(409, "靠港已关闭或不可用，请重新排程");
        return new Facts(context, Instant.now());
    }
    private void requireFresh(Facts facts) { if (facts.at().isBefore(Instant.now().minusSeconds(30))) throw new ArynBusinessException(409, "靠港事实已过期，请重试"); }
    private void requireCall(DeliveryGroup group, VesselContextDTO call) {
        if (!Objects.equals(group.getVesselId(), call.getVesselId()) || !Objects.equals(group.getPortCode(), call.getPortCode()))
            throw new ArynBusinessException(409, "靠港船舶或港口已变化，请人工处理原组");
    }
    private void requireWindow(VesselContextDTO call, LocalDateTime start, LocalDateTime end) {
        if (start == null && end == null) return;
        if (call.getDeliveryWindowStart() == null || call.getDeliveryWindowEnd() == null
                || start.isBefore(call.getDeliveryWindowStart()) || end.isAfter(call.getDeliveryWindowEnd()))
            throw new ArynBusinessException("合送窗口必须落在当前靠港配送窗口内");
    }
    private void capacity(Integer maximum) { if (maximum == null || maximum < 1 || maximum > 10000) throw new ArynBusinessException("请明确本窗口可接受的订单数（1至10000），重量和车辆运力需人工核定"); }
    private void validate(String request, String reason) {
        if (request == null || !request.matches("[a-zA-Z0-9:_-]{8,120}") || reason == null || reason.isBlank() || reason.length() > 500)
            throw new ArynBusinessException("需要有效请求号及最多500字的操作原因");
    }
    private void context(String tenant, String actor) {
        if (tenant == null || tenant.isBlank() || !tenant.equals(tenant()) || actor == null || actor.isBlank()) throw new ArynBusinessException(403, "缺少可信租户或操作者");
    }
    private String tenant() { String tenant = ArynTenantContextHolder.getTenantId(); if (tenant == null || tenant.isBlank()) throw new ArynBusinessException(403, "缺少租户上下文"); return tenant; }
    private DeliveryGroup group(String id, boolean lock) {
        var group = groups.selectOne(Wrappers.<DeliveryGroup>lambdaQuery().eq(DeliveryGroup::getTenantId, tenant()).eq(DeliveryGroup::getId, id).last(lock ? "FOR UPDATE" : ""));
        if (group == null) throw new ArynBusinessException(403, "合送组不存在"); return group;
    }
    private OrderInfo lockOrder(String tenant, String id) {
        var order = orders.selectOne(Wrappers.<OrderInfo>lambdaQuery().eq(OrderInfo::getTenantId, tenant).eq(OrderInfo::getId, id).last("FOR UPDATE"));
        if (order == null) throw new ArynBusinessException(403, "订单不存在"); return order;
    }
    private List<DeliveryGroupOrderLink> activeLinks(String id, boolean lock) {
        return new ArrayList<>(links.selectList(Wrappers.<DeliveryGroupOrderLink>lambdaQuery().eq(DeliveryGroupOrderLink::getTenantId, tenant())
            .eq(DeliveryGroupOrderLink::getGroupId, id).eq(DeliveryGroupOrderLink::getStatus, "ACTIVE").orderByAsc(DeliveryGroupOrderLink::getOrderId).last(lock ? "FOR UPDATE" : "")));
    }
    private void updateLink(DeliveryGroupOrderLink link) {
        links.update(null, Wrappers.<DeliveryGroupOrderLink>lambdaUpdate().eq(DeliveryGroupOrderLink::getTenantId, tenant()).eq(DeliveryGroupOrderLink::getId, link.getId())
            .set(DeliveryGroupOrderLink::getGroupId, link.getGroupId()).set(DeliveryGroupOrderLink::getWarehouseId, link.getWarehouseId())
            .set(DeliveryGroupOrderLink::getWaveId, link.getWaveId()).set(DeliveryGroupOrderLink::getTaskId, link.getTaskId())
            .set(DeliveryGroupOrderLink::getStatus, link.getStatus()).set(DeliveryGroupOrderLink::getRevision, link.getRevision())
            .set(DeliveryGroupOrderLink::getUpdateBy, link.getUpdateBy()).set(DeliveryGroupOrderLink::getUpdateTime, link.getUpdateTime()));
    }
    private List<OrderInfo> orderSnapshots(String tenant, List<DeliveryGroupOrderLink> active) {
        if (active.isEmpty()) return List.of();
        return orders.selectList(Wrappers.<OrderInfo>lambdaQuery().eq(OrderInfo::getTenantId, tenant).in(OrderInfo::getId, active.stream().map(DeliveryGroupOrderLink::getOrderId).toList()));
    }
    private void requireNotExecuting(List<DeliveryGroupOrderLink> active) {
        for (var link : active) {
            if (link.getWaveId() != null) {
                var wave = waves.selectOne(Wrappers.<FulfillmentWave>lambdaQuery().eq(FulfillmentWave::getTenantId, tenant()).eq(FulfillmentWave::getId, link.getWaveId()).last("FOR UPDATE"));
                if (wave == null || !"1".equals(wave.getStatus())) throw new ArynBusinessException(409, "波次已执行，不能特批追加或移组");
            }
            if (link.getTaskId() != null) {
                var task = tasks.selectOne(Wrappers.<DeliveryTask>lambdaQuery().eq(DeliveryTask::getTenantId, tenant()).eq(DeliveryTask::getId, link.getTaskId()).last("FOR UPDATE"));
                if (task == null || !"1".equals(task.getStatus())) throw new ArynBusinessException(409, "任务已派出，不能特批追加或移组");
            }
        }
    }
    /** 订单可能先单独入波次；只接纳同仓同靠港且尚未开始的波次，不能丢失履约关联。 */
    private String pendingWave(String tenant, String orderId, DeliveryGroup group) {
        var existing = picks.selectList(Wrappers.<FulfillmentPickItem>lambdaQuery().eq(FulfillmentPickItem::getTenantId, tenant)
            .eq(FulfillmentPickItem::getOrderId, orderId));
        var ids = existing.stream().map(FulfillmentPickItem::getWaveId).distinct().toList();
        if (ids.isEmpty()) return null;
        if (ids.size() != 1 || ids.get(0) == null) throw new ArynBusinessException(409, "订单波次关系异常，请先处理履约明细");
        var wave = waves.selectOne(Wrappers.<FulfillmentWave>lambdaQuery().eq(FulfillmentWave::getTenantId, tenant)
            .eq(FulfillmentWave::getId, ids.get(0)).last("FOR UPDATE"));
        if (wave == null || !FulfillmentWave.STATUS_PENDING_PICK.equals(wave.getStatus())
                || !Objects.equals(group.getWarehouseId(), wave.getWarehouseId())
                || !Objects.equals(group.getVesselCallId(), wave.getVesselCallId()) || !Objects.equals(group.getPortCode(), wave.getPortCode()))
            throw new ArynBusinessException(409, "订单已有执行中或不兼容的波次，请先处理履约安排");
        return wave.getId();
    }
    private String encode(Object value) { try { return json.writeValueAsString(value); } catch (JsonProcessingException invalid) { throw new IllegalStateException(invalid); } }
    private String fingerprint(String actor, String action, String id, Object input) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encode(List.of(actor, action, id, input)).getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
