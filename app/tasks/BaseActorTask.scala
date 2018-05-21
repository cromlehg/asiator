package tasks

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

import akka.actor.ActorSystem
import javax.inject.Inject
import models.daos.DAO
import controllers.RewardLogic
import controllers.AppConstants
import play.Logger
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import models.ScheduledTask
import models.TaskType
import scala.concurrent.Future

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.sendgrid.Content
import com.sendgrid.Email
import com.sendgrid.Mail
import com.sendgrid.Method
import com.sendgrid.SendGrid
import com.typesafe.config.Config

import javax.inject.Inject
import javax.inject.Singleton
import models.daos.DAO
import play.Logger
import play.api.mvc.ControllerComponents
import play.api.mvc.Result
import java.util.regex.Pattern
import java.io.IOException

class BaseActorTask @Inject() (actorSystem: ActorSystem, val dao: DAO, config: Config)(implicit executionContext: ExecutionContext) {

  actorSystem.scheduler.schedule(initialDelay = 1.minutes, interval = AppConstants.INTERVAL_NOTIFICATIONS_TASK.toInt.milliseconds) {
    Logger.debug("Start to send notifications")
    val pattern =
      dao.getNotificationsPages() map { count =>
        Logger.debug("Notification pages " + count);
        Future.sequence(for (i <- 1 to count.toInt) yield dao.getNotificationTasksWithEmailAndProduct(i) map { tasks =>          
          tasks foreach { task =>
            val from = new Email(config getString "notifier.from")
            val subject = config getString "notifier.subject"
            val to = new Email(task.emailOpt.get)
            val content = new Content(
              "text/plain",
              (config getString "notifier.letter").replace("%product.id%", task.productId.get.toString))
            val mail = new Mail(from, subject, to, content)
            val sg = new SendGrid(config.getString("sendgrid.apikey"));
            val request = new com.sendgrid.Request()
            try {
              request.setMethod(Method.POST)
              request.setEndpoint("mail/send")
              request.setBody(mail.build())
              val response = sg.api(request)
              if (response.getStatusCode() == 202)
                Logger.debug("Notification successfully sended to " + task.emailOpt.get)    
              else {
                Logger.error("can't send email")
                Logger.error("status code: " + response.getStatusCode())
                Logger.error("body: " + response.getBody())
                Logger.error("headers: " + response.getHeaders())
              }
            } catch {
              case e: IOException =>
                Logger.error(e.toString())
            }
          }
          dao.notificationsSended(tasks.map(_.id))
        })
      } map { r =>
        Logger.debug("Sending notifications successfully finished.")
      }
  }

  actorSystem.scheduler.schedule(initialDelay = 1.minutes, interval = AppConstants.INTERVAL_TO_REWARDER_TASK.toInt.milliseconds) {
    val pageSize = 100

    Logger.debug("Start to update balances")
    dao.getTaskLastExecution(TaskType.REWARDER) map { lastTaskExecution =>
      if (System.currentTimeMillis >= lastTaskExecution + AppConstants.INTERVAL_TO_REWARDER_TASK) {
        Logger.debug("Process reset counters")
        dao.getAccountsPages(pageSize) map { count =>
          Future.sequence(for (i <- 1 to count.toInt) yield dao.resetCounters(pageSize, i))
        } flatMap { r =>
          Logger.debug("Reset counters successfully finished. Start to update balances...")
          dao.getAccountBalancesPages(pageSize) map { countBalances =>
            Future.sequence(for (i <- 1 to countBalances.toInt) yield dao.updateBalances(pageSize, i))
          } map { r =>
            Logger.debug("Update balances successfully finished")
            dao.updateTaskExecutionTime(TaskType.REWARDER)
          } map { r =>
            Logger.debug("Update rewarder task time successfully finished")
          }
        }
      } else {
        Logger.debug("Update balances not started because interval not reached")
      }
    }

  }

  actorSystem.scheduler.schedule(initialDelay = 2.minutes, interval = AppConstants.INTERVAL_TO_SCHEDULED_TXS_PROCESSOR_TASK.toInt.milliseconds) {

    Logger.debug("Start to approve scheduled transactions")
    dao.getTaskLastExecution(TaskType.SCHEDULED_TXS_PROCESSOR) map { lastTaskExecution =>
      if (System.currentTimeMillis >= lastTaskExecution + AppConstants.INTERVAL_TO_SCHEDULED_TXS_PROCESSOR_TASK) {
        Logger.debug("Process approve scheduled transactions")
        dao.approveScheduledTransactions() map { length =>
          Logger.debug("Approve successfully finnished. Approved " + length)
          dao.updateTaskExecutionTime(TaskType.SCHEDULED_TXS_PROCESSOR)
        } map { r =>
          Logger.debug("Update txs processor time successfully finished")
        }
      } else {
        Logger.debug("Txs processor task not started becasue interval not reached")
      }
    }

  }

}