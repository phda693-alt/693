/* =========================================================
   AURORA — A Guardiã da Luz
   Jogo de ação/plataforma em Canvas 2D (gráficos vetoriais)
   ========================================================= */
"use strict";

/* ---------- Canvas & contexto ---------- */
const canvas = document.getElementById("game");
const ctx = canvas.getContext("2d");
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
  reset(x, y) {
    this.x = x; this.y = y; this.vx = 0; this.vy = 0;
    this.hp = this.maxHp; this.energy = this.maxEnergy;
    this.dead = false; this.invuln = 0; this.jumps = 0;
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
  // gravidade
  p.vy += GRAV;
  if (p.vy > 18) p.vy = 18;

  // integração + colisão
  moveWithCollision(p, level);

  // ataque
  if (p.attackCd > 0) p.attackCd -= dt;
  if (p.attackTimer > 0) p.attackTimer -= dt;
  if ((consume("k") || consume("j") || consume("x")) && p.attackCd <= 0) {
    p.attackTimer = 0.28; p.attackCd = 0.34;
    doAttack(level);
  }

  // timers
  if (p.dashCd > 0) p.dashCd -= dt;
  if (p.invuln > 0) p.invuln -= dt;
  p.energy = clamp(p.energy + dt * 14, 0, p.maxEnergy);

  // animação de caminhada
  if (p.onGround && Math.abs(p.vx) > 0.4) p.walkPhase += dt * 14 * (Math.abs(p.vx) / MAXVX + 0.4);

  // morte por queda
  if (p.y > level.height + 200) hurtPlayer(999, 0);
}

function moveWithCollision(e, level) {
  // X
  e.x += e.vx;
  for (const pl of level.platforms) {
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
  for (const pl of level.platforms) {
    if (aabb(e, pl)) {
      if (e.vy > 0) { e.y = pl.y - e.h; e.onGround = true; e.jumps = 0; }
      else if (e.vy < 0) e.y = pl.y + pl.h;
      e.vy = 0;
    }
  }
}

function doAttack(level) {
  const p = player;
  const range = 62;
  const hit = {
    x: p.facing > 0 ? p.x + p.w - 6 : p.x - range + 6,
    y: p.y + 6, w: range, h: p.h - 14,
  };
  // efeito de corte (slash)
  level.slashes.push({ x: p.x + p.w / 2 + p.facing * 30, y: p.y + p.h / 2, t: 0.28, max: 0.28, dir: p.facing });
  burst(hit.x + (p.facing > 0 ? range : 0), p.y + p.h / 2, "#ffe08a", 10, { glow: true, max: 4, g: 0 });

  for (const en of level.enemies) {
    if (en.dead) continue;
    if (aabb(hit, en)) damageEnemy(en, 34, p.facing, level);
  }
  if (level.boss && !level.boss.dead && aabb(hit, level.boss)) damageEnemy(level.boss, 24, p.facing, level);
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
    if (!en.boss) { player.energy = clamp(player.energy + 8, 0, player.maxEnergy); }
  }
}

function hurtPlayer(dmg, dir) {
  const p = player;
  if (p.invuln > 0 || p.dead) return;
  p.hp -= dmg; p.invuln = 1.0;
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
  }
}

function willBeGrounded(en, level) {
  const probe = { x: en.x + (en.vx >= 0 ? en.w : -6), y: en.y + en.h + 4, w: 6, h: 8 };
  return level.platforms.some((pl) => aabb(probe, pl));
}

