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
      generate: (...args) => {
        window.__nativeGenerate = args;
        window.__nativeCount = (window.__nativeCount || 0) + 1;
      }
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

  console.log("checking effect route");
  const effect = await evaluate(`(() => {
    effectPhotoDataUrl = "";
    renderEffectCards();
    const sampleImages = [...effectGrid.querySelectorAll(".effect-card-visual img")]
      .filter((image) => image.getAttribute("src") === "effect-model.jpg").length;
    effectPhotoDataUrl = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=";
    effectSourceSize = closestEffectSize(1600, 900);
    effectSizeSelect.value = "source";
    const callsBeforePreview = window.__nativeCount;
    renderEffectCards();
    const beforeFilter = effectPhotoImage.style.filter;
    effectPresetId = "hong-kong-red";
    effectStrength.value = "100";
    renderEffectCards();
    applyEffectPreview();
    const previewChanged = beforeFilter !== effectPhotoImage.style.filter;
    const previewCalls = window.__nativeCount - callsBeforePreview;
    startEffectGeneration();
    const callId = window.__nativeGenerate.at(-1);
    window.__miaoNative(JSON.stringify({
      callId,
      event: "status",
      message: "任务 effect123 已提交 · 已等待 9 秒"
    }));
    const statusRouted = document.getElementById("effectGeneratingText").textContent;
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
      cardCount: effectGrid.children.length,
      sampleImages,
      previewChanged,
      previewCalls,
      generatedSize: window.__nativeGenerate[2],
      effectStatus: effectResultStatus.textContent,
      effectHasSave: effectResultBody.textContent.includes("保存到手机"),
      beautyStatus: beautyResultStatus.textContent,
      imageStatus: resultStatus.textContent
    };
  })()`);

  if (effect.mode !== "effect") throw new Error(`Effect mode is ${effect.mode}`);
  if (effect.statusRouted !== "任务 effect123 已提交 · 已等待 9 秒") {
    throw new Error(`Effect status routed to the wrong panel: ${effect.statusRouted}`);
  }
  if (effect.cardCount !== 12 || effect.sampleImages !== 12) {
    throw new Error("Effect cards do not have visible sample images");
  }
  if (!effect.previewChanged || effect.previewCalls !== 0) {
    throw new Error("Effect live preview did not update locally for free");
  }
  if (effect.generatedSize !== "16:9") {
    throw new Error(`Effect source aspect was not used: ${effect.generatedSize}`);
  }
  if (effect.effectStatus !== "特效生成完成" || !effect.effectHasSave) {
    throw new Error("Effect result was not rendered in the effect result card");
  }
  if (effect.beautyStatus !== "修图完成" || effect.imageStatus === "生成完成") {
    throw new Error("Effect result leaked into another result card");
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

  console.log("PASS: beauty, effect, and AI image routes stay separate");
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
