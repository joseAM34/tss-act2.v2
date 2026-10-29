'use strict';
const ReportChartOptions={
  labels:{'text-bars':'Barras de coincidencias','text-pie':'Circular de coincidencias','text-donut':'Anillo de coincidencias',columns:'Columnas verticales',bars:'Barras horizontales',pie:'Circular',donut:'Anillo',heat:'Mapa de calor',grouped:'Barras agrupadas',stacked:'Barras apiladas',polygon:'Polígono de frecuencias',ogive:'Ojiva: frecuencia acumulada',histogram:'Histograma',box:'Caja',line:'Envíos con valor por mes de recepción',area:'Envíos con valor por mes de recepción'},
  variable(v,style){
    if(style==='polygon'||style==='ogive'){
      const bins=v.quality.intervals,points=style==='polygon'?bins.map(b=>[b.midpoint,b.count]):bins.length?[[bins[0].lower,0],...bins.map(b=>[b.upper,b.cumulative])]:[];
      return {animation:false,aria:{enabled:true},tooltip:{trigger:'axis',renderMode:'richText',valueFormatter:value=>Array.isArray(value)?Number(value[0]).toFixed(2)+'; '+value[1]:value},grid:{containLabel:true,left:20,right:20,bottom:65},xAxis:{type:'value',name:v.unit,axisLabel:{formatter:value=>Number(value).toFixed(2)}},yAxis:{type:'value',name:style==='polygon'?'Frecuencia (fi)':'Frecuencia acumulada (Fi)',minInterval:1,min:0},series:[{type:'line',smooth:false,connectNulls:false,data:points}]};
    }
    if(style.startsWith('text-')){const distribution=v.text_counts.distribution.filter(row=>row.count>0);if(!distribution.length)return {animation:false,title:{text:'Sin coincidencias para esta fuente',left:'center',top:'center',textStyle:{fontSize:14}},series:[]};return this.variable({distribution},style.slice(5));}
    if(['histogram','box','line','area'].includes(style)){
      const base={animation:false,aria:{enabled:true},tooltip:{trigger:'axis',renderMode:'richText',...(style==='box'?{valueFormatter:value=>Array.isArray(value)?value.map(n=>Number(n).toFixed(2)).join(', '):Number(value).toFixed(2)}:{})},grid:{containLabel:true,left:20,right:20,bottom:80}};
      if(style==='box')return {...base,xAxis:{type:'category',data:[v.label],axisLabel:{width:120,overflow:'truncate'}},yAxis:{type:'value',name:v.unit},series:[{type:'boxplot',data:[v.advanced.box.values]}]};
      if(style==='histogram')return {...base,xAxis:{type:'category',data:v.advanced.histogram.bins.map(b=>'['+Number(b.lower).toFixed(2)+', '+Number(b.upper).toFixed(2)+(b.closed_upper?']':')')),axisLabel:{rotate:25,width:100,overflow:'truncate'}},yAxis:{type:'value',name:'Envíos',minInterval:1},series:[{type:'bar',barCategoryGap:0,data:v.advanced.histogram.bins.map(b=>b.count)}]};
      return {...base,xAxis:{type:'category',name:'Mes de recepción',data:v.advanced.time.months,axisLabel:{rotate:25}},yAxis:{type:'value',name:'Envíos con valor',minInterval:1},series:[{type:'line',smooth:false,connectNulls:false,...(style==='area'?{areaStyle:{opacity:.2}}:{}),data:v.advanced.time.counts}]};
    }
    const labels=v.numeric?['Mínimo','Mediana','Máximo']:v.distribution.map(o=>o.label),values=v.numeric?['min','median','max'].map(k=>v.numeric[k]):v.distribution.map(o=>o.count);
    const base={animation:false,aria:{enabled:true}};
    if(style==='pie'||style==='donut')return {...base,tooltip:{trigger:'item',renderMode:'richText'},legend:{type:'scroll',bottom:0},series:[{type:'pie',stillShowZeroSum:false,radius:style==='donut'?['35%','62%']:'62%',center:['50%','43%'],label:{formatter:'{b}: {c}'},data:labels.map((name,i)=>({name,value:values[i]}))}]};
    const category={type:'category',data:labels,axisLabel:{rotate:style==='bars'?0:25,width:100,overflow:'truncate'}},numeric={type:'value',name:v.numeric?v.unit:'Envíos',...(v.numeric?{axisLabel:{formatter:value=>Number(value).toFixed(2)}}:{}),minInterval:v.numeric?0:1};
    return {...base,tooltip:{trigger:'axis',renderMode:'richText',...(v.numeric?{valueFormatter:value=>Number(value).toFixed(2)}:{})},grid:{containLabel:true,left:15,right:20,bottom:80},xAxis:style==='bars'?numeric:category,yAxis:style==='bars'?category:numeric,series:[{type:'bar',colorBy:v.numeric?'series':'data',data:values}]};
  },
  advanced(a){
    const base={animation:false,aria:{enabled:true},tooltip:{renderMode:'richText',valueFormatter:value=>Array.isArray(value)?value.map((n,i)=>a.type==='scatter'&&i===2?String(n):Number(n).toFixed(2)).join(', '):Number(value).toFixed(2)}};
    if(a.type==='scatter')return {...base,grid:{left:20,right:25,bottom:75,containLabel:true},xAxis:{type:'value',axisLabel:{formatter:value=>Number(value).toFixed(2)},name:a.x_label+' ('+a.x_unit+')',nameLocation:'middle',nameGap:35},yAxis:{type:'value',axisLabel:{formatter:value=>Number(value).toFixed(2)},name:a.y_label+' ('+a.y_unit+')',nameLocation:'middle',nameGap:40},series:[{type:'scatter',symbolSize:9,dimensions:['X','Y','Frecuencia'],data:a.points}]};
    return {...base,radar:{indicator:a.criteria.map(c=>({name:c.label,min:0,max:100})),radius:'58%',axisName:{width:120,overflow:'truncate'}},series:[{type:'radar',data:[{name:'% acuerdo sustantivo',value:a.criteria.map(c=>c.percentage)}]}]};
  },
  cross(c,style){
    const base={animation:false,aria:{enabled:true},tooltip:{renderMode:'richText'},grid:{left:15,right:20,bottom:85,containLabel:true}};
    const columns=c.column_options.map(o=>o.label),rows=c.row_options.map(o=>o.label);
    if(style==='heat'){
      const cells=c.table.observed.flatMap((row,r)=>row.map((n,col)=>[col,r,n]));
      return {...base,xAxis:{type:'category',data:columns,axisLabel:{rotate:25,width:80,overflow:'truncate'}},yAxis:{type:'category',data:rows,axisLabel:{width:80,overflow:'truncate'}},visualMap:{min:0,max:Math.max(1,...cells.map(cell=>cell[2])),orient:'horizontal',left:'center',bottom:0},series:[{type:'heatmap',data:cells,label:{show:true}}]};
    }
    return {...base,legend:{type:'scroll',bottom:0},xAxis:{type:'category',data:rows,axisLabel:{rotate:25,width:100,overflow:'truncate'}},yAxis:{type:'value',name:'Pares con valor',minInterval:1},series:columns.map((name,i)=>({name,type:'bar',...(style==='stacked'?{stack:'observados'}:{}),data:c.table.observed.map(row=>row[i])}))};
  }
};
if(typeof module!=='undefined')module.exports=ReportChartOptions;
if(typeof window!=='undefined')window.ReportCharts=(main,content,report)=>{
 const charts=[],exports=[];
 main.querySelectorAll('[data-report-variable]').forEach(element=>{
  const v=(content.display_result||content.analysis.result).variables[Number(element.dataset.reportVariable)],style=element.dataset.representation||'columns',chart=echarts.init(element);
  chart.setOption(ReportChartOptions.variable(v,style));charts.push(chart);
  exports.push({element,chart,title:v.label+' · '+ReportChartOptions.labels[style],note:`Base con valor: ${v.answered}; sin respuesta: ${v.missing}; no aplica: ${v.not_applicable}; base aplicable: ${v.applicable_base}. ${v.numeric?'Unidad: '+v.unit+'. ':''}${['line','area'].includes(style)?v.advanced.time.method:style==='histogram'?v.advanced.histogram.method:style==='box'?v.advanced.box.method:'Conteos o resumen preparado; cifras y bases en la tabla.'} ${v.type==='checkbox'?'Selección múltiple: un envío puede aportar varias menciones.':''}`});
 });
 main.querySelectorAll('[data-report-cross]').forEach(element=>{
  const saved=(content.display_crosses||content.crosses)[Number(element.dataset.reportCross)],c=saved.result,style=element.dataset.representation||'heat',chart=echarts.init(element);
  chart.setOption(ReportChartOptions.cross(c,style));charts.push(chart);
  exports.push({element,chart,title:(saved.display_title||`${c.row_label} × ${c.column_label}`)+' · '+ReportChartOptions.labels[style],note:`Conteos observados. Pares: ${c.table.n}; excluidos: ${c.excluded_pairs}; total: ${c.total_submissions}. Chi-cuadrado: ${c.table.status}. Protocolo: ${c.protocol}. Huella del cruce: ${saved.result_hash}. Bases y diagnóstico en la tabla del reporte.`});
 });
 main.querySelectorAll('[data-report-advanced]').forEach(element=>{
  const a=content.advanced_charts[Number(element.dataset.reportAdvanced)],chart=echarts.init(element);chart.setOption(ReportChartOptions.advanced(a));charts.push(chart);
  const bases=a.type==='radar'?a.criteria.map(c=>c.label+': acuerdo '+c.numerator+'/'+c.base+'; No sabe '+c.no_sabe).join('; '):'Pares con valor: '+a.n+'; excluidos: '+a.excluded;
  exports.push({element,chart,title:a.title,note:a.method+' '+bases});
 });
 if(report&&window.ReportDownloads)window.ReportDownloads.attach(main,report,exports);
 const resize=()=>charts.forEach(c=>c.resize());window.addEventListener('resize',resize);
 const dispose=()=>{window.removeEventListener('resize',resize);charts.forEach(c=>c.dispose());};
 dispose.resize=resize;
 return dispose;
};
