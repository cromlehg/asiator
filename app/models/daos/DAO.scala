package models.daos

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Random

import org.mindrot.jbcrypt.BCrypt

import controllers.AppConstants
import controllers.RewardLogic
import javax.inject.Inject
import models.AccountStatus
import models.CurrencyType
import models.PostsFilter
import models.Roles
import models.TargetType
import models.TxState
import models.TxType
import models.Account
import models.ConfirmationStatus
import play.api.db.slick.DatabaseConfigProvider
import play.api.db.slick.HasDatabaseConfigProvider
import controllers.AppContext
import models.ContentType
import models.BalanceType
import models.AccountType
import sun.net.ftp.FtpClient.TransferType

/**
 *
 * Queries with SlickBUG should be replace leftJoin with for comprehesive. Bug:
 * "Unreachable reference to after resolving monadic joins"
 *
 */

// inject this
// conf: play.api.Configuration,
// and then get conf value
// conf.underlying.getString(Utils.meidaPath)
class DAO @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext)
  extends DBTableDefinitions with HasDatabaseConfigProvider[slick.jdbc.JdbcProfile] with TraitDTOToModel {

  import profile.api._

  import scala.concurrent.Future.{ successful => future }

  val maxLikesView = 10

  def getTagsPages(): Future[Int] =
    db.run(tags.size.result) map pages

  def pages(size: Int): Int = pages(size, AppConstants.DEFAULT_PAGE_SIZE)

  def removePost(postId: Long) = {
    val query = for {
      comments <- comments.filter(_.targetId === postId).delete
      count <- posts.filter(_.id === postId).delete
    } yield count
    db.run(query.transactionally).map(_ == 1)
  }

  def pages(size: Int, pageSize: Int): Int = {
    if (size == 0) 0 else {
      val fSize = size / pageSize
      if (fSize * pageSize < size) fSize + 1 else fSize
    }
  }

  def getShortOptions(): Future[Seq[models.ShortOption]] =
    db.run(shortOptions.result)

  def getShortOptionByName(name: String): Future[Option[models.ShortOption]] =
    db.run(shortOptions.filter(_.name === name).result.headOption)

  def updateShortOptionByName(name: String, value: String): Future[Option[models.ShortOption]] =
    db.run(shortOptions.filter(_.name === name).map(_.value).update(value).map(_ > 1)
      .flatMap(_ => shortOptions.filter(_.name === name).result.headOption))

  def findAccountBySessionKeyAndIPWithBalancesAndRoles(sessionKey: String, ip: String): Future[Option[models.Account]] = {
    val query = for {
      dbSession <- sessions.filter(t => t.sessionKey === sessionKey && t.ip === ip)
      dbAccount <- accounts.filter(_.id === dbSession.userId)
    } yield (dbAccount, dbSession)
    val result = db.run(query.result.headOption).map(_.map {
      case (dbAccount, dbSession) => dbAccount.copy(sessionOpt = Some(dbSession))
    }).flatMap(_ match {
      case Some(account) => fillAccountBalances(account)
      case _ => Future.successful(None)
    })
    updateAccountWithRoles(result)
  }

  def getNotificationTasksWithEmailAndProduct(pageId: Long): Future[Seq[models.ScheduledTask]] = {
    val now = System.currentTimeMillis()
    db.run(scheduledTasks
      .filter(t => t.taskType === models.TaskType.NOTIFICATIONS && t.executed.isEmpty && t.planned < now)
      .drop(if (pageId > 0) AppConstants.DEFAULT_PAGE_SIZE * (pageId - 1) else 0).take(AppConstants.DEFAULT_PAGE_SIZE)
      .join(accounts).on(_.accountId === _.id)
      .join(posts).on { case ((task, account), product) => task.productId === product.id }
      .result) map {
      _.map {
        case ((task, account), product) =>
          task.copy(emailOpt = Some(account.email), productOpt = Some(postFrom(product)))
      }
    }
  }

  def notificationsSended(ids: Seq[Long]): Future[Int] =
    db.run(scheduledTasks.filter(_.id inSet ids).map(_.executed).update(Some(System.currentTimeMillis())).transactionally)

  def getNotificationsPages(): Future[Int] = {
    val now = System.currentTimeMillis()
    db.run(scheduledTasks
      .filter(t => t.taskType === models.TaskType.NOTIFICATIONS && t.executed.isEmpty && t.planned < now)
      .size.result) map pages
  }

  def getPostsPage(pageId: Int): Future[Seq[models.Post]] =
    db.run(posts.sortBy(_.id.desc).drop(if (pageId > 0) AppConstants.DEFAULT_PAGE_SIZE * (pageId - 1) else 0).take(AppConstants.DEFAULT_PAGE_SIZE).result) map (_ map postFrom)

  def getPostsPages(): Future[Int] =
    db.run(posts.length.result).map { r =>
      pages(r, AppConstants.DEFAULT_PAGE_SIZE.toInt)
    }

  def getTagsPage(pageId: Long): Future[Seq[models.Tag]] =
    db.run(tags.sortBy(_.id.desc).drop(if (pageId > 0) AppConstants.DEFAULT_PAGE_SIZE * (pageId - 1) else 0).take(AppConstants.DEFAULT_PAGE_SIZE).result)

  def getAccountsPage(pageId: Long): Future[Seq[models.Account]] =
    db.run(accounts.sortBy(_.id.desc).drop(if (pageId > 0) AppConstants.DEFAULT_PAGE_SIZE * (pageId - 1) else 0).take(AppConstants.DEFAULT_PAGE_SIZE).result)

  def getAccountsPages(): Future[Int] =
    db.run(accounts.size.result) map pages

  def findAccountById(id: Long) =
    getAccountFromQuery(accounts.filter(_.id === id))

  def findAccountByEmail(email: String): Future[Option[Account]] =
    getAccountFromQuery(accounts.filter(_.email === email))

  def isPostOwner(postId: Long, accountId: Long): Future[Boolean] =
    db.run(posts.filter(t => t.id === postId && t.ownerId === accountId).exists.result)

  def findAccountByLoginOrEmailWithBalances(loginOrElamil: String): Future[Option[Account]] = {
    db.run {
      for {
        dbaccount <- accounts.filter(u => u.login === loginOrElamil || u.email === loginOrElamil).result.head
        balanceTokens <- filterCurrentAccountBalanceValue(dbaccount.id, CurrencyType.TOKEN).result.headOption
      } yield (dbaccount, balanceTokens)
    } map {
      case (dbaccount, balanceTokens) => Some(dbaccount.copy(balanceTokenOpt = balanceTokens))
    }
  }

  def findAccountByLoginOrEmail(loginOrElamil: String): Future[Option[Account]] =
    getAccountFromQuery(accounts.filter(u => u.login === loginOrElamil || u.email === loginOrElamil))

  def findAccountIdByLoginOrEmail(loginOrElamil: String): Future[Option[Long]] =
    getAccountFromQuery(accounts.filter(u => u.login === loginOrElamil || u.email === loginOrElamil)).map(_.map(_.id))

  def findAccountByLogin(login: String): Future[Option[Account]] =
    getAccountFromQuery(accounts.filter(_.login === login))

  def findBalances(pageId: Long): Future[Seq[models.Balance]] =
    db.run(balances.sortBy(_.id.desc).drop(if (pageId > 0) AppConstants.DEFAULT_PAGE_SIZE * (pageId - 1) else 0).take(AppConstants.DEFAULT_PAGE_SIZE).result)

  def findAccountByIdWithBalances(id: Long): Future[Option[Account]] = {
    db.run {
      for {
        dbaccount <- accounts.filter(_.id === id).result.head
        balanceTokens <- filterCurrentAccountBalanceValue(dbaccount.id, CurrencyType.TOKEN).result.headOption
      } yield (dbaccount, balanceTokens)
    } map {
      case (dbaccount, balanceTokens) => Some(dbaccount.copy(balanceTokenOpt = balanceTokens))
    }
  }

  def findAccountByLoginWithBalances(login: String): Future[Option[Account]] = {
    db.run {
      for {
        dbaccount <- accounts.filter(_.login === login).result.head
        balanceTokens <- filterCurrentAccountBalanceValue(dbaccount.id, CurrencyType.TOKEN).result.headOption
      } yield (dbaccount, balanceTokens)
    } map {
      case (dbaccount, balanceTokens) => Some(dbaccount.copy(balanceTokenOpt = balanceTokens))
    }
  }

  def setCommentStatus(commentId: Long, status: Int): Future[Boolean] =
    db.run(comments.filter(_.id === commentId).map(_.status).update(status).transactionally.map(_ > 0))

  def isAccountOwnerOfComment(accountId: Long, commentId: Long): Future[Boolean] =
    db.run(comments.filter(t => t.id === commentId && t.ownerId === accountId).exists.result)

  def isLoginExists(login: String): Future[Boolean] =
    db.run(accounts.filter(t => t.login === login.trim.toLowerCase || t.email === login).exists.result)

  def isEmailExists(email: String): Future[Boolean] =
    db.run(accounts.filter(_.email === email.trim.toLowerCase).exists.result)

  def findSessionByAccountIdSessionKeyAndIP(userId: Long, ip: String, sessionKey: String): Future[Option[models.Session]] =
    getSessionFromQuery(sessions.filter(s => s.userId === userId && s.ip === ip && s.sessionKey === sessionKey))

  def findPostWithAccountByPostId(postId: Long, actorIdOpt: Option[Long]): Future[Option[models.Post]] =
    actorIdOpt.fold(findPostWithAccountByPostId(postId))(t => findPostWithAccountByPostId(postId, t))

  def findPostWithAccountByPostId(postId: Long): Future[Option[models.Post]] =
    db.run(for {
      dbPost <- posts.filter(_.id === postId).result.head
      dbAccount <- accounts.filter(_.id === dbPost.ownerId).result.head
    } yield (dbPost, dbAccount)) map {
      case (dbPost, dbAccount) =>
        val post: models.Post = postFrom(dbPost)
        post.ownerOpt = Some(dbAccount)
        Some(post)
    } flatMap (
      _ match {
        case Some(post) => findLikes(Seq(post.id), TargetType.POST).map { likes =>
          post.likes = likes
          Some(post)
        }
        case _ => Future.successful(None)
      })

  def findLikes(targetIds: Seq[Long], targetType: Int): Future[Seq[models.Like]] = {
    db.run((likes.filter(_.targetId inSet targetIds).filter(_.targetType === targetType).sortBy(_.id.desc) join
      accounts.map(t => (t.login, t.id, t.accountType, t.name)) on { case (like, userProp) => like.ownerId === userProp._2 }).take(targetIds.length * maxLikesView)
      .result).map(_.map {
        case (like, userProp) =>
          like.copy(
            userLoginOpt = Some(userProp._1),
            displayNameOpt = userProp._3 match {
              case AccountType.COMPANY => userProp._4
              case _ => Some(userProp._1)
            })
    })
  }

  def findTagsByTargetIds(targetType: Int, postIds: Seq[Long]): Future[Seq[(models.Tag, Long)]] = {
    val query = for {
      (tInp, t) <- tagsToTargets.filter(t => t.targetId.inSet(postIds) && t.targetType === targetType) join tags on { case (tInps, tg) => tInps.tagId === tg.id }
    } yield (tInp, t)
    db.run(query.result).map(_.map {
      case (tInp, t) => (t, tInp.targetId)
    })
  }

  def findTagsByTargetId(targetType: Int, targetId: Long): Future[Seq[models.Tag]] = {
    val query = for {
      (tInp, t) <- tagsToTargets.filter(t => t.targetId === targetId && t.targetType === targetType) join tags on { case (tInps, tg) => tInps.tagId === tg.id }
    } yield t
    db.run(query.result)
  }

  def findPostWithAccountByPostId(postId: Long, actorId: Long): Future[Option[models.Post]] = {
    val query = for {
      ((dbPost, dbAccount), dbLikedOpt) <- posts.filter(_.id === postId) join
        accounts on { case (post, user) => post.ownerId === user.id } joinLeft
        likes.filter(t => t.targetType === TargetType.POST && t.ownerId === actorId) on { case ((post, user), like) => like.targetId === post.id }
    } yield (dbPost, dbAccount, dbLikedOpt)
    db.run(query.result.headOption) map (_.map {
      case (dbPost, dbAccount, dbLikedOpt) =>
        val post = postFrom(dbPost)
        post.ownerOpt = Some(dbAccount)
        post.likedOpt = if (dbLikedOpt.isEmpty) Some(false) else Some(true)
        post
    }) flatMap (
      _ match {
        case Some(post) => findLikes(Seq(post.id), TargetType.POST).map { likes =>
          post.likes = likes
          Some(post)
        }
        case _ => Future.successful(None)
      }) flatMap (
        _ match {
          case Some(post) => findTagsByTargetId(TargetType.POST, post.id).map { tags =>
            post.tags = tags
            Some(post)
          }
          case _ => Future.successful(None)
        })
  }

  def fillAccountBalances(account: models.Account): Future[Option[Account]] = {
    db.run {
      for {
        balanceTokens <- filterCurrentAccountBalanceValue(account.id, CurrencyType.TOKEN).result.headOption
      } yield (balanceTokens)
    } map { case (balanceTokens) => Some(account.copy(balanceTokenOpt = balanceTokens)) }
  }

  def findAccountBySessionKeyAndIPWithBalances(sessionKey: String, ip: String): Future[Option[models.Account]] = {
    val query = for {
      dbSession <- sessions.filter(t => t.sessionKey === sessionKey && t.ip === ip)
      dbAccount <- accounts.filter(_.id === dbSession.userId)
    } yield (dbAccount, dbSession)
    db.run(query.result.headOption).map(_.map {
      case (dbAccount, dbSession) => dbAccount.copy(sessionOpt = Some(dbSession))
    }).flatMap(_ match {
      case Some(account) => fillAccountBalances(account)
      case _ => Future.successful(None)
    })
  }

  def approveScheduledTransactions(): Future[Int] = {
    val timestamp = System.currentTimeMillis

    val query = transactions.filter(t => t.state === TxState.SCHEDULED && t.scheduled < timestamp).result.flatMap { txs =>
      DBIO.sequence {
        txs.map { tx =>
          transactions.filter(_.id === tx.id)
            .map(itx => (itx.scheduled, itx.processed, itx.state))
            .update(None, Some(timestamp), TxState.APPROVED).zip {
              if (tx.toType == TargetType.ACCOUNT)
                updateCurrentAccountBalance(tx.toId.get, tx.currencyId, tx.amount)
              else
                DBIO.successful(0)
            }.map { case (a, b) => b }
        }
      }.map(_.sum)
    }
    db.run(query.transactionally)
  }

  def findPostsWithAccountsByText(pageId: Long, text: String, actorIdOpt: Option[Long]): Future[Seq[models.Post]] =
    actorIdOpt.fold(findPostsWithAccountsByText(pageId, text))(actorId => findPostsWithAccountsByText(pageId, text, actorId))

  def findPostsWithAccountsByText(pageId: Long, text: String, actorId: Long): Future[Seq[models.Post]] = {
    val searchString = "%" + text + "%"
    val query = for {
      (dbPost, dbLikedOpt) <- (posts.filter(t => t.content.like(searchString) || t.title.like(searchString)) union
        posts.filter(_.ownerId in {
          accounts.filter(_.login.like(searchString)).map(_.id)
        }))
        .joinLeft(likes.filter(t => t.targetType === TargetType.POST && t.ownerId === actorId)).on { case (post, like) => like.targetId === post.id }
        .sortBy(_._1.id.desc)
        .drop(if (pageId > 0) AppConstants.DEFAULT_PAGE_SIZE * (pageId - 1) else 0)
        .take(AppConstants.DEFAULT_PAGE_SIZE)
      dbAccount <- accounts.filter(_.id === dbPost.ownerId)
    } yield (dbPost, dbLikedOpt, dbAccount)
    db.run(query.result).map(_.map {
      case (dbPost, dbLikedOpt, dbAccount) =>
        val post = postFrom(dbPost)
        post.ownerOpt = Some(dbAccount)
        post.likedOpt = Some(if (dbLikedOpt.isDefined) true else false)
        post
    }) flatMap (posts =>
      findLikes(posts.map(_.id), TargetType.POST).map { likes =>
        posts.foreach(post => post.likes = likes.filter(_.targetId == post.id).take(maxLikesView))
        posts
      }) flatMap (posts =>
      findTagsByTargetIds(TargetType.POST, posts.map(_.id)).map { touples =>
        posts.foreach { post => post.tags = touples.filter(_._2 == post.id).map(_._1) }
        posts
      })
  }

  def findPostsWithAccountsByText(pageId: Long, text: String): Future[Seq[models.Post]] = {
    val searchString = "%" + text + "%"
    val query = for {
      dbPost <- (posts.filter(t => t.content.like(searchString) || t.title.like(searchString)) union
        posts.filter(_.ownerId in {
          accounts.filter(_.login.like(searchString)).map(_.id)
        })).sortBy(_.id.desc)
        .drop(if (pageId > 0) AppConstants.DEFAULT_PAGE_SIZE * (pageId - 1) else 0)
        .take(AppConstants.DEFAULT_PAGE_SIZE)
      dbAccount <- accounts.filter(_.id === dbPost.ownerId)
    } yield (dbPost, dbAccount)
    db.run(query.result).map(_.map {
      case (dbPost, dbAccount) =>
        val post = postFrom(dbPost)
        post.ownerOpt = Some(dbAccount)
        post
    }) flatMap (posts =>
      findLikes(posts.map(_.id), TargetType.POST).map { likes =>
        posts.foreach(post => post.likes = likes.filter(_.targetId == post.id).take(maxLikesView))
        posts
      }) flatMap (posts =>
      findTagsByTargetIds(TargetType.POST, posts.map(_.id)).map { touples =>
        posts.foreach { post => post.tags = touples.filter(_._2 == post.id).map(_._1) }
        posts
      })
  }

  def findPostsWithAccountsByCategoryTagNames(
    pageSizeOpt: Option[Int],
    actorIdOpt: Option[Long],
    userIdOpt: Option[Long],
    categoryOpt: Option[String],
    pageId: Long, tagNamesOpt: Option[Seq[String]]): Future[Seq[models.Post]] =
    tagNamesOpt match {
      case Some(tagNames) =>
        db.run(tags.filter(_.name inSet tagNames).map(_.id).result) flatMap { tagIds =>
          actorIdOpt.fold {
            findPostsWithAccountsByCategoryTagIds(pageSizeOpt, userIdOpt, categoryOpt, pageId, Some(tagIds))
          } { actorId =>
            findPostsWithAccountsByCategoryTagIds(pageSizeOpt, actorId, userIdOpt, categoryOpt, pageId, Some(tagIds))
          }
        }
      case _ =>
        actorIdOpt.fold {
          findPostsWithAccountsByCategoryTagIds(pageSizeOpt, userIdOpt, categoryOpt, pageId, None)
        } { actorId =>
          findPostsWithAccountsByCategoryTagIds(pageSizeOpt, actorId, userIdOpt, categoryOpt, pageId, None)
        }
    }

  def findPostsPagesWithAccountsByCategoryTagNames(
    pageSizeOpt: Option[Int],
    userIdOpt: Option[Long],
    categoryOpt: Option[String],
    tagNamesOpt: Option[Seq[String]]): Future[Int] =
    tagNamesOpt match {
      case Some(tagNames) =>
        db.run(tags.filter(_.name inSet tagNames).map(_.id).result) flatMap { tagIds =>
          findPostsPagesWithAccountsByCategoryTagIds(pageSizeOpt, userIdOpt, categoryOpt, Some(tagIds))
        }
      case _ =>
        findPostsPagesWithAccountsByCategoryTagIds(pageSizeOpt, userIdOpt, categoryOpt, None)
    }

  def findPostsPagesWithAccountsByCategoryTagIds(
    pageSizeOpt: Option[Int],
    userIdOpt: Option[Long],
    categoryOpt: Option[String],
    tagIdsOpt: Option[Seq[Long]]) = {
    db.run(findPostsWithAccountsByCategoryTagIdsQuery(userIdOpt, categoryOpt, tagIdsOpt).size.result) map pages
  }

  def findPostsWithAccountsByCategoryTagIdsQuery(
    userIdOpt: Option[Long],
    categoryOpt: Option[String],
    tagIdsOpt: Option[Seq[Long]]) = {
    val first = userIdOpt match {
      case Some(userId) => posts.filter(_.ownerId === userId)
      case _ => posts
    }
    tagIdsOpt match {
      case Some(tagIds) => first filter (_.id in {
        tagsToTargets.filter(t => t.tagId.inSet(tagIds) && t.targetType === TargetType.POST).map(_.targetId)
      })
      case _ => first
    }
  }

  def findPostsWithAccountsByCategoryTagIds(
    pageSizeOpt: Option[Int],
    userIdOpt: Option[Long],
    categoryOpt: Option[String],
    pageId: Long,
    tagIdsOpt: Option[Seq[Long]]): Future[Seq[models.Post]] = {
    val query = for {
      (dbPost, dbAccount) <- (findPostsWithAccountsByCategoryTagIdsQuery(userIdOpt, categoryOpt, tagIdsOpt)
        join accounts on { case (post, user) => post.ownerId === user.id })
        .sortBy {
          case (post, user) =>
            (categoryOpt match {
              case Some(PostsFilter.NEW) => post.created.desc
              case Some(PostsFilter.HOT) => post.commentsCount.desc
              case Some(PostsFilter.TRENDING) => post.likesCount.desc
              case Some(PostsFilter.PROMOUTED) => post.promo.desc
              case _ => post.id.desc
            })
        }.drop(if (pageId > 0) pageSizeOpt.getOrElse(AppConstants.DEFAULT_PAGE_SIZE) * (pageId - 1) else 0)
        .take(pageSizeOpt.getOrElse(AppConstants.DEFAULT_PAGE_SIZE))
    } yield (dbPost, dbAccount)
    db.run(query.result).map(_.map {
      case (dbPost, dbAccount) =>
        val post = postFrom(dbPost)
        post.ownerOpt = Some(dbAccount)
        post
    }) flatMap (posts =>
      findLikes(posts.map(_.id), TargetType.POST).map { likes =>
        posts.foreach(post => post.likes = likes.filter(_.targetId == post.id).take(maxLikesView))
        posts
      }) flatMap (posts =>
      findTagsByTargetIds(TargetType.POST, posts.map(_.id)).map { touples =>
        posts.foreach { post => post.tags = touples.filter(_._2 == post.id).map(_._1) }
        posts
      })
  }

  def findPostsWithAccountsByCategoryTagIds(
    pageSizeOpt: Option[Int],
    actorId: Long,
    userIdOpt: Option[Long],
    categoryOpt: Option[String],
    pageId: Long,
    tagIdsOpt: Option[Seq[Long]]): Future[Seq[models.Post]] = {
    val query = for {
      ((dbPost, dbAccount), dbLikeOpt) <- (findPostsWithAccountsByCategoryTagIdsQuery(userIdOpt, categoryOpt, tagIdsOpt)
        join accounts on { case (post, user) => post.ownerId === user.id })
        .joinLeft(likes.filter(t => t.targetType === TargetType.POST && t.ownerId === actorId)).on { case ((post, user), like) => like.targetId === post.id }
        .sortBy {
          case ((post, user), like) =>
            (categoryOpt match {
              case Some(PostsFilter.NEW) => post.created.desc
              case Some(PostsFilter.HOT) => post.commentsCount.desc
              case Some(PostsFilter.TRENDING) => post.likesCount.desc
              case Some(PostsFilter.PROMOUTED) => post.promo.desc
              case _ => post.id.desc
            })
        }.drop(if (pageId > 0) pageSizeOpt.getOrElse(AppConstants.DEFAULT_PAGE_SIZE) * (pageId - 1) else 0)
        .take(pageSizeOpt.getOrElse(AppConstants.DEFAULT_PAGE_SIZE))
    } yield (dbPost, dbAccount, dbLikeOpt)
    db.run(query.result).map(_.map {
      case (dbPost, dbAccount, dbLikeOpt) =>
        val post = postFrom(dbPost)
        post.ownerOpt = Some(dbAccount)
        post.likedOpt = if (dbLikeOpt.isEmpty) Some(false) else Some(true)
        post
    }) flatMap (posts =>
      findLikes(posts.map(_.id), TargetType.POST).map { likes =>
        posts.foreach(post => post.likes = likes.filter(_.targetId == post.id).take(maxLikesView))
        posts
      }) flatMap (posts =>
      findTagsByTargetIds(TargetType.POST, posts.map(_.id)).map { touples =>
        posts.foreach { post => post.tags = touples.filter(_._2 == post.id).map(_._1) }
        posts
      })
  }

  def findPostsWithAccountsByCategoryByNames(
    actorIdOpt: Option[Long],
    userLogin: String,
    categoryOpt: Option[String],
    pageId: Long,
    tagNamesOpt: Option[Seq[String]])(implicit ac: AppContext): Future[Seq[models.Post]] =
    db.run(accounts.filter(_.login === userLogin).result.headOption).flatMap(_.fold(future(Seq[models.Post]())) { user =>
      findPostsWithAccountsByCategoryTagNames(None, actorIdOpt, Some(user.id), categoryOpt, pageId, tagNamesOpt)
    })

  def findAccountBySUIDAndSessionId(sessionId: Long, sessionKey: String): Future[Option[Account]] = {
    val query = for {
      dbSession <- sessions.filter(t => t.id === sessionId && t.sessionKey === sessionKey)
      dbAccount <- accounts.filter(_.id === dbSession.userId)
    } yield (dbAccount, dbSession)
    db.run(query.result.headOption).map(_.map {
      case (dbAccount, dbSession) => dbAccount.copy(sessionOpt = Some(dbSession))
    })
  }

  def findCommentsWithAccountsByPostId(postId: Long, userIdOpt: Option[Long]): Future[Seq[models.Comment]] =
    userIdOpt.fold(findCommentsWithAccountsByPostId(postId))(t => findCommentsWithAccountsByPostId(postId, t))

  def findCommentsWithAccountsByPostId(postId: Long, userId: Long): Future[Seq[models.Comment]] = {
    val query = for {
      ((dbComment, dbAccount), dbLikeOpt) <- comments.filter(_.targetId === postId) join
        accounts on (_.ownerId === _.id) joinLeft
        likes.filter(t => t.targetType === TargetType.COMMENT && t.ownerId === userId) on { case ((c, u), l) => c.id === l.targetId }
    } yield (dbComment, dbAccount, dbLikeOpt)
    db.run(query.result).map(_.map {
      case (dbComment, dbAccount, dbLikeOpt) =>
        val comment = commentFrom(dbComment)
        comment.ownerOpt = Some(dbAccount)
        comment.likedOpt = if (dbLikeOpt.isEmpty) Some(false) else Some(true)
        comment
    }) flatMap (comments =>
      findLikes(comments.map(_.id), TargetType.COMMENT).map { likes =>
        comments.foreach(comment => comment.likes = likes.filter(_.targetId == comment.id).take(maxLikesView))
        comments
      })
  }

  def findCommentsWithAccountsByPostId(postId: Long): Future[Seq[models.Comment]] = {
    val query = for {
      dbComment <- comments.filter(_.targetId === postId)
      dbAccount <- accounts.filter(_.id === dbComment.targetId)
    } yield (dbComment, dbAccount)
    db.run(query.result).map(_.map {
      case (dbComment, dbAccount) =>
        val comment = commentFrom(dbComment)
        comment.ownerOpt = Some(dbAccount)
        comment
    }) flatMap (comments =>
      findLikes(comments.map(_.id), TargetType.COMMENT).map { likes =>
        comments.foreach(comment => comment.likes = likes.filter(_.targetId == comment.id))
        comments
      })
  }

  def findCommentsWithAccountsForAllAccountPosts(login: String, pageId: Long, userIdOpt: Option[Long]): Future[Seq[models.Comment]] =
    db.run(accounts.filter(_.login === login).result.headOption) flatMap (_.fold(future(Seq[models.Comment]())) { user =>
      findCommentsWithAccountsForAllAccountPosts(user.id, pageId, userIdOpt)
    })

  def findCommentsWithAccountsForAllAccountPosts(userId: Long, pageId: Long, userIdOpt: Option[Long]): Future[Seq[models.Comment]] =
    userIdOpt.fold(findCommentsWithAccountsForAllAccountPosts(userId, pageId))(uid => findCommentsWithAccountsForAllAccountPosts(userId, pageId, uid))

  def findCommentsWithAccountsForAllAccountPosts(userId: Long, pageId: Long, actorAccountId: Long): Future[Seq[models.Comment]] = {
    val query = for {
      (((dbPost, dbComment), dbAccount), dbLikeOpt) <- (posts.filter(_.ownerId === userId) join
        comments on (_.id === _.targetId) join
        accounts on { case ((post, comment), user) => user.id === comment.ownerId } joinLeft
        likes.filter(t => t.targetType === TargetType.COMMENT && t.ownerId === actorAccountId) on { case (((post, comment), user), like) => like.targetId === comment.id })
        .sortBy { case (((post, comment), user), likeOpt) => comment.id.desc }
        .drop(if (pageId > 0) AppConstants.DEFAULT_PAGE_SIZE * (pageId - 1) else 0)
        .take(AppConstants.DEFAULT_PAGE_SIZE)
    } yield (dbComment, dbAccount, dbLikeOpt)
    db.run(query.result).map(_.map {
      case (dbComment, dbAccount, dbLikeOpt) =>
        val comment = commentFrom(dbComment)
        comment.ownerOpt = Some(dbAccount)
        comment.likedOpt = if (dbLikeOpt.isEmpty) Some(false) else Some(true)
        comment
    }) flatMap (comments =>
      findLikes(comments.map(_.id), TargetType.COMMENT).map { likes =>
        comments.foreach(comment => comment.likes = likes.filter(_.targetId == comment.id).take(maxLikesView))
        comments
      })
  }

  def findCommentsPagesWithAccountsForAllAccountPosts(userId: Long): Future[Int] = {
    db.run(posts.filter(_.ownerId === userId).join(comments).on(_.id === _.targetId).size.result) map pages
  }

  def findCommentsWithAccountsForAllAccountPosts(userId: Long, pageId: Long): Future[Seq[models.Comment]] = {
    val query = for {
      ((dbPost, dbComment), dbAccount) <- (posts.filter(_.ownerId === userId) join
        comments on (_.id === _.targetId) join
        accounts on { case ((post, comment), user) => user.id === comment.ownerId })
        .sortBy { case ((post, comment), user) => comment.id.desc }
        .drop(if (pageId > 0) AppConstants.DEFAULT_PAGE_SIZE * (pageId - 1) else 0)
        .take(AppConstants.DEFAULT_PAGE_SIZE)
    } yield (dbComment, dbAccount)
    db.run(query.result).map(_.map {
      case (dbComment, dbAccount) =>
        val comment = commentFrom(dbComment)
        comment.ownerOpt = Some(dbAccount)
        comment
    }) flatMap (comments =>
      findLikes(comments.map(_.id), TargetType.COMMENT).map { likes =>
        comments.foreach(comment => comment.likes = likes.filter(_.targetId == comment.id).take(maxLikesView))
        comments
      })
  }

  def isAlreadyLikeCommentByAccount(userId: Long, commentId: Long): Future[Boolean] =
    db.run(likes
      .filter(t => t.ownerId === userId && t.targetId === commentId && t.targetType === TargetType.COMMENT)
      .result
      .headOption).map(_.fold(false)(_ => true))

  def isAlreadyLikePostByAccount(userId: Long, postId: Long): Future[Boolean] =
    db.run(likes
      .filter(t => t.ownerId === userId && t.targetId === postId && t.targetType === TargetType.POST)
      .result
      .headOption).map(_.fold(false)(_ => true))

  def findCommentById(id: Long): Future[Option[models.Comment]] =
    getCommentFromQuery(comments.filter(_.id === id))

  def getCommentFromQuery(query: Query[(Comments), (DBComment), Seq]): Future[Option[models.Comment]] =
    db.run(query.result.headOption).map(_.map(commentFrom))

  def findPostById(id: Long): Future[Option[models.Post]] =
    getPostFromQuery(posts.filter(_.id === id))

  def getPostFromQuery(query: Query[(Posts), (DBPost), Seq]): Future[Option[models.Post]] =
    db.run(query.result.headOption).map(_.map(postFrom))

  def getAccountFromQuery(query: Query[(Accounts), (models.Account), Seq]): Future[Option[models.Account]] =
    db.run(query.result.headOption)

  def getSessionFromQuery(query: Query[(Sessions), (models.Session), Seq]): Future[Option[models.Session]] =
    db.run(query.result.headOption)

  def createPostWithPostsCounterUpdate(
    userId: Long,
    targetIdOpt: Option[Long],
    title: String,
    content: String,
    thumbnail: Option[String],
    postType: Int,
    tagNames: Seq[String]): Future[Option[models.Post]] = {

    val query = for {
      user <- accounts.filter(_.id === userId).result.head
      post <- (posts returning posts.map(_.id) into ((v, id) => v.copy(id = id))) += new models.daos.DBPost(
        0,
        userId,
        targetIdOpt,
        title,
        thumbnail,
        content,
        models.ContentType.MARKDOWN,
        postType,
        models.PostStatus.ACTIVE,
        0,
        models.PostStatus.ACTIVE,
        0,
        0,
        0,
        System.currentTimeMillis,
        0,
        0,
        0,
        0)
      targetPostsCountOpt <- targetIdOpt match {
        case Some(targetId) =>
          posts.filter(t => t.id === targetId && t.postType === TargetType.PRODUCT)
            .map(_.postsCount).result.headOption
        case _ => DBIO.successful(None)
      }
      _ <- targetPostsCountOpt match {
        case Some(targetPostsCount) =>
          posts.filter(t => t.id === targetIdOpt.get && t.postType === TargetType.PRODUCT)
            .map(_.postsCount).update(targetPostsCount + 1)
        case _ => DBIO.successful(None)
      }
      _ <- accounts.filter(_.id === userId)
        .map(t => (t.postsCount, t.postsCounter))
        .update(user.postsCount + 1, user.postsCounter + 1)
      actualPost <- posts.filter(_.id === post.id).result.head
    } yield actualPost

    db.run(query.transactionally) flatMap { dbPost =>
      getOrCreateTags(tagNames) flatMap { tags =>
        assignTagsToPost(tags.map(_.id), dbPost.id).map { _ =>
          val post = postFrom(dbPost)
          post.tags = tags
          Some(post)
        }
      }
    }
  }

  def updatePost(postId: Long, title: String, content: String): Future[Boolean] =
    db.run(posts.filter(_.id === postId).map(post => (post.title, post.content)).update(title, content).transactionally.map(_ > 0))

  def createProductWithPostsCounterUpdate(
    userId: Long,
    name: String,
    about: String,
    thumbnail: Option[String],
    tagNames: Seq[String]): Future[Option[models.Post]] = {

    val query = for {
      user <- accounts.filter(_.id === userId).result.head
      product <- (posts returning posts.map(_.id) into ((v, id) => v.copy(id = id))) += new models.daos.DBPost(
        0,
        userId,
        None,
        name,
        thumbnail,
        about,
        models.ContentType.MARKDOWN,
        TargetType.PRODUCT,
        models.PostStatus.ACTIVE,
        0,
        models.PostStatus.ACTIVE,
        0,
        0,
        0,
        System.currentTimeMillis,
        0,
        0,
        0,
        0)
      _ <- accounts.filter(_.id === userId)
        .map(t => (t.postsCount, t.postsCounter))
        .update(user.postsCount + 1, user.postsCounter + 1)
    } yield product

    db.run(query.transactionally) map (t => Some(postFrom(t)))
    //    db.run(query.transactionally) flatMap { dbProduct =>
    //      getOrCreateTags(tagNames) flatMap { tags =>
    //        assignTagsToPost(tags.map(_.id), dbProduct.id).map { _ =>
    //          val post = productFrom(dbProduct)
    //          post.tags = tags
    //          Some(post)
    //        }
    //      }
    //    }
  }

  def assignTagsToPost(tagsIds: Seq[Long], postId: Long) =
    db.run(DBIO.sequence(tagsIds.map(tagId => tagsToTargets += models.TagToTarget(tagId, postId, TargetType.POST, System.currentTimeMillis))).map(_.sum).transactionally)

  def getOrCreateTags(names: Seq[String]): Future[Seq[models.Tag]] =
    db.run(DBIO.sequence(names.map(getOrCreateTagAction)).transactionally)

  def getOrCreateTagAction(name: String) =
    tags.filter(_.name === name).result.headOption.flatMap {
      case Some(tag) => DBIO.successful(tag)
      case None => (tags returning tags.map(_.id) into ((v, id) => v.copy(id = id))) += models.Tag(0, name)
    }

  def udateCommentReward(optTx: Option[models.Transaction], comment: DBComment, reward: Long): DBIOAction[Int, NoStream, Effect.Write] =
    optTx match {
      case Some(tx) => comments.filter(_.id === comment.id).map(_.reward).update(comment.reward + reward)
      case _ => DBIO.successful(0)
    }

  def udatePostReward(optTx: Option[models.Transaction], post: DBPost, reward: Long, currency: Int): DBIOAction[Int, NoStream, Effect.Write] =
    optTx match {
      case Some(tx) => posts.filter(_.id === post.id).map(_.reward).update(post.reward + reward)
      case _ => DBIO.successful(0)
    }

  def findAccountWithRolesById(userId: Long): Future[Option[Account]] =
    updateAccountWithRoles(findAccountById(userId))

  def updateTxsToAccountsWithAccounts(t: models.Transaction): Future[models.Transaction] =
    t.toId match {
      case Some(id) => findAccountById(id).map(_.fold(t) { u => t.copy(toAccountOpt = Some(u)) })
      case _        => future(t)
    }

  def updateTxsFromAccountsWithAccounts(t: models.Transaction): Future[models.Transaction] =
    t.fromId match {
      case Some(id) => findAccountById(id).map(_.fold(t) { u => t.copy(fromAccountOpt = Some(u)) })
      case _        => future(t)
    }

  def updateAccountWithRoles(futureOptAccount: Future[Option[Account]]): Future[Option[Account]] =
    futureOptAccount flatMap {
      case Some(u) => findRolesByAccountId(u.id).map { r => Some(u.copy(roles = r)) }
      case None    => future(None)
    }

  def findRolesByAccountId(userId: Long) =
    db.run(roles.filter(_.userId === userId).result).map(_.map(_.role))

  def getTransactionsByAccountIdWithInfo(userId: Long, pageId: Long, isSchelduled: Boolean): Future[Seq[models.Transaction]] = {
    findTransactionsByAccountId(userId, pageId, isSchelduled).flatMap { st =>
      Future.sequence(st.map(t => updateTxsFromAccountsWithAccounts(t).flatMap(updateTxsToAccountsWithAccounts)))
    }
  }

  def getTransactionsPagesByAccountIdWithInfo(userId: Long, isSchelduled: Boolean): Future[Int] = {
    if (isSchelduled)
      db.run(transactions
        .filter(t => ((t.fromType === models.TargetType.ACCOUNT && t.fromId === userId) ||
          (t.toType === models.TargetType.ACCOUNT && t.toId === userId)) && t.scheduled.isDefined).size.result) map pages
    else
      db.run(transactions
        .filter(t => (t.fromType === models.TargetType.ACCOUNT && t.fromId === userId) ||
          (t.toType === models.TargetType.ACCOUNT && t.toId === userId)).size.result) map pages
  }

  def findTransactionsByAccountId(userId: Long, pageId: Long, isSchelduled: Boolean): Future[Seq[models.Transaction]] = {
    val query = for {
      dbTx <- if (isSchelduled)
        transactions
          .filter(t => ((t.fromType === models.TargetType.ACCOUNT && t.fromId === userId) ||
            (t.toType === models.TargetType.ACCOUNT && t.toId === userId)) && t.scheduled.isDefined)
          .sortBy(_.id.desc)
          .drop(if (pageId > 0) AppConstants.DEFAULT_PAGE_SIZE * (pageId - 1) else 0)
          .take(AppConstants.DEFAULT_PAGE_SIZE)
      else
        transactions
          .filter(t => (t.fromType === models.TargetType.ACCOUNT && t.fromId === userId) ||
            (t.toType === models.TargetType.ACCOUNT && t.toId === userId))
          .sortBy(_.id.desc)
          .drop(if (pageId > 0) AppConstants.DEFAULT_PAGE_SIZE * (pageId - 1) else 0)
          .take(AppConstants.DEFAULT_PAGE_SIZE)
    } yield (dbTx)
    db.run(query.result)
  }

  def invalidateSessionBySessionKeyAndIP(sessionKey: String, ip: String): Future[Boolean] =
    db.run(sessions.filter(t => t.sessionKey === sessionKey && t.ip === ip).map(_.expire).update(System.currentTimeMillis).transactionally) map (r => if (r == 1) true else false)

  def updateAccountAbout(userId: Long, about: String): Future[Boolean] =
    db.run(accounts.filter(_.id === userId).map(_.about).update(Some(about)).transactionally) map (r => if (r == 1) true else false)

  def updateAccountBackground(userId: Long, bgURL: String): Future[Boolean] =
    db.run(accounts.filter(_.id === userId).map(_.background).update(Some(bgURL)).transactionally) map (r => if (r == 1) true else false)

  def updateAccountAvatar(userId: Long, avatarURL: String): Future[Boolean] =
    db.run(accounts.filter(_.id === userId).map(_.avatar).update(Some(avatarURL)).transactionally) map (r => if (r == 1) true else false)

  def updateAccountName(userId: Long, name: String): Future[Boolean] =
    db.run(accounts.filter(_.id === userId).map(_.name).update(Some(name)).transactionally) map (r => if (r == 1) true else false)

  def updateAccountSurname(userId: Long, surname: String): Future[Boolean] =
    db.run(accounts.filter(_.id === userId).map(_.surname).update(Some(surname)).transactionally) map (r => if (r == 1) true else false)

  def createSession(
    userId: Long,
    ip: String,
    sessionKey: String,
    created: Long,
    expire: Long): Future[Option[models.Session]] = {
    val query = for {
      dbSession <- (sessions returning sessions.map(_.id) into ((v, id) => v.copy(id = id))) += models.Session(
        0,
        userId,
        ip,
        sessionKey,
        created,
        expire)
    } yield dbSession
    db.run(query.transactionally) map { dbSession => Some(dbSession)}
  }

  def getAccountsPages(pageSize: Long) =
    db.run(accounts.length.result).map { r =>
      pages(r, pageSize.toInt)
    }

  def getAccountBalancesPages(pageSize: Long) =
    db.run(balances.filter(t => t.ownerType === TargetType.ACCOUNT && t.balanceType === BalanceType.CURRENT).length.result).map { r =>
      pages(r, pageSize.toInt)
    }

  def filterCurrentAccountBalanceValue(ownerId: Long, currencyId: Int) =
    filterCurrentAccountBalance(ownerId, currencyId).map(_.value)

  def filterCurrentAccountBalance(ownerId: Long, currencyId: Int) =
    filterAccountBalance(ownerId, currencyId, BalanceType.CURRENT)

  def filterCurrentBatchBalance(ownerId: Long, currencyId: Int) =
    balances.filter(t =>
      t.ownerType === TargetType.BATCH &&
        t.balanceType === BalanceType.CURRENT &&
        t.ownerId === ownerId &&
        t.currencyId === currencyId)

  def filterCurrentCampaingBalance(ownerId: Long, currencyId: Int) =
    balances.filter(t =>
      t.ownerType === TargetType.CAMPAIGN &&
        t.balanceType === BalanceType.CURRENT &&
        t.ownerId === ownerId &&
        t.currencyId === currencyId)

  def findCurrentBatchBalance(ownerId: Long, currencyId: Int) =
    filterCurrentBatchBalance(ownerId, currencyId).result.head

  def findCurrentCampaingBalance(ownerId: Long, currencyId: Int) =
    filterCurrentCampaingBalance(ownerId, currencyId).result.head

  def updateCurrentCampaingBalance(ownerId: Long, currencyId: Int, delta: Long) =
    findCurrentCampaingBalance(ownerId, currencyId).flatMap { balance =>
      balances.filter(_.id === balance.id)
        .map(t => (t.value, t.updated))
        .update(balance.value + delta, System.currentTimeMillis())
    }

  def updateCurrentBatchBalance(ownerId: Long, currencyId: Int, delta: Long) =
    findCurrentBatchBalance(ownerId, currencyId).flatMap { balance =>
      balances.filter(_.id === balance.id)
        .map(t => (t.value, t.updated))
        .update(balance.value + delta, System.currentTimeMillis())
    }

  def filterAccountBalance(ownerId: Long, currencyId: Int, balanceType: Int) =
    balances.filter(t =>
      t.ownerType === TargetType.ACCOUNT &&
        t.balanceType === balanceType &&
        t.ownerId === ownerId &&
        t.currencyId === currencyId)

  def findCurrentAccountBalance(ownerId: Long, currencyId: Int) =
    filterCurrentAccountBalance(ownerId, currencyId).result.head

  def findCurrentAccountBalanceOpt(ownerId: Long, currencyId: Int) =
    filterCurrentAccountBalance(ownerId, currencyId).result.headOption

  def updateCurrentAccountBalance(ownerId: Long, currencyId: Int, delta: Long) =
    findCurrentAccountBalance(ownerId, currencyId).flatMap { balance =>
      balances.filter(_.id === balance.id)
        .map(t => (t.value, t.updated))
        .update(balance.value + delta, System.currentTimeMillis())
    }

  def resetCounters(pageSize: Long, pageId: Long): Future[Int] = {
    val timestamp = System.currentTimeMillis
    db.run((for {
      accountsToUpdate <- accounts.sortBy(_.id.desc).drop(if (pageId > 0) pageSize * (pageId - 1) else 0).take(pageSize).result
      updatedRows <- DBIO.sequence {
        accountsToUpdate.map(account => accounts.filter(_.id === account.id).map(t => (
          t.likesCounter,
          t.commentsCounter,
          t.postsCounter,
          t.likesCounterStarted,
          t.postsCounterStarted,
          t.commentsCounterStarted)).update(
          0,
          0,
          0,
          timestamp,
          timestamp,
          timestamp))
      }
    } yield updatedRows).transactionally).map(_.sum)
  }

  def updateTaskExecutionTime(taskId: Long): Future[Boolean] =
    db.run(scheduledTasks.filter(_.id === taskId).map(_.executed).update(Some(System.currentTimeMillis))) map (_ == 1)

  def getTaskLastExecution(taskId: Long): Future[Long] =
    db.run(scheduledTasks.filter(_.id === taskId).result.headOption) map {
      case Some(scheduledTask) => scheduledTask.executed.getOrElse(0L)
      case _ => 0L
    }

  def transfer(fromAccountId: Long, toAccountId: Long, currency: Int, amount: Long, msgOpt: Option[String]): Future[Option[models.Transaction]] =
    db.run((for {
      tx <- (transactions returning transactions.map(_.id) into ((v, id) => Some(v.copy(id = id)))) += models.Transaction(
        0,
        System.currentTimeMillis,
        None,
        Some(System.currentTimeMillis),
        TargetType.ACCOUNT,
        TargetType.ACCOUNT,
        Some(fromAccountId),
        Some(toAccountId),
        None,
        None,
        None,
        None,
        None,
        None,
        TxType.USER_TO_USER,
        msgOpt,
        TxState.APPROVED,
        currency,
        amount)
      _ <- updateCurrentAccountBalance(fromAccountId, currency, -amount)
      _ <- updateCurrentAccountBalance(toAccountId, currency, amount)
    } yield (tx)).transactionally)

  def promote(fromAccountId: Long, post: models.Post, amount: Long, msgOpt: Option[String]): Future[Option[models.Transaction]] =
    db.run((for {
      _ <- posts.filter(_.id === post.id).map(_.promo).update(post.promo + amount)
      tx <- (transactions returning transactions.map(_.id) into ((v, id) => Some(v.copy(id = id)))) += models.Transaction(
        0,
        System.currentTimeMillis,
        None,
        Some(System.currentTimeMillis),
        TargetType.ACCOUNT,
        TargetType.SYSTEM,
        Some(fromAccountId),
        None,
        None,
        Some(TargetType.POST),
        None,
        Some(post.id),
        None,
        None,
        TxType.PROMOTE_POST,
        msgOpt,
        TxState.APPROVED,
        CurrencyType.TOKEN,
        amount)
      _ <- updateCurrentAccountBalance(fromAccountId, CurrencyType.TOKEN, -amount)
    } yield (tx)).transactionally)

  def addValueToAccountBalance(
    txType: Int,
    userId: Long,
    amount: Long,
    currency: Int,
    msg: Option[String],
    fromRouteTypeOpt: Option[Int],
    fromRouteIdOpt: Option[Long],
    toRouteTypeOpt: Option[Int],
    toRouteIdOpt: Option[Long]): Future[Option[models.Transaction]] =
    db.run(addValueToAccountBalanceDBIOAction(
      txType,
      userId,
      amount,
      msg,
      TargetType.SYSTEM,
      None,
      fromRouteTypeOpt,
      fromRouteIdOpt,
      toRouteTypeOpt,
      toRouteIdOpt).transactionally)

  def findPostByComment(commentId: Long): Future[Option[models.Post]] = {
    val query = for {
      dbComment <- comments.filter(_.id === commentId)
      dbPost <- posts.filter(_.id === dbComment.targetId)
    } yield dbPost
    db.run(query.result.headOption).map(_.map(postFrom))
  }

  def createLikeToPostWithLikesCounterUpdate(
    userId: Long,
    postId: Long,
    commentIdOpt: Option[Long]): Future[Option[models.Like]] = {
    commentIdOpt.fold(createLikeToPostWithLikesCounterUpdate(
      userId,
      postId))(commentId => createLikeToPostWithLikesCounterUpdate(
      userId,
      postId,
      commentId))
  }

  def addRewardOpt(
    txType: Int,
    userId: Long,
    value: Long,
    msg: Option[String],
    fromType: Int,
    fromId: Option[Long],
    routeTargetType: Option[Int],
    routeTargetId: Option[Long]) =
    if (value > 0)
      addValueToAccountBalanceDBIOAction(
        txType,
        userId,
        value,
        msg,
        fromType,
        fromId,
        routeTargetType,
        routeTargetId,
        None,
        None)
    else
      DBIO.successful(None)

  def createLikeToPostWithLikesCounterUpdate(
    userId: Long,
    postId: Long,
    commentId: Long): Future[Option[models.Like]] = {

    val query = for {
      like <- (likes returning likes.map(_.id) into ((v, id) => v.copy(id = id))) += models.Like(
        0,
        userId,
        TargetType.COMMENT,
        commentId,
        System.currentTimeMillis)
      account <- accounts.filter(_.id === userId).result.head
      _ <- accounts.filter(_.id === userId).map(_.likesCounter).update(account.commentsCounter + 1)
      userTokenBalance <- findCurrentAccountBalance(account.id, CurrencyType.TOKEN).map(_.value)
      userActual <- accounts.filter(_.id === userId).result.head
      rewardToken <- comments.filter(_.id === commentId).map(_.reward).result.head
    } yield (like, rewardToken, userActual)

    db.run(query.transactionally) map {
      case (like, rewardToken, userActual) =>
        Some(like.copy(
               rewardOpt = Some(rewardToken),
               ownerOpt = Some(userActual),
               userLoginOpt = Some(userActual.login),
               displayNameOpt = userActual.accountType match {
                 case AccountType.COMPANY => userActual.name
                 case _                   => Some(userActual.login)
               }))
    }
  }

  def createLikeToPostWithLikesCounterUpdate(
    userId: Long,
    postId: Long): Future[Option[models.Like]] = {
    val query = for {
      like <- (likes returning likes.map(_.id) into ((v, id) => v.copy(id = id))) += models.Like(
        0,
        userId,
        TargetType.POST,
        postId,
        System.currentTimeMillis)
      user <- accounts.filter(_.id === userId).result.head
      _ <- accounts.filter(_.id === userId).map(_.likesCounter).update(user.commentsCounter + 1)
      userTokensBalance <- findCurrentAccountBalance(userId, CurrencyType.TOKEN).map(_.value)
      rewardToken <- posts.filter(_.id === postId).map(_.reward).result.head
    } yield (like, rewardToken, user)

    db.run(query.transactionally) map {
      case (like, rewardToken, user) =>
        Some(like.copy(
               rewardOpt = Some(rewardToken),
               ownerOpt = Some(user),
               userLoginOpt = Some(user.login),
               displayNameOpt = user.accountType match {
                 case AccountType.COMPANY => user.name
                 case _                   => Some(user.login)
               }))
    }

  }

  def approveTransaction(txId: Long): Future[Option[models.Transaction]] = {
    val timestamp = System.currentTimeMillis
    val query = for {
      _ <- transactions.filter(t => t.id === txId && t.scheduled.isDefined && t.scheduled <= timestamp && t.toType === TargetType.ACCOUNT)
        .map(t => (t.scheduled, t.state, t.processed))
        .update(None, TxState.APPROVED, Some(timestamp))
      tx <- transactions.filter(_.id === txId).result.head
      user <- accounts.filter(_.id === tx.toId.get).result.head
      _ <- updateCurrentAccountBalance(user.id, tx.currencyId, tx.amount)
    } yield tx
    db.run(query.transactionally).map(t => Some(t))
  }

  def findTransactionById(txId: Long): Future[Option[models.Transaction]] =
    db.run(transactions.filter(_.id === txId).result.headOption)

  def createCommentToPostWithCommentsCounterUpdate(
    postId: Long,
    commentIdOpt: Option[Long],
    userId: Long,
    content: String,
    rewardType: Int): Future[Option[models.Comment]] = {

    val query = for {
      userOpt <- accounts.filter(_.id === userId).result.headOption
      commentOpt <- DBIO.sequenceOption(userOpt.map { user =>
        (comments returning comments.map(_.id) into ((v, id) => v.copy(id = id))) += new models.daos.DBComment(
          0,
          postId,
          userId,
          commentIdOpt,
          content,
          models.ContentType.TEXT,
          System.currentTimeMillis,
          0, 0, models.CommentStatus.VISIBLE)
      })
      _ <- userOpt match {
        case Some(user) =>
          accounts.filter(_.id === userId).map(_.commentsCounter).update(user.commentsCounter + 1)
        case _ => DBIO.successful(None)
      }

      post <- posts.filter(_.id === postId).result.head

      _ <- posts.filter(_.id === postId).map(_.commentsCount).update(post.commentsCount + 1)

      actualCommentOpt <- commentOpt match {
        case Some(comment) =>
          comments.filter(_.id === comment.id).result.headOption
        case _ => DBIO.successful(None)
      }
      actualAccountOpt <- accounts.filter(_.id === userId).result.headOption
    } yield (actualCommentOpt, actualAccountOpt)

    db.run(query.transactionally) map {
      case (Some(actualComment), Some(user)) =>
        val comment = commentFrom(actualComment)
        comment.ownerOpt = Some(user)
        Some(comment)
      case _ => None
    }
  }

  def addValueToAccountBalanceDBIOAction(
    txType: Int,
    userId: Long,
    amount: Long,
    msg: Option[String],
    fromType: Int,
    fromId: Option[Long],
    fromRouteTypeOpt: Option[Int],
    fromRouteIdOpt: Option[Long],
    toRouteTypeOpt: Option[Int],
    toRouteIdOpt: Option[Long]) =
    for {
      tx <- (transactions returning transactions.map(_.id) into ((v, id) => Some(v.copy(id = id)))) += models.Transaction(
        0,
        System.currentTimeMillis,
        None,
        Some(System.currentTimeMillis),
        fromType,
        //TargetType.SYSTEM,
        TargetType.ACCOUNT,
        fromId,
        //None,
        Some(userId),
        fromRouteTypeOpt,
        toRouteTypeOpt,
        fromRouteIdOpt,
        toRouteIdOpt,
        None,
        None,
        txType,
        msg,
        TxState.APPROVED,
        CurrencyType.TOKEN,
        amount)
      _ <- updateCurrentAccountBalance(userId, CurrencyType.TOKEN, amount)
    } yield tx

  def findAccountByConfirmCodeAndLogin(login: String, code: String): Future[Option[models.Account]] =
    getAccountFromQuery(accounts.filter(t => t.login === login && t.confirmCode === code))

  def emailVerified(login: String, code: String, password: String): Future[Option[Account]] =
    db.run(accounts.filter(t => t.login === login && t.confirmCode === code)
      .map(t => (t.confirmCode, t.accountStatus, t.hash))
      .update(None, ConfirmationStatus.CONFIRMED, Some(BCrypt.hashpw(password, BCrypt.gensalt())))).flatMap { raws =>
      if (raws == 1) findAccountByLogin(login) else Future.successful(None)
    }

  def createAccount(
    login: String,
    email: String,
    balanceDp: Long,
    accountType: Int,
    companyNameOpt: Option[String]): Future[Option[models.Account]] = {
    val timestamp = System.currentTimeMillis()
    val query = for {
      dbAccount <- (accounts returning accounts.map(_.id) into ((v, id) => v.copy(id = id))) += models.Account(
        0,
        login,
        email,
        None /*Some(BCrypt.hashpw(password, BCrypt.gensalt()))*/ ,
        None,
        None,
        ConfirmationStatus.WAIT_CONFIRMATION,
        AccountStatus.NORMAL,
        companyNameOpt, None, None, 0, System.currentTimeMillis,
        Some(BCrypt.hashpw(Random.nextString(5) + login + System.currentTimeMillis.toString, BCrypt.gensalt())
          .replaceAll("\\.", "s")
          .replaceAll("\\\\", "d")
          .replaceAll("\\$", "g").toList.map(_.toInt.toHexString).mkString.substring(0, 99)),
        0, 0, 0, 0, 0, 0, 0,
        None,
        accountType)
      dbTx <- (transactions returning transactions.map(_.id) into ((v, id) => v.copy(id = id))) += models.Transaction(
        0,
        System.currentTimeMillis,
        None,
        Some(System.currentTimeMillis),
        TargetType.SYSTEM,
        TargetType.ACCOUNT,
        None,
        Some(dbAccount.id),
        None,
        None,
        None,
        None,
        None,
        None,
        TxType.REGISTER_REWARD,
        Some("Account POWER reward for registration"),
        TxState.APPROVED,
        CurrencyType.TOKEN,
        balanceDp)
      _ <- balances += models.Balance(0, dbAccount.id, TargetType.ACCOUNT, CurrencyType.TOKEN, timestamp, BalanceType.CURRENT, 0)
    } yield (dbAccount, dbTx)
    db.run(query.transactionally) flatMap {
      case (dbAccount, dbTx) =>
        addRolesToAccount(dbAccount.id, Roles.CLIENT) map (t => Some(dbAccount.copy(roles = Seq(models.Roles.CLIENT))))
    }
  }

  def getStats: Future[models.Stats] = {
    val query = for {
      dbAccounts <- accounts.length.result
      dbUsers <- accounts.filter(_.accountType === models.AccountType.USER).length.result
      dbCompanies <- accounts.filter(_.accountType === models.AccountType.COMPANY).length.result
      dbTokens <- balances.filter(t => t.balanceType === BalanceType.CURRENT && t.currencyId === CurrencyType.TOKEN).map(_.value).sum.result
      dbPosts <- posts.filter(t => t.postType === TargetType.ARTICLE || t.postType === TargetType.REVIEW).length.result
      dbProducts <- posts.filter(_.postType === TargetType.PRODUCT).length.result

    } yield (dbAccounts, dbUsers, dbCompanies, dbTokens, dbPosts, dbProducts)
    db.run(query) map {
      case (dbAccounts, dbUsers, dbCompanies, dbTokens, dbPosts, dbProducts) =>
        new models.Stats(dbAccounts, dbUsers, dbCompanies, dbTokens.getOrElse(0), dbPosts, dbProducts)
    }
  }

  def addRolesToAccount(userId: Long, rolesIn: Int*): Future[Unit] =
    db.run(DBIO.seq(roles ++= rolesIn.map(r => models.Role(userId, r))).transactionally)

  ////////////// HELPERS ////////////////

  @inline final def someToSomeFlatMap[T1, T2](f1: Future[Option[T1]], f2: T1 => Future[Option[T2]]): Future[Option[T2]] =
    f1 flatMap (_ match {
      case Some(r) => f2(r)
      case None => Future.successful(None)
    })

  @inline final def someToSomeFlatMapElse[T](f1: Future[Option[_]], f2: Future[Option[T]]): Future[Option[T]] =
    f1 flatMap (_ match {
      case Some(r) => Future.successful(None)
      case None => f2
    })

  @inline final def someToBooleanFlatMap[T](f1: Future[Option[T]], f2: T => Future[Boolean]): Future[Boolean] =
    f1 flatMap (_ match {
      case Some(r) => f2(r)
      case None => Future.successful(false)
    })

  @inline final def someToSeqFlatMap[T1, T2](f1: Future[Option[T1]], f2: T1 => Future[Seq[T2]]): Future[Seq[T2]] =
    f1 flatMap (_ match {
      case Some(r) => f2(r)
      case None => Future.successful(Seq.empty[T2])
    })

  @inline final def seqToSeqFlatMap[T1, T2](f1: Future[Seq[T1]], f2: T1 => Future[T2]): Future[Seq[T2]] =
    f1 flatMap { rs =>
      Future.sequence {
        rs map { r =>
          f2(r)
        }
      }
    }

}


