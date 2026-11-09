'use strict';

function prepareReportPrint(root) {
  root.querySelectorAll('table').forEach(table => {
    const columns = Math.max(0, ...Array.from(table.rows, row =>
      Array.from(row.cells).reduce((total, cell) => total + (cell.colSpan || 1), 0)));
    (table.closest('.table-scroll') || table).classList.toggle('report-print-landscape', columns >= 6 || table.rows.length > 24);
    (table.closest('.table-scroll') || table).classList.toggle('report-print-compact',table.rows.length>18);
  });
  root.querySelectorAll('.general-chart,.result-chart').forEach(chart=>{
    const option=typeof echarts!=='undefined'?echarts.getInstanceByDom(chart)?.getOption():null;
    const axes=[...(option?.xAxis||[]),...(option?.yAxis||[])];
    const wide=axes.some(axis=>(axis.data?.length||0)>=8||String(axis.name||'').length>65)||(option?.series||[]).some(series=>series.type==='scatter'||(series.data?.length||0)>=12);
    chart.classList.toggle('report-print-landscape',wide);
  });

}
if (typeof document !== 'undefined') {
  const report = document.querySelector('[data-report-url]');
  if (report) {
    prepareReportPrint(report);
    window.addEventListener('beforeprint', () => prepareReportPrint(report));
  }
}
if (typeof module !== 'undefined') module.exports = {prepareReportPrint};
