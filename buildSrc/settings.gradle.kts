rootProject.name = "buildSrc"

dependencyResolutionManagement {
    versionCatalogs {
        // 与主工程共享同一份 gradle/libs.versions.toml。
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
