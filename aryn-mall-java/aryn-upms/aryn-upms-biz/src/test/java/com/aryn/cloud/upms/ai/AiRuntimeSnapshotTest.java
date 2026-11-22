package com.aryn.cloud.upms.ai;
import com.aryn.cloud.upms.api.dto.ai.*;
import com.aryn.cloud.upms.api.entity.ai.AiRuntimeState;
import com.aryn.cloud.upms.config.AiCredentialProperties;
import com.aryn.cloud.upms.support.*;
import com.aryn.cloud.upms.service.impl.*;
import com.aryn.cloud.common.ai.contract.*;
import com.aryn.cloud.common.ai.error.*;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import org.junit.jupiter.api.*;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class AiRuntimeSnapshotTest {
    AiConfigRepository repo;AiConfigServiceImpl config;AiRuntimeService runtime;AiRuntimeState state;
    String token="01234567890123456789012345678901";
    @BeforeEach void setup() {
        ArynTenantContextHolder.setTenantId("tenant");
        repo=mock(AiConfigRepository.class);config=mock(AiConfigServiceImpl.class);
        when(repo.inPlatform(any())).thenAnswer(i->((Supplier<?>)i.getArgument(0)).get());
        state=new AiRuntimeState();state.setActiveVersion("a");state.setEmergencyEpoch(0L);state.setDisabled(false);
        when(repo.state(anyBoolean())).thenReturn(state);when(repo.wasActivated("a")).thenReturn(true);when(config.disabledProviders(any())).thenReturn(Set.of());
        var settings=new AiCredentialProperties();settings.setRuntimeTokens(Map.of("order",token));
        runtime=new AiRuntimeService(repo,config,new AiRuntimeAuthorization(settings),mock(AiUsageBudgetService.class));
        for(String v:List.of("a","b")) {
            var model=new AiModel("m","p","model-"+v,AiCapability.STRUCTURED_TEXT,1000,1000,100,null,Map.of());
            when(config.configuration(v)).thenReturn(new AiConfiguration(List.of(),List.of(model),Map.of(AiUsage.PROCUREMENT_PARSE,"m")));
            when(config.snapshot(v,"m")).thenReturn(new AiModelSnapshot(v,"key-"+v,"p",URI.create("https://example.com/v1"),"model-"+v,AiCapability.STRUCTURED_TEXT,1000,1000,100,null,Map.of(),new AiSecret("secret-"+v)));
        }
    }
    @AfterEach void cleanup(){ArynTenantContextHolder.removeTenantId();}
    AiRuntimeRequest signed(String action,String version,long epoch) {
        var request=new AiRuntimeRequest(new AiCallContext("call","task","tenant",AiUsage.PROCUREMENT_PARSE),"order",Instant.now().getEpochSecond(),"",version,"p",epoch);
        return new AiRuntimeRequest(request.context(),request.caller(),request.issuedAt(),AiRuntimeSignatures.sign(action,request,token),version,"p",epoch);
    }
    @Test void twoInstancesAlwaysResolveAuthoritativeVersionWhileOldTaskKeepsSnapshot() {
        var old=runtime.resolve(signed("resolve",null,0));assertEquals("a",old.model().configVersion());
        state.setActiveVersion("b");
        assertEquals("b",runtime.resolve(signed("resolve",null,0)).model().configVersion());
        assertEquals("b",runtime.currentVersion(signed("current",null,0)));
        assertEquals("secret-a",old.model().credential().reveal());
        assertDoesNotThrow(()->runtime.checkRuntimeState(signed("check","a",0)));
    }
    @Test void emergencyAndAuthorityFailureBlockOldTask() {
        state.setDisabled(true);assertThrows(AiCallException.class,()->runtime.checkRuntimeState(signed("check","a",0)));
        state.setDisabled(false);state.setEmergencyEpoch(1L);
        assertThrows(AiCallException.class,()->runtime.checkRuntimeState(signed("check","a",0)));
        when(repo.state(false)).thenThrow(new IllegalStateException("authority down"));
        assertThrows(IllegalStateException.class,()->runtime.checkRuntimeState(signed("check","a",1)));
    }
    @Test void tenantSpoofAndWrongSignatureFailBeforeReadingCredentials() {
        ArynTenantContextHolder.setTenantId("other");assertThrows(SecurityException.class,()->runtime.resolve(signed("resolve",null,0)));
        verifyNoInteractions(repo,config);
    }
    @Test void signedActionCannotBeReusedForDifferentOperation() {
        assertThrows(SecurityException.class,()->runtime.checkRuntimeState(signed("resolve","a",0)));
        verifyNoInteractions(repo,config);
    }
    @Test void runtimeSnapshotSurvivesRpcJsonSerializationWithRedactedToString() {
        var snapshot=runtime.resolve(signed("resolve",null,0));
        String wire=com.alibaba.fastjson2.JSON.toJSONString(snapshot);
        var restored=com.alibaba.fastjson2.JSON.parseObject(wire,AiResolvedSnapshot.class);
        assertEquals("secret-a",restored.model().credential().reveal());
        assertFalse(restored.toString().contains("secret-a"));
    }

}
