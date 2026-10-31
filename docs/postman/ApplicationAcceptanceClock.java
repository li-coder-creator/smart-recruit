import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.SpringApplication;
import com.recruit.smartrecruit.SmartRecruitApplication;

/** Test-only launcher: never compiled into src/main or enabled on the developer service. */
public final class ApplicationAcceptanceClock {
    public static final String LOCAL_NOW = "2026-10-28T12:34:56";
    public static void main(String[] args) {
        boolean isolatedDatabase = false;
        boolean isolatedPort = false;
        for (String argument : args) {
            if (argument.startsWith("--spring.datasource.url=")) {
                isolatedDatabase = argument.matches("--spring\\.datasource\\.url=jdbc:mysql://[^/]+/smart_recruit_acceptance_[0-9]+\\?.+");
            }
            if (argument.equals("--server.port=18087")) isolatedPort = true;
        }
        if (!isolatedDatabase || !isolatedPort) throw new IllegalArgumentException("Isolated database and port required");
        SpringApplication application = new SpringApplication(SmartRecruitApplication.class);
        application.addInitializers(context -> context.addBeanFactoryPostProcessor(factory -> {
            BeanDefinitionRegistry registry = (BeanDefinitionRegistry) factory;
            if (!registry.containsBeanDefinition("applicationClock")) throw new IllegalStateException("Expected applicationClock");
            registry.removeBeanDefinition("applicationClock");
            registry.registerBeanDefinition("applicationClock", new RootBeanDefinition(Clock.class,
                    () -> Clock.fixed(Instant.parse("2026-10-28T04:34:56Z"), ZoneId.of("Asia/Shanghai"))));
        }));
        application.run(args);
    }
}
