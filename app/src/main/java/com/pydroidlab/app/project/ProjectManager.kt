package com.pydroidlab.app.project

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Project = a directory under the app's projects root. */
data class Project(val name: String, val dir: File)

class ProjectManager(private val context: Context) {

    private val projectsRoot: File
        get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "projects").apply { mkdirs() }

    private val prefs get() = context.getSharedPreferences("pdl_projects", Context.MODE_PRIVATE)

    fun listProjects(): List<Project> =
        projectsRoot.listFiles()?.filter { it.isDirectory }
            ?.map { Project(it.name, it) }?.sortedBy { it.name } ?: emptyList()

    fun createProject(name: String): Project {
        val dir = File(projectsRoot, sanitize(name)).apply { mkdirs() }
        // Seed a hello-world main.py for new projects.
        val main = File(dir, "main.py")
        if (!main.exists()) {
            main.writeText("# ${name}\n\ndef main():\n    print(\"Hello from ${name}!\")\n\nif __name__ == \"__main__\":\n    main()\n")
        }
        return Project(dir.name, dir)
    }

    fun deleteProject(project: Project) = project.dir.deleteRecursively()

    fun renameProject(project: Project, newName: String): Project {
        val target = File(projectsRoot, sanitize(newName))
        project.dir.renameTo(target)
        return Project(target.name, target)
    }

    var lastOpenedProject: String?
        get() = prefs.getString("last_project", null)
        set(v) = prefs.edit().putString("last_project", v).apply()

    private fun sanitize(name: String) = name.replace(Regex("[^A-Za-z0-9_\\- ]"), "_").trim()

    // ---- file operations ---------------------------------------------------

    fun listFiles(dir: File): List<File> =
        dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()

    suspend fun createFile(dir: File, name: String, template: String = ""): File = withContext(Dispatchers.IO) {
        File(dir, name).apply {
            parentFile?.mkdirs()
            writeText(template)
        }
    }

    fun createDir(dir: File, name: String): File = File(dir, name).apply { mkdirs() }

    fun rename(file: File, newName: String): File {
        val target = File(file.parentFile, newName)
        file.renameTo(target)
        return target
    }

    fun delete(file: File) = file.deleteRecursively()

    fun copy(src: File, destDir: File): File {
        val target = File(destDir, src.name)
        if (src.isDirectory) src.copyRecursively(target, overwrite = true)
        else src.copyTo(target, overwrite = true)
        return target
    }

    fun move(src: File, destDir: File): File {
        val target = copy(src, destDir)
        src.deleteRecursively()
        return target
    }

    // ---- SAF import --------------------------------------------------------

    suspend fun importFromSaf(uri: Uri, destDir: File): File? = withContext(Dispatchers.IO) {
        try {
            val name = queryDisplayName(uri) ?: "imported_${System.currentTimeMillis()}"
            val target = File(destDir, name)
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            target
        } catch (e: Exception) {
            null
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, null, null, null, null) ?: return null
        cursor.use {
            val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (it.moveToFirst() && idx >= 0) return it.getString(idx)
        }
        return null
    }

    // ---- backup ------------------------------------------------------------

    suspend fun exportProjectZip(project: Project, destZip: File): File = withContext(Dispatchers.IO) {
        ZipOutputStream(destZip.outputStream().buffered()).use { zos ->
            project.dir.walkTopDown().forEach { f ->
                if (f.isFile) {
                    val rel = f.relativeTo(project.dir).path
                    zos.putNextEntry(ZipEntry(rel))
                    f.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
        destZip
    }
}
