import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository

// Pin Fent Maven as the topmost repo so FentLib resolves reliably even after
// GTNH's settingsconvention prepends its own mirrors. Mirrors FentLib's own
// addon.gradle.kts verbatim.
val fentMavenName = "Fent Maven"
val fentMavenUrl = uri("https://maven.fentanylsolutions.org/releases")

fun RepositoryHandler.keepFentMavenFirst() {
    fun currentFentRepo(): MavenArtifactRepository? = withType(MavenArtifactRepository::class.java)
        .firstOrNull { it.url == fentMavenUrl || it.name == fentMavenName }

    fun promoteFentRepo() {
        val fentRepo = currentFentRepo() ?: maven {
            name = fentMavenName
            url = fentMavenUrl
        }
        if (firstOrNull() !== fentRepo) {
            remove(fentRepo)
            addFirst(fentRepo)
        }
    }

    promoteFentRepo()
    whenObjectAdded {
        promoteFentRepo()
    }
}

gradle.allprojects {
    repositories.keepFentMavenFirst()
    buildscript.repositories.keepFentMavenFirst()
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>().configureEach {
    relocate("org.commonmark", "dev.dediamondpro.resourcify.libs.commonmark")
}

// ktfmt (the Kotlin formatter spotless uses) crashes on JDK 25 with
// NoClassDefFoundError com.facebook.ktfmt.format.Parser because of how
// ktfmt initialises its kotlinx-coroutines fork. Disable just the Kotlin
// step so spotlessApply works for everything else.
tasks.matching { it.name == "spotlessKotlin" || it.name == "spotlessKotlinApply" || it.name == "spotlessKotlinCheck" || it.name == "spotlessKotlinDiagnose" }
    .configureEach { enabled = false }

tasks.withType<JavaExec>().configureEach {
    if (name.startsWith("runServer")) {
        doFirst("resourcifyStripClientOnlyMods") {
            classpath = classpath.filter { file ->
                val n = file.name
                !n.contains("ModularUI2", ignoreCase = true) &&
                    !n.contains("angelica", ignoreCase = true)
            }
        }
    }
}
