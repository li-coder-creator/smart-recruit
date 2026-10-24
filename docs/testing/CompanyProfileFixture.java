import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.security.MessageDigest;
import org.yaml.snakeyaml.Yaml;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Only manipulates a separately named regression database; never writes the source schema. */
public class CompanyProfileFixture {
    static final String SOURCE = "smart_recruit";
    static Connection c;
    static final ObjectMapper JSON = new ObjectMapper();
    static void exec(String sql) throws Exception {try(var s=c.createStatement()){s.execute(sql);}}
    static int update(String sql,Object...args)throws Exception{
        try(var s=c.prepareStatement(sql)){for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);return s.executeUpdate();}
    }
    static List<Map<String,Object>> rows(String sql)throws Exception{
        List<Map<String,Object>> out=new ArrayList<>();
        try(var s=c.createStatement();var r=s.executeQuery(sql)){var m=r.getMetaData();while(r.next()){
            Map<String,Object> row=new LinkedHashMap<>();for(int i=1;i<=m.getColumnCount();i++)row.put(m.getColumnLabel(i),r.getObject(i));out.add(row);}}
        return out;
    }
    static long first(String table)throws Exception{
        return ((Number)rows("SELECT MIN(id) AS id FROM "+SOURCE+".`"+table+"`").get(0).get("id")).longValue();
    }
    static void cloneRow(String table,Map<String,Object> replacements)throws Exception{
        List<String> cols=new ArrayList<>(),select=new ArrayList<>();List<Object> values=new ArrayList<>();
        for(var row:rows("SHOW COLUMNS FROM "+SOURCE+".`"+table+"`")){
            String col=row.get("Field").toString();cols.add("`"+col+"`");
            if(replacements.containsKey(col)){select.add("?");values.add(replacements.get(col));}else select.add("`"+col+"`");
        }
        values.add(first(table));
        if(update("INSERT INTO `"+table+"` ("+String.join(",",cols)+") SELECT "+String.join(",",select)
            +" FROM "+SOURCE+".`"+table+"` WHERE id=?",values.toArray())!=1)throw new IllegalStateException("Fixture clone failed");
    }
    static String fingerprint()throws Exception{
        MessageDigest md=MessageDigest.getInstance("SHA-256");
        for(String table:List.of("company","company_benefit","company_photo","company_qualification","company_review_record","job","sys_user"))
            md.update(rows("SELECT * FROM "+SOURCE+".`"+table+"` ORDER BY id").toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(md.digest());
    }
    static void seed()throws Exception{
        for(int i=1;i<=7;i++){
            Map<String,Object> r=new HashMap<>();
            r.put("id",100+i);r.put("username","cp_fixture_"+i);r.put("role",i==6?"JOB_SEEKER":i==7?"ADMIN":"COMPANY");
            r.put("password",com.recruit.smartrecruit.utils.Md5Util.encrypt(System.getenv("PROFILE_FIXTURE_PASSWORD")));
            r.put("nickname","Company profile fixture "+i);
            cloneRow("sys_user",r);
        }
        for(int i=1;i<=5;i++){
            Map<String,Object> r=new HashMap<>();
            r.put("id",200+i);r.put("user_id",100+i);r.put("name","资料回归企业"+i);
            r.put("status",i==2?"PENDING":i==3?"REJECTED":"APPROVED");
            r.put("profile_version",0);r.put("registration_version",0);r.put("registration_review_status","UNVERIFIED");
            r.put("verified_registration_version",null);
            for(String col:List.of("full_name","industry","employee_size","financing_stage","company_nature","website",
                "commute_description","founded_date","legal_representative","registered_capital","business_status",
                "credit_code","registered_address","business_scope","work_start_time","work_end_time","rest_policy",
                "overtime_policy","work_notes","product_name","product_description","team_name","team_role","team_bio"))r.put(col,null);
            cloneRow("company",r);
        }
        for(int i=1;i<=8;i++){
            Map<String,Object> r=new HashMap<>();r.put("id",900+i);
            r.put("company_id",i==7?204:i==8?202:201);
            r.put("title","企业主页回归岗位"+i);
            r.put("status",i<=3||i>=7?0:i-3);
            r.put("update_time",Timestamp.valueOf("2026-10-20 12:00:00"));
            cloneRow("job",r);
        }
    }
    static String facts()throws Exception {
        Map<String,Object> f=new LinkedHashMap<>();
        f.put("company",rows("SELECT id,status,profile_version,registration_version,registration_review_status,verified_registration_version,name,full_name,website,work_start_time,work_end_time FROM company ORDER BY id"));
        f.put("benefits",rows("SELECT company_id,benefit_code,sort_order FROM company_benefit ORDER BY company_id,sort_order"));
        f.put("reviews",rows("SELECT company_id,review_type,registration_version,snapshot_json,review_status FROM company_review_record ORDER BY id"));
        f.put("jobs",rows("SELECT id,company_id,status FROM job ORDER BY id"));
        return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(f);
    }
    public static void main(String[] args)throws Exception{
        System.setOut(new java.io.PrintStream(System.out,true,java.nio.charset.StandardCharsets.UTF_8));
        String mode=args[0],db=args[1];
        if(!db.matches("smart_recruit_profile_[0-9_]+"))throw new IllegalArgumentException("Unsafe regression schema");
        Map<?,?> config;try(var in=Files.newInputStream(Path.of("src/main/resources/application.yml"))){config=new Yaml().load(in);}
        Map<?,?> ds=(Map<?,?>)((Map<?,?>)config.get("spring")).get("datasource");
        try(Connection conn=DriverManager.getConnection(ds.get("url").toString(),ds.get("username").toString(),ds.get("password").toString())){
            c=conn;if(!SOURCE.equals(c.getCatalog()))throw new IllegalStateException("Unexpected source");
            if(mode.equals("setup")){
                String before=fingerprint();exec("CREATE DATABASE `"+db+"` CHARACTER SET utf8mb4");c.setCatalog(db);
                exec("SET FOREIGN_KEY_CHECKS=0");
                try{
                    for(var row:rows("SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA='"+SOURCE+"' AND TABLE_TYPE='BASE TABLE'")){
                        String table=row.get("TABLE_NAME").toString();
                        String ddl=rows("SHOW CREATE TABLE "+SOURCE+".`"+table+"`").get(0).get("Create Table").toString();exec(ddl);
                    }
                }finally{exec("SET FOREIGN_KEY_CHECKS=1");}
                seed();
                Files.writeString(Path.of("target/profile-source-fingerprint.txt"),before);
                System.out.println("Created isolated fixture schema "+db);return;
            }
            c.setCatalog(db);
            switch(mode){
                case "reset" -> {
                    // This connection has already been restricted to a named isolated fixture schema.
                    exec("SET FOREIGN_KEY_CHECKS=0");
                    try {
                        for(var row:rows("SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA='"+db+"' AND TABLE_TYPE='BASE TABLE'"))
                            exec("DELETE FROM `"+row.get("TABLE_NAME")+"`");
                    } finally {exec("SET FOREIGN_KEY_CHECKS=1");}
                    seed();System.out.println("Reset only isolated fixtures in "+db);
                }
                case "facts" -> System.out.println(facts());
                case "baseline" -> {Files.writeString(Path.of("target/profile-rollback-baseline.json"),facts());System.out.println("Saved rollback baseline");}
                case "verify-rollback" -> {
                    if(!Files.readString(Path.of("target/profile-rollback-baseline.json")).equals(facts()))throw new IllegalStateException("Rollback changed persisted data");
                    System.out.println("ROLLBACK VERIFIED: company, benefits and review snapshots unchanged");
                }
                case "fault-benefit" -> exec("CREATE TRIGGER profile_test_failure BEFORE INSERT ON company_benefit FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Isolated benefit failure'");
                case "fault-review" -> exec("CREATE TRIGGER profile_test_failure BEFORE INSERT ON company_review_record FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Isolated review failure'");
                case "fault-off" -> exec("DROP TRIGGER IF EXISTS profile_test_failure");
                case "verified" -> {
                    update("UPDATE company SET registration_review_status='VERIFIED',verified_registration_version=registration_version WHERE id=201");
                    update("UPDATE company_review_record r JOIN company c ON c.id=r.company_id AND c.registration_version=r.registration_version SET r.review_status='APPROVED',r.reviewer_user_id=107,r.reviewed_at=CURRENT_TIMESTAMP WHERE c.id=201 AND r.review_type='REGISTRATION'");
                    update("INSERT INTO company_photo(company_id,storage_key,original_filename,content_type,file_size) VALUES(201,'fixture/photo','fixture.png','image/png',1)");
                    update("INSERT INTO company_qualification(company_id,qualification_type,storage_key,original_filename,content_type,file_size,review_status,deleted) VALUES(201,'BUSINESS_LICENSE','fixture/license','fixture.pdf','application/pdf',1,'APPROVED',0)");
                }
                case "mismatch" -> update("UPDATE company SET verified_registration_version=registration_version-1 WHERE id=201");
                case "remove-license" -> update("UPDATE company_qualification SET deleted=1 WHERE company_id=201");
                case "source-unchanged" -> {
                    if(!Files.readString(Path.of("target/profile-source-fingerprint.txt")).equals(fingerprint()))throw new IllegalStateException("Source schema data changed");
                    System.out.println("SOURCE VERIFIED UNCHANGED");
                }
                default -> throw new IllegalArgumentException("Unknown mode");
            }
        }
    }
}
