import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.smartrecruit.entity.*;
import com.recruit.smartrecruit.entity.enums.*;
import org.apache.ibatis.annotations.*;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Source SELECTs only. All mapping fixtures are confined to the migration clone and rolled back. */
public class RecommendationInputVerification {
    public interface ProbeMapper {
        @Select("SELECT * FROM resume WHERE id=#{id}") Resumebasic resume(@Param("id") long id);
        @Select("SELECT * FROM job WHERE id=#{id}") Job job(@Param("id") long id);
        @Select("SELECT * FROM resume_experience WHERE resume_id=#{id}") ResumeExperience experience(@Param("id") long id);
        @Select("SELECT * FROM resume_skill WHERE resume_id=#{id}") ResumeSkill skill(@Param("id") long id);
        @Select("SELECT * FROM job_preference WHERE resume_id=#{id}") JobPreference preference(@Param("id") long id);
        @Select("SELECT * FROM job_required_ability WHERE job_id=#{id}") JobRequiredAbility ability(@Param("id") long id);
    }
    static void writeClone(Connection c, String sql) throws Exception {
        RecommendationInputMigration.require(RecommendationInputMigration.ISOLATED.equals(c.getCatalog()),"Fixture write must stay in isolated schema");
        RecommendationInputMigration.execute(c,sql);
    }
    public static void main(String[] args) throws Exception {
        Map<?,?> cfg;
        try(var in=Files.newInputStream(Path.of("src/main/resources/application.yml"))) {cfg=new Yaml().load(in);}
        Map<?,?> ds=(Map<?,?>)((Map<?,?>)cfg.get("spring")).get("datasource");
        String url=ds.get("url").toString(), username=ds.get("username").toString(), password=ds.get("password").toString();
        try(Connection c=DriverManager.getConnection(url,username,password)) {
            RecommendationInputMigration.require(RecommendationInputMigration.SOURCE.equals(c.getCatalog()),"Unexpected source schema");
            c.setCatalog(RecommendationInputMigration.ISOLATED);
            RecommendationInputMigration.checkDefaults(c);
            long resumeId=RecommendationInputMigration.scalar(c,"SELECT MIN(id) FROM resume");
            long jobId=RecommendationInputMigration.scalar(c,"SELECT MIN(id) FROM job");
            c.setAutoCommit(false);
            Configuration configuration=new Configuration(new Environment("mapping-probe",new JdbcTransactionFactory(),new UnpooledDataSource()));
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.addMapper(ProbeMapper.class);
            try(SqlSession session=new SqlSessionFactoryBuilder().build(configuration).openSession(c)) {
                try {
                    writeClone(c,"UPDATE resume SET career_stage='NEW_GRAD',formal_experience_status='NONE' WHERE id="+resumeId);
                    writeClone(c,"INSERT INTO resume_experience(resume_id,company_name,position,start_date,end_date,experience_type,is_current) VALUES ("+resumeId+",'mapping fixture','intern','2026-01-01','2026-06-30','INTERNSHIP',0)");
                    writeClone(c,"INSERT INTO resume_skill(resume_id,skill_name,proficiency) VALUES ("+resumeId+",'Java','FAMILIAR')");
                    writeClone(c,"INSERT INTO job_preference(resume_id,expected_position,direction_code) VALUES ("+resumeId+",'backend','BACKEND')");
                    writeClone(c,"UPDATE job SET direction_code='BACKEND',required_abilities_source='STRUCTURED',required_abilities_version=1 WHERE id="+jobId);
                    writeClone(c,"INSERT INTO job_required_ability(job_id,raw_text,ability_name,proficiency) VALUES ("+jobId+",'familiar with Java','Java','FAMILIAR')");
                    ProbeMapper mapper=session.getMapper(ProbeMapper.class);
                    RecommendationInputMigration.require(mapper.resume(resumeId).getCareerStage()==CareerStage.NEW_GRAD,"career mapping");
                    RecommendationInputMigration.require(mapper.resume(resumeId).getFormalExperienceStatus()==FormalExperienceStatus.NONE,"experience status mapping");
                    RecommendationInputMigration.require(mapper.experience(resumeId).getExperienceType()==ExperienceType.INTERNSHIP,"experience type mapping");
                    RecommendationInputMigration.require(Boolean.FALSE.equals(mapper.experience(resumeId).getIsCurrent()),"nullable boolean mapping");
                    RecommendationInputMigration.require(mapper.skill(resumeId).getProficiency()==AbilityProficiency.FAMILIAR,"skill mapping");
                    RecommendationInputMigration.require(mapper.preference(resumeId).getDirectionCode()==JobDirection.BACKEND,"preference mapping");
                    RecommendationInputMigration.require(mapper.job(jobId).getRequiredAbilitiesSource()==RequiredAbilitiesSource.STRUCTURED,"source mapping");
                    RecommendationInputMigration.require(Long.valueOf(1).equals(mapper.job(jobId).getRequiredAbilitiesVersion()),"version mapping");
                    RecommendationInputMigration.require(mapper.ability(jobId).getProficiency()==AbilityProficiency.FAMILIAR,"ability mapping");
                    writeClone(c,"DELETE FROM job WHERE id="+jobId);
                    RecommendationInputMigration.require(RecommendationInputMigration.scalar(c,"SELECT COUNT(*) FROM job_required_ability WHERE job_id="+jobId)==0,"cascade deletion");
                    boolean rejected=false;
                    try {writeClone(c,"INSERT INTO job_required_ability(job_id,raw_text) VALUES (9223372036854775807,'orphan')");}
                    catch(SQLException ex) {if(ex.getErrorCode()!=1452) throw ex; rejected=true;}
                    RecommendationInputMigration.require(rejected,"orphan foreign key must reject");
                } finally {
                    session.rollback(true);
                }
            }
        }
        ObjectMapper json=new ObjectMapper();
        Map<?,?> manifest=json.readValue(Files.readString(RecommendationInputMigration.BACKUP.resolve("manifest.json")),Map.class);
        Map<?,?> columns=json.readValue(Files.readString(RecommendationInputMigration.BACKUP.resolve("old-columns.json")),Map.class);
        Map<?,?> fingerprints=(Map<?,?>)manifest.get("oldDataFingerprints");
        try(Connection c=DriverManager.getConnection(url,username,password)) {
            c.setReadOnly(true); c.setAutoCommit(false);
            for(String schema:List.of(RecommendationInputMigration.SOURCE,RecommendationInputMigration.ISOLATED)) {
                c.setCatalog(schema);
                RecommendationInputMigration.checkDefaults(c);
                for(String table:RecommendationInputMigration.TABLES) {
                    @SuppressWarnings("unchecked") var names=(List<String>)columns.get(table);
                    String actual=RecommendationInputMigration.hash(RecommendationInputMigration.rows(c,table,names));
                    RecommendationInputMigration.require(actual.equals(((Map<?,?>)fingerprints.get(table)).get("sha256")),"Original-column fingerprint differs: "+schema+"."+table);
                }
            }
            c.rollback();
        }
        Map<String,Object> result=Map.of("date","2026-10-31","entityMappingChecks",9,"cascadeDeletion",true,"orphanRejected",true,"isolatedFixturesRolledBack",true,"sourceOldColumnsIdentical",true);
        Files.writeString(Path.of("target/recommendation-mapping-verification-2026-10-31.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        System.out.println(json.writeValueAsString(result));
    }
}
