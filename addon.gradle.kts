import com.gtnewhorizons.gtnhgradle.GTNHGradlePlugin
import com.gtnewhorizons.retrofuturagradle.mcp.ReobfuscatedJar
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import net.darkhax.curseforgegradle.CurseForgeGradlePlugin
import net.darkhax.curseforgegradle.TaskPublishCurseForge
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar

abstract class GenerateCurseForgeMcmodInfoTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputFile: RegularFileProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:Input
    abstract val limitedMessage: Property<String>

    @TaskAction
    fun generate() {
        val parsed = JsonSlurper().parse(inputFile.get().asFile) as MutableMap<*, *>
        val modList = parsed["modList"] as? List<*> ?: emptyList<Any>()
        val message = limitedMessage.get()

        for (modEntry in modList) {
            @Suppress("UNCHECKED_CAST")
            val mod = modEntry as? MutableMap<String, Any?> ?: continue
            val currentDescription = mod["description"]?.toString().orEmpty()
            mod["description"] = if (currentDescription.contains(message)) {
                currentDescription
            } else if (currentDescription.isBlank()) {
                message
            } else {
                "$currentDescription\n\n$message"
            }
        }

        val target = outputFile.get().asFile
        target.parentFile.mkdirs()
        target.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(parsed)) + "\n")
    }
}

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

// commonmark 0.22+ targets Java 11. Transform only this dependency to Java 8;
// VintageResourcify itself stays on the normal Jabel/Java 8 build path.
plugins.apply(xyz.wagyourtail.jvmdg.gradle.JVMDowngraderPlugin::class.java)
val downgradedCommonmark = configurations.create("downgradedCommonmark") {
    isCanBeConsumed = false
    isCanBeResolved = true
}
dependencies.add(downgradedCommonmark.name, "org.commonmark:commonmark:0.29.0")
extensions.configure<xyz.wagyourtail.jvmdg.gradle.JVMDowngraderExtension>("jvmdg") {
    downgradeTo.set(JavaVersion.VERSION_1_8)
    dg(downgradedCommonmark, shade = false)
}
dependencies.add("compileOnly", "org.commonmark:commonmark:0.29.0")
dependencies.add("shadowImplementation", files(downgradedCommonmark))

val curseForgeLimitedMessage =
    "This CurseForge build only supports downloads from Modrinth and CurseForge."
val configuredCurseForgeProjectId = providers.gradleProperty("curseForgeProjectId")
    .orNull
    ?.trim()
    .orEmpty()
val configuredCurseForgeRelations = providers.gradleProperty("curseForgeRelations")
    .orNull
    ?.trim()
    .orEmpty()
val configuredMinecraftVersion = providers.gradleProperty("minecraftVersion")
    .orNull
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?: "1.7.10"
val configuredUsesMixins = providers.gradleProperty("usesMixins")
    .orNull
    ?.trim()
    ?.equals("true", ignoreCase = true) == true

extensions.findByType<GTNHGradlePlugin.GTNHExtension>()
    ?.configuration
    ?.curseForgeProjectId = ""

val sourceSets = extensions.getByType<SourceSetContainer>()
val mainSourceSet = sourceSets.named("main").get()
val curseForgeReplacementSourceSet = sourceSets.create("curseforgeReplacement") {
    java.srcDir("src/curseforge/java")
    resources.setSrcDirs(emptyList<String>())
    compileClasspath = mainSourceSet.compileClasspath + mainSourceSet.output
    runtimeClasspath = output + compileClasspath
}

tasks.named<JavaCompile>(curseForgeReplacementSourceSet.compileJavaTaskName).configure {
    sourceCompatibility = tasks.named<JavaCompile>("compileJava").get().sourceCompatibility
    targetCompatibility = tasks.named<JavaCompile>("compileJava").get().targetCompatibility
    options.encoding = "UTF-8"
    options.annotationProcessorPath = configurations.named("annotationProcessor").get()
}

