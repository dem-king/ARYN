package com.aryn.cloud.product.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.product.api.entity.GoodsBrand;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.vo.GoodsBrandFilterVO;
import com.aryn.cloud.product.mapper.GoodsBrandMapper;
import com.aryn.cloud.product.service.IGoodsBrandService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GoodsBrandServiceImpl extends ServiceImpl<GoodsBrandMapper, GoodsBrand> implements IGoodsBrandService {

	@Override
	public List<GoodsBrandFilterVO> listFilterOptions(GoodsSpu query) {
		List<GoodsBrandFilterVO> options = baseMapper.selectFilterList(query);
		// 无命中时返回空列表而非 null：调用方（C 端品牌条）据此隐藏整条筛选，
		// 前端也少一处判空分支。
		return options == null ? List.of() : options;
	}

}
