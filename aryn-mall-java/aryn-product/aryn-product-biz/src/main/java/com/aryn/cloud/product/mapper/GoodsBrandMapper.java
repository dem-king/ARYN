package com.aryn.cloud.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aryn.cloud.product.api.entity.GoodsBrand;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.vo.GoodsBrandFilterVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GoodsBrandMapper extends BaseMapper<GoodsBrand> {

	/**
	 * 按查询条件聚合出「实际有在售商品」的品牌（C 端品牌筛选项）。
	 *
	 * <p>条件语义必须与 {@code GoodsSpuMapper.selectApiPage} 保持一致，否则品牌条上的
	 * 计数会与点进去的列表条数不符：同一批条件（分类 / 关键词）在两边必须选中同一集合。
	 *
	 * <p>返回的品牌恒有 1 件以上商品 —— 计数为 0 的品牌（点了必然为空的「死选项」）
	 * 由 SQL 的 INNER JOIN 直接排除，不依赖前端过滤。
	 *
	 * @param query 查询条件，字段取自 GoodsSpu：categoryFirstId / categorySecondId / name
	 * @return 有商品的品牌 + 各自商品数
	 */
	List<GoodsBrandFilterVO> selectFilterList(@Param("query") GoodsSpu query);

}
