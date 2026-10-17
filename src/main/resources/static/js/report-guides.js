'use strict';
(() => {
  if(!document.querySelector('[data-report-customizer], [data-report-url]'))return;
  const footer=document.querySelector('.site-footer .footer-inner');if(!footer)return;
  const button=document.createElement('button');button.type='button';button.className='report-guide-toggle';button.setAttribute('aria-pressed','false');button.textContent='Mostrar guías de lectura';
  button.addEventListener('click',()=>{const on=document.body.classList.toggle('report-guides');button.setAttribute('aria-pressed',String(on));button.textContent=on?'Ocultar guías de lectura':'Mostrar guías de lectura';});footer.append(button);
})();
