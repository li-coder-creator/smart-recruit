import java.nio.file.*;
import java.sql.*;
import java.util.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import org.apache.pdfbox.pdmodel.*;

public class CompanyFilesFixture extends CompanyProfileFixture {
    static Path root;
    static void files()throws Exception {
        Path fixtures=Path.of("target/company-file-fixtures");Files.createDirectories(fixtures);
        BufferedImage image=new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB);
        image.setRGB(0,0,0x3366aa);image.setRGB(1,1,0xff6600);
        ImageIO.write(image,"png",fixtures.resolve("office.png").toFile());
        ImageIO.write(image,"jpg",fixtures.resolve("office.jpg").toFile());
        Files.copy(fixtures.resolve("office.png"),fixtures.resolve("办公照片.png"),StandardCopyOption.REPLACE_EXISTING);
        Files.write(fixtures.resolve("license.pdf"),Base64.getDecoder().decode("JVBERi0xLjYKJfbk/N8KMSAwIG9iago8PAovVHlwZSAvQ2F0YWxvZwovVmVyc2lvbiAvMS42Ci9QYWdlcyAyIDAgUgo+PgplbmRvYmoKNCAwIG9iago8PAovTGVuZ3RoIDk3Ci9UeXBlIC9PYmpTdG0KL04gMgovRmlsdGVyIC9GbGF0ZURlY29kZQovRmlyc3QgOQo+PgpzdHJlYW0NCnicM1IwUDBWMDFSsLHRD6ksSFXQD0hMTy1W0PfOTClWiDYGSgcpxCroO+eX5pUoGCrY2aGqVND3TU3JTHTKr1CINtAzUABhM0MjIGluCSJjQcqKUoF6jcBGAfUDANDyHZENCmVuZHN0cmVhbQplbmRvYmoKNSAwIG9iago8PAovTGVuZ3RoIDI5Ci9Sb290IDEgMCBSCi9JRCBbPDVBN0Y3QTQ0QzM2NzcyOEQ4NEEyQTk1OEY0NTQxNDRCMTc3MjhFMzBFQkQyNzEwNTJBQjE4Q0FBMDdENTlBRUU+IDw1QTdGN0E0NEMzNjc3MjhEODRBMkE5NThGNDU0MTQ0QjE3NzI4RTMwRUJEMjcxMDUyQUIxOENBQTA3RDU5QUVFPl0KL1R5cGUgL1hSZWYKL1NpemUgNgovSW5kZXggWzAgNl0KL1cgWzEgMiAxXQovRmlsdGVyIC9GbGF0ZURlY29kZQo+PgpzdHJlYW0NCnicY2Bg+M/IwM/AxMACwoyMDH4MjIwiDAAY0QGCDQplbmRzdHJlYW0KZW5kb2JqCnN0YXJ0eHJlZgoyNzYKJSVFT0YK"));
        Files.writeString(fixtures.resolve("fake.png"),"This is text, not an image.");
        Files.writeString(fixtures.resolve("fake.pdf"),"%PDF-1.7\nNot a PDF document\n%%EOF");
        Files.writeString(fixtures.resolve("office.svg"),"<svg xmlns='http://www.w3.org/2000/svg'/>");
        Files.write(fixtures.resolve("empty.png"),new byte[0]);
        Files.copy(fixtures.resolve("office.png"),fixtures.resolve("wrong.jpg"),StandardCopyOption.REPLACE_EXISTING);
        byte[] png=Files.readAllBytes(fixtures.resolve("office.png"));
        Files.write(fixtures.resolve("photo-limit.png"),Arrays.copyOf(png,3*1024*1024));
        Files.write(fixtures.resolve("photo-over.png"),Arrays.copyOf(png,3*1024*1024+1));
        Files.write(fixtures.resolve("qualification-limit.png"),Arrays.copyOf(png,10*1024*1024));
        Files.write(fixtures.resolve("qualification-over.png"),Arrays.copyOf(png,10*1024*1024+1));
        byte[] large=png.clone();var b=java.nio.ByteBuffer.wrap(large);b.putInt(16,6000);b.putInt(20,6000);
        var crc=new java.util.zip.CRC32();crc.update(large,12,17);b.putInt(29,(int)crc.getValue());
        Files.write(fixtures.resolve("large-pixels.png"),large);
        System.out.println("Generated multipart fixtures");
    }
    static Map<String,Object> factsWithFiles()throws Exception{
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("photos",rows("SELECT * FROM company_photo ORDER BY id"));
        out.put("qualifications",rows("SELECT * FROM company_qualification ORDER BY id"));
        out.put("reviews",rows("SELECT * FROM company_review_record ORDER BY id"));
        List<String> objects=new ArrayList<>();
        if(Files.exists(root))try(var paths=Files.walk(root)){
            for(Path path:paths.filter(Files::isRegularFile).sorted().toList()){
                if(path.getFileName().toString().equals(".database"))continue;
                String key=root.relativize(path).toString().replace('\\','/');
                String digest=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
                objects.add(key+":"+Files.size(path)+":"+digest);
            }
        }
        out.put("objects",objects);return out;
    }
    public static void main(String[] args)throws Exception{
        System.setOut(new java.io.PrintStream(System.out,true,java.nio.charset.StandardCharsets.UTF_8));
        JSON.findAndRegisterModules();
        JSON.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        String mode=args[0],db=args[1];
        if(!db.matches("smart_recruit_profile_[0-9_]+"))throw new IllegalArgumentException("Unsafe fixture schema");
        root=Path.of(args[2]).toAbsolutePath().normalize();
        Path target=Path.of("target").toAbsolutePath().normalize();
        if(!root.startsWith(target)||!root.getFileName().toString().startsWith("company-files-"))throw new IllegalArgumentException("Unsafe fixture storage");
        if(mode.equals("setup")){
            Path old=Path.of("target/profile-source-fingerprint.txt");String original=Files.exists(old)?Files.readString(old):null;
            CompanyProfileFixture.main(new String[]{"setup",db});
            Files.copy(old,Path.of("target/files-source-fingerprint.txt"),StandardCopyOption.REPLACE_EXISTING);
            if(original!=null)Files.writeString(old,original);
            files();return;
        }
        if(mode.equals("generate")){files();return;}
        Map<?,?> config;try(var in=Files.newInputStream(Path.of("src/main/resources/application.yml"))){config=new org.yaml.snakeyaml.Yaml().load(in);}
        Map<?,?> ds=(Map<?,?>)((Map<?,?>)config.get("spring")).get("datasource");
        try(var conn=DriverManager.getConnection(ds.get("url").toString(),ds.get("username").toString(),ds.get("password").toString())){
            c=conn;if(!SOURCE.equals(c.getCatalog()))throw new IllegalStateException("Unexpected source");c.setCatalog(db);
            switch(mode){
                case "prepare-concurrency" -> {
                    var photos=rows("SELECT id,storage_key FROM company_photo WHERE company_id=201 ORDER BY id");
                    if(photos.size()!=5)throw new IllegalStateException("Unexpected concurrency preparation state");
                    for(var photo:photos.subList(2,photos.size())){
                        Path file=root.resolve(photo.get("storage_key").toString()).normalize();
                        if(!file.startsWith(root))throw new IllegalStateException("Unsafe fixture path");
                        update("DELETE FROM company_photo WHERE company_id=201 AND id=?",photo.get("id"));
                        Files.deleteIfExists(file);
                    }
                    System.out.println("Restored two baseline photos before valid concurrency regression");
                }
                case "baseline" -> {Files.writeString(Path.of("target/files-baseline.json"),JSON.writeValueAsString(factsWithFiles()));System.out.println("Saved DB and physical-file baseline");}
                case "verify-rollback" -> {
                    if(!Files.readString(Path.of("target/files-baseline.json")).equals(JSON.writeValueAsString(factsWithFiles())))throw new IllegalStateException("DB/files changed after rejected transaction");
                    System.out.println("DB AND PHYSICAL FILE ROLLBACK VERIFIED");
                }
                case "fault-photo-insert" -> exec("CREATE TRIGGER files_test_failure BEFORE INSERT ON company_photo FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Isolated photo insert failure'");
                case "fault-photo-delete" -> exec("CREATE TRIGGER files_test_failure BEFORE DELETE ON company_photo FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Isolated photo delete failure'");
                case "fault-review" -> exec("CREATE TRIGGER files_test_failure BEFORE INSERT ON company_review_record FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Isolated qualification review failure'");
                case "fault-remove" -> exec("CREATE TRIGGER files_test_failure BEFORE UPDATE ON company_qualification FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Isolated qualification removal failure'");
                case "fault-off" -> exec("DROP TRIGGER IF EXISTS files_test_failure");
                case "verified" -> {
                    update("UPDATE company_qualification SET review_status='APPROVED' WHERE company_id=201 AND qualification_type='BUSINESS_LICENSE' AND deleted=FALSE");
                    update("UPDATE company_review_record r JOIN company_qualification q ON q.id=r.qualification_id SET r.review_status='APPROVED',r.reviewer_user_id=107,r.reviewed_at=CURRENT_TIMESTAMP WHERE q.company_id=201 AND q.qualification_type='BUSINESS_LICENSE' AND q.deleted=FALSE");
                }
                case "io-on" -> {
                    Path folder=root.resolve("photos"),held=root.resolve("photos.held");
                    if(!folder.normalize().startsWith(root)||!held.normalize().startsWith(root))throw new IllegalStateException("Unsafe paths");
                    Files.move(folder,held);Files.writeString(folder,"Isolated storage failure");
                }
                case "io-off" -> {Files.delete(root.resolve("photos"));Files.move(root.resolve("photos.held"),root.resolve("photos"));}
                case "recovery-seed" -> {
                    var photo=rows("SELECT storage_key FROM company_photo WHERE company_id=201 ORDER BY id LIMIT 1").get(0);
                    Path current=root.resolve(photo.get("storage_key").toString()).normalize();
                    Path trash=root.resolve(".trash").resolve(current.getFileName()).normalize();
                    if(!current.startsWith(root)||!trash.startsWith(root))throw new IllegalStateException("Unsafe recovery fixture");
                    Files.createDirectories(trash.getParent());Files.move(current,trash);
                    Files.copy(Path.of("target/company-file-fixtures/office.png"),root.resolve("photos/"+UUID.randomUUID()+".png"));
                }
                case "audit" -> {
                    var facts=factsWithFiles();
                    List<Map<String,Object>> refs=new ArrayList<>();refs.addAll(rows("SELECT storage_key,file_size FROM company_photo"));refs.addAll(rows("SELECT storage_key,file_size FROM company_qualification"));
                    for(var ref:refs){
                        Path file=root.resolve(ref.get("storage_key").toString()).normalize();
                        if(!file.startsWith(root)||!Files.isRegularFile(file)||Files.size(file)!=((Number)ref.get("file_size")).longValue())throw new IllegalStateException("Missing or mismatched original");
                    }
                    List<?> objects=(List<?>)facts.get("objects");if(objects.size()!=refs.size())throw new IllegalStateException("Orphan or trash files remain");
                    if(!rows("SELECT company_id,COUNT(*) AS total FROM company_photo GROUP BY company_id HAVING COUNT(*)>6").isEmpty())throw new IllegalStateException("Photo limit exceeded");
                    if(!rows("SELECT * FROM company_qualification q LEFT JOIN company_review_record r ON r.qualification_id=q.id WHERE r.id IS NULL OR r.company_id<>q.company_id OR r.review_type<>'QUALIFICATION' OR r.registration_version IS NOT NULL").isEmpty())throw new IllegalStateException("Audit snapshot missing");
                    if(!Files.readString(Path.of("target/files-source-fingerprint.txt")).equals(fingerprint()))throw new IllegalStateException("Source data changed");
                    Files.writeString(Path.of("target/files-final-evidence.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(facts));
                    System.out.println("FILES, REVIEW LINKS, PHOTO LIMIT AND SOURCE FINGERPRINT VERIFIED");
                }
                default -> throw new IllegalArgumentException("Unknown fixture mode");
            }
        }
    }
}
