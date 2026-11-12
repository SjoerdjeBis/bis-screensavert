// Gebruik: node kies.mjs <map met ai-portals> '<JSON-lijst met wereld-id's, of []>'
// Schrijft de opnamelijst als JSON: de werelden in paren, elk paar één clip (de eerste wereld, door het portaal,
// en de tweede helemaal). Zo komt elke wereld één keer voor. Bij een oneven aantal krijgt de laatste de eerste
// als tweede wereld. Een lege lijst betekent: de openingswerelden die het project zelf als beste eerste indruk koos.
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
if (ids.length < 2) { console.error('kies minstens twee werelden'); process.exit(1); }
const list = [];
for (let i = 0; i < ids.length; i += 2) {
  const a = ids[i], b = ids[i + 1] ?? ids[0];
  list.push({ wereld: a, volgende: b, naam: `${a}--${b}`, titel: `${byId.get(a).name} → ${byId.get(b).name}` });
}
console.log(JSON.stringify(list));
