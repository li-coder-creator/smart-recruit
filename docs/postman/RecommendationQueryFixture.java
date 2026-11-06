import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.security.MessageDigest;
import org.yaml.snakeyaml.Yaml;

/** Reuses guarded fixture utilities; all writes are limited to a fresh recommendation-query schema. */
public class RecommendationQueryFixture extends JobAbilitiesFixture {
    static final LocalDate AS_OF=LocalDate.now(ZoneId.of("Asia/Shanghai"));
    static Map<String,Object> row(Object... values) {
        Map<String,Object> result=new LinkedHashMap<>();for(int i=0;i<values.length;i+=2)result.put(values[i].toString(),values[i+1]);return result;
    }
    static void insert(String table,Map<String,Object> values) throws Exception {
        if(!database.equals(connection.getCatalog())||!table.matches("[a-z_]+"))throw new IllegalStateException("Unsafe fixture insert");
        String sql="INSERT INTO `"+table+"` (`"+String.join("`,`",values.keySet())+"`) VALUES ("+String.join(",",Collections.nCopies(values.size(),"?"))+")";
        try(var s=connection.prepareStatement(sql)){int index=1;for(var value:values.values())s.setObject(index++,value);s.executeUpdate();}
    }
    static String fixtureFingerprint() throws Exception {
        var digest=MessageDigest.getInstance("SHA-256");
        for(var table:rows("SELECT table_name AS name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE' ORDER BY table_name")) {
            String name=table.get("name").toString();if(!name.matches("[a-zA-Z0-9_]+"))throw new IllegalStateException("Invalid table");
            var data=rows("SELECT * FROM `"+name+"`").stream().map(r->{try{return JSON.writeValueAsString(r);}catch(Exception e){throw new RuntimeException(e);}}).sorted().toList();
            digest.update(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));digest.update(JSON.writeValueAsBytes(data));
        }
        return HexFormat.of().formatHex(digest.digest());
    }
    static void job(int id,Map<String,Object> overrides) throws Exception {
        var values=row("id",id,"company_id",201,"title","推荐回归岗位"+id,"category","后端开发","city","上海","employment","全职",
                "status",0,"salary_min",20000,"salary_max",20000,"salary_unit","MONTH","negotiable",false,
                "experience","经验不限","education","本科","required_skills","Java","required_abilities_source","LEGACY_TEXT",
                "required_abilities_version",0L,"published_at",LocalDateTime.of(2026,10,1,10,0),"deadline",null);
        values.putAll(overrides);insert("job",values);
    }
    static void ability(int id,int jobId,String text,int order) throws Exception {insert("job_required_ability",row("id",id,"job_id",jobId,"raw_text",text,"sort_order",order));}
    static void setup() throws Exception {
        String before=fingerprint();
        if(!rows("SELECT schema_name FROM information_schema.schemata WHERE schema_name='"+database+"'").isEmpty())throw new IllegalStateException("Refuse existing fixture");
        try(var s=connection.createStatement()){s.execute("CREATE DATABASE `"+database+"` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");}
        connection.setCatalog(database);isolated("SET FOREIGN_KEY_CHECKS=0");
        try {
            for(var table:rows("SELECT table_name AS name FROM information_schema.tables WHERE table_schema='smart_recruit' AND table_type='BASE TABLE' ORDER BY table_name")) {
                String name=table.get("name").toString();if(!name.matches("[a-zA-Z0-9_]+"))throw new IllegalStateException("Invalid table");
                String ddl=rows("SHOW CREATE TABLE smart_recruit.`"+name+"`").get(0).get("Create Table").toString();
                if(ddl.contains("REFERENCES `smart_recruit`"))throw new IllegalStateException("Cross-schema FK");isolated(ddl);
            }
        }finally{isolated("SET FOREIGN_KEY_CHECKS=1");}
        String password=System.getenv("RECOMMENDATION_QUERY_FIXTURE_PASSWORD");
        if(password==null||!password.matches("[a-zA-Z0-9_]{10,26}"))throw new IllegalStateException("Synthetic password required");
        for(int i=1;i<=6;i++)insert("sys_user",row("id",100+i,"username","rq_input_"+i,"password",com.recruit.smartrecruit.utils.Md5Util.encrypt(password),"role",i<=2?"JOB_SEEKER":i==4?"ADMIN":"COMPANY"));
        insert("company",row("id",201,"user_id",103,"name","已审核回归企业","status","APPROVED"));
        insert("company",row("id",202,"user_id",105,"name","待审核回归企业","status","PENDING"));
        insert("company",row("id",203,"user_id",106,"name","拒绝回归企业","status","REJECTED"));
        for(int id:new int[]{901,902,903,904,905,906})insert("resume",row("id",id,"user_id",id==902?102:101,"title","推荐回归简历"+id,
                "career_stage",id==903?"UNKNOWN":"NEW_GRAD","formal_experience_status",id==905?"HAS_EXPERIENCE":id==903?"UNKNOWN":"NONE"));
        for(int id:new int[]{901,902,904,905,906})insert("job_preference",row("resume_id",id,"expected_position","后端开发","expected_city","Shanghai","salary_max",20000,"direction_code","BACKEND"));
        for(int id:new int[]{901,902,904,905,906}) {
            insert("resume_education",row("resume_id",id,"school_name","测试学校","major","测试专业","degree",id==905?"硕士研究生":"Bachelor",
                    "start_date",LocalDate.of(id==905?2026:2020,9,1),"end_date",LocalDate.of(id==905?2029:2024,6,30)));
            for(String skill:new String[]{"Java","SpringBoot","MySQL"})insert("resume_skill",row("resume_id",id,"skill_name",skill,"sort_order",0));
        }
        insert("resume_experience",row("resume_id",904,"company_name","实习测试企业","position","实习","experience_type","INTERNSHIP","start_date",LocalDate.of(2025,1,1),"end_date",LocalDate.of(2025,12,31),"is_current",false));
        insert("resume_experience",row("resume_id",905,"company_name","正式测试企业甲","position","开发","experience_type","FORMAL","start_date",LocalDate.of(2024,1,1),"end_date",LocalDate.of(2024,12,31),"is_current",false));
        insert("resume_experience",row("resume_id",905,"company_name","正式测试企业乙","position","开发","experience_type","FORMAL","start_date",LocalDate.of(2024,7,1),"end_date",LocalDate.of(2025,6,30),"is_current",false));
        isolated("UPDATE job_preference SET salary_min=30000,salary_max=20000 WHERE resume_id=906");
        job(1001,Map.of());
        var w2=row("required_skills","Java, Spring Boot, MySQL, Redis","salary_min",10000,"salary_max",20000,"experience","1-3","education","硕士");
        job(1002,w2);var day=new LinkedHashMap<>(w2);day.putAll(row("salary_unit","DAY","salary_min",500,"salary_max",1000));job(1003,day);
        var missing=new LinkedHashMap<>(w2);missing.putAll(row("salary_min",null,"salary_max",null));job(1004,missing);
        job(1005,row("required_skills",null));job(1006,row("required_abilities_source","STRUCTURED","required_abilities_version",2L));
        job(1007,row("required_skills","跨部门沟通"));job(1008,row("city","深圳"));
        job(1009,row("deadline",AS_OF.minusDays(1)));job(1010,row("deadline",AS_OF,"published_at",LocalDateTime.of(2026,10,1,11,0)));
        job(1011,Map.of());job(1012,row("deadline",AS_OF.minusDays(1)));
        job(1013,row("status",1));job(1014,row("status",2));job(1015,row("status",3));job(1016,row("company_id",202));job(1017,row("company_id",203));
        job(1018,row("category","前端开发","direction_code","FRONTEND","required_skills","JavaScript, Vue3"));
        job(1019,row("category","技术研发"));job(1020,row("category",null,"direction_code","BACKEND","published_at",null));
        job(1021,row("category","前端开发","direction_code","BACKEND"));
        job(1022,row("required_skills","Java\nRedis\nJava","required_abilities_source","STRUCTURED","required_abilities_version",7L));
        ability(2001,1022,"Redis",1);ability(2002,1022,"Java",0);ability(2003,1022,"Java",2);
        job(1023,row("salary_unit","YEAR","salary_min",900000,"salary_max",1000000));
        job(1024,row("negotiable",true,"salary_min",null,"salary_max",null));job(1025,row("salary_min",20000,"salary_max",10000));
        job(1026,Map.of());job(1027,row("salary_min",40000,"salary_max",50000));job(1028,row("salary_min",Integer.MAX_VALUE,"salary_max",Integer.MAX_VALUE));
        job(1029,row("experience","3–5年","education","硕士"));job(1999,row("published_at",LocalDateTime.of(2026,11,1,12,0)));
        // 已投递记录分别来自其它简历、其它用户和已拒绝终态，排除规则均按当前用户判断。
        insert("job_application",row("user_id",101,"job_id",1011,"resume_id",903,"status",4));
        insert("job_application",row("user_id",101,"job_id",1012,"resume_id",901,"status",0));
        insert("job_application",row("user_id",102,"job_id",1002,"resume_id",902,"status",0));
        Files.writeString(root.resolve("source-before.sha256"),before);Files.writeString(root.resolve("fixture-before.sha256"),fixtureFingerprint());
        Files.writeString(root.resolve("as-of-date.txt"),AS_OF.toString());
        if(!before.equals(fingerprint()))throw new IllegalStateException("Source changed during setup");
        System.out.println("Recommendation fixture ready: 30 jobs, 6 resumes; source unchanged.");
    }
    public static void main(String[] args) throws Exception {
        String mode=args[0];database=args[1];root=Path.of(args[2]).toAbsolutePath().normalize();
        if(!database.matches("smart_recruit_recommendation_query_[0-9]+")||!root.startsWith(Path.of("target").toAbsolutePath().normalize()))throw new IllegalArgumentException("Unsafe fixture target");
        Files.createDirectories(root);Map<?,?> cfg;
        try(var in=Files.newInputStream(Path.of("src/main/resources/application.yml"))){cfg=new Yaml().load(in);}
        var spring=(Map<?,?>)cfg.get("spring");var ds=(Map<?,?>)spring.get("datasource");
        try(var c=DriverManager.getConnection(ds.get("url").toString(),ds.get("username").toString(),ds.get("password").toString())) {
            connection=c;if(!SOURCE.equals(c.getCatalog()))throw new IllegalStateException("Unexpected source");
            if(mode.equals("setup")){setup();prepareRedis(spring);return;}
            c.setCatalog(database);
            if(mode.equals("redis-cleanup")){cleanupRedis(spring);return;}
            if(!mode.equals("audit"))throw new IllegalArgumentException("Unknown mode");
            String source=fingerprint(),fixture=fixtureFingerprint();
            if(!source.equals(Files.readString(root.resolve("source-before.sha256"))))throw new IllegalStateException("Source data changed");
            if(!fixture.equals(Files.readString(root.resolve("fixture-before.sha256"))))throw new IllegalStateException("Read endpoints changed fixture business data");
            if(!rows("SELECT trigger_name FROM information_schema.triggers WHERE trigger_schema=DATABASE()").isEmpty())throw new IllegalStateException("Unexpected fixture trigger");
            var result=Map.of("sourceUnchanged",true,"sourceSha256",source,"fixtureBusinessDataUnchanged",true,"fixtureSha256",fixture,"noTestTriggers",true);
            Files.writeString(root.resolve("audit.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(result));System.out.println(JSON.writeValueAsString(result));
        }
    }
}
