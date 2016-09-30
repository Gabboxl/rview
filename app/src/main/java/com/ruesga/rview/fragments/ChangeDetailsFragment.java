/*
 * Copyright (C) 2016 Jorge Ruesga
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ruesga.rview.fragments;

import android.content.Context;
import android.content.res.Resources;
import android.databinding.DataBindingUtil;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.ListPopupWindow;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.ruesga.rview.BaseActivity;
import com.ruesga.rview.R;
import com.ruesga.rview.adapters.PatchSetsAdapter;
import com.ruesga.rview.annotations.ProguardIgnored;
import com.ruesga.rview.databinding.ChangeDetailsFragmentBinding;
import com.ruesga.rview.databinding.FileInfoItemBinding;
import com.ruesga.rview.databinding.MessageItemBinding;
import com.ruesga.rview.databinding.TotalAddedDeletedBinding;
import com.ruesga.rview.exceptions.OperationFailedException;
import com.ruesga.rview.fragments.EditDialogFragment.OnEditChanged;
import com.ruesga.rview.fragments.FilterableDialogFragment.OnFilterSelectedListener;
import com.ruesga.rview.gerrit.GerritApi;
import com.ruesga.rview.gerrit.filter.ChangeQuery;
import com.ruesga.rview.gerrit.model.AbandonInput;
import com.ruesga.rview.gerrit.model.AccountInfo;
import com.ruesga.rview.gerrit.model.ActionInfo;
import com.ruesga.rview.gerrit.model.AddReviewerResultInfo;
import com.ruesga.rview.gerrit.model.ApprovalInfo;
import com.ruesga.rview.gerrit.model.ChangeInfo;
import com.ruesga.rview.gerrit.model.ChangeInput;
import com.ruesga.rview.gerrit.model.ChangeMessageInfo;
import com.ruesga.rview.gerrit.model.ChangeOptions;
import com.ruesga.rview.gerrit.model.CommentInfo;
import com.ruesga.rview.gerrit.model.ConfigInfo;
import com.ruesga.rview.gerrit.model.DownloadFormat;
import com.ruesga.rview.gerrit.model.DraftActionType;
import com.ruesga.rview.gerrit.model.FileInfo;
import com.ruesga.rview.gerrit.model.FileStatus;
import com.ruesga.rview.gerrit.model.InitialChangeStatus;
import com.ruesga.rview.gerrit.model.NotifyType;
import com.ruesga.rview.gerrit.model.RebaseInput;
import com.ruesga.rview.gerrit.model.RestoreInput;
import com.ruesga.rview.gerrit.model.RevertInput;
import com.ruesga.rview.gerrit.model.ReviewInfo;
import com.ruesga.rview.gerrit.model.ReviewInput;
import com.ruesga.rview.gerrit.model.ReviewerInfo;
import com.ruesga.rview.gerrit.model.ReviewerInput;
import com.ruesga.rview.gerrit.model.ReviewerStatus;
import com.ruesga.rview.gerrit.model.RevisionInfo;
import com.ruesga.rview.gerrit.model.SubmitInput;
import com.ruesga.rview.gerrit.model.SubmitType;
import com.ruesga.rview.gerrit.model.TopicInput;
import com.ruesga.rview.misc.ActivityHelper;
import com.ruesga.rview.misc.AndroidHelper;
import com.ruesga.rview.misc.ModelHelper;
import com.ruesga.rview.misc.PicassoHelper;
import com.ruesga.rview.misc.StringHelper;
import com.ruesga.rview.model.Account;
import com.ruesga.rview.preferences.Constants;
import com.ruesga.rview.preferences.Preferences;
import com.ruesga.rview.widget.AccountChipView.OnAccountChipClickedListener;
import com.ruesga.rview.widget.AccountChipView.OnAccountChipRemovedListener;
import com.ruesga.rview.widget.DividerItemDecoration;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import me.tatarka.rxloader.RxLoader;
import me.tatarka.rxloader.RxLoader1;
import me.tatarka.rxloader.RxLoader2;
import me.tatarka.rxloader.RxLoaderManager;
import me.tatarka.rxloader.RxLoaderManagerCompat;
import me.tatarka.rxloader.RxLoaderObserver;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class ChangeDetailsFragment extends Fragment {

    private static final String TAG = "ChangeDetailsFragment";

    private static final List<ChangeOptions> OPTIONS = new ArrayList<ChangeOptions>() {{
        add(ChangeOptions.DETAILED_ACCOUNTS);
        add(ChangeOptions.DETAILED_LABELS);
        add(ChangeOptions.ALL_REVISIONS);
        add(ChangeOptions.ALL_COMMITS);
        add(ChangeOptions.ALL_FILES);
        add(ChangeOptions.MESSAGES);
        add(ChangeOptions.REVIEWED);
        add(ChangeOptions.CHANGE_ACTIONS);
        add(ChangeOptions.CHECK);
        add(ChangeOptions.WEB_LINKS);
    }};
    private static final List<ChangeOptions> MESSAGES_OPTIONS = new ArrayList<ChangeOptions>() {{
        add(ChangeOptions.DETAILED_ACCOUNTS);
        add(ChangeOptions.MESSAGES);
    }};
    private static final List<ChangeOptions> ACTIONS_OPTIONS = new ArrayList<ChangeOptions>() {{
        add(ChangeOptions.CHANGE_ACTIONS);
    }};

    @ProguardIgnored
    public static class ListModel {
        @StringRes
        public int header;
        public boolean visible;
        private ListModel(int h) {
            header = h;
            visible = false;
        }
    }

    @ProguardIgnored
    public static class Model {
        public boolean hasData = true;
        boolean isLocked = false;
        boolean isAuthenticated = false;
        public ListModel filesListModel = new ListModel(R.string.change_details_header_files);
        public ListModel msgListModel = new ListModel(R.string.change_details_header_messages);
    }

    @ProguardIgnored
    @SuppressWarnings({"UnusedParameters", "unused"})
    public static class EventHandlers {
        private final ChangeDetailsFragment mFragment;

        public EventHandlers(ChangeDetailsFragment fragment) {
            mFragment = fragment;
        }

        public void onPatchSetPressed(View v) {
            mFragment.showPatchSetChooser(v);
        }

        public void onStarredPressed(View v) {
            mFragment.performStarred(!v.isSelected());
        }

        public void onSharePressed(View v) {
            mFragment.performShare();
        }

        public void onAddReviewerPressed(View v) {
            mFragment.performShowAddReviewerDialog(v);
        }

        public void onAddMeAsReviewerPressed(View v) {
            Account account = Preferences.getAccount(v.getContext());
            if (account != null) {
                mFragment.performAddReviewer(String.valueOf(account.mAccount.accountId));
            }
        }

        public void onTopicEditPressed(View v) {
            mFragment.performShowChangeTopicDialog(v);
        }

        public void onRelatedChangesPressed(View v) {
            mFragment.performOpenRelatedChanges();
        }

        public void onDownloadPatchSetPressed(View v) {
            mFragment.performDownloadPatchSet();
        }

        public void onViewPatchSetPressed(View v) {
            mFragment.performViewPatchSet();
        }

        public void onReviewPressed(View v) {
            mFragment.performReview();
        }

        public void onReplyCommentPressed(View v) {
            mFragment.performReplyComment((int) v.getTag());
        }

        public void onActionPressed(View v) {
            mFragment.performAction(v);
        }

        public void onWebLinkPressed(View v) {
            String url = (String) v.getTag();
            if (url != null) {
                ActivityHelper.openUriInCustomTabs(mFragment.getActivity(), url);
            }
        }

        public void onFileItemPressed(View v) {
            mFragment.performOpenFileDiff((String) v.getTag());
        }

        public void onApplyFilterPressed(View v) {
            mFragment.performApplyFilter(v);
        }
    }

    @ProguardIgnored
    public static class FileItemModel {
        public String file;
        public FileInfo info;
        public int totalAdded;
        public int totalDeleted;
        public boolean hasGraph = true;
        public int inlineComments;
    }

    public static class FileInfoViewHolder extends RecyclerView.ViewHolder {
        private final FileInfoItemBinding mBinding;
        FileInfoViewHolder(FileInfoItemBinding binding) {
            super(binding.getRoot());
            mBinding = binding;
            binding.executePendingBindings();
        }
    }

    public static class TotalAddedDeletedViewHolder extends RecyclerView.ViewHolder {
        private final TotalAddedDeletedBinding mBinding;
        TotalAddedDeletedViewHolder(TotalAddedDeletedBinding binding) {
            super(binding.getRoot());
            mBinding = binding;
            binding.executePendingBindings();
        }
    }

    public static class MessageViewHolder extends RecyclerView.ViewHolder {
        private final MessageItemBinding mBinding;
        MessageViewHolder(MessageItemBinding binding) {
            super(binding.getRoot());
            mBinding = binding;
            binding.executePendingBindings();
        }
    }

    private static class FileAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final List<FileItemModel> mFiles = new ArrayList<>();
        private FileItemModel mTotals;
        private final EventHandlers mEventHandlers;

        FileAdapter(EventHandlers handlers) {
            mEventHandlers = handlers;
        }

        void update(Map<String, FileInfo> files, Map<String, Integer> inlineComments,
                    boolean initial) {
            mFiles.clear();
            mTotals = null;
            if (files == null) {
                notifyDataSetChanged();
                return;
            }

            // Always add the commit message which is not part of the revision files
            FileItemModel commitMessage = new FileItemModel();
            commitMessage.file = Constants.COMMIT_MESSAGE;
            commitMessage.info = new FileInfo();
            commitMessage.info.status =  initial ? FileStatus.A : FileStatus.M;
            commitMessage.hasGraph = false;
            mFiles.add(commitMessage);

            int added = 0;
            int deleted = 0;
            // Compute the added and deleted from revision instead from the change
            // to be accurate with the current revision info
            for (String key : files.keySet()) {
                FileInfo info = files.get(key);
                if (info.linesInserted != null) {
                    added += info.linesInserted;
                }
                if (info.linesDeleted != null) {
                    deleted += info.linesDeleted;
                }
            }

            // Create a model from each file
            for (String key : files.keySet()) {
                FileItemModel model = new FileItemModel();
                model.file = key;
                model.info = files.get(key);
                model.totalAdded = added;
                model.totalDeleted = deleted;
                model.inlineComments =
                        inlineComments.containsKey(key) ? inlineComments.get(key) : 0;
                model.hasGraph =
                        (model.info.linesInserted != null && model.info.linesInserted > 0) ||
                                (model.info.linesDeleted != null && model.info.linesDeleted > 0) ||
                                model.inlineComments > 0;
                mFiles.add(model);
            }

            // And add the total
            mTotals = new FileItemModel();
            mTotals.info = new FileInfo();
            if (added > 0) {
                mTotals.info.linesInserted = added;
            }
            if (deleted > 0) {
                mTotals.info.linesDeleted = deleted;
            }
            mTotals.totalAdded = added;
            mTotals.totalDeleted = deleted;

            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            return mFiles.size() + (mTotals != null ? 1 : 0);
        }

        @Override
        public int getItemViewType(int position) {
            return position == getItemCount() - 1 ? 1 : 0;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == 1) {
                return new TotalAddedDeletedViewHolder(DataBindingUtil.inflate(
                        inflater, R.layout.total_added_deleted, parent, false));
            } else {
                return new FileInfoViewHolder(DataBindingUtil.inflate(
                        inflater, R.layout.file_info_item, parent, false));
            }
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof TotalAddedDeletedViewHolder) {
                TotalAddedDeletedBinding binding = ((TotalAddedDeletedViewHolder) holder).mBinding;
                binding.addedVsDeleted.with(mTotals);
                binding.setModel(mTotals);
            } else {
                FileItemModel model = mFiles.get(position);
                FileInfoItemBinding binding = ((FileInfoViewHolder) holder).mBinding;
                binding.addedVsDeleted.with(model);
                binding.setModel(model);
                binding.setHandlers(mEventHandlers);
            }
        }
    }

    private static class MessageAdapter extends RecyclerView.Adapter<MessageViewHolder> {
        private final AccountInfo mBuildBotSystemAccount;
        private final EventHandlers mEventHandlers;
        private ChangeMessageInfo[] mMessages;

        MessageAdapter(ChangeDetailsFragment fragment, EventHandlers handlers) {
            final Resources res = fragment.getResources();
            mEventHandlers = handlers;

            mBuildBotSystemAccount = new AccountInfo();
            mBuildBotSystemAccount.name = res.getString(R.string.account_build_bot_system_name);
        }


        void update(ChangeMessageInfo[] messages) {
            mMessages = messages;
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            return mMessages != null ? mMessages.length : 0;
        }

        String getMessage(int position) {
            return mMessages[position].message;
        }

        @Override
        public MessageViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            return new MessageViewHolder(DataBindingUtil.inflate(
                    inflater, R.layout.message_item, parent, false));
        }

        @Override
        public void onBindViewHolder(MessageViewHolder holder, int position) {
            final Context context = holder.mBinding.getRoot().getContext();
            ChangeMessageInfo message = mMessages[position];
            if (message.author == null) {
                message.author = mBuildBotSystemAccount;
            }
            PicassoHelper.bindAvatar(context, PicassoHelper.getPicassoClient(context),
                    message.author, holder.mBinding.avatar,
                    PicassoHelper.getDefaultAvatar(context, R.color.primaryDark));
            holder.mBinding.setIndex(position);
            holder.mBinding.setModel(message);
            holder.mBinding.setHandlers(mEventHandlers);
        }
    }

    public static class DataResponse {
        ChangeInfo mChange;
        SubmitType mSubmitType;
        Map<String, ActionInfo> mActions;
        Map<String, Integer> mInlineComments;
        ConfigInfo mProjectConfig;
    }

    private final RxLoaderObserver<DataResponse> mChangeObserver =
            new RxLoaderObserver<DataResponse>() {
                @Override
                public void onNext(DataResponse result) {
                    mModel.isLocked = false;
                    updateLocked();
                    mResponse = result;
                    updateAuthenticatedAndOwnerStatus();

                    ChangeInfo change = null;
                    mModel.hasData = result != null;
                    if (mModel.hasData) {
                        change = result.mChange;
                        if (mCurrentRevision == null
                                || !change.revisions.containsKey(mCurrentRevision)) {
                            mCurrentRevision = change.currentRevision;
                        }

                        sortRevisions(change);
                        updatePatchSetInfo(result);
                        updateChangeInfo(result);
                        updateReviewInfo(result);

                        boolean initial = change.revisions.get(mCurrentRevision).number == 1;

                        Map<String, FileInfo> files = change.revisions.get(mCurrentRevision).files;
                        mModel.filesListModel.visible = files != null && !files.isEmpty();
                        mFileAdapter.update(files, result.mInlineComments, initial);
                        mModel.msgListModel.visible =
                                change.messages != null && change.messages.length > 0;
                        mMessageAdapter.update(change.messages);
                    }

                    mBinding.setModel(mModel);
                    showProgress(false, change);
                }

                @Override
                public void onError(Throwable error) {
                    ((BaseActivity) getActivity()).handleException(TAG, error);
                    showProgress(false, null);
                }

                @Override
                public void onStarted() {
                    mModel.isLocked = true;
                    updateLocked();

                    showProgress(true, null);
                }
            };

    private final RxLoaderObserver<Boolean> mStarredObserver = new RxLoaderObserver<Boolean>() {
        @Override
        public void onNext(Boolean value) {
            mResponse.mChange.starred = value;
            updateChangeInfo(mResponse);
        }

        @Override
        public void onError(Throwable error) {
            ((BaseActivity) getActivity()).handleException(TAG, error);
        }
    };

    private final RxLoaderObserver<ReviewInfo> mReviewObserver = new RxLoaderObserver<ReviewInfo>() {
        @Override
        public void onNext(ReviewInfo review) {
            // Update internal objects
            ReviewerInfo reviever = ModelHelper.createReviewer(mAccount.mAccount, review) ;
            if (mResponse.mChange.reviewers != null) {
                // Update reviewers
                AccountInfo[] reviewers = mResponse.mChange.reviewers.get(ReviewerStatus.REVIEWER);
                mResponse.mChange.reviewers.put(ReviewerStatus.REVIEWER,
                        ModelHelper.addReviewers(new ReviewerInfo[]{reviever}, reviewers));
            }
            if (mResponse.mChange.labels != null) {
                // Update labels
                for (String label : mResponse.mChange.labels.keySet()) {
                    ApprovalInfo[] approvals = mResponse.mChange.labels.get(label).all;
                    mResponse.mChange.labels.get(label).all =
                            ModelHelper.updateApprovals(
                                    new ReviewerInfo[]{reviever}, label, approvals);
                }
            }

            // Refresh messages and actions
            updateChangeInfo(mResponse);
            updateReviewInfo(mResponse);
            performMessagesRefresh();
            performActionsRefresh();

            // Clean the message box
            mBinding.reviewInfo.reviewComment.setText(null);
            AndroidHelper.hideSoftKeyboard(getContext(), getActivity().getWindow());
        }

        @Override
        public void onError(Throwable error) {
            ((BaseActivity) getActivity()).handleException(TAG, error);
        }
    };

    private final RxLoaderObserver<String> mChangeTopicObserver = new RxLoaderObserver<String>() {
        @Override
        public void onNext(String newTopic) {
            mResponse.mChange.topic = newTopic;
            updateChangeInfo(mResponse);

            // Refresh messages
            performMessagesRefresh();
        }

        @Override
        public void onError(Throwable error) {
            ((BaseActivity) getActivity()).handleException(TAG, error);
        }
    };

    private final RxLoaderObserver<ChangeMessageInfo[]> mMessagesRefreshObserver
            = new RxLoaderObserver<ChangeMessageInfo[]>() {
        @Override
        public void onNext(ChangeMessageInfo[] messages) {
            mResponse.mChange.messages = messages;
            mModel.msgListModel.visible = messages != null && messages.length > 0;
            mMessageAdapter.update(messages);
        }

        @Override
        public void onError(Throwable error) {
            ((BaseActivity) getActivity()).handleException(TAG, error);
        }
    };

    private final RxLoaderObserver<Map<String, ActionInfo>> mActionsRefreshObserver
            = new RxLoaderObserver<Map<String, ActionInfo>>() {
        @Override
        public void onNext(Map<String, ActionInfo> actions) {
            mResponse.mChange.actions = actions;
            updateChangeInfo(mResponse);
        }

        @Override
        public void onError(Throwable error) {
            ((BaseActivity) getActivity()).handleException(TAG, error);
        }
    };

    private final RxLoaderObserver<AccountInfo> mRemoveReviewerObserver
            = new RxLoaderObserver<AccountInfo>() {
        @Override
        public void onNext(AccountInfo account) {
            // Update internal objects
            if (mResponse.mChange.reviewers != null) {
                for (ReviewerStatus status : mResponse.mChange.reviewers.keySet()) {
                    AccountInfo[] reviewers = mResponse.mChange.reviewers.get(status);
                    mResponse.mChange.reviewers.put(status,
                            ModelHelper.removeAccount(account, reviewers));
                }
            }
            if (mResponse.mChange.labels != null) {
                for (String label : mResponse.mChange.labels.keySet()) {
                    ApprovalInfo[] approvals = mResponse.mChange.labels.get(label).all;
                    mResponse.mChange.labels.get(label).all =
                            ModelHelper.removeApproval(account, approvals);
                }
            }
            mResponse.mChange.removableReviewers = ModelHelper.removeAccount(
                    account, mResponse.mChange.removableReviewers);

            updateChangeInfo(mResponse);
        }

        @Override
        public void onError(Throwable error) {
            ((BaseActivity) getActivity()).handleException(TAG, error);
        }
    };

    private final RxLoaderObserver<AddReviewerResultInfo> mAddReviewerObserver
            = new RxLoaderObserver<AddReviewerResultInfo>() {
        @Override
        public void onNext(AddReviewerResultInfo result) {
            if (!TextUtils.isEmpty(result.error) || result.confirm) {
                onError(new OperationFailedException(String.format(Locale.US,
                        "error: %s; confirm: %s", result.error, String.valueOf(result.confirm))));
                return;
            }

            // Update internal objects
            if (mResponse.mChange.reviewers != null) {
                // Update reviewers
                AccountInfo[] reviewers = mResponse.mChange.reviewers.get(ReviewerStatus.REVIEWER);
                mResponse.mChange.reviewers.put(ReviewerStatus.REVIEWER,
                        ModelHelper.addReviewers(result.reviewers, reviewers));
            }
            if (mResponse.mChange.labels != null) {
                // Update labels
                for (String label : mResponse.mChange.labels.keySet()) {
                    ApprovalInfo[] approvals = mResponse.mChange.labels.get(label).all;
                    mResponse.mChange.labels.get(label).all =
                            ModelHelper.updateApprovals(result.reviewers, label, approvals);
                }
            }
            ModelHelper.updateRemovableReviewers(getContext(), mResponse.mChange, result);

            updateChangeInfo(mResponse);
        }

        @Override
        public void onError(Throwable error) {
            ((BaseActivity) getActivity()).handleException(TAG, error);
        }
    };

    private final RxLoaderObserver<Object> mActionObserver = new RxLoaderObserver<Object>() {
        @Override
        public void onNext(Object value) {
            if (value == null) {
                // The change was deleted. Redirect to parent
                ActivityHelper.performFinishActivity(getActivity(), true);
                return;
            }

            if (value instanceof ChangeInfo) {
                // Move to the new change
                ActivityHelper.openChangeDetails(getContext(), (ChangeInfo) value, false);
                return;
            }

            // Refresh the change
            forceRefresh();
        }

        @Override
        public void onError(Throwable error) {
            ((BaseActivity) getActivity()).handleException(TAG, error);
        }
    };


    private final OnAccountChipClickedListener mOnAccountChipClickedListener
            = this::performAccountClicked;
    private final OnAccountChipRemovedListener mOnAccountChipRemovedListener
            = this::performRemoveAccount;

    private ChangeDetailsFragmentBinding mBinding;
    private Picasso mPicasso;

    private FileAdapter mFileAdapter;
    private MessageAdapter mMessageAdapter;

    private EventHandlers mEventHandlers;
    private final Model mModel = new Model();
    private String mCurrentRevision;
    private DataResponse mResponse;
    private final List<RevisionInfo> mAllRevisions = new ArrayList<>();

    private RxLoader1<String, DataResponse> mChangeLoader;
    private RxLoader1<Boolean, Boolean> mStarredLoader;
    private RxLoader1<ReviewInput, ReviewInfo> mReviewLoader;
    private RxLoader1<String, AddReviewerResultInfo> mAddReviewerLoader;
    private RxLoader1<AccountInfo, AccountInfo> mRemoveReviewerLoader;
    private RxLoader1<String, String> mChangeTopicLoader;
    private RxLoader<ChangeMessageInfo[]> mMessagesRefreshLoader;
    private RxLoader<Map<String, ActionInfo>> mActionsRefreshLoader;
    private RxLoader2<String, String[], Object> mActionLoader;
    private int mLegacyChangeId;

    private Account mAccount;

    public static ChangeDetailsFragment newInstance(int changeId) {
        ChangeDetailsFragment fragment = new ChangeDetailsFragment();
        Bundle arguments = new Bundle();
        arguments.putInt(Constants.EXTRA_LEGACY_CHANGE_ID, changeId);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mLegacyChangeId = getArguments().getInt(
                Constants.EXTRA_LEGACY_CHANGE_ID, Constants.INVALID_CHANGE_ID);
        mPicasso = PicassoHelper.getPicassoClient(getContext());
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        mBinding = DataBindingUtil.inflate(
                inflater, R.layout.change_details_fragment, container, false);
        mBinding.setModel(mModel);
        mBinding.refresh.setEnabled(false);
        startLoadersWithValidContext();
        return mBinding.getRoot();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        startLoadersWithValidContext();
    }

    private void startLoadersWithValidContext() {
        if (getActivity() == null) {
            return;
        }

        if (mFileAdapter == null) {
            // Set authenticated mode
            mAccount = Preferences.getAccount(getContext());
            if (mAccount != null) {
                mModel.isAuthenticated = mAccount.hasAuthenticatedAccessMode();
            }
            updateAuthenticatedAndOwnerStatus();


            mEventHandlers = new EventHandlers(this);

            mFileAdapter = new FileAdapter(mEventHandlers);
            mBinding.fileInfo.list.setLayoutManager(new LinearLayoutManager(
                    getActivity(), LinearLayoutManager.VERTICAL, false));
            mBinding.fileInfo.list.setNestedScrollingEnabled(false);
            mBinding.fileInfo.list.setAdapter(mFileAdapter);

            mMessageAdapter = new MessageAdapter(this, mEventHandlers);
            int leftPadding = getResources().getDimensionPixelSize(
                    R.dimen.message_list_left_padding);
            DividerItemDecoration messageDivider = new DividerItemDecoration(
                    getContext(), LinearLayoutManager.VERTICAL);
            messageDivider.setMargins(leftPadding, 0);
            mBinding.messageInfo.list.setLayoutManager(new LinearLayoutManager(
                    getActivity(), LinearLayoutManager.VERTICAL, false));
            mBinding.messageInfo.list.addItemDecoration(messageDivider);
            mBinding.messageInfo.list.setNestedScrollingEnabled(false);
            mBinding.messageInfo.list.setAdapter(mMessageAdapter);

            // Configure the refresh
            setupSwipeToRefresh();

            // Fetch or join current loader
            RxLoaderManager loaderManager = RxLoaderManagerCompat.get(this);
            mChangeLoader = loaderManager.create("fetch", this::fetchChange, mChangeObserver)
                    .start(String.valueOf(mLegacyChangeId));
            mStarredLoader = loaderManager.create("starred", this::changeStarred, mStarredObserver);
            mReviewLoader = loaderManager.create("review", this::reviewChange, mReviewObserver);
            mChangeTopicLoader = loaderManager.create(
                    "change_topic", this::changeTopic, mChangeTopicObserver);
            mAddReviewerLoader = loaderManager.create(
                    "add_reviewer", this::addReviewer, mAddReviewerObserver);
            mRemoveReviewerLoader = loaderManager.create(
                    "remove_reviewer", this::removeReviewer, mRemoveReviewerObserver);
            mMessagesRefreshLoader = loaderManager.create(
                    "messages_refresh", fetchMessages(), mMessagesRefreshObserver);
            mActionsRefreshLoader = loaderManager.create(
                    "actions_refresh", fetchActions(), mActionsRefreshObserver);
            mActionLoader = loaderManager.create(
                    "action", this::doAction, mActionObserver);
        }
    }

    @Override
    public final void onDestroyView() {
        super.onDestroyView();
        mBinding.unbind();
    }

    private void updatePatchSetInfo(DataResponse response) {
        mBinding.patchSetInfo.setChangeId(response.mChange.changeId);
        mBinding.patchSetInfo.setRevision(mCurrentRevision);
        RevisionInfo revision = response.mChange.revisions.get(mCurrentRevision);
        mBinding.patchSetInfo.setChange(response.mChange);
        mBinding.patchSetInfo.setConfig(response.mProjectConfig);
        mBinding.patchSetInfo.setModel(revision);
        final int maxRevision = computeMaxRevisionNumber(response.mChange.revisions.values());
        final String patchSetText = getContext().getString(R.string.change_details_header_patchsets,
                revision.number, maxRevision);
        mBinding.patchSetInfo.setPatchset(patchSetText);
        mBinding.patchSetInfo.setHandlers(mEventHandlers);
        mBinding.patchSetInfo.parentCommits.with(mEventHandlers).from(revision.commit);
        mBinding.patchSetInfo.setHasData(true);
    }

    private void updateChangeInfo(DataResponse response) {
        mBinding.changeInfo.owner
                .with(mPicasso)
                .listenOn(mOnAccountChipClickedListener)
                .from(response.mChange.owner);
        mBinding.changeInfo.reviewers.with(mPicasso)
                .listenOn(mOnAccountChipClickedListener)
                .listenOn(mOnAccountChipRemovedListener)
                .withRemovableReviewers(true)
                .from(response.mChange);
        mBinding.changeInfo.labels.with(mPicasso).from(response.mChange);
        mBinding.changeInfo.setModel(response.mChange);
        mBinding.changeInfo.setSubmitType(response.mSubmitType);
        mBinding.changeInfo.setActions(response.mActions);
        mBinding.changeInfo.setHandlers(mEventHandlers);
        mBinding.changeInfo.setHasData(true);
        mBinding.changeInfo.setIsReviewer(ModelHelper.isReviewer(
                mAccount.mAccount, mResponse.mChange));
        mBinding.changeInfo.setIsTwoPane(getResources().getBoolean(R.bool.config_is_two_pane));
        mBinding.changeInfo.setIsCurrentRevision(
                mCurrentRevision.equals(response.mChange.currentRevision));
    }

    private void updateReviewInfo(DataResponse response) {
        mBinding.reviewInfo.setHasData(true);
        mBinding.reviewInfo.setModel(response.mChange);
        mBinding.reviewInfo.setHandlers(mEventHandlers);
        mBinding.reviewInfo.setIsCurrentRevision(
                mCurrentRevision.equals(response.mChange.currentRevision));
        mBinding.reviewInfo.reviewLabels.from(response.mChange);
    }

    private int computeMaxRevisionNumber(Collection<RevisionInfo> revisions) {
        int max = 0;
        for (RevisionInfo revision : revisions) {
            max = Math.max(revision.number, max);
        }
        return max;
    }

    @SuppressWarnings("ConstantConditions")
    private Observable<DataResponse> fetchChange(String changeId) {
        final Context ctx = getActivity();
        final GerritApi api = ModelHelper.getGerritApi(ctx);
        final String revision = mCurrentRevision == null
                ? GerritApi.CURRENT_REVISION : mCurrentRevision;
        return Observable.zip(
                    Observable.fromCallable(() -> {
                        DataResponse dataResponse = new DataResponse();
                        dataResponse.mChange = api.getChange(
                                changeId, OPTIONS).toBlocking().first();

                        // Obtain the project configuration
                        if (dataResponse.mChange != null) {
                            dataResponse.mProjectConfig = api.getProjectConfig(
                                    dataResponse.mChange.project).toBlocking().first();
                        }

                        return dataResponse;
                    }),
                    api.getChangeRevisionSubmitType(changeId, revision),
                    api.getChangeRevisionActions(changeId, revision),
                    api.getChangeRevisionComments(changeId, revision),
                    this::combineResponse
                )
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    @SuppressWarnings("ConstantConditions")
    private Observable<Boolean> changeStarred(final Boolean starred) {
        final Context ctx = getActivity();
        final GerritApi api = ModelHelper.getGerritApi(ctx);
        return Observable.fromCallable(() -> {
                    Observable<Void> call;
                    if (starred) {
                        call = api.putDefaultStarOnChange(
                                GerritApi.SELF_ACCOUNT, String.valueOf(mLegacyChangeId));
                    } else {
                        call = api.deleteDefaultStarFromChange(
                                GerritApi.SELF_ACCOUNT, String.valueOf(mLegacyChangeId));
                    }
                    call.toBlocking().first();
                    return starred;
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    @SuppressWarnings("ConstantConditions")
    private Observable<ReviewInfo> reviewChange(final ReviewInput input) {
        final Context ctx = getActivity();
        final GerritApi api = ModelHelper.getGerritApi(ctx);
        return Observable.fromCallable(() ->
                    api.setChangeRevisionReview(
                        String.valueOf(mLegacyChangeId), mCurrentRevision, input)
                        .toBlocking().first()
                )
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    @SuppressWarnings("ConstantConditions")
    private Observable<String> changeTopic(final String newTopic) {
        final Context ctx = getActivity();
        final GerritApi api = ModelHelper.getGerritApi(ctx);
        return Observable.fromCallable(() -> {
                    if (!TextUtils.isEmpty(newTopic)) {
                        TopicInput input = new TopicInput();
                        input.topic = newTopic;
                        api.setChangeTopic(String.valueOf(mLegacyChangeId), input)
                                .toBlocking().first();
                    } else {
                        api.deleteChangeTopic(String.valueOf(mLegacyChangeId))
                                .toBlocking().first();
                    }
                    return newTopic;
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    @SuppressWarnings("ConstantConditions")
    private Observable<AddReviewerResultInfo> addReviewer(final String reviewer) {
        final Context ctx = getActivity();
        final GerritApi api = ModelHelper.getGerritApi(ctx);
        return Observable.fromCallable(() -> {
                    ReviewerInput input = new ReviewerInput();
                    input.reviewerId = reviewer;
                    return api.addChangeReviewer(String.valueOf(mLegacyChangeId), input)
                            .toBlocking()
                            .first();
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    @SuppressWarnings("ConstantConditions")
    private Observable<AccountInfo> removeReviewer(final AccountInfo account) {
        final Context ctx = getActivity();
        final GerritApi api = ModelHelper.getGerritApi(ctx);
        return Observable.fromCallable(() -> {
                    api.deleteChangeReviewer(
                            String.valueOf(mLegacyChangeId),
                            String.valueOf(account.accountId))
                            .toBlocking().first();
                    return account;
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    @SuppressWarnings("ConstantConditions")
    private Observable<ChangeMessageInfo[]> fetchMessages() {
        final Context ctx = getActivity();
        final GerritApi api = ModelHelper.getGerritApi(ctx);
        return Observable.fromCallable(() -> {
                    final ChangeInfo change = api.getChange(
                            String.valueOf(mLegacyChangeId), MESSAGES_OPTIONS)
                                .toBlocking().first();
                    return change.messages;
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    @SuppressWarnings("ConstantConditions")
    private Observable<Map<String, ActionInfo>> fetchActions() {
        final Context ctx = getActivity();
        final GerritApi api = ModelHelper.getGerritApi(ctx);
        return Observable.fromCallable(() -> {
            final ChangeInfo change = api.getChange(
                    String.valueOf(mLegacyChangeId), ACTIONS_OPTIONS)
                    .toBlocking().first();
            Map<String, ActionInfo> actions =
                    api.getChangeRevisionActions(String.valueOf(mLegacyChangeId),
                        mCurrentRevision).toBlocking().first();
            if (actions == null) {
                actions = change.actions;
            } else {
                actions.putAll(change.actions);
            }
            return actions;
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    @SuppressWarnings("ConstantConditions")
    private Observable<Object> doAction(final String action, final String[] params) {
        final Context ctx = getActivity();
        final GerritApi api = ModelHelper.getGerritApi(ctx);
        return Observable.fromCallable(() -> {
                    switch (action) {
                        case ModelHelper.ACTION_CHERRY_PICK:
                            performCherryPickChange(api, params[0], params[1]);
                            break;
                        case ModelHelper.ACTION_REBASE:
                            return performRebaseChange(api, params[0]);
                        case ModelHelper.ACTION_ABANDON:
                            performAbandonChange(api, params[0]);
                            break;
                        case ModelHelper.ACTION_RESTORE:
                            performRestoreChange(api, params[0]);
                            break;
                        case ModelHelper.ACTION_REVERT:
                            return performRevertChange(api, params[0]);
                        case ModelHelper.ACTION_PUBLISH_DRAFT:
                            performPublishDraft(api);
                            break;
                        case ModelHelper.ACTION_DELETE_CHANGE:
                            performDeleteChange(api);
                            return null;
                        case ModelHelper.ACTION_FOLLOW_UP:
                            return performFollowUp(api, params[0]);
                        case ModelHelper.ACTION_SUBMIT:
                            performSubmitChange(api);
                            break;
                    }
                    return Boolean.TRUE;
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    private void setupSwipeToRefresh() {
        mBinding.refresh.setColorSchemeColors(
                ContextCompat.getColor(getContext(), R.color.accent));
        mBinding.refresh.setOnRefreshListener(this::forceRefresh);
        mBinding.refresh.setEnabled(false);
    }

    private void forceRefresh() {
        startLoadersWithValidContext();
        mChangeLoader.restart(String.valueOf(mLegacyChangeId));
    }

    private void showProgress(boolean show, ChangeInfo change) {
        BaseActivity activity = (BaseActivity) getActivity();
        if (show) {
            activity.onRefreshStart();
        } else {
            activity.onRefreshEnd(change);
        }
        mBinding.refresh.setEnabled(!show);
    }

    private DataResponse combineResponse(DataResponse response, SubmitType submitType,
                Map<String, ActionInfo> actions,
                Map<String, List<CommentInfo>> revisionComments) {
        // Map inline comments
        Map<String, Integer> inlineComments = new HashMap<>();
        if (revisionComments != null) {
            for (String file : revisionComments.keySet()) {
                inlineComments.put(file, revisionComments.get(file).size());
            }
        }

        // Join the actions
        response.mActions = actions;
        if (response.mActions == null) {
            response.mActions = response.mChange.actions;
        } else {
            response.mActions.putAll(response.mChange.actions);
        }

        response.mSubmitType = submitType;
        response.mInlineComments = inlineComments;
        return response;
    }

    private void performOpenFileDiff(String file) {
        ActivityHelper.openDiffViewerActivity(
                getContext(), mResponse.mChange, mCurrentRevision, file);
    }

    private void sortRevisions(ChangeInfo change) {
        mAllRevisions.clear();
        for (String revision : change.revisions.keySet()) {
            RevisionInfo rev = change.revisions.get(revision);
            rev.commit.commit = revision;
            mAllRevisions.add(rev);
        }
        Collections.sort(mAllRevisions, (o1, o2) -> {
            if (o1.number > o2.number) {
                return -1;
            }
            if (o1.number < o2.number) {
                return 1;
            }
            return 0;
        });
    }

    private void showPatchSetChooser(View anchor) {
        if (mModel.isLocked) {
            return;
        }

        final ListPopupWindow popupWindow = new ListPopupWindow(getContext());
        PatchSetsAdapter adapter = new PatchSetsAdapter(
                getContext(), mAllRevisions, mCurrentRevision);
        popupWindow.setAnchorView(anchor);
        popupWindow.setAdapter(adapter);
        popupWindow.setContentWidth(adapter.measureContentWidth());
        popupWindow.setOnItemClickListener((parent, view, position, id) -> {
            popupWindow.dismiss();
            mCurrentRevision = mAllRevisions.get(position).commit.commit;
            forceRefresh();
        });
        popupWindow.setModal(true);
        popupWindow.show();
    }

    private void performStarred(boolean starred) {
        if (!mModel.isLocked) {
            mStarredLoader.restart(starred);
        }
    }

    private void performChangeTopic(String newTopic) {
        if (!mModel.isLocked) {
            mChangeTopicLoader.restart(newTopic);
        }
    }

    private void performAccountClicked(AccountInfo account) {
        ChangeQuery filter = new ChangeQuery().owner(ModelHelper.getSafeAccountOwner(account));
        String title = ModelHelper.getAccountDisplayName(account);
        ActivityHelper.openChangeListByFilterActivity(getContext(), title, filter);
    }

    private void performAddReviewer(String reviewer) {
        if (!mModel.isLocked) {
            mAddReviewerLoader.restart(reviewer);
        }
    }

    private void performRemoveAccount(AccountInfo account) {
        if (!mModel.isLocked) {
            mRemoveReviewerLoader.restart(account);
        }
    }

    private void performMessagesRefresh() {
        if (!mModel.isLocked) {
            mMessagesRefreshLoader.restart();
        }
    }

    private void performActionsRefresh() {
        if (!mModel.isLocked) {
            mActionsRefreshLoader.restart();
        }
    }

    private void performReview() {
        if (!mModel.isLocked) {
            String message = StringHelper.obtainQuoteFromMessage(
                    mBinding.reviewInfo.reviewComment.getText().toString());
            Map<String, Integer> review = mBinding.reviewInfo.reviewLabels.getReview(false);

            ReviewInput input = new ReviewInput();
            input.drafts = DraftActionType.PUBLISH_ALL_REVISIONS;
            input.strictLabels = true;
            if (!review.isEmpty()) {
                input.labels = review;
            }
            if (!TextUtils.isEmpty(message)) {
                input.message = message;
            }
            input.notify = NotifyType.ALL;

            mReviewLoader.restart(input);
        }
    }

    private void updateLocked() {
        mBinding.patchSetInfo.setIsLocked(mModel.isLocked);
        mBinding.changeInfo.setIsLocked(mModel.isLocked);
        mBinding.reviewInfo.setIsLocked(mModel.isLocked);
        mBinding.executePendingBindings();
    }

    private void updateAuthenticatedAndOwnerStatus() {
        mBinding.patchSetInfo.setIsAuthenticated(mModel.isAuthenticated);
        mBinding.changeInfo.setIsAuthenticated(mModel.isAuthenticated);
        mBinding.reviewInfo.setIsAuthenticated(mModel.isAuthenticated);

        mBinding.changeInfo.setIsOwner(mModel.isAuthenticated && mResponse != null
                && mResponse.mChange.owner.accountId == mAccount.mAccount.accountId);
        mBinding.executePendingBindings();
    }

    private void performOpenRelatedChanges() {
        ActivityHelper.openRelatedChangesActivity(
                getContext(), mResponse.mChange, mCurrentRevision);
    }

    @SuppressWarnings("ConstantConditions")
    private void performViewPatchSet() {
        final Context ctx = getActivity();
        final GerritApi api = ModelHelper.getGerritApi(ctx);
        RevisionInfo revision = mResponse.mChange.revisions.get(mCurrentRevision);
        Uri uri = api.getRevisionUri(
                String.valueOf(mLegacyChangeId), String.valueOf(revision.number));

        ActivityHelper.openUriInCustomTabs(getActivity(), uri);
    }

    @SuppressWarnings("ConstantConditions")
    private void performDownloadPatchSet() {
        DownloadFormat downloadFormat = Preferences.getAccountDownloadFormat(
                getContext(), mAccount);

        final Context ctx = getActivity();
        final GerritApi api = ModelHelper.getGerritApi(ctx);
        Uri uri = api.getDownloadRevisionUri(
                String.valueOf(mLegacyChangeId), mCurrentRevision, downloadFormat);

        ActivityHelper.downloadUri(getContext(), uri, downloadFormat.mMimeType);
    }

    @SuppressWarnings("ConstantConditions")
    private void performShare() {
        final Context ctx = getActivity();
        final GerritApi api = ModelHelper.getGerritApi(ctx);
        Uri uri = api.getChangeUri(String.valueOf(mLegacyChangeId));

        ActivityHelper.shareTextPlain(
                getContext(), uri.toString(), getString(R.string.action_share));
    }

    private void performShowAddReviewerDialog(View v) {
        AddReviewerDialogFragment fragment =
                AddReviewerDialogFragment.newInstance(mLegacyChangeId, v);
        fragment.setOnReviewerSelected(this::performAddReviewer);
        fragment.show(getChildFragmentManager(), AddReviewerDialogFragment.TAG);
    }

    private void performShowChangeTopicDialog(View v) {
        String title = getString(R.string.change_topic_title);
        String action = getString(R.string.action_change);
        String hint = getString(R.string.change_topic_hint);

        EditDialogFragment fragment = EditDialogFragment.newInstance(
                title, mResponse.mChange.topic, action, hint, true, v);
        fragment.setOnEditChanged(this::performChangeTopic);
        fragment.show(getChildFragmentManager(), EditDialogFragment.TAG);
    }

    private void performShowChooseBaseDialog(View v, OnFilterSelectedListener cb) {
        BaseChooserDialogFragment fragment = BaseChooserDialogFragment.newInstance(
                mLegacyChangeId, mResponse.mChange.project, mResponse.mChange.branch, v);
        fragment.setOnFilterSelectedListener(cb);
        fragment.show(getChildFragmentManager(), BaseChooserDialogFragment.TAG);
    }

    private void performShowCherryPickDialog(View v, OnFilterSelectedListener cb) {
        String message = mResponse.mChange.revisions.get(mCurrentRevision).commit.message;
        CherryPickChooserDialogFragment fragment = CherryPickChooserDialogFragment.newInstance(
                mResponse.mChange.project, mResponse.mChange.branch, message, v);
        fragment.setOnFilterSelectedListener(cb);
        fragment.show(getChildFragmentManager(), BaseChooserDialogFragment.TAG);
    }

    private void performShowRequestMessageDialog(
            View v, String title, String action, String hint, boolean canBeEmpty, OnEditChanged cb) {
        EditDialogFragment fragment = EditDialogFragment.newInstance(
                title, null, action, hint, canBeEmpty, v);
        fragment.setOnEditChanged(cb);
        fragment.show(getChildFragmentManager(), EditDialogFragment.TAG);
    }

    private void performReplyComment(int position) {
        String currentMessage = mBinding.reviewInfo.reviewComment.getText().toString();
        String replyMessage = mMessageAdapter.getMessage(position);
        String msg = StringHelper.quoteMessage(currentMessage, replyMessage);
        mBinding.reviewInfo.reviewComment.setText(msg);
        mBinding.reviewInfo.reviewComment.setSelection(msg.length());
    }

    private void performApplyFilter(View v) {
        String title = null;
        ChangeQuery filter = null;
        switch (v.getId()) {
            case R.id.project:
                title = getString(R.string.change_details_project);
                filter = new ChangeQuery().project(((TextView) v).getText().toString());
                break;
            case R.id.branch:
                title = getString(R.string.change_details_branch);
                filter = new ChangeQuery().branch(((TextView) v).getText().toString());
                break;
            case R.id.topic:
                title = getString(R.string.change_details_topic);
                filter = new ChangeQuery().topic(((TextView) v).getText().toString());
                break;
        }
        ActivityHelper.openChangeListByFilterActivity(getContext(), title, filter);
    }

    private void performAction(View v) {
        if (!mModel.isLocked) {
            String action;
            String hint;
            switch (v.getId()) {
                case R.id.cherrypick:
                      performShowCherryPickDialog(v, o -> {
                          String[] result = (String[]) o;
                          mActionLoader.restart(ModelHelper.ACTION_CHERRY_PICK,
                                  new String[]{result[0], result[1]});
                      });
                    break;

                case R.id.rebase:
                    performShowChooseBaseDialog(v, o -> mActionLoader.restart(
                            ModelHelper.ACTION_REBASE, new String[]{(String) o}));
                    break;

                case R.id.abandon:
                    action = getString(R.string.change_action_abandon);
                    hint = getString(R.string.actions_message_hint);
                    performShowRequestMessageDialog(v, action, action, hint, true,
                            newValue -> mActionLoader.restart(
                                    ModelHelper.ACTION_ABANDON, new String[]{newValue}));
                    break;

                case R.id.restore:
                    action = getString(R.string.change_action_restore);
                    hint = getString(R.string.actions_message_hint);
                    performShowRequestMessageDialog(v, action, action, hint, true,
                            newValue -> mActionLoader.restart(
                                    ModelHelper.ACTION_RESTORE, new String[]{newValue}));
                    break;

                case R.id.revert:
                    action = getString(R.string.change_action_revert);
                    hint = getString(R.string.actions_message_hint);
                    performShowRequestMessageDialog(v, action, action, hint, true,
                            newValue -> mActionLoader.restart(
                                    ModelHelper.ACTION_REVERT, new String[]{newValue}));
                    break;

                case R.id.publish_draft:
                    mActionLoader.restart(ModelHelper.ACTION_PUBLISH_DRAFT, null);
                    break;

                case R.id.delete_change:
                    AlertDialog dialog = new AlertDialog.Builder(getContext())
                            .setTitle(R.string.delete_draft_change_title)
                            .setMessage(R.string.delete_draft_change_confirm)
                            .setPositiveButton(android.R.string.ok, (dialog1, which) ->
                                    mActionLoader.restart(ModelHelper.ACTION_DELETE_CHANGE, null))
                            .setNegativeButton(android.R.string.cancel, null)
                            .create();
                    dialog.show();
                    break;

                case R.id.follow_up:
                    action = getString(R.string.change_action_follow_up);
                    hint = getString(R.string.actions_message_hint);
                    performShowRequestMessageDialog(v, action, action, hint, false,
                            newValue -> mActionLoader.restart(
                                    ModelHelper.ACTION_FOLLOW_UP, new String[]{newValue}));
                    break;

                case R.id.submit:
                    mActionLoader.restart(ModelHelper.ACTION_SUBMIT, null);
                    break;
            }
        }
    }

    private void performSubmitChange(GerritApi api) {
        SubmitInput input = new SubmitInput();
        input.notify = NotifyType.ALL;
        api.submitChange(String.valueOf(mLegacyChangeId), input).toBlocking().first();
    }

    private ChangeInfo performRebaseChange(GerritApi api, String base) {
        RebaseInput input = null;
        if (!TextUtils.isEmpty(base)) {
            input = new RebaseInput();
            input.base = base;
        }
        return api.rebaseChange(String.valueOf(mLegacyChangeId), input).toBlocking().first();
    }

    private void performAbandonChange(GerritApi api, String msg) {
        AbandonInput input = new AbandonInput();
        input.notify = NotifyType.ALL;
        if (!TextUtils.isEmpty(msg)) {
            input.message = msg;
        }
        api.abandonChange(String.valueOf(mLegacyChangeId), input).toBlocking().first();
    }

    private void performRestoreChange(GerritApi api, String msg) {
        RestoreInput input = new RestoreInput();
        if (!TextUtils.isEmpty(msg)) {
            input.message = msg;
        }
        api.restoreChange(String.valueOf(mLegacyChangeId), input).toBlocking().first();
    }

    private ChangeInfo performRevertChange(GerritApi api, String msg) {
        RevertInput input = new RevertInput();
        input.notify = NotifyType.ALL;
        if (!TextUtils.isEmpty(msg)) {
            input.message = msg;
        }
        return api.revertChange(String.valueOf(mLegacyChangeId), input).toBlocking().first();
    }

    private void performPublishDraft(GerritApi api) {
        api.publishChangeDraftRevision(String.valueOf(mLegacyChangeId), mCurrentRevision)
                .toBlocking().first();
    }

    private void performDeleteChange(GerritApi api) {
        api.deleteDraftChange(String.valueOf(mLegacyChangeId)).toBlocking().first();
    }

    private ChangeInfo performFollowUp(GerritApi api, String subject) {
        ChangeInput change = new ChangeInput();
        change.baseChange = mResponse.mChange.id;
        change.branch = mResponse.mChange.branch;
        change.project = mResponse.mChange.project;
        change.status = InitialChangeStatus.DRAFT;
        if (!TextUtils.isEmpty(mResponse.mChange.topic)) {
            change.topic = mResponse.mChange.topic;
        }
        change.subject = subject;
        return api.createChange(change).toBlocking().first();
    }

    private void performCherryPickChange(GerritApi api, String branch, String msg) {
        // TODO
    }
}
