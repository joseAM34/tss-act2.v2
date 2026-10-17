'use strict';
(async () => {
  const main = document.querySelector('[data-report-url]');
  if (!main) return;
  try {
    const response = await fetch(main.dataset.reportUrl, {credentials: 'same-origin', cache: 'no-store'});
    if (!response.ok) throw new Error('Reporte no disponible');
    const report = await response.json(), {content} = report;
    window.ReportCharts(main,content,report);
  } catch { const p = document.createElement('p'); p.textContent = 'No se pudieron cargar los gráficos. Las tablas y resúmenes conservan el reporte.'; main.prepend(p); const status = main.querySelector('[data-export-status]'); if(status) status.textContent = 'Exportaci\u00f3n visual no disponible: recarga el reporte. Los enlaces de datos siguen disponibles.'; }
})();
