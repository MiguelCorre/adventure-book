import { spawn, spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import path from "node:path";

const rootDirectory = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "..",
);
const backendDirectory = path.join(rootDirectory, "backend");
const frontendDirectory = path.join(rootDirectory, "frontend");
const isWindows = process.platform === "win32";
const commandShell = process.env.ComSpec ?? "cmd.exe";
const services = [];
let shuttingDown = false;

function runFrontendInstall() {
  console.log("[dev] Installing locked frontend dependencies...");

  const command = isWindows ? commandShell : "npm";
  const args = isWindows ? ["/d", "/s", "/c", "npm.cmd ci"] : ["ci"];
  const result = spawnSync(command, args, {
    cwd: frontendDirectory,
    stdio: "inherit",
    windowsHide: true,
  });

  if (result.error) {
    console.error(`[dev] Could not run npm ci: ${result.error.message}`);
    process.exit(1);
  }

  if (result.status !== 0) {
    process.exit(result.status ?? 1);
  }
}

function startService(name, command, args, cwd) {
  console.log(`[dev] Starting ${name}...`);

  const child = spawn(command, args, {
    cwd,
    stdio: "inherit",
    detached: !isWindows,
    windowsHide: true,
  });

  const service = { name, child };
  services.push(service);

  child.once("error", (error) => {
    console.error(`[dev] ${name} could not start: ${error.message}`);
    void shutDown(1);
  });

  child.once("exit", (code, signal) => {
    if (!shuttingDown) {
      const outcome = signal ? `signal ${signal}` : `code ${code ?? 1}`;
      console.error(
        `[dev] ${name} stopped with ${outcome}; stopping all services.`,
      );
      void shutDown(code ?? 1);
    }
  });
}

function hasExited(child) {
  return child.exitCode !== null || child.signalCode !== null;
}

function waitForExit(child, timeoutMs) {
  if (hasExited(child)) {
    return Promise.resolve();
  }

  return new Promise((resolve) => {
    const timeout = setTimeout(resolve, timeoutMs);
    child.once("exit", () => {
      clearTimeout(timeout);
      resolve();
    });
  });
}

function terminateWindowsTree(child) {
  return new Promise((resolve) => {
    const killer = spawn(
      "taskkill.exe",
      ["/pid", String(child.pid), "/t", "/f"],
      {
        stdio: "ignore",
        windowsHide: true,
      },
    );
    killer.once("error", resolve);
    killer.once("exit", resolve);
  });
}

async function stopService({ child }) {
  if (hasExited(child)) {
    return;
  }

  if (isWindows) {
    // Ctrl+C is delivered to every process attached to the console. Give Maven and Angular
    // time to handle it gracefully, then remove any surviving subprocess tree.
    await waitForExit(child, 3000);
    if (!hasExited(child)) {
      await terminateWindowsTree(child);
    }
    return;
  }

  try {
    process.kill(-child.pid, "SIGTERM");
  } catch (error) {
    if (error.code !== "ESRCH") {
      console.error(
        `[dev] Could not stop process ${child.pid}: ${error.message}`,
      );
    }
  }

  await waitForExit(child, 3000);
  if (!hasExited(child)) {
    try {
      process.kill(-child.pid, "SIGKILL");
    } catch (error) {
      if (error.code !== "ESRCH") {
        console.error(
          `[dev] Could not kill process ${child.pid}: ${error.message}`,
        );
      }
    }
  }
}

async function shutDown(exitCode) {
  if (shuttingDown) {
    return;
  }

  shuttingDown = true;
  await Promise.all(services.map(stopService));
  process.exit(exitCode);
}

process.once("SIGINT", () => void shutDown(130));
process.once("SIGTERM", () => void shutDown(143));

runFrontendInstall();

if (isWindows) {
  startService(
    "backend",
    commandShell,
    ["/d", "/s", "/c", "mvnw.cmd spring-boot:run"],
    backendDirectory,
  );
  startService(
    "frontend",
    commandShell,
    ["/d", "/s", "/c", "npm.cmd start"],
    frontendDirectory,
  );
} else {
  startService("backend", "./mvnw", ["spring-boot:run"], backendDirectory);
  startService("frontend", "npm", ["start"], frontendDirectory);
}
