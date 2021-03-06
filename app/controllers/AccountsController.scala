package controllers

import scala.concurrent.ExecutionContext

import com.typesafe.config.Config

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

@Singleton
class AccountsController @Inject() (cc: ControllerComponents, dao: DAO, config: Config)(implicit ec: ExecutionContext)
  extends RegisterCommonAuthorizable(cc, dao, config) {

  import scala.concurrent.Future.{ successful => future }

  case class ApproveData(
    val login: String,
    val pwd: String,
    val repwd: String,
    val understandNotRecover: Boolean,
    val haveSecured: Boolean,
    val code: String)

  val loginVerifying = nonEmptyText(3, 20).verifying("Must contain lowercase letters and digits only.", name => name.matches("[a-z0-9]{3,20}"))

  val approveForm = Form(
    mapping(
      "login" -> loginVerifying,
      "pwd" -> nonEmptyText(8, 80),
      "repwd" -> nonEmptyText(8, 80),
      "understandNotRecover" -> boolean.verifying(_ == true),
      "haveSecured" -> boolean.verifying(_ == true),
      "code" -> nonEmptyText)(ApproveData.apply)(ApproveData.unapply))

  case class AuthData(val email: String, val pass: String)

  sealed trait RegData {
    def email: String
    def login: String
  }

  case class RegDataUser(
    override val email: String,
    override val login: String) extends RegData

  case class RegDataCompany(
    override val email: String,
    override val login: String,
    val companyName: String) extends RegData

  val authForm = Form(
    mapping(
      "email" -> nonEmptyText(3, 50),
      "pass" -> nonEmptyText(8, 80))(AuthData.apply)(AuthData.unapply))

  val regFormUser = Form(
    mapping(
      "email" -> email,
      "login" -> loginVerifying)(RegDataUser.apply)(RegDataUser.unapply))

  val regFormCompany = Form(
    mapping(
      "email" -> email,
      "login" -> loginVerifying,
      "companyName" -> nonEmptyText(8, 80))(RegDataCompany.apply)(RegDataCompany.unapply))

  def login() = Action.async { implicit request =>
    implicit val ac = new AppContext()
    notAuthorized(future(Ok(views.html.app.login(authForm))))
  }

  def logout = Action.async { implicit request =>
    implicit val ac = new AppContext()
    super.logout(Redirect(controllers.routes.AccountsController.login()))
  }

  def processLogin() = Action.async { implicit request =>
    implicit val ac = new AppContext()
    notAuthorized {
      authForm.bindFromRequest.fold(formWithErrors => future(BadRequest(views.html.app.login(formWithErrors))), { authData =>
        authCheckBlock(authData.email, authData.pass) { msg =>
          val formWithErrors = authForm.fill(authData)
          future(BadRequest(views.html.app.login(formWithErrors)(Flash(formWithErrors.data) + ("error" -> msg), implicitly, implicitly)))
        } { case (account, session) => future(Redirect(routes.AccountsController.blog(account.id, 1))) }
      })
    }
  }

  def processApproveRegister() = Action.async { implicit request =>
    implicit val ac = new AppContext()
    notAuthorized {
      approveForm.bindFromRequest.fold(
        formWithErrors => future(BadRequest(views.html.app.approveRegister(formWithErrors))), {
          approveData =>
            if (approveData.pwd == approveData.repwd)
              dao.findAccountByConfirmCodeAndLogin(approveData.login, approveData.code) flatMap (_.fold(future(BadRequest("Login or confirm code not found"))) { account =>
                dao.emailVerified(approveData.login, approveData.code, approveData.pwd) map (_.fold(BadRequest("Can't verify email")) { accountVerified =>
                  Ok(views.html.app.registerFinished())
                })
              })
            else {
              val formWithErrors = approveForm.fill(approveData)
              future(Ok(views.html.app.approveRegister(formWithErrors)(Flash(formWithErrors.data) + ("error" -> "Passwords should be equals"), implicitly, implicitly)))
            }
        })
    }
  }

  def approveRegister(login: String, code: String) = Action.async { implicit request =>
    implicit val ac = new AppContext()
    notAuthorized {
      dao.findAccountByConfirmCodeAndLogin(login, code) map (_.fold(BadRequest("Login or confirm code not found")) { account =>
        Ok(views.html.app.approveRegister(approveForm.fill(ApproveData(
          login,
          BCrypt.hashpw(login.toString + code + System.currentTimeMillis() + Random.nextDouble(), BCrypt.gensalt()),
          null,
          false,
          false,
          code))))
      })
    }
  }

  private def baseRegisterChecks[T <: RegData](
    regForm: Form[T])(f1: (String, Form[_]) => Future[Result])(f2: Form[_] => Html)(f3: (T, String, String) => Future[Result])(implicit request: Request[_], ac: AppContext) = {
    regForm.bindFromRequest.fold(
      formWithErrors => future(BadRequest(f2(formWithErrors))), {
        userInRegister =>
          dao.isLoginExists(userInRegister.login) flatMap { isLoginExists =>
            if (isLoginExists)
              f1("Login already exists!", regForm.fill(userInRegister))
            else
              dao.isEmailExists(userInRegister.email) flatMap { isEmailExists =>
                if (isEmailExists)
                  f1("Email already exists!", regForm.fill(userInRegister))
                else
                  f3(userInRegister, userInRegister.login, userInRegister.email)
              }
          }
      })

  }

  def registerProcessUser() = Action.async { implicit request =>
    implicit val ac = new AppContext()
    notAuthorized {

      def redirectWithError(msg: String, form: Form[_]) =
        future(Ok(views.html.app.registerUser(form)(Flash(form.data) + ("error" -> msg), implicitly, implicitly)))

      baseRegisterChecks(regFormUser)(redirectWithError)(t => views.html.app.registerUser(t)) { (target, login, email) =>
        createAccount("sendgrid.letter2", AccountType.USER, login, email, None) { account =>
          Ok(views.html.app.registerProcess())
        }
      }

    }
  }

  def registerProcessCompany() = Action.async { implicit request =>
    implicit val ac = new AppContext()
    notAuthorized {

      def redirectWithError(msg: String, form: Form[_]) =
        future(Ok(views.html.app.registerCompany(form)(Flash(form.data) + ("error" -> msg), implicitly, implicitly)))

      baseRegisterChecks(regFormCompany)(redirectWithError)(t => views.html.app.registerUser(t)) { (target, login, email) =>
        createAccount("sendgrid.letter2", AccountType.COMPANY, login, email, Some(target.companyName)) { account =>
          Ok(views.html.app.registerProcess())
        }
      }

    }
  }

  def registerUser() = Action.async { implicit request =>
    implicit val ac = new AppContext()
    notAuthorized {
      future(Ok(views.html.app.registerUser(regFormUser)))
    }
  }

  def registerCompany() = Action.async { implicit request =>
    implicit val ac = new AppContext()
    notAuthorized {
      future(Ok(views.html.app.registerCompany(regFormCompany)))
    }
  }

  def blog(accountId: Long, pageId: Long) = Action.async { implicit request =>
    implicit val ac = new AppContext()
    optionalAuthorized { accountOpt =>
      dao.findAccountById(accountId) flatMap (_.fold(future(BadRequest("Account not found with id " + accountId))) { targetAccount =>
        dao.findPostsWithAccountsByCategoryTagIds(None, Some(targetAccount.id), None, pageId, None) map { posts =>
          Ok(views.html.app.blog(targetAccount, posts))
        }
      })
    }
  }

  def adminAccounts(pageId: Int, filterOpt: Option[String]) = Action.async { implicit request =>
    if (filterOpt.isDefined && !filterOpt.get.matches("[a-z0-9]{1,}")) {
      future(request.headers.get("referer")
        .fold {
          Redirect(controllers.routes.AccountsController.adminAccounts(1, None))
            .flashing("error" -> "Search string must contains only a-b or 0-9 symbols!")
        } { url =>
          Redirect(url)
            .flashing("error" -> "Search string must contains only a-b or 0-9 symbols!")
        })
    } else {
      implicit val ac = new AppContext()
      onlyAdmin(a =>
        dao.getAccountsPagesCount(filterOpt) flatMap { pagesCount =>
          if (pageId > pagesCount) future(BadRequest("Page not found " + pageId)) else
            dao.getAccounts(filterOpt, pageId) map { accounts =>
              Ok(views.html.app.admin.adminAccounts(
                a,
                accounts,
                pageId,
                pagesCount,
                filterOpt))
            }
        })
    }
  }

  def setAccountStatus(accountId: Long, status: Int) = Action.async { implicit request =>
    models.AccountStatus.strById(status).fold(future(BadRequest("Wrong status id " + status))) { _ =>
      implicit val ac = new AppContext()
      onlyAdmin(account =>
        dao.setAccountStatus(accountId, status) map { success =>
          if (success)
            (request.headers.get("referer")
              .fold(Redirect(controllers.routes.AppController.index)) { url => Redirect(url) })
              .flashing("error" -> ("New status has been set for account with id  " + accountId))
          else
            (request.headers.get("referer")
              .fold(Redirect(controllers.routes.AppController.index)) { url => Redirect(url) })
              .flashing("error" -> ("Can't set new status for account with id " + accountId))
        })
    }
  }


}

