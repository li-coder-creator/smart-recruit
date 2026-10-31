const fs=require('fs'),path=require('path'),assert=require('assert'),crypto=require('crypto');
const stateFile=process.env.APPLICATION_ACCEPTANCE_STATE_FILE||'target/application-acceptance-final-state.json';
const state=JSON.parse(fs.readFileSync(stateFile));
assert(/^smart_recruit_acceptance_\d+$/.test(state.database));
assert(path.resolve(state.root).startsWith(path.resolve('target')+path.sep));
function statistics(file) {
    const result=JSON.parse(fs.readFileSync(file)),text=result.content.filter(x=>x.type==='text').map(x=>x.text).join('\n');
    const number=label=>{const match=text.match(new RegExp(label+':\\s*(\\d+)'));assert(match,label);return Number(match[1]);};
    return {requests:number('Total requests'),failedRequests:number('Failed requests'),assertions:number('Total assertions'),failedAssertions:number('Failed assertions'),durationSeconds:Number(text.match(/Duration:\s*([0-9.]+)s/)?.[1]),executedAt:fs.statSync(file).mtime.toISOString()};
}
const groups=Object.fromEntries(['A','B','C'].map(group=>[group,statistics(state.root+'/result-'+group+'.json')]));
for(const group of Object.values(groups)){assert.equal(group.failedRequests,0);assert.equal(group.failedAssertions,0);}
const totals=Object.values(groups).reduce((out,group)=>({requests:out.requests+group.requests,assertions:out.assertions+group.assertions,failedRequests:out.failedRequests+group.failedRequests,failedAssertions:out.failedAssertions+group.failedAssertions}),{requests:0,assertions:0,failedRequests:0,failedAssertions:0});
assert.equal(totals.requests,190);assert.equal(totals.assertions,418);
const audit=JSON.parse(fs.readFileSync(state.root+'/acceptance-audit.json'));assert.equal(audit.database,state.database);assert.equal(audit.sourceUnchanged,true);assert.equal(audit.initialEventAndHistoryVerified,true);assert.equal(audit.versionEventConsistencyVerified,true);assert.equal(audit.terminalCleanupVerified,true);assert.equal(audit.triggers.length,0);
const manifest=JSON.parse(fs.readFileSync('docs/postman/application-acceptance-regression.manifest.json'));
const exported=JSON.parse(fs.readFileSync('docs/postman/application-acceptance-regression.postman_collection.json'));
assert.equal(exported.item.reduce((count,folder)=>count+folder.item.length,0),190);
function sourceFiles(directory) {return fs.readdirSync(directory,{withFileTypes:true}).flatMap(entry=>entry.isDirectory()?sourceFiles(path.join(directory,entry.name)):entry.name.endsWith('.java')?[path.join(directory,entry.name)]:[]);}
const digest=crypto.createHash('sha256');for(const file of sourceFiles('src/main/java').sort()){digest.update(file.replaceAll('\\','/'));digest.update(fs.readFileSync(file));}
const diagnostics=[];
const first='target/application-acceptance-fixtures-1793177085158/result-A.json';
if(fs.existsSync(first))diagnostics.push({kind:'initial-fixture-contamination',...statistics(first),reason:'Intentionally invalid status fixture was included in the normal company legacy list. Fixture moved to separate rejected company; full A/B/C rerun uses a fresh schema.'});
const startup=state.root+'/result-A-startup-not-ready.json';
if(fs.existsSync(startup))diagnostics.push({kind:'startup-not-ready',...statistics(startup),reason:'One login connection failed before server readiness; no application write executed. Launcher now waits for startup log readiness.'});
const report={scope:'Application upgrade phase five',httpExecutionDate:'2026-10-28',closeoutDate:'2026-10-31',clock:state.clock,timeZone:'Asia/Shanghai',host:'http://127.0.0.1:18087',database:state.database,groups,totals,persistentRequests:manifest.persistentRequests,collectionId:manifest.collectionId,folderId:manifest.folderId,runners:manifest.runners,diagnostics,databaseAudit:audit,currentSourceTreeSha256:digest.digest('hex'),notes:['HTTP calls executed through Postman MCP only.','Fixed Clock is provided by a test-only launcher outside src/main.','Stage three/four fault and concurrency counts are separate historical reports, not rerun totals for phase five.','Preserved test-fixture and startup failures are not counted as final passing execution.','Read-only audit normalized JDBC LocalDateTime/Timestamp before comparison.']};
fs.writeFileSync('docs/postman/application-acceptance-regression.results.json',JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify({database:state.database,...totals,sourceUnchanged:audit.sourceUnchanged,flows:audit.flows.length,events:audit.events.length,interviews:audit.interviews.length,diagnostics:diagnostics.length}));
