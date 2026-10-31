const fs = require('fs'), path = require('path'), assert = require('assert');
const {spawn, spawnSync} = require('child_process');
const {connect, data} = require('./postman-mcp.cjs');
const MAIN = '56227183-00855bc5-e68c-4aab-bdc2-faffe0f70f82';
const WORKSPACE = 'baec8bed-057c-431c-832a-21a7d3b2a685';
const ENV = '56227183-c3b583b6-bced-4cd2-a4e8-e22bc54e396f';
const STATE = process.env.APPLICATION_ACCEPTANCE_STATE_FILE || 'target/application-acceptance-state.json';
const HOST = 'http://127.0.0.1:18087', NOW = '2026-10-28T12:34:56';
assert(path.resolve(STATE).startsWith(path.resolve('target') + path.sep));
const bare = x => x.replace(/^56227183-/, ''), uid = x => x.startsWith('56227183-') ? x : '56227183-' + x;
const guard = "if(pm.environment.get('runApplicationAcceptance')!=='true'||pm.environment.get('apHost')!=='" + HOST + "'||!/^smart_recruit_acceptance_\\d+$/.test(pm.environment.get('acceptanceFixtureDatabase')||'')){pm.execution.skipRequest();}";
const ok = "const b=pm.response.json();pm.test('HTTP200且业务200',()=>{pm.expect(pm.response.code).eq(200);pm.expect(b.code).eq(200);});";
const fail = "const b=pm.response.json();pm.test('业务拒绝且无数据',()=>{pm.expect(pm.response.code).eq(200);pm.expect(b.code).eq(500);pm.expect(b.data).eq(null);});";
const unauth = "pm.test('未登录HTTP401',()=>pm.expect(pm.response.code).eq(401));";
const fixed = "pm.test('使用隔离固定时钟',()=>{pm.expect(b.data.serverTime).eq('" + NOW + "');pm.expect(b.data.timeZone).eq('Asia/Shanghai');});";
const eventKeys = 'id eventType fromStatus toStatus actorType occurredAt summary interviewId interviewChange'.split(' ').sort();
const changeKeys = 'previousStartTime previousEndTime startTime endTime previousMode mode cancelReason'.split(' ').sort();
const listKeys = 'id jobId companyId jobTitle companyName companyLogo city employment salaryMin salaryMax salaryUnit negotiable jobStatus jobAvailable jobViewable companyAvailable resumeId resumeTitle resumeAvailable status statusText createTime updateTime'.split(' ').sort();
const interviewKeys = 'id applicationId startTime endTime mode platform meetingUrl address candidateNote status timingStatus version cancelReason cancelledAt createTime updateTime'.split(' ').sort();
const privateEvents = "pm.test('时间线和面试只返回契约字段',()=>{b.data.events.forEach(e=>{pm.expect(Object.keys(e).sort()).deep.eq(" + JSON.stringify(eventKeys) + ");if(e.interviewChange)pm.expect(Object.keys(e.interviewChange).sort()).deep.eq(" + JSON.stringify(changeKeys) + ");});b.data.interviews.forEach(i=>pm.expect(Object.keys(i).sort()).deep.eq(" + JSON.stringify(interviewKeys) + "));});";
const online = {startTime:'2026-10-29T14:00:00',endTime:'2026-10-29T15:00:00',mode:'ONLINE',platform:'回归平台',meetingUrl:'https://example.com/phase5',candidateNote:'请提前准备'};
const offline = {startTime:'2026-10-30T14:00:00',endTime:'2026-10-30T15:00:00',mode:'OFFLINE',address:'隔离测试地址'};
let items = [];
function add(stage,name,method,url,body,tests=ok,who=1) {
    const header = who ? [{key:'Authorization',value:'Bearer {{acToken'+who+'}}'}] : [];
    const request = {method,url:'{{apHost}}'+url,auth:{type:'noauth'},header};
    if(body !== undefined) {header.push({key:'Content-Type',value:'application/json'});request.body={mode:'raw',raw:JSON.stringify(body)};}
    items.push({stage,name:String(items.length+1).padStart(3,'0')+' '+name,request,event:[
        {listen:'prerequest',script:{type:'text/javascript',exec:[guard]}},
        {listen:'test',script:{type:'text/javascript',exec:[tests]}}
    ]});
}
function login(stage,who) {
    add(stage,'登录合成身份'+who,'POST','/user/login',{username:'ap_event_'+who,password:'{{apPassword}}'},ok+"pm.collectionVariables.set('acToken"+who+"',b.data);",null);
    add(stage,'核对身份'+who,'GET','/user/info',undefined,ok+"pm.test('账号和角色',()=>{pm.expect(b.data.id).eq("+(100+who)+");pm.expect(b.data.role).eq('"+([1,2,7,8,9].includes(who)?'JOB_SEEKER':who===6?'ADMIN':'COMPANY')+"');});",who);
}
function page(stage,name,query,tests='',who=1) {add(stage,name,'GET','/application/mine/page'+(query?'?'+query:''),undefined,ok+tests,who);}
function ids(expected) {return "pm.test('总数及确定顺序',()=>{pm.expect(b.data.total).eq("+expected.length+");pm.expect(b.data.items.map(x=>x.id)).deep.eq("+JSON.stringify(expected)+");});";}
function findFlow(job,who=1) {page('A','定位接口新投递'+job,'dateRange=ALL&keyword=PH5_FLOW_'+job,"pm.test('只有目标投递',()=>{pm.expect(b.data.total).eq(1);pm.expect(b.data.items[0].jobId).eq("+job+");});pm.collectionVariables.set('acApp"+job+"',b.data.items[0].id);",who);}
function apply(job,who=1) {add('A','真实投递'+job,'POST','/application',{jobId:job,resumeId:who===2?302:301},ok,who);findFlow(job,who);}
function state(job,target,tests=ok,who=3) {add('A','投递'+job+'推进至'+target,'PUT','/application/{{acApp'+job+'}}/status',{status:target},tests,who);}
function detail(job,status,count,extra='',who=1) {add('A','检查真实投递详情'+job,'GET','/application/mine/{{acApp'+job+'}}',undefined,ok+fixed+privateEvents+"pm.test('当前状态和完整历史',()=>{pm.expect(b.data.status).eq("+status+");pm.expect(b.data.historyComplete).eq(true);pm.expect(b.data.events).lengthOf("+count+");pm.expect(b.data.events.filter(e=>e.eventType==='APPLIED')).lengthOf(1);pm.expect(b.data.events.every(e=>e.occurredAt==='"+NOW+"')).eq(true);});"+extra,who);}
function schedule(job,body=online,who=3) {add('A','新建面试'+job,'POST','/application/{{acApp'+job+'}}/interviews',body,ok+"pm.test('新场次版本0',()=>{pm.expect(b.data.version).eq(0);pm.expect(b.data.status).eq('SCHEDULED');pm.expect(b.data.timingStatus).eq('UPCOMING');});pm.collectionVariables.set('acInterview"+job+"',b.data.id);",who);}
function next(stage,extra='',who=1) {add(stage,'下一场面试'+who,'GET','/application/mine/interviews/next',undefined,ok+fixed+extra,who);}
function build() {
    for(const who of [1,2,3,4,5,6,8]) login('A',who);
    next('A',"pm.test('初始无排期',()=>pm.expect(b.data.interview).eq(null));");
    apply(4501);detail(4501,0,1,"pm.test('起点真实',()=>{pm.expect(b.data.events[0].eventType).eq('APPLIED');pm.expect(b.data.events[0].fromStatus).eq(null);pm.expect(b.data.events[0].toStatus).eq(0);});");
    add('A','未进入面试不能安排','POST','/application/{{acApp4501}}/interviews',online,fail,3);
    state(4501,1);detail(4501,1,2);state(4501,2);detail(4501,2,3);
    next('A',"pm.test('进入面试阶段不等于已有场次',()=>pm.expect(b.data.interview).eq(null));");
    add('A','开始等于固定当前时间拒绝','POST','/application/{{acApp4501}}/interviews',{...online,startTime:NOW},fail,3);
    add('A','开始早于固定当前时间一毫秒拒绝','POST','/application/{{acApp4501}}/interviews',{...online,startTime:'2026-10-28T12:34:55.999'},fail,3);
    schedule(4501);detail(4501,2,4);
    next('A',"pm.test('新安排立即可查',()=>{pm.expect(b.data.interview.applicationId).eq(Number(pm.collectionVariables.get('acApp4501')));pm.expect(b.data.interview.startTime).eq('2026-10-29T14:00:00');});");
    add('A','跨求职者不能读新投递','GET','/application/mine/{{acApp4501}}',undefined,fail,2);
    for(const who of [1,4,5,6]) add('A','身份'+who+'不能改约该安排','PUT','/application/{{acApp4501}}/interviews/{{acInterview4501}}',{...offline,version:0},fail,who);
    add('A','完整改为线下','PUT','/application/{{acApp4501}}/interviews/{{acInterview4501}}',{...offline,version:0},ok+"pm.test('版本加1且清空线上信息',()=>{pm.expect(b.data.version).eq(1);pm.expect(b.data.platform).eq(null);pm.expect(b.data.meetingUrl).eq(null);pm.expect(b.data.candidateNote).eq(null);});",3);
    add('A','旧页面版本拒绝覆盖','PUT','/application/{{acApp4501}}/interviews/{{acInterview4501}}',{...online,version:0},fail,3);
    detail(4501,2,5,"pm.test('改期真实快照',()=>{const e=b.data.events.find(x=>x.eventType==='INTERVIEW_RESCHEDULED');pm.expect(e.interviewChange.previousStartTime).eq('2026-10-29T14:00:00');pm.expect(e.interviewChange.startTime).eq('2026-10-30T14:00:00');pm.expect(e.interviewChange.previousMode).eq('ONLINE');pm.expect(e.interviewChange.mode).eq('OFFLINE');});");
    add('A','主动取消原面试','PUT','/application/{{acApp4501}}/interviews/{{acInterview4501}}/cancel',{version:1,reason:'沟通后重新安排'},ok,3);
    next('A',"pm.test('取消后无下一场',()=>pm.expect(b.data.interview).eq(null));");
    schedule(4501,offline);detail(4501,2,7,"pm.test('保留取消历史和新场次',()=>{pm.expect(b.data.interviews).lengthOf(2);pm.expect(b.data.interviews.filter(x=>x.status==='CANCELLED')).lengthOf(1);});");
    state(4501,3);detail(4501,3,9,"pm.test('录用自动取消且旧取消不重复',()=>{pm.expect(b.data.interviews.every(x=>x.status==='CANCELLED')).eq(true);pm.expect(b.data.interviews.map(x=>x.version).sort()).deep.eq([1,2]);pm.expect(b.data.events.filter(x=>x.eventType==='INTERVIEW_CANCELLED'&&x.actorType==='SYSTEM')).lengthOf(1);});");
    next('A',"pm.test('终态无下一场',()=>pm.expect(b.data.interview).eq(null));");
    state(4501,2,fail);add('A','终态不能再创建面试','POST','/application/{{acApp4501}}/interviews',online,fail,3);
    detail(4501,3,9);
    apply(4502);state(4502,2);schedule(4502);state(4502,4);
    detail(4502,4,5,"pm.test('跳过筛选且未通过自动失效',()=>{pm.expect(b.data.statusText).eq('未通过');pm.expect(b.data.events.filter(x=>x.eventType==='STATUS_CHANGED').map(x=>[x.fromStatus,x.toStatus])).deep.eq([[2,4],[0,2]]);pm.expect(b.data.interviews[0].status).eq('CANCELLED');});");
    for(const [job,target] of [[4503,3],[4504,4]]) {apply(job);state(job,target);detail(job,target,2,"pm.test('直达终态不伪造面试或中间阶段',()=>{pm.expect(b.data.interviews).deep.eq([]);pm.expect(b.data.events[0].fromStatus).eq(0);pm.expect(b.data.events[0].toStatus).eq("+target+");});");}
    apply(4505);state(4505,2);schedule(4505,{...online,startTime:'2026-10-28T12:34:56.001',endTime:'2026-10-28T13:34:56'});
    detail(4505,2,3);next('A',"pm.test('当前时刻后一毫秒可安排',()=>{pm.expect(b.data.interview.applicationId).eq(Number(pm.collectionVariables.get('acApp4505')));pm.expect(b.data.interview.startTime).eq('2026-10-28T12:34:56.001');});");
    apply(4506,2);state(4506,2,fail,3);state(4506,2,ok,4);schedule(4506,online,4);detail(4506,2,3,'',2);
    next('A',"pm.test('本人安排不会被另一求职者覆盖',()=>pm.expect(b.data.interview.applicationId).eq(Number(pm.collectionVariables.get('acApp4505'))));");
    next('A',"pm.test('另一求职者只读本人',()=>pm.expect(b.data.interview.applicationId).eq(Number(pm.collectionVariables.get('acApp4506'))));",2);
    add('A','完整流程后概览计数','GET','/application/mine/overview',undefined,ok+"pm.test('当前状态桶正确',()=>pm.expect(b.data).deep.eq({totalCount:10,pendingCount:1,screeningCount:1,interviewCount:2,hiredCount:3,rejectedCount:3}));");
    add('A','旧企业投递列表兼容','GET','/application/my',undefined,ok+"pm.test('企业数组仍含新投递',()=>pm.expect(b.data.some(x=>x.jobId===4501)).eq(true));",3);
    add('A','旧求职者列表兼容','GET','/application/mine',undefined,ok+"pm.test('本人数组仍含新投递',()=>pm.expect(b.data.some(x=>x.jobId===4501)).eq(true));");
    add('A','旧详情兼容','GET','/application/{{acApp4501}}',undefined,ok+"pm.test('旧详情保持状态且不扩充新事件字段',()=>{pm.expect(b.data.status).eq(3);pm.expect(b.data).not.have.property('events');});");

    for(const who of [2,3,6,8,9]) login('B',who);
    next('B','',8);
    const last7=[2108,2107,2112,2111,2110,2103,2102];
    const last30=[...last7,2101,2106,2105];
    const all=[...last30,2104];
    const q='keyword=PH5_BOUNDARY_&pageSize=100&dateRange=';
    page('B','七自然日起点前一秒排除且起点包含',q+'LAST_7_DAYS',ids(last7),2);
    page('B','三十自然日起点前一秒排除且起点包含',q+'LAST_30_DAYS',ids(last30),2);
    page('B','ALL仍排除当前时刻后一秒',q+'ALL',ids(all),2);
    page('B','默认日期为三十天','keyword=PH5_BOUNDARY_&pageSize=100',ids(last30),2);
    page('B','默认每页十条且保留total','',"pm.test('默认10条非20条',()=>{pm.expect(b.data.total).eq(11);pm.expect(b.data.items).lengthOf(10);pm.expect(b.data.items[0].jobId).eq(4506);});",2);
    for(let p=1;p<=4;p++) page('B','同时间排序分页'+p,'keyword=PH5_BOUNDARY_&dateRange=LAST_7_DAYS&pageSize=2&page='+p,"pm.test('分页total与顺序',()=>{pm.expect(b.data.total).eq(7);pm.expect(b.data.items.map(x=>x.id)).deep.eq("+JSON.stringify(last7.slice((p-1)*2,p*2))+");});",2);
    page('B','超末页保留总数','keyword=PH5_BOUNDARY_&dateRange=ALL&pageSize=2&page=999',"pm.test('不把页码修正到末页',()=>{pm.expect(b.data.total).eq(11);pm.expect(b.data.items).deep.eq([]);});",2);
    for(let status=0;status<=4;status++) {const expected=all.filter(id=>(id-2101)%5===status);page('B','日期与状态组合'+status,q+'ALL&status='+status,ids(expected),2);}
    page('B','关键词精准命中now','dateRange=ALL&keyword=PH5_BOUNDARY_2108',ids([2108]),2);
    page('B','未来记录不进入ALL列表','dateRange=ALL&keyword=PH5_BOUNDARY_2109',ids([]),2);
    add('B','概览不受时间筛选并包含所有历史状态','GET','/application/mine/overview',undefined,ok+"pm.test('概览统计全部14条记录',()=>pm.expect(b.data).deep.eq({totalCount:14,pendingCount:4,screeningCount:3,interviewCount:3,hiredCount:2,rejectedCount:2}));",2);
    page('B','不接受参数切换投递归属',q+'LAST_7_DAYS&userId=101&companyId=201&sort=idASC',ids(last7),2);
    page('B','空白关键词等同不筛选','keyword=%20%20&pageSize=100',"pm.test('空白不改变默认日期口径',()=>pm.expect(b.data.total).eq(11));",2);
    page('B','一百字符关键词允许','keyword='+('x'.repeat(100)),ids([]),2);
    for(const query of ['page=0','page=-1','page=1.5','page=abc','page=2147483648','pageSize=0','pageSize=101','pageSize=1.5','pageSize=2147483648','status=-1','status=5','status=1.5','status=abc','dateRange=last_7_days','dateRange=BAD','keyword='+('x'.repeat(101))]) add('B','非法查询 '+query.slice(0,40),'GET','/application/mine/page?'+query,undefined,fail,2);
    for(const who of [3,6]) for(const route of ['page','overview','2108']) add('B','角色'+who+'禁止本人查询'+route,'GET','/application/mine/'+route,undefined,fail,who);
    add('B','未登录不能查询本人分页','GET','/application/mine/page',undefined,unauth,null);
    for(const route of ['page','overview']) add('B','脏状态明确报错'+route,'GET','/application/mine/'+route,undefined,fail,9);
    add('B','详情脏状态明确报错','GET','/application/mine/3901',undefined,fail,9);
    for(const route of ['page','overview']) add('B','空用户'+route,'GET','/application/mine/'+route,undefined,ok+(route==='page'?"pm.test('空页',()=>{pm.expect(b.data.total).eq(0);pm.expect(b.data.items).deep.eq([]);});":"pm.test('全部计数0',()=>Object.values(b.data).forEach(x=>pm.expect(x).eq(0)));"),8);

    for(const who of [1,2,7]) login('C',who);
    const matrixIds=Array.from({length:22},(_,i)=>3022-i);
    page('C','所有关联组合均保留历史投递','dateRange=ALL&pageSize=100',ids(matrixIds)+"pm.test('列表白名单及缺失信息',()=>b.data.items.forEach(x=>pm.expect(Object.keys(x).sort()).deep.eq("+JSON.stringify(listKeys)+")));",7);
    for(let index=0;index<16;index++) {
        const id=3001+index,state=Math.floor(index/4),company=index%4;
        const assertions="pm.test('岗位和企业可见性分别判断',()=>{pm.expect(b.data.jobAvailable).eq(true);pm.expect(b.data.jobStatus).eq("+state+");pm.expect(b.data.companyAvailable).eq("+(company!==3)+");pm.expect(b.data.jobViewable).eq("+(state===0&&company===0)+");pm.expect(b.data.resumeAvailable).eq(true);pm.expect(b.data.companyLogo).eq(null);"+(company===3?"pm.expect(b.data.companyName).eq(null);pm.expect(b.data.companyId).eq(299999);":"pm.expect(b.data.companyName).a('string');")+"});";
        add('C','岗位状态'+state+'与企业组合'+company,'GET','/application/mine/'+id,undefined,ok+fixed+privateEvents+assertions+"pm.test('旧历史不补造',()=>{pm.expect(b.data.historyComplete).eq(false);pm.expect(b.data.events).deep.eq([]);pm.expect(b.data.interviews).deep.eq([]);});",7);
        page('C','列表与详情可用标记一致'+id,'dateRange=ALL&keyword=PH5_MATRIX_'+id,ids([id])+"pm.test('列表标记',()=>{const x=b.data.items[0];pm.expect(x.jobAvailable).eq(true);pm.expect(x.companyAvailable).eq("+(company!==3)+");pm.expect(x.jobViewable).eq("+(state===0&&company===0)+");});",7);
    }
    add('C','岗位缺失但投递详情仍保留','GET','/application/mine/3017',undefined,ok+fixed+"pm.test('保留原岗位ID且不猜资料',()=>{pm.expect(b.data.jobId).eq(799999);for(const k of ['jobAvailable','jobViewable','companyAvailable'])pm.expect(b.data[k]).eq(false);for(const k of ['jobTitle','companyId','companyName','salaryMin','salaryMax','salaryUnit','negotiable'])pm.expect(b.data[k]).eq(null);pm.expect(b.data.resumeAvailable).eq(true);});",7);
    for(const [id,resume] of [[3018,399999],[3019,302]]) add('C','原简历缺失或已属他人'+id,'GET','/application/mine/'+id,undefined,ok+"pm.test('保留简历ID且不泄露他人标题',()=>{pm.expect(b.data.resumeId).eq("+resume+");pm.expect(b.data.resumeAvailable).eq(false);pm.expect(b.data.resumeTitle).eq(null);});",7);
    add('C','空标题不等同简历缺失','GET','/application/mine/3020',undefined,ok+"pm.test('空标题简历仍存在',()=>{pm.expect(b.data.resumeAvailable).eq(true);pm.expect(b.data.resumeTitle).eq('');});",7);
    add('C','面议薪资不伪造数值','GET','/application/mine/3021',undefined,ok+"pm.test('面议且薪资为空',()=>{pm.expect(b.data.negotiable).eq(true);pm.expect(b.data.salaryMin).eq(null);pm.expect(b.data.salaryMax).eq(null);});",7);
    add('C','明确月薪单位和上下限','GET','/application/mine/3022',undefined,ok+"pm.test('月薪数据',()=>{pm.expect(b.data.negotiable).eq(false);pm.expect(b.data.salaryMin).eq(5000);pm.expect(b.data.salaryMax).eq(9000);pm.expect(b.data.salaryUnit).eq('MONTH');});",7);
    page('C','企业名称也可搜索','dateRange=ALL&pageSize=100&keyword=PH5_REJECTED_COMPANY',ids([3015,3011,3007,3003]),7);
    page('C','找不到关键词不影响原历史','dateRange=ALL&keyword=不存在的企业名称',ids([]),7);
    add('C','关联缺失不改变概览计数','GET','/application/mine/overview',undefined,ok+"pm.test('22条历史全部计数',()=>pm.expect(b.data).deep.eq({totalCount:22,pendingCount:6,screeningCount:4,interviewCount:4,hiredCount:4,rejectedCount:4}));",7);
    for(const who of [1,2]) add('C','其他求职者不可读缺失资料投递'+who,'GET','/application/mine/3017',undefined,fail+"pm.test('缺失和越权统一文案',()=>pm.expect(b.message).eq('投递不存在或无权访问'));",who);
    add('C','不存在投递同样拒绝','GET','/application/mine/999999',undefined,fail+"pm.test('统一错误文案',()=>pm.expect(b.message).eq('投递不存在或无权访问'));",7);
}
function fixture(mode,state,password) {
    const result=spawnSync('D:/jdk17/bin/java.exe',['-cp','target;target/classes;'+fs.readFileSync('target/files-classpath.txt','utf8').trim(),'ApplicationAcceptanceFixture',mode,state.database,state.root],{env:{...process.env,...(password?{APPLICATION_FIXTURE_PASSWORD:password}:{})},encoding:'utf8',windowsHide:true});
    if(result.status!==0) throw Error(result.stderr||result.stdout);console.log(result.stdout.trim());
}
const getId = result => result.collection?.uid||result.collection?.id||result.data?.id||result.folder?.id||result.id;
const redact = (value,password) => String(value).replaceAll(password||'___','[PASSWORD]').replaceAll(process.env.POSTMAN_API_KEY||'___','[KEY]').replace(/ApEv_[a-f0-9]{16}/g,'[PASSWORD]').replace(/eyJ[\w-]+\.[\w-]+\.[\w-]+/g,'[JWT]');
async function patchFlag(client,key,value) {
    const environment=data(await client.callTool({name:'getEnvironment',arguments:{environmentId:ENV}})).environment;
    const index=environment.values.findIndex(x=>x.key===key);
    const body=index<0?[{op:'add',path:'/values/'+environment.values.length,value:{key,value,enabled:true,type:'default'}}]:[{op:'replace',path:'/values/'+index+'/value',value}];
    data(await client.callTool({name:'patchEnvironment',arguments:{environmentId:ENV,body}}));
}
(async()=>{
    const mode=process.argv[2];
    if(mode==='audit'){fixture('audit',JSON.parse(fs.readFileSync(STATE)));return;}
    if(mode==='start') {
        const state=JSON.parse(fs.readFileSync(STATE));assert(!state.serverPid&&/^smart_recruit_acceptance_\d+$/.test(state.database));
        const url=fs.readFileSync('src/main/resources/application.yml','utf8').match(/url:\s*['"]?(jdbc:mysql:[^\s'"]+)/)?.[1];assert(url?.includes('/smart_recruit?'));
        const out=fs.openSync(state.root+'/server.log','a'),err=fs.openSync(state.root+'/server-error.log','a');
        const child=spawn('D:/jdk17/bin/java.exe',['-cp','target;target/classes;'+fs.readFileSync('target/files-classpath.txt','utf8').trim(),'ApplicationAcceptanceClock','--server.port=18087','--spring.datasource.url='+url.replace('/smart_recruit?','/'+state.database+'?'),'--spring.data.redis.database=14','--smart-recruit.company-files.root='+state.root+'/storage','--spring.main.banner-mode=off'],{detached:true,windowsHide:true,stdio:['ignore',out,err]});
        state.serverPid=child.pid;fs.writeFileSync(STATE,JSON.stringify(state,null,2));child.unref();
        const deadline=Date.now()+55000;
        while(!fs.readFileSync(state.root+'/server.log','utf8').includes('Started ApplicationAcceptanceClock')) {
            assert(Date.now()<deadline,'Test server did not become ready; inspect isolated logs before running requests');
            process.kill(child.pid,0);
            await new Promise(resolve=>setTimeout(resolve,500));
        }
        console.log('Isolated acceptance server ready PID='+child.pid+' fixed Shanghai now='+NOW);return;
    }
    if(mode==='build') {build();console.log(JSON.stringify({persistentRequests:items.length,groups:Object.fromEntries(['A','B','C'].map(stage=>[stage,items.filter(x=>x.stage===stage).length]))}));return;}
    const client=await connect();let password;
    try {
        if(mode==='setup') {
            assert(!fs.existsSync(STATE));const environment=data(await client.callTool({name:'getEnvironment',arguments:{environmentId:ENV}})).environment;
            assert(environment.values.find(x=>x.key==='apHost')?.value===HOST);password=environment.values.find(x=>x.key==='apPassword')?.value;assert(password,'Previously authorized synthetic secret required');
            const stamp=Date.now(),state={database:'smart_recruit_acceptance_'+stamp,root:path.resolve('target/application-acceptance-fixtures-'+stamp).replaceAll('\\','/'),clock:NOW,port:18087,environmentId:ENV,runners:{},folders:{}};
            if(fs.existsSync('docs/postman/application-acceptance-regression.manifest.json')) {const manifest=JSON.parse(fs.readFileSync('docs/postman/application-acceptance-regression.manifest.json'));assert.equal(manifest.collectionId,MAIN);Object.assign(state,{runners:manifest.runners,folders:manifest.folders,folderId:manifest.folderId});}
            fs.writeFileSync(STATE,JSON.stringify(state,null,2));fixture('setup',state,password);
            await patchFlag(client,'acceptanceFixtureDatabase',state.database);await patchFlag(client,'runApplicationAcceptance','true');
            console.log('New isolated schema ready; only nonsecret environment flags updated; existing authorized secret reused.');return;
        }
        if(mode==='guard-off') {await patchFlag(client,'runApplicationAcceptance','false');console.log('Acceptance guard disabled; credentials unchanged.');return;}
        if(mode==='status') {
            const environment=data(await client.callTool({name:'getEnvironment',arguments:{environmentId:ENV}})).environment;
            const allowed=['apHost','acceptanceFixtureDatabase','runApplicationAcceptance','runInterviewRegression','runApplicationEventRegression','applicationEventFixtureReady'];
            console.log(JSON.stringify(Object.fromEntries(allowed.map(key=>[key,environment.values.find(x=>x.key===key)?.value??null]))));return;
        }
        const state=JSON.parse(fs.readFileSync(STATE)),save=()=>fs.writeFileSync(STATE,JSON.stringify(state,null,2));build();
        if(mode==='prepare') {
            const collection=data(await client.callTool({name:'getCollection',arguments:{collectionId:MAIN}})).collection;assert.equal(collection.info.name,'SmartRecruit API - Optimized Regression');
            const parent=collection.itemRefs.find(x=>x.name==='05 Application');assert(parent);
            if(!state.folderId) {state.folderId=getId(data(await client.callTool({name:'createCollectionFolder',arguments:{collectionId:MAIN,folder:bare(parent.id),name:'投递升级第五阶段验收 2026-10-28'}})));save();}
            for(const stage of ['A','B','C']) {
                const cases=items.filter(x=>x.stage===stage).map(({stage,...request})=>request);
                if(!state.runners[stage]) {state.runners[stage]=getId(data(await client.callTool({name:'createCollection',arguments:{workspace:WORKSPACE,collection:{info:{name:'Smart Recruit Acceptance '+stage+' Execution',schema:'https://schema.getpostman.com/json/collection/v2.1.0/collection.json'},item:cases}}})));save();}
                const full=data(await client.callTool({name:'getCollection',arguments:{collectionId:state.runners[stage],model:'full'}})).collection;assert.equal(full.item.length,cases.length);
                if(!state.folders[stage]) {const folder=getId(data(await client.callTool({name:'createCollectionFolder',arguments:{collectionId:MAIN,folder:bare(state.folderId),name:stage+' '+({A:'真实投递完整流程',B:'自然日和查询输入边界',C:'历史关联缺失与可见性矩阵'}[stage])}})));data(await client.callTool({name:'transferCollectionRequests',arguments:{ids:full.item.map(x=>uid(x.id)),mode:'copy',target:{id:uid(folder),model:'folder'},location:{position:'end'}}}));state.folders[stage]=folder;save();}
                console.log('Persisted '+stage+' requests='+cases.length);
            }
            fs.writeFileSync('docs/postman/application-acceptance-regression.manifest.json',JSON.stringify({collectionId:MAIN,folderId:state.folderId,runners:state.runners,folders:state.folders,persistentRequests:items.length,clock:NOW},null,2));
            fs.writeFileSync('docs/postman/application-acceptance-regression.postman_collection.json',JSON.stringify({info:{name:'投递升级第五阶段持久验收',schema:'https://schema.getpostman.com/json/collection/v2.1.0/collection.json',description:'仅新建隔离库和18087测试实例，applicationClock固定为2026-10-28T12:34:56 Asia/Shanghai。按A/B/C顺序运行。'},item:['A','B','C'].map(stage=>({name:stage,item:items.filter(x=>x.stage===stage).map(({stage,...request})=>request)}))},null,2));return;
        }
        if(mode==='verify') {
            const collection=data(await client.callTool({name:'getCollection',arguments:{collectionId:MAIN,model:'full'}})).collection;
            const folder=collection.item.find(x=>x.name==='05 Application').item.find(x=>bare(x.id)===bare(state.folderId));const actual=folder.item.flatMap(x=>x.item);assert.equal(actual.length,items.length);
            for(const expected of items) {const found=actual.find(x=>x.name===expected.name||x.name===expected.name+' Copy');assert(found,expected.name);assert.deepStrictEqual(found.event,expected.event);assert.equal(found.request.method,expected.request.method);assert.equal(typeof found.request.url==='string'?found.request.url:found.request.url.raw,expected.request.url);assert.equal(found.request.body?.raw,expected.request.body?.raw);}
            console.log('Verified '+actual.length+' exact requests, bodies and scripts in required collection.');return;
        }
        assert(['A','B','C'].includes(mode));const environment=data(await client.callTool({name:'getEnvironment',arguments:{environmentId:ENV}})).environment;password=environment.values.find(x=>x.key==='apPassword')?.value;
        const result=await client.callTool({name:'runCollection',arguments:{collectionId:state.runners[mode],environmentId:ENV,requestTimeout:30000,scriptTimeout:20000,stopOnError:true,stopOnFailure:true}},undefined,{timeout:300000});
        const output=result.content.filter(x=>x.type==='text').map(x=>x.text).join('\n');fs.writeFileSync(state.root+'/result-'+mode+'.json',redact(JSON.stringify(result),password));
        const position=output.lastIndexOf('=== ');console.log(redact(position>=0?output.slice(position):output.slice(-6000),password));
        if(result.isError||!Number(output.match(/Total assertions:\s*(\d+)/)?.[1])||/Failed assertions:\s*[1-9]|Failed requests:\s*[1-9]/.test(output))process.exitCode=1;
    } finally {await client.close();}
})().catch(error=>{console.error(redact(error.message));process.exitCode=1;});
