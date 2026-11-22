package com.aryn.cloud.upms.ai;
import com.aryn.cloud.upms.support.*;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AiConfigAuthorizationTest {
    @Test void permissionsNeverSubstituteForPlatformIdentity() {
        var guard=mock(PlatformTenantGuard.class); var auth=new AiConfigAuthorization(guard);
        when(guard.requirePlatformTenant()).thenThrow(new ArynBusinessException(403,"非平台管理员"));
        assertThrows(ArynBusinessException.class,()->auth.require("edit"));
        verify(guard).requirePlatformTenant();
    }
    @Test void actionsHaveSeparatePermissions() {
        var guard=mock(PlatformTenantGuard.class);var auth=new AiConfigAuthorization(guard);
        ArynUser user=new ArynUser();user.setPermissions(List.of("upms:ai-config:query"));
        when(guard.requirePlatformTenant()).thenReturn(user);
        assertSame(user,auth.require("query"));
        for(String action:List.of("edit","test","activate")) assertThrows(ArynBusinessException.class,()->auth.require(action));
    }
}
