/* =========================================================
   AURORA — A Guardiã da Luz
   Jogo de ação/plataforma em Canvas 2D (gráficos vetoriais)
   ========================================================= */
"use strict";

/* ---------- Canvas & contexto ---------- */
const canvas = document.getElementById("game");
let ctx = canvas.getContext("2d");
// Executa uma função de desenho redirecionando temporariamente o contexto global
function withContext(c, fn) { const old = ctx; ctx = c; try { fn(); } finally { ctx = old; } }
let VW = 1280, VH = 720; // resolução virtual (mundo)
let scale = 1, offX = 0, offY = 0;

function resize() {
  const w = window.innerWidth, h = window.innerHeight;
  const dpr = Math.min(window.devicePixelRatio || 1, 2);
  canvas.width = w * dpr;
  canvas.height = h * dpr;
  canvas.style.width = w + "px";
  canvas.style.height = h + "px";
  // manter proporção 16:9 (letterbox)
  scale = Math.min(w / VW, h / VH);
  offX = (w - VW * scale) / 2;
  offY = (h - VH * scale) / 2;
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
}
window.addEventListener("resize", resize);
resize();

/* ---------- Utilidades ---------- */
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const lerp = (a, b, t) => a + (b - a) * t;
const rand = (a, b) => a + Math.random() * (b - a);
const now = () => performance.now();
function aabb(a, b) {
  return a.x < b.x + b.w && a.x + a.w > b.x && a.y < b.y + b.h && a.y + a.h > b.y;
}
function roundRect(c, x, y, w, h, r) {
  r = Math.min(r, w / 2, h / 2);
  c.beginPath();
  c.moveTo(x + r, y);
  c.arcTo(x + w, y, x + w, y + h, r);
  c.arcTo(x + w, y + h, x, y + h, r);
  c.arcTo(x, y + h, x, y, r);
  c.arcTo(x, y, x + w, y, r);
  c.closePath();
}

/* ---------- Input ---------- */
const keys = {};
const pressed = {};
function keyName(e) {
  let k = e.key;
  if (k === " ") return " ";
  return k.length === 1 ? k.toLowerCase() : k;
}
window.addEventListener("keydown", (e) => {
  const k = keyName(e);
  if ([" ", "ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight"].includes(e.key)) e.preventDefault();
  if (!keys[k]) pressed[k] = true;
  keys[k] = true;
});
window.addEventListener("keyup", (e) => { keys[keyName(e)] = false; });

// Botões touch
document.querySelectorAll(".tbtn").forEach((btn) => {
  const k = btn.getAttribute("data-key");
  const down = (e) => { e.preventDefault(); if (!keys[k]) pressed[k] = true; keys[k] = true; };
  const up = (e) => { e.preventDefault(); keys[k] = false; };
  btn.addEventListener("touchstart", down, { passive: false });
  btn.addEventListener("touchend", up, { passive: false });
  btn.addEventListener("mousedown", down);
  btn.addEventListener("mouseup", up);
  btn.addEventListener("mouseleave", up);
});
if ("ontouchstart" in window) {
  const tc = document.getElementById("touch-controls");
  tc.classList.remove("hidden");
  tc.classList.add("show");
}

function consume(k) { if (pressed[k]) { pressed[k] = false; return true; } return false; }

/* ---------- Suporte a controle USB (Gamepad API) ---------- */
// Mapeamento padrão (Xbox/PS/genérico USB):
// A=pular  B=dash  X=atacar  Y=feixe  RB=nova  LB=interagir  Start=pausar
const GP_MAP = { 0: " ", 1: "l", 2: "k", 3: "u", 5: "o", 4: "e", 9: "p", 8: "e" };
const gpPrev = {};       // estado anterior dos botões (borda de subida)
const gpHeld = {};       // teclas que o gamepad está segurando
let gamepadConnected = false;

if (typeof window !== "undefined" && window.addEventListener) {
  window.addEventListener("gamepadconnected", () => { gamepadConnected = true; });
  window.addEventListener("gamepaddisconnected", () => {
    gamepadConnected = false;
    for (const k in gpHeld) { if (gpHeld[k]) keys[k] = false; gpHeld[k] = false; }
  });
}

function setGpKey(k, on) {
  if (on) { keys[k] = true; gpHeld[k] = true; }
  else if (gpHeld[k]) { keys[k] = false; gpHeld[k] = false; }
}

function pollGamepad() {
  const nav = (typeof navigator !== "undefined") ? navigator : null;
  if (!nav || !nav.getGamepads) return;
  const pads = nav.getGamepads();
  let pad = null;
  for (const p of pads) { if (p && p.connected !== false) { pad = p; break; } }
  if (!pad) return;
  gamepadConnected = true;

  // Movimento: analógico esquerdo + D-pad
  const ax = pad.axes && pad.axes.length ? pad.axes[0] : 0;
  const dpadL = pad.buttons[14] && pad.buttons[14].pressed;
  const dpadR = pad.buttons[15] && pad.buttons[15].pressed;
  setGpKey("ArrowLeft", ax < -0.35 || dpadL);
  setGpKey("ArrowRight", ax > 0.35 || dpadR);
  // Pular também pelo direcional para cima
  const up = (pad.buttons[12] && pad.buttons[12].pressed) || (pad.axes[1] || 0) < -0.5;

  // Botões de ação
  for (const idx in GP_MAP) {
    const k = GP_MAP[idx];
    const b = pad.buttons[idx];
    const down = !!(b && b.pressed);
    if (down && !gpPrev[idx]) pressed[k] = true;
    setGpKey(k, down);
    gpPrev[idx] = down;
  }
  // borda de subida do "para cima" -> pulo
  if (up && !gpPrev.up) pressed[" "] = true;
  gpPrev.up = up;
}


/* ---------- Câmera ---------- */
const camera = {
  x: 0, y: 0, shake: 0,
  follow(target, level) {
    const tx = target.x + target.w / 2 - VW / 2;
    const ty = target.y + target.h / 2 - VH / 2;
    this.x = lerp(this.x, tx, 0.08);
    this.y = lerp(this.y, ty, 0.08);
    this.x = clamp(this.x, 0, Math.max(0, level.width - VW));
    this.y = clamp(this.y, -120, Math.max(0, level.height - VH));
    if (this.shake > 0.2) this.shake *= 0.9; else this.shake = 0;
  },
  get sx() { return this.x + (this.shake ? rand(-this.shake, this.shake) : 0); },
  get sy() { return this.y + (this.shake ? rand(-this.shake, this.shake) : 0); },
};

/* ---------- Partículas ---------- */
const particles = [];
function spawnParticle(o) { particles.push(Object.assign({ life: 1, maxLife: 1, vx: 0, vy: 0, g: 0, r: 3, glow: false }, o)); }
function burst(x, y, color, n, opts = {}) {
  for (let i = 0; i < n; i++) {
    const a = rand(0, Math.PI * 2), sp = rand(opts.min || 1, opts.max || 6);
    spawnParticle({
      x, y, vx: Math.cos(a) * sp, vy: Math.sin(a) * sp - (opts.up || 0),
      g: opts.g ?? 0.15, r: rand(opts.rmin || 2, opts.rmax || 5),
      color, life: rand(0.4, opts.maxLife || 1), maxLife: opts.maxLife || 1, glow: opts.glow ?? true,
    });
  }
}
function updateParticles(dt) {
  for (let i = particles.length - 1; i >= 0; i--) {
    const p = particles[i];
    p.vy += p.g; p.x += p.vx; p.y += p.vy; p.vx *= 0.98;
    p.life -= dt * 1.4;
    if (p.life <= 0) particles.splice(i, 1);
  }
}
function drawParticles() {
  for (const p of particles) {
    const a = clamp(p.life / p.maxLife, 0, 1);
    ctx.globalAlpha = a;
    if (p.glow) { ctx.shadowColor = p.color; ctx.shadowBlur = 14; }
    ctx.fillStyle = p.color;
    ctx.beginPath();
    ctx.arc(p.x - camera.sx, p.y - camera.sy, p.r * a, 0, Math.PI * 2);
    ctx.fill();
    ctx.shadowBlur = 0;
  }
  ctx.globalAlpha = 1;
}

/* ---------- Estrelas / poeira de fundo ---------- */
const stars = [];
for (let i = 0; i < 90; i++) stars.push({ x: rand(0, VW), y: rand(0, VH), r: rand(0.6, 2.2), a: rand(0.2, 1), tw: rand(0.5, 2) });
let motes = [];
function initMotes() {
  motes = [];
  for (let i = 0; i < 40; i++) motes.push({ x: rand(0, VW * 2), y: rand(0, VH), r: rand(1, 3), vy: rand(-0.3, -0.9), a: rand(0.1, 0.5) });
}


/* ---------- Temas / cenários de fundo ---------- */
const THEMES = {
  forest: { sky: ["#2a1650", "#3d1f63", "#1a0e2e"], glow: "#7b4bff", hill: ["#241241", "#160a2b"], accent: "#a86bff", ground: ["#3a2456", "#241436"] },
  ruins:  { sky: ["#0d2b3a", "#124257", "#08202b"], glow: "#39c8d8", hill: ["#0e3345", "#082330"], accent: "#4fe0e0", ground: ["#123a4a", "#0a2530"] },
  citadel:{ sky: ["#2b0b1d", "#450f2c", "#170510"], glow: "#ff3b7f", hill: ["#3a0c22", "#1c0611"], accent: "#ff5c93", ground: ["#3a1024", "#1e0813"] },
  swamp:  { sky: ["#12321f", "#1c4a2e", "#0a2216"], glow: "#5fe08a", hill: ["#123a24", "#0a2417"], accent: "#7dffb0", ground: ["#1d4a2f", "#10301e"] },
  frost:  { sky: ["#153044", "#22506e", "#0c1f2e"], glow: "#8fd8ff", hill: ["#1a3c54", "#0e2536"], accent: "#c9f0ff", ground: ["#274d66", "#16303f"] },
  cavern: { sky: ["#241a12", "#3a2a18", "#140d08"], glow: "#e0a35f", hill: ["#3a2a18", "#1c140b"], accent: "#ffcf8a", ground: ["#43301c", "#241810"] },
  volcano:{ sky: ["#340c0c", "#5a1410", "#1a0605"], glow: "#ff7a3b", hill: ["#4a1210", "#240806"], accent: "#ff9c5c", ground: ["#4a1a12", "#26100a"] },
  crypt:  { sky: ["#1a1a24", "#2a2438", "#0c0c14"], glow: "#8affc0", hill: ["#24242f", "#12121a"], accent: "#9dffb8", ground: ["#2a2a38", "#161620"] },
  sky:    { sky: ["#1b3a6b", "#3f6fb0", "#8fc0f0"], glow: "#ffffff", hill: ["#5a86c0", "#3a5f96"], accent: "#ffffff", ground: ["#6a9fd6", "#3f6fb0"] },
  storm:  { sky: ["#0e1622", "#1c2a3e", "#080d16"], glow: "#8fb4ff", hill: ["#182437", "#0c1420"], accent: "#bcd4ff", ground: ["#20304a", "#101828"] },
  peak:   { sky: ["#4a7ec0", "#8fc0f0", "#e8f4ff"], glow: "#ffffff", hill: ["#c8e0f5", "#9fc4e8"], accent: "#ffffff", ground: ["#dcecfa", "#a8cbe8"] },
};

function drawBackground(theme, level, t) {
  const th = THEMES[theme];
  // céu
  const g = ctx.createLinearGradient(0, 0, 0, VH);
  g.addColorStop(0, th.sky[0]); g.addColorStop(0.55, th.sky[1]); g.addColorStop(1, th.sky[2]);
  ctx.fillStyle = g;
  ctx.fillRect(0, 0, VW, VH);

  // halo de luz central
  const rg = ctx.createRadialGradient(VW * 0.5, VH * 0.28, 30, VW * 0.5, VH * 0.28, VW * 0.6);
  rg.addColorStop(0, th.glow + "55"); rg.addColorStop(1, "transparent");
  ctx.fillStyle = rg;
  ctx.fillRect(0, 0, VW, VH);

  // estrelas (parallax leve)
  for (const s of stars) {
    const px = (s.x - camera.x * 0.15) % VW; const x = px < 0 ? px + VW : px;
    ctx.globalAlpha = s.a * (0.6 + 0.4 * Math.sin(t * s.tw + s.x));
    ctx.fillStyle = "#fff";
    ctx.beginPath(); ctx.arc(x, s.y, s.r, 0, Math.PI * 2); ctx.fill();
  }
  ctx.globalAlpha = 1;

  // colinas distantes (parallax)
  drawHills(camera.x * 0.25, VH * 0.62, 180, th.hill[0], level.width, 90, 0.7);
  drawHills(camera.x * 0.45, VH * 0.72, 240, th.hill[1], level.width, 140, 1);

  // partículas flutuantes (motes)
  for (const m of motes) {
    m.y += m.vy; if (m.y < -10) { m.y = VH + 10; m.x = rand(0, VW); }
    const x = ((m.x - camera.x * 0.6) % (VW)); const px = x < 0 ? x + VW : x;
    ctx.globalAlpha = m.a; ctx.fillStyle = th.accent; ctx.shadowColor = th.accent; ctx.shadowBlur = 8;
    ctx.beginPath(); ctx.arc(px, m.y, m.r, 0, Math.PI * 2); ctx.fill();
  }
  ctx.shadowBlur = 0; ctx.globalAlpha = 1;

  // efeitos de cenário animado (vulcão, raios, ventania, neve, brasas)
  drawAmbient(theme, level, t);
}

function drawHills(scrollX, baseY, amp, color, width, step, alpha) {
  ctx.globalAlpha = alpha;
  ctx.fillStyle = color;
  ctx.beginPath();
  ctx.moveTo(-50, VH);
  const off = -(scrollX % (step * 2));
  for (let x = off - step; x <= VW + step; x += step) {
    const seed = Math.round((x + scrollX) / step);
    const h = baseY - (Math.abs(Math.sin(seed * 1.7)) * amp);
    ctx.quadraticCurveTo(x - step / 2, h - 20, x, h);
  }
  ctx.lineTo(VW + 50, VH); ctx.closePath(); ctx.fill();
  ctx.globalAlpha = 1;
}

/* ---------- Clima / cenário animado ---------- */
const THEME_WEATHER = { volcano: "ash", cavern: "ash", frost: "snow", storm: "storm", sky: "wind", peak: "wind" };
const weather = { type: "none", flash: 0, flashCd: 4, boltX: 0 };
let rainDrops = [], snowFlakes = [], windStreaks = [], embers = [], volcanoes = [];
function initWeather(level) {
  weather.type = level.weather || THEME_WEATHER[level.theme] || "none";
  weather.flash = 0; weather.flashCd = rand(2, 5); weather.boltX = VW / 2;
  rainDrops = []; snowFlakes = []; windStreaks = []; embers = []; volcanoes = [];
  for (let i = 0; i < 140; i++) rainDrops.push({ x: rand(0, VW), y: rand(0, VH), len: rand(12, 24), sp: rand(16, 24) });
  for (let i = 0; i < 100; i++) snowFlakes.push({ x: rand(0, VW), y: rand(0, VH), r: rand(1.2, 3.4), sw: rand(0.4, 1.3), ph: rand(0, 6), sp: rand(0.6, 1.6) });
  for (let i = 0; i < 26; i++) windStreaks.push({ x: rand(0, VW), y: rand(0, VH), len: rand(50, 140), sp: rand(7, 13), a: rand(0.05, 0.16) });
  for (let i = 0; i < 46; i++) embers.push({ x: rand(0, VW), y: rand(0, VH), r: rand(1, 3.2), sp: rand(0.5, 1.6), drift: rand(-0.5, 0.5), ph: rand(0, 6) });
  // vulcões ao fundo em cenários de fogo
  if (level.theme === "volcano" || level.theme === "cavern" || level.weather === "ash") {
    volcanoes.push({ wx: level.width * 0.35, erupt: 0, cd: rand(3, 6) });
    volcanoes.push({ wx: level.width * 0.72, erupt: 0, cd: rand(4, 8) });
  }
}
function updateWeather(dt) {
  const w = weather.type;
  if (w === "storm") {
    weather.flashCd -= dt;
    if (weather.flash > 0) weather.flash -= dt * 3;
    if (weather.flashCd <= 0) { weather.flash = 1; weather.flashCd = rand(2.5, 6); weather.boltX = rand(VW * 0.15, VW * 0.85); camera.shake = Math.max(camera.shake, 5); }
    for (const r of rainDrops) { r.y += r.sp; r.x -= r.sp * 0.35; if (r.y > VH) { r.y = -10; r.x = rand(0, VW + 200); } if (r.x < -20) r.x = VW + 20; }
  }
  if (w === "snow") for (const s of snowFlakes) { s.ph += dt * 2; s.y += s.sp; s.x += Math.sin(s.ph) * 0.6; if (s.y > VH) { s.y = -8; s.x = rand(0, VW); } }
  if (w === "wind") for (const s of windStreaks) { s.x -= s.sp; if (s.x < -s.len) { s.x = VW + rand(0, 100); s.y = rand(0, VH); } }
  if (w === "ash") for (const e of embers) { e.ph += dt * 2; e.y -= e.sp; e.x += Math.sin(e.ph) * e.drift; if (e.y < -10) { e.y = VH + 10; e.x = rand(0, VW); } }
  for (const v of volcanoes) { v.cd -= dt; if (v.erupt > 0) v.erupt -= dt; if (v.cd <= 0) { v.erupt = 1.2; v.cd = rand(4, 9); } }
}
function drawVolcano(v, level, t) {
  const x = v.wx - camera.x * 0.2;
  const sx = ((x % (level.width)) + level.width) % level.width; // não repete fora da fase, usa direto
  const px = v.wx - camera.x * 0.2;
  if (px < -300 || px > VW + 300) return;
  const baseY = VH * 0.76, w = 240, h = 200;
  // montanha
  const g = ctx.createLinearGradient(0, baseY - h, 0, baseY);
  g.addColorStop(0, "#3a1408"); g.addColorStop(1, "#160805");
  ctx.fillStyle = g;
  ctx.beginPath(); ctx.moveTo(px - w, baseY); ctx.lineTo(px - 34, baseY - h); ctx.lineTo(px + 34, baseY - h); ctx.lineTo(px + w, baseY); ctx.closePath(); ctx.fill();
  // cratera brilhante
  const glow = 0.4 + v.erupt * 0.6;
  ctx.fillStyle = `rgba(255,120,40,${glow})`; ctx.shadowColor = "#ff7a3b"; ctx.shadowBlur = 30 + v.erupt * 30;
  ctx.beginPath(); ctx.ellipse(px, baseY - h + 4, 34, 10, 0, 0, Math.PI * 2); ctx.fill();
  ctx.shadowBlur = 0;
  // lava escorrendo
  ctx.strokeStyle = `rgba(255,90,30,${0.5 + v.erupt * 0.4})`; ctx.lineWidth = 3;
  ctx.beginPath(); ctx.moveTo(px - 10, baseY - h + 6); ctx.quadraticCurveTo(px - 40, baseY - h / 2, px - 30, baseY); ctx.stroke();
  // erupção: jato de lava
  if (v.erupt > 0) {
    for (let i = 0; i < 3; i++) {
      const a = -Math.PI / 2 + rand(-0.5, 0.5);
      spawnParticle({ x: px + camera.sx + rand(-20, 20), y: baseY - h + camera.sy, vx: Math.cos(a) * rand(2, 5), vy: Math.sin(a) * rand(4, 8), g: 0.2, r: rand(2, 5), color: "#ff7a3b", life: 1.2, maxLife: 1.2, glow: true });
    }
  }
}
function drawAmbient(theme, level, t) {
  const w = weather.type;
  // vulcões ao fundo
  for (const v of volcanoes) drawVolcano(v, level, t);
  // brasas subindo
  if (w === "ash") {
    for (const e of embers) {
      ctx.globalAlpha = 0.5; ctx.fillStyle = e.ph % 2 < 1 ? "#ff9c5c" : "#ffcf8a";
      ctx.shadowColor = "#ff7a3b"; ctx.shadowBlur = 6;
      ctx.beginPath(); ctx.arc(e.x, e.y, e.r, 0, Math.PI * 2); ctx.fill();
    }
    ctx.shadowBlur = 0; ctx.globalAlpha = 1;
  }
  // neve
  if (w === "snow") {
    ctx.fillStyle = "#ffffff";
    for (const s of snowFlakes) { ctx.globalAlpha = 0.35 + s.sw * 0.4; ctx.beginPath(); ctx.arc(s.x, s.y, s.r, 0, Math.PI * 2); ctx.fill(); }
    ctx.globalAlpha = 1;
  }
  // ventania (nuvens/riscos horizontais)
  if (w === "wind") {
    ctx.strokeStyle = "#ffffff"; ctx.lineCap = "round";
    for (const s of windStreaks) { ctx.globalAlpha = s.a; ctx.lineWidth = 3; ctx.beginPath(); ctx.moveTo(s.x, s.y); ctx.quadraticCurveTo(s.x + s.len / 2, s.y - 6, s.x + s.len, s.y); ctx.stroke(); }
    ctx.globalAlpha = 1;
  }
  // tempestade: chuva + relâmpagos
  if (w === "storm") {
    ctx.strokeStyle = "rgba(180,210,255,0.5)"; ctx.lineWidth = 1.6; ctx.lineCap = "round";
    for (const r of rainDrops) { ctx.beginPath(); ctx.moveTo(r.x, r.y); ctx.lineTo(r.x - r.len * 0.35, r.y + r.len); ctx.stroke(); }
    if (weather.flash > 0) {
      ctx.fillStyle = `rgba(200,220,255,${clamp(weather.flash, 0, 1) * 0.45})`;
      ctx.fillRect(0, 0, VW, VH);
      // raio ramificado
      ctx.strokeStyle = "#eaf2ff"; ctx.shadowColor = "#bcd4ff"; ctx.shadowBlur = 24; ctx.lineWidth = 3;
      let bx = weather.boltX, by = 0;
      ctx.beginPath(); ctx.moveTo(bx, by);
      for (let s = 0; s < 8; s++) { bx += rand(-40, 40); by += VH / 8; ctx.lineTo(bx, by); }
      ctx.stroke(); ctx.shadowBlur = 0;
    }
  }
}

/* ---------- Plataformas ---------- */
function drawPlatform(p, theme) {
  const th = THEMES[theme];
  const x = p.x - camera.sx, y = p.y - camera.sy;
  const g = ctx.createLinearGradient(0, y, 0, y + p.h);
  g.addColorStop(0, th.ground[0]); g.addColorStop(1, th.ground[1]);
  ctx.fillStyle = g;
  roundRect(ctx, x, y, p.w, p.h, 10); ctx.fill();
  // borda luminosa no topo
  ctx.fillStyle = th.accent;
  ctx.globalAlpha = 0.85; ctx.shadowColor = th.accent; ctx.shadowBlur = 12;
  roundRect(ctx, x + 3, y, p.w - 6, 5, 3); ctx.fill();
  ctx.shadowBlur = 0; ctx.globalAlpha = 1;
  // textura/relevo
  ctx.strokeStyle = "rgba(255,255,255,0.05)"; ctx.lineWidth = 2;
  for (let i = 20; i < p.w - 10; i += 42) {
    ctx.beginPath(); ctx.moveTo(x + i, y + 12); ctx.lineTo(x + i, y + p.h - 6); ctx.stroke();
  }
}


/* =========================================================
   DESENHO DOS PERSONAGENS (vetorial, anti-aliased)
   ========================================================= */

