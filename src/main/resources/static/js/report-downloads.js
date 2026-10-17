'use strict';
window.ReportDownloads = (() => {
  const wrap = (value, width = 100) => {
    const chars = Array.from(String(value ?? ''));
    const lines = [];
    for (let i = 0; i < chars.length; i += width) lines.push(chars.slice(i, i + width).join(''));
    return lines.join('\n');
  };
  function attach(main, report, entries) {
    const status = main.querySelector('[data-export-status]');
    const print = main.querySelector('[data-report-print]');
    const say = text => { status.textContent = text; };
    const c = report.content, r = c.analysis.result, context = c.context;
    const metadata = [
      `${r.title} · Versión ${r.instrument_version} · ${r.origin} · ${r.mode}`,
      `Recepción: ${context.from_date ?? 'sin inicio'} a ${context.until_date ?? 'sin final'} · ${context.timezone}`,
      `Corte: ${context.cutoff_utc} · ${r.submission_count} envíos · ${r.protocol}`,
      `Reporte: ${report.id} · Huella: ${report.logical_hash}`,
      `Fuente: ${c.analysis.source_hash} · Unidad: ${context.unit_of_analysis}`
    ];
    entries.forEach(({element, chart, title, note}, index) => {
      const controls = document.createElement('div'); controls.className = 'draft-actions report-export-controls';
      for (const type of ['png', 'svg']) {
        const button = document.createElement('button'); button.type = 'button'; button.className = 'button button-outline';
        button.textContent = `Descargar ${type.toUpperCase()}`; button.setAttribute('aria-label', `${button.textContent}: ${title}`);
        button.addEventListener('click', () => {
          let exported, host;
          try {
            const option = chart.getOption();
            const circular = option.series.some(series => series.type === 'pie');
            const radial = option.series.some(series => series.type === 'radar');
            const x = Array.isArray(option.xAxis?.[0]?.data) ? option.xAxis[0].data : [];
            const y = Array.isArray(option.yAxis?.[0]?.data) ? option.yAxis[0].data : null;
            const names = circular ? option.series[0].data.map(item => item.name) : (option.legend?.[0]?.data || option.series.filter(series => series.name).map(series => series.name));
            // Full labels in exports, without the interactive chart's truncation.
            const width = Math.max(1200, x.length * 130 + 300);
            const header = wrap(title, Math.floor(width / 10));
            const footer = [...metadata, note].map(line => wrap(line, Math.floor(width / 8))).join('\n');
            const top = header.split('\n').length * 24 + 30;
            const bottom = footer.split('\n').length * 19 + 25;
            const labelLines = Math.max(1, ...x.map(v => Math.ceil(Array.from(String(v)).length / 16)));
            const rowHeight = Array.isArray(y) ? Math.max(38, ...y.map(v => Math.ceil(Array.from(String(v)).length / 26) * 16 + 12)) : 38;
            const legendLines = Math.max(1, ...names.map(name => Math.ceil(Array.from(String(name)).length / (circular ? 38 : 32))));
            const legendHeight = names.length ? (circular ? names.length : Math.ceil(names.length / 3)) * (legendLines * 18 + 12) : 0;
            const radarLines = radial ? Math.max(1,...option.radar[0].indicator.map(axis=>Math.ceil(Array.from(String(axis.name)).length/26))) : 1;
            const plotHeight = Math.max(420,radial?radarLines*48:circular ? legendHeight : Array.isArray(y) ? y.length * rowHeight : 420);
            const height = top + plotHeight + labelLines * 16 + 70 + bottom + (circular ? 0 : legendHeight);
            if (width > 12000 || height > 12000 || width * height > 6000000) throw new Error('Este gráfico supera el tamaño de imagen admitido. Usa las tablas CSV/Excel del reporte.');
            host = document.createElement('div'); host.className = 'report-export-renderer'; document.body.append(host);
            exported = echarts.init(host, null, {renderer: type === 'svg' ? 'svg' : 'canvas', width, height});
            option.animation = false; option.backgroundColor = '#fff'; option.tooltip = {show: false};
            option.series.forEach(series => { if (series.type === 'bar') series.label = {show: true, position: Array.isArray(y) && option.yAxis?.[0]?.type === 'category' ? 'right' : 'top'}; });
            if (circular) {
              option.series.forEach(series => {
                const outer = Math.min(width * .2, plotHeight * .35);
                series.radius = Array.isArray(series.radius) ? [outer * .55, outer] : outer;
                series.center = [width * .35, top + plotHeight / 2];
                series.label = {show: true, formatter: item => wrap(item.name, 16) + ': ' + item.value, fontSize: 12};
              });
              option.legend = [{type: 'plain', orient: 'vertical', right: 25, top: top + 10, width: 350, formatter: value => wrap(value, 38), textStyle: {fontSize: 12}}];
            } else if(radial) {
              option.radar[0]={...option.radar[0],center:[width/2,top+plotHeight/2],radius:Math.min(width*.26,plotHeight*.33),axisName:{formatter:value=>wrap(value,26),fontSize:12,lineHeight:14}};
            } else {
              option.grid = [{left: Array.isArray(y) ? 230 : 70, right: 35, top, bottom: bottom + labelLines * 16 + 70 + legendHeight, containLabel: true}];
              option.xAxis[0].axisLabel = {interval: 0, rotate: 0, formatter: value => wrap(value, 16), fontSize: 12};
              option.yAxis[0].axisLabel = {formatter: value => wrap(value, 26), fontSize: 12};
              if (option.legend?.length) option.legend[0] = {...option.legend[0], type: 'plain', left: 25, right: 25, bottom: bottom + 10, formatter: value => wrap(value, 32)};
            }
            if (option.visualMap?.length) option.visualMap[0] = {...option.visualMap[0], bottom: bottom + 10};
            option.graphic = [
              {type: 'text', left: 25, top: 15, style: {text: header, fill: '#17324d', font: 'bold 18px sans-serif', lineHeight: 24}},
              {type: 'text', left: 25, bottom: 15, style: {text: footer, fill: '#20384d', font: '13px sans-serif', lineHeight: 19}}
            ];
            exported.setOption(option);
            const link = document.createElement('a');
            link.download = `reporte-${report.id}-grafico-${index + 1}.${type}`;
            link.href = exported.getDataURL({type, pixelRatio: 2, backgroundColor: '#fff'});
            document.body.append(link); link.click(); link.remove();
            say(`Descarga solicitada: gráfico ${index + 1} en ${type.toUpperCase()}, con contexto y corte del reporte.`);
          } catch (error) { say(error.message || 'No se pudo exportar el gráfico. Conserva el reporte y vuelve a intentarlo.'); }
          finally { exported?.dispose(); host?.remove(); }
        });
        controls.append(button);
      }
      element.after(controls);
    });
    if (print) {
      print.disabled = false;
      print.addEventListener('click', () => {
        say('En el diálogo de impresión elige «Guardar como PDF». Se utiliza este reporte guardado.');
        window.print();
      });
    }
    const resize = () => entries.forEach(e => e.chart.resize());
    window.addEventListener('beforeprint', resize);
    window.addEventListener('afterprint', resize);
    say(entries.length ? 'Reporte listo: puedes guardar PDF, imágenes o datos del mismo corte.' : 'Reporte listo: puedes guardar PDF y datos. Esta plantilla no incluye gráficos con valores.');
  }
  return {attach};
})();
