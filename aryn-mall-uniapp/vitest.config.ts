import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { defineConfig } from "vitest/config";

/**
 * 去掉 JSONC 注释（`src/pages.json` 由 uni-pages 生成，带 `//` 注释）。
 *
 * 状态机而非正则：字符串里出现的 `//`（例如图片 URL）不能被当成注释起点。
 */
function stripJsonComments(source: string) {
  let result = "";
  let inString = false;
  let inLineComment = false;
  let inBlockComment = false;

  for (let i = 0; i < source.length; i++) {
    const char = source[i];
    const next = source[i + 1];

    if (inLineComment) {
      if (char === "\n") {
        inLineComment = false;
        result += char;
      }
      continue;
    }
    if (inBlockComment) {
      if (char === "*" && next === "/") {
        inBlockComment = false;
        i++;
      }
      continue;
    }
    if (inString) {
      result += char;
      if (char === "\\") {
        result += next ?? "";
        i++;
      } else if (char === '"') {
        inString = false;
      }
      continue;
    }
    if (char === '"') {
      inString = true;
      result += char;
      continue;
    }
    if (char === "/" && next === "/") {
      inLineComment = true;
      i++;
      continue;
    }
    if (char === "/" && next === "*") {
      inBlockComment = true;
      i++;
      continue;
    }
    result += char;
  }
  return result;
}

/**
 * 让测试可以直接 import `@/pages.json`。
 *
 * 构建期由 uni 插件处理，测试环境没有它，vite 的 JSON 插件会因注释解析失败；
 * 这里统一在 load 阶段剥离注释，保证「tabBar 页面清单」在测试里与运行时同源。
 */
function jsoncPlugin() {
  return {
    name: "aryn:jsonc",
    enforce: "pre" as const,
    transform(code: string, id: string) {
      if (!id.endsWith(".json") || !code.includes("//")) return null;
      return { code: stripJsonComments(code), map: null };
    },
  };
}

export default defineConfig({
  plugins: [jsoncPlugin()],
  // 值与 vite.config.ts 的 `@` 别名保持一致：测试里要 import 运行时模块
  // （如 @/pages.json、@/utils/*），只有类型导入时才可以不带别名解析。
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
  test: {
    environment: "node",
    include: ["src/**/*.test.ts"],
  },
});
