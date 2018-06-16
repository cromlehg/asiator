package models

case class Emission(
  val id: Long,
  val currencyId: Int,
  val initialValue: Long,
  val currentValue: Long,
  val percentRate: Long,
  val start: Long,
  val startPercent: Long,
  val endPercent: Long,
  val deltaPercent: Long) {

  def now =
    ((System.currentTimeMillis - start) * deltaPercent.toDouble / controllers.TimeConstants.YEAR.toDouble / percentRate * initialValue - currentValue).toLong

}

object Emission {

  def apply(
    id: Long,
    currencyId: Int,
    initialValue: Long,
    currentValue: Long,
    percentRate: Long,
    start: Long,
    startPercent: Long,
    endPercent: Long,
    deltaPercent: Long): Emission =
    new Emission(
      id,
      currencyId,
      initialValue,
      currentValue,
      percentRate,
      start,
      startPercent,
      endPercent,
      deltaPercent)

}
