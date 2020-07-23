package com.dcrandroid.adapter

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.dcrandroid.R
import com.dcrandroid.activities.ProposalDetailsActivity
import com.dcrandroid.data.Proposal
import com.dcrandroid.util.Utils
import java.security.Timestamp
import java.text.SimpleDateFormat
import java.util.*


class ProposalAdapter(private val proposals: List<Proposal>, private val context: Context) : RecyclerView.Adapter<ProposalAdapter.MyViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val itemView = LayoutInflater.from(parent.context)
                .inflate(R.layout.proposal_list_row, parent, false)
        return MyViewHolder(itemView)
    }

    inner class MyViewHolder internal constructor(var view: View) : RecyclerView.ViewHolder(view) {
        var title: TextView = view.findViewById(R.id.proposal_title)
//        var status: TextView = view.findViewById(R.id.proposal_status)
        var author: TextView = view.findViewById(R.id.proposal_author)
        var timestamp: TextView = view.findViewById(R.id.proposal_timestamp)
        var comments: TextView = view.findViewById(R.id.proposal_comments)
        var version: TextView = view.findViewById(R.id.proposal_version)

//        var meta: TextView = view.findViewById(R.id.meta)
        var progressBar: ProgressBar = view.findViewById(R.id.progressBar)
        var progress: TextView = view.findViewById(R.id.progress)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val proposal = proposals[position]
        holder.title.text = proposal.name
        holder.author.text = proposal.username

//        val sdf = SimpleDateFormat("dd/MM/yy hh:mm a")
//        val netDate = Date(0)
//        val date =sdf.format(netDate)


//        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
//        var dateStr = "2016-01-24T16:00:00.000Z"
//        var date = inputFormat.parse(dateStr)
//        var niceDateStr = DateUtils.getRelativeTimeSpanString(date.time, Calendar.getInstance().timeInMillis, DateUtils.MINUTE_IN_MILLIS)
//        val stamp = Timestamp(System.currentTimeMillis())
//        val date = Date(proposal.timestamp)
//        println(date)

        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
        val date = java.util.Date(proposal.timestamp * 1000)
        sdf.format(date)

//        java.time.format.DateTimeFormatter.ISO_INSTANT
//                .format(java.time.Instant.ofEpochSecond(proposal.timestamp))

        holder.timestamp.text = Utils.calculateTime(System.currentTimeMillis() / 1000 - proposal.timestamp, context)
        Log.i("ProposalAdapter", "[][[][][][][][][][][][[] " + sdf.format(date))
        holder.comments.text = String.format(Locale.getDefault(), "%d Comments", proposal.getNumcomments())
        holder.version.text = String.format(Locale.getDefault(), "version %s", proposal.version)
        val meta = String.format(Locale.getDefault(), "updated %s \nby %s \nversion %s - %d Comments",
                Utils.calculateTime(System.currentTimeMillis() / 1000 - proposal.timestamp, context), proposal.username, proposal.version, proposal.getNumcomments())
//        holder.meta.setText(meta, TextView.BufferType.SPANNABLE)
        if (proposal.voteStatus != null && proposal.voteStatus!!.totalvotes != 0) {
            holder.progress.visibility = View.VISIBLE
            holder.progressBar.visibility = View.VISIBLE
            val percentage = (proposal.voteStatus!!.yes.toFloat() / proposal.voteStatus!!.totalvotes.toFloat()) * 100
            holder.progress.text = "%.2f%%".format(percentage)
            holder.progressBar.progress = percentage.toInt()
        } else {
            holder.progress.visibility = View.GONE
            holder.progressBar.visibility = View.GONE
        }
        holder.view.setOnClickListener {
            val intent = Intent(context, ProposalDetailsActivity::class.java)
            val b = Bundle()
            b.putSerializable("proposal", proposal)
            intent.putExtras(b)
            context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int {
        return proposals.size
    }
}