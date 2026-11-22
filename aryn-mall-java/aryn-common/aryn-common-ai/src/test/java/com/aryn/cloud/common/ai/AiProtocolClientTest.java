package com.aryn.cloud.common.ai;

import com.aryn.cloud.common.ai.client.AiProtocolClient;
import com.aryn.cloud.common.ai.contract.*;
import com.aryn.cloud.common.ai.error.*;
import com.aryn.cloud.common.ai.protocol.AiEndpointPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class AiProtocolClientTest {
    HttpServer server;
    AtomicReference<String> sent = new AtomicReference<>();
    AtomicReference<String> path = new AtomicReference<>();
    String response;
    int status;
    long delay;
    AiProtocolClient client;
    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1", exchange -> {
            sent.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            path.set(exchange.getRequestURI().getPath());
            try { if (delay > 0) Thread.sleep(delay); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            try { exchange.sendResponseHeaders(status, bytes.length); exchange.getResponseBody().write(bytes); }
            finally { exchange.close(); }
        });
        server.start();
        status = 200;
        response = "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"{\\\"rows\\\":[]}\"}}],\"usage\":{\"total_tokens\":12}}";
        client = new AiProtocolClient(new AiEndpointPolicy(Set.of("127.0.0.1")), 4096);
    }
    @AfterEach void stop() { server.stop(0); }
    AiModelSnapshot snapshot(AiCapability capability, String model, int timeout, Map<String,Object> options) {
        return new AiModelSnapshot("v1", "credential1", "provider1", URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1"),
            model, capability, timeout, 2000, 100, capability == AiCapability.EMBEDDING ? 2 : null, options, new AiSecret("test-secret"));
    }
    AiCallContext context() { return new AiCallContext("call1", "task1", "tenant1", AiUsage.PROCUREMENT_PARSE); }
    @Test void textUsesSnapshotAndOmitsUndeclaredOptions() throws Exception {
        var result = client.text(snapshot(AiCapability.STRUCTURED_TEXT,"model-a",2000,Map.of()),context(),"system","input",Set.of("rows"));
        assertTrue(result.output().has("rows"));
        assertEquals(12L,result.totalTokens());
        assertEquals("v1",result.configVersion());
        var body = new ObjectMapper().readTree(sent.get());
        assertEquals("model-a", body.path("model").asText());
        assertFalse(body.has("thinking")); assertFalse(body.has("dimensions"));
        assertEquals("/v1/chat/completions", path.get());
        client.text(snapshot(AiCapability.STRUCTURED_TEXT,"model-b",2000,Map.of("temperature",0)),context(),"s","i",Set.of("rows"));
        assertTrue(sent.get().contains("model-b"));
        assertFalse(snapshot(AiCapability.STRUCTURED_TEXT,"m",2000,Map.of()).toString().contains("test-secret"));
    }
    @Test void embeddingsRequireOrderedCompleteDimensions() {
        response = "{\"data\":[{\"index\":1,\"embedding\":[3,4]},{\"index\":0,\"embedding\":[1,2]}]}";
        var result=client.embed(snapshot(AiCapability.EMBEDDING,"embed-a",2000,Map.of()),context(),List.of("a","b"));
        assertEquals(List.of(1.0,2.0), result.vectors().get(0));
        assertFalse(sent.get().contains("dimensions"));
        response = "{\"data\":[{\"index\":0,\"embedding\":[1]}]}";
        assertError(AiErrorCode.INVALID_RESULT, () -> client.embed(snapshot(AiCapability.EMBEDDING,"m",2000,Map.of()),context(),List.of("a")));
        response = "{\"data\":[{\"index\":0,\"embedding\":[1,2]},{\"index\":0,\"embedding\":[1,2]}]}";
        assertError(AiErrorCode.INVALID_RESULT, () -> client.embed(snapshot(AiCapability.EMBEDDING,"m",2000,Map.of()),context(),List.of("a","b")));
    }
    @ParameterizedTest @CsvSource({"401,AUTHENTICATION","403,AUTHENTICATION","429,RATE_LIMITED","500,UNAVAILABLE"})
    void errorsAreSanitized(int http, AiErrorCode code) {
        status=http; response="provider secret test-secret";
        var error=assertThrows(AiCallException.class, () -> client.text(snapshot(AiCapability.STRUCTURED_TEXT,"m",2000,Map.of()),context(),"s","i",Set.of()));
        assertEquals(code,error.code()); assertFalse(error.toString().contains("test-secret"));
    }
    @Test void invalidTruncatedAndOversizedResultsFailExplicitly() {
        response="not-json";
        assertTextError(AiErrorCode.PROTOCOL);
        response="{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"{}\"}}]}";
        assertTextError(AiErrorCode.TRUNCATED);
        response="x".repeat(5000);
        assertTextError(AiErrorCode.RESPONSE_TOO_LARGE);
    }
    @Test void timeoutAndSchemaAreChecked() {
        response="{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"{}\"}}]}";
        assertTextError(AiErrorCode.INVALID_RESULT);
        delay=200;
        assertError(AiErrorCode.TIMEOUT, () -> client.text(snapshot(AiCapability.STRUCTURED_TEXT,"m",30,Map.of()),context(),"s","i",Set.of()));
    }
    @Test void capabilitiesParametersAndNetworkAreRestricted() {
        assertThrows(IllegalArgumentException.class, () -> snapshot(AiCapability.STRUCTURED_TEXT,"m",1000,Map.of("unknown",true)));
        assertThrows(IllegalArgumentException.class, () -> snapshot(AiCapability.EMBEDDING,"m",1000,Map.of("thinking",true)));
        assertThrows(IllegalArgumentException.class, () -> new AiEndpointPolicy(Set.of()).validate(URI.create("http://127.0.0.1/v1")));
        assertThrows(IllegalArgumentException.class, () -> new AiEndpointPolicy(Set.of()).validate(URI.create("https://169.254.169.254/latest")));
    }
    void assertTextError(AiErrorCode code) { assertError(code, () -> client.text(snapshot(AiCapability.STRUCTURED_TEXT,"m",2000,Map.of()),context(),"s","i",Set.of("rows"))); }
    void assertError(AiErrorCode code, org.junit.jupiter.api.function.Executable executable) { assertEquals(code,assertThrows(AiCallException.class,executable).code()); }
}
