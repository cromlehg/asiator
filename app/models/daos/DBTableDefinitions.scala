package models.daos

import slick.jdbc.JdbcProfile
import slick.lifted.ProvenShape.proveShapeOf

trait DBTableDefinitions {

  protected val driver: JdbcProfile
  import driver.api._

  class Roles(tag: Tag) extends Table[models.Role](tag, "roles") {
    def userId = column[Long]("user_id")
    def role = column[Int]("role")
    def * = (userId, role) <> [models.Role](t => models.Role(t._1, t._2), models.Role.unapply)
  }

  val roles = TableQuery[Roles]
  
  
  class ScheduledTasks(tag: Tag) extends Table[models.ScheduledTask](tag, "scheduled_tasks") {
    def id        = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def executed  = column[Option[Long]]("executed")
    def taskType  = column[Int]("task_type")
    def planned   = column[Option[Long]]("planned")
    def accountId = column[Option[Long]]("account_id")
    def productId = column[Option[Long]]("product_id")
    def * = (
        id, 
        executed,
        taskType,
        planned,
        accountId,
        productId) <> [models.ScheduledTask]( t => 
          models.ScheduledTask(t._1, t._2, t._3, t._4, t._5, t._6), t =>
          Some(
            t.id,
            t.executed,
            t.taskType,
            t.planned,
            t.accountId,
            t.productId))
  }

  val scheduledTasks = TableQuery[ScheduledTasks]

