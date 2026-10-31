const fs=require('fs'),path=require('path'),assert=require('assert'),{spawn,spawnSync}=require('child_process');
const {connect,data}=require('./postman-mcp.cjs');
const MAIN='56227183-00855bc5-e68c-4aab-bdc2-faffe0f70f82',WORKSPACE='baec8bed-057c-431c-832a-21a7d3b2a685',ENV='56227183-c3b583b6-bced-4cd2-a4e8-e22bc54e396f';
const STATE=process.env.INTERVIEW_STATE_FILE||'target/interview-state.json',HOST='http://127.0.0.1:18087';
assert(path.resolve(STATE).startsWith(path.resolve('target')+path.sep));
const bare=x=>x.replace(/^56227183-/,''),uid=x=>x.startsWith('56227183-')?x:'56227183-'+x;
const guard="if(pm.environment.get('runInterviewRegression')!=='true'||pm.environment.get('apHost')!=='"+HOST+"'||!/^smart_recruit_interviews_\\d+$/.test(pm.environment.get('interviewFixtureDatabase')||'')){pm.execution.skipRequest();}";
const dates="const SHANGHAI_OFFSET_MS=8*60*60*1000;const at=m=>new Date(Date.now()+SHANGHAI_OFFSET_MS+m*60000).toISOString().slice(0,-1);pm.collectionVariables.set('ivStart',at(1440));pm.collectionVariables.set('ivEnd',at(1500));pm.collectionVariables.set('ivLater',at(2880));pm.collectionVariables.set('ivLaterEnd',at(2940));pm.collectionVariables.set('ivPast',at(-60));const late=new Date(Date.now()+SHANGHAI_OFFSET_MS+86400000);late.setUTCHours(23,30,0,0);pm.collectionVariables.set('ivCrossStart',late.toISOString().slice(0,-1));pm.collectionVariables.set('ivCrossEnd',new Date(late.getTime()+3600000).toISOString().slice(0,-1));";
const ok="const b=pm.response.json();pm.test('HTTP200和业务200',()=>{pm.expect(pm.response.code).eq(200);pm.expect(b.code).eq(200);});";
const fail="const b=pm.response.json();pm.test('业务拒绝不返回数据',()=>{pm.expect(pm.response.code).eq(200);pm.expect(b.code).eq(500);pm.expect(b.data).eq(null);});";
const unauth="pm.test('未登录HTTP401',()=>pm.expect(pm.response.code).eq(401));";
const keys='id applicationId startTime endTime mode platform meetingUrl address candidateNote status timingStatus version cancelReason cancelledAt createTime updateTime'.split(' ').sort();
const changes='previousStartTime previousEndTime startTime endTime previousMode mode cancelReason'.split(' ').sort();
const safe=x=>"pm.test('安排响应白名单',()=>pm.expect(Object.keys("+x+").sort()).deep.eq("+JSON.stringify(keys)+"));";
const listSafe="pm.test('列表包装及隐私',()=>{pm.expect(Object.keys(b.data).sort()).deep.eq(['applicationId','serverTime','timeZone','items'].sort());b.data.items.forEach(x=>pm.expect(Object.keys(x).sort()).deep.eq("+JSON.stringify(keys)+"));pm.expect(b.data.timeZone).eq('Asia/Shanghai');});";
const detailSafe="pm.test('事件快照仅白名单',()=>b.data.events.filter(e=>e.interviewId!==null).forEach(e=>{pm.expect(e.fromStatus).eq(null);pm.expect(e.toStatus).eq(null);pm.expect(Object.keys(e.interviewChange).sort()).deep.eq("+JSON.stringify(changes)+");pm.expect(e).not.have.property('operatorUserId');}));";
const online={startTime:'{{ivStart}}',endTime:'{{ivEnd}}',mode:'ONLINE',platform:' 隔离平台 ',meetingUrl:' https://example.com/meeting ',candidateNote:' 求职者可见 '};
const offline={startTime:'{{ivLater}}',endTime:'{{ivLaterEnd}}',mode:'OFFLINE',address:' 上海测试地址 '};
let items=[];
function add(stage,name,method,url,body,tests=ok,who=3){const header=who?[{key:'Authorization',value:'Bearer {{ivToken'+who+'}}'}]:[];const request={method,url:'{{apHost}}'+url,auth:{type:'noauth'},header};if(body!==undefined){header.push({key:'Content-Type',value:'application/json'});request.body={mode:'raw',raw:JSON.stringify(body)};}items.push({stage,name:String(items.length+1).padStart(3,'0')+' '+name,request,event:[{listen:'prerequest',script:{type:'text/javascript',exec:[guard+dates]}},{listen:'test',script:{type:'text/javascript',exec:[tests]}}]});}
function login(stage,w){add(stage,'登录合成身份'+w,'POST','/user/login',{username:'ap_event_'+w,password:'{{apPassword}}'},ok+"pm.collectionVariables.set('ivToken"+w+"',b.data);",null);add(stage,'核对身份'+w,'GET','/user/info',undefined,ok+"pm.test('真实身份',()=>pm.expect(b.data.id).eq("+(100+w)+"));",w);}
function create(stage,app,body=online,tests='',who=3){add(stage,'创建排期'+app,'POST','/application/'+app+'/interviews',body,ok+safe('b.data')+"pm.collectionVariables.set('iv"+app+"',b.data.id);pm.collectionVariables.set('ivRow"+app+"',JSON.stringify(b.data));pm.test('服务器初始状态与版本',()=>{pm.expect(b.data.applicationId).eq("+app+");pm.expect(b.data.version).eq(0);pm.expect(b.data.status).eq('SCHEDULED');});"+tests,who);}
function listing(stage,app,tests='',who=3){add(stage,'读取企业排期'+app,'GET','/application/'+app+'/interviews',undefined,ok+listSafe+tests,who);}
function load(stage,app){listing(stage,app,"pm.test('存在目标排期',()=>pm.expect(b.data.items).lengthOf(1));pm.collectionVariables.set('iv"+app+"',b.data.items[0].id);pm.collectionVariables.set('ivRow"+app+"',JSON.stringify(b.data.items[0]));");}
function update(stage,app,body,tests=ok,who=3,id='{{iv'+app+'}}'){add(stage,'改约排期'+app,'PUT','/application/'+app+'/interviews/'+id,body,tests,who);}
function cancel(stage,app,version,reason=' 企业沟通后取消 ',tests=ok,who=3,id='{{iv'+app+'}}'){add(stage,'取消排期'+app,'PUT','/application/'+app+'/interviews/'+id+'/cancel',{version,reason},tests,who);}
function detail(stage,app,tests='',who=1){add(stage,'求职者真实详情'+app,'GET','/application/mine/'+app,undefined,ok+detailSafe+tests,who);}
function end(stage,app,target=3,tests=ok){add(stage,'结束投递'+app+'至'+target,'PUT','/application/'+app+'/status',{status:target},tests);}
function next(stage,tests='',who=1){add(stage,'查询近期面试'+who,'GET','/application/mine/interviews/next',undefined,ok+"pm.test('近期包装与时区',()=>{pm.expect(Object.keys(b.data).sort()).deep.eq(['serverTime','timeZone','interview'].sort());pm.expect(b.data.timeZone).eq('Asia/Shanghai');pm.expect(b.data.serverTime).a('string');});"+tests,who);}
function race(stage,app,kind,firstEnd=false){
 const same=kind==='create'||kind==='reschedule'||kind==='mixed';
 const make=(operation)=>{if(operation==='end')return {kind:'end',method:'PUT',url:'/application/'+app+'/status',body:{status:kind==='cancelEnd'?4:3}};if(operation==='create')return {kind:'create',method:'POST',url:'/application/'+app+'/interviews',body:online};if(operation==='cancel')return {kind:'cancel',method:'PUT',url:'/application/'+app+'/interviews/{{iv'+app+'}}/cancel',body:{version:0,reason:'并发取消'}};return {kind:'reschedule',method:'PUT',url:'/application/'+app+'/interviews/{{iv'+app+'}}',body:{...offline,version:0}};};
 let jobs=kind==='create'?[make('create'),make('create')]:kind==='reschedule'?[make('reschedule'),make('reschedule')]:kind==='mixed'?[make('reschedule'),make('cancel')]:[make(kind==='createEnd'?'create':kind==='cancelEnd'?'cancel':'reschedule'),make('end')];
 if(firstEnd)jobs.reverse();
 const code="const jobs="+JSON.stringify(jobs)+";let done=0;const results=[];jobs.forEach((j,index)=>setTimeout(()=>{const started=Date.now();pm.sendRequest({method:j.method,url:pm.environment.get('apHost')+pm.variables.replaceIn(j.url),header:{Authorization:'Bearer '+pm.collectionVariables.get('ivToken3'),'Content-Type':'application/json'},body:{mode:'raw',raw:pm.variables.replaceIn(JSON.stringify(j.body))}},(err,res)=>{pm.test('并发请求可达',()=>pm.expect(err).eq(null));if(!err)results.push({kind:j.kind,code:res.json().code,started,ended:Date.now()});if(++done===2){pm.test('请求确实重叠',()=>pm.expect(Math.max(...results.map(x=>x.started))).below(Math.min(...results.map(x=>x.ended))));"+(same?"pm.test('版本或有效场次竞争一成功一拒绝',()=>pm.expect(results.map(x=>x.code).sort()).deep.eq([200,500]));":"pm.test('终态请求成功且竞争写入安全结束',()=>{pm.expect(results.find(x=>x.kind==='end').code).eq(200);pm.expect([200,500]).include(results.find(x=>x.kind!=='end').code);});")+"}});},index*100));";
 listing(stage,app,code);
 if(same)listing(stage,app,"pm.test('竞争没有重复场次或重复版本',()=>{pm.expect(b.data.items).lengthOf(1);pm.expect(b.data.items[0].version).eq("+(kind==='create'?0:1)+");});");
 else {listing(stage,app,"pm.test('终态不残留有效安排',()=>b.data.items.forEach(x=>pm.expect(x.status).eq('CANCELLED')));");detail(stage,app,"pm.test('投递已到终态',()=>pm.expect(b.data.status).eq("+(kind==='cancelEnd'?4:3)+"));");}
}
function build(){
 for(const w of [1,2,3,4,5,6,7])login('A',w);
 next('A',"pm.test('自己的最早进行中优先',()=>{pm.expect(b.data.interview.id).eq(2024);pm.expect(b.data.interview.timingStatus).eq('ONGOING');});");
 next('A',"pm.test('另一个求职者只读本人',()=>pm.expect(b.data.interview.id).eq(2008));",2);
 next('A',"pm.test('无安排返回null',()=>pm.expect(b.data.interview).eq(null));",7);
 for(const w of [3,6])add('A','非求职者不能查近期','GET','/application/mine/interviews/next',undefined,fail,w);
 for(const w of [1,2,4,5,6,null])add('A','排期读取权限'+w,'GET','/application/1001/interviews',undefined,w===null?unauth:fail,w);
 listing('A',1001,"pm.test('初始空数组',()=>pm.expect(b.data.items).deep.eq([]));");
 for(const w of [1,2,4,5,6,null])add('A','排期创建权限'+w,'POST','/application/1001/interviews',online,w===null?unauth:fail,w);
 for(const app of [1004,1005,1006,999999,-1])add('A','错误投递状态或ID'+app,'POST','/application/'+app+'/interviews',online,fail);
 const bad=[{startTime:null},{endTime:null},{mode:null},{startTime:'{{ivPast}}'},{endTime:'{{ivStart}}'},{endTime:'{{ivPast}}'},{platform:'  '},{meetingUrl:'  '},{address:'冲突地址'},{platform:'x'.repeat(51)},{meetingUrl:'https://example.com/'+('x'.repeat(1001))},{candidateNote:'x'.repeat(501)},{mode:'BAD'},{mode:0},{mode:'OFFLINE',platform:null,meetingUrl:null,address:'  '},{startTime:'2026-02-30T12:00:00'},{startTime:'2027-02-30T12:00:00'},{startTime:'2027-01-01T12:00:00Z'},{startTime:'2027-01-01T12:00:00+08:00'},{startTime:'not-a-time'},{version:0},{status:'SCHEDULED'},{applicationId:1007},{createdBy:106}];
 for(const protocol of ['javascript:alert(1)','file:///tmp/demo','ftp://example.com/a','//example.com/a','https:///a'])bad.push({meetingUrl:protocol});
 for(let i=0;i<bad.length;i++)add('A','拒绝非法创建参数'+i,'POST','/application/1009/interviews',{...online,...bad[i]},fail);
 listing('A',1009,"pm.test('非法请求无写入',()=>pm.expect(b.data.items).deep.eq([]));");
 create('A',1001,online,"pm.test('字符串归一和时间状态',()=>{pm.expect(b.data.platform).eq('隔离平台');pm.expect(b.data.meetingUrl).eq('https://example.com/meeting');pm.expect(b.data.candidateNote).eq('求职者可见');pm.expect(b.data.address).eq(null);pm.expect(b.data.timingStatus).eq('UPCOMING');});");
 add('A','重复有效安排拒绝','POST','/application/1001/interviews',online,fail);
 for(const w of [1,2,4,5,6,null]){update('A',1001,{...offline,version:0},w===null?unauth:fail,w);cancel('A',1001,0,'测试',w===null?unauth:fail,w);}
 for(const v of [null,-1,0.9,'0',9223372036854776000])update('A',1001,{...offline,version:v},fail);
 update('A',1001,{...offline},fail);update('A',1001,{...offline,version:99},fail);
 update('A',1001,{...online,startTime:'{{ivPast}}',version:0},fail);
 update('A',1001,{...offline,version:0},ok+safe('b.data')+"pm.test('完整替换清除线上信息',()=>{pm.expect(b.data.version).eq(1);pm.expect(b.data.mode).eq('OFFLINE');pm.expect(b.data.platform).eq(null);pm.expect(b.data.meetingUrl).eq(null);pm.expect(b.data.candidateNote).eq(null);pm.expect(b.data.address).eq('上海测试地址');});");
 update('A',1001,{...online,version:0},fail);
 update('A',1001,{...online,version:1},ok+safe('b.data')+"pm.test('切回线上清空线下地址',()=>{pm.expect(b.data.version).eq(2);pm.expect(b.data.address).eq(null);});");
 detail('A',1001,"pm.test('改约保留真实旧快照',()=>{const original=JSON.parse(pm.collectionVariables.get('ivRow1001'));const initial=b.data.events.find(x=>x.eventType==='INTERVIEW_SCHEDULED');pm.expect(initial.interviewChange.startTime).eq(original.startTime);pm.expect(initial.interviewChange.mode).eq('ONLINE');pm.expect(b.data.events.filter(x=>x.eventType==='INTERVIEW_RESCHEDULED')).lengthOf(2);pm.expect(b.data.historyComplete).eq(false);});");
 for(const [v,r]of [[null,'x'],[-1,'x'],[2.5,'x'],['2','x'],[2,' '],[2,'x'.repeat(501)],[1,'x']])cancel('A',1001,v,r,fail);
 cancel('A',1001,2);listing('A',1001,"pm.test('取消版本与原因',()=>{const x=b.data.items[0];pm.expect(x.version).eq(3);pm.expect(x.status).eq('CANCELLED');pm.expect(x.cancelReason).eq('企业沟通后取消');pm.expect(x.timingStatus).eq('CANCELLED');});");
 cancel('A',1001,3,'重复',fail);update('A',1001,{...online,version:3},fail);
 create('A',1001,offline);listing('A',1001,"pm.test('取消历史保留且允许新场次',()=>pm.expect(b.data.items).lengthOf(2));");
 end('A',1001);listing('A',1001,"pm.test('终态只取消新有效安排，旧取消版本保留',()=>{pm.expect(b.data.items.every(x=>x.status==='CANCELLED')).eq(true);pm.expect(b.data.items.map(x=>x.version).sort()).deep.eq([1,3]);});");
 detail('A',1001,"pm.test('自动取消为SYSTEM真实事件',()=>pm.expect(b.data.events.filter(x=>x.eventType==='INTERVIEW_CANCELLED'&&x.actorType==='SYSTEM')).lengthOf(1));");
 add('A','终态不能新建安排','POST','/application/1001/interviews',online,fail);
 create('A',1009,{...online,platform:'x'.repeat(50),candidateNote:'x'.repeat(500),meetingUrl:'https://example.com/'+('x'.repeat(980))});
 update('A',1009,{...offline,address:'地'.repeat(255),version:0},ok);cancel('A',1009,1);
 add('A','进行中已有有效安排不能再建','POST','/application/1002/interviews',online,fail);
 update('A',1002,{...online,version:0},fail,3,'2002');cancel('A',1002,0,'进行中取消',ok,3,'2002');
 update('A',1003,{...online,version:0},fail,3,'2003');cancel('A',1003,0,'过期',fail,3,'2003');
 create('A',1003);listing('A',1003,"pm.test('过期不冒充完成且允许重建',()=>{const old=b.data.items.find(x=>x.id===2003);pm.expect(old.status).eq('SCHEDULED');pm.expect(old.timingStatus).eq('EXPIRED');pm.expect(old.version).eq(0);pm.expect(b.data.items).lengthOf(2);});");
 create('A',1007,online,'',4);add('A','外企读取不泄露场次','GET','/application/1007/interviews',undefined,fail,3);
 update('A',1001,{...online,version:0},fail,3,'{{iv1007}}');
 create('A',1012);detail('A',1012,"pm.test('结束岗位和缺简历不影响排期',()=>{pm.expect(b.data.jobViewable).eq(false);pm.expect(b.data.resumeAvailable).eq(false);});");
 create('A',1013,{startTime:'{{ivCrossStart}}',endTime:'{{ivCrossEnd}}',mode:'OFFLINE',address:'跨日面试地址'});detail('A',1013,"pm.test('暂停岗位跨日安排可读',()=>{pm.expect(b.data.jobViewable).eq(false);pm.expect(b.data.interviews[0].startTime.slice(0,10)).not.eq(b.data.interviews[0].endTime.slice(0,10));});");
 end('A',1010,4);listing('A',1010,"pm.test('拒绝时取消进行中安排',()=>{pm.expect(b.data.items[0].status).eq('CANCELLED');pm.expect(b.data.items[0].version).eq(1);});");
 cancel('A',1024,0,'测试近期排序',ok,3,'2024');
 for(const app of [1015,1016,1019,1020,1022,1023,1028,1029])create('A',app);
 for(const w of [1,3])login('B',w);
 for(const app of [1015,1016])load('B',app);
 add('B','创建事件失败回滚','POST','/application/1014/interviews',online,fail);
 listing('B',1014,"pm.test('失败无安排残留',()=>pm.expect(b.data.items).deep.eq([]));");
 update('B',1015,{...offline,version:0},fail);cancel('B',1016,0,'故障取消',fail);end('B',1017,3,fail);
 listing('B',1015,"pm.test('改约字段和版本回滚',()=>{pm.expect(b.data.items[0].version).eq(0);pm.expect(b.data.items[0].mode).eq('ONLINE');});");
 listing('B',1016,"pm.test('取消状态和版本回滚',()=>{pm.expect(b.data.items[0].version).eq(0);pm.expect(b.data.items[0].status).eq('SCHEDULED');});");
 detail('B',1017,"pm.test('第二场取消失败使整个终态联动回滚',()=>{pm.expect(b.data.status).eq(2);pm.expect(b.data.events).deep.eq([]);pm.expect(b.data.interviews.filter(x=>x.id===20172||x.id===20173).every(x=>x.status==='SCHEDULED'&&x.version===0)).eq(true);});");
 for(const w of [1,3])login('C',w);
 load('C',1015);load('C',1016);create('C',1014);update('C',1015,{...offline,version:0},ok);cancel('C',1016,0);end('C',1017);
 listing('C',1017,"pm.test('取消所有未结束场次，保留过期和原取消',()=>{const m=Object.fromEntries(b.data.items.map(x=>[x.id,x]));for(const id of [20172,20173]){pm.expect(m[id].status).eq('CANCELLED');pm.expect(m[id].version).eq(1);}pm.expect(m[20170].status).eq('SCHEDULED');pm.expect(m[20170].version).eq(0);pm.expect(m[20171].version).eq(0);});");
 detail('C',1017,"pm.test('终态与两条SYSTEM取消事件原子提交',()=>{pm.expect(b.data.status).eq(3);pm.expect(b.data.events.filter(x=>x.eventType==='INTERVIEW_CANCELLED'&&x.actorType==='SYSTEM')).lengthOf(2);pm.expect(b.data.events.filter(x=>x.eventType==='STATUS_CHANGED')).lengthOf(1);});");
 next('C',"pm.test('排除其它用户及终态，开始相同按ID最早',()=>pm.expect(b.data.interview.id).eq(2025));");
 for(const w of [1,3])login('D',w);
 for(const app of [1019,1020,1022,1023,1028,1029])load('D',app);
 race('D',1018,'create');race('D',1019,'reschedule');race('D',1020,'mixed');
 race('D',1021,'createEnd');race('D',1022,'rescheduleEnd');race('D',1023,'cancelEnd');
 race('D',1027,'createEnd',true);race('D',1028,'rescheduleEnd',true);race('D',1029,'cancelEnd',true);
 login('E',3);
 for(const startTime of ['2027-02-30T12:00:00','2027-01-01T12:00:00Z','2027-01-01T12:00:00+08:00','2027-01-01T12:00:00.1234','2027-13-01T12:00:00'])add('E','独立校验严格本地时间 '+startTime,'POST','/application/1009/interviews',{...online,startTime,endTime:'2028-01-03T13:00:00'},fail);
}
function fixture(mode,s,password){const r=spawnSync('D:/jdk17/bin/java.exe',['-cp','target;target/classes;'+fs.readFileSync('target/files-classpath.txt','utf8').trim(),'InterviewFixture',mode,s.database,s.root],{env:{...process.env,...(password?{APPLICATION_FIXTURE_PASSWORD:password}:{})},encoding:'utf8',windowsHide:true});if(r.status!==0)throw Error(r.stderr||r.stdout);console.log(r.stdout.trim()||('Fixture '+mode+' complete'));}
const getId=r=>r.collection?.uid||r.collection?.id||r.data?.id||r.folder?.id||r.id;
const redact=(x,p)=>String(x).replaceAll(p||'___','[PASSWORD]').replaceAll(process.env.POSTMAN_API_KEY||'___','[KEY]').replace(/ApEv_[a-f0-9]{16}/g,'[PASSWORD]').replace(/eyJ[\w-]+\.[\w-]+\.[\w-]+/g,'[JWT]');
(async()=>{const mode=process.argv[2];
 if(['baseline','fault-on','fault-off','verify-rollback','delay-on','delay-off','audit'].includes(mode)){fixture(mode,JSON.parse(fs.readFileSync(STATE)));return;}
 if(mode==='start'){
  const s=JSON.parse(fs.readFileSync(STATE));assert(!s.serverPid&&/^smart_recruit_interviews_\d+$/.test(s.database));
  const url=fs.readFileSync('src/main/resources/application.yml','utf8').match(/url:\s*['"]?(jdbc:mysql:[^\s'"]+)/)?.[1];assert(url?.includes('/smart_recruit?'));
  const out=fs.openSync(s.root+'/server.log','a'),err=fs.openSync(s.root+'/server-error.log','a');
  const child=spawn('D:/jdk17/bin/java.exe',['-cp','target/classes;'+fs.readFileSync('target/files-classpath.txt','utf8').trim(),'com.recruit.smartrecruit.SmartRecruitApplication','--server.port=18087','--spring.datasource.url='+url.replace('/smart_recruit?','/'+s.database+'?'),'--smart-recruit.company-files.root='+s.root+'/storage','--spring.main.banner-mode=off'],{detached:true,windowsHide:true,stdio:['ignore',out,err]});s.serverPid=child.pid;fs.writeFileSync(STATE,JSON.stringify(s,null,2));child.unref();console.log('Isolated interview server started PID='+child.pid);return;
 }
 const c=await connect();let password;try{
  if(mode==='setup'){
   assert(!fs.existsSync(STATE));const env=data(await c.callTool({name:'getEnvironment',arguments:{environmentId:ENV}})).environment;
   assert(env.values.find(x=>x.key==='apHost')?.value===HOST);password=env.values.find(x=>x.key==='apPassword')?.value;assert(password,'Existing authorized synthetic password required');
   const stamp=Date.now(),s={database:'smart_recruit_interviews_'+stamp,root:path.resolve('target/interview-fixtures-'+stamp).replaceAll('\\','/'),port:18087,environmentId:ENV,runners:{},folders:{}};
   if(fs.existsSync('docs/postman/interview-regression.manifest.json')){const manifest=JSON.parse(fs.readFileSync('docs/postman/interview-regression.manifest.json'));assert.equal(manifest.collectionId,MAIN);s.runners=manifest.runners;s.folders=manifest.folders;s.folderId=manifest.folderId;}
   fs.writeFileSync(STATE,JSON.stringify(s,null,2));fixture('setup',s,password);
   for(const [key,value]of [['runInterviewRegression','true'],['interviewFixtureDatabase',s.database]]){
    const current=data(await c.callTool({name:'getEnvironment',arguments:{environmentId:ENV}})).environment;
    const index=current.values.findIndex(x=>x.key===key);
    const body=index<0?[{op:'add',path:'/values/'+current.values.length,value:{key,value,enabled:true,type:'default'}}]:[{op:'replace',path:'/values/'+index+'/value',value}];
    data(await c.callTool({name:'patchEnvironment',arguments:{environmentId:ENV,body}}));
   }
   console.log('Isolated fixtures ready; reused existing authorized secret, only nonsecret regression flags patched.');return;
  }
  const s=JSON.parse(fs.readFileSync(STATE)),save=()=>fs.writeFileSync(STATE,JSON.stringify(s,null,2));
  if(mode==='guard-off'){
   const env=data(await c.callTool({name:'getEnvironment',arguments:{environmentId:ENV}})).environment,index=env.values.findIndex(x=>x.key==='runInterviewRegression');assert(index>=0);
   data(await c.callTool({name:'patchEnvironment',arguments:{environmentId:ENV,body:[{op:'replace',path:'/values/'+index+'/value',value:'false'}]}}));console.log('Interview regression guard disabled; secret unchanged.');return;
  }
  build();
  if(mode==='prepare'){
   const col=data(await c.callTool({name:'getCollection',arguments:{collectionId:MAIN}})).collection;assert.equal(col.info.name,'SmartRecruit API - Optimized Regression');const parent=col.itemRefs.find(x=>x.name==='05 Application');assert(parent);
   if(!s.folderId){s.folderId=getId(data(await c.callTool({name:'createCollectionFolder',arguments:{collectionId:MAIN,folder:bare(parent.id),name:'面试排期回归 2026-10-25'}})));save();}
   for(const stage of ['A','B','C','D','E']){
    const cases=items.filter(x=>x.stage===stage).map(({stage,...x})=>x);
    if(!s.runners[stage]){s.runners[stage]=getId(data(await c.callTool({name:'createCollection',arguments:{workspace:WORKSPACE,collection:{info:{name:'Smart Recruit Interview '+stage+' Execution',schema:'https://schema.getpostman.com/json/collection/v2.1.0/collection.json'},item:cases}}})));save();}
    const full=data(await c.callTool({name:'getCollection',arguments:{collectionId:s.runners[stage],model:'full'}})).collection;assert.equal(full.item.length,cases.length);
    if(!s.folders[stage]){const id=getId(data(await c.callTool({name:'createCollectionFolder',arguments:{collectionId:MAIN,folder:bare(s.folderId),name:stage+' '+({A:'排期权限与生命周期',B:'事件失败整体验证',C:'故障恢复和近期面试',D:'并发排期与终态竞争',E:'严格本地时间格式'}[stage])}})));data(await c.callTool({name:'transferCollectionRequests',arguments:{ids:full.item.map(x=>uid(x.id)),mode:'copy',target:{id:uid(id),model:'folder'},location:{position:'end'}}}));s.folders[stage]=id;save();}
    console.log('Persisted '+stage+' requests='+cases.length);
   }
   fs.writeFileSync('docs/postman/interview-regression.postman_collection.json',JSON.stringify({info:{name:'面试排期持久回归',description:'仅隔离端口18087。A正常，B前baseline及fault-on，B后verify-rollback和fault-off，C恢复，D前delay-on，结束后delay-off和audit。E独立校验时间格式。',schema:'https://schema.getpostman.com/json/collection/v2.1.0/collection.json'},item:['A','B','C','D','E'].map(stage=>({name:stage,item:items.filter(x=>x.stage===stage).map(({stage,...x})=>x)}))},null,2));
   fs.writeFileSync('docs/postman/interview-regression.manifest.json',JSON.stringify({collectionId:MAIN,folderId:s.folderId,runners:s.runners,folders:s.folders,persistentRequests:items.length},null,2));return;
  }
  if(mode==='verify'){
   const col=data(await c.callTool({name:'getCollection',arguments:{collectionId:MAIN,model:'full'}})).collection;const folder=col.item.find(x=>x.name==='05 Application').item.find(x=>bare(x.id)===bare(s.folderId));const actual=folder.item.flatMap(x=>x.item);assert.equal(actual.length,items.length);
   for(const x of items){const y=actual.find(z=>z.name===x.name||z.name===x.name+' Copy');assert(y,x.name);assert.deepStrictEqual(y.event,x.event);assert.equal(y.request.method,x.request.method);assert.equal(typeof y.request.url==='string'?y.request.url:y.request.url.raw,x.request.url);assert.equal(y.request.body?.raw,x.request.body?.raw);}
   console.log('Verified '+items.length+' persistent requests, bodies and scripts');return;
  }
  assert(['A','B','C','D','E'].includes(mode));const env=data(await c.callTool({name:'getEnvironment',arguments:{environmentId:ENV}})).environment;password=env.values.find(x=>x.key==='apPassword')?.value;
  const result=await c.callTool({name:'runCollection',arguments:{collectionId:s.runners[mode],environmentId:ENV,requestTimeout:30000,scriptTimeout:20000,stopOnError:true,stopOnFailure:true}},undefined,{timeout:300000});
  const text=result.content.filter(x=>x.type==='text').map(x=>x.text).join('\n');fs.writeFileSync(s.root+'/result-'+mode+'.json',redact(JSON.stringify(result),password));const pos=text.lastIndexOf('=== ');console.log(redact(pos>=0?text.slice(pos):text.slice(-5000),password));
  if(result.isError||!Number(text.match(/Total assertions:\s*(\d+)/)?.[1])||/Failed assertions:\s*[1-9]|Failed requests:\s*[1-9]/.test(text))process.exitCode=1;
 }finally{await c.close();}
})().catch(e=>{console.error(redact(e.message));process.exitCode=1;});
