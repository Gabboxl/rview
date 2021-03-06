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
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.ruesga.rview.BaseActivity;
import com.ruesga.rview.R;
import com.ruesga.rview.databinding.StatsPageFragmentBinding;
import com.ruesga.rview.gerrit.GerritApi;
import com.ruesga.rview.gerrit.filter.ChangeQuery;
import com.ruesga.rview.gerrit.model.ChangeInfo;
import com.ruesga.rview.gerrit.model.ChangeOptions;
import com.ruesga.rview.gerrit.model.ChangeStatus;
import com.ruesga.rview.misc.ModelHelper;
import com.ruesga.rview.model.Stats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;
import me.tatarka.rxloader2.RxLoader;
import me.tatarka.rxloader2.RxLoaderManager;
import me.tatarka.rxloader2.RxLoaderManagerCompat;
import me.tatarka.rxloader2.RxLoaderObserver;
import me.tatarka.rxloader2.safe.SafeObservable;

public abstract class StatsPageFragment<T> extends Fragment implements SelectableFragment {

    private static final int MAX_CHANGES = 500;
    private static final int MAX_DAYS = 30;

    private static final List<ChangeOptions> OPTIONS = new ArrayList<ChangeOptions>() {{
        add(ChangeOptions.DETAILED_ACCOUNTS);
    }};

    private final RxLoaderObserver<T> mDetailsObserver = new RxLoaderObserver<T>() {
        @Override
        public void onNext(T result) {
            bindDetails(result);
            showProgress(false, result);
            performRequestStats();
        }

        @Override
        public void onError(Throwable e) {
            //noinspection ConstantConditions
            ((BaseActivity) getActivity()).handleException(getStatsFragmentTag(), e);
            showProgress(false, null);
        }

        @Override
        public void onStarted() {
            showProgress(true, null);
        }
    };

    private final RxLoaderObserver<List<Stats>> mStatsObserver = new RxLoaderObserver<List<Stats>>() {
        @Override
        public void onNext(List<Stats> stats) {
            mBinding.mergedStatusChart.update(stats);
            mBinding.activityChart.update(stats);
            mBinding.top5List.listenTo(item -> openCrossItem(item)).update(stats);
            mBinding.setLoading(false);
            mBinding.setEmpty(stats == null || stats.isEmpty());
        }

        @Override
        public void onError(Throwable e) {
            //noinspection ConstantConditions
            ((BaseActivity) getActivity()).handleException(getStatsFragmentTag(), e);
            mBinding.setLoading(false);
        }
    };


    private StatsPageFragmentBinding mBinding;
    private RxLoader<List<Stats>> mStatsLoader;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        mBinding = DataBindingUtil.inflate(inflater, R.layout.stats_page_fragment, container, false);
        mBinding.detailsStub.addView(inflateDetails(inflater, mBinding.detailsStub));
        mBinding.setLoading(true);
        mBinding.setEmpty(true);
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

        if (mStatsLoader == null) {
            RxLoaderManager loaderManager = RxLoaderManagerCompat.get(this);
            mStatsLoader = loaderManager.create("stats", internalFetchStats(), mStatsObserver);
            RxLoader<T> loader = loaderManager.create(
                    "details", internalFetchDetails(), mDetailsObserver);
            loader.start();
        }
    }

    public abstract View inflateDetails(LayoutInflater inflater, @Nullable ViewGroup container);

    public abstract Observable<T> fetchDetails();

    public abstract void bindDetails(T result);

    public abstract String getStatsFragmentTag();

    public abstract ChangeQuery getStatsQuery();

    public abstract String getDescription(ChangeInfo change);

    public abstract String getCrossDescription(ChangeInfo change);

    public abstract String getSerializedCrossItem(ChangeInfo change);

    public abstract void openCrossItem(String item);

    protected int getMaxDays() {
        return MAX_DAYS;
    }

    private void showProgress(boolean show, T result) {
        BaseActivity activity = (BaseActivity) getActivity();
        if (show) {
            //noinspection ConstantConditions
            activity.onRefreshStart(this);
        } else {
            //noinspection ConstantConditions
            activity.onRefreshEnd(this, result);
        }
    }

    private Observable<T> internalFetchDetails() {
        return fetchDetails()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    @SuppressWarnings("ConstantConditions")
    private Observable<List<Stats>> internalFetchStats() {
        final Context ctx = getActivity();
        final GerritApi api = ModelHelper.getGerritApi(ctx);
        return SafeObservable.fromNullCallable(() -> {
                ChangeQuery query = getStatsQuery();
                List<ChangeInfo> changes = new ArrayList<>();
                List<ChangeInfo> tmp;
                int start = 0;
                do {
                    tmp = api.getChanges(query, MAX_CHANGES, start, OPTIONS)
                            .blockingFirst();
                    changes.addAll(tmp);
                    start += MAX_CHANGES;
                } while (tmp.size() == MAX_CHANGES);
                return changes;
            })
            .flatMap((Function<List<ChangeInfo>, ObservableSource<List<Stats>>>) changes -> {
                ArrayList<Stats> stats = new ArrayList<>(changes.size());
                for (ChangeInfo change : changes) {
                    final Stats s = new Stats();
                    if (change.status.equals(ChangeStatus.NEW)
                            || change.status.equals(ChangeStatus.DRAFT)
                            || change.status.equals(ChangeStatus.SUBMITTED)) {
                        s.mStatus = ChangeStatus.NEW;
                    } else {
                        s.mStatus = change.status;
                    }
                    if (s.mStatus.equals(ChangeStatus.NEW)) {
                        s.mDate = change.created;
                    } else {
                        s.mDate = change.updated;
                    }
                    s.mDescription = getDescription(change);
                    s.mCrossDescription = getCrossDescription(change);
                    s.mCrossItem = getSerializedCrossItem(change);
                    stats.add(s);
                }

                Collections.sort(stats, (o1, o2) -> o1.mDate.compareTo(o2.mDate));
                return Observable.just(stats);
            })
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread());
    }

    private void performRequestStats() {
        mStatsLoader.clear();
        mStatsLoader.restart();
    }

    @Override
    public void onFragmentSelected() {
        //noinspection ConstantConditions
        ((BaseActivity) getActivity()).setUseTwoPanel(false);
    }
}
