/**
 * "Sevilla se apaga": canvas port of the night skyline of Seville whose lights flicker and switch
 * off. Static layers are pre-rendered once into offscreen canvases; only lights, stars, water and
 * the final dimming are redrawn per frame. The seeded rng keeps the result identical between runs.
 */
export const SCENE_WIDTH = 1920;
export const SCENE_HEIGHT = 1080;
/** Time (seconds) at which every light is off and the scene rests in moonlight. */
export const SCENE_END = 4.8;

const W = SCENE_WIDTH;
const H = SCENE_HEIGHT;
const WY = 760;
const END = SCENE_END;
/** Faint moonlight left on the monuments once their lights are off, so their shapes stay readable. */
const MOONLIGHT = 0.16;

interface Light {
  tOn: number;
  tOff: number;
  seed: number;
  flick: number;
  speed: number;
  drop: number;
}
interface LightOptions {
  flick?: number;
  speed?: number;
  drop?: number;
}
interface Layer {
  c: HTMLCanvasElement;
  x: number;
  y: number;
}
interface Win {
  x: number;
  y: number;
  w: number;
  h: number;
  c: string;
  b: number;
  l: Light;
}
interface Lamp {
  x: number;
  y: number;
  l: Light;
  v: number;
}
interface Star {
  x: number;
  y: number;
  r: number;
  b: number;
  sp: number;
  seed: number;
}
type Ctx = CanvasRenderingContext2D;
type Stop = readonly [number, string];

export interface SevilleNightScene {
  /** Draws the frame at time `t` (seconds, clamped to the end of the animation). */
  render(t: number): void;
}

const hash = (n: number): number => {
  const x = Math.sin(n * 127.1 + 311.7) * 43758.5453;
  return x - Math.floor(x);
};
const noise = (x: number): number => {
  const i = Math.floor(x);
  const f = x - i;
  const u = f * f * (3 - 2 * f);
  return hash(i) * (1 - u) + hash(i + 1) * u;
};

function level(l: Light, t: number): number {
  if (t < l.tOn || t >= l.tOff) return 0;
  let v = 1 - l.flick * noise(t * l.speed + l.seed);
  if (hash(Math.floor(t * 10) + l.seed * 7.3) < l.drop) v *= 0.22; // brief twinkle dip
  const d = t - l.tOn;
  if (d < 0.8) v *= hash(Math.floor(d * 28) + l.seed) < d / 0.8 + 0.05 ? 0.55 + (0.45 * d) / 0.8 : 0.04; // sputter on
  const e = l.tOff - t;
  if (e < 0.7) v *= hash(Math.floor(t * 24) + l.seed * 3.1) < e / 0.7 ? 1 : 0.03; // sputter off
  return v;
}

const newCanvas = (w: number, h: number): HTMLCanvasElement => {
  const c = document.createElement('canvas');
  c.width = w;
  c.height = h;
  return c;
};
const get2d = (c: HTMLCanvasElement): Ctx => c.getContext('2d') as Ctx;

function layer(x: number, y: number, w: number, h: number, fn: (g: Ctx) => void): Layer {
  const c = newCanvas(w, h);
  const g = get2d(c);
  g.translate(-x, -y);
  fn(g);
  return { c, x, y };
}
const vgrad = (g: Ctx, y0: number, y1: number, stops: readonly Stop[]): CanvasGradient => {
  const gr = g.createLinearGradient(0, y0, 0, y1);
  stops.forEach((s) => gr.addColorStop(s[0], s[1]));
  return gr;
};
function arch(g: Ctx, x: number, y: number, w: number, h: number): void {
  g.beginPath();
  g.moveTo(x, y + h);
  g.lineTo(x, y + w / 2);
  g.arc(x + w / 2, y + w / 2, w / 2, Math.PI, 0);
  g.lineTo(x + w, y + h);
  g.closePath();
  g.fill();
}
function pointed(g: Ctx, x: number, y: number, w: number, h: number): void {
  g.beginPath();
  g.moveTo(x, y + h);
  g.lineTo(x, y + w * 0.8);
  g.quadraticCurveTo(x, y, x + w / 2, y);
  g.quadraticCurveTo(x + w, y, x + w, y + w * 0.8);
  g.lineTo(x + w, y + h);
  g.closePath();
  g.fill();
}
function tri(g: Ctx, ax: number, ay: number, bx: number, by: number, cx: number, cy: number): void {
  g.beginPath();
  g.moveTo(ax, ay);
  g.lineTo(bx, by);
  g.lineTo(cx, cy);
  g.closePath();
  g.fill();
}
function poly(g: Ctx, pts: readonly (readonly [number, number])[]): void {
  g.beginPath();
  pts.forEach((p, i) => (i ? g.lineTo(p[0], p[1]) : g.moveTo(p[0], p[1])));
  g.closePath();
  g.fill();
}
function circle(g: Ctx, x: number, y: number, r: number): void {
  g.beginPath();
  g.arc(x, y, r, 0, Math.PI * 2);
  g.fill();
}

