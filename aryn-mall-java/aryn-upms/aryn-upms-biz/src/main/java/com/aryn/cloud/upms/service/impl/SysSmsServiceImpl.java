
package com.aryn.cloud.upms.service.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.aryn.cloud.common.core.constant.CacheConstants;
import com.aryn.cloud.common.sms.util.AliSmsUtils;
import com.aryn.cloud.upms.service.ISysSmsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
/**
 * 短信
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:47
 */
@Service
@RequiredArgsConstructor
public class SysSmsServiceImpl implements ISysSmsService {

	private final StringRedisTemplate redisTemplate;

	private final AliSmsUtils aliSmsUtils;

	@Override
	public Boolean sendSmsCode(String mobile, String type) {
		String code = RandomUtil.randomNumbers(4);
		switch (type) {
			case "register":
				// 发送注册验证码
				break;
			case "login":
			case "update":
				// 发送登录验证码
				// 发送忘记密码验证码
				String codeStr = redisTemplate.opsForValue().get(CacheConstants.SMS_CODE_KEY + mobile);
				if (StringUtils.hasText(codeStr)) {
					// 验证码未过期
					return Boolean.TRUE;
				}
				Map<String, String> maps = new HashMap<>();
				maps.put("code", code);
				try {
					aliSmsUtils.sendSms(mobile, JSONUtil.toJsonStr(maps));
				}
				catch (Exception e) {
					throw new RuntimeException(e.getMessage());
				}
				// 保存验证码信息
				redisTemplate.opsForValue().set(CacheConstants.SMS_CODE_KEY + mobile, code, 300, TimeUnit.SECONDS);
				break;
			case "forget":
				break;
			default:
		}
		return Boolean.TRUE;
	}

}
