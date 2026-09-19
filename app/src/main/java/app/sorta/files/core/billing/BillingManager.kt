package app.sorta.files.core.billing

import android.app.Activity
import android.content.Context
import app.sorta.files.BuildConfig
import app.sorta.files.data.prefs.UserPrefs
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Thin wrapper around Play Billing for the single "remove_ads" INAPP product. */
class BillingManager(
    context: Context,
    private val prefs: UserPrefs,
    private val scope: CoroutineScope,
) {
    private val productId = BuildConfig.REMOVE_ADS_PRODUCT_ID

    /** null until queryProductDetails resolves; contains localized price when ready. */
    val product = MutableStateFlow<ProductDetails?>(null)
    /** one-off UI messages (toasts). */
    val events = MutableStateFlow<String?>(null)

    private val listener = PurchasesUpdatedListener { result, purchases ->
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases?.forEach { handlePurchase(it) }
            BillingClient.BillingResponseCode.USER_CANCELED -> {}
            else -> events.value = "billing_error:${result.responseCode}"
        }
    }

    private val client = BillingClient.newBuilder(context)
        .setListener(listener).enablePendingPurchases().build()

    fun start() {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode != BillingClient.BillingResponseCode.OK) return
                queryProduct()
                restorePurchases()
            }
            override fun onBillingServiceDisconnected() {}
        })
    }

    private fun queryProduct() {
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            listOf(QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.INAPP)
                .build())
        ).build()
        client.queryProductDetailsAsync(params) { result, list ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                product.value = list.firstOrNull()
            }
        }
    }

    /** Re-check entitlement (called on startup + "Restore purchase"). */
    fun restorePurchases() {
        if (!client.isReady) { start(); return }
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP).build()
        ) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            val owned = purchases.any {
                it.products.contains(productId) &&
                    it.purchaseState == Purchase.PurchaseState.PURCHASED
            }
            purchases.filter { it.products.contains(productId) }
                .forEach { handlePurchase(it) }
            scope.launch { prefs.setAdsRemoved(owned) }
        }
    }

    fun launchPurchase(activity: Activity) {
        val p = product.value
        if (p == null) { events.value = "billing_unavailable"; queryProduct(); return }
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(
            listOf(BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(p).build())
        ).build()
        client.launchBillingFlow(activity, params)
    }

    private fun handlePurchase(p: Purchase) {
        if (p.purchaseState != Purchase.PurchaseState.PURCHASED) return
        if (!p.products.contains(productId)) return
        scope.launch { prefs.setAdsRemoved(true) }
        if (!p.isAcknowledged) {
            client.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(p.purchaseToken).build()
            ) {}
        }
    }

    fun price(): String? = product.value?.oneTimePurchaseOfferDetails?.formattedPrice

    fun consumeEvent() { events.value = null }

    fun destroy() { if (client.isReady) client.endConnection() }
}
