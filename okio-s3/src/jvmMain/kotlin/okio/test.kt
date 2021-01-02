package okio

import io.minio.MinioClient
import okio.Path.Companion.toPath

@OptIn(ExperimentalFilesystem::class) fun main() {
  val minioClient: MinioClient = MinioClient.builder()
    .endpoint("http://127.0.0.1:9000")
    .credentials("minioadmin", "minioadmin")
    .build()

  val fs = S3Filesystem(minioClient)

  // fs.createDirectory("/abc".toPath())

  // fs.sink("/abc/def.txt".toPath())

  println(fs.list("/".toPath()))
  println(fs.list("/abc".toPath()))

  println(fs.source("/abc/def.txt".toPath()).buffer().readUtf8(10))
}