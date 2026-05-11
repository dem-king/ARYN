
package com.aryn.cloud.user.config;

import cn.binarywang.wx.miniapp.config.impl.WxMaDefaultConfigImpl;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * @author 雨滴kian
 * @date 2023/7/25
 */
public class WxMiniAppRedisConfigStorage extends WxMaDefaultConfigImpl {

	private static final String ACCESS_TOKEN_KEY = "wx:ma:access_token:";

	private final RedisTemplate<String, String> redisTemplate;

	private volatile String accessTokenKey;

	public WxMiniAppRedisConfigStorage(RedisTemplate<String, String> redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	@Override
	public void setAppid(String appId) {
		super.setAppid(appId);
		this.accessTokenKey = ACCESS_TOKEN_KEY.concat(appId);
	}

	// token相关
	@Override
	public String getAccessToken() {
		return redisTemplate.opsForValue().get(this.accessTokenKey);
	}

	@Override
	public boolean isAccessTokenExpired() {
		return redisTemplate.getExpire(accessTokenKey) < 2L;
	}

	@Override
	public synchronized void updateAccessToken(String accessToken, int expiresInSeconds) {
		redisTemplate.opsForValue().set(accessTokenKey, accessToken, expiresInSeconds - 200, TimeUnit.SECONDS);
	}

	@Override
	public void expireAccessToken() {
		redisTemplate.expire(this.accessTokenKey, 0, TimeUnit.SECONDS);
	}

}