/* ---------- Aurora (heroína) ---------- */
function drawAurora(a, screenX, screenY) {
  const cx = screenX + a.w / 2;
  const cy = screenY + a.h / 2;
  const face = a.facing; // 1 direita, -1 esquerda
  const walk = a.walkPhase;
  const legSwing = a.onGround && Math.abs(a.vx) > 0.4 ? Math.sin(walk) * 9 : 0;
  const legSwing2 = a.onGround && Math.abs(a.vx) > 0.4 ? Math.sin(walk + Math.PI) * 9 : 0;

  ctx.save();
  ctx.translate(cx, cy);
  ctx.scale(face, 1);

  // aura de luz
  const aura = ctx.createRadialGradient(0, 0, 4, 0, 0, 60);
  aura.addColorStop(0, "rgba(255,224,150,0.35)");
  aura.addColorStop(1, "rgba(255,224,150,0)");
  ctx.fillStyle = aura;
  ctx.beginPath(); ctx.arc(0, -4, 60, 0, Math.PI * 2); ctx.fill();

  // sombra no chão
  ctx.fillStyle = "rgba(0,0,0,0.28)";
  ctx.beginPath(); ctx.ellipse(0, a.h / 2 - 2, 20, 6, 0, 0, Math.PI * 2); ctx.fill();

  // ----- PERNAS -----
  const hipY = 14;
  drawLimb(0, -6, hipY, legSwing, 16, "#3a2b6e", 8, "#5a48a0");   // perna trás
  drawLimb(0, -6, hipY, legSwing2, 16, "#4a3688", 8, "#6a54b8");  // perna frente

  // ----- CORPO / VESTIDO -----
  const dress = ctx.createLinearGradient(0, -18, 0, 22);
  dress.addColorStop(0, "#7a5bd6");
  dress.addColorStop(0.5, "#5a3fb0");
  dress.addColorStop(1, "#3a2680");
  ctx.fillStyle = dress;
  ctx.beginPath();
  ctx.moveTo(-8, -20);
  ctx.lineTo(8, -20);
  ctx.quadraticCurveTo(15, 0, 16, 18);       // saia esquerda
  ctx.quadraticCurveTo(0, 24, -16, 18);      // barra
  ctx.quadraticCurveTo(-15, 0, -8, -20);
  ctx.closePath(); ctx.fill();
  // faixa luminosa
  ctx.strokeStyle = "#ffe08a"; ctx.lineWidth = 3; ctx.shadowColor = "#ffe08a"; ctx.shadowBlur = 8;
  ctx.beginPath(); ctx.moveTo(-8, -8); ctx.lineTo(9, -6); ctx.stroke();
  ctx.shadowBlur = 0;

  // ----- BRAÇOS -----
  const armSwing = a.attackTimer > 0 ? -1.1 : (a.onGround ? Math.sin(walk + Math.PI) * 0.5 : -0.3);
  // braço de trás
  ctx.strokeStyle = "#c9a06a"; ctx.lineWidth = 6; ctx.lineCap = "round";
  ctx.beginPath(); ctx.moveTo(-4, -16);
  ctx.lineTo(-4 + Math.cos(armSwing + 1.7) * 16, -16 + Math.sin(armSwing + 1.7) * 16);
  ctx.stroke();

  // ----- CABEÇA -----
  const headY = -30;
  // pescoço
  ctx.fillStyle = "#e9be8f"; ctx.fillRect(-4, -24, 8, 8);
  // cabelo de trás
  ctx.fillStyle = "#3a2154";
  ctx.beginPath(); ctx.arc(0, headY, 15, 0, Math.PI * 2); ctx.fill();
  // rosto
  const skin = ctx.createLinearGradient(0, headY - 12, 0, headY + 12);
  skin.addColorStop(0, "#ffe0b8"); skin.addColorStop(1, "#e9be8f");
  ctx.fillStyle = skin;
  ctx.beginPath(); ctx.arc(2, headY, 11, 0, Math.PI * 2); ctx.fill();
  // bochecha
  ctx.fillStyle = "rgba(255,150,150,0.35)";
  ctx.beginPath(); ctx.arc(6, headY + 3, 3, 0, Math.PI * 2); ctx.fill();
  // olho
  ctx.fillStyle = "#241033";
  ctx.beginPath(); ctx.ellipse(6, headY - 1, 2, 3, 0, 0, Math.PI * 2); ctx.fill();
  ctx.fillStyle = "#8fd8ff";
  ctx.beginPath(); ctx.arc(6.6, headY - 2, 1, 0, Math.PI * 2); ctx.fill();
  // sobrancelha
  ctx.strokeStyle = "#5a3a2a"; ctx.lineWidth = 1.3;
  ctx.beginPath(); ctx.moveTo(3, headY - 5); ctx.lineTo(9, headY - 5); ctx.stroke();
  // boca
  ctx.strokeStyle = "#b5605a"; ctx.lineWidth = 1.4;
  ctx.beginPath(); ctx.arc(6, headY + 5, 2.5, 0.1, Math.PI - 0.4); ctx.stroke();
  // cabelo da frente (franja) fluindo
  ctx.fillStyle = "#4a2a6e";
  ctx.beginPath();
  ctx.moveTo(-13, headY - 4);
  ctx.quadraticCurveTo(-2, headY - 18, 13, headY - 8);
  ctx.quadraticCurveTo(6, headY - 6, 8, headY - 2);
  ctx.quadraticCurveTo(0, headY - 10, -6, headY - 2);
  ctx.quadraticCurveTo(-12, headY + 2, -13, headY - 4);
  ctx.closePath(); ctx.fill();
  // mecha esvoaçante
  ctx.fillStyle = "#5a3a86";
  ctx.beginPath();
  ctx.moveTo(-12, headY - 2);
  ctx.quadraticCurveTo(-30 - Math.sin(walk * 0.5) * 4, headY + 6, -20, headY + 22);
  ctx.quadraticCurveTo(-14, headY + 8, -8, headY + 2);
  ctx.closePath(); ctx.fill();
  // coroa de luz
  ctx.fillStyle = "#ffe08a"; ctx.shadowColor = "#ffe08a"; ctx.shadowBlur = 10;
  ctx.beginPath(); ctx.moveTo(-6, headY - 12); ctx.lineTo(-4, headY - 18); ctx.lineTo(-2, headY - 12);
  ctx.lineTo(2, headY - 19); ctx.lineTo(6, headY - 12); ctx.closePath(); ctx.fill();
  ctx.shadowBlur = 0;

  // ----- BRAÇO DA FRENTE + CAJADO -----
  const handX = 6 + Math.cos(armSwing) * 16;
  const handY = -16 + Math.sin(armSwing) * 16;
  ctx.strokeStyle = "#e9be8f"; ctx.lineWidth = 6;
  ctx.beginPath(); ctx.moveTo(4, -16); ctx.lineTo(handX, handY); ctx.stroke();

  // cajado luminoso
  ctx.save();
  ctx.translate(handX, handY);
  ctx.rotate(a.attackTimer > 0 ? -0.5 - (1 - a.attackTimer / 0.28) * 1.2 : -0.2);
  ctx.strokeStyle = "#caa46a"; ctx.lineWidth = 4; ctx.lineCap = "round";
  ctx.beginPath(); ctx.moveTo(0, 12); ctx.lineTo(0, -26); ctx.stroke();
  // cristal
  const cr = ctx.createRadialGradient(0, -30, 1, 0, -30, 12);
  cr.addColorStop(0, "#ffffff"); cr.addColorStop(0.5, "#ffe08a"); cr.addColorStop(1, "rgba(255,180,80,0)");
  ctx.fillStyle = cr; ctx.beginPath(); ctx.arc(0, -30, 12, 0, Math.PI * 2); ctx.fill();
  ctx.fillStyle = "#fff6df"; ctx.shadowColor = "#ffe08a"; ctx.shadowBlur = 14;
  ctx.beginPath(); ctx.arc(0, -30, 4.5, 0, Math.PI * 2); ctx.fill();
  ctx.shadowBlur = 0;
  ctx.restore();

  ctx.restore();
}

/* desenha um membro simples (perna) articulado */
function drawLimb(ox, oy, len, swing, thick, color, footR, footColor) {
  ctx.save();
  ctx.strokeStyle = color; ctx.lineWidth = thick; ctx.lineCap = "round";
  const rad = swing * Math.PI / 180;
  const ex = ox + Math.sin(rad) * len;
  const ey = oy + len + Math.cos(rad) * 2;
  ctx.beginPath(); ctx.moveTo(ox, oy); ctx.lineTo(ex, ey); ctx.stroke();
  ctx.fillStyle = footColor || color;
  ctx.beginPath(); ctx.ellipse(ex + 2, ey + 2, footR, footR * 0.6, 0, 0, Math.PI * 2); ctx.fill();
  ctx.restore();
}


/* ---------- Sombra (inimigo terrestre humanoide) ---------- */
function drawShade(e, sx, sy) {
  const cx = sx + e.w / 2, cy = sy + e.h / 2;
  const face = e.facing;
  const bob = Math.sin(e.phase) * 2;
  ctx.save();
  ctx.translate(cx, cy + bob);
  ctx.scale(face, 1);

  // sombra chão
  ctx.fillStyle = "rgba(0,0,0,0.3)";
  ctx.beginPath(); ctx.ellipse(0, e.h / 2 - 2 - bob, 18, 5, 0, 0, Math.PI * 2); ctx.fill();

  const hurt = e.hurtTimer > 0;
  const bodyCol = hurt ? "#ff8ea3" : "#241035";
  // corpo esfumaçado
  const bg = ctx.createLinearGradient(0, -26, 0, 22);
  bg.addColorStop(0, hurt ? "#ffb3c1" : "#3a1a55");
  bg.addColorStop(1, bodyCol);
  ctx.fillStyle = bg;
  ctx.beginPath();
  ctx.moveTo(-13, -14);
  ctx.quadraticCurveTo(0, -30, 13, -14);
  ctx.quadraticCurveTo(18, 6, 12, 20);
  // barra irregular (fumaça)
  ctx.quadraticCurveTo(6, 14 + Math.sin(e.phase * 2) * 3, 0, 22);
  ctx.quadraticCurveTo(-6, 14 + Math.cos(e.phase * 2) * 3, -12, 20);
  ctx.quadraticCurveTo(-18, 6, -13, -14);
  ctx.closePath(); ctx.fill();

  // braços garras
  ctx.strokeStyle = bodyCol; ctx.lineWidth = 5; ctx.lineCap = "round";
  const aw = Math.sin(e.phase) * 0.4;
  ctx.beginPath(); ctx.moveTo(-10, -6); ctx.lineTo(-18, 6 + aw * 6); ctx.stroke();
  ctx.beginPath(); ctx.moveTo(10, -6); ctx.lineTo(18, 6 - aw * 6); ctx.stroke();

  // cabeça
  ctx.fillStyle = bodyCol;
  ctx.beginPath(); ctx.arc(0, -20, 12, 0, Math.PI * 2); ctx.fill();
  // chifres
  ctx.fillStyle = hurt ? "#ffb3c1" : "#160a24";
  ctx.beginPath(); ctx.moveTo(-9, -28); ctx.lineTo(-13, -38); ctx.lineTo(-5, -30); ctx.closePath(); ctx.fill();
  ctx.beginPath(); ctx.moveTo(9, -28); ctx.lineTo(13, -38); ctx.lineTo(5, -30); ctx.closePath(); ctx.fill();
  // olhos brilhantes
  const eye = hurt ? "#fff" : "#ff4d6d";
  ctx.fillStyle = eye; ctx.shadowColor = eye; ctx.shadowBlur = 10;
  ctx.beginPath(); ctx.ellipse(-4, -21, 2.4, 3.4, 0.2, 0, Math.PI * 2); ctx.fill();
  ctx.beginPath(); ctx.ellipse(5, -21, 2.4, 3.4, -0.2, 0, Math.PI * 2); ctx.fill();
  ctx.shadowBlur = 0;
  // boca irada
  ctx.strokeStyle = eye; ctx.lineWidth = 1.6;
  ctx.beginPath(); ctx.moveTo(-5, -13); ctx.lineTo(0, -11); ctx.lineTo(5, -13); ctx.stroke();

  ctx.restore();
}

/* ---------- Espectro (inimigo voador) ---------- */
function drawWisp(e, sx, sy) {
  const cx = sx + e.w / 2, cy = sy + e.h / 2;
  ctx.save();
  ctx.translate(cx, cy);
  const hurt = e.hurtTimer > 0;
  const col = hurt ? "#ffd0dc" : "#4fe0e0";
  // cauda de fumaça
  for (let i = 3; i >= 1; i--) {
    ctx.globalAlpha = 0.18 * i;
    ctx.fillStyle = col;
    ctx.beginPath();
    ctx.arc(-e.facing * i * 8, Math.sin(e.phase + i) * 4, 10 - i * 1.5, 0, Math.PI * 2);
    ctx.fill();
  }
  ctx.globalAlpha = 1;
  // corpo
  const bg = ctx.createRadialGradient(0, 0, 2, 0, 0, 16);
  bg.addColorStop(0, hurt ? "#fff" : "#d8ffff"); bg.addColorStop(1, col);
  ctx.fillStyle = bg; ctx.shadowColor = col; ctx.shadowBlur = 16;
  ctx.beginPath(); ctx.arc(0, 0, 14, 0, Math.PI * 2); ctx.fill();
  ctx.shadowBlur = 0;
  // rosto
  ctx.fillStyle = "#0a2b30";
  ctx.beginPath(); ctx.ellipse(-4, -2, 2, 3.2, 0, 0, Math.PI * 2); ctx.fill();
  ctx.beginPath(); ctx.ellipse(4, -2, 2, 3.2, 0, 0, Math.PI * 2); ctx.fill();
  ctx.strokeStyle = "#0a2b30"; ctx.lineWidth = 1.6;
  ctx.beginPath(); ctx.arc(0, 4, 3, 0.2, Math.PI - 0.2); ctx.stroke();
  ctx.restore();
}

/* ---------- Golem de Sombra (brute) ---------- */
function drawBrute(e, sx, sy) {
  const cx = sx + e.w / 2, cy = sy + e.h / 2;
  const hurt = e.hurtTimer > 0;
  ctx.save(); ctx.translate(cx, cy); ctx.scale(e.facing, 1);
  ctx.fillStyle = "rgba(0,0,0,0.32)";
  ctx.beginPath(); ctx.ellipse(0, e.h / 2 - 2, 30, 7, 0, 0, Math.PI * 2); ctx.fill();
  const rock = ctx.createLinearGradient(0, -40, 0, 40);
  rock.addColorStop(0, hurt ? "#ffb3c1" : "#6a4a86"); rock.addColorStop(1, hurt ? "#e07a92" : "#2e1c47");
  // pernas
  ctx.fillStyle = hurt ? "#e07a92" : "#2e1c47";
  roundRect(ctx, -22, 10, 16, 24, 5); ctx.fill();
  roundRect(ctx, 6, 10, 16, 24, 5); ctx.fill();
  // corpo rochoso
  ctx.fillStyle = rock;
  roundRect(ctx, -28, -30, 56, 48, 12); ctx.fill();
  // braços grandes
  ctx.fillStyle = hurt ? "#f0a0b4" : "#4a3168";
  roundRect(ctx, -40, -24, 16, 40, 7); ctx.fill();
  roundRect(ctx, 24, -24, 16, 40, 7); ctx.fill();
  // fissuras brilhantes
  ctx.strokeStyle = e.charging > 0 ? "#ff5c93" : "#a86bff"; ctx.lineWidth = 2.5; ctx.shadowColor = ctx.strokeStyle; ctx.shadowBlur = 10;
  ctx.beginPath(); ctx.moveTo(-14, -20); ctx.lineTo(-6, -6); ctx.lineTo(-12, 8); ctx.stroke();
  ctx.beginPath(); ctx.moveTo(10, -18); ctx.lineTo(4, -2); ctx.stroke();
  ctx.shadowBlur = 0;
  // cabeça
  ctx.fillStyle = rock;
  roundRect(ctx, -18, -52, 36, 26, 8); ctx.fill();
  // olhos
  const eye = hurt ? "#fff" : "#ff5c93";
  ctx.fillStyle = eye; ctx.shadowColor = eye; ctx.shadowBlur = 12;
  ctx.beginPath(); ctx.rect(-12, -44, 8, 5); ctx.rect(4, -44, 8, 5); ctx.fill();
  ctx.shadowBlur = 0;
  // boca (dentes de pedra)
  ctx.fillStyle = "#1a0f2a";
  ctx.beginPath(); ctx.rect(-10, -32, 20, 5); ctx.fill();
  ctx.fillStyle = rock;
  for (let i = -8; i < 10; i += 6) { ctx.beginPath(); ctx.rect(i, -32, 3, 5); ctx.fill(); }
  ctx.restore();
}

/* ---------- Morcego Corrompido (bat) ---------- */
function drawBat(e, sx, sy) {
  const cx = sx + e.w / 2, cy = sy + e.h / 2;
  const hurt = e.hurtTimer > 0;
  const flap = Math.sin(e.phase * 6) * 0.6;
  ctx.save(); ctx.translate(cx, cy); ctx.scale(e.facing, 1);
  const col = hurt ? "#ffd0dc" : "#3a1a55";
  // asas
  ctx.fillStyle = col;
  for (const s of [-1, 1]) {
    ctx.save(); ctx.scale(s, 1); ctx.rotate(flap * s);
    ctx.beginPath(); ctx.moveTo(6, -2);
    ctx.quadraticCurveTo(26, -12, 34, -2);
    ctx.quadraticCurveTo(26, -2, 30, 8);
    ctx.quadraticCurveTo(20, 2, 6, 6);
    ctx.closePath(); ctx.fill();
    ctx.restore();
  }
  // corpo
  ctx.fillStyle = hurt ? "#ffb3c1" : "#4a2668";
  ctx.beginPath(); ctx.ellipse(0, 0, 10, 12, 0, 0, Math.PI * 2); ctx.fill();
  // orelhas
  ctx.fillStyle = col;
  ctx.beginPath(); ctx.moveTo(-5, -8); ctx.lineTo(-8, -18); ctx.lineTo(-1, -10); ctx.closePath(); ctx.fill();
  ctx.beginPath(); ctx.moveTo(5, -8); ctx.lineTo(8, -18); ctx.lineTo(1, -10); ctx.closePath(); ctx.fill();
  // olhos
  const eye = hurt ? "#fff" : "#ff4d6d";
  ctx.fillStyle = eye; ctx.shadowColor = eye; ctx.shadowBlur = 8;
  ctx.beginPath(); ctx.arc(-3, -2, 2, 0, Math.PI * 2); ctx.arc(3, -2, 2, 0, Math.PI * 2); ctx.fill();
  ctx.shadowBlur = 0;
  // presas
  ctx.fillStyle = "#fff";
  ctx.beginPath(); ctx.moveTo(-2, 4); ctx.lineTo(-1, 8); ctx.lineTo(0, 4); ctx.closePath();
  ctx.moveTo(2, 4); ctx.lineTo(1, 8); ctx.lineTo(0, 4); ctx.closePath(); ctx.fill();
  ctx.restore();
}

/* ---------- Sentinela (turret) ---------- */
function drawTurret(e, sx, sy) {
  const cx = sx + e.w / 2, cy = sy + e.h / 2;
  const hurt = e.hurtTimer > 0;
  ctx.save(); ctx.translate(cx, cy);
  ctx.fillStyle = "rgba(0,0,0,0.3)";
  ctx.beginPath(); ctx.ellipse(0, e.h / 2 - 2, 24, 6, 0, 0, Math.PI * 2); ctx.fill();
  // base
  const base = ctx.createLinearGradient(0, -10, 0, 26);
  base.addColorStop(0, hurt ? "#e6f2ff" : "#3a5a70"); base.addColorStop(1, "#14232e");
  ctx.fillStyle = base;
  ctx.beginPath(); ctx.moveTo(-20, 26); ctx.lineTo(-12, -6); ctx.lineTo(12, -6); ctx.lineTo(20, 26); ctx.closePath(); ctx.fill();
  // cabeça-olho que gira
  const ang = Math.atan2((player.y) - (cy), (player.x) - (cx));
  ctx.save(); ctx.rotate(ang * (e.facing >= 0 ? 1 : 1));
  ctx.fillStyle = hurt ? "#fff" : "#22485e";
  ctx.beginPath(); ctx.arc(0, -14, 16, 0, Math.PI * 2); ctx.fill();
  // cano
  ctx.fillStyle = "#0e1c26"; roundRect(ctx, 4, -19, 20, 10, 4); ctx.fill();
  ctx.restore();
  // olho central brilhante
  const eye = hurt ? "#fff" : "#8fd8ff";
  ctx.fillStyle = eye; ctx.shadowColor = eye; ctx.shadowBlur = 16;
  ctx.beginPath(); ctx.arc(0, -14, 6, 0, Math.PI * 2); ctx.fill();
  ctx.fillStyle = "#06121a"; ctx.beginPath(); ctx.arc(0, -14, 2.6, 0, Math.PI * 2); ctx.fill();
  ctx.shadowBlur = 0;
  ctx.restore();
}

/* ---------- Cuspidor (spitter) ---------- */
function drawSpitter(e, sx, sy) {
  const cx = sx + e.w / 2, cy = sy + e.h / 2;
  const hurt = e.hurtTimer > 0;
  ctx.save(); ctx.translate(cx, cy); ctx.scale(e.facing, 1);
  ctx.fillStyle = "rgba(0,0,0,0.3)";
  ctx.beginPath(); ctx.ellipse(0, e.h / 2 - 2, 24, 6, 0, 0, Math.PI * 2); ctx.fill();
  // corpo bulboso
  const body = ctx.createRadialGradient(-4, 0, 4, 0, 6, 30);
  body.addColorStop(0, hurt ? "#eaffef" : "#2e6b45"); body.addColorStop(1, hurt ? "#ff9bb5" : "#123a24");
  ctx.fillStyle = body;
  ctx.beginPath(); ctx.ellipse(0, 6, 26, 20, 0, 0, Math.PI * 2); ctx.fill();
  // pústulas
  ctx.fillStyle = hurt ? "#fff" : "#7dffb0"; ctx.shadowColor = "#7dffb0"; ctx.shadowBlur = 8;
  for (const q of [[-12, 0], [8, -4], [0, 12]]) { ctx.beginPath(); ctx.arc(q[0], q[1], 3.5, 0, Math.PI * 2); ctx.fill(); }
  ctx.shadowBlur = 0;
  // boca aberta (canhão)
  ctx.fillStyle = "#0a1f12"; ctx.beginPath(); ctx.ellipse(16, -2, 7, 9, 0, 0, Math.PI * 2); ctx.fill();
  // olhos
  const eye = hurt ? "#fff" : "#d6ff8a";
  ctx.fillStyle = eye; ctx.shadowColor = eye; ctx.shadowBlur = 8;
  ctx.beginPath(); ctx.arc(-8, -10, 3, 0, Math.PI * 2); ctx.arc(2, -12, 3, 0, Math.PI * 2); ctx.fill();
  ctx.fillStyle = "#0a1f12";
  ctx.beginPath(); ctx.arc(-7, -10, 1.4, 0, Math.PI * 2); ctx.arc(3, -12, 1.4, 0, Math.PI * 2); ctx.fill();
  ctx.shadowBlur = 0;
  ctx.restore();
}

/* Dispatch de desenho por tipo de inimigo */
function drawEnemy(en, sx, sy) {
  switch (en.type) {
    case "shade": return drawShade(en, sx, sy);
    case "wisp": return drawWisp(en, sx, sy);
    case "brute": return drawBrute(en, sx, sy);
    case "bat": return drawBat(en, sx, sy);
    case "turret": return drawTurret(en, sx, sy);
    case "spitter": return drawSpitter(en, sx, sy);
    case "reaper": return drawReaper(en, sx, sy);
    case "mage": return drawMage(en, sx, sy);
    case "slime": return drawSlime(en, sx, sy);
    case "titan": return drawTitan(en, sx, sy);
    case "dragon": return drawDragon(en, sx, sy);
    default: return drawShade(en, sx, sy);
  }
}

/* ---------- NÖX (chefão) ---------- */
function drawNox(b, sx, sy) {
  const cx = sx + b.w / 2, cy = sy + b.h / 2;
  const t = b.phaseT;
  ctx.save();
  ctx.translate(cx, cy);
  ctx.scale(b.facing, 1);

  const hurt = b.hurtTimer > 0;
  // aura sombria pulsante
  const aura = ctx.createRadialGradient(0, 0, 20, 0, 0, 150);
  aura.addColorStop(0, hurt ? "rgba(255,120,160,0.5)" : "rgba(140,20,80,0.45)");
  aura.addColorStop(1, "rgba(20,5,15,0)");
  ctx.fillStyle = aura;
  ctx.beginPath(); ctx.arc(0, 0, 150 + Math.sin(t * 3) * 8, 0, Math.PI * 2); ctx.fill();

  // sombra
  ctx.fillStyle = "rgba(0,0,0,0.4)";
  ctx.beginPath(); ctx.ellipse(0, b.h / 2 - 4, 60, 14, 0, 0, Math.PI * 2); ctx.fill();

  // manto/corpo
  const body = ctx.createLinearGradient(0, -80, 0, 90);
  body.addColorStop(0, hurt ? "#ff7aa0" : "#4a0f2c");
  body.addColorStop(0.6, hurt ? "#c23a63" : "#2a0818");
  body.addColorStop(1, "#160510");
  ctx.fillStyle = body;
  ctx.beginPath();
  ctx.moveTo(-30, -50);
  ctx.quadraticCurveTo(0, -80, 30, -50);
  ctx.quadraticCurveTo(58, 20, 46, 90);
  ctx.quadraticCurveTo(20, 78 + Math.sin(t * 2) * 6, 0, 92);
  ctx.quadraticCurveTo(-20, 78 + Math.cos(t * 2) * 6, -46, 90);
  ctx.quadraticCurveTo(-58, 20, -30, -50);
  ctx.closePath(); ctx.fill();

  // ombreiras/espinhos
  ctx.fillStyle = hurt ? "#ffb3c9" : "#3a0c22";
  for (const s of [-1, 1]) {
    ctx.beginPath();
    ctx.moveTo(s * 30, -46); ctx.lineTo(s * 52, -70); ctx.lineTo(s * 40, -40); ctx.closePath(); ctx.fill();
  }

  // braços com garras
  ctx.strokeStyle = hurt ? "#ff9bb5" : "#2a0818"; ctx.lineWidth = 12; ctx.lineCap = "round";
  const arm = Math.sin(t * 1.5) * 0.3 + (b.attacking ? -0.8 : 0);
  for (const s of [-1, 1]) {
    ctx.beginPath();
    ctx.moveTo(s * 34, -34);
    ctx.lineTo(s * (60 + Math.cos(arm) * 8), 10 + Math.sin(arm) * 10);
    ctx.stroke();
    // garras
    ctx.strokeStyle = hurt ? "#fff" : "#ff3b7f"; ctx.lineWidth = 3;
    for (let f = -1; f <= 1; f++) {
      ctx.beginPath();
      ctx.moveTo(s * (60 + Math.cos(arm) * 8), 10 + Math.sin(arm) * 10);
      ctx.lineTo(s * (72 + Math.cos(arm) * 8), 22 + f * 8 + Math.sin(arm) * 10);
      ctx.stroke();
    }
    ctx.strokeStyle = hurt ? "#ff9bb5" : "#2a0818"; ctx.lineWidth = 12;
  }

  // cabeça
  ctx.fillStyle = hurt ? "#ff9bb5" : "#1c0611";
  ctx.beginPath(); ctx.arc(0, -58, 26, 0, Math.PI * 2); ctx.fill();

  // coroa quebrada (roubou o Coração de Luz)
  ctx.fillStyle = "#2a0818";
  ctx.beginPath();
  ctx.moveTo(-24, -74);
  ctx.lineTo(-24, -92); ctx.lineTo(-14, -80); ctx.lineTo(-6, -98);
  ctx.lineTo(2, -80); ctx.lineTo(12, -96); ctx.lineTo(20, -80);
  ctx.lineTo(24, -92); ctx.lineTo(24, -74);
  ctx.closePath(); ctx.fill();
  // gema roubada na coroa
  ctx.fillStyle = "#ffe08a"; ctx.shadowColor = "#ffe08a"; ctx.shadowBlur = 14;
  ctx.beginPath(); ctx.arc(-6, -88, 4, 0, Math.PI * 2); ctx.fill();
  ctx.shadowBlur = 0;

  // olhos ardentes
  const eye = hurt ? "#fff" : "#ff2e63";
  ctx.fillStyle = eye; ctx.shadowColor = eye; ctx.shadowBlur = 20;
  ctx.beginPath(); ctx.ellipse(-9, -60, 4, 6, 0.3, 0, Math.PI * 2); ctx.fill();
  ctx.beginPath(); ctx.ellipse(10, -60, 4, 6, -0.3, 0, Math.PI * 2); ctx.fill();
  ctx.shadowBlur = 0;
  // brilho pupila
  ctx.fillStyle = "#fff";
  ctx.beginPath(); ctx.arc(-8, -62, 1.4, 0, Math.PI * 2); ctx.fill();
  ctx.beginPath(); ctx.arc(11, -62, 1.4, 0, Math.PI * 2); ctx.fill();
  // boca sombria
  ctx.strokeStyle = eye; ctx.lineWidth = 2.5;
  ctx.beginPath(); ctx.moveTo(-10, -46); ctx.quadraticCurveTo(0, -40, 10, -46); ctx.stroke();
  // presas
  ctx.fillStyle = "#fff";
  ctx.beginPath(); ctx.moveTo(-5, -44); ctx.lineTo(-3, -38); ctx.lineTo(-1, -44); ctx.closePath(); ctx.fill();
  ctx.beginPath(); ctx.moveTo(5, -44); ctx.lineTo(3, -38); ctx.lineTo(1, -44); ctx.closePath(); ctx.fill();

  ctx.restore();
}


/* =========================================================
   ENTIDADES
   ========================================================= */
