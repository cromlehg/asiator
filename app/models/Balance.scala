package models

import controllers.AppContext
import play.api.libs.json.JsObject
import play.api.libs.json.Json

case class Balance(
  val id:          Long,
  val ownerId:     Long,
  val ownerType:   Int,
  val currencyId:  Int,
  val updated:     Long,
  val balanceType: Int,
  val value:       Long,
  val currencyOpt: Option[Currency]) {

  def toJson()(implicit ac: AppContext): JsObject = {
    var jsObj = Json.obj(
      "id" -> id,
      "owner_id" -> ownerId,
      "owner_type" -> TargetType.strById(ownerType),
      "currency" -> CurrencyType.strById(currencyId.toInt),
      "updated" -> updated,
      "balance_type" -> BalanceType.strById(balanceType),
      "value" -> value)
    jsObj
  }
}

object Balance {

  def apply(
    id:          Long,
    ownerId:     Long,
    ownerType:   Int,
    currencyId:  Int,
    updated:     Long,
    balanceType: Int,
    value:       Long,
    currencyOpt: Option[Currency]): Balance =
      new Balance(
        id,
        ownerId,
        ownerType,
        currencyId,
        updated,
        balanceType,
        value,
        currencyOpt)

  def apply(
    id:          Long,
    ownerId:     Long,
    ownerType:   Int,
    currencyId:  Int,
    updated:     Long,
    balanceType: Int,
    value:       Long): Balance =
      new Balance(
        id,
        ownerId,
        ownerType,
        currencyId,
        updated,
        balanceType,
        value,
        None)

}

