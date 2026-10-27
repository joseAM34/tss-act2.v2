'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const {mountQuestionOptions, mountQuestionLimits} = require('../../main/resources/static/js/question-options.js');

class Element {
  constructor() { this.children = []; this.listeners = {}; this.value = ''; }
  append(...items) { items.forEach(item => { item.parentElement = this; this.children.push(item); }); }
  addEventListener(name, callback) { (this.listeners[name] ||= []).push(callback); }
  fire(name) { (this.listeners[name] || []).forEach(callback => callback()); }
  setAttribute(name, value) { this[name] = value; }
  removeAttribute(name) { delete this[name]; }
  remove() { this.parentElement.children.splice(this.parentElement.children.indexOf(this), 1); }
  focus() { this.focused = true; }
}
function editor(value) {
  const source = new Element(); source.value = value;
  const panel = new Element(); panel.append(source);
  const form = new Element(), type = new Element(); type.value = 'radio';
  source.closest = () => form;
  form.querySelector = () => type;
  form.querySelectorAll = () => [];
  panel.querySelector = () => new Element();
  mountQuestionOptions({querySelector: () => source, createElement: () => new Element()});
  const [, list, add] = panel.children;
  return {source, form, type, panel, list, add};
}
test('añadir, quitar y renombrar conserva códigos y no reutiliza los eliminados', () => {
  const e = editor('SI | Sí\nOPCION_1 | Otra');
  e.list.children[0].children[0].children[1].value = 'Sí, claro | de acuerdo';
  e.list.children[1].children[1].fire('click');
  e.add.fire('click');
  e.list.children[1].children[0].children[1].value = 'Nueva';
  e.form.fire('submit');
  assert.equal(e.source.value, 'SI | Sí, claro | de acuerdo\nOPCION_2 | Nueva');
  assert.equal(e.list.children[1].children[0].children[1].focused, true);
});
test('cambiar a texto omite opciones y permite recuperarlas al volver a selección', () => {
  const e = editor('NO_SABE | No sabe');
  e.type.value = 'text'; e.type.fire('change'); e.form.fire('submit');
  assert.equal(e.panel.hidden, true); assert.equal(e.source.value, '');
  assert.equal(e.list.children[0].children[0].children[1].disabled, true);
  e.type.value = 'select'; e.type.fire('change'); e.form.fire('submit');
  assert.equal(e.source.value, 'NO_SABE | No sabe');
});
test('limita a 100 opciones y permite volver a añadir después de quitar', () => {
  const e = editor('');
  for (let i = 0; i < 101; i++) e.add.fire('click');
  assert.equal(e.list.children.length, 100); assert.equal(e.add.disabled, true);
  e.list.children[0].children[1].fire('click');
  assert.equal(e.add.disabled, false);
  e.add.fire('click'); assert.equal(e.list.children.length, 100);
});

test('los límites corresponden al tipo y los campos ocultos no se envían ni pierden su valor', () => {
  const type = new Element();
  const groups = ['checkbox', 'number'].map(questionType => {
    const group = new Element(); group.dataset = {questionType};
    const input = new Element(); input.value = '3';
    group.querySelectorAll = () => [input];
    return {group, input};
  });
  const form = {querySelectorAll: () => groups.map(item => item.group)};
  type.value = 'checkbox'; mountQuestionLimits(form, type);
  for (const value of ['number', 'radio', 'select', 'text', 'textarea', 'email', 'tel', 'url', 'checkbox']) {
    type.value = value; type.fire('change');
    groups.forEach(({group, input}) => {
      assert.equal(group.hidden, value !== group.dataset.questionType);
      assert.equal(input.disabled, group.hidden);
      assert.equal(input.value, '3');
    });
  }
});
