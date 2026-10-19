'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {createResultsCoordinator,resultRepresentations}=require('../../main/resources/static/js/workspace-results.js');
const {textRepresentationAvailability}=require('../../main/resources/static/js/workspace-results.js');
test('textos y coincidencias sensibles requieren confirmación incluso si hay menciones',()=>{
  const data={sensitive:true,sensitiveAcknowledged:false,groupable:true,dictionaryRevision:1,base:2,keywords:{distribution:[{count:2}]}};
  assert.deepEqual(textRepresentationAvailability(data),{texts:false,matches:false,'match-chart':false,'match-pie':false,'match-donut':false});
  assert.deepEqual(textRepresentationAvailability({...data,sensitiveAcknowledged:true}),{texts:true,matches:true,'match-chart':true,'match-pie':true,'match-donut':true});
});
test('coincidencias y gráficas usan menciones del diccionario guardado, no respuestas completas repetidas',()=>{
  const unique={groupable:true,dictionaryRevision:1,base:2,distribution:[{count:1},{count:1}],keywords:{distribution:[{count:0}]}};
  assert.deepEqual(textRepresentationAvailability(unique),{texts:true,matches:false,'match-chart':false,'match-pie':false,'match-donut':false});
  assert.equal(textRepresentationAvailability({...unique,distribution:[{count:2}]}).matches,false);
  assert.deepEqual(textRepresentationAvailability({...unique,keywords:{distribution:[{count:1}]}}),{texts:true,matches:true,'match-chart':true,'match-pie':true,'match-donut':true});
  assert.equal(textRepresentationAvailability({...unique,groupable:false,keywords:{distribution:[{count:1}]}})['match-pie'],false);
  assert.equal(textRepresentationAvailability({...unique,groupable:false}).matches,false);
  assert.equal(textRepresentationAvailability(null).texts,false);
  assert.equal(textRepresentationAvailability({...unique,dictionaryRevision:0,distribution:[{count:2}]}).matches,false);
});
test('una consulta anterior no puede reemplazar una actualización manual más reciente',()=>{const c=createResultsCoordinator(),old=c.start(),latest=c.start();assert.equal(c.current(old),false);assert.equal(c.current(latest),true);c.invalidate();assert.equal(c.current(latest),false);});
test('las representaciones dependen de la escala y los datos preparados, no fabrican compatibilidad',()=>{const multiple=resultRepresentations({type:'checkbox'}),single=resultRepresentations({type:'select'}),numeric=resultRepresentations({type:'number',numeric:{n:3},advanced:{histogram:{},box:{},time:{}}});assert.equal(multiple.find(([id])=>id==='pie')[2],false);assert.equal(single.find(([id])=>id==='pie')[2],true);assert.equal(numeric.find(([id])=>id==='pie')[2],false);assert.equal(numeric.find(([id])=>id==='histogram')[2],true);assert.equal(multiple.find(([id])=>id==='histogram')[2],false);});
