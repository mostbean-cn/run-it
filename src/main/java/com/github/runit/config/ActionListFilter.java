package com.github.runit.config;

import com.github.runit.i18n.RunItBundle;

public enum ActionListFilter {
    PROJECT_RELATED("filter.project_related"),
    PROJECT_UNRELATED("filter.project_unrelated"),
    ALL("filter.all");

    private final String messageKey;

    ActionListFilter(String messageKey) {
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
