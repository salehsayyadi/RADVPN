package app.radvpn

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.widget.LinearLayout
import org.json.JSONObject

/** Native account controls. Session identity always comes from the authenticated API. */
internal object AccountScreen {
 fun render(activity:Activity,parent:LinearLayout,data:JSONObject,ready:Boolean,text:(String)->String,
            label:(String)->Unit,button:(String,Boolean,()->Unit)->Unit,
            request:(()->JSONObject,(JSONObject)->Unit)->Unit,restore:()->Unit,
            loggedIn:(JSONObject)->Unit,importSubscription:(JSONObject,Boolean)->Unit,
            save:()->Unit,clear:()->Unit,render:()->Unit,report:(String)->Unit,
            dialog:(AlertDialog.Builder)->AlertDialog) {
  val state=data.optJSONObject("radvpn")?:JSONObject()
  if(!ready) {
   AccountLoginForm.render(activity,parent,text,label,button,state.optString("token").isNotBlank(),restore,
    {code->request({AccountApi.login(code)},loggedIn)},{report("account_login_help")})
   return
  }
  val account=state.optJSONObject("account")?:JSONObject()
  AccountDetails.lines(account,text).forEach(label)
  fun refresh(){request({AccountApi.validate(data)}){result->state.put("account",result);save();render()}}
  button("update",false,::refresh)
  button("account_import",true){request({AccountApi.validate(data)}){result->
   state.put("account",result);save()
   val sub=AccountDetails.subscription(data,result)
   if(sub==null)report("account_subscription_pending")else importSubscription(sub.first,sub.second)
  }}
  button("account_devices",false){request({AccountApi.call(AccountApi.PRODUCTION_ENDPOINT,"v1/account/sessions",state.getString("token"))}){result->
   val rows=result.getJSONArray("sessions");val current=result.getString("current_session_id")
   if(rows.length()==0)report("account_no_devices") else {
    val labels=Array(rows.length()){i->val row=rows.getJSONObject(i)
     row.optString("device_name")+" · "+row.optString("platform")+if(row.getString("id")==current)" · "+text("account_this_device") else ""
    }
    dialog(AlertDialog.Builder(activity).setTitle(text("account_devices")).setItems(labels){_,index->
     val sid=rows.getJSONObject(index).getString("id")
     dialog(AlertDialog.Builder(activity).setTitle(text("account_revoke_device")).setMessage(text("account_revoke_confirm"))
      .setNegativeButton(text("close"),null).setPositiveButton(text("account_revoke_device")){_,_->
       request({AccountApi.call(AccountApi.PRODUCTION_ENDPOINT,"v1/account/sessions/"+sid+"/revoke",state.getString("token"),JSONObject())}){ack->
        if(ack.optBoolean("current_session"))clear()else refresh()
       }
      })
    }.setNegativeButton(text("close"),null))
   }
  }}
  button("account_purchase",false){AccountDetails.purchaseLink(account)?.let{activity.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(it)))}}
  button("account_logout",false){request({AccountDetails.logout(state)}){clear()}}
 }
}
