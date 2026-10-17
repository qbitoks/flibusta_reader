package app.folio.core.data.modules

/** The repository Folio's installable modules live in: github.com/qbitoks/folio-modules. */
object ModulesRepository {
    private const val RAW = "https://raw.githubusercontent.com/qbitoks/folio-modules/main/"
    const val MODULES_JSON = RAW + "modules.json"
    /** Folio's project page, linked from «О приложении». */
    const val APP_PAGE = "https://github.com/qbitoks/folio"
    /** Folio's own releases: `release-metadata.json` of the latest one tells about an update. */
    const val APP_METADATA = "https://github.com/qbitoks/folio/releases/latest/download/release-metadata.json"
    const val APP_RELEASES = "https://github.com/qbitoks/folio/releases/latest/download/"
}
