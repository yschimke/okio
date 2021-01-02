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

import io.minio.CopyObjectArgs
import io.minio.CopySource
import io.minio.GetObjectArgs
import io.minio.ListBucketsArgs
import io.minio.ListObjectsArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.RemoveBucketArgs
import io.minio.RemoveObjectArgs
import okio.Path.Companion.toPath

/**
 * A fully in-memory filesystem useful for testing. It includes features to support writing
 * better tests.
 *
 * Use [openPaths] to see which paths have been opened for read or write, but not yet closed. Tests
 * should call [checkNoOpenFiles] in `tearDown()` to confirm that no file streams were leaked.
 *
 * By default this filesystem permits deletion and removal of open files. Configure
 * [windowsLimitations] to true to throw an [IOException] when asked to delete or rename an open
 * file.
 */
@ExperimentalFilesystem
class S3Filesystem(private val minioClient: MinioClient) : Filesystem() {
  override fun canonicalize(path: Path): Path {
    TODO("Not yet implemented")
  }

  override fun metadataOrNull(path: Path): FileMetadata? {
    TODO("Not yet implemented")
  }

  override fun list(dir: Path): List<Path> {
    return if (dir == "/".toPath())
      minioClient.listBuckets(ListBucketsArgs.Builder()
        .build())
        .map { ("/" + it.name()).toPath() }
    else
      minioClient.listObjects(ListObjectsArgs.builder()
        .bucket(dir.name)
        .build())
        .map { dir / it.get().objectName() }
  }

  override fun source(file: Path): Source {
    val (bucket, name) = split(file)

    val result = minioClient.getObject(GetObjectArgs.builder()
      .bucket(bucket)
      .`object`(name)
      .build())

    val bytes = result.readAllBytes()

    return Buffer().apply {
      write(bytes)
    }
  }

  override fun sink(file: Path): Sink {
    minioClient.putObject(PutObjectArgs.Builder()
      .bucket("abc")
      .`object`("def.txt")
      .stream(Buffer()
        .apply { writeUtf8("a".repeat(5 * 1024 * 1024)) }
        .inputStream(),
        5 * 1024 * 1024,
        5 * 1024 * 1024
      )
      .build())

    return Buffer()
  }

  override fun appendingSink(file: Path): Sink {
    TODO("Not yet implemented")
  }

  override fun createDirectory(dir: Path) {
    val (bucket, name) = split(dir)

    minioClient.makeBucket(MakeBucketArgs.Builder()
      .bucket(bucket)
      .build())
  }

  override fun atomicMove(source: Path, target: Path) {
    val (sourceBucket, sourceName) = split(source)
    val (targetBucket, targetName) = split(target)

    minioClient.copyObject(CopyObjectArgs.Builder()
      .source(CopySource.Builder()
        .bucket(sourceBucket)
        .`object`(sourceName)
        .build())
      .bucket(targetBucket)
      .`object`(targetName)
      .build())
  }

  override fun delete(path: Path) {
    val (bucket, name) = split(path)

    if (name != null) {
      minioClient.removeObject(RemoveObjectArgs.Builder()
        .bucket(bucket)
        .`object`(name)
        .build())
    } else {
      minioClient.removeBucket(RemoveBucketArgs.Builder()
        .bucket(bucket)
        .build())
    }
  }

  fun split(path: Path): Pair<String, String?> {
    val parts = path.toString().substring(1).split("/", limit = 2)

    println("$path -> $parts")

    return Pair(parts[0], parts.getOrNull(1))
  }
}
