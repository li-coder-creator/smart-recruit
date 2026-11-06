const fs=require('fs'),path=require('path'),assert=require('assert'),crypto=require('crypto'),{spawn,spawnSync}=require('child_process');
const {connect,data}=require('./postman-mcp.cjs');
const MAIN='56227183-00855bc5-e68c-4aab-bdc2-faffe0f70f82',WORKSPACE='baec8bed-057c-431c-832a-21a7d3b2a685';
const STATE=process.env.RECOMMENDATION_QUERY_STATE_FILE||'target/recommendation-query-state.json',HOST='http://127.0.0.1:18091';
assert(path.resolve(STATE).startsWith(path.resolve('target')+path.sep));
const bare=x=>x.replace(/^56227183-/,''),uid=x=>x.startsWith('56227183-')?x:'56227183-'+x;
const cp=()=> 'target/recommendation-build/target/classes;'+fs.readFileSync('target/files-classpath.txt','utf8').trim();
const getId=r=>r.collection?.uid||r.environment?.uid||r.collection?.id||r.environment?.id||r.data?.id||r.id;
const guard="if(pm.environment.get('runRecommendationQueryRegression')!=='true'||pm.environment.get('rqFixtureReady')!=='true'||pm.environment.get('rqHost')!=='"+HOST+"'){pm.execution.skipRequest();}";
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
function login(stage,who){
 add(stage,'登录回归身份'+who,'POST','/user/login',{username:'rq_input_'+who,password:'{{rqPassword}}'},"pm.collectionVariables.set('rqToken"+who+"',b.data);",null);
 add(stage,'核对身份'+who,'GET','/user/info',undefined,test('身份角色',"pm.expect(b.data.id).eq("+(100+who)+");pm.expect(b.data.role).eq('"+(who<=2?'JOB_SEEKER':who===4?'ADMIN':'COMPANY')+"');"),who);
}
const route='/job/recommendations';
function query(stage,name,overrides={},checks='',who=1,expected=ok){add(stage,name,'POST',route,{resumeId:901,size:100,...overrides},checks,who,expected);}
const dimension=(id,name)=>"b.data.items.find(x=>x.jobId==="+id+").dimensions.find(x=>x.dimension==='"+name+"')";
function build(){
 login('A',1);
 add('A','获取推荐选项与默认规则','GET',route+'/options',undefined,test('选项不含演示默认偏好',"pm.expect(b.data.defaultExcludeApplied).eq(true);pm.expect(b.data.defaultExcludeExpired).eq(true);pm.expect(b.data.defaultStrictCity).eq(false);pm.expect(b.data.semanticEnabled).eq(false);pm.expect(b.data.salaryUnit).eq('元/月');pm.expect(b.data.directions.map(x=>x.code)).deep.eq(['BACKEND','FRONTEND']);pm.expect(b.data.presets).lengthOf(3);pm.expect(b.data.presets[0].weights.SKILL).eq(35);pm.expect(b.data.cities.some(x=>x.code==='XIAN')).eq(true);pm.expect(b.data.versions.embeddingModelVersion).eq(null);"));
 add('A','获取所选简历默认上下文','GET',route+'/context?resumeId=901',undefined,test('来自真实所选简历',"pm.expect(b.data.resumeId).eq(901);pm.expect(b.data.preferences.directionCode).eq('BACKEND');pm.expect(b.data.preferences.cityCode).eq('SHANGHAI');pm.expect(b.data.preferences.salaryMax).eq(20000);pm.expect(b.data.actualFormalMonths).eq(0);pm.expect(b.data.highestEducation).eq('BACHELOR');pm.expect(b.data.skills).lengthOf(3);pm.expect(b.data.asOfDate).eq(pm.environment.get('rqAsOfDate'));pm.expect(b.data).not.have.property('phone');pm.expect(b.data).not.have.property('email');")+remember('rqDefaultContext'));
 add('A','默认分页无需演示偏好','POST',route,{resumeId:901},test('默认分页和总数',"pm.expect(b.data.page).eq(1);pm.expect(b.data.size).eq(10);pm.expect(b.data.items).lengthOf(10);pm.expect(b.data.total).eq(19);pm.expect(b.data.excludeApplied).eq(true);pm.expect(b.data.excludeExpired).eq(true);pm.expect(b.data.items[0].jobId).eq(1999);"));
 query('A','全局推荐资格过滤与可解释分数',{},test('全局结果与手算',"pm.expect(b.data.total).eq(19);pm.expect(b.data.items).lengthOf(19);const ids=b.data.items.map(x=>x.jobId);for(const id of [1009,1011,1012,1013,1014,1015,1016,1017,1018,1019,1021])pm.expect(ids).not.include(id);pm.expect(b.data.items[0].jobId).eq(1999);pm.expect(b.data.items.find(x=>x.jobId===1002).totalScore).eq(70.5);pm.expect(b.data.items.find(x=>x.jobId===1003).totalScore).eq(69);pm.expect(b.data.items.find(x=>x.jobId===1004).totalScore).eq(51.75);pm.expect(b.data.items.find(x=>x.jobId===1003).excludedWeight).eq(25);pm.expect(b.data.items.find(x=>x.jobId===1004).dataCoverage).eq(75);pm.expect(b.data.items.every(x=>x.applied===false&&x.expired===false)).eq(true);pm.expect(b.data.items.every(x=>x.companyId===201)).eq(true);pm.expect(b.data.items.find(x=>x.jobId===1002).applied).eq(false);pm.expect(b.data.items.at(-1).totalScore).eq(null);pm.expect(b.data.items[0]).not.have.property('unroundedScore');")+"pm.collectionVariables.set('rqFullIds',JSON.stringify(b.data.items.map(x=>x.jobId)));"+test('来源版本与逐项证据',"const x=b.data.items.find(x=>x.jobId===1022);pm.expect(x.requiredAbilitiesSource).eq('STRUCTURED');pm.expect(x.requiredAbilitiesVersion).eq(7);pm.expect(x.abilityMatches).lengthOf(2);pm.expect(x.abilityMatches[0].requirements).lengthOf(2);pm.expect(x.abilityMatches[0].candidateEvidence.rawText).eq('Java');pm.expect(x.totalScore).eq(82.5);"));
 query('A','三种状态与空能力不可回退',{},test('UNKNOWN和不可比较',"pm.expect("+dimension(1002,'EDUCATION')+".score).eq(0);pm.expect("+dimension(1002,'EDUCATION')+".meetsRequirement).eq(false);pm.expect("+dimension(1003,'SALARY')+".state).eq('NOT_APPLICABLE');pm.expect("+dimension(1004,'SALARY')+".state).eq('UNKNOWN');pm.expect("+dimension(1004,'SALARY')+".effectiveWeightRatio).eq(0.25);for(const id of [1005,1006,1007]){const x=b.data.items.find(x=>x.jobId===id);pm.expect(x.totalScore).eq(null);pm.expect(x.scoreReason).eq('CORE_SKILLS_UNKNOWN');}pm.expect(b.data.items.find(x=>x.jobId===1007).abilityMatches[0].reason).eq('SEMANTIC_NOT_CONFIGURED');pm.expect(b.data.items.find(x=>x.jobId===1006).abilityMatches).deep.eq([]);"));
 query('A','默认排除截止但截止当天保留',{},test('边界日',"const today=b.data.items.find(x=>x.jobId===1010);pm.expect(today.deadline).eq(pm.environment.get('rqAsOfDate'));pm.expect(today.expired).eq(false);"));
 query('A','关闭已投递排除按用户而非简历判断',{excludeApplied:false},test('已拒绝及其它简历投递仍标记',"pm.expect(b.data.total).eq(20);pm.expect(b.data.items.find(x=>x.jobId===1011).applied).eq(true);pm.expect(b.data.items.some(x=>x.jobId===1012)).eq(false);"));
 query('A','关闭过期排除但继续排除已投递',{excludeExpired:false},test('开关独立',"pm.expect(b.data.total).eq(20);pm.expect(b.data.items.find(x=>x.jobId===1009).expired).eq(true);pm.expect(b.data.items.some(x=>x.jobId===1012)).eq(false);"));
 query('A','同时关闭两个排除开关',{excludeApplied:false,excludeExpired:false},test('全部可选历史',"pm.expect(b.data.total).eq(22);const x=b.data.items.find(x=>x.jobId===1012);pm.expect(x.applied).eq(true);pm.expect(x.expired).eq(true);for(const id of [1013,1014,1015,1016,1017])pm.expect(b.data.items.map(x=>x.jobId)).not.include(id);"));
 query('A','严格城市识别英文与中文别名',{strictCity:true,preferences:{city:'上海市'}},test('同城结果',"pm.expect(b.data.total).eq(18);pm.expect(b.data.items.every(x=>x.city==='上海')).eq(true);"));
 query('A','期望深圳只保留深圳岗位',{strictCity:true,preferences:{city:'Shenzhen'}},test('同城100',"pm.expect(b.data.total).eq(1);pm.expect(b.data.items[0].jobId).eq(1008);pm.expect(b.data.items[0].totalScore).eq(100);"));
 query('A','城市代码来自options可直接回传',{strictCity:true,preferences:{city:'XIAN'}},test('合法无结果',"pm.expect(b.data.total).eq(0);pm.expect(b.data.items).deep.eq([]);pm.expect(b.data.appliedPreferences.cityCode).eq('XIAN');"));
 query('A','明确覆盖方向优先于默认后端文本',{preferences:{directionCode:'FRONTEND'}},test('前端方向',"pm.expect(b.data.total).eq(1);pm.expect(b.data.items[0].jobId).eq(1018);pm.expect(b.data.appliedPreferences.directionCode).eq('FRONTEND');"));
 query('A','只按月薪上限排序',{sort:'SALARY_DESC'},test('非月薪与面议不混排金额',"pm.expect(b.data.items[0].jobId).eq(1028);pm.expect(b.data.items[1].jobId).eq(1027);const ids=b.data.items.map(x=>x.jobId);pm.expect(ids.indexOf(1023)).greaterThan(ids.indexOf(1001));pm.expect(ids.indexOf(1024)).greaterThan(ids.indexOf(1001));"));
 query('A','全局排序后第一页',{page:1,size:1},test('全局第一',"pm.expect(b.data.total).eq(19);pm.expect(b.data.items.map(x=>x.jobId)).deep.eq(JSON.parse(pm.collectionVariables.get('rqFullIds')).slice(0,1));"));
 query('A','全局排序后第二页',{page:2,size:3},test('与全局切片一致',"pm.expect(b.data.items.map(x=>x.jobId)).deep.eq(JSON.parse(pm.collectionVariables.get('rqFullIds')).slice(3,6));"));
 query('A','页码超过总数仍成功空页',{page:2147483647,size:100},test('无整数溢出',"pm.expect(b.data.total).eq(19);pm.expect(b.data.items).deep.eq([]);"));
 query('A','薪资优先预设',{preset:'SALARY_FIRST'},test('请求权重',"pm.expect(b.data.requestedWeights.SALARY).eq(70);"));
 query('A','同城优先预设',{preset:'CITY_FIRST'},test('请求权重',"pm.expect(b.data.requestedWeights.CITY).eq(60);"));
 query('A','自定义相对权重无需凑成100',{weights:{skill:1,salary:1,city:1,experience:1,education:1}},test('归一化',"pm.expect(b.data.items.find(x=>x.jobId===1002).totalScore).eq(61);"));
 query('A','显式忽略技能仍保留技能未知事实',{weights:{skill:0,salary:100,city:0,experience:0,education:0}},test('核心技能忽略',"const x=b.data.items.find(x=>x.jobId===1007);pm.expect(x.totalScore).eq(100);pm.expect(x.dimensions.find(x=>x.dimension==='SKILL').state).eq('UNKNOWN');pm.expect(x.dimensions.find(x=>x.dimension==='SKILL').ignored).eq(true);const day=b.data.items.find(x=>x.jobId===1003);pm.expect(day.totalScore).eq(null);pm.expect(day.scoreReason).eq('NO_COMPARABLE_DIMENSIONS');"));
 query('A','仅实习且确认NONE正式工龄0',{resumeId:904},test('经验55',"pm.expect("+dimension(1002,'EXPERIENCE')+".score).eq(55);"));
 query('A','正式经历合并18个月及在读硕士',{resumeId:905},test('经验学历实证',"pm.expect("+dimension(1029,'EXPERIENCE')+".score).eq(32.5);pm.expect("+dimension(1029,'EDUCATION')+".score).eq(100);pm.expect(b.data.items.find(x=>x.jobId===1029).totalScore).eq(93.25);"));
 add('A','没有默认偏好上下文不填演示值','GET',route+'/context?resumeId=903',undefined,test('缺失保持缺失',"pm.expect(b.data.directionRequired).eq(true);pm.expect(b.data.preferences.directionCode).eq(null);pm.expect(b.data.preferences.city).eq(null);pm.expect(b.data.preferences.salaryMax).eq(null);pm.expect(b.data.actualFormalMonths).eq(null);pm.expect(b.data.highestEducation).eq(null);"));
 query('A','空简历选择明确方向后允许返回未知评分',{resumeId:903,preferences:{directionCode:'BACKEND'}},test('不冒充零能力或满分',"pm.expect(b.data.total).eq(19);pm.expect(b.data.items.every(x=>x.totalScore===null)).eq(true);"));
 query('A','旧意向薪资矛盾进入UNKNOWN而不污染总请求',{resumeId:906},test('不误排除分母',"pm.expect("+dimension(1002,'SALARY')+".state).eq('UNKNOWN');pm.expect(b.data.items.find(x=>x.jobId===1002).totalScore).eq(51.75);"));
 login('B',1);
 const invalid=[{}, {resumeId:null},{resumeId:0},{resumeId:-1},{resumeId:'901'},{resumeId:901.5},{page:0},{page:null},{page:1.5},{page:'1'},{size:0},{size:101},{size:2147483648},{sort:'NEWEST'},{sort:0},{sort:null},{preset:'BAD'},{preset:0},{strictCity:null},{strictCity:'false'},{excludeApplied:null},{excludeApplied:1},{excludeExpired:null},{excludeExpired:'true'},
 {weights:{skill:0,salary:0,city:0,experience:0,education:0}},{weights:{skill:35}},{weights:{skill:-1,salary:1,city:1,experience:1,education:1}},{weights:{skill:101,salary:0,city:0,experience:0,education:0}},{weights:{skill:35.5,salary:25,city:20,experience:10,education:10}},{weights:{skill:'35',salary:25,city:20,experience:10,education:10}},{weights:{skill:35,salary:25,city:20,experience:10,education:10,extra:1}},{preset:'BALANCED',weights:{skill:1,salary:1,city:1,experience:1,education:1}},
 {preferences:{directionCode:'OTHER'}},{preferences:{directionCode:0}},{preferences:{directionCode:null}},{preferences:{salaryMax:0}},{preferences:{salaryMax:-1}},{preferences:{salaryMax:'20000'}},{preferences:{salaryMax:1.5}},{preferences:{salaryMin:30000,salaryMax:20000}},{preferences:{city:'x'.repeat(51)}},{strictCity:true,preferences:{city:null}},{strictCity:true,preferences:{city:'华东'}},{preferences:{id:1}},{userId:102},{status:0},{asOfDate:'2020-01-01'},{resumeId:903}];
 invalid.forEach((body,i)=>add('B','拒绝非法请求 '+i,'POST',route,i===0?{}:{resumeId:901,...body},'',1,fail));
 for(const suffix of ['', '?resumeId=0','?resumeId=-1','?resumeId=abc'])add('B','拒绝非法上下文ID','GET',route+'/context'+suffix,undefined,'',1,fail);
 query('B','不存在简历不泄露其它资料',{resumeId:999999},'',1,fail);
 login('C',1);login('C',2);login('C',3);login('C',4);
 query('C','他人简历不能作为推荐依据',{resumeId:902},'',1,fail);
 add('C','他人简历上下文不可读','GET',route+'/context?resumeId=902',undefined,'',1,fail);
 query('C','求职者只能推荐自己的简历',{resumeId:901},'',2,fail);
 query('C','另一个求职者自己的简历可查询',{resumeId:902},test('已投递标记隔离',"pm.expect(b.data.items.some(x=>x.jobId===1002)).eq(false);pm.expect(b.data.items.some(x=>x.jobId===1011)).eq(true);"),2);
 for(const who of [3,4]) {add('C','非求职者不能读选项','GET',route+'/options',undefined,'',who,fail);add('C','非求职者不能读上下文','GET',route+'/context?resumeId=901',undefined,'',who,fail);query('C','非求职者不能查询',{},'',who,fail);}
 add('C','匿名不能读选项','GET',route+'/options',undefined,'',null,unauthorized);
 add('C','匿名不能读上下文','GET',route+'/context?resumeId=901',undefined,'',null,unauthorized);
 query('C','匿名不能查询',{},'',null,unauthorized);
 login('D',1);
 add('D','保存原始求职意向','GET','/resume/901/preference',undefined,remember('rqBeforePreference'));
 add('D','保存原始上下文','GET',route+'/context?resumeId=901',undefined,remember('rqBeforeContext'));
 query('D','本次覆盖城市方向薪资不写回',{preferences:{directionCode:'FRONTEND',city:'深圳',salaryMin:30000,salaryMax:40000}});
 query('D','显式null清空本次城市和薪资',{preferences:{city:null,salaryMax:null}},test('本次未知保留权重',"pm.expect(b.data.appliedPreferences.city).eq(null);pm.expect(b.data.appliedPreferences.salaryMax).eq(null);pm.expect("+dimension(1002,'CITY')+".state).eq('UNKNOWN');pm.expect("+dimension(1002,'SALARY')+".state).eq('UNKNOWN');"));
 add('D','覆盖后默认上下文不变','GET',route+'/context?resumeId=901',undefined,unchanged('rqBeforeContext'));
 add('D','覆盖后数据库求职意向不变','GET','/resume/901/preference',undefined,unchanged('rqBeforePreference'));
 query('D','多次相同查询结果稳定',{},remember('rqRepeated'));
 query('D','重复查询逐项一致',{},unchanged('rqRepeated'));
 const parallel="let done=0;const values=[];for(let i=0;i<2;i++)pm.sendRequest({url:pm.environment.get('rqHost')+'"+route+"',method:'POST',header:{Authorization:'Bearer '+pm.collectionVariables.get('rqToken1'),'Content-Type':'application/json'},body:{mode:'raw',raw:JSON.stringify({resumeId:901,size:100})}},(err,res)=>{pm.test('并发只读请求成功',()=>{pm.expect(err).eq(null);pm.expect(res.code).eq(200);pm.expect(res.json().code).eq(200);});if(!err)values.push(res.json().data);if(++done===2)pm.test('并发结果一致',()=>pm.expect(values[0]).deep.eq(values[1]));});";
 add('D','同时查询不产生共享可变状态','GET',route+'/context?resumeId=901',undefined,parallel);
}

