const test=require('node:test'),assert=require('node:assert/strict');
const {reportQuestionKind,reportVisibleQuestions,reportIncludeVisible}=require('../../main/resources/static/js/report-question-filters.js');
test('distingue opciones, números nativos, cantidades extraídas y textos pendientes',()=>{
 assert.equal(reportQuestionKind({type:'checkbox'}),'options');assert.equal(reportQuestionKind({original_type:'number'}),'numeric');
 assert.equal(reportQuestionKind({original_type:'textarea',numeric:{n:3},converted:3}),'numeric');assert.equal(reportQuestionKind({original_type:'textarea'}),'open');
});
test('selección masiva afecta solo preguntas visibles y admite filtros combinados o vacíos',()=>{
 const questions=['a','b','c'].map(id=>({id,included:false}));const variables=[{question_id:'a',type:'checkbox'},{question_id:'b',numeric:{n:2}},{question_id:'c',original_type:'text'}];
 const visible=reportVisibleQuestions(questions,variables,['numeric']);assert.deepEqual(visible,['b']);reportIncludeVisible(questions,visible,true);assert.deepEqual(questions.map(q=>q.included),[false,true,false]);
 assert.deepEqual(reportVisibleQuestions(questions,variables,['options','open']),['a','c']);reportIncludeVisible(questions,['a','c'],true);reportIncludeVisible(questions,['b'],false);assert.deepEqual(questions.map(q=>q.included),[true,false,true]);assert.deepEqual(reportVisibleQuestions(questions,variables,[]),[]);
});
