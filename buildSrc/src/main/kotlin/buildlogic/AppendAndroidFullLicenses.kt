/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation (version 3 only).
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package buildlogic

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.io.Serializable
import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider

/** 补充数据是官方任务的输入，合并后的同一个输出随任务缓存，不再登记第二个同名 Android 资源。 */
class AppendAndroidFullLicenses(
    private val manifest: Provider<RegularFile>,
    private val supplementalManifest: Provider<RegularFile>,
) : Action<Task>, Serializable {
    @Suppress("UNCHECKED_CAST")
    override fun execute(task: Task) {
        val parser = JsonSlurper()
        val output = manifest.get().asFile
        val original = parser.parse(output, "UTF-8") as Map<String, Any?>
        val supplemental = parser.parse(supplementalManifest.get().asFile, "UTF-8") as Map<String, Any?>
        val libraries = (original["libraries"] as List<Map<String, Any?>>).toMutableList()
        val licenses = (original["licenses"] as Map<String, Any?>).toMutableMap()
        val ids = libraries.mapTo(hashSetOf()) { it["uniqueId"] }
        (supplemental["licenses"] as Map<String, Map<String, Any?>>).forEach { (id, license) ->
            check((license["content"] as? String)?.isNotBlank() == true) { "Missing original license text: $id" }
            check(id !in licenses || licenses[id] == license) { "Conflicting license identifier: $id" }
            licenses[id] = license
        }
        (supplemental["libraries"] as List<Map<String, Any?>>).forEach { library ->
            val id = library["uniqueId"] as String
            check(id.startsWith("android-full:")) { "Unexpected supplemental component: $id" }
            check(ids.add(id)) { "Duplicate Android Full component: $id" }
            val licenseIds = library["licenses"] as List<String>
            check(licenseIds.isNotEmpty() && licenseIds.all { it in licenses }) { "Missing licenses for $id" }
            libraries.add(library)
        }
        val result = original.toMutableMap()
        result["libraries"] = libraries.sortedBy { it["uniqueId"] as String }
        result["licenses"] = licenses
        output.writeText(JsonOutput.toJson(result), Charsets.UTF_8)
    }
}
