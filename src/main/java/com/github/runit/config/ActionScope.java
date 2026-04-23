package com.github.runit.config;

import com.github.runit.i18n.RunItBundle;

public enum ActionScope {
    PROJECT("scope.project"),
    GLOBAL("scope.global");

    private final String messageKey;

    ActionScope(String messageKey) {
        this.messageKey = messageKey;
    }

    public String getDisplayName() {
        return RunItBundle.message(messageKey);
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
}
