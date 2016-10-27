package com.bakkenbaeck.token.presenter;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.util.Log;
import android.view.View;

import com.bakkenbaeck.token.R;
import com.bakkenbaeck.token.model.ActivityResultHolder;
import com.bakkenbaeck.token.model.ChatMessage;
import com.bakkenbaeck.token.model.LocalBalance;
import com.bakkenbaeck.token.network.ws.model.ConnectionState;
import com.bakkenbaeck.token.network.ws.model.Message;
import com.bakkenbaeck.token.presenter.store.ChatMessageStore;
import com.bakkenbaeck.token.util.LogUtil;
import com.bakkenbaeck.token.util.MessageUtil;
import com.bakkenbaeck.token.util.OnCompletedObserver;
import com.bakkenbaeck.token.util.OnNextObserver;
import com.bakkenbaeck.token.util.OnNextSubscriber;
import com.bakkenbaeck.token.util.OnSingleClickListener;
import com.bakkenbaeck.token.util.SharedPrefsUtil;
import com.bakkenbaeck.token.util.SnackbarUtil;
import com.bakkenbaeck.token.view.BaseApplication;
import com.bakkenbaeck.token.view.activity.ChatActivity;
import com.bakkenbaeck.token.view.activity.VideoActivity;
import com.bakkenbaeck.token.view.activity.WithdrawActivity;
import com.bakkenbaeck.token.view.adapter.MessageAdapter;
import com.bakkenbaeck.token.view.custom.BalanceBar;
import com.bakkenbaeck.token.view.dialog.PhoneInputDialog;
import com.bakkenbaeck.token.view.dialog.VerificationCodeDialog;

import java.util.Calendar;

import io.realm.Realm;
import rx.Subscriber;

import static android.app.Activity.RESULT_OK;

public final class ChatPresenter implements Presenter<ChatActivity>, View.OnClickListener {
    private static final String TAG = "ChatPresenter";
    private final int VIDEO_REQUEST_CODE = 1;
    private final int WITHDRAW_REQUEST_CODE = 2;

    private ChatActivity activity;
    private MessageAdapter messageAdapter;
    private boolean firstViewAttachment = true;
    private boolean isShowingAnotherOneButton;
    private ChatMessageStore chatMessageStore;
    private Snackbar networkStateSnackbar;

    @Override
    public void onViewAttached(final ChatActivity activity) {
        this.activity = activity;
        initToolbar();

        if (firstViewAttachment) {
            firstViewAttachment = false;
            initLongLivingObjects();
        }
        initShortLivingObjects();

        // Refresh state
        unpauseMessageAdapter();
        this.messageAdapter.notifyDataSetChanged();
        refreshAnotherOneButtonState();
        scrollToBottom(false);

        BaseApplication.get().reconnectWebsocket();

        initBalanceBar();
        initView();
    }

    private void initView(){
        this.activity.getBinding().messagesList.setAdapter(this.messageAdapter);
    }

