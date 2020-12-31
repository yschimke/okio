/*
 * Copyright (C) 2020 Square, Inc.
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
package okio

import kotlin.jvm.JvmName

/**
 * A [Filesystem] that forwards calls to another, intended for subclassing.
 *
 * ### Fault Injection
 *
 * You can use this to deterministically trigger filesystem failures in tests. This is useful to
 * confirm that your program behaves correctly even if its filesystem operations fail. For example,
 * this subclass fails every access of files named `unlucky.txt`:
 *
 * ```
 * val faultyFilesystem = object : ForwardingFilesystem(FileSystem.SYSTEM) {
 *   override fun onPathParameter(path: Path, functionName: String, parameterName: String): Path {
 *     if (path.name == "unlucky.txt") throw IOException("synthetic failure!")
 *     return path
 *   }
 * }
 * ```
 *
 * You can fail specific operations by overriding them directly:
 *
 * ```
 * val faultyFilesystem = object : ForwardingFilesystem(FileSystem.SYSTEM) {
 *   override fun delete(path: Path) {
 *     throw IOException("synthetic failure!")
 *   }
 * }
 * ```
 *
 * ### Observability
 *
 * You can extend this to verify which files your program accesses. This is a testing filesystem
 * that records accesses as they happen:
 *
 * ```
 * class LoggingFilesystem : ForwardingFilesystem(Filesystem.SYSTEM) {
 *   val log = mutableListOf<String>()
 *
 *   override fun onPathParameter(path: Path, functionName: String, parameterName: String): Path {
 *     log += "$functionName($parameterName=$path)"
 *     return path
 *   }
 * }
 * ```
 *
 * This makes it easy for tests to assert exactly which files were accessed.
 *
 * ```
 * @Test
 * fun testMergeJsonReports() {
 *   createSampleJsonReports()
 *   loggingFilesystem.log.clear()
 *
 *   mergeJsonReports()
 *
 *   assertThat(loggingFilesystem.log).containsExactly(
 *     "list(dir=json_reports)",
 *     "source(file=json_reports/2020-10.json)",
 *     "source(file=json_reports/2020-12.json)",
 *     "source(file=json_reports/2020-11.json)",
 *     "sink(file=json_reports/2020-all.json)"
 *   )
 * }
 * ```
 *
 * ### Transformations
 *
 * Subclasses can transform file names and content.
 *
 * For example, your program may be written to operate on a well-known directory like `/etc/` or
 * `/System`. You can rewrite paths to make such operations safer to test.
 *
 * You may also transform file content to apply application-layer encryption or compression. This
 * is particularly useful in situations where it's difficult or impossible to enable those features
 * in the underlying filesystem.
 *
 * ### Abstract Functions Only
 *
 * Some filesystem functions like [copy] are implemented by using other features. These are the
 * non-abstract functions in the [Filesystem] interface.
 *
 * **This class forwards only the abstract functions;** non-abstract functions delegate to the
 * other functions of this class. If desired, subclasses may override non-abstract functions to
 * forward them.
 */
@ExperimentalFilesystem
abstract class ForwardingFilesystem(
  /** [Filesystem] to which this instance is delegating. */
  @get:JvmName("delegate")
  val delegate: Filesystem
) : Filesystem() {

  /**
   * Invoked each time a path is passed as a parameter to this filesystem. This returns the path to
   * pass to [delegate], which should be [path] itself or a path on [delegate] that corresponds to
   * it.
   *
   * Subclasses may override this to log accesses, fail on unexpected accesses, or map paths across
   * filesystems.
   *
   * The base implementation returns [path].
   *
   * Note that this function will be called twice for calls to [atomicMove]; once for the source
   * file and once for the target file.
   *
   * @param path the path passed to any of the functions of this.
   * @param functionName a string like "canonicalize", "metadataOrNull", or "appendingSink".
   * @param parameterName a string like "path", "file", "source", or "target".
   * @return the path to pass to [delegate] for the same parameter.
   */
  open fun onPathParameter(path: Path, functionName: String, parameterName: String): Path = path

  /**
   * Invoked each time a path is returned by [delegate]. This returns the path to return to the
   * caller, which should be [path] itself or a path on this that corresponds to it.
   *
   * Subclasses may override this to log accesses, fail on unexpected path accesses, or map
   * directories or path names.
   *
   * The base implementation returns [path].
   *
   * @param path the path returned by any of the functions of this.
   * @param functionName a string like "canonicalize" or "list".
   * @return the path to return to the caller.
   */
  open fun onPathResult(path: Path, functionName: String): Path = path

  @Throws(IOException::class)
  override fun canonicalize(path: Path): Path {
    val path = onPathParameter(path, "canonicalize", "path")
    val result = delegate.canonicalize(path)
    return onPathResult(result, "canonicalize")
  }

  @Throws(IOException::class)
  override fun metadataOrNull(path: Path): FileMetadata? {
    val path = onPathParameter(path, "metadataOrNull", "path")
    return delegate.metadataOrNull(path)
  }

  @Throws(IOException::class)
  override fun list(dir: Path): List<Path> {
    val dir = onPathParameter(dir, "list", "dir")
    val result = delegate.list(dir)
    return result.map { onPathResult(it, "list") }.sorted()
  }

  @Throws(IOException::class)
  override fun source(file: Path): Source {
    val file = onPathParameter(file, "source", "file")
    return delegate.source(file)
  }

  @Throws(IOException::class)
  override fun sink(file: Path): Sink {
    val file = onPathParameter(file, "sink", "file")
    return delegate.sink(file)
  }

  @Throws(IOException::class)
  override fun appendingSink(file: Path): Sink {
    val file = onPathParameter(file, "appendingSink", "file")
    return delegate.appendingSink(file)
  }

  @Throws(IOException::class)
  override fun createDirectory(dir: Path) {
    val dir = onPathParameter(dir, "createDirectory", "dir")
    delegate.createDirectory(dir)
  }

  @Throws(IOException::class)
  override fun atomicMove(source: Path, target: Path) {
    val source = onPathParameter(source, "atomicMove", "source")
    val target = onPathParameter(target, "atomicMove", "target")
    delegate.atomicMove(source, target)
  }

  @Throws(IOException::class)
  override fun delete(path: Path) {
    val path = onPathParameter(path, "delete", "path")
    delegate.delete(path)
  }

  override fun toString() = "${this::class.simpleName}($delegate)"
}
