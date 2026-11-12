// Neemt één clip op uit Portals (https://github.com/emollick/ai-portals, CC BY-NC-SA 4.0): de vlucht door een
// wereld en door het portaal de volgende in. De site tekent live in de browser en wil een grafische kaart; hier
// draait hij zonder, dus traag. Daarom een virtuele klok: elk beeld is precies 1/fps seconde, hoe lang het
// tekenen ook duurt, en het resultaat is een vloeiende video.
//
// Gebruik: node record.mjs <map met ai-portals> <wereld> <volgende wereld> <uit.mp4> [seconden] [fps] [breedte] [hoogte]
// Omgeving: PLAYWRIGHT (pad naar playwright/index.mjs), MAX_UREN (bij te traag tekenen stapt hij terug naar 720p).
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { spawn } from 'node:child_process';

const [root, world, next, out, secondsArg = '60', fpsArg = '24', wArg = '1920', hArg = '1080'] = process.argv.slice(2);
if (!out) { console.error('gebruik: node record.mjs <ai-portals> <wereld> <volgende> <uit.mp4> [seconden] [fps] [breedte] [hoogte]'); process.exit(2); }
const seconds = Number(secondsArg), fps = Number(fpsArg);
let width = Number(wArg), height = Number(hArg);
const maxHours = Number(process.env.MAX_UREN || 0);
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

const total = Math.round(seconds * fps);
// Van mooi naar snel: met kantgladmaken, zonder, en dan in 720p. De eerste die binnen de tijd past, wint.
const profiles = [[width, height, 2], [width, height, 0], [1280, 720, 2]].filter(([w], i) => i === 0 || maxHours > 0 && (w < width || i === 1));
let browser, page, first = [], startedAt = Date.now(), aa = 2;
for (const [i, [w, h, samples]] of profiles.entries()) {
  ({ browser, page } = await open(w, h, samples));
  width = w; height = h; aa = samples; first = []; startedAt = Date.now();
  if (!(maxHours > 0) || i === profiles.length - 1) break;
  // De eerste drie beelden tellen niet mee: daarin worden de shaders nog gebouwd.
  let timed = 0;
  for (let k = 0; k < 8; k++) { if (k === 3) timed = Date.now(); first.push(Buffer.from(await frame(page), 'base64')); }
  const hours = ((Date.now() - timed) / 5) * total / 3.6e6;
  console.log(`schatting op ${w}x${h}, aa ${samples}: ${hours.toFixed(1)} uur`);
  if (hours <= maxHours) break;
  await browser.close();
}

const fadeAt = Math.max(0, seconds - 1);
const ffmpeg = spawn('ffmpeg', ['-y', '-loglevel', 'error', '-f', 'image2pipe', '-framerate', String(fps), '-c:v', 'mjpeg', '-i', '-',
  // H.264 High in 8 bit met gematigde bitrate: wat een Chromecast zonder haperen afspeelt en streamt.
  '-vf', `fade=t=out:st=${fadeAt}:d=1,format=yuv420p`, '-c:v', 'libx264', '-profile:v', 'high', '-level', '4.1', '-preset', 'slow',
  '-crf', '20', '-maxrate', '9M', '-bufsize', '18M', '-g', String(fps * 2), '-movflags', '+faststart', '-an', out], { stdio: ['pipe', 'inherit', 'inherit'] });
const write = async (jpeg) => { if (!ffmpeg.stdin.write(jpeg)) await new Promise((r) => ffmpeg.stdin.once('drain', r)); };

for (let i = 0; i < total; i++) {
  await write(i < first.length ? first[i] : Buffer.from(await frame(page), 'base64'));
  if ((i + 1) % (fps * 5) === 0) console.log(`${i + 1}/${total} beelden, ${((Date.now() - startedAt) / 60000).toFixed(0)} min`);
}
const info = await page.evaluate(() => { const e = window.__engine; return { now: e.cur.def.id, name: e.cur.def.name }; });
ffmpeg.stdin.end();
const code = await new Promise((r) => ffmpeg.on('close', r));
await browser.close(); server.close();
if (code !== 0) { console.error('ffmpeg gaf fout', code); process.exit(1); }
console.log(JSON.stringify({ world, next, eindigtIn: info.now, width, height, aa, fps, seconds, bytes: fs.statSync(out).size }));
