const fs=require('fs'),path=require('path'),assert=require('assert'),crypto=require('crypto'),{spawn,spawnSync}=require('child_process');
const {connect,data}=require('./postman-mcp.cjs');
const MAIN='56227183-00855bc5-e68c-4aab-bdc2-faffe0f70f82',WORKSPACE='baec8bed-057c-431c-832a-21a7d3b2a685';
const STATE=process.env.RESUME_INPUT_STATE_FILE||'target/resume-input-state.json',HOST='http://127.0.0.1:18089';
assert(path.resolve(STATE).startsWith(path.resolve('target')+path.sep));
const bare=x=>x.replace(/^56227183-/,''),uid=x=>x.startsWith('56227183-')?x:'56227183-'+x;
const cp=()=> 'target/recommendation-build/classes;'+fs.readFileSync('target/files-classpath.txt','utf8').trim();
const getId=r=>r.collection?.uid||r.environment?.uid||r.collection?.id||r.environment?.id||r.data?.id||r.id;
const guard="if(pm.environment.get('runResumeInputRegression')!=='true'||pm.environment.get('rsFixtureReady')!=='true'||pm.environment.get('rsHost')!=='"+HOST+"'){pm.execution.skipRequest();}";
const ok="const b=pm.response.json();pm.test('HTTP200与业务200',()=>{pm.expect(pm.response.code).eq(200);pm.expect(b.code).eq(200);});";
const fail="const b=pm.response.json();pm.test('业务拒绝且无成功数据',()=>{pm.expect(pm.response.code).eq(200);pm.expect(b.code).eq(500);pm.expect(b.data).eq(null);});";
const unauthorized="pm.test('未登录HTTP401',()=>pm.expect(pm.response.code).eq(401));";
let items=[];
function add(stage,name,method,url,body,tests='',who=1,expected=ok){
 const headers=who?[{key:'Authorization',value:'Bearer {{rsToken'+who+'}}'}]:[];
 const request={method,url:'{{rsHost}}'+url,auth:{type:'noauth'},header:headers};
 if(body!==undefined){headers.push({key:'Content-Type',value:'application/json'});request.body={mode:'raw',raw:JSON.stringify(body)};}
 items.push({stage,name:String(items.length+1).padStart(3,'0')+' '+name,request,event:[{listen:'prerequest',script:{type:'text/javascript',exec:[guard]}},{listen:'test',script:{type:'text/javascript',exec:[expected+tests]}}]});
}
const test=(name,expression)=>"pm.test("+JSON.stringify(name)+",()=>{"+expression+"});";
const remember=key=>"pm.collectionVariables.set('"+key+"',JSON.stringify(b.data));";
const unchanged=key=>test('失败后全部业务字段未变',"pm.expect(b.data).deep.eq(JSON.parse(pm.collectionVariables.get('"+key+"')))");
function login(stage,who){
 add(stage,'登录隔离身份'+who,'POST','/user/login',{username:'rs_input_'+who,password:'{{rsPassword}}'},"pm.collectionVariables.set('rsToken"+who+"',b.data);",null);
 add(stage,'核对身份'+who,'GET','/user/info',undefined,test('ID与角色',"pm.expect(b.data.id).eq("+(100+who)+");pm.expect(b.data.role).eq('"+(who<=2?'JOB_SEEKER':who===3?'COMPANY':'ADMIN')+"');"),who);
}
const internship={companyName:'回归企业',position:'后端实习',experienceType:'INTERNSHIP',startDate:'2026-01-01',endDate:'2026-06-30',isCurrent:false,description:'完成接口开发'};
const formal={...internship,position:'后端开发',experienceType:'FORMAL'};
function mainDetail(extra=''){add('A','重读完整简历','GET','/resume/{{rsMain}}/detail',undefined,extra);}
function build(){
 for(const who of [1,2,3,4])login('A',who);
 add('A','创建含身份与零经验声明的简历','POST','/resume',{title:'推荐输入回归',careerStage:'NEW_GRAD',formalExperienceStatus:'NONE'},"pm.collectionVariables.set('rsMain',b.data);"+test('返回自增主键','pm.expect(b.data).a("number");'));
 mainDetail(test('完整层级与声明',"pm.expect(b.data.resume.careerStage).eq('NEW_GRAD');pm.expect(b.data.resume.formalExperienceStatus).eq('NONE');pm.expect(b.data.experiences).deep.eq([]);pm.expect(b.data.skills).deep.eq([]);"));
 add('A','旧基础表单省略新字段','PUT','/resume/{{rsMain}}/basic',{title:'推荐输入回归',phone:null});
 add('A','重读基础资料声明未丢失','GET','/resume/{{rsMain}}',undefined,test('省略保留',"pm.expect(b.data.careerStage).eq('NEW_GRAD');pm.expect(b.data.formalExperienceStatus).eq('NONE');"));
 add('A','NONE允许保存实习','POST','/resume/{{rsMain}}/experience',internship);
 add('A','实习类型与false正确返回','GET','/resume/{{rsMain}}/experience',undefined,test('一条实习',"pm.expect(b.data).lengthOf(1);pm.expect(b.data[0].experienceType).eq('INTERNSHIP');pm.expect(b.data[0].isCurrent).eq(false);")+"pm.collectionVariables.set('rsIntern',b.data[0].id);");
 mainDetail(remember('beforeNoneConflict'));
 add('A','NONE拒绝正式经历','POST','/resume/{{rsMain}}/experience',formal,'',1,fail);
 mainDetail(unchanged('beforeNoneConflict'));
 add('A','NONE拒绝旧请求的未分类经历','POST','/resume/{{rsMain}}/experience',{companyName:'旧企业',position:'旧职位'},'',1,fail);
 mainDetail(unchanged('beforeNoneConflict'));
 for(const body of [{title:'推荐输入回归',careerStage:null},{title:'推荐输入回归',careerStage:'BAD'},{title:'推荐输入回归',careerStage:0},{title:'推荐输入回归',formalExperienceStatus:null},{title:'推荐输入回归',formalExperienceStatus:'BAD'},{title:'推荐输入回归',userId:102}])add('A','非法声明或归属字段拒绝','PUT','/resume/{{rsMain}}/basic',body,'',1,fail);
 mainDetail(unchanged('beforeNoneConflict'));
 add('A','明确重置声明为未知','PUT','/resume/{{rsMain}}/basic',{title:'推荐输入回归',careerStage:'UNKNOWN',formalExperienceStatus:'UNKNOWN'});
 add('A','添加正式经历并自动确认有经历','POST','/resume/{{rsMain}}/experience',formal);
 add('A','读取正式经历ID','GET','/resume/{{rsMain}}/experience',undefined,"const e=b.data.find(x=>x.experienceType==='FORMAL');pm.test('正式经历存在',()=>pm.expect(e).an('object'));pm.collectionVariables.set('rsFormal',e.id);");
 mainDetail(test('正式记录更新声明',"pm.expect(b.data.resume.formalExperienceStatus).eq('HAS_EXPERIENCE');"));
 add('A','旧经历表单保留新字段和未传日期','PUT','/resume/experience/{{rsFormal}}',{companyName:'回归企业',position:'后端开发',description:'旧表单更新'});
 add('A','读取旧表单更新后的经历','GET','/resume/{{rsMain}}/experience',undefined,test('类型日期false仍在',"const e=b.data.find(x=>x.id==pm.collectionVariables.get('rsFormal'));pm.expect(e.experienceType).eq('FORMAL');pm.expect(e.isCurrent).eq(false);pm.expect(e.startDate).eq('2026-01-01');pm.expect(e.endDate).eq('2026-06-30');"));
 mainDetail(remember('beforeDateErrors'));
 for(const body of [{...formal,isCurrent:true},{...formal,isCurrent:null},{...formal,startDate:null},{...formal,startDate:'2099-01-01',endDate:'2099-02-01'},{...formal,endDate:'2025-01-01'},{...formal,endDate:'2099-01-01'},{...formal,experienceType:null},{...formal,experienceType:'BAD'},{...formal,endDate:null}])add('A','拒绝类型和日期非法组合','PUT','/resume/experience/{{rsFormal}}',body,'',1,fail);
 mainDetail(unchanged('beforeDateErrors'));
 add('A','改为至今须显式清空结束日期','PUT','/resume/experience/{{rsFormal}}',{...formal,isCurrent:true,endDate:null});
 add('A','至今与空结束日期重读','GET','/resume/{{rsMain}}/experience',undefined,test('至今字段',"const e=b.data.find(x=>x.id==pm.collectionVariables.get('rsFormal'));pm.expect(e.isCurrent).eq(true);pm.expect(e.endDate).eq(null);"));
 add('A','将最后正式经历改为实习','PUT','/resume/experience/{{rsFormal}}',internship);
 mainDetail(test('失去最后正式记录后为未知',"pm.expect(b.data.resume.formalExperienceStatus).eq('UNKNOWN');"));
 add('A','只有实习可再次确认NONE','PUT','/resume/{{rsMain}}/basic',{title:'推荐输入回归',formalExperienceStatus:'NONE'});
 add('A','已有实习不能改为正式以绕过声明','PUT','/resume/experience/{{rsIntern}}',formal,'',1,fail);
 add('A','新增自由文本技能及程度','POST','/resume/{{rsMain}}/skill',{skillName:'熟悉Java',description:'能按规范编写过程文件',proficiency:'FAMILIAR'});
 add('A','读取技能原文与程度','GET','/resume/{{rsMain}}/skill',undefined,test('原文和程度',"pm.expect(b.data).lengthOf(1);pm.expect(b.data[0].skillName).eq('熟悉Java');pm.expect(b.data[0].description).eq('能按规范编写过程文件');pm.expect(b.data[0].proficiency).eq('FAMILIAR');")+"pm.collectionVariables.set('rsSkill',b.data[0].id);");
 add('A','旧技能表单保留程度','PUT','/resume/skill/{{rsSkill}}',{skillName:'熟悉Java',description:'旧表单更新'});
 add('A','读取保留程度','GET','/resume/{{rsMain}}/skill',undefined,test('仍熟悉',"pm.expect(b.data[0].proficiency).eq('FAMILIAR');"));
 add('A','显式清空程度','PUT','/resume/skill/{{rsSkill}}',{skillName:'熟悉Java',proficiency:null});
 add('A','读取已清空程度','GET','/resume/{{rsMain}}/skill',undefined,test('程度null',"pm.expect(b.data[0].proficiency).eq(null);"));
 mainDetail(remember('beforeSkillErrors'));
 for(const body of [{skillName:'不会Java',proficiency:'EXPERT'},{skillName:'Java',proficiency:'BAD'},{skillName:'Java',proficiency:0},{skillName:'x'.repeat(51)},{skillName:'Java',description:'x'.repeat(501)},{skillName:'Java',resumeId:910}])add('A','拒绝技能矛盾或越界参数','POST','/resume/{{rsMain}}/skill',body,'',1,fail);
 mainDetail(unchanged('beforeSkillErrors'));
 add('A','通用能力可以保存','POST','/resume/{{rsMain}}/skill',{skillName:'跨部门沟通',description:'协调产品和研发推进需求'});
 add('A','兼容前端id字段的求职意向保存','POST','/resume/{{rsMain}}/preference',{id:null,expectedPosition:'后端开发',expectedCity:'上海',salaryMin:10000,salaryMax:20000,directionCode:'BACKEND'});
 add('A','意向重读并取得路径ID','GET','/resume/{{rsMain}}/preference',undefined,test('方向保存',"pm.expect(b.data.directionCode).eq('BACKEND');")+"pm.collectionVariables.set('rsPref',b.data.id);");
 add('A','旧意向表单同职位保留方向且忽略体内id','PUT','/resume/preference/{{rsPref}}',{id:999999,expectedPosition:'后端开发',expectedCity:'上海',salaryMin:10000,salaryMax:20000});
 add('A','验证方向保留且目标未变','GET','/resume/{{rsMain}}/preference',undefined,test('正确目标和方向',"pm.expect(b.data.id).eq(Number(pm.collectionVariables.get('rsPref')));pm.expect(b.data.directionCode).eq('BACKEND');"));
 add('A','旧客户端改变职位清除过期方向','PUT','/resume/preference/{{rsPref}}',{expectedPosition:'研发工程师'});
 add('A','宽泛职位没有被猜成后端','GET','/resume/{{rsMain}}/preference',undefined,test('未知方向',"pm.expect(b.data.directionCode).eq(null);"));
 add('A','宽泛职位允许明确选择方向','PUT','/resume/preference/{{rsPref}}',{expectedPosition:'研发工程师',directionCode:'BACKEND'});
 add('A','明确清空意向方向','PUT','/resume/preference/{{rsPref}}',{expectedPosition:'研发工程师',directionCode:null});
 add('A','方向清空并保存意向快照','GET','/resume/{{rsMain}}/preference',undefined,test('方向null',"pm.expect(b.data.directionCode).eq(null);")+remember('prefBeforeErrors'));
 for(const body of [{expectedPosition:'前端开发',directionCode:'BACKEND'},{expectedPosition:'研发工程师',salaryMin:20000,salaryMax:10000},{expectedPosition:'研发工程师',directionCode:'BAD'},{expectedPosition:'研发工程师',resumeId:910}])add('A','拒绝意向矛盾或非法归属','PUT','/resume/preference/{{rsPref}}',body,'',1,fail);
 add('A','意向失败后重读未变','GET','/resume/{{rsMain}}/preference',undefined,unchanged('prefBeforeErrors'));
 add('A','拒绝重复意向','POST','/resume/{{rsMain}}/preference',{expectedPosition:'后端开发'},'',1,fail);
 for(const who of [2,3,4]){
  add('A','他人或角色不能修改基础资料','PUT','/resume/{{rsMain}}/basic',{title:'越权修改',formalExperienceStatus:'UNKNOWN'},'',who,fail);
  add('A','他人或角色不能添加经历','POST','/resume/{{rsMain}}/experience',internship,'',who,fail);
  add('A','他人或角色不能修改技能','PUT','/resume/skill/{{rsSkill}}',{skillName:'越权'},'',who,fail);
  add('A','他人或角色不能修改意向','PUT','/resume/preference/{{rsPref}}',{expectedPosition:'越权'},'',who,fail);
  add('A','他人或角色不能读完整详情','GET','/resume/{{rsMain}}/detail',undefined,'',who,fail);
 }
 add('A','未登录不能写简历','PUT','/resume/{{rsMain}}/basic',{title:'越权'},'',null,unauthorized);
 add('A','不存在经历拒绝','PUT','/resume/experience/999999',{companyName:'无',position:'无'},'',1,fail);
 add('A','不存在技能拒绝','DELETE','/resume/skill/999999',undefined,'',1,fail);
 add('A','不存在意向拒绝','DELETE','/resume/preference/999999',undefined,'',1,fail);
 add('A','旧请求创建简历','POST','/resume',{title:'旧表单简历'},"pm.collectionVariables.set('rsLegacy',b.data);");
 add('A','旧创建默认未知','GET','/resume/{{rsLegacy}}',undefined,test('未知而非零经验',"pm.expect(b.data.careerStage).eq('UNKNOWN');pm.expect(b.data.formalExperienceStatus).eq('UNKNOWN');"));
 add('A','旧经历允许不完整信息','POST','/resume/{{rsLegacy}}/experience',{companyName:'旧企业',position:'旧职位',periodText:'至今'});
 add('A','旧文本至今不推断boolean','GET','/resume/{{rsLegacy}}/experience',undefined,test('未知保留',"pm.expect(b.data[0].experienceType).eq('UNKNOWN');pm.expect(b.data[0].isCurrent).eq(null);")+"pm.collectionVariables.set('rsLegacyExp',b.data[0].id);");
 add('A','旧经历只更新描述','PUT','/resume/experience/{{rsLegacyExp}}',{companyName:'旧企业',position:'旧职位',description:'丰富说明'});
 add('A','未分类记录阻止NONE声明','PUT','/resume/{{rsLegacy}}/basic',{title:'旧表单简历',formalExperienceStatus:'NONE'},'',1,fail);
 add('A','确认旧经历为实习并补全日期','PUT','/resume/experience/{{rsLegacyExp}}',internship);
 add('A','仅实习可以确认NONE','PUT','/resume/{{rsLegacy}}/basic',{title:'旧表单简历',formalExperienceStatus:'NONE'});
 add('A','声明HAS但未补全记录','PUT','/resume/{{rsLegacy}}/basic',{title:'旧表单简历',formalExperienceStatus:'HAS_EXPERIENCE'});
 add('A','新增实习不清掉已有HAS申报','POST','/resume/{{rsLegacy}}/experience',internship);
 add('A','确认HAS申报仍在','GET','/resume/{{rsLegacy}}',undefined,test('申报有但未填正式记录',"pm.expect(b.data.formalExperienceStatus).eq('HAS_EXPERIENCE');"));
 add('A','删除意向','DELETE','/resume/preference/{{rsPref}}');
 add('A','意向为空仍返回成功null','GET','/resume/{{rsMain}}/preference',undefined,test('无意向',"pm.expect(b.data).eq(null);"));
 add('A','删除技能','DELETE','/resume/skill/{{rsSkill}}');
 add('A','删除简历清理子表','DELETE','/resume/{{rsLegacy}}');
 add('A','删除后不能读取','GET','/resume/{{rsLegacy}}',undefined,'',1,fail);
 add('A','简历列表原分页结构兼容','GET','/resume?pageNum=1&pageSize=10',undefined,test('分页items',"pm.expect(b.data.items).an('array');pm.expect(b.data.total).a('number');"));
 login('B',1);
 add('B','保存故障前详情','GET','/resume/903/detail',undefined,remember('faultBefore'));
 add('B','完成度失败导致新增技能回滚','POST','/resume/903/skill',{skillName:'Java'},test('真实DB故障非业务成功',"pm.expect(b.message).include('系统异常');"),1,fail);
 add('B','技能失败后全部详情不变','GET','/resume/903/detail',undefined,unchanged('faultBefore'));
 add('B','完成度失败导致正式经历及声明一起回滚','POST','/resume/903/experience',formal,'',1,fail);
 add('B','经历和HAS更新都回滚','GET','/resume/903/detail',undefined,unchanged('faultBefore'));
 login('C',1);
 add('C','解除故障后技能可保存','POST','/resume/903/skill',{skillName:'Java'});
 add('C','解除故障后经历可保存','POST','/resume/903/experience',formal);
 add('C','恢复后重读声明与完成度','GET','/resume/903/detail',undefined,test('恢复数据完整',"pm.expect(b.data.resume.formalExperienceStatus).eq('HAS_EXPERIENCE');pm.expect(b.data.resume.completionRate).eq(20);pm.expect(b.data.skills).lengthOf(1);pm.expect(b.data.experiences).lengthOf(1);"));
 login('D',1);
 const race="let done=0;const results=[];const send=(method,url,body,index)=>setTimeout(()=>{const started=Date.now();pm.sendRequest({url:pm.environment.get('rsHost')+url,method,header:{Authorization:'Bearer '+pm.collectionVariables.get('rsToken1'),'Content-Type':'application/json'},...(body?{body:{mode:'raw',raw:JSON.stringify(body)}}:{})},(err,res)=>{pm.test('并发请求可达',()=>pm.expect(err).eq(null));if(!err)results.push({code:res.json().code,started,ended:Date.now()});if(++done===2){pm.test('一次成功一次拒绝',()=>pm.expect(results.map(x=>x.code).sort()).deep.eq([200,500]));pm.test('请求实际重叠',()=>pm.expect(Math.max(...results.map(x=>x.started))).below(Math.min(...results.map(x=>x.ended))));console.log('RS_RACE='+JSON.stringify(results));}});},index*100);";
 add('D','正式经历与NONE并发竞争','GET','/resume/902/detail',undefined,race+"send('POST','/resume/902/experience',"+JSON.stringify(formal)+",0);send('PUT','/resume/902/basic',{title:'并发简历',formalExperienceStatus:'NONE'},1);");
 add('D','并发后声明与正式经历一致','GET','/resume/902/detail',undefined,test('FORMAL赢得父锁后NONE拒绝',"pm.expect(b.data.resume.formalExperienceStatus).eq('HAS_EXPERIENCE');pm.expect(b.data.experiences).lengthOf(1);pm.expect(b.data.experiences[0].experienceType).eq('FORMAL');")+"pm.collectionVariables.set('rsRaceExp',b.data.experiences[0].id);");
 add('D','同一经历并发删除','GET','/resume/902/detail',undefined,race+"const url='/resume/experience/'+pm.collectionVariables.get('rsRaceExp');send('DELETE',url,null,0);send('DELETE',url,null,1);");
 add('D','删除后未知而非NONE且没有残留','GET','/resume/902/detail',undefined,test('最后正式经历已删',"pm.expect(b.data.experiences).deep.eq([]);pm.expect(b.data.resume.formalExperienceStatus).eq('UNKNOWN');"));
}
function fixture(mode,s,password){const r=spawnSync('D:/jdk17/bin/java.exe',['-cp','target;'+cp(),'ResumeInputFixture',mode,s.database,s.root],{env:{...process.env,...(password?{RESUME_INPUT_FIXTURE_PASSWORD:password}:{})},encoding:'utf8',windowsHide:true});if(r.status!==0)throw Error(r.stderr||r.stdout);console.log(r.stdout.trim()||('Fixture '+mode+' complete'));}
const redact=(value,password)=>String(value).replaceAll(password||'___','[REDACTED]').replace(/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g,'[REDACTED_JWT]');
(async()=>{
 const mode=process.argv[2];
 if(mode==='setup-local'){
  assert(!fs.existsSync(STATE),'Existing state; do not overwrite');const stamp=Date.now(),password='RsIn_'+crypto.randomBytes(8).toString('hex');
  const s={database:'smart_recruit_resume_inputs_'+stamp,root:path.resolve('target/resume-input-fixture-'+stamp).replaceAll('\\','/'),port:18089,runners:{},folders:{}};
  if(fs.existsSync('docs/postman/resume-input-regression.manifest.json')){
   const m=JSON.parse(fs.readFileSync('docs/postman/resume-input-regression.manifest.json'));assert.equal(m.mainCollectionId,MAIN);
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
  assert(Number.isInteger(s.serverPid)&&s.serverPid>0);assert(/^smart_recruit_resume_inputs_\d+$/.test(s.database));
  const script='$p=Get-CimInstance Win32_Process -Filter "ProcessId = '+s.serverPid+'"; if($null -eq $p){exit 0}; if($p.Name -ne "java.exe" -or -not $p.CommandLine.Contains("'+s.database+'") -or -not $p.CommandLine.Contains("--server.port=18089")){throw "Refuse unrelated process"}; Stop-Process -Id '+s.serverPid+' -Force -Confirm:$false -ErrorAction Stop';
  const r=spawnSync('powershell.exe',['-NoProfile','-NonInteractive','-Command',script],{windowsHide:true,encoding:'utf8'});if(r.status!==0)throw Error(r.stderr||'Stop failed');s.stoppedPid=s.serverPid;delete s.serverPid;save();console.log('Owned isolated server stopped.');return;
 }
 if(['baseline','fault-on','fault-off','verify-rollback','delay-on','delay-off','audit'].includes(mode)){fixture(mode,s);return;}
 if(mode==='start'){
  assert(!s.serverPid);const configured=fs.readFileSync('src/main/resources/application.yml','utf8').match(/url:\s*['"]?(jdbc:mysql:[^\s'"]+)/)?.[1];assert(configured?.includes('/smart_recruit?'));
  const out=fs.openSync(s.root+'/server.log','a'),err=fs.openSync(s.root+'/server-error.log','a');
  const p=spawn('D:/jdk17/bin/java.exe',['-cp',cp(),'com.recruit.smartrecruit.SmartRecruitApplication','--server.port=18089','--spring.datasource.url='+configured.replace('/smart_recruit?','/'+s.database+'?'),'--smart-recruit.company-files.root='+s.root+'/storage','--spring.main.banner-mode=off'],{windowsHide:true,detached:true,stdio:['ignore',out,err]});s.serverPid=p.pid;save();p.unref();console.log('Isolated server PID='+p.pid+' port=18089');return;
 }
 const c=await connect();let password;
 try{
  if(mode==='setup-environment'){
   assert(!s.environmentId);password=JSON.parse(fs.readFileSync(s.root+'/credentials.json')).password;
   const r=data(await c.callTool({name:'createEnvironment',arguments:{workspace:WORKSPACE,environment:{name:'Smart Recruit Resume Inputs Isolated Regression',values:[{key:'rsHost',value:HOST,type:'default',enabled:true},{key:'rsPassword',value:password,type:'secret',enabled:true},{key:'runResumeInputRegression',value:'true',type:'default',enabled:true},{key:'rsFixtureReady',value:'true',type:'default',enabled:true}]}}}));s.environmentId=getId(r);assert(s.environmentId);save();console.log('Postman isolated environment saved: '+s.environmentId);return;
  }
  if(mode==='guard-off'){
   const env=data(await c.callTool({name:'getEnvironment',arguments:{environmentId:s.environmentId}})).environment;
   for(const key of ['runResumeInputRegression','rsFixtureReady']){const index=env.values.findIndex(x=>x.key===key);assert(index>=0);data(await c.callTool({name:'patchEnvironment',arguments:{environmentId:s.environmentId,body:[{op:'replace',path:'/values/'+index+'/value',value:'false'}]}}));}console.log('Regression execution guards disabled.');return;
  }
  build();
  if(mode==='prepare'){
   const col=data(await c.callTool({name:'getCollection',arguments:{collectionId:MAIN}})).collection;assert.equal(col.info.name,'SmartRecruit API - Optimized Regression');const parent=col.itemRefs.find(x=>x.name==='04 Resume');assert(parent);
   if(!s.folderId){s.folderId=getId(data(await c.callTool({name:'createCollectionFolder',arguments:{collectionId:MAIN,folder:bare(parent.id),name:'推荐输入接入回归 2026-11-01'}})));assert(s.folderId);save();}
   for(const stage of ['A','B','C','D']){
    const cases=items.filter(x=>x.stage===stage).map(({stage,...x})=>x);
    if(!s.runners[stage]){s.runners[stage]=getId(data(await c.callTool({name:'createCollection',arguments:{workspace:WORKSPACE,collection:{info:{name:'Smart Recruit Resume Inputs '+stage+' Execution',schema:'https://schema.getpostman.com/json/collection/v2.1.0/collection.json'},item:cases}}})));assert(s.runners[stage]);save();}
    const full=data(await c.callTool({name:'getCollection',arguments:{collectionId:s.runners[stage],model:'full'}})).collection;assert.equal(full.item.length,cases.length);
    if(!s.folders[stage]){const folderId=getId(data(await c.callTool({name:'createCollectionFolder',arguments:{collectionId:MAIN,folder:bare(s.folderId),name:stage+' '+({A:'保存校验权限兼容',B:'完成度故障回滚',C:'故障解除恢复',D:'父锁并发一致性'}[stage])}})));assert(folderId);data(await c.callTool({name:'transferCollectionRequests',arguments:{ids:full.item.map(x=>uid(x.id)),mode:'copy',target:{id:uid(folderId),model:'folder'},location:{position:'end'}}}));s.folders[stage]=folderId;save();}
    console.log('Persisted '+stage+' requests='+cases.length);
   }
   fs.writeFileSync('docs/postman/resume-input-regression.postman_collection.json',JSON.stringify({info:{name:'推荐输入接入持久回归',schema:'https://schema.getpostman.com/json/collection/v2.1.0/collection.json',description:'所有用例已加入SmartRecruit API - Optimized Regression / 04 Resume。仅隔离端口18089；B前开启故障、B后解除；D前开启延迟、D后清理。'},item:['A','B','C','D'].map(stage=>({name:stage,item:items.filter(x=>x.stage===stage).map(({stage,...x})=>x)}))},null,2));
   fs.writeFileSync('docs/postman/resume-input-regression.manifest.json',JSON.stringify({mainCollectionId:MAIN,folderId:s.folderId,runners:s.runners,folders:s.folders,requestsByStage:Object.fromEntries(['A','B','C','D'].map(stage=>[stage,items.filter(x=>x.stage===stage).length]))},null,2));return;
  }
  if(mode==='verify'){
   const col=data(await c.callTool({name:'getCollection',arguments:{collectionId:MAIN,model:'full'}})).collection;const root=col.item.find(x=>x.name==='04 Resume').item.find(x=>bare(x.id)===bare(s.folderId));const actual=root.item.flatMap(x=>x.item);assert.equal(actual.length,items.length);
   for(const item of items){const found=actual.find(x=>x.name===item.name||x.name===item.name+' Copy');assert(found,item.name);assert.deepStrictEqual(found.event,item.event);assert.equal(found.request.method,item.request.method);assert.equal(typeof found.request.url==='string'?found.request.url:found.request.url.raw,item.request.url);assert.equal(found.request.body?.raw,item.request.body?.raw);}console.log('Verified '+actual.length+' exact persistent requests in required collection');return;
  }
  assert(['A','B','C','D'].includes(mode));const env=data(await c.callTool({name:'getEnvironment',arguments:{environmentId:s.environmentId}})).environment;password=env.values.find(x=>x.key==='rsPassword')?.value;
  const result=await c.callTool({name:'runCollection',arguments:{collectionId:s.runners[mode],environmentId:s.environmentId,requestTimeout:30000,scriptTimeout:20000,stopOnError:true,stopOnFailure:true}},undefined,{timeout:300000});
  const output=result.content.filter(x=>x.type==='text').map(x=>x.text).join('\n');fs.writeFileSync(s.root+'/result-'+mode+'.json',redact(JSON.stringify(result),password));
  const tail=output.lastIndexOf('=== ');console.log(redact(tail>=0?output.slice(tail):output.slice(-5000),password));
  for(const line of output.match(/^.*RS_RACE=.*$/gm)||[])console.log(redact(line,password));
  if(result.isError||!Number(output.match(/Total assertions:\s*(\d+)/)?.[1])||/Failed assertions:\s*[1-9]|Failed requests:\s*[1-9]/.test(output))process.exitCode=1;
 }finally{await c.close();}
})().catch(e=>{console.error(redact(e.message,process.env.POSTMAN_API_KEY));process.exitCode=1;});
