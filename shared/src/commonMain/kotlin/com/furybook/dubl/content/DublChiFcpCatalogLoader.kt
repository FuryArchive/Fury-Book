package com.furybook.dubl.content

import com.furybook.dubl.acquisition.CatalogLoadResult
import com.furybook.dubl.acquisition.loadAcquisitionCatalog
import com.furybook.content.FcpContentPack
import com.furybook.content.FcpTextSource
import com.furybook.content.FcpUiComponent
import com.furybook.content.FcpUiHostCapabilities
import com.furybook.content.FcpUiSurface
import com.furybook.content.firstUi
import com.furybook.dubl.data.mergeDevelopmentCatalogs
import com.furybook.dubl.data.parseChiCatalog
import com.furybook.dubl.data.parseDevelopmentCatalog
import com.furybook.dubl.model.ChiCatalog
import com.furybook.dubl.model.DevelopmentCatalog
import com.furybook.dubl.model.DublRuleset

object DublChiFcp {
    const val BUNDLED_ROOT = "fcp/dubl-chi-3.69"
    const val PACK_ID = "dubl-chi-3.69"

    fun open(source: FcpTextSource): DublChiFcpCatalogLoader =
        DublChiFcpCatalogLoader(FcpContentPack.load(BUNDLED_ROOT, source))
}

class DublChiFcpCatalogLoader(
    val pack: FcpContentPack,
) {
    init {
        FcpUiHostCapabilities.requireSupported(pack.manifest)
        require(pack.manifest.id == DublChiFcp.PACK_ID) {
            "Unexpected bundled DUBL Chi FCP id: ${pack.manifest.id}"
        }
        require(pack.manifest.ruleset.id == DublRuleset.ID) {
            "FCP ruleset ${pack.manifest.ruleset.id} is not supported by the DUBL engine"
        }
        require(pack.manifest.ruleset.version == DublRuleset.VERSION) {
            "FCP ruleset version ${pack.manifest.ruleset.version} does not match DUBL ${DublRuleset.VERSION}"
        }
        require(pack.manifest.dependencies.any { it.id == DublFcp.PACK_ID && it.version == DublRuleset.VERSION }) {
            "DUBL Chi FCP must depend on ${DublFcp.PACK_ID} ${DublRuleset.VERSION}"
        }
    }

    fun claimedDevelopmentIds(): Set<String> =
        pack.manifest.claims("dubl.development").mapTo(linkedSetOf()) { it.id }

    fun loadAcquisition(corePack: FcpContentPack): CatalogLoadResult =
        loadAcquisitionCatalog(listOf(corePack, pack), includeChi = true)

    fun loadDevelopment(): DevelopmentCatalog =
        mergeDevelopmentCatalogs(
            *pack.readAll("dubl.development")
                .map(::parseDevelopmentCatalog)
                .toTypedArray(),
        )

    fun loadChi(): ChiCatalog =
        parseChiCatalog(pack.readSingle("dubl.chi"))

    fun verifyContent() {
        loadDevelopment()
        loadChi()
        require(pack.manifest.firstUi(FcpUiSurface.CHARACTER_RESOURCES, FcpUiComponent.RESOURCE_METER, DublUiBinding.CHI) != null)
        require(pack.manifest.firstUi(FcpUiSurface.DEVELOPMENT_TABS, FcpUiComponent.DEVELOPMENT_BROWSER, DublUiBinding.CHI) != null)
    }
}