const GRAV = 0.62, MOVE = 0.9, MAXVX = 6.2, FRICTION = 0.82, JUMP = -14.2;

const player = {
  x: 0, y: 0, w: 34, h: 74, vx: 0, vy: 0,
  facing: 1, onGround: false, jumps: 0, maxJumps: 2,
  hp: 100, maxHp: 100, energy: 100, maxEnergy: 100,
  walkPhase: 0, attackTimer: 0, attackCd: 0,
  dashTimer: 0, dashCd: 0, invuln: 0, dead: false,
  novaCd: 0, novaTimer: 0, beamCd: 0, gliding: false, powerCd: 0,
  defense: 0, dmgMult: 1, rangeMult: 1,
  reset(x, y) {
    this.x = x; this.y = y; this.vx = 0; this.vy = 0;
    this.hp = this.maxHp; this.energy = this.maxEnergy;
    this.dead = false; this.invuln = 0; this.jumps = 0;
    this.novaCd = 0; this.novaTimer = 0; this.beamCd = 0; this.gliding = false; this.powerCd = 0;
  },
  get hitbox() { return { x: this.x, y: this.y, w: this.w, h: this.h }; },
};

function playerUpdate(dt, level) {
  const p = player;
  if (p.dead) return;

  // movimento horizontal
  let ax = 0;
  if (keys["ArrowLeft"] || keys["a"]) { ax = -MOVE; p.facing = -1; }
  if (keys["ArrowRight"] || keys["d"]) { ax = MOVE; p.facing = 1; }

  // dash
  if ((consume("l") || consume("Shift")) && p.dashCd <= 0 && p.energy >= 20) {
    p.dashTimer = 0.18; p.dashCd = 0.6; p.energy -= 20; p.invuln = Math.max(p.invuln, 0.22);
    burst(p.x + p.w / 2, p.y + p.h / 2, "#8fd8ff", 14, { glow: true, max: 5, g: 0 });
  }
  if (p.dashTimer > 0) {
    p.vx = p.facing * 12; p.dashTimer -= dt;
    if (Math.random() < 0.6) spawnParticle({ x: p.x + p.w / 2, y: p.y + rand(6, p.h - 6), vx: -p.facing * 2, vy: 0, g: 0, r: rand(2, 4), color: "#bfe9ff", life: 0.4, maxLife: 0.4, glow: true });
  } else {
    p.vx += ax;
    p.vx *= FRICTION;
    p.vx = clamp(p.vx, -MAXVX, MAXVX);
  }

  // pulo / pulo duplo
  if (consume(" ") || consume("ArrowUp") || consume("w")) {
    if (p.onGround || p.jumps < p.maxJumps) {
      p.vy = JUMP; p.onGround = false; p.jumps++;
      burst(p.x + p.w / 2, p.y + p.h, "#c9b3ff", 8, { up: 1, g: 0.1, max: 3 });
    }
  }
  // gravidade (+ PLANAR: segurar pulo enquanto cai reduz a gravidade)
  const holdJump = keys[" "] || keys["ArrowUp"] || keys["w"];
  p.gliding = false;
  if (!p.onGround && p.vy > 0.5 && holdJump && p.dashTimer <= 0) {
    p.vy += GRAV * 0.28;         // queda planada
    if (p.vy > 3.2) p.vy = 3.2;  // velocidade máxima de planeio
    p.gliding = true;
    if (Math.random() < 0.5) spawnParticle({ x: p.x + rand(0, p.w), y: p.y + p.h, vx: rand(-1, 1), vy: rand(0.4, 1.2), g: 0, r: rand(2, 4), color: "#bfe9ff", life: 0.4, maxLife: 0.4, glow: true });
  } else {
    p.vy += GRAV;
  }
  if (p.vy > 18) p.vy = 18;

  // integração + colisão
  moveWithCollision(p, level);

  // ataque corpo-a-corpo
  if (p.attackCd > 0) p.attackCd -= dt;
  if (p.attackTimer > 0) p.attackTimer -= dt;
  if ((consume("k") || consume("j") || consume("x")) && p.attackCd <= 0) {
    p.attackTimer = 0.28; p.attackCd = 0.34;
    doAttack(level);
  }

  // PODER: Feixe de Luz (ataque à distância) — tecla U / I
  if (p.beamCd > 0) p.beamCd -= dt;
  if ((consume("u") || consume("i")) && p.beamCd <= 0 && p.energy >= 12) {
    p.beamCd = 0.32; p.energy -= 12;
    level.projectiles.push({ x: p.x + p.w / 2 + p.facing * 20, y: p.y + p.h * 0.42, vx: p.facing * 11, vy: 0, r: 8, color: "#fff2c9", from: "player", dmg: 22, life: 1.6 });
    burst(p.x + p.w / 2 + p.facing * 24, p.y + p.h * 0.42, "#ffe08a", 8, { glow: true, max: 4, g: 0 });
  }

  // PODER: Nova de Luz (explosão em área) — tecla O / F
  if (p.novaCd > 0) p.novaCd -= dt;
  if (p.novaTimer > 0) p.novaTimer -= dt;
  if ((consume("o") || consume("f")) && p.novaCd <= 0 && p.energy >= 45) {
    p.novaCd = 1.4; p.energy -= 45; p.novaTimer = 0.4;
    doNova(level);
  }

  // PODERES DE CHEFÃO (teclas 1-4, desbloqueados ao vencer chefões)
  useBossPowers(dt, level);

  // timers
  if (p.dashCd > 0) p.dashCd -= dt;
  if (p.invuln > 0) p.invuln -= dt;
  p.energy = clamp(p.energy + dt * 16, 0, p.maxEnergy);

  // animação de caminhada
  if (p.onGround && Math.abs(p.vx) > 0.4) p.walkPhase += dt * 14 * (Math.abs(p.vx) / MAXVX + 0.4);

  // morte por queda
  if (p.y > level.height + 200) hurtPlayer(999, 0);
}

// sólidos = plataformas fixas + plataformas móveis
function getSolids(level) {
  return level.movers && level.movers.length ? level.platforms.concat(level.movers) : level.platforms;
}

function moveWithCollision(e, level) {
  const solids = getSolids(level);
  // X
  e.x += e.vx;
  for (const pl of solids) {
    if (aabb(e, pl)) {
      if (e.vx > 0) e.x = pl.x - e.w;
      else if (e.vx < 0) e.x = pl.x + pl.w;
      e.vx = 0;
    }
  }
  e.x = clamp(e.x, 0, level.width - e.w);
  // Y
  e.y += e.vy;
  e.onGround = false;
  for (const pl of solids) {
    if (aabb(e, pl)) {
      if (e.vy > 0) { e.y = pl.y - e.h; e.onGround = true; e.jumps = 0; e.standingOn = pl; }
      else if (e.vy < 0) e.y = pl.y + pl.h;
      e.vy = 0;
    }
  }
}

function doAttack(level) {
  const p = player;
  const range = 62 * (p.rangeMult || 1);
  const hit = {
    x: p.facing > 0 ? p.x + p.w - 6 : p.x - range + 6,
    y: p.y + 6, w: range, h: p.h - 14,
  };
  // efeito de corte (slash)
  level.slashes.push({ x: p.x + p.w / 2 + p.facing * 30, y: p.y + p.h / 2, t: 0.28, max: 0.28, dir: p.facing });
  burst(hit.x + (p.facing > 0 ? range : 0), p.y + p.h / 2, "#ffe08a", 10, { glow: true, max: 4, g: 0 });

  const dm = p.dmgMult || 1;
  for (const en of level.enemies) {
    if (en.dead) continue;
    if (aabb(hit, en)) damageEnemy(en, 34 * dm, p.facing, level);
  }
  if (level.boss && !level.boss.dead && aabb(hit, level.boss)) damageEnemy(level.boss, 24 * dm, p.facing, level);
}

// Nova de Luz: onda de choque radiante que atinge tudo por perto
function doNova(level) {
  const p = player;
  const cx = p.x + p.w / 2, cy = p.y + p.h / 2;
  const R = 200;
  level.novas.push({ x: cx, y: cy, r: 0, max: R, t: 0.4 });
  camera.shake = 12;
  burst(cx, cy, "#fff6df", 40, { glow: true, max: 9, g: 0, maxLife: 0.9 });
  const inRange = (e) => {
    const ex = e.x + e.w / 2, ey = e.y + e.h / 2;
    return Math.hypot(ex - cx, ey - cy) < R + Math.max(e.w, e.h) / 2;
  };
  for (const en of level.enemies) { if (!en.dead && inRange(en)) damageEnemy(en, 55, en.x + en.w / 2 > cx ? 1 : -1, level); }
  if (level.boss && !level.boss.dead && inRange(level.boss)) damageEnemy(level.boss, 40, level.boss.x + level.boss.w / 2 > cx ? 1 : -1, level);
  // destrói projéteis inimigos próximos
  for (let i = level.projectiles.length - 1; i >= 0; i--) {
    const pr = level.projectiles[i];
    if (pr.from === "enemy" && Math.hypot(pr.x - cx, pr.y - cy) < R) { burst(pr.x, pr.y, pr.color, 6, { glow: true, max: 4 }); level.projectiles.splice(i, 1); }
  }
}

function damageEnemy(en, dmg, dir, level) {
  en.hp -= dmg; en.hurtTimer = 0.16;
  en.vx = (dir || 1) * (en.boss ? 1.5 : 5);
  en.vy = -3;
  camera.shake = en.boss ? 8 : 5;
  burst(en.x + en.w / 2, en.y + en.h / 2, en.boss ? "#ff77aa" : "#ff9ecb", en.boss ? 16 : 10, { glow: true, max: 6 });
  if (en.hp <= 0) {
    en.dead = true;
    burst(en.x + en.w / 2, en.y + en.h / 2, en.boss ? "#ffe08a" : "#c9b3ff", en.boss ? 60 : 22, { glow: true, max: 9, maxLife: 1.4 });
    // soltar moedas
    if (level.coins) {
      if (en.boss) dropCoins(level, en.x + en.w / 2, en.y + en.h / 2, 24, 3);
      else dropCoins(level, en.x + en.w / 2, en.y + en.h / 2, 1 + Math.round((en.maxHp || 40) / 45), 1);
    }
    if (!en.boss) { player.energy = clamp(player.energy + 8, 0, player.maxEnergy); }
  }
}

function hurtPlayer(dmg, dir) {
  const p = player;
  if (p.invuln > 0 || p.dead) return;
  p.hp -= dmg * (1 - (p.defense || 0)); p.invuln = 1.0;
  p.vx = dir * 6; p.vy = -6;
  camera.shake = 10;
  burst(p.x + p.w / 2, p.y + p.h / 2, "#ff6b8b", 16, { glow: true, max: 6 });
  if (p.hp <= 0) { p.hp = 0; p.dead = true; onPlayerDeath(); }
}


/* ---------- IA dos inimigos ---------- */
function enemyUpdate(en, dt, level) {
  if (en.dead) return;
  en.phase += dt * 5;
  if (en.hurtTimer > 0) en.hurtTimer -= dt;

  if (en.type === "shade") {
    // persegue o jogador no chão
    const dx = (player.x + player.w / 2) - (en.x + en.w / 2);
    en.facing = dx > 0 ? 1 : -1;
    const dist = Math.abs(dx);
    if (dist < 380) en.vx += en.facing * 0.3; else en.vx *= 0.9;
    en.vx = clamp(en.vx, -2.4, 2.4);
    en.vy += GRAV; if (en.vy > 16) en.vy = 16;
    moveWithCollision(en, level);
    // patrulha nas bordas de plataforma (impede queda)
    if (en.onGround && !willBeGrounded(en, level)) en.vx = -en.vx, en.x += en.vx * 2;
    if (aabb(en, player.hitbox)) hurtPlayer(12, en.facing);
  } else if (en.type === "wisp") {
    // voa em direção ao jogador de forma senoidal
    const tx = player.x + player.w / 2, ty = player.y + player.h / 2 - 30;
    en.x = lerp(en.x, tx - en.w / 2, 0.012);
    en.baseY = lerp(en.baseY, ty - en.h / 2, 0.02);
    en.y = en.baseY + Math.sin(en.phase) * 22;
    en.facing = (tx > en.x) ? 1 : -1;
    en.shootCd -= dt;
    if (en.shootCd <= 0 && Math.abs(tx - en.x) < 460) {
      en.shootCd = 2.2;
      const a = Math.atan2(ty - (en.y + en.h / 2), tx - (en.x + en.w / 2));
      level.projectiles.push({ x: en.x + en.w / 2, y: en.y + en.h / 2, vx: Math.cos(a) * 4.4, vy: Math.sin(a) * 4.4, r: 7, color: "#4fe0e0", from: "enemy", life: 4 });
    }
    if (aabb(en, player.hitbox)) hurtPlayer(10, en.facing);
  } else if (en.type === "brute") {
    // Golem: avança lento; quando perto e pronto, investe
    const dx = (player.x + player.w / 2) - (en.x + en.w / 2);
    en.facing = dx > 0 ? 1 : -1;
    en.vy += GRAV; if (en.vy > 16) en.vy = 16;
    if (en.chargeCd > 0) en.chargeCd -= dt;
    if (en.charging > 0) {
      en.charging -= dt; en.vx = en.facing * 7.5;
      if (Math.random() < 0.4) spawnParticle({ x: en.x + en.w / 2, y: en.y + en.h, vx: rand(-1, 1), vy: 0, g: 0, r: rand(2, 5), color: "#a86bff", life: 0.4, maxLife: 0.4, glow: true });
    } else {
      if (Math.abs(dx) < 140 && en.chargeCd <= 0 && en.onGround) { en.charging = 0.55; en.chargeCd = rand(2.5, 4); camera.shake = 4; }
      else { en.vx += en.facing * 0.16; en.vx = clamp(en.vx, -1.6, 1.6); }
    }
    moveWithCollision(en, level);
    if (en.onGround && !willBeGrounded(en, level) && en.charging <= 0) { en.vx = -en.vx; en.x += en.vx * 2; }
    if (aabb(en, player.hitbox)) hurtPlayer(en.charging > 0 ? 24 : 16, en.facing);
  } else if (en.type === "bat") {
    // Morcego: paira e mergulha
    const tx = player.x + player.w / 2, ty = player.y + player.h / 2;
    if (en.diveCd > 0) en.diveCd -= dt;
    if (en.diving > 0) {
      en.diving -= dt; en.x += en.vx; en.y += en.vy; en.vy += 0.15;
      en.baseY = en.y;
      if (en.diving <= 0) en.diveCd = rand(1.5, 3);
    } else {
      en.x = lerp(en.x, tx - en.w / 2, 0.03);
      en.baseY = lerp(en.baseY, ty - 120, 0.03);
      en.y = en.baseY + Math.sin(en.phase * 1.4) * 12;
      if (en.diveCd <= 0 && Math.abs(tx - (en.x + en.w / 2)) < 260) {
        const a = Math.atan2(ty - (en.y + en.h / 2), tx - (en.x + en.w / 2));
        en.vx = Math.cos(a) * 8; en.vy = Math.sin(a) * 8; en.diving = 0.5;
      }
    }
    en.facing = (tx > en.x) ? 1 : -1;
    if (aabb(en, player.hitbox)) hurtPlayer(12, en.facing);
  } else if (en.type === "turret") {
    // Sentinela fixa: atira em rajada mirando o jogador
    const tx = player.x + player.w / 2, ty = player.y + player.h / 2;
    en.facing = (tx > en.x) ? 1 : -1;
    en.shootCd -= dt;
    if (en.shootCd <= 0 && Math.hypot(tx - (en.x + en.w / 2), ty - (en.y + en.h / 2)) < 620) {
      en.shootCd = 1.6;
      const a = Math.atan2(ty - (en.y + 12), tx - (en.x + en.w / 2));
      for (let s = -1; s <= 1; s++) {
        level.projectiles.push({ x: en.x + en.w / 2, y: en.y + 12, vx: Math.cos(a + s * 0.16) * 5, vy: Math.sin(a + s * 0.16) * 5, r: 7, color: "#c9f0ff", from: "enemy", dmg: 12, life: 3 });
      }
      burst(en.x + en.w / 2, en.y + 12, "#c9f0ff", 5, { glow: true, max: 4, g: 0 });
    }
    if (aabb(en, player.hitbox)) hurtPlayer(10, en.facing);
  } else if (en.type === "spitter") {
    // Cuspidor: anda devagar e lança projéteis em arco
    const dx = (player.x + player.w / 2) - (en.x + en.w / 2);
    en.facing = dx > 0 ? 1 : -1;
    en.vy += GRAV; if (en.vy > 16) en.vy = 16;
    en.vx = en.paceDir * 0.8;
    moveWithCollision(en, level);
    if (en.onGround && !willBeGrounded(en, level)) en.paceDir = -en.paceDir;
    en.shootCd -= dt;
    if (en.shootCd <= 0 && Math.abs(dx) < 520) {
      en.shootCd = rand(1.6, 2.6);
      const dir = Math.sign(dx) || 1;
      const power = clamp(Math.abs(dx) / 90, 3, 7);
      level.projectiles.push({ x: en.x + en.w / 2, y: en.y + 6, vx: dir * power, vy: -6.5, r: 9, color: "#7dffb0", from: "enemy", dmg: 14, arc: true, life: 4 });
      burst(en.x + en.w / 2, en.y + 6, "#7dffb0", 5, { glow: true, max: 4, g: 0 });
    }
    if (aabb(en, player.hitbox)) hurtPlayer(12, en.facing);
  } else if (en.type === "reaper") {
    // Ceifador: paira e faz investidas horizontais rápidas
    const tx = player.x + player.w / 2, ty = player.y + player.h / 2;
    en.facing = tx > (en.x + en.w / 2) ? 1 : -1;
    if (en.dashCd > 0) en.dashCd -= dt;
    if (en.slashing > 0) {
      en.slashing -= dt; en.x += en.vx; en.y += en.vy; en.vy *= 0.9;
      if (Math.random() < 0.5) spawnParticle({ x: en.x + en.w / 2, y: en.y + en.h / 2, vx: 0, vy: 0, g: 0, r: rand(2, 4), color: "#7dff5f", life: 0.35, maxLife: 0.35, glow: true });
      if (en.slashing <= 0) en.dashCd = rand(1.4, 2.6);
    } else {
      en.x = lerp(en.x, tx - en.w / 2, 0.03);
      en.baseY = lerp(en.baseY, ty - en.h / 2, 0.04);
      en.y = en.baseY + Math.sin(en.phase * 1.2) * 10;
      if (en.dashCd <= 0 && Math.abs(ty - (en.y + en.h / 2)) < 80 && Math.abs(tx - (en.x + en.w / 2)) < 420) {
        en.vx = en.facing * 12; en.vy = 0; en.slashing = 0.45; camera.shake = 3;
      }
    }
    if (aabb(en, player.hitbox)) hurtPlayer(en.slashing > 0 ? 18 : 12, en.facing);
  } else if (en.type === "mage") {
    // Feiticeiro: teleporta para longe e lança orbes
    const tx = player.x + player.w / 2, ty = player.y + player.h / 2;
    en.facing = tx > (en.x + en.w / 2) ? 1 : -1;
    if (en.teleT > 0) { en.teleT -= dt; if (en.teleT <= 0) burst(en.x + en.w / 2, en.y + en.h / 2, "#b98cff", 16, { glow: true, max: 6 }); }
    en.y = en.baseY + Math.sin(en.phase) * 6;
    if (en.teleCd > 0) en.teleCd -= dt;
    // teleporta se o jogador chega perto
    if (en.teleCd <= 0 && Math.abs(tx - (en.x + en.w / 2)) < 200) {
      burst(en.x + en.w / 2, en.y + en.h / 2, "#b98cff", 16, { glow: true, max: 6 });
      en.teleT = 0.35; en.teleCd = rand(3, 4.5);
      en.x = clamp(tx + (Math.random() < 0.5 ? -1 : 1) * rand(280, 420), 40, level.width - 80);
      en.baseY = clamp(ty - rand(20, 120), 80, level.height - 200); en.y = en.baseY;
    }
    if (en.castCd > 0) en.castCd -= dt;
    if (en.castT > 0) en.castT -= dt;
    if (en.teleT <= 0 && en.castCd <= 0 && Math.abs(tx - en.x) < 560) {
      en.castCd = rand(1.6, 2.6); en.castT = 0.25;
      const a = Math.atan2(ty - (en.y + 20), tx - (en.x + en.w / 2));
      level.projectiles.push({ x: en.x + en.w / 2, y: en.y + 20, vx: Math.cos(a) * 3.6, vy: Math.sin(a) * 3.6, r: 8, color: "#ff8cf0", from: "enemy", dmg: 14, life: 5, homing: 0.02 });
    }
    if (en.teleT <= 0 && aabb(en, player.hitbox)) hurtPlayer(10, en.facing);
  } else if (en.type === "slime") {
    // Limo: pula em direção ao jogador
    const dx = (player.x + player.w / 2) - (en.x + en.w / 2);
    en.facing = dx > 0 ? 1 : -1;
    en.vy += GRAV; if (en.vy > 16) en.vy = 16;
    if (en.onGround) {
      en.vx *= 0.8;
      en.hopCd -= dt;
      if (en.hopCd <= 0) { en.vy = -11; en.vx = clamp(dx * 0.03, -4.5, 4.5); en.hopCd = rand(0.6, 1.3); }
    }
    moveWithCollision(en, level);
    if (aabb(en, player.hitbox)) hurtPlayer(12, en.facing);
  } else if (en.type === "titan") {
    // Titã: avança pesado; investe e às vezes pisa criando onda de choque
    const dx = (player.x + player.w / 2) - (en.x + en.w / 2);
    en.facing = dx > 0 ? 1 : -1;
    en.vy += GRAV; if (en.vy > 16) en.vy = 16;
    if (en.chargeCd > 0) en.chargeCd -= dt;
    if (en.stompCd > 0) en.stompCd -= dt;
    if (en.charging > 0) {
      en.charging -= dt; en.vx = en.facing * 6.5;
      if (Math.random() < 0.4) spawnParticle({ x: en.x + en.w / 2, y: en.y + en.h, vx: rand(-1.5, 1.5), vy: 0, g: 0, r: rand(3, 6), color: "#ff9c5c", life: 0.4, maxLife: 0.4, glow: true });
    } else {
      if (Math.abs(dx) < 120 && en.stompCd <= 0 && en.onGround) {
        // pisão: onda de choque
        en.stompCd = rand(3, 5); camera.shake = 14;
        burst(en.x + en.w / 2, en.y + en.h, "#ffcf8a", 26, { glow: true, max: 8, up: 2 });
        for (const s of [-1, 1]) level.projectiles.push({ x: en.x + en.w / 2, y: en.y + en.h - 8, vx: s * 5, vy: -1, r: 12, color: "#ffcf8a", from: "enemy", ground: true, life: 1.1 });
      } else if (Math.abs(dx) < 320 && en.chargeCd <= 0 && en.onGround) {
        en.charging = 0.7; en.chargeCd = rand(3, 5);
      } else { en.vx += en.facing * 0.12; en.vx = clamp(en.vx, -1.3, 1.3); }
    }
    moveWithCollision(en, level);
    if (en.onGround && !willBeGrounded(en, level) && en.charging <= 0) { en.vx = -en.vx; en.x += en.vx * 2; }
    if (aabb(en, player.hitbox)) hurtPlayer(en.charging > 0 ? 28 : 20, en.facing);
  } else if (en.type === "dragon") {
    // Dragão: paira, cospe fogo em leque e mergulha
    const tx = player.x + player.w / 2, ty = player.y + player.h / 2;
    en.facing = tx > (en.x + en.w / 2) ? 1 : -1;
    if (en.breatheCd > 0) en.breatheCd -= dt;
    if (en.diveCd > 0) en.diveCd -= dt;
    if (en.diving > 0) {
      en.diving -= dt; en.x += en.vx; en.y += en.vy; en.vy += 0.2; en.baseY = en.y;
      if (en.diving <= 0) en.diveCd = rand(3, 5);
    } else {
      en.x = lerp(en.x, tx - en.w / 2, 0.02);
      en.baseY = lerp(en.baseY, ty - 160, 0.02);
      en.y = en.baseY + Math.sin(en.phase) * 16;
      if (en.diveCd <= 0 && Math.abs(tx - (en.x + en.w / 2)) < 200) {
        const a = Math.atan2(ty - (en.y + en.h / 2), tx - (en.x + en.w / 2));
        en.vx = Math.cos(a) * 9; en.vy = Math.sin(a) * 9; en.diving = 0.55;
      } else if (en.breatheCd <= 0 && Math.abs(tx - en.x) < 520) {
        en.breatheCd = rand(1.8, 3);
        const base = Math.atan2(ty - (en.y + en.h / 2), tx - (en.x + en.w / 2));
        for (let s = -1; s <= 1; s++) level.projectiles.push({ x: en.x + en.w / 2, y: en.y + en.h / 2, vx: Math.cos(base + s * 0.2) * 5, vy: Math.sin(base + s * 0.2) * 5, r: 9, color: "#ff7a3b", from: "enemy", dmg: 14, life: 3 });
        burst(en.x + en.w / 2, en.y + en.h / 2, "#ff9c5c", 8, { glow: true, max: 5, g: 0 });
      }
    }
    if (aabb(en, player.hitbox)) hurtPlayer(en.diving > 0 ? 20 : 14, en.facing);
  }
}

function willBeGrounded(en, level) {
  const probe = { x: en.x + (en.vx >= 0 ? en.w : -6), y: en.y + en.h + 4, w: 6, h: 8 };
  return getSolids(level).some((pl) => aabb(probe, pl));
}

