const { spawn } = require("child_process");
const os = require("os");
const path = require("path");

const edge = "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe";
const html = "file:///E:/Codex/work/miaoji-paint/android/app/src/main/assets/index.html?preview=1";
const profile = path.join(os.tmpdir(), `miaoji-routing-check-${process.pid}`);
const port = 9341;

const child = spawn(edge, [
  "--headless=new",
  "--disable-gpu",
  `--remote-debugging-port=${port}`,
  `--user-data-dir=${profile}`,
  "about:blank"
], { stdio: "ignore" });

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function pageTarget() {
  for (let attempt = 0; attempt < 40; attempt++) {
    try {
      const targets = await fetch(`http://127.0.0.1:${port}/json/list`).then((response) => response.json());
      const page = targets.find((target) => target.type === "page");
      if (page) return page;
    } catch {}
    await sleep(250);
  }
  throw new Error("Edge DevTools target unavailable");
}

async function main() {
  console.log("starting browser target");
  const target = await pageTarget();
  const socket = new WebSocket(target.webSocketDebuggerUrl);
  const waiting = new Map();
  let nextId = 0;

  socket.addEventListener("message", (event) => {
    const message = JSON.parse(event.data);
    const pending = waiting.get(message.id);
    if (!pending) return;
    waiting.delete(message.id);
    if (message.error) pending.reject(new Error(message.error.message));
    else pending.resolve(message.result);
  });

  await new Promise((resolve, reject) => {
    socket.addEventListener("open", resolve, { once: true });
    socket.addEventListener("error", reject, { once: true });
  });

  function send(method, params = {}) {
    return new Promise((resolve, reject) => {
      const id = ++nextId;
      waiting.set(id, { resolve, reject });
      socket.send(JSON.stringify({ id, method, params }));
    });
  }

  async function evaluate(expression) {
    const response = await send("Runtime.evaluate", {
      expression,
      awaitPromise: true,
      returnByValue: true
    });
    if (response.exceptionDetails) {
      throw new Error(
        response.exceptionDetails.exception?.description
        || response.exceptionDetails.text
        || "Runtime evaluation failed"
      );
    }
    return response.result.value;
  }

  await send("Page.enable");
  console.log("loading app");
  await send("Page.navigate", { url: html });
  await sleep(1200);

  console.log("checking beauty route");
  const beauty = await evaluate(`(() => {
    window.Android = {
      hasKey: () => true,
      generate: (...args) => { window.__nativeGenerate = args; }
    };
    beautyPhotoDataUrl = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=";
    startBeautyGeneration();
    const callId = window.__nativeGenerate.at(-1);
    window.__miaoNative(JSON.stringify({
      callId,
      event: "status",
      message: "任务 beauty123 已提交 · 已等待 12 秒"
    }));
    const statusRouted = document.getElementById("beautyGeneratingText").textContent;
    window.__miaoNative(JSON.stringify({
      callId,
      event: "result",
      data: {
        imageUrl: "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=",
        chargedCents: 8,
        mimeType: "image/png"
      }
    }));
    return {
      mode: generationMode,
      statusRouted,
      beautyStatus: beautyResultStatus.textContent,
      beautyHasSave: beautyResultBody.textContent.includes("保存到手机"),
      imageStatus: resultStatus.textContent
    };
  })()`);

  if (beauty.mode !== "beauty") throw new Error(`Beauty mode is ${beauty.mode}`);
  if (beauty.statusRouted !== "任务 beauty123 已提交 · 已等待 12 秒") {
    throw new Error(`Beauty status routed to the wrong panel: ${beauty.statusRouted}`);
  }
  if (beauty.beautyStatus !== "修图完成" || !beauty.beautyHasSave) {
    throw new Error("Beauty result was not rendered in the beauty result card");
  }
  if (beauty.imageStatus === "生成完成") {
    throw new Error("Beauty result leaked into the AI image result card");
  }

  console.log("checking image route");
  const image = await evaluate(`(() => {
    window.confirm = () => true;
    promptInput.value = "路由检查";
    startGeneration();
    return {
      mode: generationMode,
      hasGenerateCall: Boolean(window.__nativeGenerate)
    };
  })()`);

  if (image.mode !== "image" || !image.hasGenerateCall) {
    throw new Error("AI image generation did not switch back to image mode");
  }

  console.log("PASS: beauty status/result and AI image routes stay separate");
  socket.close();
}

main()
  .catch((error) => {
    console.error(error);
    process.exitCode = 1;
  })
  .finally(async () => {
    child.kill();
    await sleep(400);
  });
