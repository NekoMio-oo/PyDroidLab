package com.pydroidlab.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class WorkspaceFileItem(
    val file: File,
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long
)

class FileRepository(context: Context) {

    val workspaceRoot: File = File(context.filesDir, "workspace").apply {
        if (!exists()) mkdirs()
    }

    val userSitePackagesDir: File = File(context.filesDir, "python/site-packages").apply {
        if (!exists()) mkdirs()
    }

    val pipCacheDir: File = File(context.cacheDir, "pip-cache").apply {
        if (!exists()) mkdirs()
    }

    /**
     * Seeds a default `main.py` and an `examples/` folder in the workspace on first launch.
     */
    suspend fun ensureInitialWorkspace(): File = withContext(Dispatchers.IO) {
        val defaultMain = File(workspaceRoot, "main.py")
        if (!defaultMain.exists()) {
            defaultMain.writeText(SampleRepository.samples.first().code, Charsets.UTF_8)
        }
        val examplesDir = File(workspaceRoot, "examples")
        if (!examplesDir.exists()) {
            examplesDir.mkdirs()
            SampleRepository.samples.forEach { sample ->
                val target = File(examplesDir, sample.fileName)
                if (!target.exists()) {
                    target.writeText(sample.code, Charsets.UTF_8)
                }
            }
        }
        defaultMain
    }

    suspend fun listDirectory(dir: File): List<WorkspaceFileItem> = withContext(Dispatchers.IO) {
        val safeDir = if (dir.exists() && dir.isDirectory && isInsideWorkspace(dir)) {
            dir
        } else {
            workspaceRoot
        }
        val children = safeDir.listFiles()?.toList().orEmpty()
        children
            .sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
            .map { f ->
                WorkspaceFileItem(
                    file = f,
                    name = f.name,
                    path = f.absolutePath,
                    isDirectory = f.isDirectory,
                    sizeBytes = if (f.isFile) f.length() else 0L,
                    lastModified = f.lastModified()
                )
            }
    }

    suspend fun listAllDirectories(): List<File> = withContext(Dispatchers.IO) {
        val result = mutableListOf(workspaceRoot)
        fun walk(current: File) {
            current.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name.lowercase() }?.forEach { sub ->
                result.add(sub)
                walk(sub)
            }
        }
        walk(workspaceRoot)
        result
    }

    suspend fun readFile(file: File): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(isInsideWorkspace(file)) { "Access outside workspace is not permitted" }
            file.readText(Charsets.UTF_8)
        }
    }

    suspend fun writeFile(file: File, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(isInsideWorkspace(file)) { "Access outside workspace is not permitted" }
            file.parentFile?.mkdirs()
            file.writeText(content, Charsets.UTF_8)
        }
    }

    suspend fun createNewFile(parentDir: File, fileName: String, initialContent: String = ""): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cleanName = sanitizeName(fileName)
                require(cleanName.isNotEmpty()) { "文件名不能为空" }
                val finalName = if (cleanName.contains('.')) cleanName else "$cleanName.py"
                val target = File(parentDir, finalName)
                require(isInsideWorkspace(target)) { "非法路径" }
                require(!target.exists()) { "文件 '$finalName' 已存在" }
                target.writeText(initialContent, Charsets.UTF_8)
                target
            }
        }

    suspend fun createNewFolder(parentDir: File, folderName: String): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cleanName = sanitizeName(folderName)
                require(cleanName.isNotEmpty()) { "文件夹名称不能为空" }
                val target = File(parentDir, cleanName)
                require(isInsideWorkspace(target)) { "非法路径" }
                require(!target.exists()) { "文件夹 '$cleanName' 已存在" }
                require(target.mkdirs()) { "创建文件夹失败" }
                target
            }
        }

    suspend fun renameItem(source: File, newName: String): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cleanName = sanitizeName(newName)
                require(cleanName.isNotEmpty()) { "新名称不能为空" }
                require(source != workspaceRoot) { "不能重命名工作区根目录" }
                val target = File(source.parentFile, cleanName)
                require(isInsideWorkspace(target)) { "非法路径" }
                require(!target.exists()) { "目标名称 '$cleanName' 已存在" }
                require(source.renameTo(target)) { "重命名失败" }
                target
            }
        }

    suspend fun moveItem(source: File, targetDirectory: File): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(source != workspaceRoot) { "不能移动工作区根目录" }
                require(targetDirectory.isDirectory && isInsideWorkspace(targetDirectory)) { "无效的目标文件夹" }
                val dest = File(targetDirectory, source.name)
                require(!dest.canonicalPath.startsWith(source.canonicalPath + File.separator)) {
                    "不能将文件夹移动到其自身的子目录中"
                }
                require(!dest.exists()) { "目标文件夹中已存在同名项 '${source.name}'" }
                if (!source.renameTo(dest)) {
                    if (source.isDirectory) {
                        source.copyRecursively(dest, overwrite = false)
                        source.deleteRecursively()
                    } else {
                        source.copyTo(dest, overwrite = false)
                        source.delete()
                    }
                }
                dest
            }
        }

    suspend fun deleteItem(target: File): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(target != workspaceRoot) { "不能删除工作区根目录" }
            require(isInsideWorkspace(target)) { "非法路径" }
            val deleted = if (target.isDirectory) target.deleteRecursively() else target.delete()
            require(deleted) { "删除失败" }
        }
    }

    fun relativePath(file: File): String {
        val rootPath = workspaceRoot.canonicalPath
        val filePath = file.canonicalPath
        return if (filePath == rootPath) {
            "/workspace"
        } else if (filePath.startsWith(rootPath)) {
            "/workspace" + filePath.removePrefix(rootPath)
        } else {
            file.name
        }
    }

    private fun isInsideWorkspace(file: File): Boolean {
        val rootCanonical = workspaceRoot.canonicalPath
        val targetCanonical = file.canonicalPath
        return targetCanonical == rootCanonical || targetCanonical.startsWith("$rootCanonical${File.separator}")
    }

    private fun sanitizeName(raw: String): String {
        return raw.trim().replace("/", "").replace("\\", "").replace("..", "")
    }
}
