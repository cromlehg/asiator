package controllers

import scala.concurrent.ExecutionContext

import com.typesafe.config.Config

import javax.inject.Inject
import javax.inject.Singleton
import models.daos.DAO
import play.api.mvc.ControllerComponents

import play.api.data.Forms.email
import play.api.data.Forms.text
import play.api.data.Forms.boolean
import play.api.data.Forms.mapping
import play.api.data.Forms.nonEmptyText
import play.api.data.Forms.number
import play.api.data.Forms.longNumber
import play.api.mvc.Flash

import play.api.data.Form
import models.AccountType
import scala.concurrent.Future



@Singleton
class BalancesController @Inject() (cc: ControllerComponents, dao: DAO, config: Config)(implicit ec: ExecutionContext)
  extends Authorizable(cc, dao, config) {

  import scala.concurrent.Future.{ successful => future }


  case class ChargeData(val login: String, val currencyId: Int, val value: Long)

  val chargeForm = Form(
    mapping(
      "login"      -> nonEmptyText(3, 50),
      "currencyId" -> number,
      "value"      -> longNumber)(ChargeData.apply)(ChargeData.unapply))

  def balances(pageId: Long) = Action.async { implicit request =>
    implicit val ac = new AppContext()
    dao.findBalances(pageId) map { balances =>
      Ok(views.html.dev.balances(balances))
    }
  }

  def adminCharge = Action.async { implicit request =>
    implicit val ac = new AppContext()
    onlyAdmin(a => future(Ok(views.html.app.admin.charge(chargeForm))))
  }

  def adminChargeProcess = Action.async { implicit request =>
    implicit val ac = new AppContext()
    onlyAdmin { a =>
      chargeForm.bindFromRequest.fold(formWithErrors => future(BadRequest(views.html.app.admin.charge(formWithErrors))), { chargeData =>
        dao.findAccountByLoginOrEmail(chargeData.login) flatMap ( _.fold {
          val formWithErrors = chargeForm.fill(chargeData)
          future(BadRequest(views.html.app.admin.charge(formWithErrors)(Flash(formWithErrors.data) + ("error" -> "User not found!"), implicitly, implicitly)))
        } { u =>
          if(chargeData.value == 0) {
            val formWithErrors = chargeForm.fill(chargeData)
            future(BadRequest(views.html.app.admin.charge(formWithErrors)(Flash(formWithErrors.data) + ("error" -> "Charge value can't be empty or null!"), implicitly, implicitly)))
          } else
            dao.getCurrentAccountBalanceValue(u.id, chargeData.currencyId) flatMap {
              _.fold {
                val formWithErrors = chargeForm.fill(chargeData)
                future(BadRequest(views.html.app.admin.charge(formWithErrors)(Flash(formWithErrors.data) + ("error" -> ("Currecy with id " + chargeData.currencyId + " not exists!")), implicitly, implicitly)))
              } { value =>
                dao.chargeBalance(u.id, chargeData.currencyId, chargeData.value) map {
                  _.fold {
                    val formWithErrors = chargeForm.fill(chargeData)
                    BadRequest(views.html.app.admin.charge(formWithErrors)(Flash(formWithErrors.data) + ("error" -> "Error occurs during balance changing, you can try later!"), implicitly, implicitly))
                  } { newBalance =>
                    Redirect(controllers.routes.BalancesController.adminCharge)
                      .flashing("success" -> ("Balances for @" + u.login +
                         " updated with currency id " + chargeData.currencyId +
                         " from " + value + " to " + newBalance))
                  }
                }
              }
            }
        })
      })
    }
  }


}

