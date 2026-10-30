const { spawn } = require("child_process");
const fs = require("fs");
const path = require("path");

const edge = "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe";
const html = "file:///E:/Codex/work/miaoji-paint/android/app/src/main/assets/index.html?preview=1";
const output = "E:\\Codex\\outputs\\妙极手机画图\\界面预览.png";
const outputStyle = "E:\\Codex\\outputs\\妙极手机画图\\界面预览_风格提示词.png";
const outputSkills = "E:\\Codex\\outputs\\妙极手机画图\\界面预览_公开Skill-v1.9.0.png";
const outputTravelSkill = "E:\\Codex\\outputs\\妙极手机画图\\界面预览_旅行Skill提示词-v1.9.0.png";
const outputBottom = "E:\\Codex\\outputs\\妙极手机画图\\界面预览_下半页.png";
const outputSettings = "E:\\Codex\\outputs\\妙极手机画图\\界面预览_设置.png";
const outputHelp = "E:\\Codex\\outputs\\妙极手机画图\\界面预览_风格说明.png";
const outputBeauty = "E:\\Codex\\outputs\\妙极手机画图\\界面预览_美颜.png";
const outputEffects = "E:\\Codex\\outputs\\妙极手机画图\\界面预览_热门特效.png";
const outputEffectLibrary = "E:\\Codex\\outputs\\妙极手机画图\\界面预览_热门特效库.png";
const outputEffectSize = "E:\\Codex\\outputs\\妙极手机画图\\界面预览_特效比例.png";
const outputFoodLibrary = "E:\\Codex\\outputs\\妙极手机画图\\界面预览_美食灵感.png";
const profile = "E:\\Codex\\work\\miaoji-paint\\edge-mobile-profile";
const port = 9337;

