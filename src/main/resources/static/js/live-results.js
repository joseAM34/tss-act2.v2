'use strict';
function createLiveCoordinator() {
  let generation = 0;
  return {start: () => ++generation, current: token => token === generation, invalidate: () => ++generation};
}
if (typeof module !== 'undefined') module.exports = {createLiveCoordinator};
if (typeof document !== 'undefined') (() => {
  const root = document.querySelector('[data-live-url]');
  if (!root) return;
  const content=root.querySelector('[data-live-content]'), status=root.querySelector('[data-live-status]');
  const save=root.querySelector('[data-live-save]'), saveButton=root.querySelector('[data-live-save-button]');
  const reportButton=root.querySelector('[data-live-report-button]');
  const setSaveDisabled=value=>{saveButton.disabled=value;if(reportButton)reportButton.disabled=value;};
  const refresh=root.querySelector('[data-live-refresh]'), toggle=root.querySelector('[data-live-toggle]');
  const cutoffField=document.createElement('input');cutoffField.type='hidden';cutoffField.name='expectedCutoff';save.append(cutoffField);
  const coordinator=createLiveCoordinator();let charts=[], timer, controller, lastFrame, saving=false,dirty=false;
  refresh.hidden=false;root.querySelector('[data-live-toggle-label]').hidden=false;
  const dispose=()=>{charts.forEach(chart=>chart.dispose());charts=[];};
  function draw(frame) {
    if(!window.echarts)return;
    root.querySelectorAll('[data-live-variable]').forEach(element=>{
      const v=frame.result.variables[Number(element.dataset.liveVariable)], chart=echarts.init(element);
      chart.setOption({tooltip:{trigger:'axis',renderMode:'richText'},grid:{left:15,right:15,bottom:85,containLabel:true},
        xAxis:{type:'category',data:v.numeric?['Mínimo','Mediana','Máximo']:v.distribution.map(o=>o.label),axisLabel:{rotate:25,width:110,overflow:'truncate'}},
        yAxis:{type:'value',name:v.numeric?v.unit:'Envíos'},series:[{type:'bar',data:v.numeric?['min','median','max'].map(k=>v.numeric[k]):v.distribution.map(o=>o.count)}],aria:{enabled:true}});
      charts.push(chart);
    });
    const element=root.querySelector('[data-live-cross]');
    if(element && frame.cross){const c=frame.cross,chart=echarts.init(element),cells=c.table.observed.flatMap((r,i)=>r.map((n,j)=>[j,i,n]));
      chart.setOption({tooltip:{renderMode:'richText'},grid:{left:15,right:15,bottom:85,containLabel:true},xAxis:{type:'category',data:c.column_options.map(o=>o.label),axisLabel:{rotate:25,width:100,overflow:'truncate'}},yAxis:{type:'category',data:c.row_options.map(o=>o.label)},visualMap:{min:0,max:Math.max(1,...cells.map(c=>c[2])),bottom:0,orient:'horizontal',left:'center'},series:[{type:'heatmap',data:cells,label:{show:true}}],aria:{enabled:true}});charts.push(chart);}
  }
  function accept(frame,html) {
    dispose();
    // HTML producido por Thymeleaf en el mismo backend, con todos los textos escapados.
    if(html!==undefined)content.innerHTML=html;
    lastFrame=frame;save.elements.expectedHash.value=frame.hash;save.elements.expectedCutoff.value=frame.cutoff;setSaveDisabled(false);draw(frame);
    for(const id of ['live-row','live-column']) {
      const select=root.querySelector('#'+id), selected=select.value;
      select.replaceChildren(new Option('Sin cruce',''));
      frame.result.variables.filter(v=>v.type==='radio'||v.type==='select').forEach(v=>select.add(new Option(v.label,v.question_id)));
      select.value=selected;
    }
  }
  try {if(root.dataset.liveInitial)accept(JSON.parse(root.dataset.liveInitial));}catch{status.textContent='No se pudieron dibujar los gráficos iniciales. Las tablas conservan el corte.';}
  async function update() {
    clearTimeout(timer);
    if(document.hidden || saving || dirty){schedule();return;}
    const token=coordinator.start();controller?.abort();const requestController=new AbortController();controller=requestController;
    const timeout=setTimeout(()=>requestController.abort(),20000);
    refresh.disabled=true;
    try {
      const response=await fetch(root.dataset.liveUrl,{credentials:'same-origin',cache:'no-store',signal:controller.signal,headers:{Accept:'application/json'}});
      if(!response.ok){const error=await response.json().catch(()=>({}));throw Error(error.message||'La sesión expiró o no se pudo consultar.');}
      const payload=await response.json();if(!coordinator.current(token))return;
      if(!lastFrame || payload.frame.hash!==lastFrame.hash)accept(payload.frame,payload.html);
      else {setSaveDisabled(false);}
      status.textContent='Verificado: '+new Date(payload.frame.cutoff).toLocaleString('es-BO',{timeZone:'America/La_Paz'})+' (hora de Bolivia). El corte de las tablas permanece indicado arriba.';
      root.dataset.liveStale='false';
    }catch(error){if(!coordinator.current(token))return;setSaveDisabled(true);root.dataset.liveStale='true';status.textContent=(lastFrame?'Vista desactualizada. Se conserva el último corte. ':'')+(error.name==='AbortError'?'La consulta excedió el tiempo disponible.':error.message)+' Vuelve a intentar.';
    }finally{clearTimeout(timeout);if(coordinator.current(token))refresh.disabled=false;schedule();}
  }
  function schedule(){clearTimeout(timer);if(toggle.checked && !document.hidden && !dirty)timer=setTimeout(update,30000);}
  root.querySelector('.live-filters').addEventListener('change',()=>{dirty=true;coordinator.invalidate();controller?.abort();clearTimeout(timer);setSaveDisabled(true);refresh.disabled=true;toggle.checked=false;status.textContent='Aplica la selección para consultar esos filtros. Las tablas conservan la selección anterior.';});
  refresh.addEventListener('click',update);toggle.addEventListener('change',()=>{if(toggle.checked)update();else clearTimeout(timer);});
  document.addEventListener('visibilitychange',()=>{clearTimeout(timer);if(!document.hidden && toggle.checked)update();else {coordinator.invalidate();controller?.abort();refresh.disabled=false;}});
  save.addEventListener('submit',async event=>{
    event.preventDefault();if(!lastFrame||saveButton.disabled||saving)return;
    saving=true;clearTimeout(timer);coordinator.invalidate();controller?.abort();setSaveDisabled(true);refresh.disabled=true;
    const saveController=new AbortController(), saveTimeout=setTimeout(()=>saveController.abort(),20000);
    const message=root.querySelector('[data-live-saved]');message.textContent='Guardando el corte mostrado…';
    try {
      const response=await fetch(save.action,{method:'POST',credentials:'same-origin',cache:'no-store',signal:saveController.signal,body:new URLSearchParams(new FormData(save)),headers:{Accept:'application/json'}});
      const payload=await response.json();if(!response.ok)throw Error(payload.message||'No se pudo guardar el corte.');
      if(event.submitter===reportButton){location.assign('/admin/reportes/nuevo?analysisId='+encodeURIComponent(payload.analysisId)+(payload.crossId?'&crossId='+encodeURIComponent(payload.crossId):''));return;}
      message.replaceChildren();const a=document.createElement('a');a.className='text-link';a.href='/admin/analisis-general/'+encodeURIComponent(payload.analysisId);a.textContent='Corte guardado. Abrir análisis y crear reporte →';message.append(a);
      const reportLink=document.createElement('a');reportLink.className='text-link';reportLink.href='/admin/reportes/nuevo?analysisId='+encodeURIComponent(payload.analysisId);reportLink.textContent=' · Crear reporte y exportar →';message.append(reportLink);
      if(payload.crossId){const b=document.createElement('a');b.className='text-link';b.href='/admin/cruces-generales/'+encodeURIComponent(payload.crossId);b.textContent=' · Abrir cruce guardado →';message.append(b);}
    }catch(error){message.textContent=error.message+' Actualiza antes de volver a guardar.';root.dataset.liveStale='true';}
    finally{clearTimeout(saveTimeout);saving=false;refresh.disabled=dirty;schedule();}
  });
  window.addEventListener('resize',()=>charts.forEach(c=>c.resize()));
  window.addEventListener('pagehide',()=>{clearTimeout(timer);coordinator.invalidate();controller?.abort();dispose();});
  schedule();
})();
