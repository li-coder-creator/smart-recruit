import java.nio.file.*;
import java.sql.*;
import java.util.*;
public class CompanyQualificationReviewFixture extends CompanyRegistrationReviewFixture {
 public static void main(String[] args)throws Exception {
  JSON.findAndRegisterModules();
  JSON.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  if(args[0].equals("setup")){CompanyRegistrationReviewFixture.main(new String[]{"setup",args[1]});CompanyFilesFixture.files();return;}
  if(!List.of("tamper","missing","restore","fault-material","delay-material204","delay-material205","audit-material").contains(args[0])){CompanyRegistrationReviewFixture.main(new String[]{args[0],args[1]});return;}
  String db=args[1];if(!db.matches("smart_recruit_profile_[0-9_]+"))throw new IllegalArgumentException("Unsafe schema");
  Path root=Path.of(args[2]).toAbsolutePath().normalize();if(!root.startsWith(Path.of("target").toAbsolutePath().normalize()))throw new IllegalArgumentException("Unsafe root");
  Map<?,?> config;try(var in=Files.newInputStream(Path.of("src/main/resources/application.yml"))){config=new org.yaml.snakeyaml.Yaml().load(in);}
  Map<?,?> ds=(Map<?,?>)((Map<?,?>)config.get("spring")).get("datasource");
  try(var conn=DriverManager.getConnection(ds.get("url").toString(),ds.get("username").toString(),ds.get("password").toString())){
   c=conn;c.setCatalog(db);
   if(args[0].equals("audit-material")){
    if(!rows("SELECT q.id FROM company_qualification q LEFT JOIN company_review_record r ON r.qualification_id=q.id AND r.company_id=q.company_id AND r.review_type='QUALIFICATION' WHERE r.id IS NULL OR q.review_status<>r.review_status OR (r.review_status<>'PENDING' AND (r.reviewer_user_id IS NULL OR r.reviewed_at IS NULL))").isEmpty())throw new IllegalStateException("Material review inconsistency");
    for(var row:rows("SELECT q.storage_key,q.file_size,r.snapshot_json FROM company_qualification q JOIN company_review_record r ON r.qualification_id=q.id WHERE q.storage_key LIKE 'qualifications/%'")){
     Path original=root.resolve(row.get("storage_key").toString()).normalize();if(!original.startsWith(root))throw new IllegalStateException("Unsafe object");
     byte[] bytes=Files.readAllBytes(original);var snapshot=JSON.readTree(row.get("snapshot_json").toString());
     if(bytes.length!=((Number)row.get("file_size")).longValue()||!java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)).equals(snapshot.get("sha256").asText()))throw new IllegalStateException("Original not restored");
    }
    Files.writeString(Path.of("target/qualification-review-final-evidence.json"),reviewFacts());System.out.println("Material states, reviewer evidence and all real originals verified");return;
   }
   if(args[0].startsWith("delay-material")){int company=args[0].endsWith("204")?204:205;exec("CREATE TRIGGER review_test_delay BEFORE UPDATE ON company_review_record FOR EACH ROW BEGIN IF OLD.company_id="+company+" AND OLD.review_type='QUALIFICATION' THEN DO SLEEP(2); END IF; END");return;}
   if(args[0].equals("fault-material")){exec("CREATE TRIGGER review_test_failure BEFORE UPDATE ON company_qualification FOR EACH ROW BEGIN IF OLD.company_id=204 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Isolated material second-write failure'; END IF; END");return;}
   var q=rows("SELECT storage_key FROM company_qualification WHERE original_filename='tamper.png'").get(0);
   Path file=root.resolve(q.get("storage_key").toString()).normalize();if(!file.startsWith(root))throw new IllegalArgumentException("Unsafe object");
   Path held=file.resolveSibling(file.getFileName()+".held");
   if(args[0].equals("restore")){Files.move(held,file,StandardCopyOption.REPLACE_EXISTING);}
   else {Files.move(file,held);if(args[0].equals("tamper"))Files.writeString(file,"Modified after submission");}
   System.out.println("Isolated original: "+args[0]);
  }
 }
}
