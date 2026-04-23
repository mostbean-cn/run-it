package com.github.runit.config;

public class ScopedAction {
    private final ActionConfig actionConfig;
    private final ActionScope scope;
    private final int index;

    public ScopedAction(ActionConfig actionConfig, ActionScope scope, int index) {
        this.actionConfig = actionConfig;
        this.scope = scope;
        this.index = index;
    }

    public ActionConfig getActionConfig() {
        return actionConfig;
    }

    public ActionScope getScope() {
        return scope;
    }

    public int getIndex() {
        return index;
    }
}
