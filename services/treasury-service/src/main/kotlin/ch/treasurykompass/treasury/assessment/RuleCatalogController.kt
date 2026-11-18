package ch.treasurykompass.treasury.assessment

import ch.treasurykompass.treasury.rules.RuleCatalog
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** OpenAPI `RuleInfo`. */
data class RuleInfoView(val id: String, val title: String, val description: String, val regelwerkSection: String, val legalBasis: String?)

@RestController
@RequestMapping("/api/rules")
class RuleCatalogController {

    @GetMapping
    fun rules(): List<RuleInfoView> =
        RuleCatalog.rules.map { RuleInfoView(it.code, it.title, it.description, it.regelwerkSection, it.legalBasis) }
}
