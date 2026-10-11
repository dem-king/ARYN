
package com.aryn.cloud.product.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.vo.GoodsCatalogSummaryVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.io.Serializable;
import java.util.List;

/**
 * 商品spu
 *
 * @author 雨滴kian
 * @since 2022/2/22 14:31
 */
@Mapper
public interface GoodsSpuMapper extends BaseMapper<GoodsSpu> {

	/**
	 * 分页查询商品
	 *
	 * @author 雨滴kian
	 * @date 2022/6/15
	 * @param page
	 * @param goodsSpu
	 * @return: com.baomidou.mybatisplus.core.metadata.IPage<com.aryn.cloud.mall.common.entity.GoodsSpu>
	 */
	IPage<GoodsSpu> selectPageByAdmin(Page page, @Param("query") GoodsSpu goodsSpu);

	/**
	 * 分页查询商品库列表
	 *
	 * @author 雨滴kian
	 * @date 2022/6/15
	 * @param page
	 * @param goodsSpu
	 * @return: com.baomidou.mybatisplus.core.metadata.IPage<com.aryn.cloud.mall.common.entity.GoodsSpu>
	 */
	IPage<GoodsSpu> selectPageWarehouse(Page page, @Param("query") GoodsSpu goodsSpu);

	/**
	 * 商品详情
	 *
	 * @author 雨滴kian
	 * @date 2022/6/15
	 * @param id
	 * @return: com.aryn.cloud.mall.common.entity.GoodsSpu
	 */
	GoodsSpu selectSpuById(Serializable id);

	/**
	 * 分页查询商品列表
	 *
	 * @author 雨滴kian
	 * @date 2022/6/6
	 * @param page
	 * @param goodsSpu
	 * @return: com.baomidou.mybatisplus.core.metadata.IPage<com.aryn.cloud.mall.common.entity.GoodsSpu>
	 */
	IPage<GoodsSpu> selectApiPage(Page page, @Param("query") GoodsSpu goodsSpu);

	/**
	 * 购物车查询商品
	 *
	 * @author 雨滴kian
	 * @date 2022/6/6
	 * @param id
	 * @return: com.aryn.cloud.mall.common.entity.GoodsSpu
	 */
	GoodsSpu selectSpuByShoppingCart(Serializable id);

	/**
	 * 商品详情
	 *
	 * @author 雨滴kian
	 * @date 2022/6/6
	 * @param id
	 * @return: com.aryn.cloud.mall.common.entity.GoodsSpu
	 */
	GoodsSpu selectApiSpuById(Serializable id);

	/**
	 * 获取热搜商品 Top10
	 * @return
	 */
	List<GoodsSpu> getTop10HotSearchGoods();

	/**
	 * 统一商品目录分页（C 端场景选货页/搜索共用）。
	 *
	 * <p>船供资料三表已下线，目录只查 goods_spu + goods_sku；
	 * 历史上的船供摘要分页（已随船供资料下线删除）由本查询承接。
	 *
	 * <p>必须跳过租户拦截器：SQL 里的 specs_info 用了 JSON_TABLE 派生表，
	 * 项目内置的 JSqlParser 5.2 解析不了（报 "Failed to process, Error SQL"），
	 * 而租户隔离已由 SQL 显式保证（spu.tenant_id = #{tenantId}、
	 * sku.tenant_id = spu.tenant_id），不能依赖拦截器注入。
	 * @param tenantId 租户
	 * @param page 分页
	 * @param keyword 关键词（匹配商品名称/子标题）
	 * @param status 商品状态过滤（C 端固定传 1）
	 * @return 目录行摘要分页
	 */
	@InterceptorIgnore(tenantLine = "true")
	IPage<GoodsCatalogSummaryVO> selectCatalogPage(Page<GoodsCatalogSummaryVO> page, @Param("tenantId") String tenantId,
			@Param("keyword") String keyword, @Param("status") String status);

	/**
	 * 搜索联想候选（在售商品名，按销量倒序取候选，精排去重在服务层）。
	 *
	 * <p>租户显式传参（与 selectCatalogPage 同模式，不依赖拦截器，显式跳过租户注入）；
	 * keyword 必须先经服务层 {@code escapeLikeKeyword} 转义，否则 %/_ 会被
	 * 当通配符，用户输入 % 即可拖全租户扫描。
	 * @param tenantId 租户
	 * @param keyword 已转义关键词
	 * @param limit 候选上限
	 * @return 商品名候选（可能重名，由服务层去重）
	 */
	@InterceptorIgnore(tenantLine = "true")
	List<String> selectSuggestNames(@Param("tenantId") String tenantId, @Param("keyword") String keyword,
			@Param("limit") int limit);

}
