package org.scalawiki.wlx.dto

import java.util.Locale

import scala.collection.immutable.SortedSet

trait AdmDivision {
  def code: String

  def name: String

  override def toString = s"$name ($code)"

  def regions: Seq[AdmDivision]

  def languageCodes: Seq[String] = Nil

  def withoutLangCodes: AdmDivision = this

  def parent: () => Option[AdmDivision]

  def regionId(monumentId: String): String = monumentId.split("-").take(2).mkString

  def regionName(regId: String): String = if (regId == code) name else ""

  def byId(monumentId: String): Option[AdmDivision] = if (regionId(monumentId) == code) Some(this) else None

  def byName(name: String): Seq[AdmDivision] = {
    Seq(this).filter(_.name == name) ++ regions.flatMap(_.byName(name))
  }

  def byRegion(monumentIds: Set[String]): Map[AdmDivision, Set[String]] = {
    val entries = monumentIds.flatMap(id => byId(id).map(adm => id -> adm))

    entries
      .groupBy { case (id, adm) => adm }
      .mapValues(_.toMap.keySet)
  }

  def byIdAndName(regionId: String, name: String): Seq[AdmDivision] = {
    byId(regionId).map { region =>
      val here = region.byName(name)
      if (here.isEmpty) {
        region.parent().map(_.byName(name)).getOrElse(Nil)
      } else {
        here
      }
    }.getOrElse(Nil)
  }

  def withParents(parent: () => Option[AdmDivision] = () => None): AdmDivision

  def regionsWithParents(): Seq[AdmDivision] = {
    regions.map(_.withParents(() => Some(this)))
  }
}

trait AdmRegion extends AdmDivision {
  lazy val regionIds: SortedSet[String] = SortedSet(regions.map(_.code): _*)

  lazy val regionNames: Seq[String] = regions.sortBy(_.code).map(_.name)

  lazy val regionById: Map[String, AdmDivision] = regions.groupBy(_.code).mapValues(_.head)

  override def regionName(regId: String) = regionById.get(regId).map(_.name).getOrElse("")

  override def byId(monumentId: String): Option[AdmDivision] = {
    regionById.get(regionId(monumentId)).flatMap(region => region.byId(monumentId).orElse(Some(region)))
  }
}


case class NoAdmDivision(code: String = "", name: String = "") extends AdmRegion {
  override val regions: Seq[AdmDivision] = Nil

  override def withParents(parent: () => Option[AdmDivision]): AdmDivision = this

  override val parent: () => Option[AdmDivision] = () => None
}

case class Country(code: String,
                   name: String,
                   override val languageCodes: Seq[String] = Nil,
                   var regions: Seq[AdmDivision] = Nil
                  ) extends AdmRegion {

  val parent: () =>  Option[AdmDivision] = () => None

  override def withoutLangCodes = copy(languageCodes = Nil)

  override def regionId(monumentId: String): String = monumentId.split("-").head

  override def withParents(parent: () => Option[AdmDivision] = () => None): AdmDivision = {
    regions = regionsWithParents()
    this
  }
}

case class Region(code: String, name: String,
                  var regions: Seq[AdmDivision] = Nil,
                  var parent: () => Option[AdmDivision] = () => None)
  extends AdmRegion {

  override def withParents(parent: () => Option[AdmDivision] = () => None): AdmDivision = {
    this.parent = parent
    this.regions = regionsWithParents()
    this
  }
}

case class NoRegions(code: String, name: String,
                     var parent: () => Option[AdmDivision] = () => None)
  extends AdmDivision {

  val regions: Seq[AdmDivision] = Nil

  override def withParents(parent: () => Option[AdmDivision] = () => None): AdmDivision = {
    this.parent = parent
    this
  }

}

object AdmDivision {
  def apply(code: String, name: String,
            regions: Seq[AdmDivision],
            parent: () => Option[AdmDivision]): AdmDivision = {
    if (regions.isEmpty) {
      NoRegions(code, name, parent)
    } else {
      Region(code, name, regions, parent)
    }
  }
}

object Country {

  val Azerbaijan = new Country("AZ", "Azerbaijan", Seq("az"))

  val Ukraine: Country = new Country("UA", "Ukraine", Seq("uk"), Koatuu.regions(() => Some(Ukraine)))

  val customCountries = Seq(Ukraine)

  def langMap: Map[String, Seq[String]] = {
    Locale.getAvailableLocales
      .groupBy(_.getCountry)
      .map {
        case (countryCode, locales) =>

          val langs = locales.flatMap {
            locale =>
              Option(locale.getLanguage)
                .filter(_.nonEmpty)
          }
          countryCode -> langs.toSeq
      }
  }

  def fromJavaLocales: Seq[Country] = {

    val countryLangs = langMap

    val fromJava = Locale.getISOCountries.map { countryCode =>

      val langCodes = countryLangs.getOrElse(countryCode, Seq.empty)

      val locales = langCodes.map(langCode => new Locale(langCode, countryCode))

      val locale = locales.headOption.getOrElse(new Locale("", countryCode))

      val langs = locales.map(_.getDisplayLanguage(Locale.ENGLISH)).distinct

      new Country(locale.getCountry,
        locale.getDisplayCountry(Locale.ENGLISH),
        langCodes
      )
    }.filterNot(_.code == "ua")

    Seq(Ukraine) ++ fromJava
  }

  lazy val countryMap: Map[String, Country] =
    (fromJavaLocales ++ customCountries).groupBy(_.code.toLowerCase).mapValues(_.head)

  def byCode(code: String): Option[Country] = countryMap.get(code.toLowerCase)
}