/* ---------- Boss (dispatch) ---------- */
function bossUpdate(b, dt, level) {
  if (b.dead) return;
  if (b.kind === "gorvax") return bossUpdateGorvax(b, dt, level);
  if (b.kind === "tempestas") return bossUpdateTempestas(b, dt, level);
  if (b.kind === "ignis") return bossUpdateIgnis(b, dt, level);
  b.phaseT += dt;
  if (b.hurtTimer > 0) b.hurtTimer -= dt;
  b.stateT -= dt;

  const hpPct = b.hp / b.maxHp;
  const enraged = hpPct < 0.4;
  const dx = (player.x + player.w / 2) - (b.x + b.w / 2);
  b.facing = dx > 0 ? 1 : -1;

  b.vy += GRAV; if (b.vy > 16) b.vy = 16;
  b.attacking = false;

  if (b.state === "idle") {
    // aproxima devagar
    b.vx = lerp(b.vx, clamp(dx * 0.02, -2.6, 2.6), 0.1);
    if (b.stateT <= 0) {
      const roll = Math.random();
      if (roll < 0.4) { b.state = "volley"; b.stateT = 1.1; b.shots = enraged ? 7 : 5; b.shotTimer = 0; }
      else if (roll < 0.72) { b.state = "slam"; b.stateT = 1.0; b.slammed = false; }
      else { b.state = "summon"; b.stateT = 1.2; b.summoned = false; }
    }
  } else if (b.state === "volley") {
    b.vx *= 0.85; b.attacking = true;
    b.shotTimer -= dt;
    if (b.shots > 0 && b.shotTimer <= 0) {
      b.shots--; b.shotTimer = 0.16;
      const base = Math.atan2((player.y) - (b.y + 40), (player.x) - (b.x + b.w / 2));
      const spread = (b.shots - 2) * 0.12;
      level.projectiles.push({ x: b.x + b.w / 2, y: b.y + 30, vx: Math.cos(base + spread) * 5.2, vy: Math.sin(base + spread) * 5.2, r: 9, color: "#ff3b7f", from: "enemy", life: 4 });
      burst(b.x + b.w / 2, b.y + 30, "#ff77aa", 5, { glow: true, max: 4, g: 0 });
    }
    if (b.stateT <= 0) { b.state = "idle"; b.stateT = enraged ? 0.7 : 1.2; }
  } else if (b.state === "slam") {
    b.attacking = true;
    // salta em direção ao jogador e cai
    if (!b.slammed && b.onGround) { b.vy = -15; b.vx = clamp(dx * 0.05, -6, 6); b.slammed = true; }
    // proteção contra travamento: se demorar demais, encerra o estado
    if (b.stateT < -1.2) { b.state = "idle"; b.stateT = 1.2; }
    if (b.slammed && b.onGround && b.vy === 0 && b.stateT < 0.6) {
      // impacto: onda de choque
      camera.shake = 16;
      burst(b.x + b.w / 2, b.y + b.h, "#ff3b7f", 30, { glow: true, max: 8, up: 2 });
      for (const s of [-1, 1]) level.projectiles.push({ x: b.x + b.w / 2, y: b.y + b.h - 10, vx: s * 4, vy: -1, r: 10, color: "#ff77aa", from: "enemy", life: 1.2, ground: true });
      b.state = "idle"; b.stateT = enraged ? 0.8 : 1.3;
    }
  } else if (b.state === "summon") {
    b.vx *= 0.8;
    if (!b.summoned && b.stateT < 0.6) {
      b.summoned = true;
      const aliveWisps = level.enemies.filter((e) => !e.dead).length;
      const toSpawn = Math.max(0, Math.min(enraged ? 3 : 2, 5 - aliveWisps));
      for (let i = 0; i < toSpawn; i++) {
        const sx = b.x + rand(-200, 200);
        level.enemies.push(makeEnemy("wisp", sx, b.y - 40));
        burst(sx, b.y, "#8c1450", 14, { glow: true, max: 6 });
      }
    }
    if (b.stateT <= 0) { b.state = "idle"; b.stateT = 1.2; }
  }

  moveWithCollision(b, level);
  if (aabb(b, player.hitbox)) hurtPlayer(enraged ? 20 : 15, b.facing);
}

/* ---------- Boss Gorvax (colosso de pedra) ---------- */
function bossUpdateGorvax(b, dt, level) {
  b.phaseT += dt;
  if (b.hurtTimer > 0) b.hurtTimer -= dt;
  b.stateT -= dt;
  const hpPct = b.hp / b.maxHp;
  const enraged = hpPct < 0.4;
  const dx = (player.x + player.w / 2) - (b.x + b.w / 2);
  b.facing = dx > 0 ? 1 : -1;
  b.vy += GRAV; if (b.vy > 16) b.vy = 16;
  b.attacking = false;

  if (b.state === "idle") {
    b.vx = lerp(b.vx, clamp(dx * 0.015, -1.8, 1.8), 0.1);
    if (b.stateT <= 0) {
      const roll = Math.random();
      if (roll < 0.4) { b.state = "charge"; b.stateT = 1.6; b.chargeDone = false; b.windup = 0.4; }
      else if (roll < 0.72) { b.state = "rocks"; b.stateT = 1.2; b.shots = enraged ? 6 : 4; b.shotTimer = 0.2; }
      else { b.state = "stomp"; b.stateT = 1.0; b.slammed = false; }
    }
  } else if (b.state === "charge") {
    b.attacking = true;
    if (b.windup > 0) { b.windup -= dt; b.vx *= 0.7; }
    else { b.vx = b.facing * (enraged ? 11 : 8.5);
      if (Math.random() < 0.5) burst(b.x + b.w / 2, b.y + b.h - 6, "#e0a35f", 6, { glow: true, max: 5 }); }
    if (b.stateT <= 0) { b.state = "idle"; b.stateT = enraged ? 0.7 : 1.2; }
  } else if (b.state === "rocks") {
    b.vx *= 0.8; b.attacking = true;
    b.shotTimer -= dt;
    if (b.shots > 0 && b.shotTimer <= 0) {
      b.shots--; b.shotTimer = 0.22;
      const dir = Math.sign(dx) || 1;
      const power = clamp(Math.abs(dx) / 80, 4, 9);
      level.projectiles.push({ x: b.x + b.w / 2, y: b.y + 20, vx: dir * power + rand(-1, 1), vy: -8, r: 12, color: "#e0a35f", from: "enemy", dmg: 16, arc: true, life: 5 });
      burst(b.x + b.w / 2, b.y + 20, "#ffcf8a", 5, { glow: true, max: 4, g: 0 });
    }
    if (b.stateT <= 0) { b.state = "idle"; b.stateT = enraged ? 0.8 : 1.3; }
  } else if (b.state === "stomp") {
    b.attacking = true;
    if (!b.slammed && b.onGround) { b.vy = -13; b.vx = clamp(dx * 0.04, -5, 5); b.slammed = true; }
    if (b.stateT < -1.2) { b.state = "idle"; b.stateT = 1.2; }
    if (b.slammed && b.onGround && b.vy === 0 && b.stateT < 0.6) {
      camera.shake = 18;
      burst(b.x + b.w / 2, b.y + b.h, "#e0a35f", 34, { glow: true, max: 8, up: 2 });
      for (const s of [-1, 1]) for (let k = 1; k <= 2; k++)
        level.projectiles.push({ x: b.x + b.w / 2, y: b.y + b.h - 10, vx: s * 3.5 * k, vy: -1, r: 11, color: "#ffcf8a", from: "enemy", ground: true, life: 1.3 });
      b.state = "idle"; b.stateT = enraged ? 0.8 : 1.3;
    }
  }

  moveWithCollision(b, level);
  if (aabb(b, player.hitbox)) hurtPlayer(b.state === "charge" && b.windup <= 0 ? 24 : 16, b.facing);
}

/* ---------- Desenho do Gorvax ---------- */
function drawGorvax(b, sx, sy) {
  const cx = sx + b.w / 2, cy = sy + b.h / 2;
  const t = b.phaseT;
  const hurt = b.hurtTimer > 0;
  ctx.save(); ctx.translate(cx, cy); ctx.scale(b.facing, 1);
  // aura
  const aura = ctx.createRadialGradient(0, 0, 20, 0, 0, 140);
  aura.addColorStop(0, hurt ? "rgba(255,220,150,0.5)" : "rgba(224,163,95,0.35)");
  aura.addColorStop(1, "rgba(20,12,5,0)");
  ctx.fillStyle = aura; ctx.beginPath(); ctx.arc(0, 0, 140, 0, Math.PI * 2); ctx.fill();
  // sombra
  ctx.fillStyle = "rgba(0,0,0,0.4)"; ctx.beginPath(); ctx.ellipse(0, b.h / 2 - 4, 66, 14, 0, 0, Math.PI * 2); ctx.fill();
  const rock = ctx.createLinearGradient(0, -70, 0, 70);
  rock.addColorStop(0, hurt ? "#ffe0b0" : "#8a6038"); rock.addColorStop(1, hurt ? "#e0956a" : "#3a2412");
  // pernas
  ctx.fillStyle = hurt ? "#e0956a" : "#3a2412";
  roundRect(ctx, -48, 30, 34, 44, 10); ctx.fill();
  roundRect(ctx, 14, 30, 34, 44, 10); ctx.fill();
  // braços/punhos gigantes
  ctx.fillStyle = rock;
  const arm = Math.sin(t * 1.5) * 0.2 + (b.attacking ? -0.5 : 0);
  for (const s of [-1, 1]) {
    ctx.save(); ctx.translate(s * 60, -10); ctx.rotate(arm * s);
    roundRect(ctx, -18, -20, 36, 60, 12); ctx.fill();
    ctx.restore();
  }
  // corpo
  ctx.fillStyle = rock;
  roundRect(ctx, -56, -60, 112, 100, 22); ctx.fill();
  // fissuras de lava
  ctx.strokeStyle = b.attacking ? "#ff7a3b" : "#ffcf8a"; ctx.lineWidth = 4; ctx.shadowColor = ctx.strokeStyle; ctx.shadowBlur = 16;
  ctx.beginPath(); ctx.moveTo(-30, -40); ctx.lineTo(-10, -10); ctx.lineTo(-24, 20); ctx.stroke();
  ctx.beginPath(); ctx.moveTo(20, -34); ctx.lineTo(6, -6); ctx.lineTo(24, 24); ctx.stroke();
  ctx.shadowBlur = 0;
  // cabeça
  ctx.fillStyle = rock; roundRect(ctx, -34, -96, 68, 44, 14); ctx.fill();
  // chifres de pedra
  ctx.fillStyle = hurt ? "#ffe0b0" : "#5a3a1e";
  ctx.beginPath(); ctx.moveTo(-30, -92); ctx.lineTo(-46, -116); ctx.lineTo(-20, -96); ctx.closePath(); ctx.fill();
  ctx.beginPath(); ctx.moveTo(30, -92); ctx.lineTo(46, -116); ctx.lineTo(20, -96); ctx.closePath(); ctx.fill();
  // olhos
  const eye = hurt ? "#fff" : "#ffcf8a";
  ctx.fillStyle = eye; ctx.shadowColor = "#ff7a3b"; ctx.shadowBlur = 18;
  ctx.beginPath(); ctx.rect(-24, -84, 16, 8); ctx.rect(8, -84, 16, 8); ctx.fill();
  ctx.shadowBlur = 0;
  // boca
  ctx.fillStyle = "#1a0d05"; roundRect(ctx, -22, -64, 44, 8, 3); ctx.fill();
  ctx.fillStyle = rock;
  for (let i = -18; i < 20; i += 8) { ctx.beginPath(); ctx.rect(i, -64, 4, 8); ctx.fill(); }
  ctx.restore();
}

/* Dispatch de desenho do chefão */
function drawBoss(b, sx, sy) {
  if (b.kind === "gorvax") return drawGorvax(b, sx, sy);
  if (b.kind === "tempestas") return drawTempestas(b, sx, sy);
  if (b.kind === "ignis") return drawIgnis(b, sx, sy);
  return drawNox(b, sx, sy);
}

/* ---------- Projéteis ---------- */
function updateProjectiles(dt, level) {
  for (let i = level.projectiles.length - 1; i >= 0; i--) {
    const pr = level.projectiles[i];
    // teleguiado (orbe do feiticeiro)
    if (pr.homing && pr.from !== "player" && !player.dead) {
      const a = Math.atan2((player.y + player.h / 2) - pr.y, (player.x + player.w / 2) - pr.x);
      const sp = Math.hypot(pr.vx, pr.vy) || 3.6;
      pr.vx = lerp(pr.vx, Math.cos(a) * sp, pr.homing);
      pr.vy = lerp(pr.vy, Math.sin(a) * sp, pr.homing);
    }
    pr.x += pr.vx; pr.y += pr.vy;
    if (pr.ground) pr.vy += GRAV * 0.5;
    else if (pr.arc) pr.vy += 0.22;      // projétil em arco (cuspidor)
    else if (pr.from !== "player" && !pr.homing) pr.vy += 0.02;
    pr.life -= dt;
    const box = { x: pr.x - pr.r, y: pr.y - pr.r, w: pr.r * 2, h: pr.r * 2 };
    // rastro
    if (Math.random() < 0.5) spawnParticle({ x: pr.x, y: pr.y, vx: 0, vy: 0, g: 0, r: pr.r * 0.5, color: pr.color, life: 0.3, maxLife: 0.3, glow: true });
    let remove = pr.life <= 0;

    if (pr.from === "player") {
      // Feixe do jogador: atinge inimigos e o chefão
      for (const en of level.enemies) {
        if (en.dead) continue;
        if (aabb(box, en)) { damageEnemy(en, pr.dmg || 20, Math.sign(pr.vx) || 1, level); remove = true; break; }
      }
      if (!remove && level.boss && !level.boss.dead && aabb(box, level.boss)) { damageEnemy(level.boss, pr.dmg || 16, Math.sign(pr.vx) || 1, level); remove = true; }
    } else {
      // Projétil inimigo: atinge o jogador
      if (aabb(box, player.hitbox)) {
        hurtPlayer(pr.ground ? 18 : (pr.dmg || 12), Math.sign(pr.vx) || 1); remove = true;
        burst(pr.x, pr.y, pr.color, 10, { glow: true, max: 5 });
      }
    }
    for (const pl of getSolids(level)) { if (aabb(box, pl)) { remove = true; break; } }
    if (remove) level.projectiles.splice(i, 1);
  }
}

// Nova: atualização e desenho das ondas de choque
function updateNovas(dt, level) {
  for (let i = level.novas.length - 1; i >= 0; i--) {
    const n = level.novas[i];
    n.t -= dt; n.r = lerp(n.r, n.max, 0.35);
    if (n.t <= 0) level.novas.splice(i, 1);
  }
}
function drawNovas(level) {
  for (const n of level.novas) {
    const a = clamp(n.t / 0.4, 0, 1);
    const x = n.x - camera.sx, y = n.y - camera.sy;
    ctx.strokeStyle = `rgba(255,246,223,${a})`; ctx.lineWidth = 6 * a + 2;
    ctx.shadowColor = "#ffe08a"; ctx.shadowBlur = 24;
    ctx.beginPath(); ctx.arc(x, y, n.r, 0, Math.PI * 2); ctx.stroke();
    const g = ctx.createRadialGradient(x, y, n.r * 0.5, x, y, n.r);
    g.addColorStop(0, "transparent"); g.addColorStop(0.8, `rgba(255,224,138,${a * 0.25})`); g.addColorStop(1, "transparent");
    ctx.fillStyle = g; ctx.beginPath(); ctx.arc(x, y, n.r, 0, Math.PI * 2); ctx.fill();
    ctx.shadowBlur = 0;
  }
}
function drawProjectiles(level) {
  for (const pr of level.projectiles) {
    ctx.shadowColor = pr.color; ctx.shadowBlur = 16;
    const g = ctx.createRadialGradient(pr.x - camera.sx, pr.y - camera.sy, 1, pr.x - camera.sx, pr.y - camera.sy, pr.r);
    g.addColorStop(0, "#fff"); g.addColorStop(1, pr.color);
    ctx.fillStyle = g;
    ctx.beginPath(); ctx.arc(pr.x - camera.sx, pr.y - camera.sy, pr.r, 0, Math.PI * 2); ctx.fill();
    ctx.shadowBlur = 0;
  }
}

/* ---------- Coletáveis (fragmentos de luz) ---------- */
function updateCollectibles(dt, level) {
  for (const c of level.fragments) {
    if (c.taken) continue;
    c.phase += dt * 3;
    c.y = c.baseY + Math.sin(c.phase) * 8;
    if (aabb({ x: c.x - 14, y: c.y - 14, w: 28, h: 28 }, player.hitbox)) {
      c.taken = true; level.collected++;
      player.hp = clamp(player.hp + 6, 0, player.maxHp);
      burst(c.x, c.y, "#ffe08a", 20, { glow: true, max: 6 });
      updateHUD(level);
    }
  }
}
function drawFragment(c) {
  if (c.taken) return;
  const x = c.x - camera.sx, y = c.y - camera.sy;
  const glow = ctx.createRadialGradient(x, y, 2, x, y, 26);
  glow.addColorStop(0, "rgba(255,224,138,0.6)"); glow.addColorStop(1, "transparent");
  ctx.fillStyle = glow; ctx.beginPath(); ctx.arc(x, y, 26, 0, Math.PI * 2); ctx.fill();
  ctx.save();
  ctx.translate(x, y); ctx.rotate(c.phase);
  ctx.fillStyle = "#fff6df"; ctx.shadowColor = "#ffe08a"; ctx.shadowBlur = 16;
  ctx.beginPath();
  for (let i = 0; i < 4; i++) {
    const a = i * Math.PI / 2;
    ctx.lineTo(Math.cos(a) * 11, Math.sin(a) * 11);
    ctx.lineTo(Math.cos(a + Math.PI / 4) * 4, Math.sin(a + Math.PI / 4) * 4);
  }
  ctx.closePath(); ctx.fill();
  ctx.shadowBlur = 0;
  ctx.restore();
}

/* ---------- Portal / objetivo ---------- */
function drawGoal(level) {
  if (!level.goal) return;
  const g = level.goal; const x = g.x - camera.sx, y = g.y - camera.sy;
  const t = now() / 400;
  const ring = ctx.createRadialGradient(x, y, 6, x, y, 60);
  ring.addColorStop(0, "rgba(255,224,138,0.5)"); ring.addColorStop(1, "transparent");
  ctx.fillStyle = ring; ctx.beginPath(); ctx.arc(x, y, 60, 0, Math.PI * 2); ctx.fill();
  for (let i = 0; i < 3; i++) {
    ctx.strokeStyle = `rgba(255,224,138,${0.5 - i * 0.13})`; ctx.lineWidth = 3;
    ctx.beginPath(); ctx.ellipse(x, y, 22 + i * 10, 40 + i * 14, Math.sin(t + i) * 0.3, 0, Math.PI * 2); ctx.stroke();
  }
  ctx.fillStyle = "#fff6df"; ctx.shadowColor = "#ffe08a"; ctx.shadowBlur = 20;
  ctx.beginPath(); ctx.arc(x, y, 8 + Math.sin(t * 2) * 2, 0, Math.PI * 2); ctx.fill();
  ctx.shadowBlur = 0;
}

/* ---------- Slashes (efeito de ataque) ---------- */
function drawSlashes(level, dt) {
  for (let i = level.slashes.length - 1; i >= 0; i--) {
    const s = level.slashes[i]; s.t -= dt;
    if (s.t <= 0) { level.slashes.splice(i, 1); continue; }
    const a = s.t / s.max;
    const x = s.x - camera.sx, y = s.y - camera.sy;
    ctx.save(); ctx.translate(x, y); ctx.scale(s.dir, 1);
    ctx.globalAlpha = a; ctx.strokeStyle = "#fff6df"; ctx.lineWidth = 6 * a; ctx.lineCap = "round";
    ctx.shadowColor = "#ffe08a"; ctx.shadowBlur = 16;
    ctx.beginPath(); ctx.arc(0, 0, 40, -0.9 - (1 - a) * 0.8, 0.9 + (1 - a) * 0.4); ctx.stroke();
    ctx.restore();
  }
  ctx.globalAlpha = 1; ctx.shadowBlur = 0;
}


/* =========================================================
   FÁBRICA DE INIMIGOS E FASES
   ========================================================= */
function makeEnemy(type, x, y) {
  if (type === "shade") return { type, x, y, w: 40, h: 56, vx: 0, vy: 0, facing: -1, hp: 40, maxHp: 40, phase: rand(0, 6), hurtTimer: 0, onGround: false, dead: false };
  if (type === "wisp") return { type, x, y, baseY: y, w: 34, h: 34, vx: 0, vy: 0, facing: -1, hp: 26, maxHp: 26, phase: rand(0, 6), hurtTimer: 0, shootCd: rand(1, 2.5), dead: false };
  // Golem de Sombra: pesado, lento, avança e às vezes investe
  if (type === "brute") return { type, x, y, w: 66, h: 82, vx: 0, vy: 0, facing: -1, hp: 160, maxHp: 160, phase: rand(0, 6), hurtTimer: 0, onGround: false, dead: false, chargeCd: rand(2, 4), charging: 0 };
  // Morcego Corrompido: voa rápido e mergulha no jogador
  if (type === "bat") return { type, x, y, baseY: y, w: 46, h: 34, vx: 0, vy: 0, facing: -1, hp: 22, maxHp: 22, phase: rand(0, 6), hurtTimer: 0, diveCd: rand(1, 3), diving: 0, dead: false };
  // Sentinela: torre fixa que atira mirando no jogador
  if (type === "turret") return { type, x, y, w: 48, h: 54, vx: 0, vy: 0, facing: -1, hp: 60, maxHp: 60, phase: rand(0, 6), hurtTimer: 0, onGround: true, static: true, shootCd: rand(1, 2), dead: false };
  // Cuspidor: fica no chão e lança projéteis em arco
  if (type === "spitter") return { type, x, y, w: 54, h: 46, vx: 0, vy: 0, facing: -1, hp: 80, maxHp: 80, phase: rand(0, 6), hurtTimer: 0, onGround: false, shootCd: rand(1.4, 2.6), paceDir: Math.random() < 0.5 ? -1 : 1, dead: false };
  // Ceifador: voa e faz investidas horizontais com a foice
  if (type === "reaper") return { type, x, y, baseY: y, w: 48, h: 50, vx: 0, vy: 0, facing: -1, hp: 55, maxHp: 55, phase: rand(0, 6), hurtTimer: 0, dashCd: rand(1.5, 3), slashing: 0, dead: false };
  // Feiticeiro: teleporta e lança orbes teleguiadas
  if (type === "mage") return { type, x, y, baseY: y, w: 40, h: 54, vx: 0, vy: 0, facing: -1, hp: 50, maxHp: 50, phase: rand(0, 6), hurtTimer: 0, castCd: rand(1, 2), castT: 0, teleT: 0, teleCd: rand(2.5, 4), dead: false };
  // Limo: pula em direção ao jogador
  if (type === "slime") return { type, x, y, w: 48, h: 40, vx: 0, vy: 0, facing: -1, hp: 45, maxHp: 45, phase: rand(0, 6), hurtTimer: 0, onGround: false, hopCd: rand(0.4, 1.2), dead: false };
  // Titã: golem GIGANTE, muito resistente, avança e investe com pisão
  if (type === "titan") return { type, x, y, w: 110, h: 132, vx: 0, vy: 0, facing: -1, hp: 360, maxHp: 360, phase: rand(0, 6), hurtTimer: 0, onGround: false, dead: false, chargeCd: rand(2.5, 4.5), charging: 0, stompCd: rand(2, 4), big: true };
  // Dragão: criatura voadora GRANDE que mergulha e cospe fogo
  if (type === "dragon") return { type, x, y, baseY: y, w: 128, h: 92, vx: 0, vy: 0, facing: -1, hp: 240, maxHp: 240, phase: rand(0, 6), hurtTimer: 0, breatheCd: rand(1.5, 3), diveCd: rand(3, 5), diving: 0, dead: false, big: true };
  return { type: "shade", x, y, w: 40, h: 56, vx: 0, vy: 0, facing: -1, hp: 40, maxHp: 40, phase: rand(0, 6), hurtTimer: 0, onGround: false, dead: false };
}

// P = plataforma, F = fragmento, S = shade, W = wisp
function buildLevel(def) {
  return {
    theme: def.theme,
    name: def.name,
    chapter: def.chapter,
    width: def.width,
    height: def.height,
    spawn: def.spawn,
    goal: def.goal || null,
    isBoss: !!def.boss,
    bossKind: def.boss || null,
    platforms: def.platforms.map((p) => ({ x: p[0], y: p[1], w: p[2], h: p[3] })),
    movers: (def.movers || []).map((m) => ({ x: m[0], y: m[1], w: m[2], h: m[3], x0: m[0], y0: m[1], dx: m[4] || 0, dy: m[5] || 0, speed: m[6] || 1, phase: rand(0, 6), mover: true })),
    hazards: (def.hazards || []).map(makeHazard),
    enemies: (def.enemies || []).map((e) => makeEnemy(e[0], e[1], e[2])),
    fragments: (def.fragments || []).map((f) => ({ x: f[0], y: f[1], baseY: f[1], phase: rand(0, 6), taken: false })),
    coins: (def.coins || []).map((c) => makeCoin(c[0], c[1], c[2] || 1, false)),
    merchants: (def.merchants || []).map((m) => ({ x: m[0], y: m[1], near: false })),
    weather: def.weather || null,
    vertical: !!def.vertical,
    projectiles: [],
    slashes: [],
    novas: [],
    fx: [],
    boss: null,
    collected: 0,
    intro: def.intro,
  };
}

// Obstáculos: spike (espinhos), lava (poça), saw (serra móvel)
function makeHazard(h) {
  const type = h[0];
  if (type === "spike") return { type, x: h[1], y: h[2], w: h[3], h: 26 };
  if (type === "lava")  return { type, x: h[1], y: h[2], w: h[3], h: h[4] || 40, phase: rand(0, 6) };
  if (type === "saw")   return { type, x0: h[1], y0: h[2], x: h[1], y: h[2], range: h[3] || 160, axis: h[4] || "x", speed: h[5] || 1.4, r: 30, phase: rand(0, 6), angle: 0 };
  return { type: "spike", x: h[1], y: h[2], w: h[3] || 40, h: 26 };
}

// Atualização de plataformas móveis (carrega o jogador junto)
function updateMovers(dt, level) {
  if (!level.movers) return;
  for (const m of level.movers) {
    m.phase += dt * m.speed;
    const nx = m.x0 + Math.sin(m.phase) * m.dx;
    const ny = m.y0 + Math.sin(m.phase) * m.dy;
    const ddx = nx - m.x, ddy = ny - m.y;
    // carrega o jogador se estiver em cima
    const onTop = !player.dead && player.vy >= 0 &&
      player.x + player.w > m.x + 3 && player.x < m.x + m.w - 3 &&
      Math.abs((player.y + player.h) - m.y) < 8;
    if (onTop) { player.x += ddx; player.y += ddy; }
    m.x = nx; m.y = ny;
  }
}

// Atualização e colisão dos obstáculos
function updateHazards(dt, level) {
  if (!level.hazards) return;
  for (const hz of level.hazards) {
    if (hz.type === "saw") {
      hz.phase += dt * hz.speed; hz.angle += dt * 10;
      if (hz.axis === "x") hz.x = hz.x0 + Math.sin(hz.phase) * hz.range;
      else hz.y = hz.y0 + Math.sin(hz.phase) * hz.range;
      const box = { x: hz.x - hz.r, y: hz.y - hz.r, w: hz.r * 2, h: hz.r * 2 };
      if (aabb(box, player.hitbox)) hurtPlayer(22, player.x < hz.x ? -1 : 1);
    } else if (hz.type === "spike") {
      const box = { x: hz.x, y: hz.y, w: hz.w, h: hz.h };
      if (aabb(box, player.hitbox)) hurtPlayer(18, player.x + player.w / 2 < hz.x + hz.w / 2 ? -1 : 1);
    } else if (hz.type === "lava") {
      hz.phase += dt * 2;
      const box = { x: hz.x, y: hz.y, w: hz.w, h: hz.h };
      if (aabb(box, player.hitbox)) hurtPlayer(22, 0), player.vy = -9;
    }
  }
}

