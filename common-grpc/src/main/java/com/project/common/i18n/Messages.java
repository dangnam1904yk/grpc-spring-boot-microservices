package com.project.common.i18n;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * Dịch message theo ngôn ngữ của request / gRPC call đang xử lý.
 * <ul>
 * <li>REST: header {@code Accept-Language} (LocaleResolver của {@link I18nAutoConfiguration}).</li>
 * <li>gRPC: metadata {@code accept-language}, service gọi tự chuyển tiếp ngôn ngữ của request gốc
 * ({@link GrpcLocaleClientInterceptor} / {@link GrpcLocaleServerInterceptor}).</li>
 * </ul>
 * Ngôn ngữ không hỗ trợ hoặc không gửi thì dùng {@code app.i18n.default-locale}.
 */
public class Messages {

    /** Message của chính common-grpc (lỗi chung): {@code i18n/common_vi.properties}, {@code i18n/common_en.properties}. */
    public static final String COMMON_BASENAME = "i18n/common";

    private final MessageSource source;
    private final Locale defaultLocale;
    private final List<Locale> supportedLocales;

    public Messages(MessageSource source, I18nProperties properties) {
        this.source = source;
        this.defaultLocale = properties.getDefaultLocale();
        this.supportedLocales = List.copyOf(properties.getSupportedLocales());
    }

    /** Chỉ có message chung, khi service không bật {@link I18nAutoConfiguration} (test auto-config lẻ). */
    public static Messages commonOnly() {
        I18nProperties properties = new I18nProperties();
        properties.setBasenames(List.of());
        return new Messages(messageSource(properties), properties);
    }

    /** Message của service trước, message chung sau: service đặt lại key chung được. */
    static ResourceBundleMessageSource messageSource(I18nProperties properties) {
        List<String> basenames = new ArrayList<>(properties.getBasenames());
        basenames.add(COMMON_BASENAME);
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasenames(basenames.toArray(String[]::new));
        source.setDefaultEncoding("UTF-8");
        // Ngôn ngữ không có file thì về ngôn ngữ mặc định, không phụ thuộc locale của máy chủ
        source.setFallbackToSystemLocale(false);
        source.setDefaultLocale(properties.getDefaultLocale());
        return source;
    }

    /** Ngôn ngữ của request / call hiện tại, đã quy về ngôn ngữ được hỗ trợ. */
    public Locale locale() {
        LocaleContext context = LocaleContextHolder.getLocaleContext();
        return resolve(context != null ? context.getLocale() : null);
    }

    public Locale resolve(Locale requested) {
        if (requested == null) {
            return defaultLocale;
        }
        return supportedLocales.stream()
                .filter(l -> l.getLanguage().equals(requested.getLanguage()))
                .findFirst()
                .orElse(defaultLocale);
    }

    /** Giá trị header {@code Accept-Language} ({@code "en-US,en;q=0.9"}); sai định dạng thì như không gửi. */
    public Locale resolve(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return defaultLocale;
        }
        try {
            // lookup (không phải filter): "vi-VN" vẫn khớp "vi"
            Locale match = Locale.lookup(Locale.LanguageRange.parse(acceptLanguage), supportedLocales);
            return match != null ? match : defaultLocale;
        } catch (IllegalArgumentException e) {
            return defaultLocale;
        }
    }

    public String get(MessageSourceResolvable message) {
        return get(message, locale());
    }

    public String get(MessageSourceResolvable message, Locale locale) {
        return source.getMessage(message, locale);
    }

    /** Key hoặc chuỗi thường. */
    public String get(String key, Object... args) {
        return get(LocalizedMessage.of(key, args));
    }

    public Map<String, String> get(Map<String, ? extends MessageSourceResolvable> messages) {
        Locale locale = locale();
        Map<String, String> result = new LinkedHashMap<>();
        messages.forEach((field, message) -> result.put(field, get(message, locale)));
        return result;
    }
}
