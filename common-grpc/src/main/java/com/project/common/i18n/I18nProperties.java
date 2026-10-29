package com.project.common.i18n;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * <pre>
 * app:
 *   i18n:
 *     default-locale: vi            # request không gửi / gửi ngôn ngữ không hỗ trợ
 *     supported-locales: [vi, en]
 *     basenames: [i18n/messages]    # file của service: i18n/messages_vi.properties, i18n/messages_en.properties
 * </pre>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.i18n")
public class I18nProperties {

    private Locale defaultLocale = Locale.forLanguageTag("vi");

    private List<Locale> supportedLocales = new ArrayList<>(List.of(Locale.forLanguageTag("vi"), Locale.ENGLISH));

    /** Message của service, tra trước message chung của common-grpc ({@value Messages#COMMON_BASENAME}). */
    private List<String> basenames = new ArrayList<>(List.of("i18n/messages"));
}
