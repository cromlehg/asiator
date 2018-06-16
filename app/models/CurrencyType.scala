package models

object CurrencyType {

  val TOKEN = 1

  val ETH = 2

  def idByStr(str: String): Option[Int] =
    str match {
      case "astor"  => Some(TOKEN)
      case "eth"    => Some(ETH)
      case _        => None
    }

  def strById(id: Int): Option[String] =
    id match {
      case 1 => Some("astor")
      case 2 => Some("eth")
      case _ => None
    }

}