function fixture(mode,s,password){const r=spawnSync('D:/jdk17/bin/java.exe',['-cp','target;'+cp(),'RecommendationQueryFixture',mode,s.database,s.root],{env:{...process.env,...(password?{RECOMMENDATION_QUERY_FIXTURE_PASSWORD:password}:{})},encoding:'utf8',windowsHide:true});if(r.status!==0)throw Error(r.stderr||r.stdout);console.log(r.stdout.trim()||('Fixture '+mode+' complete'));}
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
  const s={database:'smart_recruit_recommendation_query_'+stamp,root:path.resolve('target/recommendation-query-fixture-'+stamp).replaceAll('\\','/'),port:18091,runners:{},folders:{}};
  if(fs.existsSync('docs/postman/recommendation-query-regression.manifest.json')){
   const m=JSON.parse(fs.readFileSync('docs/postman/recommendation-query-regression.manifest.json'));assert.equal(m.mainCollectionId,MAIN);
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
  assert(Number.isInteger(s.serverPid)&&s.serverPid>0);assert(/^smart_recruit_recommendation_query_\d+$/.test(s.database));
  const script='$p=Get-CimInstance Win32_Process -Filter "ProcessId = '+s.serverPid+'"; if($null -eq $p){exit 0}; if($p.Name -ne "java.exe" -or -not $p.CommandLine.Contains("'+s.database+'") -or -not $p.CommandLine.Contains("--server.port=18091")){throw "Refuse unrelated process"}; Stop-Process -Id '+s.serverPid+' -Force -Confirm:$false -ErrorAction Stop';
  const r=spawnSync('powershell.exe',['-NoProfile','-NonInteractive','-Command',script],{windowsHide:true,encoding:'utf8'});if(r.status!==0)throw Error(r.stderr||'Stop failed');s.stoppedPid=s.serverPid;delete s.serverPid;save();console.log('Owned isolated server stopped.');return;
 }
 if(['audit','redis-cleanup'].includes(mode)){fixture(mode,s);return;}
 if(mode==='start'){
  assert(!s.serverPid);const configured=fs.readFileSync('src/main/resources/application.yml','utf8').match(/url:\s*['"]?(jdbc:mysql:[^\s'"]+)/)?.[1];assert(configured?.includes('/smart_recruit?'));
  const out=fs.openSync(s.root+'/server.log','a'),err=fs.openSync(s.root+'/server-error.log','a');
  const p=spawn('D:/jdk17/bin/java.exe',['-cp',cp(),'com.recruit.smartrecruit.SmartRecruitApplication','--server.port=18091','--spring.data.redis.database='+JSON.parse(fs.readFileSync(s.root+'/redis.json')).database,'--spring.datasource.url='+configured.replace('/smart_recruit?','/'+s.database+'?'),'--smart-recruit.company-files.root='+s.root+'/storage','--spring.main.banner-mode=off'],{windowsHide:true,detached:true,stdio:['ignore',out,err]});s.serverPid=p.pid;save();p.unref();console.log('Isolated server PID='+p.pid+' port=18091');return;
 }
 const c=await connect();let password;
 try{
  if(mode==='setup-environment'){
   assert(!s.environmentId);password=JSON.parse(fs.readFileSync(s.root+'/credentials.json')).password;
   const r=data(await c.callTool({name:'createEnvironment',arguments:{workspace:WORKSPACE,environment:{name:'Smart Recruit Recommendation Query Isolated Regression',values:[{key:'rqHost',value:HOST,type:'default',enabled:true},{key:'rqPassword',value:password,type:'secret',enabled:true},{key:'rqAsOfDate',value:fs.readFileSync(s.root+'/as-of-date.txt','utf8'),type:'default',enabled:true},{key:'runRecommendationQueryRegression',value:'true',type:'default',enabled:true},{key:'rqFixtureReady',value:'true',type:'default',enabled:true}]}}}));s.environmentId=getId(r);assert(s.environmentId);save();console.log('Postman isolated environment saved: '+s.environmentId);return;
  }
  if(mode==='guard-off'){
   const env=data(await c.callTool({name:'getEnvironment',arguments:{environmentId:s.environmentId}})).environment;
   for(const key of ['runRecommendationQueryRegression','rqFixtureReady']){const index=env.values.findIndex(x=>x.key===key);assert(index>=0);data(await c.callTool({name:'patchEnvironment',arguments:{environmentId:s.environmentId,body:[{op:'replace',path:'/values/'+index+'/value',value:'false'}]}}));}const verified=data(await c.callTool({name:'getEnvironment',arguments:{environmentId:s.environmentId}})).environment;for(const key of ['runRecommendationQueryRegression','rqFixtureReady'])assert.equal(verified.values.find(x=>x.key===key).value,'false');console.log('Regression execution guards verified OFF.');return;
  }
  build();
  if(mode==='prepare'){
   const col=data(await c.callTool({name:'getCollection',arguments:{collectionId:MAIN}})).collection;assert.equal(col.info.name,'SmartRecruit API - Optimized Regression');const parent=col.itemRefs.find(x=>x.name==='03 Job');assert(parent);
   if(!s.folderId){s.folderId=getId(data(await c.callTool({name:'createCollectionFolder',arguments:{collectionId:MAIN,folder:bare(parent.id),name:'推荐查询回归 2026-11-01'}})));assert(s.folderId);save();}
   for(const stage of ['A','B','C','D']){
    const cases=items.filter(x=>x.stage===stage).map(({stage,...x})=>x);
    if(!s.runners[stage]){s.runners[stage]=getId(data(await c.callTool({name:'createCollection',arguments:{workspace:WORKSPACE,collection:{info:{name:'Smart Recruit Recommendation Query '+stage+' Execution',schema:'https://schema.getpostman.com/json/collection/v2.1.0/collection.json'},item:cases}}})));assert(s.runners[stage]);save();}
    const full=data(await c.callTool({name:'getCollection',arguments:{collectionId:s.runners[stage],model:'full'}})).collection;assert.equal(full.item.length,cases.length);
    if(!s.folders[stage]){const folderId=getId(data(await c.callTool({name:'createCollectionFolder',arguments:{collectionId:MAIN,folder:bare(s.folderId),name:stage+' '+({A:'契约评分过滤分页',B:'参数与边界校验',C:'身份与简历归属',D:'只读覆盖及重复查询'}[stage])}})));assert(folderId);data(await c.callTool({name:'transferCollectionRequests',arguments:{ids:full.item.map(x=>uid(x.id)),mode:'copy',target:{id:uid(folderId),model:'folder'},location:{position:'end'}}}));s.folders[stage]=folderId;save();}
    console.log('Persisted '+stage+' requests='+cases.length);
   }
   fs.writeFileSync('docs/postman/recommendation-query-regression.postman_collection.json',JSON.stringify({info:{name:'推荐查询持久回归',schema:'https://schema.getpostman.com/json/collection/v2.1.0/collection.json',description:'所有用例已加入SmartRecruit API - Optimized Regression / 03 Job。仅隔离端口18091；全部业务请求只读；夹具数据由受保护的本地脚本建立。'},item:['A','B','C','D'].map(stage=>({name:stage,item:items.filter(x=>x.stage===stage).map(({stage,...x})=>x)}))},null,2));
   fs.writeFileSync('docs/postman/recommendation-query-regression.manifest.json',JSON.stringify({mainCollectionId:MAIN,folderId:s.folderId,runners:s.runners,folders:s.folders,requestsByStage:Object.fromEntries(['A','B','C','D'].map(stage=>[stage,items.filter(x=>x.stage===stage).length]))},null,2));return;
  }
  if(mode==='verify'){
   const col=data(await c.callTool({name:'getCollection',arguments:{collectionId:MAIN,model:'full'}})).collection;const root=col.item.find(x=>x.name==='03 Job').item.find(x=>bare(x.id)===bare(s.folderId));const actual=root.item.flatMap(x=>x.item);assert.equal(actual.length,items.length);
   for(const item of items){const found=actual.find(x=>x.name===item.name||x.name===item.name+' Copy');assert(found,item.name);assert.deepStrictEqual(found.event,item.event);assert.equal(found.request.method,item.request.method);assert.equal(typeof found.request.url==='string'?found.request.url:found.request.url.raw,item.request.url);assert.equal(found.request.body?.raw,item.request.body?.raw);}console.log('Verified '+actual.length+' exact persistent requests in required collection');return;
  }
  assert(['A','B','C','D'].includes(mode));const env=data(await c.callTool({name:'getEnvironment',arguments:{environmentId:s.environmentId}})).environment;password=env.values.find(x=>x.key==='rqPassword')?.value;
  const result=await c.callTool({name:'runCollection',arguments:{collectionId:s.runners[mode],environmentId:s.environmentId,requestTimeout:30000,scriptTimeout:20000,stopOnError:true,stopOnFailure:true}},undefined,{timeout:300000});
  const output=result.content.filter(x=>x.type==='text').map(x=>x.text).join('\n');fs.writeFileSync(s.root+'/result-'+mode+'.json',redact(JSON.stringify(result),password));
  const tail=output.lastIndexOf('=== ');console.log(redact(tail>=0?output.slice(tail):output.slice(-5000),password));
  for(const line of output.match(/^.*RS_RACE=.*$/gm)||[])console.log(redact(line,password));
  if(result.isError||!Number(output.match(/Total assertions:\s*(\d+)/)?.[1])||/Failed assertions:\s*[1-9]|Failed requests:\s*[1-9]/.test(output)){console.log(redact(output.slice(-12000),password));process.exitCode=1;}
 }finally{await c.close();}
})().catch(e=>{console.error(redact(e.message,process.env.POSTMAN_API_KEY));process.exitCode=1;});
