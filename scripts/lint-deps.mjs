#!/usr/bin/env node
/**
 * UI Monorepo 层级依赖检查器 (Harness Engineering)
 *
 * 扫描 aryn-mall-ui monorepo 下各包的 package.json，
 * 对照层级映射表，检测跨层依赖违规。
 *
 * 用法:
 *   node scripts/lint-deps.mjs                 # 检查 aryn-mall-ui
 *   node scripts/lint-deps.mjs --verbose       # 输出详细信息
 */

import { readFileSync, existsSync } from "node:fs";
import { join, resolve, dirname, relative } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(__dirname, "..");
const UI_ROOT = join(ROOT, "aryn-mall-ui");

// ============================================================================
// 层级映射表
// ============================================================================

const LAYER_MAP = {
  // Layer 0 — 纯类型定义
  "@vben-core/typings": 0,
  "@vben/types": 0,

  // Layer 1 — 工具/常量
  "@vben-core/shared": 1,
  "@vben/utils": 1,
  "@vben/constants": 1,
  "@vben/icons": 1,
  "@vben/locales": 1,

  // Layer 2 — 状态/偏好
  "@vben-core/preferences": 2,
  "@vben/stores": 2,
  "@vben/preferences": 2,

  // Layer 3 — 效果/逻辑复用
  "@vben/access": 3,
  "@vben/hooks": 3,
  "@vben/request": 3,
  "@vben/plugins": 3,
  "@vben-core/composables": 3,

  // Layer 4 — UI 组件/布局
  "@vben/layouts": 4,
  "@vben/styles": 4,
  "@vben-core/form-ui": 4,
  "@vben-core/layout-ui": 4,
  "@vben-core/menu-ui": 4,
  "@vben-core/popup-ui": 4,
  "@vben-core/shadcn-ui": 4,
  "@vben-core/tabs-ui": 4,

  // Layer 5 — 应用入口
  "@vben/web-ele": 5,
};

// L3-4 包之间不得循环依赖，允许内部依赖但需要检查

// 可读取的 package.json 列表
const PACKAGES_DIRS = [
  "packages/types",
  "packages/utils",
  "packages/constants",
  "packages/icons",
  "packages/stores",
  "packages/locales",
  "packages/preferences",
  "packages/styles",
  "packages/effects/access",
  "packages/effects/hooks",
  "packages/effects/request",
  "packages/effects/plugins",
  "packages/effects/layouts",
  "packages/@core/base/typings",
  "packages/@core/base/shared",
  "packages/@core/base/icons",
  "packages/@core/composables",
  "packages/@core/preferences",
  "packages/@core/ui-kit/form-ui",
  "packages/@core/ui-kit/layout-ui",
  "packages/@core/ui-kit/menu-ui",
  "packages/@core/ui-kit/popup-ui",
  "packages/@core/ui-kit/shadcn-ui",
  "packages/@core/ui-kit/tabs-ui",
  "apps/web-ele/web-ele",
];

// ============================================================================
// 工具函数
// ============================================================================

function readPackageJson(dir) {
  const path = join(UI_ROOT, dir, "package.json");
  if (!existsSync(path)) return null;
  try {
    return JSON.parse(readFileSync(path, "utf-8"));
  } catch {
    return null;
  }
}

function getWorkspaceDep(pkg, depName) {
  const all = { ...pkg.dependencies, ...pkg.devDependencies };
  return all[depName] || null;
}

function collectPackages() {
  const result = [];
  for (const dir of PACKAGES_DIRS) {
    const pkg = readPackageJson(dir);
    if (pkg && pkg.name) {
      result.push({ name: pkg.name, dir, layer: LAYER_MAP[pkg.name] ?? -1, deps: { ...pkg.dependencies, ...pkg.devDependencies } });
    }
  }
  return result;
}

// ============================================================================
// 检查逻辑
// ============================================================================

