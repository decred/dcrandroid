/*
 * Copyright (c) 2018-2019 The Decred developers
 * Use of this source code is governed by an ISC
 * license that can be found in the LICENSE file.
 */

package com.dcrandroid.fragments

import android.app.Activity.RESULT_OK
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.dcrandroid.BuildConfig
import com.dcrandroid.MainActivity
import com.dcrandroid.R
import com.dcrandroid.activities.TransactionDetailsActivity
import com.dcrandroid.activities.VerifySeedActivity
import com.dcrandroid.adapter.TransactionAdapter
import com.dcrandroid.data.Constants
import com.dcrandroid.data.Transaction
import com.dcrandroid.extensions.totalWalletBalance
import com.dcrandroid.util.*
import com.google.gson.GsonBuilder
import dcrlibwallet.*
import kotlinx.android.synthetic.main.content_overview.*
import kotlinx.android.synthetic.main.overview_sync_layout.*
import java.math.BigDecimal
import java.math.MathContext
import java.text.DecimalFormat
import kotlin.math.roundToInt

const val TAG = "OverviewFragment"
const val VERIFY_SEED_REQUEST_CODE = 5

class OverviewFragment : Fragment(), SwipeRefreshLayout.OnRefreshListener, SyncProgressListener {

    private var walletData: WalletData? = null
    private val wallet: LibWallet
        get() = walletData!!.wallet

    private var transactionAdapter: TransactionAdapter? = null
    private var util: PreferenceUtil? = null
    private val transactionList = ArrayList<Transaction>()
    private var latestTransactionHeight: Int = 0
    private var needsUpdate = false
    private var isForeground: Boolean = false
    private var notificationManager: NotificationManager? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.content_overview, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        if (activity != null) {
            activity!!.title = getString(R.string.overview)
        }

        registerNotificationChannel()

        util = PreferenceUtil(context!!)
        walletData = WalletData.getInstance()

        swipe_refresh_layout2.setColorSchemeResources(R.color.colorPrimaryDarkBlue,
                R.color.colorPrimaryDarkBlue,
                R.color.colorPrimaryDarkBlue,
                R.color.colorPrimaryDarkBlue)
        swipe_refresh_layout2.setOnRefreshListener(this)
        transactionAdapter = TransactionAdapter(transactionList, context!!)

        if (!walletData!!.wallet.isSyncing) setupSyncLayout()
        else hideSyncLayout()

        val mLayoutManager = LinearLayoutManager(context)
        history_recycler_view2.layoutManager = mLayoutManager
        history_recycler_view2.itemAnimator = DefaultItemAnimator()
        history_recycler_view2.addItemDecoration(DividerItemDecoration(context!!, LinearLayoutManager.VERTICAL))

        history_recycler_view2.addOnItemTouchListener(RecyclerTouchListener(context, history_recycler_view2, object : RecyclerTouchListener.ClickListener {
            override fun onClick(view: View, position: Int) {
                if (transactionList.size <= position || position < 0) {
                    return
                }
                val tx = transactionList[position]
                val i = Intent(context, TransactionDetailsActivity::class.java)
                i.putExtra(Constants.TRANSACTION, tx)
                startActivity(i)
            }

            override fun onLongClick(view: View, position: Int) {

            }
        }))

        show_history.setOnClickListener {
            it.postDelayed({
                if (activity != null && activity is MainActivity) {
                    val mainActivity = activity as MainActivity?
                    mainActivity!!.displayHistory()
                }
            }, 200)
        }

        send.setOnClickListener {
            it.postDelayed({
                if (activity != null && activity is MainActivity) {
                    val mainActivity = activity as MainActivity?
                    mainActivity!!.displaySend()
                }
            }, 200)
        }

        receive.setOnClickListener {
            it.postDelayed({
                if (activity != null && activity is MainActivity) {
                    val mainActivity = activity as MainActivity?
                    mainActivity!!.displayReceive()
                }
            }, 200)
        }

        tap_for_more_info.setOnClickListener {
            it.visibility = View.GONE
            pb_status_layout.visibility = View.VISIBLE
            syncing_peers.visibility = View.VISIBLE
        }

        pb_status_layout.setOnClickListener {
            it.visibility = View.GONE
            syncing_peers.visibility = View.GONE
            tap_for_more_info.visibility = View.VISIBLE
        }

        pb_status_layout.setOnLongClickListener {
            if (pb_verbose_status.visibility == View.VISIBLE) {
                pb_verbose_status.visibility = View.GONE
            } else {
                pb_verbose_status.visibility = View.VISIBLE
            }
            return@setOnLongClickListener true
        }

