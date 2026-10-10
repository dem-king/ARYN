-- ============================================================================
-- 靠港生效后购物车归属顺延：存量「无靠港归属/挂已结束靠港」行一次性回填（Boot 单体模式）
-- 目标库：aryn_boot
-- 背景：购物车行在加购时刻快照船舶与靠港归属（shopping_cart.vessel_id / vessel_call_id，
--       防串船分组键）。「有船无靠港」期间加购的行 vessel_call_id 落空；靠港结束
--       （status 3已完成 / 4已取消 / ETD 过点）后新加购的行同样只带船不带靠港。
--       代码侧已在海员申报（declareCall）与运营排产（saveCall）新靠港生效时自动把
--       这类行顺延到新靠港（RemoteShoppingCartService.reattachRowsToVesselCall）；
--       本脚本针对「靠港在代码上线前已生效、不会再触发申报」的存量行做一次性补迁，
--       使用户无需删掉重加。
-- 口径：同船（vessel_id 相同）且行归属为空，或挂在该船已结束（3）/已取消（4）/ETD
--       已过/ETD 为空的靠港上的行 → 迁到该船当前可用靠港（1计划中/2靠泊中且 ETD
--       未过，按 ETA 最近的一班，与 App 端 availableCalls 口径一致）。
--       没有可用靠港的船舶不动（等下一次申报靠港时由代码自动迁移）。
--       挂在「其他可用靠港」上的行不迁移（多靠港船各自的行保持原归属）。
-- 特性：可重复执行（迁移后行不再满足 WHERE）；不执行 DROP/TRUNCATE。
-- 执行：mysql -u root -p aryn_boot < 113vessel_call_reattach_cart_backfill.sql
-- ============================================================================

USE `aryn_boot`;

SET NAMES utf8mb4;

-- ============ shopping_cart：无归属行与挂已结束靠港的行顺延到当前可用靠港 ============
-- live 子查询：每艘船取「可用靠港中 ETA 最早」的一班（同 ETA 取 id 小者），
-- 与 useShipContextLoad 回落 calls[0] 的前端口径一致。
UPDATE `shopping_cart` sc
JOIN (
    SELECT vc1.`vessel_id`, vc1.`id` AS `live_call_id`
    FROM `vessel_call` vc1
    WHERE vc1.`del_flag` = '0'
      AND vc1.`status` IN ('1', '2')
      AND vc1.`etd` > NOW()
      AND NOT EXISTS (
          SELECT 1
          FROM `vessel_call` vc2
          WHERE vc2.`vessel_id` = vc1.`vessel_id`
            AND vc2.`del_flag` = '0'
            AND vc2.`status` IN ('1', '2')
            AND vc2.`etd` > NOW()
            AND (vc2.`eta` < vc1.`eta`
                 OR (vc2.`eta` = vc1.`eta` AND vc2.`id` < vc1.`id`))
      )
) live ON live.`vessel_id` = sc.`vessel_id`
SET sc.`vessel_call_id` = live.`live_call_id`,
    sc.`update_time` = NOW()
WHERE sc.`del_flag` = '0'
  AND (sc.`vessel_call_id` IS NULL
       OR sc.`vessel_call_id` = ''
       OR sc.`vessel_call_id` IN (
           SELECT `stale`.`id`
           FROM (
               SELECT vc3.`id`
               FROM `vessel_call` vc3
               WHERE vc3.`del_flag` = '0'
                 AND (vc3.`status` IN ('3', '4')
                      OR vc3.`etd` IS NULL
                      OR vc3.`etd` <= NOW())
           ) `stale`
       ));

-- 验证：不应再有「船舶有可用靠港、行却无归属/挂已结束靠港」的购物车行
-- SELECT sc.`id`, sc.`vessel_id`, sc.`vessel_call_id`
-- FROM `shopping_cart` sc
-- JOIN `vessel_call` vc ON vc.`vessel_id` = sc.`vessel_id`
--   AND vc.`del_flag` = '0' AND vc.`status` IN ('1', '2') AND vc.`etd` > NOW()
-- WHERE sc.`del_flag` = '0'
--   AND (sc.`vessel_call_id` IS NULL OR sc.`vessel_call_id` = ''
--        OR NOT EXISTS (SELECT 1 FROM `vessel_call` lc
--                       WHERE lc.`id` = sc.`vessel_call_id`
--                         AND lc.`del_flag` = '0'
--                         AND lc.`status` IN ('1', '2') AND lc.`etd` > NOW()));

SET FOREIGN_KEY_CHECKS = 1;
