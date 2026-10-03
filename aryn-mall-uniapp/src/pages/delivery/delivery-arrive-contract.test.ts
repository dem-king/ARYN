import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * 「送达必须带凭证」的前后端契约守门。
 *
 * 真实缺陷：出车单详情页的「已送达」直接无参调用 `arriveTask(task.id)`，而后端
 * `arriveWithEvidence` 强制要求 1–6 张凭证，一律抛
 * 「送达凭证图片数量必须为1至6张」。前端 catch 里读的是 `error?.msg`，
 * 而拦截器抛出的 `ApiError` 只带 `message`，于是后端文案被兜底成「操作失败」——
 * 司机在界面上只看到「操作失败」，无从知道要补照片。
 *
 * 这类漏网方式：类型检查、单测、编译全绿（`materialIds` 是可选参数），
 * 只有真机点一次才暴露。所以把「谁在什么状态下必须传凭证」钉成静态断言。
 */
const projectRoot = fileURLToPath(new URL('../../..', import.meta.url))
const repoRoot = resolve(projectRoot, '..')

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

function repoSource(relativePath: string) {
  return readFileSync(resolve(repoRoot, relativePath), 'utf8')
}

const TASK_SERVICE = 'aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/service/impl/DeliveryTaskServiceImpl.java'

describe('delivery arrive evidence contract', () => {
  it('后端要求送达凭证为 1 至 6 张（前端据此必须先收图）', () => {
    const service = repoSource(TASK_SERVICE)
    expect(service).toContain('送达凭证图片数量必须为1至6张')
    // 数量校验必须在写状态之前：先改状态再拒绝会让任务卡在半途
    const validationIndex = service.indexOf('送达凭证图片数量必须为1至6张')
    const updateIndex = service.indexOf('DeliveryTaskStatusEnum.ARRIVED.getCode()')
    expect(validationIndex).toBeGreaterThan(-1)
    expect(validationIndex).toBeLessThan(updateIndex)
  })

  it('出车单页的「已送达」不再裸调用，改为先收凭证再提交', () => {
    const page = source('src/pages/delivery/trip-detail.vue')

    // 入口改为打开凭证弹层，提交动作必须把 materialIds 交给接口
    expect(page).toContain('function handleArriveTask(')
    expect(page).toContain('evidenceVisible.value = true')
    expect(page).toMatch(/arriveTask\(task\.id,\s*evidenceIds\.value\)/)
    // 裸调用形态（只有一个参数）不得复现
    expect(page).not.toMatch(/arriveTask\(task\.id\)/)
    expect(page).toContain('uploadDeliveryEvidence')
    expect(page).toContain('请先上传至少一张送达凭证')
  })

  it('只有「待送达」状态才展示已送达按钮，避免必然失败的操作入口', () => {
    const page = source('src/pages/delivery/trip-detail.vue')
    const enumSource = repoSource(
      'aryn-mall-java/aryn-order/aryn-order-api/src/main/java/com/aryn/cloud/order/api/enums/DeliveryTaskStatusEnum.java',
    )

    // 后端只允许 status=4（待送达）流转到已送达
    expect(enumSource).toContain('WAITING_ARRIVE("4", "待送达")')
    const arriveCall = page.match(/<wd-button[^>]*@click\.stop="handleArriveTask\(task\)"[\s\S]*?已送达/)
    expect(arriveCall, '未找到「已送达」按钮').toBeTruthy()
    expect(arriveCall?.[0]).toContain('v-if="task.status === \'4\'"')
  })

  it('错误文案取 message，不再用恒为 undefined 的 msg 盖掉后端原因', () => {
    const pages = [
      source('src/pages/delivery/trip-detail.vue'),
      source('src/pages/delivery/task-detail.vue'),
    ]
    for (const page of pages) {
      // ApiError 继承 Error，只有 message；读 msg 会把真实原因吞成兜底文案
      expect(page).not.toMatch(/showToast\(error\?\.msg \|\|/)
      expect(page).toMatch(/showToast\(error\?\.message \|\| error\?\.msg \|\|/)
    }
  })

  it('凭证上传走配送员专用接口（C 端 /app/upload 不接受 delivery token）', () => {
    const fileApi = source('src/api/upms/file.ts')
    expect(fileApi).toContain('\'/upms/file/staff/delivery-evidence/upload\'')
    expect(fileApi).toContain('Local.get(\'deliveryToken\')')
  })

  it('出车单收车口径：任务结清即完成，不再等客户签收', () => {
    const tripService = repoSource(
      'aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/service/impl/DeliveryTripServiceImpl.java',
    )
    const taskService = repoSource(TASK_SERVICE)

    // 结清口径必须包含「已送达」——原实现只在全部签收时收车，
    // 客户不点确认收货，司机的出车单就永远停在配送中
    expect(tripService).toContain('completeIfAllTasksSettled')
    expect(tripService).toMatch(/SETTLED_TASK_STATUSES\s*=\s*List\.of\([\s\S]*?ARRIVED/)
    // 送达路径必须触发收车判定，否则「已送达」后出车单仍挂配送中
    const arriveIndex = taskService.indexOf('"ARRIVE", DeliveryTaskStatusEnum.WAITING_ARRIVE.getCode()')
    expect(arriveIndex).toBeGreaterThan(-1)
    expect(taskService.slice(arriveIndex)).toContain('completeIfAllTasksSettled')
  })

  it('已完成的出车单详情仍展示送达清单，不退回空白卡', () => {
    const page = source('src/pages/delivery/trip-detail.vue')
    // status=4 已完成后走送货视图，保留「全部配送完成」与完成时间
    expect(page).toMatch(/isDeliverView\s*=\s*computed\(\(\)\s*=>\s*trip\.value\?\.status === '3' \|\| trip\.value\?\.status === '4'\)/)
    expect(page).toContain('出车单已完成')
  })

  it('任务详情在送达后回显已上传凭证（渲染 materialUrl 快照）', () => {
    const page = source('src/pages/delivery/task-detail.vue')
    const api = source('src/api/delivery.ts')

    // 送达确认后（不再是待送达）必须从后端拉取凭证回显，否则司机看不到自己传过的照片
    expect(page).toContain('getTaskEvidence')
    expect(page).toMatch(/loadSavedEvidence/)
    expect(page).toMatch(/savedEvidenceUrls/)
    expect(page).toContain('item?.materialUrl')
    // 凭证查询接口走 app 配送域
    expect(api).toMatch(/getTaskEvidence[\s\S]*?\/evidence/)
  })

  it('后端送达落库凭证时快照素材 URL，读取侧对存量空 URL 回填', () => {
    const taskService = repoSource(TASK_SERVICE)

    expect(taskService).toContain('saveEvidenceWithUrl')
    expect(taskService).toContain('backfillEvidenceUrls')
    expect(taskService).toContain('RemoteMaterialService')
  })

  it('买家侧配送进度带回并渲染送达凭证（仅 URL 不含内部 ID）', () => {
    const vo = repoSource(
      'aryn-mall-java/aryn-order/aryn-order-api/src/main/java/com/aryn/cloud/order/api/vo/DeliveryProgressVO.java',
    )
    const api = source('src/api/delivery.ts')
    const component = source('src/components/delivery/delivery-progress.vue')

    // 后端 VO 与前端类型同名字段，C 端复用 delivery-progress 接口零新增请求
    expect(vo).toContain('evidenceUrls')
    expect(api).toMatch(/evidenceUrls\?: string\[\]/)
    expect(component).toMatch(/progress\.evidenceUrls/)
    expect(component).toContain('previewEvidence')
    expect(component).toContain('送达凭证')
  })
})
