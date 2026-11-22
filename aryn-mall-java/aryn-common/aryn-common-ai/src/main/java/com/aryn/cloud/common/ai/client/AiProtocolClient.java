package com.aryn.cloud.common.ai.client;

import com.aryn.cloud.common.ai.contract.*;
import com.aryn.cloud.common.ai.error.*;
import com.aryn.cloud.common.ai.protocol.AiEndpointPolicy;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;

/** 无自动重试：由持久业务任务决定重试与预算，每次调用前重新检查运行状态。 */
public final class AiProtocolClient {
    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json = new ObjectMapper();
    private final AiEndpointPolicy endpointPolicy;
    private final int maxResponseBytes;
    public AiProtocolClient(AiEndpointPolicy endpointPolicy, int maxResponseBytes) {
        if (maxResponseBytes < 1 || maxResponseBytes > 16777216) throw new IllegalArgumentException("响应限制无效");
        this.endpointPolicy = endpointPolicy; this.maxResponseBytes = maxResponseBytes;
    }
    public AiTextResult text(AiModelSnapshot snapshot, AiCallContext context, String system, String input, Set<String> requiredFields) {
        requireCapability(snapshot, AiCapability.STRUCTURED_TEXT);
        checkInput(snapshot, List.of(system, input));
        ObjectNode body = base(snapshot);
        body.put("max_tokens", snapshot.maxOutputTokens());
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role","system").put("content",system);
        messages.addObject().put("role","user").put("content",input);
        long start = System.nanoTime();
        JsonNode response = send(snapshot, context, "/chat/completions", body);
        JsonNode choices = response.path("choices");
        if (!choices.isArray() || choices.size() != 1) throw error(AiErrorCode.PROTOCOL);
        JsonNode choice = choices.get(0);
        if ("length".equals(choice.path("finish_reason").asText())) throw error(AiErrorCode.TRUNCATED);
        if (!"stop".equals(choice.path("finish_reason").asText())) throw error(AiErrorCode.INVALID_RESULT);
        JsonNode content = choice.path("message").path("content");
        if (!content.isTextual()) throw error(AiErrorCode.PROTOCOL);
        JsonNode output = parse(content.asText());
        if (!output.isObject() && !output.isArray()) throw error(AiErrorCode.INVALID_RESULT);
        if (!requiredFields.isEmpty()) {
            if (!output.isObject()) throw error(AiErrorCode.INVALID_RESULT);
            for (String field : requiredFields) if (!output.hasNonNull(field)) throw error(AiErrorCode.INVALID_RESULT);
        }
        return new AiTextResult(output, tokens(response), snapshot.configVersion(), elapsed(start));
    }
    public AiEmbeddingResult embed(AiModelSnapshot snapshot, AiCallContext context, List<String> inputs) {
        requireCapability(snapshot, AiCapability.EMBEDDING); checkInput(snapshot, inputs);
        if (inputs.isEmpty() || inputs.size() > 256) throw new IllegalArgumentException("向量批次大小无效");
        ObjectNode body = base(snapshot); body.set("input", json.valueToTree(inputs));
        long start = System.nanoTime();
        JsonNode response = send(snapshot,context,"/embeddings",body);
        JsonNode data = response.path("data");
        if (!data.isArray() || data.size() != inputs.size()) throw error(AiErrorCode.INVALID_RESULT);
        List<List<Double>> vectors = new ArrayList<>(Collections.nCopies(inputs.size(), null));
        for (JsonNode row : data) {
            if (!row.path("index").isIntegralNumber()) throw error(AiErrorCode.INVALID_RESULT);
            int index = row.path("index").asInt(-1); JsonNode vector = row.path("embedding");
            if (index < 0 || index >= inputs.size() || vectors.get(index) != null || !vector.isArray() || vector.size() != snapshot.dimension()) throw error(AiErrorCode.INVALID_RESULT);
            List<Double> values = new ArrayList<>();
            for (JsonNode value : vector) {
                if (!value.isNumber() || !Double.isFinite(value.asDouble())) throw error(AiErrorCode.INVALID_RESULT);
                values.add(value.asDouble());
            }
            vectors.set(index, values);
        }
        return new AiEmbeddingResult(vectors,tokens(response),snapshot.configVersion(),elapsed(start));
    }
    private ObjectNode base(AiModelSnapshot snapshot) {
        ObjectNode body = json.createObjectNode().put("model",snapshot.model());
        snapshot.parameters().forEach((key,value) -> {
            if (key.equals("response_format")) body.putObject(key).put("type",value.toString());
            else if (key.equals("thinking")) body.putObject(key).put("type",Boolean.TRUE.equals(value) ? "enabled" : "disabled");
            else body.set(key,json.valueToTree(value));
        });
        return body;
    }
    private JsonNode send(AiModelSnapshot snapshot, AiCallContext context, String path, ObjectNode body) {
        endpointPolicy.validate(snapshot.endpoint());
        String base = snapshot.endpoint().toString().replaceAll("/+$", "");
        URI target = URI.create(base + path);
        HttpRequest request = HttpRequest.newBuilder(target).timeout(Duration.ofMillis(snapshot.timeoutMillis()))
            .header("Authorization","Bearer " + snapshot.credential().reveal()).header("Content-Type","application/json")
            .header("X-Request-Id",context.callId()).POST(HttpRequest.BodyPublishers.ofString(body.toString(),StandardCharsets.UTF_8)).build();
        CompletableFuture<HttpResponse<byte[]>> future = http.sendAsync(request, info -> new LimitedBody(maxResponseBytes));
        try {
            HttpResponse<byte[]> response = future.get(snapshot.timeoutMillis(),TimeUnit.MILLISECONDS);
            int status = response.statusCode();
            if (status == 401 || status == 403) throw error(AiErrorCode.AUTHENTICATION);
            if (status == 429) throw error(AiErrorCode.RATE_LIMITED);
            if (status >= 500) throw error(AiErrorCode.UNAVAILABLE);
            if (status < 200 || status >= 300) throw error(AiErrorCode.PROTOCOL);
            return parse(new String(response.body(),StandardCharsets.UTF_8));
        } catch (TimeoutException ex) { future.cancel(true); throw error(AiErrorCode.TIMEOUT); }
        catch (InterruptedException ex) { future.cancel(true); Thread.currentThread().interrupt(); throw error(AiErrorCode.CANCELLED); }
        catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            while (cause != null) {
                if (cause instanceof AiCallException ai) throw ai;
                if (cause instanceof HttpTimeoutException) throw error(AiErrorCode.TIMEOUT);
                cause = cause.getCause();
            }
            throw error(AiErrorCode.UNAVAILABLE);
        }
    }
    private JsonNode parse(String text) {
        try { return json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(text); }
        catch (Exception ex) { throw error(AiErrorCode.PROTOCOL); }
    }
    private static void requireCapability(AiModelSnapshot snapshot,AiCapability capability) {
        if (snapshot.capability() != capability) throw new IllegalArgumentException("AI 能力不匹配");
    }
    private static void checkInput(AiModelSnapshot snapshot,List<String> inputs) {
        long length=0;
        for (String input : inputs) { if (input == null) throw new IllegalArgumentException("输入不能为空"); length += input.length(); }
        if (length > snapshot.maxInputChars()) throw new IllegalArgumentException("AI 输入超限");
    }
    private static Long tokens(JsonNode response) {
        JsonNode tokens=response.path("usage").path("total_tokens");
        return tokens.isIntegralNumber() && tokens.canConvertToLong() && tokens.asLong() >= 0 ? tokens.asLong() : null;
    }
    private static long elapsed(long start) { return TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start); }
    private static AiCallException error(AiErrorCode code) { return new AiCallException(code); }
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        final CompletableFuture<byte[]> result=new CompletableFuture<>();
        final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        final int limit;
        Flow.Subscription subscription;
        LimitedBody(int limit) { this.limit=limit; }
        public CompletionStage<byte[]> getBody() { return result; }
        public void onSubscribe(Flow.Subscription subscription) { this.subscription=subscription; subscription.request(1); }
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer:buffers) {
                if ((long)bytes.size()+buffer.remaining()>limit) { subscription.cancel(); result.completeExceptionally(error(AiErrorCode.RESPONSE_TOO_LARGE)); return; }
                byte[] chunk=new byte[buffer.remaining()]; buffer.get(chunk); bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        public void onError(Throwable error) { result.completeExceptionally(error); }
        public void onComplete() { result.complete(bytes.toByteArray()); }
    }
}
