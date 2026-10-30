const { spawn } = require("child_process");
const fs = require("fs");
const path = require("path");

const edge = "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe";
const html = "file:///E:/Codex/work/miaoji-paint/android/app/src/main/assets/index.html?preview=1";
const outputFolder = "E:\\Codex\\outputs\\妙极手机画图";
const outputCard = path.join(outputFolder, "界面预览_旅行地理志-v1.8.1.png");
const outputPrompt = path.join(outputFolder, "界面预览_旅行地理志提示词-v1.8.1.png");
const profile = "E:\\Codex\\work\\miaoji-paint\\edge-geo-editorial-profile";
const port = 9342;

const child = spawn(edge, [
  "--headless=new",
  "--disable-gpu",
  "--hide-scrollbars",
  "--allow-file-access-from-files",
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
      throw new Error(response.exceptionDetails.exception?.description || response.exceptionDetails.text);
    }
    return response.result.value;
  }

  await send("Page.enable");
  await send("Emulation.setDeviceMetricsOverride", {
    width: 430,
    height: 932,
    deviceScaleFactor: 1,
    mobile: true,
    screenWidth: 430,
    screenHeight: 932
  });
  await send("Page.navigate", { url: html });
  await sleep(1200);
  await evaluate("document.fonts.ready.then(() => true)");

  const card = await evaluate(`(async () => {
    selectedStyleCategory = "skill";
    renderStyleCards();
    const findCard = () => [...styleGrid.querySelectorAll(".style-card")]
      .find((element) => element.querySelector(".style-meta strong")?.textContent === "旅行地理志");
    let card = findCard();
    if (!card) throw new Error("旅行地理志卡片不存在");
    card.click();
    await new Promise((resolve) => setTimeout(resolve, 400));
    card = findCard();
    const image = card.querySelector(".style-preview img");
    card.scrollIntoView({ block: "center" });
    return {
      selected: card.classList.contains("selected"),
      imageLoaded: Boolean(image && image.complete && image.naturalWidth > 0),
      imageSource: image?.getAttribute("src"),
      skillCount: STYLE_CARDS.filter((style) => style.category === "skill").length,
      skillFilter: document.querySelector('[data-category="skill"]').textContent,
      promptVisible: !selectedStyleEditor.hidden,
      promptLength: stylePromptEditor.value.length
    };
  })()`);

  if (!card.selected || !card.imageLoaded || card.imageSource !== "style-geo-editorial.jpg") {
    throw new Error(`旅行地理志卡片检查失败: ${JSON.stringify(card)}`);
  }
  if (card.skillCount !== 15 || card.skillFilter !== "公开 Skill 15") {
    throw new Error(`Skill 数量检查失败: ${JSON.stringify(card)}`);
  }
  if (!card.promptVisible || card.promptLength < 260) {
    throw new Error(`旅行地理志提示词检查失败: ${JSON.stringify(card)}`);
  }

  await evaluate("toastEl.style.display = 'none'");
  fs.mkdirSync(outputFolder, { recursive: true });
  const cardShot = await send("Page.captureScreenshot", {
    format: "png",
    captureBeyondViewport: false
  });
  fs.writeFileSync(outputCard, Buffer.from(cardShot.data, "base64"));

  await evaluate("selectedStyleEditor.scrollIntoView({ block: 'center' })");
  await sleep(250);
  const promptShot = await send("Page.captureScreenshot", {
    format: "png",
    captureBeyondViewport: false
  });
  fs.writeFileSync(outputPrompt, Buffer.from(promptShot.data, "base64"));

  console.log(JSON.stringify({ outputCard, outputPrompt, ...card }, null, 2));
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
