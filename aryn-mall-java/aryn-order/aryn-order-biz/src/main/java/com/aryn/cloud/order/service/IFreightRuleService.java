package com.aryn.cloud.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.order.api.entity.FreightRule;

import java.util.List;

/**
 * 运费规则服务
 *
 * @author aryn
 * @since 2026/11/12
 */
public interface IFreightRuleService extends IService<FreightRule> {

	/**
	 * 匹配当前订单可用的运费规则。
	 *
	 * <p>匹配顺序：场景（精确优先于「全部」）→ 配送方式（精确优先于「全部」）→
	 * 省份（指定省份优先于全国）→ priority 数值大者优先。
	 * 无命中返回 null，表示「未配置规则 = 不收运费」，不阻断下单。
	 *
	 * @param bizScene 购买场景：1海员个人/2船供采购/3企业采购
	 * @param deliveryWay 配送方式：1普通快递/3公司配送（岸上）
	 * @param provinceCode 收货地址省编码（可为空）
	 * @return 命中的规则；无则 null
	 */
	FreightRule match(String bizScene, String deliveryWay, String provinceCode);

	/**
	 * 启用中的规则列表（管理端下拉/排查用）
	 */
	List<FreightRule> listEnabled();

	/**
	 * 新增规则（含合法性校验）
	 */
	boolean saveRule(FreightRule rule);

	/**
	 * 修改规则（含合法性校验）
	 */
	boolean updateRule(FreightRule rule);

}
