'use strict';
(() => {
  const $ = id => document.getElementById(id);
  const make = (tag, text, className) => {
    const node = document.createElement(tag);
    if (text !== undefined) node.textContent = text;
    if (className) node.className = className;
    return node;
  };
  // Prepared fixtures only. This prototype does not infer statistical results from real responses.
  const questions = [
    {id:'empleo',label:'Situación laboral',group:'Perfil laboral',type:'category',options:['Trabajando','Sin trabajo','Emprendimiento'],counts:[18,8,4],percent:['60,00','26,67','13,33']},
    {id:'interes',label:'Interés en posgrado',group:'Formación y financiamiento',type:'category',options:['Sí','No'],counts:[24,6],percent:['80,00','20,00']},
    {id:'financia',label:'Financiamiento previsto',group:'Formación y financiamiento',type:'category',options:['Recursos propios','Beca','Empresa'],counts:[18,9,3],percent:['60,00','30,00','10,00']},
    {id:'meses',label:'Duración del desempleo',group:'Tiempos',type:'numeric'}
  ];
  const matrices = {'empleo|interes':[[16,2],[5,3],[3,1]],'empleo|financia':[[10,6,2],[6,2,0],[2,1,1]],'interes|financia':[[15,7,2],[3,2,1]]};
  const byId = id => questions.find(q => q.id === id);
  const saved = [{ids:['empleo','interes'],name:'Empleo e interés en posgrado',representations:['table','heat'],included:false,comment:''}];
  const selected = [];
  let activeQuestion='empleo';
  let editingCross=null;
  let previewQuestion=null,previewCross=null,currentScreen='';
  const sampleAvailable = () => $('date-from').value === '2026-10-01' && $('date-until').value === '2026-10-15';
  const title = () => $('report-title').value.trim() || 'Resultados de titulados';
  const pairName = () => selected.length === 2 ? selected.map(id => byId(id).label).join(' y ') : '';
  const questionRepresentations = Object.fromEntries(questions.map(q=>[q.id,['table','columns']]));
  let crossRepresentations=['table','heat'];
  function representationPicker(items,values,onChange) {
    const root=make('div',undefined,'representation-picker');
    const icons={table:'▦',columns:'▥',bars:'☰',pie:'◕',donut:'◎',heat:'▩',grouped:'▥',stacked:'▤',line:'⌁',area:'◩',histogram:'▥',box:'⊞',scatter:'⁙',radar:'⬡'};
    for(const group of [...new Set(items.map(item=>item[3]))]) {
      const field=make('fieldset'),legend=make('legend',group);field.append(legend);
      const buttons=make('div',undefined,'representation-buttons');
      items.filter(item=>item[3]===group).forEach(([value,label,reason])=>{
        const name=label.replace(/^[▥☰◕◎▦]\s*/,''),button=make('button',undefined,'representation-button');button.type='button';button.dataset.representation=value;button.disabled=!!reason;
        const icon=make('span',icons[value]||'▥','representation-icon');icon.setAttribute('aria-hidden','true');button.append(icon,make('span',name,'representation-name'));
        button.setAttribute('aria-pressed',String(values.includes(value)));button.title=reason||label;
        button.addEventListener('click',()=>{const i=values.indexOf(value);if(i<0)values.push(value);else values.splice(i,1);button.setAttribute('aria-pressed',String(values.includes(value)));onChange();});
        const option=make('div',undefined,'representation-option');option.append(button);buttons.append(option);
      });field.append(buttons);root.append(field);
      const unavailable=items.filter(item=>item[3]===group&&item[2]);
      if(unavailable.length){const details=make('details',undefined,'representation-reasons'),summary=make('summary','¿Por qué hay opciones en gris?');details.append(summary);unavailable.forEach(([,label,reason])=>details.append(make('p',label.replace(/^[▥☰◕◎▦]\s*/,'')+': '+reason)));field.append(details);}
    }
    root.append(make('p','Activa varias opciones a la vez. Resaltado: seleccionado; sin resaltar: disponible; gris: no disponible.','help'));
    return root;
  }
  function renderCrossPicker() {
    $('cross-representations').replaceChildren(representationPicker([
      ['table','Tabla',null,'Datos'],['heat','Mapa de calor',null,'Comparaciones'],['grouped','Barras agrupadas',null,'Comparaciones'],['stacked','Barras apiladas',null,'Comparaciones'],
      ['radar','Radar','Requiere varios criterios de escala compatible.','Otros'],['scatter','Dispersi\u00f3n','Requiere dos variables num\u00e9ricas.','Otros']
    ],crossRepresentations,renderCrossPreview));
  }
  function table(caption,headers,rows) {
    const scroll=make('div',undefined,'table-scroll'), t=make('table');t.append(make('caption',caption));
    const head=make('thead'),tr=make('tr');headers.forEach(h=>{const th=make('th',h);th.scope='col';tr.append(th);});head.append(tr);t.append(head);
    const body=make('tbody');for(const row of rows){const line=make('tr');row.forEach((v,i)=>{const cell=make(i===0?'th':'td',String(v));if(i===0)cell.scope='row';line.append(cell);});body.append(line);}t.append(body);scroll.append(t);return scroll;
  }
  function chartBars(q,columns) {
    const numeric=q.type==='numeric',scale=numeric?36:30;
    if(numeric)q={...q,options:['Mínimo','Mediana','Máximo'],counts:[0,6,36]};
    const chart=make('div',undefined,columns?'columns chart':'chart');chart.setAttribute('role','img');chart.setAttribute('aria-label',`${q.label}: ${numeric?'resumen ficticio en meses':'conteos ficticios'}; cifras equivalentes en la tabla.`);
    q.options.forEach((label,i)=>{
      if(columns){const col=make('div',undefined,'column');col.append(make('span',String(q.counts[i])));const fill=make('div',undefined,'fill');fill.style.height=(q.counts[i]/scale*125)+'px';col.append(fill,make('small',label));chart.append(col);}
      else {const row=make('div',undefined,'bar-row'),track=make('div',undefined,'track'),fill=make('div',undefined,'fill');fill.style.width=q.counts[i]/scale*100+'%';track.append(fill);row.append(make('span',label,'bar-label'),track,make('strong',String(q.counts[i])));chart.append(row);}
    });return chart;
  }
  function descriptive(q) {
    const displayTitle=$('graph-title-'+q.id)?.value.trim()||q.label;
    const section=make('section');section.append(make('h3',displayTitle),make('p','Base con valor: 30 envíos ficticios · Sin respuesta: 0 · No aplica: 0'));
    const numeric=q.type==='numeric';
    const t=numeric ? table('Resumen numérico ficticio · meses',['Medida','Valor'],[['Base numérica',30],['Mínimo',0],['Mediana',6],['Media','8,2'],['Máximo',36],['Desviación estándar muestral','9,1']]) : table('Distribución ficticia · porcentajes sobre 30 envíos con valor',['Opción','Envíos','%'],q.options.map((label,i)=>[label,q.counts[i],q.percent[i]]));
    const choices=questionRepresentations[q.id],graphs=choices.filter(value=>value!=='table');
    const hasChart=$('include-charts').checked&&graphs.length>0;
    if(choices.includes('table')&&$('include-tables').checked)section.append(t);
    else if(hasChart){const details=make('details',undefined,'plain-details');details.append(make('summary','Consultar tabla equivalente'),t);section.append(details);}
    if(hasChart)graphs.forEach(style=>{section.append(make('h4',({columns:'Columnas verticales',bars:'Barras horizontales',pie:'Circular',donut:'Anillo'})[style]),style==='pie'||style==='donut'?chartCircle(q,style==='donut'):chartBars(q,style==='columns'));});
    if(!hasChart&&!(choices.includes('table')&&$('include-tables').checked))section.append(make('p','Sin representaciones activas. Activa una tabla o un gr\u00e1fico.','notice'));
    if(numeric)section.append(make('p','Gráfico de mínimo, mediana y máximo en meses, no una distribución de frecuencias ni un pronóstico. Cajas e histogramas quedan para otra etapa.'));
    appendComment(section,$('question-comment-'+q.id)?.value,'Comentario de esta pregunta');
    return section;
  }
  function appendComment(section,text,label) {
    if(!text?.trim())return;const note=make('div',undefined,'element-comment');note.append(make('strong',label),make('p',text,'comment'));section.append(note);
  }
  function chartCircle(q,donut) {
    const chart=make('div',undefined,'chart circle-layout'),disc=make('div',undefined,'circle-chart'+(donut?' donut':'')),colors=['#286d8b','#ad6524','#66548e'];
    chart.setAttribute('role','img');chart.setAttribute('aria-label',`${donut?'Anillo':'Circular'} de ${q.label}; 30 envíos con una sola categoría por envío. Valores en la tabla y leyenda.`);
    let start=0;const slices=q.counts.map((count,i)=>{const end=start+count/30*360;const part=`${colors[i]} ${start}deg ${end}deg`;start=end;return part;});disc.style.background='conic-gradient('+slices.join(',')+')';
    if(donut)disc.append(make('span','30 envíos','circle-center'));
    const legend=make('div',undefined,'circle-legend');q.options.forEach((name,i)=>{const row=make('p');const swatch=make('span',undefined,'swatch');swatch.style.backgroundColor=colors[i];row.append(swatch,make('span',`${name}: ${q.counts[i]} (${q.percent[i]} %)`));legend.append(row);});chart.append(disc,legend);return chart;
  }
  function crossMatrix(ids) {
    const direct=matrices[ids.join('|')];if(direct)return direct;
    const reverse=matrices[[...ids].reverse().join('|')];return reverse[0].map((_,i)=>reverse.map(row=>row[i]));
  }
  function comparison(c) {
    if(c.representations){
      const section=make('section');section.append(make('h3',c.name));
      if(!c.representations.length)section.append(make('p','Sin representaciones activas.','notice'));
      c.representations.forEach(style=>{const part=comparison({...c,representations:null,style,comment:''});part.querySelector('h3').remove();part.querySelector('.notice')?.remove();section.append(part);});
      section.append(make('p','Chi-cuadrado no ejecutado: independencia sin documentar y frecuencias esperadas insuficientes.','notice'));appendComment(section,c.comment,'Comentario de esta comparaci\u00f3n');return section;
    }
    const row=byId(c.ids[0]),col=byId(c.ids[1]),matrix=crossMatrix(c.ids),section=make('section');
    section.append(make('h3',c.name),make('p',`${row.label} × ${col.label} · 30 pares con valor · 0 excluidos`));
    const t=table('Conteos observados de la muestra ficticia',['Filas / columnas',...col.options],row.options.map((label,i)=>[label,...matrix[i]]));section.append(t);
    if(c.style==='heat') {
      t.querySelector('caption').textContent='Mapa de calor de conteos ficticios · más intensidad = más envíos';
      t.querySelectorAll('tbody tr').forEach((tr,r)=>tr.querySelectorAll('td').forEach((cell,k)=>{const n=matrix[r][k];cell.className='heat-cell';cell.style.backgroundColor=`rgba(24,121,132,${0.07+n/24*0.55})`;cell.style.color='#17384d';}));
    }
    if(c.style==='grouped'||c.style==='stacked') {
      const chart=make('div',undefined,'chart cross-bars'),colors=['#286d8b','#ad6524','#66548e'];
      chart.setAttribute('role','img');chart.setAttribute('aria-label','Barras de conteos observados. Categorías y valores disponibles en la tabla anterior.');
      chart.append(make('p',c.style==='grouped'?'Barras agrupadas · conteos de envíos':'Barras apiladas · conteos de envíos','series-key'));
      col.options.forEach((label,i)=>{const key=make('span',`${i?' · ':''}${label}`);key.style.color=colors[i];chart.append(key);});
      matrix.forEach((values,r)=>{
        chart.append(make('p',row.options[r],'group-label'));
        if(c.style==='stacked'){const track=make('div',undefined,'segments');values.forEach((n,i)=>{const segment=make('div',undefined,'segment');segment.style.width=n/30*100+'%';segment.style.backgroundColor=colors[i];segment.title=`${col.options[i]}: ${n} envíos`;track.append(segment);});chart.append(track);}
        else values.forEach((n,i)=>{const line=make('div',undefined,'bar-row'),track=make('div',undefined,'track'),fill=make('div',undefined,'fill');fill.style.width=n/30*100+'%';fill.style.backgroundColor=colors[i];track.append(fill);line.append(make('span',col.options[i],'bar-label'),track,make('strong',String(n)));chart.append(line);});
      });section.append(chart);
    }
    section.append(make('p','Chi-cuadrado no ejecutado en esta muestra: independencia sin documentar y frecuencias esperadas insuficientes. No hay p, decisión ni V de Cramér. La descripción sigue disponible.','notice'));
    appendComment(section,c.comment,'Comentario de esta comparación');
    return section;
  }
  function renderPreview() {
    $('generation-error').hidden=true;
    const available=sampleAvailable(),from=$('date-from').value,until=$('date-until').value,invalid=from&&until&&from>until;
    $('selection-state').textContent=invalid?'Revisa las fechas: el inicio debe ser anterior o igual al final.':available?'Muestra disponible: 30 envíos sintéticos.':'Sin muestra preparada para este período. No significa que un estudio real tenga cero respuestas.';
    $('generate').disabled=!available||invalid;
    renderFocused();
    questions.forEach(q=>{
      const names={table:'Tabla',columns:'Columnas',bars:'Barras',pie:'Circular',donut:'Anillo'};
      const info=document.querySelector('[data-preview-question="'+q.id+'"]').closest('.question-row').querySelector('.help');
      info.textContent=questionRepresentations[q.id].map(value=>names[value]).join(' · ')||'Sin representaciones seleccionadas';
    });
    const qs=questions.filter(q=>$('include-'+q.id).checked),crosses=saved.filter(c=>c.included);
    $('preview-summary').textContent=available?`${qs.length} preguntas · ${crosses.length} comparaciones · ${$('include-charts').checked?'Gráficos activados':'Tablas sin gráficos descriptivos'} · ${$('save-template').checked?'Con plantilla reutilizable':'Sin guardar plantilla'}`:'Selecciona el período de demostración para previsualizar los datos.';
    const paper=$('report-paper');paper.replaceChildren(make('p','DATOS SINTÉTICOS · VISTA PREVIA DE DEMOSTRACIÓN','eyebrow'),make('h2',title()));
    paper.append(make('p',`Encuesta de titulados ficticia · v1 · Origen SINTETICO · Recepción ${from||'sin inicio'} a ${until||'sin final'} · America/La_Paz`,'report-meta'));
    if(!available||invalid){paper.append(make('p','No se dibujan gráficos ni se rellenan bases con ceros para un período sin fixture.','notice'));return;}
    if($('introduction').value.trim())paper.append(make('p',$('introduction').value,'comment'));
    if($('include-context').checked){const context=make('section');context.append(make('h3','Contexto y fuentes'),make('p','30 envíos de demostración. Unidad: un envío; no se garantiza una persona o empresa independiente por fila. Los porcentajes describen esta muestra ficticia, no una población.'),make('p','Corte ficticio: 15/10/2026, 18:00 · America/La_Paz. Este prototipo no congela datos del sistema.'));paper.append(context);}
    if(qs.length&&!$('include-tables').checked&&!$('include-charts').checked)paper.append(make('p','Activa tablas o gráficos para previsualizar las preguntas seleccionadas.','notice'));
    else qs.forEach(q=>paper.append(descriptive(q)));
    crosses.forEach(c=>paper.append(comparison(c)));
    if($('comments').value.trim()){const section=make('section');section.append(make('h3','Comentarios administrativos'),make('p',$('comments').value,'comment'));paper.append(section);}
    paper.append(make('p','Referencias de demostración · Sin aprobación institucional. La vista final conservará fuente, protocolo y huellas reales al conectarse al backend.','reference'));
  }
  function renderSaved() {
    const container=$('saved-crosses');container.replaceChildren();
    saved.forEach((c,i)=>{const card=make('div',undefined,'saved-cross'),label=make('label',undefined,'check'),input=make('input');input.type='checkbox';input.checked=c.included;input.id='saved-'+i;input.addEventListener('change',()=>{c.included=input.checked;renderPreview();});label.append(input,make('span','Incluir'));const view=make('button',c.name,'list-title');view.type='button';view.dataset.previewCross=String(i);view.setAttribute('aria-pressed','false');view.addEventListener('click',()=>{previewCross=previewCross===i?null:i;renderListPreviews();});card.append(label,view,make('small',c.ids.map(id=>byId(id).label).join(' × ')));container.append(card);});
    renderListPreviews();
  }
  function renderCrossSelection() {
    document.querySelectorAll('[data-cross-id]').forEach(input=>{
      const id=input.dataset.crossId,q=byId(id),chosen=selected.includes(id),numeric=q.type==='numeric',full=selected.length===2&&!chosen;
      input.checked=chosen;input.disabled=numeric||full;const card=input.closest('.candidate');card.className='candidate '+(chosen?'selected':numeric||full?'unavailable':selected.length?'compatible':'');
      card.querySelector('.compatibility').textContent=chosen?'Seleccionada':numeric?'No disponible en esta entrega: comparación numérica prevista.':full?'Quita una selección para elegir esta pregunta.':selected.length?'Compatible: categorías de selección única del mismo conjunto.':'Categorías de selección única; selecciona una pregunta base.';
    });
    $('cross-selection').textContent=selected.length?`${selected.length}/2 seleccionadas · ${selected.map(id=>byId(id).label).join(' → ')}`:'Selecciona la primera pregunta.';
    $('add-cross').disabled=selected.length!==2||!sampleAvailable();
    renderCrossPreview();
  }
  function renderCrossPreview() {
    const preview=$('cross-preview');preview.replaceChildren();
    if(!sampleAvailable()){preview.append(make('p','No hay muestra preparada para este período.'));return;}
    if(selected.length!==2){preview.append(make('p','Selecciona dos preguntas para ver una tabla de la muestra ficticia.'));return;}
    preview.append(comparison({ids:[...selected],name:$('cross-name').value.trim()||pairName(),representations:[...crossRepresentations],comment:$('cross-comment').value}));
  }
  questions.forEach(q=>{
    const card=make('div',undefined,'question-row'),label=make('label',undefined,'check'),input=make('input');input.type='checkbox';input.checked=true;input.id='include-'+q.id;input.addEventListener('change',renderPreview);label.append(input,make('span','Incluir'));input.setAttribute('aria-label','Incluir en el reporte: '+q.label);
    const choices=[['columns',q.type==='numeric'?'▥ Columnas del resumen (meses)':'▥ Columnas verticales'],['bars',q.type==='numeric'?'☰ Barras del resumen (meses)':'☰ Barras horizontales'],['pie','◕ Circular',q.type==='numeric'?'necesita categorías exclusivas':null],['donut','◎ Anillo',q.type==='numeric'?'necesita categorías exclusivas':null],['table','▦ Solo tabla'],['line','Líneas','requiere una serie temporal ordenada'],['area','Áreas','requiere una serie temporal ordenada'],['histogram','Histograma','requiere observaciones numéricas individuales'],['box','Caja','requiere cuartiles y reglas definidas'],['scatter','Dispersión','requiere dos variables numéricas'],['radar','Radar','requiere varios criterios de escala compatible']];
    const select=representationPicker(choices.map(([value,label,reason])=>[value,label.replace('Solo tabla','Tabla'),reason,value==='table'?'Datos':['columns','bars'].includes(value)?'Comparar cantidades':['pie','donut'].includes(value)?'Partes de un total':'Otros']),questionRepresentations[q.id],renderPreview);
    const view=make('button',q.label,'list-title');view.type='button';view.dataset.previewQuestion=q.id;view.setAttribute('aria-pressed','false');view.addEventListener('click',()=>{previewQuestion=previewQuestion===q.id?null:q.id;renderListPreviews();});
    card.append(label,view,make('p',q.type==='numeric'?'Columnas del resumen en meses y tabla':'Columnas verticales y tabla','help'));$('report-questions').append(card);
    const controls=make('div');controls.dataset.questionEditor=q.id;controls.hidden=true;
    const titleLabel=make('label','Título de presentación');titleLabel.htmlFor='graph-title-'+q.id;
    const displayTitle=make('input');displayTitle.id=titleLabel.htmlFor;displayTitle.value=q.label;displayTitle.maxLength=160;displayTitle.addEventListener('input',renderPreview);
    const styleLabel=make('label','Representación');
    const commentDetails=make('details',undefined,'plain-details');commentDetails.append(make('summary','Añadir comentario a esta pregunta (opcional)'));
    const commentLabel=make('label','Comentario de esta pregunta');commentLabel.htmlFor='question-comment-'+q.id;const comment=make('textarea');comment.id=commentLabel.htmlFor;comment.rows=3;comment.maxLength=1000;comment.placeholder='Sin comentario por defecto';comment.addEventListener('input',renderPreview);commentDetails.append(commentLabel,comment,make('p','Este texto aparece junto a la pregunta, separado de las cifras.','help'));
    controls.append(titleLabel,displayTitle,styleLabel,select,make('p','Gráficos establecidos compatibles con esta pregunta. Las opciones deshabilitadas indican qué datos faltan. No se usan gráficos 3D.','help'),commentDetails,make('p','Pregunta original: '+q.label,'help'));$('question-edit-controls').append(controls);
  });
  [...new Set(questions.map(q=>q.group))].forEach(group=>{
    const details=make('details',undefined,'question-group');details.open=true;details.append(make('summary',group));
    questions.filter(q=>q.group===group).forEach(q=>{
      const card=make('div',undefined,'candidate'),label=make('label',undefined,'check'),input=make('input');input.type='checkbox';input.dataset.crossId=q.id;input.id='cross-'+q.id;label.append(input,make('span',q.label));
      card.append(label,make('small',q.type==='numeric'?'▦ Tabla · Cajas/dispersión pendientes':'▦ Tabla · ▩ Calor · ▥ Barras'),make('small',undefined,'compatibility'));details.append(card);
      input.addEventListener('change',()=>{if(input.checked&&selected.length<2)selected.push(q.id);else if(!input.checked)selected.splice(selected.indexOf(q.id),1);$('cross-name').value=pairName();renderCrossSelection();});
    });$('cross-groups').append(details);
  });
  $('cross-name').addEventListener('input',renderCrossPreview);$('cross-comment').addEventListener('input',renderCrossPreview);
  $('add-cross').addEventListener('click',()=>{
    if(selected.length!==2||!sampleAvailable())return;
    const existing=editingCross===null?saved.find(c=>c.ids.join('|')===selected.join('|')):saved[editingCross];
    if(existing){existing.ids=[...selected];existing.name=$('cross-name').value.trim()||pairName();existing.representations=[...crossRepresentations];existing.comment=$('cross-comment').value;existing.included=true;previewCross=saved.indexOf(existing);}
    else {saved.push({ids:[...selected],name:$('cross-name').value.trim()||pairName(),representations:[...crossRepresentations],included:true,comment:$('cross-comment').value});previewCross=saved.length-1;}
    renderSaved();renderPreview();$('cross-selection').textContent='Comparación añadida al reporte de demostración. Puedes editar su representación en la lista superior.';
    location.hash='cruces';
  });
  for(const id of ['report-title','introduction','comments'])$(id).addEventListener('input',renderPreview);
  for(const id of ['date-from','date-until','include-context','include-tables','include-charts','save-template'])$(id).addEventListener('change',()=>{
    $('template-fields').hidden=!$('save-template').checked;

    $('generation-error').hidden=true;renderPreview();renderCrossSelection();
  });
  $('report-form').addEventListener('submit',event=>{
    event.preventDefault();if(document.querySelector('[data-screen="reporte"]').hidden)return;
    const qs=questions.filter(q=>$('include-'+q.id).checked),crosses=saved.filter(c=>c.included);
    let error='';if(!sampleAvailable())error='Selecciona el período de demostración disponible.';
    else if(!qs.length&&!crosses.length)error='Incluye al menos una pregunta o comparación con resultados.';
    else if(qs.some(q=>!questionRepresentations[q.id].some(value=>value==='table'?$('include-tables').checked:$('include-charts').checked)))error='Activa al menos una representación para cada pregunta incluida, o quita su casilla de inclusión.';
    else if(crosses.some(c=>!c.representations.length))error='Activa al menos una representación para cada comparación incluida, o quita su casilla de inclusión.';
    $('generation-error').hidden=!error;$('generation-error').textContent=error;if(error){$('generation-error').tabIndex=-1;$('generation-error').focus();return;}
    const copy=$('report-paper').cloneNode(true);copy.removeAttribute('id');$('generated-content').replaceChildren(copy);
    $('generated-template').textContent=$('save-template').checked?`En el flujo real se guardaría la configuración como «${$('template-name').value.trim()||title()}», sin datos ni comentarios del reporte.`:'No se guardaría una plantilla: solo este reporte con su configuración.';
    $('generated-dialog').showModal();
  });
  $('close-dialog').addEventListener('click',()=>$('generated-dialog').close());
  function renderFocused() {
    $('editing-question').textContent=byId(activeQuestion).label;
    $('question-preview').replaceChildren(sampleAvailable()?descriptive(byId(activeQuestion)):make('p','No hay muestra preparada para este período.'));
    const presentation=$('presentation-preview');presentation.replaceChildren(make('h2',title()));
    if($('include-context').checked)presentation.append(make('h3','Contexto y fuentes'),make('p',sampleAvailable()?'Titulados · v1 · 30 envíos sintéticos. Corte ficticio: 15/10/2026, 18:00 · America/La_Paz.':'Sin muestra preparada para el período seleccionado.'));
    presentation.append(make('h3','Introducción'),make('p',$('introduction').value||'Sin introducción.','comment'),make('h3','Comentarios administrativos'),make('p',$('comments').value||'Sin comentarios.','comment'));
    renderListPreviews();
  }
  function renderListPreviews() {
    document.querySelectorAll('[data-preview-question]').forEach(button=>{const active=button.dataset.previewQuestion===previewQuestion;button.setAttribute('aria-pressed',String(active));button.closest('.question-row').classList.toggle('preview-selected',active);});
    document.querySelectorAll('[data-preview-cross]').forEach(button=>{const active=Number(button.dataset.previewCross)===previewCross;button.setAttribute('aria-pressed',String(active));button.closest('.saved-cross').classList.toggle('preview-selected',active);});
    const question=$('question-list-preview'),cross=$('cross-list-preview');question.replaceChildren();cross.replaceChildren();
    question.hidden=previewQuestion===null;cross.hidden=previewCross===null;
    $('question-preview-hint').hidden=previewQuestion!==null;$('cross-preview-hint').hidden=previewCross!==null;
    const qedit=$('edit-question-selection'),cedit=$('edit-cross-selection');qedit.hidden=previewQuestion===null;cedit.hidden=previewCross===null;
    if(previewQuestion!==null){qedit.href='#pregunta/'+previewQuestion;question.append(sampleAvailable()?descriptive(byId(previewQuestion)):make('p','Sin muestra preparada para este período.'));}
    if(previewCross!==null){cedit.href='#cruce/'+previewCross;cross.append(sampleAvailable()?comparison(saved[previewCross]):make('p','Sin muestra preparada para este período.'));}
  }
  function route() {
    const [page='reporte',id]=(location.hash.slice(1)||'reporte').split('/');
    const pages={reporte:['Generar reporte','Configura los datos. A la derecha ves el reporte completo.','#reporte'],personalizar:['Personalizar reporte','Elige un apartado. Cada editor tiene su propia vista previa.','#reporte'],preguntas:['Preguntas y gráficos','Elige una pregunta para configurar solo su representación.','#personalizar'],pregunta:['Configurar gráfico','A la derecha aparece únicamente la pregunta que estás editando.','#preguntas'],cruces:['Comparaciones del reporte','Incluye comparaciones guardadas o crea una nueva.','#personalizar'],cruce:['Crear comparación','Selecciona preguntas a la izquierda y revisa solo este cruce a la derecha.','#cruces'],presentacion:['Presentación y comentarios','Edita los textos y revisa cómo aparecerán.','#personalizar']};
    if(!pages[page]||(page==='pregunta'&&!byId(id))||(page==='cruce'&&id!=='nuevo'&&!saved[Number(id)])){location.hash='reporte';return;}
    if(page==='pregunta')activeQuestion=id;
    if(page==='preguntas')previewQuestion=currentScreen==='pregunta'?activeQuestion:null;
    if(page==='cruces'){if(currentScreen!=='cruce')previewCross=null;else if(editingCross!==null)previewCross=editingCross;}
    if(page==='cruce'){
      editingCross=id==='nuevo'?null:Number(id);const c=editingCross===null?null:saved[editingCross];
      selected.splice(0,selected.length,...(c?c.ids:[]));$('cross-name').value=c?c.name:'';crossRepresentations=c?[...c.representations]:['table','heat'];renderCrossPicker();$('cross-comment').value=c?.comment||'';$('add-cross').textContent=c?'Guardar comparación':'Añadir al reporte';
    }
    document.querySelectorAll('[data-screen]').forEach(s=>{s.hidden=s.dataset.screen!==page;});
    document.querySelectorAll('[data-question-editor]').forEach(s=>{s.hidden=s.dataset.questionEditor!==activeQuestion;});
    $('page-title').textContent=page==='pregunta'?'Gráfico: '+byId(id).label:page==='cruce'&&editingCross!==null?'Editar comparación':pages[page][0];
    $('page-description').textContent=pages[page][1];$('route-crumb').textContent=page==='reporte'?'': '› '+$('page-title').textContent;
    $('back-screen').hidden=page==='reporte';$('back-screen').href=pages[page][2];
    $('back-screen').textContent=page==='personalizar'?'← Volver al reporte':page==='pregunta'?'← Preguntas y gráficos':page==='cruce'?'← Comparaciones':'← Personalizar';
    currentScreen=page;renderPreview();renderCrossSelection();window.scrollTo(0,0);$('page-title').focus({preventScroll:true});
  }
  window.addEventListener('hashchange',route);
  for(const [screen,label] of [['pregunta','Vista previa de la pregunta'],['cruce','Configuración y vista previa de la comparación']]){
    const panel=document.querySelector('[data-screen='+screen+'] .editor-layout>.preview-column');
    panel.tabIndex=0;panel.setAttribute('role','region');panel.setAttribute('aria-label',label);
  }
  renderCrossPicker();renderSaved();route();
})();
