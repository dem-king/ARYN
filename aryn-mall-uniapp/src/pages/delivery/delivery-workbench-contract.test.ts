import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * 「工作台能装下整天多趟活」的前后端契约守门。
 *
 * 真实缺陷：后端 `getActiveTrip` 只返回最新一趟（LIMIT 1），而每次派单都会
 * 新建一支出车单。司机一天被派 5 单（分 5 次派）就有 5 张出车单，工作台却
 * 只显示最后一趟 —— 前面 4 单在首页等于消失，配货时根本不知道有这些单。
 *
 * 修法是两半：后端派单并入司机未出车的在途趟次 + 工作台返回全部在途趟次。
 * 缺任何一半都会退回旧症状，所以两半都钉住。
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
const TRIP_SERVICE = 'aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/service/impl/DeliveryTripServiceImpl.java'
const TRIP_CONTROLLER = 'aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/controller/app/AppDeliveryTripController.java'

describe('delivery workbench multi-trip contract', () => {
  it('派单并入司机当前这趟车（含已出发），而不是每次都新建一趟', () => {
    const service = repoSource(TASK_SERVICE)

    // 一个司机一辆车：待配货/配货中/配送中都要能并入，车开出去不是「停止加单」的理由
    expect(service).toContain('resolveAssignTrip')
    expect(service).toMatch(/resolveAssignTrip[\s\S]*?WAITING_LOAD\.getCode\(\)[\s\S]*?LOADING\.getCode\(\)[\s\S]*?DELIVERING\.getCode\(\)/)
    // 新建出车单只发生在「司机没有在途趟次」的分支里：派单主流程直接复用解析结果
    const assignIndex = service.indexOf('public String assignTasks(')
    const assignBody = service.slice(assignIndex, service.indexOf('private record AssignTripTarget', assignIndex))
    expect(assignBody).toContain('resolveAssignTrip(staff, warehouseAddress, tasks.size())')
    expect(assignBody).not.toContain('new DeliveryTrip()')
    // 任务落点必须与趟次进度对齐（待配货→待取货 / 配货中→配货中 / 配送中→配货中）
    expect(service).toContain('planTaskAttach')
    expect(service).toMatch(/planTaskAttach[\s\S]*?DELIVERING\.getCode\(\)[\s\S]*?PICKING\.getCode\(\)/)
    // 并入已出发的趟次不能提前转待收货：车开出去不代表货已经在车上，
    // 必须由司机配齐后再次出发（depart 只推进「配货中」的任务）
    expect(service).not.toContain('markAttachedTasksShipped')
    expect(service).toContain('reDepartRequired')
  })

  it('工作台返回在途趟次（含每趟按顺序排列的订单）', () => {
    const tripService = repoSource(TRIP_SERVICE)
    const controller = repoSource(TRIP_CONTROLLER)

    expect(tripService).toContain('listActiveTripBriefs')
    // 在途口径 1/2/3，不把已完成的老趟次翻出来（历史遗留多张也全部返回，由司机拉合）
    expect(tripService).toMatch(/listActiveTripBriefs[\s\S]*?WAITING_LOAD\.getCode\(\)[\s\S]*?LOADING\.getCode\(\)[\s\S]*?DELIVERING\.getCode\(\)/)
    // 站点按 sortNo 升序：司机照这个顺序装车与送货
    expect(tripService).toMatch(/orderByAsc\(DeliveryTask::getSortNo\)/)
    // 新接口与旧接口并存：/active 保留给尚未升级的旧版本小程序
    expect(controller).toContain('@GetMapping("/workbench")')
    expect(controller).toContain('@GetMapping("/active")')
  })

  it('工作台统计一次聚合，且「今日已完成」按送达时间当天判定', () => {
    const service = repoSource(TASK_SERVICE)

    expect(service).toContain('countPendingTasks')
    expect(service).toContain('countTodayDoneTasks')
    // 原实现把 status=5/6 的总数当「今日完成」，实际是历史累计
    expect(service).toMatch(/countTodayDoneTasks[\s\S]*?getArriveTime, startOfDay/)
  })

  it('前端工作台用聚合接口渲染趟次列表，不再只取单张出车单', () => {
    const page = source('src/pages/delivery/index.vue')
    const api = source('src/api/delivery.ts')

    expect(api).toContain('getDeliveryWorkbench')
    expect(api).toContain('/trip/workbench')
    expect(page).toContain('getDeliveryWorkbench')
    expect(page).toContain('trips')
    // 每趟卡里要直接列出站点顺序，司机在首页就能知道先送哪家
    expect(page).toContain('送货顺序')
    expect(page).toMatch(/v-for="\(stop, stopIndex\) in trip\.taskList"/)
    // 异常/待退回必须显式标出：这两类没有「已送达」按钮，不标司机就会干等
    expect(page).toContain('异常')
    expect(page).toContain('待退回')
  })

  it('配货视图按「分类 + 商品 + 规格」合并整趟车明细', () => {
    const page = source('src/pages/delivery/trip-detail.vue')

    expect(page).toContain('buildPickRows')
    expect(page).toContain('groupPickRows')
    // 一次勾选命中该货在整趟车的多条明细，必须走批量接口
    expect(page).toContain('batchPickItems')
    // 逐条 pick 在合并视图下会把一次点击放大成几十个请求
    expect(page).not.toMatch(/pickItem\(trip\.value\.id/)
  })

  it('后端批量取货校验明细属于本趟车，跨趟混入整批拒绝', () => {
    const itemService = repoSource(
      'aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/service/impl/DeliveryTaskItemServiceImpl.java',
    )

    expect(itemService).toContain('batchPick')
    expect(itemService).toContain('存在不属于该出车单的取货明细')
    expect(itemService).toContain('出车单当前状态不允许确认取货')
  })

  it('送货顺序提交与调整：整趟排列校验 + 松手落库的排序面板', () => {
    const page = source('src/pages/delivery/trip-detail.vue')
    const tripService = repoSource(TRIP_SERVICE)

    // 后端拒绝部分列表：未提交的任务会留在原序号上，路线静默错乱
    expect(tripService).toContain('送货顺序与当前趟次不一致，请刷新后重试')
    // 前端拖拽松手即保存，并提供上移/下移兜底
    expect(page).toContain('handleSortTouchEnd')
    expect(page).toContain('persistRouteOrder')
    expect(page).toContain('handleMoveUp')
    expect(page).toContain('handleMoveDown')
    // 失败要回滚本地顺序，否则界面与库里路线不一致
    expect(page).toMatch(/catch[\s\S]*?routeOrderIds\.value = previous/)
  })

  it('配送模式双端一致：接口路径首段为微服务域，boot 由 rewriteBootUrl 改写', () => {
    const api = source('src/api/delivery.ts')

    // boot 模式会把 /mall-order 剥成 /boot，cloud 由网关路由到 order 服务
    expect(api).toContain('\'/mall-order/app/delivery\'')
    expect(api).not.toContain('\'/boot/')
  })

  it('配货页三个加单入口都走「订单并入本趟」，司机不手填商品', () => {
    const page = source('src/pages/delivery/trip-detail.vue')
    const api = source('src/api/delivery.ts')

    expect(api).toContain('getPullCandidates')
    expect(api).toContain('pullOrdersIntoTrip')
    expect(api).toContain('/pull-candidates')
    expect(api).toContain('/pull-orders')
    // 两个入口：我的任务 / 未派送订单（后者受租户开关控制）
    expect(page).toContain('openPullPanel(\'MINE\'')
    expect(page).toContain('openPullPanel(\'UNASSIGNED\'')
    expect(page).toContain('我的任务')
    expect(page).toContain('未派送订单')
    // 提交必须带订单ID走接口，不能在前端拼商品
    expect(page).toMatch(/pullOrdersIntoTrip\(trip\.value\.id, orderIds\)/)
  })

  it('司机自助拉单受租户开关约束：关闭时隐藏未派送入口且后端拒绝拉入', () => {
    const page = source('src/pages/delivery/trip-detail.vue')
    const api = source('src/api/delivery.ts')
    const itemService = repoSource(TASK_SERVICE)

    // 后端按 order_config 判定，缺省即放行（配置异常不能把司机彻底堵死）
    expect(itemService).toContain('isDriverSelfPullUnassignedAllowed')
    expect(itemService).toContain('getDriverSelfPullUnassigned')
    // 关掉后空 source 也不再并入未派送池：「临时新增」不能成为绕过开关的后门
    expect(itemService).toMatch(/wantUnassigned[\s\S]{0,200}isDriverSelfPullUnassignedAllowed/)
    // 写入侧逐单校验才是真闸门：候选列表只是 UI 便利
    expect(itemService).toContain('assertPullAllowed')
    expect(itemService).toContain('未开放司机自助拉单')
    // 界面按后端下发的开关隐藏入口
    expect(api).toContain('selfPullUnassignedAllowed')
    expect(page).toContain('canSelfPullUnassigned')
    expect(page).toContain('selfPullUnassignedAllowed')
  })

  it('后端拉单：本司机在途趟次（含已出发）可加单，已收车不可，且不抢别人已接的单', () => {
    const service = repoSource(TASK_SERVICE)
    const page = source('src/pages/delivery/trip-detail.vue')

    expect(service).toContain('pullOrdersIntoTrip')
    // 已收车不再加单；已出发（配送中）可以 —— 货陆续装车、司机回车取货都正常
    expect(service).toContain('出车单已收车，无法追加订单')
    expect(service).not.toContain('出车单已出发，无法追加订单')
    expect(service).toContain('请至少选择一个订单')
    // 未派送候选里排除别人已经接走的单（司机自助拉单不是抢单）
    expect(service).toContain('已被别人接走')
    // 从别的趟次挪过来时要重算/收车原趟次
    expect(service).toMatch(/moveTaskIntoTrip[\s\S]*?completeIfAllTasksSettled\(previousTripId\)/)
    // 配送中的详情页也要有加单入口，否则「再捎一单」根本无从点起
    expect(page).toMatch(/再加一单[\s\S]*?openPullPanel/)
  })

  it('出发可重复：已出发的趟次再点出发只带新并入的单，不重复推进老单', () => {
    const tripService = repoSource(TRIP_SERVICE)

    expect(tripService).toContain('alreadyDeparted')
    // 重复出发时只校验新并入任务的明细，不把整趟重新校验一遍
    expect(tripService).toContain('allPickedByTask')
    // 首次出发才改趟次状态，已出发不再重复改
    expect(tripService).toMatch(/if \(!alreadyDeparted\)[\s\S]*?set\(DeliveryTrip::getStatus, DeliveryTripStatusEnum\.DELIVERING\.getCode\(\)\)/)
  })

  it('拉单必须先看到商品明细：候选行带商品行，勾选后面板可核对', () => {
    const page = source('src/pages/delivery/trip-detail.vue')
    const api = source('src/api/delivery.ts')

    // 候选订单要下发商品摘要（后端 fillCandidateItems 补齐）
    expect(api).toContain('DeliveryCandidateItem')
    expect(api).toMatch(/items\?: DeliveryCandidateItem\[\]/)
    // 面板必须渲染这些商品行：司机看不到明细就无从判断这单要不要装车、装什么
    expect(page).toMatch(/candidate\.items[\s\S]*?item\.spuName/)
  })

  it('配送中加单不跳过配货：新货落配货中，司机配齐后再次出发', () => {
    const page = source('src/pages/delivery/trip-detail.vue')
    const itemService = repoSource(
      'aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/service/impl/DeliveryTaskItemServiceImpl.java',
    )

    // 新加货要有独立配货清单与再次出发按钮，否则货在库里没法确认取货
    expect(page).toContain('pendingPickGroups')
    expect(page).toContain('isPendingAllPicked')
    expect(page).toContain('配货完毕，再次出发')
    // 后端在「配送中」趟次上只放行新并入（配货中）任务的取货
    expect(itemService).toContain('allBelongToPickingTasks')
    expect(itemService).toContain('只能确认本趟新加的货，已出发的货不能改取货状态')
  })
})
