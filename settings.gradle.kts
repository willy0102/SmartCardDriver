pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            authentication {
                create<BasicAuthentication>("basic")
            }
            url = uri("https://api.mapbox.com/downloads/v2/releases/maven")
            credentials {
                username = "mapbox"
                password = if (providers.gradleProperty("MAPBOX_DOWNLOADS_TOKEN").isPresent) {
                    providers.gradleProperty("MAPBOX_DOWNLOADS_TOKEN").get()
                } else {
                    System.getenv("MAPBOX_DOWNLOADS_TOKEN")
                } ?: throw IllegalArgumentException("MAPBOX_DOWNLOADS_TOKEN key is not specified")
            }
        }
    }
}

rootProject.name = "SmartCardDriver"
include(":app")
 