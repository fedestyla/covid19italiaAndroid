package help.covid19italia

import android.content.Context
import android.content.Intent
import androidx.browser.trusted.TrustedWebActivityIntentBuilder
import com.google.androidbrowserhelper.trusted.LauncherActivity
import com.google.androidbrowserhelper.trusted.LauncherActivityMetadata
import com.google.androidbrowserhelper.trusted.TwaLauncher.FallbackStrategy


class CovidLauncherActivity : LauncherActivity() {

    override fun getFallbackStrategy(): FallbackStrategy? {
        return FallbackStrategy { context: Context?, twaIntentBuilder: TrustedWebActivityIntentBuilder, mProviderPackage: String?, completionCallback: Runnable? ->
            val intent: Intent = WebViewFallbackActivity.createLaunchIntent(
                this, twaIntentBuilder.uri, LauncherActivityMetadata.parse(this)
            )
            startActivity(intent)
            completionCallback!!.run()
        }
    }
}