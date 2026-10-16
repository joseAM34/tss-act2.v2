'use strict';
(async () => {
  const main = document.querySelector('[data-report-url]');
  if (!main) return;
  try {
    const response = await fetch(main.dataset.reportUrl, {credentials: 'same-origin', cache: 'no-store'});
    if (!response.ok) throw new Error('Reporte no disponible');
    const report = await response.json(), {content} = report, charts = [], exports = [];
    document.querySelectorAll('[data-report-variable]').forEach(element => {
      const v = content.analysis.result.variables[Number(element.dataset.reportVariable)];
      const chart = echarts.init(element);
      chart.setOption({animation: false, tooltip: {trigger: 'axis', renderMode: 'richText'}, grid: {containLabel: true, left: 15, right: 15, bottom: 80},
        xAxis: {type: 'category', data: v.numeric ? ['Mínimo', 'Mediana', 'Máximo'] : v.distribution.map(o => o.label), axisLabel: {rotate: 30, width: 100, overflow: 'truncate'}},
        yAxis: {type: 'value', name: v.numeric ? v.unit : 'Envíos', minInterval: v.numeric ? 0 : 1},
        series: [{type: 'bar', data: v.numeric ? ['min','median','max'].map(k => v.numeric[k]) : v.distribution.map(o => o.count)}], aria: {enabled: true}});
      charts.push(chart);
      exports.push({element, chart, title: v.label, note: `Base con valor: ${v.answered}; sin respuesta: ${v.missing}; no aplica: ${v.not_applicable}; base aplicable: ${v.applicable_base}. ${v.numeric ? "Unidad: " + v.unit + "; base numérica: " + v.numeric.n : "Conteos de envíos; porcentajes y bases en la tabla."} ${v.type === 'checkbox' ? 'Selección múltiple: un envío puede aportar varias menciones.' : ''}`});
    });
    document.querySelectorAll('[data-report-cross]').forEach(element => {
      const savedCross = content.crosses[Number(element.dataset.reportCross)], c = savedCross.result;
      const cells = c.table.observed.flatMap((row,r) => row.map((n,col) => [col,r,n])), chart = echarts.init(element);
      chart.setOption({animation: false, tooltip: {position: 'top', renderMode: 'richText'}, grid: {left: 15, right: 15, bottom: 85, containLabel: true},
        xAxis: {type: 'category', data: c.column_options.map(o => o.label), axisLabel: {rotate: 25, width: 80, overflow: 'truncate'}},
        yAxis: {type: 'category', data: c.row_options.map(o => o.label), axisLabel: {width: 80, overflow: 'truncate'}},
        visualMap: {min: 0, max: Math.max(1,...cells.map(c => c[2])), orient: 'horizontal', left: 'center', bottom: 0},
        series: [{type: 'heatmap', data: cells, label: {show: true}}], aria: {enabled: true}});
      charts.push(chart);
      exports.push({element, chart, title: `${c.row_label} × ${c.column_label}`, note: `Conteos observados. Pares: ${c.table.n}; excluidos: ${c.excluded_pairs}; total: ${c.total_submissions}. Chi-cuadrado: ${c.table.status}. Protocolo: ${c.protocol}. Huella del cruce: ${savedCross.result_hash}. Bases y diagnóstico en la tabla del reporte.`});
    });
    window.ReportDownloads.attach(main, report, exports);
    window.addEventListener('resize', () => charts.forEach(chart => chart.resize()));
  } catch { const p = document.createElement('p'); p.textContent = 'No se pudieron cargar los gráficos. Las tablas y resúmenes conservan el reporte.'; main.prepend(p); const status = main.querySelector('[data-export-status]'); if(status) status.textContent = 'Exportaci\u00f3n visual no disponible: recarga el reporte. Los enlaces de datos siguen disponibles.'; }
})();
