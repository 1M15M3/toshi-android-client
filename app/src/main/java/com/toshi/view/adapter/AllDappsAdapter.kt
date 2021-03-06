/*
 * 	Copyright (c) 2017. Toshi Inc
 *
 * 	This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.toshi.view.adapter

import android.support.v7.util.DiffUtil
import android.support.v7.util.ListUpdateCallback
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import com.toshi.R
import com.toshi.model.local.dapp.DappDiffUtil
import com.toshi.model.network.dapp.Dapp
import com.toshi.view.adapter.viewholder.DappViewHolder

class AllDappsAdapter : RecyclerView.Adapter<DappViewHolder>() {

    private val dapps by lazy { mutableListOf<Dapp>() }
    var onItemClickedListener: ((Dapp) -> Unit)? = null

    fun setDapps(newDapps: List<Dapp>) {
        if (newDapps.isEmpty()) return
        val diffResult = DiffUtil.calculateDiff(DappDiffUtil(newDapps, dapps))
        dapps.clear()
        dapps.addAll(newDapps)
        diffResult.dispatchUpdatesTo(listUpdateCallback)
    }

    private val listUpdateCallback = object : ListUpdateCallback {
        override fun onChanged(position: Int, count: Int, payload: Any?) {
            notifyItemRangeChanged(position, count, payload)
        }

        override fun onMoved(fromPosition: Int, toPosition: Int) {
            notifyItemMoved(fromPosition, toPosition)
        }

        override fun onRemoved(position: Int, count: Int) {
            notifyItemRangeRemoved(position, count)
        }

        override fun onInserted(position: Int, count: Int) {
            notifyItemRangeInserted(position, count)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DappViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.list_item__dapp, parent, false)
        return DappViewHolder(v)
    }

    override fun onBindViewHolder(holder: DappViewHolder, position: Int) {
        val dapp = dapps[position]
        holder.setDapp(dapp)
                .setOnClickListener(dapp) { onItemClickedListener?.invoke(it) }
    }

    override fun getItemCount() = dapps.size
}