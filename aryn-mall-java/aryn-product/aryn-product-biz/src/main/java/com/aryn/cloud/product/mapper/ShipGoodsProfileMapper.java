package com.aryn.cloud.product.mapper;

import com.aryn.cloud.product.api.entity.ShipGoodsProfile;
import com.aryn.cloud.product.api.vo.ShipProductSummaryVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** ShipGoodsProfile 持久层。 */
@Mapper
public interface ShipGoodsProfileMapper extends BaseMapper<ShipGoodsProfile> {

	/**
	 * 船供商品摘要分页（联表一次返回，条件取自 ShipProductSummaryVO 同名字段）。
	 */
	IPage<ShipProductSummaryVO> selectShipSummaryPage(IPage<ShipProductSummaryVO> page, @Param("tenantId") String tenantId,
			@Param("query") ShipProductSummaryVO query);

	/**
	 * 个人商品摘要分页（sale_scope 为 1 或 3）。
	 */
	IPage<ShipProductSummaryVO> selectPersonalSummaryPage(IPage<ShipProductSummaryVO> page,
			@Param("tenantId") String tenantId, @Param("query") ShipProductSummaryVO query);

}
