package au.com.shiftyjelly.pocketcasts.profile

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.colorResource
import androidx.constraintlayout.widget.ConstraintLayout
import au.com.shiftyjelly.pocketcasts.account.ProfileCircleView
import au.com.shiftyjelly.pocketcasts.account.onboarding.components.SubscriptionTierPill
import au.com.shiftyjelly.pocketcasts.localization.extensions.getStringPluralDaysMonthsOrYears
import au.com.shiftyjelly.pocketcasts.localization.extensions.getStringPluralSecondsMinutesHoursDaysOrYears
import au.com.shiftyjelly.pocketcasts.models.to.SignInState
import au.com.shiftyjelly.pocketcasts.models.to.SubscriptionStatus
import au.com.shiftyjelly.pocketcasts.models.type.SubscriptionFrequency
import au.com.shiftyjelly.pocketcasts.models.type.SubscriptionPlatform
import au.com.shiftyjelly.pocketcasts.models.type.SubscriptionType
import au.com.shiftyjelly.pocketcasts.ui.extensions.getThemeColor
import au.com.shiftyjelly.pocketcasts.utils.Gravatar
import au.com.shiftyjelly.pocketcasts.utils.TimeConstants
import au.com.shiftyjelly.pocketcasts.utils.days
import au.com.shiftyjelly.pocketcasts.utils.extensions.dpToPx
import au.com.shiftyjelly.pocketcasts.utils.extensions.isLandscape
import au.com.shiftyjelly.pocketcasts.utils.extensions.toLocalizedFormatLongStyle
import java.util.Date
import au.com.shiftyjelly.pocketcasts.images.R as IR
import au.com.shiftyjelly.pocketcasts.localization.R as LR
import au.com.shiftyjelly.pocketcasts.ui.R as UR

open class UserView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {
    val isNewLayout = BuildConfig.ADD_PATRON_ENABLED && !context.isLandscape()
    open val layoutResource = if (isNewLayout) {
        R.layout.view_user_new
    } else {
        R.layout.view_user
    }

    var signedInState: SignInState? = null
        set(value) {
            field = value
            update(value)
        }

    var accountStartDate: Date = Date()
    val maxSubscriptionExpiryMs = 30L * 24L * 60L * 60L * 1000L
    val lblUsername: TextView?
    val lblUserEmail: TextView
    val lblSignInStatus: TextView?
    val imgProfilePicture: ProfileCircleView
    val btnAccount: Button?
    private val subscriptionTierPill: ComposeView?

    init {
        LayoutInflater.from(context).inflate(layoutResource, this, true)
        lblUsername = findViewById(R.id.lblUsername)
        lblUserEmail = findViewById(R.id.lblUserEmail)
        lblSignInStatus = findViewById(R.id.lblSignInStatus)
        imgProfilePicture = findViewById(R.id.imgProfilePicture)
        btnAccount = findViewById(R.id.btnAccount)
        subscriptionTierPill = findViewById(R.id.subscriptionTierPill)
        setBackgroundResource(R.drawable.background_user_view)
    }

    open fun update(signInState: SignInState?) {
        val isPatron = false // TODO: Patron - get Patron state from subscription status
        updateProfileImageAndDaysRemaining(signInState)
        updateSignInStatus(signInState) // Visible only in old layout
        updateUsername(signInState, isPatron)
        updateEmail(signInState, isPatron)
        updateSubscriptionTierPill(signInState, isPatron)
        updateAccountButton(signInState) // Visible only in new layout
    }

    private fun updateProfileImageAndDaysRemaining(signInState: SignInState?) {
        when (signInState) {
            is SignInState.SignedIn -> {
                val gravatarUrl = Gravatar.getUrl(signInState.email)
                setDaysRemainingText(signInState)
                var percent = 1.0f
                val daysLeft = daysLeft(signInState, 30)
                if (daysLeft != null && daysLeft > 0 && daysLeft <= 30) {
                    percent = daysLeft / 30f
                }
                imgProfilePicture.setup(percent, signInState.isSignedInAsPlus, gravatarUrl)
            }
            is SignInState.SignedOut -> imgProfilePicture.setup(0.0f, false)
            else -> imgProfilePicture.setup(0.0f, false)
        }
    }

