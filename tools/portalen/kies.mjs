// Gebruik: node kies.mjs <map met ai-portals> '<JSON-lijst met wereld-id's, of []>'
// Schrijft de opnamelijst als JSON: elke wereld met de wereld erna (door het portaal) en de titel.
// Een lege lijst betekent: de openingswerelden die het project zelf als beste eerste indruk koos.
import fs from 'node:fs';
import path from 'node:path';

const [root, wanted = '[]'] = process.argv.slice(2);
const source = fs.readFileSync(path.join(root, 'src/worlds/index.js'), 'utf8')
  .replace(/load: \(\) => import\([^)]*\)/g, 'load: 0').replace(/export const/g, 'const');
const WORLDS = new Function(source + '; return WORLDS;')();
const byId = new Map(WORLDS.map((w) => [w.id, w]));
let ids = JSON.parse(wanted);
if (!ids.length) ids = WORLDS.filter((w) => w.opener).map((w) => w.id);
const missing = ids.filter((id) => !byId.has(id));
if (missing.length) { console.error('onbekende werelden: ' + missing.join(', ')); process.exit(1); }
// Achter het portaal een andere mooie wereld: de volgende van de lijst, of bij één wereld een van de openers.
const openers = WORLDS.filter((w) => w.opener).map((w) => w.id);
const list = ids.map((id, i) => {
  const next = ids.length > 1 ? ids[(i + 1) % ids.length] : openers.find((o) => o !== id);
  return { wereld: id, volgende: next, titel: byId.get(id).name };
});
console.log(JSON.stringify(list));
