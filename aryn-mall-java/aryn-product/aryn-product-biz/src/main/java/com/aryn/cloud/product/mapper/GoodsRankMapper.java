package com.aryn.cloud.product.mapper;

import com.aryn.cloud.product.api.vo.AppGoodsRankVO;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * C 端商品榜单 Mapper（热搜榜聚合 + 热销榜当前在售商品回填）。
 *
 * <p>两条查询都用显式 tenant_id 保证租户隔离，不依赖租户拦截器注入：
 * 榜单是 C 端免登接口，租户来自请求头，服务层已做「租户缺失 fail-closed」，
 * SQL 必须原样守住该口径（拦截器在上下文缺失时会注入 tenant_id = NULL，
 * 叠加显式条件反而查不到数据，且不利于单测/任务线程复用场景）。
 *
 * @author aryn
 * @since 2026/10/25
 */
@Mapper
public interface GoodsRankMapper {

	/**
	 * 热搜榜：当前租户近 N 天浏览足迹聚合，仅上架商品，按浏览次数倒序。
	 *
	 * @param tenantId 租户
	 * @param days 统计窗口天数
	 * @param limit 返回条数
	 * @return 榜单条目
	 */
	@InterceptorIgnore(tenantLine = "true")
	List<AppGoodsRankVO> selectHotTop10(@Param("tenantId") String tenantId, @Param("days") int days,
			@Param("limit") int limit);

	/**
	 * 按 spuId 批量查询当前在售商品（status='1'、del_flag='0'、当前租户）。
	 *
	 * <p>热销榜保持订单域名次：调用方按返回结果做 id 映射后按原顺序重排，
	 * 查不到（下架/删除/跨租户）的商品由调用方跳过。
	 *
	 * @param tenantId 租户
	 * @param ids spuId 集合（非空，由调用方保证）
	 * @return 当前在售商品榜单字段
	 */
	@InterceptorIgnore(tenantLine = "true")
	List<AppGoodsRankVO> selectOnSaleByIds(@Param("tenantId") String tenantId, @Param("ids") List<String> ids);

}
