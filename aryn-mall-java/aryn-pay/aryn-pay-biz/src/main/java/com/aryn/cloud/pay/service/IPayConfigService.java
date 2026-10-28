
package com.aryn.cloud.pay.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.pay.api.entity.PayConfig;
import com.aryn.cloud.pay.api.vo.PayConfigVO;

/**
 * 支付配置
 *
 * @author 雨滴kian
 * @date 2022/6/16
 */
public interface IPayConfigService extends IService<PayConfig> {

	/**
	 * 通过appid查询支付配置
	 * @param appId
	 * @return
	 */
	PayConfig getByAppId(String appId);

	/**
	 * 通过支付类型查询
	 * @param payType 支付类型
	 * @param terminalType 终端类型
	 * @return
	 */
	PayConfigVO getConfig(String payType, String terminalType);

	boolean saveConfig(PayConfig payConfig);

	/**
	 * 编辑支付配置：同一租户的「支付类型 + 支付端」必须唯一，排除自身。
	 * @param payConfig 支付配置
	 * @return 是否更新成功
	 */
	boolean updateConfig(PayConfig payConfig);

}
