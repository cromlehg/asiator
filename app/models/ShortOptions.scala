package models

class ShortOption(
  val id: Long,
  val name: String,
  val descr: String,
  val ttype: String,
  val value: String) {

  def toBoolean = value.toBoolean

}

object ShortOptions {

  val TYPE_BOOLEAN = "Boolean"

  val ARTICLES_CHANGE_ALLOWED = "ARTICLES_CHANGE_ALLOWED"
  
  val ARTICLES_POST_ALLOWED = "ARTICLES_POST_ALLOWED"

}