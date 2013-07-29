
package com.example.BluetoothRemote;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;

/**
 * This Activity appears as a dialog. It lists any bookmarks and
 * when a bookmark is chosen by the user, the url sent back to the parent
 * Activity in the result Intent.
 */
public class BookmarkListActivity extends Activity {
    // Debugging
    private static final String TAG = "BookmarkListActivity";
    private static final boolean D = true;

    // Member fields
    private ArrayAdapter<String> mBookmarksArrayAdapter;

    // Return Intent extra
    public static String EXTRA_BOOKMARK_URL = "bookmark_url";
    public static String EXTRA_BOOKMARK_OPEN = "bookmark_add"; // true for add, false for delete

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Setup the window
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.bookmark_list);

        // Set result CANCELED in case the user backs out
        setResult(Activity.RESULT_CANCELED);

        // Initialize array adapter.
        mBookmarksArrayAdapter = new ArrayAdapter<String>(this, R.layout.bookmark_name);

        // Find and set up the ListView
        ListView bookmarkListView = (ListView) findViewById(R.id.bookmarks);
        bookmarkListView.setAdapter(mBookmarksArrayAdapter);
        bookmarkListView.setOnItemClickListener(mBookmarkClickListener);
        bookmarkListView.setOnItemLongClickListener(mBookmarkLongClickListener);

        // Get a set of current bookmarks
        Set<String> bookmarks = new HashSet<String>();

        try
        {
            FileInputStream fis = openFileInput("bookmarks.txt");
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
            String bookmark;
            bookmark = reader.readLine();
            while (bookmark != null){
                bookmarks.add(bookmark);
                bookmark = reader.readLine();
            }
            reader.close();
            fis.close();
        }
        catch (Exception e)
        {
            // error
        }

        // If there are bookmarks, add each one to the ArrayAdapter
        if (bookmarks.size() > 0) {
            for (String bookmark : bookmarks) {
                mBookmarksArrayAdapter.add(bookmark);
            }
        } else {
            String noBookmarks = getResources().getText(R.string.no_boomarks).toString();
            mBookmarksArrayAdapter.add(noBookmarks);
        }
    }

    // The on-click listener for all bookmarks in the ListView
    private OnItemClickListener mBookmarkClickListener = new OnItemClickListener() {
        public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {
            // Get the bookmark
            String url = ((TextView) v).getText().toString();

            // Create the result Intent
            Intent intent = new Intent();
            intent.putExtra(EXTRA_BOOKMARK_URL, url);
            intent.putExtra(EXTRA_BOOKMARK_OPEN, true);

            // Set result and finish this Activity
            setResult(Activity.RESULT_OK, intent);
            finish();
        }
    };

    // The on-long-click listener for all bookmarks in the ListView
    private OnItemLongClickListener mBookmarkLongClickListener = new OnItemLongClickListener() {
        public boolean onItemLongClick(AdapterView<?> av, View v, int arg2, long arg3) {
            // Get the bookmark
            String url = ((TextView) v).getText().toString();
            confirmDelete(url);
            return true;
        }
    };

    /**
     * Confirms deletion with the user. If "Yes" is selected,
     * {@link #deleteBookmark(String)} is called to return the intent.
     * @param url
     * @see #deleteBookmark(String)
     */
    private void confirmDelete(final String url){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Delete Bookmark?");
        builder.setMessage("Are you sure you want to delete " + url + " from your bookmarks?");
        builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                // Continue with delete
                deleteBookmark(url);
            }
        });
        builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                // Do nothing
            }
        });
        builder.show();
    }

    /**
     * Returns the Intent to the requesting Activity, and in turn,
     * deleting the bookmark
     * @param url
     */
    private void deleteBookmark(String url) {
        // Create the result Intent
        Intent intent = new Intent();
        intent.putExtra(EXTRA_BOOKMARK_URL, url);
        intent.putExtra(EXTRA_BOOKMARK_OPEN, false);

        // Set result and finish this Activity
        setResult(Activity.RESULT_OK, intent);
        finish();
    }

}
