import java.nio.file.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import org.yaml.snakeyaml.Yaml;

/** All fault/delay/data writes are restricted to a fresh interview regression schema. */
public class InterviewFixture extends ApplicationEventFixture {
    static void seedInterviews() throws Exception {
        for(int i=1;i<=30;i++){
            var job=new HashMap<String,Object>();job.put("id",600+i);job.put("company_id",i==7?202:201);
            job.put("title","IV_"+(1000+i));job.put("status",i==12?2:i==13?1:0);job.put("deadline",null);
            cloneRow("job",job);
            int status=i==4?0:i==5||i==30?3:i==6?4:2;
            update("INSERT INTO job_application(id,user_id,job_id,resume_id,status,has_interviewed,history_recorded_from_start,create_time,update_time) VALUES(?,?,?,?,?,TRUE,FALSE,NOW(),NOW())",
                    1000+i,i==8?102:101,600+i,i==8?302:i==12?399999:301,status);
        }
        // Explicit old fixtures for ONGOING/EXPIRED/read filtering; never write these into source.
        seed(2002,1002,-30,90,"SCHEDULED");
        seed(2003,1003,-4320,-4260,"SCHEDULED");
        seed(2008,1008,-300,300,"SCHEDULED");
        seed(2010,1010,-60,120,"SCHEDULED");
        seed(2024,1024,-120,60,"SCHEDULED");
        seed(2025,1025,10,70,"SCHEDULED");
        // Identical starts use id as deterministic tie-breaker.
        update("INSERT INTO application_interview(id,application_id,start_time,end_time,mode,platform,meeting_url,status,version,created_by,updated_by) SELECT 2026,1026,start_time,end_time,mode,platform,meeting_url,status,0,103,103 FROM application_interview WHERE id=2025");
        seed(2030,1030,5,65,"SCHEDULED"); // wrong parent status, excluded from seeker next
        // Multiple pre-existing open rows test terminal cleanup and whole-transaction rollback.
        seed(20170,1017,-4320,-4260,"SCHEDULED");seed(20171,1017,1440,1500,"CANCELLED");
        seed(20172,1017,2880,2940,"SCHEDULED");seed(20173,1017,-30,120,"SCHEDULED");
        System.out.println("Interview fixtures ready: 30 applications, controlled old/past/ongoing arrangements.");
    }
    static void seed(long id,long app,int fromMinutes,int toMinutes,String status) throws Exception {
        update("INSERT INTO application_interview(id,application_id,start_time,end_time,mode,platform,meeting_url,status,version,created_by,updated_by,cancel_reason,cancel_source,cancelled_at) VALUES(?,?,TIMESTAMPADD(MINUTE,?,NOW(3)),TIMESTAMPADD(MINUTE,?,NOW(3)),'ONLINE','隔离平台','https://example.com/old-meeting',?,0,103,103,?,?,?)",
                id,app,fromMinutes,toMinutes,status,status.equals("CANCELLED")?"旧取消":null,
                status.equals("CANCELLED")?"COMPANY":null,status.equals("CANCELLED")?Timestamp.valueOf(LocalDateTime.now()):null);
    }
    static String interviewFacts() throws Exception {
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("applications",rows("SELECT * FROM job_application ORDER BY id"));
        out.put("interviews",rows("SELECT * FROM application_interview ORDER BY id"));
        out.put("events",rows("SELECT * FROM application_event ORDER BY id"));
        return JSON_TIME.writeValueAsString(out);
    }
    static void verify() throws Exception {
        if(!Files.readString(root.resolve("source-before.sha256")).equals(sourceFingerprint()))throw new IllegalStateException("Source changed");
        if(((Number)rows("SELECT COUNT(*) AS c FROM (SELECT application_id FROM application_interview WHERE status='SCHEDULED' AND end_time>NOW(3) GROUP BY application_id HAVING COUNT(*)>1) x").get(0).get("c")).longValue()!=0)throw new IllegalStateException("Duplicate effective arrangements");
        // 1030 is a deliberate old inconsistent parent used only to verify next-query filtering.
        if(((Number)rows("SELECT COUNT(*) AS c FROM application_interview i JOIN job_application a ON a.id=i.application_id WHERE a.status IN (3,4) AND a.id<>1030 AND i.status='SCHEDULED' AND i.end_time>NOW(3)").get(0).get("c")).longValue()!=0)throw new IllegalStateException("Effective arrangement left on ended flow");
        if(((Number)rows("SELECT COUNT(*) AS c FROM application_interview i WHERE i.version<>(SELECT COUNT(*) FROM application_event e WHERE e.interview_id=i.id AND e.event_type IN ('INTERVIEW_RESCHEDULED','INTERVIEW_CANCELLED'))").get(0).get("c")).longValue()!=0)throw new IllegalStateException("Version/event evidence mismatch");
        if(((Number)rows("SELECT COUNT(*) AS c FROM application_event e JOIN application_interview i ON i.id=e.interview_id WHERE e.application_id<>i.application_id").get(0).get("c")).longValue()!=0)throw new IllegalStateException("Cross-application event link");
        for(var e:rows("SELECT id,event_type,from_status,to_status,interview_id,detail_json FROM application_event WHERE interview_id IS NOT NULL ORDER BY id")){
            if(e.get("from_status")!=null||e.get("to_status")!=null)throw new IllegalStateException("Interview event has statuses");
            var detail=JSON_TIME.readTree(e.get("detail_json").toString());
            var names=detail.fieldNames();while(names.hasNext())if(!Set.of("previousStartTime","previousEndTime","startTime","endTime","previousMode","mode","cancelReason").contains(names.next()))throw new IllegalStateException("Private event JSON field");
        }
        var audit=new LinkedHashMap<String,Object>();audit.put("sourceUnchanged",true);audit.put("sourceSha256",sourceFingerprint());
        audit.put("singleEffectiveArrangementVerified",true);audit.put("terminalCleanupVerified",true);audit.put("versionEventConsistencyVerified",true);
        audit.put("interviews",rows("SELECT id,application_id,status,version,start_time,end_time,cancel_source,cancel_reason FROM application_interview ORDER BY id"));
        audit.put("events",rows("SELECT id,application_id,event_type,interview_id,from_status,to_status,actor_type FROM application_event ORDER BY id"));
        audit.put("applications",rows("SELECT id,status,history_recorded_from_start FROM job_application ORDER BY id"));
        audit.put("triggers",rows("SELECT TRIGGER_NAME FROM information_schema.triggers WHERE TRIGGER_SCHEMA=DATABASE()"));
        Files.writeString(root.resolve("interview-audit.json"),JSON_TIME.writerWithDefaultPrettyPrinter().writeValueAsString(audit));
        System.out.println("INTERVIEW AUDIT: source unchanged; JSON whitelist verified; report="+root.resolve("interview-audit.json"));
    }
    public static void main(String[] args) throws Exception {
        String mode=args[0];database=args[1];root=Path.of(args[2]).toAbsolutePath().normalize();
        if(!database.matches("smart_recruit_interviews_[0-9]+")||!root.startsWith(Path.of("target").toAbsolutePath().normalize()))throw new IllegalArgumentException("Unsafe isolated target");
        Files.createDirectories(root);
        Map<?,?> cfg;try(var in=Files.newInputStream(Path.of("src/main/resources/application.yml"))){cfg=new Yaml().load(in);}
        var ds=(Map<?,?>)((Map<?,?>)cfg.get("spring")).get("datasource");
        try(var db=DriverManager.getConnection(ds.get("url").toString(),ds.get("username").toString(),ds.get("password").toString())){
            c=db;if(!SOURCE.equals(c.getCatalog()))throw new IllegalStateException("Unexpected source");
            if(mode.equals("setup")){setup();seedInterviews();return;}c.setCatalog(database);
            if(!database.equals(c.getCatalog()))throw new IllegalStateException("Target mismatch");
            switch(mode){
                case "baseline" -> Files.writeString(root.resolve("interview-rollback-before.json"),interviewFacts());
                case "verify-rollback" -> {
                    if(!Files.readString(root.resolve("interview-rollback-before.json")).equals(interviewFacts()))throw new IllegalStateException("Rollback data differs");
                    System.out.println("ROLLBACK VERIFIED: applications, interview fields/versions and events unchanged");
                }
                case "fault-on" -> exec("CREATE TRIGGER iv_event_failure BEFORE INSERT ON application_event FOR EACH ROW BEGIN IF NEW.event_type IN ('INTERVIEW_SCHEDULED','INTERVIEW_RESCHEDULED') OR (NEW.event_type='INTERVIEW_CANCELLED' AND NEW.interview_id<>20172) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IV_TEST_EVENT_FAILURE'; END IF; END");
                case "fault-off" -> exec("DROP TRIGGER iv_event_failure");
                case "delay-on" -> exec("CREATE TRIGGER iv_event_delay BEFORE INSERT ON application_event FOR EACH ROW DO SLEEP(2)");
                case "delay-off" -> exec("DROP TRIGGER iv_event_delay");
                case "audit" -> verify();
                default -> throw new IllegalArgumentException("Unknown command");
            }
        }
    }
}