        syncing_peers.setOnClickListener {
            it.visibility = View.GONE
            pb_status_layout.visibility = View.GONE
            tap_for_more_info.visibility = View.VISIBLE
        }

        // using true as default as per backwards compatibility
        // we don't want to tell wallets created before this
        // feature to verify their seed
        if (!util!!.getBoolean(Constants.VERIFIED_SEED, true)) {
            verify_seed.visibility = View.VISIBLE

            verify_seed.setOnClickListener {
                val seed = util!!.get(Constants.SEED)
                val i = Intent(context, VerifySeedActivity::class.java)
                        .putExtra(Constants.SEED, seed)
                startActivityForResult(i, VERIFY_SEED_REQUEST_CODE)
            }
        }

        val vto = history_recycler_view2.viewTreeObserver
        if (vto.isAlive) {
            vto.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    val recyclerViewHeight = history_recycler_view2.height
                    println("Recycler View Height: ${history_recycler_view2.height}")
                    if (recyclerViewHeight != 0)
                        prepareHistoryData()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                        history_recycler_view2.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    } else {
                        history_recycler_view2.viewTreeObserver.removeGlobalOnLayoutListener(this)
                    }
                }
            })
        }

        history_recycler_view2.adapter = transactionAdapter
        registerForContextMenu(history_recycler_view2)

        walletData!!.wallet.removeSyncProgressListener(TAG)
        walletData!!.wallet.addSyncProgressListener(this, TAG)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VERIFY_SEED_REQUEST_CODE && resultCode == RESULT_OK) {
            verify_seed.visibility = View.GONE
        }
    }

    private fun registerNotificationChannel() {
        notificationManager = context!!.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("new transaction", getString(R.string.app_name), NotificationManager.IMPORTANCE_DEFAULT)
            channel.enableLights(true)
            channel.enableVibration(true)
            channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            channel.importance = NotificationManager.IMPORTANCE_LOW
            notificationManager!!.createNotificationChannel(channel)
        }
    }

    private fun getMaxDisplayItems(): Int {
        if (activity == null) {
            return 0
        }
        val px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 52f, activity!!.resources.displayMetrics).roundToInt()
        val max = history_recycler_view2.height / px
        if (max < 1) {
            return 5
        }


        return max
    }

    private fun getBalance() {
        val requiredConfirmation = if (util!!.getBoolean(Constants.SPEND_UNCONFIRMED_FUNDS)) 0 else Constants.REQUIRED_CONFIRMATIONS
        val totalBalance = wallet.totalWalletBalance(requiredConfirmation, context!!)
        overview_av_balance.text = CoinFormat.format(Utils.formatDecredWithComma(totalBalance) + " DCR")
    }

    private fun prepareHistoryData() {
        if (!isForeground) {
            needsUpdate = true
            return
        }

        if (activity == null) {
            return
        }

        if (swipe_refresh_layout2.isRefreshing) {
            return
        }

        getTransactions()
    }

    private fun getTransactions() {
        activity!!.runOnUiThread { swipe_refresh_layout2.isRefreshing = true }
        if (walletData!!.wallet.isSyncing) {
            no_history.setText(R.string.synchronizing)
            swipe_refresh_layout2.isRefreshing = false
            return
        }

        getBalance()

        object : Thread() {
            override fun run() {
                try {

                    val jsonResult = walletData!!.wallet.getTransactions(getMaxDisplayItems(), Dcrlibwallet.TxFilterAll)

                    val gson = GsonBuilder().registerTypeHierarchyAdapter(ArrayList::class.java, Deserializer.TransactionDeserializer())
                            .create()

                    val transactions = gson.fromJson(jsonResult, Array<Transaction>::class.java)
                    if (transactions == null || transactions.isEmpty()) {
                        activity!!.runOnUiThread {
                            no_history.setText(R.string.no_transactions)
                            history_recycler_view2.visibility = View.GONE
                            swipe_refresh_layout2.isRefreshing = false
                        }
                    } else {
                        activity!!.runOnUiThread {
                            transactionList.clear()
                            transactionList.addAll(transactions)

                            latestTransactionHeight = transactions[0].height
                            transactionList.forEach { latestTransactionHeight = if (it.height < latestTransactionHeight) it.height else latestTransactionHeight }
                            latestTransactionHeight += 1

                            val recentTransactionHash = util!!.get(Constants.RECENT_TRANSACTION_HASH)

                            if (recentTransactionHash.isNotEmpty()) {
                                val hashIndex = transactionList.indexOfFirst { it.hash == recentTransactionHash }
                                if (hashIndex == -1) {
                                    // All transactions in this list is new
                                    transactionList.map { it.animate = true }
                                } else if (hashIndex != 0) {
                                    transactionList.mapIndexed { index, it -> if(index < hashIndex) it.animate = true }
                                }
                            } else {
                                transactionList.map { it.animate = true }
                            }

                            util!!.set(Constants.RECENT_TRANSACTION_HASH, transactionList[0].hash)


                            val txNotificationHash = util!!.get(Constants.TX_NOTIFICATION_HASH)
                            println("Hash $txNotificationHash")

                            if (txNotificationHash.isNotEmpty() && txNotificationHash != transactionList[0].hash) {
                                val hashIndex = transactionList.indexOfFirst { it.hash == txNotificationHash }
                                val format = DecimalFormat(getString(R.string.you_received) + " #.######## DCR")

                                if (hashIndex > 0) {
                                    println("Hash is $hashIndex")
                                    val subList = transactionList.subList(0, hashIndex)
                                    subList.forEach {
                                        if (it.direction == 1) {
                                            val satoshi = BigDecimal.valueOf(it.amount)

                                            val amount = satoshi.divide(BigDecimal.valueOf(1e8), MathContext(100))
                                            println("Sending Notifications for ${it.hash}")
                                            Utils.sendTransactionNotification(context, notificationManager, format.format(amount), it.totalInput.toInt() + it.totalOutput.toInt() + it.timestamp.toInt())
                                        } else {
                                            println("Not Sending Notifications for ${it.hash}")
                                        }
                                    }
                                } else if (hashIndex < 0) {
                                    println("Hash is less $hashIndex")
                                    val subList = transactionList.subList(0, transactionList.size - 1)
                                    subList.forEach {
                                        if (it.direction == 1) {
                                            val satoshi = BigDecimal.valueOf(it.amount)

                                            val amount = satoshi.divide(BigDecimal.valueOf(1e8), MathContext(100))
                                            Utils.sendTransactionNotification(context, notificationManager, format.format(amount), it.totalInput.toInt() + it.totalOutput.toInt() + it.timestamp.toInt())
                                        }
                                    }
                                }
                            }

                            println("First Hash: " + transactionList[0].hash)
                            util!!.set(Constants.TX_NOTIFICATION_HASH, transactionList[0].hash)

                            history_recycler_view2.visibility = View.VISIBLE

                            transactionAdapter!!.notifyDataSetChanged()
                            swipe_refresh_layout2.isRefreshing = false
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }.start()
    }

    override fun onRefresh() {
        getTransactions()
    }

    override fun onResume() {
        super.onResume()

        if (walletData!!.wallet.isSyncing) {
            overview_sync_layout.visibility = View.VISIBLE
            tv_synchronizing.setText(R.string.starting_synchronization)
        } else {
            hideSyncLayout()
        }

        isForeground = true
        if (needsUpdate) {
            needsUpdate = false
            prepareHistoryData()
        }
    }

    override fun onPause() {
        super.onPause()
        isForeground = false
    }

    fun newTransaction(transaction: Transaction) {

        if (transactionList.find { it.hash == transaction.hash } != null) {
            // Transaction is a duplicate
            return
        }

        transaction.animate = true
        transactionList.add(0, transaction)
        if (transactionList.size > getMaxDisplayItems()) {
            transactionList.removeAt(transactionList.size - 1)
        }
        latestTransactionHeight = transaction.height + 1

        if (activity == null) {
            return
        }

        activity!!.runOnUiThread {
            history_recycler_view2.visibility = View.VISIBLE
            util!!.set(Constants.RECENT_TRANSACTION_HASH, transaction.hash)
            transactionAdapter!!.notifyDataSetChanged()
            getBalance()
        }
    }

    fun transactionConfirmed(hash: String, height: Int) {
        for (i in transactionList.indices) {
            if (transactionList[i].hash == hash) {
                val transaction = transactionList[i]
                transaction.height = height
                transaction.animate = false
                latestTransactionHeight = transaction.height + 1
                transactionList[i] = transaction
                transactionAdapter!!.notifyItemChanged(i)
                break
            }
        }

        getBalance()
    }

    fun blockAttached(height: Int) {
        if (height - latestTransactionHeight < 2) {
            for (i in transactionList.indices) {
                val tx = transactionList[i]
                if (height - tx.height >= 2) {
                    continue
                }
                tx.animate = false
                activity!!.runOnUiThread {
                    transactionAdapter!!.notifyItemChanged(i)
                }
            }
        }
    }

    private fun publishProgress(syncProgress: Int, syncStatus: String, remainingSyncTime: Long) {
        if (activity != null) {
            activity!!.runOnUiThread {
                tv_synchronizing.setText(R.string.synchronizing)
                pb_percent_complete.visibility = View.VISIBLE
                if (pb_status_layout.visibility == View.GONE) {
                    tap_for_more_info.visibility = View.VISIBLE
                }

                pb_sync_progress.apply {
                    visibility = View.VISIBLE
                    progress = syncProgress
                }

                pb_percent_complete.text = Utils.getSyncTimeRemaining(remainingSyncTime, syncProgress, false, context)
                pb_status.text = syncStatus

                val peers = walletData!!.peers
                if (BuildConfig.IS_TESTNET) {
                    if (peers == 1) {
                        syncing_peers.text = getString(R.string.one_syncing_peer_testnet)
                    } else {
                        syncing_peers.text = getString(R.string.syncing_peers_testnet, peers)
                    }
                } else {
                    if (peers == 1) {
                        syncing_peers.text = getString(R.string.one_syncing_peer_mainnet)
                    } else {
                        syncing_peers.text = getString(R.string.syncing_peers_mainnet, peers)
                    }
                }
            }
        }
    }

    override fun onHeadersFetchProgress(report: HeadersFetchProgressReport) {
        if (context == null) {
            return
        }

        val daysBehind = Utils.calculateDays(System.currentTimeMillis() / 1000 - report.currentHeaderTimestamp, context)
        val syncStatus = getString(R.string.fetched_header_format, report.fetchedHeadersCount,
                report.totalHeadersToFetch, report.headersFetchProgress, daysBehind)

        publishProgress(report.generalSyncProgress.totalSyncProgress, syncStatus, report.generalSyncProgress.totalTimeRemainingSeconds)
    }

    override fun onAddressDiscoveryProgress(report: AddressDiscoveryProgressReport) {
        if (context == null) {
            return
        }
        val discoveryProgress = report.addressDiscoveryProgress.toLong()
        val syncStatus = if (discoveryProgress > 100) {
            getString(R.string.overview_discovering_used_addresses_over, discoveryProgress)
        } else {
            getString(R.string.overview_discovering_used_addresses, discoveryProgress)
        }

        publishProgress(report.generalSyncProgress.totalSyncProgress, syncStatus, report.generalSyncProgress.totalTimeRemainingSeconds)
    }

    override fun onHeadersRescanProgress(report: HeadersRescanProgressReport) {
        if (context == null) {
            return
        }

        val syncStatus = getString(R.string.overview_rescan_height_format, report.currentRescanHeight,
                report.totalHeadersToScan, Math.round(report.rescanProgress.toFloat()))

        publishProgress(report.generalSyncProgress.totalSyncProgress, syncStatus, report.generalSyncProgress.totalTimeRemainingSeconds)
    }

    override fun debug(debugInfo: DebugInfo) {
        if (context == null) {
            return
        }

        // Current Stage Time
        val elapsedStageTime = debugInfo.currentStageTimeElapsed
        val remainingStageTime = debugInfo.currentStageTimeRemaining
        val totalStageTime = elapsedStageTime + remainingStageTime

        // Total Sync Time
        val elapsedSyncTime = debugInfo.totalTimeElapsed
        val remainingSyncTime = debugInfo.totalTimeRemaining
        val totalSyncTime = elapsedSyncTime + remainingSyncTime

        activity!!.runOnUiThread {
            pb_verbose_status.text = getString(R.string.sync_status_verbose, Utils.getTime(elapsedSyncTime), Utils.getTime(remainingSyncTime),
                    Utils.getTime(Math.round(totalSyncTime.toFloat()).toLong()), Utils.getTime(elapsedStageTime), Utils.getTime(remainingStageTime),
                    Utils.getTime(Math.round(totalStageTime.toFloat()).toLong()))
        }
    }

    override fun onSyncCanceled(willRestart: Boolean) {
        if (context == null) {
            return
        }
        // clear sync layout if sync is not going to restart.
        if (!willRestart)
            hideSyncLayout()
    }

    override fun onPeerConnectedOrDisconnected(numberOfConnectedPeers: Int) {}

    override fun onSyncCompleted() {
        if (context == null) {
            return
        }
        hideSyncLayout()
    }

    override fun onSyncEndedWithError(err: java.lang.Exception?) {
        if (context == null) {
            return
        }
        err!!.printStackTrace()
        hideSyncLayout()
    }

    fun setupSyncLayout() {
        pb_sync_progress.progress = 0
        overview_sync_layout.visibility = View.VISIBLE
        tv_synchronizing.setText(R.string.starting_synchronization)
    }

    private fun hideSyncLayout() {
        activity!!.runOnUiThread {
            overview_sync_layout.visibility = View.GONE
            pb_sync_progress.visibility = View.GONE
            pb_percent_complete.visibility = View.GONE

            getBalance()
            prepareHistoryData()
        }
    }
}
