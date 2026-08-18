package com.munehisa.backend.infra.i18n;

import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.LocaleResolver;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class I18nConfigTest {

    private final I18nConfig config = new I18nConfig();

    @Test
    void localeResolver_resolvesPtBr_whenHeaderIsPtBr() {
        LocaleResolver resolver = config.localeResolver();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "pt-BR");

        assertEquals(Locale.forLanguageTag("pt-BR"), resolver.resolveLocale(request));
    }

    @Test
    void localeResolver_resolvesEnglish_whenHeaderIsEn() {
        LocaleResolver resolver = config.localeResolver();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "en");

        assertEquals(Locale.ENGLISH, resolver.resolveLocale(request));
    }

    @Test
    void localeResolver_resolvesEnglish_whenHeaderIsMissing() {
        LocaleResolver resolver = config.localeResolver();
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertEquals(Locale.ENGLISH, resolver.resolveLocale(request));
    }

    @Test
    void localeResolver_resolvesEnglish_whenHeaderIsUnsupported() {
        LocaleResolver resolver = config.localeResolver();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "fr");

        assertEquals(Locale.ENGLISH, resolver.resolveLocale(request));
    }

    @Test
    void messageSource_resolvesKnownKey_inBothLocales() {
        MessageSource messageSource = config.messageSource();

        String english = messageSource.getMessage(
                "error.insufficientCashBalance", new Object[]{"150.00", "50.00"}, Locale.ENGLISH);
        String portuguese = messageSource.getMessage(
                "error.insufficientCashBalance", new Object[]{"150.00", "50.00"}, Locale.forLanguageTag("pt-BR"));

        assertEquals("Withdrawal amount (150.00) exceeds available cash balance (50.00)", english);
        assertNotEquals(english, portuguese);
    }
}
