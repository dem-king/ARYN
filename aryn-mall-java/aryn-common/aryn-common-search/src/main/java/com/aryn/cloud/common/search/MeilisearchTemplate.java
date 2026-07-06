/*
 * Copyright (c) 2025 天启雨数科技有限公司
 * All rights reserved.
 * 注意：
 * 本项目源代码由天启雨数科技有限公司原创开发，版权所有。
 */
package com.aryn.cloud.common.search;

import java.util.List;

import com.alibaba.fastjson2.JSON;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.SearchRequest;
import com.meilisearch.sdk.exceptions.MeilisearchException;
import com.meilisearch.sdk.model.Searchable;
import com.meilisearch.sdk.model.TaskInfo;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

/**
 * Meilisearch 操作模板类
 * <p>
 * 封装 Meilisearch Client 常用操作，提供索引管理、文档增删查等能力。
 * </p>
 *
 * @author aryn
 */
@Slf4j
@Component
public class MeilisearchTemplate {

	private final Client client;

	public MeilisearchTemplate(Client client) {
		this.client = client;
	}

	/**
	 * 创建或更新索引
	 * @param indexUid 索引唯一标识
	 * @param primaryKey 主键字段名
	 * @return 任务信息
	 */
	public TaskInfo createOrUpdateIndex(String indexUid, String primaryKey) {
		try {
			log.info("[Meilisearch] 创建/更新索引: indexUid={}, primaryKey={}", indexUid, primaryKey);
			TaskInfo taskInfo = client.createIndex(indexUid, primaryKey);
			log.info("[Meilisearch] 索引创建任务已提交: taskUid={}", taskInfo.getTaskUid());
			return taskInfo;
		}
		catch (MeilisearchException ex) {
			log.error("[Meilisearch] 创建索引失败: indexUid={}", indexUid, ex);
			throw new RuntimeException("创建 Meilisearch 索引失败: " + indexUid, ex);
		}
	}

	/**
	 * 添加或更新文档
	 * @param indexUid 索引唯一标识
	 * @param documents 文档列表
	 * @param primaryKey 主键字段名
	 * @param <T> 文档类型
	 * @return 任务信息
	 */
	public <T> TaskInfo addDocuments(String indexUid, List<T> documents, String primaryKey) {
		try {
			log.info("[Meilisearch] 添加文档: indexUid={}, count={}", indexUid, documents.size());
			Index index = client.getIndex(indexUid);
			String json = JSON.toJSONString(documents);
			TaskInfo taskInfo = index.addDocuments(json, primaryKey);
			log.info("[Meilisearch] 文档添加任务已提交: taskUid={}", taskInfo.getTaskUid());
			return taskInfo;
		}
		catch (MeilisearchException ex) {
			log.error("[Meilisearch] 添加文档失败: indexUid={}", indexUid, ex);
			throw new RuntimeException("添加 Meilisearch 文档失败: " + indexUid, ex);
		}
	}

	/**
	 * 删除单个文档
	 * @param indexUid 索引唯一标识
	 * @param documentId 文档主键值
	 * @return 任务信息
	 */
	public TaskInfo deleteDocument(String indexUid, String documentId) {
		try {
			log.info("[Meilisearch] 删除文档: indexUid={}, documentId={}", indexUid, documentId);
			Index index = client.getIndex(indexUid);
			TaskInfo taskInfo = index.deleteDocument(documentId);
			log.info("[Meilisearch] 文档删除任务已提交: taskUid={}", taskInfo.getTaskUid());
			return taskInfo;
		}
		catch (MeilisearchException ex) {
			log.error("[Meilisearch] 删除文档失败: indexUid={}, documentId={}", indexUid, documentId, ex);
			throw new RuntimeException("删除 Meilisearch 文档失败: " + indexUid, ex);
		}
	}

	/**
	 * 搜索文档
	 * @param indexUid 索引唯一标识
	 * @param query 搜索关键词
	 * @param searchRequest 搜索参数（可为 null，表示仅关键词搜索）
	 * @return 搜索结果
	 */
	public Searchable search(String indexUid, String query, SearchRequest searchRequest) {
		try {
			log.info("[Meilisearch] 执行搜索: indexUid={}, query={}", indexUid, query);
			Index index = client.getIndex(indexUid);
			SearchRequest request = searchRequest;
			if (request == null) {
				request = SearchRequest.builder().q(query).build();
			}
			Searchable result = index.search(request);
			log.info("[Meilisearch] 搜索完成: indexUid={}, hitsCount={}", indexUid,
					result.getHits() != null ? result.getHits().size() : 0);
			return result;
		}
		catch (MeilisearchException ex) {
			log.error("[Meilisearch] 搜索失败: indexUid={}, query={}", indexUid, query, ex);
			throw new RuntimeException("Meilisearch 搜索失败: " + indexUid, ex);
		}
	}

	/**
	 * 删除索引
	 * @param indexUid 索引唯一标识
	 * @return 任务信息
	 */
	public TaskInfo deleteIndex(String indexUid) {
		try {
			log.info("[Meilisearch] 删除索引: indexUid={}", indexUid);
			TaskInfo taskInfo = client.deleteIndex(indexUid);
			log.info("[Meilisearch] 索引删除任务已提交: taskUid={}", taskInfo.getTaskUid());
			return taskInfo;
		}
		catch (MeilisearchException ex) {
			log.error("[Meilisearch] 删除索引失败: indexUid={}", indexUid, ex);
			throw new RuntimeException("删除 Meilisearch 索引失败: " + indexUid, ex);
		}
	}

}