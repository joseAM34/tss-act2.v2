'use strict';
(async () => {
  const main = document.querySelector('[data-cross-url]'), element = document.getElementById('cross-heatmap');
  if (!main || !element || !window.echarts) return;
  try {
    const response = await fetch(main.dataset.crossUrl, {credentials: 'same-origin', cache: 'no-store'});
    if (!response.ok) throw new Error('Resultado no disponible');
    const {result} = await response.json();
    const cells = result.table.observed.flatMap((row, r) => row.map((value, c) => [c, r, value]));
    const chart = echarts.init(element);
    chart.setOption({tooltip: {position: 'top', renderMode: 'richText'}, grid: {left: 15, right: 15, bottom: 85, containLabel: true},
      xAxis: {type: 'category', data: result.column_options.map(o => o.label), axisLabel: {width: 80, overflow: 'truncate', rotate: 25}},
      yAxis: {type: 'category', data: result.row_options.map(o => o.label), axisLabel: {width: 80, overflow: 'truncate'}},
      visualMap: {min: 0, max: Math.max(1, ...cells.map(c => c[2])), orient: 'horizontal', left: 'center', bottom: 0},
      series: [{type: 'heatmap', data: cells, label: {show: true}}], aria: {enabled: true}});
    window.addEventListener('resize', () => chart.resize());
  } catch { const p = document.createElement('p'); p.textContent = 'El mapa no pudo cargarse. Consulta la tabla de conteos.'; element.replaceWith(p); }
})();
