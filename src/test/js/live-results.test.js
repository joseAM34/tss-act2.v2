'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const {createLiveCoordinator}=require('../../main/resources/static/js/live-results.js');
test('una respuesta antigua no reemplaza el resultado de una consulta nueva',()=>{
  const coordinator=createLiveCoordinator(), first=coordinator.start(), second=coordinator.start();let visible;
  const deliver=(token,result)=>{if(coordinator.current(token))visible=result;};
  deliver(second,'nuevo');deliver(first,'antiguo');assert.equal(visible,'nuevo');
});
test('guardar o abandonar la pestaña invalida consultas que siguen pendientes',()=>{
  const coordinator=createLiveCoordinator(), token=coordinator.start();coordinator.invalidate();
  assert.equal(coordinator.current(token),false);assert.equal(coordinator.current(coordinator.start()),true);
});
