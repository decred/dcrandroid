/*
 * Copyright (c) 2018-2019 The Decred developers
 * Use of this source code is governed by an ISC
 * license that can be found in the LICENSE file.
 */

package com.dcrandroid.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.os.Build
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.dcrandroid.BuildConfig
import com.dcrandroid.MainActivity
import com.dcrandroid.R
import com.dcrandroid.activities.ProposalDetails
import com.dcrandroid.data.Constants
import com.dcrandroid.data.Proposal
import com.dcrandroid.util.Deserializer
import com.dcrandroid.util.PreferenceUtil
import com.dcrandroid.util.QueryAPI
import com.google.gson.GsonBuilder
import org.json.JSONObject
import java.util.ArrayList
import java.util.concurrent.atomic.AtomicInteger

const val ACTIVE_VOTING = 3
const val FINISHED_VOTING = 4

class AlarmReceiver: BroadcastReceiver(), QueryAPI.QueryAPICallback {

    private var context: Context? = null
    private var util: PreferenceUtil? = null
    private var notificationManager: NotificationManager? = null

    private var votingStartNotifications: Boolean = false
    private var votingEndNotifications: Boolean = false

    private val notificationId = AtomicInteger(0)

    override fun onReceive(context: Context?, intent: Intent?) {
        this.context = context
        util = PreferenceUtil(context!!)
        votingStartNotifications = util!!.getBoolean(Constants.VOTING_START_NOTIFICATIONS, false)
        votingEndNotifications = util!!.getBoolean(Constants.VOTING_END_NOTIFICATIONS, false)
        if (votingStartNotifications || votingEndNotifications) {
            if (intent!!.action != null && intent.action!!.equals(Intent.ACTION_BOOT_COMPLETED, ignoreCase = true)) {
                MainActivity().enablePoliteiaNotifs()
            }
            registerProposalNotificationChannel()
            processProposalNotification()
        }
    }

