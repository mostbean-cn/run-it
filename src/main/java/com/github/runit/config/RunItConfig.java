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
            if (action != null && action.isDamaged() && action.rawTomlBlock != null && !action.rawTomlBlock.isBlank()) {
                sb.append(action.rawTomlBlock);
                if (!action.rawTomlBlock.endsWith("\n")) {
                    sb.append("\n");
                }
                sb.append("\n");
                continue;
            }
            sb.append("[[actions]]\n");
            sb.append("id = ").append(TomlStringUtil.quote(action.id)).append("\n");
            sb.append("name = ").append(TomlStringUtil.quote(action.name)).append("\n");
            sb.append("icon = ").append(TomlStringUtil.quote(action.icon)).append("\n");
            sb.append("scope = ").append(TomlStringUtil.quote(action.scope)).append("\n");
            if (action.projectKey != null && !action.projectKey.isBlank()) {
                sb.append("projectKey = ").append(TomlStringUtil.quote(action.projectKey)).append("\n");
            }
            if (action.disabledProjectKeys != null && !action.disabledProjectKeys.isEmpty()) {
                sb.append("disabledProjectKeys = [");
                for (int i = 0; i < action.disabledProjectKeys.size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(TomlStringUtil.quote(action.disabledProjectKeys.get(i)));
                }
                sb.append("]\n");
            }
            sb.append("command = ").append(TomlStringUtil.quote(action.command)).append("\n\n");
        }
        return sb.toString();
    }
}
