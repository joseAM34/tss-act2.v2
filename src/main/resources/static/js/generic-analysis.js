'use strict';
(async () => {
  const main = document.querySelector('[data-analysis-url]');
  if (!main || !window.echarts) return;
  try {
    const response = await fetch(main.dataset.analysisUrl, {credentials: 'same-origin', cache: 'no-store'});
    if (!response.ok) throw new Error('No se pudo cargar el resultado');
    const {result} = await response.json();
    const charts = [];
    document.querySelectorAll('[data-variable-index]').forEach(element => {
      const variable = result.variables[Number(element.dataset.variableIndex)];
      const chart = echarts.init(element);
      const labels = variable.numeric ? ['Mínimo', 'Mediana', 'Máximo'] : variable.distribution.map(o => o.label);
      const values = variable.numeric ? ['min', 'median', 'max'].map(k => variable.numeric[k]) : variable.distribution.map(o => o.count);
      chart.setOption({tooltip: {trigger: 'axis', renderMode: 'richText'}, grid: {containLabel: true, left: 15, right: 15, bottom: 80},
        xAxis: {type: 'category', data: labels, axisLabel: {rotate: 30, width: 100, overflow: 'truncate'}},
        yAxis: {type: 'value', name: variable.numeric ? variable.unit : 'Envíos'},
        series: [{type: 'bar', colorBy: variable.numeric ? 'series' : 'data', data: values}], aria: {enabled: true}});
      charts.push(chart);
    });
    window.addEventListener('resize', () => charts.forEach(chart => chart.resize()));
  } catch {
    const message = document.createElement('p'); message.textContent = 'No se pudieron cargar los gráficos. Las tablas conservan los resultados.'; main.prepend(message);
  }
})();
