/*
 * 	Copyright (c) 2017. Token Browser, Inc
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

package com.tokenbrowser.view.notification.model;


import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v4.app.TaskStackBuilder;

import com.bumptech.glide.Glide;
import com.tokenbrowser.R;
import com.tokenbrowser.model.local.User;
import com.tokenbrowser.service.NotificationDismissedReceiver;
import com.tokenbrowser.view.BaseApplication;
import com.tokenbrowser.view.activity.ChatActivity;
import com.tokenbrowser.view.activity.MainActivity;
import com.tokenbrowser.view.activity.SplashActivity;
import com.tokenbrowser.view.custom.CropCircleTransformation;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import rx.Completable;

public class ChatNotification {

    public static final String DEFAULT_TAG = "unknown";

    private final User sender;
    private final ArrayList<String> messages;
    private List<String> lastFewMessages;
    private CharSequence lastMessage;
    private static final int MAXIMUM_NUMBER_OF_SHOWN_MESSAGES = 5;
    private Bitmap largeIcon;

    public ChatNotification(final User sender) {
        this.sender = sender;
        this.messages = new ArrayList<>();
        generateLatestMessages(this.messages);
    }

    public ChatNotification addUnreadMessage(final String unreadMessage) {
        this.messages.add(unreadMessage);
        generateLatestMessages(this.messages);
        return this;
    }

    private synchronized void generateLatestMessages(final ArrayList<String> messages) {
        if (messages.size() == 0) {
            this.lastMessage = "";
            this.lastFewMessages = new ArrayList<>(0);
            return;
        }

        this.lastMessage = messages.get(messages.size() -1);

        final int end = Math.max(messages.size(), 0);
        final int start = Math.max(end - MAXIMUM_NUMBER_OF_SHOWN_MESSAGES, 0);
        this.lastFewMessages = messages.subList(start, end);

    }

    public String getTag() {
        return this.sender == null ? DEFAULT_TAG : sender.getTokenId();
    }

    public String getTitle() {
        return this.sender == null
                ? BaseApplication.get().getString(R.string.unknown_sender)
                : this.sender.getDisplayName();
    }

    public Bitmap getLargeIcon() {
        return this.largeIcon;
    }

    public CharSequence getLastMessage() {
        return this.lastMessage;
    }

    public List<String> getLastFewMessages() {
        return new ArrayList<>(lastFewMessages);
    }

    public PendingIntent getPendingIntent() {
        final Intent mainIntent = new Intent(BaseApplication.get(), MainActivity.class);
        mainIntent.putExtra(MainActivity.EXTRA__ACTIVE_TAB, 1);

        if (this.sender == null || this.sender.getTokenId() == null) {
            return TaskStackBuilder.create(BaseApplication.get())
                    .addParentStack(MainActivity.class)
                    .addNextIntent(mainIntent)
                    .getPendingIntent(0, PendingIntent.FLAG_ONE_SHOT);
        }

        final Intent chatIntent = new Intent(BaseApplication.get(), ChatActivity.class);
        chatIntent.putExtra(ChatActivity.EXTRA__REMOTE_USER_ADDRESS, this.sender.getTokenId());

        final PendingIntent nextIntent = TaskStackBuilder.create(BaseApplication.get())
                .addParentStack(MainActivity.class)
                .addNextIntent(mainIntent)
                .addNextIntent(chatIntent)
                .getPendingIntent(getTitle().hashCode(), PendingIntent.FLAG_ONE_SHOT);

        final Intent splashIntent = new Intent(BaseApplication.get(), SplashActivity.class);
        splashIntent.putExtra(SplashActivity.EXTRA__NEXT_INTENT, nextIntent);

        return PendingIntent.getActivity(
                BaseApplication.get(),
                getTitle().hashCode(),
                splashIntent,
                PendingIntent.FLAG_ONE_SHOT);
    }

    public PendingIntent getDeleteIntent() {
        final Intent intent =
                new Intent(BaseApplication.get(), NotificationDismissedReceiver.class)
                        .putExtra(NotificationDismissedReceiver.TAG, getTag());

        return PendingIntent.getBroadcast(
                BaseApplication.get(),
                1,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public int getNumberOfUnreadMessages() {
        return messages.size();
    }

    public Completable generateLargeIcon() {
        if (this.largeIcon != null) return Completable.complete();
        if (getAvatarUri() == null) return Completable.fromAction(this::setDefaultLargeIcon);

        return Completable.fromAction(() -> {
            try {
                fetchUserAvatar();
            } catch (InterruptedException | ExecutionException e) {
                setDefaultLargeIcon();
            }
        });
    }

    private void fetchUserAvatar() throws InterruptedException, ExecutionException {
        this.largeIcon = Glide
                        .with(BaseApplication.get())
                        .load(getAvatarUri())
                        .asBitmap()
                        .transform(new CropCircleTransformation(BaseApplication.get()))
                        .into(200, 200)
                        .get();
    }

    private Bitmap setDefaultLargeIcon() {
        return this.largeIcon = BitmapFactory.decodeResource(BaseApplication.get().getResources(), R.mipmap.ic_launcher);
    }

    private String getAvatarUri() {
        return this.sender == null
                ? null
                : this.sender.getAvatar();
    }
}
