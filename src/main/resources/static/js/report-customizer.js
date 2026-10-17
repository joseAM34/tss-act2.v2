'use strict';
(() => {
  const root=document.querySelector('[data-report-customizer]');if(!root)return;
  const $=id=>document.getElementById(id),bootstrap=JSON.parse($('custom-bootstrap').value),config=bootstrap.configuration;
  const form=$('custom-form'),csrf=form.querySelector('[name=_csrf]');
  let screen='',question=null,cross=null,advancedIndex=null,timer,sequence=0,controller,latest=null,expectedHash='',generating=false,applying=false;
  let disposers=[];
  // Bounded, page-memory cache. Never persisted to storage or trusted for generation.
  const previewCache=new Map();let cacheScope='';
  const scope=()=>['preguntas','pregunta'].includes(screen)?'question:'+question:['cruces','cruce-editar'].includes(screen)?'cross:'+cross:screen;
  const hasCrossPreview=id=>latest?.catalog?.crosses?.some(c=>c.id===id);

  config.crossSettings=config.crossSettings||[];config.advanced=config.advanced||[];
  config.questions.forEach(item=>item.representations=item.representations||['table','columns']);
  config.crossSettings.forEach(item=>item.representations=item.representations||['table','heat']);
  let pair=[],pairHash='',pairPreview=null,pairSequence=0,pairController,pairSaving=false,newDispose;
  let pairRepresentations=['table','heat'];
  const crossName=item=>item.result.row_label+' × '+item.result.column_label;
  const crossSetting=id=>{
    let setting=config.crossSettings.find(item=>item.id===id);
    if(!setting){const original=bootstrap.crosses.find(item=>item.id===id);setting={id,title:crossName(original).slice(0,200),comment:''};config.crossSettings.push(setting);}
    setting.representations=setting.representations||['table','heat'];return setting;
  };
  config.crossIds.forEach(crossSetting);
  function representationButtons(container,values,choices,onChange){
    container.replaceChildren();container.className='report-representations';
    const icons={table:'▦',columns:'▥',bars:'☰',pie:'◕',donut:'◎',heat:'▩',grouped:'▥',stacked:'▤',line:'⌁',area:'◩',histogram:'▥',box:'⊞',scatter:'⁙',radar:'⬡'};
    for(const group of [...new Set(choices.map(item=>item[3]))]){
      const field=node('fieldset');field.append(node('legend',group));const buttons=node('div');buttons.className='representation-buttons';
      choices.filter(item=>item[3]===group).forEach(([id,label,reason])=>{
        const button=node('button');button.type='button';button.dataset.representation=id;button.disabled=!!reason;button.title=reason||label;button.setAttribute('aria-pressed',String(values.includes(id)));
        const icon=node('span',icons[id]);icon.className='representation-icon';icon.setAttribute('aria-hidden','true');button.append(icon,node('span',label));
        button.addEventListener('click',()=>{const index=values.indexOf(id);if(index<0)values.push(id);else values.splice(index,1);button.setAttribute('aria-pressed',String(values.includes(id)));onChange();});buttons.append(button);
      });field.append(buttons);const unavailable=choices.filter(item=>item[3]===group&&item[2]);
      if(unavailable.length){const details=node('details');details.append(node('summary','¿Por qué hay opciones en gris?'));unavailable.forEach(([,label,reason])=>details.append(node('p',label+': '+reason)));field.append(details);}container.append(field);
    }
    const help=node('p','Puedes activar varias representaciones. Resaltado: seleccionado; apagado: disponible; gris: no disponible.');help.className='representation-help';container.append(help);
  }
  const crossChoices=[['table','Tabla',null,'Datos'],['heat','Mapa de calor',null,'Comparaciones'],['grouped','Barras agrupadas',null,'Comparaciones'],['stacked','Barras apiladas',null,'Comparaciones'],['radar','Radar','Requiere varios criterios de escala compatible.','Otros'],['scatter','Dispersión','Requiere dos variables numéricas.','Otros']];
  function questionButtons(){
    const q=active(),variable=bootstrap.result.variables.find(v=>v.question_id===question),exclusive=['radio','select'].includes(variable.type)&&!variable.numeric,advanced=variable.advanced||{};
    representationButtons($('question-representations'),q.representations,[['table','Tabla',null,'Datos'],['columns','Columnas',null,'Comparar cantidades'],['bars','Barras',null,'Comparar cantidades'],['pie','Circular',exclusive?null:'Requiere categorías de selección única.','Partes de un total'],['donut','Anillo',exclusive?null:'Requiere categorías de selección única.','Partes de un total'],['line','Líneas',advanced.time?null:'Requiere fechas de recepción congeladas.','Evolución de recepción'],['area','Áreas',advanced.time?null:'Requiere fechas de recepción congeladas.','Evolución de recepción'],['histogram','Histograma',advanced.histogram?null:'Requiere al menos un valor numérico.','Distribución'],['box','Caja',advanced.box?null:'Requiere al menos dos valores numéricos.','Distribución'],['scatter','Dispersión','Requiere dos variables numéricas.','Otros'],['radar','Radar','Requiere varios criterios de escala compatible.','Otros']],invalidate);
  }
  const node=(tag,text)=>{const n=document.createElement(tag);if(text!==undefined)n.textContent=text;return n;};
  const status=(message,error=false)=>{$('custom-status').textContent=message;$('custom-status').dataset.error=String(error);};
  const questions=()=>config.questions;
  const active=()=>questions().find(q=>q.id===question);
  const input=(id,value,change,checked=false)=>{const element=$(id);element[checked?'checked':'value']=value;element.addEventListener(checked?'change':'input',()=>{change(element[checked?'checked':'value']);invalidate();});};
  input('reportTitle',config.title,value=>config.title=value);
  input('custom-introduction',config.introduction,value=>config.introduction=value);
  input('custom-comment',config.comment,value=>config.comment=value);
  input('custom-context',config.context,value=>config.context=value,true);
  input('custom-tables',config.tables,value=>config.tables=value,true);
  input('custom-charts',config.charts,value=>config.charts=value,true);
  input('question-title','',value=>{if(active())active().title=value;});
  input('question-comment','',value=>{if(active())active().comment=value;});
  input('edit-cross-title','',value=>{if(cross)crossSetting(cross).title=value;});
  input('edit-cross-comment','',value=>{if(cross)crossSetting(cross).comment=value;});
  input('advanced-title','',value=>{if(advancedIndex!==null)config.advanced[advancedIndex].title=value;});
  input('advanced-comment','',value=>{if(advancedIndex!==null)config.advanced[advancedIndex].comment=value;});
  const advancedKey=item=>item.type+':'+item.questions.join('|');
  function renderAdvancedList(){
    const container=$('advanced-list');container.replaceChildren();
    config.advanced.forEach((item,index)=>{
      const row=node('div');row.className='custom-choice';const label=node('label'),check=node('input');check.type='checkbox';check.checked=item.included;check.setAttribute('aria-label','Incluir '+item.title);check.addEventListener('change',()=>{item.included=check.checked;invalidate();});label.append(check,node('span','Incluir'));
      const button=node('button',item.title);button.type='button';button.dataset.advanced=String(index);button.setAttribute('aria-pressed',String(index===advancedIndex));button.addEventListener('click',()=>{advancedIndex=advancedIndex===index?null:index;renderAdvancedList();render();});row.append(label,button);container.append(row);
    });if(!config.advanced.length)container.append(node('p','No hay gráficos avanzados añadidos.'));
  }
  function addAdvanced(type,ids,title){
    const key=type+':'+ids.join('|');let index=config.advanced.findIndex(item=>advancedKey(item)===key);
    if(index<0){config.advanced.push({type,questions:ids,title:title.slice(0,200),comment:'',included:true});index=config.advanced.length-1;}else config.advanced[index].included=true;
    advancedIndex=index;renderAdvancedList();invalidate();$('advanced-status').textContent='Gráfico añadido a la configuración. Revisa su vista previa y sus bases.';
  }
  const advancedOptions=bootstrap.advancedOptions||{numeric:[],radar:[]};
  for(const id of ['scatter-x','scatter-y'])advancedOptions.numeric.forEach(item=>{const option=node('option',item.label+' ('+item.unit+')');option.value=item.id;$(id).append(option);});
  if(advancedOptions.numeric.length>1)$('scatter-y').selectedIndex=1;
  $('add-scatter').disabled=advancedOptions.numeric.length<2;
  $('add-scatter').addEventListener('click',()=>{const x=$('scatter-x').value,y=$('scatter-y').value;if(!x||!y||x===y){$('advanced-status').textContent='Selecciona dos magnitudes distintas.';return;}addAdvanced('scatter',[x,y],advancedOptions.numeric.find(item=>item.id===x).label+' × '+advancedOptions.numeric.find(item=>item.id===y).label);});
  advancedOptions.radar.forEach(item=>{const button=node('button',item.title);button.type='button';button.className='button button-outline';button.addEventListener('click',()=>addAdvanced('radar',item.questions,item.title));$('radar-options').append(button);});
  if(!advancedOptions.radar.length)$('radar-options').append(node('p','No hay paneles compatibles de cinco criterios de empleadores con su escala original en este conjunto.'));
  const list=(container,items,isCross)=>{
    items.forEach(item=>{
    const id=item.id,row=node('div');row.className='custom-choice';
      const label=node('label'),check=node('input');check.type='checkbox';check.setAttribute('aria-label','Incluir '+(isCross?item.result.row_label+' × '+item.result.column_label:item.title));
      check.name=isCross?'crossIds':'questions';check.value=id;check.checked=isCross?config.crossIds.includes(id):item.included;
      check.addEventListener('change',()=>{if(isCross){config.crossIds=config.crossIds.filter(value=>value!==id);if(check.checked){crossSetting(id);config.crossIds.push(id);}}else item.included=check.checked;invalidate();});label.append(check,node('span','Incluir'));
      const button=node('button',isCross?item.result.row_label+' × '+item.result.column_label:item.title);button.type='button';button.dataset[isCross?'cross':'question']=id;button.setAttribute('aria-pressed','false');
      button.addEventListener('click',()=>{previewCache.clear();if(isCross){cross=cross===id?null:id;if(cross&&!hasCrossPreview(cross))refresh();}else question=question===id?null:id;render();});row.append(label,button);container.append(row);
    });
  };
  const renderCrossList=()=>{$('custom-crosses').replaceChildren();list($('custom-crosses'),bootstrap.crosses,true);if(!bootstrap.crosses.length)$('custom-crosses').append(node('p','No hay cruces guardados de este conjunto.'));};
  list($('custom-questions'),questions(),false);renderCrossList();
  function invalidate(){expectedHash='';$('custom-generate').disabled=true;sequence++;controller?.abort();status('Actualizando vista previa…');clearTimeout(timer);timer=setTimeout(refresh,250);}
  async function post(url,body,signal){
    const response=await fetch(url,{method:'POST',credentials:'same-origin',cache:'no-store',signal,headers:{'Content-Type':'application/json','X-CSRF-TOKEN':csrf.value,Accept:'application/json'},body:JSON.stringify(body)});
    if(response.redirected||!response.headers.get('Content-Type')?.includes('application/json'))throw Error('La sesión expiró o la respuesta no se pudo verificar. Inicia sesión o vuelve a intentar.');
    const data=await response.json().catch(()=>({}));if(!response.ok)throw Error(data.message||'No se pudo verificar la vista previa. Revisa la configuración y la sesión.');return data;
  }
  async function refresh(){
    clearTimeout(timer);if(generating||applying)return;const token=++sequence;controller?.abort();controller=new AbortController();expectedHash='';$('custom-generate').disabled=true;
    status('Actualizando vista previa…');
    const requestController=controller,timeout=setTimeout(()=>requestController.abort(),20000);
    try{
      const currentScope=scope();if(cacheScope!==currentScope){previewCache.clear();cacheScope=currentScope;}
      const key=JSON.stringify([config,cross]);
      const data=previewCache.get(key)||await post('/admin/reportes/personalizar/vista-previa'+(cross?'?focusCross='+encodeURIComponent(cross):''),config,controller.signal);
      if(token===sequence){if(previewCache.size>=6)previewCache.delete(previewCache.keys().next().value);previewCache.set(key,data);}
      if(token!==sequence)return;latest=data;expectedHash=data.hash;$('custom-generate').disabled=false;status('Vista previa verificada con el corte congelado.');render();
    }catch(error){if(token!==sequence)return;latest=null;expectedHash='';status(error.name==='AbortError'?'La consulta tardó demasiado. Cambia una opción o recarga para volver a intentar.':error.message,true);render();}
    finally{clearTimeout(timeout);}
  }
  function render(){
    disposers.forEach(dispose=>dispose());disposers=[];
    const containers=['full-preview','question-list-preview','question-editor-preview','cross-list-preview','edit-cross-preview','advanced-list-preview','advanced-editor-preview','text-preview'];containers.forEach(id=>$(id).replaceChildren());
    root.querySelectorAll('[data-question]').forEach(button=>{button.setAttribute('aria-pressed',String(button.dataset.question===question));button.textContent=questions().find(q=>q.id===button.dataset.question).title;});
    root.querySelectorAll('[data-cross]').forEach(button=>{button.setAttribute('aria-pressed',String(button.dataset.cross===cross));button.textContent=config.crossSettings.find(item=>item.id===button.dataset.cross)?.title||crossName(bootstrap.crosses.find(item=>item.id===button.dataset.cross));});
    $('custom-edit-cross').hidden=!cross;
    $('custom-edit-question').hidden=!question;$('question-hint').hidden=!!question;$('cross-hint').hidden=!!cross;
    $('edit-advanced').hidden=advancedIndex===null;$('advanced-hint').hidden=advancedIndex!==null;
    if(!latest)return;
    const draw=(container,html,content)=>{container.innerHTML=html;disposers.push(window.ReportCharts(container,content));};
    if(screen==='reporte')draw($('full-preview'),latest.html,latest.content);
    if(screen==='preguntas'||screen==='pregunta'){
      if(!question)return;const source=node('div');source.innerHTML=latest.catalogHtml;
      const section=[...source.querySelectorAll('[data-question-id]')].find(item=>item.dataset.questionId===question);
      const target=$(screen==='preguntas'?'question-list-preview':'question-editor-preview');if(section)draw(target,section.outerHTML,latest.catalog);else target.append(node('p','Activa una representación para previsualizar esta pregunta.'));
    }
    if((screen==='cruces'||screen==='cruce-editar')&&cross){const source=node('div');source.innerHTML=latest.catalogHtml;const section=[...source.querySelectorAll('[data-cross-id]')].find(item=>item.dataset.crossId===cross),target=$(screen==='cruces'?'cross-list-preview':'edit-cross-preview');if(section)draw(target,section.outerHTML,latest.catalog);else target.append(node('p','Activa una representación para previsualizar esta comparación.'));}
    if((screen==='avanzados'||screen==='avanzado')&&advancedIndex!==null){
      const source=node('div');source.innerHTML=latest.catalogHtml;const key=advancedKey(config.advanced[advancedIndex]),section=[...source.querySelectorAll('[data-advanced-id]')].find(item=>item.dataset.advancedId===key);
      if(section)draw($(screen==='avanzados'?'advanced-list-preview':'advanced-editor-preview'),section.outerHTML,latest.catalog);
    }
    if(screen==='presentacion'){
      const t=latest.content.template,p=$('text-preview');p.append(node('h3',t.title));if(config.context)p.append(node('p','Contexto y fuentes incluidos · corte '+bootstrap.cutoff));
      p.append(node('h3','Introducción'),node('p',t.introduction||'Sin introducción.'),node('h3','Comentario general'),node('p',latest.content.comment||'Sin comentarios.'));
    }
  }
  function renderCandidates(){
    const container=$('cross-candidates');container.replaceChildren();
    const choices=bootstrap.crossChoices||[];
    for(const group of [...new Set(choices.map(item=>item.group))]){
      const details=node('details');details.open=true;details.append(node('summary',group));
      for(const choice of choices.filter(item=>item.group===group)){
        const chosen=pair.includes(choice.id),base=choices.find(item=>item.id===pair[0]);
        const cells=base&&base.id!==choice.id&&base.options*choice.options>2500;
        const disabled=pairSaving||!choice.eligible||(!chosen&&(pair.length===2||cells));
        const card=node('div');card.className='cross-candidate '+(chosen?'chosen':disabled?'unavailable':pair.length?'compatible':'');
        const label=node('label'),check=node('input');check.type='checkbox';check.checked=chosen;check.disabled=disabled;check.dataset.candidate=choice.id;
        check.addEventListener('change',()=>{pairHash='';pairPreview=null;pairSequence++;pairController?.abort();newDispose?.();newDispose=null;$('new-cross-preview').replaceChildren();if(check.checked)pair.push(choice.id);else pair=pair.filter(id=>id!==choice.id);$('new-cross-title').value=pair.map(id=>choices.find(item=>item.id===id).label).join(' × ').slice(0,200);renderCandidates();previewPair();});
        label.append(check,node('span',choice.label));card.append(label,node('small',chosen?'Seleccionada':cells?'Supera las 2500 celdas admitidas.':!choice.eligible?choice.reason:pair.length===2?'Quita una selección para cambiarla.':pair.length?'Compatible con la pregunta seleccionada.':choice.reason));details.append(card);
      }container.append(details);
    }
    $('cross-pair-status').textContent=pair.length+'/2 preguntas seleccionadas';$('add-report-cross').disabled=pair.length!==2||!pairHash||pairSaving||!pairRepresentations.length;
  }
  function drawNewCross(){
    newDispose?.();newDispose=null;const container=$('new-cross-preview');container.replaceChildren();if(!pairPreview||screen!=='cruce-nuevo')return;
    container.innerHTML=pairPreview.html;const heading=container.querySelector('h3');if(heading)heading.textContent=$('new-cross-title').value.trim()||'Nueva comparación';
    const comment=$('new-cross-comment').value;if(comment.trim()){const p=node('p',comment);p.className='report-prose';heading?.after(p);}
    newDispose=window.ReportCharts(container,{analysis:{result:{variables:[]}},crosses:[pairPreview.cross]});
  }
  async function previewPair(){
    if(pair.length!==2){$('new-cross-status').textContent='Selecciona dos preguntas compatibles.';return;}
    const token=++pairSequence;pairController?.abort();pairController=new AbortController();$('new-cross-status').textContent='Calculando desde el conjunto congelado…';
    const requestController=pairController,timeout=setTimeout(()=>requestController.abort(),20000);
    try{
      const result=await post('/admin/reportes/personalizar/cruces/vista-previa',{analysisId:config.analysisId,row:pair[0],column:pair[1],representations:pairRepresentations},pairController.signal);
      if(token!==pairSequence)return;pairPreview=result;pairHash=result.hash;$('new-cross-status').textContent='Cruce verificado. Revisa su tabla y diagnóstico antes de añadirlo.';drawNewCross();renderCandidates();
    }catch(error){if(token!==pairSequence)return;pairHash='';$('new-cross-status').textContent=error.name==='AbortError'?'La consulta tardó demasiado. Cambia una selección para volver a intentar.':error.message;renderCandidates();}
    finally{clearTimeout(timeout);}
  }
  for(const id of ['new-cross-title','new-cross-comment'])$(id).addEventListener('input',drawNewCross);
  $('add-report-cross').addEventListener('click',async()=>{
    if(pairSaving||!pairHash||pair.length!==2)return;
    if(config.crossIds.length>=10){$('new-cross-status').textContent='Ya hay diez comparaciones incluidas. Quita una antes de añadir otra.';return;}
    pairSaving=true;renderCandidates();const title=$('new-cross-title').value.trim(),comment=$('new-cross-comment').value;
    const representations=[...pairRepresentations],request={pair:{analysisId:config.analysisId,row:pair[0],column:pair[1],representations},expectedHash:pairHash};
    try{
      const saved=await post('/admin/reportes/personalizar/cruces',request);
      if(!bootstrap.crosses.some(item=>item.id===saved.id))bootstrap.crosses.unshift(saved);
      if(!config.crossIds.includes(saved.id))config.crossIds.push(saved.id);
      config.crossSettings=config.crossSettings.filter(item=>item.id!==saved.id);config.crossSettings.push({id:saved.id,title:title||crossName(saved).slice(0,200),comment,representations});
      cross=saved.id;renderCrossList();invalidate();location.hash='cruces';
    }catch(error){pairHash='';$('new-cross-status').textContent=error.message+' Vuelve a seleccionar las preguntas para revisar la vista previa.';}
    finally{pairSaving=false;renderCandidates();}
  });
  function route(){
    const next=(location.hash.slice(1)||'reporte');if(!['reporte','personalizar','preguntas','pregunta','cruces','cruce-nuevo','cruce-editar','avanzados','avanzado','presentacion'].includes(next)){location.hash='reporte';return;}
    if(next==='preguntas'&&screen!=='pregunta')question=null;if(next==='cruces'&&!['cruces','cruce-editar','cruce-nuevo'].includes(screen))cross=null;
    if(next==='pregunta'&&!question){location.hash='preguntas';return;}
    if(next==='cruce-editar'&&!cross){location.hash='cruces';return;}
    if(next!=='cruce-nuevo'){pairController?.abort();pairSequence++;newDispose?.();newDispose=null;}
    else if(screen!=='cruce-nuevo'){pair=[];pairHash='';pairPreview=null;pairRepresentations=['table','heat'];$('new-cross-title').value='';$('new-cross-comment').value='';$('new-cross-preview').replaceChildren();$('new-cross-status').textContent='Selecciona dos preguntas compatibles.';renderCandidates();
      representationButtons($('new-cross-representations'),pairRepresentations,crossChoices,()=>{pairHash='';pairPreview=null;pairSequence++;pairController?.abort();newDispose?.();newDispose=null;$('new-cross-preview').replaceChildren();renderCandidates();if(pairRepresentations.length)previewPair();else $('new-cross-status').textContent='Activa al menos una representación.';});}
    if(next==='avanzados'&&screen!=='avanzado')advancedIndex=null;if(next==='avanzado'&&advancedIndex===null){location.hash='avanzados';return;}
    if(next!==screen)previewCache.clear();
    screen=next;root.querySelectorAll('[data-custom-screen]').forEach(section=>section.hidden=section.dataset.customScreen!==screen);
    const titles={reporte:'Generar reporte',personalizar:'Personalizar reporte',preguntas:'Preguntas y gráficos',pregunta:'Configurar esta pregunta',cruces:'Comparaciones del reporte','cruce-nuevo':'Crear comparación','cruce-editar':'Editar comparación',avanzados:'Gráficos avanzados',avanzado:'Configurar gráfico avanzado',presentacion:'Presentación y comentarios'};
    $('custom-heading').textContent=titles[screen];$('custom-back').hidden=screen==='reporte';$('custom-back').href=screen==='personalizar'?'#reporte':screen==='pregunta'?'#preguntas':'#personalizar';
    if(screen==='pregunta'){const q=active();$('question-title').value=q.title;$('question-comment').value=q.comment;$('question-original').textContent=bootstrap.result.variables.find(v=>v.question_id===question).label;questionButtons();}
    if(screen==='cruce-editar'){const setting=crossSetting(cross);$('edit-cross-title').value=setting.title;$('edit-cross-comment').value=setting.comment;representationButtons($('edit-cross-representations'),setting.representations,crossChoices,invalidate);if(!hasCrossPreview(cross))refresh();}
    if(screen.startsWith('cruce-'))$('custom-back').href='#cruces';
    $('custom-back').textContent=screen.startsWith('cruce-')?'← Comparaciones':screen==='pregunta'?'← Preguntas y gráficos':screen==='personalizar'?'← Volver al reporte':'← Personalizar';
    if(screen==='avanzados')renderAdvancedList();
    if(screen==='avanzado'){const setting=config.advanced[advancedIndex];$('advanced-title').value=setting.title;$('advanced-comment').value=setting.comment;$('custom-back').href='#avanzados';$('custom-back').textContent='← Gráficos avanzados';}
    render();$('custom-heading').focus({preventScroll:true});window.scrollTo(0,0);
  }
  $('save-presentation-template').addEventListener('change',()=>{$('presentation-template-name').disabled=!$('save-presentation-template').checked;});
  $('apply-presentation-template')?.addEventListener('click',async()=>{
    const button=$('apply-presentation-template');if(generating||applying)return;applying=true;button.disabled=true;$('presentation-template-status').textContent='Aplicando plantilla al corte seleccionado…';expectedHash='';sequence++;controller?.abort();clearTimeout(timer);$('custom-generate').disabled=true;
    try{
      const data=await post('/admin/reportes/personalizar/plantillas/aplicar',{templateId:$('presentation-template').value,analysisId:config.analysisId});
      for(const item of data.crosses)if(!bootstrap.crosses.some(c=>c.id===item.id))bootstrap.crosses.push(item);
      Object.assign(config,data.configuration);
      config.crossSettings=config.crossSettings||[];config.advanced=config.advanced||[];
      config.questions.forEach(q=>q.representations=q.representations||['table','columns']);
      config.crossSettings.forEach(c=>c.representations=c.representations||['table','heat']);
      for(const [id,key,checked] of [['reportTitle','title',false],['custom-introduction','introduction',false],['custom-comment','comment',false],['custom-context','context',true],['custom-tables','tables',true],['custom-charts','charts',true]])$(id)[checked?'checked':'value']=config[key];
      question=null;cross=null;advancedIndex=null;$('custom-questions').replaceChildren();list($('custom-questions'),questions(),false);renderCrossList();renderAdvancedList();applying=false;invalidate();button.disabled=false;$('presentation-template-status').textContent='Plantilla aplicada. Revisa la vista previa antes de generar.';

    }catch(error){applying=false;$('presentation-template-status').textContent=error.message;button.disabled=false;refresh();}
  });
  form.addEventListener('submit',async event=>{
    event.preventDefault();if(generating||applying||!expectedHash||$('custom-generate').disabled||screen!=='reporte')return;
    generating=true;$('custom-generate').disabled=true;const hash=expectedHash;
    try{const data=await post(form.action,{configuration:config,expectedHash:hash,saveTemplate:$('save-presentation-template').checked,templateName:$('presentation-template-name').value});location.assign(data.url);}
    catch(error){status(error.message,true);expectedHash='';latest=null;render();}
    finally{generating=false;}
  });
  window.addEventListener('hashchange',route);window.addEventListener('pagehide',()=>{previewCache.clear();controller?.abort();clearTimeout(timer);disposers.forEach(dispose=>dispose());});route();refresh();
})();
