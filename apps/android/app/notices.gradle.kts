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
            fun pom(group: String, name: String, version: String): java.io.File {
                val dep = dependencies.create(mapOf("group" to group, "name" to name, "version" to version, "ext" to "pom"))
                val file = configurations.detachedConfiguration(dep).apply { isTransitive = false }.singleFile
                file.copyTo(out.resolve("$group.$name-$version.pom.txt"), overwrite = true)
                return file
            }
            var doc = factory.newDocumentBuilder().parse(pom(id.group, id.name, id.version))
            var licenses = doc.getElementsByTagName("license")
            var depth = 0
            while (licenses.length == 0 && depth++ < 8) {
                val parents = doc.getElementsByTagName("parent")
                check(parents.length > 0) { "No declared or inherited runtime license in POM: $id" }
                val parent = parents.item(0) as org.w3c.dom.Element
                fun field(key: String) = parent.getElementsByTagName(key).item(0).textContent.trim()
                doc = factory.newDocumentBuilder().parse(pom(field("groupId"), field("artifactId"), field("version")))
                licenses = doc.getElementsByTagName("license")
            }
            check(licenses.length > 0) { "No declared/inherited runtime license in POM: $id" }
            records.append("$id\n")
            for (i in 0 until licenses.length) records.append(licenses.item(i).textContent.trim().replace(Regex("\\s+"), " ")).append('\n')
            val scm = doc.getElementsByTagName("scm")
            if (scm.length > 0) records.append("Source: ").append(scm.item(0).textContent.trim().replace(Regex("\\s+"), " ")).append('\n')
            // All current Maven runtime modules declare Apache 2.0; fail rather than silently substituting a license.
            for (i in 0 until licenses.length) check(licenses.item(i).textContent.contains("apache", ignoreCase = true)) { "Review new runtime license before distributing $id" }
            records.append("License text: ../ZXing-LICENSE.txt (Apache License 2.0)\n\n")
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