function checkDependencies(packages, verbose = false) {
  const violations = [];
  const layerIdx = new Map(packages.map((p) => [p.name, p]));

  for (const pkg of packages) {
    const sourceLayer = pkg.layer;
    if (sourceLayer === -1) continue; // 不在层级映射中，跳过

    for (const [depName, depVer] of Object.entries(pkg.deps)) {
      // 只检查 workspace:* 依赖
      if (depVer !== "workspace:*") continue;

      const target = layerIdx.get(depName);
      if (!target || target.layer === -1) continue;

      const targetLayer = target.layer;

      // 规则: source 只能依赖同层或更低层 (source >= target)
      if (sourceLayer < targetLayer) {
        violations.push({
          type: "layer_violation",
          source: { name: pkg.name, dir: pkg.dir, layer: sourceLayer },
          target: { name: depName, dir: target.dir, layer: targetLayer },
          message: `${pkg.name} (L${sourceLayer}) → depends on → ${depName} (L${targetLayer})`,
        });
      }
    }
  }

  return violations;
}

// ============================================================================
// 输出
// ============================================================================

const RED = "\x1b[91m";
const GREEN = "\x1b[92m";
const YELLOW = "\x1b[93m";
const RESET = "\x1b[0m";
const BOLD = "\x1b[1m";

function printViolation(v) {
  console.log(`${RED}${BOLD}✗ LAYER VIOLATION${RESET}`);
  console.log(`  Source:  ${v.source.name} (Layer ${v.source.layer}) → ${v.source.dir}`);
  console.log(`  Depends: ${v.target.name} (Layer ${v.target.layer}) → ${v.target.dir}`);
  console.log(`  Rule:    Layer ${v.source.layer} packages cannot depend on Layer ${v.target.layer} packages.`);
  console.log(
    `  Fix:     Restructure dependency: invert through injection, move shared logic to lower layer, or add an interface package.`,
  );
  console.log();
}

function printPackageTree(packages) {
  console.log(`${BOLD}Package Dependency Tree:${RESET}\n`);
  for (const pkg of packages) {
    if (pkg.layer === -1) continue;
    const deps = Object.entries(pkg.deps)
      .filter(([, v]) => v === "workspace:*")
      .map(([name]) => {
        const depPkg = packages.find((p) => p.name === name);
        return depPkg ? `${name} (L${depPkg.layer})` : name;
      });
    const depsStr = deps.length > 0 ? ` → ${deps.join(", ")}` : "";
    console.log(`  L${pkg.layer}  ${pkg.name}  ${depsStr}`);
  }
}

// ============================================================================
// 主流程
// ============================================================================

function main() {
  const verbose = process.argv.includes("--verbose");

  if (!existsSync(UI_ROOT)) {
    console.error(`${RED}Error: ${UI_ROOT} not found.${RESET}`);
    process.exit(1);
  }

  console.log(`${BOLD}=== Aryn Mall UI — Arch Dependency Check ===${RESET}\n`);

  const packages = collectPackages();
  const mapped = packages.filter((p) => p.layer !== -1);
  const unmapped = packages.filter((p) => p.layer === -1);

  if (mapped.length === 0) {
    console.log(`${YELLOW}No mapped packages found. Check paths.${RESET}`);
    process.exit(1);
  }

  if (verbose) {
    printPackageTree(mapped);
  }

  const violations = checkDependencies(mapped);

  if (violations.length > 0) {
    console.log(`${RED}${BOLD}=== Violations (${violations.length}) ===${RESET}\n`);
    for (const v of violations) {
      printViolation(v);
    }
  }

  console.log(`${BOLD}--- Summary ---${RESET}`);
  console.log(`Mapped packages:  ${mapped.length}`);
  if (unmapped.length > 0) {
    console.log(`Unmapped (skipped): ${unmapped.map((p) => p.name).join(", ")}`);
  }
  console.log(`Violations:       ${violations.length}`);

  if (violations.length > 0) {
    console.log(`\n${RED}${BOLD}✗ FAILED: ${violations.length} architecture violation(s).${RESET}`);
    process.exit(1);
  } else {
    console.log(`\n${GREEN}${BOLD}✓ PASSED: Clean architecture.${RESET}`);
  }
}

main();
