package com.aryn.cloud.promotion.service.impl;

import com.aryn.cloud.common.ai.error.AiCallException;
import com.aryn.cloud.common.ai.error.AiErrorCode;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.entity.CampaignGenerationJob;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** 所有模型/商品外呼在租约短事务之外；完成提交同时校验租约和活动包baseRevision。 */
@Service
@RequiredArgsConstructor
public class CampaignGenerationService {
	private final CampaignDraftService drafts;
	private final CampaignProposalValidator validator;
	private final CampaignGenerationParser parser;
	private final CampaignGenerationStore jobs;
	private final CampaignModelGateway gateway;
	private final ObjectMapper codec;

	public CampaignGenerationJob enqueue(String actor, String draftId, Long revision, String requestId) {
		var existing = jobs.find(actor, draftId, requestId);
		if (existing != null) {
			if (!Objects.equals(revision, existing.getBaseRevision())) { throw new ArynBusinessException(409, "请求标识已绑定其他修订"); }
			return existing;
		}
		var draft = drafts.get(actor, draftId);
		if (!Objects.equals(revision, draft.getRevision())) { throw new ArynBusinessException(409, "活动包已变化"); }
		if ("POLICY".equals(draft.getDefinition().getPrimaryType())) {throw new ArynBusinessException("协议政策使用显式政策草稿命令，不由模型替代");}
		var input = validator.prepare(draft.getDefinition());
		// 入队即固定权威配置；不调用模型，worker重试只能解析同一版本。
		var model = gateway.open(requestId, null).metadata();
		return jobs.enqueue(actor, draftId, revision, requestId, input, digest(json(input)), model);
	}

	public void process(String id) {
		String token = jobs.claim(id);
		if (token == null) { return; }
		try {
			var job = jobs.internal(id, false);
			if (!jobs.sourceCurrent(job)) { jobs.markStale(id, token); return; }
			var session = gateway.open(id, job.getConfigVersion());
			jobs.bind(id, token, session.metadata());
			var input = jobs.input(job);
			var output = session.generate(json(input));
			// 价格/库存/成员可在模型调用期间变化，提交草稿前重新核实；只会保存仍合法的提议。
			var currentFacts = validator.prepare(input.getConstraints());
			var proposal = parser.parse(currentFacts, output);
			jobs.complete(id, token, proposal, json(Map.of("modelOutput", output, "validatedCandidates", currentFacts.getCandidates())));
		}
		catch (AiCallException failure) {
			boolean retry = Set.of(AiErrorCode.TIMEOUT, AiErrorCode.RATE_LIMITED, AiErrorCode.UNAVAILABLE).contains(failure.code());
			jobs.fail(id, token, failure.code().name(), retry);
		}
		catch (ArynBusinessException rejected) { jobs.fail(id, token, "BUSINESS_RULE_REJECTED", false); }
		catch (RuntimeException failed) { jobs.fail(id, token, "GENERATION_FAILED", false); }
	}
	private String json(Object value) {
		try { return codec.writeValueAsString(value); }
		catch (Exception invalid) { throw new ArynBusinessException("生成输入或结果无法编码"); }
	}
	private String digest(String value) {
		try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
		catch (Exception unavailable) { throw new IllegalStateException("无法生成输入摘要", unavailable); }
	}
}