val curseForgeMcmodInfoFile = layout.buildDirectory.file("generated/curseforgeResources/mcmod.info")
val generateCurseForgeMcmodInfo = tasks.register<GenerateCurseForgeMcmodInfoTask>("generateCurseForgeMcmodInfo") {
    val processedMcmodInfo = layout.buildDirectory.file("resources/main/mcmod.info")

    dependsOn(tasks.named("processResources"))
    inputFile.set(processedMcmodInfo)
    outputFile.set(curseForgeMcmodInfoFile)
    limitedMessage.set(curseForgeLimitedMessage)
}

val curseForgeJar = tasks.register<Jar>("curseForgeJar") {
    group = "build"
    description = "Assembles a CurseForge-specific dev jar that only enables Modrinth and CurseForge downloads."

    val normalJar = tasks.named<AbstractArchiveTask>("shadowJar")

    dependsOn(normalJar, tasks.named(curseForgeReplacementSourceSet.classesTaskName), generateCurseForgeMcmodInfo)
    archiveClassifier.set("curseforge-dev")
    manifest {
        attributes(
            "FMLCorePluginContainsFMLMod" to "true",
            "FMLCorePlugin" to "dev.dediamondpro.resourcify.core.EarlyMixinLoader",
            "TweakClass" to "org.spongepowered.asm.launch.MixinTweaker",
            "MixinConfigs" to "mixins.vintage-resourcify.json",
            "ForceLoadAsMod" to "true",
        )
    }

    from(normalJar.map { zipTree(it.archiveFile.get().asFile) }) {
        exclude(
            "META-INF/MANIFEST.MF",
            "mcmod.info",
            "dev/dediamondpro/resourcify/config/ConfiguredPlatforms*.class",
            "dev/dediamondpro/resourcify/services/DistributionPolicy.class",
        )
    }
    from(curseForgeReplacementSourceSet.output.classesDirs) {
        include(
            "dev/dediamondpro/resourcify/config/ConfiguredPlatforms.class",
            "dev/dediamondpro/resourcify/services/DistributionPolicy.class",
        )
    }
    from(curseForgeMcmodInfoFile)
}

val reobfCurseForgeJar = tasks.named<ReobfuscatedJar>("reobfCurseForgeJar") {
    archiveClassifier.set("curseforge")
    getExtraSrgFiles().from(layout.buildDirectory.file("tmp/mixins/mixins.srg"))
}

tasks.named("assemble").configure {
    dependsOn(reobfCurseForgeJar)
}

if (configuredCurseForgeProjectId.isNotEmpty()) {
    plugins.apply(CurseForgeGradlePlugin::class.java)

    val changelogFile = file(System.getenv("CHANGELOG_FILE") ?: "CHANGELOG.md")
    val modVersionProvider = providers.provider {
        val extras = extensions.extraProperties
        if (extras.has("modVersion")) {
            extras.get("modVersion").toString()
        } else {
            version.toString()
        }
    }

    val publishCurseforge = tasks.register<TaskPublishCurseForge>("publishCurseforge") {
        group = "publishing"
        description = "Publishes the CurseForge-specific VintageResourcify jar to CurseForge."
        dependsOn(reobfCurseForgeJar)

        apiToken = providers.environmentVariable("CURSEFORGE_TOKEN")
        disableVersionDetection()
        val artifact = upload(configuredCurseForgeProjectId, reobfCurseForgeJar.flatMap { it.archiveFile })
        if (changelogFile.exists()) {
            artifact.changelogType = "markdown"
            artifact.changelog = changelogFile
        }
        artifact.releaseType = modVersionProvider.map { if (it.endsWith("-pre")) "beta" else "release" }
        artifact.addGameVersion(configuredMinecraftVersion, "Forge")
        artifact.addModLoader("Forge")

        configuredCurseForgeRelations.split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { relation ->
                val parts = relation.split(":", limit = 2)
                if (parts.size == 2) {
                    artifact.addRelation(parts[1], parts[0])
                }
            }
        if (configuredUsesMixins) {
            artifact.addRelation("unimixins", "requiredDependency")
        }
    }

    if (providers.environmentVariable("CURSEFORGE_TOKEN").orNull != null) {
        tasks.named("publish").configure {
            dependsOn(publishCurseforge)
        }
    }
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
