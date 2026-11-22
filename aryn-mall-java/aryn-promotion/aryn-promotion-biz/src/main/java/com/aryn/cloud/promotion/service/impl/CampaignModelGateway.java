package com.aryn.cloud.promotion.service.impl;
import com.fasterxml.jackson.databind.JsonNode;
/** 最小模型端口；不向活动包/任务持久化凭证。 */
public interface CampaignModelGateway {
	Session open(String taskId, String configVersion);
	interface Session {
		Metadata metadata();
		JsonNode generate(String input);
	}
	record Metadata(String configVersion, String provider, String model) { }
}
