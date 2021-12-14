plugins {
    id("sap.commerce.build") version("3.6.0")
    id("sap.commerce.build.ccv2") version("3.6.0")
}
import mpern.sap.commerce.build.tasks.HybrisAntTask
import org.apache.tools.ant.taskdefs.condition.Os

//***********************
//* Script configurations
//***********************
val jacocoLib by configurations.creating
val extensionPack by configurations.creating

val DEPENDENCY_FOLDER = "dependencies"
repositories {
    flatDir { dirs(DEPENDENCY_FOLDER) }
    mavenCentral()
}

dependencies {
    dbDriver("mysql:mysql-connector-java:8.0.27")
    jacocoLib("org.jacoco:org.jacoco.agent:0.8.7")
}

//********************************
//* Helper atributes and functions
//********************************
fun createWindowsPath(path: String): String {
    return path.toString().replace("[/]".toRegex(), "\\")
}

//*******************************
//* Setup Environments properties
//*******************************

val envsDirPath = "hybris/config/environments"
val envValue = if (project.hasProperty("environment")) project.property("environment") else "local"

val optionalConfigDirPath = "hybris/config/optional-config"
val optionalConfigDir = file("${optionalConfigDirPath}")
val optionalConfigs = mapOf(
    "10-local.properties" to file("${envsDirPath}/commons/common.properties"),
    "20-local.properties" to file("${envsDirPath}/${envValue}/local.properties")
)

val extensionPacksPaths = mapOf(
    "hybris-cloud-extension-pack" to "commerce-cloud-extension-pack"
)

//************
//* Other Task
//************

// https://help.sap.com/viewer/b2f400d4c0414461a4bb7e115dccd779/LATEST/en-US/784f9480cf064d3b81af9cad5739fecc.html
tasks.register<Copy>("enableModeltMock") {
    from("hybris/bin/custom/extras/modelt/extensioninfo.disabled")
    into("hybris/bin/custom/extras/modelt/")
    rename { "extensioninfo.xml" }
}

//Optional: automate downloads from launchpad.support.sap.com
//  remove this block if you use something better, like Maven
//  Recommended reading: 
//  https://github.com/SAP/commerce-gradle-plugin/blob/master/docs/FAQ.md#downloadPlatform

/*if (project.hasProperty("sUser") && project.hasProperty("sUserPass")) {
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
}*/

