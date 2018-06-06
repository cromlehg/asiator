package models

import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.LocalDateTime

import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.JsObject
import controllers.AppConstants
import controllers.AppContext
import java.util.Date

case class Account(
  val id: Long,
  val login: String,
  val email: String,
  val hash: Option[String],
  val avatar: Option[String],
  val background: Option[String],
  val confirmationStatus: Int,
  val accountStatus: Int,
  val name: Option[String],
  val surname: Option[String],
  val platformEth: Option[String],
  val timezoneId: Int,
  val registered: Long,
  val confirmCode: Option[String],
  val postsCounter: Int,
  val postsCounterStarted: Long,
  val likesCounter: Int,
  val likesCounterStarted: Long,
  val commentsCounter: Int,
  val commentsCounterStarted: Long,
  val postsCount: Long,
  val about: Option[String],
  val accountType: Int,
  val roles: Seq[Int],
  val balanceTokenOpt: Option[Long],
  val sessionOpt: Option[Session]) {

  val balances: Map[String, Balance] = Map()

  val ldt = new LocalDateTime(registered, DateTimeZone.UTC)

  val commentsLimt = AppConstants.COMMENTS_COUNTER_LIMIT - commentsCounter

  val likesLimt = AppConstants.LIKES_COUNTER_LIMIT - likesCounter

  val postsLimt = AppConstants.POSTS_COUNTER_LIMIT - postsCounter

  val isAdmin = roles.contains(Roles.ADMIN)

  val notAdmin = !isAdmin

  lazy val createdPrettyTime = ContentCompilerHelper.prettyTime.format(new Date(registered))

  override def equals(obj: Any) = obj match {
    case user: Account => user.email == email
    case _ => false
  }

  override def toString = email

  def getRegistered(zone: String): DateTime = getRegistered.toDateTime(DateTimeZone forID zone)

  def getRegistered: LocalDateTime = ldt

  def toJsonAuth(inJsObj: JsObject)(implicit ac: AppContext): JsObject = {
    var jsObj = inJsObj ++ Json.obj("email" -> email)
    accountType match {
      case AccountType.USER =>
        jsObj = jsObj ++ Json.obj("name" -> name, "surname" -> surname)
      case AccountType.COMPANY =>
        jsObj = jsObj ++ Json.obj("company_name" -> name)
      case _ =>
    }
    jsObj = confirmCode.fold(jsObj) { t => jsObj ++ Json.obj("confirm_code" -> t) }
    jsObj
  }

  lazy val displayName = accountType match {
    case AccountType.COMPANY => name.getOrElse("")
    case _ => login
  }

  def toJson(implicit ac: AppContext): JsObject = {
    var jsObj = Json.obj(
      "id" -> id,
      "login" -> login,
      "confirmation_status" -> ConfirmationStatus.strById(confirmationStatus),
      "account_status" -> AccountStatus.strById(accountStatus),
      "posts_count" -> postsCount,
      "registered" -> registered,
      "comments_limit" -> commentsLimt,
      "likes_limit" -> likesLimt,
      "account_type" -> AccountType.strById(accountType),
      "posts_limit" -> postsLimt,
      "display_name" -> displayName)

    jsObj = balanceTokenOpt.fold(jsObj) { t => jsObj ++ Json.obj("balance_token" -> t) }

    jsObj = about.fold(jsObj) { t => jsObj ++ Json.obj("about" -> t) }

    if (accountType == AccountType.COMPANY) {
      // TODO
    }

    jsObj = about.fold(jsObj) { t => jsObj ++ Json.obj("about" -> t) }
    jsObj = avatar.fold(jsObj) { t => jsObj ++ Json.obj("avatar" -> t) }
    jsObj = background.fold(jsObj) { t => jsObj ++ Json.obj("background" -> t) }

    ac.authorizedOpt.fold(jsObj)(_ => toJsonAuth(jsObj))
  }

}

