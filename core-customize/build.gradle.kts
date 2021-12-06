plugins {
    id("sap.commerce.build") version("3.6.0")
    id("sap.commerce.build.ccv2") version("3.6.0")
    id("de.undercouch.download") version("4.1.2")
}
import mpern.sap.commerce.build.tasks.HybrisAntTask
import org.apache.tools.ant.taskdefs.condition.Os

import de.undercouch.gradle.tasks.download.Download
import de.undercouch.gradle.tasks.download.Verify

val DEPENDENCY_FOLDER = "dependencies"
repositories {
    flatDir { dirs(DEPENDENCY_FOLDER) }
    mavenCentral()
}

//Optional: automate downloads from launchpad.support.sap.com
//  remove this block if you use something better, like Maven
//  Recommended reading: 
//  https://github.com/SAP/commerce-gradle-plugin/blob/master/docs/FAQ.md#downloadPlatform
if (project.hasProperty("sUser") && project.hasProperty("sUserPass")) {
    val SUSER = project.property("sUser") as String
    val SUSERPASS = project.property("sUserPass") as String

    val COMMERCE_VERSION = CCV2.manifest.commerceSuiteVersion
    tasks.register<Download>("downloadPlatform") {
        src("https://softwaredownloads.sap.com/file/0020000000989902021")
        dest(file("${DEPENDENCY_FOLDER}/hybris-commerce-suite-${COMMERCE_VERSION}.zip"))
        username(SUSER)
        password(SUSERPASS)
        overwrite(false)
        tempAndMove(true)
        onlyIfModified(true)
        useETag(true)
    }

    tasks.register<Verify>("downloadAndVerifyPlatform") {
        dependsOn("downloadPlatform") 
        src(file("dependencies/hybris-commerce-suite-${COMMERCE_VERSION}.zip"))
        algorithm("SHA-256")
        checksum("add4f893b349770c3f918042784b6c08ed7114ba5c98231f7de7e725b2a02803")
    }

    tasks.named("bootstrapPlatform") {
        dependsOn("downloadAndVerifyPlatform")
    }

    //check if Integration Extension Pack is configured and download it too
    if (CCV2.manifest.extensionPacks.any{"hybris-commerce-integrations".equals(it.name)}) {
        val INTEXTPACK_VERSION = CCV2.manifest.extensionPacks.first{"hybris-commerce-integrations".equals(it.name)}.version
        tasks.register<Download>("downloadIntExtPack") {
            src("https://softwaredownloads.sap.com/file/0020000001002692021")
            dest(file("${DEPENDENCY_FOLDER}/hybris-commerce-integrations-${INTEXTPACK_VERSION}.zip"))
            username(SUSER)
            password(SUSERPASS)
            overwrite(false)
            tempAndMove(true)
            onlyIfModified(true)
            useETag(true)
        }

        tasks.register<Verify>("downloadAndVerifyIntExtPack") {
            dependsOn("downloadIntExtPack")
            src(file("${DEPENDENCY_FOLDER}/hybris-commerce-integrations-${INTEXTPACK_VERSION}.zip"))
            algorithm("SHA-256")
            checksum("352fcb5b9b7b58ebc50f61873351e88ab343cbbd28955fd3332653d2284c266c")
        }

        tasks.named("bootstrapPlatform") {
            dependsOn("downloadAndVerifyIntExtPack")
        }
    }
}

var envValue = "local";
if (project.hasProperty("environment")) {
    envValue = project.property("environment") as String
}

val optionalConfigDir = file("hybris/config/optional-config")
val optionalConfigs = mapOf(
    "10-local.properties" to file("hybris/config/environments/commons/common.properties"),
    "20-local.properties" to file("hybris/config/environments/${envValue}/local.properties")
)
val symlink = tasks.register("symlinkConfig") {
    println("Generating Config SymLinks")
}
optionalConfigs.forEach{
    val singleLink = tasks.register<Exec>("symlink${it.key}") {
        println("Generating SymLink for ${it.key}")
        val path = it.value.relativeTo(optionalConfigDir)
        if (Os.isFamily(Os.FAMILY_UNIX)) {
            commandLine("sh", "-c", "ln -sfn ${path} ${it.key}")
        } else {
            // https://blogs.windows.com/windowsdeveloper/2016/12/02/symlinks-windows-10/
            val windowsPath = path.toString().replace("[/]".toRegex(), "\\")
            commandLine("cmd", "/c", """mklink /d "${it.key}" "${windowsPath}" """)
        }
        workingDir(optionalConfigDir)
    }
    symlink.configure {
        dependsOn(singleLink)
    }
}

tasks.register<WriteProperties>("generateLocalProperties") {
    comment = "GENEREATED AT " + java.time.Instant.now()
    outputFile = project.file("hybris/config/local.properties")

    property("hybris.optional.config.dir", "\${HYBRIS_CONFIG_DIR}/local-config")
}

// https://help.sap.com/viewer/b2f400d4c0414461a4bb7e115dccd779/LATEST/en-US/784f9480cf064d3b81af9cad5739fecc.html
tasks.register<Copy>("enableModeltMock") {
    from("hybris/bin/custom/extras/modelt/extensioninfo.disabled")
    into("hybris/bin/custom/extras/modelt/")
    rename { "extensioninfo.xml" }
}

tasks.named("installManifestAddons") {
    mustRunAfter("generateLocalProperties")
}

tasks.register("createLocalSymLinks") {
    // mustRunAfter("bootstrapPlatform")
    group = "SAP Commerce"
    description = "Setup local development"
}

tasks.register("setupEnvironment") {
    group = "SAP Commerce"
    description = "Setup local development"

    dependsOn("bootstrapPlatform", "symlinkConfig", "generateLocalProperties","installManifestAddons", "enableModeltMock")
}

// tasks.register("setupLocalDevelopment") {
//     group = "SAP Commerce"
//     description = "Setup local development"
//     dependsOn("bootstrapPlatform", "generateLocalProperties", "installManifestAddons", "enableModeltMock")
// }