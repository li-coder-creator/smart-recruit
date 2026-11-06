import java.nio.file.*;
import java.sql.*;
import java.security.MessageDigest;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.yaml.snakeyaml.Yaml;

/** Fixture DML and triggers are restricted to a new job-ability regression schema. */
public class JobAbilitiesFixture {
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
        facts.put("jobs",rows("SELECT * FROM job ORDER BY id"));
        facts.put("abilities",rows("SELECT * FROM job_required_ability ORDER BY id"));
        facts.put("activities",rows("SELECT * FROM job_activity ORDER BY id"));
        return JSON.writeValueAsString(facts);
    }
    static void insertJob(int id,int companyId) throws Exception {
        isolated("INSERT INTO job(id,company_id,title,required_skills,status) VALUES ("+id+","+companyId+",'夹具岗位','旧原文',3)");
    }
    static void prepareRedis(Map<?,?> spring) throws Exception {
        var cfg=(Map<?,?>)((Map<?,?>)spring.get("data")).get("redis");
        var uri=io.lettuce.core.RedisURI.Builder.redis(cfg.get("host").toString(),Integer.parseInt(cfg.get("port").toString()));
        if(cfg.get("password")!=null)uri.withPassword(cfg.get("password").toString().toCharArray());
        var client=io.lettuce.core.RedisClient.create(uri.build());
        try(var rc=client.connect()) {
            var commands=rc.sync();int chosen=-1;
            for(int index=15;index>=1;index--){commands.select(index);if(commands.dbsize()==0){chosen=index;break;}}
            if(chosen<0)throw new IllegalStateException("No empty isolated Redis database");
            String marker="ja:fixture:"+database;commands.set(marker,database);
            Files.writeString(root.resolve("redis.json"),JSON.writeValueAsString(Map.of("database",chosen,"marker",marker)));
        }finally{client.shutdown();}
    }
    static void cleanupRedis(Map<?,?> spring) throws Exception {
        var saved=JSON.readTree(Files.readString(root.resolve("redis.json")));
        var cfg=(Map<?,?>)((Map<?,?>)spring.get("data")).get("redis");
        var builder=io.lettuce.core.RedisURI.Builder.redis(cfg.get("host").toString(),Integer.parseInt(cfg.get("port").toString())).withDatabase(saved.get("database").asInt());
        if(cfg.get("password")!=null)builder.withPassword(cfg.get("password").toString().toCharArray());
        var client=io.lettuce.core.RedisClient.create(builder.build());
        try(var rc=client.connect()) {
            var cmd=rc.sync();if(!database.equals(cmd.get(saved.get("marker").asText())))throw new IllegalStateException("Redis ownership marker missing");
            var keys=cmd.keys("*");
            Set<String> ownIds=new HashSet<>(List.of("901","902","903","904","905","910"));
            for(var job:rows("SELECT id FROM job"))ownIds.add(job.get("id").toString());
            for(String key:keys) {
                if(key.equals(saved.get("marker").asText()))continue;
                String prefix="job:detail:content:v1:";
                if(!key.startsWith(prefix)||!ownIds.contains(key.substring(prefix.length())))
                    throw new IllegalStateException("Refuse cleanup of unrelated Redis key");
            }
            // 此库在setup时为空；逐一核对本夹具的岗位ID，只删除所有权明确的键。
            Files.writeString(root.resolve("redis-cleaned-keys.json"),JSON.writeValueAsString(keys));
            for(String key:keys)cmd.del(key);
            if(cmd.dbsize()!=0)throw new IllegalStateException("Unexpected concurrent Redis writer");
        }finally{client.shutdown();}
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
        String password=System.getenv("JOB_ABILITIES_FIXTURE_PASSWORD");
        if(password==null || !password.matches("[a-zA-Z0-9_]{10,26}")) throw new IllegalStateException("Synthetic password required");
        for(int i=1;i<=4;i++) {
            try(var s=connection.prepareStatement("INSERT INTO sys_user(id,username,password,role) VALUES (?,?,?,?)")) {
                s.setLong(1,100+i);s.setString(2,"ja_input_"+i);s.setString(3,com.recruit.smartrecruit.utils.Md5Util.encrypt(password));
                s.setString(4,i<=2||i==4?"COMPANY":"JOB_SEEKER");s.executeUpdate();
            }
        }
        for(int i:new int[]{1,2,4}) isolated("INSERT INTO company(id,user_id,name,status) VALUES ("+(200+i)+","+(100+i)+",'回归企业"+i+"','"+(i==4?"PENDING":"APPROVED")+"')");
        for(int id:new int[]{901,902,903,904,905})insertJob(id,201);
        insertJob(910,202);
        Files.writeString(root.resolve("source-before.sha256"),before);
        if(!before.equals(fingerprint())) throw new IllegalStateException("Source changed during fixture setup");
        System.out.println("Isolated job abilities fixture created; source unchanged.");
    }
    public static void main(String[] args) throws Exception {
        String mode=args[0];database=args[1];root=Path.of(args[2]).toAbsolutePath().normalize();
        if(!database.matches("smart_recruit_job_abilities_[0-9]+")) throw new IllegalArgumentException("Unsafe fixture database");
        if(!root.startsWith(Path.of("target").toAbsolutePath().normalize())) throw new IllegalArgumentException("Unsafe evidence path");
        Files.createDirectories(root);
        Map<?,?> cfg;
        try(var in=Files.newInputStream(Path.of("src/main/resources/application.yml"))) {cfg=new Yaml().load(in);}
        var ds=(Map<?,?>)((Map<?,?>)cfg.get("spring")).get("datasource");
        try(var c=DriverManager.getConnection(ds.get("url").toString(),ds.get("username").toString(),ds.get("password").toString())) {
            connection=c;
            if(!SOURCE.equals(c.getCatalog())) throw new IllegalStateException("Unexpected source");
            if(mode.equals("setup")) {setup();prepareRedis((Map<?,?>)cfg.get("spring"));return;}
            c.setCatalog(database);
            switch(mode) {
                case "baseline" -> Files.writeString(root.resolve("rollback-before.json"),rollbackFacts());
                case "fault-on" -> isolated("CREATE TRIGGER ja_activity_failure BEFORE INSERT ON job_activity FOR EACH ROW BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='JA_TEST_ACTIVITY_FAILURE'; END");
                case "fault-off" -> isolated("DROP TRIGGER ja_activity_failure");
                case "child-fault-on" -> isolated("CREATE TRIGGER ja_child_failure BEFORE INSERT ON job_required_ability FOR EACH ROW BEGIN IF NEW.raw_text='故障条目' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='JA_TEST_CHILD_FAILURE'; END IF; END");
                case "child-fault-off" -> isolated("DROP TRIGGER ja_child_failure");
                case "verify-rollback" -> {
                    if(!Files.readString(root.resolve("rollback-before.json")).equals(rollbackFacts()))throw new IllegalStateException("Rollback facts differ");
                    System.out.println("Rollback verified: jobs, abilities, versions, timestamps and activities unchanged.");
                }
                case "delay-on" -> isolated("CREATE TRIGGER ja_insert_delay BEFORE INSERT ON job_required_ability FOR EACH ROW BEGIN IF NEW.job_id=902 THEN DO SLEEP(1); END IF; END");
                case "delay-off" -> isolated("DROP TRIGGER ja_insert_delay");
                case "redis-cleanup" -> cleanupRedis((Map<?,?>)cfg.get("spring"));
                case "audit" -> {
                    String now=fingerprint();
                    if(!now.equals(Files.readString(root.resolve("source-before.sha256"))))throw new IllegalStateException("Source changed during regression");
                    if(!rows("SELECT trigger_name FROM information_schema.triggers WHERE trigger_schema=DATABASE()").isEmpty())throw new IllegalStateException("Fixture triggers remain enabled");
                    var jobs=rows("SELECT id,required_skills,required_abilities_source,required_abilities_version FROM job ORDER BY id");
                    for(var j:jobs)if("STRUCTURED".equals(j.get("required_abilities_source"))){
                        var items=rows("SELECT * FROM job_required_ability WHERE job_id="+j.get("id")+" ORDER BY sort_order,id");
                        String projection=items.isEmpty()?null:String.join("\n",items.stream().map(x->x.get("raw_text").toString()).toList());
                        if(!Objects.equals(projection,j.get("required_skills")))throw new IllegalStateException("Projection differs");
                        for(int i=0;i<items.size();i++)if(((Number)items.get(i).get("sort_order")).intValue()!=i)throw new IllegalStateException("Incorrect ordering");
                    }
                    if(!rows("SELECT a.id FROM job_required_ability a LEFT JOIN job j ON j.id=a.job_id WHERE j.id IS NULL").isEmpty())throw new IllegalStateException("Orphan abilities");
                    var result=Map.of("sourceUnchanged",true,"sourceSha256",now,"fixtureTriggersRemoved",true,"projectionsAndOrderingConsistent",true,"noOrphanAbilities",true);
                    Files.writeString(root.resolve("audit.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(result));
                    System.out.println(JSON.writeValueAsString(result));
                }
                default -> throw new IllegalArgumentException("Unknown fixture mode");
            }
        }
    }
}
