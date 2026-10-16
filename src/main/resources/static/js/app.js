(() => {
  'use strict';

  const form = document.querySelector('[data-survey-form]');
  if (form) {
    if (form.dataset.previewReadonly === 'true') form.addEventListener('submit', event => event.preventDefault());
    const questions = Array.from(form.querySelectorAll('[data-question-id]'));
    const questionById = new Map(questions.map(question => [question.dataset.questionId, question]));
    const sections = [];
    for (const question of questions) {
      const heading = question.closest('.question-block').querySelector('.form-section-heading');
      if (heading) {
        sections.push({
          heading,
          link: document.querySelector(`.survey-sidebar a[href="#${heading.id}"]`),
          questions: []
        });
      }
      if (sections.length) sections[sections.length - 1].questions.push(question);
    }

    const answersOf = question => {
      if (!question || question.hidden) return [];
      return Array.from(question.querySelectorAll('input, select, textarea'))
        .filter(input => !input.disabled && ((input.type !== 'checkbox' && input.type !== 'radio') || input.checked))
        .map(input => input.value);
    };

    const updateQuestions = () => {
      // Definiciones ordenadas: cada condición hace referencia a una pregunta anterior.
      for (const question of questions) {
        const parentId = question.dataset.dependsOn;
        const parentAnswers = answersOf(questionById.get(parentId));
        const active = !parentId || question.dataset.dependsValue.split('|').some(value => parentAnswers.includes(value));
        question.hidden = !active;
        const required = active && question.dataset.required === 'true';
        for (const input of question.querySelectorAll('input, select, textarea')) {
          input.disabled = !active;
          // Cada grupo tiene su propio límite; required en cada casilla exigiría marcarlas todas.
          input.required = required && input.type !== 'checkbox';
        }
        const counter = question.querySelector('[data-selection-count]');
        if (counter) {
          const checkboxes = Array.from(question.querySelectorAll('input[type="checkbox"]'));
          const selected = checkboxes.filter(input => input.checked).length;
          const min = Number(question.dataset.minSelections) || 0;
          const max = Number(question.dataset.maxSelections) || 0;
          const minimum = required ? Math.max(1, min) : min;
          let error = '';
          if (active && ((required || selected > 0) && selected < minimum)) {
            error = min > 0 && min === max
              ? `Selecciona exactamente ${min} ${min === 1 ? 'opción' : 'opciones'}${required ? '.' : ' o deja la pregunta vacía.'}`
              : `Selecciona al menos ${minimum} ${minimum === 1 ? 'opción' : 'opciones'}.`;
          } else if (active && max > 0 && selected > max) {
            error = min > 0 && min === max
              ? `Selecciona exactamente ${max} ${max === 1 ? 'opción' : 'opciones'}.`
              : `Selecciona como máximo ${max} ${max === 1 ? 'opción' : 'opciones'}.`;
          }
          for (const input of checkboxes) input.setCustomValidity('');
          if (checkboxes.length) checkboxes[0].setCustomValidity(error);
          counter.textContent = `${selected} ${selected === 1 ? 'opción seleccionada' : 'opciones seleccionadas'}`
            + (min > 0 && min === max
              ? ` · ${required ? 'Selecciona' : 'Si respondes, selecciona'} exactamente ${min}`
              : (max > 0 ? ` · Máximo: ${max}` : '') + (minimum > 0 ? ` · Mínimo: ${minimum}` : ''));
          counter.classList.toggle('over-limit', error !== '');
        }
      }
      for (const section of sections) {
        const active = section.questions.some(question => !question.hidden);
        section.heading.hidden = !active;
        if (section.link) section.link.hidden = !active;
      }
    };

    form.addEventListener('change', updateQuestions);
    form.addEventListener('input', updateQuestions);
    updateQuestions();

    const errorSummary = form.querySelector('[data-error-summary]');
    if (errorSummary) errorSummary.focus();
  }

  const passwordToggle = document.querySelector('[data-password-toggle]');
  if (passwordToggle) {
    const password = document.getElementById(passwordToggle.getAttribute('aria-controls'));
    if (password) {
      passwordToggle.hidden = false;
      passwordToggle.addEventListener('click', () => {
        const show = password.type === 'password';
        password.type = show ? 'text' : 'password';
        passwordToggle.textContent = show ? 'Ocultar' : 'Mostrar';
        passwordToggle.setAttribute('aria-pressed', String(show));
      });
    }
  }
})();
