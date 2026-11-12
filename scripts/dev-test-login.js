import { spawnSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { createServer } from "node:http";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

// 开发测试登录配套脚本：从 .env 读测试账号，经 adb reverse 暴露给 Debug 构建的「测试登录」按钮。
// 凭据只走本机回环，不打印、不进命令行参数、不写日志。
const PORT = 8787;
const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const KEYS = ["MISSAV_TEST_EMAIL", "MISSAV_TEST_PASSWORD"];

function readTestAccount() {
  const values = Object.fromEntries(KEYS.map((key) => [key, ""]));
  try {
    for (const line of readFileSync(join(root, ".env"), "utf8").split(/\r?\n/)) {
      const match = /^([A-Z_]+)=(.*)$/.exec(line.trim());
      if (match && KEYS.includes(match[1])) {
        values[match[1]] = match[2].replace(/^["']|["']$/g, "");
      }
    }
  } catch {
    // 文件缺失按“未配置”处理，下面统一报错。
  }
  return { email: values.MISSAV_TEST_EMAIL, password: values.MISSAV_TEST_PASSWORD };
}

const account = readTestAccount();
if (!account.email || !account.password) {
  console.error("缺少 MISSAV_TEST_EMAIL / MISSAV_TEST_PASSWORD，请按 .env.example 创建 .env");
  process.exit(1);
}

const server = createServer((request, response) => {
  if (request.url !== "/test-login") {
    response.writeHead(404);
    response.end();
    return;
  }
  response.writeHead(200, { "content-type": "application/json" });
  response.end(JSON.stringify(account));
});

server.listen(PORT, "127.0.0.1", () => {
  const adb = process.env.ADB ?? "adb";
  const serial = process.env.ADB_SERIAL;
  // NOTE: 无线调试常以 IP:端口 作为序列号；adb 守护进程重启后需要先 connect 才能用 -s 定位设备。
  if (serial && /^[^:]+:\d+$/.test(serial)) {
    spawnSync(adb, ["connect", serial], { stdio: "inherit" });
  }
  const args = [...(serial ? ["-s", serial] : []), "reverse", `tcp:${PORT}`, `tcp:${PORT}`];
  // NOTE: 用 inherit 让 adb 直接继承终端的 stdio；部分沙箱环境会掐掉带管道的孙进程。
  const reverse = spawnSync(adb, args, { stdio: "inherit" });
  if (reverse.error || reverse.status !== 0) {
    console.error(`adb reverse 未生效（${reverse.error?.message ?? `exit ${reverse.status}`}）。`);
    console.error(`请手动执行：${adb} ${serial ? `-s ${serial} ` : ""}reverse tcp:${PORT} tcp:${PORT}`);
    console.error("同一台手机有多个传输条目时用 ADB_SERIAL 指定序列号；adb 不在 PATH 时用 ADB 指向 adb.exe。");
  } else {
    console.log(`测试登录服务就绪：设备内 http://127.0.0.1:${PORT}/test-login`);
  }
  console.log("在 App 登录页点「测试登录（Debug）」；Ctrl+C 结束。");
});
