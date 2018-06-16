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

}
