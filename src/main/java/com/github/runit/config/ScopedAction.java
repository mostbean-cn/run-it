package com.github.runit.config;

public class ScopedAction {
    private final ActionConfig actionConfig;
    private final ActionScope scope;
    private final int index;
    private final boolean enabledForCurrentProject;

    public ScopedAction(ActionConfig actionConfig, ActionScope scope, int index) {
        this(actionConfig, scope, index, true);
    }

    public ScopedAction(ActionConfig actionConfig, ActionScope scope, int index, boolean enabledForCurrentProject) {
        this.actionConfig = actionConfig;
        this.scope = scope;
        this.index = index;
        this.enabledForCurrentProject = enabledForCurrentProject;
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

    public boolean isEnabledForCurrentProject() {
        return enabledForCurrentProject;
    }
}
