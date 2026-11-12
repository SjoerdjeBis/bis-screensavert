// Neemt één clip op uit Portals (https://github.com/emollick/ai-portals, CC BY-NC-SA 4.0): de vlucht door een
// wereld, door het portaal, en door de volgende wereld tot die zijn eigen einde nadert. De site tekent live in de
// browser en wil een grafische kaart; hier draait hij zonder, dus traag. Daarom een virtuele klok: elk beeld is
// precies 1/fps seconde, hoe lang het tekenen ook duurt, en het resultaat is een vloeiende video.
//
// Gebruik: node record.mjs <map met ai-portals> <wereld> <tweede wereld> <uit.mp4> [fps] [breedte] [hoogte]
// Omgeving: PLAYWRIGHT (pad naar playwright/index.mjs); MAX_UREN (zoveel mag de opname duren: is de
// schatting hoger, dan zonder kantgladmaken, en anders in 720p; loopt hij toch uit, dan stopt hij op tijd).
import http from 'node:http';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawn } from 'node:child_process';

const [root, world, next, out, fpsArg = '24', wArg = '1920', hArg = '1080'] = process.argv.slice(2);
if (!out) { console.error('gebruik: node record.mjs <ai-portals> <wereld> <tweede wereld> <uit.mp4> [fps] [breedte] [hoogte]'); process.exit(2); }
const fps = Number(fpsArg);
let width = Number(wArg), height = Number(hArg);
const maxHours = Number(process.env.MAX_UREN || 0);
// Het portaal en de tweede wereld tekenen trager dan het begin waarop de schatting is gebaseerd (gemeten: tot 1,9 keer).
const SAFETY = 2;
// Nooit langer dan dit, ook als de tweede wereld niet ophoudt.
const MAX_SECONDS = 180;
// Zo ver voor het eind van de tweede wereld stopt de clip: zonder wereld erachter remt de vlucht daar af.
const END_METRES = 50;
const { chromium } = await import(process.env.PLAYWRIGHT || 'playwright');