const WIN_COLORS = ['#ffd58a', '#ffc56b', '#ffb45c', '#ffe3a8', '#ffcf7a', '#bcd4ff'];

function cathedral(g: Ctx, lit: boolean): void {
  const stone = lit ? vgrad(g, 490, 720, [[0, '#8a6448'], [0.5, '#c38c5f'], [1, '#e6ab6f']]) : '#12121b';
  const shade = lit ? 'rgba(70,35,18,.42)' : 'rgba(0,0,0,.35)';
  const win = lit ? '#3a2015' : '#08080d';
  g.fillStyle = stone;
  g.fillRect(630, 615, 480, 105);
  g.fillRect(690, 560, 370, 56);
  g.fillRect(858, 518, 74, 43);
  tri(g, 856, 519, 934, 519, 895, 490);
  for (const px of [862, 928]) {
    g.fillRect(px - 3, 505, 6, 14);
    tri(g, px - 3, 505, px + 3, 505, px, 490);
  }
  for (let x = 636; x <= 1106; x += 34) {
    g.fillRect(x - 4, 598, 8, 18);
    tri(g, x - 4, 598, x + 4, 598, x, 576);
  }
  for (let x = 696; x <= 1056; x += 37) {
    g.fillRect(x - 3, 546, 6, 15);
    tri(g, x - 3, 546, x + 3, 546, x, 529);
  }
  g.fillStyle = shade;
  for (let x = 636; x <= 1106; x += 34) g.fillRect(x - 4, 616, 8, 104);
  for (let x = 696; x <= 1056; x += 37) g.fillRect(x - 3, 561, 6, 54);
  g.fillRect(630, 712, 480, 8);
  g.fillStyle = win;
  for (let x = 636; x < 1100; x += 34) pointed(g, x + 11, 640, 12, 52);
  for (let x = 696; x < 1050; x += 37) pointed(g, x + 14, 570, 9, 32);
  circle(g, 895, 538, 9);
  if (lit) {
    g.fillStyle = 'rgba(255,200,140,.18)';
    circle(g, 895, 538, 6);
  }
}

