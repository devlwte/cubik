// Prueba de la calculadora de index.html: node test/calc.test.js
const fs = require('fs'), vm = require('vm'), assert = require('assert');
const html = fs.readFileSync(__dirname + '/../index.html', 'utf8');
const js = html.slice(html.lastIndexOf('<script>') + 8, html.lastIndexOf('</script>')).replace(/\ninit\(\);\s*$/, '');
const ctx = { console, localStorage: { getItem: () => null, setItem() {} }, crypto: {} };
vm.createContext(ctx);
vm.runInContext(js + '\nthis.T = { calculate, options, evalMath, fmtNum, press, pressMath, fromV1, splitExpr, numberName };', ctx);
const T = ctx.T;
const groups = l => { const g = []; l.rows.forEach(n => { const x = g.find(x => x.n === n); x ? x.c++ : g.push({ n, c: 1 }); }); return g.map(x => `${x.c}x${x.n}`).join(' '); };

// 350÷12×5 → A: 2 filas de 30 y 10 de 29; B: pisos completos; C: otra cantidad de filas.
const r = T.calculate('350÷12×5', 50, null);
assert(r.ok);
const o = T.options(r.load);
assert.strictEqual(groups(o[0].load), '2x30 10x29');
assert.strictEqual(groups(o[1].load), '10x30 2x25');
assert.strictEqual(o.length, 2);
o.forEach(x => assert.strictEqual(x.load.rows.reduce((a, b) => a + b), 350));
// Las opciones nunca cambian las filas: 260÷9 → A parejo 29 y 28, B un solo remate, C pisos completos.
const o9 = T.options(T.calculate('260÷9', 50, null).load);
assert.strictEqual(o9.map(x => groups(x.load)).join(' | '), '8x29 1x28 | 4x30 5x28 | 7x30 2x25');
o9.forEach(x => { assert.strictEqual(x.load.rows.length, 9); assert.strictEqual(x.load.rows.reduce((a, b) => a + b), 260); });
// Sin ×5 usa ancho 5; productos después de la coma, lo que sobra es RESTO.
assert.strictEqual(T.calculate('360÷12', 50, null).load.W, 5);
const p = T.calculate('360÷12,200', 50, null);
assert.strictEqual(JSON.stringify(p.products.map(x => [x.n, !!x.rest])), "[[200,false],[160,true]]");
assert(T.calculate('360÷12×5,200,100', 50, null).ok);
// ",2" = dividir en 2 productos iguales.
assert.strictEqual(JSON.stringify(T.calculate('260÷9,2', 50, null).products.map(x => [x.n, !!x.rest])), '[[130,false],[130,false]]');
assert.strictEqual(T.calculate('100÷9,3', 50, null).products.map(x => x.n).join(), '34,33,33');
assert.strictEqual(T.numberName('260÷9,2', 2), 'cuántos productos');
assert(T.calculate('360,200', 50, null).ok);
assert(!T.calculate('360÷12,400', 50, null).ok);
// Teclas en modo bultos.
let s = ''; for (const k of ['3', '5', '0', '÷', '1', '2', '×', '5', ',', '2', '0', '0']) s = T.press(s, k);
assert.strictEqual(s, '350÷12×5,200');
assert.strictEqual(T.press('350÷12', '÷'), '350÷12');
assert.strictEqual(T.press('350,2', '×'), '350,2');
assert.strictEqual(T.numberName('350÷12×5,200', 2), 'el ancho');
assert.strictEqual(T.numberName('350÷12×5,200', 3), 'el producto 1');
// Historial viejo al formato nuevo.
assert.strictEqual(T.fromV1('300,8,5÷130,100'), '300÷8×5,130,100');
assert.strictEqual(T.fromV1('300÷130'), '300,130');
assert.strictEqual(T.fromV1('200+100,9'), '200+100÷9');
// Calculadora normal.
assert.strictEqual(T.fmtNum(T.evalMath('2+3×4')), '14');
assert.strictEqual(T.fmtNum(T.evalMath('200+10%')), '220');
assert.strictEqual(T.fmtNum(T.evalMath('50%')), '0.5');
assert.strictEqual(T.fmtNum(T.evalMath('0.1+0.2')), '0.3');
assert.strictEqual(T.fmtNum(T.evalMath('−5×2')), '−10');
assert.strictEqual(T.evalMath('5÷0'), null);
assert.strictEqual(T.pressMath('5+', '×'), '5×');
assert.strictEqual(T.pressMath('', '.'), '0.');
console.log('ok');
