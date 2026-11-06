const fs=require('fs'),path=require('path'),assert=require('assert'),crypto=require('crypto'),{spawn,spawnSync}=require('child_process');
const {connect,data}=require('./postman-mcp.cjs');
const MAIN='56227183-00855bc5-e68c-4aab-bdc2-faffe0f70f82',WORKSPACE='baec8bed-057c-431c-832a-21a7d3b2a685';
const STATE=process.env.JOB_LIST_CARDS_STATE_FILE||'target/job-list-cards-state.json',HOST='http://127.0.0.1:18092';
assert(path.resolve(STATE).startsWith(path.resolve('target')+path.sep));
const bare=x=>x.replace(/^56227183-/,''),uid=x=>x.startsWith('56227183-')?x:'56227183-'+x;
const cp=()=> 'target/recommendation-build/target/classes;'+fs.readFileSync('target/files-classpath.txt','utf8').trim();
const getId=r=>r.collection?.uid||r.environment?.uid||r.collection?.id||r.environment?.id||r.data?.id||r.id;
const guard="if(pm.environment.get('runJobListCardsRegression')!=='true'||pm.environment.get('rqFixtureReady')!=='true'||pm.environment.get('rqHost')!=='"+HOST+"'){pm.execution.skipRequest();}";
const ok="const b=pm.response.json();pm.test('HTTP200与业务200',()=>{pm.expect(pm.response.code).eq(200);pm.expect(b.code).eq(200);});";
const fail="const b=pm.response.json();pm.test('业务拒绝且无成功数据',()=>{pm.expect(pm.response.code).eq(200);pm.expect(b.code).eq(500);pm.expect(b.data).eq(null);});";
const unauthorized="pm.test('未登录HTTP401',()=>pm.expect(pm.response.code).eq(401));";
let items=[];
function add(stage,name,method,url,body,tests='',who=1,expected=ok){
 const headers=who?[{key:'Authorization',value:'Bearer {{rqToken'+who+'}}'}]:[];
 const request={method,url:'{{rqHost}}'+url,auth:{type:'noauth'},header:headers};
 if(body!==undefined){headers.push({key:'Content-Type',value:'application/json'});request.body={mode:'raw',raw:JSON.stringify(body)};}
 items.push({stage,name:String(items.length+1).padStart(3,'0')+' '+name,request,event:[{listen:'prerequest',script:{type:'text/javascript',exec:[guard]}},{listen:'test',script:{type:'text/javascript',exec:[expected+tests]}}]});
}
const test=(name,expression)=>"pm.test("+JSON.stringify(name)+",()=>{"+expression+"});";
const remember=key=>"pm.collectionVariables.set('"+key+"',JSON.stringify(b.data));";
const unchanged=key=>test('失败后全部业务字段未变',"pm.expect(b.data).deep.eq(JSON.parse(pm.collectionVariables.get('"+key+"')))");