// Desenho dos obstáculos
function drawHazards(level) {
  if (!level.hazards) return;
  for (const hz of level.hazards) {
    if (hz.type === "spike") {
      const x = hz.x - camera.sx, y = hz.y - camera.sy;
      const n = Math.max(1, Math.floor(hz.w / 20));
      for (let i = 0; i < n; i++) {
        const sx = x + i * (hz.w / n);
        const g = ctx.createLinearGradient(0, y, 0, y + hz.h);
        g.addColorStop(0, "#e9eefc"); g.addColorStop(1, "#6b7390");
        ctx.fillStyle = g;
        ctx.beginPath(); ctx.moveTo(sx, y + hz.h); ctx.lineTo(sx + hz.w / n / 2, y); ctx.lineTo(sx + hz.w / n, y + hz.h); ctx.closePath(); ctx.fill();
      }
    } else if (hz.type === "lava") {
      const x = hz.x - camera.sx, y = hz.y - camera.sy;
      const g = ctx.createLinearGradient(0, y, 0, y + hz.h);
      g.addColorStop(0, "#ffd24a"); g.addColorStop(0.4, "#ff6a1f"); g.addColorStop(1, "#8c1400");
      ctx.fillStyle = g; ctx.shadowColor = "#ff6a1f"; ctx.shadowBlur = 24;
      ctx.beginPath(); ctx.moveTo(x, y + 6);
      for (let i = 0; i <= hz.w; i += 24) ctx.lineTo(x + i, y + 6 + Math.sin(hz.phase + i * 0.05) * 5);
      ctx.lineTo(x + hz.w, y + hz.h); ctx.lineTo(x, y + hz.h); ctx.closePath(); ctx.fill();
      ctx.shadowBlur = 0;
      if (Math.random() < 0.3) spawnParticle({ x: hz.x + rand(0, hz.w) + camera.sx - camera.sx, y: hz.y, vx: rand(-0.5, 0.5), vy: rand(-2, -0.6), g: 0.06, r: rand(2, 4), color: "#ffb04a", life: 0.8, maxLife: 0.8, glow: true });
    } else if (hz.type === "saw") {
      const x = hz.x - camera.sx, y = hz.y - camera.sy;
      ctx.save(); ctx.translate(x, y); ctx.rotate(hz.angle);
      ctx.fillStyle = "#c9d2e6"; ctx.shadowColor = "#9fb0d0"; ctx.shadowBlur = 12;
      ctx.beginPath();
      for (let i = 0; i < 12; i++) {
        const a = i / 12 * Math.PI * 2;
        ctx.lineTo(Math.cos(a) * hz.r, Math.sin(a) * hz.r);
        ctx.lineTo(Math.cos(a + 0.16) * (hz.r - 10), Math.sin(a + 0.16) * (hz.r - 10));
      }
      ctx.closePath(); ctx.fill();
      ctx.fillStyle = "#5b6a80"; ctx.beginPath(); ctx.arc(0, 0, 8, 0, Math.PI * 2); ctx.fill();
      ctx.shadowBlur = 0; ctx.restore();
    }
  }
}

const LEVELS = [
  {
    theme: "forest", name: "Floresta Sombria", chapter: "Capítulo I",
    width: 3600, height: 900, spawn: { x: 80, y: 600 },
    intro: "A <b>Floresta de Lumina</b> foi engolida pela escuridão. Aurora desperta e sente os fragmentos do <b>Coração de Luz</b> chamando por ela...",
    goal: { x: 3480, y: 560 },
    platforms: [
      [0, 760, 900, 140], [1040, 700, 320, 40], [1480, 620, 260, 40],
      [1860, 700, 300, 40], [2180, 760, 700, 140], [2500, 600, 220, 40],
      [2960, 640, 260, 40], [3260, 720, 500, 180], [1250, 540, 140, 30],
    ],
    enemies: [["shade", 500, 700], ["brute", 2280, 678], ["wisp", 1600, 460], ["shade", 3350, 660], ["wisp", 2650, 460], ["bat", 1300, 420]],
    hazards: [["spike", 620, 734, 70], ["spike", 2360, 734, 90]],
    fragments: [[1180, 640], [1560, 560], [1980, 640], [2560, 540], [3010, 580]],
    coins: [[200, 720], [260, 720], [320, 720], [1120, 660], [1200, 660], [1500, 580], [1580, 580], [2000, 660], [2560, 500], [2980, 600], [3040, 600]],
    merchants: [[760, 720]],
  },
  {
    theme: "ruins", name: "Ruínas de Cristal", chapter: "Capítulo II",
    width: 4000, height: 1000, spawn: { x: 70, y: 500 },
    intro: "Nas <b>Ruínas de Cristal</b>, os ecos dos antigos guardiões sussurram. Sentinelas antigas despertam para barrar o caminho de Aurora.",
    goal: { x: 3860, y: 480 },
    platforms: [
      [0, 660, 620, 340], [760, 600, 220, 30], [1080, 500, 200, 30],
      [1380, 420, 200, 30], [1680, 540, 240, 30], [2000, 660, 500, 340],
      [2200, 460, 180, 30], [2640, 560, 220, 30], [2960, 460, 200, 30],
      [3260, 560, 220, 30], [3560, 640, 440, 360],
    ],
    movers: [["mover", 1180, 460, 120, 24, 0, -120, 1.0], ["mover", 2500, 560, 130, 24, 130, 0, 1.2]],
    hazards: [["saw", 340, 634, 150, "x", 1.4]],
    enemies: [["turret", 2202, 406], ["shade", 300, 604], ["wisp", 1500, 300], ["bat", 2150, 360], ["wisp", 2800, 380], ["turret", 3262, 506], ["shade", 3700, 584]],
    fragments: [[860, 540], [1180, 440], [1480, 360], [2280, 400], [2720, 500], [3060, 400], [3360, 500]],
  },
  {
    theme: "swamp", name: "Pântano Corrompido", chapter: "Capítulo III",
    width: 4200, height: 1050, spawn: { x: 70, y: 600 },
    intro: "O <b>Pântano Corrompido</b> ferve com ácido das sombras. Cuspidores emboscam entre as poças — cuidado onde pisa, guardiã.",
    goal: { x: 4080, y: 640 },
    platforms: [
      [0, 720, 700, 330], [900, 720, 500, 330], [1600, 640, 220, 30],
      [1950, 720, 600, 330], [2750, 620, 220, 30], [3050, 720, 500, 330],
      [3650, 640, 240, 30], [3950, 720, 300, 330],
    ],
    movers: [["mover", 1500, 780, 120, 22, 0, -130, 1.1], ["mover", 3520, 780, 120, 22, 130, 0, 1.2]],
    hazards: [
      ["lava", 700, 900, 200, 150], ["lava", 1400, 950, 200, 100],
      ["lava", 2550, 960, 500, 90], ["lava", 3550, 960, 100, 90],
      ["spike", 1100, 694, 80],
    ],
    enemies: [["shade", 300, 664], ["spitter", 1150, 674], ["bat", 1300, 400], ["spitter", 2200, 674], ["bat", 2450, 380], ["spitter", 3250, 674], ["bat", 3500, 400], ["wisp", 2000, 480]],
    fragments: [[1000, 640], [1600, 580], [2200, 640], [2750, 560], [3650, 580], [4050, 640]],
    coins: [[200, 680], [340, 680], [960, 680], [1040, 680], [2050, 680], [2150, 680], [3120, 680], [3220, 680], [4000, 680]],
    merchants: [[1050, 680]],
  },
  {
    theme: "frost", name: "Abismo Gélido", chapter: "Capítulo IV", weather: "snow",
    width: 4200, height: 1100, spawn: { x: 70, y: 560 },
    intro: "No <b>Abismo Gélido</b> cai neve eterna. Lâminas de gelo giram nas passagens estreitas e o vento uiva entre os picos.",
    goal: { x: 4080, y: 640 },
    platforms: [
      [0, 700, 600, 400], [760, 620, 200, 30], [1050, 700, 500, 400],
      [1700, 600, 200, 30], [2000, 520, 200, 30], [2300, 640, 240, 30],
      [2650, 700, 500, 400], [3300, 600, 220, 30], [3650, 700, 550, 400],
    ],
    movers: [["mover", 2540, 560, 120, 22, 150, 0, 1.2], ["mover", 1560, 640, 110, 22, 0, -120, 1.0]],
    hazards: [
      ["saw", 1300, 674, 190, "x", 1.5], ["spike", 300, 674, 90],
      ["spike", 2820, 674, 100], ["saw", 3410, 570, 120, "y", 1.7],
    ],
    enemies: [["turret", 802, 566], ["shade", 1200, 644], ["bat", 1500, 360], ["turret", 3302, 546], ["bat", 2500, 340], ["spitter", 2700, 654], ["bat", 3700, 380]],
    fragments: [[760, 560], [2000, 460], [2300, 580], [3300, 540], [1300, 640], [3900, 640]],
    coins: [[240, 640], [340, 640], [1300, 620], [2000, 440, 10], [2700, 620], [3900, 620, 25]],
    merchants: [[440, 644]],
  },
  {
    theme: "cavern", name: "Caverna do Colosso", chapter: "Capítulo V — Guardião de Pedra", boss: "gorvax",
    width: 1700, height: 950, spawn: { x: 120, y: 600 },
    intro: "Nas profundezas ecoa um trovão de rocha. <b>GORVAX</b>, o Colosso de Pedra, guarda a passagem para a cidadela. Ele não deixará Aurora passar.",
    platforms: [
      [0, 780, 1700, 170], [120, 620, 220, 30], [1360, 620, 220, 30], [700, 520, 300, 30],
    ],
    enemies: [],
    fragments: [],
  },
  {
    theme: "volcano", name: "Forja das Sombras", chapter: "Capítulo VI", weather: "ash",
    width: 4400, height: 1100, spawn: { x: 70, y: 600 },
    intro: "A <b>Forja das Sombras</b> arde sob a cidadela. Golems despertam da lava e o ar queima — mas o Coração de Luz está perto.",
    goal: { x: 4280, y: 640 },
    platforms: [
      [0, 720, 650, 380], [850, 660, 200, 30], [1150, 720, 500, 380],
      [1750, 600, 220, 30], [2080, 700, 240, 30], [2400, 720, 500, 380],
      [3050, 620, 200, 30], [3350, 700, 240, 30], [3650, 720, 750, 380],
    ],
    movers: [["mover", 1680, 780, 130, 22, 160, 0, 1.3], ["mover", 2950, 780, 130, 22, 160, 0, 1.3]],
    hazards: [
      ["lava", 650, 940, 200, 160], ["lava", 1650, 980, 750, 120],
      ["lava", 2900, 980, 750, 120], ["saw", 1350, 674, 170, "x", 1.7],
      ["saw", 3800, 674, 190, "x", 1.9], ["spike", 300, 694, 90],
    ],
    enemies: [["shade", 400, 664], ["titan", 1250, 588], ["bat", 1800, 360], ["turret", 2082, 646], ["spitter", 2600, 674], ["bat", 3100, 360], ["brute", 3750, 638]],
    fragments: [[850, 600], [1750, 540], [2200, 640], [3050, 560], [3400, 640], [4050, 640]],
    coins: [[200, 680], [300, 680], [2200, 680], [2300, 680, 10], [3700, 680], [3800, 680], [3900, 680, 25]],
    merchants: [[500, 680]],
  },
  {
    theme: "crypt", name: "Cripta Sombria", chapter: "Capítulo VII",
    width: 4200, height: 1050, spawn: { x: 70, y: 600 },
    intro: "Na <b>Cripta Sombria</b>, feiticeiros teleportam entre as tumbas e ceifadores cortam a escuridão. Compre equipamento antes de seguir.",
    goal: { x: 4080, y: 640 },
    platforms: [
      [0, 720, 700, 330], [880, 640, 220, 30], [1180, 720, 500, 330],
      [1780, 600, 220, 30], [2100, 700, 260, 30], [2450, 720, 500, 330],
      [3100, 620, 220, 30], [3400, 700, 260, 30], [3700, 720, 500, 330],
    ],
    movers: [["mover", 1750, 780, 120, 22, 0, -120, 1.1]],
    hazards: [["spike", 300, 694, 90], ["saw", 1350, 674, 160, "x", 1.5]],
    enemies: [["slime", 400, 680], ["mage", 940, 560], ["reaper", 1500, 420], ["slime", 1350, 680], ["mage", 2650, 560], ["reaper", 2300, 420], ["slime", 3500, 680], ["mage", 3900, 560]],
    fragments: [[880, 580], [1780, 540], [2100, 640], [3100, 560], [3400, 640], [4000, 660]],
    coins: [[200, 680], [320, 680], [1250, 680], [1350, 680], [2500, 680], [2600, 680], [3760, 680], [3860, 680]],
    merchants: [[1250, 680]],
  },
  {
    theme: "sky", name: "Ascensão às Nuvens", chapter: "Capítulo VIII — Rumo ao Céu", weather: "wind", vertical: true,
    width: 1400, height: 2620, spawn: { x: 100, y: 2360 },
    intro: "Para alcançar os reinos flutuantes, é preciso <b>subir até as nuvens</b>. Escale as ilhas suspensas sem cair no vazio — o vento sopra forte lá em cima.",
    goal: { x: 1130, y: 280 },
    platforms: [
      [0, 2460, 1400, 160],
      [180, 2280, 220, 26], [460, 2150, 220, 26], [740, 2020, 220, 26], [1020, 1890, 220, 26],
      [740, 1760, 220, 26], [460, 1630, 220, 26], [180, 1500, 220, 26], [460, 1370, 220, 26],
      [740, 1240, 220, 26], [1020, 1110, 220, 26], [740, 980, 220, 26], [460, 850, 220, 26],
      [180, 720, 220, 26], [460, 590, 220, 26], [740, 460, 220, 26], [1000, 330, 260, 26],
    ],
    movers: [["mover", 1080, 1600, 120, 22, 0, -160, 1.0], ["mover", 120, 940, 120, 22, 0, -150, 1.1]],
    hazards: [["saw", 620, 1240, 120, "x", 1.5]],
    enemies: [["wisp", 600, 2000], ["bat", 900, 1650], ["reaper", 520, 1300], ["wisp", 900, 950], ["bat", 320, 650], ["reaper", 1000, 430]],
    fragments: [[290, 2260], [850, 2000], [290, 1480], [1130, 1090], [290, 700], [850, 440]],
    coins: [[290, 2260], [560, 2130], [850, 2000, 10], [290, 1480], [1130, 1090, 10], [560, 830], [290, 700], [1120, 310, 25]],
    merchants: [[320, 2440]],
  },
  {
    theme: "sky", name: "Jardins Suspensos", chapter: "Capítulo IX",
    width: 4500, height: 1150, spawn: { x: 70, y: 600 }, weather: "wind",
    intro: "Acima das nuvens, os <b>Jardins Suspensos</b> flutuam entre ilhas de luz. Não olhe para baixo, herói — um passo em falso é o abismo.",
    goal: { x: 4360, y: 660 },
    platforms: [
      [0, 720, 480, 430], [640, 660, 200, 30], [950, 600, 200, 30],
      [1250, 660, 200, 30], [1550, 560, 220, 30], [1900, 640, 200, 30],
      [2200, 560, 220, 30], [2550, 660, 200, 30], [2850, 600, 220, 30],
      [3200, 640, 200, 30], [3500, 560, 220, 30], [3850, 640, 200, 30],
      [4150, 720, 350, 430],
    ],
    movers: [["mover", 480, 700, 130, 22, 130, 0, 1.2], ["mover", 3200, 600, 120, 22, 0, -120, 1.1]],
    hazards: [["saw", 2300, 530, 120, "y", 1.6]],
    enemies: [["bat", 700, 400], ["wisp", 1050, 400], ["slime", 1600, 520], ["reaper", 2000, 420], ["bat", 2600, 420], ["wisp", 2950, 420], ["reaper", 3300, 420], ["bat", 3900, 400]],
    fragments: [[640, 600], [950, 540], [1550, 500], [2200, 500], [2850, 540], [3500, 500], [3900, 580]],
    coins: [[200, 680], [300, 680], [700, 620], [1250, 620], [1900, 600], [2550, 620], [3200, 600], [3850, 600], [4250, 680]],
    merchants: [[120, 680]],
  },
  {
    theme: "storm", name: "Pico da Tempestade", chapter: "Capítulo X — Senhor da Tempestade", boss: "tempestas", weather: "storm",
    width: 1800, height: 950, spawn: { x: 120, y: 600 },
    intro: "No cume varrido por raios, <b>TEMPESTAS</b>, o Senhor da Tempestade, domina os céus. Derrote-o para absorver o poder do relâmpago.",
    platforms: [
      [0, 780, 1800, 120], [140, 600, 240, 28], [1420, 600, 240, 28], [760, 500, 300, 28],
    ],
    enemies: [],
    fragments: [],
  },
  {
    theme: "citadel", name: "Fortaleza Estelar", chapter: "Capítulo XI", weather: "storm",
    width: 4600, height: 1100, spawn: { x: 70, y: 600 },
    intro: "A <b>Fortaleza Estelar</b> guarda os portões da cidadela. Titãs e dragões patrulham as muralhas sob o trovão. Este é o último respiro antes do fim.",
    goal: { x: 4480, y: 600 },
    platforms: [
      [0, 720, 640, 380], [820, 640, 220, 30], [1140, 720, 520, 380],
      [1760, 600, 220, 30], [2080, 680, 240, 30], [2420, 720, 520, 380],
      [3040, 600, 220, 30], [3360, 680, 240, 30], [3680, 720, 920, 380],
    ],
    movers: [["mover", 660, 720, 130, 22, 150, 0, 1.3], ["mover", 2960, 640, 120, 22, 0, -130, 1.1]],
    hazards: [["spike", 300, 694, 90], ["saw", 1350, 674, 170, "x", 1.7], ["spike", 2500, 694, 100], ["saw", 3760, 674, 180, "x", 1.9]],
    enemies: [["titan", 380, 588], ["mage", 900, 600], ["reaper", 1500, 440], ["turret", 2082, 626], ["spitter", 2600, 674], ["dragon", 3100, 320], ["mage", 3400, 620], ["brute", 4000, 638], ["reaper", 3600, 440]],
    fragments: [[820, 580], [1760, 540], [2080, 620], [3040, 540], [3360, 620], [4200, 660]],
    coins: [[200, 680], [320, 680], [1200, 680], [1300, 680, 10], [2500, 680], [2600, 680], [3800, 680], [3900, 680], [4000, 680, 25]],
    merchants: [[450, 680]],
  },
  {
    theme: "volcano", name: "Núcleo Vulcânico", chapter: "Capítulo XII — Descida ao Fogo", weather: "ash", vertical: true,
    width: 1400, height: 2500, spawn: { x: 80, y: 2280 },
    intro: "As <b>entranhas do vulcão</b> fervem. Escale as pontes de obsidiana enquanto as brasas sobem e a lava borbulha lá embaixo.",
    goal: { x: 1130, y: 210 },
    platforms: [
      [0, 2360, 1400, 140],
      [180, 2200, 220, 26], [460, 2070, 220, 26], [740, 1940, 220, 26], [1020, 1810, 220, 26],
      [740, 1680, 220, 26], [460, 1550, 220, 26], [180, 1420, 220, 26], [460, 1290, 220, 26],
      [740, 1160, 220, 26], [1020, 1030, 220, 26], [740, 900, 220, 26], [460, 770, 220, 26],
      [180, 640, 220, 26], [460, 510, 220, 26], [740, 380, 220, 26], [1000, 260, 260, 26],
    ],
    movers: [["mover", 1080, 1500, 120, 22, 0, -160, 1.0]],
    hazards: [["lava", 520, 2344, 700, 30], ["saw", 620, 1160, 120, "x", 1.6], ["saw", 620, 900, 120, "x", 1.7]],
    enemies: [["spitter", 280, 2154], ["bat", 800, 1800], ["reaper", 600, 1400], ["spitter", 560, 1244], ["bat", 900, 1000], ["reaper", 300, 600]],
    fragments: [[290, 2180], [850, 1920], [290, 1400], [1130, 1010], [290, 620], [850, 360]],
    coins: [[290, 2180], [850, 1920, 10], [560, 1530], [290, 1400], [1130, 1010, 10], [290, 620], [850, 360, 25]],
    merchants: [[300, 2360]],
  },
  {
    theme: "volcano", name: "Coração do Vulcão", chapter: "Capítulo XIII — Coração do Vulcão", boss: "ignis", weather: "ash",
    width: 1800, height: 950, spawn: { x: 120, y: 600 },
    intro: "No coração incandescente, <b>IGNIS</b> desperta da lava. Vença o Coração do Vulcão e domine a Chuva de Meteoros.",
    platforms: [
      [0, 780, 1800, 120], [140, 600, 240, 28], [1420, 600, 240, 28], [760, 520, 300, 28],
    ],
    enemies: [],
    fragments: [],
  },
  {
    theme: "peak", name: "Escadaria Celeste", chapter: "Capítulo XIV — A Escadaria Celeste", weather: "wind", vertical: true,
    width: 1400, height: 2500, spawn: { x: 80, y: 2280 },
    intro: "Acima de todas as nuvens ergue-se a <b>Escadaria Celeste</b>. Suba entre dragões e ventos cortantes até o teto do mundo.",
    goal: { x: 1140, y: 210 },
    platforms: [
      [0, 2360, 1400, 140],
      [200, 2200, 220, 26], [500, 2070, 220, 26], [800, 1940, 220, 26], [1040, 1810, 220, 26],
      [760, 1680, 220, 26], [460, 1550, 220, 26], [180, 1420, 220, 26], [460, 1290, 220, 26],
      [760, 1160, 220, 26], [1040, 1030, 220, 26], [760, 900, 220, 26], [460, 770, 220, 26],
      [180, 640, 220, 26], [480, 510, 220, 26], [780, 380, 220, 26], [1000, 260, 260, 26],
    ],
    movers: [["mover", 120, 1500, 120, 22, 0, -160, 1.05]],
    hazards: [["saw", 640, 1160, 120, "x", 1.7]],
    enemies: [["reaper", 500, 2000], ["bat", 900, 1650], ["dragon", 600, 1200], ["wisp", 300, 900], ["reaper", 1000, 550], ["bat", 500, 340]],
    fragments: [[300, 2180], [880, 1920], [280, 1400], [1130, 1010], [280, 620], [860, 360]],
    coins: [[300, 2180], [880, 1920, 10], [560, 1530], [280, 1400], [1130, 1010, 10], [280, 620], [860, 360, 25]],
    merchants: [[300, 2360]],
  },
  {
    theme: "citadel", name: "Muralhas da Cidadela", chapter: "Capítulo XV — As Muralhas", weather: "storm",
    width: 4800, height: 1100, spawn: { x: 70, y: 600 },
    intro: "As <b>Muralhas da Cidadela</b> fervilham com a legião de Nöx sob a tempestade. Titãs e dragões defendem o portão final.",
    goal: { x: 4680, y: 600 },
    platforms: [
      [0, 720, 700, 380], [880, 640, 220, 30], [1200, 720, 560, 380],
      [1860, 600, 220, 30], [2200, 680, 240, 30], [2540, 720, 560, 380],
      [3200, 600, 220, 30], [3540, 680, 240, 30], [3880, 720, 920, 380],
    ],
    movers: [["mover", 720, 720, 140, 22, 150, 0, 1.3], ["mover", 3120, 640, 120, 22, 0, -130, 1.1]],
    hazards: [["spike", 320, 694, 90], ["saw", 1420, 674, 180, "x", 1.8], ["saw", 2800, 674, 180, "x", 1.9], ["spike", 3600, 694, 100]],
    enemies: [["titan", 420, 588], ["reaper", 1000, 440], ["dragon", 1900, 320], ["turret", 2202, 626], ["spitter", 2700, 674], ["mage", 3200, 560], ["titan", 3900, 588], ["dragon", 3400, 320]],
    fragments: [[880, 580], [1860, 540], [2200, 620], [3200, 540], [3540, 620], [4400, 660]],
    coins: [[200, 680], [320, 680, 10], [1300, 680], [2600, 680], [2700, 680, 10], [3900, 680], [4000, 680, 25]],
    merchants: [[460, 680]],
  },
  {
    theme: "citadel", name: "Cidadela das Sombras", chapter: "Capítulo XVI — O Confronto Final", boss: "nox",
    width: 1600, height: 900, spawn: { x: 120, y: 600 },
    intro: "No trono da <b>Cidadela das Sombras</b>, <b>NÖX</b> aguarda — o Devorador de Luz que roubou o Coração. Chegou a hora de reacender o mundo.",
    platforms: [
      [0, 780, 1600, 120], [120, 620, 220, 30], [1260, 620, 220, 30], [640, 520, 320, 30],
    ],
    enemies: [],
    fragments: [],
  },
];

function makeBoss(kind, x, y) {
  const base = {
    boss: true, kind, x, y, vx: 0, vy: 0, facing: -1,
    hurtTimer: 0, phaseT: 0, onGround: false, dead: false,
    state: "idle", stateT: 1.4, shots: 0, shotTimer: 0, attacking: false,
    slammed: false, summoned: false, charging: 0, chargeDone: false,
  };
  if (kind === "gorvax") {
    return Object.assign(base, {
      w: 150, h: 150, hp: 440, maxHp: 440, power: "quake",
      name: "GORVAX", subtitle: "O Colosso de Pedra", color: "#e0a35f", eye: "#ffcf8a",
    });
  }
  if (kind === "tempestas") {
    return Object.assign(base, {
      w: 130, h: 150, hp: 540, maxHp: 540, power: "storm",
      name: "TEMPESTAS", subtitle: "O Senhor da Tempestade", color: "#bcd4ff", eye: "#eaf2ff",
    });
  }
  if (kind === "ignis") {
    return Object.assign(base, {
      w: 140, h: 150, hp: 580, maxHp: 580, power: "meteor",
      name: "IGNIS", subtitle: "O Coração do Vulcão", color: "#ff9c5c", eye: "#ffd24a",
    });
  }
  // padrão: Nöx
  return Object.assign(base, {
    w: 120, h: 170, hp: 680, maxHp: 680, power: "void",
    name: "NÖX", subtitle: "O Devorador de Luz", color: "#ff5c93", eye: "#ff2e63",
  });
}

/* =========================================================
   HUD & retrato
   ========================================================= */
