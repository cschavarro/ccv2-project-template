plugins {
    id("sap.commerce.build") version("3.6.0")
    id("sap.commerce.build.ccv2") version("3.6.0")
}
import mpern.sap.commerce.build.tasks.HybrisAntTask
import org.apache.tools.ant.taskdefs.condition.Os

val DEPENDENCY_FOLDER = "dependencies"
repositories {
    flatDir { dirs(DEPENDENCY_FOLDER) }
    mavenCentral()
}

dependencies {
    dbDriver("mysql:mysql-connector-java:5.1.34")
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

    from("${envsDirPath}/commons")
    into("hybris/config")
    exclude ( "common.properties" )
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

tasks.register("generateEnvironment") {
    dependsOn("symlinkConfig", "copyConfigDir", "configureSolrConfig", "generateLocalProperties")
}

val unpackManifestExtensionPacks = tasks.register("unpackManifestExtensionPacks") {
    doFirst {
        println("Unpacking Extension Packs...")
    }
}
CCV2.manifest.extensionPacks.forEach {
    val unpackExtensionPack = tasks.register<Copy>("unpackExtensionPack-${it.name}") {
        doFirst {
            println("Unzipping [${it.name}] extension pack for version ${it.version}")
        }
        val extensionPackZip = file("${DEPENDENCY_FOLDER}/${it.name}-${it.version}.zip")
        if (!extensionPackZip.exists()) {
            throw GradleException("Extension pack does not exist")
        }

        from(zipTree(extensionPackZip))
        into("${DEPENDENCY_FOLDER}/temp")     
    }
    
    val moveExtensionPack = tasks.register<Copy>("moveExtensionPack-${it.name}") {
        doFirst {
            println("Moving [${it.name}] extension pack")
        }
        // TODO Add support for 20XX Intergration packs folder structure
        // Structure is diferent

        from("${DEPENDENCY_FOLDER}/temp/commerce-cloud-extension-pack")
        into("hybris/bin/modules")
        duplicatesStrategy = DuplicatesStrategy.WARN
        
        doLast {
            delete("${DEPENDENCY_FOLDER}/temp")
        }
        mustRunAfter("unpackExtensionPack-${it.name}")
    }

    unpackManifestExtensionPacks.configure {
        dependsOn(unpackExtensionPack, moveExtensionPack)
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

    if (Os.isFamily(Os.FAMILY_UNIX)) {
        commandLine("sh", "-c", "ln -sfn ../../../../../solr/server/solr/configsets configsets")
    } else {
        // https://blogs.windows.com/windowsdeveloper/2016/12/02/symlinks-windows-10/
        commandLine("cmd", "/c", """mklink /d "configsets" "..\\..\\..\\..\\..\\solr\\server\\solr\\configsets" """)
    }
    workingDir("hybris/config/solr/instances/default")
}