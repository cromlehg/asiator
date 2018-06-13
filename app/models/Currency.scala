package models

case class Currency(
  val id:     Long,
  val ticker: String,
  val name:   String) {

}

object Currency {

 def apply(id: Long, ticker: String, name: String): Currency =
   new Currency(id, ticker, name)

}
