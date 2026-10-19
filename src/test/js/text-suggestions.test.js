'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {mergeDictionaryTerms,canonicalDictionary}=require('../../main/resources/static/js/text-suggestions.js');
const {processedDictionaryItems}=require('../../main/resources/static/js/text-suggestions.js');
const {buildDictionaryPrompt}=require('../../main/resources/static/js/text-suggestions.js');
test('el prompt incluye pregunta y términos; los originales solo se incluyen expresamente',()=>{const base=buildDictionaryPrompt('¿Qué herramientas?',[{term:'ia'},{term:'inteligencia artificial'}]);assert(base.includes('¿Qué herramientas?'));assert(base.includes('ia\ninteligencia artificial'));assert(!base.includes('RESPUESTAS ORIGINALES'));assert(buildDictionaryPrompt('Pregunta',[],['Texto privado']).includes('Texto privado'));});
test('añadir procesadas excluye fallidas y no acepta Iowa como sinónimo de IA',()=>{const items=processedDictionaryItems([{term:'ia',processed:true,lookupResult:{status:'AMBIGUO'},proposedGroup:'iowa',proposedAliases:['iowa','ia']},{term:'pendiente',processed:false},{term:'inteligencia artificial',processed:true,lookupResult:{status:'ESTABLECIDO'},proposedGroup:'inteligencia artificial',proposedAliases:['ia','inteligencia artificial']}]);assert.equal(items.length,2);assert.equal(items[0].proposedGroup,'ia');assert.deepEqual(items[0].proposedAliases,['ia']);assert.deepEqual(items[1].proposedAliases,['ia','inteligencia artificial']);});
test('unir términos elimina grupos anteriores y conserva sus variantes y fuentes',()=>{
 const rules='IA=IA|AI\nInteligencia artificial=inteligencia artificial\nOtro=otro\n# Fuente: https://www.wikidata.org/wiki/Q11660';
 const merged=mergeDictionaryTerms(rules,['IA','Inteligencia artificial'],'Inteligencia artificial');
 assert(!merged.split('\n').some(line=>line.startsWith('ia=')));assert(merged.includes('otro=otro'));assert(merged.includes('# Fuente:'));const group=merged.split('\n').find(line=>line.startsWith('inteligencia artificial='));assert(group);assert.deepEqual(new Set(group.split('=')[1].split('|')),new Set(['ia','ai','inteligencia artificial']));
});
test('la normalización fusiona grupos conectados y conserva nombres técnicos distintos',()=>{const result=canonicalDictionary('IA=IA|Inteligencia Artificial\nInteligencia artificial=INTELIGENCIA ARTIFICIAL\nC++=C++\nC#=C#');assert.equal(result.split('\n').length,3);assert(result.includes('c++=c++'));assert(result.includes('c#=c#'));});