const hpFill = document.getElementById("hp-fill");
const energyFill = document.getElementById("energy-fill");
const fragCount = document.getElementById("frag-count");
const fragTotal = document.getElementById("frag-total");
const coinCount = document.getElementById("coin-count");
const stageName = document.getElementById("stage-name");
const powersHud = document.getElementById("powers-hud");
const bossBar = document.getElementById("boss-bar");
const bossFill = document.getElementById("boss-fill");
const hud = document.getElementById("hud");

function updateHUD(level) {
  hpFill.style.width = (player.hp / player.maxHp * 100) + "%";
  energyFill.style.width = (player.energy / player.maxEnergy * 100) + "%";
  fragCount.textContent = level.collected;
  fragTotal.textContent = level.fragments.length;
  if (coinCount) coinCount.textContent = profile.coins;
  stageName.textContent = level.name;
  if (powersHud) {
    let h = "";
    for (const id in BOSS_POWERS) {
      if (profile.powers[id]) {
        const bp = BOSS_POWERS[id];
        const ready = player.energy >= bp.cost;
        h += `<span class="pw" style="color:${bp.color};opacity:${ready ? 1 : 0.4}">[${bp.key}] ${bp.name}</span>`;
      }
    }
    powersHud.innerHTML = h;
  }
}

// Retrato animado da Aurora no HUD
const pcanvas = document.getElementById("portrait");
const pctx = pcanvas.getContext("2d");
function drawPortrait() {
  if (profile.hero === "kael") return drawKaelPortrait();
  pctx.clearRect(0, 0, 72, 72);
  pctx.save(); pctx.translate(36, 42);
  // rosto
  const skin = pctx.createLinearGradient(0, -20, 0, 18);
  skin.addColorStop(0, "#ffe0b8"); skin.addColorStop(1, "#e9be8f");
  pctx.fillStyle = "#3a2154"; pctx.beginPath(); pctx.arc(0, -6, 22, 0, Math.PI * 2); pctx.fill();
  pctx.fillStyle = skin; pctx.beginPath(); pctx.arc(2, -4, 17, 0, Math.PI * 2); pctx.fill();
  // olhos
  const blink = (Math.sin(now() / 500) > 0.96) ? 0.3 : 1;
  pctx.fillStyle = "#241033";
  pctx.beginPath(); pctx.ellipse(-4, -5, 2.4, 3.6 * blink, 0, 0, Math.PI * 2); pctx.fill();
  pctx.beginPath(); pctx.ellipse(8, -5, 2.4, 3.6 * blink, 0, 0, Math.PI * 2); pctx.fill();
  pctx.fillStyle = "#8fd8ff";
  pctx.beginPath(); pctx.arc(-3, -6, 1, 0, Math.PI * 2); pctx.fill();
  pctx.beginPath(); pctx.arc(9, -6, 1, 0, Math.PI * 2); pctx.fill();
  // boca
  pctx.strokeStyle = "#b5605a"; pctx.lineWidth = 1.6;
  pctx.beginPath(); pctx.arc(2, 3, 4, 0.15, Math.PI - 0.3); pctx.stroke();
  // franja
  pctx.fillStyle = "#4a2a6e";
  pctx.beginPath(); pctx.moveTo(-20, -8); pctx.quadraticCurveTo(0, -30, 20, -10);
  pctx.quadraticCurveTo(8, -12, 10, -6); pctx.quadraticCurveTo(0, -16, -8, -6);
  pctx.quadraticCurveTo(-18, -2, -20, -8); pctx.closePath(); pctx.fill();
  // coroa
  pctx.fillStyle = "#ffe08a"; pctx.shadowColor = "#ffe08a"; pctx.shadowBlur = 8;
  pctx.beginPath(); pctx.moveTo(-8, -22); pctx.lineTo(-4, -30); pctx.lineTo(0, -22);
  pctx.lineTo(4, -31); pctx.lineTo(8, -22); pctx.closePath(); pctx.fill();
  pctx.shadowBlur = 0;
  pctx.restore();
}

// Retrato do herói Kael no HUD
function drawKaelPortrait() {
  pctx.clearRect(0, 0, 72, 72);
  pctx.save(); pctx.translate(36, 42);
  const skin = pctx.createLinearGradient(0, -20, 0, 18);
  skin.addColorStop(0, "#f2c99a"); skin.addColorStop(1, "#d99a63");
  // rosto
  pctx.fillStyle = skin; pctx.beginPath(); pctx.arc(1, -4, 18, 0, Math.PI * 2); pctx.fill();
  // mandíbula/barba
  pctx.fillStyle = "rgba(60,36,20,0.5)";
  pctx.beginPath(); pctx.arc(1, 4, 15, 0.15, Math.PI - 0.15); pctx.fill();
  // olhos
  const blink = (Math.sin(now() / 500) > 0.96) ? 0.3 : 1;
  pctx.fillStyle = "#241033";
  pctx.beginPath(); pctx.ellipse(-6, -6, 2.4, 3.6 * blink, 0, 0, Math.PI * 2); pctx.fill();
  pctx.beginPath(); pctx.ellipse(8, -6, 2.4, 3.6 * blink, 0, 0, Math.PI * 2); pctx.fill();
  pctx.fillStyle = "#ffcf6a";
  pctx.beginPath(); pctx.arc(-5, -7, 1, 0, Math.PI * 2); pctx.fill();
  pctx.beginPath(); pctx.arc(9, -7, 1, 0, Math.PI * 2); pctx.fill();
  // sobrancelhas
  pctx.strokeStyle = "#3a2414"; pctx.lineWidth = 2.4;
  pctx.beginPath(); pctx.moveTo(-10, -12); pctx.lineTo(-2, -11); pctx.moveTo(4, -11); pctx.lineTo(12, -12); pctx.stroke();
  // boca
  pctx.strokeStyle = "#a5504a"; pctx.lineWidth = 1.8;
  pctx.beginPath(); pctx.moveTo(-4, 4); pctx.lineTo(6, 4); pctx.stroke();
  // cabelo curto
  pctx.fillStyle = "#3a2414";
  pctx.beginPath(); pctx.moveTo(-18, -6); pctx.quadraticCurveTo(-18, -26, 1, -26);
  pctx.quadraticCurveTo(20, -26, 19, -4); pctx.quadraticCurveTo(12, -16, 2, -14);
  pctx.quadraticCurveTo(-8, -16, -18, -6); pctx.closePath(); pctx.fill();
  // diadema
  pctx.fillStyle = "#ffd98a"; pctx.shadowColor = "#ffd98a"; pctx.shadowBlur = 8;
  pctx.fillRect(-16, -20, 34, 3); pctx.shadowBlur = 0;
  pctx.restore();
}


/* =========================================================
   MÁQUINA DE ESTADOS & TELAS
   ========================================================= */
const overlay = document.getElementById("overlay");
let state = "menu";            // menu | story | playing | dead | levelclear | victory
let level = null;
let levelIndex = 0;
let goalReached = false;
let bossIntroT = 0;
let victoryT = 0;
let bossDownT = 0;
let lastUnlockedPower = null;

function showOverlay(html) { overlay.innerHTML = html; overlay.classList.remove("gone"); }
function hideOverlay() { overlay.classList.add("gone"); }

function menuScreen() {
  state = "menu";
  hud.classList.add("hidden"); bossBar.classList.add("hidden");
  showOverlay(`
    <div class="card">
      <div class="title">AURORA</div>
      <div class="subtitle">A Guardiã da Luz</div>
      <p class="story">O mundo de <b>Lumina</b> mergulhou nas trevas quando <b>Nöx</b> despedaçou o Coração de Luz.
      Escolha seu herói, atravesse <b>16 reinos</b> (escale até as nuvens!), colete moedas, equipe-se em <b>lojas que mudam a cada fase</b>,
      e derrote <b>4 chefões</b> — a cada um vencido você <b>absorve o poder dele</b>.</p>
      <div class="btn-row">
        <button class="btn" id="btn-start">▶ Escolher Herói</button>
      </div>
      <div class="controls-hint">
        <b>🎮 Controle USB suportado!</b> &nbsp; Mover <kbd>←</kbd><kbd>→</kbd>/<kbd>A</kbd><kbd>D</kbd> • Pular <kbd>Espaço</kbd> • Planar (segure o pulo)<br>
        Atacar <kbd>K</kbd> • Feixe <kbd>U</kbd> • Nova <kbd>O</kbd> • Dash <kbd>L</kbd> • Loja <kbd>E</kbd> • Poderes de chefão <kbd>1</kbd><kbd>2</kbd><kbd>3</kbd><kbd>4</kbd> • Pausar <kbd>P</kbd>
      </div>
    </div>`);
  document.getElementById("btn-start").onclick = heroSelectScreen;
}

function heroSelectScreen() {
  state = "heroselect";
  hud.classList.add("hidden"); bossBar.classList.add("hidden");
  showOverlay(`
    <div class="card">
      <div class="chapter">Escolha seu Herói</div>
      <div class="hero-select">
        <div class="hero-card" data-hero="aurora">
          <canvas class="hero-canvas" width="150" height="180"></canvas>
          <div class="hero-name">Aurora</div>
          <div class="hero-sub">A Guardiã da Luz</div>
          <div class="hero-desc">Maga ágil com cajado radiante.</div>
          <button class="btn hero-pick" data-pick="aurora">Jogar com Aurora</button>
        </div>
        <div class="hero-card" data-hero="kael">
          <canvas class="hero-canvas" width="150" height="180"></canvas>
          <div class="hero-name">Kael</div>
          <div class="hero-sub">O Cavaleiro do Alvorecer</div>
          <div class="hero-desc">Guerreiro robusto de espada solar.</div>
          <button class="btn hero-pick" data-pick="kael">Jogar com Kael</button>
        </div>
      </div>
      <div class="btn-row"><button class="btn secondary" id="btn-back">Voltar</button></div>
    </div>`);
  // desenha a prévia de cada herói
  overlay.querySelectorAll(".hero-card").forEach((card) => {
    const hero = card.getAttribute("data-hero");
    const cv = card.querySelector(".hero-canvas");
    drawHeroPreview(cv, hero);
    card.querySelector(".hero-pick").onclick = () => { profile.hero = hero; resetProfile(); applyLoadout(); startLevel(0); };
  });
  document.getElementById("btn-back").onclick = menuScreen;
}

// Desenha uma prévia grande do herói (corpo inteiro) num canvas do menu
function drawHeroPreview(cv, hero) {
  const c = cv.getContext("2d");
  c.clearRect(0, 0, cv.width, cv.height);
  const prev = profile.hero;
  profile.hero = hero;
  // usa o ctx global temporariamente? Não — desenhamos direto com uma versão simples.
  const g = c.createRadialGradient(75, 90, 6, 75, 90, 90);
  g.addColorStop(0, hero === "kael" ? "rgba(120,180,255,0.25)" : "rgba(255,224,150,0.28)");
  g.addColorStop(1, "transparent");
  c.fillStyle = g; c.fillRect(0, 0, cv.width, cv.height);
  // reaproveita as rotinas de desenho redirecionando para este contexto
  const fake = { x: 75 - 17, y: 96 - 37, w: 34, h: 74, vx: 0, vy: 0, facing: 1, onGround: true, walkPhase: 0, attackTimer: 0 };
  withContext(c, () => {
    // câmera zero para prévia
    const ox = camera.x, oy = camera.y, osh = camera.shake;
    camera.x = 0; camera.y = 0; camera.shake = 0;
    if (hero === "kael") drawKael(fake, fake.x, fake.y);
    else drawAurora(fake, fake.x, fake.y);
    camera.x = ox; camera.y = oy; camera.shake = osh;
  });
  profile.hero = prev;
}

function storyScreen(idx) {
  state = "story";
  const def = LEVELS[idx];
  hud.classList.add("hidden"); bossBar.classList.add("hidden");
  showOverlay(`
    <div class="card">
      <div class="chapter">${def.chapter}</div>
      <div class="title" style="font-size:clamp(30px,6vw,52px)">${def.name}</div>
      <p class="story">${def.intro}</p>
      <div class="btn-row"><button class="btn" id="btn-go">${def.boss ? "⚔ Enfrentar " + (def.boss === "gorvax" ? "Gorvax" : "Nöx") : "Entrar na Fase"}</button></div>
    </div>`);
  document.getElementById("btn-go").onclick = () => beginPlay(idx);
}

function startLevel(idx) { storyScreen(idx); }

function beginPlay(idx) {
  levelIndex = idx;
  level = buildLevel(LEVELS[idx]);
  initMotes();
  initWeather(level);
  applyLoadout();
  player.reset(level.spawn.x, level.spawn.y);
  camera.x = level.spawn.x - VW / 2; camera.y = 0;
  goalReached = false;
  particles.length = 0;
  if (level.isBoss) {
    level.boss = makeBoss(level.bossKind, 0, 0);
    level.boss.x = level.width / 2 - level.boss.w / 2;
    level.boss.y = 380;
    bossIntroT = 1.6;
    const bn = document.querySelector("#boss-bar .boss-name");
    if (bn) bn.textContent = level.boss.name + " — " + level.boss.subtitle;
    bossBar.classList.remove("hidden");
  } else {
    bossBar.classList.add("hidden");
  }
  hud.classList.remove("hidden");
  updateHUD(level);
  hideOverlay();
  state = "playing";
}

function onPlayerDeath() {
  setTimeout(() => {
    state = "dead";
    showOverlay(`
      <div class="card">
        <div class="title" style="color:#ff7a9c;background:none;-webkit-text-fill-color:#ff7a9c">A Luz se Apagou</div>
        <p class="story">${HEROES[profile.hero].name} caiu... mas a esperança não. Levante-se, herói.</p>
        <div class="btn-row">
          <button class="btn" id="btn-retry">↻ Tentar Novamente</button>
          <button class="btn secondary" id="btn-menu">Menu</button>
        </div>
      </div>`);
    document.getElementById("btn-retry").onclick = () => beginPlay(levelIndex);
    document.getElementById("btn-menu").onclick = menuScreen;
  }, 700);
}

function levelClear() {
  if (levelIndex >= LEVELS.length - 1) return; // último é boss -> vitória
  state = "levelclear";
  hud.classList.add("hidden");
  let info = level.isBoss
    ? `<b>${level.boss ? level.boss.name : "O chefão"}</b> foi derrotado! A luz avança pelas terras corrompidas.`
    : `Fragmentos de luz: <b>${level.collected} / ${level.fragments.length}</b> ✦<br>A escuridão recua. ${HEROES[profile.hero].name} segue adiante.`;
  if (level.isBoss && lastUnlockedPower) {
    const bp = BOSS_POWERS[lastUnlockedPower];
    info += `<br><br><span style="color:${bp.color}">✨ Novo poder absorvido: <b>${bp.name}</b> — tecla <kbd>${bp.key}</kbd>!</span>`;
    lastUnlockedPower = null;
  }
  showOverlay(`
    <div class="card">
      <div class="chapter">${level.isBoss ? "Chefão Derrotado" : "Fase Concluída"}</div>
      <div class="title" style="font-size:clamp(28px,6vw,48px)">${level.name}</div>
      <p class="story">${info}<br><span style="color:#ffd24a">🪙 Moedas: <b>${profile.coins}</b></span></p>
      <div class="btn-row">
        <button class="btn" id="btn-next">Próxima Fase →</button>
        <button class="btn secondary" id="btn-shop">🛒 Loja</button>
      </div>
    </div>`);
  document.getElementById("btn-next").onclick = () => startLevel(levelIndex + 1);
  document.getElementById("btn-shop").onclick = () => openShop(levelClear);
}

function victoryScreen() {
  state = "victory";
  hud.classList.add("hidden"); bossBar.classList.add("hidden");
  showOverlay(`
    <div class="card">
      <div class="title">LUMINA RENASCE</div>
      <div class="subtitle">Você venceu</div>
      <p class="story">Com o último golpe, o <b>Coração de Luz</b> se reergueu das trevas. A aurora rompe o céu de Lumina,
      e o nome da guardiã ecoa por todos os reinos. <b>A luz voltou para casa.</b></p>
      <div class="btn-row"><button class="btn" id="btn-again">↻ Jogar de Novo</button></div>
    </div>`);
  document.getElementById("btn-again").onclick = menuScreen;
}

/* pausa */
let paused = false;
function togglePause() {
  if (state !== "playing") return;
  paused = !paused;
  if (paused) showOverlay(`<div class="card"><div class="title" style="font-size:44px">Pausado</div><div class="btn-row"><button class="btn" id="btn-resume">Continuar</button><button class="btn secondary" id="btn-quit">Menu</button></div></div>`);
  else hideOverlay();
  if (paused) {
    document.getElementById("btn-resume").onclick = togglePause;
    document.getElementById("btn-quit").onclick = () => { paused = false; menuScreen(); };
  }
}


/* =========================================================
   RENDER DO MUNDO
   ========================================================= */
function render(t, dt) {
  ctx.save();
  ctx.translate(offX, offY);
  ctx.scale(scale, scale);
  // clip para a área virtual
  ctx.beginPath(); ctx.rect(0, 0, VW, VH); ctx.clip();

  if (level) {
    drawBackground(level.theme, level, t);
    // plataformas fixas + móveis
    for (const pl of level.platforms) drawPlatform(pl, level.theme);
    for (const m of level.movers) drawPlatform(m, level.theme);
    // obstáculos
    drawHazards(level);
    // objetivo
    drawGoal(level);
    // fragmentos
    for (const c of level.fragments) drawFragment(c);
    // moedas
    if (level.coins) for (const c of level.coins) drawCoin(c);
    // mercadores
    if (level.merchants) for (const m of level.merchants) drawMerchant(m);
    // inimigos
    for (const en of level.enemies) {
      if (en.dead) continue;
      const sx = en.x - camera.sx, sy = en.y - camera.sy;
      if (sx < -120 || sx > VW + 120) continue;
      drawEnemy(en, sx, sy);
    }
    // boss
    if (level.boss && !level.boss.dead) drawBoss(level.boss, level.boss.x - camera.sx, level.boss.y - camera.sy);
    // ondas de Nova
    drawNovas(level);
    // efeitos de poder (raios, meteoros)
    drawFx(level);
    // slashes
    drawSlashes(level, dt);
    // projéteis
    drawProjectiles(level);
    // jogador
    if (!player.dead) {
      const blink = player.invuln > 0 && Math.floor(player.invuln * 12) % 2 === 0;
      if (!blink) drawHero(player, player.x - camera.sx, player.y - camera.sy);
    }
    // partículas
    drawParticles();

    // vinheta
    const vg = ctx.createRadialGradient(VW / 2, VH / 2, VH * 0.4, VW / 2, VH / 2, VH * 0.85);
    vg.addColorStop(0, "transparent"); vg.addColorStop(1, "rgba(0,0,0,0.55)");
    ctx.fillStyle = vg; ctx.fillRect(0, 0, VW, VH);

    // intro do boss
    if (level.isBoss && level.boss && bossIntroT > 0) {
      ctx.globalAlpha = clamp(bossIntroT, 0, 1);
      ctx.fillStyle = "rgba(0,0,0,0.5)"; ctx.fillRect(0, 0, VW, VH);
      ctx.globalAlpha = 1;
      ctx.textAlign = "center";
      ctx.font = "800 64px Cinzel, serif";
      ctx.fillStyle = level.boss.color; ctx.shadowColor = level.boss.eye; ctx.shadowBlur = 24;
      ctx.fillText(level.boss.name, VW / 2, VH / 2 - 10);
      ctx.font = "400 22px Poppins, sans-serif"; ctx.shadowBlur = 0; ctx.fillStyle = "#ffd0e0";
      ctx.fillText(level.boss.subtitle, VW / 2, VH / 2 + 28);
      ctx.textAlign = "left";
    }
  }
  ctx.restore();
}

/* =========================================================
   LOOP PRINCIPAL
   ========================================================= */
let last = now();
function loop() {
  const tNow = now();
  let dt = (tNow - last) / 1000;
  last = tNow;
  if (dt > 0.05) dt = 0.05; // clamp para estabilidade
  const t = tNow / 1000;

  pollGamepad();
  if (consume("p")) togglePause();
  if (level && (state === "playing" || state === "bossdown")) updateWeather(dt);

  if (state === "playing" && !paused && level) {
    // update
    updateMovers(dt, level);
    playerUpdate(dt, level);
    for (const en of level.enemies) enemyUpdate(en, dt, level);
    updateHazards(dt, level);
    if (level.boss) {
      if (bossIntroT > 0) bossIntroT -= dt;
      else bossUpdate(level.boss, dt, level);
      bossFill.style.width = clamp(level.boss.hp / level.boss.maxHp * 100, 0, 100) + "%";
      if (level.boss.dead && state === "playing") {
        state = "bossdown"; bossDownT = 2.8;
        camera.shake = 20;
        burst(level.boss.x + level.boss.w / 2, level.boss.y + level.boss.h / 2, "#ffe08a", 90, { glow: true, max: 10, maxLife: 1.8 });
        // o herói absorve o poder do chefão
        if (level.boss.power && !profile.powers[level.boss.power]) {
          profile.powers[level.boss.power] = true;
          lastUnlockedPower = level.boss.power;
        } else lastUnlockedPower = null;
      }
    }
    updateProjectiles(dt, level);
    updateNovas(dt, level);
    updateFx(dt, level);
    updateCollectibles(dt, level);
    updateCoins(dt, level);
    updateParticles(dt);
    camera.follow(player, level);
    updateHUD(level);

    // interagir com o mercador (abrir loja)
    if (consume("e") && level.merchants) {
      for (const m of level.merchants) {
        if (Math.abs((player.x + player.w / 2) - m.x) < 90 && Math.abs((player.y + player.h / 2) - m.y) < 120) { openShop(); break; }
      }
    }

    // chegar ao objetivo (fases normais)
    if (level.goal && !goalReached && !level.isBoss) {
      const g = { x: level.goal.x - 40, y: level.goal.y - 60, w: 80, h: 120 };
      if (aabb(g, player.hitbox)) {
        goalReached = true;
        if (levelIndex >= LEVELS.length - 1) victoryScreen(); else levelClear();
      }
    }
  } else if (state === "bossdown") {
    // celebração após derrotar um chefão
    bossDownT -= dt;
    updateNovas(dt, level); updateParticles(dt);
    if (level) camera.follow(player, level);
    if (Math.random() < 0.3 && level && level.boss) burst(level.boss.x + rand(0, level.boss.w), level.boss.y + rand(0, level.boss.h), "#ffe08a", 6, { glow: true, max: 7, maxLife: 1.4 });
    if (bossDownT <= 0) {
      bossBar.classList.add("hidden");
      if (levelIndex >= LEVELS.length - 1) victoryScreen(); else levelClear();
    }
  }

  // sempre desenhar cena de fundo do jogo se existir
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  render(t, dt);
  drawPortrait();

  requestAnimationFrame(loop);
}

/* =========================================================
   PERFIL, HERÓIS, MOEDAS E LOJA
   ========================================================= */
const profile = {
  hero: "aurora",
  coins: 0,
  owned: {},                                   // { itemId: true }
  equipped: { outfit: null, armor: null, weapon: null, equip: null },
  powers: {},                                  // poderes de chefão desbloqueados { quake, storm, meteor, void }
};

// Poderes concedidos ao derrotar cada chefão (tecla, custo, nome)
const BOSS_POWERS = {
  quake:  { key: "1", cost: 40, name: "Terremoto", color: "#e0a35f", boss: "GORVAX" },
  storm:  { key: "2", cost: 45, name: "Tempestade de Raios", color: "#bcd4ff", boss: "TEMPESTAS" },
  meteor: { key: "3", cost: 45, name: "Chuva de Meteoros", color: "#ff7a3b", boss: "IGNIS" },
  void:   { key: "4", cost: 55, name: "Colapso do Vazio", color: "#c98cff", boss: "NÖX" },
};

const HEROES = {
  aurora: { name: "Aurora", subtitle: "A Guardiã da Luz" },
  kael:   { name: "Kael",   subtitle: "O Cavaleiro do Alvorecer" },
};

// Loja: roupas (outfit), armaduras (armor), armas (weapon) e equipamentos (equip)
const SHOP_ITEMS = [
  // ---- ROUPAS (cosmético + leve bônus de energia) ----
  { id: "outfit_royal",  name: "Traje Real",      type: "outfit", price: 25, energy: 10,
    desc: "Vestes nobres douradas.", garmentTop: "#d8b24a", garment: "#b8862a", garmentDark: "#7a5410", accent: "#fff2c9" },
  { id: "outfit_shadow", name: "Manto das Sombras", type: "outfit", price: 45, energy: 20,
    desc: "Um manto que absorve a luz.", garmentTop: "#5a4a7a", garment: "#2e2448", garmentDark: "#160f2a", accent: "#b98cff" },
  { id: "outfit_ember",  name: "Vestes de Brasa",  type: "outfit", price: 70, energy: 25,
    desc: "Tecido forjado em lava.", garmentTop: "#ff8a4a", garment: "#c23a1a", garmentDark: "#7a1808", accent: "#ffd24a" },
  // ---- ARMADURAS (+vida máxima e defesa) ----
  { id: "armor_leather", name: "Armadura de Couro", type: "armor", price: 40, maxHp: 30, defense: 0.10,
    desc: "+30 vida, 10% defesa.", plate: "#8a5a3a" },
  { id: "armor_steel",   name: "Armadura de Aço",   type: "armor", price: 95, maxHp: 70, defense: 0.22,
    desc: "+70 vida, 22% defesa.", plate: "#9fb0c8" },
  { id: "armor_aegis",   name: "Égide de Luz",      type: "armor", price: 175, maxHp: 130, defense: 0.35,
    desc: "+130 vida, 35% defesa.", plate: "#ffe08a" },
  // ---- ARMAS (mais dano/alcance) ----
  { id: "weapon_blade",  name: "Lâmina Radiante",   type: "weapon", price: 55, dmg: 1.4, range: 1.05,
    desc: "+40% de dano." },
  { id: "weapon_great",  name: "Montante Solar",    type: "weapon", price: 120, dmg: 1.9, range: 1.25,
    desc: "+90% dano, mais alcance." },
  // ---- EQUIPAMENTOS (utilidades) ----
  { id: "equip_boots",   name: "Botas Aladas",      type: "equip", price: 65, jumps: 1,
    desc: "+1 pulo (pulo triplo)." },
  { id: "equip_core",    name: "Núcleo de Energia",  type: "equip", price: 80, energy: 60,
    desc: "+60 de energia máxima." },
  // ---- ITENS EXTRA (rotacionam entre as fases) ----
  { id: "outfit_frost",  name: "Manto Glacial",     type: "outfit", price: 60, energy: 22,
    desc: "Vestes de gelo eterno.", garmentTop: "#8fd8ff", garment: "#3f8fd0", garmentDark: "#1e4f80", accent: "#eaffff" },
  { id: "outfit_storm",  name: "Vestes da Tempestade", type: "outfit", price: 90, energy: 30,
    desc: "Crepitam com raios.", garmentTop: "#6a8fd0", garment: "#2f4a80", garmentDark: "#141e40", accent: "#bcd4ff" },
  { id: "armor_crystal", name: "Armadura de Cristal", type: "armor", price: 130, maxHp: 100, defense: 0.28,
    desc: "+100 vida, 28% defesa.", plate: "#9df0ff" },
  { id: "armor_dragon",  name: "Escamas de Dragão",  type: "armor", price: 210, maxHp: 160, defense: 0.40,
    desc: "+160 vida, 40% defesa.", plate: "#ff9c5c" },
  { id: "weapon_spear",  name: "Lança Estelar",     type: "weapon", price: 95, dmg: 1.6, range: 1.45,
    desc: "+60% dano, alcance longo." },
  { id: "weapon_hammer", name: "Martelo do Trovão",  type: "weapon", price: 165, dmg: 2.3, range: 1.15,
    desc: "+130% de dano brutal." },
  { id: "equip_ring",    name: "Anel de Vigor",     type: "equip", price: 110, maxHp: 60,
    desc: "+60 de vida máxima." },
  { id: "equip_wings",   name: "Asas Etéreas",      type: "equip", price: 150, jumps: 2, energy: 30,
    desc: "+2 pulos e +30 energia." },
];

