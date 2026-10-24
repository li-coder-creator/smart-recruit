import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Gallery regression fixtures; writes only a newly named isolated database and target directory. */
public class CompanyGalleryFixture extends CompanyProfileFixture {
    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(System.out, true, java.nio.charset.StandardCharsets.UTF_8));
        JSON.findAndRegisterModules();
        JSON.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        String mode = args[0], database = args[1];
        if (!database.matches("smart_recruit_profile_[0-9_]+")) throw new IllegalArgumentException("Unsafe fixture schema");
        Path root = Path.of(args[2]).toAbsolutePath().normalize();
        if (!root.startsWith(Path.of("target").toAbsolutePath().normalize())
                || !root.getFileName().toString().startsWith("company-gallery-")) {
            throw new IllegalArgumentException("Unsafe fixture directory");
        }
        Map<?, ?> config;
        try (var input = Files.newInputStream(Path.of("src/main/resources/application.yml"))) {
            config = new org.yaml.snakeyaml.Yaml().load(input);
        }
        Map<?, ?> ds = (Map<?, ?>) ((Map<?, ?>) config.get("spring")).get("datasource");
        try (var connection = DriverManager.getConnection(ds.get("url").toString(),
                ds.get("username").toString(), ds.get("password").toString())) {
            c = connection;
            if (!SOURCE.equals(c.getCatalog())) throw new IllegalStateException("Unexpected source schema");
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
                if (!before.equals(fingerprint())) throw new IllegalStateException("Source changed during setup");
                Files.writeString(Path.of("target/gallery-source-fingerprint.txt"), before);
                Path fixtures = Path.of("target/company-gallery-fixtures");
                Files.createDirectories(fixtures);
                BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
                image.setRGB(0, 0, 0x3366aa);
                ImageIO.write(image, "png", fixtures.resolve("office.png").toFile());
                ImageIO.write(image, "jpg", fixtures.resolve("office.jpg").toFile());
                System.out.println("Created isolated gallery fixtures: " + database);
                return;
            }
            c.setCatalog(database);
            switch (mode) {
                case "sort" -> {
                    var photos = rows("SELECT id FROM company_photo WHERE company_id=201 ORDER BY id");
                    if (photos.size() != 3) throw new IllegalStateException("Expected exactly three sorting fixtures");
                    for (int index = 0; index < photos.size(); index++) {
                        update("UPDATE company_photo SET sort_order=? WHERE id=? AND company_id=201",
                                index == 0 ? 4 : 0, photos.get(index).get("id"));
                    }
                    System.out.println("Seeded non-insertion order and tied sortOrder fixtures");
                }
                case "revoke" -> {
                    if (update("UPDATE company SET status='REJECTED' WHERE id=204") != 1) {
                        throw new IllegalStateException("Expected foreign company fixture");
                    }
                    System.out.println("Revoked admission only for isolated company 204");
                }
                case "audit" -> {
                    if (!Files.readString(Path.of("target/gallery-source-fingerprint.txt")).equals(fingerprint())) {
                        throw new IllegalStateException("Source data changed");
                    }
                    var photos = rows("SELECT * FROM company_photo ORDER BY company_id,sort_order,id");
                    for (var photo : photos) {
                        Path file = root.resolve(photo.get("storage_key").toString()).normalize();
                        if (!file.startsWith(root) || !Files.isRegularFile(file)
                                || Files.size(file) != ((Number) photo.get("file_size")).longValue()) {
                            throw new IllegalStateException("Photo file mismatch");
                        }
                    }
                    try (var files = Files.walk(root)) {
                        if (files.filter(Files::isRegularFile).filter(p -> !p.getFileName().toString().equals(".database"))
                                .count() != photos.size()) throw new IllegalStateException("Orphan file remains");
                    }
                    Map<String, Object> facts = new LinkedHashMap<>();
                    facts.put("photos", photos);
                    facts.put("companies", rows("SELECT id,status FROM company ORDER BY id"));
                    facts.put("sourceUnchanged", true);
                    Files.writeString(Path.of("target/gallery-final-evidence.json"),
                            JSON.writerWithDefaultPrettyPrinter().writeValueAsString(facts));
                    System.out.println("Source unchanged; photo records, physical files and cleanup verified");
                }
                default -> throw new IllegalArgumentException("Unknown fixture mode");
            }
        }
    }
}
