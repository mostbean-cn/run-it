package com.github.runit.executor;

import com.github.runit.config.ActionConfig;
import com.github.runit.i18n.RunItBundle;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.openapi.project.Project;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import org.jetbrains.annotations.NotNull;

public class CommandExecutor {

    public static void execute(@NotNull Project project, @NotNull ActionConfig action) {
        String command = action.command.trim();
        if (command.isEmpty()) {
            return;
        }

        if (action.background) {
            executeInBackground(project, action.name, command);
            return;
        }

        String title = RunItBundle.message("command.title", action.name);
        if (TerminalCommandRunner.execute(project, title, command)) {
            return;
        }

        executeInRunWindow(project, title, command);
    }

    private static void executeInBackground(Project project, String title, String command) {
        try {
            GeneralCommandLine cmdLine = buildBackgroundCommandLine(project, command);
            OSProcessHandler processHandler = new OSProcessHandler(cmdLine);

            processHandler.addProcessListener(new ProcessListener() {
                @Override
                public void processTerminated(@NotNull ProcessEvent event) {
                    int exitCode = event.getExitCode();
                    if (exitCode != 0) {
                        showNotification(project,
                                RunItBundle.message("command.title", title),
                                "Background command failed with exit code " + exitCode,
                                NotificationType.WARNING);
                    }
                }
            });

            processHandler.startNotify();
        } catch (ExecutionException e) {
            showNotification(project,
                    RunItBundle.message("command.title", title),
                    "Failed to start background command: " + e.getMessage(),
                    NotificationType.ERROR);
        }
    }

    private static GeneralCommandLine buildBackgroundCommandLine(Project project, String command) {
        GeneralCommandLine cmdLine = new GeneralCommandLine();
        cmdLine.setWorkDirectory(project.getBasePath());

        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            cmdLine.setExePath("powershell.exe");
            cmdLine.addParameter("-NoProfile");
            cmdLine.addParameter("-NonInteractive");
            cmdLine.addParameter("-Command");
            cmdLine.addParameter(command);
        } else {
            cmdLine.setExePath("/bin/sh");
            cmdLine.addParameter("-c");
            cmdLine.addParameter(command);
        }
        return cmdLine;
    }

    private static void showNotification(Project project, String title, String content, NotificationType type) {
        new Notification("RunIt", title, content, type).notify(project);
    }

    private static void executeInRunWindow(Project project, String title, String command) {
        ConsoleView consoleView = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();

        try {
            GeneralCommandLine cmdLine = buildCommandLine(project, command);
            OSProcessHandler processHandler = new OSProcessHandler(cmdLine);
            processHandler.addProcessListener(new ProcessListener() {
                @Override
                public void processTerminated(@NotNull ProcessEvent event) {
                    int exitCode = event.getExitCode();
                    String msg = RunItBundle.message("command.process.finished", exitCode);
                    consoleView.print(msg, exitCode == 0
                            ? ConsoleViewContentType.NORMAL_OUTPUT
                            : ConsoleViewContentType.ERROR_OUTPUT);
                }
            });

            consoleView.attachToProcess(processHandler);

            RunContentDescriptor descriptor = new RunContentDescriptor(consoleView, processHandler, consoleView.getComponent(), title);
            descriptor.setAutoFocusContent(true);

            Executor executor = DefaultRunExecutor.getRunExecutorInstance();
            RunContentManager.getInstance(project).showRunContent(executor, descriptor);

            processHandler.startNotify();

        } catch (ExecutionException e) {
            consoleView.print(RunItBundle.message("command.process.start_failed", e.getMessage()), ConsoleViewContentType.ERROR_OUTPUT);
            RunContentDescriptor descriptor = new RunContentDescriptor(consoleView, null, consoleView.getComponent(), title);
            Executor executor = DefaultRunExecutor.getRunExecutorInstance();
            RunContentManager.getInstance(project).showRunContent(executor, descriptor);
        }
    }

    private static GeneralCommandLine buildCommandLine(Project project, String command) {
        GeneralCommandLine cmdLine = new GeneralCommandLine();
        cmdLine.setWorkDirectory(project.getBasePath());

        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            cmdLine.setExePath("cmd.exe");
            cmdLine.addParameter("/c");
            cmdLine.addParameter(command);
        } else {
            cmdLine.setExePath("/bin/sh");
            cmdLine.addParameter("-c");
            cmdLine.addParameter(command);
        }
        return cmdLine;
    }
}