/* ---------- Boss Nöx ---------- */
function bossUpdate(b, dt, level) {
  if (b.dead) return;
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

/* ---------- Projéteis ---------- */
function updateProjectiles(dt, level) {
  for (let i = level.projectiles.length - 1; i >= 0; i--) {
    const pr = level.projectiles[i];
    pr.x += pr.vx; pr.y += pr.vy;
    if (!pr.ground) pr.vy += 0.02; else pr.vy += GRAV * 0.5;
    pr.life -= dt;
    // rastro
    if (Math.random() < 0.5) spawnParticle({ x: pr.x, y: pr.y, vx: 0, vy: 0, g: 0, r: pr.r * 0.5, color: pr.color, life: 0.3, maxLife: 0.3, glow: true });
    let remove = pr.life <= 0;
    if (aabb({ x: pr.x - pr.r, y: pr.y - pr.r, w: pr.r * 2, h: pr.r * 2 }, player.hitbox)) {
      hurtPlayer(pr.ground ? 18 : 12, Math.sign(pr.vx) || 1); remove = true;
      burst(pr.x, pr.y, pr.color, 10, { glow: true, max: 5 });
    }
    for (const pl of level.platforms) { if (aabb({ x: pr.x - pr.r, y: pr.y - pr.r, w: pr.r * 2, h: pr.r * 2 }, pl)) { remove = true; break; } }
    if (remove) level.projectiles.splice(i, 1);
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
    isBoss: !!def.isBoss,
    platforms: def.platforms.map((p) => ({ x: p[0], y: p[1], w: p[2], h: p[3] })),
    enemies: (def.enemies || []).map((e) => makeEnemy(e[0], e[1], e[2])),
    fragments: (def.fragments || []).map((f) => ({ x: f[0], y: f[1], baseY: f[1], phase: rand(0, 6), taken: false })),
    projectiles: [],
    slashes: [],
    boss: null,
    collected: 0,
    intro: def.intro,
  };
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
    enemies: [["shade", 500, 700], ["shade", 2300, 700], ["wisp", 1600, 460], ["shade", 3350, 660], ["wisp", 2650, 460]],
    fragments: [[1180, 640], [1560, 560], [1980, 640], [2560, 540], [3010, 580]],
  },
  {
    theme: "ruins", name: "Ruínas de Cristal", chapter: "Capítulo II",
    width: 4000, height: 1000, spawn: { x: 70, y: 500 },
    intro: "Nas <b>Ruínas de Cristal</b>, os ecos dos antigos guardiões sussurram. Espectros protegem os fragmentos — mas a luz de Aurora cresce.",
    goal: { x: 3860, y: 480 },
    platforms: [
      [0, 660, 620, 340], [760, 600, 220, 30], [1080, 500, 200, 30],
      [1380, 420, 200, 30], [1680, 540, 240, 30], [2000, 660, 500, 340],
      [2200, 460, 180, 30], [2640, 560, 220, 30], [2960, 460, 200, 30],
      [3260, 560, 220, 30], [3560, 640, 440, 360],
    ],
    enemies: [["wisp", 900, 400], ["shade", 300, 600], ["wisp", 1500, 300], ["shade", 2150, 600], ["wisp", 2800, 380], ["wisp", 3100, 320], ["shade", 3700, 580]],
    fragments: [[860, 540], [1180, 440], [1480, 360], [2280, 400], [2720, 500], [3060, 400], [3360, 500]],
  },
  {
    theme: "citadel", name: "Cidadela das Sombras", chapter: "Capítulo III — O Confronto Final",
    width: 1600, height: 900, spawn: { x: 120, y: 600 }, isBoss: true,
    intro: "No trono da <b>Cidadela das Sombras</b>, <b>NÖX</b> aguarda — o Devorador de Luz que roubou o Coração. Chegou a hora de reacender o mundo.",
    platforms: [
      [0, 780, 1600, 120], [120, 620, 220, 30], [1260, 620, 220, 30], [640, 520, 320, 30],
    ],
    enemies: [],
    fragments: [],
  },
];

function makeBoss(x, y) {
  return {
    boss: true, x, y, w: 120, h: 170, vx: 0, vy: 0, facing: -1,
    hp: 620, maxHp: 620, hurtTimer: 0, phaseT: 0, onGround: false, dead: false,
    state: "idle", stateT: 1.4, shots: 0, shotTimer: 0, attacking: false, slammed: false, summoned: false,
  };
}

/* =========================================================
   HUD & retrato
   ========================================================= */
const hpFill = document.getElementById("hp-fill");
const energyFill = document.getElementById("energy-fill");
const fragCount = document.getElementById("frag-count");
const fragTotal = document.getElementById("frag-total");
const stageName = document.getElementById("stage-name");
const bossBar = document.getElementById("boss-bar");
const bossFill = document.getElementById("boss-fill");
const hud = document.getElementById("hud");

function updateHUD(level) {
  hpFill.style.width = (player.hp / player.maxHp * 100) + "%";
  energyFill.style.width = (player.energy / player.maxEnergy * 100) + "%";
  fragCount.textContent = level.collected;
  fragTotal.textContent = level.fragments.length;
  stageName.textContent = level.name;
}

// Retrato animado da Aurora no HUD
const pcanvas = document.getElementById("portrait");
const pctx = pcanvas.getContext("2d");
function drawPortrait() {
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
      Guie <b>Aurora</b>, a última guardiã, por três reinos corrompidos, recupere os fragmentos e reacenda o mundo.</p>
      <div class="btn-row">
        <button class="btn" id="btn-start">▶ Começar Jornada</button>
      </div>
      <div class="controls-hint">
        Mover <kbd>←</kbd><kbd>→</kbd> ou <kbd>A</kbd><kbd>D</kbd> &nbsp;•&nbsp; Pular (duplo) <kbd>Espaço</kbd> / <kbd>W</kbd><br>
        Atacar <kbd>K</kbd> / <kbd>X</kbd> &nbsp;•&nbsp; Avanço (dash) <kbd>L</kbd> / <kbd>Shift</kbd> &nbsp;•&nbsp; Pausar <kbd>P</kbd>
      </div>
    </div>`);
  document.getElementById("btn-start").onclick = () => startLevel(0);
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
      <div class="btn-row"><button class="btn" id="btn-go">${def.isBoss ? "⚔ Enfrentar Nöx" : "Entrar na Fase"}</button></div>
    </div>`);
  document.getElementById("btn-go").onclick = () => beginPlay(idx);
}

function startLevel(idx) { storyScreen(idx); }

