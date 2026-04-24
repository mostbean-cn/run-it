import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.1.0"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        create(providers.gradleProperty("intellijPlatform.type"), providers.gradleProperty("intellijPlatform.version"))
        instrumentationTools()
        bundledPlugins(
            listOf(
                "com.intellij.java",
                "org.jetbrains.plugins.terminal",
            )
        )
        testFramework(TestFrameworkType.Platform)
    }
    implementation("com.moandjiezana.toml:toml4j:0.7.2")
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }

    patchPluginXml {
        sinceBuild.set("232")
        untilBuild.set(provider { null })
    }

    test {
        useJUnitPlatform()
    }
}
