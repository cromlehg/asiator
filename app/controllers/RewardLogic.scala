package controllers

import models.RewardType

object RewardLogic {

  val POWER_IN_DOLLAR_PRICE = 10

  val TOKEN_IN_POWER = 10

  val REWARD_POOL_DIV = 15

  val POST_REWARD_POOL_K = 0.75

  val COMMENT_REWARD_POOL_K = 0.75

  val LIKE_FOR_ACTOR_REWARD_POOL_K = 0.005

  val LIKE_FOR_TARGET_REWARD_POOL_K = 0.75

  def rewardPool(dp: Double) =
    if (dp == 0) 0 else dp / 15

  def POWERInDOLLAR(power: Double): Long = if (power == 0) 0 else (power / POWER_IN_DOLLAR_PRICE).toLong

  def POWERInTOKEN(power: Double): Long = if (power == 0) 0 else (power / TOKEN_IN_POWER).toLong

  def TOKENInPOWER(token: Double): Long = if (token == 0) 0 else (token * TOKEN_IN_POWER).toLong

  def DOLLARInTOKEN(dollar: Long): Long = dollar

  def TOKENInDOLLAR(token: Long): Long = token

  def postRewardPool(dp: Double) = (rewardPool(dp) * POST_REWARD_POOL_K).toLong

  def commentRewardPool(dp: Double) = (rewardPool(dp) * COMMENT_REWARD_POOL_K).toLong

  def likeRewardPoolForLiker(dp: Double) = (rewardPool(dp) * LIKE_FOR_ACTOR_REWARD_POOL_K).toLong

  def likeRewardPoolForTarget(dp: Double) = (rewardPool(dp) * LIKE_FOR_TARGET_REWARD_POOL_K).toLong

  def postRewardPower(p: Double, rewardType: Int): Long =
    rewardType match {
      case RewardType.POWER                     => postRewardPool(p)
      case RewardType.POWER_50_DOLLARS_50 => (0.5 * postRewardPool(p)).toLong
    }

  def postRewardDollars(p: Long, rewardType: Int): Long =
    rewardType match {
      case RewardType.POWER                     => 0
      case RewardType.POWER_50_DOLLARS_50 => POWERInDOLLAR(0.5 * postRewardPool(p))
    }

  def postRewardByLikePower(dp: Double, rewardType: Int): Long =
    rewardType match {
      case RewardType.POWER                     => likeRewardPoolForTarget(dp)
      case RewardType.POWER_50_DOLLARS_50 => (0.5 * likeRewardPoolForTarget(dp)).toLong
    }

  def postRewardByLikeDollars(p: Long, rewardType: Int): Long =
    rewardType match {
      case RewardType.POWER                     => 0
      case RewardType.POWER_50_DOLLARS_50 => POWERInDOLLAR(0.5 * likeRewardPoolForTarget(p))
    }

  def likerRewardByLikePower(p: Double): Long =
    likeRewardPoolForLiker(p)

  def commentRewardPower(p: Double): Long =
    commentRewardPool(p)

  def rewardDollarEquivalent(rewardDollar: Long, rewardPower: Long): Long =
    (rewardDollar + (rewardPower / RewardLogic.POWER_IN_DOLLAR_PRICE).toLong)

}
