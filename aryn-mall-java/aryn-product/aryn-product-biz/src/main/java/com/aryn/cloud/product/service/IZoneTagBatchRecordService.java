package com.aryn.cloud.product.service;

import com.aryn.cloud.product.api.entity.GoodsZoneTagBatch;
import com.aryn.cloud.product.api.entity.GoodsZoneTagBatchItem;
import com.aryn.cloud.product.api.vo.ZoneTagBatchDetailVO;
import com.aryn.cloud.product.api.vo.ZoneTagBatchVO;
import com.aryn.cloud.product.api.vo.ZoneTagUndoResultVO;
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

/**
 * 专区标签批量操作记录（批次头 + 明细 + 撤销）。
 *
 * <p>批次由 {@link IGoodsZoneTagBatchService} 在执行完成后写入；撤销按
 * 明细中的 before/after 快照回滚，只回滚当前值仍等于批次 after 的条目。
 *
 * @author aryn
 * @since 2026/11/04
 */
public interface IZoneTagBatchRecordService {

	/**
	 * 保存批次头与明细（调用方保证在同一事务内）。
	 */
	void saveBatch(GoodsZoneTagBatch batch, List<GoodsZoneTagBatchItem> items);

	/**
	 * 回写系统活动同步结果与撤销状态等批次头字段。
	 */
	void updateSyncStatus(String batchId, String syncStatus, String syncMessage);

	/**
	 * 管理端批次分页（按创建时间倒序）。
	 */
	IPage<ZoneTagBatchVO> page(long current, long size);

	/**
	 * 批次详情（头 + 全部明细）。
	 */
	ZoneTagBatchDetailVO detail(String batchId);

	/**
	 * 撤销批次：按 before 快照回滚，冲突项跳过。
	 */
	ZoneTagUndoResultVO undo(String batchId);

}