    private fun registerProposalNotificationChannel() {
        notificationManager = context!!.getSystemService(NOTIFICATION_SERVICE) as NotificationManager?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(Constants.VOTING_CHANGE_NOTIFICATIONS, context!!.getString(R.string.politeia), NotificationManager.IMPORTANCE_DEFAULT)
            channel.enableLights(true)
            channel.enableVibration(true)
            channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            channel.importance = NotificationManager.IMPORTANCE_LOW
            notificationManager!!.createNotificationChannel(channel)
        }
    }

    private fun processProposalNotification(){
        val vettedProposalsUrl = context!!.getString(R.string.vetted_proposals_url, BuildConfig.PoliteiaHost)
        val userAgent = util!!.get(Constants.USER_AGENT, Constants.EMPTY_STRING)
        QueryAPI(vettedProposalsUrl, userAgent, this).execute()
    }

    private fun parseResults(proposalsJson: String, voteStatusJson: String){
        val gson = GsonBuilder()
                .registerTypeHierarchyAdapter(ArrayList::class.java, Deserializer.ProposalDeserializer())
                .registerTypeHierarchyAdapter(Proposal.VoteStatus.OptionsResult::class.java, Deserializer.OptionsResultDeserializer())
                .create()

        var parentObj = JSONObject(proposalsJson)
        val proposals = gson.fromJson(parentObj.getJSONArray(Constants.PROPOSALS).toString(), Array<Proposal>::class.java)

        parentObj = JSONObject(voteStatusJson)
        val voteStatus = gson.fromJson(parentObj.getJSONArray(Constants.VOTES_STATUS).toString(), Array<Proposal.VoteStatus>::class.java)

        val voteStartedTokens = util!!.getStringList(Constants.VOTE_STARTED_TOKENS)
        val voteFinishedTokens = util!!.getStringList(Constants.VOTE_FINISHED_TOKENS)

        proposals.map { proposal ->
            val status = voteStatus.find { it.token == proposal.censorshipRecord!!.token } ?: return@map

            proposal.voteStatus = status

            if(voteStartedTokens == null){
                return@map
            }

            if(proposal.censorshipRecord == null){
                return@map
            }

            if(proposal.censorshipRecord!!.token == null){
                return@map
            }

            if (!voteStartedTokens.contains(proposal.censorshipRecord!!.token) && status.status == ACTIVE_VOTING && votingStartNotifications) {
                sendNotification(proposal)
                if(voteStartedTokens.indexOf(proposal.censorshipRecord!!.token) < 0){
                    voteStartedTokens.add(proposal.censorshipRecord!!.token)
                }
            }else if (!voteFinishedTokens.contains(proposal.censorshipRecord!!.token) && status.status == FINISHED_VOTING && votingEndNotifications) {
                sendNotification(proposal)
                if(voteFinishedTokens.indexOf(proposal.censorshipRecord!!.token) < 0){
                    voteFinishedTokens.add(proposal.censorshipRecord!!.token)
                }
            }
        }

        util!!.setStringList(Constants.VOTE_STARTED_TOKENS, voteStartedTokens)
        util!!.setStringList(Constants.VOTE_FINISHED_TOKENS, voteFinishedTokens)
    }

    private fun sendNotification(proposal: Proposal) {
        val launchIntent = Intent(context, ProposalDetails::class.java)
        launchIntent.action = Constants.NEW_POLITEIA_PROPOSAL_NOTIFICATION
        launchIntent.putExtra(Constants.PROPOSAL, proposal)

        val launchPendingIntent = PendingIntent.getActivity(context, Constants.POLITEIA_VOTING_STATUS, launchIntent, PendingIntent.FLAG_UPDATE_CURRENT)

        val notificationBuilder = NotificationCompat.Builder(context!!, Constants.VOTING_CHANGE_NOTIFICATIONS)
                .setSmallIcon(R.drawable.politeia2)
                .setOngoing(false)
                .setAutoCancel(true)
                .setGroup(Constants.POLITEIA_NOTIFICATIONS_GROUP)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(launchPendingIntent)

        if(proposal.voteStatus!!.status == ACTIVE_VOTING){
            notificationBuilder.setContentTitle(context!!.getString(R.string.proposal_voting_started, proposal.name))
        }else{
            notificationBuilder.setContentTitle(context!!.getString(R.string.proposal_voting_finished, proposal.name))
            notificationBuilder.setContentText(context!!.getString(R.string.proposals_votes_summary,
                    proposal.voteStatus!!.optionsResult!!.yes, proposal.voteStatus!!.optionsResult!!.no))
        }

        val notification = notificationBuilder.build()

        val groupSummary = NotificationCompat.Builder(context!!, Constants.VOTING_CHANGE_NOTIFICATIONS)
                .setContentTitle(context!!.getString(R.string.politeia))
                .setSmallIcon(R.drawable.politeia2)
                .setGroup(Constants.POLITEIA_NOTIFICATIONS_GROUP)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

        notificationManager?.let {
            synchronized(it) {
                it.notify(notificationId.incrementAndGet(), notification)
                it.notify(Constants.PROPOSAL_SUMMARY_ID, groupSummary)
            }
        }
    }

    override fun onQueryAPIError(e: Exception) {
        e.printStackTrace()
        Looper.prepare()
        Toast.makeText(context, e.localizedMessage, Toast.LENGTH_SHORT).show()
    }

    override fun onQueryAPISuccess(result: String?) {
        val voteStatusUrl = context!!.getString(R.string.proposals_vote_status_url, BuildConfig.PoliteiaHost)
        val userAgent = util!!.get(Constants.USER_AGENT, Constants.EMPTY_STRING)
        val proposalsJson = result

        QueryAPI(voteStatusUrl, userAgent, object : QueryAPI.QueryAPICallback{
            override fun onQueryAPISuccess(result: String?) {
                parseResults(proposalsJson!!, result!!)
            }

            override fun onQueryAPIError(e: Exception) {
                e.printStackTrace()
                Looper.prepare()
                Toast.makeText(context, e.localizedMessage, Toast.LENGTH_SHORT).show()
            }
        }).execute()
    }
}