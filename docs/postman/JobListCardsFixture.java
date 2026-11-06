import org.yaml.snakeyaml.Yaml;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** 仅在新建隔离库中准备卡片回归数据；源库只读，复用已有合成身份和岗位。 */
public class JobListCardsFixture extends RecommendationQueryFixture {
    public static void main(String[] args) throws Exception {
        String mode=args[0];database=args[1];root=Path.of(args[2]).toAbsolutePath().normalize();
        if(!database.matches("smart_recruit_job_cards_[0-9]+")||!root.startsWith(Path.of("target").toAbsolutePath().normalize()))throw new IllegalArgumentException("Unsafe fixture target");
        Files.createDirectories(root);Map<?,?> cfg;
        try(var in=Files.newInputStream(Path.of("src/main/resources/application.yml"))){cfg=new Yaml().load(in);}
        var spring=(Map<?,?>)cfg.get("spring");var ds=(Map<?,?>)spring.get("datasource");
        try(var c=DriverManager.getConnection(ds.get("url").toString(),ds.get("username").toString(),ds.get("password").toString())) {
            connection=c;if(!SOURCE.equals(c.getCatalog()))throw new IllegalStateException("Unexpected source");
            if(mode.equals("setup")) {
                RecommendationQueryFixture.setup();
                isolated("UPDATE company SET logo='https://example.invalid/job-card-logo.png' WHERE id=201");
                isolated("UPDATE job SET summary='岗位卡片简介',benefits='弹性工作、带薪年假',preferred_skills='Docker',direction_code='BACKEND',team='平台组',deadline='2026-12-31' WHERE id=1001");
                // 固定排序，验证连接后跨页不重复、不遗漏。
                isolated("UPDATE job SET update_time='2026-11-01 12:00:00'");
                Files.writeString(root.resolve("fixture-before.sha256"),fixtureFingerprint());
                prepareRedis(spring);return;
            }
            c.setCatalog(database);
            if(mode.equals("redis-cleanup")){cleanupRedis(spring);return;}
            if(!mode.equals("audit"))throw new IllegalArgumentException("Unknown mode");
            String source=fingerprint(),fixture=fixtureFingerprint();
            if(!source.equals(Files.readString(root.resolve("source-before.sha256"))))throw new IllegalStateException("Source data changed");
            if(!fixture.equals(Files.readString(root.resolve("fixture-before.sha256"))))throw new IllegalStateException("Read endpoints changed fixture business data");
            var result=Map.of("sourceUnchanged",true,"sourceSha256",source,"fixtureBusinessDataUnchanged",true,"fixtureSha256",fixture);
            Files.writeString(root.resolve("audit.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(result));System.out.println(JSON.writeValueAsString(result));
        }
    }
}
