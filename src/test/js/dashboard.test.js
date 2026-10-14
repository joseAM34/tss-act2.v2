'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const dashboard = require('../../main/resources/static/js/dashboard.js');
test('mapa asimétrico conserva columna/fila y diferencia cero de ausencia', () => {
  assert.deepEqual(dashboard.heatmapCoordinates([[18, 12], [6, 24]]).available, [[0, 0, 18], [1, 0, 12], [0, 1, 6], [1, 1, 24]]);
  assert.deepEqual(dashboard.heatmapCoordinates([[0, null]]), { available: [[0, 0, 0]], missing: [[1, 0]] });
});
test('cambio de modo usa porcentajes y tabla enviados, sin recalcular', () => {
  const countTable = { columns: ['Categoría', 'Conteo'], rows: [['A', 3]] };
  const percentTable = { columns: ['Categoría', '% participantes'], rows: [['A', 75]] };
  const chart = { type: 'bar', available: true, title: 'Selección múltiple', categories: ['A', 'B', 'C'], series: [{ name: 'Respuestas', values: [3, 2, 2] }], modes: [
    { id: 'count', label: 'Conteos', unit: 'Envíos', values: [3, 2, 2], table: countTable },
    { id: 'percent', label: '% participantes', unit: '%', max: 100, values: [75, 50, 50], table: percentTable }
  ] };
  assert.deepEqual(dashboard.chartOption(chart, 'percent', true).series[0].data.map(item => item.value), [75, 50, 50]);
  assert.equal(dashboard.chartOption(chart, 'percent', true).xAxis.max, 100);
  assert.equal(dashboard.tableFor(chart, 'percent'), percentTable);
  assert.equal(dashboard.tableFor(chart, 'count'), countTable);
});
test('radar incompleto no dibuja y acuerdo cero con base positiva sí', () => {
  const chart = { type: 'radar', available: true, title: 'Criterios', axes: ['A', 'B', 'C', 'D', 'E'].map(name => ({ name })), series: [{ name: 'Grupo', values: [0, 0, 0, 0, 0], denominators: [1, 2, 3, 4, 5] }] };
  assert.equal(dashboard.validRadar(chart), true);
  assert.deepEqual(dashboard.chartOption(chart, null, true).radar.indicator.map(axis => [axis.min, axis.max]), [[0, 100], [0, 100], [0, 100], [0, 100], [0, 100]]);
  chart.series[0].values[2] = null;
  assert.equal(dashboard.chartOption(chart, null, true), null);
  chart.series[0].values[2] = 0; chart.series[0].denominators[2] = 0;
  assert.equal(dashboard.validRadar(chart), false);
});
test('suspensión inferencial no impide representar mapa descriptivo disponible', () => {
  const chart = { type: 'heatmap', available: true, status: 'FRECUENCIAS_INSUFICIENTES', title: 'Cruce', rows: ['A', 'B'], columns: ['C', 'D'], modes: [{ id: 'O', label: 'Observadas', values: [[1, 0], [2, 3]], unit: 'Envíos', max: 3 }] };
  assert.ok(dashboard.chartOption(chart, 'O', true));
  chart.available = false;
  assert.equal(dashboard.chartOption(chart, 'O', true), null);
});
test('una respuesta obsoleta no puede sobrescribir una consulta nueva', () => {
  const gate = dashboard.requestGate(), first = gate.next(), second = gate.next();
  assert.equal(gate.current(first), false); assert.equal(gate.current(second), true);
});
test('caja conserva exactamente cinco números del backend y movimiento reducido', () => {
  const chart = { type: 'boxplot', available: true, title: 'Duración', categories: ['Meses'], series: [{ name: 'Resumen', values: [[2, 4, 4.5, 5.5, 9]] }] };
  const option = dashboard.chartOption(chart, null, true);
  assert.deepEqual(option.series[0].data, [[2, 4, 4.5, 5.5, 9]]); assert.equal(option.animation, false);
});
test('tooltip permanece en texto richText, valores nulos y cero distintos', () => {
  const chart = { type: 'bar', available: true, title: '<img src=x onerror=alert(1)>', categories: ['A'], series: [{ name: 'Respuestas', values: [0] }] };
  const option = dashboard.chartOption(chart, null, true);
  assert.equal(option.tooltip.renderMode, 'richText'); assert.equal(option.aria.enabled, true); assert.equal(option.aria.decal.show, true);
  assert.equal(option.series[0].data[0].value, 0); assert.equal(dashboard.format(0), '0'); assert.equal(dashboard.format(null), '—');
  assert.ok(option.tooltip.formatter({ name: 'A', seriesName: 'Respuestas', value: 0 }).includes('<img src=x onerror=alert(1)>'));
});
test('p redondeada se limita a presentación y no calcula una decisión', () => {
  assert.equal(dashboard.format(true, 'reject_h0_adjusted'), 'Rechazo de H0');
  assert.equal(dashboard.format(false, 'reject_h0_adjusted'), 'No rechazo de H0');
  assert.equal(dashboard.format(null, 'reject_h0_adjusted'), '—');
  assert.equal(dashboard.format(0.0496, 'p'), '0,0496');
  assert.equal(dashboard.format(0.0496, 'p original'), '0,0496');
  assert.equal(dashboard.format(0.0003, 'p ajustada'), '< 0,001');
});
test('selección ordenada y colores estables conservan contextos entre filtros', () => {
  assert.equal(dashboard.queryString({ survey: 'titulados', year: '2020', version: '' }), dashboard.queryString({ year: '2020', survey: 'titulados' }));
  assert.equal(dashboard.stableColor('No sabe'), '#68756d'); assert.equal(dashboard.stableColor('Acuerdo'), dashboard.stableColor('Acuerdo'));
});
