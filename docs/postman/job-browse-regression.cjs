const fs=require('fs'),path=require('path'),assert=require('assert'),crypto=require('crypto'),{spawn,spawnSync}=require('child_process');
const {connect,data}=require('./postman-mcp.cjs');
const MAIN='56227183-00855bc5-e68c-4aab-bdc2-faffe0f70f82',WORKSPACE='baec8bed-057c-431c-832a-21a7d3b2a685';
const STATE=process.env.JOB_BROWSE_STATE_FILE||'target/job-browse-state.json',HOST='http://127.0.0.1:18093';
assert(path.resolve(STATE).startsWith(path.resolve('target')+path.sep));
const bare=x=>x.replace(/^56227183-/,''),uid=x=>x.startsWith('56227183-')?x:'56227183-'+x;
const cp=()=> 'target/recommendation-build/target/classes;'+fs.readFileSync('target/files-classpath.txt','utf8').trim();
const getId=r=>r.collection?.uid||r.environment?.uid||r.collection?.id||r.environment?.id||r.data?.id||r.id;
const guard="if(pm.environment.get('runJobBrowseRegression')!=='true'||pm.environment.get('rqFixtureReady')!=='true'||pm.environment.get('rqHost')!=='"+HOST+"'){pm.execution.skipRequest();}";
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
 const all="JSON.parse(pm.collectionVariables.get('jbAll'))";
 const sorted="JSON.parse(pm.collectionVariables.get('jbSalary'))";
 get('默认过滤与分页全量基准','/job?size=100',test('38个唯一审核通过企业在招岗位',"pm.expect(b.data.total).eq(38);pm.expect(b.data.items).lengthOf(38);pm.expect(new Set(b.data.items.map(x=>x.id)).size).eq(38);pm.expect(b.data.items.every(x=>x.status===0&&[201,204].includes(x.companyId))).eq(true);pm.expect(b.data.items.some(x=>[1013,1014,1015,1016,1017].includes(x.id))).eq(false);pm.expect(b.data.items[0].id).eq(2002);")+remember('jbAll'));
 get('完整新旧卡片字段','/job?size=100',test('新增12字段取自数据库',"const x="+item(1001)+";pm.expect(x.companyId).eq(201);pm.expect(x.companyName).eq('已审核回归企业');pm.expect(x.companyLogo).eq('https://example.invalid/job-card-logo.png');pm.expect(x.summary).eq('岗位卡片简介');pm.expect(x.benefits).eq('弹性工作、带薪年假');pm.expect(x.requiredSkills).eq('Java');pm.expect(x.preferredSkills).eq('Docker');pm.expect(x.directionCode).eq('BACKEND');pm.expect(x.requiredAbilitiesSource).eq('LEGACY_TEXT');pm.expect(x.requiredAbilitiesVersion).eq(0);pm.expect(x.publishedAt).eq('2026-10-01T10:00:00');pm.expect(x.deadline).eq('2026-12-31');pm.expect(x).include.all.keys('id','title','category','team','employment','status','city','experience','education','salaryMin','salaryMax','salaryUnit','negotiable','updateTime');")+test('空值与复杂技能保持',"pm.expect("+item(2001)+".companyLogo).eq(null);pm.expect("+item(1020)+".publishedAt).eq(null);pm.expect("+item(1020)+".category).eq(null);pm.expect("+item(1005)+".requiredSkills).eq(null);pm.expect("+item(1007)+".requiredSkills).eq('跨部门沟通');pm.expect("+item(1022)+".requiredAbilitiesVersion).eq(7);"));
 get('显式默认排序','/job?size=100&sort=UPDATED_DESC',unchanged('jbAll'));
 get('空排序沿用默认','/job?size=100&sort=',unchanged('jbAll'));
 get('默认分页','/job',test('默认10条且total正确',"pm.expect(b.data.total).eq(38);pm.expect(b.data.items).deep.eq("+all+".items.slice(0,10));"));
 for(let p=1;p<=6;p++)get('默认排序第'+p+'页','/job?page='+p+'&size=7',test('等于全量切片',"pm.expect(b.data.total).eq(38);pm.expect(b.data.items).deep.eq("+all+".items.slice("+((p-1)*7)+","+(p*7)+"));"));
 get('末页外仍保留total','/job?page=100&size=10',test('空items',"pm.expect(b.data.total).eq(38);pm.expect(b.data.items).deep.eq([]);"));
 for(const [name,word,ids] of [['企业名','星河',[2001]],['旧技能自然语言','Kotlin',[2001]],['加分技能','Kafka',[2001]],['旧加分字段','Docker',[1001]],['结构化重复条目','Redis',[1022,1004,1003,1002]],['结构化原文','数据库索引调优',[1022]],['结构化名称','SQL',[1022,1004,1003,1002]],['复杂自然语言','跨部门沟通',[1007]],['标题','1999',[1999]],['类别','前端开发',[1021,1018]],['城市','深圳',[1008]],['部门','平台组',[1001]],['来源为空列表不回退投影','过时技能',[]],['所有非公开状态','隐藏',[]],['未审核企业名称','隐藏企业待审',[]],['拒绝企业技能','隐藏技能',[]],['无命中','NO_SUCH_JOB',[]],])get('关键词'+name,'/job?size=100&keyword='+encodeURIComponent(word),test('命中且不重复',"pm.expect(b.data.items.map(x=>x.id)).deep.eq("+JSON.stringify(ids)+");pm.expect(b.data.total).eq("+ids.length+");"));
 get('大小写兼容','/job?size=100&keyword=kotlin',test('按当前数据库排序规则不区分大小写',"pm.expect(b.data.items.map(x=>x.id)).deep.eq([2001]);"));
 get('关键词首尾空格','/job?size=100&keyword=%20%20%E6%98%9F%E6%B2%B3%20%20',test('先trim后搜索',"pm.expect(b.data.items.map(x=>x.id)).deep.eq([2001]);"));
 get('纯空格关键词','/job?size=100&keyword=%20%20',unchanged('jbAll'));
 get('LIKE通配符仍不能绕过审核与状态','/job?size=100&keyword=%25',unchanged('jbAll'));
 const valid="x.salaryUnit==='MONTH'&&x.negotiable===false&&Number.isInteger(x.salaryMin)&&x.salaryMin>0&&Number.isInteger(x.salaryMax)&&x.salaryMax>=x.salaryMin";
 get('月薪上限降序全量','/job?size=100&sort=SALARY_DESC',test('全局排序而不是页内排序',"const source="+all+".items;const eligible=x=>"+valid+";const time=(a,b)=>b.updateTime.localeCompare(a.updateTime)||b.id-a.id;const expected=[...source].sort((a,b)=>Number(eligible(b))-Number(eligible(a))||(eligible(a)?b.salaryMax-a.salaryMax:0)||time(a,b));pm.expect(b.data.total).eq(38);pm.expect(b.data.items).deep.eq(expected);pm.expect(b.data.items.slice(0,5).map(x=>x.id)).deep.eq([1028,1027,2001,2002,2003]);")+test('年薪日薪面议和异常范围全部置后',"let tail=false;for(const x of b.data.items){const eligible="+valid+";if(!eligible)tail=true;else pm.expect(tail).eq(false);}const ids=b.data.items.filter(x=>!("+valid+")).map(x=>x.id);pm.expect(ids).include.members([1003,1004,1023,1024,1025,2004,2005,2006,2007,2008,2009,2010,2011,2012,2013]);")+remember('jbSalary'));
 for(let p=1;p<=6;p++)get('薪资排序第'+p+'页','/job?sort=SALARY_DESC&page='+p+'&size=7',test('薪资全量切片一致',"pm.expect(b.data.total).eq(38);pm.expect(b.data.items).deep.eq("+sorted+".items.slice("+((p-1)*7)+","+(p*7)+"));"));
 get('搜索与薪资排序组合','/job?sort=SALARY_DESC&size=100&keyword=Java',test('先过滤再排序分页',"const expected="+sorted+".items.filter(x=>x.companyId!==204&&x.id!==1006&&x.id!==1005&&x.id!==1007);pm.expect(b.data.items).deep.eq(expected);pm.expect(b.data.total).eq(expected.length);"));
 get('搜索企业名与排序组合','/job?sort=SALARY_DESC&keyword='+encodeURIComponent('星河'),test('只保留目标企业',"pm.expect(b.data.total).eq(1);pm.expect(b.data.items[0].id).eq(2001);"));
 for(const q of ['page=0','page=-1','size=0','size=101'])get('非法分页'+q,'/job?'+q,'',1,fail);
 for(const value of ['SALARY_ASC','salary','unknown'])get('非法排序拒绝'+value,'/job?sort='+encodeURIComponent(value),test('参数错误提示明确',"pm.expect(b.message).eq('参数sort格式不正确');"),1,fail);
 get('分页数字格式非法','/job?page=abc',test('参数错误提示',"pm.expect(b.message).eq('参数page格式不正确');"),1,fail);
 get('合法请求在错误后仍正常','/job?size=100',unchanged('jbAll'));
 get('企业主页兼容','/company/201/jobs?pageSize=100',test('37个本企业在招岗位',"pm.expect(b.data.total).eq(37);pm.expect(b.data.items).deep.eq("+all+".items.filter(x=>x.companyId===201));"));
 get('新增审核企业主页空Logo','/company/204/jobs?pageSize=100',test('跨企业不串数据',"pm.expect(b.data.total).eq(1);pm.expect(b.data.items).deep.eq("+all+".items.filter(x=>x.companyId===204));"));
 get('未审核企业主页拒绝','/company/202/jobs','',1,fail);
 login(2);get('另一求职者查询一致','/job?size=100',unchanged('jbAll'),2);
 login(3);
 get('企业管理保留全部状态','/job/company?size=100',test('本企业40岗及原招聘计数',"pm.expect(b.data.total).eq(40);pm.expect(b.data.items.every(x=>x.companyId===201)).eq(true);pm.expect(b.data.items.find(x=>x.id===1015).status).eq(3);const {applicationCount,pendingApplicationCount,currentInterviewApplicationCount,...card}="+item(1001)+";pm.expect(card).deep.eq("+all+".items.find(x=>x.id===1001));pm.expect(applicationCount).eq(0);pm.expect("+item(1012)+".pendingApplicationCount).eq(1);"),3);
 get('企业草稿筛选保留','/job/company?filter=DRAFT',test('只有草稿',"pm.expect(b.data.total).eq(1);pm.expect(b.data.items[0].id).eq(1015);"),3);
 get('企业不可浏览求职者列表','/job','',3,fail);
 login(5);get('未审核企业仍能管理本人岗位','/job/company?size=100',test('管理入口不受公开审核过滤影响',"pm.expect(b.data.total).eq(1);pm.expect(b.data.items[0].id).eq(1016);"),5);
 login(6);get('被拒企业仍能管理本人岗位','/job/company?size=100',test('管理入口归属正确',"pm.expect(b.data.total).eq(1);pm.expect(b.data.items[0].id).eq(1017);"),6);
 login(4);get('管理员不可浏览求职者列表','/job','',4,fail);
 get('匿名HTTP401','/job','',null,unauthorized);
 get('求职者不可浏览企业管理列表','/job/company','',1,fail);
 get('最终重复读取不变','/job?size=100',unchanged('jbAll'));
}

