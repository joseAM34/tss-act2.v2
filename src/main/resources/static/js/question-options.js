'use strict';

function mountQuestionLimits(form, type) {
  const groups = form.querySelectorAll('[data-question-type]');
  function update() {
    groups.forEach(group => {
      const active = group.dataset.questionType === type.value;
      group.hidden = !active;
      group.querySelectorAll('input').forEach(input => { input.disabled = !active; });
    });
  }
  type.addEventListener('change', update);
  update();
}

function mountQuestionOptions(document) {
  const source = document.querySelector('#options');
  if (!source) return;
  const form = source.closest('form');
  const type = form.querySelector('#type');
  mountQuestionLimits(form, type);
  const panel = source.parentElement;
  const initial = source.value.split(/\r?\n/).filter(line => line.trim());
  // Preserve the raw editor if a malformed submission needs correcting.
  if (initial.some(line => !line.includes('|'))) return;
  const used = new Set(initial.map(line => line.split('|', 1)[0].trim()));
  let nextCode = 1;
  const heading = panel.querySelector('label');
  heading.removeAttribute('for');
  heading.textContent = 'Opciones de respuesta';
  panel.querySelector('#options-help').textContent = 'Escribe una respuesta por casilla. Puedes añadir o quitar opciones. Los cambios se guardan al pulsar «Guardar pregunta».';
  const list = document.createElement('div');
  list.className = 'question-options-list';
  const add = document.createElement('button');
  add.type = 'button'; add.className = 'button button-secondary';
  add.textContent = '+ Añadir respuesta';
  const status = document.createElement('p');
  status.className = 'field-hint'; status.setAttribute('aria-live', 'polite');
  const rows = [];
  function refresh() {
    rows.forEach((row, index) => {
      row.label.textContent = `Respuesta ${index + 1}`;
      row.remove.setAttribute('aria-label', `Quitar respuesta ${index + 1}`);
    });
    add.disabled = rows.length >= 100;
    status.textContent = `${rows.length} de 100 opciones como máximo.`;
  }
  function append(code, text, focus) {
    const element = document.createElement('div');
    element.className = 'question-option-row';
    const label = document.createElement('label');
    const input = document.createElement('input');
    input.type = 'text'; input.value = text; input.maxLength = 1000;
    input.placeholder = 'Escribe una respuesta';
    input.id = `option-text-${list.children.length}-${code}`;
    input.required = true; label.htmlFor = input.id;
    const remove = document.createElement('button');
    remove.type = 'button'; remove.className = 'button button-secondary'; remove.textContent = 'Quitar';
    const field = document.createElement('div'); field.className = 'form-control';
    field.append(label, input); element.append(field, remove);
    const row = {code, element, input, label, remove};
    remove.addEventListener('click', () => {
      const index = rows.indexOf(row);
      rows.splice(index, 1); element.remove(); refresh();
      (rows[index]?.input || rows[index - 1]?.input || add).focus();
    });
    rows.push(row); list.append(element); refresh();
    if (focus) input.focus();
  }
  add.addEventListener('click', () => {
    if (rows.length >= 100) return;
    let code;
    do { code = `OPCION_${nextCode++}`; } while (used.has(code));
    used.add(code); append(code, '', true);
  });
  initial.forEach(line => {
    const separator = line.indexOf('|');
    append(line.slice(0, separator).trim(), line.slice(separator + 1).trim(), false);
  });
  source.hidden = true;
  panel.append(list, add, status);
  function updateType() {
    const choices = ['radio', 'checkbox', 'select'].includes(type.value);
    panel.hidden = !choices;
    rows.forEach(row => { row.input.disabled = !choices; });
  }
  type.addEventListener('change', updateType);
  // Newly added rows must follow the active question type too.
  add.addEventListener('click', updateType);
  form.addEventListener('submit', () => {
    source.value = panel.hidden ? '' : rows.map(row => `${row.code} | ${row.input.value.trim()}`).join('\n');
  });
  refresh(); updateType();
}

if (typeof document !== 'undefined') mountQuestionOptions(document);
if (typeof module !== 'undefined') module.exports = {mountQuestionOptions, mountQuestionLimits};