    private fun daysLeft(signInState: SignInState.SignedIn, maxDays: Int): Int? {
        val timeInXDays = Date(Date().time + maxDays.days())
        val plusStatus = signInState.subscriptionStatus as? SubscriptionStatus.Plus
        if (plusStatus != null && plusStatus.expiry.before(timeInXDays)) {
            // probably shouldn't be do straight millisecond maths because of day light savings
            return ((plusStatus.expiry.time - Date().time) / TimeConstants.MILLISECONDS_IN_ONE_DAY).toInt()
        }
        return null
    }

    private fun setDaysRemainingText(signInState: SignInState.SignedIn) {
        val status = ((signInState as? SignInState.SignedIn)?.subscriptionStatus as? SubscriptionStatus.Plus) ?: return
        if (status.autoRenew) {
            return
        }

        val timeLeftMs = status.expiry.time - Date().time
        if (timeLeftMs <= 0) {
            return
        }

        if (timeLeftMs <= maxSubscriptionExpiryMs) {
            val expiresIn = resources.getStringPluralSecondsMinutesHoursDaysOrYears(timeLeftMs)
            lblSignInStatus?.text = context.getString(LR.string.profile_plus_expires_in, expiresIn).uppercase()
            lblSignInStatus?.setTextColor(lblSignInStatus.context.getThemeColor(UR.attr.support_05))
        }
    }

    private fun updateSignInStatus(signInState: SignInState?) {
        when (signInState) {
            is SignInState.SignedIn -> {
                val strPocketCastsPlus = context.getString(LR.string.pocket_casts_plus).uppercase()
                val strSignedInAs = context.getString(LR.string.profile_signed_in_as).uppercase()

                lblSignInStatus?.text = if (signInState.isSignedInAsPlus) strPocketCastsPlus else strSignedInAs
                lblSignInStatus?.setTextColor(lblSignInStatus.context.getThemeColor(UR.attr.primary_text_02))
            }

            is SignInState.SignedOut ->
                lblSignInStatus?.text = context.getString(LR.string.profile_not_signed_in)

            else -> lblSignInStatus?.text = null
        }
    }

    private fun updateUsername(signInState: SignInState?, isPatron: Boolean) {
        lblUsername?.visibility = View.GONE
        if (signInState is SignInState.SignedIn && isNewLayout && isPatron) {
            lblUsername?.visibility = View.VISIBLE
            // TODO: Patron - update user name logic
            lblUsername?.text = signInState.email
                .split("@").first()
                .replace(".", " ")
                .replace("_", " ")
        }
    }

    private fun updateEmail(signInState: SignInState?, isPatron: Boolean) {
        when (signInState) {
            is SignInState.SignedIn -> {
                lblUserEmail.text = signInState.email
                lblUserEmail.visibility = View.VISIBLE
                if (isNewLayout) {
                    val marginLayoutParams = lblUserEmail.layoutParams as MarginLayoutParams
                    val marginTop = if (isPatron) 4 else 16
                    lblUserEmail.layoutParams = marginLayoutParams.apply {
                        topMargin = marginTop.dpToPx(context)
                    }
                }
            }
            is SignInState.SignedOut -> {
                lblUserEmail.text = context.getString(LR.string.profile_set_up_account)
                if (isNewLayout) lblUserEmail.visibility = View.GONE
            }
            null -> lblUserEmail.text = null
        }
    }

    private fun updateSubscriptionTierPill(signInState: SignInState?, isPatron: Boolean) {
        subscriptionTierPill?.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                if (signInState is SignInState.SignedIn && isNewLayout && isPatron) {
                    SubscriptionTierPill(
                        iconRes = IR.drawable.ic_patron,
                        shortNameRes = LR.string.pocket_casts_patron_short,
                        iconColor = Color.White,
                        backgroundColor = colorResource(id = R.color.patron_purple),
                    )
                }
            }
        }
    }

    private fun updateAccountButton(signInState: SignInState?) {
        btnAccount?.text = when (signInState) {
            is SignInState.SignedIn -> context.getString(LR.string.profile_account)
            is SignInState.SignedOut -> context.getString(LR.string.profile_set_up_account)
            else -> null
        }
    }
}

