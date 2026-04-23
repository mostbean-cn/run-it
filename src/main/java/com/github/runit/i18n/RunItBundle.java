package com.github.runit.i18n;

import com.github.runit.settings.RunItLanguageMode;
import com.github.runit.settings.RunItSettingsService;
import com.intellij.DynamicBundle;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

public final class RunItBundle {
    private static final String BUNDLE = "messages.RunItBundle";
    private static final ResourceBundle.Control NO_FALLBACK_CONTROL = new ResourceBundle.Control() {
        @Override
        public Locale getFallbackLocale(String baseName, Locale locale) {
            return null;
        }
    };

    private RunItBundle() {
    }

    public static String message(String key, Object... params) {
        String pattern = getBundle(resolveLocale()).getString(key);
        return MessageFormat.format(pattern, params);
    }

    public static String message(RunItLanguageMode languageMode, String key, Object... params) {
        String pattern = getBundle(resolveLocale(languageMode)).getString(key);
        return MessageFormat.format(pattern, params);
    }

    public static void clearCache() {
        ResourceBundle.clearCache(RunItBundle.class.getClassLoader());
    }

    private static ResourceBundle getBundle(Locale locale) {
        return ResourceBundle.getBundle(BUNDLE, locale, RunItBundle.class.getClassLoader(), NO_FALLBACK_CONTROL);
    }

    private static Locale resolveLocale() {
        try {
            return resolveLocale(RunItSettingsService.getInstance().getLanguageMode());
        } catch (Exception ignored) {
            return resolveIdeLocale();
        }
    }

    private static Locale resolveLocale(RunItLanguageMode languageMode) {
        return switch (languageMode) {
            case ZH_CN -> Locale.SIMPLIFIED_CHINESE;
            case EN -> Locale.ENGLISH;
            case FOLLOW_IDE -> resolveIdeLocale();
        };
    }

    private static Locale resolveIdeLocale() {
        Locale locale = DynamicBundle.getLocale();
        String language = locale.getLanguage();
        return language != null && language.toLowerCase(Locale.ROOT).startsWith("zh")
                ? Locale.SIMPLIFIED_CHINESE
                : Locale.ENGLISH;
    }
}
