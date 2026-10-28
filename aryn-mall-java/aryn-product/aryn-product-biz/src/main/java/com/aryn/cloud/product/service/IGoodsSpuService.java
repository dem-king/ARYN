
package com.aryn.cloud.product.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.vo.GoodsCatalogSummaryVO;

import java.math.BigDecimal;
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
	 * 分页查询商品（C 端「低价捡漏」排序）。
	 *
	 * <p>携带 bargainRate 时：追加原价门槛过滤原价 &gt;= 售价 × bargainRate，
	 * 按折扣率降序固定排序并忽略 asc/desc（实现内清空 Page.orders）；bargainRate 为空时
	 * 行为与 {@link #apiPage(Page, GoodsSpu)} 完全一致（存量零影响）。
	 * @param page page
	 * @param goodsSpu goodsSpu
	 * @param bargainRate 折扣率阈值，允许区间 (1.00, 9.99]，null＝不启用捡漏过滤/排序
	 * @return IPage<GoodsSpu>
	 * @throws com.aryn.cloud.common.security.handler.ArynBusinessException bargainRate 越界时抛出
	 */
	IPage<GoodsSpu> apiPage(Page page, GoodsSpu goodsSpu, BigDecimal bargainRate);

	/**
	 * 推荐流分页（商详/购物车/个人中心/订单页共用）。
	 *
	 * <p>排除 {@code spuId} 与 {@code excludeIds}，优先展示与上下文商品同二级分类、
	 * 再同一级分类的商品，其余按销量降序、上架时间降序兜底；只返回当前租户在售商品，
	 * 出参已脱敏成本价。无显式上下文时，登录用户按近期浏览足迹的品类个性化，
	 * 未登录则退化为全店排序。
	 *
	 * <p>推荐流为无限滚动，服务层关闭 count 查询（前端只按返回条数判断是否还有下一页）。
	 *
	 * @param page 分页
	 * @param spuId 当前商品 SPU ID（可空）
	 * @param sourceSpuIds 提供分类上下文的一组商品 ID（购物车/订单页，可空）
	 * @param excludeIds 额外排除的商品 ID（购物车/订单页，可空）
	 * @return IPage<GoodsSpu>
	 */
	IPage<GoodsSpu> recommendPage(Page page, String spuId, List<String> sourceSpuIds, List<String> excludeIds);

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
	 * 搜索联想词（C 端搜索页「猜你想搜」）。
	 *
	 * <p>词源=当前租户在售商品名，与结果页（selectApiPage）同口径只匹配名称，
	 * 前缀命中优先于包含命中，忽略大小写去重后最多 10 条。出参只含名称，
	 * 不回 ID/价格（最窄白名单）。输入无效（空/超长）或租户缺失时返回空列表，
	 * 不抛异常——联想是加速项，任何异常都不应打断搜索主流程。
	 * @param tenantId 租户（缺失时 fail-closed 返回空）
	 * @param keyword 用户输入
	 * @return 联想词列表
	 */
	List<String> suggestNames(String tenantId, String keyword);

}
