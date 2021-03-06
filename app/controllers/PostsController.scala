package controllers

import scala.concurrent.ExecutionContext

import com.typesafe.config.Config

import javax.inject.Inject
import javax.inject.Singleton
import models.PostsFilter
import models.daos.DAO
import play.api.mvc.ControllerComponents
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
import models.ShortOptions

@Singleton
class PostsController @Inject() (cc: ControllerComponents, dao: DAO, config: Config)(implicit ec: ExecutionContext)
  extends Authorizable(cc, dao, config) with JSONSupport {

  import scala.concurrent.Future.{ successful => future }

  implicit val ac = new AppContext()

  case class PostPostData(val title: String, val content: String)

  val articleForm = Form(
    mapping(
      "title" -> nonEmptyText(3, 100),
      "content" -> nonEmptyText(500))(PostPostData.apply)(PostPostData.unapply))

  def booleanOptionFold(name: String)(ifFalse: Future[Result])(ifTrue: Future[Result]): Future[Result] =
    dao.getShortOptionByName(name) flatMap {
      _.fold(future(BadRequest("Not found option " + name)))(option => if (option.toBoolean) ifTrue else ifFalse)
    }

  def booleanOptionTrue(name: String)(ifTrue: Future[Result]): Future[Result] =
    booleanOptionFold(name)(future(BadRequest("Not available now")))(ifTrue)

  def processCreatePost() = Action.async { implicit request =>
    onlyAuthorized { account =>

      def redirectWithError(msg: String, form: Form[_]) =
        future(Ok(views.html.app.createPost(form)(Flash(form.data) + ("error" -> msg), implicitly, implicitly)))

      articleForm.bindFromRequest.fold(
        formWithErrors => future(BadRequest(views.html.app.createPost(formWithErrors))), {
          post =>
            booleanOptionFold(ShortOptions.ARTICLES_POST_ALLOWED) {
              redirectWithError("Not allowed for now!", articleForm.fill(post))
            } {
              dao.getShortOptionByName(ShortOptions.ARTICLES_PREMODERATION) flatMap {
                _.fold(future(BadRequest("Not found option for posts moderation"))) { modOpt =>
                   
                  dao.createPostWithPostsCounterUpdate(
                    account.id,
                    None,
                    post.title,
                    post.content,
                    None,
                    TargetType.ARTICLE,
                    Seq.empty[String],
                    modOpt.toBoolean) flatMap { createdPostOpt =>
                      createdPostOpt match {
                        case Some(createdPost) =>
                          future(Redirect(controllers.routes.PostsController.viewPost(createdPost.id))
                            .flashing("success" -> ("Post successfully created!")))
                        case _ =>
                          redirectWithError("Some problems during post creation!", articleForm.fill(post))
                      }
                    }


                }
              }

            }
      })
    }
  }

  def createPost() = Action.async { implicit request =>
    onlyAuthorized { account =>
      future(Ok(views.html.app.createPost(articleForm)))
    }
  }

  def removePost(postId: Long) = Action.async { implicit request =>
    implicit val ac = new AppContext()
    onlyAuthorized { account =>
      dao.findPostById(postId) flatMap {
        _.fold(future(BadRequest("Post with id " + postId + " not exists"))) { post =>
          if (post.ownerId == account.id || account.isAdmin) {
            dao.removePost(postId) map { isRemoved =>
              if (isRemoved)
                request.headers.get("referer")
                  .fold(Redirect(controllers.routes.AppController.index())) { url => Redirect(url) }
              else
                BadRequest("Couldn't remove post with id " + postId)
            }
          } else future(BadRequest("You are not authorized to remove this post"))
        }
      }
    }
  }

  def processEditPost(postId: Long) = Action.async { implicit request =>
    onlyAuthorized { account =>

      def redirectWithError(msg: String, form: Form[_]) =
        future(Ok(views.html.app.editPost(form, postId)(Flash(form.data) + ("error" -> msg), implicitly, implicitly)))

      dao.findPostById(postId) flatMap (
        _.fold(future(BadRequest("Post not found"))) { post =>
          if (account.id != post.ownerId && account.notAdmin) {
            future(BadRequest("You have no permissions to edit this post"))
          } else {
            articleForm.bindFromRequest.fold(
              formWithErrors => Future(BadRequest(views.html.app.createPost(formWithErrors))), {
                post =>
                  booleanOptionFold(ShortOptions.ARTICLES_CHANGE_ALLOWED) {
                    redirectWithError("Not allowed for now!", articleForm.fill(post))
                  } {
                    dao.updatePost(
                      postId,
                      post.title,
                      post.content) flatMap { result =>
                        if (result)
                          future(Redirect(controllers.routes.PostsController.viewPost(postId))
                            .flashing("success" -> ("Post successfully updated!")))
                        else
                          redirectWithError("Some problems during post update!", articleForm.fill(post))
                      }
                  }
              })
          }
        })
    }
  }

  def editPost(postId: Long) = Action.async { implicit request =>
    onlyAuthorized { account =>
      dao.findPostById(postId) map (
        _.fold(BadRequest("Post not found")) { post =>
          if (account.id == post.ownerId || account.isAdmin) {
            val postData = Map("title" -> post.title, "content" -> post.content)
            Ok(views.html.app.editPost(articleForm.bind(postData), postId))
          } else {
            BadRequest("You have no permissions to edit this post")
          }
        })
    }
  }

  def posts(pageId: Long) = Action.async { implicit request =>
    optionalAuthorized { accountOpt =>
      dao.findPostsWithAccountsByCategoryTagIds(None, None, None, pageId, None) map { posts =>
        Ok(views.html.app.posts(posts, Some(PostsFilter.NEW)))
      }
    }
  }

  def postsByFilter(pageId: Long, filter: String) = Action.async { implicit request =>
    optionalAuthorized { accountOpt =>
      dao.findPostsWithAccountsByCategoryTagIds(None, None, Some(filter), pageId, None) map { posts =>
        Ok(views.html.app.posts(posts, Some(filter)))
      }
    }
  }

  def viewPost(postId: Long) = Action.async { implicit request =>
    optionalAuthorized { accountOpt =>
      dao.findPostWithAccountByPostId(postId) flatMap (
        _.fold(future(NotFound(""))) { post =>
          future(Ok(views.html.app.viewPost(post, Seq())))
        })
    }
  }

  def findPostsByCategory() = Action.async(parse.json) { implicit request =>
    fieldInt("pattern_id")(patternId => fieldInt("page_size")(pageSize =>
      if (pageSize > AppConstants.MAX_PAGE_SIZE)
        future(BadRequest("Page size can't be greater than " + AppConstants.MAX_PAGE_SIZE))
      else if (pageSize < AppConstants.MIN_PAGE_SIZE)
        future(BadRequest("Page size can't be less than " + AppConstants.MIN_PAGE_SIZE))
      else
        fieldLong("page_id")(pageId => fieldStringOpt("filter") { filterName =>
          optionalAuthorized { optUser =>
            fieldSeqStringOptOpt("tags") { tagNamesOpt: Option[Seq[String]] =>
              val tagNamesOptPrepared = tagNamesOpt.map(_.map(_.trim.toLowerCase))
              if (tagNamesOptPrepared.isDefined && tagNamesOptPrepared.get.length > AppConstants.TAGS_PER_POST_LIMIT) future(BadRequest("You have more than " + AppConstants.TAGS_PER_POST_LIMIT + " tags"))
              else if (tagNamesOptPrepared.isDefined && tagNamesOptPrepared.get.exists(_.length < AppConstants.TAG_SIZE_LIMIT)) future(BadRequest("Each tag length should be more than " + (AppConstants.TAG_SIZE_LIMIT - 1))) else {
                val userIdOpt = optUser.map(_.id)
                withAccountNameOrIdSingleOpt(idOpt =>
                  dao.findPostsWithAccountsByCategoryTagNames(Some(pageSize), userIdOpt, idOpt, filterName, pageId, tagNamesOptPrepared) map { posts =>
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
        })))
  }

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

  def adminModerationPosts(pageId: Int) = Action.async { implicit request =>
    implicit val ac = new AppContext()
    onlyAdmin(a =>
      dao.getAdminModeratePostsPagesCount() flatMap { pagesCount =>
        if (pageId > pagesCount)
          future(Ok(views.html.app.admin.adminModeratePosts(a, Seq(), pageId, pagesCount)))
        else
          dao.getAdminModeratePosts(pageId) map { posts =>
            Ok(views.html.app.admin.adminModeratePosts(a, posts, pageId, pagesCount))
          }
      })
  }

  def moderatePost(postId: Long, moderateStatus: Int) = Action.async { implicit request =>
    models.ModerateStatus.strById(moderateStatus).fold(future(BadRequest("Wrong moderate status id " + moderateStatus))) { _ =>
      implicit val ac = new AppContext()
      onlyAdmin(account =>
        dao.setModerateStatusToPost(postId, moderateStatus) map { success =>
          if (success)
            request.headers.get("referer")
              .fold(Redirect(controllers.routes.AdminController.admin)) { url => Redirect(url) }
          else
            BadRequest("Couldn't set moderate status " + moderateStatus + " for post " + postId)
        })
    }
  }

}

