'use strict';
(() => {
  const $ = selector => document.querySelector(selector);
  const names = {resumen:'Resumen', versiones:'Versiones', respuestas:'Respuestas', resultados:'Resultados', importar:'Importar', reportes:'Reportes y plantillas'};
  const surveys = {titulados:'Trayectoria de titulados', empleadores:'Demanda de empleadores'};
  // Fixtures independientes, sin lecturas del sistema ni cálculos estadísticos en el navegador.
  const fixtures = {
    titulados: {
      v2: {total:120, real:90, unknown:30, date:'15 oct 2026 · 10:30', bars:[['Sistemas',54,'60%',60],['Otras áreas',27,'30%',30],['Sin experiencia',9,'10%',10]], base:90},
      v1: {total:80, real:60, unknown:20, date:'30 sep 2026 · 18:00', bars:[['Sistemas',36,'60%',60],['Otras áreas',18,'30%',30],['Sin experiencia',6,'10%',10]], base:60}
    },
    empleadores: {
      v2: {total:60, real:40, unknown:20, date:'15 oct 2026 · 10:30', bars:[['Contrata',24,'60%',60],['No contrata',12,'30%',30],['No sabe',4,'10%',10]], base:40},
      v1: {total:30, real:20, unknown:10, date:'30 sep 2026 · 18:00', bars:[['Contrata',12,'60%',60],['No contrata',6,'30%',30],['No sabe',2,'10%',10]], base:20}
    }
  };
  let survey = 'titulados', version = 'v2', scope = 'REAL', importStep = 0, corrected = false, consent = false;
  const data = () => fixtures[survey][version];
  const title = () => surveys[survey];
  const heading = (eyebrow, name, intro, actions = '') => `<div class="heading"><div><p class="eyebrow">${eyebrow}</p><h1 id="page-title">${name}</h1><p>${intro}</p></div>${actions}</div>`;
  const button = (action, text, kind = 'secondary') => `<button class="${kind}" data-action="${action}">${text}</button>`;
  const stats = () => `<div class="stats"><article class="stat"><span>Envíos recibidos · ${version}</span><strong>${data().total}</strong><small>Un envío no equivale a una persona independiente.</small></article><article class="stat"><span>Clasificados como reales</span><strong>${data().real}</strong><small>Revisión administrativa registrada.</small></article><article class="stat"><span>Origen sin verificar</span><strong>${data().unknown}</strong><small>Se mantienen separados de los reales.</small></article></div>`;
  const chart = () => `<div class="chart" role="img" aria-label="Distribución ficticia; cifras equivalentes en la tabla desplegable">${data().bars.map((b,i) => `<div class="bar-row"><span>${b[0]}</span><div class="bar-track"><span class="bar ${i === 1 ? 'alt' : i === 2 ? 'third' : ''}" style="width:${b[3]}%"></span></div><span class="bar-value">${b[1]} / ${data().base} · ${b[2]}</span></div>`).join('')}</div><details><summary>Ver cifras en tabla</summary><div class="table-wrap"><table><caption>Origen real · ${version} · Base con valor: ${data().base} envíos</caption><thead><tr><th>Categoría</th><th>Envíos</th><th>Base</th><th>Porcentaje</th></tr></thead><tbody>${data().bars.map(b => `<tr><td>${b[0]}</td><td>${b[1]}</td><td>${data().base}</td><td>${b[2]}</td></tr>`).join('')}</tbody></table></div></details>`;
  function catalog() {
    return heading('TU ESPACIO DE TRABAJO','Mis encuestas','Elige una encuesta. Sus versiones, respuestas y resultados estarán en un mismo lugar.',button('create','＋ Crear encuesta','primary')) + `<div class="grid">${Object.entries(surveys).map(([id,name]) => `<article class="card survey-card"><div class="card-top"><span class="survey-icon" aria-hidden="true">${id === 'titulados' ? '◫' : '▥'}</span><span class="pill good">En recepción</span></div><h2>${name}</h2><p>${id === 'titulados' ? 'Formación, experiencia laboral y continuidad de estudios.' : 'Necesidades de contratación y valoración de la formación.'}</p><div class="survey-meta"><span>2 versiones publicadas</span><span>1 borrador</span></div><div class="actions"><span class="pill">Versión activa: v2</span><button class="primary" data-survey="${id}">Abrir encuesta →</button></div></article>`).join('')}</div><div class="note section-gap"><strong>Un recorrido más directo</strong>Abre una encuesta, elige su versión y trabaja desde sus secciones. Ya no tendrás que volver a buscarla en cada herramienta.</div>`;
  }
  function summary() {
    return heading('VISTA GENERAL',title(),'Un vistazo a la recepción y las próximas acciones de esta versión.',`<div class="actions">${button('share','Compartir encuesta') }<a class="primary" href="#resultados">Ver resultados →</a></div>`) + stats() + `<div class="grid"><article class="card"><div class="card-top"><h2>Continúa trabajando</h2><span class="pill">${version}</span></div><div class="shortcut-list"><a href="#respuestas"><span>Revisar respuestas<small>${data().unknown} envíos con origen pendiente.</small></span><span>→</span></a><a href="#importar"><span>Importar CSV o Excel<small>Detectar columnas, revisar avisos y confirmar.</small></span><span>→</span></a><a href="#versiones"><span>Gestionar versiones<small>Publicaciones conservadas y un borrador editable.</small></span><span>→</span></a><a href="#reportes"><span>Preparar un reporte<small>Elegir plantilla y guardar una fecha de corte.</small></span><span>→</span></a></div></article><article class="card"><h2>Qué ha ocurrido</h2><ul class="list"><li>Última recepción de ejemplo<small>${data().date} · Hora de Bolivia</small></li><li>La versión ${version} está ${version === 'v2' ? 'recibiendo respuestas' : 'cerrada'}<small>${version === 'v2' ? 'Su enlace público admite nuevos envíos.' : 'Sus resultados siguen disponibles.'}</small></li><li>Hay un borrador para la próxima versión<small>Editar el borrador conserva las versiones publicadas.</small></li></ul><details><summary>¿Encuesta, versión o borrador?</summary><p>La encuesta agrupa el trabajo. Una versión publicada conserva las preguntas que produjeron sus respuestas. El borrador permite preparar cambios para una publicación futura.</p></details></article></div>`;
  }
  function versions() {
    return heading('ESTRUCTURA DE LA ENCUESTA','Versiones y borradores','Las versiones conservan sus propios datos. Abre una para trabajar con ella.',button('new-draft','＋ Nuevo borrador','primary')) + `<article class="card"><div class="card-top"><h2>Publicadas</h2><span class="pill">No se mezclan sus respuestas</span></div><div class="table-wrap"><table><caption>Versiones de ${title()}</caption><thead><tr><th>Versión</th><th>Estado</th><th>Envíos</th><th>Acciones</th></tr></thead><tbody>${['v2','v1'].map(v => `<tr><td><strong>${v}</strong>${v === version ? ' · Seleccionada' : ''}</td><td><span class="pill ${v === 'v2' ? 'good' : ''}">${v === 'v2' ? 'En recepción · Activa' : 'Cerrada'}</span></td><td>${fixtures[survey][v].total}</td><td><button class="text-button" data-version="${v}">Abrir versión →</button></td></tr>`).join('')}</tbody></table></div></article><article class="card section-gap"><div class="card-top"><h2>Próxima versión</h2><span class="pill warn">Borrador</span></div><p>Cambios de preguntas pendientes de publicación. Aún no tiene respuestas.</p><div class="actions">${button('edit','Editar borrador','primary')}${button('duplicate','Crear variante como otra encuesta')}</div><details><summary>¿Cuál es la diferencia?</summary><p>Una versión continúa la misma encuesta y mantiene sus respuestas separadas. Una variante crea otra encuesta cuando cambia el propósito o el público; conserva una referencia a su origen.</p></details></article>`;
  }
  function responses() {
    return heading('DATOS RECIBIDOS','Respuestas','Consulta los envíos de esta versión y revisa su origen desde aquí.',`<div class="actions"><a class="secondary" href="#importar">Importar respuestas</a>${button('export-data','Exportar datos','primary')}</div>`) + `<div class="toolbar"><label>Buscar referencia <input id="response-search" type="search" placeholder="Ej. DEMO-001"></label><span class="pill">${version} · ${data().total} envíos ficticios</span></div><article class="card"><h2>Últimos envíos de ejemplo</h2><div class="table-wrap"><table><caption>Extracto de 3 filas; no muestra datos de contacto.</caption><thead><tr><th>Referencia</th><th>Recepción</th><th>Procedencia</th><th>Origen</th><th>Detalle</th></tr></thead><tbody id="response-rows">${['Web','Excel','CSV'].map((via,i) => `<tr data-reference="DEMO-00${i+1}"><td>DEMO-00${i+1}</td><td>${data().date}</td><td>${via}</td><td><span class="pill ${i === 2 ? 'warn' : 'good'}">${i === 2 ? 'Sin verificar' : 'Real revisado'}</span></td><td>${button('response','Ver envío')}</td></tr>`).join('')}</tbody></table></div><p id="search-empty" hidden>No hay referencias coincidentes en este extracto.</p></article><div class="note section-gap"><strong>Clasificar sin repetir trabajo</strong>La propuesta agrupa revisiones y conserva quién las hizo. Importar no clasifica automáticamente una respuesta como real.</div>`;
  }
  function results() {
    const real = scope === 'REAL';
    return heading('EXPLORACIÓN DE LA VERSIÓN','Resultados','Gráficos y explicaciones sobre la selección visible. Los reportes guardados conservan su propio corte.',`<div class="actions">${button('refresh','↻ Actualizar vista')}<a class="primary" href="#reportes">Crear reporte →</a></div>`) + `<div class="toolbar"><label>Origen <select id="origin"><option value="REAL" ${real ? 'selected' : ''}>Real revisado</option><option value="NO_DECLARADO" ${!real ? 'selected' : ''}>Sin verificar</option></select></label><span class="pill">${version} · Todo el período · ${real ? data().real : data().unknown} envíos</span></div><div class="note"><strong>Vista de ejemplo · Actualización automática propuesta</strong>Último corte mostrado: ${data().date} (America/La_Paz). La maqueta no recibe datos en vivo. En la implementación, el servidor actualizará los resultados y conservará la selección.</div>${real ? `<div class="grid"><article class="card"><div class="card-top"><h2>${survey === 'titulados' ? 'Experiencia laboral' : 'Expectativa de contratación'}</h2><span class="pill">Descriptivo</span></div><p>Porcentaje sobre ${data().base} envíos con respuesta. Sin respuesta: 0 · No aplica: 0.</p>${chart()}<details open><summary>¿Cómo leo este gráfico?</summary><p>La primera categoría reúne ${data().bars[0][1]} de ${data().base} envíos: 60%. Describe esta selección; no demuestra la situación de toda la población.</p></details><div class="actions">${button('export-chart','Exportar gráfico')}</div></article><article class="card"><div class="card-top"><h2>¿Hay relación entre dos respuestas?</h2><span class="pill warn">Falta revisión</span></div><p><strong>Chi-cuadrado</strong> compara cómo se distribuyen dos variables categóricas. El cruce se elige de forma explícita; no se buscan automáticamente resultados significativos.</p><div class="note warn section-gap"><strong>Prueba sin ejecutar</strong>Este ejemplo no documenta independencia de las unidades. El tamaño de la base no la demuestra. Por eso p, decisión y V de Cramér permanecen ausentes.</div><p>Las frecuencias y el gráfico descriptivo pueden consultarse mientras se revisa la aplicabilidad.</p><div class="actions">${button('cross','Elegir variables del cruce','primary')}${button('requirements','Ver qué falta')}</div><details><summary>¿Qué significa cuando sí puede calcularse?</summary><p>Una p menor al umbral registrado aporta evidencia de asociación bajo los supuestos del contraste. No demuestra causa y efecto. Una p mayor no prueba que las variables sean independientes.</p></details></article></div>` : `<article class="card"><h2>Origen sin verificar</h2><p>${data().unknown} envíos pendientes de revisión. Permanecen separados de los reales y no se presentan como resultados oficiales.</p><div class="skeleton">Estado vacío de demostración: este prototipo no incluye una distribución para este grupo. No se rellena con ceros.</div><div class="actions"><a class="primary" href="#respuestas">Revisar los envíos →</a></div></article>`}`;
  }
  function importer() {
    const steps = `<ol class="steps" aria-label="Progreso de importación">${['Archivo','Revisar','Confirmar'].map((s,i) => `<li class="${i === importStep ? 'current' : ''}" ${i === importStep ? 'aria-current="step"' : ''}>${i+1}. ${s}</li>`).join('')}</ol>`;
    let content;
    if (importStep === 0) content = `<article class="card"><h2>Trae tus respuestas a ${version}</h2><p>CSV o Excel. La encuesta y la versión ya están elegidas.</p><div class="dropzone"><strong>Prueba el recorrido con un archivo de ejemplo</strong><p>La maqueta no carga archivos del equipo. Usaremos «respuestas-ejemplo.xlsx».</p>${button('sample-file','Usar archivo de ejemplo →','primary')}</div><details><summary>¿Qué hará el sistema?</summary><p>Propondrá hoja, encabezados y correspondencias cuando sean inequívocos. Las decisiones ambiguas se mostrarán para resolverlas; el original siempre se conservará.</p></details></article>`;
    else if (importStep === 1) content = `<article class="card"><div class="file-label">▤ respuestas-ejemplo.xlsx · Hoja «Respuestas» · 3 filas de ejemplo</div><div class="card-top"><h2>Revisa lo que detectamos</h2><span class="pill good">3 columnas reconocidas</span></div><p>Propuestas automáticas visibles antes de incorporar datos.</p><div class="table-wrap"><table><caption>Correspondencias propuestas para esta versión</caption><thead><tr><th>Columna del archivo</th><th>Pregunta o campo</th><th>Resultado</th></tr></thead><tbody><tr><td>consentimiento</td><td>Consentimiento académico · «SI»</td><td class="row-ok">3 valores reconocidos</td></tr><tr><td>q_opcion</td><td>Pregunta de selección única</td><td class="row-ok">2 valores compatibles</td></tr><tr><td>q_opcion · fila 4</td><td>${corrected ? 'Categoría A' : 'Valor «A?»'}</td><td class="${corrected ? 'row-ok' : 'row-warn'}">${corrected ? 'Correspondencia revisada' : 'Necesita tu elección'}</td></tr><tr><td>fecha_externa</td><td>Fecha de la fuente</td><td class="row-ok">Formato con zona reconocido</td></tr></tbody></table></div><div class="note ${corrected ? '' : 'warn'} section-gap"><strong>${corrected ? 'Discrepancia resuelta en la copia de trabajo' : 'Una discrepancia concreta'}</strong>${corrected ? 'La propuesta relaciona «A?» con Categoría A. El archivo original se conserva.' : '«A?» no coincide con una opción. Elige una correspondencia; no se cambia silenciosamente.'}</div><label>Correspondencia para «A?» <select id="mapping"><option value="">Sin resolver</option><option value="A" ${corrected ? 'selected' : ''}>Relacionar con Categoría A</option></select></label><div class="actions"><button class="secondary" data-action="import-back">← Archivo</button><button class="primary" data-action="import-next" ${!corrected ? 'disabled' : ''}>Revisar confirmación →</button></div></article>`;
    else content = `<article class="card"><h2>Todo listo para revisar la incorporación</h2><p>3 envíos de ejemplo → ${title()} → ${version}. Sin coincidencias de lote en este ejemplo. Origen inicial: sin verificar.</p><label class="check"><input type="checkbox" id="consent-check" ${consent ? 'checked' : ''}><span>Revisé las correspondencias y el consentimiento registrado en la fuente.<small>Esta aceptación registra la revisión. No reemplaza un consentimiento ausente ni concede aprobación institucional.</small></span></label><details><summary>¿Y si el archivo no tiene evidencia de consentimiento?</summary><p>Se informa qué falta y se conserva el lote para resolverlo. Si la documentación exige respaldo escrito, se solicita allí, una sola vez, y queda disponible para reutilizar su referencia cuando corresponda.</p></details><p class="section-gap">El registro automático conservará actor, fecha, archivo, correspondencias y aceptación. No se exige una justificación rutinaria en este ejemplo.</p><div class="actions">${button('import-review','← Revisar columnas')}<button class="primary" data-action="confirm-import" ${!consent || version === 'v1' ? 'disabled' : ''}>Simular incorporación de 3 envíos</button></div></article>`;
    return heading('ASISTENTE DE IMPORTACIÓN','Importar respuestas','Tres pasos: detectar, resolver lo que no cuadra y confirmar.') + (version === 'v1' ? `<div class="note warn"><strong>Esta versión está cerrada</strong>Puedes revisar el ejemplo, pero no incorporar envíos. Elige v2 para completar la simulación.</div>` : '') + steps + content;
  }
  function reports() {
    return heading('SALIDAS DE ESTA VERSIÓN','Reportes y plantillas','Selecciona una presentación y guarda los resultados con su fecha de corte.',button('template','＋ Crear plantilla')) + `<div class="grid"><article class="card"><h2>Crear reporte de ${version}</h2><p>Origen real revisado · Todo el período · Corte de ejemplo: ${data().date}.</p><label class="check"><input type="checkbox" checked disabled><span>Resumen y contexto</span></label><label class="check"><input type="checkbox" checked><span>Gráficos y sus tablas</span></label><label class="check"><input type="checkbox"><span>Cruces y estado del chi-cuadrado</span></label><div class="actions">${button('report-preview','Previsualizar reporte','primary')}${button('formats','Formatos de descarga')}</div><p>Propuesta: PDF, gráficos PNG/SVG y datos CSV/Excel. Esta maqueta no genera archivos.</p></article><article class="card"><div class="card-top"><h2>Plantilla: informe breve</h2><span class="pill">Reutilizable</span></div><div class="report-preview"><p class="eyebrow">ESTUDIO DE MERCADO · ${version}</p><h3>${title()}</h3><p>Vista previa ilustrativa · Datos ficticios</p><div class="report-lines"></div><div class="report-lines short"></div>${chart()}</div><div class="actions">${button('template','Editar esta plantilla')}</div></article></div><div class="note section-gap"><strong>Los reportes guardados no cambian al llegar nuevas respuestas</strong>Conservan versión, selección, fecha de corte y resultados. Puedes generar otro con los datos actualizados.</div>`;
  }
  const screens = {encuestas:catalog,resumen:summary,versiones:versions,respuestas:responses,resultados:results,importar:importer,reportes:reports};
  function render(focus = false) {
    const raw = location.hash.slice(1) || 'encuestas';
    const page = Object.hasOwn(screens,raw) ? raw : 'encuestas';
    const home = page === 'encuestas';
    $('aside').hidden = home;
    $('.shell').style.gridTemplateColumns = home ? 'minmax(0,1fr)' : '';
    $('#workspace-context').hidden = home;
    $('#side-title').textContent = title();
    $('#selected-survey').textContent = title();
    $('#crumb-survey').textContent = home ? '' : title();
    $('#crumb-page').textContent = home ? '' : names[page];
    $('#version').value = version;
    $('#version-status').textContent = version === 'v2' ? 'En recepción' : 'Cerrada';
    $('#version-status').className = `pill ${version === 'v2' ? 'good' : ''}`;
    document.querySelectorAll('#workspace-nav a').forEach(a => {
      if (a.hash === `#${page}`) a.setAttribute('aria-current','page'); else a.removeAttribute('aria-current');
    });
    $('#view').innerHTML = screens[page](); // Solo plantillas y fixtures locales; no admite texto de participantes.
    document.title = `${home ? 'Mis encuestas' : `${names[page]} · ${title()} · ${version}`} · Prototipo UMSS`;
    if (focus) $('#contenido').focus({preventScroll:true});
  }
  const dialogs = {
    guide:['Tu recorrido','1. Abre una encuesta. 2. Revisa sus versiones. 3. Entra en Resultados. 4. Prueba la importación con el archivo de ejemplo. 5. Previsualiza un reporte. La versión seleccionada se conserva entre secciones.'],
    create:['Crear encuesta','La pantalla futura permitirá comenzar en blanco, usar una plantilla de cuestionario o crear una variante. Esta acción no crea encuestas en la maqueta.'],
    share:['Compartir esta versión', 'Se mostrarán el enlace principal (versión activa) y el enlace fijo de la versión elegida. Si está cerrada, se explicará que no recibe respuestas. Aquí no se genera un enlace público real.'],
    'new-draft':['Continuar el borrador existente','Esta encuesta de ejemplo ya tiene un borrador. El sistema abriría ese borrador para editarlo; conservaría las versiones publicadas.'],
    edit:['Editar la próxima versión','Aquí aparecería el editor de secciones y preguntas, con vista previa del formulario. Publicar y activar serán acciones explícitas. Las respuestas de versiones anteriores se conservarán.'],
    duplicate:['Crear una variante','Se copiaría la estructura a otra encuesta, con un nombre y una referencia a la encuesta de origen. No se copiarían respuestas ni aprobaciones.'],
    response:['Detalle del envío','Se mostrarían las respuestas originales, estados, procedencia e historial de revisión. La clasificación de origen y las correcciones quedarían registradas. Aquí solo hay referencias ficticias.'],
    'export-data':['Exportar datos de esta selección','La propuesta ofrecerá CSV y Excel con versión, filtros y fecha de corte. Los contactos y textos originales quedan fuera de las matrices ordinarias. En el paso 1 no se generan descargas.'],
    'export-chart':['Exportar este gráfico','La propuesta ofrecerá PNG, SVG y una página PDF con título, selección, base, corte y tabla equivalente. Esta maqueta no genera archivos.'],
    cross:['Seleccionar un cruce','Elegirás una pregunta para filas y otra para columnas. El servidor comprobará compatibilidad, independencia documentada, variación y frecuencias esperadas. Se mostrarán la tabla y las razones cuando una prueba no sea aplicable.'],
    requirements:['Qué falta para esta prueba','El ejemplo no tiene evidencia de independencia de las unidades. La revisión metodológica se documentaría una vez por ámbito válido y se referenciaría en los cruces compatibles. Una casilla por sí sola no demuestra independencia.'],
    template:['Editar plantilla de reporte','Podrás elegir título, introducción, secciones, orden y presencia de gráficos. Los resultados vendrán del análisis y no podrán editarse como texto. Las plantillas de reporte se distinguirán de las plantillas para crear cuestionarios.'],
    formats:['Formatos previstos','Reporte: PDF. Gráfico: PNG o SVG. Datos: CSV o Excel. La elección incluirá versión, filtros y corte. Son formatos de la propuesta; no se presentan como exportaciones ya implementadas.']
  };
  function show(titleText, body) {
    $('#dialog-title').textContent = titleText;
    $('#dialog-content').replaceChildren();
    const p = document.createElement('p'); p.textContent = body; $('#dialog-content').append(p);
    $('#action-dialog').showModal();
  }
  let toastTimer;
  function notify(message) { clearTimeout(toastTimer); $('#status').textContent = message; toastTimer = setTimeout(() => { $('#status').textContent = ''; },6500); }
  document.addEventListener('click', event => {
    const surveyButton = event.target.closest('[data-survey]');
    if (surveyButton) { survey = surveyButton.dataset.survey; version = 'v2'; scope = 'REAL'; importStep = 0; corrected = false; consent = false; location.hash = 'resumen'; return; }
    const versionButton = event.target.closest('[data-version]');
    if (versionButton) { version = versionButton.dataset.version; location.hash = 'resumen'; return; }
    if (event.target.closest('.close,.close-dialog')) { $('#action-dialog').close(); return; }
    const control = event.target.closest('[data-action]');
    if (!control) return;
    const action = control.dataset.action;
    if (dialogs[action]) { show(...dialogs[action]); return; }
    if (action === 'sample-file') { importStep = 1; corrected = false; consent = false; render(true); }
    if (action === 'import-back') { importStep = 0; render(true); }
    if (action === 'import-review') { importStep = 1; render(true); }
    if (action === 'import-next' && corrected) { importStep = 2; render(true); }
    if (action === 'confirm-import' && consent && corrected && version === 'v2') {
      show('Recorrido completado','Así finalizaría la importación y aparecerían accesos a los envíos y sus resultados. Esta es una simulación: no se importaron datos ni se cambió ningún contador.');
    }
    if (action === 'refresh') notify('Vista de ejemplo revisada. No hay conexión en vivo; se conserva el mismo corte ficticio.');
    if (action === 'report-preview') {
      const charts = $('#view input:not(:disabled)')?.checked;
      show('Vista previa del reporte', `${title()} · ${version} · Origen real revisado · Base: ${data().base} envíos · Corte: ${data().date}. ${charts ? 'Incluye gráficos y tablas.' : 'Gráficos desmarcados.'} Los cruces seleccionados mostrarían su estado de aplicabilidad. En esta maqueta no se guarda ni exporta el reporte.`);
    }
  });
  document.addEventListener('change', event => {
    if (event.target.id === 'version') { version = event.target.value; importStep = 0; corrected = false; consent = false; render(); notify(`Ahora estás viendo ${version}. Se conserva la sección elegida.`); }
    if (event.target.id === 'origin') { scope = event.target.value; render(); $('#origin').focus(); }
    if (event.target.id === 'mapping') { corrected = event.target.value === 'A'; render(); $('#mapping').focus(); }
    if (event.target.id === 'consent-check') { consent = event.target.checked; $('[data-action="confirm-import"]').disabled = !consent || version === 'v1'; }
  });
  document.addEventListener('input', event => {
    if (event.target.id !== 'response-search') return;
    const query = event.target.value.trim().toUpperCase(); let visible = 0;
    document.querySelectorAll('[data-reference]').forEach(row => { row.hidden = !row.dataset.reference.includes(query); if (!row.hidden) visible++; });
    $('#search-empty').hidden = visible > 0;
  });
  window.addEventListener('hashchange', () => render(true));
  render();
})();