// Estoque da loja muda a cada fase (janela rotativa sobre o catálogo)
function shopStock(idx) {
  const n = SHOP_ITEMS.length, size = 8, start = (idx * 3) % n;
  const set = new Set();
  for (let i = 0; i < size; i++) set.add(SHOP_ITEMS[(start + i) % n].id);
  // garante ao menos 1 item já equipado/possuído visível para reequipar
  for (const t of ["outfit", "armor", "weapon", "equip"]) {
    const eq = profile.equipped[t];
    if (eq) set.add(eq);
  }
  return set;
}

function itemById(id) { return SHOP_ITEMS.find((i) => i.id === id) || null; }

// Calcula os atributos do jogador a partir do equipamento
function applyLoadout() {
  let maxHp = 100, maxEnergy = 100, jumps = 2, defense = 0, dmgMult = 1, rangeMult = 1;
  const arm = itemById(profile.equipped.armor);
  const wp = itemById(profile.equipped.weapon);
  const eq = itemById(profile.equipped.equip);
  const outfit = itemById(profile.equipped.outfit);
  if (arm) { maxHp += arm.maxHp || 0; defense += arm.defense || 0; }
  if (wp) { dmgMult *= wp.dmg || 1; rangeMult *= wp.range || 1; }
  if (eq) { maxHp += eq.maxHp || 0; maxEnergy += eq.energy || 0; jumps += eq.jumps || 0; }
  if (outfit) { maxEnergy += outfit.energy || 0; }
  player.maxHp = maxHp; player.maxEnergy = maxEnergy; player.maxJumps = jumps;
  player.defense = Math.min(defense, 0.6); player.dmgMult = dmgMult; player.rangeMult = rangeMult;
}

// Paleta do herói (roupa equipada altera as cores)
function heroPalette() {
  const base = profile.hero === "kael"
    ? { garmentTop: "#4a8fd6", garment: "#2f6bb0", garmentDark: "#1e3f70", accent: "#ffd98a" }
    : { garmentTop: "#7a5bd6", garment: "#5a3fb0", garmentDark: "#3a2680", accent: "#ffe08a" };
  const o = itemById(profile.equipped.outfit);
  if (o) { base.garmentTop = o.garmentTop; base.garment = o.garment; base.garmentDark = o.garmentDark; base.accent = o.accent || base.accent; }
  return base;
}

// Overlay de armadura desenhado sobre o corpo (dentro do contexto já transladado)
function drawArmorOverlay() {
  const arm = itemById(profile.equipped.armor);
  if (!arm) return;
  ctx.fillStyle = arm.plate;
  ctx.strokeStyle = "rgba(0,0,0,0.25)"; ctx.lineWidth = 1.5;
  // peitoral
  roundRect(ctx, -11, -19, 22, 22, 5); ctx.fill(); ctx.stroke();
  // ombreiras
  ctx.beginPath(); ctx.ellipse(-11, -17, 7, 5, 0, 0, Math.PI * 2); ctx.fill();
  ctx.beginPath(); ctx.ellipse(11, -17, 7, 5, 0, 0, Math.PI * 2); ctx.fill();
  // brilho
  ctx.fillStyle = "rgba(255,255,255,0.25)";
  ctx.beginPath(); ctx.ellipse(-4, -14, 3, 5, 0.4, 0, Math.PI * 2); ctx.fill();
}

/* ---------- Herói masculino: Kael ---------- */
function drawKael(a, screenX, screenY) {
  const cx = screenX + a.w / 2, cy = screenY + a.h / 2;
  const face = a.facing, walk = a.walkPhase;
  const pal = heroPalette();
  const legSwing = a.onGround && Math.abs(a.vx) > 0.4 ? Math.sin(walk) * 9 : 0;
  const legSwing2 = a.onGround && Math.abs(a.vx) > 0.4 ? Math.sin(walk + Math.PI) * 9 : 0;
  ctx.save(); ctx.translate(cx, cy); ctx.scale(face, 1);
  // aura
  const aura = ctx.createRadialGradient(0, 0, 4, 0, 0, 60);
  aura.addColorStop(0, "rgba(255,224,150,0.30)"); aura.addColorStop(1, "rgba(255,224,150,0)");
  ctx.fillStyle = aura; ctx.beginPath(); ctx.arc(0, -4, 60, 0, Math.PI * 2); ctx.fill();
  // sombra
  ctx.fillStyle = "rgba(0,0,0,0.28)"; ctx.beginPath(); ctx.ellipse(0, a.h / 2 - 2, 20, 6, 0, 0, Math.PI * 2); ctx.fill();
  // pernas (calças)
  drawLimb(-5, -6, 16, legSwing, 9, pal.garmentDark, 8, "#2a1c14");
  drawLimb(5, -6, 16, legSwing2, 9, pal.garment, 8, "#3a281a");
  // tronco (túnica/peitoral)
  const body = ctx.createLinearGradient(0, -20, 0, 20);
  body.addColorStop(0, pal.garmentTop); body.addColorStop(1, pal.garmentDark);
  ctx.fillStyle = body;
  roundRect(ctx, -13, -22, 26, 40, 7); ctx.fill();
  // cinto + faixa
  ctx.fillStyle = pal.accent; ctx.shadowColor = pal.accent; ctx.shadowBlur = 6;
  ctx.fillRect(-13, 8, 26, 5);
  ctx.shadowBlur = 0;
  ctx.strokeStyle = pal.accent; ctx.lineWidth = 2.5;
  ctx.beginPath(); ctx.moveTo(-10, -18); ctx.lineTo(8, 6); ctx.stroke();
  // armadura por cima (se houver)
  drawArmorOverlay();
  // braço de trás
  const armSwing = a.attackTimer > 0 ? -1.2 : (a.onGround ? Math.sin(walk + Math.PI) * 0.5 : -0.3);
  ctx.strokeStyle = "#d6a06a"; ctx.lineWidth = 7; ctx.lineCap = "round";
  ctx.beginPath(); ctx.moveTo(-6, -16); ctx.lineTo(-6 + Math.cos(armSwing + 1.7) * 15, -16 + Math.sin(armSwing + 1.7) * 15); ctx.stroke();
  // cabeça
  const headY = -32;
  ctx.fillStyle = "#d99a63"; ctx.fillRect(-5, -26, 10, 9); // pescoço
  const skin = ctx.createLinearGradient(0, headY - 12, 0, headY + 12);
  skin.addColorStop(0, "#f2c99a"); skin.addColorStop(1, "#d99a63");
  ctx.fillStyle = skin; ctx.beginPath(); ctx.arc(1, headY, 12, 0, Math.PI * 2); ctx.fill();
  // mandíbula marcada
  ctx.fillStyle = "rgba(150,90,50,0.25)";
  ctx.beginPath(); ctx.arc(1, headY + 6, 9, 0.2, Math.PI - 0.2); ctx.fill();
  // olho
  ctx.fillStyle = "#241033"; ctx.beginPath(); ctx.ellipse(6, headY - 1, 2, 3, 0, 0, Math.PI * 2); ctx.fill();
  ctx.fillStyle = "#ffcf6a"; ctx.beginPath(); ctx.arc(6.5, headY - 2, 1, 0, Math.PI * 2); ctx.fill();
  // sobrancelha grossa
  ctx.strokeStyle = "#3a2414"; ctx.lineWidth = 2; ctx.beginPath(); ctx.moveTo(2, headY - 5); ctx.lineTo(10, headY - 4); ctx.stroke();
  // boca
  ctx.strokeStyle = "#a5504a"; ctx.lineWidth = 1.4; ctx.beginPath(); ctx.moveTo(3, headY + 6); ctx.lineTo(9, headY + 6); ctx.stroke();
  // barba curta
  ctx.fillStyle = "rgba(60,36,20,0.55)";
  ctx.beginPath(); ctx.arc(2, headY + 7, 9, 0.15, Math.PI - 0.15); ctx.fill();
  // cabelo curto
  ctx.fillStyle = "#3a2414";
  ctx.beginPath();
  ctx.moveTo(-11, headY - 2); ctx.quadraticCurveTo(-12, headY - 15, 1, headY - 15);
  ctx.quadraticCurveTo(13, headY - 15, 12, headY - 1);
  ctx.quadraticCurveTo(8, headY - 9, 2, headY - 8);
  ctx.quadraticCurveTo(-4, headY - 9, -11, headY - 2); ctx.closePath(); ctx.fill();
  // diadema de luz
  ctx.fillStyle = pal.accent; ctx.shadowColor = pal.accent; ctx.shadowBlur = 8;
  ctx.fillRect(-11, headY - 11, 24, 3);
  ctx.shadowBlur = 0;
  // braço da frente + ESPADA
  const handX = 6 + Math.cos(armSwing) * 16, handY = -14 + Math.sin(armSwing) * 16;
  ctx.strokeStyle = "#f2c99a"; ctx.lineWidth = 7; ctx.beginPath(); ctx.moveTo(6, -14); ctx.lineTo(handX, handY); ctx.stroke();
  ctx.save(); ctx.translate(handX, handY);
  ctx.rotate(a.attackTimer > 0 ? -1.3 - (1 - a.attackTimer / 0.28) * 1.4 : -0.5);
  // lâmina
  const blade = ctx.createLinearGradient(0, -46, 0, 0);
  blade.addColorStop(0, "#ffffff"); blade.addColorStop(1, "#bfe9ff");
  ctx.fillStyle = blade; ctx.shadowColor = "#bfe9ff"; ctx.shadowBlur = 12;
  ctx.beginPath(); ctx.moveTo(-4, 0); ctx.lineTo(-3, -44); ctx.lineTo(0, -52); ctx.lineTo(3, -44); ctx.lineTo(4, 0); ctx.closePath(); ctx.fill();
  ctx.shadowBlur = 0;
  // guarda e cabo
  ctx.fillStyle = pal.accent; ctx.fillRect(-9, -2, 18, 4);
  ctx.fillStyle = "#6a4a2a"; ctx.fillRect(-2, 2, 4, 12);
  ctx.restore();
  ctx.restore();
}

// Dispatch de desenho do herói (aplica também tinta de roupa na Aurora)
function drawHero(p, x, y) {
  if (profile.hero === "kael") drawKael(p, x, y);
  else drawAurora(p, x, y);
}

/* =========================================================
   MOEDAS
   ========================================================= */
function makeCoin(x, y, value, loose) {
  return { x, y, baseY: y, vx: loose ? rand(-2.4, 2.4) : 0, vy: loose ? rand(-6, -3) : 0, phase: rand(0, 6), taken: false, value: value || 1, loose: !!loose, settle: 0 };
}
function dropCoins(level, x, y, n, value) {
  for (let i = 0; i < n; i++) level.coins.push(makeCoin(x + rand(-10, 10), y - rand(0, 16), value, true));
}
function updateCoins(dt, level) {
  if (!level.coins) return;
  for (const c of level.coins) {
    if (c.taken) continue;
    c.phase += dt * 4;
    if (c.loose) {
      c.vy += GRAV * 0.7; c.x += c.vx; c.y += c.vy; c.vx *= 0.96;
      // assenta ao tocar um sólido logo abaixo
      for (const pl of getSolids(level)) {
        if (c.x > pl.x && c.x < pl.x + pl.w && c.y >= pl.y - 6 && c.y <= pl.y + 14 && c.vy >= 0) {
          c.y = pl.y - 8; c.baseY = c.y; c.loose = false; c.vy = 0; break;
        }
      }
      if (c.y > level.height + 100) c.taken = true; // caiu no abismo
    } else {
      c.y = c.baseY + Math.sin(c.phase) * 4;
    }
    const rr = c.value >= 25 ? 22 : c.value >= 5 ? 16 : 13;
    if (!c.taken && aabb({ x: c.x - rr, y: c.y - rr, w: rr * 2, h: rr * 2 }, player.hitbox)) {
      c.taken = true; profile.coins += c.value;
      burst(c.x, c.y, c.value >= 5 ? "#ffe08a" : "#ffd24a", c.value >= 5 ? 22 : 10, { glow: true, max: c.value >= 5 ? 7 : 5 });
      updateHUD(level);
    }
  }
}
function drawCoin(c) {
  if (c.taken) return;
  const x = c.x - camera.sx, y = c.y - camera.sy;
  const big = c.value >= 5;
  const huge = c.value >= 25;
  const R = huge ? 17 : big ? 13 : 9;
  const sw = Math.abs(Math.cos(c.phase)) * 0.8 + 0.2; // giro (largura)
  const glow = ctx.createRadialGradient(x, y, 1, x, y, R + 10);
  glow.addColorStop(0, big ? "rgba(255,224,138,0.65)" : "rgba(255,210,74,0.5)"); glow.addColorStop(1, "transparent");
  ctx.fillStyle = glow; ctx.beginPath(); ctx.arc(x, y, R + 10, 0, Math.PI * 2); ctx.fill();
  ctx.save(); ctx.translate(x, y);
  const g = ctx.createLinearGradient(-R, 0, R, 0);
  if (huge) { g.addColorStop(0, "#7a3ad0"); g.addColorStop(0.5, "#e0b0ff"); g.addColorStop(1, "#7a3ad0"); }
  else if (big) { g.addColorStop(0, "#b8860a"); g.addColorStop(0.5, "#fff2c9"); g.addColorStop(1, "#b8860a"); }
  else { g.addColorStop(0, "#c8901a"); g.addColorStop(0.5, "#ffe08a"); g.addColorStop(1, "#c8901a"); }
  ctx.fillStyle = g; ctx.shadowColor = huge ? "#c98cff" : "#ffd24a"; ctx.shadowBlur = big ? 14 : 8;
  ctx.beginPath(); ctx.ellipse(0, 0, R * sw, R, 0, 0, Math.PI * 2); ctx.fill();
  // aro
  if (big) { ctx.strokeStyle = huge ? "#fff0ff" : "#fff6df"; ctx.lineWidth = 2; ctx.beginPath(); ctx.ellipse(0, 0, R * sw, R, 0, 0, Math.PI * 2); ctx.stroke(); }
  ctx.shadowBlur = 0;
  if (sw > 0.55) {
    ctx.fillStyle = huge ? "#3a1060" : "#a86f10";
    ctx.font = `bold ${big ? 12 : 9}px Poppins, sans-serif`; ctx.textAlign = "center"; ctx.textBaseline = "middle";
    ctx.fillText(big ? String(c.value) : "✦", 0, 0);
    ctx.textAlign = "left"; ctx.textBaseline = "alphabetic";
  }
  ctx.restore();
}

/* =========================================================
   MERCADOR & LOJA
   ========================================================= */
function drawMerchant(m) {
  const x = m.x - camera.sx, y = m.y - camera.sy;
  const t = now() / 500;
  // barraca / brilho
  const glow = ctx.createRadialGradient(x, y - 20, 6, x, y - 20, 70);
  glow.addColorStop(0, "rgba(255,224,138,0.35)"); glow.addColorStop(1, "transparent");
  ctx.fillStyle = glow; ctx.beginPath(); ctx.arc(x, y - 20, 70, 0, Math.PI * 2); ctx.fill();
  ctx.save(); ctx.translate(x, y);
  // corpo encapuzado
  ctx.fillStyle = "#3a2b6e";
  ctx.beginPath(); ctx.moveTo(-20, 6); ctx.quadraticCurveTo(0, -46, 20, 6); ctx.closePath(); ctx.fill();
  // rosto na sombra do capuz
  ctx.fillStyle = "#e9be8f"; ctx.beginPath(); ctx.arc(0, -18, 9, 0, Math.PI * 2); ctx.fill();
  ctx.fillStyle = "#ffd24a"; ctx.shadowColor = "#ffd24a"; ctx.shadowBlur = 8;
  ctx.beginPath(); ctx.arc(-3, -18, 1.6, 0, Math.PI * 2); ctx.arc(3, -18, 1.6, 0, Math.PI * 2); ctx.fill();
  ctx.shadowBlur = 0;
  ctx.strokeStyle = "#b5605a"; ctx.lineWidth = 1.2; ctx.beginPath(); ctx.arc(0, -14, 2.5, 0.2, Math.PI - 0.2); ctx.stroke();
  // sacola de moedas
  ctx.fillStyle = "#8a5a2a"; ctx.beginPath(); ctx.arc(14, -2, 7, 0, Math.PI * 2); ctx.fill();
  ctx.fillStyle = "#ffd24a"; ctx.font = "bold 12px Poppins"; ctx.textAlign = "center"; ctx.fillText("✦", 14, 2); ctx.textAlign = "left";
  ctx.restore();
  // símbolo flutuante da loja
  ctx.fillStyle = "#ffe08a"; ctx.shadowColor = "#ffe08a"; ctx.shadowBlur = 12;
  ctx.font = "20px Poppins"; ctx.textAlign = "center";
  ctx.fillText("🛒", x, y - 52 + Math.sin(t * 2) * 4);
  ctx.shadowBlur = 0; ctx.textAlign = "left";
  // prompt de interação quando perto
  if (level && !player.dead && Math.abs((player.x + player.w / 2) - m.x) < 90 && Math.abs((player.y + player.h / 2) - m.y) < 120) {
    m.near = true;
    ctx.fillStyle = "#fff6df"; ctx.font = "700 16px Poppins"; ctx.textAlign = "center";
    ctx.fillText("Pressione E / LB — LOJA", x, y - 78);
    ctx.textAlign = "left";
  } else m.near = false;
}

let shopReturn = null;
function openShop(returnFn) {
  if (state === "shop") return;
  shopReturn = returnFn || null;
  state = "shop";
  renderShop();
}
function closeShop() {
  if (shopReturn) { const f = shopReturn; shopReturn = null; f(); }
  else { hideOverlay(); state = "playing"; }
}
function renderShop() {
  const cats = [["outfit", "👗 Roupas"], ["armor", "🛡️ Armaduras"], ["weapon", "⚔️ Armas"], ["equip", "🔧 Equipamentos"]];
  const stock = shopStock(levelIndex);
  let html = `<div class="card shop-card">
    <div class="shop-head">
      <div class="chapter" style="margin:0">Loja do Mercador <span style="opacity:.7;font-size:12px">· estoque desta fase</span></div>
      <div class="shop-coins">✦ <b>${profile.coins}</b></div>
    </div>
    <div class="shop-grid">`;
  for (const [type, label] of cats) {
    const items = SHOP_ITEMS.filter((i) => i.type === type && stock.has(i.id));
    if (!items.length) continue;
    html += `<div class="shop-cat">${label}</div>`;
    for (const it of items) {
      const owned = !!profile.owned[it.id];
      const equipped = profile.equipped[it.type] === it.id;
      const canBuy = !owned && profile.coins >= it.price;
      let btn;
      if (equipped) btn = `<button class="shop-btn equipped" disabled>Equipado</button>`;
      else if (owned) btn = `<button class="shop-btn equip" data-equip="${it.id}">Equipar</button>`;
      else btn = `<button class="shop-btn buy ${canBuy ? "" : "off"}" ${canBuy ? "" : "disabled"} data-buy="${it.id}">Comprar ✦${it.price}</button>`;
      html += `<div class="shop-item">
        <div class="shop-item-info"><b>${it.name}</b><span>${it.desc}</span></div>
        ${btn}
      </div>`;
    }
  }
  html += `</div><div class="btn-row"><button class="btn" id="shop-close">Voltar ao Jogo</button></div></div>`;
  showOverlay(html);
  overlay.querySelectorAll("[data-buy]").forEach((b) => b.onclick = () => buyItem(b.getAttribute("data-buy")));
  overlay.querySelectorAll("[data-equip]").forEach((b) => b.onclick = () => equipItem(b.getAttribute("data-equip")));
  const close = document.getElementById("shop-close");
  if (close) close.onclick = closeShop;
}
function buyItem(id) {
  const it = itemById(id);
  if (!it || profile.owned[id] || profile.coins < it.price) return;
  profile.coins -= it.price; profile.owned[id] = true;
  equipItem(id);
}
function equipItem(id) {
  const it = itemById(id);
  if (!it || !profile.owned[id]) return;
  profile.equipped[it.type] = id;
  applyLoadout();
  // reaplica cura proporcional ao aumentar vida máxima
  player.hp = clamp(player.hp, 0, player.maxHp);
  if (level) updateHUD(level);
  renderShop();
}

/* =========================================================
   NOVOS MONSTROS: Ceifador, Feiticeiro, Limo
   ========================================================= */
function drawReaper(e, sx, sy) {
  const cx = sx + e.w / 2, cy = sy + e.h / 2;
  const hurt = e.hurtTimer > 0;
  ctx.save(); ctx.translate(cx, cy); ctx.scale(e.facing, 1);
  const col = hurt ? "#ffd0dc" : "#1c1030";
  // manto esvoaçante
  ctx.fillStyle = col;
  ctx.beginPath();
  ctx.moveTo(0, -22);
  ctx.quadraticCurveTo(24, -10, 18, 24);
  ctx.quadraticCurveTo(0, 14 + Math.sin(e.phase * 2) * 4, -18, 24);
  ctx.quadraticCurveTo(-24, -10, 0, -22);
  ctx.closePath(); ctx.fill();
  // capuz
  ctx.fillStyle = hurt ? "#ffb3c1" : "#2a1a45";
  ctx.beginPath(); ctx.arc(0, -20, 12, 0, Math.PI * 2); ctx.fill();
  // rosto vazio
  ctx.fillStyle = "#000"; ctx.beginPath(); ctx.arc(0, -18, 7, 0, Math.PI * 2); ctx.fill();
  const eye = hurt ? "#fff" : "#7dff5f";
  ctx.fillStyle = eye; ctx.shadowColor = eye; ctx.shadowBlur = 10;
  ctx.beginPath(); ctx.arc(-3, -18, 2, 0, Math.PI * 2); ctx.arc(3, -18, 2, 0, Math.PI * 2); ctx.fill();
  ctx.shadowBlur = 0;
  // foice
  ctx.strokeStyle = "#6a4a2a"; ctx.lineWidth = 3; ctx.lineCap = "round";
  ctx.beginPath(); ctx.moveTo(16, 18); ctx.lineTo(24, -22); ctx.stroke();
  ctx.strokeStyle = e.slashing > 0 ? "#eaffea" : "#c9d2e6"; ctx.lineWidth = 4;
  ctx.shadowColor = "#9fe8a0"; ctx.shadowBlur = e.slashing > 0 ? 14 : 4;
  ctx.beginPath(); ctx.arc(24, -22, 16, Math.PI, Math.PI * 1.9); ctx.stroke();
  ctx.shadowBlur = 0;
  ctx.restore();
}
function drawMage(e, sx, sy) {
  const cx = sx + e.w / 2, cy = sy + e.h / 2;
  const hurt = e.hurtTimer > 0;
  const fade = e.teleT > 0 ? 0.35 : 1;
  ctx.save(); ctx.translate(cx, cy); ctx.globalAlpha = fade; ctx.scale(e.facing, 1);
  // túnica longa
  const g = ctx.createLinearGradient(0, -20, 0, 26);
  g.addColorStop(0, hurt ? "#ffd0dc" : "#4a2a86"); g.addColorStop(1, hurt ? "#e07a92" : "#1e1040");
  ctx.fillStyle = g;
  ctx.beginPath(); ctx.moveTo(-14, -12); ctx.quadraticCurveTo(0, -26, 14, -12);
  ctx.quadraticCurveTo(20, 20, 0, 28); ctx.quadraticCurveTo(-20, 20, -14, -12); ctx.closePath(); ctx.fill();
  // chapéu pontudo
  ctx.fillStyle = hurt ? "#ffb3c1" : "#33205e";
  ctx.beginPath(); ctx.moveTo(-14, -20); ctx.lineTo(4, -52); ctx.lineTo(14, -18); ctx.closePath(); ctx.fill();
  ctx.fillStyle = "#ffd24a"; ctx.beginPath(); ctx.arc(4, -52, 3, 0, Math.PI * 2); ctx.fill();
  // rosto
  ctx.fillStyle = "#cfe0ff"; ctx.beginPath(); ctx.arc(0, -16, 9, 0, Math.PI * 2); ctx.fill();
  const eye = hurt ? "#fff" : "#b98cff";
  ctx.fillStyle = eye; ctx.shadowColor = eye; ctx.shadowBlur = 10;
  ctx.beginPath(); ctx.arc(-3, -16, 2, 0, Math.PI * 2); ctx.arc(4, -16, 2, 0, Math.PI * 2); ctx.fill();
  ctx.shadowBlur = 0;
  // orbe na mão
  const oc = e.castT > 0 ? "#ff8cf0" : "#b98cff";
  ctx.fillStyle = oc; ctx.shadowColor = oc; ctx.shadowBlur = 14;
  ctx.beginPath(); ctx.arc(14, 2, 5 + (e.castT > 0 ? 2 : 0), 0, Math.PI * 2); ctx.fill();
  ctx.shadowBlur = 0;
  ctx.globalAlpha = 1; ctx.restore();
}
function drawSlime(e, sx, sy) {
  const cx = sx + e.w / 2, cy = sy + e.h / 2;
  const hurt = e.hurtTimer > 0;
  const squash = e.onGround ? 1 + Math.sin(e.phase * 3) * 0.06 : 0.85;
  ctx.save(); ctx.translate(cx, cy + e.h / 2);
  ctx.fillStyle = "rgba(0,0,0,0.28)"; ctx.beginPath(); ctx.ellipse(0, 0, 22, 5, 0, 0, Math.PI * 2); ctx.fill();
  const g = ctx.createRadialGradient(-4, -14, 3, 0, -10, 26);
  g.addColorStop(0, hurt ? "#eaffef" : "#8affc0"); g.addColorStop(1, hurt ? "#ff9bb5" : "#1a9a5a");
  ctx.fillStyle = g; ctx.globalAlpha = 0.92;
  ctx.beginPath();
  ctx.ellipse(0, -18 * squash, 24, 22 * squash, 0, 0, Math.PI * 2); ctx.fill();
  ctx.globalAlpha = 1;
  // olhos
  ctx.fillStyle = "#0a2b18"; ctx.beginPath(); ctx.ellipse(-7, -22, 3, 4, 0, 0, Math.PI * 2); ctx.ellipse(7, -22, 3, 4, 0, 0, Math.PI * 2); ctx.fill();
  ctx.fillStyle = "#fff"; ctx.beginPath(); ctx.arc(-6, -23, 1.2, 0, Math.PI * 2); ctx.arc(8, -23, 1.2, 0, Math.PI * 2); ctx.fill();
  // boca
  ctx.strokeStyle = "#0a2b18"; ctx.lineWidth = 1.6; ctx.beginPath(); ctx.arc(0, -14, 4, 0.15, Math.PI - 0.15); ctx.stroke();
  ctx.restore();
}

