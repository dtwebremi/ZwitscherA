package de.bsd.zwitscher;

import java.util.ArrayList;
import java.util.List;

import android.app.ListActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.*;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import twitter4j.Paging;
import twitter4j.Status;
import twitter4j.User;

/**
 * Show the list of tweets
 * @author Heiko W. Rupp
 */
public class TweetListActivity extends ListActivity implements AbsListView.OnScrollListener {

    List<Status> statuses;
    Bundle intentInfo;
    TweetListActivity thisActivity;
    ProgressBar pg;
    TextView titleTextBox;
    int list_id;
    int timeLine;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        intentInfo = getIntent().getExtras();
        thisActivity = this;
        // Get the windows progress bar from the enclosing TabWidget
        TabWidget parent = (TabWidget) this.getParent();
        pg = parent.pg;
        titleTextBox = parent.titleTextBox;

        fillListViewFromTimeline(true); // Only get tweets from db to speed things up at start
    }

    @Override
    public void onResume() {

    	super.onResume();
        intentInfo = getIntent().getExtras();
        if (intentInfo==null) {
            timeLine = R.string.home_timeline;
            list_id = 0;
        } else {
            // TODO more
        }


        // Get the windows progress bar from the enclosing TabWidget
        TabWidget parent = (TabWidget) this.getParent();
        pg = parent.pg;

		ListView lv = getListView();
        lv.setOnScrollListener(this);

		lv.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				Intent i = new Intent(parent.getContext(),OneTweetActivity.class);
				i.putExtra(getString(R.string.status), statuses.get(position));
				startActivity(i);

			}
		});
		lv.setOnItemLongClickListener(new OnItemLongClickListener() { // directly go to reply.

			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view,
					int position, long id) {
				Log.i("TLA","Long click, pos=" + position + ",id="+id);
				Intent i = new Intent(parent.getContext(), NewTweetActivity.class);
				i.putExtra(getString(R.string.status), statuses.get(position));
				i.putExtra("op",getString(R.string.reply));
				startActivity(i);

				return true; // We've consumed the long click
			}
		});

    }

	private List<Status> getTimlinesFromTwitter(int timeline, int listId, String specialName, boolean fromDbOnly) {
		TwitterHelper th = new TwitterHelper(getApplicationContext());
		Paging paging = new Paging().count(100);
		TweetDB tdb = new TweetDB(this);
		List<Status> myStatus = new ArrayList<Status>();

		// special name is set for lists - this is the list name
		// Now fake it for other timelines
        switch (timeline) {
        case R.string.home_timeline:
        	specialName = "home";
        	break;
        case R.string.mentions:
        	specialName = "mentions";
        	break;
        }

    	long last = tdb.getLastRead(specialName);
    	if (last!=0 )//&& !Debug.isDebuggerConnected())
    		paging.sinceId(last);

        switch (timeline) {
        case R.string.home_timeline:
        	myStatus = th.getTimeline(paging,R.string.home_timeline, fromDbOnly);
        	break;
        case R.string.mentions:
        	myStatus = th.getTimeline(paging, R.string.mentions, fromDbOnly);
        	break;
        case R.string.list:
        	myStatus = th.getUserList(paging,listId, fromDbOnly);
        	break;
        }

        // Update the 'since' id in the database
    	if (myStatus.size()>0) {
    		last = myStatus.get(0).getId(); // assumption is that twitter sends the newest (=highest id) first
    		tdb.updateOrInsertLastRead(specialName, last);
    	}

    	statuses = new ArrayList<Status>();
		List<Status> data = new ArrayList<Status>(myStatus.size());
        String filter = getFilter();
		for (Status status : myStatus) {
			User user = status.getUser();
			String item ="";
			if ((filter==null) || (filter!= null && !status.getText().matches(filter))) {
				data.add(status);
				statuses.add(status);
			} else {
				Log.i("TweetListActivity::filter",status.getUser().getScreenName() + " - " + status.getText());

			}
		}
		return data;
	}

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {

    	if (item!=null && item.getItemId() == R.id.reload_item) {
            fillListViewFromTimeline(false);
    		return true;
    	}

    	return super.onMenuItemSelected(featureId, item);
    }

    private void fillListViewFromTimeline(boolean fromDbOnly) {
    	new GetTimeLineTask().execute(fromDbOnly);
    }

    @Override
    public void onScrollStateChanged(AbsListView absListView, int i) {
        // TODO: Customise this generated block
    }

    @Override
    public void onScroll(AbsListView absListView, int firstVisible, int visibleCount, int totalCount) {

        boolean loadMore = /* maybe add a padding */
            firstVisible + visibleCount >= totalCount;

        ListAdapter adapter = absListView.getAdapter();
        Log.d("onScroll: ",  firstVisible + ", " + visibleCount + ", " +totalCount);
        if(loadMore) {
            Log.d("onSroll:","loadMore");
            if (adapter instanceof StatusAdapter) {
                StatusAdapter sta = (StatusAdapter) adapter;
                Status last = (Status) sta.getItem(totalCount-1);

                TwitterHelper th = new TwitterHelper(thisActivity);
                List<Status> newStatuses = th.getStatuesFromDb(last.getId(),5,list_id);

                int i = 0;
                for (Status status : newStatuses ) {
                    sta.insert(status,totalCount+i);
                    statuses.add(status);
                    i++;
                }
            }

        }
    }

    private class GetTimeLineTask extends AsyncTask<Boolean, Void, List<Status>> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            pg.setVisibility(ProgressBar.VISIBLE);
            titleTextBox.setText("Getting tweets...");
        }


		@Override
		protected List<twitter4j.Status> doInBackground(Boolean... params) {
            boolean fromDbOnly = params[0];
	        List<twitter4j.Status> data;
	        if (intentInfo==null)
	            data = getTimlinesFromTwitter(R.string.home_timeline,0, null, fromDbOnly);
	        else {
	        	String timelineString = intentInfo.getString("timeline");
				if (timelineString!=null && timelineString.contains("mentions")) {
	        		data = getTimlinesFromTwitter(R.string.mentions, 0, null, fromDbOnly);
	        	} else {

		            String listName = intentInfo.getString("listName");
		            int id = intentInfo.getInt("id");
		            data = getTimlinesFromTwitter(R.string.list,id, listName, fromDbOnly);
	        	}
	        }
	        return data;
		}

		@Override
		protected void onPostExecute(List<twitter4j.Status> result) {
	        setListAdapter(new StatusAdapter<twitter4j.Status>(thisActivity, R.layout.list_item, result));
            pg.setVisibility(ProgressBar.INVISIBLE);
            titleTextBox.setText("");
	        getListView().requestLayout();
		}
    }

    // ".*(http://4sq.com/|http://shz.am/).*"
    private String getFilter() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String exp = prefs.getString("filter",null);
        if (exp==null)
            return null;
        String ret=".*(" + exp.replaceAll(",","|") + ").*";

        Log.i("TweetListActivity::getFilter()","Filter is " + ret);
        return ret;
    }

}