function giralda(g: Ctx, lit: boolean): void {
  const cx = 585;
  const C = lit
    ? {
        shaft: vgrad(g, 395, 720, [[0, '#c27a51'], [0.55, '#dd9361'], [1, '#f3b47c']]),
        side: '#8a4f33',
        line: 'rgba(105,45,22,.55)',
        open: '#ffd690',
        top: '#efc28e',
        topSide: '#9b6a46',
        bell: '#4a2a12',
        bronze: '#c9a062',
      }
    : {
        shaft: '#16151e',
        side: '#0b0b12',
        line: 'rgba(0,0,0,.35)',
        open: '#05060a',
        top: '#17171f',
        topSide: '#0b0b12',
        bell: '#05060a',
        bronze: '#0d0d14',
      };
  // shaft (Almohad minaret)
  g.fillStyle = C.side;
  poly(g, [[cx + 32, 400], [cx + 44, 405], [cx + 44, 720], [cx + 32, 720]]);
  g.fillStyle = C.shaft;
  g.fillRect(cx - 32, 400, 64, 320);
  g.fillStyle = C.top;
  g.fillRect(cx - 35, 394, 70, 7);
  // sebka lattice panels
  g.strokeStyle = C.line;
  g.lineWidth = 1.2;
  for (const px of [cx - 28, cx + 13]) {
    g.save();
    g.beginPath();
    g.rect(px, 438, 15, 150);
    g.clip();
    for (let k = -160; k < 170; k += 7) {
      g.beginPath();
      g.moveTo(px + k, 438);
      g.lineTo(px + k + 150, 588);
      g.stroke();
      g.beginPath();
      g.moveTo(px + k + 150, 438);
      g.lineTo(px + k, 588);
      g.stroke();
    }
    g.restore();
    g.strokeRect(px, 438, 15, 150);
  }
  g.fillStyle = C.line;
  for (const y of [448, 503, 558]) {
    arch(g, cx - 6, y, 5, 13);
    arch(g, cx + 1, y, 5, 13);
  }
  for (const y of [612, 662]) arch(g, cx - 3.5, y, 7, 16);
  g.fillRect(cx - 32, 430, 64, 2);
  // belfry (Renaissance top)
  g.fillStyle = C.topSide;
  poly(g, [[cx + 29, 338], [cx + 39, 341], [cx + 39, 394], [cx + 29, 394]]);
  g.fillStyle = C.top;
  g.fillRect(cx - 29, 336, 58, 58);
  g.fillStyle = C.open;
  arch(g, cx - 9, 344, 18, 46);
  arch(g, cx - 24, 351, 9, 37);
  arch(g, cx + 15, 351, 9, 37);
  g.fillStyle = C.bell;
  poly(g, [[cx - 6, 378], [cx - 4, 366], [cx - 2, 363], [cx + 2, 363], [cx + 4, 366], [cx + 6, 378]]);
  poly(g, [[cx - 22, 380], [cx - 21, 372], [cx - 17, 372], [cx - 16, 380]]);
  poly(g, [[cx + 17, 380], [cx + 18, 372], [cx + 22, 372], [cx + 23, 380]]);
  g.fillStyle = C.top;
  g.fillRect(cx - 33, 330, 66, 6);
  g.fillStyle = C.line;
  for (let x = cx - 32; x < cx + 33; x += 4) g.fillRect(x, 331, 1, 4);
  // clock body
  g.fillStyle = C.top;
  g.fillRect(cx - 22, 293, 44, 37);
  g.fillStyle = C.line;
  g.fillRect(cx - 19, 296, 2, 32);
  g.fillRect(cx + 17, 296, 2, 32);
  g.fillStyle = C.open;
  circle(g, cx, 311, 6);
  g.fillStyle = C.top;
  g.fillRect(cx - 19, 288, 38, 5);
  // stars body
  g.fillRect(cx - 17, 263, 34, 26);
  g.fillStyle = C.open;
  arch(g, cx - 12, 268, 6, 16);
  arch(g, cx - 3, 268, 6, 16);
  arch(g, cx + 6, 268, 6, 16);
  // azucena body + dome
  g.fillStyle = C.top;
  g.fillRect(cx - 12, 240, 24, 23);
  g.fillStyle = C.open;
  arch(g, cx - 3, 244, 6, 14);
  g.fillStyle = C.top;
  g.beginPath();
  g.ellipse(cx, 241, 12, 13, 0, Math.PI, 0);
  g.fill();
  circle(g, cx, 224, 2.6);
  g.fillRect(cx - 0.8, 196, 1.6, 30);
  // Giraldillo weathervane
  g.fillStyle = C.bronze;
  poly(g, [[cx - 4, 216], [cx - 2, 201], [cx + 2, 201], [cx + 4, 216]]);
  circle(g, cx, 197.5, 2.4);
  poly(g, [[cx + 2, 202], [cx + 15, 198], [cx + 15, 210], [cx + 2, 207]]);
  g.strokeStyle = C.bronze;
  g.lineWidth = 1.2;
  g.beginPath();
  g.moveTo(cx - 2, 203);
  g.lineTo(cx - 8, 189);
  g.stroke();
}

function palm(g: Ctx, rng: () => number, x: number, base: number, h: number, lean: number): void {
  const tx = x + lean;
  const ty = base - h;
  g.strokeStyle = '#05070d';
  g.lineCap = 'round';
  g.lineWidth = 6;
  g.beginPath();
  g.moveTo(x, base);
  g.quadraticCurveTo(x + lean * 0.15, base - h * 0.5, tx, ty);
  g.stroke();
  for (let i = 0; i < 11; i++) {
    const a = -Math.PI + (i / 10) * Math.PI + (rng() - 0.5) * 0.25;
    const len = 42 + rng() * 22;
    const ex = tx + Math.cos(a) * len;
    const ey = ty + Math.sin(a) * len * 0.45 + 26 + Math.abs(Math.cos(a)) * 10;
    g.lineWidth = 3.4;
    g.beginPath();
    g.moveTo(tx, ty);
    g.quadraticCurveTo(tx + Math.cos(a) * len * 0.55, ty - 20 + Math.sin(a) * 12, ex, ey);
    g.stroke();
  }
  g.fillStyle = '#05070d';
  circle(g, tx, ty + 2, 6);
}

