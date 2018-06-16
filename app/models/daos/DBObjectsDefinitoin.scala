package models.daos

case class DBComment(
  val id: Long,
  val targetId: Long,
  val ownerId: Long,
  val parentId: Option[Long],
  val content: String,
  val contentType: Int,
  val created: Long,
  val likesCount: Int,
  val reward: Long,
  val status: Int)

