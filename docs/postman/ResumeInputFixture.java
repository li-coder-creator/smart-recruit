import java.nio.file.*;
import java.sql.*;
import java.security.MessageDigest;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.yaml.snakeyaml.Yaml;

/** Fixture DML and triggers are restricted to a new resume-input regression schema. */
public class ResumeInputFixture {
    static final String SOURCE="smart_recruit";
    static final ObjectMapper JSON=new ObjectMapper().findAndRegisterModules();
    static Connection connection;
    static String database;
    static Path root;
    static List<Map<String,Object>> rows(String sql) throws Exception {
        List<Map<String,Object>> result=new ArrayList<>();
        try(var s=connection.createStatement(); var rs=s.executeQuery(sql)) {
            var meta=rs.getMetaData();
            while(rs.next()) {
                Map<String,Object> row=new LinkedHashMap<>();
                for(int i=1;i<=meta.getColumnCount();i++) row.put(meta.getColumnLabel(i),rs.getObject(i));
                result.add(row);
            }
        }
        return result;
    }
    static void isolated(String sql) throws Exception {
        if(!database.equals(connection.getCatalog())) throw new IllegalStateException("Fixture write outside isolated schema");
        try(var s=connection.createStatement()) {s.execute(sql);}
    }
    static String fingerprint() throws Exception {
        var digest=MessageDigest.getInstance("SHA-256");
        for(var table:rows("SELECT table_name AS name FROM information_schema.tables WHERE table_schema='smart_recruit' AND table_type='BASE TABLE' ORDER BY table_name")) {
            String name=table.get("name").toString();
            if(!name.matches("[a-zA-Z0-9_]+")) throw new IllegalStateException("Invalid table name");
            var canonical=rows("SELECT * FROM smart_recruit.`"+name+"`").stream().map(row->{try{return JSON.writeValueAsString(row);}catch(Exception e){throw new RuntimeException(e);}}).sorted().toList();
            digest.update(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            digest.update(JSON.writeValueAsBytes(canonical));
        }
        return HexFormat.of().formatHex(digest.digest());
    }
    static String rollbackFacts() throws Exception {
        Map<String,Object> facts=new LinkedHashMap<>();
        facts.put("resume",rows("SELECT * FROM resume WHERE id=903"));
        facts.put("experiences",rows("SELECT * FROM resume_experience WHERE resume_id=903 ORDER BY id"));
        facts.put("skills",rows("SELECT * FROM resume_skill WHERE resume_id=903 ORDER BY id"));
        return JSON.writeValueAsString(facts);
    }
    static void setup() throws Exception {
        String before=fingerprint();
        if(!rows("SELECT schema_name FROM information_schema.schemata WHERE schema_name='"+database+"'").isEmpty()) throw new IllegalStateException("Refuse existing fixture schema");
        try(var s=connection.createStatement()) {s.execute("CREATE DATABASE `"+database+"` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");}
        connection.setCatalog(database);
        isolated("SET FOREIGN_KEY_CHECKS=0");
        try {
            for(var table:rows("SELECT table_name AS name FROM information_schema.tables WHERE table_schema='smart_recruit' AND table_type='BASE TABLE' ORDER BY table_name")) {
                String name=table.get("name").toString();
                if(!name.matches("[a-zA-Z0-9_]+")) throw new IllegalStateException("Invalid table");
                String ddl=rows("SHOW CREATE TABLE smart_recruit.`"+name+"`").get(0).get("Create Table").toString();
                if(ddl.contains("REFERENCES `smart_recruit`")) throw new IllegalStateException("Cross-schema FK requires review");
                isolated(ddl);
            }
        } finally {isolated("SET FOREIGN_KEY_CHECKS=1");}
        String password=System.getenv("RESUME_INPUT_FIXTURE_PASSWORD");
        if(password==null || !password.matches("[a-zA-Z0-9_]{10,26}")) throw new IllegalStateException("Synthetic password required");
        for(int i=1;i<=4;i++) {
            try(var s=connection.prepareStatement("INSERT INTO sys_user(id,username,password,role) VALUES (?,?,?,?)")) {
                s.setLong(1,100+i);s.setString(2,"rs_input_"+i);s.setString(3,com.recruit.smartrecruit.utils.Md5Util.encrypt(password));
                s.setString(4,i<=2?"JOB_SEEKER":i==3?"COMPANY":"ADMIN");s.executeUpdate();
            }
        }
        for(int resumeId:new int[]{901,902,903,910}) {
            isolated("INSERT INTO resume(id,user_id,title,completion_rate) VALUES ("+resumeId+","+(resumeId==910?102:101)+",'输入回归夹具',0)");
        }
        Files.writeString(root.resolve("source-before.sha256"),before);
        if(!before.equals(fingerprint())) throw new IllegalStateException("Source changed during fixture setup");
        System.out.println("Isolated resume fixture created; source unchanged.");
    }
    public static void main(String[] args) throws Exception {
        String mode=args[0];database=args[1];root=Path.of(args[2]).toAbsolutePath().normalize();
        if(!database.matches("smart_recruit_resume_inputs_[0-9]+")) throw new IllegalArgumentException("Unsafe fixture database");
        if(!root.startsWith(Path.of("target").toAbsolutePath().normalize())) throw new IllegalArgumentException("Unsafe evidence path");
        Files.createDirectories(root);
        Map<?,?> cfg;
        try(var in=Files.newInputStream(Path.of("src/main/resources/application.yml"))) {cfg=new Yaml().load(in);}
        var ds=(Map<?,?>)((Map<?,?>)cfg.get("spring")).get("datasource");
        try(var c=DriverManager.getConnection(ds.get("url").toString(),ds.get("username").toString(),ds.get("password").toString())) {
            connection=c;
            if(!SOURCE.equals(c.getCatalog())) throw new IllegalStateException("Unexpected source");
            if(mode.equals("setup")) {setup();return;}
            c.setCatalog(database);
            switch(mode) {
                case "baseline" -> Files.writeString(root.resolve("rollback-before.json"),rollbackFacts());
                case "fault-on" -> isolated("CREATE TRIGGER rs_completion_failure BEFORE UPDATE ON resume FOR EACH ROW BEGIN IF NEW.id=903 AND NEW.completion_rate<>OLD.completion_rate THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='RS_TEST_COMPLETION_FAILURE'; END IF; END");
                case "fault-off" -> isolated("DROP TRIGGER rs_completion_failure");
                case "verify-rollback" -> {
                    if(!Files.readString(root.resolve("rollback-before.json")).equals(rollbackFacts())) throw new IllegalStateException("Rollback facts differ");
                    System.out.println("Rollback verified: declarations, timestamps, experience, skills and completion unchanged.");
                }
                case "delay-on" -> {
                    isolated("CREATE TRIGGER rs_insert_delay BEFORE INSERT ON resume_experience FOR EACH ROW BEGIN IF NEW.resume_id=902 THEN DO SLEEP(1); END IF; END");
                    isolated("CREATE TRIGGER rs_delete_delay BEFORE DELETE ON resume_experience FOR EACH ROW BEGIN IF OLD.resume_id=902 THEN DO SLEEP(1); END IF; END");
                }
                case "delay-off" -> {isolated("DROP TRIGGER rs_insert_delay");isolated("DROP TRIGGER rs_delete_delay");}
                case "audit" -> {
                    String now=fingerprint();
                    if(!now.equals(Files.readString(root.resolve("source-before.sha256")))) throw new IllegalStateException("Source changed during regression");
                    var triggers=rows("SELECT trigger_name FROM information_schema.triggers WHERE trigger_schema=DATABASE()");
                    if(!triggers.isEmpty()) throw new IllegalStateException("Fixture triggers remain enabled");
                    if(!rows("SELECT r.id FROM resume r WHERE r.formal_experience_status='NONE' AND EXISTS(SELECT 1 FROM resume_experience e WHERE e.resume_id=r.id AND e.experience_type IN('FORMAL','UNKNOWN'))").isEmpty()) throw new IllegalStateException("Conflicting zero-experience declaration");
                    var result=Map.of("sourceUnchanged",true,"sourceSha256",now,"fixtureTriggersRemoved",true,"noneDeclarationConsistent",true);
                    Files.writeString(root.resolve("audit.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(result));
                    System.out.println(JSON.writeValueAsString(result));
                }
                default -> throw new IllegalArgumentException("Unknown fixture mode");
            }
        }
    }
}
