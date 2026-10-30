package app.radvpn
import org.json.JSONObject
internal object Api { fun call(value:JSONObject):JSONObject = error("No live API allowed in fixture") }
internal object DisplayNames { fun repair(value:String):String = value }
fun main() { AccountSubscriptionFixtures.verify().forEach{println("PASS: $it")} }