    private void initBalanceBar(){
        final BalanceBar balanceBar = this.activity.getBinding().balanceBar;
        balanceBar.setOnLevelClicked(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                showPhoneInputDialog();
            }
        });
    }
    
    public boolean isAttached() {
        return this.activity != null;
    }

    private void initLongLivingObjects() {
        initNetworkStateSnackbar();

        this.chatMessageStore = new ChatMessageStore();
        this.chatMessageStore.getEmptySetObservable().subscribe(this.noStoredChatMessages);
        this.chatMessageStore.getNewMessageObservable().subscribe(this.newChatMessage);
        this.chatMessageStore.getUnwatchedVideoObservable().subscribe(this.unwatchedVideo);
        this.chatMessageStore.getNewDateObservable().subscribe(this.newDateMessage);
        BaseApplication.get().getSocketObservables().getMessageObservable().subscribe(this.newMessageSubscriber);
        BaseApplication.get().getSocketObservables().getConnectionObservable().subscribe(this.connectionStateSubscriber);

        this.messageAdapter = new MessageAdapter(activity);
        this.messageAdapter.setOnVerifyClickListener(this);
        registerMessageClickedObservable();

        this.chatMessageStore.load();
    }

    private void initNetworkStateSnackbar() {
        this.networkStateSnackbar = Snackbar.make(
                this.activity.getBinding().balanceBar,
                Html.fromHtml(this.activity.getString(R.string.socket__connecting_state)),
                Snackbar.LENGTH_INDEFINITE);
    }

    private void initShortLivingObjects() {
        BaseApplication.get().getLocalBalanceManager().getObservable().subscribe(this.newBalanceSubscriber);
        BaseApplication.get().getLocalBalanceManager().getLevelObservable().subscribe(this.newReputationSubscriber);
    }

    private void initToolbar() {
        final String title = this.activity.getResources().getString(R.string.chat__title);
        final Toolbar toolbar = this.activity.getBinding().toolbar;
        this.activity.setSupportActionBar(toolbar);
        this.activity.getSupportActionBar().setTitle(title);
    }

    private final OnCompletedObserver<Void> noStoredChatMessages = new OnCompletedObserver<Void>() {
        @Override
        public void onCompleted() {
            showWelcomeMessage();
        }
    };

    private final OnNextObserver<ChatMessage> newChatMessage = new OnNextObserver<ChatMessage>() {
        @Override
        public void onNext(final ChatMessage chatMessage) {
            messageAdapter.addMessage(chatMessage);
        }
    };

    private final OnNextObserver<Boolean> unwatchedVideo = new OnNextObserver<Boolean>() {
        @Override
        public void onNext(final Boolean hasUnwatchedVideo) {
            if (!hasUnwatchedVideo) {
                promptNewVideo();
            }
        }
    };

    private void showWelcomeMessage() {
        ChatMessage message = new ChatMessage().makeDayMessage();
        displayMessage(message);

        final ChatMessage response = new ChatMessage().makeRemoteVideoMessage(this.activity.getResources().getString(R.string.chat__welcome_message));
        showAVideo(response);
    }

    private void showAVideo(ChatMessage message) {
        displayMessage(message, 500);
    }

    private void showVideoRequestMessage() {
        final ChatMessage message = new ChatMessage().makeLocalMessageWithText(this.activity.getResources().getString(R.string.chat__request_video_message));
        displayMessage(message);
    }

    private void displayMessage(final ChatMessage chatMessage, final int delay) {
        final Handler handler = new Handler(Looper.getMainLooper());

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                chatMessageStore.save(chatMessage);
                scrollToBottom(true);
            }
        }, delay);

    }

    private void displayMessage(final ChatMessage chatMessage) {
        displayMessage(chatMessage, 0);
    }

    private final OnNextObserver<LocalBalance> newBalanceSubscriber = new OnNextObserver<LocalBalance>() {
        @Override
        public void onNext(final LocalBalance newBalance) {
            if (activity != null && newBalance != null) {
                activity.getBinding().balanceBar.setEthValue(newBalance.getEthValue(), newBalance.getUnconfirmedBalanceAsEth());
                activity.getBinding().balanceBar.setBalance(newBalance);
            }
        }
    };

    private final OnNextObserver<Integer> newReputationSubscriber = new OnNextObserver<Integer>() {
        @Override
        public void onNext(Integer reputationScore) {
            if(activity != null) {
                BalanceBar balanceBar = activity.getBinding().balanceBar;
                if (balanceBar != null) {
                    balanceBar.setReputation(reputationScore);
                    if (reputationScore == 0) {
                        balanceBar.enableClickEvents();
                        //if reputation is 0, set verifies to false so the user can click the verify button
                        SharedPrefsUtil.saveVerified(false);
                        messageAdapter.notifyDataSetChanged();
                    } else {
                        balanceBar.disableClickEvents();
                    }
                }
            }
        }
    };

    private final OnNextObserver<Message> newMessageSubscriber = new OnNextObserver<Message>() {
        @Override
        public void onNext(final Message message) {
            ChatMessage response;

            if(message.getType() != null && message.getType().equals(ChatMessage.REWARD_EARNED_TYPE)){
                response = new ChatMessage().makeRemoteRewardMessage(message);
            }else{
                response = new ChatMessage().makeRemoteMessageWithText(message.toString());
            }

            displayMessage(response, 0);
        }
    };

    @Override
    public void onClick(final View view) {
        showPhoneInputDialog();
    }

    private PhoneInputDialog phoneInputDialog;

    private void showPhoneInputDialog(){
        if(phoneInputDialog != null){
            if(phoneInputDialog.isVisible2()){
                return;
            }
        }
        phoneInputDialog = new PhoneInputDialog();
        phoneInputDialog.show(activity.getSupportFragmentManager(), "dialog");
    }

    private void scrollToBottom(final boolean animate) {
        if (this.activity != null && this.messageAdapter.getItemCount() > 0) {
            if (animate) {
                this.activity.getBinding().messagesList.smoothScrollToPosition(this.messageAdapter.getItemCount() - 1);
            } else {
                this.activity.getBinding().messagesList.scrollToPosition(this.messageAdapter.getItemCount() - 1);
            }
        }
    }

    private void unpauseMessageAdapter() {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                messageAdapter.unPauseRendering();
                scrollToBottom(true);
            }
        }, 0);
    }

    private void refreshAnotherOneButtonState() {
        this.activity.getBinding().buttonAnotherVideo.setVisibility(
                this.isShowingAnotherOneButton
                        ? View.VISIBLE
                        : View.INVISIBLE
        );

        if (this.isShowingAnotherOneButton) {
            this.activity.getBinding().buttonAnotherVideo.setOnClickListener(new OnSingleClickListener() {
                @Override
                public void onSingleClick(final View view) {
                    long nextDateEnabled = SharedPrefsUtil.getNextDateEnabled();
                    long currentDate = System.currentTimeMillis();

                    Log.d(TAG, "onSingleClick: currentDate: " + currentDate + " nextDateEnabled: " + nextDateEnabled);

                    final Handler handler = new Handler(Looper.getMainLooper());
                    if(nextDateEnabled == 0 || currentDate >= nextDateEnabled) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                chatMessageStore.checkDate();
                            }
                        });
                    }else{
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                SnackbarUtil.make(activity.getBinding().balanceBar, "You have watched too many ads, come back tomorrow").show();
                            }
                        });
                    }
                }
            });
        }
    }

    private void watchAnotherVideo(){
        isShowingAnotherOneButton = false;
        refreshAnotherOneButtonState();
        showVideoRequestMessage();

        String s = MessageUtil.getRandomMessage();
        ChatMessage message = new ChatMessage().makeRemoteVideoMessage(s);
        showAVideo(message);
    }

    private void promptNewVideo() {
        this.isShowingAnotherOneButton = true;
        refreshAnotherOneButtonState();
    }

    //Subscriber to the date database call. Checking if the last message has a different date than today
    private final Subscriber<ChatMessage> newDateMessage = new Subscriber<ChatMessage>() {
        @Override
        public void onCompleted() {

        }

        @Override
        public void onError(Throwable e) {
            watchAnotherVideo();
        }

        @Override
        public void onNext(ChatMessage chatMessage) {
            if(chatMessage != null) {
                Calendar today = Calendar.getInstance();
                Calendar anotherDay = Calendar.getInstance();
                anotherDay.setTimeInMillis(chatMessage.getCreationTime());

                if (today.get(Calendar.DAY_OF_YEAR) != anotherDay.get(Calendar.DAY_OF_YEAR)) {
                    ChatMessage message = new ChatMessage().makeDayMessage();
                    displayMessage(message);
                }
            }

            watchAnotherVideo();
        }
    };

    @Override
    public void onViewDetached() {
        if(connectionStateSubscriber != null) {
            connectionStateSubscriber.unsubscribe();
        }
        this.messageAdapter.pauseRendering();
        this.activity = null;
    }

    private void registerMessageClickedObservable() {
        this.messageAdapter.getPositionClicks().subscribe(this.clicksSubscriber);
    }

    private void unregisterMessageClickedObservable() {
        if (this.clicksSubscriber.isUnsubscribed()) {
            return;
        }
        this.clicksSubscriber.unsubscribe();
    }

    private final OnNextSubscriber<Integer> clicksSubscriber = new OnNextSubscriber<Integer>() {
        @Override
        public void onNext(final Integer clickedPosition) {
            showVideoActivity(clickedPosition);
        }

        private void showVideoActivity(final int clickedPosition) {
            final Intent intent = new Intent(activity, VideoActivity.class);
            intent.putExtra(VideoPresenter.INTENT_CLICKED_POSITION, clickedPosition);
            activity.startActivityForResult(intent, VIDEO_REQUEST_CODE);
        }
    };

    @Override
    public void onViewDestroyed() {
        if(messageAdapter != null){
            messageAdapter.clean();
            messageAdapter = null;
        }
        this.activity = null;
        unregisterMessageClickedObservable();
    }

    public void handleActivityResult(final ActivityResultHolder activityResultHolder) {
        if (activityResultHolder.getResultCode() != RESULT_OK) {
            return;
        }

        if (activityResultHolder.getRequestCode() == VIDEO_REQUEST_CODE) {
            handleVideoCompleted(activityResultHolder);
            return;
        }
    }

    private void handleVideoCompleted(final ActivityResultHolder activityResultHolder) {
        markVideoAsWatched(activityResultHolder);
        promptNewVideo();
    }

    private void markVideoAsWatched(final ActivityResultHolder activityResultHolder) {
        final int clickedPosition = activityResultHolder.getIntent().getIntExtra(VideoPresenter.INTENT_CLICKED_POSITION, 0);
        final ChatMessage clickedMessage = this.messageAdapter.getItemAt(clickedPosition);
        Realm.getDefaultInstance().beginTransaction();
        clickedMessage.markAsWatched();
        Realm.getDefaultInstance().commitTransaction();
        this.messageAdapter.notifyItemChanged(clickedPosition);
    }

    public void handleWithdrawClicked() {
        final Intent intent = new Intent(this.activity, WithdrawActivity.class);
        this.activity.startActivityForResult(intent, WITHDRAW_REQUEST_CODE);
        this.activity.overridePendingTransition(R.anim.enter_fade_in, R.anim.exit_fade_out);
    }

    private final OnNextSubscriber<ConnectionState> connectionStateSubscriber = new OnNextSubscriber<ConnectionState>() {
        @Override
        public void onNext(ConnectionState connectionState) {
            LogUtil.e(getClass(), "connectionStateSubscriber");

            if (connectionState == ConnectionState.CONNECTING) {
                if (networkStateSnackbar.isShownOrQueued()) {
                    return;
                }
                LogUtil.e(getClass(), "Connecting");
                //networkStateSnackbar.show();
            } else {
                LogUtil.e(getClass(), "Connecting");
                //networkStateSnackbar.dismiss();
            }
        }
    };

    public void onPhoneInputSuccess(final PhoneInputDialog dialog) {
        final String phoneNumber = dialog.getInputtedPhoneNumber();
        final VerificationCodeDialog vcDialog = VerificationCodeDialog.newInstance(phoneNumber);
        vcDialog.show(this.activity.getSupportFragmentManager(), "dialog");
    }

    public void onVerificationSuccess() {
        if(activity != null && messageAdapter != null) {
            messageAdapter.disableVerifyButton(activity);
        }

        SharedPrefsUtil.saveVerified(true);
    }
}