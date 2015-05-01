package org.scalawiki.stat

import org.scalawiki.dto.filter.RevisionFilter
import org.scalawiki.dto.history.Annotation
import org.scalawiki.dto.{Page, Revision}
import org.xwiki.blame.AnnotatedElement

class RevisionStat(val page: Page, revFilter: RevisionFilter) {

  def revisions = revFilter(page.history.revisions)

  val users = page.history.users(revFilter.from, revFilter.to)
  val delta = page.history.delta(revFilter.from, revFilter.to).getOrElse(0L)

  val annotation: Option[Annotation] = Annotation.create(page)

  def pageAnnotatedElements: Seq[AnnotatedElement[Revision, String]] =
    annotation.fold(Seq.empty[AnnotatedElement[Revision, String]])(_.annotatedElements)

  val annotatedElements = pageAnnotatedElements
    .filter(element => revFilter.predicate(element.getRevision))

  val byRevisionContent: Map[Revision, Seq[String]] = annotatedElements.groupBy(_.getRevision).mapValues(_.map(_.getElement))
  val byUserContent: Map[String, Seq[String]] = annotatedElements.groupBy(_.getRevision.user.flatMap(_.name) getOrElse "").mapValues(_.map(_.getElement))

  val byRevisionSize = byRevisionContent.mapValues(_.map(_.getBytes.size.toLong).sum)
  val addedOrRewritten = byRevisionSize.values.sum

  private val _byUserSize: Map[String, Long] = byUserContent.mapValues(_.map(_.getBytes.size.toLong).sum)

  def byUserSize(user: String): Long = _byUserSize.getOrElse(user, 0L)

  val usersSorted = _byUserSize.toSeq.sortBy{
    case (user, size) => -size
  }

  val usersSortedString = usersSorted.map {
    case (user, size) => s"[[User:$user|$user]]: $size"
  }.mkString("<br>")

  def stat = s"| [[${page.title}]] || $addedOrRewritten || $delta || ${_byUserSize.keySet.size} || $usersSortedString"

}

object RevisionStat {

  def statHeader = s"! title !! added or rewritten !! size increase !! users !! users by added or rewritten text size\n"

}