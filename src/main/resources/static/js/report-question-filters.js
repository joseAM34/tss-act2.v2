 'use strict';
function reportQuestionKind(variable={}){
 if(variable.numeric!=null||variable.converted!=null||['number','integer'].includes(variable.original_type||variable.type))return 'numeric';
 if(['text','textarea','email','tel'].includes(variable.original_type||variable.type))return 'open';
 return 'options';
}
function reportVisibleQuestions(questions,variables,kinds){
 const selected=new Set(kinds),byId=new Map(variables.map(v=>[v.question_id,v]));
 return questions.filter(q=>selected.has(reportQuestionKind(byId.get(q.id)))).map(q=>q.id);
}
function reportIncludeVisible(questions,visible,included){const ids=new Set(visible);questions.forEach(q=>{if(ids.has(q.id))q.included=included;});}
if(typeof module!=='undefined')module.exports={reportQuestionKind,reportVisibleQuestions,reportIncludeVisible};
