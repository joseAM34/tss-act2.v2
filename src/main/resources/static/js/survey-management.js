'use strict';

async function fetchSurveyBackup(url, data, request = fetch) {
  const response = await request(url, {method: 'POST', body: data, credentials: 'same-origin', cache: 'no-store'});
  const contentType = response.headers.get('Content-Type') || '';
  if (!response.ok || !contentType.startsWith('application/zip')) {
    let message = 'No se pudo descargar el respaldo. Recarga la pantalla y vuelve a intentarlo. La encuesta se conserva.';
    if (typeof DOMParser !== 'undefined') {
      const page = new DOMParser().parseFromString(await response.text(), 'text/html');
      const detail = page.querySelector('[role="alert"]')?.textContent?.trim();
      if (detail) message = detail;
    }
    throw new Error(message);
  }
  const id = response.headers.get('X-Survey-Backup-Id');
  if (!id || !/^[a-f0-9-]{36}$/i.test(id)) throw new Error('La descarga no contiene una referencia válida. La encuesta se conserva.');
  const blob = await response.blob();
  if (!blob.size) throw new Error('El respaldo está vacío. La encuesta se conserva.');
  const disposition = response.headers.get('Content-Disposition') || '';
  const filename = (disposition.match(/filename="([^"]+)"/)?.[1] || 'respaldo-encuesta.zip').replace(/[^a-zA-Z0-9_.-]/g, '_');
  return {id, blob, filename};
}

if (typeof module !== 'undefined') module.exports = {fetchSurveyBackup};
if (typeof document !== 'undefined') {
  const container = document.querySelector('[data-survey-deletion]');
  if (container) {
    const form = container.querySelector('[data-survey-backup]');
    const deletion = container.querySelector('[data-delete-with-backup]');
    const status = container.querySelector('[data-backup-status]');
    let busy = false;
    form.addEventListener('submit', async event => {
      event.preventDefault();
      if (busy) return;
      busy = true;
      const button = form.querySelector('button');
      button.disabled = true;
      deletion.hidden = true;
      deletion.elements.namedItem('confirm').checked = false;
      deletion.elements.namedItem('backupId').value = '';
      status.textContent = 'Preparando y descargando el respaldo completo…';
      try {
        const backup = await fetchSurveyBackup(form.action, new FormData(form));
        const url = URL.createObjectURL(backup.blob);
        const link = document.createElement('a');
        link.href = url; link.download = backup.filename;
        document.body.append(link); link.click(); link.remove();
        setTimeout(() => URL.revokeObjectURL(url), 60000);
        deletion.elements.namedItem('backupId').value = backup.id;
        deletion.hidden = false;
        status.textContent = 'Descarga preparada. Comprueba que el ZIP se guardó completo y confirma abajo para eliminar.';
      } catch (error) {
        status.textContent = error.message || 'No se pudo completar la descarga. La encuesta se conserva.';
      } finally {
        busy = false; button.disabled = false;
      }
    });
    deletion.addEventListener('submit', () => {
      deletion.querySelector('button').disabled = true;
      form.querySelector('button').disabled = true;
      status.textContent = 'Eliminando la encuesta respaldada…';
    });
  }
}
