package com.github.runit.settings;

import com.github.runit.i18n.RunItBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(name = "RunItSettings", storages = @Storage("runItSettings.xml"))
public final class RunItSettingsService implements PersistentStateComponent<RunItSettingsService.State> {
    public static class State {
        public String languageMode = RunItLanguageMode.FOLLOW_IDE.name();
    }

    private State state = new State();

    public static RunItSettingsService getInstance() {
        return ApplicationManager.getApplication().getService(RunItSettingsService.class);
    }

    @Override
    public @Nullable State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        XmlSerializerUtil.copyBean(state, this.state);
    }

    public RunItLanguageMode getLanguageMode() {
        try {
            return RunItLanguageMode.valueOf(state.languageMode);
        } catch (Exception ignored) {
            return RunItLanguageMode.FOLLOW_IDE;
        }
    }

    public void setLanguageMode(RunItLanguageMode languageMode) {
        state.languageMode = languageMode != null ? languageMode.name() : RunItLanguageMode.FOLLOW_IDE.name();
        RunItBundle.clearCache();
    }
}
