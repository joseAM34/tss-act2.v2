'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const options=require('../../main/resources/static/js/report-charts.js');
const categorical={numeric:null,distribution:[{label:'Sí',count:7},{label:'No sabe',count:0},{label:'No',count:3}]};
test('una plantilla sin menciones en su nueva fuente muestra ausencia sin fabricar sectores',()=>{
  const v={text_counts:{distribution:[{label:'ia',count:0}]}};
  for(const style of ['text-bars','text-pie','text-donut']){const chart=options.variable(v,style);assert.deepEqual(chart.series,[]);assert.match(chart.title.text,/Sin coincidencias/);}
});
test('coincidencias de reportes usan sus menciones congeladas en barras, circular y anillo',()=>{
  const v={distribution:[{label:'Con respuesta',count:53}],text_counts:{distribution:[{label:'inteligencia artificial',count:35},{label:'sin menciones',count:0},{label:'ciberseguridad',count:14}]}};
  assert.deepEqual(options.variable(v,'text-bars').series[0].data,[35,14]);
  for(const style of ['text-pie','text-donut'])assert.deepEqual(options.variable(v,style).series[0].data,[{name:'inteligencia artificial',value:35},{name:'ciberseguridad',value:14}]);
});
test('columnas y barras conservan categorías, ceros y conteos en su orientación',()=>{
  const columns=options.variable(categorical,'columns'),bars=options.variable(categorical,'bars');
  assert.deepEqual(columns.xAxis.data,['Sí','No sabe','No']);assert.deepEqual(bars.yAxis.data,columns.xAxis.data);
  assert.deepEqual(columns.series[0].data,[7,0,3]);assert.deepEqual(bars.series[0].data,[7,0,3]);assert.equal(bars.xAxis.type,'value');
});
test('circular y anillo representan conteos sin fabricar porcentajes',()=>{
  const pie=options.variable(categorical,'pie'),donut=options.variable(categorical,'donut');
  assert.deepEqual(pie.series[0].data,[{name:'Sí',value:7},{name:'No sabe',value:0},{name:'No',value:3}]);
  assert.deepEqual(donut.series[0].data,pie.series[0].data);assert(Array.isArray(donut.series[0].radius));assert(!pie.series[0].label.formatter.includes('{d}'));
});
test('resumen numérico usa valores preparados y conserva el cero',()=>{
  const chart=options.variable({numeric:{min:0,median:6,max:30},unit:'meses'},'bars');
  assert.deepEqual(chart.series[0].data,[0,6,30]);assert.equal(chart.xAxis.name,'meses');assert.deepEqual(chart.yAxis.data,['Mínimo','Mediana','Máximo']);
});
test('cruces agrupados y apilados usan los mismos conteos absolutos que el mapa de calor',()=>{
  const source={row_options:[{label:'A'},{label:'B'}],column_options:[{label:'X'},{label:'Y'}],table:{observed:[[2,0],[3,5]]}};
  const grouped=options.cross(source,'grouped'),stacked=options.cross(source,'stacked'),heat=options.cross(source,'heat');
  assert.deepEqual(grouped.series.map(s=>s.data),[[2,3],[0,5]]);assert.deepEqual(stacked.series.map(s=>s.data),grouped.series.map(s=>s.data));
  assert(grouped.series.every(s=>!s.stack));assert(stacked.series.every(s=>s.stack==='observados'));assert.deepEqual(heat.series[0].data,[[0,0,2],[1,0,0],[0,1,3],[1,1,5]]);
});
test('histograma y caja representan los intervalos y cuartiles calculados por Java',()=>{
  const variable={label:'Meses',unit:'meses',advanced:{histogram:{bins:[{lower:0,upper:2,closed_upper:false,count:3},{lower:2,upper:4,closed_upper:true,count:1}]},box:{values:[0,1,2,3,4]}}};
  const histogram=options.variable(variable,'histogram'),box=options.variable(variable,'box');
  assert.deepEqual(histogram.series[0].data,[3,1]);assert.deepEqual(histogram.xAxis.data,['[0.00, 2.00)','[2.00, 4.00]']);assert.deepEqual(box.series[0].data,[[0,1,2,3,4]]);assert.equal(box.series[0].type,'boxplot');
});
test('línea y área conservan meses y ceros, sin suavizar ni proyectar',()=>{
  const variable={advanced:{time:{months:['2026-01','2026-02','2026-03'],counts:[0,2,0]}}};
  const line=options.variable(variable,'line'),area=options.variable(variable,'area');assert.deepEqual(line.series[0].data,[0,2,0]);assert.deepEqual(area.series[0].data,line.series[0].data);assert.equal(line.series[0].smooth,false);assert(area.series[0].areaStyle);assert.equal(line.yAxis.name,'Envíos con valor');
});
test('dispersión mantiene coordenadas agregadas y radar conserva cero sobre ejes 0 a 100',()=>{
  const scatter=options.advanced({type:'scatter',x_label:'Meses',x_unit:'meses',y_label:'Años',y_unit:'años',points:[[0,1,2],[3,2,1]]});assert.deepEqual(scatter.series[0].data,[[0,1,2],[3,2,1]]);assert.equal(scatter.series[0].type,'scatter');
  const radar=options.advanced({type:'radar',criteria:[{label:'A',percentage:0},{label:'B',percentage:50},{label:'C',percentage:100}]});assert.deepEqual(radar.series[0].data[0].value,[0,50,100]);assert(radar.radar.indicator.every(axis=>axis.min===0&&axis.max===100));
});
