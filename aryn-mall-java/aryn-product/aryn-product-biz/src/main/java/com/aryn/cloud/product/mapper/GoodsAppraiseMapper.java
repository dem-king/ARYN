
package com.aryn.cloud.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.product.api.entity.GoodsAppraise;
import com.aryn.cloud.product.api.vo.AppraiseCountVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 商品评价
 *
 * @author 雨滴kian
 * @since 2022/3/10 17:09
 */
@Mapper
public interface GoodsAppraiseMapper extends BaseMapper<GoodsAppraise> {

	/**
	 * 评论数量查询
	 * @param goodsAppraise
	 * @return
	 */
	AppraiseCountVO selectGoodsAppraiseCount(@Param("query") GoodsAppraise goodsAppraise);

	IPage<GoodsAppraise> selectAppraisePage(Page page, @Param("query") GoodsAppraise goodsAppraise);

	/**
	 * 商详口碑气泡买家头像：该 SPU 近期非匿名、非系统默认评价的头像快照（去重），
	 * 不足返回实际条数；与评价列表同一公开口径（匿名/系统评价不入列）。
	 * @param spuId 商品 SPU ID
	 * @param limit 最多返回条数（≤3）
	 */
	List<String> selectRecentBuyerAvatars(@Param("spuId") String spuId, @Param("limit") int limit);

}
