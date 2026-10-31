const fs=require('fs'),path=require('path'),assert=require('assert'),crypto=require('crypto'),{spawnSync,spawn}=require('child_process');
const {connect,data}=require('./postman-mcp.cjs');
const MAIN='56227183-00855bc5-e68c-4aab-bdc2-faffe0f70f82',WORKSPACE='baec8bed-057c-431c-832a-21a7d3b2a685';
const STATE=process.env.APPLICATION_EVENT_STATE_FILE||'target/application-event-state.json',HOST='http://127.0.0.1:18087';
assert(path.resolve(STATE).startsWith(path.resolve('target')+path.sep),'State must remain inside target');
const bare=x=>x.replace(/^56227183-/,''),uid=x=>x.startsWith('56227183-')?x:'56227183-'+x;
const guard="if(pm.environment.get('runApplicationEventRegression')!=='true'||pm.environment.get('applicationEventFixtureReady')!=='true'||pm.environment.get('apHost')!=='"+HOST+"'){pm.execution.skipRequest();}";
const ok="const b=pm.response.json();pm.test('HTTP200及业务200',()=>{pm.expect(pm.response.code).eq(200);pm.expect(b.code).eq(200);});";
const fail="const b=pm.response.json();pm.test('业务拒绝且无数据',()=>{pm.expect(pm.response.code).eq(200);pm.expect(b.code).eq(500);pm.expect(b.data).eq(null);});";
const noauth="pm.test('未登录HTTP401',()=>pm.expect(pm.response.code).eq(401));";
const eventKeys=['id','eventType','fromStatus','toStatus','actorType','occurredAt','summary','interviewId','interviewChange'].sort();
const safe="pm.test('事件响应白名单及隐私',()=>{b.data.events.forEach(e=>{pm.expect(Object.keys(e).sort()).deep.eq("+JSON.stringify(eventKeys)+");pm.expect(e.interviewId).eq(null);pm.expect(e.interviewChange).eq(null);pm.expect(e.occurredAt).a('string');});pm.expect(b.data).not.have.property('userId');pm.expect(b.data).not.have.property('hasInterviewed');});";
let items=[];
function add(stage,name,method,url,body,tests=ok,who=1){const header=who?[{key:'Authorization',value:'Bearer {{apToken'+who+'}}'}]:[];const request={method,url:'{{apHost}}'+url,auth:{type:'noauth'},header};if(body!==undefined){header.push({key:'Content-Type',value:'application/json'});request.body={mode:'raw',raw:JSON.stringify(body)};}items.push({stage,name:String(items.length+1).padStart(3,'0')+' '+name,request,event:[{listen:'prerequest',script:{type:'text/javascript',exec:[guard]}},{listen:'test',script:{type:'text/javascript',exec:[tests]}}]});}
function login(stage,who){add(stage,'登录隔离身份'+who,'POST','/user/login',{username:'ap_event_'+who,password:'{{apPassword}}'},ok+"pm.collectionVariables.set('apToken"+who+"',b.data);",null);add(stage,'核对身份'+who,'GET','/user/info',undefined,ok+"pm.test('账号ID与角色',()=>{pm.expect(b.data.id).eq("+(100+who)+");pm.expect(b.data.role).eq('"+(who<=2||who===7?'JOB_SEEKER':who===6?'ADMIN':'COMPANY')+"');});",who);}
function apply(stage,job,tests=ok,who=1,resume=301){add(stage,'投递岗位'+job,'POST','/application',{jobId:job,resumeId:resume},tests,who);}
function find(stage,job,tests=''){add(stage,'查询岗位'+job+'的投递ID','GET','/application/mine/page?dateRange=ALL&keyword=APE_'+job,undefined,ok+"pm.test('唯一目标投递',()=>{pm.expect(b.data.total).eq(1);pm.expect(b.data.items).lengthOf(1);pm.expect(b.data.items[0].jobId).eq("+job+");});pm.collectionVariables.set('app"+job+"',b.data.items[0].id);"+tests);}
function detail(stage,job,tests='',who=1){add(stage,'核对岗位'+job+'真实时间线','GET','/application/mine/{{app'+job+'}}',undefined,ok+safe+tests,who);}
function status(stage,job,to,tests=ok,who=3){add(stage,'岗位'+job+'状态推进至'+to,'PUT','/application/{{app'+job+'}}/status',{status:to},tests,who);}
function timeline(count,status,complete=true){return "pm.test('真实状态和历史完整性',()=>{pm.expect(b.data.status).eq("+status+");pm.expect(b.data.historyComplete).eq("+complete+");pm.expect(b.data.events).lengthOf("+count+");pm.expect(b.data.timeZone).eq('Asia/Shanghai');pm.expect(b.data.interviews).deep.eq([]);});";}
function build(){
 for(const w of [1,2,3,4,5,6,7])login('A',w);
 add('A','全历史概览五种状态各一条','GET','/application/mine/overview',undefined,ok+"pm.test('初始五桶及总数',()=>pm.expect(b.data).deep.eq({totalCount:5,pendingCount:1,screeningCount:1,interviewCount:1,hiredCount:1,rejectedCount:1}));");
 add('A','默认30天不包含45天前旧投递','GET','/application/mine/page',undefined,ok+"pm.test('日期筛选为空',()=>{pm.expect(b.data.total).eq(0);pm.expect(b.data.items).deep.eq([]);});");
 add('A','全部历史分页且同时间稳定排序','GET','/application/mine/page?dateRange=ALL&pageSize=2',undefined,ok+"pm.test('总数与稳定顺序',()=>{pm.expect(b.data.total).eq(5);pm.expect(b.data.items.map(x=>x.id)).deep.eq([506,505]);});");
 add('A','超末页返回空不修正页码','GET','/application/mine/page?dateRange=ALL&page=999',undefined,ok+"pm.test('总数保留且空页',()=>{pm.expect(b.data.total).eq(5);pm.expect(b.data.items).deep.eq([]);});");
 add('A','百分号下划线转义符字面搜索','GET','/application/mine/page?dateRange=ALL&keyword=%25_%21',undefined,ok+"pm.test('仅匹配字面字符岗位',()=>{pm.expect(b.data.total).eq(1);pm.expect(b.data.items[0].jobId).eq(416);});");
 add('A','空白关键词不筛选','GET','/application/mine/page?dateRange=ALL&keyword=%20%20',undefined,ok+"pm.test('全部历史',()=>pm.expect(b.data.total).eq(5));");
 add('A','空用户六个计数均零','GET','/application/mine/overview',undefined,ok+"pm.test('空用户计数',()=>Object.values(b.data).forEach(x=>pm.expect(x).eq(0)));",7);
 add('A','列表状态筛选仅面试','GET','/application/mine/page?dateRange=ALL&status=2',undefined,ok+"pm.test('仅返回当前面试',()=>{pm.expect(b.data.total).eq(1);pm.expect(b.data.items[0].status).eq(2);});");
 for(const q of ['page=0','pageSize=101','page=abc','status=-1','status=5','status=abc','dateRange=WRONG','keyword='+('x'.repeat(101))])add('A','拒绝非法筛选 '+q.slice(0,25),'GET','/application/mine/page?'+q,undefined,fail);
 for(const w of [3,6])for(const route of ['page','overview','501'])add('A','角色'+w+'不能使用本人查询','GET','/application/mine/'+route,undefined,fail,w);
 add('A','本人查询未登录','GET','/application/mine/overview',undefined,noauth,null);
 add('A','他人投递与不存在统一拒绝','GET','/application/mine/503',undefined,fail+"pm.test('统一错误文案',()=>pm.expect(b.message).eq('投递不存在或无权访问'));");
 add('A','不存在投递','GET','/application/mine/999999',undefined,fail+"pm.test('统一错误文案',()=>pm.expect(b.message).eq('投递不存在或无权访问'));");
 for(const [who,resume,job] of [[1,302,403],[1,999999,403],[2,301,403],[3,301,403],[6,301,403],[1,301,999999],[1,301,423],[1,301,424],[1,301,425]])apply('A',job,fail,who,resume);
 add('A','未登录不能投递','POST','/application',{jobId:403,resumeId:301},noauth,null);
 add('A','缺少岗位参数不能投递','POST','/application',{resumeId:301},fail);
 apply('A',401);find('A',401);detail('A',401,timeline(1,0)+"pm.test('真实APPLIED',()=>{const e=b.data.events[0];pm.expect(e.eventType).eq('APPLIED');pm.expect(e.fromStatus).eq(null);pm.expect(e.toStatus).eq(0);pm.expect(e.actorType).eq('JOB_SEEKER');});");
 apply('A',401,fail);detail('A',401,timeline(1,0));
 for(const w of [1,4,5,6])status('A',401,2,fail,w);
 add('A','未登录不能变更状态','PUT','/application/{{app401}}/status',{status:2},noauth,null);
 for(const v of [-1,5,'bad',null])status('A',401,v,fail);
 status('A',401,0,fail);detail('A',401,timeline(1,0));
 for(const [to,n] of [[1,2],[2,3],[3,4]]){status('A',401,to);detail('A',401,timeline(n,to)+"pm.test('真实起止状态',()=>{const e=b.data.events[0];pm.expect(e.eventType).eq('STATUS_CHANGED');pm.expect(e.fromStatus).eq("+(to-1)+");pm.expect(e.toStatus).eq("+to+");pm.expect(e.actorType).eq('COMPANY');});");}
 for(const to of [3,2,4])status('A',401,to,fail);detail('A',401,timeline(4,3));
 apply('A',402);find('A',402);status('A',402,3);detail('A',402,timeline(2,3)+"pm.test('跳级不伪造中间事件',()=>{pm.expect(b.data.events.map(x=>x.eventType)).deep.eq(['STATUS_CHANGED','APPLIED']);pm.expect(b.data.events[0].fromStatus).eq(0);pm.expect(b.data.events[0].toStatus).eq(3);});");
 apply('A',408);find('A',408);status('A',408,4);detail('A',408,timeline(2,4)+"pm.test('求职者文案为未通过',()=>pm.expect(b.data.statusText).eq('未通过'));");status('A',408,3,fail);
 for(const job of [405,406]){apply('A',job);find('A',job);detail('A',job,timeline(1,0));}
 add('A','旧简历缺失详情仍可见','GET','/application/mine/501',undefined,ok+safe+timeline(0,0,false)+"pm.test('缺失标记明确',()=>{pm.expect(b.data.resumeAvailable).eq(false);pm.expect(b.data.resumeTitle).eq(null);});");
 for(const [id,from,to]of [[501,0,2],[502,2,3],[506,1,4]]){add('A','旧投递实际状态变更'+id,'PUT','/application/'+id+'/status',{status:to},ok,3);add('A','旧记录不补造历史'+id,'GET','/application/mine/'+id,undefined,ok+safe+timeline(1,to,false)+"pm.test('只记录本次真实变更',()=>{pm.expect(b.data.events[0].fromStatus).eq("+from+");pm.expect(b.data.events[0].toStatus).eq("+to+");pm.expect(b.data.events[0].eventType).eq('STATUS_CHANGED');});");}
 add('A','旧接口列表仍兼容','GET','/application/mine',undefined,ok+"pm.test('旧列表仍为数组',()=>pm.expect(b.data).an('array'));");
 add('A','旧企业列表仍兼容','GET','/application/my',undefined,ok+"pm.test('旧企业列表仍为数组',()=>pm.expect(b.data).an('array'));",3);
 add('A','旧详情仍可读取','GET','/application/501',undefined,ok+"pm.test('旧详情没有扩大事件字段',()=>{pm.expect(b.data.id).eq(501);pm.expect(b.data.resumeAvailable).eq(false);pm.expect(b.data).not.have.property('events');});");
 for(const w of [1,3])login('B',w);find('B',405);detail('B',405,"pm.collectionVariables.set('before405',JSON.stringify(b.data));");
 apply('B',404,fail+"pm.test('注入故障不返回成功',()=>pm.expect(b.message).include('系统异常'));");
 add('B','失败新投递不存在','GET','/application/mine/page?dateRange=ALL&keyword=APE_404',undefined,ok+"pm.test('投递已回滚',()=>{pm.expect(b.data.total).eq(0);pm.expect(b.data.items).deep.eq([]);});");
 status('B',405,2,fail);detail('B',405,"pm.test('状态与更新时间和时间线均回滚',()=>pm.expect(b.data).deep.eq(JSON.parse(pm.collectionVariables.get('before405'))));");
 // serverTime is request metadata, not a persisted rollback field.
 items.at(-1).event[1].script.exec=[ok+safe+"pm.test('业务字段与事件均回滚',()=>{const before=JSON.parse(pm.collectionVariables.get('before405'));delete before.serverTime;const current={...b.data};delete current.serverTime;pm.expect(current).deep.eq(before);});"];
 for(const w of [1,3])login('C',w);apply('C',404);find('C',404);detail('C',404,timeline(1,0));find('C',405);status('C',405,2);detail('C',405,timeline(2,2));
 for(const w of [1,3])login('D',w);find('D',406);
 detail('D',406,"let done=0;const results=[];[2,3].forEach((target,index)=>setTimeout(()=>{const started=Date.now();pm.sendRequest({url:pm.environment.get('apHost')+'/application/'+b.data.id+'/status',method:'PUT',header:{Authorization:'Bearer '+pm.collectionVariables.get('apToken3'),'Content-Type':'application/json'},body:{mode:'raw',raw:JSON.stringify({status:target})}},(err,res)=>{pm.test('并发状态请求可达',()=>pm.expect(err).eq(null));if(!err)results.push({target,code:res.json().code,message:res.json().message,started,ended:Date.now()});if(++done===2){pm.test('同原状态竞争一成功一拒绝',()=>pm.expect(results.map(x=>x.code).sort()).deep.eq([200,500]));pm.test('两个请求实际重叠',()=>pm.expect(Math.max(...results.map(x=>x.started))).below(Math.min(...results.map(x=>x.ended))));pm.test('冲突明确提示刷新',()=>pm.expect(results.find(x=>x.code===500).message).include('刷新'));console.log('AP_STATUS_RACE='+JSON.stringify(results));}});},index*100));");
 detail('D',406,"pm.test('竞争仅追加一条真实状态事件',()=>{pm.expect(b.data.events).lengthOf(2);pm.expect(b.data.events[0].fromStatus).eq(0);pm.expect(b.data.events[0].toStatus).eq(b.data.status);pm.expect([2,3]).include(b.data.status);});");
 add('D','同岗位并发投递','GET','/application/mine/overview',undefined,ok+"let done=0;const results=[];[0,1].forEach(index=>setTimeout(()=>{const started=Date.now();pm.sendRequest({url:pm.environment.get('apHost')+'/application',method:'POST',header:{Authorization:'Bearer '+pm.collectionVariables.get('apToken1'),'Content-Type':'application/json'},body:{mode:'raw',raw:JSON.stringify({jobId:407,resumeId:301})}},(err,res)=>{pm.test('并发投递请求可达',()=>pm.expect(err).eq(null));if(!err)results.push({code:res.json().code,message:res.json().message,started,ended:Date.now()});if(++done===2){pm.test('并发投递一成功一拒绝',()=>pm.expect(results.map(x=>x.code).sort()).deep.eq([200,500]));pm.test('投递请求实际重叠',()=>pm.expect(Math.max(...results.map(x=>x.started))).below(Math.min(...results.map(x=>x.ended))));console.log('AP_APPLY_RACE='+JSON.stringify(results));}});},index*100));");
 find('D',407);detail('D',407,timeline(1,0));
}
function fixture(mode,state,password){const r=spawnSync('D:/jdk17/bin/java.exe',['-cp','target;target/classes;'+fs.readFileSync('target/files-classpath.txt','utf8').trim(),'ApplicationEventFixture',mode,state.database,state.root],{env:{...process.env,...(password?{APPLICATION_FIXTURE_PASSWORD:password}:{})},encoding:'utf8',windowsHide:true});if(r.status!==0)throw Error(r.stderr||r.stdout);console.log(r.stdout.trim()||('Fixture '+mode+' completed'));}
function getId(r){return r.collection?.uid||r.environment?.uid||r.collection?.id||r.environment?.id||r.data?.id||r.folder?.id||r.id;}
function redact(s,password){return String(s).replaceAll(password||'___','[PASSWORD]').replaceAll(process.env.POSTMAN_API_KEY||'___','[KEY]').replace(/ApEv_[a-f0-9]{16}/g,'[PASSWORD]').replace(/eyJ[\w-]+\.[\w-]+\.[\w-]+/g,'[JWT]');}
(async()=>{const mode=process.argv[2];
 if(mode==='setup-local'){
  assert(!fs.existsSync(STATE),'Existing fixture state: refuse overwrite');
  const suffix=Date.now(),password='ApEv_'+crypto.randomBytes(8).toString('hex');
  const s={database:'smart_recruit_application_events_'+suffix,port:18087,root:path.resolve('target/application-event-fixtures-'+suffix).replaceAll('\\','/'),runners:{},folders:{}};
  fs.mkdirSync(s.root,{recursive:true});
  fs.writeFileSync(s.root+'/credentials.json',JSON.stringify({password}),{flag:'wx'});
  if(fs.existsSync('docs/postman/application-event-regression.manifest.json')){const m=JSON.parse(fs.readFileSync('docs/postman/application-event-regression.manifest.json'));s.runners=m.runners;s.folders=m.folders;s.folderId=m.folderId;}
  fs.writeFileSync(STATE,JSON.stringify(s,null,2));fixture('setup',s,password);
  console.log('Local fixture ready; no Postman credential/environment writes performed.');return;
 }
 if(['baseline','fault-on','fault-off','verify-rollback','delay-on','delay-off','audit'].includes(mode)){fixture(mode,JSON.parse(fs.readFileSync(STATE)));return;}
 if(mode==='start'){
  const s=JSON.parse(fs.readFileSync(STATE));assert(!s.serverPid);assert(/^smart_recruit_application_events_\d+$/.test(s.database));
  const configured=fs.readFileSync('src/main/resources/application.yml','utf8').match(/url:\s*['"]?(jdbc:mysql:[^\s'"]+)/)?.[1];assert(configured?.includes('/smart_recruit?'));
  const out=fs.openSync(s.root+'/server.log','a'),err=fs.openSync(s.root+'/server-error.log','a');
  const p=spawn('D:/jdk17/bin/java.exe',['-cp','target/classes;'+fs.readFileSync('target/files-classpath.txt','utf8').trim(),'com.recruit.smartrecruit.SmartRecruitApplication','--server.port=18087','--spring.datasource.url='+configured.replace('/smart_recruit?','/'+s.database+'?'),'--smart-recruit.company-files.root='+s.root+'/storage','--spring.main.banner-mode=off'],{windowsHide:true,detached:true,stdio:['ignore',out,err]});
  s.serverPid=p.pid;fs.writeFileSync(STATE,JSON.stringify(s,null,2));p.unref();console.log('Isolated server started PID='+p.pid+' port=18087');return;
 }
 const c=await connect();let password;
 try{
  if(mode==='setup-existing-environment'){
   assert(!fs.existsSync(STATE),'Use a new state file for a fresh regression database');
   const environmentId='56227183-c3b583b6-bced-4cd2-a4e8-e22bc54e396f';
   const env=data(await c.callTool({name:'getEnvironment',arguments:{environmentId}})).environment;
   password=env.values.find(x=>x.key==='apPassword')?.value;assert(password);
   const stamp=Date.now(),manifest=JSON.parse(fs.readFileSync('docs/postman/application-event-regression.manifest.json'));
   const s={database:'smart_recruit_application_events_'+stamp,root:path.resolve('target/application-event-fixtures-'+stamp).replaceAll('\\','/'),port:18087,environmentId,runners:manifest.runners,folders:manifest.folders,folderId:manifest.folderId};
   fs.writeFileSync(STATE,JSON.stringify(s,null,2));fixture('setup',s,password);
   for(const key of ['runApplicationEventRegression','applicationEventFixtureReady']){
    const index=env.values.findIndex(x=>x.key===key);assert(index>=0);
    data(await c.callTool({name:'patchEnvironment',arguments:{environmentId,body:[{op:'replace',path:'/values/'+index+'/value',value:'true'}]}}));
   }
   console.log('Fresh phase-three fixture created; reused existing authorized Postman secret and persistent requests.');return;
  }
  if(mode==='setup-environment'){
   const s=JSON.parse(fs.readFileSync(STATE));assert(!s.environmentId);
   password=JSON.parse(fs.readFileSync(s.root+'/credentials.json')).password;
   const r=data(await c.callTool({name:'createEnvironment',arguments:{workspace:WORKSPACE,environment:{name:'Smart Recruit Application Events Isolated Regression',values:[{key:'apHost',value:HOST,type:'default',enabled:true},{key:'apPassword',value:password,type:'secret',enabled:true},{key:'runApplicationEventRegression',value:'true',type:'default',enabled:true},{key:'applicationEventFixtureReady',value:'true',type:'default',enabled:true}]}}}));
   s.environmentId=getId(r);assert(s.environmentId);fs.writeFileSync(STATE,JSON.stringify(s,null,2));console.log('Postman isolated environment created: '+s.environmentId);return;
  }
  if(mode==='setup'){
   assert(!fs.existsSync(STATE),'Existing fixture state: refuse overwrite');password='ApEv_'+crypto.randomBytes(8).toString('hex');
   const suffix=Date.now(),s={database:'smart_recruit_application_events_'+suffix,port:18087,root:path.resolve('target/application-event-fixtures-'+suffix).replaceAll('\\','/'),runners:{},folders:{}};
   fs.writeFileSync(STATE,JSON.stringify(s,null,2));fixture('setup',s,password);
   const r=data(await c.callTool({name:'createEnvironment',arguments:{workspace:WORKSPACE,environment:{name:'Smart Recruit Application Events Isolated Regression',values:[{key:'apHost',value:HOST,type:'default',enabled:true},{key:'apPassword',value:password,type:'secret',enabled:true},{key:'runApplicationEventRegression',value:'true',type:'default',enabled:true},{key:'applicationEventFixtureReady',value:'true',type:'default',enabled:true}]}}}));
   s.environmentId=getId(r);assert(s.environmentId);fs.writeFileSync(STATE,JSON.stringify(s,null,2));console.log(JSON.stringify({database:s.database,environmentId:s.environmentId,root:s.root}));return;
  }
  const s=JSON.parse(fs.readFileSync(STATE)),save=()=>fs.writeFileSync(STATE,JSON.stringify(s,null,2));
  if(mode==='guard-off'){
   const env=data(await c.callTool({name:'getEnvironment',arguments:{environmentId:s.environmentId}})).environment;
   for(const key of ['runApplicationEventRegression','applicationEventFixtureReady']){
    const index=env.values.findIndex(x=>x.key===key);assert(index>=0);
    data(await c.callTool({name:'patchEnvironment',arguments:{environmentId:s.environmentId,body:[{op:'replace',path:'/values/'+index+'/value',value:'false'}]}}));
   }
   console.log('Application event regression guards disabled; existing secret unchanged.');return;
  }
  build();
  if(mode==='prepare'){
   const col=data(await c.callTool({name:'getCollection',arguments:{collectionId:MAIN}})).collection;assert.equal(col.info.name,'SmartRecruit API - Optimized Regression');const parent=col.itemRefs.find(x=>x.name==='05 Application');assert(parent,'Application folder missing');
   if(!s.folderId){s.folderId=getId(data(await c.callTool({name:'createCollectionFolder',arguments:{collectionId:MAIN,folder:bare(parent.id),name:'我的投递真实事件回归 2026-10-25'}})));assert(s.folderId);save();}
   for(const stage of ['A','B','C','D']){
    const cases=items.filter(x=>x.stage===stage).map(({stage,...x})=>x);
    if(!s.runners[stage]){s.runners[stage]=getId(data(await c.callTool({name:'createCollection',arguments:{workspace:WORKSPACE,collection:{info:{name:'Smart Recruit Application Events '+stage+' Execution',schema:'https://schema.getpostman.com/json/collection/v2.1.0/collection.json'},item:cases}}})));assert(s.runners[stage]);save();}
    const full=data(await c.callTool({name:'getCollection',arguments:{collectionId:s.runners[stage],model:'full'}})).collection;assert.equal(full.item.length,cases.length);
    if(!s.folders[stage]){const id=getId(data(await c.callTool({name:'createCollectionFolder',arguments:{collectionId:MAIN,folder:bare(s.folderId),name:stage+' '+({A:'功能权限与历史',B:'事件失败回滚',C:'故障解除后恢复',D:'并发状态与重复投递'}[stage])}})));assert(id);data(await c.callTool({name:'transferCollectionRequests',arguments:{ids:full.item.map(x=>uid(x.id)),mode:'copy',target:{id:uid(id),model:'folder'},location:{position:'end'}}}));s.folders[stage]=id;save();}
    console.log('Persisted '+stage+' requests='+cases.length);
   }
   const artifact={info:{name:'我的投递真实事件持久回归',schema:'https://schema.getpostman.com/json/collection/v2.1.0/collection.json',description:'仅允许隔离端口18087。A正常；B前启用事件失败触发器，B后核对回滚并移除；C恢复；D前启用延时触发器，D后移除。所有用例已持久加入 SmartRecruit API - Optimized Regression / 05 Application。'},item:['A','B','C','D'].map(stage=>({name:stage,item:items.filter(x=>x.stage===stage).map(({stage,...x})=>x)}))};
   fs.writeFileSync('docs/postman/application-event-regression.postman_collection.json',JSON.stringify(artifact,null,2));return;
  }
  if(mode==='verify'){
   const col=data(await c.callTool({name:'getCollection',arguments:{collectionId:MAIN,model:'full'}})).collection;
   const root=col.item.find(x=>x.name==='05 Application').item.find(x=>bare(x.id)===bare(s.folderId));const actual=root.item.flatMap(x=>x.item);assert.equal(actual.length,items.length);
   for(const x of items){const found=actual.find(y=>y.name===x.name||y.name===x.name+' Copy');assert(found,x.name);assert.deepStrictEqual(found.event,x.event,x.name);assert.equal(found.request.method,x.request.method,x.name);assert.equal(typeof found.request.url==='string'?found.request.url:found.request.url.raw,x.request.url,x.name);assert.equal(found.request.body?.raw,x.request.body?.raw,x.name);}
   console.log('Verified '+actual.length+' persistent requests and exact scripts in required collection');return;
  }
  assert(['A','B','C','D'].includes(mode),'Unknown phase');
  const env=data(await c.callTool({name:'getEnvironment',arguments:{environmentId:s.environmentId}})).environment;password=env.values.find(x=>x.key==='apPassword')?.value;
  const result=await c.callTool({name:'runCollection',arguments:{collectionId:s.runners[mode],environmentId:s.environmentId,requestTimeout:30000,scriptTimeout:20000,stopOnError:true,stopOnFailure:true}},undefined,{timeout:300000});
  const text=result.content.filter(x=>x.type==='text').map(x=>x.text).join('\n');fs.writeFileSync(s.root+'/result-'+mode+'.json',redact(JSON.stringify(result),password));
  const pos=text.lastIndexOf('=== ');console.log(redact(pos>=0?text.slice(pos):text.slice(-4500),password));
  for(const line of text.match(/^.*AP_(?:STATUS|APPLY)_RACE=.*$/gm)||[])console.log(redact(line,password));
  if(result.isError||!Number(text.match(/Total assertions:\s*(\d+)/)?.[1])||/Failed assertions:\s*[1-9]|Failed requests:\s*[1-9]/.test(text))process.exitCode=1;
 }finally{await c.close();}
})().catch(e=>{console.error(redact(e.message));process.exitCode=1;});