function fixture(mode,s,password){const r=spawnSync('D:/jdk17/bin/java.exe',['-cp','target;'+cp(),'JobBrowseFixture',mode,s.database,s.root],{env:{...process.env,...(password?{RECOMMENDATION_QUERY_FIXTURE_PASSWORD:password}:{})},encoding:'utf8',windowsHide:true});if(r.status!==0)throw Error(r.stderr||r.stdout);console.log(r.stdout.trim()||('Fixture '+mode+' complete'));}
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
  const s={database:'smart_recruit_job_browse_'+stamp,root:path.resolve('target/job-browse-fixture-'+stamp).replaceAll('\\','/'),port:18093,runners:{},folders:{}};
  if(fs.existsSync('docs/postman/job-browse-regression.manifest.json')){
   const m=JSON.parse(fs.readFileSync('docs/postman/job-browse-regression.manifest.json'));assert.equal(m.mainCollectionId,MAIN);
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
  assert(Number.isInteger(s.serverPid)&&s.serverPid>0);assert(/^smart_recruit_job_browse_\d+$/.test(s.database));
  const script='$p=Get-CimInstance Win32_Process -Filter "ProcessId = '+s.serverPid+'"; if($null -eq $p){exit 0}; if($p.Name -ne "java.exe" -or -not $p.CommandLine.Contains("'+s.database+'") -or -not $p.CommandLine.Contains("--server.port=18093")){throw "Refuse unrelated process"}; Stop-Process -Id '+s.serverPid+' -Force -Confirm:$false -ErrorAction Stop';
  const r=spawnSync('powershell.exe',['-NoProfile','-NonInteractive','-Command',script],{windowsHide:true,encoding:'utf8'});if(r.status!==0)throw Error(r.stderr||'Stop failed');s.stoppedPid=s.serverPid;delete s.serverPid;save();console.log('Owned isolated server stopped.');return;
 }
 if(['audit','redis-cleanup'].includes(mode)){fixture(mode,s);return;}
 if(mode==='start'){
  assert(!s.serverPid);const configured=fs.readFileSync('src/main/resources/application.yml','utf8').match(/url:\s*['"]?(jdbc:mysql:[^\s'"]+)/)?.[1];assert(configured?.includes('/smart_recruit?'));
  const out=fs.openSync(s.root+'/server.log','a'),err=fs.openSync(s.root+'/server-error.log','a');
  const p=spawn('D:/jdk17/bin/java.exe',['-cp',cp(),'com.recruit.smartrecruit.SmartRecruitApplication','--server.port=18093','--spring.data.redis.database='+JSON.parse(fs.readFileSync(s.root+'/redis.json')).database,'--spring.datasource.url='+configured.replace('/smart_recruit?','/'+s.database+'?'),'--smart-recruit.company-files.root='+s.root+'/storage','--spring.main.banner-mode=off'],{windowsHide:true,detached:true,stdio:['ignore',out,err]});s.serverPid=p.pid;save();p.unref();console.log('Isolated server PID='+p.pid+' port=18093');return;
 }
 const c=await connect();let password;
 try{
  if(mode==='setup-environment'){
   assert(!s.environmentId);password=JSON.parse(fs.readFileSync(s.root+'/credentials.json')).password;
   const r=data(await c.callTool({name:'createEnvironment',arguments:{workspace:WORKSPACE,environment:{name:'Smart Recruit Job Browse Isolated Regression',values:[{key:'rqHost',value:HOST,type:'default',enabled:true},{key:'rqPassword',value:password,type:'secret',enabled:true},{key:'rqAsOfDate',value:fs.readFileSync(s.root+'/as-of-date.txt','utf8'),type:'default',enabled:true},{key:'runJobBrowseRegression',value:'true',type:'default',enabled:true},{key:'rqFixtureReady',value:'true',type:'default',enabled:true}]}}}));s.environmentId=getId(r);assert(s.environmentId);save();console.log('Postman isolated environment saved: '+s.environmentId);return;
  }
  if(mode==='guard-off'){
   const env=data(await c.callTool({name:'getEnvironment',arguments:{environmentId:s.environmentId}})).environment;
   for(const key of ['runJobBrowseRegression','rqFixtureReady']){const index=env.values.findIndex(x=>x.key===key);assert(index>=0);data(await c.callTool({name:'patchEnvironment',arguments:{environmentId:s.environmentId,body:[{op:'replace',path:'/values/'+index+'/value',value:'false'}]}}));}const verified=data(await c.callTool({name:'getEnvironment',arguments:{environmentId:s.environmentId}})).environment;for(const key of ['runJobBrowseRegression','rqFixtureReady'])assert.equal(verified.values.find(x=>x.key===key).value,'false');console.log('Regression execution guards verified OFF.');return;
  }
  build();
  if(mode==='prepare'){
   const col=data(await c.callTool({name:'getCollection',arguments:{collectionId:MAIN}})).collection;assert.equal(col.info.name,'SmartRecruit API - Optimized Regression');const parent=col.itemRefs.find(x=>x.name==='03 Job');assert(parent);
   if(!s.folderId){s.folderId=getId(data(await c.callTool({name:'createCollectionFolder',arguments:{collectionId:MAIN,folder:bare(parent.id),name:'普通岗位查询回归 2026-11-01'}})));assert(s.folderId);save();}
   for(const stage of ['A']){
    const cases=items.filter(x=>x.stage===stage).map(({stage,...x})=>x);
    if(!s.runners[stage]){s.runners[stage]=getId(data(await c.callTool({name:'createCollection',arguments:{workspace:WORKSPACE,collection:{info:{name:'Smart Recruit Job Browse '+stage+' Execution',schema:'https://schema.getpostman.com/json/collection/v2.1.0/collection.json'},item:cases}}})));assert(s.runners[stage]);save();}
    const full=data(await c.callTool({name:'getCollection',arguments:{collectionId:s.runners[stage],model:'full'}})).collection;assert.equal(full.item.length,cases.length);
    if(!s.folders[stage]){const folderId=getId(data(await c.callTool({name:'createCollectionFolder',arguments:{collectionId:MAIN,folder:bare(s.folderId),name:stage+' '+({A:'审核搜索排序与卡片回归',B:'参数与边界校验',C:'身份与简历归属',D:'只读覆盖及重复查询'}[stage])}})));assert(folderId);data(await c.callTool({name:'transferCollectionRequests',arguments:{ids:full.item.map(x=>uid(x.id)),mode:'copy',target:{id:uid(folderId),model:'folder'},location:{position:'end'}}}));s.folders[stage]=folderId;save();}
    console.log('Persisted '+stage+' requests='+cases.length);
   }
   fs.writeFileSync('docs/postman/job-browse-regression.postman_collection.json',JSON.stringify({info:{name:'普通岗位查询持久回归',schema:'https://schema.getpostman.com/json/collection/v2.1.0/collection.json',description:'所有用例已加入SmartRecruit API - Optimized Regression / 03 Job。仅隔离端口18093；全部业务请求只读；夹具数据由受保护的本地脚本建立。'},item:['A'].map(stage=>({name:stage,item:items.filter(x=>x.stage===stage).map(({stage,...x})=>x)}))},null,2));
   fs.writeFileSync('docs/postman/job-browse-regression.manifest.json',JSON.stringify({mainCollectionId:MAIN,folderId:s.folderId,runners:s.runners,folders:s.folders,requestsByStage:Object.fromEntries(['A'].map(stage=>[stage,items.filter(x=>x.stage===stage).length]))},null,2));return;
  }
  if(mode==='sync'){
   const col=data(await c.callTool({name:'getCollection',arguments:{collectionId:MAIN,model:'full'}})).collection;
   const root=col.item.find(x=>x.name==='03 Job').item.find(x=>bare(x.id)===bare(s.folderId));
   let changed=0;
   for(const [collectionId,actual] of [[MAIN,root.item.flatMap(x=>x.item)],[s.runners.A,data(await c.callTool({name:'getCollection',arguments:{collectionId:s.runners.A,model:'full'}})).collection.item]]){
    for(const item of items){const found=actual.find(x=>x.name===item.name||x.name===item.name+' Copy');assert(found,item.name);
     if(JSON.stringify(found.event)!==JSON.stringify(item.event)){
      data(await c.callTool({name:'updateCollectionRequest',arguments:{collectionId:bare(collectionId),requestId:bare(found.id),events:item.event}}));changed++;
     }
    }
   }
   const exportPath='docs/postman/job-browse-regression.postman_collection.json',exported=JSON.parse(fs.readFileSync(exportPath));
   exported.item=[{name:'A',item:items.map(({stage,...x})=>x)}];fs.writeFileSync(exportPath,JSON.stringify(exported,null,2));
   console.log('Updated '+changed+' request scripts in main collection and runner; local export synchronized.');return;
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
