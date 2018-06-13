package models.daos

trait TraitDTOToModel {

  def commentFrom(dto: DBComment) =
    new models.Comment(
      dto.id,
      dto.targetId,
      dto.ownerId,
      dto.parentId,
      dto.content,
      dto.contentType,
      dto.created,
      dto.likesCount,
      dto.reward,
      dto.status)

  def postFrom(dto: DBPost) =
    new models.Post(
      dto.id,
      dto.ownerId,
      dto.targetId,
      dto.title,
      dto.thumbnail,
      dto.content,
      dto.contentType,
      dto.postType,
      dto.status,
      dto.promo,
      dto.typeStatus,
      dto.likesCount,
      dto.commentsCount,
      dto.postsCount,
      dto.created,
      dto.viewsCount,
      dto.reward,
      dto.rate,
      dto.rateCount)

}