  class Comments(tag: Tag) extends Table[DBComment](tag, "comments") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def targetId = column[Long]("target_id")
    def ownerId = column[Long]("owner_id")
    def parentId = column[Option[Long]]("parent_id")
    def content = column[String]("content")
    def contentType = column[Int]("content_type")
    def created = column[Long]("created")
    def likesCount = column[Int]("likes_count")
    def reward = column[Long]("reward")
    def status = column[Int]("status")
    def * = (
        id, 
        targetId, 
        ownerId, 
        parentId, 
        content, 
        contentType, 
        created, 
        likesCount,
        reward,
        status) <> (DBComment.tupled, DBComment.unapply)
  }

  val comments = TableQuery[Comments]
  
  class Currencies(tag: Tag) extends Table[models.Currency](tag, "currencies") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def ticker = column[String]("ticker")
    def name = column[String]("name")
    def * = (id, ticker, name) <> [models.Currency](t => models.Currency(t._1, t._2, t._3), models.Currency.unapply)
  }

  val currencies = TableQuery[Currencies]
  
  class Balances(tag: Tag) extends Table[models.Balance](tag, "balances") {
    def id           = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def ownerId      = column[Long]("owner_id")
    def ownerType    = column[Int]("owner_type")
    def currencyId   = column[Int]("currency_id")
    def updated      = column[Long]("updated")
    def balanceType  = column[Int]("balance_type")
    def value        = column[Long]("value")
    def * = (
        id, 
        ownerId, 
        ownerType, 
        currencyId, 
        updated, 
        balanceType, 
        value) <> [models.Balance]( t =>
          models.Balance(t._1, t._2, t._3, t._4, t._5, t._6, t._7), t =>
          Some((
            t.id, 
            t.ownerId, 
            t.ownerType, 
            t.currencyId, 
            t.updated, 
            t.balanceType, 
            t.value)))
  }

  val balances = TableQuery[Balances]

  class Tags(tag: Tag) extends Table[models.Tag](tag, "tags") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name")
    def * = (id, name) <> (t => models.Tag(t._1, t._2), models.Tag.unapply)
  }
  
  val tags = TableQuery[Tags]
  
  class TagsToTargets(tag: Tag) extends Table[models.TagToTarget](tag, "tags_to_targets") {
    def tagId = column[Long]("tag_id")
    def targetId = column[Long]("target_id")
    def targetType = column[Int]("target_type")
    def created = column[Long]("created")
    def * = (tagId, targetId, targetType, created) <> [models.TagToTarget](t => models.TagToTarget(t._1, t._2, t._3, t._4), models.TagToTarget.unapply)
  }
 
  val tagsToTargets = TableQuery[TagsToTargets]
  
  class Sessions(tag: Tag) extends Table[models.Session](tag, "sessions") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def userId = column[Long]("user_id")
    def ip = column[String]("ip")
    def sessionKey = column[String]("session_key")
    def created = column[Long]("created")
    def expire = column[Long]("expire")
    def * = (id, userId, ip, sessionKey, created, expire) <> (t =>
      models.Session(t._1, t._2, t._3, t._4, t._5, t._6), models.Session.unapply)
  }

  val sessions = TableQuery[Sessions]

  class Accounts(tag: Tag) extends Table[models.Account](tag, "accounts") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def login = column[String]("login")
    def email = column[String]("email")
    def hash = column[Option[String]]("hash")
    def avatar = column[Option[String]]("avatar")
    def background = column[Option[String]]("background")
    def confirmationStatus = column[Int]("confirmation_status")
    def accountStatus = column[Int]("account_status")
    def name = column[Option[String]]("name")
    def surname = column[Option[String]]("surname")
    def platformEth = column[Option[String]]("platform_eth")
    def timezoneId = column[Int]("timezone_id")
    def registered = column[Long]("registered")
    def confirmCode = column[Option[String]]("confirm_code")
    def postsCounter = column[Int]("posts_counter")
    def postsCounterStarted = column[Long]("posts_counter_started")
    def likesCounter = column[Int]("likes_counter")
    def likesCounterStarted = column[Long]("likes_counter_started")
    def commentsCounter = column[Int]("comments_counter")
    def commentsCounterStarted = column[Long]("comments_counter_started")
    def postsCount = column[Long]("posts_count")
    def about = column[Option[String]]("about")
    def accountType = column[Int]("account_type")
    def * = ((
      id,
      login,
      email,
      hash,
      avatar,
      background,
      confirmationStatus,
      accountStatus,
      name,
      surname,
      platformEth,
      timezoneId,
      registered,
      confirmCode,
      postsCounter,
      postsCounterStarted,
      likesCounter,
      likesCounterStarted,
      commentsCounter,
      commentsCounterStarted),( 
        postsCount,
        about,
        accountType)) <> [models.Account](t =>
          models.Account(
            t._1._1,
            t._1._2,
            t._1._3,
            t._1._4,
            t._1._5,
            t._1._6,
            t._1._7,
            t._1._8,
            t._1._9,
            t._1._10,
            t._1._11,
            t._1._12,
            t._1._13,
            t._1._14,
            t._1._15,
            t._1._16,
            t._1._17,
            t._1._18,
            t._1._19,
            t._1._20,
            t._2._1,
            t._2._2,
            t._2._3), t => Some(
     (t.id,
      t.login,
      t.email,
      t.hash,
      t.avatar,
      t.background,
      t.confirmationStatus,
      t.accountStatus,
      t.name,
      t.surname,
      t.platformEth,
      t.timezoneId,
      t.registered,
      t.confirmCode,
      t.postsCounter,
      t.postsCounterStarted,
      t.likesCounter,
      t.likesCounterStarted,
      t.commentsCounter,
      t.commentsCounterStarted),( 
        t.postsCount,
        t.about,
        t.accountType))   
      )

  }

  val accounts = TableQuery[Accounts]

  class Posts(tag: Tag) extends Table[models.Post](tag, "posts") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def ownerId = column[Long]("owner_id")
    def targetId = column[Option[Long]]("target_id")
    def title = column[String]("title")
    def thumbnail = column[Option[String]]("thumbnail")
    def content = column[String]("content")
    def contentType = column[Int]("content_type")
    def postType = column[Int]("post_type")
    def status = column[Int]("status")
    def promo = column[Long]("promo")
    def typeStatus = column[Int]("type_status")
    def likesCount = column[Int]("likes_count")
    def commentsCount = column[Int]("comments_count")
    def postsCount = column[Int]("posts_count")
    def created = column[Long]("created")
    def viewsCount = column[Int]("views_count")
    def reward = column[Long]("reward")
    def rate = column[Int]("rate")
    def rateCount = column[Int]("rate_count")
    def moderateStatus = column[Int]("moderate_status")

    def * = (
      id,
      ownerId,
      targetId,
      title,
      thumbnail,
      content,
      contentType,
      postType,
      status,
      promo,
      typeStatus,
      likesCount,
      commentsCount,
      postsCount,
      created,
      viewsCount,
      reward,
      rate,
      rateCount,
      moderateStatus) <> [models.Post]( t =>
          models.Post(
            t._1,
            t._2,
            t._3,
            t._4,
            t._5,
            t._6,
            t._7,
            t._8,
            t._9,
            t._10,
            t._11,
            t._12,
            t._13,
            t._14,
            t._15,
            t._16,
            t._17,
            t._18,
            t._19,
            t._20), t =>
          Some((
      t.id,
      t.ownerId,
      t.targetId,
      t.title,
      t.thumbnail,
      t.content,
      t.contentType,
      t.postType,
      t.status,
      t.promo,
      t.typeStatus,
      t.likesCount,
      t.commentsCount,
      t.postsCount,
      t.created,
      t.viewsCount,
      t.reward,
      t.rate,
      t.rateCount,
      t.moderateStatus)))

  }

  val posts = TableQuery[Posts]
  
  class Likes(tag: Tag) extends Table[models.Like](tag, "likes") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def ownerId = column[Long]("owner_id")
    def targetType = column[Int]("target_type")
    def targetId = column[Long]("target_id")
    def created = column[Long]("created")

    def * = (
      id,
      ownerId,
      targetType,
      targetId,
      created) <> [models.Like](t =>
        models.Like(
            t._1,
            t._2,
            t._3,
            t._4,
            t._5),t => Some(
            t.id,
      t.ownerId,
      t.targetType,
      t.targetId,
      t.created
            ))

  }

  val likes = TableQuery[Likes]

  class Transactions(tag: Tag) extends Table[models.Transaction](tag, "txs") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def created = column[Long]("created")
    def scheduled = column[Option[Long]]("scheduled")
    def processed = column[Option[Long]]("processed")
    def fromType = column[Int]("from_type")
    def toType = column[Int]("to_type")
    def fromId = column[Option[Long]]("from_id")
    def toId = column[Option[Long]]("to_id")
    def fromRouteType = column[Option[Int]]("from_route_type")
    def toRouteType = column[Option[Int]]("to_route_type")
    def fromRouteId = column[Option[Long]]("from_route_id")
    def toRouteId = column[Option[Long]]("to_route_id")
    def from = column[Option[String]]("from")
    def to = column[Option[String]]("to")
    def txType = column[Int]("type")
    def msg = column[Option[String]]("msg")
    def state = column[Int]("state")
    def currency = column[Int]("currency")
    def amount = column[Long]("amount")
    
    
    def * = ((
      id,
      created,
      scheduled,
      processed,
      fromType,
      toType,
      fromId,
      toId,
      fromRouteType,
      toRouteType,
      fromRouteId,
      toRouteId),( 
        from,
        to,
        txType,
        msg,
        state,
        currency,
        amount)) <> [models.Transaction](t =>
          models.Transaction(
            t._1._1,
            t._1._2,
            t._1._3,
            t._1._4,
            t._1._5,
            t._1._6,
            t._1._7,
            t._1._8,
            t._1._9,
            t._1._10,
            t._1._11,
            t._1._12,
            t._2._1,
            t._2._2,
            t._2._3,
            t._2._4,
            t._2._5,
            t._2._6,
            t._2._7), t => Some(
     (t.id,
      t.created,
      t.scheduled,
      t.processed,
      t.fromType,
      t.toType,
      t.fromId,
      t.toId,
      t.fromRouteType,
      t.toRouteType,
      t.fromRouteId,
      t.toRouteId),( 
        t.from,
        t.to,
        t.txType,
        t.msg,
        t.state,
        t.currencyId,
        t.amount))   
      )
    
  }

  val transactions = TableQuery[Transactions]
  
  class ShortOptions(tag: Tag) extends Table[models.ShortOption](tag, "short_options") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name")
    def value = column[String]("value")
    def ttype = column[String]("type")
    def descr = column[String]("descr")
    def * = (id, name, value, ttype, descr) <> [models.ShortOption](t => 
      models.ShortOption(t._1, t._2, t._3, t._4, t._5), models.ShortOption.unapply)
  }

  val shortOptions = TableQuery[ShortOptions]
  
}