function torre(g: Ctx, lit: boolean): void {
  const cx = 1380;
  const cyl = (x: number, w: number): CanvasGradient => {
    const gr = g.createLinearGradient(x, 0, x + w, 0);
    const stops: readonly Stop[] = lit
      ? [[0, '#5a3218'], [0.28, '#d9a256'], [0.45, '#f7cf82'], [0.7, '#d29548'], [1, '#4a2a14']]
      : [[0, '#0a0a10'], [0.45, '#1d1c25'], [1, '#08080d']];
    stops.forEach((s) => gr.addColorStop(s[0], s[1]));
    return gr;
  };
  const line = lit ? 'rgba(95,52,20,.5)' : 'rgba(0,0,0,.4)';
  const open = lit ? '#2a1608' : '#050508';
  const merlons = (r: number, y: number, step: number): void => {
    for (let a = -80; a <= 80; a += step) {
      const s = Math.sin((a * Math.PI) / 180);
      const c = Math.cos((a * Math.PI) / 180);
      const mw = 9 * c + 2;
      const x = cx + r * s;
      g.fillRect(x - mw / 2, y - 12, mw, 13);
      tri(g, x - mw / 2, y - 12, x + mw / 2, y - 12, x, y - 19);
    }
  };
  // attached wall stretch
  g.fillStyle = lit ? '#a8743f' : '#111019';
  g.fillRect(1262, 692, 60, 68);
  for (let x = 1265; x < 1320; x += 12) {
    g.fillRect(x, 682, 7, 11);
    tri(g, x, 682, x + 7, 682, x + 3.5, 677);
  }
  // body 1 (dodecagonal)
  g.fillStyle = cyl(cx - 70, 140);
  g.fillRect(cx - 70, 742, 140, 18);
  g.fillStyle = cyl(cx - 65, 130);
  g.fillRect(cx - 65, 580, 130, 162);
  merlons(65, 581, 16);
  g.fillStyle = line;
  for (const a of [-60, -30, 0, 30, 60]) g.fillRect(cx + 65 * Math.sin((a * Math.PI) / 180) - 0.6, 584, 1.2, 158);
  g.fillRect(cx - 65, 576, 130, 4);
  g.fillStyle = open;
  arch(g, cx - 38, 628, 8, 22);
  arch(g, cx + 30, 628, 8, 22);
  arch(g, cx - 4.5, 688, 9, 26);
  // body 2
  g.fillStyle = cyl(cx - 36, 72);
  g.fillRect(cx - 36, 522, 72, 58);
  merlons(36, 523, 26);
  g.fillStyle = line;
  g.fillRect(cx - 36, 519, 72, 3);
  for (const a of [-45, 0, 45]) g.fillRect(cx + 36 * Math.sin((a * Math.PI) / 180) - 0.5, 526, 1, 54);
  g.fillStyle = open;
  arch(g, cx - 4, 540, 8, 20);
  // body 3 + golden dome
  g.fillStyle = cyl(cx - 21, 42);
  g.fillRect(cx - 21, 482, 42, 40);
  g.fillStyle = open;
  arch(g, cx - 3, 492, 6, 15);
  const dome = g.createRadialGradient(cx - 7, 470, 2, cx, 480, 26);
  if (lit) {
    dome.addColorStop(0, '#fff2b8');
    dome.addColorStop(0.5, '#eab24c');
    dome.addColorStop(1, '#8a5a1c');
  } else {
    dome.addColorStop(0, '#24212a');
    dome.addColorStop(1, '#0e0d14');
  }
  g.fillStyle = dome;
  g.beginPath();
  g.ellipse(cx, 483, 22, 22, 0, Math.PI, 0);
  g.fill();
  g.fillStyle = lit ? '#f4d27a' : '#14131a';
  circle(g, cx, 458, 3.2);
  g.fillRect(cx - 1, 443, 2, 15);
}

const piers = [
  { x: -20, w: 30 },
  { x: 185, w: 34 },
  { x: 395, w: 34 },
  { x: 600, w: 46 },
];

