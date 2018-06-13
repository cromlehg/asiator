package models

case class ScheduledTask(
  val id:        Long,
  val executed:  Option[Long],
  val taskType:  Int,
  val planned:   Option[Long],
  val accountId: Option[Long],
  val productId: Option[Long],
  val productOpt: Option[Post],
  val emailOpt: Option[String]) {

}

object ScheduledTask {

  def apply(
    id:         Long,
    executed:   Option[Long],
    taskType:   Int,
    planned:    Option[Long],
    accountId:  Option[Long],
    productId:  Option[Long],
    productOpt: Option[Post],
    emailOpt:   Option[String]): ScheduledTask =
      new ScheduledTask(
        id,
        executed,
        taskType,
        planned,
        accountId,
        productId,
        productOpt,
        emailOpt)

  def apply(
    id:         Long,
    executed:   Option[Long],
    taskType:   Int,
    planned:    Option[Long],
    accountId:  Option[Long],
    productId:  Option[Long]): ScheduledTask =
      new ScheduledTask(
        id,
        executed,
        taskType,
        planned,
        accountId,
        productId,
        None,
        None)


}

