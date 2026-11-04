'use strict';
const test = require('node:test'), assert = require('node:assert/strict');
const {createReportDraftStore, reportDraftConfiguration} = require('../../main/resources/static/js/report-draft.js');
const scope = {owner: 'admin-a', analysisId: 'cut-a', mode: 'report', templateHash: ''};
const config = () => ({analysisId: 'cut-a', title: 'Mi reporte', introduction: 'Texto pendiente', context: true, tables: true, charts: true, comment: 'Comentario', questions: [{id: 'q1', included: true, title: 'Título', comment: 'Nota', representations: ['bars'], sensitiveAcknowledged: true}], crossIds: ['c1'], crossSettings: [{id: 'c1', title: 'Comparación', comment: '', representations: ['heat']}], advanced: [{type: 'scatter', questions: ['q1','q2'], title: 'Dispersión', comment: '', included: true}]});
function memory() {
  const data = new Map();
  return {getItem: key => data.get(key) ?? null, setItem: (key, value) => data.set(key, value), removeItem: key => data.delete(key), data};
}
test('recupera textos y elecciones tras recrear el editor y solo borra por acción explícita', () => {
  const storage = memory(), store = createReportDraftStore(() => storage, scope);
  assert.equal(store.load().status, 'empty');
  assert.equal(store.save(config(), {templateName: 'Plantilla pendiente', pair: ['q1','q2'], pairComment: 'Sin añadir todavía'}).status, 'saved');
  const reopened = createReportDraftStore(() => storage, scope), restored = reopened.load();
  assert.equal(restored.status, 'restored');
  assert.deepEqual(restored.value.configuration, reportDraftConfiguration(config()));
  assert.equal(restored.value.ui.pairComment, 'Sin añadir todavía');
  assert.equal(reopened.clear(), true);
  assert.equal(createReportDraftStore(() => storage, scope).load().status, 'empty');
});
test('excluye resultados, tokens, vistas previas y confirmaciones sensibles', () => {
  const raw = config();raw.result = {contact: 'dato privado'};raw.csrf = 'token';raw.html = '<b>respuesta</b>';raw.expectedHash = 'hash';raw.questions[0].answers = ['respuesta'];
  const clean = reportDraftConfiguration(raw), serialized = JSON.stringify(clean);
  for (const secret of ['dato privado', 'token', '<b>', 'expectedHash', 'answers']) assert.equal(serialized.includes(secret), false);
  assert.equal(clean.questions[0].sensitiveAcknowledged, false);
});
test('separa cuenta, corte y revisión de plantilla', () => {
  const storage = memory(), store = createReportDraftStore(() => storage, scope);store.load();store.save(config(), {});
  for (const different of [{owner: 'admin-b'}, {analysisId: 'cut-b'}, {mode: 'template'}, {templateHash: 'otra-revision'}]) {
    assert.equal(createReportDraftStore(() => storage, {...scope, ...different}).load().status, 'empty');
  }
});
test('un error de almacenamiento no rompe el editor ni elimina una copia anterior', () => {
  const storage = memory(), store = createReportDraftStore(() => storage, scope);store.load();store.save(config(), {});
  const original = [...storage.data.values()][0];
  storage.setItem = () => {throw Error('QuotaExceededError');};
  assert.equal(store.save({...config(), title: 'Otro'}, {}).status, 'unavailable');
  assert.equal([...storage.data.values()][0], original);
  assert.equal(createReportDraftStore(() => {throw Error('SecurityError');}, scope).load().status, 'unavailable');
});
test('dos pestañas no se sobrescriben ni borran mutuamente sus cambios', () => {
  const storage = memory(), a = createReportDraftStore(() => storage, scope), b = createReportDraftStore(() => storage, scope);
  a.load();b.load();a.save(config(), {});
  assert.equal(b.save({...config(), title: 'Cambio concurrente'}, {}).status, 'conflict');
  assert.equal(b.clear(), false);
  assert.equal(createReportDraftStore(() => storage, scope).load().value.configuration.title, 'Mi reporte');
});
test('rechaza copias caducadas y contenido corrupto sin afectar otras claves', () => {
  const storage = memory(), store = createReportDraftStore(() => storage, scope, () => 1000);store.load();store.save(config(), {});
  assert.equal(createReportDraftStore(() => storage, scope, () => 1000 + 8 * 86400000).load().status, 'invalid');
  store.load();store.save(config(), {});
  const key = [...storage.data.keys()][0];storage.setItem(key, '{corrupto');
  assert.equal(createReportDraftStore(() => storage, scope).load().status, 'unavailable');
});
