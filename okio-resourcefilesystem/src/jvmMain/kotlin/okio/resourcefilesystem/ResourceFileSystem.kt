/*
 * Copyright (C) 2021 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okio.resourcefilesystem

import okio.ExperimentalFileSystem
import okio.FileMetadata
import okio.FileNotFoundException
import okio.FileSystem.Companion.SYSTEM
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.Source
import okio.internal.ReadOnlyFilesystem
import okio.source
import java.io.InputStream
import java.net.URL

/**
 * A file system exposing something similar to the traditional java style resources.
 *
 * Both metadata and file listings are best effort, and will work better
 * for local project paths. The file system does not handle merging of
 * multiple paths from difference resources like overlapping Jar files.
 *
 * While a single `val everything = ResourceFileSystem()` close to the callsite would allow
 * code requiring classpath resources to access the implicit process classpath, this should be
 * avoided.
 *
 * Instead each module should provide a scoped and potentially restricted access to a subset that
 * it guarantees to provide. This allows individual modules flexibilities in how they provide their
 * resources, for example using a configured override over the defaults shipped inside the jars.
 *
 * ```
 * internal val OkHttpResources = ResourceFileSystem(paths = listOf("/okhttp3/internal".toPath()))
 * ...
 * val PUBLIC_SUFFIX_RESOURCE = "/okhttp3/internal/publicsuffixes/publicsuffixes.gz".toPath()
 * val resource = OkHttpResources.source(PUBLIC_SUFFIX_RESOURCE)
 * ```
 *
 * TODO hook into GraalVM building such that resources remain available
 */
@ExperimentalFileSystem
class ResourceFileSystem(val paths: List<Path>? = null) : ReadOnlyFilesystem() {
  override fun canonicalize(path: Path): Path {
    val cleaned = super.canonicalize(path)

    if (paths != null) {
      // TODO should this fail early on files that don't exist also
      paths.forEach {
        val pathBytes = path.toString()
        if (pathBytes.startsWith(it.toString())) {
          return cleaned
        }
      }

      throw IOException("Requested path $path is not within resource filesystem $paths")
    }

    return cleaned
  }

  override fun list(dir: Path): List<Path> {
    val systemPath = toSystemPath(dir) ?: throw IOException("not listable")

    return SYSTEM.list(systemPath).map { dir / it.name }
  }

  override fun metadataOrNull(path: Path): FileMetadata? {
    val systemPath = toSystemPath(path) ?: throw IOException("metadata for $path not supported")

    // TODO consider unwrapping jar urls
    return SYSTEM.metadataOrNull(systemPath)
  }

  override fun source(file: Path): Source {
    val resourceName = canonicalize(file).toString().substring(1)

    val stream: InputStream = this.javaClass.classLoader.getResourceAsStream(resourceName)
      ?: throw FileNotFoundException("file not found: $file")

    return stream.source()
  }

  /**
   * Return the [SYSTEM] path for a file if it is available. This should always be treated as best
   * effort since the FileSystem abstraction is designed to hide the specifics of where files
   * are loaded from e.g. from within a Zip file.
   */
  fun toSystemPath(path: Path): Path? {
    val resourceName = canonicalize(path).toString().substring(1)

    val url: URL = this.javaClass.classLoader.getResource(resourceName) ?: return null

    return if (url.protocol == "file") {
      url.path.toPath()
    } else {
      null
    }
  }

  companion object {
    val CLASSPATH = ResourceFileSystem()
  }
}