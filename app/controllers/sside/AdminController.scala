package controllers.sside

import scala.concurrent.ExecutionContext

import com.typesafe.config.Config

import controllers.Authorizable
import controllers.RegisterCommonAuthorizable
import controllers.AppContext
import javax.inject.Inject
import javax.inject.Singleton
import models.daos.DAO
import play.api.mvc.ControllerComponents
import scala.util.Random

import play.api.data.Forms.email
import play.api.data.Forms.text
import play.api.data.Forms.boolean
import play.api.data.Forms.mapping
import play.api.data.Forms.nonEmptyText
import play.api.mvc.Flash

import play.api.data.Form
import models.AccountType

import java.io.IOException

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.mindrot.jbcrypt.BCrypt

import com.sendgrid.Content
import com.sendgrid.Email
import com.sendgrid.Mail
import com.sendgrid.Method
import com.sendgrid.SendGrid
import com.typesafe.config.Config

import javax.inject.Inject
import javax.inject.Singleton
import models.AccountStatus
import models.AccountType
import models.CommentsViewType
import models.CurrencyType
import models.ErrCodes
import models.TargetType
import models.daos.DAO
import play.Logger
import play.api.libs.json.JsArray
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents
import play.api.mvc.Request
import play.api.mvc.Result
import play.twirl.api.Html
import play.api.mvc.Action
import controllers.AppConstants
import models.ShortOptions
import controllers.JSONSupport

@Singleton
class AdminController @Inject() (cc: ControllerComponents, dao: DAO, config: Config)(implicit ec: ExecutionContext)
  extends RegisterCommonAuthorizable(cc, dao, config) with JSONSupport {

  import scala.concurrent.Future.{ successful => future }

  case class AuthData(val email: String, val pass: String)

  val authForm = Form(
    mapping(
      "email" -> nonEmptyText(3, 50),
      "pass" -> nonEmptyText(8, 80))(AuthData.apply)(AuthData.unapply))

  protected def onlyAdmin[T](f: models.Account => Future[Result])(implicit request: Request[T], ac: AppContext): Future[Result] =
    super.onlyAdmin(future(Redirect(controllers.sside.routes.AccountsController.login())))(f)

  def admin() = Action.async { implicit request =>
    implicit val ac = new AppContext()
    onlyAdmin(account => future(Redirect(controllers.sside.routes.AdminController.adminOptions())))
  }

  def adminAccounts(pageId: Int) = Action.async { implicit request =>
    implicit val ac = new AppContext()
    onlyAdmin(a =>
      dao.getAccountsPages() flatMap { pagesCount =>
        if (pageId > pagesCount) future(BadRequest("Page not found " + pageId)) else
          dao.getAccountsPage(pageId) map { accounts =>
            Ok(views.html.app.admin.adminAccounts(a, accounts, pageId, pagesCount))
          }
      })
  }

  def adminPosts(pageId: Int) = Action.async { implicit request =>
    implicit val ac = new AppContext()
    onlyAdmin(a =>
      dao.getPostsPages() flatMap { pagesCount =>
        if (pageId > pagesCount)
          future(Ok(views.html.app.admin.adminPosts(a, Seq(), pageId, pagesCount)))
        else
          dao.getPostsPage(pageId) map { posts =>
            Ok(views.html.app.admin.adminPosts(a, posts, pageId, pagesCount))
          }
      })
  }

  def adminStats = Action.async { implicit request =>
    implicit val ac = new AppContext()
    onlyAdmin(a => dao.getStats map { stats =>
      Ok(views.html.app.admin.adminStats(stats))
    })
  }

  def adminOptions() = Action.async { implicit request =>
    implicit val ac = new AppContext()
    onlyAdmin(a => dao.getShortOptions map (t => Ok(views.html.app.admin.adminOptions(a, t))))
  }

  def switchBooleanOption() = Action.async(parse.json) { implicit request =>
    implicit val ac = new AppContext()
    onlyAdmin(a => fieldString("name")(name => dao.getShortOptionByName(name) flatMap (_.fold(future(BadRequest("Not found"))) { option =>
      if (option.ttype != ShortOptions.TYPE_BOOLEAN) future(BadRequest("Option must be boolean to switch")) else
        dao.updateShortOptionByName(name, if (option.toBoolean) "false" else "true") map {
          _.fold(BadRequest("Can't update option"))(t => Ok(t.toBoolean.toString))
        }
    })))
  }

}

