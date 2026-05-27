package com.github.runit.config;

import java.util.ArrayList;
import java.util.List;

public class RunItConfig {
    public int version = 1;
    public List<ActionConfig> actions = new ArrayList<>();

    public RunItConfig() {
    }

    public String toToml() {
        StringBuilder sb = new StringBuilder();
        sb.append("version = ").append(version).append("\n\n");
        for (ActionConfig action : actions) {
            sb.append("[[actions]]\n");
            sb.append("id = \"").append(escape(action.id)).append("\"\n");
            sb.append("name = \"").append(escape(action.name)).append("\"\n");
            sb.append("icon = \"").append(escape(action.icon)).append("\"\n");
            sb.append("scope = \"").append(escape(action.scope)).append("\"\n");
            if (action.projectKey != null && !action.projectKey.isBlank()) {
                sb.append("projectKey = \"").append(escape(action.projectKey)).append("\"\n");
            }
            if (action.disabledProjectKeys != null && !action.disabledProjectKeys.isEmpty()) {
                sb.append("disabledProjectKeys = [");
                for (int i = 0; i < action.disabledProjectKeys.size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append("\"").append(escape(action.disabledProjectKeys.get(i))).append("\"");
                }
                sb.append("]\n");
            }
            if (canUseLiteralString(action.command)) {
                sb.append("command = '").append(action.command).append("'\n\n");
            } else if (canUseMultilineLiteralString(action.command)) {
                sb.append("command = '''").append(action.command).append("'''\n\n");
            } else {
                sb.append("command = \"").append(escape(action.command)).append("\"\n\n");
            }
        }
        return sb.toString();
    }

    private boolean canUseLiteralString(String s) {
        if (s == null) return false;
        return !s.contains("'") && !s.contains("\n") && !s.contains("\r");
    }

    private boolean canUseMultilineLiteralString(String s) {
        if (s == null) return false;
        return !s.contains("'''");
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
