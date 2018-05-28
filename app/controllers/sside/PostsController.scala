package controllers.sside

import scala.concurrent.ExecutionContext

import com.typesafe.config.Config

import controllers.Authorizable
import javax.inject.Inject
import javax.inject.Singleton
import models.PostsFilter
import models.daos.DAO
import play.api.mvc.ControllerComponents
import controllers.AppContext
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
import controllers.JSONSupport

@Singleton
class PostsController @Inject() (cc: ControllerComponents, dao: DAO, config: Config)(implicit ec: ExecutionContext)
  extends Authorizable(cc, dao, config) with JSONSupport {

  import scala.concurrent.Future.{ successful => future }

  implicit val ac = new AppContext()

  case class PostPostData(val title: String, val content: String)

  val createPostForm = Form(
    mapping(
      "title" -> nonEmptyText(3, 100),
      "content" -> nonEmptyText(500))(PostPostData.apply)(PostPostData.unapply))

  def processCreatePost() = Action.async { implicit request =>
    onlyAuthorized { account =>

      def redirectWithError(msg: String, form: Form[_]) =
        future(Ok(views.html.app.createPost(form)(Flash(form.data) + ("error" -> msg), implicitly, implicitly)))

      createPostForm.bindFromRequest.fold(
        formWithErrors => Future(BadRequest(views.html.app.createPost(formWithErrors))), {
          post =>
            dao.createPostWithPostsCounterUpdate(
              account.id,
              None,
              post.title,
              post.content,
              None,
              TargetType.ARTICLE,
              Seq.empty[String]) flatMap { createdPostOpt =>
                createdPostOpt match {
                  case Some(createdPost) =>
                    Future.successful(Redirect(controllers.sside.routes.PostsController.viewPost(createdPost.id))
                      .flashing("success" -> ("Post successfully created!")))
                  case _ =>
                    redirectWithError("Some problems during post creation!", createPostForm.fill(post))
                }
              }

        })

    }
  }

  def createPost() = Action.async { implicit request =>
    onlyAuthorized { account =>
      Future(Ok(views.html.app.createPost(createPostForm)))
    }
  }

  def posts(pageId: Long) = Action.async { implicit request =>
    optionalAuthorized { accountOpt =>
      dao.findPostsWithAccountsByCategoryTagIds(None, None, pageId, None) map { posts =>
        Ok(views.html.app.posts(posts, Some(PostsFilter.NEW)))
      }
    }
  }

  def postsByFilter(pageId: Long, filter: String) = Action.async { implicit request =>
    optionalAuthorized { accountOpt =>
      dao.findPostsWithAccountsByCategoryTagIds(None, Some(filter), pageId, None) map { posts =>
        Ok(views.html.app.posts(posts, Some(filter)))
      }
    }
  }

  def viewPost(postId: Long) = Action.async { implicit request =>
    optionalAuthorized { accountOpt =>
      dao.findPostWithAccountByPostId(postId) flatMap (
        _.fold(future(NotFound(""))) { post =>
          //dao.findReviewsWithAccountsByCategoryTagIds(post.id, 1) map { reviews =>
          future(Ok(views.html.app.viewPost(post, Seq())))
          //}
        })
    }
  }

  def findPostsByCategory() = Action.async(parse.json) { implicit request =>
    fieldInt("pattern_id")(patternId =>
      fieldLong("page_id")(pageId => fieldStringOpt("filter") { filterName =>
        optionalAuthorized { optUser =>
          fieldSeqStringOptOpt("tags") { tagNamesOpt: Option[Seq[String]] =>
            val tagNamesOptPrepared = tagNamesOpt.map(_.map(_.trim.toLowerCase))
            if (tagNamesOptPrepared.isDefined && tagNamesOptPrepared.get.length > AppConstants.TAGS_PER_POST_LIMIT) future(BadRequest("You have more than " + AppConstants.TAGS_PER_POST_LIMIT + " tags"))
            else if (tagNamesOptPrepared.isDefined && tagNamesOptPrepared.get.exists(_.length < AppConstants.TAG_SIZE_LIMIT)) future(BadRequest("Each tag length should be more than " + (AppConstants.TAG_SIZE_LIMIT - 1))) else {
              val userIdOpt = optUser.map(_.id)
              withAccountNameOrIdSingleOpt(idOpt =>
                dao.findPostsWithAccountsByCategoryTagNames(userIdOpt, idOpt, filterName, pageId, tagNamesOptPrepared) map { posts =>
                  if (patternId == 3) 
                    Ok(views.html.app.common.postsList(posts))
                  else if (patternId == 2)
                    Ok(views.html.app.common.postsListThumb2(posts))
                  else
                    Ok(views.html.app.common.postsListThumb1(posts))
                })
            }
          }
        }
      }))
  }

  //  def findPostsByCategory() = Action.async(parse.json) { implicit request =>
  //    fieldLong("page_id")(pageId => fieldStringOpt("filter") { filterName =>
  //      optionalAuthorized { optUser =>
  //        fieldSeqStringOptOpt("tags") { tagNamesOpt: Option[Seq[String]] =>
  //          val tagNamesOptPrepared = tagNamesOpt.map(_.map(_.trim.toLowerCase))
  //          if (tagNamesOptPrepared.isDefined && tagNamesOptPrepared.get.length > AppConstants.TAGS_PER_POST_LIMIT) future(BadRequest("You have more than " + AppConstants.TAGS_PER_POST_LIMIT + " tags"))
  //          else if (tagNamesOptPrepared.isDefined && tagNamesOptPrepared.get.exists(_.length < AppConstants.TAG_SIZE_LIMIT)) future(BadRequest("Each tag length should be more than " + (AppConstants.TAG_SIZE_LIMIT - 1))) else {
  //            val userIdOpt = optUser.map(_.id)
  //            withAccountNameOrIdSingleOpt(idOpt =>
  //              dao.findPostsWithAccountsByCategoryTagNames(userIdOpt, idOpt, filterName, pageId, tagNamesOptPrepared) map { posts =>
  //                Ok(views.html.app.common.postsListThumb2(posts))
  //              })
  //          }
  //        }
  //      }
  //    })
  //  }

  private def withNameOrId(
    idFieldName: String,
    nameFieldName: String,
    idByName: String => Future[Option[Long]],
    f: Long => Future[Result])(implicit request: Request[JsValue], ac: AppContext): Future[Result] =
    fieldLongOpt(idFieldName)(_.fold(
      fieldString(nameFieldName) { str =>
        val preapred = str.trim()
        if (preapred.length < 1) future(BadRequest("Should be 1 symbol at least")) else
          idByName(preapred) flatMap (_.fold(future(BadRequest("Not found")))(f))
      })(f))

  private def withNameOrIdOpt(
    idFieldName: String,
    nameFieldName: String,
    idByName: String => Future[Option[Long]])(
    f1: Long => Future[Result])(
    f2: Future[Result])(implicit request: Request[JsValue], ac: AppContext): Future[Result] =
    fieldLongOpt(idFieldName)(_.fold(fieldStringOpt(nameFieldName)(_.fold(f2) { str =>
      val preapred = str.trim()
      if (preapred.length < 1) future(BadRequest("Should be 1 symbol at least")) else
        idByName(preapred) flatMap (_.fold(future(BadRequest("Not found")))(f1))
    }))(f1))

  private def withNameOrIdSingleOpt(
    idFieldName: String,
    nameFieldName: String,
    idByName: String => Future[Option[Long]])(
    f: Option[Long] => Future[Result])(implicit request: Request[JsValue], ac: AppContext): Future[Result] =
    withNameOrIdOpt(idFieldName, nameFieldName, idByName)(t => f(Some(t)))(f(None))

  private def withAccountNameOrIdOpt(f1: Long => Future[Result])(f2: Future[Result])(implicit request: Request[JsValue], ac: AppContext): Future[Result] =
    withNameOrIdOpt("account_id", "login", dao.findAccountIdByLoginOrEmail)(f1)(f2)

  private def withAccountNameOrIdSingleOpt(f: Option[Long] => Future[Result])(implicit request: Request[JsValue], ac: AppContext): Future[Result] =
    withNameOrIdSingleOpt("account_id", "login", dao.findAccountIdByLoginOrEmail)(f)

  private def withAccountNameOrId(f: Long => Future[Result])(implicit request: Request[JsValue], ac: AppContext): Future[Result] =
    withNameOrId("account_id", "login", dao.findAccountIdByLoginOrEmail, f)

}

