import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Only creates/mutates a separately named regression schema; source tables are fingerprinted. */
public class CompanyRegistrationReviewFixture extends CompanyProfileFixture {
    static String reviewFacts() throws Exception {
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("companies", rows("SELECT * FROM company ORDER BY id"));
        facts.put("reviews", rows("SELECT * FROM company_review_record ORDER BY id"));
        facts.put("benefits", rows("SELECT * FROM company_benefit ORDER BY id"));
        facts.put("qualifications", rows("SELECT * FROM company_qualification ORDER BY id"));
        return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(facts);
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(System.out, true, java.nio.charset.StandardCharsets.UTF_8));
        JSON.findAndRegisterModules();
        JSON.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        String mode = args[0], database = args[1];
        if (!database.matches("smart_recruit_profile_[0-9_]+")) throw new IllegalArgumentException("Unsafe schema");
        Map<?, ?> config;
        try (var input = Files.newInputStream(Path.of("src/main/resources/application.yml"))) {
            config = new org.yaml.snakeyaml.Yaml().load(input);
        }
        Map<?, ?> ds = (Map<?, ?>) ((Map<?, ?>) config.get("spring")).get("datasource");
        try (var connection = DriverManager.getConnection(ds.get("url").toString(),
                ds.get("username").toString(), ds.get("password").toString())) {
            c = connection;
            if (!SOURCE.equals(c.getCatalog())) throw new IllegalStateException("Unexpected source");
            if (mode.equals("setup")) {
                String before = fingerprint();
                exec("CREATE DATABASE `" + database + "` CHARACTER SET utf8mb4");
                c.setCatalog(database);
                exec("SET FOREIGN_KEY_CHECKS=0");
                try {
                    for (var table : rows("SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA='"
                            + SOURCE + "' AND TABLE_TYPE='BASE TABLE'")) {
                        exec(rows("SHOW CREATE TABLE " + SOURCE + ".`" + table.get("TABLE_NAME") + "`")
                                .get(0).get("Create Table").toString());
                    }
                } finally { exec("SET FOREIGN_KEY_CHECKS=1"); }
                seed();
                Map<String, Object> admin = new HashMap<>();
                admin.put("id", 108); admin.put("username", "cp_fixture_8"); admin.put("role", "ADMIN");
                admin.put("password", com.recruit.smartrecruit.utils.Md5Util.encrypt(System.getenv("PROFILE_FIXTURE_PASSWORD")));
                cloneRow("sys_user", admin);
                // Metadata-only qualification fixture distinguishes registration and material review semantics.
                update("INSERT INTO company_qualification(id,company_id,qualification_type,storage_key,original_filename,"
                        + "content_type,file_size,review_status,deleted) VALUES(7001,201,'BUSINESS_LICENSE',"
                        + "'fixture/review-license','fixture.pdf','application/pdf',1,'APPROVED',0)");
                update("INSERT INTO company_review_record(id,company_id,review_type,qualification_id,snapshot_json,"
                        + "review_status,reviewer_user_id,reviewed_at) VALUES(8001,201,'QUALIFICATION',7001,"
                        + "'{\"qualificationId\":7001}','APPROVED',107,CURRENT_TIMESTAMP)");
                if (!before.equals(fingerprint())) throw new IllegalStateException("Source changed during setup");
                Files.writeString(Path.of("target/registration-review-source-fingerprint.txt"), before);
                System.out.println("Created isolated registration review schema " + database);
                return;
            }
            c.setCatalog(database);
            switch (mode) {
                case "baseline" -> {
                    Files.writeString(Path.of("target/registration-review-baseline.json"), reviewFacts());
                    System.out.println("Saved transaction baseline");
                }
                case "verify-rollback" -> {
                    if (!Files.readString(Path.of("target/registration-review-baseline.json")).equals(reviewFacts())) {
                        throw new IllegalStateException("Transaction baseline changed");
                    }
                    System.out.println("Company, review evidence, benefits and qualifications unchanged after failure");
                }
                case "fault-company" -> exec("CREATE TRIGGER review_test_failure BEFORE UPDATE ON company FOR EACH ROW "
                        + "BEGIN IF OLD.id=204 AND NEW.registration_review_status<>OLD.registration_review_status THEN "
                        + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Isolated second-write failure'; END IF; END");
                case "delay-review204", "delay-review205" -> {
                    int companyId = mode.endsWith("204") ? 204 : 205;
                    exec("CREATE TRIGGER review_test_delay BEFORE UPDATE ON company_review_record FOR EACH ROW "
                            + "BEGIN IF OLD.company_id=" + companyId + " AND OLD.review_type='REGISTRATION' THEN "
                            + "DO SLEEP(2); END IF; END");
                }
                case "delay-profile205" -> exec("CREATE TRIGGER review_test_delay BEFORE UPDATE ON company FOR EACH ROW "
                        + "BEGIN IF OLD.id=205 AND NEW.registration_version>OLD.registration_version THEN "
                        + "DO SLEEP(2); END IF; END");
                case "fault-off" -> {
                    exec("DROP TRIGGER IF EXISTS review_test_failure");
                    exec("DROP TRIGGER IF EXISTS review_test_delay");
                }
                case "audit" -> {
                    if (!Files.readString(Path.of("target/registration-review-source-fingerprint.txt")).equals(fingerprint())) {
                        throw new IllegalStateException("Source data changed");
                    }
                    if (!rows("SELECT c.id FROM company c LEFT JOIN company_review_record r ON r.company_id=c.id "
                            + "AND r.review_type='REGISTRATION' AND r.registration_version=c.registration_version "
                            + "WHERE c.registration_review_status='VERIFIED' AND (c.verified_registration_version "
                            + "IS NULL OR c.verified_registration_version<>c.registration_version OR r.id IS NULL "
                            + "OR r.review_status<>'APPROVED')").isEmpty()) throw new IllegalStateException("Invalid verified state");
                    if (!rows("SELECT id FROM company_review_record WHERE review_type='REGISTRATION' AND "
                            + "((review_status='PENDING' AND (reviewer_user_id IS NOT NULL OR reviewed_at IS NOT NULL "
                            + "OR review_comment IS NOT NULL)) OR (review_status<>'PENDING' AND (reviewer_user_id "
                            + "IS NULL OR reviewer_user_id NOT IN(107,108) OR reviewed_at IS NULL)))").isEmpty()) {
                        throw new IllegalStateException("Invalid reviewer evidence");
                    }
                    if (!rows("SELECT TRIGGER_NAME FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA='" + database
                            + "' AND TRIGGER_NAME IN('review_test_failure','review_test_delay')").isEmpty()) {
                        throw new IllegalStateException("Test trigger remains");
                    }
                    Files.writeString(Path.of("target/registration-review-final-evidence.json"), reviewFacts());
                    System.out.println("Source fingerprint, review/version evidence and trigger cleanup verified");
                }
                default -> throw new IllegalArgumentException("Unknown fixture mode");
            }
        }
    }
}
