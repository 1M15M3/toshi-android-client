/*
 * 	Copyright (c) 2017. Toshi Browser, Inc
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

package com.toshi.view.adapter.viewholder;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.toshi.R;
import com.toshi.model.local.SendState;
import com.toshi.util.ImageUtil;
import com.toshi.view.adapter.listeners.OnItemClickListener;

import java.io.File;

import de.hdodenhof.circleimageview.CircleImageView;
import jp.wasabeef.glide.transformations.RoundedCornersTransformation;

public final class ImageViewHolder extends RecyclerView.ViewHolder {

    private @Nullable CircleImageView avatar;
    private @Nullable TextView sentStatusMessage;
    private @NonNull ImageView image;
    private @SendState.State int sendState;
    private String attachmentFilePath;
    private String avatarUri;

    public ImageViewHolder(final View v) {
        super(v);
        this.avatar = (CircleImageView) v.findViewById(R.id.avatar);
        this.sentStatusMessage = (TextView) v.findViewById(R.id.sent_status_message);
        this.image = (ImageView) v.findViewById(R.id.image);
    }

    public ImageViewHolder setAvatarUri(final String uri) {
        this.avatarUri = uri;
        return this;
    }

    public ImageViewHolder setSendState(final @SendState.State int sendState) {
        this.sendState = sendState;
        return this;
    }

    public ImageViewHolder setAttachmentFilePath(final String filePath) {
        this.attachmentFilePath = filePath;
        return this;
    }

    public ImageViewHolder draw() {
        showImage();
        renderAvatar();
        setSendState();
        return this;
    }

    private void renderAvatar() {
        if (this.avatar == null) {
            return;
        }

        Glide
            .with(this.avatar.getContext())
            .load(this.avatarUri)
            .into(this.avatar);
    }

    private void showImage() {
        final File imageFile = new File(this.attachmentFilePath);
        ImageUtil.renderFileIntoTarget(imageFile, this.image, new RoundedCornersTransformation(this.image.getContext(), 30, 0));
        this.attachmentFilePath = null;
    }

    private void setSendState() {
        if (this.sentStatusMessage == null) {
            return;
        }

        this.sentStatusMessage.setVisibility(View.GONE);
        if (this.sendState == SendState.STATE_FAILED || this.sendState == SendState.STATE_PENDING) {
            this.sentStatusMessage.setVisibility(View.VISIBLE);
            this.sentStatusMessage.setText(this.sendState == SendState.STATE_FAILED
                    ? R.string.error__message_failed
                    : R.string.error__message_pending);
        }
    }

    public ImageViewHolder setClickableImage(final OnItemClickListener<String> listener, final String filePath) {
        this.image.setOnClickListener(v -> listener.onItemClick(filePath));
        return this;
    }
}
