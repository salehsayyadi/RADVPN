package app.radvpn

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object Vault {
 private fun key(): SecretKey {
  val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
  if (ks.containsAlias("radvpn.v2")) return ks.getKey("radvpn.v2", null) as SecretKey
  return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
   init(KeyGenParameterSpec.Builder("radvpn.v2", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()); generateKey()
  }
 }
 fun encrypt(data: JSONObject): ByteArray = Cipher.getInstance("AES/GCM/NoPadding").run { init(Cipher.ENCRYPT_MODE,key()); iv + doFinal(data.toString().toByteArray(Charsets.UTF_8)) }
 fun decrypt(bytes: ByteArray): JSONObject { require(bytes.size in 29..(8*1024*1024)); return Cipher.getInstance("AES/GCM/NoPadding").run { init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12))); JSONObject(String(doFinal(bytes.copyOfRange(12,bytes.size)),Charsets.UTF_8)) } }
 @Synchronized fun load(context: Context): JSONObject {
  val file = File(context.filesDir,"vault.bin")
  if (!file.exists()) return JSONObject().put("lang","en").put("profiles",JSONArray()).put("subscriptions",JSONArray()).put("selected","").put("settings",Api.call(JSONObject().put("method","defaults")))
  val data=decrypt(AtomicFile(file).readFully())
  val profiles=data.getJSONArray("profiles")
  for(i in 0 until profiles.length()){val p=profiles.getJSONObject(i);val old=p.optString("name");val repaired=DisplayNames.repair(old);if(repaired!=old){if(!p.has("nameBeforeEncodingRepair"))p.put("nameBeforeEncodingRepair",old);p.put("name",repaired)}}
  return data
 }
 @Synchronized fun save(context: Context,data: JSONObject) {
  val bytes=encrypt(data);val file=AtomicFile(File(context.filesDir,"vault.bin")); val out=file.startWrite()
  try { out.write(bytes);file.finishWrite(out) } catch(e: Exception) { file.failWrite(out);throw e }
 }
}
object Api {
 fun call(request: JSONObject): JSONObject {
  val wire=JSONObject(request.toString())
  wire.optJSONObject("profile")?.let { p ->
   val clean=JSONObject()
   for (key in listOf("id","name","outbound","enabled","favorite","group")) if(p.has(key))clean.put(key,p.get(key))
   wire.put("profile",clean)
  }
  val result=JSONObject(engine.Engine.invoke(wire.toString()))
  if (!result.optBoolean("ok")) throw IllegalStateException(result.optString("error","operation_failed"))
  return result.optJSONObject("data") ?: JSONObject()
 }
}
class Words(context: Context, language: String) {
 private val values=JSONObject(context.assets.open(if(language=="fa") "fa.json" else "en.json").bufferedReader().use{it.readText()})
 fun t(key: String): String=values.optString(key,values.optString("operation_failed"))
}
