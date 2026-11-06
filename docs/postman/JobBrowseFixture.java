import org.yaml.snakeyaml.Yaml;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** 仅在新建隔离库中准备卡片回归数据；源库只读，复用已有合成身份和岗位。 */
public class JobBrowseFixture extends RecommendationQueryFixture {
    public static void main(String[] args) throws Exception {
        String mode=args[0];database=args[1];root=Path.of(args[2]).toAbsolutePath().normalize();
        if(!database.matches("smart_recruit_job_browse_[0-9]+")||!root.startsWith(Path.of("target").toAbsolutePath().normalize()))throw new IllegalArgumentException("Unsafe fixture target");
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
                insert("sys_user",row("id",107,"username","rq_input_7","password",com.recruit.smartrecruit.utils.Md5Util.encrypt(System.getenv("RECOMMENDATION_QUERY_FIXTURE_PASSWORD")),"role","COMPANY"));
                insert("company",row("id",204,"user_id",107,"name","星河科技","status","APPROVED"));
                job(2001,row("company_id",204,"title","平台工程师","required_skills","熟悉Kotlin","preferred_skills","Kafka","salary_min",25000,"salary_max",35000));
                job(2002,row("salary_min",10000,"salary_max",30000));
                job(2003,row("salary_min",20000,"salary_max",30000));
                job(2004,row("salary_min",null,"salary_max",999999));
                job(2005,row("negotiable",true,"salary_min",800000,"salary_max",900000));
                job(2006,row("salary_min",0,"salary_max",500000));
                job(2007,row("salary_min",-1,"salary_max",700000));
                job(2008,row("salary_min",10000,"salary_max",null));
                job(2009,row("salary_min",null,"salary_max",null));
                // salary_unit 在当前表中非空；用已有 YEAR、DAY 覆盖不可直接比较的单位。
                job(2010,row("salary_min",10000,"salary_max",-100));
                job(2011,row("salary_min",10000,"salary_max",0));
                job(2012,row("salary_unit","YEAR","salary_min",1000000,"salary_max",2000000));
                job(2013,row("salary_unit","DAY","salary_min",1000000,"salary_max",2000000));
                isolated("UPDATE job SET update_time='2026-11-01 12:00:00'");
                isolated("UPDATE job SET update_time='2026-11-01 13:00:00' WHERE id=2002");
                isolated("UPDATE job SET title='隐藏标题',required_skills='隐藏技能',preferred_skills='隐藏加分' WHERE id IN (1013,1014,1015,1016,1017)");
                isolated("UPDATE company SET name='隐藏企业待审' WHERE id=202");
                isolated("UPDATE company SET name='隐藏企业拒绝' WHERE id=203");
                isolated("UPDATE job SET required_skills='过时技能' WHERE id=1006");
                ability(2004,1022,"数据库索引调优",3);
                isolated("UPDATE job_required_ability SET ability_name='SQL' WHERE id=2004");
                // 必须放在所有岗位内容更新之后，避免 ON UPDATE 把1006等记录改成当前时间。
                isolated("UPDATE job SET update_time='2026-11-01 12:00:00'");
                isolated("UPDATE job SET update_time='2026-11-01 13:00:00' WHERE id=2002");
                Files.writeString(root.resolve("fixture-before.sha256"),fixtureFingerprint());
                prepareRedis(spring);return;
            }
            c.setCatalog(database);
            if(mode.equals("redis-cleanup")){cleanupRedis(spring);return;}
            if(mode.equals("normalize-times")) {
                // 仅允许修复仍与建立时完全一致的自有夹具，防止覆盖应用产生的意外写入。
                if(!fixtureFingerprint().equals(Files.readString(root.resolve("fixture-before.sha256"))))throw new IllegalStateException("Fixture data unexpectedly changed");
                isolated("UPDATE job SET update_time='2026-11-01 12:00:00'");
                isolated("UPDATE job SET update_time='2026-11-01 13:00:00' WHERE id=2002");
                Files.writeString(root.resolve("fixture-before.sha256"),fixtureFingerprint());
                System.out.println("Fixture ordering timestamps normalized; prior business fingerprint verified unchanged.");return;
            }
            if(!mode.equals("audit"))throw new IllegalArgumentException("Unknown mode");
            String source=fingerprint(),fixture=fixtureFingerprint();
            if(!source.equals(Files.readString(root.resolve("source-before.sha256"))))throw new IllegalStateException("Source data changed");
            if(!fixture.equals(Files.readString(root.resolve("fixture-before.sha256"))))throw new IllegalStateException("Read endpoints changed fixture business data");
            var result=Map.of("sourceUnchanged",true,"sourceSha256",source,"fixtureBusinessDataUnchanged",true,"fixtureSha256",fixture);
            Files.writeString(root.resolve("audit.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(result));System.out.println(JSON.writeValueAsString(result));
        }
    }
}
