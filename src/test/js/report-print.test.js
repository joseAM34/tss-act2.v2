'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {prepareReportPrint}=require('../../main/resources/static/js/report-print.js');
function table(spans){
  const classes=new Set();
  const wrapper={classList:{toggle:(name,enabled)=>enabled?classes.add(name):classes.delete(name)}};
  return {rows:spans.map(row=>({cells:row.map(colSpan=>({colSpan}))})),closest:()=>wrapper,classes};
}
test('solo las tablas de seis o más columnas ocupan páginas horizontales',()=>{
  const small=table([[1,1,1,1,1]]),wide=table([[1,1,1,1,1,1]]);
  prepareReportPrint({querySelectorAll:()=>[small,wide]});
  assert.equal(small.classes.size,0);assert.equal(wide.classes.has('report-print-landscape'),true);
  wide.rows=[{cells:[{colSpan:2}]}];prepareReportPrint({querySelectorAll:()=>[wide]});assert.equal(wide.classes.size,0);
});
test('cuenta encabezados combinados y columnas de filas posteriores',()=>{
  const merged=table([[6],[1,1,1,1,1,1]]),later=table([[1],[1,1,1,1,1,1,1]]);
  prepareReportPrint({querySelectorAll:()=>[merged,later]});
  assert.equal(merged.classes.size,1);assert.equal(later.classes.size,1);
});
