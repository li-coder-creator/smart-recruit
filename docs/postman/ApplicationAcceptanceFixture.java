import java.nio.file.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import org.yaml.snakeyaml.Yaml;

/** Stage five fixtures. All writes are limited to a newly created acceptance schema. */
public class ApplicationAcceptanceFixture extends ApplicationEventFixture {
    static final LocalDateTime NOW = LocalDateTime.parse(ApplicationAcceptanceClock.LOCAL_NOW);
    static void application(long id, long user, long job, long resume, int status, LocalDateTime created) throws Exception {
        update("INSERT INTO job_application(id,user_id,job_id,resume_id,status,has_interviewed,history_recorded_from_start,create_time,update_time) VALUES(?,?,?,?,?,FALSE,FALSE,?,?)",
                id,user,job,resume,status,Timestamp.valueOf(created),Timestamp.valueOf(created));
    }
    static void job(long id,long company,int status,String title) throws Exception {
        var replacement = new HashMap<String,Object>();
        replacement.put("id",id);replacement.put("company_id",company);replacement.put("status",status);
        replacement.put("title",title);replacement.put("deadline",null);cloneRow("job",replacement);
    }
    static void seedAcceptance() throws Exception {
        Object passwordHash = rows("SELECT password FROM sys_user WHERE id=101").get(0).get("password");
        for (int i : new int[]{8,9}) {
            var user = new HashMap<String,Object>();
            user.put("id",100+i);user.put("username","ap_event_"+i);user.put("role","JOB_SEEKER");
            user.put("password",passwordHash);user.put("phone",null);user.put("email",null);
            user.put("nickname","阶段五合成身份"+i);cloneRow("sys_user",user);
        }
        var rejected = new HashMap<String,Object>();
        rejected.put("id",204);rejected.put("user_id",109);rejected.put("name","PH5_REJECTED_COMPANY");
        rejected.put("status","REJECTED");rejected.put("logo","   ");cloneRow("company",rejected);
        for (int i=0;i<2;i++) {
            var resume = new HashMap<String,Object>();
            resume.put("id",303+i);resume.put("user_id",107);resume.put("title",i==0?"":"PH5_MATRIX_RESUME");
            resume.put("phone",null);resume.put("email",null);cloneRow("resume",resume);
        }
        // Old orphan fixtures are deliberately restricted to the isolated schema.
        exec("SET FOREIGN_KEY_CHECKS=0");
        try {
            LocalDateTime seven = NOW.toLocalDate().minusDays(6).atStartOfDay();
            LocalDateTime thirty = NOW.toLocalDate().minusDays(29).atStartOfDay();
            LocalDateTime[] times = {seven.minusSeconds(1),seven,seven.plusSeconds(1),
                    thirty.minusSeconds(1),thirty,thirty.plusSeconds(1),NOW.minusSeconds(1),NOW,NOW.plusSeconds(1),
                    NOW.minusDays(2),NOW.minusDays(2),NOW.minusDays(2)};
            for (int i=0;i<times.length;i++) {
                job(6101+i,201,0,"PH5_BOUNDARY_"+(2101+i));
                application(2101+i,102,6101+i,302,i%5,times[i]);
            }
            long[] companies = {201,203,204,299999};
            for(int state=0;state<4;state++) for(int company=0;company<4;company++) {
                int index=state*4+company;
                job(7001+index,companies[company],state,"PH5_MATRIX_"+(3001+index));
                application(3001+index,107,7001+index,304,index%5,NOW.minusDays(1));
            }
            application(3017,107,799999,304,0,NOW.minusDays(1));
            for(int i=18;i<=22;i++) {
                job(7000+i,201,0,"PH5_MATRIX_"+(3000+i));
                long resume=i==18?399999:i==19?302:i==20?303:304;
                application(3000+i,107,7000+i,resume,(i-1)%5,NOW.minusDays(1));
            }
            update("UPDATE job SET salary_min=NULL,salary_max=NULL,negotiable=TRUE WHERE id=7021");
            update("UPDATE job SET salary_min=5000,salary_max=9000,negotiable=FALSE,salary_unit='MONTH' WHERE id=7022");
            // Dirty-state rejection must not poison the normal company's legacy list.
            job(7901,204,0,"PH5_INVALID_STATUS");application(3901,109,7901,301,9,NOW.minusDays(1));
        } finally { exec("SET FOREIGN_KEY_CHECKS=1"); }
        for(int i=1;i<=6;i++) job(4500+i,i==6?202:201,0,"PH5_FLOW_"+(4500+i));
        var report = new LinkedHashMap<String,Object>();
        report.put("clock",ApplicationAcceptanceClock.LOCAL_NOW);report.put("timeZone","Asia/Shanghai");
        report.put("boundaryRows",rows("SELECT id,status,create_time FROM job_application WHERE user_id=102 ORDER BY create_time DESC,id DESC"));
        report.put("matrixRows",rows("SELECT id,job_id,resume_id,status FROM job_application WHERE user_id=107 ORDER BY id"));
        report.put("initialApplications",rows("SELECT COUNT(*) AS count FROM job_application"));
        Files.writeString(root.resolve("fixture-summary.json"),JSON_TIME.writerWithDefaultPrettyPrinter().writeValueAsString(report));
        if(!Files.readString(root.resolve("source-before.sha256")).equals(sourceFingerprint())) throw new IllegalStateException("Source changed");
        System.out.println("Acceptance fixtures: fixed Shanghai clock; 12 boundary rows; 22 association cases; 6 API-only flow jobs; source unchanged.");
    }
    static void check(boolean condition,String message) { if(!condition) throw new IllegalStateException(message); }
    static LocalDateTime localTime(Object value) {
        if (value instanceof LocalDateTime local) return local;
        if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime();
        throw new IllegalStateException("Unexpected JDBC date-time type: " + (value == null ? "null" : value.getClass().getName()));
    }
    static void verifyAcceptance() throws Exception {
        check(Files.readString(root.resolve("source-before.sha256")).equals(sourceFingerprint()),"Source changed");
        var flows=rows("SELECT id,user_id,job_id,status,has_interviewed,history_recorded_from_start FROM job_application WHERE job_id BETWEEN 4501 AND 4506 ORDER BY job_id");
        check(flows.size()==6,"Expected six real API-created applications");
        int[] targets={3,4,3,4,2,2};
        int[] expectedEvents={9,5,2,2,3,3};
        int[] expectedInterviews={2,1,0,0,1,1};
        for(int index=0;index<flows.size();index++) {
            var flow=flows.get(index);long id=((Number)flow.get("id")).longValue();
            check(((Number)flow.get("status")).intValue()==targets[index],"Final flow status");
            check(Boolean.TRUE.equals(flow.get("history_recorded_from_start")),"New history incomplete");
            check(Objects.equals(flow.get("has_interviewed"),index!=2&&index!=3),"Interview evidence");
            var events=rows("SELECT id,event_type,from_status,to_status,actor_type,occurred_at,detail_json FROM application_event WHERE application_id="+id+" ORDER BY id");
            check(events.size()==expectedEvents[index],"Unexpected flow events job="+flow.get("job_id"));
            check(events.stream().filter(x->"APPLIED".equals(x.get("event_type"))).count()==1,"Initial event");
            check(events.get(0).get("event_type").equals("APPLIED"),"First event");
            check(events.stream().allMatch(x->NOW.equals(localTime(x.get("occurred_at")))),"Fixed clock not used by write path");
            var interviews=rows("SELECT id,status,version FROM application_interview WHERE application_id="+id+" ORDER BY id");
            check(interviews.size()==expectedInterviews[index],"Unexpected flow interviews");
            for(var interview:interviews) {
                long interviewId=((Number)interview.get("id")).longValue();
                long changes=((Number)rows("SELECT COUNT(*) AS count FROM application_event WHERE interview_id="+interviewId+" AND event_type IN ('INTERVIEW_RESCHEDULED','INTERVIEW_CANCELLED')").get(0).get("count")).longValue();
                check(((Number)interview.get("version")).longValue()==changes,"Version and event mismatch");
                check(interview.get("status").equals(index<4?"CANCELLED":"SCHEDULED"),"Final interview status");
            }
        }
        check(((Number)rows("SELECT COUNT(*) AS count FROM job_application WHERE job_id NOT BETWEEN 4501 AND 4506 AND history_recorded_from_start=TRUE").get(0).get("count")).intValue()==0,"Legacy marker changed");
        check(((Number)rows("SELECT COUNT(*) AS count FROM application_interview i JOIN job_application a ON a.id=i.application_id WHERE a.status IN (3,4) AND i.status='SCHEDULED' AND i.end_time>'"+NOW+"'").get(0).get("count")).intValue()==0,"Terminal arrangement remains");
        check(((Number)rows("SELECT COUNT(*) AS count FROM (SELECT application_id FROM application_interview WHERE status='SCHEDULED' AND end_time>'"+NOW+"' GROUP BY application_id HAVING COUNT(*)>1) t").get(0).get("count")).intValue()==0,"Duplicate effective arrangement");
        var triggers=rows("SELECT TRIGGER_NAME FROM information_schema.triggers WHERE TRIGGER_SCHEMA=DATABASE()");check(triggers.isEmpty(),"Unexpected test triggers");
        var audit=new LinkedHashMap<String,Object>();audit.put("database",database);audit.put("sourceUnchanged",true);audit.put("sourceSha256",sourceFingerprint());
        audit.put("clock",ApplicationAcceptanceClock.LOCAL_NOW);audit.put("flows",flows);audit.put("initialEventAndHistoryVerified",true);
        audit.put("versionEventConsistencyVerified",true);audit.put("terminalCleanupVerified",true);audit.put("triggers",triggers);
        audit.put("interviews",rows("SELECT id,application_id,status,version,cancel_source FROM application_interview ORDER BY id"));
        audit.put("events",rows("SELECT id,application_id,event_type,from_status,to_status,interview_id,actor_type FROM application_event ORDER BY id"));
        audit.put("queryIndexes",rows("SHOW INDEX FROM job_application WHERE Key_name IN ('idx_application_user_time','idx_application_user_status_time')"));
        Files.writeString(root.resolve("acceptance-audit.json"),JSON_TIME.writerWithDefaultPrettyPrinter().writeValueAsString(audit));
        System.out.println("ACCEPTANCE AUDIT: six real flows, complete initial histories, versions/events and terminal cleanup verified; source unchanged; no triggers.");
    }
    public static void main(String[] args) throws Exception {
        String mode=args[0];database=args[1];root=Path.of(args[2]).toAbsolutePath().normalize();
        if(!database.matches("smart_recruit_acceptance_[0-9]+")||!root.startsWith(Path.of("target").toAbsolutePath().normalize())) throw new IllegalArgumentException("Unsafe isolated target");
        Files.createDirectories(root);
        Map<?,?> config;try(var input=Files.newInputStream(Path.of("src/main/resources/application.yml"))){config=new Yaml().load(input);}
        var ds=(Map<?,?>)((Map<?,?>)config.get("spring")).get("datasource");
        try(var connection=DriverManager.getConnection(ds.get("url").toString(),ds.get("username").toString(),ds.get("password").toString())) {
            c=connection;if(!SOURCE.equals(c.getCatalog())) throw new IllegalStateException("Unexpected source");
            if(mode.equals("setup")){setup();seedAcceptance();return;}
            c.setCatalog(database);if(!database.equals(c.getCatalog())) throw new IllegalStateException("Wrong target");
            if(mode.equals("audit")){verifyAcceptance();return;}
            throw new IllegalArgumentException("Unknown command");
        }
    }
}
