package org.scalawiki.wlx.stat

import java.nio.file.{Files, Paths}

import org.scalawiki.MwBot
import org.scalawiki.slick.Slick
import org.scalawiki.wlx.dto.{Contest, Monument}
import org.scalawiki.wlx.query.{ImageQuery, ImageQueryApi, MonumentQuery}
import org.scalawiki.wlx.{ImageDB, ListFiller, MonumentDB}

import scala.collection.immutable.SortedSet

class Statistics {

  import scala.concurrent.ExecutionContext.Implicits.global

  val slick = new Slick()

  //  val contest = Contest.WLEUkraine(2015, "09-15", "10-15")
  val previousContests = (2012 to 2013).map(year => Contest.WLMUkraine(year, "09-01", "09-30"))

  def init(contest: Contest): Unit = {

    val monumentDb = getMonumentDb(contest)

    //articleStatistics(monumentDb)
    //    imagesStatistics(monumentQuery, monumentDb)
  }

  def getMonumentDb(contest: Contest): MonumentDB = {
    val monumentQuery = MonumentQuery.create(contest)
    var allMonuments = monumentQuery.byMonumentTemplate(contest.uploadConfigs.head.listTemplate)

    if (contest.country.code == "ru"){
      allMonuments = allMonuments.filter(_.page.contains("Природные памятники России"))
    }

    new MonumentDB(contest, allMonuments)
  }

  def articleStatistics(monumentDb: MonumentDB) = {
    val regionIds = SortedSet(monumentDb._byRegion.keySet.toSeq: _*)
    var allMonuments = 0
    var allArticles = 0
    for (regId <- regionIds) {
      val monuments = monumentDb.byRegion(regId)
      val withArticles = monuments.filter(_.article.isDefined)
      val regionName = monumentDb.contest.country.regionById(regId).name

      allMonuments += monuments.size
      allArticles += withArticles.size
      val percentage = withArticles.size * 100 / monuments.size
      println(s"$regId - $regionName, Monuments: ${monuments.size}, Article - ${withArticles.size}, percentage - $percentage")
    }
    val percentage = allArticles * 100 / allMonuments
    println(s"Ukraine, Monuments: $allMonuments, Article - $allArticles, percentage - $percentage")
  }

  def imagesStatistics(monumentQuery: MonumentQuery, monumentDb: MonumentDB) {
    val imageQueryDb = ImageQuery.create(db = true)
    val imageQueryApi = ImageQuery.create(db = false)

    val contest = monumentDb.contest
    val imageDbFuture = ImageDB.create(contest, imageQueryApi, monumentDb)

    imageDbFuture onFailure {
      case f =>
        println("Failure " + f)
    }

    imageDbFuture onSuccess {
      case imageDb =>

        authorsStat(monumentDb, imageDb)
        byDayAndRegion(imageDb)
        new SpecialNominations().specialNominations(contest, imageDb, monumentQuery)
        wrongIds(contest, imageDb, monumentDb)

        val total = new ImageQueryApi().imagesWithTemplateAsync(contest.uploadConfigs.head.fileTemplate, contest)
        for (totalImages <- total) {

          val totalImageDb = new ImageDB(contest, totalImages, monumentDb)

          regionalStat(contest, monumentDb, imageQueryDb, imageDb, totalImageDb)

          Thread.sleep(5000)
          fillLists(monumentDb, totalImageDb)
        }
    }
  }

  def wrongIds(wlmContest: Contest, imageDb: ImageDB, monumentDb: MonumentDB) {

    val wrongIdImages = imageDb.images.filterNot(image => image.monumentId.fold(false)(monumentDb.ids.contains))

    val text = wrongIdImages.map(_.title).mkString("<gallery>", "\n", "</gallery>")
    MwBot.get(MwBot.commons).page("Commons:Wiki Loves Monuments 2014 in Ukraine/Images with bad ids").edit(text, "updating")
  }