/* ---------- Titã (golem gigante) ---------- */
function drawTitan(e, sx, sy) {
  const cx = sx + e.w / 2, cy = sy + e.h / 2;
  const hurt = e.hurtTimer > 0;
  ctx.save(); ctx.translate(cx, cy); ctx.scale(e.facing, 1);
  ctx.fillStyle = "rgba(0,0,0,0.34)";
  ctx.beginPath(); ctx.ellipse(0, e.h / 2 - 2, 52, 11, 0, 0, Math.PI * 2); ctx.fill();
  const rock = ctx.createLinearGradient(0, -66, 0, 66);
  rock.addColorStop(0, hurt ? "#ffc0a0" : "#7a5238"); rock.addColorStop(1, hurt ? "#e08060" : "#2e1c12");
  // pernas
  ctx.fillStyle = hurt ? "#e08060" : "#2e1c12";
  roundRect(ctx, -36, 20, 28, 44, 8); ctx.fill();
  roundRect(ctx, 8, 20, 28, 44, 8); ctx.fill();
  // braços enormes
  ctx.fillStyle = rock;
  roundRect(ctx, -62, -40, 26, 70, 12); ctx.fill();
  roundRect(ctx, 36, -40, 26, 70, 12); ctx.fill();
  // punhos
  ctx.fillStyle = hurt ? "#ffd0b0" : "#3a2418";
  ctx.beginPath(); ctx.arc(-49, 34, 16, 0, Math.PI * 2); ctx.arc(49, 34, 16, 0, Math.PI * 2); ctx.fill();
  // corpo
  ctx.fillStyle = rock;
  roundRect(ctx, -46, -50, 92, 78, 16); ctx.fill();
  // fissuras de lava
  ctx.strokeStyle = e.charging > 0 ? "#ff7a3b" : "#ff9c5c"; ctx.lineWidth = 3.5; ctx.shadowColor = ctx.strokeStyle; ctx.shadowBlur = 14;
  ctx.beginPath(); ctx.moveTo(-24, -34); ctx.lineTo(-8, -6); ctx.lineTo(-20, 18); ctx.stroke();
  ctx.beginPath(); ctx.moveTo(18, -30); ctx.lineTo(6, -2); ctx.lineTo(22, 20); ctx.stroke();
  ctx.shadowBlur = 0;
  // cabeça
  ctx.fillStyle = rock; roundRect(ctx, -26, -82, 52, 36, 10); ctx.fill();
  // olhos
  const eye = hurt ? "#fff" : "#ff7a3b";
  ctx.fillStyle = eye; ctx.shadowColor = eye; ctx.shadowBlur = 14;
  ctx.beginPath(); ctx.rect(-18, -70, 12, 7); ctx.rect(6, -70, 12, 7); ctx.fill();
  ctx.shadowBlur = 0;
  // boca/dentes
  ctx.fillStyle = "#160a05"; ctx.fillRect(-16, -54, 32, 6);
  ctx.fillStyle = rock; for (let i = -13; i < 15; i += 6) { ctx.fillRect(i, -54, 3, 6); }
  ctx.restore();
}

/* ---------- Dragão (voador grande) ---------- */
function drawDragon(e, sx, sy) {
  const cx = sx + e.w / 2, cy = sy + e.h / 2;
  const hurt = e.hurtTimer > 0;
  const flap = Math.sin(e.phase * 4) * 0.5;
  ctx.save(); ctx.translate(cx, cy); ctx.scale(e.facing, 1);
  const col = hurt ? "#ffc0a0" : "#5a2a18";
  const belly = hurt ? "#ffe0c0" : "#c26a2a";
  // asas
  ctx.fillStyle = col;
  for (const s of [-1, 1]) {
    ctx.save(); ctx.scale(1, s); ctx.rotate(flap);
    ctx.beginPath(); ctx.moveTo(-6, 0);
    ctx.quadraticCurveTo(-40, -34, -66, -12);
    ctx.quadraticCurveTo(-44, -6, -50, 10);
    ctx.quadraticCurveTo(-30, 2, -6, 8);
    ctx.closePath(); ctx.fill();
    ctx.restore();
  }
  // cauda
  ctx.strokeStyle = col; ctx.lineWidth = 12; ctx.lineCap = "round";
  ctx.beginPath(); ctx.moveTo(-30, 6); ctx.quadraticCurveTo(-58, 12 + Math.sin(e.phase) * 6, -70, 0); ctx.stroke();
  // corpo
  const bg = ctx.createLinearGradient(0, -20, 0, 24);
  bg.addColorStop(0, col); bg.addColorStop(1, belly);
  ctx.fillStyle = bg;
  ctx.beginPath(); ctx.ellipse(0, 4, 34, 22, 0, 0, Math.PI * 2); ctx.fill();
  // pescoço + cabeça
  ctx.fillStyle = col;
  ctx.beginPath(); ctx.moveTo(20, -6); ctx.quadraticCurveTo(46, -20, 52, -6); ctx.quadraticCurveTo(60, 2, 50, 10); ctx.quadraticCurveTo(36, 6, 20, 10); ctx.closePath(); ctx.fill();
  // focinho
  ctx.fillStyle = belly; ctx.beginPath(); ctx.moveTo(48, -6); ctx.lineTo(64, -2); ctx.lineTo(48, 6); ctx.closePath(); ctx.fill();
  // chifres
  ctx.strokeStyle = "#ffd0a0"; ctx.lineWidth = 3;
  ctx.beginPath(); ctx.moveTo(44, -12); ctx.lineTo(40, -24); ctx.stroke();
  // olho
  const eye = hurt ? "#fff" : "#ffd24a";
  ctx.fillStyle = eye; ctx.shadowColor = "#ff7a3b"; ctx.shadowBlur = 10;
  ctx.beginPath(); ctx.arc(46, -4, 3.4, 0, Math.PI * 2); ctx.fill();
  ctx.shadowBlur = 0; ctx.fillStyle = "#3a1000";
  ctx.beginPath(); ctx.arc(47, -4, 1.4, 0, Math.PI * 2); ctx.fill();
  // brasa na boca
  if (e.breatheCd < 0.5) { ctx.fillStyle = "#ff7a3b"; ctx.shadowColor = "#ff7a3b"; ctx.shadowBlur = 12; ctx.beginPath(); ctx.arc(62, 0, 4, 0, Math.PI * 2); ctx.fill(); ctx.shadowBlur = 0; }
  ctx.restore();
}

/* =========================================================
   PODERES DE CHEFÃO (desbloqueados ao vencer chefões)
   ========================================================= */
function resetProfile() {
  profile.coins = 0; profile.owned = {}; profile.powers = {};
  profile.equipped = { outfit: null, armor: null, weapon: null, equip: null };
}
function enemiesInView(level, pad) {
  pad = pad || 200; const arr = [];
  for (const en of level.enemies) { if (en.dead) continue; const sx = en.x - camera.x; if (sx > -pad && sx < VW + pad) arr.push(en); }
  return arr;
}
function firePower(id, level) {
  const bp = BOSS_POWERS[id];
  if (!profile.powers[id] || player.powerCd > 0 || player.energy < bp.cost) return false;
  player.powerCd = 0.7; player.energy -= bp.cost;
  if (id === "quake") doQuake(level);
  else if (id === "storm") doStorm(level);
  else if (id === "meteor") doMeteor(level);
  else if (id === "void") doVoid(level);
  return true;
}
function useBossPowers(dt, level) {
  if (player.powerCd > 0) player.powerCd -= dt;
  for (const id in BOSS_POWERS) { if (consume(BOSS_POWERS[id].key)) firePower(id, level); }
  // botão touch "★": dispara o primeiro poder disponível
  if (consume("power")) { for (const id in BOSS_POWERS) { if (firePower(id, level)) break; } }
}
function doQuake(level) {
  const cx = player.x + player.w / 2, cy = player.y + player.h / 2;
  level.novas.push({ x: cx, y: cy, r: 0, max: 280, t: 0.5 });
  camera.shake = 16; burst(cx, cy, "#e0a35f", 42, { glow: true, max: 9 });
  const R = 280;
  for (const en of level.enemies) { if (en.dead) continue; const ex = en.x + en.w / 2, ey = en.y + en.h / 2; if (Math.hypot(ex - cx, ey - cy) < R + Math.max(en.w, en.h) / 2) { damageEnemy(en, 80 * player.dmgMult, ex > cx ? 1 : -1, level); en.vy = -8; } }
  if (level.boss && !level.boss.dead && Math.hypot(level.boss.x - cx, level.boss.y - cy) < R + 120) damageEnemy(level.boss, 60, 1, level);
}
function doStorm(level) {
  camera.shake = 8; weather.flash = Math.max(weather.flash, 0.8);
  const targets = enemiesInView(level, 160);
  if (level.boss && !level.boss.dead) targets.push(level.boss);
  for (const en of targets) { const x = en.x + en.w / 2, y = en.y + en.h / 2; level.fx.push({ type: "bolt", x, y, t: 0.35 }); damageEnemy(en, (en.boss ? 50 : 60) * player.dmgMult, 1, level); burst(x, y, "#bcd4ff", 14, { glow: true, max: 6 }); }
}
function doMeteor(level) {
  camera.shake = 10;
  const targets = enemiesInView(level, 160);
  if (level.boss && !level.boss.dead) targets.push(level.boss);
  for (const en of targets) { const x = en.x + en.w / 2, y = en.y + en.h / 2; level.fx.push({ type: "meteor", x, y, t: 0.5 }); damageEnemy(en, (en.boss ? 55 : 70) * player.dmgMult, 1, level); burst(x, y, "#ff7a3b", 16, { glow: true, max: 7 }); }
  for (let i = 0; i < 6; i++) level.fx.push({ type: "meteor", x: camera.x + rand(0, VW), y: camera.y + rand(240, 620), t: rand(0.3, 0.6) });
}
function doVoid(level) {
  const cx = player.x + player.w / 2, cy = player.y + player.h / 2;
  level.novas.push({ x: cx, y: cy, r: 0, max: 440, t: 0.6 });
  camera.shake = 20; burst(cx, cy, "#c98cff", 64, { glow: true, max: 11, maxLife: 1.4 });
  const targets = enemiesInView(level, 300);
  if (level.boss && !level.boss.dead) targets.push(level.boss);
  for (const en of targets) damageEnemy(en, (en.boss ? 70 : 110) * player.dmgMult, en.x + en.w / 2 > cx ? 1 : -1, level);
}
function updateFx(dt, level) {
  if (!level.fx) return;
  for (let i = level.fx.length - 1; i >= 0; i--) { level.fx[i].t -= dt; if (level.fx[i].t <= 0) level.fx.splice(i, 1); }
}
function drawFx(level) {
  if (!level.fx) return;
  for (const f of level.fx) {
    const a = clamp(f.t / 0.4, 0, 1);
    if (f.type === "bolt") {
      const x = f.x - camera.sx, y1 = f.y - camera.sy, y0 = y1 - 460;
      ctx.strokeStyle = `rgba(210,230,255,${a})`; ctx.shadowColor = "#bcd4ff"; ctx.shadowBlur = 22; ctx.lineWidth = 4; ctx.lineCap = "round";
      let bx = x, by = y0; ctx.beginPath(); ctx.moveTo(bx, by);
      for (let s = 0; s < 9; s++) { bx += rand(-16, 16); by += (y1 - y0) / 9; ctx.lineTo(bx, by); }
      ctx.stroke(); ctx.shadowBlur = 0;
    } else if (f.type === "meteor") {
      const frac = clamp(f.t / 0.5, 0, 1); // 1 (alto) -> 0 (impacto)
      const tx = f.x - camera.sx, ty = f.y - camera.sy;
      const hx = tx - 120 * frac, hy = ty - 300 * frac;
      ctx.strokeStyle = `rgba(255,150,60,${a})`; ctx.lineWidth = 6; ctx.lineCap = "round"; ctx.shadowColor = "#ff7a3b"; ctx.shadowBlur = 18;
      ctx.beginPath(); ctx.moveTo(hx - 40 * frac, hy - 100 * frac); ctx.lineTo(hx, hy); ctx.stroke();
      ctx.fillStyle = "#ffcf8a"; ctx.beginPath(); ctx.arc(hx, hy, 8, 0, Math.PI * 2); ctx.fill(); ctx.shadowBlur = 0;
    }
  }
}

/* =========================================================
   CHEFÕES: Tempestas (tempestade) e Ignis (vulcão)
   ========================================================= */
function bossUpdateTempestas(b, dt, level) {
  b.phaseT += dt; if (b.hurtTimer > 0) b.hurtTimer -= dt; b.stateT -= dt;
  const enraged = b.hp / b.maxHp < 0.4;
  const dx = (player.x + player.w / 2) - (b.x + b.w / 2);
  b.facing = dx > 0 ? 1 : -1; b.attacking = false;
  // paira sobre o jogador
  const hoverY = player.y - 200;
  b.x = lerp(b.x, player.x + player.w / 2 - b.w / 2 + (Math.sin(b.phaseT) * 120), 0.02);
  b.y = lerp(b.y, clamp(hoverY, 60, level.height - 400), 0.03) + Math.sin(b.phaseT * 1.5) * 3;
  weather.flash = Math.max(weather.flash, 0.05);
  if (b.state === "idle") {
    if (b.stateT <= 0) { const r = Math.random(); if (r < 0.5) { b.state = "bolts"; b.stateT = 1.1; b.shots = enraged ? 6 : 4; b.shotTimer = 0; } else { b.state = "raincall"; b.stateT = 1.2; b.rained = false; } }
  } else if (b.state === "bolts") {
    b.attacking = true; b.shotTimer -= dt;
    if (b.shots > 0 && b.shotTimer <= 0) { b.shots--; b.shotTimer = 0.18; const a = Math.atan2((player.y) - (b.y + 40), (player.x) - (b.x + b.w / 2)); level.projectiles.push({ x: b.x + b.w / 2, y: b.y + 40, vx: Math.cos(a) * 6.5, vy: Math.sin(a) * 6.5, r: 8, color: "#bcd4ff", from: "enemy", dmg: 14, life: 4 }); burst(b.x + b.w / 2, b.y + 40, "#bcd4ff", 5, { glow: true, max: 4, g: 0 }); }
    if (b.stateT <= 0) { b.state = "idle"; b.stateT = enraged ? 0.6 : 1.1; }
  } else if (b.state === "raincall") {
    b.attacking = true;
    if (!b.rained && b.stateT < 0.7) {
      b.rained = true; weather.flash = 1; camera.shake = 8;
      const n = enraged ? 9 : 6;
      for (let i = 0; i < n; i++) { const px = camera.x + (VW / n) * i + rand(0, VW / n); level.projectiles.push({ x: px, y: camera.y - 20, vx: 0, vy: 6, r: 8, color: "#bcd4ff", from: "enemy", dmg: 14, life: 4 }); }
    }
    if (b.stateT <= 0) { b.state = "idle"; b.stateT = enraged ? 0.7 : 1.2; }
  }
  if (aabb(b, player.hitbox)) hurtPlayer(enraged ? 18 : 14, b.facing);
}
function bossUpdateIgnis(b, dt, level) {
  b.phaseT += dt; if (b.hurtTimer > 0) b.hurtTimer -= dt; b.stateT -= dt;
  const enraged = b.hp / b.maxHp < 0.4;
  const dx = (player.x + player.w / 2) - (b.x + b.w / 2);
  b.facing = dx > 0 ? 1 : -1;
  b.vy += GRAV; if (b.vy > 16) b.vy = 16; b.attacking = false;
  if (b.state === "idle") {
    b.vx = lerp(b.vx, clamp(dx * 0.015, -2, 2), 0.1);
    if (b.stateT <= 0) { const r = Math.random(); if (r < 0.4) { b.state = "fireballs"; b.stateT = 1.2; b.shots = enraged ? 6 : 4; b.shotTimer = 0.2; } else if (r < 0.72) { b.state = "meteors"; b.stateT = 1.3; b.rained = false; } else { b.state = "stomp"; b.stateT = 1.0; b.slammed = false; } }
  } else if (b.state === "fireballs") {
    b.vx *= 0.85; b.attacking = true; b.shotTimer -= dt;
    if (b.shots > 0 && b.shotTimer <= 0) { b.shots--; b.shotTimer = 0.22; const dir = Math.sign(dx) || 1; const pw = clamp(Math.abs(dx) / 80, 4, 9); level.projectiles.push({ x: b.x + b.w / 2, y: b.y + 20, vx: dir * pw + rand(-1, 1), vy: -8, r: 11, color: "#ff7a3b", from: "enemy", dmg: 16, arc: true, life: 5 }); burst(b.x + b.w / 2, b.y + 20, "#ff9c5c", 5, { glow: true, max: 4, g: 0 }); }
    if (b.stateT <= 0) { b.state = "idle"; b.stateT = enraged ? 0.8 : 1.3; }
  } else if (b.state === "meteors") {
    b.attacking = true;
    if (!b.rained && b.stateT < 0.7) { b.rained = true; camera.shake = 10; const n = enraged ? 8 : 5; for (let i = 0; i < n; i++) { const px = camera.x + rand(0, VW); level.fx.push({ type: "meteor", x: px, y: player.y + rand(-40, 40), t: rand(0.4, 0.7) }); level.projectiles.push({ x: px, y: camera.y - 20, vx: 0, vy: 7, r: 10, color: "#ff7a3b", from: "enemy", dmg: 16, life: 4 }); } }
    if (b.stateT <= 0) { b.state = "idle"; b.stateT = enraged ? 0.8 : 1.3; }
  } else if (b.state === "stomp") {
    b.attacking = true;
    if (!b.slammed && b.onGround) { b.vy = -13; b.vx = clamp(dx * 0.04, -5, 5); b.slammed = true; }
    if (b.stateT < -1.2) { b.state = "idle"; b.stateT = 1.2; }
    if (b.slammed && b.onGround && b.vy === 0 && b.stateT < 0.6) { camera.shake = 16; burst(b.x + b.w / 2, b.y + b.h, "#ff7a3b", 30, { glow: true, max: 8, up: 2 }); for (const s of [-1, 1]) level.projectiles.push({ x: b.x + b.w / 2, y: b.y + b.h - 10, vx: s * 4.5, vy: -1, r: 12, color: "#ff9c5c", from: "enemy", ground: true, life: 1.2 }); b.state = "idle"; b.stateT = enraged ? 0.8 : 1.3; }
  }
  moveWithCollision(b, level);
  if (aabb(b, player.hitbox)) hurtPlayer(enraged ? 20 : 15, b.facing);
}
function drawTempestas(b, sx, sy) {
  const cx = sx + b.w / 2, cy = sy + b.h / 2, t = b.phaseT, hurt = b.hurtTimer > 0;
  ctx.save(); ctx.translate(cx, cy);
  const aura = ctx.createRadialGradient(0, 0, 20, 0, 0, 150);
  aura.addColorStop(0, hurt ? "rgba(255,255,255,0.5)" : "rgba(140,180,255,0.4)"); aura.addColorStop(1, "rgba(20,30,50,0)");
  ctx.fillStyle = aura; ctx.beginPath(); ctx.arc(0, 0, 150, 0, Math.PI * 2); ctx.fill();
  ctx.scale(b.facing, 1);
  // manto de nuvem
  const body = ctx.createLinearGradient(0, -60, 0, 70);
  body.addColorStop(0, hurt ? "#eaf2ff" : "#3a4a70"); body.addColorStop(1, hurt ? "#b0c4e0" : "#141e30");
  ctx.fillStyle = body;
  ctx.beginPath(); ctx.moveTo(-40, -40); ctx.quadraticCurveTo(0, -70, 40, -40); ctx.quadraticCurveTo(56, 30, 40, 74); ctx.quadraticCurveTo(0, 62 + Math.sin(t * 2) * 8, -40, 74); ctx.quadraticCurveTo(-56, 30, -40, -40); ctx.closePath(); ctx.fill();
  // braços de raio
  ctx.strokeStyle = b.attacking ? "#eaf2ff" : "#8fb4ff"; ctx.lineWidth = 5; ctx.lineCap = "round"; ctx.shadowColor = "#bcd4ff"; ctx.shadowBlur = 12;
  for (const s of [-1, 1]) { ctx.beginPath(); ctx.moveTo(s * 34, -30); ctx.lineTo(s * 54, 4 + Math.sin(t * 3) * 6); ctx.lineTo(s * 46, 24); ctx.stroke(); }
  ctx.shadowBlur = 0;
  // cabeça
  ctx.fillStyle = hurt ? "#eaf2ff" : "#1c2740"; ctx.beginPath(); ctx.arc(0, -52, 24, 0, Math.PI * 2); ctx.fill();
  // coroa de raios
  ctx.strokeStyle = "#bcd4ff"; ctx.lineWidth = 3; ctx.shadowColor = "#bcd4ff"; ctx.shadowBlur = 14;
  for (let i = -2; i <= 2; i++) { ctx.beginPath(); ctx.moveTo(i * 9, -70); ctx.lineTo(i * 11, -92); ctx.stroke(); }
  ctx.shadowBlur = 0;
  // olhos
  const eye = hurt ? "#fff" : "#eaf2ff"; ctx.fillStyle = eye; ctx.shadowColor = "#bcd4ff"; ctx.shadowBlur = 16;
  ctx.beginPath(); ctx.ellipse(-9, -54, 4, 6, 0.2, 0, Math.PI * 2); ctx.ellipse(9, -54, 4, 6, -0.2, 0, Math.PI * 2); ctx.fill(); ctx.shadowBlur = 0;
  ctx.strokeStyle = eye; ctx.lineWidth = 2; ctx.beginPath(); ctx.moveTo(-8, -40); ctx.quadraticCurveTo(0, -34, 8, -40); ctx.stroke();
  ctx.restore();
}
function drawIgnis(b, sx, sy) {
  const cx = sx + b.w / 2, cy = sy + b.h / 2, t = b.phaseT, hurt = b.hurtTimer > 0;
  ctx.save(); ctx.translate(cx, cy);
  const aura = ctx.createRadialGradient(0, 0, 20, 0, 0, 150);
  aura.addColorStop(0, hurt ? "rgba(255,220,150,0.5)" : "rgba(255,120,40,0.4)"); aura.addColorStop(1, "rgba(30,8,4,0)");
  ctx.fillStyle = aura; ctx.beginPath(); ctx.arc(0, 0, 150 + Math.sin(t * 3) * 8, 0, Math.PI * 2); ctx.fill();
  ctx.fillStyle = "rgba(0,0,0,0.4)"; ctx.beginPath(); ctx.ellipse(0, b.h / 2 - 4, 60, 14, 0, 0, Math.PI * 2); ctx.fill();
  ctx.scale(b.facing, 1);
  const rock = ctx.createLinearGradient(0, -70, 0, 70);
  rock.addColorStop(0, hurt ? "#ffd0a0" : "#5a2410"); rock.addColorStop(1, "#1a0805");
  // pernas
  ctx.fillStyle = "#1a0805"; roundRect(ctx, -44, 24, 30, 46, 9); ctx.fill(); roundRect(ctx, 14, 24, 30, 46, 9); ctx.fill();
  // corpo com veios de lava
  ctx.fillStyle = rock; roundRect(ctx, -50, -56, 100, 90, 18); ctx.fill();
  ctx.strokeStyle = b.attacking ? "#ffd24a" : "#ff7a3b"; ctx.lineWidth = 4; ctx.shadowColor = "#ff7a3b"; ctx.shadowBlur = 16;
  ctx.beginPath(); ctx.moveTo(-26, -40); ctx.lineTo(-8, -6); ctx.lineTo(-22, 24); ctx.stroke();
  ctx.beginPath(); ctx.moveTo(20, -36); ctx.lineTo(6, -2); ctx.lineTo(24, 26); ctx.stroke();
  ctx.shadowBlur = 0;
  // braços
  ctx.strokeStyle = "#3a1408"; ctx.lineWidth = 14; ctx.lineCap = "round";
  const arm = Math.sin(t * 1.5) * 0.3 + (b.attacking ? -0.6 : 0);
  for (const s of [-1, 1]) { ctx.beginPath(); ctx.moveTo(s * 40, -30); ctx.lineTo(s * (64 + Math.cos(arm) * 6), 16 + Math.sin(arm) * 8); ctx.stroke(); }
  // cabeça
  ctx.fillStyle = rock; roundRect(ctx, -30, -92, 60, 42, 12); ctx.fill();
  // chifres em chamas
  ctx.fillStyle = "#ff7a3b"; ctx.shadowColor = "#ff7a3b"; ctx.shadowBlur = 14;
  ctx.beginPath(); ctx.moveTo(-26, -88); ctx.lineTo(-40, -114); ctx.lineTo(-16, -92); ctx.closePath(); ctx.fill();
  ctx.beginPath(); ctx.moveTo(26, -88); ctx.lineTo(40, -114); ctx.lineTo(16, -92); ctx.closePath(); ctx.fill();
  ctx.shadowBlur = 0;
  // olhos
  const eye = hurt ? "#fff" : "#ffd24a"; ctx.fillStyle = eye; ctx.shadowColor = "#ff7a3b"; ctx.shadowBlur = 18;
  ctx.beginPath(); ctx.rect(-22, -80, 15, 8); ctx.rect(7, -80, 15, 8); ctx.fill(); ctx.shadowBlur = 0;
  // boca
  ctx.fillStyle = "#1a0805"; roundRect(ctx, -20, -60, 40, 8, 3); ctx.fill();
  ctx.fillStyle = "#ff7a3b"; for (let i = -16; i < 18; i += 8) ctx.fillRect(i, -60, 4, 8);
  ctx.restore();
}

/* ---------- Início ---------- */
menuScreen();
requestAnimationFrame(loop);
