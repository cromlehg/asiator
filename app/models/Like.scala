package models

import play.api.libs.json.JsValue
import play.api.libs.json.Json
import controllers.AppContext

case class Like(
  val id: Long,
  val ownerId: Long,
  val targetType: Int,
  val targetId: Long,
  val created: Long,
  var userLoginOpt: Option[String],
  var displayNameOpt: Option[String],
  var ownerOpt: Option[Account],
  var rewardOpt: Option[Long]) extends TraitDateSupports {

  def createdShortDate = formattedShortDate(created)

  def toJson(implicit ac: AppContext): JsValue = {
    var jsObj = Json.obj(
      "id" -> id,
      "owner_id" -> ownerId,
      "target_type" -> TargetType.strById(targetType),
      "target_id" -> targetId,
      "created" -> created)
    jsObj = userLoginOpt.fold(jsObj)(t => jsObj ++ Json.obj("login" -> t))
    jsObj = displayNameOpt.fold(jsObj)(t => jsObj ++ Json.obj("display_name" -> t))
    jsObj = ownerOpt.fold(jsObj)(user => jsObj + ("owner" -> user.toJson))
    jsObj = rewardOpt.fold(jsObj)(t => jsObj ++ Json.obj("reward" -> t))
    jsObj
  }

}

object Like {

  def apply(
    id: Long,
    ownerId: Long,
    targetType: Int,
    targetId: Long,
    created: Long,
    userLoginOpt: Option[String],
    displayNameOpt: Option[String],
    ownerOpt: Option[Account],
    rewardOpt: Option[Long]): Like =
    new Like(
      id,
      ownerId,
      targetType,
      targetId,
      created,
      userLoginOpt,
      displayNameOpt,
      ownerOpt,
      rewardOpt)

  def apply(
    id: Long,
    ownerId: Long,
    targetType: Int,
    targetId: Long,
    created: Long): Like =
    new Like(
      id,
      ownerId,
      targetType,
      targetId,
      created,
      None,
      None,
      None,
      None)

}
