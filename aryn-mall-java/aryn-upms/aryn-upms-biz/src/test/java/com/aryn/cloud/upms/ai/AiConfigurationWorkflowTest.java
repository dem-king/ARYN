package com.aryn.cloud.upms.ai;

import com.aryn.cloud.common.ai.contract.*;
import com.aryn.cloud.common.ai.error.AiCallException;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.upms.api.client.AiRuntimeClient;
import com.aryn.cloud.upms.api.dto.ai.*;
import com.aryn.cloud.upms.api.entity.ai.AiCapabilityTest;
import com.aryn.cloud.upms.config.AiCredentialProperties;
import com.aryn.cloud.upms.dubbo.RemoteAiConfigServiceImpl;
import com.aryn.cloud.upms.service.impl.*;
import com.aryn.cloud.upms.support.AiRuntimeAuthorization;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 真实本地HTTP + 真实配置/运行态服务/客户端；仓储用可审计内存fixture。 */
class AiConfigurationWorkflowTest {
    private final AiConfigServiceTest fixture = new AiConfigServiceTest();
    private final List<HttpServer> servers = new ArrayList<>();
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final AtomicBoolean invalid = new AtomicBoolean();
    private static final String TOKEN = "test-only-runtime-token-0123456789";

    @BeforeEach void setup() {
        fixture.setup();
        ArynTenantContextHolder.setTenantId("tenant-a");
        List<AiCapabilityTest> tests = new ArrayList<>();
        Set<String> activated = new HashSet<>();
        doAnswer(i -> { tests.add(i.getArgument(0)); return null; }).when(fixture.repo).recordTest(any());
        when(fixture.repo.tested(anyString(), anyString(), any(LocalDateTime.class))).thenAnswer(i ->
            tests.stream().anyMatch(t -> t.getVersionId().equals(i.getArgument(0))
                && t.getModelId().equals(i.getArgument(1)) && Boolean.TRUE.equals(t.getPassed())
                && t.getCreateTime().isAfter(i.getArgument(2))));
        doAnswer(i -> {
            if (Set.of("ACTIVATE", "ROLLBACK").contains(i.getArgument(0))) activated.add(i.getArgument(1));
            return null;
        }).when(fixture.repo).operation(anyString(), any(), any(), anyString());
        when(fixture.repo.wasActivated(anyString())).thenAnswer(i -> activated.contains(i.getArgument(0)));
    }

    @AfterEach void close() {
        servers.forEach(s -> s.stop(0));
        ArynTenantContextHolder.removeTenantId();
    }

    private String endpoint(String name) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(name + ":" + exchange.getRequestHeaders().getFirst("Authorization") + ":" + body);
            byte[] response = (invalid.get() ? "{}" :
                "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"{\\\"ok\\\":true}\"}}],\"usage\":{\"total_tokens\":4}}")
                .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        servers.add(server);
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    private AiConfigSaveCommand command(long revision, String url, String model, String secret) {
        return new AiConfigSaveCommand(revision,
            List.of(new AiConfigSaveCommand.ProviderCommand("p", "Local fake", "OPENAI_COMPATIBLE", url,
                true, AiConfigSaveCommand.KeyOperation.REPLACE, secret)),
            List.of(new AiModel("m", "p", model, AiCapability.STRUCTURED_TEXT, 2000, 2000, 100, null, Map.of())),
            Map.of(AiUsage.PROCUREMENT_PARSE, "m"));
    }

    @Test void switchAndRollbackKeepInflightSnapshotAndEmergencyStopsBothInstances() throws Exception {
        var service = fixture.service;
        var a = service.save(command(0, endpoint("A"), "model-a", "key-a"));
        assertTrue(service.test(a.version(), "m").passed());
        service.activate(a.version(), 1, false);
        var settings = new AiCredentialProperties();
        settings.setRuntimeTokens(Map.of("order", TOKEN));
        var budget = mock(AiUsageBudgetService.class);
        when(budget.begin(any(), any())).thenReturn(true);
        var runtime = new AiRuntimeService(fixture.repo, service, new AiRuntimeAuthorization(settings), budget);
        var remote = new RemoteAiConfigServiceImpl(runtime);
        var client1 = new AiRuntimeClient(remote, "order", TOKEN, Set.of("127.0.0.1"));
        var client2 = new AiRuntimeClient(remote, "order", TOKEN, Set.of("127.0.0.1"));
        var inflight = client1.open("tenant-a", "inflight", AiUsage.PROCUREMENT_PARSE);
        var b = service.save(command(2, endpoint("B"), "model-b", "key-b"));
        invalid.set(true);
        assertFalse(service.test(b.version(), "m").passed());
        assertThrows(IllegalStateException.class, () -> service.activate(b.version(), 3, false));
        assertEquals(a.version(), fixture.state.getActiveVersion());
        assertThrows(AiCallException.class, () -> client2.openVersion("tenant-a", "unapproved", AiUsage.PROCUREMENT_PARSE, b.version()));
        invalid.set(false);
        assertTrue(service.test(b.version(), "m").passed());
        service.activate(b.version(), 3, false);
        requests.clear();
        inflight.text("fixed", "sample", Set.of("ok"));
        client2.open("tenant-a", "new", AiUsage.PROCUREMENT_PARSE).text("fixed", "sample", Set.of("ok"));
        assertTrue(requests.get(0).startsWith("A:Bearer key-a:"));
        assertTrue(requests.get(1).startsWith("B:Bearer key-b:"));
        service.activate(a.version(), 4, true);
        assertEquals(a.version(), client2.open("tenant-a", "rollback", AiUsage.PROCUREMENT_PARSE).model().configVersion());
        service.emergency(null, true, 5);
        int before = requests.size();
        assertThrows(AiCallException.class, () -> inflight.text("fixed", "sample", Set.of("ok")));
        assertThrows(AiCallException.class, () -> client2.open("tenant-a", "blocked", AiUsage.PROCUREMENT_PARSE));
        assertEquals(before, requests.size());
        verify(budget, times(2)).report(any());
    }

    @Test void bootstrapImportsOnceWithoutActivatingOrOverwritingExistingDraft() {
        var environment = new MockEnvironment().withProperty("AI_API_KEY", "test-legacy-key")
            .withProperty("AI_BASE_URL", "http://127.0.0.1:8080/v1").withProperty("AI_MODEL", "legacy-model");
        var bootstrap = new AiLegacyBootstrapService(fixture.service, environment);
        var imported = bootstrap.importDraft(0, false);
        assertNull(imported.activeVersion());
        assertEquals("legacy-model", imported.configuration().models().get(0).model());
        assertFalse(imported.toString().contains("test-legacy-key"));
        assertThrows(IllegalStateException.class, () -> bootstrap.importDraft(1, false));
        assertThrows(IllegalStateException.class, () -> bootstrap.importDraft(0, true));
    }
}
