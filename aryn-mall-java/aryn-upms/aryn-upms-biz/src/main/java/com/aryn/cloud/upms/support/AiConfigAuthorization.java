package com.aryn.cloud.upms.support;
import org.springframework.stereotype.Component;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import lombok.RequiredArgsConstructor;
@Component @RequiredArgsConstructor
public class AiConfigAuthorization {
    private final PlatformTenantGuard platformGuard;
    public ArynUser require(String operation) {
        ArynUser user=platformGuard.requirePlatformTenant();
        String permission="upms:ai-config:"+operation;
        if(user.getPermissions()==null || !user.getPermissions().contains(permission)) throw new ArynBusinessException(403,"缺少 AI 配置操作权限");
        return user;
    }
}
