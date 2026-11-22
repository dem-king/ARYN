package com.aryn.cloud.upms.ai;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.upms.mapper.SysCopyrightConfigMapper;
import com.aryn.cloud.upms.support.AiConfigRepository;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiPlatformContextTest {
    @AfterEach void cleanup() { ArynTenantContextHolder.removeTenantId(); }
    @Test void platformScopeRestoresBusinessTenantEvenAfterFailure() {
        var lookup = mock(SysCopyrightConfigMapper.class);
        when(lookup.selectPlatformTenantId()).thenReturn("platform");
        var repo = new AiConfigRepository(lookup, null, null, null, null, null, null);
        ArynTenantContextHolder.setTenantId("buyer");
        assertEquals("platform", repo.inPlatform(ArynTenantContextHolder::getTenantId));
        assertEquals("buyer", ArynTenantContextHolder.getTenantId());
        assertThrows(IllegalArgumentException.class, () -> repo.inPlatform(() -> { throw new IllegalArgumentException(); }));
        assertEquals("buyer", ArynTenantContextHolder.getTenantId());
        ArynTenantContextHolder.removeTenantId();
        repo.inPlatform(() -> "value");
        assertNull(ArynTenantContextHolder.getTenantId());
    }
    @Test void callLogReadUsesChosenTenantAndRestoresPlatformAfterFailure() {
        var calls = mock(com.aryn.cloud.upms.mapper.AiCallLogMapper.class);
        var repo = new AiConfigRepository(null, null, null, null, null, null, calls);
        ArynTenantContextHolder.setTenantId("platform");
        when(calls.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenAnswer(call -> {
            assertEquals("buyer", ArynTenantContextHolder.getTenantId());
            throw new IllegalStateException("database failure");
        });
        assertThrows(IllegalStateException.class, () -> repo.callLogs("buyer"));
        assertEquals("platform", ArynTenantContextHolder.getTenantId());
        assertThrows(IllegalArgumentException.class, () -> repo.callLogs("buyer OR 1=1"));
        assertEquals("platform", ArynTenantContextHolder.getTenantId());
    }
}