function beginPlay(idx) {
  levelIndex = idx;
  level = buildLevel(LEVELS[idx]);
  initMotes();
  player.reset(level.spawn.x, level.spawn.y);
  camera.x = level.spawn.x - VW / 2; camera.y = 0;
  goalReached = false;
  particles.length = 0;
  if (level.isBoss) {
    level.boss = makeBoss(level.width / 2 - 60, 560);
    bossIntroT = 1.6;
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
        <p class="story">Aurora caiu... mas a esperança não. Levante-se, guardiã.</p>
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
  const collected = level.collected, total = level.fragments.length;
  showOverlay(`
    <div class="card">
      <div class="chapter">Fase Concluída</div>
      <div class="title" style="font-size:clamp(28px,6vw,48px)">${level.name}</div>
      <p class="story">Fragmentos de luz: <b>${collected} / ${total}</b> ✦<br>A escuridão recua. Aurora segue adiante.</p>
      <div class="btn-row"><button class="btn" id="btn-next">Próxima Fase →</button></div>
    </div>`);
  document.getElementById("btn-next").onclick = () => startLevel(levelIndex + 1);
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
    // plataformas
    for (const pl of level.platforms) drawPlatform(pl, level.theme);
    // objetivo
    drawGoal(level);
    // fragmentos
    for (const c of level.fragments) drawFragment(c);
    // inimigos
    for (const en of level.enemies) {
      if (en.dead) continue;
      const sx = en.x - camera.sx, sy = en.y - camera.sy;
      if (sx < -100 || sx > VW + 100) continue;
      if (en.type === "shade") drawShade(en, sx, sy);
      else if (en.type === "wisp") drawWisp(en, sx, sy);
    }
    // boss
    if (level.boss && !level.boss.dead) drawNox(level.boss, level.boss.x - camera.sx, level.boss.y - camera.sy);
    // slashes
    drawSlashes(level, dt);
    // projéteis
    drawProjectiles(level);
    // jogador
    if (!player.dead) {
      const blink = player.invuln > 0 && Math.floor(player.invuln * 12) % 2 === 0;
      if (!blink) drawAurora(player, player.x - camera.sx, player.y - camera.sy);
    }
    // partículas
    drawParticles();

    // vinheta
    const vg = ctx.createRadialGradient(VW / 2, VH / 2, VH * 0.4, VW / 2, VH / 2, VH * 0.85);
    vg.addColorStop(0, "transparent"); vg.addColorStop(1, "rgba(0,0,0,0.55)");
    ctx.fillStyle = vg; ctx.fillRect(0, 0, VW, VH);

    // intro do boss
    if (level.isBoss && bossIntroT > 0) {
      ctx.globalAlpha = clamp(bossIntroT, 0, 1);
      ctx.fillStyle = "rgba(0,0,0,0.5)"; ctx.fillRect(0, 0, VW, VH);
      ctx.globalAlpha = 1;
      ctx.textAlign = "center";
      ctx.font = "800 64px Cinzel, serif";
      ctx.fillStyle = "#ff5c93"; ctx.shadowColor = "#ff2e63"; ctx.shadowBlur = 24;
      ctx.fillText("NÖX", VW / 2, VH / 2 - 10);
      ctx.font = "400 22px Poppins, sans-serif"; ctx.shadowBlur = 0; ctx.fillStyle = "#ffd0e0";
      ctx.fillText("O Devorador de Luz", VW / 2, VH / 2 + 28);
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

  if (consume("p")) togglePause();

  if (state === "playing" && !paused && level) {
    // update
    playerUpdate(dt, level);
    for (const en of level.enemies) enemyUpdate(en, dt, level);
    if (level.boss) {
      if (bossIntroT > 0) bossIntroT -= dt;
      else bossUpdate(level.boss, dt, level);
      bossFill.style.width = clamp(level.boss.hp / level.boss.maxHp * 100, 0, 100) + "%";
      if (level.boss.dead && state === "playing") {
        state = "victory"; victoryT = 2.2;
        camera.shake = 20;
        burst(level.boss.x + level.boss.w / 2, level.boss.y + level.boss.h / 2, "#ffe08a", 80, { glow: true, max: 10, maxLife: 1.8 });
      }
    }
    updateProjectiles(dt, level);
    updateCollectibles(dt, level);
    updateParticles(dt);
    camera.follow(player, level);
    updateHUD(level);

    // limpar inimigos mortos ocasionalmente
    // (mantidos no array; render/AI já ignoram dead)

    // chegar ao objetivo
    if (level.goal && !goalReached && !level.isBoss) {
      const g = { x: level.goal.x - 40, y: level.goal.y - 60, w: 80, h: 120 };
      if (aabb(g, player.hitbox)) {
        goalReached = true;
        if (levelIndex >= LEVELS.length - 1) victoryScreen(); else levelClear();
      }
    }
  } else if (state === "victory" && victoryT > 0) {
    victoryT -= dt;
    updateParticles(dt);
    if (level) { camera.follow(player, level); }
    if (victoryT <= 0) victoryScreen();
  }

  // sempre desenhar cena de fundo do jogo se existir
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  render(t, dt);
  drawPortrait();

  requestAnimationFrame(loop);
}

/* ---------- Início ---------- */
menuScreen();
requestAnimationFrame(loop);
