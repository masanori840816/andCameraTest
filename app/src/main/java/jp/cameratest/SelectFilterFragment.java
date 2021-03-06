package jp.cameratest;

import android.os.Bundle;
import android.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import jp.cameratest.databinding.FragmentSelectFilterBinding;

public class SelectFilterFragment extends Fragment {
    protected FilterListAdapter adapter;

    public SelectFilterFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_select_filter, container, false);
        FragmentSelectFilterBinding binding = FragmentSelectFilterBinding.bind(rootView);

        int scrollPosition = 0;

        // If a layout manager has already been set, get current scroll position.
        if (binding.recyclerSelectfilter.getLayoutManager() != null) {
            scrollPosition = ((LinearLayoutManager) binding.recyclerSelectfilter.getLayoutManager())
                    .findFirstCompletelyVisibleItemPosition();
        }
        binding.recyclerSelectfilter.scrollToPosition(scrollPosition);

        adapter = new FilterListAdapter();

        // Set CustomAdapter as the adapter for RecyclerView.
        binding.recyclerSelectfilter.setAdapter(adapter);

        // Inflate the layout for this fragment
        return rootView;
    }
}
