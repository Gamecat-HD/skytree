plugins {
    id("net.fabricmc.fabric-loom") version "1.19.4"
}

version = "${property("mod_version")}+${property("minecraft_version")}"
base.archivesName = "skytree"

repositories {
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") }
        filter { includeGroup("maven.modrinth") }
    }
    exclusiveContent {
        forRepository {
            maven("https://maven.teamresourceful.com/repository/maven-public/") {
                // One module holds jars for several MC versions; pick the file by classifier instead.
                metadataSources { artifact() }
            }
        }
        filter { includeGroup("tech.thatgravyboat") }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    implementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")

    // SkyRecipes provides the item/recipe data, RRV the overlay we hook into.
    compileOnly("maven.modrinth:enhanced-skyrecipes:${property("skyrecipes_version")}")
    compileOnly("maven.modrinth:rrv:${property("rrv_version")}")
    // Optional: sacks/storage/vault counts (SkyBlockAPI ships inside SkyOcean).
    compileOnly("tech.thatgravyboat:skyblock-api:${property("skyblock_api_version")}:${property("minecraft_version")}@jar")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

loom {
    accessWidenerPath = file("src/main/resources/skytree.ct")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
    withSourcesJar()
}

tasks.test { useJUnitPlatform() }

tasks.processResources {
    val v = project.version.toString()
    inputs.property("version", v)
    filesMatching("fabric.mod.json") { expand("version" to v) }
}
