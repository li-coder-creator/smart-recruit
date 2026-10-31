import java.nio.file.*;
import java.sql.*;
import java.security.MessageDigest;
import java.util.*;
import org.yaml.snakeyaml.Yaml;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Only writes a fresh, explicitly named isolated schema. Source data is fingerprinted, never updated. */
public class ApplicationEventFixture {
    static final String SOURCE="smart_recruit";
    static Connection c;
    static void exec(String sql) throws Exception { try(var s=c.createStatement()){s.execute(sql);} }
    static int update(String sql,Object...args) throws Exception {
        try(var s=c.prepareStatement(sql)){for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);return s.executeUpdate();}
    }
    static List<Map<String,Object>> rows(String sql) throws Exception {
        List<Map<String,Object>> out=new ArrayList<>();
        try(var s=c.createStatement();var r=s.executeQuery(sql)){
            var m=r.getMetaData();while(r.next()){
                Map<String,Object> row=new LinkedHashMap<>();
                for(int i=1;i<=m.getColumnCount();i++)row.put(m.getColumnLabel(i),r.getObject(i));
                out.add(row);
            }
        }
        return out;
    }
    static void cloneRow(String table,Map<String,Object> replacement) throws Exception {
        if(!Set.of("sys_user","company","resume","job").contains(table))throw new IllegalArgumentException("Unexpected template table");
        List<String> columns=new ArrayList<>(),select=new ArrayList<>();List<Object> args=new ArrayList<>();
        for(var row:rows("SHOW COLUMNS FROM smart_recruit.`"+table+"`")){
            String name=row.get("Field").toString();columns.add("`"+name+"`");
            if(replacement.containsKey(name)){select.add("?");args.add(replacement.get(name));}else select.add("`"+name+"`");
        }
        if(update("INSERT INTO `"+table+"` ("+String.join(",",columns)+") SELECT "+String.join(",",select)
                +" FROM smart_recruit.`"+table+"` ORDER BY id LIMIT 1",args.toArray())!=1)throw new IllegalStateException("Source template missing");
    }
    static String database;
    static Path root;
    static final ObjectMapper JSON_TIME=new ObjectMapper().findAndRegisterModules();
    static String sourceFingerprint() throws Exception {
        var digest=MessageDigest.getInstance("SHA-256");
        for(var t:rows("SELECT TABLE_NAME FROM information_schema.tables WHERE TABLE_SCHEMA='smart_recruit' AND TABLE_TYPE='BASE TABLE' ORDER BY TABLE_NAME")) {
            String name=t.get("TABLE_NAME").toString();
            if(!name.matches("[a-zA-Z0-9_]+"))throw new IllegalStateException("Unexpected source table");
            var values=rows("SELECT * FROM smart_recruit.`"+name+"`");
            var canonical=values.stream().map(row->{try{return JSON_TIME.writeValueAsString(row);}catch(Exception e){throw new RuntimeException(e);}}).sorted().toList();
            digest.update(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            digest.update(JSON_TIME.writeValueAsBytes(canonical));
        }
        return HexFormat.of().formatHex(digest.digest());
    }
    static void setup() throws Exception {
        String before=sourceFingerprint();
        exec("CREATE DATABASE `"+database+"` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");c.setCatalog(database);
        exec("SET FOREIGN_KEY_CHECKS=0");
        try {
            for(var t:rows("SELECT TABLE_NAME FROM information_schema.tables WHERE TABLE_SCHEMA='smart_recruit' AND TABLE_TYPE='BASE TABLE' ORDER BY TABLE_NAME")){
                String table=t.get("TABLE_NAME").toString();
                exec(rows("SHOW CREATE TABLE smart_recruit.`"+table+"`").get(0).get("Create Table").toString());
            }
        } finally {exec("SET FOREIGN_KEY_CHECKS=1");}
        String password=System.getenv("APPLICATION_FIXTURE_PASSWORD");
        if(password==null||password.length()<10)throw new IllegalStateException("Synthetic fixture password required");
        for(int i=1;i<=7;i++) {
            var r=new HashMap<String,Object>();r.put("id",100+i);r.put("username","ap_event_"+i);
            r.put("role",i<=2||i==7?"JOB_SEEKER":i==6?"ADMIN":"COMPANY");
            r.put("password",com.recruit.smartrecruit.utils.Md5Util.encrypt(password));
            r.put("nickname","投递事件测试身份"+i);r.put("phone",null);r.put("email",null);cloneRow("sys_user",r);
        }
        for(int i=1;i<=3;i++){
            var r=new HashMap<String,Object>();r.put("id",200+i);r.put("user_id",102+i);r.put("name","投递事件隔离企业"+i);
            r.put("status",i==3?"PENDING":"APPROVED");r.put("logo",null);cloneRow("company",r);
        }
        for(int i=1;i<=2;i++){
            var r=new HashMap<String,Object>();r.put("id",300+i);r.put("user_id",100+i);r.put("title","投递事件隔离简历"+i);
            r.put("real_name","回归求职者"+i);r.put("phone",null);r.put("email",null);cloneRow("resume",r);
        }
        for(int i=1;i<=25;i++){
            var r=new HashMap<String,Object>();r.put("id",400+i);r.put("company_id",i==21?202:i==22?203:201);
            r.put("title","APE_"+(400+i)+(i==16?" 百分%_!":""));r.put("status",i>=23?i-22:0);
            r.put("deadline",null);r.put("city","上海");cloneRow("job",r);
        }
        int[] job={411,412,413,414,415,416};int[] status={0,2,0,3,4,1};
        for(int i=0;i<job.length;i++)update("INSERT INTO job_application(id,user_id,job_id,resume_id,status,has_interviewed,history_recorded_from_start,create_time,update_time) VALUES(?,?,?,?,?,?,FALSE,DATE_SUB(NOW(),INTERVAL 45 DAY),DATE_SUB(NOW(),INTERVAL 45 DAY))",
                501+i,i==2?102:101,job[i],i==0?399999:i==2?302:301,status[i],i<=3?null:false);
        Files.writeString(root.resolve("source-before.sha256"),before);
        if(!before.equals(sourceFingerprint()))throw new IllegalStateException("Source changed during setup");
        System.out.println("Created isolated schema "+database+"; 7 synthetic identities; 6 legacy fixtures; source unchanged.");
    }
    static String facts() throws Exception {
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("applications",rows("SELECT * FROM job_application ORDER BY id"));
        out.put("events",rows("SELECT * FROM application_event ORDER BY id"));
        return JSON_TIME.writeValueAsString(out);
    }
    static void audit() throws Exception {
        if(!Files.readString(root.resolve("source-before.sha256")).equals(sourceFingerprint()))throw new IllegalStateException("Source schema data changed");
        long invalid=((Number)rows("SELECT COUNT(*) AS c FROM job_application a WHERE a.history_recorded_from_start=TRUE AND (SELECT COUNT(*) FROM application_event e WHERE e.application_id=a.id AND e.event_type='APPLIED')<>1").get(0).get("c")).longValue();
        if(invalid!=0)throw new IllegalStateException("Initial event invariant failed");
        var events=rows("SELECT e.id,e.application_id,e.event_type,e.from_status,e.to_status,e.actor_type,e.operator_user_id FROM application_event e ORDER BY e.id");
        var apps=rows("SELECT id,job_id,status,has_interviewed,history_recorded_from_start,create_time,update_time FROM job_application ORDER BY id");
        if(apps.size()!=13||events.size()!=17)throw new IllegalStateException("Unexpected final fixture row/event counts");
        long complete=apps.stream().filter(x->Boolean.TRUE.equals(x.get("history_recorded_from_start"))).count();
        if(complete!=7)throw new IllegalStateException("New/legacy history marker mismatch");
        for(var a:apps){
            int job=((Number)a.get("job_id")).intValue(),status=((Number)a.get("status")).intValue();
            Object evidence=a.get("has_interviewed");
            boolean shouldBeTrue=Set.of(401,405,411,412).contains(job)||(job==406&&status==2);
            boolean shouldBeNull=Set.of(413,414).contains(job);
            if(shouldBeNull?evidence!=null:!Objects.equals(evidence,shouldBeTrue))throw new IllegalStateException("Interview evidence mismatch job="+job);
            if(job>=411&&Boolean.TRUE.equals(a.get("history_recorded_from_start")))throw new IllegalStateException("Legacy history was upgraded");
        }
        for(var e:events){
            String type=e.get("event_type").toString();
            if(type.equals("STATUS_CHANGED")){
                var from=com.recruit.smartrecruit.entity.enums.ApplicationStatus.fromCode(((Number)e.get("from_status")).intValue());
                var to=com.recruit.smartrecruit.entity.enums.ApplicationStatus.fromCode(((Number)e.get("to_status")).intValue());
                if(!from.canTransitionTo(to))throw new IllegalStateException("Illegal state event");
            }
            long actor=((Number)e.get("operator_user_id")).longValue();
            if(type.equals("APPLIED")?(actor!=101||!"JOB_SEEKER".equals(e.get("actor_type"))):(actor!=103||!"COMPANY".equals(e.get("actor_type"))))throw new IllegalStateException("Event actor evidence mismatch");
        }
        Map<String,Object> report=new LinkedHashMap<>();report.put("database",database);report.put("sourceUnchanged",true);
        report.put("sourceSha256",sourceFingerprint());report.put("newHistoryCompleteCount",complete);report.put("interviewEvidenceVerified",true);report.put("applications",apps);report.put("events",events);
        report.put("triggers",rows("SELECT TRIGGER_NAME FROM information_schema.triggers WHERE TRIGGER_SCHEMA=DATABASE()"));
        Files.writeString(root.resolve("audit.json"),JSON_TIME.writerWithDefaultPrettyPrinter().writeValueAsString(report));
        System.out.println("AUDIT: source unchanged; applications="+apps.size()+" events="+events.size()+" initial-event invariants hold; report="+root.resolve("audit.json"));
    }
    public static void main(String[] args) throws Exception {
        String mode=args[0];database=args[1];root=Path.of(args[2]).toAbsolutePath().normalize();
        if(!database.matches("smart_recruit_application_events_[0-9]+"))throw new IllegalArgumentException("Unsafe target schema");
        if(!root.startsWith(Path.of("target").toAbsolutePath().normalize()))throw new IllegalArgumentException("Unsafe evidence folder");
        Files.createDirectories(root);
        Map<?,?> cfg;try(var input=Files.newInputStream(Path.of("src/main/resources/application.yml"))){cfg=new Yaml().load(input);}
        var ds=(Map<?,?>)((Map<?,?>)cfg.get("spring")).get("datasource");
        try(var db=DriverManager.getConnection(ds.get("url").toString(),ds.get("username").toString(),ds.get("password").toString())){
            c=db;if(!SOURCE.equals(c.getCatalog()))throw new IllegalStateException("Unexpected source");
            if(mode.equals("setup")){setup();return;}c.setCatalog(database);
            if(!database.equals(c.getCatalog()))throw new IllegalStateException("Target mismatch");
            switch(mode){
                case "baseline" -> {Files.writeString(root.resolve("rollback-before.json"),facts());System.out.println("Saved rollback baseline");}
                case "fault-on" -> exec("CREATE TRIGGER ap_event_failure BEFORE INSERT ON application_event FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='AP_TEST_EVENT_FAILURE'");
                case "fault-off" -> exec("DROP TRIGGER ap_event_failure");
                case "verify-rollback" -> {if(!Files.readString(root.resolve("rollback-before.json")).equals(facts()))throw new IllegalStateException("Rollback facts differ");System.out.println("ROLLBACK VERIFIED: applications, timestamps, interview evidence and events unchanged");}
                case "delay-on" -> exec("CREATE TRIGGER ap_event_delay BEFORE INSERT ON application_event FOR EACH ROW DO SLEEP(2)");
                case "delay-off" -> exec("DROP TRIGGER ap_event_delay");
                case "audit" -> audit();
                default -> throw new IllegalArgumentException("Unknown fixture command");
            }
        }
    }
}
