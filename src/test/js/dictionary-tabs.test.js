'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {dictionaryModeAvailability}=require('../../main/resources/static/js/dictionary-tabs.js');
test('sin registro permite elegir ambos modos y el registro bloquea únicamente el contrario',()=>{
 assert.deepEqual(dictionaryModeAvailability('NONE'),{TERMS:true,NUMERIC:true});
 assert.deepEqual(dictionaryModeAvailability('TERMS'),{TERMS:true,NUMERIC:false});
 assert.deepEqual(dictionaryModeAvailability('NUMERIC'),{TERMS:false,NUMERIC:true});
});
