/* El backend produce cifras y decisiones. Este módulo solo dibuja y presenta. */
(function (global, factory) {
  'use strict';
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else {
    global.MarketDashboard = api;
    if (global.document) {
      if (global.document.readyState === 'loading') global.document.addEventListener('DOMContentLoaded', api.start, { once: true });
      else api.start();
    }
  }
})(typeof globalThis === 'object' ? globalThis : this, function () {
  'use strict';
  const COLORS = ['#126f67', '#315b8b', '#9d5a20', '#754b85', '#3c7844', '#a13951', '#596c32', '#376f79'];
  const LABELS = {
    study: 'Estudio', survey: 'Cuestionario', version: 'Versión del instrumento', from: 'Envíos desde', to: 'Envíos hasta',
    year: 'Año de egreso', employment: 'Situación laboral', graduateSector: 'Sector de titulados', training: 'Formación realizada',
    interest: 'Interés futuro', organizationType: 'Tipo de organización', organizationSize: 'Tamaño de organización',
    organizationSector: 'Sector de organización', compareBy: 'Comparar por', groupA: 'Grupo A', groupB: 'Grupo B'
  };
  const METRIC_LABELS = {
    value: 'Valor', numerator: 'Numerador', denominator: 'Denominador', n_base: 'Base de envíos', n: 'Datos válidos',
    n_pairs: 'Pares válidos', n_validos: 'Respuestas válidas', n_sustantivos: 'Respuestas sustantivas', n_no_sabe: 'No sabe',
    chi_square: 'Chi-cuadrado', df: 'Grados de libertad', p: 'Valor p original', p_adjusted: 'Valor p ajustado', alpha: 'Alpha',
    cramers_v: 'V de Cramér', reject_h0: 'Decisión original', reject_h0_adjusted: 'Decisión ajustada', status: 'Estado del contraste',
    family_id: 'Familia de contrastes', min_expected: 'Menor frecuencia esperada', below_five_count: 'Celdas esperadas menores de 5',
    mean: 'Media', median: 'Mediana', sample_standard_deviation: 'Desviación muestral', min: 'Mínimo', max: 'Máximo',
    q1: 'Primer cuartil', q3: 'Tercer cuartil', reason: 'Motivo', n_scored: 'Respuestas puntuadas', n_selected: 'Envíos seleccionados'
  };
  const STATES = {
    CALCULADO: 'Calculado', LISTO: 'Disponible', DESCRIPTIVO: 'Descripción disponible', SIN_DATOS: 'No hay respuestas válidas para esta selección.',
    NO_APLICA: 'Este bloque no aplica a los participantes de la selección actual.',
    NO_RECOLECTADA: 'Esta variable no se recogió en la versión seleccionada.',
    VALOR_INVALIDO: 'Hay valores que requieren revisión.', PENDIENTE_DEFINICION: 'El indicador aún no tiene una definición disponible.',
    FRECUENCIAS_INSUFICIENTES: 'No se ejecutó el contraste: frecuencias esperadas insuficientes.',
    SIN_VARIACION: 'La selección no tiene suficientes categorías para este contraste.',
    INDEPENDENCIA_NO_DOCUMENTADA: 'El contraste requiere documentar la independencia y la unidad de análisis.',
    ESTRUCTURA_INCOMPATIBLE: 'La estructura de este cruce requiere otro análisis.',
    ENTRADA_INCOMPATIBLE: 'Los datos de este resultado no son conteos válidos para el contraste.',
    ERROR_CALCULO: 'No se pudo completar el cálculo.', BORRADOR: 'Borrador exploratorio',
    NO_DECLARADO: 'Origen sin verificar', REAL: 'Datos clasificados como reales', SINTETICO: 'Datos sintéticos'
  };
  const numberFormat = new Intl.NumberFormat('es-BO', { maximumFractionDigits: 2 });
  const pFormat = new Intl.NumberFormat('es-BO', { minimumFractionDigits: 4, maximumFractionDigits: 4 });
  function finite(value) { return typeof value === 'number' && Number.isFinite(value); }
  function format(value, key) {
    if (value === null || value === undefined) return '—';
    if (typeof value === 'boolean') return key && key.startsWith('reject_h0') ? (value ? 'Rechazo de H0' : 'No rechazo de H0') : (value ? 'Sí' : 'No');
    const probability = key === 'p' || key === 'p_adjusted' || key === 'p original' || key === 'p ajustada';
    if (finite(value)) return probability ? (value > 0 && value < 0.001 ? '< 0,001' : pFormat.format(value)) : numberFormat.format(value);
    if (Array.isArray(value)) return value.map(item => format(item)).join(' · ');
    if (typeof value === 'object') return Object.entries(value).map(([name, entry]) => name + ': ' + format(entry)).join(' · ');
    return STATES[value] || String(value);
  }
  function shorten(value, max) { const text = String(value === undefined || value === null ? '' : value); return text.length > max ? text.slice(0, max - 1) + '…' : text; }
  // Canvas rich-text no interpreta HTML. Evita además que etiquetas activen sus macros de estilo.
  function richText(value) { return String(value === undefined || value === null ? '' : value).replace(/\{/g, '（').replace(/\}/g, '）'); }
  function stableColor(key) {
    if (/no sabe|no puede valorar|^NS$|NO_SABE|NO_PUEDE_VALORAR/i.test(String(key))) return '#68756d';
    let hash = 0; for (const letter of String(key)) hash = ((hash * 31) + letter.codePointAt(0)) >>> 0;
    return COLORS[hash % COLORS.length];
  }
  function selectedMode(chart, modeId) {
    return Array.isArray(chart.modes) && chart.modes.length ? chart.modes.find(mode => mode.id === modeId) || chart.modes[0] : chart;
  }
  function tableFor(chart, modeId) { return selectedMode(chart, modeId).table || chart.table || { columns: [], rows: [] }; }
  function heatmapCoordinates(matrix) {
    const available = [], missing = [];
    (matrix || []).forEach((row, i) => (row || []).forEach((value, j) => {
      if (finite(value)) available.push([j, i, value]);
      else missing.push([j, i]);
    }));
    return { available, missing };
  }
  function validRadar(chart) {
    const axes = chart.axes || [], series = chart.series || [];
    return axes.length > 0 && series.length > 0 && series.length <= 2 && series.every(item =>
      Array.isArray(item.values) && item.values.length === axes.length && item.values.every(value => finite(value) && value >= 0 && value <= 100)
      && (!Array.isArray(item.denominators) || (item.denominators.length === axes.length && item.denominators.every(value => finite(value) && value > 0))));
  }
  function baseText(chart, mode) { return [mode.label || '', mode.unit || chart.unit || '', chart.n_pairs !== undefined ? 'Pares válidos: ' + format(chart.n_pairs) : 'Base: ' + format(chart.n_base) + ' envíos', mode.base_description || ''].filter(Boolean).join(' · '); }
  function tooltipText(params, chart, mode) {
    const items = Array.isArray(params) ? params : [params];
    const lines = [richText(chart.title), richText(baseText(chart, mode))];
    for (const item of items) {
      if (!item) continue;
      let value = item.value;
      if (chart.type === 'heatmap') {
        const entry = item.data || [];
        lines.push(richText((chart.rows || [])[entry[1]]) + ' / ' + richText((chart.columns || [])[entry[0]]) + ': ' + (entry.length < 3 ? 'No disponible' : format(entry[2])));
        if (Array.isArray(mode.denominators) && Array.isArray(mode.denominators[entry[1]])) lines.push('Denominador de la celda: ' + format(mode.denominators[entry[1]][entry[0]]));
        if (chart.status) lines.push('Estado del contraste: ' + format(chart.status));
      } else if (chart.type === 'scatter') {
        value = Array.isArray(value) ? value : [];
        lines.push(richText(chart.x_label || 'X') + ': ' + format(value[0]) + ' ' + richText(chart.x_unit || chart.unit || ''));
        lines.push(richText(chart.y_label || 'Y') + ': ' + format(value[1]) + ' ' + richText(chart.y_unit || chart.unit || ''));
      } else if (chart.type === 'radar') {
        lines.push(richText(item.name || item.seriesName));
        const series = (chart.series || []).find(series => series.name === item.name);
        (chart.axes || []).forEach((axis, i) => lines.push(richText(axis.name) + ': ' + format((value || [])[i]) + ' %' + (series && Array.isArray(series.denominators) ? ' · Base sustantiva: ' + format(series.denominators[i]) : '')));
      } else if (chart.type === 'boxplot') {
        const raw = Array.isArray(value) ? value.slice(-5) : [];
        lines.push(['Mínimo', 'Q1', 'Mediana', 'Q3', 'Máximo'].map((name, i) => name + ': ' + format(raw[i])).join('\n'));
        lines.push('Bigotes: mínimo y máximo');
      } else {
        if (value && typeof value === 'object' && !Array.isArray(value)) value = value.value;
        lines.push(richText(item.name || '') + (item.seriesName ? ' · ' + richText(item.seriesName) : '') + ': ' + (value === null || value === undefined ? 'No disponible' : format(value)));
      }
    }
    return lines.join('\n');
  }
  function chartOption(chart, modeId, reducedMotion) {
    if (chart.available !== true || chart.type === 'table' || chart.type === 'indicator') return null;
    if (chart.type === 'radar' && !validRadar(chart)) return null;
    const mode = selectedMode(chart, modeId), percentage = /%|porcentaje/i.test(mode.unit || chart.unit || '');
    const option = {
      animation: !reducedMotion, animationDuration: 180, animationDurationUpdate: 180,
      color: (chart.series || []).map(series => stableColor(series.name)),
      textStyle: { fontFamily: 'Segoe UI, Arial, sans-serif', color: '#20384d' },
      aria: { enabled: true, label: { enabled: true, description: chart.title + '. ' + (chart.subtitle || '') + '. ' + baseText(chart, mode) + '. Tabla completa disponible después del gráfico.' }, decal: { show: true } },
      tooltip: { trigger: ['bar', 'stacked'].includes(chart.type) ? 'axis' : 'item', renderMode: 'richText', confine: true, backgroundColor: '#ffffff', borderColor: '#71867a', textStyle: { color: '#20384d', fontSize: 12 }, formatter: params => tooltipText(params, chart, mode) },
      legend: { show: (chart.series || []).length > 1, top: 0, type: 'scroll', textStyle: { color: '#20384d', fontSize: 11 } },
      grid: { left: 8, right: 20, top: (chart.series || []).length > 1 ? 42 : 16, bottom: 32, containLabel: true }
    };
    if (chart.type === 'bar' || chart.type === 'stacked') {
      option.xAxis = { type: 'value', min: 0, max: mode.max !== undefined ? mode.max : percentage ? 100 : undefined, axisLabel: { color: '#435a4c', formatter: value => format(value) + (percentage ? ' %' : '') }, splitLine: { lineStyle: { color: '#e2e9e4' } } };
      option.yAxis = { type: 'category', inverse: true, data: chart.categories || [], axisLabel: { color: '#20384d', fontSize: 11, formatter: value => shorten(value, 28) }, axisLine: { lineStyle: { color: '#71867a' } }, axisTick: { show: false } };
      let series = chart.series || [];
      if (chart.modes && chart.modes.length) series = [{ name: mode.label || (series[0] ? series[0].name : ''), values: mode.values || [] }];
      option.series = series.map(item => ({ name: item.name, type: 'bar', stack: chart.type === 'stacked' ? 'respuestas' : undefined, barMaxWidth: 30,
        data: (item.values || []).map((value, i) => ({ value: finite(value) ? value : null, itemStyle: { color: series.length > 1 ? stableColor(item.name) : stableColor((chart.categories || [])[i]) } })),
        label: { show: chart.type !== 'stacked', position: 'right', color: '#20384d', fontSize: 11, formatter: params => finite(params.value) ? format(params.value) : '' }
      }));
    } else if (chart.type === 'heatmap') {
      const coordinates = heatmapCoordinates(mode.values), rows = chart.rows || [], columns = chart.columns || [];
      const maximum = finite(mode.max) ? mode.max : percentage ? 100 : Math.max(1, ...coordinates.available.map(entry => entry[2]));
      option.grid = { left: 8, right: 20, top: 18, bottom: 90, containLabel: true };
      option.xAxis = { type: 'category', data: columns, position: 'top', splitArea: { show: true }, axisLabel: { color: '#20384d', fontSize: 10, interval: 0, formatter: value => shorten(value, 18) }, axisTick: { show: false } };
      option.yAxis = { type: 'category', data: rows, inverse: true, splitArea: { show: true }, axisLabel: { color: '#20384d', fontSize: 10, formatter: value => shorten(value, 24) }, axisTick: { show: false } };
      option.visualMap = { min: finite(mode.min) ? mode.min : 0, max: maximum, dimension: 2, seriesIndex: 0, calculable: false, orient: 'horizontal', left: 'center', bottom: 5, text: [format(maximum) + (percentage ? ' %' : ''), '0'], textStyle: { color: '#20384d' }, inRange: { color: ['#f1f7f3', '#d8eade', '#b1d5c9', '#82b4a4'] } };
      option.series = [{ type: 'heatmap', name: mode.label, data: coordinates.available, itemStyle: { borderWidth: 1, borderColor: '#9eafa5' }, label: { show: true, color: '#132e25', formatter: params => format(params.value[2]) } },
        { type: 'scatter', name: 'No disponible', data: coordinates.missing, symbol: 'rect', symbolSize: 18, itemStyle: { color: '#6b7770', borderColor: '#20384d', borderWidth: 1 }, label: { show: true, color: '#ffffff', formatter: '—' } }];
    } else if (chart.type === 'radar') {
      option.radar = { indicator: chart.axes.map(axis => ({ name: axis.name, min: 0, max: 100 })), center: ['50%', '53%'], radius: '42%', axisName: { color: '#20384d', fontSize: 10, formatter: name => shorten(name, 17) }, splitArea: { areaStyle: { color: ['#fff', '#f4f7f3'] } }, splitLine: { lineStyle: { color: '#aabfb2' } } };
      option.series = [{ type: 'radar', data: chart.series.map((item, i) => ({ name: item.name, value: item.values, symbol: i ? 'diamond' : 'circle', symbolSize: 7, lineStyle: { width: 2, type: i ? 'dashed' : 'solid', color: stableColor(item.name) }, itemStyle: { color: stableColor(item.name) }, areaStyle: { opacity: 0.035 } })) }];
    } else if (chart.type === 'boxplot') {
      option.xAxis = { type: 'category', data: chart.categories || [], axisLabel: { color: '#20384d', fontSize: 11, formatter: label => shorten(label, 30) } };
      option.yAxis = { type: 'value', min: 0, name: chart.unit || '', nameTextStyle: { color: '#20384d', fontSize: 11 }, axisLabel: { color: '#435a4c' }, splitLine: { lineStyle: { color: '#e2e9e4' } } };
      option.series = (chart.series || []).map(item => ({ name: item.name, type: 'boxplot', data: item.values || [], itemStyle: { color: '#d5e9df', borderColor: '#126f67', borderWidth: 2 } }));
    } else if (chart.type === 'scatter') {
      option.grid = { left: 12, right: 20, top: 32, bottom: 64, containLabel: true };
      option.xAxis = { type: 'value', min: 0, name: shorten(chart.x_label || 'X', 48), nameLocation: 'middle', nameGap: 32, nameTextStyle: { color: '#20384d', fontSize: 11 }, axisLabel: { color: '#435a4c' } };
      option.yAxis = { type: 'value', min: 0, name: shorten(chart.y_label || 'Y', 44), nameTextStyle: { color: '#20384d', fontSize: 11 }, axisLabel: { color: '#435a4c' }, splitLine: { lineStyle: { color: '#e2e9e4' } } };
      option.series = (chart.series || []).map(item => ({ name: item.name, type: 'scatter', symbolSize: 9, data: (item.values || []).filter(point => Array.isArray(point) && point.length === 2 && point.every(finite)), itemStyle: { color: stableColor(item.name), opacity: 0.8 } }));
    } else return null;
    return option;
  }
  function queryString(selection) { return new URLSearchParams(Object.entries(selection || {}).filter(([, value]) => value !== '' && value !== null && value !== undefined).sort(([a], [b]) => a.localeCompare(b))).toString(); }
  function requestGate() {
    let current = 0;
    return { next() { return ++current; }, current(sequence) { return current === sequence; } };
  }
  function start() {
    const root = document.querySelector('[data-endpoint]');
    if (!root || !root.matches('.dashboard-main')) return;
    const endpoint = root.dataset.endpoint;
    const $ = id => document.getElementById(id);
    const form = $('dashboard-filter-form'), body = $('dashboard-body'), panels = $('dashboard-panels'), tabs = $('dashboard-tabs');
    const gate = requestGate(), instances = new Map();
    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    const observer = typeof ResizeObserver === 'function' ? new ResizeObserver(entries => entries.forEach(entry => {
      const instance = instances.get(entry.target); if (instance && entry.contentRect.width > 0 && entry.contentRect.height > 0) instance.resize();
    })) : null;
    let abort, payload, applied = {}, requested = {}, comparisonCategories = {}, activeSection = 'V01', alive = true;
    function node(tag, className, text) {
      const element = document.createElement(tag); if (className) element.className = className; if (text !== undefined && text !== null) element.textContent = String(text); return element;
    }
    function disposeCharts() { if (observer) observer.disconnect(); instances.forEach(instance => instance.dispose()); instances.clear(); }
    function currentSelection() { return Object.fromEntries(Array.from(new FormData(form)).map(([key, value]) => [key, String(value)]).filter(([, value]) => value)); }
    function updateDirty() { $('dashboard-dirty').hidden = queryString(currentSelection()) === queryString(applied); }
    function fillOptions(select, options, selected) {
      select.replaceChildren(node('option', '', select.name === 'compareBy' ? 'Sin comparación' : select.name === 'groupA' || select.name === 'groupB' ? 'Seleccionar categoría' : 'Todas las categorías'));
      select.firstElementChild.value = '';
      (options || []).forEach(item => { const option = node('option', '', item.label); option.value = String(item.code); select.append(option); });
      select.value = selected || '';
    }
    function updateGroups(preserve) {
      const compare = form.elements.namedItem('compareBy');
      if (!compare) return;
      const variable = compare.value, options = comparisonCategories[variable] || (payload && payload.filters ? payload.filters[variable] : []) || [];
      for (const name of ['groupA', 'groupB']) {
        const select = form.elements.namedItem(name); if (!select) continue;
        const selected = preserve ? select.value : '';
        fillOptions(select, options.length ? options : payload && payload.filters ? payload.filters[name] : [], selected);
        select.disabled = !variable;
      }
    }
    function buildFilters(data, selection) {
      const area = $('dashboard-filter-fields'); area.replaceChildren();
      comparisonCategories = data.context.comparison_catalog || data.filters.comparisonCategories || data.context.comparison_categories || {};
      for (const [name, label] of Object.entries(LABELS)) {
        if (!Object.hasOwn(data.filters, name) && name !== 'from' && name !== 'to') continue;
        const wrapper = node('div', 'dashboard-filter-field'), title = node('label', '', label); title.htmlFor = 'filter-' + name;
        const field = node(name === 'from' || name === 'to' ? 'input' : 'select'); field.name = name; field.id = title.htmlFor;
        if (field.tagName === 'INPUT') { field.type = 'date'; field.value = selection[name] || ''; field.disabled = data.context.date_available === false; }
        else fillOptions(field, data.filters[name] || [], selection[name]);
        wrapper.append(title, field); area.append(wrapper);
      }
      updateGroups(true);
      $('dashboard-comparison-hint').textContent = data.context.date_available === false
        ? 'Este conjunto no conserva fechas de envío suficientes para filtrar por período. La comparación utiliza categorías válidas de cada grupo.'
        : 'La comparación conserva las bases de cada grupo. Selecciona una variable y dos categorías distintas.';
    }
    function selectionLabel(selection, filters) {
      const entries = Object.entries(selection).filter(([name, value]) => value && LABELS[name]);
      return entries.length ? entries.map(([name, value]) => LABELS[name] + ': ' + (((filters[name] || []).find(option => String(option.code) === String(value)) || {}).label || value)).join(' · ') : 'Selección base del conjunto, sin filtros adicionales.';
    }
    function renderContext(data) {
      const context = data.context, origin = $('dashboard-origin');
      origin.hidden = false; origin.dataset.origin = context.origin || '';
      origin.textContent = context.origin === 'SINTETICO' ? 'Demostración: datos sintéticos · Borrador exploratorio'
        : context.origin === 'REAL' ? 'Datos clasificados como reales · Borrador exploratorio'
        : 'Origen sin verificar · Borrador exploratorio';
      $('dashboard-selection').textContent = selectionLabel(applied, data.filters);
      const facts = $('dashboard-context-facts'); facts.replaceChildren();
      for (const [label, value] of [['Envíos seleccionados', context.n_selected], ['Base del conjunto', context.n_base], ['Unidad', context.unit || 'Un envío'], ['Actualización', 'Selección recibida del backend']]) {
        const item = node('span'); item.append(document.createTextNode(label + ': '), node('strong', '', format(value))); facts.append(item);
      }
      if (context.group_definitions) facts.append(node('span', '', 'Grupos: ' + format(context.group_definitions)));
      const detail = $('dashboard-trace-list'); detail.replaceChildren();
      for (const [label, value] of [['Análisis', context.analysis_id], ['Conjunto de origen', context.source_run_id], ['Protocolo', context.protocol_version], ['Estado', context.status], ['Modo del conjunto', context.mode], ['Fecha de corte', context.cutoff || context.cutoff_utc], ['Huella de la selección', context.selection_hash], ['Huella del resultado', context.result_hash], ['Consulta', context.query_id], ['Configuración visual', data.visual_config_version]]) {
        if (value === undefined || value === null) continue;
        const entry = node('div'); entry.append(node('dt', '', label), node('dd', '', format(value))); detail.append(entry);
      }
      $('dashboard-trace').hidden = false;
    }
    function renderTable(area, chart, modeId) {
      area.replaceChildren();
      const data = tableFor(chart, modeId), table = node('table'), head = node('thead'), header = node('tr'), tbody = node('tbody');
      table.append(node('caption', '', chart.title + (selectedMode(chart, modeId).label ? ' · ' + selectedMode(chart, modeId).label : '')));
      (data.columns || []).forEach(column => { const cell = node('th', '', column); cell.scope = 'col'; header.append(cell); }); head.append(header);
      (data.rows || []).forEach(row => {
        const line = node('tr');
        (row || []).forEach(value => { const cell = node('td', value === null || value === undefined ? 'dashboard-null' : '', format(value)); if (value === null || value === undefined) cell.title = 'No disponible'; line.append(cell); });
        tbody.append(line);
      });
      table.append(head, tbody); area.append(table);
    }
    function renderMetrics(card, metrics) {
      if (!metrics || !Object.keys(metrics).length) return;
      const list = node('dl', 'dashboard-metrics');
      for (const [key, value] of Object.entries(metrics)) {
        if (typeof value === 'object' && value !== null) continue;
        const entry = node('div'); entry.append(node('dt', '', METRIC_LABELS[key] || key.replaceAll('_', ' ')), node('dd', '', format(value, key))); list.append(entry);
      }
      card.append(list);
    }
    function renderCard(chart, parent) {
      const wide = ['heatmap', 'scatter', 'stacked', 'table'].includes(chart.type);
      const card = node('article', 'dashboard-chart-card' + (wide ? ' wide' : '')); card.dataset.chartId = chart.id;
      const title = node('h3', '', chart.title); title.id = 'chart-title-' + chart.id; card.setAttribute('aria-labelledby', title.id); card.append(title);
      const subtitle = node('p', 'dashboard-chart-subtitle', chart.subtitle || ''); card.append(subtitle);
      let modeId = chart.modes && chart.modes[0] ? chart.modes[0].id : undefined;
      const modeNote = node('p', 'dashboard-chart-subtitle', baseText(chart, selectedMode(chart, modeId))); card.append(modeNote);
      const state = node('p', 'dashboard-chart-state' + (chart.available ? '' : ' warning'));
      const statusText = STATES[chart.status] || chart.status || '';
      state.textContent = [chart.available && ['LISTO', 'CALCULADO', 'DESCRIPTIVO'].includes(chart.status) ? '' : statusText, chart.reason || ''].filter(Boolean).join(' ');
      state.hidden = !state.textContent; card.append(state);
      if (chart.type === 'indicator') {
        card.append(node('p', 'dashboard-indicator-value', format(chart.metrics && chart.metrics.value) + (chart.unit ? ' ' + chart.unit : '')));
        if (chart.metrics && (chart.metrics.numerator !== undefined || chart.metrics.denominator !== undefined)) card.append(node('p', 'dashboard-indicator-basis', format(chart.metrics.numerator) + ' de ' + format(chart.metrics.denominator) + ' respuestas válidas'));
      }
      const stage = node('div', 'dashboard-chart-stage' + (chart.type === 'heatmap' ? ' heatmap-stage' : ''));
      const plot = node('div', 'dashboard-chart'); plot.dataset.type = chart.type; plot.setAttribute('role', 'img'); plot.setAttribute('aria-labelledby', title.id);
      stage.append(plot); card.append(stage);
      const hiddenSeries = node('p', 'dashboard-hidden-series'); hiddenSeries.hidden = true; hiddenSeries.setAttribute('aria-live', 'polite'); card.append(hiddenSeries);
      const detail = node('details'), summary = node('summary', '', 'Ver tabla de datos y bases'); detail.append(summary);
      const scroll = node('div', 'table-scroll'); scroll.tabIndex = 0; scroll.setAttribute('role', 'region'); scroll.setAttribute('aria-label', 'Tabla de ' + chart.title); detail.append(scroll);
      detail.append(node('p', 'dashboard-table-note', '— indica un dato no disponible; 0 es un valor observado. La tabla conserva todas las series aunque se oculten en la leyenda.'));
      if (chart.type === 'boxplot') detail.append(node('p', 'dashboard-table-note', 'Bigotes: mínimo y máximo. No se utilizan límites de 1,5 IQR ni se identifican valores atípicos. Con un solo dato, la dispersión muestral no está disponible.'));
      if (chart.type === 'heatmap') detail.append(node('p', 'dashboard-table-note', 'El marcador gris «—» señala una celda no disponible, distinta del color de cero. El mapa es descriptivo; el resultado global del contraste no implica significación por celda.'));
      if (chart.type === 'scatter') detail.append(node('p', 'dashboard-table-note', 'Cada punto es un par válido de duraciones, sin identificadores personales. Una relación visual no demuestra causalidad. Los puntos coincidentes pueden superponerse; la tabla conserva cada par.'));
      card.append(detail); renderTable(scroll, chart, modeId); renderMetrics(card, chart.metrics); parent.append(card);
      const draw = () => {
        const existing = instances.get(plot); if (existing) { if (observer) observer.unobserve(plot); existing.dispose(); instances.delete(plot); }
        hiddenSeries.textContent = ''; hiddenSeries.hidden = true;
        const option = chartOption(chart, modeId, reducedMotion);
        plot.hidden = !option;
        if (!option) {
          if (chart.type === 'radar' && chart.available && !validRadar(chart)) { state.hidden = false; state.textContent = 'No se dibuja el radar: falta al menos un eje con un dato y una base válidos.'; }
          return;
        }
        if (!window.echarts) { plot.hidden = true; state.hidden = false; state.textContent = 'No se pudo cargar el recurso de gráficos. La tabla completa sigue disponible.'; detail.open = true; return; }
        if (!plot.clientWidth || !plot.clientHeight) return;
        const instance = window.echarts.init(plot, null, { renderer: 'canvas' }); instances.set(plot, instance);
        instance.setOption(option, { notMerge: true });
        instance.on('legendselectchanged', event => {
          const hidden = Object.entries(event.selected || {}).filter(([, selected]) => !selected).map(([name]) => name);
          hiddenSeries.textContent = hidden.length ? 'Series ocultas: ' + hidden.join(', ') + '. Las cifras y la tabla conservan la base completa.' : '';
          hiddenSeries.hidden = !hidden.length;
        });
        if (observer) observer.observe(plot);
      };
      card.dashboardDraw = draw;
      if (chart.modes && chart.modes.length > 1) {
        const controls = node('div', 'dashboard-chart-controls'), label = node('label', '', 'Mostrar'), select = node('select');
        select.id = 'mode-' + chart.id; label.htmlFor = select.id;
        chart.modes.forEach(mode => { const option = node('option', '', mode.label); option.value = mode.id; select.append(option); });
        select.addEventListener('change', () => { modeId = select.value; modeNote.textContent = baseText(chart, selectedMode(chart, modeId)); renderTable(scroll, chart, modeId); hiddenSeries.hidden = true; draw(); });
        controls.append(label, select); card.insertBefore(controls, stage);
      }
    }
    function selectSection(sectionId, focus) {
      disposeCharts(); activeSection = sectionId;
      tabs.querySelectorAll('[role="tab"]').forEach(tab => { const selected = tab.dataset.section === sectionId; tab.setAttribute('aria-selected', String(selected)); tab.tabIndex = selected ? 0 : -1; if (selected && focus) tab.focus(); });
      panels.querySelectorAll('[role="tabpanel"]').forEach(panel => { panel.hidden = panel.dataset.section !== sectionId; if (!panel.hidden) panel.querySelectorAll('.dashboard-chart-card').forEach(card => card.dashboardDraw()); });
    }
    function renderSections(data) {
      disposeCharts(); tabs.replaceChildren(); panels.replaceChildren();
      (data.sections || []).forEach(section => {
        const tab = node('button', '', section.title); tab.type = 'button'; tab.id = 'tab-' + section.id; tab.dataset.section = section.id; tab.setAttribute('role', 'tab'); tab.setAttribute('aria-controls', 'panel-' + section.id); tab.addEventListener('click', () => selectSection(section.id, false)); tabs.append(tab);
        const panel = node('section', 'dashboard-panel'); panel.id = 'panel-' + section.id; panel.dataset.section = section.id; panel.setAttribute('role', 'tabpanel'); panel.setAttribute('aria-labelledby', tab.id); panel.tabIndex = 0; panel.hidden = true;
        panel.append(node('h2', '', section.title), node('p', 'dashboard-panel-description', section.description || ''));
        const grid = node('div', 'dashboard-chart-grid');
        (section.charts || []).forEach(chart => renderCard(chart, grid));
        if (!(section.charts || []).length) grid.append(node('p', 'dashboard-empty', 'No hay resultados disponibles en esta sección para la selección actual.'));
        panel.append(grid); panels.append(panel);
      });
      body.hidden = false;
      selectSection((data.sections || []).some(section => section.id === activeSection) ? activeSection : data.sections && data.sections[0] ? data.sections[0].id : '', false);
    }
    async function load(selection) {
      if (!alive) return;
      requested = { ...selection }; const sequence = gate.next();
      if (abort) abort.abort(); abort = new AbortController();
      disposeCharts(); body.hidden = true; panels.replaceChildren(); tabs.replaceChildren();
      $('dashboard-query-error').hidden = true; root.setAttribute('aria-busy', 'true');
      $('dashboard-request').dataset.state = 'loading'; $('dashboard-request').textContent = 'Actualizando selección…';
      $('dashboard-selection').textContent = 'Selección solicitada: ' + selectionLabel(selection, payload ? payload.filters : {});
      $('dashboard-context-facts').replaceChildren(); $('dashboard-trace').hidden = true;
      try {
        const response = await fetch(endpoint + (queryString(selection) ? '?' + queryString(selection) : ''), { credentials: 'same-origin', headers: { Accept: 'application/json' }, signal: abort.signal, cache: 'no-store' });
        if (!gate.current(sequence) || !alive) return;
        if (response.redirected || response.status === 401 || response.status === 403) throw new Error('La sesión venció o no tienes acceso a estos resultados. Inicia sesión para continuar.');
        if (!response.ok) {
          let message = 'No se pudieron obtener los resultados. Revisa la selección e inténtalo de nuevo.';
          if ((response.headers.get('Content-Type') || '').includes('application/json')) {
            const error = await response.json(); if (error.error || error.message || error.detail || error.reason) message = error.error || error.message || error.detail || error.reason;
          }
          throw new Error(message);
        }
        if (!(response.headers.get('Content-Type') || '').includes('application/json')) throw new Error('La respuesta no contiene resultados. Comprueba tu sesión de administración.');
        const data = await response.json();
        if (!gate.current(sequence) || !alive) return;
        if (!data || !data.context || !data.filters || !Array.isArray(data.sections)) throw new Error('El resultado recibido no tiene un formato disponible para esta vista.');
        const formDraft = currentSelection(), preserveDraft = payload && queryString(formDraft) !== queryString(selection);
        payload = data; applied = data.context.applied_selection || { ...selection };
        buildFilters(data, preserveDraft ? formDraft : applied); renderContext(data); renderSections(data); updateDirty();
        $('dashboard-request').dataset.state = 'ready'; $('dashboard-request').textContent = 'Selección actualizada: ' + format(data.context.n_selected) + ' envíos. Gráficos y tablas usan la misma selección.';
      } catch (error) {
        if (!gate.current(sequence) || !alive || error.name === 'AbortError') return;
        $('dashboard-request').dataset.state = 'error'; $('dashboard-request').textContent = 'No se cargaron resultados para la selección solicitada.';
        $('dashboard-error-message').textContent = error.message || 'No se pudieron obtener los resultados.'; $('dashboard-query-error').hidden = false;
      } finally { if (gate.current(sequence) && alive) root.setAttribute('aria-busy', 'false'); }
    }
    form.addEventListener('submit', event => { event.preventDefault(); load(currentSelection()); });
    form.addEventListener('change', event => { if (event.target.name === 'compareBy') updateGroups(false); updateDirty(); });
    $('dashboard-reset').addEventListener('click', () => { for (const field of Array.from(form.elements)) if (field.name) field.value = ''; updateGroups(false); load({}); });
    $('dashboard-retry').addEventListener('click', () => load(requested));
    tabs.addEventListener('keydown', event => {
      const buttons = Array.from(tabs.querySelectorAll('[role="tab"]')), index = buttons.indexOf(document.activeElement); if (index < 0) return;
      let next;
      if (event.key === 'ArrowRight' || event.key === 'ArrowDown') next = (index + 1) % buttons.length;
      else if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') next = (index - 1 + buttons.length) % buttons.length;
      else if (event.key === 'Home') next = 0; else if (event.key === 'End') next = buttons.length - 1;
      if (next !== undefined) { event.preventDefault(); selectSection(buttons[next].dataset.section, true); }
    });
    function resize() { instances.forEach(instance => instance.resize()); tabs.setAttribute('aria-orientation', window.innerWidth > 850 ? 'vertical' : 'horizontal'); }
    window.addEventListener('resize', resize);
    window.addEventListener('pagehide', () => { alive = false; gate.next(); if (abort) abort.abort(); disposeCharts(); window.removeEventListener('resize', resize); }, { once: true });
    resize(); load({});
  }
  return { chartOption, heatmapCoordinates, validRadar, selectedMode, tableFor, format, queryString, requestGate, stableColor, start };
});
