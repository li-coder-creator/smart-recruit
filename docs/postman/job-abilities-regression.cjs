const fs=require('fs'),path=require('path'),assert=require('assert'),crypto=require('crypto'),{spawn,spawnSync}=require('child_process');
const {connect,data}=require('./postman-mcp.cjs');
const MAIN='56227183-00855bc5-e68c-4aab-bdc2-faffe0f70f82',WORKSPACE='baec8bed-057c-431c-832a-21a7d3b2a685';
const STATE=process.env.JOB_ABILITIES_STATE_FILE||'target/job-abilities-state.json',HOST='http://127.0.0.1:18090';
assert(path.resolve(STATE).startsWith(path.resolve('target')+path.sep));
const bare=x=>x.replace(/^56227183-/,''),uid=x=>x.startsWith('56227183-')?x:'56227183-'+x;
const cp=()=> 'target/recommendation-build/target/classes;'+fs.readFileSync('target/files-classpath.txt','utf8').trim();
const getId=r=>r.collection?.uid||r.environment?.uid||r.collection?.id||r.environment?.id||r.data?.id||r.id;
const guard="if(pm.environment.get('runJobAbilitiesRegression')!=='true'||pm.environment.get('jaFixtureReady')!=='true'||pm.environment.get('jaHost')!=='"+HOST+"'){pm.execution.skipRequest();}";
const ok="const b=pm.response.json();pm.test('HTTP200与业务200',()=>{pm.expect(pm.response.code).eq(200);pm.expect(b.code).eq(200);});";
const fail="const b=pm.response.json();pm.test('业务拒绝且无成功数据',()=>{pm.expect(pm.response.code).eq(200);pm.expect(b.code).eq(500);pm.expect(b.data).eq(null);});";
const unauthorized="pm.test('未登录HTTP401',()=>pm.expect(pm.response.code).eq(401));";
let items=[];
function add(stage,name,method,url,body,tests='',who=1,expected=ok){
 const headers=who?[{key:'Authorization',value:'Bearer {{jaToken'+who+'}}'}]:[];
 const request={method,url:'{{jaHost}}'+url,auth:{type:'noauth'},header:headers};
 if(body!==undefined){headers.push({key:'Content-Type',value:'application/json'});request.body={mode:'raw',raw:JSON.stringify(body)};}
 items.push({stage,name:String(items.length+1).padStart(3,'0')+' '+name,request,event:[{listen:'prerequest',script:{type:'text/javascript',exec:[guard]}},{listen:'test',script:{type:'text/javascript',exec:[expected+tests]}}]});
}
const test=(name,expression)=>"pm.test("+JSON.stringify(name)+",()=>{"+expression+"});";
const remember=key=>"pm.collectionVariables.set('"+key+"',JSON.stringify(b.data));";
const unchanged=key=>test('失败后全部业务字段未变',"pm.expect(b.data).deep.eq(JSON.parse(pm.collectionVariables.get('"+key+"')))");
function login(stage,who){
 add(stage,'登录隔离身份'+who,'POST','/user/login',{username:'ja_input_'+who,password:'{{jaPassword}}'},"pm.collectionVariables.set('jaToken"+who+"',b.data);",null);
 add(stage,'核对身份'+who,'GET','/user/info',undefined,test('ID与角色',"pm.expect(b.data.id).eq("+(100+who)+");pm.expect(b.data.role).eq('"+(who===3?'JOB_SEEKER':'COMPANY')+"');"),who);
}
const full={title:'推荐岗位',salaryMin:10000,salaryMax:20000,city:'上海',category:'后端开发',employment:'全职',workMode:'现场',address:'测试地址',responsibilities:'实现业务接口',requirements:'能独立完成任务'};
const abilities=[{rawText:' 熟悉Java ',abilityName:'Java',proficiency:'FAMILIAR'},{rawText:'按规范编写过程文件'},{rawText:'跨部门沟通'}];
const structured=(version,list=abilities)=>({...full,requiredAbilitiesVersion:version,requiredAbilities:list});
function detail(stage,id,extra='',who=1){add(stage,'读取岗位详情 '+id,'GET','/job/company/'+id,undefined,extra,who);}
const content=(source,version,text)=>test('来源版本及展示文本',"pm.expect(b.data.requiredAbilitiesSource).eq('"+source+"');pm.expect(b.data.requiredAbilitiesVersion).eq("+version+");"+(text!==undefined?"pm.expect(b.data.requiredSkills).eq("+JSON.stringify(text)+");":""));
function build(){
 for(const who of [1,2,3,4])login('A',who);
 detail('A',901,content('LEGACY_TEXT',0,'旧原文')+test('旧数据子项为null',"pm.expect(b.data.requiredAbilities).eq(null);"));
 add('A','旧请求更新文本版本递增','PUT','/job/901',{...full,requiredSkills:'Java, Spring Boot',directionCode:'BACKEND'});
 detail('A',901,content('LEGACY_TEXT',1,'Java, Spring Boot'));
 add('A','旧版本禁止转为结构化','PUT','/job/901',structured(0),'',1,fail);
 detail('A',901,content('LEGACY_TEXT',1,'Java, Spring Boot'));
 add('A','正确版本切换结构化','PUT','/job/901',{...structured(1),directionCode:'BACKEND'});
 detail('A',901,content('STRUCTURED',2,' 熟悉Java \n按规范编写过程文件\n跨部门沟通')+test('子项顺序原文程度',"pm.expect(b.data.directionCode).eq('BACKEND');pm.expect(b.data.requiredAbilities.map(x=>x.rawText)).deep.eq("+JSON.stringify(abilities.map(x=>x.rawText))+");pm.expect(b.data.requiredAbilities[0].proficiency).eq('FAMILIAR');")+remember('jaBefore'));
 detail('A',901,unchanged('jaBefore'));
 add('A','旧表单省略能力保持内容','PUT','/job/901',full);
 detail('A',901,content('STRUCTURED',2)+test('方向保留',"pm.expect(b.data.directionCode).eq('BACKEND');"));
 add('A','null数组不是清空','PUT','/job/901',{...full,requiredAbilities:null});
 add('A','旧展示文本原样回传可兼容','PUT','/job/901',{...full,requiredSkills:abilities.map(x=>x.rawText).join('\n')});
 detail('A',901,content('STRUCTURED',2)+remember('jaBefore'));
 for(const body of [{...full,requiredSkills:null},{...full,requiredSkills:'不同文本'},structured(1),{...structured(2),requiredSkills:'不同文本'}])add('A','拒绝覆盖新来源或过期列表','PUT','/job/901',body,'',1,fail);
 detail('A',901,unchanged('jaBefore'));
 add('A','草稿明确清空列表','PUT','/job/901',structured(2,[]));
 detail('A',901,content('STRUCTURED',3,null)+test('新来源空数组',"pm.expect(b.data.requiredAbilities).deep.eq([]);"));
 add('A','空能力草稿拒绝发布','PUT','/job/901/publish',undefined,'',1,fail);
 add('A','空新来源禁止回退文本','PUT','/job/901',{...full,requiredSkills:'Java'},'',1,fail);
 add('A','重新补充能力','PUT','/job/901',structured(3));
 add('A','发布已有结构化草稿','PUT','/job/901/publish');
 add('A','求职者读取新增能力','GET','/job/901',undefined,content('STRUCTURED',4)+test('状态招聘中',"pm.expect(b.data.status).eq(0);"),3);
 add('A','已发布不能清空能力','PUT','/job/901',structured(4,[]),'',1,fail);
 detail('A',901,content('STRUCTURED',4));
 add('A','暂停招聘','PUT','/job/901/pause');
 add('A','暂停不允许求职者查看','GET','/job/901',undefined,'',3,fail);
 add('A','暂停也不能清空能力','PUT','/job/901',structured(4,[]),'',1,fail);
 add('A','恢复招聘','PUT','/job/901/resume');
 add('A','结束招聘','PUT','/job/901/end');
 detail('A',901,content('STRUCTURED',4)+test('状态已结束',"pm.expect(b.data.status).eq(2);"));
 add('A','已发布历史不可物理删除','DELETE','/job/901',undefined,'',1,fail);
 for(const body of [{...structured(0,[]),title:'空能力直接发布'}, {...full,title:'无必备技能'}])add('A','直接发布缺能力拒绝','POST','/job',body,'',1,fail);
 add('A','直接发布新能力岗位','POST','/job',{...structured(0),title:'直接发布回归'});
 add('A','分页定位直接发布岗位','GET','/job/company?page=1&size=10&keyword='+encodeURIComponent('直接发布回归'),undefined,test('唯一发布记录',"pm.expect(b.data.items).lengthOf(1);pm.collectionVariables.set('jaPublished',b.data.items[0].id);"));
 detail('A','{{jaPublished}}',content('STRUCTURED',1)+test('真实发布时间',"pm.expect(b.data.publishedAt).a('string');"));
 add('A','求职者读缓存详情','GET','/job/{{jaPublished}}',undefined,content('STRUCTURED',1),3);
 add('A','新草稿携带非零版本拒绝','POST','/job/drafts',{...structured(1),title:'过期新建'},'',1,fail);
 add('A','新增逐条能力草稿','POST','/job/drafts',{...structured(0,[]),title:'新建空草稿'});
 add('A','分页定位空草稿','GET','/job/company?page=1&size=10&keyword='+encodeURIComponent('新建空草稿'),undefined,test('找到草稿',"pm.expect(b.data.items).lengthOf(1);pm.collectionVariables.set('jaEmpty',b.data.items[0].id);"));
 detail('A','{{jaEmpty}}',content('STRUCTURED',1,null)+test('数组为空',"pm.expect(b.data.requiredAbilities).deep.eq([]);"));
 add('A','null数组的新草稿保持旧模式','POST','/job/drafts',{title:'null数组旧草稿',requiredAbilities:null,requiredSkills:'旧原文'});
 add('A','定位旧草稿','GET','/job/company?page=1&size=10&keyword='+encodeURIComponent('null数组旧草稿'),undefined,"pm.collectionVariables.set('jaLegacy',b.data.items[0].id);");
 detail('A','{{jaLegacy}}',content('LEGACY_TEXT',0,'旧原文'));
 add('A','旧文本完整岗位仍允许发布','PUT','/job/{{jaLegacy}}',{...full,requiredSkills:'旧原文'});
 add('A','旧草稿可发布无需伪结构化','PUT','/job/{{jaLegacy}}/publish');
 detail('A','{{jaLegacy}}',content('LEGACY_TEXT',0,'旧原文'));
 add('A','旧类别改变清除方向','PUT','/job/901',{...full,category:'研发',directionCode:'BACKEND'});
 add('A','同类旧表单保留方向','PUT','/job/901',{...full,category:'研发'});
 detail('A',901,test('方向保留',"pm.expect(b.data.directionCode).eq('BACKEND');"));
 add('A','改变类别未传方向则清空','PUT','/job/901',{...full,category:'技术'});
 detail('A',901,test('未知而非自动推断',"pm.expect(b.data.directionCode).eq(null);"));
 add('A','宽泛类别明确方向可保存','PUT','/job/901',{...full,category:'技术',directionCode:'FRONTEND'});
 add('A','方向可显式清空','PUT','/job/901',{...full,category:'技术',directionCode:null});
 detail('A',901,test('显式null',"pm.expect(b.data.directionCode).eq(null);")+remember('jaBefore'));
 const invalid=[
 {...structured(4),directionCode:'FRONTEND'}, {...full,directionCode:'BAD'}, {...full,directionCode:0},
 {...full,requiredAbilities:abilities}, {...structured(-1)}, {...structured('4')}, {...structured(4.5)},
 {...structured(4),requiredAbilitiesVersion:null}, {...structured(4),requiredAbilitiesVersion:1e30},
 {...structured(4,[null])}, {...structured(4,[{rawText:'  '}])}, {...structured(4,[{rawText:'x'.repeat(501)}])},
 {...structured(4,[{rawText:'a'.repeat(250)},{rawText:'b'.repeat(250)}])},
 {...structured(4,Array.from({length:21},()=>({rawText:'Java'})))},
 {...structured(4,[{rawText:'Java',abilityName:'x'.repeat(51)}])},
 {...structured(4,[{rawText:'Java',proficiency:'BAD'}])}, {...structured(4,[{rawText:'Java',proficiency:0}])},
 ...['jobId','id','sortOrder','canonicalCode'].map(key=>structured(4,[{rawText:'Java',[key]:123}])),
 ...['requiredAbilitiesSource','status','companyId'].map(key=>({...full,[key]:1}))
 ];
 for(const body of invalid)add('A','非法输入不改变岗位','PUT','/job/901',body,'',1,fail);
 detail('A',901,unchanged('jaBefore'));
 add('A','投影恰好500字符可保存草稿','PUT','/job/905',structured(0,[{rawText:'x'.repeat(500)}]));
 detail('A',905,content('STRUCTURED',1,'x'.repeat(500)));
 add('A','20条要求允许且按数组序','PUT','/job/905',structured(1,Array.from({length:20},(_,i)=>({rawText:'要求'+i}))));
 detail('A',905,test('20条有序',"pm.expect(b.data.requiredAbilities).lengthOf(20);pm.expect(b.data.requiredAbilities.map(x=>x.rawText)).deep.eq(Array.from({length:20},(_,i)=>'要求'+i));"));
 for(const who of [2,3,4]){
  add('A','越权或未审核禁止修改','PUT','/job/901',structured(4),'',who,fail);
  add('A','越权或未审核禁止创建','POST','/job/drafts',structured(0),'',who,who===2?ok:fail);
  add('A','越权禁止读取企业详情','GET','/job/company/901',undefined,'',who,fail);
 }
 add('A','未登录不能写','PUT','/job/901',structured(4),'',null,unauthorized);
 add('A','不存在岗位拒绝写','PUT','/job/999999',structured(0),'',1,fail);
 add('A','求职者不能查看草稿','GET','/job/905',undefined,'',3,fail);
 add('A','准备日志故障回滚对象','PUT','/job/903',structured(0));
 add('A','准备发布故障回滚对象','PUT','/job/904',structured(0));
 login('B',1);
 detail('B',903,remember('jaFaultBefore'));
 add('B','日志失败主表子表版本全部回滚','PUT','/job/903',structured(1,[{rawText:'新能力'}]),test('确实触发数据库错误',"pm.expect(b.message).include('系统异常');"),1,fail);
 detail('B',903,unchanged('jaFaultBefore'));
 add('B','日志失败新草稿回滚','POST','/job/drafts',{...structured(0),title:'日志故障新草稿'},'',1,fail);
 add('B','日志失败直接发布回滚','POST','/job',{...structured(0),title:'日志故障直接发布'},'',1,fail);
 add('B','日志失败草稿发布回滚','PUT','/job/904/publish',undefined,'',1,fail);
 detail('B',904,test('仍是草稿',"pm.expect(b.data.status).eq(3);pm.expect(b.data.publishedAt).eq(null);"));
 login('C',1);
 detail('C',903,remember('jaChildBefore'));
 add('C','子表第二项失败整次替换回滚','PUT','/job/903',structured(1,[{rawText:'首项成功'},{rawText:'故障条目'}]),'',1,fail);
 detail('C',903,unchanged('jaChildBefore'));
 add('C','子表失败新建主表也回滚','POST','/job/drafts',{...structured(0,[{rawText:'故障条目'}]),title:'子表故障草稿'},'',1,fail);
 login('D',1);
 const race="let done=0;const results=[];const send=(body,index)=>setTimeout(()=>{const started=Date.now();pm.sendRequest({url:pm.environment.get('jaHost')+'/job/902',method:'PUT',header:{Authorization:'Bearer '+pm.collectionVariables.get('jaToken1'),'Content-Type':'application/json'},body:{mode:'raw',raw:JSON.stringify(body)}},(err,res)=>{pm.test('并发请求可达',()=>pm.expect(err).eq(null));if(!err)results.push({code:res.json().code,started,ended:Date.now()});if(++done===2){pm.test('一次成功一次版本拒绝',()=>pm.expect(results.map(x=>x.code).sort()).deep.eq([200,500]));pm.test('请求实际重叠',()=>pm.expect(Math.max(...results.map(x=>x.started))).below(Math.min(...results.map(x=>x.ended))));}});},index*100);";
 detail('D',902,race+"send("+JSON.stringify(structured(0,[{rawText:'并发赢家'}]))+",0);send("+JSON.stringify(structured(0,[{rawText:'并发另一方'}]))+",1);");
 detail('D',902,content('STRUCTURED',1)+test('单次成功无混合列表',"pm.expect(b.data.requiredAbilities).lengthOf(1);pm.expect(b.data.requiredSkills).eq(b.data.requiredAbilities[0].rawText);"));
 login('E',1);
 add('E','故障解除正常替换并提交','PUT','/job/903',structured(1,[{rawText:'故障解除后能力'}]));
 detail('E',903,content('STRUCTURED',2,'故障解除后能力'));
 detail('E',903,content('STRUCTURED',2,'故障解除后能力'));
 add('E','故障解除可正常发布草稿','PUT','/job/904/publish');
 detail('E',904,test('已发布',"pm.expect(b.data.status).eq(0);"));
 add('E','删除结构化草稿级联子表','DELETE','/job/905');
 add('E','删除后缓存不再可见','GET','/job/company/905',undefined,'',1,fail);
}

