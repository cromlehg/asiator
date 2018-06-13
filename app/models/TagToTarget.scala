package models

case class TagToTarget(
  val tagId: Long,
  val targetId: Long,
  val targetType: Int,
  val created: Long)

object TagToTarget {

  def apply(
    tagId: Long,
    targetId: Long,
    targetType: Int,
    created: Long) =
    new TagToTarget(
      tagId,
      targetId,
      targetType,
      created)

}