'use strict';

function prepareReportPrint(root) {
  root.querySelectorAll('table').forEach(table => {
    const columns = Math.max(0, ...Array.from(table.rows, row =>
      Array.from(row.cells).reduce((total, cell) => total + (cell.colSpan || 1), 0)));
    (table.closest('.table-scroll') || table).classList.toggle('report-print-landscape', columns >= 6);
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
