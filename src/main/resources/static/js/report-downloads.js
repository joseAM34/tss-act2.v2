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
            const x = option.xAxis[0].data, y = option.yAxis[0].data;
            // Full labels in exports, without the interactive chart's truncation.
            const width = Math.max(1200, x.length * 130 + 300);
            const header = wrap(title, Math.floor(width / 10));
            const footer = [...metadata, note].map(line => wrap(line, Math.floor(width / 8))).join('\n');
            const top = header.split('\n').length * 24 + 30;
            const bottom = footer.split('\n').length * 19 + 25;
            const labelLines = Math.max(1, ...x.map(v => Math.ceil(Array.from(String(v)).length / 16)));
            const rowHeight = Array.isArray(y) ? Math.max(38, ...y.map(v => Math.ceil(Array.from(String(v)).length / 26) * 16 + 12)) : 38;
            const plotHeight = Math.max(420, Array.isArray(y) ? y.length * rowHeight : 420);
            const height = top + plotHeight + labelLines * 16 + 70 + bottom;
            if (width > 12000 || height > 12000 || width * height > 6000000) throw new Error('Este gráfico supera el tamaño de imagen admitido. Usa las tablas CSV/Excel del reporte.');
            host = document.createElement('div'); host.className = 'report-export-renderer'; document.body.append(host);
            exported = echarts.init(host, null, {renderer: type === 'svg' ? 'svg' : 'canvas', width, height});
            option.animation = false; option.backgroundColor = '#fff'; option.tooltip = {show: false};
            option.series.forEach(series => { if (series.type === 'bar') series.label = {show: true, position: 'top'}; });
            option.grid = [{left: Array.isArray(y) ? 230 : 70, right: 35, top, bottom: bottom + labelLines * 16 + 70, containLabel: true}];
            option.xAxis[0].axisLabel = {interval: 0, rotate: 0, formatter: value => wrap(value, 16), fontSize: 12};
            option.yAxis[0].axisLabel = {formatter: value => wrap(value, 26), fontSize: 12};
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
