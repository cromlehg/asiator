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
class EmissionController @Inject() (cc: ControllerComponents, dao: DAO, config: Config)(implicit ec: ExecutionContext)
  extends Authorizable(cc, dao, config) {

  import scala.concurrent.Future.{ successful => future }

  def emissions = Action.async { implicit request =>
    implicit val ac = new AppContext()
    onlyAdmin(a => dao.getEmissions map { emissions =>
      Ok(views.html.app.admin.emissions(emissions))
    })
  }

}

