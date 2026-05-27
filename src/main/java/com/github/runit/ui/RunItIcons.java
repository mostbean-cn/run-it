package com.github.runit.ui;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RunItIcons {
    public static final Icon TOOLBAR = IconLoader.getIcon("/icons/runIt.svg", RunItIcons.class);
    public static final Icon RUN_COMMAND = com.intellij.icons.AllIcons.Actions.Execute;

    private static final List<IconDefinition> ICON_DEFINITIONS = createIconDefinitions();
    private static final Map<String, IconDefinition> ICON_DEFINITIONS_BY_KEY = createIconDefinitionsByKey();
    private static final Map<String, Icon> ICONS = createIconsByKey();

    private RunItIcons() {
    }

    public static Map<String, Icon> availableIcons() {
        return ICONS;
    }

    public static List<IconDefinition> availableIconDefinitions(IconCategory category) {
        List<IconDefinition> definitions = new ArrayList<>();
        for (IconDefinition definition : ICON_DEFINITIONS) {
            if (definition.category() == category) {
                definitions.add(definition);
            }
        }
        return Collections.unmodifiableList(definitions);
    }

    public static IconDefinition findDefinition(String iconName) {
        if (iconName == null || iconName.isBlank()) {
            return ICON_DEFINITIONS_BY_KEY.get("run");
        }
        return ICON_DEFINITIONS_BY_KEY.getOrDefault(iconName, ICON_DEFINITIONS_BY_KEY.get("run"));
    }

    public static Icon resolve(String iconName) {
        if (iconName == null || iconName.isBlank()) {
            return RUN_COMMAND;
        }
        return ICONS.getOrDefault(iconName, RUN_COMMAND);
    }

    private static List<IconDefinition> createIconDefinitions() {
        List<IconDefinition> definitions = new ArrayList<>();
        definitions.add(new IconDefinition("run", RUN_COMMAND, IconCategory.DEV));
        definitions.add(new IconDefinition("clean", com.intellij.icons.AllIcons.Actions.GC, IconCategory.DEV));
        definitions.add(new IconDefinition("build", com.intellij.icons.AllIcons.Actions.Compile, IconCategory.DEV));
        definitions.add(new IconDefinition("test", com.intellij.icons.AllIcons.Actions.RunAll, IconCategory.DEV));
        definitions.add(new IconDefinition("deploy", com.intellij.icons.AllIcons.Nodes.Deploy, IconCategory.DEV));
        definitions.add(new IconDefinition("terminal", com.intellij.icons.AllIcons.Toolwindows.ToolWindowRun, IconCategory.DEV));
        definitions.add(new IconDefinition("debug", com.intellij.icons.AllIcons.Actions.StartDebugger, IconCategory.DEV));
        definitions.add(new IconDefinition("refresh", com.intellij.icons.AllIcons.Actions.Refresh, IconCategory.DEV));

        definitions.add(new IconDefinition("anthropic", loadBundledIcon("anthropic"), IconCategory.AI));
        definitions.add(new IconDefinition("deepSeek", loadBundledIcon("deepSeek"), IconCategory.AI));
        definitions.add(new IconDefinition("gemini", loadBundledIcon("gemini"), IconCategory.AI));
        definitions.add(new IconDefinition("openAI", loadBundledIcon("openAI"), IconCategory.AI));
        definitions.add(new IconDefinition("openRouter", loadBundledIcon("openRouter"), IconCategory.AI));
        definitions.add(new IconDefinition("mistral", loadBundledIcon("mistral"), IconCategory.AI));
        definitions.add(new IconDefinition("ollama", loadBundledIcon("ollama"), IconCategory.AI));
        return Collections.unmodifiableList(definitions);
    }

    private static Map<String, IconDefinition> createIconDefinitionsByKey() {
        Map<String, IconDefinition> definitionsByKey = new LinkedHashMap<>();
        for (IconDefinition definition : ICON_DEFINITIONS) {
            definitionsByKey.put(definition.key(), definition);
        }
        return Collections.unmodifiableMap(definitionsByKey);
    }

    private static Map<String, Icon> createIconsByKey() {
        Map<String, Icon> icons = new LinkedHashMap<>();
        for (IconDefinition definition : ICON_DEFINITIONS) {
            icons.put(definition.key(), definition.icon());
        }
        return Collections.unmodifiableMap(icons);
    }

    private static Icon loadBundledIcon(String name) {
        return IconLoader.getIcon("/icons/ai/" + name + ".svg", RunItIcons.class);
    }

    public enum IconCategory {
        DEV("icon.category.dev"),
        AI("icon.category.ai");

        private final String messageKey;

        IconCategory(String messageKey) {
            this.messageKey = messageKey;
        }

        public String getMessageKey() {
            return messageKey;
        }
    }

    public record IconDefinition(String key, Icon icon, IconCategory category) {
    }
}
