package models.daos

case class DBWallet(
  id: Long,
  owner_id: Option[Long],
  owner_type_id: Int,
  base_currency_id: Int,
  address: String,
  private_key: String)

case class DBCurrency(
  val id: Int,
  val ticker: String,
  val name: String)

case class DBBalance(
  val id: Long,
  val ownerId: Long,
  val ownerType: Int,
  val currencyId: Int,
  val updated: Long,
  val balanceType: Int,
  val value: Long)

case class DBPosition(
  val id: Long,
  val itemId: Long,
  val timestamp: Long,
  val longitude: Double,
  val latitude: Double,
  val accuracy: Double)

case class DBScheduledTask(
  val id: Long,
  val executed: Option[Long],
  val taskType: Int,
  val planned: Option[Long],
  val accountId: Option[Long],
  val productId: Option[Long])

case class DBComment(
  val id: Long,
  val targetId: Long,
  val ownerId: Long,
  val parentId: Option[Long],
  val content: String,
  val contentType: Int,
  val created: Long,
  val likesCount: Int,
  val reward: Long,
  val status: Int)

case class DBSession(
  val id: Long,
  val userId: Long,
  val ip: String,
  val sessionKey: String,
  val created: Long,
  val expire: Long)

case class DBPost(
  val id: Long,
  val ownerId: Long,
  val targetId: Option[Long],
  val title: String,
  val thumbnail: Option[String],
  val content: String,
  val contentType: Int,
  val postType: Int,
  val status: Int,
  val promo: Long,
  val typeStatus: Int,
  val likesCount: Int,
  val commentsCount: Int,
  val postsCount: Int,
  val created: Long,
  val viewsCount: Int,
  val reward: Long,
  val rate: Int,
  val rateCount: Int)

