'use strict';

// Only presentation choices are persisted: never bootstrap results, HTML, tokens or hashes of previews.
function reportDraftConfiguration(config) {
  const pick = (item, fields) => Object.fromEntries(fields.map(key => [key, item[key]]));
  const copy = pick(config, ['analysisId', 'title', 'introduction', 'context', 'tables', 'charts', 'comment']);
  copy.questions = config.questions.map(q => ({...pick(q, ['id', 'included', 'title', 'comment', 'representations']), sensitiveAcknowledged: false}));
  copy.crossIds = [...config.crossIds];
  copy.crossSettings = (config.crossSettings || []).map(c => pick(c, ['id', 'title', 'comment', 'representations']));
  copy.advanced = (config.advanced || []).map(a => pick(a, ['type', 'questions', 'title', 'comment', 'included']));
  return JSON.parse(JSON.stringify(copy));
}

function createReportDraftStore(getStorage, scope, now = Date.now) {
  const key = 'umss:report-draft:v1:' + JSON.stringify(scope);
  const ttl = 7 * 24 * 60 * 60 * 1000;
  let previous;
  const strings = value => Array.isArray(value) ? value.filter(item => typeof item === 'string') : [];
  const uiState = (ui = {}) => ({
    templateName: typeof ui.templateName === 'string' ? ui.templateName : '',
    saveTemplate: ui.saveTemplate === true,
    hiddenCrosses: strings(ui.hiddenCrosses), pair: strings(ui.pair).slice(0, 2),
    pairRepresentations: strings(ui.pairRepresentations),
    pairTitle: typeof ui.pairTitle === 'string' ? ui.pairTitle : '',
    pairComment: typeof ui.pairComment === 'string' ? ui.pairComment : '',
    crossLabels: (Array.isArray(ui.crossLabels) ? ui.crossLabels : [])
      .filter(item => item && ['id', 'row', 'column'].every(key => typeof item[key] === 'string'))
      .map(({id, row, column}) => ({id, row, column}))
  });
  return {
    load() {
      try {
        const storage = getStorage(); previous = storage.getItem(key);
        if (!previous) return {status: 'empty'};
        const value = JSON.parse(previous);
        if (value.version !== 1 || JSON.stringify(value.scope) !== JSON.stringify(scope)
            || !Number.isFinite(value.updatedAt) || now() - value.updatedAt > ttl
            || !value.configuration || value.configuration.analysisId !== scope.analysisId
            || !Array.isArray(value.configuration.questions) || !Array.isArray(value.configuration.crossIds)) {
          storage.removeItem(key); previous = null; return {status: 'invalid'};
        }
        value.configuration = reportDraftConfiguration(value.configuration);
        value.ui = uiState(value.ui || {});
        return {status: 'restored', value};
      } catch (_) { return {status: 'unavailable'}; }
    },
    save(configuration, ui) {
      try {
        const storage = getStorage();
        if (storage.getItem(key) !== previous) return {status: 'conflict'};
        const value = {version: 1, scope, updatedAt: now(), configuration: reportDraftConfiguration(configuration), ui: uiState(ui)};
        const serialized = JSON.stringify(value);
        if (serialized.length > 250000) return {status: 'unavailable'};
        storage.setItem(key, serialized); previous = serialized;
        return {status: 'saved', updatedAt: value.updatedAt};
      } catch (_) { return {status: 'unavailable'}; }
    },
    clear() {
      try {
        const storage = getStorage();
        if (storage.getItem(key) !== previous) return false;
        storage.removeItem(key); previous = null; return true;
      } catch (_) { return false; }
    }
  };
}

if (typeof module !== 'undefined') module.exports = {createReportDraftStore, reportDraftConfiguration};
if (typeof window !== 'undefined') window.ReportDrafts = {createReportDraftStore};
