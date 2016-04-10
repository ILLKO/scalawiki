package org.scalawiki.bots.museum

import com.typesafe.config.ConfigFactory
import net.ceedubs.ficus.FicusConfig
import org.specs2.mutable.Specification

class EntrySpec extends Specification {

  "fromRow" should {
    "get dir" in {
      Entry.fromRow(Seq("dir")) === Entry("dir")
    }

    "get dir and article" in {
      Entry.fromRow(Seq("dir", "article")) === Entry("dir", Some("article"))
    }

    "get dir, article and wlmId" in {
      Entry.fromRow(Seq("dir", "article", "wlmId")) === Entry("dir", Some("article"), Some("wlmId"))
    }

    "get dir, article, wlmId and extra column" in {
      Entry.fromRow(Seq("dir", "article", "wlmId", "something else")) === Entry("dir", Some("article"), Some("wlmId"))
    }

    "get dir, empty article and empty wlmId" in {
      Entry.fromRow(Seq("dir", " ", " ")) === Entry("dir")
    }

    "get dir, article and empty wlmId" in {
      Entry.fromRow(Seq("dir", "article", " ")) === Entry("dir", Some("article"))
    }
  }

  "imagesMaps" should {
    "be empty when no images" in {
      Entry("dir").imagesMaps === Seq.empty
    }

    "default article to dir" in {
      Entry("dir",
        images = Seq(
          EntryImage("image", Some("description"))
        )
      ).withWikiDescriptions.imagesMaps === Seq(
        Map(
          "title" -> "dir 1",
          "file" -> "image",
          "description" -> "{{uk|description, [[:uk:dir|]]}}",
          "source-description" -> "description"
        )
      )
    }

    "use article" in {
      Entry("dir",
        Some("article"),
        images = Seq(
          EntryImage("image", Some("description"))
        )
      ).withWikiDescriptions.imagesMaps === Seq(
        Map(
          "title" -> "article 1",
          "file" -> "image",
          "description" -> "{{uk|description, [[:uk:article|]]}}",
          "source-description" -> "description"
        )
      )
    }

    "use parent wlm id" in {
      Entry("dir",
        Some("article"),
        images = Seq(
          EntryImage("image", Some("description"))
        ),
        wlmId = Some("parent-wlm-id")
      ).withWikiDescriptions.imagesMaps === Seq(
        Map(
          "title" -> "article 1",
          "file" -> "image",
          "description" -> "{{uk|description, [[:uk:article|]]}} {{Monument Ukraine|parent-wlm-id}}",
          "source-description" -> "description"
        )
      )
    }

    "override parent wlm id" in {
      val entry = Entry("dir",
        Some("article"),
        images = Seq(
          EntryImage("image1", Some("description1"), wlmId = None),
          EntryImage("image2", Some("description2"), wlmId = Some("specific-wlm-id"))
        ),
        wlmId = Some("parent-wlm-id")
      ).withWikiDescriptions

      val maps = entry.imagesMaps

      maps.size === 2

      maps.head === Map(
        "title" -> "article 1",
        "file" -> "image1",
        "description" -> "{{uk|description1, [[:uk:article|]]}} {{Monument Ukraine|parent-wlm-id}}",
        "source-description" -> "description1"
      )

      maps.last === Map(
        "title" -> "article 2",
        "file" -> "image2",
        "description" -> "{{uk|description2, [[:uk:article|]]}} {{Monument Ukraine|specific-wlm-id}}",
        "source-description" -> "description2",
        "wlm-id" -> "specific-wlm-id"
      )
    }
  }

  "to/fromConfig" should {

    def roundTrip(entry: Entry, dir: String): Entry =
      Entry.fromConfig(entry.toConfig, dir)

    "map dir to article" in {
      import net.ceedubs.ficus.Ficus._

      val cfg: FicusConfig = Entry("dir").toConfig
      cfg.as[String]("article") === "dir"
      cfg.as[Seq[String]]("images") === Seq.empty
    }

    "map dir" in {
      val entry = Entry("dir", Some("article"))
      roundTrip(entry, "dir") === entry
    }

    "read image" in {
      val entry = Entry("dir", article = Some("article"),
        images = Seq(
          EntryImage("image", Some("description"))
        )).withWikiDescriptions

      roundTrip(entry, "dir") === entry
    }

    "read parent wlmId" in {
      val entry = Entry("dir", article = Some("article"), wlmId = Some("wlm-id"),
        images = Seq(
          EntryImage("image", Some("description"))
        )).withWikiDescriptions

      roundTrip(entry, "dir") === entry
    }

    "read entry and image wlmId" in {
      val entry = Entry("dir", article = Some("article"), wlmId = Some("parent-wlm-id"),
        images = Seq(
          EntryImage("image", Some("description"), wlmId = Some("image-wlm"))
        )).withWikiDescriptions

      roundTrip(entry, "dir") === entry
    }

    "read wiki-description" in {
      val origEntry = Entry("dir", article = Some("article"),
        images = Seq(
          EntryImage("image1", Some("description1"))
        )).withWikiDescriptions

      val str = origEntry.toConfig.root().render().replace("{{uk|description1", "{{uk|description2")
      val cfg = ConfigFactory.parseString(str)
      val entry = Entry.fromConfig(cfg, "dir")
      val image = entry.images.head
      image.wikiDescription === Some("{{uk|description2, [[:uk:article|]]}}")
    }
  }

//  "diff reporter" should {
//    "tell article change in wikidescription" in {
//
//      val entry = Entry("dir", article = Some("article1"), wlmId = Some("wlm-id"),
//        images = Seq(
//          EntryImage("image", Some("description"))
//        ))
//
//      val changedArticle = entry.copy(article = Some("article2"))
//    }
//  }
}