class ExpandedUserView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : UserView(context, attrs, defStyleAttr) {
    override val layoutResource: Int
        get() = R.layout.view_expanded_user
    val lblAccountType: TextView
        get() = findViewById(R.id.lblAccountType)
    val lblPaymentStatus: TextView
        get() = findViewById(R.id.lblPaymentStatus)

    override fun update(signInState: SignInState?) {
        super.update(signInState)

        val strPocketCasts = context.getString(LR.string.pocket_casts)
        val strPocketCastsPlus = context.getString(LR.string.pocket_casts_plus)
        lblAccountType.text = if (signInState?.isSignedInAsPlus == true) strPocketCastsPlus else strPocketCasts

        val status = (signInState as? SignInState.SignedIn)?.subscriptionStatus ?: return
        when (status) {
            is SubscriptionStatus.Free -> {
                lblPaymentStatus.text = ""
                lblSignInStatus?.text = ""
            }
            is SubscriptionStatus.Plus -> {
                val activeSubscription = status.subscriptions.getOrNull(status.index)
                if (activeSubscription == null || activeSubscription.type == SubscriptionType.PLUS) {
                    setupLabelsForPlusUser(status, signInState)
                } else {
                    setupLabelsForSupporter(activeSubscription)
                }
            }
        }
    }

    private fun setupLabelsForPlusUser(status: SubscriptionStatus.Plus, signInState: SignInState) {
        if (status.autoRenew) {
            val strMonthly = context.getString(LR.string.profile_monthly)
            val strYearly = context.getString(LR.string.profile_yearly)
            lblPaymentStatus.text = context.getString(LR.string.profile_next_payment, status.expiry.toLocalizedFormatLongStyle())
            lblSignInStatus?.text = when (status.frequency) {
                SubscriptionFrequency.MONTHLY -> strMonthly
                SubscriptionFrequency.YEARLY -> strYearly
                else -> null
            }
            lblSignInStatus?.setTextColor(context.getThemeColor(UR.attr.primary_text_02))
        } else {
            if (status.platform == SubscriptionPlatform.GIFT) {
                if (signInState.isLifetimePlus) {
                    lblPaymentStatus.text = context.resources.getString(LR.string.plus_thanks_for_your_support_bang)
                } else {
                    val giftDaysString = context.resources.getStringPluralDaysMonthsOrYears(status.giftDays)
                    lblPaymentStatus.text = context.resources.getString(LR.string.profile_time_free, giftDaysString)
                }
            } else {
                lblPaymentStatus.text = context.getString(LR.string.profile_payment_cancelled)
            }

            if (signInState.isLifetimePlus) {
                lblSignInStatus?.text = context.resources.getString(LR.string.plus_lifetime_member)
                lblSignInStatus?.setTextColor(lblSignInStatus.context.getThemeColor(UR.attr.support_02))
            } else {
                lblSignInStatus?.text = context.getString(LR.string.profile_plus_expires, status.expiry.toLocalizedFormatLongStyle())
                lblSignInStatus?.setTextColor(lblSignInStatus.context.getThemeColor(UR.attr.primary_text_02))
            }
        }
    }

    private fun setupLabelsForSupporter(subscription: SubscriptionStatus.Subscription) {
        if (subscription.autoRenewing) {
            lblPaymentStatus.text = context.getString(LR.string.supporter)
            lblPaymentStatus.setTextColor(lblPaymentStatus.context.getThemeColor(UR.attr.support_02))

            lblSignInStatus?.text = context.getString(LR.string.supporter_check_contributions)
            lblSignInStatus?.setTextColor(context.getThemeColor(UR.attr.primary_text_02))
        } else {
            lblPaymentStatus.text = context.getString(LR.string.supporter_payment_cancelled)
            lblPaymentStatus.setTextColor(lblPaymentStatus.context.getThemeColor(UR.attr.support_05))

            val expiryDate = subscription.expiryDate?.let { it.toLocalizedFormatLongStyle() } ?: context.getString(LR.string.profile_expiry_date_unknown)
            lblSignInStatus?.text = context.getString(LR.string.supporter_subscription_ends, expiryDate)
            lblSignInStatus?.setTextColor(context.getThemeColor(UR.attr.primary_text_02))
        }
    }
}