function fixture(mode,s,password){const r=spawnSync('D:/jdk17/bin/java.exe',['-cp','target;'+cp(),'JobAbilitiesFixture',mode,s.database,s.root],{env:{...process.env,...(password?{JOB_ABILITIES_FIXTURE_PASSWORD:password}:{})},encoding:'utf8',windowsHide:true});if(r.status!==0)throw Error(r.stderr||r.stdout);console.log(r.stdout.trim()||('Fixture '+mode+' complete'));}
const redact=(value,password)=>String(value).replaceAll(password||'___','[REDACTED]').replace(/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g,'[REDACTED_JWT]');
(async()=>{
 const mode=process.argv[2];
 if(mode==='setup-local'){
  assert(!fs.existsSync(STATE),'Existing state; do not overwrite');const stamp=Date.now(),password='JaIn_'+crypto.randomBytes(8).toString('hex');
  const s={database:'smart_recruit_job_abilities_'+stamp,root:path.resolve('target/job-abilities-fixture-'+stamp).replaceAll('\\','/'),port:18090,runners:{},folders:{}};
  if(fs.existsSync('docs/postman/job-abilities-regression.manifest.json')){
   const m=JSON.parse(fs.readFileSync('docs/postman/job-abilities-regression.manifest.json'));assert.equal(m.mainCollectionId,MAIN);
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
  assert(Number.isInteger(s.serverPid)&&s.serverPid>0);assert(/^smart_recruit_job_abilities_\d+$/.test(s.database));
  const script='$p=Get-CimInstance Win32_Process -Filter "ProcessId = '+s.serverPid+'"; if($null -eq $p){exit 0}; if($p.Name -ne "java.exe" -or -not $p.CommandLine.Contains("'+s.database+'") -or -not $p.CommandLine.Contains("--server.port=18090")){throw "Refuse unrelated process"}; Stop-Process -Id '+s.serverPid+' -Force -Confirm:$false -ErrorAction Stop';
  const r=spawnSync('powershell.exe',['-NoProfile','-NonInteractive','-Command',script],{windowsHide:true,encoding:'utf8'});if(r.status!==0)throw Error(r.stderr||'Stop failed');s.stoppedPid=s.serverPid;delete s.serverPid;save();console.log('Owned isolated server stopped.');return;
 }
 if(['baseline','fault-on','fault-off','child-fault-on','child-fault-off','verify-rollback','delay-on','delay-off','audit','redis-cleanup'].includes(mode)){fixture(mode,s);return;}
 if(mode==='start'){
  assert(!s.serverPid);const configured=fs.readFileSync('src/main/resources/application.yml','utf8').match(/url:\s*['"]?(jdbc:mysql:[^\s'"]+)/)?.[1];assert(configured?.includes('/smart_recruit?'));
  const out=fs.openSync(s.root+'/server.log','a'),err=fs.openSync(s.root+'/server-error.log','a');
  const p=spawn('D:/jdk17/bin/java.exe',['-cp',cp(),'com.recruit.smartrecruit.SmartRecruitApplication','--server.port=18090','--spring.data.redis.database='+JSON.parse(fs.readFileSync(s.root+'/redis.json')).database,'--spring.datasource.url='+configured.replace('/smart_recruit?','/'+s.database+'?'),'--smart-recruit.company-files.root='+s.root+'/storage','--spring.main.banner-mode=off'],{windowsHide:true,detached:true,stdio:['ignore',out,err]});s.serverPid=p.pid;save();p.unref();console.log('Isolated server PID='+p.pid+' port=18090');return;
 }
 const c=await connect();let password;
 try{
  if(mode==='setup-environment'){
   assert(!s.environmentId);password=JSON.parse(fs.readFileSync(s.root+'/credentials.json')).password;
   const r=data(await c.callTool({name:'createEnvironment',arguments:{workspace:WORKSPACE,environment:{name:'Smart Recruit Job Abilities Isolated Regression',values:[{key:'jaHost',value:HOST,type:'default',enabled:true},{key:'jaPassword',value:password,type:'secret',enabled:true},{key:'runJobAbilitiesRegression',value:'true',type:'default',enabled:true},{key:'jaFixtureReady',value:'true',type:'default',enabled:true}]}}}));s.environmentId=getId(r);assert(s.environmentId);save();console.log('Postman isolated environment saved: '+s.environmentId);return;
  }
  if(mode==='guard-off'){
   const env=data(await c.callTool({name:'getEnvironment',arguments:{environmentId:s.environmentId}})).environment;
   for(const key of ['runJobAbilitiesRegression','jaFixtureReady']){const index=env.values.findIndex(x=>x.key===key);assert(index>=0);data(await c.callTool({name:'patchEnvironment',arguments:{environmentId:s.environmentId,body:[{op:'replace',path:'/values/'+index+'/value',value:'false'}]}}));}const verified=data(await c.callTool({name:'getEnvironment',arguments:{environmentId:s.environmentId}})).environment;for(const key of ['runJobAbilitiesRegression','jaFixtureReady'])assert.equal(verified.values.find(x=>x.key===key).value,'false');console.log('Regression execution guards verified OFF.');return;
  }
  build();
  if(mode==='prepare'){
   const col=data(await c.callTool({name:'getCollection',arguments:{collectionId:MAIN}})).collection;assert.equal(col.info.name,'SmartRecruit API - Optimized Regression');const parent=col.itemRefs.find(x=>x.name==='03 Job');assert(parent);
   if(!s.folderId){s.folderId=getId(data(await c.callTool({name:'createCollectionFolder',arguments:{collectionId:MAIN,folder:bare(parent.id),name:'岗位能力接入回归 2026-11-01'}})));assert(s.folderId);save();}
   for(const stage of ['A','B','C','D','E']){
    const cases=items.filter(x=>x.stage===stage).map(({stage,...x})=>x);
    if(!s.runners[stage]){s.runners[stage]=getId(data(await c.callTool({name:'createCollection',arguments:{workspace:WORKSPACE,collection:{info:{name:'Smart Recruit Job Abilities '+stage+' Execution',schema:'https://schema.getpostman.com/json/collection/v2.1.0/collection.json'},item:cases}}})));assert(s.runners[stage]);save();}
    const full=data(await c.callTool({name:'getCollection',arguments:{collectionId:s.runners[stage],model:'full'}})).collection;assert.equal(full.item.length,cases.length);
    if(!s.folders[stage]){const folderId=getId(data(await c.callTool({name:'createCollectionFolder',arguments:{collectionId:MAIN,folder:bare(s.folderId),name:stage+' '+({A:'保存校验权限兼容',B:'操作记录故障回滚',C:'子表故障回滚',D:'父锁并发版本一致性',E:'解除故障及级联删除'}[stage])}})));assert(folderId);data(await c.callTool({name:'transferCollectionRequests',arguments:{ids:full.item.map(x=>uid(x.id)),mode:'copy',target:{id:uid(folderId),model:'folder'},location:{position:'end'}}}));s.folders[stage]=folderId;save();}
    console.log('Persisted '+stage+' requests='+cases.length);
   }
   fs.writeFileSync('docs/postman/job-abilities-regression.postman_collection.json',JSON.stringify({info:{name:'岗位能力接入持久回归',schema:'https://schema.getpostman.com/json/collection/v2.1.0/collection.json',description:'所有用例已加入SmartRecruit API - Optimized Regression / 03 Job。仅隔离端口18090；B前开启日志故障、C前切换子表故障、D前开启延迟，阶段后全部清理。'},item:['A','B','C','D','E'].map(stage=>({name:stage,item:items.filter(x=>x.stage===stage).map(({stage,...x})=>x)}))},null,2));
   fs.writeFileSync('docs/postman/job-abilities-regression.manifest.json',JSON.stringify({mainCollectionId:MAIN,folderId:s.folderId,runners:s.runners,folders:s.folders,requestsByStage:Object.fromEntries(['A','B','C','D','E'].map(stage=>[stage,items.filter(x=>x.stage===stage).length]))},null,2));return;
  }
  if(mode==='verify'){
   const col=data(await c.callTool({name:'getCollection',arguments:{collectionId:MAIN,model:'full'}})).collection;const root=col.item.find(x=>x.name==='03 Job').item.find(x=>bare(x.id)===bare(s.folderId));const actual=root.item.flatMap(x=>x.item);assert.equal(actual.length,items.length);
   for(const item of items){const found=actual.find(x=>x.name===item.name||x.name===item.name+' Copy');assert(found,item.name);assert.deepStrictEqual(found.event,item.event);assert.equal(found.request.method,item.request.method);assert.equal(typeof found.request.url==='string'?found.request.url:found.request.url.raw,item.request.url);assert.equal(found.request.body?.raw,item.request.body?.raw);}console.log('Verified '+actual.length+' exact persistent requests in required collection');return;
  }
  assert(['A','B','C','D','E'].includes(mode));const env=data(await c.callTool({name:'getEnvironment',arguments:{environmentId:s.environmentId}})).environment;password=env.values.find(x=>x.key==='jaPassword')?.value;
  const result=await c.callTool({name:'runCollection',arguments:{collectionId:s.runners[mode],environmentId:s.environmentId,requestTimeout:30000,scriptTimeout:20000,stopOnError:true,stopOnFailure:true}},undefined,{timeout:300000});
  const output=result.content.filter(x=>x.type==='text').map(x=>x.text).join('\n');fs.writeFileSync(s.root+'/result-'+mode+'.json',redact(JSON.stringify(result),password));
  const tail=output.lastIndexOf('=== ');console.log(redact(tail>=0?output.slice(tail):output.slice(-5000),password));
  for(const line of output.match(/^.*RS_RACE=.*$/gm)||[])console.log(redact(line,password));
  if(result.isError||!Number(output.match(/Total assertions:\s*(\d+)/)?.[1])||/Failed assertions:\s*[1-9]|Failed requests:\s*[1-9]/.test(output))process.exitCode=1;
 }finally{await c.close();}
})().catch(e=>{console.error(redact(e.message,process.env.POSTMAN_API_KEY));process.exitCode=1;});
