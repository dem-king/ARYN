package com.aryn.cloud.order.support.procurement;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.order.api.entity.ProcurementDraft;
import com.aryn.cloud.order.service.IBuyerOrgService;
import com.aryn.cloud.vessel.api.dto.VesselContextDTO;
import com.aryn.cloud.vessel.api.remote.RemoteVesselService;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProcurementAccessTest {
    private final IBuyerOrgService buyers = mock(IBuyerOrgService.class);
    private final RemoteVesselService vessels = mock(RemoteVesselService.class);
    private final ProcurementAccess access = new ProcurementAccess(buyers);
    private ProcurementDraft draft;
    @BeforeEach void setup() {
        ArynTenantContextHolder.setTenantId("tenant");
        ReflectionTestUtils.setField(access, "vessels", vessels);
        draft = new ProcurementDraft(); draft.setTenantId("tenant"); draft.setOwnerUserId("actor"); draft.setPurchaseScene("1");
    }
    @AfterEach void cleanup() { ArynTenantContextHolder.removeTenantId(); }
    @Test void personalDraftNeverAcceptsAnotherActorTenantOrEnterpriseIdentity() {
        access.require("tenant", "actor", draft, false);
        assertThrows(RuntimeException.class, () -> access.require("tenant", "other", draft, false));
        assertThrows(RuntimeException.class, () -> access.require("other", "actor", draft, false));
        draft.setBuyerOrgId("org");
        assertThrows(RuntimeException.class, () -> access.require("tenant", "actor", draft, false));
        verifyNoInteractions(buyers, vessels);
    }
    @Test void vesselMembershipAndCallBindingAreRecheckedButClosedCallDoesNotEraseDraft() {
        draft.setPurchaseScene("2"); draft.setVesselId("vessel"); draft.setVesselCallId("call");
        when(vessels.isVesselMember("tenant", "vessel", "actor")).thenReturn(true);
        var context = new VesselContextDTO(); context.setVesselId("vessel"); context.setCallOrderable(false);
        when(vessels.getVesselCallSnapshot("tenant", "call")).thenReturn(context);
        access.require("tenant", "actor", draft, false);
        context.setVesselId("another-vessel");
        assertThrows(RuntimeException.class, () -> access.require("tenant", "actor", draft, false));
        when(vessels.isVesselMember("tenant", "vessel", "actor")).thenReturn(false);
        assertThrows(RuntimeException.class, () -> access.require("tenant", "actor", draft, false));
    }
    @Test void enterpriseDraftDelegatesToExistingMembershipGuardAndRejectsMixedScene() {
        draft.setPurchaseScene("3"); draft.setBuyerOrgId("org");
        access.require("tenant", "actor", draft, false);
        verify(buyers).requireUsableForCart("tenant", "actor", "org");
        when(buyers.requireUsableForCart("tenant", "actor", "org")).thenThrow(new IllegalStateException("revoked"));
        assertThrows(RuntimeException.class, () -> access.require("tenant", "actor", draft, false));
        draft.setVesselId("vessel");
        assertThrows(RuntimeException.class, () -> access.require("tenant", "actor", draft, false));
    }
}
