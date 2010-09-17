package de.bsd.zwitscher;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;
import android.widget.Toast;

import twitter4j.Paging;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.UserList;
import twitter4j.http.AccessToken;
import twitter4j.http.RequestToken;

public class TwitterHelper {

	
    Context context;

	public TwitterHelper(Context context) {
		this.context = context;
	}
	
	public void tweet(String text) {
        Twitter twitter = getTwitter();
        
        try {
			twitter.updateStatus(text);
			Toast.makeText(context, R.string.tweet_sent , 2500).show();
		} catch (TwitterException e) {
			Toast.makeText(context, "Failed to send tweet: " + e.getLocalizedMessage(), 10000).show();
		}
	}
	
	public List<Status> getFriendsTimeline(Paging paging) {
        Twitter twitter = getTwitter();

        List<Status> statuses;
		try {
			statuses = twitter.getFriendsTimeline(paging );
			return statuses;
		}
		catch (Exception e) {
        	System.err.println("Got exception: " + e.getMessage() );
        	if (e.getCause()!=null)
        		System.err.println("   " + e.getCause().getMessage());
            String text =  context.getString(R.string.no_connect) + ": " + e.getMessage();
            Toast.makeText(context, text, 15000).show();
            return new ArrayList<Status>();
		}
	}
	
	public List<String> getListNames() {
		Twitter twitter = getTwitter();
		
		try {
			String username = twitter.getScreenName(); 
			List<UserList> userLists = twitter.getUserLists(username, -1);
			List<String> lists = new ArrayList<String>(userLists.size());
			for (UserList uList : userLists) {
				lists.add(uList.getName());
			}
			return lists;
		} catch (Exception e) {
			Toast.makeText(context, "Getting lists failed: " + e.getMessage(), 15000).show();
			e.printStackTrace();
			return new ArrayList<String>();
		} // TODO cursor?
	}


	private Twitter getTwitter() {
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        
        String accessTokenToken = preferences.getString("accessToken",null);
        String accessTokenSecret = preferences.getString("accessTokenSecret",null);
        if (accessTokenToken!=null && accessTokenSecret!=null) {
        	Twitter twitter = new TwitterFactory().getInstance();
            twitter.setOAuthConsumer(TwitterConsumerToken.consumerKey, TwitterConsumerToken.consumerSecret);
        	twitter.setOAuthAccessToken(accessTokenToken, accessTokenSecret); // TODO replace by non-deprecated method
        	
        	return twitter;
        }
        
		return null;
	}

	public String getAuthUrl() throws Exception {
		// TODO use fresh token for the first call
        RequestToken requestToken = getRequestToken(true);
        String authUrl = requestToken.getAuthorizationURL();
        
        return authUrl;
	}

	public RequestToken getRequestToken(boolean useFresh) throws Exception {
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

		if (!useFresh) {
			if (preferences.contains("requestToken")) {
				String rt = preferences.getString("requestToken", null);
				String rts = preferences.getString("requestTokenSecret", null);
				RequestToken token = new RequestToken(rt, rts);
				return token;
			}
		}
		
		
        Twitter twitter = new TwitterFactory().getInstance();
        twitter.setOAuthConsumer(TwitterConsumerToken.consumerKey, TwitterConsumerToken.consumerSecret);
        RequestToken requestToken = twitter.getOAuthRequestToken();
        Editor editor = preferences.edit();
        editor.putString("requestToken", requestToken.getToken());
        editor.putString("requestTokenSecret", requestToken.getTokenSecret());
        editor.commit();
        
        return requestToken;
	}

	public void generateAuthToken(String pin) throws Exception{
        Twitter twitter = new TwitterFactory().getInstance();
        twitter.setOAuthConsumer(TwitterConsumerToken.consumerKey, TwitterConsumerToken.consumerSecret);
        RequestToken requestToken = getRequestToken(false); // twitter.getOAuthRequestToken();
		AccessToken accessToken = twitter.getOAuthAccessToken(requestToken, pin);
		
		
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
		Editor editor = preferences.edit();
		editor.putString("accessToken", accessToken.getToken());
		editor.putString("accessTokenSecret", accessToken.getTokenSecret());
		editor.commit();

		
	}
	
}