function login(who){
 add('A','登录隔离身份'+who,'POST','/user/login',{username:'rq_input_'+who,password:'{{rqPassword}}'},"pm.collectionVariables.set('rqToken"+who+"',b.data);",null);
 add('A','核对身份'+who,'GET','/user/info',undefined,test('身份角色',"pm.expect(b.data.id).eq("+(100+who)+");pm.expect(b.data.role).eq('"+(who<=2?'JOB_SEEKER':who===4?'ADMIN':'COMPANY')+"');"),who);
}
function get(name,url,checks='',who=1,expected=ok){add('A',name,'GET',url,undefined,checks,who,expected);}
const item=id=>"b.data.items.find(x=>x.id==="+id+")";
function build(){
 login(1);
 get('新增字段与旧字段契约','/job?size=100',test('27个招聘中岗位且不重复',"pm.expect(b.data.total).eq(27);pm.expect(b.data.items).lengthOf(27);pm.expect(new Set(b.data.items.map(x=>x.id)).size).eq(27);pm.expect(b.data.items.every(x=>x.status===0)).eq(true);")+test('企业及完整卡片字段',"const x="+item(1001)+";pm.expect(x.companyId).eq(201);pm.expect(x.companyName).eq('已审核回归企业');pm.expect(x.companyLogo).eq('https://example.invalid/job-card-logo.png');pm.expect(x.summary).eq('岗位卡片简介');pm.expect(x.benefits).eq('弹性工作、带薪年假');pm.expect(x.requiredSkills).eq('Java');pm.expect(x.preferredSkills).eq('Docker');pm.expect(x.directionCode).eq('BACKEND');pm.expect(x.requiredAbilitiesSource).eq('LEGACY_TEXT');pm.expect(x.requiredAbilitiesVersion).eq(0);pm.expect(x.publishedAt).eq('2026-10-01T10:00:00');pm.expect(x.deadline).eq('2026-12-31');pm.expect(x.team).eq('平台组');pm.expect(x.salaryUnit).eq('MONTH');pm.expect(x.negotiable).eq(false);pm.expect(x).include.all.keys('id','title','category','employment','status','city','experience','education','salaryMin','salaryMax','updateTime');")+remember('jcAll'));
 get('缺失字段及原文不伪造','/job?size=100',test('空值真实返回',"const x="+item(1016)+";pm.expect(x.companyId).eq(202);pm.expect(x.companyLogo).eq(null);const y="+item(1020)+";pm.expect(y.publishedAt).eq(null);pm.expect(y.deadline).eq(null);pm.expect(y.category).eq(null);pm.expect("+item(1005)+".requiredSkills).eq(null);pm.expect("+item(1007)+".requiredSkills).eq('跨部门沟通');pm.expect("+item(1019)+".directionCode).eq(null);")+test('结构化展示投影原样返回且不放大行数',"const x="+item(1022)+";pm.expect(x.requiredSkills).eq(['Java','Redis','Java'].join(String.fromCharCode(10)));pm.expect(x.requiredAbilitiesSource).eq('STRUCTURED');pm.expect(x.requiredAbilitiesVersion).eq(7);pm.expect(b.data.items.filter(x=>x.id===1022)).lengthOf(1);"));
 get('月年日薪及面议','/job?size=100',test('单位和空薪资保留',"pm.expect("+item(1003)+".salaryUnit).eq('DAY');pm.expect("+item(1023)+".salaryUnit).eq('YEAR');pm.expect("+item(1024)+".negotiable).eq(true);pm.expect("+item(1024)+".salaryMin).eq(null);pm.expect("+item(1004)+".salaryMax).eq(null);"));
 for(let page=1;page<=4;page++)get('第'+page+'页稳定切片','/job?page='+page+'&size=7',test('分页总数和全量切片一致',"const all=JSON.parse(pm.collectionVariables.get('jcAll'));pm.expect(b.data.total).eq(all.total);pm.expect(b.data.items).deep.eq(all.items.slice("+((page-1)*7)+","+(page*7)+"));"));
 get('超出末页','/job?page=100&size=10',test('空items保留total',"pm.expect(b.data.total).eq(27);pm.expect(b.data.items).deep.eq([]);"));
 get('默认分页','/job',test('默认10条',"pm.expect(b.data.total).eq(27);pm.expect(b.data.items).lengthOf(10);"));
 for(const [name,word,ids] of [['标题','1999',[1999]],['部门','平台组',[1001]],['城市','深圳',[1008]],['类别','前端开发',[1021,1018]]])get('原搜索'+name,'/job?size=100&keyword='+encodeURIComponent(word),test('搜索命中',"pm.expect(b.data.items.map(x=>x.id)).deep.eq("+JSON.stringify(ids)+");pm.expect(b.data.total).eq("+ids.length+");"));
 get('搜索词trim','/job?size=100&keyword=%20%201999%20%20',test('trim有效',"pm.expect(b.data.items.map(x=>x.id)).deep.eq([1999]);"));
 get('无命中搜索','/job?keyword=NO_SUCH_JOB',test('空列表',"pm.expect(b.data.total).eq(0);pm.expect(b.data.items).deep.eq([]);"));
 get('企业主页岗位共享字段','/company/201/jobs?pageSize=100',test('25个本企业在招岗位且字段一致',"const all=JSON.parse(pm.collectionVariables.get('jcAll'));pm.expect(b.data.total).eq(25);pm.expect(b.data.items).deep.eq(all.items.filter(x=>x.companyId===201));"));
 get('企业主页分页','/company/201/jobs?page=2&pageSize=7',test('企业岗位分页',"const all=JSON.parse(pm.collectionVariables.get('jcAll'));pm.expect(b.data.total).eq(25);pm.expect(b.data.items).deep.eq(all.items.filter(x=>x.companyId===201).slice(7,14));"));
 get('未通过企业主页拒绝','/company/202/jobs', '',1,fail);
 for(const q of ['page=0','size=0','size=101'])get('非法分页'+q,'/job?'+q,'',1,fail);
 login(2);get('另一求职者可读取一致卡片','/job?size=100',unchanged('jcAll'),2);
 login(3);
 get('企业管理继承字段和招聘计数','/job/company?size=100',test('仅本公司28岗含草稿',"pm.expect(b.data.total).eq(28);pm.expect(b.data.items.every(x=>x.companyId===201)).eq(true);pm.expect(b.data.items.find(x=>x.id===1015).status).eq(3);const all=JSON.parse(pm.collectionVariables.get('jcAll'));const x="+item(1001)+";const {applicationCount,pendingApplicationCount,currentInterviewApplicationCount,...card}=x;pm.expect(card).deep.eq(all.items.find(x=>x.id===1001));pm.expect(applicationCount).eq(0);const applied="+item(1012)+";pm.expect(applied.applicationCount).eq(1);pm.expect(applied.pendingApplicationCount).eq(1);"),3);
 get('企业管理状态筛选','/job/company?filter=DRAFT',test('草稿归属筛选',"pm.expect(b.data.total).eq(1);pm.expect(b.data.items[0].id).eq(1015);pm.expect(b.data.items[0].companyName).eq('已审核回归企业');"),3);
 get('企业不可访问求职者列表','/job','',3,fail);
 login(4);get('管理员不可访问求职者列表','/job','',4,fail);
 get('匿名访问被拒绝','/job','',null,unauthorized);
 get('求职者不可访问企业管理列表','/job/company','',1,fail);
 get('重复查询保持业务数据','/job?size=100',unchanged('jcAll'));
}

