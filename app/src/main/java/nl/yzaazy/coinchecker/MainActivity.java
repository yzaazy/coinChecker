package nl.yzaazy.coinchecker;

import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import cn.pedant.SweetAlert.SweetAlertDialog;
import in.galaxyofandroid.spinerdialog.OnSpinerItemClick;
import in.galaxyofandroid.spinerdialog.SpinnerDialog;
import nl.yzaazy.coinchecker.Adapter.ListAdapter;
import nl.yzaazy.coinchecker.Helpers.CoinInfoGetter;
import nl.yzaazy.coinchecker.Helpers.SettingsHelper;
import nl.yzaazy.coinchecker.Objects.Coin;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    String TAG = getClass().getName();
    SettingsHelper mSettingsHelper = new SettingsHelper();
    ArrayList<String> mNameList = new ArrayList<>();
    List<Coin> mList = new ArrayList<>();
    SwipeRefreshLayout mSwipeRefreshLayout;
    SpinnerDialog mSpinnerDialog;
    ListView mListView;
    ListAdapter mAdapter;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSettingsHelper.checkSettings();
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        mListView = findViewById(R.id.lvCoins);
        updateUI();
        //Swipe Refresh Layout
        mSwipeRefreshLayout = findViewById(R.id.srlCoins);
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                updateUI();
                mSwipeRefreshLayout.setRefreshing(false);
            }
        });
        //Floating Action Button
        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                fillFabButton();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        //noinspection SimplifiableIfStatement
        switch (id) {
            case R.id.action_remove_all:
                Coin.executeQuery("UPDATE coin SET is_checked = ?", "false");
                updateUI();
                Snackbar.make(this.mListView, R.string.delete_all_notification, Snackbar.LENGTH_SHORT).show();
                return true;
            case R.id.action_refresh:
                updateUI();
                Snackbar.make(this.mListView, R.string.refresh_notification, Snackbar.LENGTH_SHORT).show();
                return true;
            case R.id.switch_currency:
                mSettingsHelper.switchCurrency();
                updateUI();
                Snackbar.make(this.mListView, R.string.switch_currency, Snackbar.LENGTH_SHORT).show();
                return true;
            case R.id.force_database:
                mSettingsHelper.setJSONDate(new Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)));
                Snackbar.make(this.mListView, R.string.force_database, Snackbar.LENGTH_SHORT).show();
                return true;

        }
        return super.onOptionsItemSelected(item);
    }

    private void updateUI() {
        mList.clear();
        mNameList.clear();
        //todo: fix this with the new api
        new GetCoinsJSON().execute("https://api.coinmarketcap.com/v1/ticker/?convert=EUR");
        mAdapter = new ListAdapter(getApplicationContext(), mList, LayoutInflater.from(getApplicationContext()));
        mListView.setAdapter(mAdapter);
        mAdapter.notifyDataSetChanged();
    }

//    private List<String> getTrackedCoinNameList() {
//        List<TrackedCoin> trackedCoinList = TrackedCoin.listAll(TrackedCoin.class);
//        List<String> trackedCoinNameList = new ArrayList<>(trackedCoinList.size());
//        for (TrackedCoin trackedCoin : trackedCoinList) {
//            trackedCoinNameList.add(trackedCoin != null ? trackedCoin.getName() : null);
//        }
//        return trackedCoinNameList;
//    }

    private void fillFabButton() {
        SweetAlertDialog pDialog = new SweetAlertDialog(MainActivity.this, SweetAlertDialog.PROGRESS_TYPE);
        pDialog.getProgressHelper().setBarColor(Color.parseColor("#3F51B5"));
        pDialog.setCancelable(false);
        mSpinnerDialog = new SpinnerDialog(MainActivity.this, mNameList, MainActivity.this.getResources().getString(R.string.add_coin));
        CoinInfoGetter mCoinInfoGetter = new CoinInfoGetter(getApplicationContext(), mNameList, mSpinnerDialog, pDialog);
        mCoinInfoGetter.getAllCoins();
        //todo: create own spinner with better search and other cool stuff like custom list view.
        mSpinnerDialog.bindOnSpinerListener(new OnSpinerItemClick() {
            @Override
            public void onClick(String item, int position) {
                Coin coin = Coin.find(Coin.class, "name = ?", item).get(0);
                if(coin.getTracked()){
                    Snackbar.make(mListView, R.string.duplicate_coin_input, Snackbar.LENGTH_SHORT).show();
                }else {
                    coin.setTracked();
                    coin.save();
                    Snackbar.make(mListView, R.string.saved_coin_to_check, Snackbar.LENGTH_SHORT).show();
                    updateUI();
                }
                mAdapter.notifyDataSetChanged();
            }
        });
    }

    private class GetCoinsJSON extends AsyncTask<String, String, String> {
        OkHttpClient client = new OkHttpClient();

        @Override
        protected String doInBackground(String... params) {
            String url = params[0];
            Request request = new Request.Builder()
                    .url(url)
                    .build();
            try (Response response = client.newCall(request).execute()) {
                return response.body().string();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
//
//            List<String> coinToChecksNameList = getTrackedCoinNameList();
//
//            try {
//                JSONArray coinArray = new JSONArray(result);
//                for (int i = 0; i <= coinArray.length(); i++) {
//                    JSONObject coin = coinArray.getJSONObject(i);
//                    if (coinToChecksNameList.contains(coin.getString("name"))) {
//                        CryptoCoin cryptoCoin = new CryptoCoin();
//                        cryptoCoin.setId(coin.getString("id"));
//                        cryptoCoin.setName(coin.getString("name"));
//                        cryptoCoin.setSymbol(coin.getString("symbol"));
//                        try {
//                            cryptoCoin.setPercent_change_24h(Double.parseDouble(coin.getString("percent_change_24h")));
//                        } catch (NumberFormatException e) {
//                            cryptoCoin.setPercent_change_24h(0.0);
//                        }
//                        try {
//                            cryptoCoin.setPrice_usd(Double.parseDouble(coin.getString("price_usd")));
//                        } catch (NumberFormatException e) {
//                            cryptoCoin.setPrice_usd(0.0);
//                        }
//                        try {
//                            cryptoCoin.setPrice_eur(Double.parseDouble(coin.getString("price_eur")));
//                        } catch (NumberFormatException e) {
//                            cryptoCoin.setPrice_eur(0.0);
//                        }
//                        mList.add(cryptoCoin);
//                        mAdapter.notifyDataSetChanged();
//                    }
//                }
//            } catch (JSONException e) {
//                e.printStackTrace();
//            }
        }
    }

}
