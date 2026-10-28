#!/usr/bin/env bash
#
# 用 macOS launchd 托管演示环境：登录后自动拉起、每 5 分钟自愈，
# 完全独立于终端/IDE/DSH 会话（关掉任何软件都不影响隧道和 Pages 代理）。
#
# 做三件事：
#   1. 写 ~/Library/LaunchAgents/com.aryn.demo-env.plist
#   2. launchctl 加载并立即执行一次
#   3. 之后每 300 秒执行一遍 start-demo-env.sh（已存活的服务不会重启；
#      隧道挂了会自动重建，并自动把 Cloudflare Pages 的 /api 代理更新到新域名）
#
# 用法：
#   ./dev-tools/install-demo-launchd.sh install     # 安装/更新并立即启动
#   ./dev-tools/install-demo-launchd.sh uninstall   # 卸载（停止自启，已在跑的服务保留）
#   ./dev-tools/install-demo-launchd.sh status      # 查看 launchd 状态 + 最近日志
#   ./dev-tools/install-demo-launchd.sh run         # 立即手动执行一次
#   ./dev-tools/install-demo-launchd.sh logs        # 打印日志路径
#
# 注意：需要 Docker/OrbStack 在登录后自动启动（一般默认就是）；Mac 休眠时不会运行，
#      演示期间建议接通电源并打开“防止自动休眠”，或另开终端跑 caffeinate -dims。

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
LABEL="com.aryn.demo-env"
PLIST="${HOME}/Library/LaunchAgents/${LABEL}.plist"
LOG_DIR="${HOME}/Library/Logs/aryn-demo"
UID_NUM="$(id -u)"

log() { printf '[demo-launchd] %s\n' "$*"; }
die() { printf '[demo-launchd] 错误: %s\n' "$*" >&2; exit 1; }

need_nvm_node() {
  # launchd 不加载 shell 配置，需要把 nvm 的 node 目录显式写进 PATH
  local node_bin
  node_bin="$(command -v node 2>/dev/null || true)"
  [ -n "$node_bin" ] || die '当前 shell 找不到 node，请确认已安装 Node >= 22'
  dirname "$node_bin"
}

write_plist() {
  local node_dir
  node_dir="$(need_nvm_node)"
  mkdir -p "${HOME}/Library/LaunchAgents" "$LOG_DIR"
  cat > "$PLIST" <<PLIST_EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key>
  <string>${LABEL}</string>
  <key>ProgramArguments</key>
  <array>
    <string>/bin/bash</string>
    <string>-lc</string>
    <string>cd "${ROOT_DIR}" &amp;&amp; echo "===== $(date) launchd loop start =====" &amp;&amp; while true; do AUTO_PAGES=true ./dev-tools/start-demo-env.sh; echo "----- 下一轮 300s 后 -----"; sleep 300; done</string>
  </array>
  <key>WorkingDirectory</key>
  <string>${ROOT_DIR}</string>
  <key>EnvironmentVariables</key>
  <dict>
    <key>PATH</key>
    <string>${node_dir}:/opt/homebrew/bin:/opt/homebrew/sbin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:/Users/wangqingyu/.orbstack/bin</string>
    <key>HOME</key>
    <string>${HOME}</string>
    <key>AUTO_PAGES</key>
    <string>true</string>
  </dict>
  <key>RunAtLoad</key>
  <true/>
  <key>KeepAlive</key>
  <true/>
  <key>ThrottleInterval</key>
  <integer>10</integer>
  <key>StandardOutPath</key>
  <string>${LOG_DIR}/env.log</string>
  <key>StandardErrorPath</key>
  <string>${LOG_DIR}/env.err.log</string>
  <key>AbandonProcessGroup</key>
  <true/>
  <key>ProcessType</key>
  <string>Background</string>
  <key>LowPriorityIO</key>
  <true/>
</dict>
</plist>
PLIST_EOF
  plutil -lint "$PLIST" >/dev/null 2>&1 || die "plist 格式错误: $PLIST"
}

status_cmd() {
  echo "──────────────────────────────────────────────"
  if launchctl print "gui/${UID_NUM}/${LABEL}" >/dev/null 2>&1; then
    log "launchd 已加载：${LABEL}（每 300 秒自愈一次）"
    launchctl print "gui/${UID_NUM}/${LABEL}" 2>/dev/null | grep -E "state =|last exit code|runs =|program =" | head -5
  else
    log "launchd 未加载（未安装，或已执行 uninstall/--stop）"
  fi
  echo "--- 最近日志（${LOG_DIR}/env.log）---"
  tail -15 "${LOG_DIR}/env.log" 2>/dev/null || log '暂无日志'
  echo "──────────────────────────────────────────────"
  "${SCRIPT_DIR}/start-demo-env.sh" --status 2>/dev/null || true
}

case "${1:-}" in
  install)
    write_plist
    launchctl bootout "gui/${UID_NUM}/${LABEL}" >/dev/null 2>&1 || true
    launchctl bootstrap "gui/${UID_NUM}/${LABEL}" "$PLIST" 2>/dev/null || launchctl load -w "$PLIST" 2>/dev/null || true
    launchctl enable "gui/${UID_NUM}/${LABEL}" >/dev/null 2>&1 || true
    launchctl kickstart -k "gui/${UID_NUM}/${LABEL}" >/dev/null 2>&1 || launchctl start "$LABEL" 2>/dev/null || true
    log "已安装并立即启动：${PLIST}"
    log "日志：${LOG_DIR}/env.log（约 30~90 秒后可用 start-demo-env.sh --status 查看链接）"
    log "关闭自启：./dev-tools/install-demo-launchd.sh uninstall"
    ;;
  uninstall)
    launchctl bootout "gui/${UID_NUM}/${LABEL}" >/dev/null 2>&1 || launchctl unload -w "$PLIST" 2>/dev/null || true
    rm -f "$PLIST"
    log '已卸载 launchd 自启（已在运行的隧道/服务不会被杀掉；需要的话执行 start-demo-env.sh --stop）'
    ;;
  status)
    status_cmd
    ;;
  run)
    launchctl kickstart -k "gui/${UID_NUM}/${LABEL}" >/dev/null 2>&1 || die '未安装，先执行 install'
    log '已触发一次执行，结果见日志'
    ;;
  logs)
    log "日志目录：${LOG_DIR}"
    ls -l "$LOG_DIR" 2>/dev/null || true
    ;;
  *)
    sed -n '2,24p' "$0"
    ;;
esac