// Alleen deze map, alleen op deze machine.
const TYPES = { '.html': 'text/html', '.js': 'text/javascript', '.mjs': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.jpg': 'image/jpeg', '.png': 'image/png', '.svg': 'image/svg+xml' };
const base = path.resolve(root);
const server = http.createServer((req, res) => {
  const rel = decodeURIComponent(new URL(req.url, 'http://x').pathname);
  let file = path.join(base, rel);
  if (file !== base && !file.startsWith(base + path.sep)) { res.writeHead(403); return res.end(); }
  if (fs.existsSync(file) && fs.statSync(file).isDirectory()) file = path.join(file, 'index.html');
  fs.readFile(file, (err, body) => {
    if (err) { res.writeHead(404); return res.end(); }
    res.writeHead(200, { 'Content-Type': TYPES[path.extname(file)] || 'application/octet-stream' });
    res.end(body);
  });
});
await new Promise((r) => server.listen(0, '127.0.0.1', r));
// pr=1: vaste resolutie. Anders verlaagt de site die zelf zodra hij "traag" loopt, en elk van die stappen
// geeft een zwart beeld in de opname.
const address = (aa) => `http://127.0.0.1:${server.address().port}/?world=${encodeURIComponent(world)}&next=${encodeURIComponent(next)}&seed=1&pr=1&aa=${aa}`;

async function open(w, h, aa) {
  const browser = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const page = await browser.newPage({ viewport: { width: w, height: h }, deviceScaleFactor: 1 });
  page.on('pageerror', (e) => console.log('paginafout:', String(e).slice(0, 300)));
  await page.addInitScript((fps) => {
    let now = 0, queue = [];
    performance.now = () => now;
    window.requestAnimationFrame = (cb) => { queue.push(cb); return queue.length; };
    window.__step = () => { now += 1000 / fps; const q = queue; queue = []; for (const cb of q) cb(now); };
  }, fps);
  await page.goto(address(aa), { waitUntil: 'load' });
  // De klok laten lopen zonder te tekenen of te vliegen (hold) tot de eerste wereld staat en de volgende
  // achter het portaal klaar is. Het opbouwen gaat een stukje per beeld, dus dit kost honderden stappen.
  for (let i = 0; ; i++) {
    const s = await page.evaluate(async () => {
      const e = window.__engine;
      if (e) e.opts.hold = true;
      window.__step(); await new Promise((r) => setTimeout(r, 0));
      const ready = !!(e && e.cur && e.cur.ready && e.next && e.next.ready);
      if (ready) e.opts.hold = false;
      return { error: window.__shotError || null, ready };
    });
    if (s.error) throw new Error(s.error);
    if (s.ready) break;
    if (i > 20000) throw new Error('de werelden kwamen niet klaar');
  }
  // Schermteksten van de site zelf horen niet in de clip (de canvas wordt rechtstreeks gelezen, maar voor de zekerheid).
  await page.addStyleTag({ content: '#veil,#title,#hud{display:none!important}' });
  return { browser, page };
}

const frame = (page) => page.evaluate(() => { window.__step(); return document.getElementById('view').toDataURL('image/jpeg', 0.95).split(',')[1]; });

// Hoe lang de clip ongeveer wordt: de rest van de eerste wereld plus de hele tweede.
const plannedSeconds = (p) => p.evaluate(() => { const e = window.__engine; return e.cur.duration * (1 - (e.stats.u || 0)) + e.next.duration; });

// Van mooi naar snel: met kantgladmaken, zonder, en dan in 720p. De eerste die binnen de tijd past, wint.
const profiles = [[width, height, 2], [width, height, 0], [1280, 720, 2]].filter(([w], i) => i === 0 || (maxHours > 0 && (w < width || i === 1)));
let browser, page, first = [], startedAt = Date.now(), aa = 2;
for (const [i, [w, h, samples]] of profiles.entries()) {
  ({ browser, page } = await open(w, h, samples));
  width = w; height = h; aa = samples; first = []; startedAt = Date.now();
  if (!(maxHours > 0) || i === profiles.length - 1) break;
  // De eerste drie beelden tellen niet mee: daarin worden de shaders nog gebouwd.
  let timed = 0;
  for (let k = 0; k < 8; k++) { if (k === 3) timed = Date.now(); first.push(Buffer.from(await frame(page), 'base64')); }
  const frames = (await plannedSeconds(page)) * fps;
  const hours = ((Date.now() - timed) / 5) * frames * SAFETY / 3.6e6;
  console.log(`schatting op ${w}x${h}, aa ${samples}: ${hours.toFixed(1)} uur (met marge) voor ${(frames / fps).toFixed(0)} s`);
  if (hours <= maxHours) break;
  await browser.close();
}

// Beelden eerst naar schijf: pas aan het eind is bekend hoe lang de clip is, en dus waar het uitfaden begint.
const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'portaal-'));
let count = 0, reason = 'tweede wereld aan het eind';
const save = (jpeg) => fs.writeFileSync(path.join(dir, `${String(count++).padStart(5, '0')}.jpg`), jpeg);
first.forEach(save);
for (;;) {
  save(Buffer.from(await frame(page), 'base64'));
  const s = await page.evaluate((id) => { const e = window.__engine; return { second: e.cur.def.id === id, left: e.cur.path.length - e.cur.dist }; }, next);
  if (s.second && s.left <= END_METRES) break;
  if (count >= MAX_SECONDS * fps) { reason = 'maximale lengte'; break; }
  if (maxHours > 0 && (Date.now() - startedAt) / 3.6e6 > maxHours * 1.1) { reason = 'tijd op'; break; }
  if (count % (fps * 10) === 0) console.log(`${count / fps} s opgenomen, ${((Date.now() - startedAt) / 60000).toFixed(0)} min`);
}
const info = await page.evaluate(() => ({ now: window.__engine.cur.def.id }));
await browser.close(); server.close();
console.log(`gestopt na ${(count / fps).toFixed(1)} s: ${reason}`);

const seconds = count / fps;
const ffmpeg = spawn('ffmpeg', ['-y', '-loglevel', 'error', '-framerate', String(fps), '-i', path.join(dir, '%05d.jpg'),
  // H.264 High in 8 bit met gematigde bitrate: wat een Chromecast zonder haperen afspeelt en streamt.
  '-vf', `fade=t=out:st=${Math.max(0, seconds - 1.5)}:d=1.5,format=yuv420p`, '-c:v', 'libx264', '-profile:v', 'high', '-level', '4.1', '-preset', 'slow',
  '-crf', '20', '-maxrate', '9M', '-bufsize', '18M', '-g', String(fps * 2), '-movflags', '+faststart', '-an', out], { stdio: 'inherit' });
const code = await new Promise((r) => ffmpeg.on('close', r));
fs.rmSync(dir, { recursive: true, force: true });
if (code !== 0) { console.error('ffmpeg gaf fout', code); process.exit(1); }
console.log(JSON.stringify({ world, next, eindigtIn: info.now, reason, width, height, aa, fps, seconds, bytes: fs.statSync(out).size }));
