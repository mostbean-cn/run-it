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
            sb.append("command = \"").append(escape(action.command)).append("\"\n\n");
        }
        return sb.toString();
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
