package com.aryn.cloud.upms.ai;
import com.aryn.cloud.upms.support.*;
import com.aryn.cloud.upms.service.impl.AiConfigServiceImpl;
import com.aryn.cloud.upms.config.AiCredentialProperties;
import com.aryn.cloud.upms.api.dto.ai.*;
import com.aryn.cloud.upms.api.entity.ai.*;
import com.aryn.cloud.common.ai.contract.*;
import com.aryn.cloud.common.security.entity.ArynUser;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.function.Supplier;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class AiConfigServiceTest {
    AiConfigRepository repo; AiConfigAuthorization auth; AiConfigServiceImpl service;
    AiRuntimeState state; Map<String,AiConfigVersion> versions;Map<String,AiCredentialVersion> credentials;
    @BeforeEach void setup() {
        repo=mock(AiConfigRepository.class);auth=mock(AiConfigAuthorization.class);
        ArynUser admin=new ArynUser();admin.setUserId("admin");when(auth.require(anyString())).thenReturn(admin);
        when(repo.inPlatform(any())).thenAnswer(i->((Supplier<?>)i.getArgument(0)).get());
        state=new AiRuntimeState();state.setId("platform");state.setTenantId("platform");state.setRevision(0L);state.setEmergencyEpoch(0L);state.setDisabled(false);state.setDisabledProviders("[]");
        when(repo.state(anyBoolean())).thenReturn(state);
        versions=new HashMap<>();credentials=new HashMap<>();
        doAnswer(i->{var v=(AiConfigVersion)i.getArgument(0);versions.put(v.getId(),v);return null;}).when(repo).insertVersion(any());
        doAnswer(i->{var v=(AiCredentialVersion)i.getArgument(0);credentials.put(v.getId(),v);return null;}).when(repo).insertCredential(any());
        when(repo.version(anyString())).thenAnswer(i->versions.get(i.getArgument(0)));
        when(repo.credential(anyString())).thenAnswer(i->credentials.get(i.getArgument(0)));
        var props=new AiCredentialProperties();props.setKeyId("master");props.setKeys(Map.of("master",Base64.getEncoder().encodeToString(new byte[32])));props.setAllowedPrivateHosts(Set.of("127.0.0.1"));
        service=new AiConfigServiceImpl(repo,auth,new AiCredentialCipher(props),props,Optional.empty());
    }
    AiConfigSaveCommand command(long revision,String key) {
        var provider=new AiConfigSaveCommand.ProviderCommand("p","test","OPENAI_COMPATIBLE","http://127.0.0.1:8080/v1",true,key==null?AiConfigSaveCommand.KeyOperation.KEEP:AiConfigSaveCommand.KeyOperation.REPLACE,key);
        var model=new AiModel("m","p","model-a",AiCapability.STRUCTURED_TEXT,1000,2000,100,null,Map.of());
        return new AiConfigSaveCommand(revision,List.of(provider),List.of(model),Map.of(AiUsage.PROCUREMENT_PARSE,"m"));
    }
    @Test void savingDraftNeverChangesActiveAndRequiresFreshCapabilityTest() {
        var draft=service.save(command(0,"first-key"));
        assertNull(state.getActiveVersion());assertEquals(1,state.getRevision());
        assertFalse(draft.toString().contains("first-key"));assertFalse(versions.values().iterator().next().getDocument().contains("first-key"));
        assertThrows(IllegalStateException.class,()->service.activate(draft.version(),1,false));assertNull(state.getActiveVersion());
        when(repo.tested(eq(draft.version()),eq("m"),any(LocalDateTime.class))).thenReturn(true);
        service.activate(draft.version(),1,false);assertEquals(draft.version(),state.getActiveVersion());
        var edited=service.save(command(2,"second-key"));
        assertNotEquals(draft.version(),edited.version());assertEquals(draft.version(),state.getActiveVersion());
        assertNotEquals(draft.configuration().providers().get(0).credentialVersion(),edited.configuration().providers().get(0).credentialVersion());
        assertThrows(IllegalStateException.class,()->service.activate(edited.version(),3,false));assertEquals(draft.version(),state.getActiveVersion());
    }
    @Test void staleRevisionCannotWriteOrActivateAndKeepRetainsCredentialVersion() {
        var first=service.save(command(0,"first-key"));
        assertThrows(IllegalStateException.class,()->service.save(command(0,"lost-update")));
        assertEquals(1,credentials.size());
        var second=service.save(command(1,null));
        assertEquals(first.configuration().providers().get(0).credentialVersion(),second.configuration().providers().get(0).credentialVersion());
        assertThrows(IllegalStateException.class,()->service.activate(first.version(),1,true));
    }
    @Test void emergencyStateIsIndependentOfConfigurationRollback() {
        var draft=service.save(command(0,"first-key"));
        when(repo.tested(anyString(),anyString(),any(LocalDateTime.class))).thenReturn(true);
        service.activate(draft.version(),1,false);
        service.emergency("p",true,2);
        assertEquals(1,state.getEmergencyEpoch());assertTrue(state.getDisabledProviders().contains("p"));
        service.activate(draft.version(),3,true);
        assertTrue(state.getDisabledProviders().contains("p"));
    }

    @Test
    void savedTestResultsMatchTheServerActivationWindow() {
        var draft = service.save(command(0, "fixture-key"));
        var failed = new AiCapabilityTest();
        failed.setModelId("m");
        failed.setPassed(false);
        failed.setStatus("TIMEOUT");
        failed.setCreateTime(LocalDateTime.now());
        var passed = new AiCapabilityTest();
        passed.setModelId("m");
        passed.setPassed(true);
        passed.setStatus("PASSED");
        passed.setCreateTime(failed.getCreateTime().minusMinutes(5));
        when(repo.recentTests(eq(draft.version()), any(LocalDateTime.class))).thenReturn(List.of(failed, passed));

        var results = service.testResults(draft.version());

        verify(auth).require("query");
        assertEquals(1, results.size());
        assertEquals(draft.version(), results.get(0).version());
        assertEquals("PASSED", results.get(0).status());
        assertTrue(results.get(0).passed());
    }
}