const child = spawn(edge, [
  "--headless=new",
  "--disable-gpu",
  "--hide-scrollbars",
  `--remote-debugging-port=${port}`,
  `--user-data-dir=${profile}`,
  "about:blank"
], { stdio: "ignore" });

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function pageTarget() {
  for (let attempt = 0; attempt < 40; attempt++) {
    try {
      const targets = await fetch(`http://127.0.0.1:${port}/json/list`).then((r) => r.json());
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
  let nextId = 0;
  const waiting = new Map();

  socket.addEventListener("message", (event) => {
    const message = JSON.parse(event.data);
    if (!message.id || !waiting.has(message.id)) return;
    const { resolve, reject } = waiting.get(message.id);
    waiting.delete(message.id);
    if (message.error) reject(new Error(message.error.message));
    else resolve(message.result);
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
  await sleep(1300);
  await send("Runtime.evaluate", {
    expression: "document.fonts.ready.then(() => true)",
    awaitPromise: true
  });
  await send("Runtime.evaluate", {
    expression: "document.querySelectorAll('.style-card')[1].click()"
  });
  await sleep(250);
  const shot = await send("Page.captureScreenshot", {
    format: "png",
    captureBeyondViewport: false
  });
  fs.mkdirSync(path.dirname(output), { recursive: true });
  fs.writeFileSync(output, Buffer.from(shot.data, "base64"));
  await send("Runtime.evaluate", {
    expression: "document.getElementById('selectedStyleEditor').scrollIntoView({block:'center'})"
  });
  await sleep(350);
  const styleShot = await send("Page.captureScreenshot", {
    format: "png",
    captureBeyondViewport: false
  });
  fs.writeFileSync(outputStyle, Buffer.from(styleShot.data, "base64"));
  await send("Runtime.evaluate", {
    expression: "document.querySelector('[data-category=\"skill\"]').click(); document.querySelector('.style-library').scrollIntoView({block:'start'}); window.scrollBy(0,-330)"
  });
  await sleep(450);
  await send("Runtime.evaluate", {
    expression: "document.getElementById('toast').style.display='none'"
  });
  const skillsShot = await send("Page.captureScreenshot", {
    format: "png",
    captureBeyondViewport: false
  });
  fs.writeFileSync(outputSkills, Buffer.from(skillsShot.data, "base64"));
  await send("Runtime.evaluate", {
    expression: "const card=[...document.querySelectorAll('.style-card')].find((item)=>item.querySelector('strong')?.textContent==='旅行冰箱贴'); card.click(); document.getElementById('selectedStyleEditor').scrollIntoView({block:'center'})"
  });
  await sleep(450);
  await send("Runtime.evaluate", {
    expression: "document.getElementById('toast').style.display='none'"
  });
  const travelSkillShot = await send("Page.captureScreenshot", {
    format: "png",
    captureBeyondViewport: false
  });
  fs.writeFileSync(outputTravelSkill, Buffer.from(travelSkillShot.data, "base64"));
  await send("Runtime.evaluate", {
    expression: "document.querySelector('[data-category=\"food\"]').click(); document.getElementById('styleGrid').scrollIntoView({block:'start'})"
  });
  await sleep(450);
  const foodShot = await send("Page.captureScreenshot", {
    format: "png",
    captureBeyondViewport: false
  });
  fs.writeFileSync(outputFoodLibrary, Buffer.from(foodShot.data, "base64"));
  await send("Runtime.evaluate", {
    expression: "window.scrollTo(0, document.documentElement.scrollHeight)"
  });
  await sleep(500);
  const bottomShot = await send("Page.captureScreenshot", {
    format: "png",
    captureBeyondViewport: false
  });
  fs.writeFileSync(outputBottom, Buffer.from(bottomShot.data, "base64"));
  await send("Runtime.evaluate", {
    expression: "window.scrollTo(0,0); document.getElementById('settingsBtn').click()"
  });
  await sleep(350);
  const settingsShot = await send("Page.captureScreenshot", {
    format: "png",
    captureBeyondViewport: false
  });
  fs.writeFileSync(outputSettings, Buffer.from(settingsShot.data, "base64"));
  await send("Runtime.evaluate", {
    expression: "document.querySelector('#settingsModal .modal-close').click(); document.getElementById('helpBtn').click()"
  });
  await sleep(350);
  const helpShot = await send("Page.captureScreenshot", {
    format: "png",
    captureBeyondViewport: false
  });
  fs.writeFileSync(outputHelp, Buffer.from(helpShot.data, "base64"));
  await send("Runtime.evaluate", {
    expression: "document.querySelector('#helpModal .modal-close').click(); document.querySelector('[data-page=\"beautyPage\"]').click()"
  });
  await sleep(450);
  const beautyShot = await send("Page.captureScreenshot", {
    format: "png",
    captureBeyondViewport: false
  });
  fs.writeFileSync(outputBeauty, Buffer.from(beautyShot.data, "base64"));
  await send("Runtime.evaluate", {
    expression: "document.querySelector('[data-page=\"effectPage\"]').click()"
  });
  await sleep(450);
  const effectsShot = await send("Page.captureScreenshot", {
    format: "png",
    captureBeyondViewport: false
  });
  fs.writeFileSync(outputEffects, Buffer.from(effectsShot.data, "base64"));
  await send("Runtime.evaluate", {
    expression: "document.getElementById('effectGrid').scrollIntoView({block:'start'})"
  });
  await sleep(350);
  const effectLibraryShot = await send("Page.captureScreenshot", {
    format: "png",
    captureBeyondViewport: false
  });
  fs.writeFileSync(outputEffectLibrary, Buffer.from(effectLibraryShot.data, "base64"));
  await send("Runtime.evaluate", {
    expression: "document.getElementById('effectSizeSelect').scrollIntoView({block:'center'})"
  });
  await sleep(350);
  const effectSizeShot = await send("Page.captureScreenshot", {
    format: "png",
    captureBeyondViewport: false
  });
  fs.writeFileSync(outputEffectSize, Buffer.from(effectSizeShot.data, "base64"));
  console.log(output);
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
