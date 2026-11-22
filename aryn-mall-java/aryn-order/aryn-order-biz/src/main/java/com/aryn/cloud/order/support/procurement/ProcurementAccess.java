package com.aryn.cloud.order.support.procurement;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.entity.ProcurementDraft;
import com.aryn.cloud.order.service.IBuyerOrgService;
import com.aryn.cloud.vessel.api.remote.RemoteVesselService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import java.util.Objects;

/** 草稿属于个人，主体权限每次重验；不使用共享购物车的24小时收集期。 */
@Component
@RequiredArgsConstructor
public class ProcurementAccess {
    private final IBuyerOrgService buyers;
    @DubboReference(check = false) private RemoteVesselService vessels;

    public void require(String tenant, String actor, ProcurementDraft draft, boolean creating) {
        if (!StringUtils.hasText(tenant) || !Objects.equals(tenant, ArynTenantContextHolder.getTenantId())
                || !StringUtils.hasText(actor) || draft == null
                || !Objects.equals(tenant, draft.getTenantId()) || !Objects.equals(actor, draft.getOwnerUserId()))
            throw new ArynBusinessException(403, "无权访问该采购草稿");
        switch (draft.getPurchaseScene() == null ? "" : draft.getPurchaseScene()) {
            case "1" -> {
                if (StringUtils.hasText(draft.getBuyerOrgId())) throw new ArynBusinessException("个人采购不能绑定企业主体");
                if (StringUtils.hasText(draft.getVesselId()) || StringUtils.hasText(draft.getVesselCallId()))
                    requireVessel(tenant, actor, draft, creating);
            }
            case "2" -> {
                if (StringUtils.hasText(draft.getBuyerOrgId())) throw new ArynBusinessException("船供采购不能绑定岸上企业主体");
                requireVessel(tenant, actor, draft, creating);
            }
            case "3" -> {
                if (StringUtils.hasText(draft.getVesselId()) || StringUtils.hasText(draft.getVesselCallId()))
                    throw new ArynBusinessException("岸上企业采购不能绑定船舶");
                buyers.requireUsableForCart(tenant, actor, draft.getBuyerOrgId());
            }
            default -> throw new ArynBusinessException("请选择合法的采购场景");
        }
    }

    private void requireVessel(String tenant, String actor, ProcurementDraft draft, boolean creating) {
        if (!StringUtils.hasText(draft.getVesselId()) || !StringUtils.hasText(draft.getVesselCallId())
                || !vessels.isVesselMember(tenant, draft.getVesselId(), actor))
            throw new ArynBusinessException(403, "船舶成员资格已失效或未选择靠港计划");
        var context = creating ? vessels.getVesselCallContext(tenant, draft.getVesselCallId())
                : vessels.getVesselCallSnapshot(tenant, draft.getVesselCallId());
        if (context == null || !Objects.equals(context.getVesselId(), draft.getVesselId()))
            throw new ArynBusinessException(403, "靠港计划不属于当前船舶或已不可用");
    }
}
