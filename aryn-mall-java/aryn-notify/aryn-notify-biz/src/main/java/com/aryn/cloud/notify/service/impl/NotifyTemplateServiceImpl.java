
package com.aryn.cloud.notify.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.notify.api.dto.NotifyTemplateDTO;
import com.aryn.cloud.notify.api.entity.NotifyTemplate;
import com.aryn.cloud.notify.mapper.NotifyTemplateMapper;
import com.aryn.cloud.notify.service.INotifyTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

/**
 * 消息模板 Service 实现
 *
 * @author aryn
 * @since 2026/07/05
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotifyTemplateServiceImpl implements INotifyTemplateService {

	private final NotifyTemplateMapper notifyTemplateMapper;

	@Override
	public IPage<NotifyTemplate> page(Page<NotifyTemplate> page, NotifyTemplate template) {
		LambdaQueryWrapper<NotifyTemplate> wrapper = new LambdaQueryWrapper<NotifyTemplate>()
			.like(StrUtil.isNotBlank(template.getTemplateCode()), NotifyTemplate::getTemplateCode,
					template.getTemplateCode())
			.like(StrUtil.isNotBlank(template.getTemplateName()), NotifyTemplate::getTemplateName,
					template.getTemplateName())
			.eq(template.getNotifyType() != null, NotifyTemplate::getNotifyType, template.getNotifyType())
			.eq(StrUtil.isNotBlank(template.getStatus()), NotifyTemplate::getStatus, template.getStatus())
			.orderByDesc(NotifyTemplate::getCreateTime);
		return notifyTemplateMapper.selectPage(page, wrapper);
	}

	@Override
	public NotifyTemplate getById(String id) {
		return notifyTemplateMapper.selectById(id);
	}

	@Override
	public void save(NotifyTemplateDTO dto) {
		// 校验 templateCode 唯一
		NotifyTemplate exists = notifyTemplateMapper.selectByCode(dto.getTemplateCode());
		if (exists != null) {
			throw new ArynBusinessException("模板编码已存在: " + dto.getTemplateCode());
		}
		NotifyTemplate template = new NotifyTemplate();
		BeanUtils.copyProperties(dto, template);
		if (StrUtil.isBlank(template.getStatus())) {
			template.setStatus("1");
		}
		notifyTemplateMapper.insert(template);
	}

	@Override
	public void update(NotifyTemplateDTO dto) {
		if (StrUtil.isBlank(dto.getId())) {
			throw new ArynBusinessException("模板ID不能为空");
		}
		NotifyTemplate template = new NotifyTemplate();
		BeanUtils.copyProperties(dto, template);
		notifyTemplateMapper.updateById(template);
	}

	@Override
	public void delete(String id) {
		notifyTemplateMapper.deleteById(id);
	}

}