  def byDayAndRegion(imageDb: ImageDB): Unit = {

    val byDay = imageDb.withCorrectIds.groupBy(_.date.getOrElse("2014-00-00").substring(8, 10))

    val firstSlice = (16 to 16).flatMap(day => byDay.getOrElse(day.toString, Seq.empty))

    val byRegion = firstSlice.groupBy(im => Monument.getRegionId(im.monumentId))

    var text = ""
    val dayPage = "Commons:Wiki Loves Monuments 2014 in Ukraine/Day 2"
    for (regionId <- SortedSet(byRegion.keySet.toSeq: _*)) {
      val regionName: String = imageDb.monumentDb.contest.country.regionById(regionId).name
      val pageName = s"$dayPage Region $regionName"
      val gallery = byRegion(regionId).map(_.title).mkString("<gallery>\n", "\n", "</gallery>")

      text += s"* [[$pageName|$regionName]]\n"

      MwBot.get(MwBot.commons).page(pageName).edit(gallery, "updating")
    }
    MwBot.get(MwBot.commons).page(dayPage).edit(text, "updating")
  }

  def authorsStat(monumentDb: MonumentDB, imageDb: ImageDB) {
    val output = new Output()
    val text = output.authorsMonuments(imageDb)
    Files.write(Paths.get("authorsRating.txt"), text.getBytes)
    MwBot.get(MwBot.commons).page("Commons:Wiki Loves Monuments 2014 in Ukraine/Number of objects pictured by uploader").edit(text, "updating")
  }

  def regionalStat(wlmContest: Contest, monumentDb: MonumentDB,
                   imageQueryDb: ImageQuery,
                   currentYear: ImageDB,
                   totalImageDb: ImageDB) {

    val dbsByYear = previousContests.map(contest => ImageDB.create(contest, imageQueryDb, monumentDb))

    dbsByYear.head.map {
      firstYear =>
        dbsByYear.last.map {
          lastYear =>

            val imageDbs = Seq(firstYear, lastYear, currentYear)

            val output = new Output()

            val idsStat = output.monumentsPictured(imageDbs, totalImageDb, monumentDb)
            //            println(idsStat)

            val authorStat = output.authorsContributed(imageDbs, totalImageDb, monumentDb)
            //            println(authorStat)

            val toc = "__TOC__"
            val category = "\n[[Category:Wiki Loves Monuments in Ukraine]]"
            val regionalStat = toc + idsStat + authorStat + category

            //      val bot = MwBot.get(MwBot.commons)
            //      bot.await(bot.page("Commons:Wiki Loves Monuments 2014 in Ukraine/Regional statistics").edit(regionalStat, "update statistics"))

            MwBot.get(MwBot.commons).page("Commons:Wiki Loves Monuments in Ukraine/Regional statistics").edit(regionalStat, "updating")

            val authorsByRegionTotal = output.authorsMonuments(totalImageDb) + "\n[[Category:Wiki Loves Monuments in Ukraine]]"

            MwBot.get(MwBot.commons).page("Commons:Wiki Loves Monuments in Ukraine/3 years total number of objects pictured by uploader").edit(authorsByRegionTotal, "updating")

            val mostPopularMonuments = output.mostPopularMonuments(imageDbs, totalImageDb, monumentDb)
            MwBot.get(MwBot.commons).page("Commons:Wiki Loves Monuments in Ukraine/Most photographed objects").edit(mostPopularMonuments, "updating")

            val monumentQuery = MonumentQuery.create(wlmContest)

            new SpecialNominations().specialNominations(previousContests.find(_.year == 2012).get, firstYear, monumentQuery)
            new SpecialNominations().specialNominations(previousContests.find(_.year == 2013).get, lastYear, monumentQuery)
        }
    }
  }

  def fillLists(monumentDb: MonumentDB, imageDb: ImageDB): Unit = {
    new ListFiller().fillLists(monumentDb, imageDb)
  }

  def saveMonuments(monumentDb: MonumentDB) {
    import scala.slick.driver.H2Driver.simple._

    slick.db.withSession { implicit session =>
      slick.monuments.ddl.drop
      slick.monuments.ddl.create

      slick.monuments ++= monumentDb.allMonuments
    }
  }

  def initImages() {

    slick.db.withSession { implicit session =>
      //      slick.images.ddl.drop
      //      slick.images.ddl.create
    }
  }

  def saveImages(imageDb: ImageDB) {
    import scala.slick.driver.H2Driver.simple._

    slick.db.withSession { implicit session =>
      slick.images ++= imageDb.images
    }
  }


}

object Statistics {
  def main(args: Array[String]) {
    val stat = new Statistics()

    val contests = Contest.allWLE
    val dbs = contests.map(stat.getMonumentDb)

    println(new MonumentDbStat().getStat(dbs))

  }
}