function bridge(g: Ctx, lit: boolean, bridgeLamps: readonly Lamp[]): void {
  const iron = lit ? '#d39356' : '#1b1c26';
  const ironDim = lit ? '#9a6536' : '#15161e';
  g.fillStyle = lit ? vgrad(g, 640, 760, [[0, '#caa071'], [1, '#86613e']]) : '#13141c';
  for (const p of piers) {
    g.fillRect(p.x, 692, p.w, 68);
    g.fillRect(p.x - 3, 686, p.w + 6, 7);
  }
  g.fillRect(600, 640, 50, 52);
  g.fillStyle = ironDim;
  g.fillRect(0, 648, 650, 14);
  g.fillStyle = iron;
  g.fillRect(0, 633, 650, 2.5);
  g.fillRect(0, 646, 650, 2.5);
  for (let x = 0; x < 650; x += 7) g.fillRect(x, 635, 1.5, 11);
  g.strokeStyle = iron;
  for (let i = 0; i < piers.length - 1; i++) {
    const a = piers[i].x + piers[i].w;
    const b = piers[i + 1].x;
    const mid = (a + b) / 2;
    const half = (b - a) / 2;
    const ay = (x: number): number => 667 + 39 * Math.pow((x - mid) / half, 2);
    for (const [off, lw] of [[0, 7], [8, 3]]) {
      g.lineWidth = lw;
      g.beginPath();
      for (let x = a; x <= b; x += 3) {
        if (x === a) g.moveTo(x, ay(x) + off);
        else g.lineTo(x, ay(x) + off);
      }
      g.stroke();
    }
    g.lineWidth = 2.4;
    for (const dir of [1, -1]) {
      // the famous iron rings
      let x = dir > 0 ? a : b;
      for (let n = 0; n < 12; n++) {
        let r = (ay(x) - 662) / 2;
        for (let k = 0; k < 3; k++) r = Math.max(0, (ay(x + dir * r) - 4 - 662) / 2);
        if (r < 3 || (dir > 0 ? x + 2 * r > mid : x - 2 * r < mid)) break;
        g.beginPath();
        g.arc(x + dir * r, 662 + r, r - 1.2, 0, Math.PI * 2);
        g.stroke();
        x += dir * 2 * r;
      }
    }
  }
  g.fillStyle = iron;
  for (const lp of bridgeLamps) {
    g.fillRect(lp.x - 1.5, 607, 3, 27);
    g.fillRect(lp.x - 4, 597, 8, 10);
    tri(g, lp.x - 5, 597, lp.x + 5, 597, lp.x, 591);
  }
}

function halo(g: Ctx, x: number, y: number, r: number, rgb: string, a: number): void {
  if (a < 0.004) return;
  const gr = g.createRadialGradient(x, y, 0, x, y, r);
  gr.addColorStop(0, `rgba(${rgb},${a})`);
  gr.addColorStop(1, `rgba(${rgb},0)`);
  g.fillStyle = gr;
  g.fillRect(x - r, y - r, r * 2, r * 2);
}

/**
 * Builds the scene (pre-renders every static layer) and returns a `render(t)` function that draws
 * onto `ctx`, which must belong to a canvas of SCENE_WIDTH x SCENE_HEIGHT. Browser only.
 */
