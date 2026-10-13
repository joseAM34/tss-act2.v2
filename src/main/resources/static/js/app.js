(() => {
  'use strict';

  const form = document.querySelector('[data-survey-form]');
  if (form) {
    const questions = Array.from(form.querySelectorAll('[data-question-id]'));
    const questionById = new Map(questions.map(question => [question.dataset.questionId, question]));

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
        const active = !parentId || answersOf(questionById.get(parentId)).includes(question.dataset.dependsValue);
        question.hidden = !active;
        const required = active && question.dataset.required === 'true';
        for (const input of question.querySelectorAll('input, select, textarea')) {
          input.disabled = !active;
          // Un grupo checkbox se valida en el servidor; required en cada casilla exigiría marcarlas todas.
          input.required = required && input.type !== 'checkbox';
        }
        const counter = question.querySelector('[data-selection-count]');
        if (counter) {
          const selected = question.querySelectorAll('input[type="checkbox"]:checked').length;
          const min = Number(question.dataset.minSelections) || 0;
          const max = Number(question.dataset.maxSelections) || 0;
          counter.textContent = `${selected} ${selected === 1 ? 'opción seleccionada' : 'opciones seleccionadas'}`
            + (max > 0 ? ` · Máximo: ${max}` : '')
            + (min > 0 ? ` · Mínimo: ${min}` : '');
          counter.classList.toggle('over-limit', max > 0 && selected > max);
        }
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