function fixture(mode,s,password){const r=spawnSync('D:/jdk17/bin/java.exe',['-cp','target;'+cp(),'JobListCardsFixture',mode,s.database,s.root],{env:{...process.env,...(password?{RECOMMENDATION_QUERY_FIXTURE_PASSWORD:password}:{})},encoding:'utf8',windowsHide:true});if(r.status!==0)throw Error(r.stderr||r.stdout);console.log(r.stdout.trim()||('Fixture '+mode+' complete'));}
const redact=(value,password)=>{
 let result=String(value);const secrets=[password,process.env.POSTMAN_API_KEY];
 try{const state=JSON.parse(fs.readFileSync(STATE));secrets.push(JSON.parse(fs.readFileSync(state.root+'/credentials.json')).password);}catch{}
 for(const secret of secrets)if(secret)result=result.replaceAll(secret,'[REDACTED]');
 return result.replace(/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g,'[REDACTED_JWT]');
};
(async()=>{
 const mode=process.argv[2];
 if(mode==='setup-local'){
  assert(!fs.existsSync(STATE),'Existing state; do not overwrite');const stamp=Date.now(),password='RqIn_'+crypto.randomBytes(8).toString('hex');
  const s={database:'smart_recruit_job_cards_'+stamp,root:path.resolve('target/job-list-cards-fixture-'+stamp).replaceAll('\\','/'),port:18092,runners:{},folders:{}};
  if(fs.existsSync('docs/postman/job-list-cards-regression.manifest.json')){
   const m=JSON.parse(fs.readFileSync('docs/postman/job-list-cards-regression.manifest.json'));assert.equal(m.mainCollectionId,MAIN);
   Object.assign(s,{runners:m.runners,folders:m.folders,folderId:m.folderId});
  }
  fs.mkdirSync(s.root,{recursive:true});fs.writeFileSync(s.root+'/credentials.json',JSON.stringify({password}),{flag:'wx'});fs.writeFileSync(STATE,JSON.stringify(s,null,2));fixture('setup',s,password);return;
 }
 const s=JSON.parse(fs.readFileSync(STATE)),save=()=>{
  const latest=JSON.parse(fs.readFileSync(STATE));
  const merged={...latest,...s,runners:{...latest.runners,...s.runners},folders:{...latest.folders,...s.folders}};
  if(mode==='stop')delete merged.serverPid;
  fs.writeFileSync(STATE,JSON.stringify(merged,null,2));
 };
 if(mode==='stop'){
  s.serverPid=s.serverPid||Number(fs.readFileSync(s.root+'/server.log','utf8').match(/with PID (\d+)/)?.[1]);
  assert(Number.isInteger(s.serverPid)&&s.serverPid>0);assert(/^smart_recruit_job_cards_\d+$/.test(s.database));
  const script='$p=Get-CimInstance Win32_Process -Filter "ProcessId = '+s.serverPid+'"; if($null -eq $p){exit 0}; if($p.Name -ne "java.exe" -or -not $p.CommandLine.Contains("'+s.database+'") -or -not $p.CommandLine.Contains("--server.port=18092")){throw "Refuse unrelated process"}; Stop-Process -Id '+s.serverPid+' -Force -Confirm:$false -ErrorAction Stop';
  const r=spawnSync('powershell.exe',['-NoProfile','-NonInteractive','-Command',script],{windowsHide:true,encoding:'utf8'});if(r.status!==0)throw Error(r.stderr||'Stop failed');s.stoppedPid=s.serverPid;delete s.serverPid;save();console.log('Owned isolated server stopped.');return;
 }
 if(['audit','redis-cleanup'].includes(mode)){fixture(mode,s);return;}
 if(mode==='start'){
  assert(!s.serverPid);const configured=fs.readFileSync('src/main/resources/application.yml','utf8').match(/url:\s*['"]?(jdbc:mysql:[^\s'"]+)/)?.[1];assert(configured?.includes('/smart_recruit?'));
  const out=fs.openSync(s.root+'/server.log','a'),err=fs.openSync(s.root+'/server-error.log','a');
  const p=spawn('D:/jdk17/bin/java.exe',['-cp',cp(),'com.recruit.smartrecruit.SmartRecruitApplication','--server.port=18092','--spring.data.redis.database='+JSON.parse(fs.readFileSync(s.root+'/redis.json')).database,'--spring.datasource.url='+configured.replace('/smart_recruit?','/'+s.database+'?'),'--smart-recruit.company-files.root='+s.root+'/storage','--spring.main.banner-mode=off'],{windowsHide:true,detached:true,stdio:['ignore',out,err]});s.serverPid=p.pid;save();p.unref();console.log('Isolated server PID='+p.pid+' port=18092');return;
 }
 const c=await connect();let password;
 try{
  if(mode==='setup-environment'){
   assert(!s.environmentId);password=JSON.parse(fs.readFileSync(s.root+'/credentials.json')).password;
   const r=data(await c.callTool({name:'createEnvironment',arguments:{workspace:WORKSPACE,environment:{name:'Smart Recruit Job List Cards Isolated Regression',values:[{key:'rqHost',value:HOST,type:'default',enabled:true},{key:'rqPassword',value:password,type:'secret',enabled:true},{key:'rqAsOfDate',value:fs.readFileSync(s.root+'/as-of-date.txt','utf8'),type:'default',enabled:true},{key:'runJobListCardsRegression',value:'true',type:'default',enabled:true},{key:'rqFixtureReady',value:'true',type:'default',enabled:true}]}}}));s.environmentId=getId(r);assert(s.environmentId);save();console.log('Postman isolated environment saved: '+s.environmentId);return;
  }
  if(mode==='guard-off'){
   const env=data(await c.callTool({name:'getEnvironment',arguments:{environmentId:s.environmentId}})).environment;
   for(const key of ['runJobListCardsRegression','rqFixtureReady']){const index=env.values.findIndex(x=>x.key===key);assert(index>=0);data(await c.callTool({name:'patchEnvironment',arguments:{environmentId:s.environmentId,body:[{op:'replace',path:'/values/'+index+'/value',value:'false'}]}}));}const verified=data(await c.callTool({name:'getEnvironment',arguments:{environmentId:s.environmentId}})).environment;for(const key of ['runJobListCardsRegression','rqFixtureReady'])assert.equal(verified.values.find(x=>x.key===key).value,'false');console.log('Regression execution guards verified OFF.');return;
  }
  build();
  if(mode==='prepare'){
   const col=data(await c.callTool({name:'getCollection',arguments:{collectionId:MAIN}})).collection;assert.equal(col.info.name,'SmartRecruit API - Optimized Regression');const parent=col.itemRefs.find(x=>x.name==='03 Job');assert(parent);
   if(!s.folderId){s.folderId=getId(data(await c.callTool({name:'createCollectionFolder',arguments:{collectionId:MAIN,folder:bare(parent.id),name:'普通岗位卡片回归 2026-11-01'}})));assert(s.folderId);save();}
   for(const stage of ['A']){
    const cases=items.filter(x=>x.stage===stage).map(({stage,...x})=>x);
    if(!s.runners[stage]){s.runners[stage]=getId(data(await c.callTool({name:'createCollection',arguments:{workspace:WORKSPACE,collection:{info:{name:'Smart Recruit Job List Cards '+stage+' Execution',schema:'https://schema.getpostman.com/json/collection/v2.1.0/collection.json'},item:cases}}})));assert(s.runners[stage]);save();}
    const full=data(await c.callTool({name:'getCollection',arguments:{collectionId:s.runners[stage],model:'full'}})).collection;assert.equal(full.item.length,cases.length);
    if(!s.folders[stage]){const folderId=getId(data(await c.callTool({name:'createCollectionFolder',arguments:{collectionId:MAIN,folder:bare(s.folderId),name:stage+' '+({A:'卡片字段分页与权限',B:'参数与边界校验',C:'身份与简历归属',D:'只读覆盖及重复查询'}[stage])}})));assert(folderId);data(await c.callTool({name:'transferCollectionRequests',arguments:{ids:full.item.map(x=>uid(x.id)),mode:'copy',target:{id:uid(folderId),model:'folder'},location:{position:'end'}}}));s.folders[stage]=folderId;save();}
    console.log('Persisted '+stage+' requests='+cases.length);
   }
   fs.writeFileSync('docs/postman/job-list-cards-regression.postman_collection.json',JSON.stringify({info:{name:'普通岗位卡片持久回归',schema:'https://schema.getpostman.com/json/collection/v2.1.0/collection.json',description:'所有用例已加入SmartRecruit API - Optimized Regression / 03 Job。仅隔离端口18092；全部业务请求只读；夹具数据由受保护的本地脚本建立。'},item:['A'].map(stage=>({name:stage,item:items.filter(x=>x.stage===stage).map(({stage,...x})=>x)}))},null,2));
   fs.writeFileSync('docs/postman/job-list-cards-regression.manifest.json',JSON.stringify({mainCollectionId:MAIN,folderId:s.folderId,runners:s.runners,folders:s.folders,requestsByStage:Object.fromEntries(['A'].map(stage=>[stage,items.filter(x=>x.stage===stage).length]))},null,2));return;
  }
  if(mode==='verify'){
   const col=data(await c.callTool({name:'getCollection',arguments:{collectionId:MAIN,model:'full'}})).collection;const root=col.item.find(x=>x.name==='03 Job').item.find(x=>bare(x.id)===bare(s.folderId));const actual=root.item.flatMap(x=>x.item);assert.equal(actual.length,items.length);
   for(const item of items){const found=actual.find(x=>x.name===item.name||x.name===item.name+' Copy');assert(found,item.name);assert.deepStrictEqual(found.event,item.event);assert.equal(found.request.method,item.request.method);assert.equal(typeof found.request.url==='string'?found.request.url:found.request.url.raw,item.request.url);assert.equal(found.request.body?.raw,item.request.body?.raw);}console.log('Verified '+actual.length+' exact persistent requests in required collection');return;
  }
  assert(['A'].includes(mode));const env=data(await c.callTool({name:'getEnvironment',arguments:{environmentId:s.environmentId}})).environment;password=env.values.find(x=>x.key==='rqPassword')?.value;
  const result=await c.callTool({name:'runCollection',arguments:{collectionId:s.runners[mode],environmentId:s.environmentId,requestTimeout:30000,scriptTimeout:20000,stopOnError:true,stopOnFailure:true}},undefined,{timeout:300000});
  const output=result.content.filter(x=>x.type==='text').map(x=>x.text).join('\n');fs.writeFileSync(s.root+'/result-'+mode+'.json',redact(JSON.stringify(result),password));
  const tail=output.lastIndexOf('=== ');console.log(redact(tail>=0?output.slice(tail):output.slice(-5000),password));
  for(const line of output.match(/^.*RS_RACE=.*$/gm)||[])console.log(redact(line,password));
  if(result.isError||!Number(output.match(/Total assertions:\s*(\d+)/)?.[1])||/Failed assertions:\s*[1-9]|Failed requests:\s*[1-9]/.test(output)){console.log(redact(output.slice(-12000),password));process.exitCode=1;}
 }finally{await c.close();}
})().catch(e=>{console.error(redact(e.message,process.env.POSTMAN_API_KEY));process.exitCode=1;});
