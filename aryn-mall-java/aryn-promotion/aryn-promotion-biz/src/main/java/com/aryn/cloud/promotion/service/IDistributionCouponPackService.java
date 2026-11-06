package com.aryn.cloud.promotion.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.promotion.api.dto.DistributionCouponPackSaveDTO;
import com.aryn.cloud.promotion.api.entity.DistributionCouponPack;
import com.aryn.cloud.promotion.api.vo.DistributionCouponPackVO;

import java.util.List;

/**
 * 分销佣金兑换券包
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
public interface IDistributionCouponPackService {

	/**
	 * 管理端分页
	 * @param page 分页参数
	 * @param query 查询条件（packName 模糊、status 精确）
	 * @return 券包视图分页（含关联券模板）
	 */
	IPage<DistributionCouponPackVO> getPage(Page<DistributionCouponPack> page, DistributionCouponPack query);

	/**
	 * 全部券包（下拉/选择模板用）
	 * @return 券包视图列表（含关联券模板）
	 */
	List<DistributionCouponPackVO> listAll();

	/**
	 * 券包详情
	 * @param id 券包ID
	 * @return 券包视图（含关联券模板）
	 */
	DistributionCouponPackVO getDetail(String id);

	/**
	 * 新增券包
	 * @param dto 券包配置
	 * @return 是否成功
	 */
	Boolean savePack(DistributionCouponPackSaveDTO dto);

	/**
	 * 编辑券包
	 * @param dto 券包配置（必须带 id）
	 * @return 是否成功
	 */
	Boolean updatePack(DistributionCouponPackSaveDTO dto);

	/**
	 * 上架/下架
	 * @param id 券包ID
	 * @param status 0 上架 / 1 下架
	 * @return 是否成功
	 */
	Boolean updateStatus(String id, String status);

	/**
	 * 逻辑删除券包及其明细
	 * @param id 券包ID
	 * @return 是否成功
	 */
	Boolean removePack(String id);

	/**
	 * 小程序可兑换券包列表：仅上架且（库存不限或库存 > 0）
	 * @return 券包视图列表（含关联券模板）
	 */
	List<DistributionCouponPackVO> listOnShelf();

}
