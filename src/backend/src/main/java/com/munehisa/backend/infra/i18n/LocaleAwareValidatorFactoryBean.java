package com.munehisa.backend.infra.i18n;

import jakarta.validation.Configuration;
import org.hibernate.validator.HibernateValidatorConfiguration;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

public class LocaleAwareValidatorFactoryBean extends LocalValidatorFactoryBean {

    @Override
    protected void postProcessConfiguration(Configuration<?> configuration) {
        super.postProcessConfiguration(configuration);
        if (configuration instanceof HibernateValidatorConfiguration hibernateConfig) {
            hibernateConfig.localeResolver(context -> LocaleContextHolder.getLocale());
        }
    }
}
