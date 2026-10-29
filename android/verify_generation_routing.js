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
    window.confirm = () => true;
    beautyPhotoDataUrl = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=";
    beautyQualitySelect.value = "high";
    const qualityOptions = beautyQualitySelect.options.length;
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
      qualityOptions,
      generatedQuality: window.__nativeGenerate[4],
      statusRouted,
      beautyStatus: beautyResultStatus.textContent,
      beautyHasSave: beautyResultBody.textContent.includes("保存到手机"),
      imageStatus: resultStatus.textContent
    };
  })()`);

  if (beauty.mode !== "beauty") throw new Error(`Beauty mode is ${beauty.mode}`);
  if (beauty.qualityOptions < 2 || beauty.generatedQuality !== "high") {
    throw new Error("Beauty quality selector did not reach the native request");
  }
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
      .filter((image) => {
        const source = image.getAttribute("src") || "";
        return source.includes("effect-") && source.endsWith(".jpg");
      }).length;
    effectPhotoDataUrl = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=";
    effectSourceSize = closestEffectSize(1600, 900);
    effectSizeSelect.value = "source";
    effectQualitySelect.value = "high";
    const qualityOptions = effectQualitySelect.options.length;
    const callsBeforePreview = window.__nativeCount;
    renderEffectCards();
    const beforeFilter = effectPhotoImage.style.filter;
    effectPresetId = "hong-kong-red";
    effectStrength.value = "100";
    renderEffectCards();
    applyEffectPreview();
    const previewChanged = beforeFilter !== effectPhotoImage.style.filter;
    const previewCalls = window.__nativeCount - callsBeforePreview;
    document.querySelector('[data-audience="women"]').click();
    const femaleCardCount = effectGrid.children.length;
    document.querySelector('[data-audience="men"]').click();
    const maleCardCount = effectGrid.children.length;
    const maleNames = [...effectGrid.querySelectorAll(".effect-card-copy strong")]
      .map((element) => element.textContent);
    document.querySelector('[data-audience="all"]').click();
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
      qualityOptions,
      statusRouted,
      cardCount: effectGrid.children.length,
      sampleImages,
      femaleCardCount,
      maleCardCount,
      maleNames,
      previewChanged,
      previewCalls,
      generatedSize: window.__nativeGenerate[2],
      generatedQuality: window.__nativeGenerate[4],
      effectStatus: effectResultStatus.textContent,
      effectHasSave: effectResultBody.textContent.includes("保存到手机"),
      beautyStatus: beautyResultStatus.textContent,
      imageStatus: resultStatus.textContent
    };
  })()`);

  if (effect.mode !== "effect") throw new Error(`Effect mode is ${effect.mode}`);
  if (effect.qualityOptions < 2 || effect.generatedQuality !== "high") {
    throw new Error("Effect quality selector did not reach the native request");
  }
  if (effect.statusRouted !== "任务 effect123 已提交 · 已等待 9 秒") {
    throw new Error(`Effect status routed to the wrong panel: ${effect.statusRouted}`);
  }
  if (effect.cardCount !== 26 || effect.sampleImages !== 26) {
    throw new Error("Effect cards do not have visible sample images");
  }
  if (
    effect.femaleCardCount !== 12
    || effect.maleCardCount !== 14
    || !effect.maleNames.includes("暗线特工")
    || !effect.maleNames.includes("赛博机甲战士")
    || !effect.maleNames.includes("高定男装大片")
    || !effect.maleNames.includes("暗夜机车骑士")
    || !effect.maleNames.includes("上海滩侦探")
    || !effect.maleNames.includes("山海经战神")
    || !effect.maleNames.includes("星舰指挥官")
  ) {
    throw new Error("Male effect filter or presets are missing");
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
    const referenceFirst = document.querySelector("#imagePage .stack")
      .firstElementChild.contains(referenceZone);
    promptInput.value = "路由检查";
    startGeneration();
    return {
      mode: generationMode,
      hasGenerateCall: Boolean(window.__nativeGenerate),
      referenceFirst
    };
  })()`);

  if (image.mode !== "image" || !image.hasGenerateCall || !image.referenceFirst) {
    throw new Error("AI image route or reference placement check failed");
  }

  console.log("checking new image presets");
  const presets = await evaluate(`(() => {
    const foodIds = [
      "ingredient-atlas", "recipe-zine", "tabletop-diorama",
      "ingredient-section", "kitchen-moment", "visual-recipe"
    ];
    const transformIds = [
      "torn-cartoon", "double-exposure", "acrylic-relief",
      "screenprint", "paper-diorama", "ink-deconstruct"
    ];
    const skillIds = [
      "papercraft-travel", "iso-block-world", "pixel-dissolve",
      "pen-wash-travel", "watercolor-card", "impasto-diorama",
      "photo-relic", "zine-postcard", "gathered-zine",
      "heytea-doodle", "abstract-editorial", "mono-color",
      "xxd-panel", "line-art-extension"
    ];
    const food = STYLE_CARDS.filter((style) => style.category === "food");
    const transform = STYLE_CARDS.filter((style) => style.category === "transform");
    const skill = STYLE_CARDS.filter((style) => style.category === "skill");
    const travel = STYLE_CARDS.find((style) => style.id === "travel-memory");
    document.querySelector('[data-category="food"]').click();
    const renderedFoodCards = styleGrid.children.length;
    document.querySelector('[data-category="transform"]').click();
    const renderedTransformCards = styleGrid.children.length;
    document.querySelector('[data-category="skill"]').click();
    return {
      foodCount: food.length,
      foodIdsPresent: foodIds.every((id) => STYLE_CARDS.some((style) => style.id === id)),
      detailedPrompts: food.every((style) => style.prompt.length >= 250),
      previewAssets: food.every((style) => style.previewImage === \`style-\${style.id}.jpg\`),
      renderedFoodCards,
      transformCount: transform.length,
      transformIdsPresent: transformIds.every((id) => STYLE_CARDS.some((style) => style.id === id)),
      transformPrompts: transform.every((style) => style.prompt.length >= 220),
      transformPreviews: transform.every((style) => style.previewImage === \`style-\${style.id}.jpg\`),
      renderedTransformCards,
      skillCount: skill.length,
      skillIdsPresent: skillIds.every((id) => STYLE_CARDS.some((style) => style.id === id)),
      skillPrompts: skill.every((style) => style.prompt.length >= 260),
      skillPreviews: skill.every((style) => style.previewImage === \`style-\${style.id}.jpg\`),
      renderedSkillCards: styleGrid.children.length,
      travelGeneric: travel
        && travel.name === "旅途手记拼贴"
        && travel.prompt.includes("不要预设海边")
        && travel.prompt.includes("如果照片不适合转化")
    };
  })()`);

  if (
    presets.foodCount !== 6
    || !presets.foodIdsPresent
    || !presets.detailedPrompts
    || !presets.previewAssets
    || presets.renderedFoodCards !== 6
    || presets.transformCount !== 6
    || !presets.transformIdsPresent
    || !presets.transformPrompts
    || !presets.transformPreviews
    || presets.renderedTransformCards !== 6
    || presets.skillCount !== 14
    || !presets.skillIdsPresent
    || !presets.skillPrompts
    || !presets.skillPreviews
    || presets.renderedSkillCards !== 14
    || !presets.travelGeneric
  ) {
    throw new Error(`New image presets check failed: ${JSON.stringify(presets)}`);
  }

  console.log("PASS: routes stay separate and new image presets are present");
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
