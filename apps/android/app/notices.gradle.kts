import javax.xml.parsers.DocumentBuilderFactory
import java.util.zip.ZipFile

tasks.register("collectAndroidNotices") {
    doLast {
        val out = file("src/main/assets/licenses/maven")
        out.mkdirs()
        val records = StringBuilder("RADVPN Android runtime dependency notices\nGenerated from the resolved debugRuntimeClasspath. RADVPN: MIT.\n\n")
        val factory = DocumentBuilderFactory.newInstance()
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        configurations.getByName("debugRuntimeClasspath").resolvedConfiguration.resolvedArtifacts.sortedBy { it.moduleVersion.id.toString() }.forEach { a ->
            val id = a.moduleVersion.id
            val pomDependency = dependencies.create(mapOf("group" to id.group, "name" to id.name, "version" to id.version, "ext" to "pom"))
            val cfg = configurations.detachedConfiguration(pomDependency).apply { isTransitive = false }
            val pom = cfg.singleFile
            val doc = factory.newDocumentBuilder().parse(pom)
            val licenses = doc.getElementsByTagName("license")
            check(licenses.length > 0) { "No declared runtime license in POM: $id" }
            records.append("$id\n")
            for (i in 0 until licenses.length) records.append(licenses.item(i).textContent.trim().replace(Regex("\\s+"), " ")).append('\n')
            val scm = doc.getElementsByTagName("scm")
            if (scm.length > 0) records.append("Source: ").append(scm.item(0).textContent.trim().replace(Regex("\\s+"), " ")).append('\n')
            // All current Maven runtime modules declare Apache 2.0; fail rather than silently substituting a license.
            for (i in 0 until licenses.length) check(licenses.item(i).textContent.contains("apache", ignoreCase = true)) { "Review new runtime license before distributing $id" }
            records.append("License text: ../ZXing-LICENSE.txt (Apache License 2.0)\n\n")
            pom.copyTo(out.resolve("${id.group}.${id.name}-${id.version}.pom.txt"), overwrite = true)
            ZipFile(a.file).use { zip ->
                zip.entries().asSequence().filter { !it.isDirectory && it.name.startsWith("META-INF/") && (it.name.contains("LICENSE",true) || it.name.contains("NOTICE",true)) }.forEach { entry ->
                    zip.getInputStream(entry).use { input -> out.resolve("${id.group}.${id.name}-${id.version}-${entry.name.replace('/', '_')}").outputStream().use { input.copyTo(it) } }
                }
            }
        }
        file("src/main/assets/licenses/ANDROID-NOTICES.txt").writeText(records.toString()+"Go/Xray licenses and exact versions: go/index.json and go/*.\nXray source: https://github.com/XTLS/Xray-core/tree/1b1647cd9ddf0abc34b981d4611fc03bcac1ca74\nCorresponding RADVPN sources: https://github.com/salehsayyadi/RADVPN\nNo modified upstream Xray files. No Wintun or .NET code is bundled in Android.\n")
    }
}
tasks.named("preBuild").configure { dependsOn("collectAndroidNotices") }
