package com.github.runit.settings;

public enum RunItLanguageMode {
    FOLLOW_IDE("settings.language.follow_ide"),
    ZH_CN("settings.language.zh_cn"),
    EN("settings.language.en");

    private final String messageKey;

    RunItLanguageMode(String messageKey) {
        this.messageKey = messageKey;
    }

    public String getMessageKey() {
        return messageKey;
    }
}