//***************************
//* Set up Environment tasks
//***************************
val symlink = tasks.register("symlinkConfig") {
    doFirst {
        println("Generating Config SymLinks...")
    }
    
    dependsOn("validateEnvironment")
    mustRunAfter("bootstrapPlatform")
}
optionalConfigs.forEach{
    val singleLink = tasks.register<Exec>("symlink${it.key}") {
        doFirst {
            println("Generating SymLink for ${it.key}...")
        }
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

tasks.register("validateEnvironment") {
    doFirst {
        println("Validating environment...")
    }
    
    if (!file("${envsDirPath}/${envValue}/local.properties").exists()) {
        throw GradleException("Environment folder does not exist")
    }
}

tasks.register<Copy>("generateDeveloperProperties") {
    doFirst {
        println("Generating Developer properties...")
    }
    
    onlyIf {
        envValue == "local"
    }
    from("${envsDirPath}/local/sample-developer.properties")
    into("${optionalConfigDirPath}")
    rename { "99-local.properties" }

    dependsOn("validateEnvironment")
}

tasks.register<Copy>("copyConfigDir") {
    doFirst {
        println("Copy commons config directory...")
    }

    // coppy excluding local* files 
    from("${envsDirPath}/commons")
    into("hybris/config")
    exclude ( "common.properties", "localextensions.xml" )

    doLast {
        println("Linking localextensions...")
        exec {
            val extensionsPath = "environments/commons/localextensions.xml"
            if (Os.isFamily(Os.FAMILY_UNIX)) {
                commandLine("sh", "-c", "ln -sfn ${extensionsPath} localextensions.xml")
            } else {
                // https://blogs.windows.com/windowsdeveloper/2016/12/02/symlinks-windows-10/
                val windowsPath = createWindowsPath(extensionsPath)
                commandLine("cmd", "/c", """mklink /d "localextensions.xml" "${windowsPath}" """)
            }
            workingDir("hybris/config")
        }
    }

    mustRunAfter("symlinkConfig")
}

tasks.register<WriteProperties>("generateLocalProperties") {
    doFirst {
        println("Generating Local properties...")
    }
    
    comment = "GENEREATED AT " + java.time.Instant.now()
    outputFile = project.file("hybris/config/local.properties")

    property("hybris.optional.config.dir", "\${HYBRIS_CONFIG_DIR}/optional-config")

    dependsOn("generateDeveloperProperties")
    mustRunAfter("symlinkConfig", "copyConfigDir")
}

tasks.register<Copy>("copyJacocoLibs") {
    from(jacocoLib)
    into("hybris/bin/platform/lib")

    mustRunAfter("bootstrapPlatform")
}

tasks.register("generateEnvironment") {
    dependsOn("symlinkConfig", "copyConfigDir", "copyJacocoLibs", "configureSolrConfig", "generateLocalProperties")
    mustRunAfter("bootstrapPlatformExt")
}

val unpackManifestExtensionPacks = tasks.register("unpackManifestExtensionPacks") {
    doLast {
        println("Unpacking Extension Packs...")
        file("hybris/bin/modules/.lastupdate").createNewFile();
    }
}
CCV2.manifest.extensionPacks.forEach {
    // creates dependency of files
    extensionPack.defaultDependencies {
        add(project.dependencies.create("de.hybris.platform:${it.name}:${it.version}@zip"))
    }

    val unpackExtensionPack = tasks.register("unpackExtensionPack-${it.name}") {
        onlyIf {
            val isUnpacked = file("hybris/bin/modules/.lastupdate").exists()
            if (isUnpacked) {
                logger.lifecycle("Already unpacked!")
            }
            !isUnpacked
        }
        doLast {
            println("Unpacking [${it.name}] extension pack with version ${it.version}")
            copy {
                extensionPack.forEach { 
                    from(zipTree(it))
                }
                into(temporaryDir)     
            }
            copy {
                // Add support for different extension packs folder structure
                val extensionPackPath = extensionPacksPaths["${it.name}"]
                val extensionPackStructure = "${temporaryDir.toString()}/${extensionPackPath}" // path based on extension pack
                from(extensionPackStructure)
                into("hybris/bin/modules")
                duplicatesStrategy = DuplicatesStrategy.WARN
            }

            delete(temporaryDir)
        }
    }

    unpackManifestExtensionPacks.configure {
        dependsOn(unpackExtensionPack)
    }
}

tasks.register<Copy>("boostrapExtensionPacks") {
    onlyIf {
        CCV2.manifest.extensionPacks != null || !CCV2.manifest.extensionPacks.isEmpty()
    }
    dependsOn("unpackManifestExtensionPacks")
    mustRunAfter("bootstrapPlatform")
}

tasks.register("bootstrapPlatformExt") {
    dependsOn("bootstrapPlatform", "boostrapExtensionPacks")
}

tasks.named("installManifestAddons") {
    mustRunAfter("generateLocalProperties")
}

//***************************
//* Main Setup task
//***************************
tasks.register("setupEnvironment") {
    group = "SAP Commerce"
    description = "Setup local development"

    dependsOn("bootstrapPlatformExt", "generateEnvironment","installManifestAddons", "enableModeltMock")
}

//**************************
//* Solr Setup Configuration
//**************************
tasks.register<HybrisAntTask>("startSolr") {
    args("startSolrServers")
}
tasks.register<HybrisAntTask>("stopSolr") {
    args("stopSolrServers")
    mustRunAfter("startSolr")
}
tasks.register("startStopSolr") {
    dependsOn("startSolr", "stopSolr")
}
tasks.register("configureSolrConfig") {
    dependsOn("symlinkSolrConfig")
    group = "Setup"
    description = "Prepare Solr configuration"

    mustRunAfter("copyConfigDir")
}
tasks.register("clearDefaultSolrConfig") {
    dependsOn("startStopSolr")
    doLast {
        val configSetsDir = file("hybris/config/solr/instances/default/configsets");
        if (configSetsDir.exists()) {
            delete(configSetsDir)
        }
    }
}
tasks.register<Exec>("symlinkSolrConfig") {
    dependsOn("clearDefaultSolrConfig")

    val solrPath = "../../../../../solr/server/solr/configsets"
    if (Os.isFamily(Os.FAMILY_UNIX)) {
        commandLine("sh", "-c", "ln -sfn ${solrPath} configsets")
    } else {
        // https://blogs.windows.com/windowsdeveloper/2016/12/02/symlinks-windows-10/
        val windowsPath = createWindowsPath(solrPath)
        commandLine("cmd", "/c", """mklink /d "configsets" "${windowsPath}" """)
    }
    workingDir("hybris/config/solr/instances/default")
}