package models

object BalanceType {

  val CURRENT = 0

  def idByStr(str: String): Option[Int] =
    str match {
      case "current"  => Some(CURRENT)
      case _          => None
    }

  def strById(id: Int): Option[String] =
    id match {
      case 0 => Some("current")
      case _ => None
    }

}