import com.furybook.dubl.model.*
import com.furybook.dubl.data.*
import com.furybook.core.json.*
import java.io.File
fun main(args:Array<String>) {
 val base=File(args[0]);val output=File(args[1]);output.mkdirs()
 fun read(path:String)=File(base,path).readText()
 fun catalog(chi:Boolean):DevelopmentCatalog {
  val core=parseRoot(read("fcp/dubl-3.69/manifest.json")).array("entries").mapNotNull { it.asObject() }.filter { it.string("kind")=="dubl.development" }.sortedBy { it.int("order") }.map { parseDevelopmentCatalog(read("fcp/dubl-3.69/"+it.string("path"))) }
  val cat=mergeDevelopmentCatalogs(*core.toTypedArray())
  return if(chi) mergeDevelopmentCatalogs(cat,parseDevelopmentCatalog(read("fcp/dubl-chi-3.69/content/development_chi_catalog.json"))) else cat.withoutEntries(setOf("martial_technique_08d81ce45b880c24"))
 }
 val cat=catalog(false)
 val self="feat_dd0ba74aedebce10";val eagle="feat_9012c539238a1e6f"
 val fixtures=linkedMapOf<String,DublCharacter>()
 for(done in listOf(false,true)) fixtures["self_"+done]=DublCharacter(id="old",experience=if(done)155 else 170,creationComplete=done,development=mapOf(self to OwnedDevelopment(1)),skills=mapOf("athletics" to CharacterSkill("athletics","athletics",rank=2)))
 fixtures["eagle"]=DublCharacter(id="old",experience=100,creationComplete=true,development=mapOf(eagle to OwnedDevelopment(1)))
 fixtures["negative"]=DublCharacter(id="old",experience=0,xpAdjustment=10,creationComplete=true)
 val ability=cat.entries.first {it.isAbility && it.abilityOptions.size>1}
 fixtures["mixed"]=DublCharacter(id="old",experience=2000,creationExperience=2000,creationComplete=true,abilityPointsOverride=1,attributes=defaultAttributes()+ (AttributeId.STRENGTH to AttributeValue(2)),development=mapOf(ability.id to OwnedDevelopment(2,99),eagle to OwnedDevelopment(3)),developmentOverrides=mapOf(eagle to cat.byId(eagle)!!.copy(cost=7)),skills=mapOf("athletics" to CharacterSkill("athletics","athletics",rank=3)),hiddenSkillIds=setOf("athletics"),chiBonusRanks=2,magic=CharacterMagic(manaRank=1,schools=listOf(MagicSchool("Боевая магия",2),MagicSchool("Custom",5)),spells=listOf(KnownSpell("known",cost=5),KnownSpell("unfinished",cost=10,incomplete=true),KnownSpell("override",cost=90,xpOverride=17),KnownSpell("unpriced",cost=90),KnownSpell("unlearned",cost=10,learned=false))),xpAdjustment=-7)
 fixtures["legacy_mana"]=DublCharacter(id="old",experience=400,development=mapOf("feat_bf7b04eb68961725" to OwnedDevelopment(3)),magic=CharacterMagic(manaRank=1))
 for((name,c)in fixtures){val raw=SnapshotCodec.encode(AppSnapshot(listOf(c),c.id));File(output,"$name.json").writeText(raw);val normalized=SnapshotCodec.decode(raw){error("identity")}.characters.single();val b=CharacterEconomy.breakdown(normalized,normalized.effectiveDevelopmentCatalog(cat),false);val withChi=CharacterEconomy.breakdown(normalized,normalized.effectiveDevelopmentCatalog(catalog(true)),true);println("$name chiXP=${withChi.remainingXp} chiAP=${withChi.abilityPointsRemaining} XP=${b.remainingXp} AP=${b.abilityPointsRemaining} components=$b")}
}
