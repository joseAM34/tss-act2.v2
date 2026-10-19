'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const {fetchSurveyBackup} = require('../../main/resources/static/js/survey-management.js');
const id = '3d2c6c90-1356-4d1b-ae72-5825c314c31e';
function response(overrides={}) {
  return {ok:true,headers:new Headers({'Content-Type':'application/zip','X-Survey-Backup-Id':id,'Content-Disposition':'attachment; filename="respaldo.zip"'}),blob:async()=>new Blob(['zip']),...overrides};
}
test('la referencia para eliminar solo se entrega después de recibir todo el archivo', async()=>{
  let release;const pending=new Promise(resolve=>{release=resolve;});let completed=false;
  const result=fetchSurveyBackup('/backup',{csrf:'token'},async(url,options)=>{
    assert.equal(options.method,'POST');assert.equal(options.credentials,'same-origin');assert.equal(options.cache,'no-store');
    return response({blob:()=>pending});
  }).then(value=>{completed=true;return value;});
  await new Promise(resolve=>setImmediate(resolve));assert.equal(completed,false);
  release(new Blob(['zip']));assert.equal((await result).id,id);
});
test('rechaza fallos de red, descarga incompleta y una redirección al login',async()=>{
  await assert.rejects(fetchSurveyBackup('/backup',{},async()=>{throw Error('network');}));
  await assert.rejects(fetchSurveyBackup('/backup',{},async()=>response({blob:async()=>{throw Error('truncated');}})));
  await assert.rejects(fetchSurveyBackup('/backup',{},async()=>response({headers:new Headers({'Content-Type':'text/html'})})));
});
test('rechaza ZIP vacíos o sin referencia válida y conserva un nombre de archivo seguro',async()=>{
  await assert.rejects(fetchSurveyBackup('/backup',{},async()=>response({blob:async()=>new Blob([])})));
  await assert.rejects(fetchSurveyBackup('/backup',{},async()=>response({headers:new Headers({'Content-Type':'application/zip','X-Survey-Backup-Id':'invalid'})})));
  const result=await fetchSurveyBackup('/backup',{},async()=>response({headers:new Headers({'Content-Type':'application/zip','X-Survey-Backup-Id':id,'Content-Disposition':'attachment; filename="../fuente.zip"'})}));
  assert.equal(result.filename,'.._fuente.zip');assert.equal(result.id,id);
});
