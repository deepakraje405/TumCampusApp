package de.tum.in.tumcampusapp.activities;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;

import java.util.List;

import de.tum.in.tumcampusapp.R;
import de.tum.in.tumcampusapp.activities.generic.ActivityForLoadingInBackground;
import de.tum.in.tumcampusapp.auxiliary.DepartureView;
import de.tum.in.tumcampusapp.auxiliary.NetUtils;
import de.tum.in.tumcampusapp.managers.RecentsManager;
import de.tum.in.tumcampusapp.managers.TransportManager;

/**
 * Activity to show transport departures for a specified station
 * <p>
 * NEEDS: EXTRA_STATION set in incoming bundle (station name)
 */
public class TransportationDetailsActivity extends ActivityForLoadingInBackground<String, List<TransportManager.Departure>> {
    public static final String EXTRA_STATION = "station";
    public static final String EXTRA_STATION_ID = "stationID";

    private LinearLayout mViewResults;
    private RecentsManager recentsManager;
    private TransportManager transportManager;

    public TransportationDetailsActivity() {
        super(R.layout.activity_transportation_detail);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // get all stations from db
        recentsManager = new RecentsManager(this, RecentsManager.STATIONS);
        transportManager = new TransportManager(this);
        mViewResults = (LinearLayout) this.findViewById(R.id.activity_transport_result);

        Intent intent = getIntent();
        if (intent == null) {
            finish();
            return;
        }
        String location = intent.getStringExtra(EXTRA_STATION);
        setTitle(location);
        String locationID = intent.getStringExtra(EXTRA_STATION_ID);

        startLoading(location, locationID);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_transport, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_transport_usage) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.transport_action_usage)
                    .setMessage(R.string.transport_help_text)
                    .setPositiveButton(android.R.string.ok, null)
                    .create().show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Load departure times
     *
     * @param arg Station name
     * @return List of departures
     */
    @Override
    protected List<TransportManager.Departure> onLoadInBackground(String... arg) {
        final String location = arg[0];

        // save clicked station into db
        recentsManager.replaceIntoDb(location);

        // Check for internet connectivity
        if (!NetUtils.isConnected(this)) {
            showNoInternetLayout();
            return null;
        }

        // get departures from website
        final String locationID = arg[1];
        List<TransportManager.Departure> departureCursor = TransportManager.getDeparturesFromExternal(this, locationID);
        if (departureCursor.isEmpty()) {
            showError(R.string.no_departures_found);
        }

        return departureCursor;
    }

    /**
     * Adds a new {@link DepartureView} for each departure entry
     *
     * @param result List of departures
     */
    @Override
    protected void onLoadFinished(List<TransportManager.Departure> result) {
        showLoadingEnded();
        if (result == null) {
            return;
        }
        mViewResults.removeAllViews();
        for (TransportManager.Departure d : result) {
            DepartureView view = new DepartureView(this, true);

            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    DepartureView departureView = (DepartureView) v;
                    String symbol = departureView.getSymbol();
                    boolean highlight;
                    if (transportManager.isFavorite(symbol)) {
                        transportManager.deleteFavorite(symbol);
                        highlight = false;
                    } else {
                        transportManager.addFavorite(symbol);
                        highlight = true;
                    }

                    // Update the other views with the same symbol
                    for (int i = 0; i < mViewResults.getChildCount(); i++) {
                        DepartureView child = (DepartureView) mViewResults.getChildAt(i);
                        if (child.getSymbol().equals(symbol)) {
                            child.setSymbol(symbol, highlight);
                        }
                    }
                }
            });

            if (transportManager.isFavorite(d.symbol)) {
                view.setSymbol(d.symbol, true);
            } else {
                view.setSymbol(d.symbol, false);
            }

            view.setLine(d.direction);
            view.setTime(d.countDown);
            mViewResults.addView(view);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        for (int i = 0; i < mViewResults.getChildCount(); i++) {
            View view = mViewResults.getChildAt(i);
            if (!(view instanceof DepartureView)) {
                continue;
            }
            ((DepartureView) view).removeAllCallbacksAndMessages();
        }
    }
}