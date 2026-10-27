package app.radvpn
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
object DisplayNames {
 private fun score(text:String)=text.count{it in "ÃÂðØÙâ"}
 fun repair(text:String):String {
  var current=text
  repeat(3){if(score(current)==0)return current;var best=current
   for(name in listOf("windows-1252","ISO-8859-1"))try{
    val bytes=Charset.forName(name).newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(current))
    val candidate=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(bytes).toString()
    if(score(candidate)<score(best) && candidate.none{it.isISOControl() && it!='\t'})best=candidate
   }catch(_:Exception){}
   if(best==current)return current;current=best
  };return current
 }
}
