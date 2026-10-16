'use strict';

function proposeCsvColumns(headers, fields) {
  const used = new Set(fields.filter(field => field.index >= 0).map(field => field.index));
  return fields.map(field => {
    if (field.index >= 0) return field.index;
    const matches = headers.map((header, index) => header === field.code ? index : -1).filter(index => index >= 0);
    if (matches.length !== 1 || used.has(matches[0])) return -1;
    used.add(matches[0]);
    return matches[0];
  });
}

if (typeof module !== 'undefined') module.exports = {proposeCsvColumns};
if (typeof document !== 'undefined') {
  document.querySelector('[data-csv-match]')?.addEventListener('click', () => {
    const form = document.querySelector('[data-csv-mapping]');
    const questionSelects = [...form.querySelectorAll('select[name^="column."]')];
    if (!questionSelects.length) return;
    const headers = [...questionSelects[0].options].filter(option => Number(option.value) >= 0).map(option => option.dataset.csvHeader);
    const selects = [...questionSelects, ...['consentColumn', 'dateColumn', 'externalIdColumn'].map(name => form.elements.namedItem(name))];
    const metadataCodes = {consentColumn: 'consentimiento', dateColumn: 'fecha_externa', externalIdColumn: 'id_externo'};
    const fields = selects.map(select => ({code: metadataCodes[select.name] ?? select.name.slice('column.'.length), index: Number(select.value)}));
    const proposals = proposeCsvColumns(headers, fields);
    let added = 0;
    selects.forEach((select, index) => {
      if (Number(select.value) < 0 && proposals[index] >= 0) {
        select.value = String(proposals[index]);
        select.dispatchEvent(new Event('change', {bubbles: true}));
        added++;
      }
    });
    const pending = questionSelects.filter(select => Number(select.value) < 0).length;
    form.querySelector('[data-csv-match-status]').textContent = `${added} columnas relacionadas. ${pending} preguntas pendientes. Revisa la propuesta y guarda la revisión; todavía no se importaron respuestas.`;
  });
}
