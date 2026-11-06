import com.fasterxml.jackson.databind.ObjectMapper;
import org.yaml.snakeyaml.Yaml;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;

/** Reviewed six-statement migration runner. Backups contain private data: keep storage/ ignored. */
public class RecommendationInputMigration {
    static final String SOURCE = "smart_recruit";
    static final String ISOLATED = "smart_recruit_recommendation_migration_20261031";
    static final String LOCK = "smart_recruit.recommendation.inputs.20261031";
    static final List<String> TABLES = List.of("resume", "resume_experience", "resume_skill", "job_preference", "job");
    static final Map<String, List<String>> NEW_COLUMNS = Map.of(
            "resume", List.of("career_stage", "formal_experience_status"),
            "resume_experience", List.of("experience_type", "is_current"),
            "resume_skill", List.of("proficiency"), "job_preference", List.of("direction_code"),
            "job", List.of("direction_code", "required_abilities_source", "required_abilities_version"));
    static final Path BACKUP = Path.of("storage/migration-backups/2026-10-31-recommendation-inputs");
    static final ObjectMapper JSON = new ObjectMapper();
    static final Map<String, Object> REPORT = new LinkedHashMap<>();

    static String id(String name) {
        if (!name.matches("[a-z0-9_]+")) throw new IllegalArgumentException("Unsafe identifier");
        return "`" + name + "`";
    }
    static List<Map<String,Object>> query(Connection c, String sql) throws Exception {
        List<Map<String,Object>> rows = new ArrayList<>();
        try (Statement s=c.createStatement()) {
            s.setQueryTimeout(20);
            try(ResultSet rs=s.executeQuery(sql)) {
                ResultSetMetaData m=rs.getMetaData();
                while(rs.next()) {
                    Map<String,Object> row=new LinkedHashMap<>();
                    for(int i=1;i<=m.getColumnCount();i++) {
                        Object v=rs.getObject(i);
                        row.put(m.getColumnLabel(i), v instanceof java.util.Date || v instanceof java.time.temporal.TemporalAccessor ? v.toString() : v);
                    }
                    rows.add(row);
                }
            }
        }
        return rows;
    }
    static long scalar(Connection c, String sql) throws Exception {
        return ((Number) query(c,sql).get(0).values().iterator().next()).longValue();
    }
    static void execute(Connection c, String sql) throws Exception {
        try(Statement s=c.createStatement()) {s.setQueryTimeout(30); s.execute(sql);}
    }
    static void require(boolean condition, String message) {
        if(!condition) throw new IllegalStateException(message);
    }
    static List<String> columns(Connection c, String table) throws Exception {
        List<String> out=new ArrayList<>();
        for(var row:query(c,"SELECT column_name AS columnName FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='"+table+"' ORDER BY ordinal_position"))
            out.add(row.get("columnName").toString());
        return out;
    }
    static List<Map<String,Object>> rows(Connection c, String table, List<String> columns) throws Exception {
        return query(c,"SELECT "+String.join(",",columns.stream().map(RecommendationInputMigration::id).toList())+" FROM "+id(table)+" ORDER BY id");
    }
    static void preflight(Connection c) throws Exception {
        for(String table:TABLES) {
            List<String> cols=columns(c,table);
            require(cols.contains("id"),"Missing parent table: "+table);
            for(String col:NEW_COLUMNS.get(table)) require(!cols.contains(col),"Migration state differs: "+table+"."+col);
        }
        require(scalar(c,"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='job_required_ability'")==0,"Ability table already exists");
        require(query(c,"SELECT column_type FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='job' AND column_name='id'").get(0).values().iterator().next().toString().matches("bigint(\\(20\\))?"),"Unexpected signed job.id type");
    }
    static String hash(Object value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JSON.writeValueAsBytes(value)));
    }
    static void saveReport() throws Exception {
        Files.writeString(BACKUP.resolve("manifest.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(REPORT));
    }
    static void checkDefaults(Connection c) throws Exception {
        for(String sql:List.of(
                "SELECT COUNT(*) FROM resume WHERE career_stage<>'UNKNOWN' OR formal_experience_status<>'UNKNOWN'",
                "SELECT COUNT(*) FROM resume_experience WHERE experience_type<>'UNKNOWN' OR is_current IS NOT NULL",
                "SELECT COUNT(*) FROM resume_skill WHERE proficiency IS NOT NULL",
                "SELECT COUNT(*) FROM job_preference WHERE direction_code IS NOT NULL",
                "SELECT COUNT(*) FROM job WHERE direction_code IS NOT NULL OR required_abilities_source<>'LEGACY_TEXT' OR required_abilities_version<>0",
                "SELECT COUNT(*) FROM job_required_ability")) require(scalar(c,sql)==0,"Unexpected migration defaults");
        require(scalar(c,"SELECT COUNT(*) FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE() AND table_name='job_required_ability' AND referenced_table_name='job' AND delete_rule='CASCADE'")==1,"Missing cascade foreign key");
    }
    static void verifyRows(Connection c, Map<String,List<String>> oldColumns, Map<String,List<Map<String,Object>>> before) throws Exception {
        for(String table:TABLES) require(before.get(table).equals(rows(c,table,oldColumns.get(table))),"Old-column data changed: "+table);
    }
    static String sqlValue(Object v) {
        if(v==null) return "NULL";
        if(v instanceof Number) return v.toString();
        if(v instanceof Boolean b) return b?"1":"0";
        return "CONVERT(X'"+HexFormat.of().formatHex(v.toString().getBytes(StandardCharsets.UTF_8))+"' USING utf8mb4)";
    }
    public static void main(String[] args) throws Exception {
        require(args.length==1 && args[0].equals("--apply"),"Use --apply for the authorized migration");
        require(!Files.exists(BACKUP),"Backup directory exists; inspect it instead of repeating migration");
        Map<?,?> config;
        try(var in=Files.newInputStream(Path.of("src/main/resources/application.yml"))) {config=new Yaml().load(in);}
        Map<?,?> ds=(Map<?,?>)((Map<?,?>)config.get("spring")).get("datasource");
        try(Connection c=DriverManager.getConnection(ds.get("url").toString(),ds.get("username").toString(),ds.get("password").toString())) {
            require(SOURCE.equals(c.getCatalog()),"Unexpected source database");
            require(query(c,"SELECT VERSION()").get(0).values().iterator().next().toString().equals("8.0.13"),"Review server version before execution");
            require(scalar(c,"SELECT GET_LOCK('"+LOCK+"',0)")==1,"Another migration owns the lock");
            try {
                preflight(c);
                require(scalar(c,"SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='"+ISOLATED+"'")==0,"Isolated schema already exists; do not overwrite");
                require(scalar(c,"SELECT COUNT(*) FROM information_schema.processlist WHERE db=DATABASE() AND id<>CONNECTION_ID() AND command<>'Sleep'")==0,"Other database operations active");
                Files.createDirectories(BACKUP.getParent());
                Files.createDirectory(BACKUP);
                REPORT.put("date","2026-10-31"); REPORT.put("source",SOURCE); REPORT.put("isolatedSchema",ISOLATED);
                REPORT.put("status","BACKUP_STARTED"); saveReport();
                Map<String,List<String>> oldColumns=new LinkedHashMap<>();
                Map<String,List<Map<String,Object>>> before=new LinkedHashMap<>();
                Map<String,String> ddls=new LinkedHashMap<>();
                c.setReadOnly(true); c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ); c.setAutoCommit(false);
                for(String table:TABLES) {
                    oldColumns.put(table,columns(c,table)); before.put(table,rows(c,table,oldColumns.get(table)));
                    ddls.put(table,query(c,"SHOW CREATE TABLE "+id(table)).get(0).get("Create Table").toString());
                }
                c.rollback(); c.setAutoCommit(true); c.setReadOnly(false);
                Files.writeString(BACKUP.resolve("old-rows.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(before));
                Files.writeString(BACKUP.resolve("old-columns.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(oldColumns));
                Files.writeString(BACKUP.resolve("schema-before.sql"),String.join(";\n\n",ddls.values())+";\n");
                StringBuilder inserts=new StringBuilder("-- Private backup; do not import over existing rows or commit this file.\n");
                for(String table:TABLES) for(var row:before.get(table)) {
                    inserts.append("INSERT INTO ").append(id(table)).append(" (").append(String.join(",",oldColumns.get(table).stream().map(RecommendationInputMigration::id).toList())).append(") VALUES (");
                    inserts.append(String.join(",",row.values().stream().map(RecommendationInputMigration::sqlValue).toList())).append(");\n");
                }
                Files.writeString(BACKUP.resolve("data-before.sql"),inserts);
                Map<String,Object> fingerprints=new LinkedHashMap<>();
                for(String table:TABLES) fingerprints.put(table,Map.of("rows",before.get(table).size(),"sha256",hash(before.get(table))));
                REPORT.put("oldDataFingerprints",fingerprints); REPORT.put("status","BACKUP_COMPLETE"); saveReport();
                String script=Files.readString(Path.of("docs/sql/2026-10-25-job-recommendation.draft.sql")).replace("\r\n","\n");
                String ddl=script.substring(script.indexOf("ALTER TABLE resume\n"),script.indexOf("-- C."));
                List<String> steps=Arrays.stream(ddl.split(";")).map(String::strip).filter(x->!x.isEmpty()).toList();
                require(steps.size()==6,"Expected five ALTER and one CREATE statements");
                execute(c,"CREATE DATABASE "+id(ISOLATED)+" CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
                for(String table:TABLES) {
                    execute(c,"CREATE TABLE "+id(ISOLATED)+"."+id(table)+" LIKE "+id(SOURCE)+"."+id(table));
                    execute(c,"INSERT INTO "+id(ISOLATED)+"."+id(table)+" SELECT * FROM "+id(SOURCE)+"."+id(table));
                }
                c.setCatalog(ISOLATED); preflight(c);
                for(String step:steps) execute(c,step);
                checkDefaults(c); verifyRows(c,oldColumns,before);
                REPORT.put("isolatedMigrationVerified",true); REPORT.put("status","ISOLATED_VERIFIED"); saveReport();
                c.setCatalog(SOURCE); preflight(c); verifyRows(c,oldColumns,before);
                require(scalar(c,"SELECT COUNT(*) FROM information_schema.processlist WHERE db=DATABASE() AND id<>CONNECTION_ID() AND command<>'Sleep'")==0,"Other source operations became active");
                execute(c,"SET SESSION lock_wait_timeout=10");
                List<String> completed=new ArrayList<>(); REPORT.put("sourceDdlCompleted",completed);
                REPORT.put("status","SOURCE_MIGRATING"); saveReport();
                for(String step:steps) {
                    execute(c,step); completed.add(step.lines().findFirst().orElseThrow()); saveReport();
                }
                checkDefaults(c); verifyRows(c,oldColumns,before);
                REPORT.put("oldColumnsIdentical",true); REPORT.put("defaultChecksPassed",true);
                Map<String,Object> schemaAfter=new LinkedHashMap<>();
                for(String table:TABLES) schemaAfter.put(table,columns(c,table));
                schemaAfter.put("job_required_ability",columns(c,"job_required_ability"));
                REPORT.put("columnsAfter",schemaAfter); REPORT.put("status","COMPLETE"); saveReport();
                System.out.println("Migration COMPLETE; isolated verification passed; all original columns identical.");
                System.out.println(JSON.writeValueAsString(fingerprints));
                System.out.println("Private backup and manifest: "+BACKUP);
            } finally {
                c.setCatalog(SOURCE);
                query(c,"SELECT RELEASE_LOCK('"+LOCK+"')");
            }
        } catch(Exception ex) {
            if(Files.exists(BACKUP)) {REPORT.put("failureType",ex.getClass().getSimpleName()); saveReport();}
            throw ex;
        }
    }
}
