-- 悦航购下线船供履约作业菜单（Boot 单体模式）
-- 目标库：aryn_boot
-- 背景：实际履约作业不使用扫码拣货流程（司机线下取货），
--       拣货波次、履约异常、港口配送看板三个页面上线后无实际使用；
--       将其从「船供运营」菜单下隐藏，避免无效入口。
-- 语义：仅逻辑删除 sys_menu 页面菜单（del_flag 0→1），
--       页面下的按钮权限与 sys_tenant_menu / sys_role_menu 授权行全部保留，
--       恢复显示只需把对应行 del_flag 改回 '0'。可重复执行，不物理删除任何数据。
-- 注意：菜单在登录时快照，执行后需重新登录管理端才生效。

USE `aryn_boot`;
SET NAMES utf8mb4;

UPDATE `sys_menu`
SET `del_flag` = '1', `update_time` = NOW(), `update_by` = 'system'
WHERE `id` IN (
  '2110000000000000002', -- 拣货波次 /fulfillment/wave
  '2110000000000000003', -- 履约异常 /fulfillment/exception
  '2110000000000000103'  -- 港口配送看板 /delivery/port-board
)
  AND `del_flag` = '0';

SET FOREIGN_KEY_CHECKS = 1;
