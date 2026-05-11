
package com.aryn.cloud.product.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.product.api.entity.GoodsAppraise;
import com.aryn.cloud.product.api.vo.AppraiseCountVO;
import com.aryn.cloud.product.mapper.GoodsAppraiseMapper;
import com.aryn.cloud.product.service.IGoodsAppraiseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 商品评价
 *
 * @author 雨滴kian
 * @since 2022/3/7 14:18
 */
@Service
@RequiredArgsConstructor
public class GoodsAppraiseServiceImpl extends ServiceImpl<GoodsAppraiseMapper, GoodsAppraise>
		implements IGoodsAppraiseService {

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean add(List<GoodsAppraise> listGoodsAppraise) {
		return this.saveBatch(listGoodsAppraise);
	}

	@Override
	public AppraiseCountVO getCount(GoodsAppraise goodsAppraise) {
		return baseMapper.selectGoodsAppraiseCount(goodsAppraise);
	}

	@Override
	public IPage<GoodsAppraise> getPage(Page page, GoodsAppraise goodsAppraise) {
		return baseMapper.selectAppraisePage(page, goodsAppraise);
	}

	@Override
	public boolean reply(GoodsAppraise goodsAppraise) {
		if (!StringUtils.hasText(goodsAppraise.getId())) {
			throw new ArynBusinessException("id 不能为空");
		}
		if (!StringUtils.hasText(goodsAppraise.getBusinessReply())) {
			throw new ArynBusinessException("回复内容不能为空");
		}
		GoodsAppraise target = this.getById(goodsAppraise.getId());
		if (Objects.isNull(target)) {
			throw new ArynBusinessException("评价不存在");
		}
		target.setBusinessReply(goodsAppraise.getBusinessReply());
		target.setReplyTime(LocalDateTime.now());
		return this.updateById(target);
	}

	@Override
	public long countNegativeAppraise(LocalDateTime startTime, LocalDateTime endTime) {
		return this.count(Wrappers.<GoodsAppraise>lambdaQuery()
			.le(GoodsAppraise::getGoodsScore, 2)
			.between(startTime != null && endTime != null, GoodsAppraise::getCreateTime, startTime, endTime));
	}

}
