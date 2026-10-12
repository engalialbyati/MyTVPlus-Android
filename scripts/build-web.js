'use strict';
/* Assembles mobile/www from the desktop renderer + Android bridge shim. */
const fs = require('node:fs');
const path = require('node:path');

const ROOT = path.join(__dirname, '..');
const SRC = path.join(ROOT, '..', 'desktop-client', 'renderer');
const WWW = path.join(ROOT, 'www');

fs.rmSync(WWW, { recursive: true, force: true });
fs.mkdirSync(path.join(WWW, 'vendor'), { recursive: true });
fs.mkdirSync(path.join(WWW, 'css'), { recursive: true });

// copy renderer
fs.copyFileSync(path.join(SRC, 'app.js'), path.join(mkdirp(path.join(WWW, 'js')), 'app.js'));
fs.copyFileSync(path.join(SRC, 'styles.css'), path.join(WWW, 'css', 'styles.css'));

// index.html: viewport + shim/crypto scripts
let html = fs.readFileSync(path.join(SRC, 'index.html'), 'utf8');
html = html.replace('<meta charset="UTF-8">',
  '<meta charset="UTF-8">\n<meta name="viewport" content="width=device-width, initial-scale=1, user-scalable=no">');
html = html.replace('<link rel="stylesheet" href="styles.css">', '<link rel="stylesheet" href="css/styles.css">');
html = html.replace('<script src="vendor/hls.min.js"></script>',
  '<script src="vendor/crypto-js.min.js"></script>\n' +
  '<script src="vendor/capacitor.js"></script>\n' +
  '<script src="vendor/hls.min.js"></script>\n' +
  '<script src="js/native-shim.js"></script>');
html = html.replace('<script src="app.js"></script>', '<script src="js/app.js"></script>');
fs.writeFileSync(path.join(WWW, 'index.html'), html);

// vendors
copy(path.join(ROOT, '..', 'desktop-client', 'node_modules', 'hls.js', 'dist', 'hls.min.js'), path.join(WWW, 'vendor', 'hls.min.js'));
copy(path.join(ROOT, 'node_modules', 'crypto-js', 'crypto-js.js'), path.join(WWW, 'vendor', 'crypto-js.min.js'));
copy(path.join(ROOT, 'node_modules', '@capacitor', 'core', 'dist', 'capacitor.js'), path.join(WWW, 'vendor', 'capacitor.js'));

// shim
copy(path.join(ROOT, 'www-shim', 'native-shim.js'), path.join(WWW, 'js', 'native-shim.js'));

function mkdirp(d) { fs.mkdirSync(d, { recursive: true }); return d; }
function copy(a, b) { fs.copyFileSync(a, b); console.log('copied', path.relative(ROOT, b)); }
console.log('www/ built');
