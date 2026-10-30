package dev.pridgey.beddery.system

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.consumePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Optional tips through Google Play. Tips unlock nothing: each is a consumable product that's used up
 * straight away, so people can tip again. The products (see [PRODUCTS]) are set up in Play Console.
 */
object TipJar {

    /** Smallest to largest. Play Console product IDs, as one-time consumable products. */
    val PRODUCTS = listOf("tip_small", "tip_medium", "tip_large")

    data class Tip(val id: String, val price: String, val details: ProductDetails)

    sealed interface State {
        data object Loading : State
        /** Play isn't reachable, or the products aren't set up (for example, a build not installed from Play). */
        data object Unavailable : State
        data class Ready(val tips: List<Tip>) : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    /** Goes up by one each time a tip goes through, so the screen can say thanks. */
    private val _thanks = MutableStateFlow(0)
    val thanks: StateFlow<Int> = _thanks.asStateFlow()

    private var client: BillingClient? = null
    private const val PREFS = "tips"

    fun hasTipped(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("tipped", false)

    /** Connects to Play and loads prices. Safe to call more than once. */
    fun load(context: Context) {
        val app = context.applicationContext
        val c = client ?: BillingClient.newBuilder(app)
            .setListener { result, purchases -> if (result.responseCode == BillingClient.BillingResponseCode.OK) handle(app, purchases.orEmpty()) }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()
            .also { client = it }
        scope.launch {
            if (!c.isReady && !connect(c)) { _state.value = State.Unavailable; return@launch }
            // A tip that finished while the app was closed (or was pending) still needs to be used up.
            val owned = c.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build())
            handle(app, owned.purchasesList)
            val params = QueryProductDetailsParams.newBuilder().setProductList(
                PRODUCTS.map {
                    QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(BillingClient.ProductType.INAPP).build()
                },
            ).build()
            val result = c.queryProductDetails(params)
            val tips = result.productDetailsList.orEmpty()
                .mapNotNull { d -> d.oneTimePurchaseOfferDetails?.let { Tip(d.productId, it.formattedPrice, d) } }
                .sortedBy { PRODUCTS.indexOf(it.id) }
            _state.value = if (tips.isEmpty()) State.Unavailable else State.Ready(tips)
        }
    }

    fun buy(activity: Activity, tip: Tip) {
        val c = client ?: return
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(tip.details).build()))
            .build()
        c.launchBillingFlow(activity, params)
    }

    private fun handle(context: Context, purchases: List<Purchase>) {
        val c = client ?: return
        purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }.forEach { p ->
            scope.launch {
                val done = c.consumePurchase(ConsumeParams.newBuilder().setPurchaseToken(p.purchaseToken).build())
                if (done.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("tipped", true).apply()
                    _thanks.update { it + 1 }
                }
            }
        }
    }

    private suspend fun connect(c: BillingClient): Boolean = suspendCancellableCoroutine { cont ->
        c.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (cont.isActive) cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
            }
            override fun onBillingServiceDisconnected() {
                if (cont.isActive) cont.resume(false)
            }
        })
    }
}
