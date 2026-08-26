plugins {
    kotlin("jvm")
    application
}

group = "com.nestmusic"
version = "1.0.0"

// Repositories are defined centrally in settings.gradle.kts.

kotlin { jvmToolchain(21) }

application {
    mainClass.set("com.nestmusic.desktop.MainKt")
}

tasks.jar {
    manifest { attributes["Main-Class"] = application.mainClass.get() }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}

// Builds a native Windows installer when run on Windows with JDK 21+.
// jpackage is included in every full JDK distribution.
tasks.register<Exec>("packageWindows") {
    group = "distribution"
    description = "Create a Nest Music .exe installer using jpackage (Windows only)"
    dependsOn(tasks.jar)
    notCompatibleWithConfigurationCache("jpackage uses Exec configuration at execution time")
    onlyIf { System.getProperty("os.name").lowercase().contains("windows") }

    val outputDir = layout.buildDirectory.dir("windows-installer").get().asFile
    doFirst {
        outputDir.mkdirs()
        commandLine(
            "jpackage",
            "--type", "exe",
            "--name", "Nest Music",
            "--app-version", project.version.toString(),
            "--description", "Nest Music desktop companion",
            "--vendor", "Nest Music",
            "--input", tasks.jar.get().destinationDirectory.get().asFile.absolutePath,
            "--main-jar", tasks.jar.get().archiveFileName.get(),
            "--main-class", application.mainClass.get(),
            "--dest", outputDir.absolutePath,
            "--win-menu",
            "--win-shortcut",
            "--win-dir-chooser",
        )
    }
}
