package com.deuxfreres.emballage.admin;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Base des onglets « liste » : barre de titre, recherche, actualisation, bouton +. */
public abstract class ListFragment extends Fragment {
    protected MaterialToolbar toolbar;
    protected SwipeRefreshLayout swipe;
    protected ExtendedFloatingActionButton fab;
    protected EditText search;
    protected TextView empty;
    protected Rows adapter;
    private List<Rows.Row> all = new ArrayList<>();

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        View v = inf.inflate(R.layout.fragment_list, c, false);
        toolbar = v.findViewById(R.id.toolbar);
        swipe = v.findViewById(R.id.swipe);
        fab = v.findViewById(R.id.fab);
        search = v.findViewById(R.id.search);
        empty = v.findViewById(R.id.empty);
        RecyclerView list = v.findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new Rows(this::onRow, this::onRowLong);
        list.setAdapter(adapter);
        swipe.setColorSchemeResources(R.color.pink);
        swipe.setOnRefreshListener(this::load);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b2, int c2) {}
            @Override public void onTextChanged(CharSequence s, int a, int b2, int c2) {}
            @Override public void afterTextChanged(Editable s) { filter(); }
        });
        setup();
        return v;
    }

    @Override public void onResume() { super.onResume(); load(); }

    protected abstract void setup();
    protected abstract void load();
    protected void onRow(Rows.Row r) {}
    protected void onRowLong(Rows.Row r) {}

    protected void show(List<Rows.Row> rows, int emptyText) {
        swipe.setRefreshing(false);
        all = rows;
        empty.setText(emptyText);
        filter();
    }

    private void filter() {
        String q = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        List<Rows.Row> out = new ArrayList<>();
        for (Rows.Row r : all) {
            if (q.isEmpty() || (r.title + " " + r.subtitle + " " + r.meta).toLowerCase(Locale.ROOT).contains(q)) out.add(r);
        }
        adapter.submit(out);
        empty.setVisibility(out.isEmpty() ? View.VISIBLE : View.GONE);
    }

    protected void failed(Exception e) {
        swipe.setRefreshing(false);
        if (getActivity() != null) Ui.error(getActivity(), e);
    }
}
