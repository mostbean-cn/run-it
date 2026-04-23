package com.github.runit.ui;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RunItIcons {
    public static final Icon TOOLBAR = IconLoader.getIcon("/icons/runIt.svg", RunItIcons.class);
    public static final Icon RUN_COMMAND = com.intellij.icons.AllIcons.Actions.Execute;

    private static final Map<String, Icon> ICONS = createIcons();

    private RunItIcons() {
    }

    public static Map<String, Icon> availableIcons() {
        return ICONS;
    }

    public static Icon resolve(String iconName) {
        if (iconName == null || iconName.isBlank()) {
            return RUN_COMMAND;
        }
        return ICONS.getOrDefault(iconName, RUN_COMMAND);
    }

    private static Map<String, Icon> createIcons() {
        Map<String, Icon> icons = new LinkedHashMap<>();
        icons.put("run", RUN_COMMAND);
        icons.put("clean", com.intellij.icons.AllIcons.Actions.GC);
        icons.put("build", com.intellij.icons.AllIcons.Actions.Compile);
        icons.put("test", com.intellij.icons.AllIcons.Actions.RunAll);
        icons.put("deploy", com.intellij.icons.AllIcons.Nodes.Deploy);
        icons.put("terminal", com.intellij.icons.AllIcons.Toolwindows.ToolWindowRun);
        icons.put("debug", com.intellij.icons.AllIcons.Actions.StartDebugger);
        icons.put("refresh", com.intellij.icons.AllIcons.Actions.Refresh);
        return Collections.unmodifiableMap(icons);
    }
}
