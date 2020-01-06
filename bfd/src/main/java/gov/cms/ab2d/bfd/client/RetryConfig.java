package gov.cms.ab2d.bfd.client;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;


@Configuration
@PropertySource("classpath:application.bfd.properties")
public class RetryConfig {

    @Value("${bfd.retry.maxAttempts}")
    private int retryMaxAttempts;

    @Value("${bfd.retry.backoffDelay}")
    private long retryBackoffDelay;


    @Bean
    RetryTemplate retryTemplate() {
        RetryTemplate template = new RetryTemplate();
        template.setRetryPolicy(buildSimpleRetryPolicy());
        template.setBackOffPolicy(buildBackoffPolicy());

        return template;
    }


    private SimpleRetryPolicy buildSimpleRetryPolicy() {
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(retryMaxAttempts);
        return retryPolicy;
    }

    private FixedBackOffPolicy buildBackoffPolicy() {
        FixedBackOffPolicy fixedBackOffPolicy = new FixedBackOffPolicy();
        fixedBackOffPolicy.setBackOffPeriod(retryBackoffDelay);
        return fixedBackOffPolicy;
    }


}
