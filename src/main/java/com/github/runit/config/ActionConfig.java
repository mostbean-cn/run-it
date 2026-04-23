package com.github.runit.config;

import java.util.Objects;

public class ActionConfig {
    public String name = "";
    public String icon = "run";
    public String command = "";

    public ActionConfig() {
    }

    public ActionConfig(String name, String icon, String command) {
        this.name = name;
        this.icon = icon;
        this.command = command;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ActionConfig that = (ActionConfig) o;
        return Objects.equals(name, that.name) && Objects.equals(icon, that.icon) && Objects.equals(command, that.command);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, icon, command);
    }
}
