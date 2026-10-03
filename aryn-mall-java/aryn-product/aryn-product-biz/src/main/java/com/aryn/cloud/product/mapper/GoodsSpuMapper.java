
package com.aryn.cloud.product.mapper;

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
	 * @param tenantId 租户
	 * @param page 分页
	 * @param keyword 关键词（匹配商品名称/子标题）
	 * @param status 商品状态过滤（C 端固定传 1）
	 * @return 目录行摘要分页
	 */
	IPage<GoodsCatalogSummaryVO> selectCatalogPage(Page<GoodsCatalogSummaryVO> page, @Param("tenantId") String tenantId,
			@Param("keyword") String keyword, @Param("status") String status);

}
