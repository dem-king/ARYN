
package com.aryn.cloud.product.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.vo.GoodsCatalogSummaryVO;

import java.util.List;
import java.util.Map;

/**
 * 商品spu
 *
 * @author 雨滴kian
 * @since 2022/2/22 15:03
 */
public interface IGoodsSpuService extends IService<GoodsSpu> {

	/**
	 * 分页查询商品
	 * @param page page
	 * @param goodsSpu goodsSpu
	 * @return IPage<GoodsSpu>
	 */
	IPage<GoodsSpu> adminPage(Page page, GoodsSpu goodsSpu);

	/**
	 * 统一商品目录分页（C 端场景选货页/搜索共用）。
	 *
	 * <p>船供资料已下线（2026-09-29），原船供摘要分页由本方法承接。
	 * @param tenantId 租户
	 * @param page 分页
	 * @param keyword 关键词（匹配名称/子标题）
	 * @param status 商品状态过滤
	 * @return 目录行摘要分页
	 */
	IPage<GoodsCatalogSummaryVO> catalogPage(String tenantId, Page<GoodsCatalogSummaryVO> page, String keyword,
			String status);

	/**
	 * 分页查询商品库列表
	 * @param page page
	 * @param goodsSpu goodsSpu
	 * @return IPage<GoodsSpu>
	 */
	IPage<GoodsSpu> warehousePage(Page page, GoodsSpu goodsSpu);

	/**
	 * 新增商品
	 * @param goodsSpu
	 * @author 雨滴kian
	 * @date 2022/6/16
	 * @return: boolean
	 */
	boolean saveGoods(GoodsSpu goodsSpu);

	/**
	 * 通过ID查询商品
	 * @param id
	 * @author 雨滴kian
	 * @date 2022/6/16
	 * @return: com.aryn.cloud.mall.common.entity.GoodsSpu
	 */
	GoodsSpu getSpuById(String id);

	/**
	 * 修改商品
	 * @param goodsSpu
	 * @author 雨滴kian
	 * @date 2022/6/16
	 * @return: boolean
	 */
	boolean updateGoods(GoodsSpu goodsSpu);

	/**
	 * 分页查询商品
	 * @param page page
	 * @param goodsSpu goodsSpu
	 * @return IPage<GoodsSpu>
	 */
	IPage<GoodsSpu> apiPage(Page page, GoodsSpu goodsSpu);

	/**
	 * 商品详情
	 * @param id
	 * @author 雨滴kian
	 * @date 2022/6/6
	 * @return: com.aryn.cloud.mall.common.entity.GoodsSpu
	 */
	GoodsSpu getApiSpuById(String id);

	/**
	 * C 端按 ID 批量查询在售商品（装修楼层、分类页用）。
	 * <p>仅返回上架商品；出参已脱敏成本价，管理端读写成本价走 admin 系列方法，不受影响。
	 * @param ids 商品 SPU ID 列表
	 * @return 在售商品列表（costPrice 已置空）
	 */
	List<GoodsSpu> apiListByIds(List<String> ids);

	/**
	 * 更新销量
	 * @param spuId
	 * @param buyQuantity
	 * @return
	 */
	boolean updateSalesVolume(String spuId, Integer buyQuantity);

	/**
	 * 扣减库存
	 * @param result
	 * @return
	 */
	boolean reduceStock(Map<String, Integer> result);

	/**
	 * 回滚库存
	 * @param result
	 */
	boolean rollbackStock(Map<String, Integer> result);

	/**
	 * 获取热搜商品 Top10
	 * @return
	 */
	List<GoodsSpu> getTop10HotSearchGoods();

}
