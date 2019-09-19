/**
 * BreadWallet
 *
 * Created by Pablo Budelli on <pablo.budelli@breadwallet.com> 10/10/19.
 * Copyright (c) 2019 breadwallet LLC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.breadwallet.ui.login

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.text.format.DateUtils
import android.view.View
import android.view.animation.AccelerateInterpolator
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.breadwallet.R
import com.breadwallet.presenter.activities.BrdWalletActivity
import com.breadwallet.presenter.activities.InputPinActivity
import com.breadwallet.presenter.customviews.PinLayout
import com.breadwallet.presenter.interfaces.BRAuthCompletion
import com.breadwallet.tools.animation.SpringAnimator
import com.breadwallet.tools.animation.UiUtils
import com.breadwallet.tools.manager.AppEntryPointHandler
import com.breadwallet.tools.manager.BRSharedPrefs
import com.breadwallet.tools.security.AuthManager
import com.breadwallet.tools.security.BRKeyStore
import com.breadwallet.tools.util.BRConstants
import com.breadwallet.tools.util.EventUtils
import com.breadwallet.ui.BaseController
import com.breadwallet.ui.home.HomeController
import com.breadwallet.ui.util.logError
import com.breadwallet.ui.wallet.WalletActivity
import com.breadwallet.wallet.wallets.ethereum.WalletTokenManager
import kotlinx.android.synthetic.main.activity_pin.*
import kotlinx.android.synthetic.main.pin_digits.*

class LoginController(args: Bundle? = null) : BaseController(args) {

    companion object {
        private const val EXTRA_URL = "com.breadwallet.ui.login.LoginController.PENDING_URL"
    }

    override val layoutId = R.layout.activity_pin

    constructor(intentUrl: String?) : this(
            bundleOf(EXTRA_URL to intentUrl)
    )

    override fun onCreateView(view: View) {
        super.onCreateView(view)
        val pin = BRKeyStore.getPinCode(activity)
        if (pin.isEmpty()) {
            val intent = Intent(activity, InputPinActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivityForResult(intent, InputPinActivity.SET_PIN_REQUEST_CODE)
            return
        }
        check(PinLayout.isPinLengthValid(pin.length)) { "Pin length illegal: " + pin.length }

        brkeyboard.setShowDecimal(false)
        brkeyboard.setDeleteButtonBackgroundColor(activity!!.getColor(android.R.color.transparent))
        brkeyboard.setDeleteImage(R.drawable.ic_delete_dark)

        val pinDigitButtonColors = resources?.getIntArray(R.array.pin_digit_button_colors)
        brkeyboard.setButtonTextColor(pinDigitButtonColors)

        val useFingerprint = AuthManager.isFingerPrintAvailableAndSetup(activity)
                && BRSharedPrefs.getUseFingerprint(activity)
        fingerprint_icon.isVisible = useFingerprint

        fingerprint_icon.setOnClickListener { showFingerprintPrompt() }

        pin_digits.setup(brkeyboard, object : PinLayout.PinLayoutListener {
            override fun onPinLocked() {
                UiUtils.showWalletDisabled(activity)
            }

            override fun onPinInserted(pin: String?, isPinCorrect: Boolean) {
                if (isPinCorrect) {
                    unlockWallet()
                } else {
                    showFailedToUnlock()
                }
            }
        })

        Handler().postDelayed({
            if (fingerprint_icon != null && useFingerprint) {
                showFingerprintPrompt()
            }
        }, DateUtils.SECOND_IN_MILLIS / 2)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == InputPinActivity.SET_PIN_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            val isPinAccepted = data?.getBooleanExtra(InputPinActivity.EXTRA_PIN_ACCEPTED, false) ?: false
            if (isPinAccepted) {
                UiUtils.startBreadActivity(activity, false)
            }
        }
    }

    override fun handleBack() = router.backstackSize > 1 || activity?.isTaskRoot == false

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == BRConstants.CAMERA_REQUEST_ID) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                UiUtils.openScanner(activity)
            } else {
                logError("onRequestPermissionsResult: permission isn't granted for: $requestCode")
            }
        }
    }

    private fun unlockWallet() {
        fingerprint_icon.visibility = View.INVISIBLE
        pinLayout.animate().translationY(-R.dimen.animation_long.toFloat()).setInterpolator(AccelerateInterpolator())
        brkeyboard.animate().translationY(R.dimen.animation_long.toFloat()).setInterpolator(AccelerateInterpolator())
        unlocked_image.animate().alpha(1f).setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                super.onAnimationEnd(animation)
                Handler().postDelayed({
                    val pendingUrl = args.getString(EXTRA_URL).orEmpty()
                    val showHomeScreen = BRSharedPrefs.wasAppBackgroundedFromHome(activity)
                    val currencyCode = BRSharedPrefs.getCurrentWalletCurrencyCode(activity)
                    when {
                        pendingUrl.isNotBlank() -> {
                            router.replaceTopController(RouterTransaction.with(HomeController()))
                            AppEntryPointHandler.processDeepLink(activity, pendingUrl)
                        }
                        showHomeScreen -> {
                            router.replaceTopController(RouterTransaction.with(HomeController())
                                    .popChangeHandler(HorizontalChangeHandler())
                                    .pushChangeHandler(HorizontalChangeHandler()))
                        }
                        currencyCode.equals(WalletTokenManager.BRD_CURRENCY_CODE, ignoreCase = true) -> {
                            BrdWalletActivity.start(activity, currencyCode)
                        }
                        currencyCode.isNotBlank() -> {
                            WalletActivity.start(activity!!, currencyCode)
                        }
                        else -> {
                            router.replaceTopController(RouterTransaction.with(HomeController()))
                            WalletActivity.start(activity!!, currencyCode)
                        }
                    }
                }, DateUtils.SECOND_IN_MILLIS / 2)
            }
        })
        EventUtils.pushEvent(EventUtils.EVENT_LOGIN_SUCCESS)
    }

    private fun showFailedToUnlock() {
        SpringAnimator.failShakeAnimation(activity, pinLayout)
        EventUtils.pushEvent(EventUtils.EVENT_LOGIN_FAILED)
    }

    private fun showFingerprintPrompt() {
        AuthManager.getInstance().authPrompt(activity, "", "", false, true,
                object : BRAuthCompletion {
                    override fun onComplete() {
                        unlockWallet()
                    }

                    override fun onCancel() = Unit
                })
    }

}