export function createSevilleNightScene(ctx: Ctx): SevilleNightScene {
  const rng = (() => {
    let s = 20261001;
    return () => (s = (s * 16807) % 2147483647) / 2147483647;
  })();
  const L = (tOn: number, tOff: number, o: LightOptions = {}): Light => ({
    tOn,
    tOff,
    seed: rng() * 1000,
    flick: o.flick ?? 0.14,
    speed: o.speed ?? 3,
    drop: o.drop ?? 0.012,
  });
  const pickWin = (): string => WIN_COLORS[rng() < 0.07 ? 5 : Math.floor(rng() * 5)];

  // sky
  const sky = layer(0, 0, W, WY, (g) => {
    g.fillStyle = vgrad(g, 0, WY, [[0, '#03050d'], [0.5, '#0a1226'], [0.84, '#18213f'], [1, '#272846']]);
    g.fillRect(0, 0, W, WY);
    const mx = 1640;
    const my = 165;
    const hl = g.createRadialGradient(mx, my, 10, mx, my, 190);
    hl.addColorStop(0, 'rgba(190,200,240,.18)');
    hl.addColorStop(1, 'rgba(190,200,240,0)');
    g.fillStyle = hl;
    g.fillRect(mx - 200, my - 200, 400, 400);
    const m = newCanvas(80, 80);
    const mg = get2d(m);
    mg.fillStyle = '#f1eedf';
    circle(mg, 40, 40, 24);
    mg.globalCompositeOperation = 'destination-out';
    circle(mg, 50, 34, 22);
    g.drawImage(m, mx - 40, my - 40);
  });
  const stars: Star[] = Array.from({ length: 280 }, () => ({
    x: rng() * W,
    y: Math.pow(rng(), 1.5) * WY * 0.72,
    r: rng() < 0.1 ? 2.2 : 1.3,
    b: 0.25 + rng() * 0.7,
    sp: 0.8 + rng() * 2.5,
    seed: rng() * 100,
  }));

  // far city
  const farWins: Win[] = [];
  const nearWins: Win[] = [];
  const far = layer(0, 500, W, 220, (g) => {
    let x = -10;
    while (x < W) {
      const w = 30 + rng() * 70;
      const top = 612 + rng() * 52;
      g.fillStyle = '#0d1222';
      g.fillRect(x, top, w, 720 - top);
      const r = rng();
      if (r < 0.12) {
        // belfry wall
        const bw = 14 + rng() * 10;
        const bh = 28 + rng() * 22;
        const bx = x + w / 2 - bw / 2;
        g.fillRect(bx, top - bh, bw, bh);
        tri(g, bx - 2, top - bh, bx + bw + 2, top - bh, bx + bw / 2, top - bh - 10);
        g.globalCompositeOperation = 'destination-out';
        arch(g, bx + bw / 2 - 3.5, top - bh + 6, 7, 14);
        g.globalCompositeOperation = 'source-over';
      } else if (r < 0.19) {
        // church dome
        const dr = 10 + rng() * 10;
        g.beginPath();
        g.ellipse(x + w / 2, top, dr, dr * 1.05, 0, Math.PI, 0);
        g.fill();
        g.fillRect(x + w / 2 - 1, top - dr - 9, 2, 9);
      }
      for (let yy = top + 10; yy < 708; yy += 14)
        for (let xx = x + 5; xx < x + w - 8; xx += 11)
          if (rng() < 0.26)
            farWins.push({
              x: xx,
              y: yy,
              w: 5,
              h: 7,
              c: pickWin(),
              b: 0.35 + rng() * 0.4,
              l: L(-1, 2.0 + rng() * 1.0, { flick: 0.12, speed: 2 + rng() * 3, drop: 0.02 }),
            });
      x += w + rng() * 4;
    }
  });

  const cathD = layer(620, 480, 500, 240, (g) => cathedral(g, false));
  const cathL = layer(620, 480, 500, 240, (g) => cathedral(g, true));
  const girD = layer(540, 180, 110, 545, (g) => giralda(g, false));
  const girL = layer(540, 180, 110, 545, (g) => giralda(g, true));

  // near city
  const near = layer(0, 560, W, 160, (g) => {
    const zones: readonly (readonly [number, number, number, number])[] = [
      [0, 545, 600, 680],
      [545, 1115, 690, 708],
      [1115, W + 20, 612, 690],
    ];
    for (const [a, b, tMin, tMax] of zones) {
      let x = a;
      while (x < b) {
        const w = Math.min(40 + rng() * 80, b - x + 2);
        const top = tMin + rng() * (tMax - tMin);
        g.fillStyle = '#090c17';
        g.fillRect(x, top, w, 720 - top);
        g.fillStyle = '#121729';
        g.fillRect(x, top, w, 2);
        for (let yy = top + 12; yy < 704; yy += 20)
          for (let xx = x + 7; xx < x + w - 10; xx += 16)
            if (rng() < 0.3)
              nearWins.push({
                x: xx,
                y: yy,
                w: 7,
                h: 11,
                c: pickWin(),
                b: 0.6 + rng() * 0.4,
                l: L(-1, 2.1 + rng() * 0.9, { flick: 0.1, speed: 1.5 + rng() * 3, drop: 0.018 }),
              });
        x += w;
      }
    }
  });

  // riverbank: wall, palms, lamp posts
  const bankLamps: Lamp[] = [];
  const bridgeLamps: Lamp[] = [];
  for (let x = 655; x < 1910; x += 88) {
    if (x > 1250 && x < 1490) continue;
    bankLamps.push({ x, y: 666, v: 0, l: L(-1, 2.4 + ((1910 - x) / 1255) * 0.5, { flick: 0.22, speed: 7, drop: 0.025 }) });
  }
  for (let x = 40; x < 620; x += 96)
    bridgeLamps.push({ x, y: 602, v: 0, l: L(-1, 2.6 + ((620 - x) / 620) * 0.3, { flick: 0.22, speed: 7, drop: 0.025 }) });
  const lamps = bankLamps.concat(bridgeLamps);

  const bank = layer(0, 500, W, 260, (g) => {
    g.fillStyle = '#0b0e18';
    g.fillRect(0, 720, W, 40);
    g.fillStyle = '#161a29';
    g.fillRect(0, 717, W, 4);
    g.fillStyle = 'rgba(0,0,0,.35)';
    for (let x = 0; x < W; x += 46) g.fillRect(x, 721, 1, 39);
    const palms: readonly (readonly [number, number, number])[] = [
      [690, 140, -14], [770, 118, 10], [1015, 150, 12], [1160, 132, -10],
      [1525, 158, 14], [1640, 124, -12], [1790, 146, 9], [1885, 120, -8],
    ];
    for (const [x, h, lean] of palms) palm(g, rng, x, 720, h, lean);
    g.fillStyle = '#07080e';
    for (const lp of bankLamps) {
      g.fillRect(lp.x - 2, 672, 4, 48);
      g.fillRect(lp.x - 4, 711, 8, 9);
      poly(g, [[lp.x - 5, 672], [lp.x - 3.5, 660], [lp.x + 3.5, 660], [lp.x + 5, 672]]);
      tri(g, lp.x - 5, 660, lp.x + 5, 660, lp.x, 653);
    }
  });

  const torD = layer(1250, 430, 220, 330, (g) => torre(g, false));
  const torL = layer(1250, 430, 220, 330, (g) => torre(g, true));
  const briD = layer(0, 585, 660, 175, (g) => bridge(g, false, bridgeLamps));
  const briL = layer(0, 585, 660, 175, (g) => bridge(g, true, bridgeLamps));

  // monument lights
  const LB = L(-1, 3.1, { flick: 0.08, speed: 2.4, drop: 0.008 });
  const LC = L(-1, 3.25, { flick: 0.07, speed: 2, drop: 0.006 });
  const LT = L(-1, 3.4, { flick: 0.07, speed: 2.2, drop: 0.006 });
  const LG = L(-1, 3.55, { flick: 0.06, speed: 1.8, drop: 0.005 });

  // render
  const sc = newCanvas(W, WY);
  const sg = get2d(sc);
  const blit = (g: Ctx, l: Layer): void => g.drawImage(l.c, l.x, l.y);
  const lit = (g: Ctx, l: Layer, v: number): void => {
    if (v > 0.003) {
      g.globalAlpha = Math.min(1, v);
      blit(g, l);
      g.globalAlpha = 1;
    }
  };
  const drawWins = (g: Ctx, wins: readonly Win[], t: number): void => {
    for (const w of wins) {
      const v = level(w.l, t);
      if (v < 0.02) continue;
      g.globalAlpha = v * w.b;
      g.fillStyle = w.c;
      g.fillRect(w.x, w.y, w.w, w.h);
    }
    g.globalAlpha = 1;
  };

  const streak = (t: number, x: number, v: number, width: number, rgb: string, seed: number): void => {
    if (v < 0.02) return;
    for (let k = 0; k < 30; k++) {
      const y = WY + 6 + k * 10;
      if (y > H) break;
      const n = noise(k * 1.7 - t * 3.5 + seed);
      const w = width * (0.4 + n) * (1 + k * 0.04);
      ctx.fillStyle = `rgba(${rgb},${v * 0.42 * (1 - k / 30) * n})`;
      ctx.fillRect(x - w / 2 + Math.sin(k * 0.9 + t * 2) * 4, y, w, 2.5);
    }
  };

  function render(time: number): void {
    const t = Math.min(Math.max(time, 0), END);
    const g = sg;
    g.globalCompositeOperation = 'source-over';
    g.globalAlpha = 1;
    blit(g, sky);
    g.fillStyle = '#e8ecff';
    for (const s of stars) {
      g.globalAlpha = s.b * (0.4 + 0.6 * noise(t * s.sp + s.seed));
      g.fillRect(s.x, s.y, s.r, s.r);
    }
    g.globalAlpha = 1;

    const vB = level(LB, t);
    const vC = level(LC, t);
    const vT = level(LT, t);
    const vG = level(LG, t);
    let lampSum = 0;
    for (const lp of lamps) {
      lp.v = level(lp.l, t);
      lampSum += lp.v;
    }
    const glow = (0.5 * (vB + vC + vT + vG)) / 4 + (0.5 * lampSum) / lamps.length;
    if (glow > 0.005) {
      g.fillStyle = vgrad(g, WY - 340, WY, [[0, 'rgba(255,140,60,0)'], [1, `rgba(255,135,60,${0.3 * glow})`]]);
      g.fillRect(0, WY - 340, W, 340);
    }
    blit(g, far);
    drawWins(g, farWins, t);
    blit(g, cathD);
    lit(g, cathL, Math.max(vC, MOONLIGHT));
    blit(g, girD);
    lit(g, girL, Math.max(vG, MOONLIGHT));
    blit(g, near);
    drawWins(g, nearWins, t);
    blit(g, bank);
    g.globalCompositeOperation = 'lighter';
    for (const lp of bankLamps) {
      halo(g, lp.x, 724, 72, '255,160,80', 0.3 * lp.v);
      halo(g, lp.x, 690, 46, '255,170,90', 0.12 * lp.v);
    }
    g.globalCompositeOperation = 'source-over';
    blit(g, torD);
    lit(g, torL, Math.max(vT, MOONLIGHT));
    blit(g, briD);
    lit(g, briL, Math.max(vB, MOONLIGHT));

    g.globalCompositeOperation = 'lighter';
    halo(g, 585, 480, 290, '255,150,80', 0.12 * vG);
    halo(g, 585, 362, 62, '255,205,140', 0.38 * vG);
    halo(g, 870, 630, 330, '255,150,80', 0.07 * vC);
    halo(g, 1380, 600, 250, '255,175,85', 0.15 * vT);
    halo(g, 330, 680, 320, '255,160,90', 0.06 * vB);
    for (const lp of lamps) {
      halo(g, lp.x, lp.y, 40, '255,200,130', 0.55 * lp.v);
      halo(g, lp.x, lp.y, 7, '255,244,210', 0.95 * lp.v);
    }
    g.globalCompositeOperation = 'source-over';

    // compose: city above, Guadalquivir below
    ctx.globalCompositeOperation = 'source-over';
    ctx.globalAlpha = 1;
    ctx.drawImage(sc, 0, 0);
    ctx.fillStyle = vgrad(ctx, WY, H, [[0, '#0b1020'], [1, '#02040a']]);
    ctx.fillRect(0, WY, W, H - WY);
    const depth = H - WY;
    for (let y = WY; y < H; y += 2) {
      const d = y - WY;
      const sy = WY - 2 - d * 1.04;
      if (sy < 0) break;
      const amp = 1 + d * 0.035;
      const dx = Math.sin(d * 0.11 - t * 1.8) * amp + Math.sin(d * 0.37 + t * 2.7) * amp * 0.4;
      ctx.globalAlpha = 0.58 * (1 - d / depth) + 0.1;
      ctx.drawImage(sc, 0, sy, W, 2, dx, y, W, 2);
    }
    ctx.globalAlpha = 1;
    ctx.fillStyle = vgrad(ctx, WY, H, [[0, 'rgba(3,6,14,.25)'], [1, 'rgba(2,3,8,.7)']]);
    ctx.fillRect(0, WY, W, H - WY);
    ctx.globalCompositeOperation = 'lighter';
    for (const lp of lamps) streak(t, lp.x, lp.v, 10, '255,185,100', lp.l.seed);
    streak(t, 1380, vT, 90, '255,190,95', 3);
    streak(t, 585, vG * 0.6, 40, '255,160,90', 9);
    ctx.globalCompositeOperation = 'source-over';
    // river mist at the waterline
    ctx.fillStyle = vgrad(ctx, WY - 70, WY + 40, [
      [0, 'rgba(40,44,70,0)'],
      [0.6, `rgba(70,60,70,${0.12 + 0.1 * glow})`],
      [1, 'rgba(40,44,70,0)'],
    ]);
    ctx.fillRect(0, WY - 70, W, 110);
    // vignette
    const vg = ctx.createRadialGradient(W / 2, H * 0.55, H * 0.35, W / 2, H * 0.55, H * 1.05);
    vg.addColorStop(0, 'rgba(0,0,0,0)');
    vg.addColorStop(1, 'rgba(0,0,0,.6)');
    ctx.fillStyle = vg;
    ctx.fillRect(0, 0, W, H);
    // dim to a moonlit dark at the end, keeping the silhouettes visible
    let dark = 0;
    if (t > 3.7) dark = Math.min(0.15, ((t - 3.7) / 0.8) * 0.15);
    if (dark > 0) {
      ctx.fillStyle = `rgba(1,2,5,${dark})`;
      ctx.fillRect(0, 0, W, H);
    }
  }

  return { render };
}
