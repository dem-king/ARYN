package com.aryn.cloud.user.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.user.api.entity.MemberTag;
import com.aryn.cloud.user.api.entity.UserTagRel;
import com.aryn.cloud.user.mapper.MemberTagMapper;
import com.aryn.cloud.user.mapper.UserTagRelMapper;
import com.aryn.cloud.user.mapper.UserInfoMapper;
import com.aryn.cloud.user.api.entity.UserInfo;
import com.aryn.cloud.user.service.IMemberTagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 会员标签
 *
 * @author 雨滴kian
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberTagServiceImpl extends ServiceImpl<MemberTagMapper, MemberTag> implements IMemberTagService {

	private final UserTagRelMapper userTagRelMapper;

	private final UserInfoMapper userInfoMapper;

	@Override
	public IPage<MemberTag> getPage(Page page, MemberTag memberTag) {
		return this.page(page, Wrappers.<MemberTag>lambdaQuery()
				.like(memberTag != null && StrUtil.isNotBlank(memberTag.getTagName()),
						MemberTag::getTagName, memberTag == null ? null : memberTag.getTagName())
				.eq(memberTag != null && StrUtil.isNotBlank(memberTag.getStatus()),
						MemberTag::getStatus, memberTag == null ? null : memberTag.getStatus())
				.orderByAsc(MemberTag::getSortOrder).orderByDesc(MemberTag::getCreateTime));
	}

	@Override
	public boolean saveTag(MemberTag memberTag) {
		validateTag(memberTag);
		clearSystemFields(memberTag);
		// 校验标签名唯一
		long count = this.count(Wrappers.<MemberTag>lambdaQuery().eq(MemberTag::getTagName, memberTag.getTagName()));
		if (count > 0) {
			throw new ArynBusinessException("标签名称已存在");
		}
		return this.save(memberTag);
	}

	@Override
	public boolean updateTag(MemberTag memberTag) {
		validateTag(memberTag);
		clearSystemFields(memberTag);
		// 校验标签名唯一（排除自身）
		long count = this.count(Wrappers.<MemberTag>lambdaQuery()
				.eq(MemberTag::getTagName, memberTag.getTagName())
				.ne(MemberTag::getId, memberTag.getId()));
		if (count > 0) {
			throw new ArynBusinessException("标签名称已存在");
		}
		return this.updateById(memberTag);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean deleteTag(String id) {
		// 删除标签关联关系
		userTagRelMapper.delete(Wrappers.<UserTagRel>lambdaQuery().eq(UserTagRel::getTagId, id));
		return this.removeById(id);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void tagUser(String userId, List<String> tagIds) {
		if (userInfoMapper.selectById(userId) == null) {
			throw new ArynBusinessException("用户不存在");
		}
		Set<String> uniqueTagIds = tagIds == null ? Set.of() : new LinkedHashSet<>(tagIds);
		if (uniqueTagIds.stream().anyMatch(tagId -> tagId == null || tagId.isBlank())) {
			throw new ArynBusinessException("标签ID不能为空");
		}
		if (uniqueTagIds.isEmpty()) {
			return;
		}
		if (this.listByIds(uniqueTagIds).size() != uniqueTagIds.size()) {
			throw new ArynBusinessException("会员标签不存在");
		}
		// 查询已存在的标签关联
		List<UserTagRel> existRels = userTagRelMapper.selectList(
				Wrappers.<UserTagRel>lambdaQuery().eq(UserTagRel::getUserId, userId).in(UserTagRel::getTagId, uniqueTagIds));
		List<String> existTagIds = existRels.stream().map(UserTagRel::getTagId).collect(Collectors.toList());

		// 批量插入不存在的关联
		List<UserTagRel> newRels = new ArrayList<>();
		for (String tagId : uniqueTagIds) {
			if (!existTagIds.contains(tagId)) {
				UserTagRel rel = new UserTagRel();
				// 自定义批量 SQL 不走 MyBatis-Plus 自动填充，主键与审计字段在此显式赋值
				rel.setId(IdWorker.getIdStr());
				rel.setUserId(userId);
				rel.setTagId(tagId);
				rel.setTenantId(ArynTenantContextHolder.getTenantId());
				rel.setCreateBy(currentUsername());
				rel.setCreateTime(LocalDateTime.now());
				newRels.add(rel);
			}
		}
		if (!newRels.isEmpty()) {
			// 并发下「先查后插」会同时通过查重，靠唯一键 + INSERT IGNORE 幂等跳过
			userTagRelMapper.insertIgnoreBatch(newRels);
		}
	}

	/** 取当前登录用户名作为审计字段；定时任务/无登录态场景返回 null */
	private String currentUsername() {
		ArynUser user = SecurityUtils.getUser();
		return user == null ? null : user.getUsername();
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void untagUser(String userId, List<String> tagIds) {
		if (tagIds == null || tagIds.isEmpty()) {
			return;
		}
		userTagRelMapper.delete(
				Wrappers.<UserTagRel>lambdaQuery().eq(UserTagRel::getUserId, userId).in(UserTagRel::getTagId, tagIds));
	}

	@Override
	public List<MemberTag> getUserTags(String userId) {
		List<UserTagRel> rels = userTagRelMapper.selectList(
				Wrappers.<UserTagRel>lambdaQuery().eq(UserTagRel::getUserId, userId));
		if (rels.isEmpty()) {
			return new ArrayList<>();
		}
		List<String> tagIds = rels.stream().map(UserTagRel::getTagId).collect(Collectors.toList());
		return this.listByIds(tagIds);
	}

	private void validateTag(MemberTag memberTag) {
		if (memberTag == null || memberTag.getTagName() == null || memberTag.getTagName().isBlank()) {
			throw new ArynBusinessException("标签名称不能为空");
		}
		if (memberTag.getSortOrder() != null && memberTag.getSortOrder() < 0) {
			throw new ArynBusinessException("标签排序号不能小于0");
		}
		if (memberTag.getStatus() != null && !"0".equals(memberTag.getStatus())
				&& !"1".equals(memberTag.getStatus())) {
			throw new ArynBusinessException("标签状态不合法");
		}
	}

	private void clearSystemFields(MemberTag memberTag) {
		memberTag.setTenantId(null);
		memberTag.setCreateBy(null);
		memberTag.setUpdateBy(null);
		memberTag.setCreateTime(null);
		memberTag.setUpdateTime(null);
		memberTag.setDelFlag(null);
	}

}
