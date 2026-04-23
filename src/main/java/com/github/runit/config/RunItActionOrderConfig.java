package com.github.runit.config;

import com.moandjiezana.toml.Toml;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RunItActionOrderConfig {
    private static final int VERSION = 1;

    private final Map<String, List<String>> ordersByProject = new LinkedHashMap<>();

    public static RunItActionOrderConfig load(File file) {
        RunItActionOrderConfig config = new RunItActionOrderConfig();
        if (!file.exists()) {
            return config;
        }

        Toml toml = new Toml().read(file);
        List<Toml> projects = toml.getTables("projects");
        if (projects == null) {
            return config;
        }

        for (Toml projectTable : projects) {
            String projectKey = trimToNull(projectTable.getString("key"));
            if (projectKey == null) {
                continue;
            }

            List<String> actionIds = new ArrayList<>();
            List<Object> rawActionIds = projectTable.getList("actionIds");
            if (rawActionIds != null) {
                for (Object rawActionId : rawActionIds) {
                    if (rawActionId instanceof String actionId) {
                        String normalized = trimToNull(actionId);
                        if (normalized != null) {
                            actionIds.add(normalized);
                        }
                    }
                }
            }

            config.ordersByProject.put(projectKey, actionIds);
        }
        return config;
    }

    public List<String> getActionOrder(String projectKey) {
        List<String> actionIds = ordersByProject.get(projectKey);
        return actionIds != null ? new ArrayList<>(actionIds) : new ArrayList<>();
    }

    public void setActionOrder(String projectKey, List<String> actionIds) {
        if (actionIds == null || actionIds.isEmpty()) {
            ordersByProject.remove(projectKey);
            return;
        }
        ordersByProject.put(projectKey, new ArrayList<>(actionIds));
    }

    public String toToml() {
        StringBuilder sb = new StringBuilder();
        sb.append("version = ").append(VERSION).append("\n\n");
        for (Map.Entry<String, List<String>> entry : ordersByProject.entrySet()) {
            sb.append("[[projects]]\n");
            sb.append("key = \"").append(escape(entry.getKey())).append("\"\n");
            sb.append("actionIds = [");
            List<String> actionIds = entry.getValue();
            for (int i = 0; i < actionIds.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append("\"").append(escape(actionIds.get(i))).append("\"");
            }
            sb.append("]\n\n");
        }
        return sb.toString();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
